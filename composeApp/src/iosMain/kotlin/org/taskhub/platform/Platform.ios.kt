package org.taskhub.platform

actual fun shareText(text: String, title: String) {
    // TODO: iOS implementation using UIActivityViewController
    println("shareText not implemented on iOS: $title")
}

actual fun saveWidgetThemeToCache(theme: String) {
    // iOS: no widget cache yet — no-op for now
}