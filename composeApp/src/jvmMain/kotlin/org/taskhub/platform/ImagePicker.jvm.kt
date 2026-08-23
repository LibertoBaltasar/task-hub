package org.taskhub.platform

/** Sin picker nativo implementado en JVM/desktop todavía: no-op para no romper el build. */
private object NoOpImagePicker : ImagePicker {
    override fun pickFromGallery() {}
    override fun takePhoto() {}
}

actual fun createImagePicker(): ImagePicker = NoOpImagePicker
