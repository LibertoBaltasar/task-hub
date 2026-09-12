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
