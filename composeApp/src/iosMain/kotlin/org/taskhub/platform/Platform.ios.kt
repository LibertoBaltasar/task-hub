package org.taskhub.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

actual fun shareText(text: String, title: String) {
    // TODO: iOS implementation using UIActivityViewController
    println("shareText not implemented on iOS: $title")
}

actual fun saveWidgetThemeToCache(theme: String) {
    // iOS: no widget cache yet — no-op for now
}

actual fun updateWidgetPendingTasks(taskList: String) {
    // iOS: no widget yet — no-op
}

actual fun launchGoogleSignIn() {
    // iOS: Google Sign-In no está implementado todavía. Sin esto,
    // GoogleAuthManager.signIn()/linkCalendar() se quedaban colgados para
    // siempre en SigningIn (GoogleSignInResultHolder.result nunca volvía a
    // emitir), incluido cada vez que el usuario pulsaba "Vincular Google
    // Calendar" desde Ajustes o el detalle de una tarea. Señalizar "sin
    // token" desbloquea el flujo (vuelve a Anonymous) en vez de colgarlo.
    GoogleSignInResultHolder.setResult("")
}

actual suspend fun getGoogleCalendarAccessToken(): String? {
    // iOS: Google Sign-In not supported — no-op
    return null
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