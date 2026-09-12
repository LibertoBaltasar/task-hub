package org.taskhub.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AccountLinkingRules] es la lógica pura que decide el fix de la auditoría
 * de sincronización 2026-09-12: vincular la credencial de Google a la sesión
 * anónima activa (mismo UID, sin hogares huérfanos) en vez de pedir siempre
 * un login normal.
 */
class AccountLinkingRulesTest {

    // ── shouldAttemptLinking ─────────────────────────────────────────────

    @Test
    fun shouldAttemptLinking_sinSesionDeGooglePrevia_intentaVincular() {
        assertTrue(AccountLinkingRules.shouldAttemptLinking(alreadyHasGoogleSession = false))
    }

    @Test
    fun shouldAttemptLinking_conSesionDeGooglePrevia_noIntentaVincular() {
        assertFalse(AccountLinkingRules.shouldAttemptLinking(alreadyHasGoogleSession = true))
    }

    // ── shouldFallBackToPlainSignIn ──────────────────────────────────────

    @Test
    fun shouldFallBackToPlainSignIn_credencialYaVinculadaAOtraCuenta_caeALoginNormal() {
        assertTrue(
            AccountLinkingRules.shouldFallBackToPlainSignIn(
                attemptedLinking = true,
                errorMessage = "FEDERATED_USER_ID_ALREADY_LINKED"
            )
        )
    }

    @Test
    fun shouldFallBackToPlainSignIn_noSeIntentoVincular_noReintenta() {
        // Si no se mandó idToken de vinculación, este mensaje no puede venir
        // de esa causa — no tiene sentido reintentar sin vincular una segunda vez.
        assertFalse(
            AccountLinkingRules.shouldFallBackToPlainSignIn(
                attemptedLinking = false,
                errorMessage = "FEDERATED_USER_ID_ALREADY_LINKED"
            )
        )
    }

    @Test
    fun shouldFallBackToPlainSignIn_otroMotivoDeFallo_noReintentaYPropagaElError() {
        assertFalse(
            AccountLinkingRules.shouldFallBackToPlainSignIn(
                attemptedLinking = true,
                errorMessage = "INVALID_ID_TOKEN"
            )
        )
    }

    @Test
    fun shouldFallBackToPlainSignIn_sinMensaje_noReintenta() {
        assertFalse(
            AccountLinkingRules.shouldFallBackToPlainSignIn(
                attemptedLinking = true,
                errorMessage = null
            )
        )
    }
}
