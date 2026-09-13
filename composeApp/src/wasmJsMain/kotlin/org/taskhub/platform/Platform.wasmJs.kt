/**
 * `actual` web (wasmJs) de las declaraciones sueltas de [Platform.kt].
 * El navegador no tiene hoja de compartir nativa fiable ni widget de home
 * screen, así que `shareText` queda como no-op (log a consola) y el widget
 * no-op; Google Sign-In queda como no-op configurable (ver
 * `docs/` — pendiente para un encargo posterior); `secureRandomInt` usa
 * `kotlin.random.Random` como placeholder (NO es un CSPRNG real todavía,
 * ver hueco documentado en el resumen del encargo web).
 */
package org.taskhub.platform

import kotlin.random.Random

/**
 * Web: no hay Web Share API cableada todavía (queda como no-op con log a
 * consola). [title] no se usa.
 */
actual fun shareText(text: String, title: String) {
    println("shareText not implemented on web: $title")
}

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
 * Placeholder: `kotlin.random.Random` NO es un CSPRNG. Pendiente cablear
 * `crypto.getRandomValues` del navegador (hueco documentado en el resumen
 * del encargo web) antes de confiar en este valor para códigos de invitación
 * en producción web.
 */
actual fun secureRandomInt(bound: Int): Int = Random.nextInt(bound)
