package org.taskhub.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [retryTransientReadFailure] es la única pieza nueva de la tarjeta kanban
 * "Retry/backoff idempotente en FirestoreClient" que se puede probar sin I/O
 * real: decide qué fallos son transitorios ([isTransientReadFailure]) y
 * cuántas veces reintentar antes de rendirse. El resto del cambio (aplicar el
 * helper en los call-sites de lectura) no añade lógica propia que testear.
 */
class FirestoreClientRetryTest {

    // ── isTransientReadFailure ──────────────────────────────────────────

    @Test
    fun isTransientReadFailure_serverError5xx_esTransitorio() {
        val e = FirestoreException(statusCode = 503, code = "UNAVAILABLE", message = "boom")
        assertTrue(e.isTransientReadFailure())
    }

    @Test
    fun isTransientReadFailure_clientError4xx_noEsTransitorio() {
        val e = FirestoreException(statusCode = 404, code = "NOT_FOUND", message = "gone")
        assertFalse(e.isTransientReadFailure())
    }

    @Test
    fun isTransientReadFailure_excepcionAjenaANetworking_noEsTransitorio() {
        assertFalse(IllegalStateException("dato inconsistente").isTransientReadFailure())
    }

    /**
     * [FirestoreClient.redactApiKey] reenvuelve un timeout/error de conexión
     * como `IllegalStateException` para censurar la API key embebida en el
     * mensaje (ver su KDoc) — sin mirar [Throwable.cause], `ensureAuth()`
     * trataría ese timeout de red como un refresh token inválido y borraría
     * la sesión de Google persistida (panel de expertos sync v10, Oleada A,
     * CRÍTICO). Este test cubre esa pieza añadida en la Oleada A, que se
     * había quedado sin test unitario propio.
     */
    @Test
    fun isTransientReadFailure_causaEsIOException_esTransitorioPeseAEnvoltorioNoTransitorio() {
        val original = kotlinx.io.IOException("timeout")
        val redacted = IllegalStateException("mensaje censurado ***", original)
        assertTrue(redacted.isTransientReadFailure())
    }

    /** Igual que el caso anterior pero encadenado dos niveles (recursión). */
    @Test
    fun isTransientReadFailure_causaAnidadaDosNiveles_esTransitorio() {
        val original = kotlinx.io.IOException("connect refused")
        val middle = IllegalStateException("envuelto una vez", original)
        val outer = IllegalStateException("envuelto dos veces", middle)
        assertTrue(outer.isTransientReadFailure())
    }

    /** Una causa que tampoco es transitoria no debe "contagiar" falso positivo. */
    @Test
    fun isTransientReadFailure_causaNoTransitoria_noEsTransitorio() {
        val original = IllegalArgumentException("dato corrupto")
        val wrapped = IllegalStateException("mensaje censurado ***", original)
        assertFalse(wrapped.isTransientReadFailure())
    }

    // ── retryTransientReadFailure ────────────────────────────────────────

    /** Dos fallos 5xx seguidos de un éxito deben acabar devolviendo el éxito. */
    @Test
    fun retryTransientReadFailure_reintentaTrasFallosTransitoriosYAcabaEnExito() = runTest {
        var attempts = 0
        val result = retryTransientReadFailure(maxAttempts = 3, initialDelayMillis = 1) {
            attempts++
            if (attempts < 3) throw FirestoreException(statusCode = 503, code = "UNAVAILABLE", message = "boom")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    /** Agotados los intentos, se relanza la última excepción tal cual. */
    @Test
    fun retryTransientReadFailure_agotaIntentosYRelanzaElUltimoFallo() = runTest {
        var attempts = 0
        assertFailsWith<FirestoreException> {
            retryTransientReadFailure(maxAttempts = 2, initialDelayMillis = 1) {
                attempts++
                throw FirestoreException(statusCode = 503, code = "UNAVAILABLE", message = "boom")
            }
        }
        assertEquals(2, attempts)
    }

    /** Un 4xx no es transitorio: se relanza en el primer intento, sin reintentar. */
    @Test
    fun retryTransientReadFailure_noReintentaUnFalloNoTransitorio() = runTest {
        var attempts = 0
        assertFailsWith<FirestoreException> {
            retryTransientReadFailure(maxAttempts = 3, initialDelayMillis = 1) {
                attempts++
                throw FirestoreException(statusCode = 404, code = "NOT_FOUND", message = "gone")
            }
        }
        assertEquals(1, attempts)
    }
}
