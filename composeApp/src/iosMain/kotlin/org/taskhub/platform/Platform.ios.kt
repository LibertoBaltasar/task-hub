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
    // iOS: Google Sign-In not supported — no-op
}

actual suspend fun getGoogleCalendarAccessToken(): String? {
    // iOS: Google Sign-In not supported — no-op
    return null
}