/**
 * `actual` JVM (desktop) de las declaraciones sueltas de [Platform.kt]. La
 * mayoría son no-op o placeholders (compartir, widget, Google Sign-In no
 * están implementados en desktop); `secureRandomInt` sí usa un CSPRNG real
 * (`java.security.SecureRandom`).
 */
package org.taskhub.platform

import java.security.SecureRandom

/** Aún no implementado en JVM/Desktop: solo deja constancia por consola. */
actual fun shareText(text: String, title: String) {
    // TODO: JVM/Desktop implementation
    println("shareText not implemented on JVM: $title")
}

/** JVM: no hay widget de escritorio — no-op. */
actual fun saveWidgetThemeToCache(theme: String) {
    // JVM: no widget — no-op
}

/** JVM: no hay widget de escritorio — no-op. */
actual fun updateWidgetPendingTasks(taskList: String) {
    // JVM: no widget — no-op
}

actual fun launchGoogleSignIn() {
    // JVM/Desktop: Google Sign-In no está implementado todavía. Sin esto,
    // GoogleAuthManager.signIn()/linkCalendar() se quedaban colgados para
    // siempre en SigningIn (GoogleSignInResultHolder.result nunca volvía a
    // emitir). Señalizar "sin token" desbloquea el flujo (vuelve a Anonymous)
    // en vez de colgarlo.
    GoogleSignInResultHolder.setResult("")
}

/** JVM: Google Sign-In no soportado — siempre devuelve null (sin token). */
actual suspend fun getGoogleCalendarAccessToken(): String? {
    // JVM: Google Sign-In not supported — no-op
    return null
}

/** JVM: Google Sign-In no soportado — no hay nada que revocar, no-op. */
actual suspend fun revokeGoogleCalendarAccess() {
    // JVM: Google Sign-In not supported — no-op
}

private val secureRandom = SecureRandom()

/** Implementación JVM: delega en `java.security.SecureRandom`, un CSPRNG del JDK. */
actual fun secureRandomInt(bound: Int): Int = secureRandom.nextInt(bound)