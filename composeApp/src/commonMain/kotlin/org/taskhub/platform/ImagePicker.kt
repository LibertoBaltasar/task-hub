package org.taskhub.platform

/**
 * Selector de imagen multiplataforma para elegir la foto de avatar del
 * usuario, desde galería o cámara. El resultado (JPEG ya redimensionado y
 * comprimido) se entrega de forma asíncrona vía [ImagePickerResultHolder],
 * igual que el flujo de Google Sign-In ([GoogleSignInResultHolder]).
 *
 * Implementación real solo en Android; iOS y JVM/desktop usan un no-op que
 * no interrumpe el flujo (no hay picker nativo implementado todavía ahí).
 */
interface ImagePicker {
    /** Abre el selector de galería (solo imágenes). */
    fun pickFromGallery()

    /** Abre la cámara del sistema para tomar una foto nueva. */
    fun takePhoto()
}

/** Crea el selector de imágenes de la plataforma actual. */
expect fun createImagePicker(): ImagePicker
