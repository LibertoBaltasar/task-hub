/**
 * `actual` web (wasmJs) de las declaraciones sueltas de [Platform.kt].
 * El navegador no tiene hoja de compartir nativa fiable ni widget de home
 * screen, así que `shareText` queda como no-op (log a consola) y el widget
 * no-op; Google Sign-In queda como no-op configurable (ver
 * `docs/` — pendiente para un encargo posterior); `secureRandomInt` usa el
 * CSPRNG del navegador (`crypto.getRandomValues`), igual que el resto de
 * plataformas.
 */
package org.taskhub.platform

/**
 * Web: no hay Web Share API cableada todavía (queda como no-op con log a
 * consola). [title] no se usa.
 */
actual fun shareText(text: String, title: String) {
    println("shareText not implemented on web: $title")
}

/** Web: no hay widget de home screen. */
actual val hasHomeScreenWidget: Boolean = false

/** Web: getGoogleCalendarAccessToken() está hardcodeado a null (ver abajo). */
actual val hasCalendarSupport: Boolean = false

/** Web: createNotificationScheduler() devuelve NoOpNotificationScheduler. */
actual val hasNotificationSupport: Boolean = false

/** Web: no hay widget de home screen — no-op. */
actual fun saveWidgetThemeToCache(theme: String) {
    // Web: no widget — no-op
}

/** Web: no hay widget de home screen — no-op. */
actual fun updateWidgetPendingTasks(taskList: String) {
    // Web: no widget — no-op
}

/**
 * Web: Google Sign-In no está implementado todavía en este build (ver hueco
 * documentado). Señaliza "sin token" de inmediato para no dejar
 * `GoogleAuthManager` colgado en `SigningIn` (mismo contrato que el no-op de
 * iOS en `Platform.ios.kt`).
 */
actual fun launchGoogleSignIn() {
    GoogleSignInResultHolder.setResult("")
}

/** Web: Google Sign-In no soportado todavía — siempre devuelve null (sin token). */
actual suspend fun getGoogleCalendarAccessToken(): String? {
    return null
}

/** Web: Google Sign-In no soportado todavía — no hay nada que revocar, no-op. */
actual suspend fun revokeGoogleCalendarAccess() {
    // Web: Google Sign-In not supported yet — no-op
}

/**
 * Índice aleatorio en [0, bound) usando `crypto.getRandomValues` del
 * navegador (Web Crypto API, disponible en todos los navegadores modernos
 * incluidos los que ejecutan Wasm) — antes usaba `kotlin.random.Random`, que
 * NO es un CSPRNG, para `inviteCode` (`HouseholdRepository.generateInviteCode`),
 * la única barrera para unirse a un hogar ajeno sin invitación explícita.
 * Sesgo de módulo despreciable para los `bound` pequeños usados hoy (36, el
 * tamaño del alfabeto de `inviteCode`).
 */
@JsFun("(bound) => { const arr = new Uint32Array(1); crypto.getRandomValues(arr); return Math.floor((arr[0] / 4294967296) * bound); }")
private external fun jsSecureRandomInt(bound: Int): Int

actual fun secureRandomInt(bound: Int): Int = jsSecureRandomInt(bound)
