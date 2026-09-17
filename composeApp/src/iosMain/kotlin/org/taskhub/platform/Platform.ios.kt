package org.taskhub.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/**
 * Implementación iOS del `expect` [shareText] (`platform/Platform.kt`).
 *
 * Pendiente de implementar con `UIActivityViewController` (el share sheet
 * nativo de iOS/UIKit) — de momento solo deja constancia por consola.
 */
actual fun shareText(text: String, title: String) {
    // TODO: iOS implementation using UIActivityViewController
    println("shareText not implemented on iOS: $title")
}

/** Todavía no existe un widget de iOS (WidgetKit). */
actual val hasHomeScreenWidget: Boolean = false

/** iOS: Google Sign-In no está implementado — nunca hay token de Calendar. */
actual val hasCalendarSupport: Boolean = false

/**
 * Implementación iOS del `expect` [saveWidgetThemeToCache] (`platform/Platform.kt`).
 *
 * No-op: todavía no existe un widget de iOS (WidgetKit) que consuma este tema.
 */
actual fun saveWidgetThemeToCache(theme: String) {
    // iOS: no widget cache yet — no-op for now
}

/**
 * Implementación iOS del `expect` [updateWidgetPendingTasks] (`platform/Platform.kt`).
 *
 * No-op: todavía no existe un widget de iOS (WidgetKit) que consuma esta lista.
 */
actual fun updateWidgetPendingTasks(taskList: String) {
    // iOS: no widget yet — no-op
}

actual fun launchGoogleSignIn() {
    // iOS: Google Sign-In no está implementado todavía. Sin esto,
    // GoogleAuthManager.signIn()/linkCalendar() se quedaban colgados para
    // siempre en SigningIn (GoogleSignInResultHolder.result nunca volvía a
    // emitir), incluido cada vez que el usuario pulsaba "Vincular Google
    // Calendar" desde Ajustes o el detalle de una tarea. Señalizar "sin
    // token" desbloquea el flujo (vuelve a SignedOut) en vez de colgarlo.
    //
    // IMPORTANTE (Google-only, docs/google-only-auth-2026-09-12.md): al no
    // haber ya ningún modo anónimo de respaldo, esto deja el build iOS sin
    // forma de pasar el gate de login de `App.kt` — se queda
    // permanentemente en AuthGateScreen. Pendiente de decisión de producto.
    GoogleSignInResultHolder.setResult("")
}

/**
 * Implementación iOS del `expect` [getGoogleCalendarAccessToken] (`platform/Platform.kt`).
 *
 * No-op: Google Sign-In no está implementado en iOS todavía (ver
 * [launchGoogleSignIn] arriba), así que nunca hay una cuenta vinculada de la
 * que obtener un token de Calendar. Siempre devuelve null.
 */
actual suspend fun getGoogleCalendarAccessToken(): String? {
    // iOS: Google Sign-In not supported — no-op
    return null
}

/**
 * Implementación iOS del `expect` [revokeGoogleCalendarAccess] (`platform/Platform.kt`).
 *
 * No-op por el mismo motivo que [getGoogleCalendarAccessToken]: sin Google
 * Sign-In en iOS no hay consentimiento OAuth que revocar.
 */
actual suspend fun revokeGoogleCalendarAccess() {
    // iOS: Google Sign-In not supported — no-op
}

/**
 * Genera un entero uniforme en [0, bound) usando el CSPRNG del sistema
 * (Security.framework), con rejection sampling (mismo algoritmo que
 * `java.util.Random.nextInt(bound)`/Android `SecureRandom.nextInt(bound)`).
 *
 * La implementación anterior pedía un solo byte y aplicaba `% bound`: con
 * bound=36 (alfabeto del código de invitación) eso sesgaba ~14% a favor de
 * los primeros 4 caracteres del alfabeto (256 % 36 = 4), y para bound > 256
 * jamás podía devolver valores >= 256. El rejection sampling sobre 31 bits
 * evita ambos problemas.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomInt(bound: Int): Int {
    require(bound > 0) { "bound debe ser positivo" }
    val bytes = ByteArray(4)
    var bits: Int
    var value: Int
    do {
        val status = SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), bytes.refTo(0))
        check(status == 0) { "SecRandomCopyBytes falló con status $status" }
        bits = (((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)) and 0x7fffffff
        value = bits % bound
    } while (bits - value + (bound - 1) < 0)
    return value
}