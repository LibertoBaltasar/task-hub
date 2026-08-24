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
    // JVM: Google Sign-In not supported — no-op
}

actual suspend fun getGoogleCalendarAccessToken(): String? {
    // JVM: Google Sign-In not supported — no-op
    return null
}