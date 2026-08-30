package org.taskhub.platform

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