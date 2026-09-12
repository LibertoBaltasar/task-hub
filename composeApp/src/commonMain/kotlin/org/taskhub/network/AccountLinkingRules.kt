/**
 * Lógica pura (sin I/O) de [FirestoreRepository.signInWithGoogle]: decide si
 * hay que intentar vincular la credencial de Google a la sesión anónima
 * activa, y si un fallo de esa vinculación debe reintentarse como login
 * normal (sin vincular). Extraída para poder testear la decisión sin montar
 * un cliente HTTP — ver `docs/auditoria-sync-dispositivos-2026-09-12.md`
 * (causa raíz: sin vincular, un login con Google desde modo anónimo siempre
 * pedía un UID nuevo, dejando huérfanos los hogares compartidos creados con
 * el UID anónimo).
 */
package org.taskhub.network

object AccountLinkingRules {
    /**
     * Mensaje de error literal que Identity Toolkit devuelve cuando se
     * intenta vincular (`idToken` en `accounts:signInWithIdp`) una credencial
     * de Google que ya pertenece a OTRA cuenta permanente distinta de la
     * sesión anónima activa.
     */
    const val FEDERATED_USER_ID_ALREADY_LINKED = "FEDERATED_USER_ID_ALREADY_LINKED"

    /**
     * Solo tiene sentido intentar vincular si el usuario AÚN no tiene sesión
     * de Google — es el caso real que deja hogares huérfanos (login desde
     * modo anónimo). Si ya había sesión de Google (p.ej.
     * [org.taskhub.ui.models.GoogleAuthManager.reauthenticateForDeletion]),
     * no hay una identidad anónima "activa" que fusionar:
     * [FirestoreClient.ensureAuth] ni siquiera devolvería su token (prioriza
     * el de Google).
     */
    fun shouldAttemptLinking(alreadyHasGoogleSession: Boolean): Boolean = !alreadyHasGoogleSession

    /**
     * True si el fallo de un intento de vinculación debe reintentarse como
     * login normal (sin idToken de vinculación) — solo cuando SÍ se había
     * intentado vincular ([attemptedLinking]) y el motivo del fallo es
     * específicamente que la cuenta de Google ya está vinculada a otro UID
     * permanente ([FEDERATED_USER_ID_ALREADY_LINKED]). Cualquier otro fallo
     * (red, token anónimo inválido/caducado, etc.) debe relanzarse tal cual,
     * no enmascararse con un reintento silencioso.
     */
    fun shouldFallBackToPlainSignIn(attemptedLinking: Boolean, errorMessage: String?): Boolean =
        attemptedLinking && errorMessage?.contains(FEDERATED_USER_ID_ALREADY_LINKED) == true
}
