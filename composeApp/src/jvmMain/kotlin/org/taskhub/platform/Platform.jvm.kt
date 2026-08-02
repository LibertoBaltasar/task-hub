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