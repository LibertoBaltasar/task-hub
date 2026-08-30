package org.taskhub.platform

actual fun shareText(text: String, title: String) {
    // TODO: JVM/Desktop implementation
    println("shareText not implemented on JVM: $title")
}

actual fun saveWidgetThemeToCache(theme: String) {
    // JVM: no widget — no-op
}

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

actual suspend fun getGoogleCalendarAccessToken(): String? {
    // JVM: Google Sign-In not supported — no-op
    return null
}