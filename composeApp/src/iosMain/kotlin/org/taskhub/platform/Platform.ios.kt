package org.taskhub.platform

actual fun shareText(text: String, title: String) {
    // TODO: iOS implementation using UIActivityViewController
    println("shareText not implemented on iOS: $title")
}