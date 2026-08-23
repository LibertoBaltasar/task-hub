package org.taskhub.platform

import org.taskhub.ImagePickerHelper

/** Delega en [ImagePickerHelper], registrado desde `MainActivity.onCreate`. */
private object AndroidImagePicker : ImagePicker {
    override fun pickFromGallery() = ImagePickerHelper.pickFromGallery()
    override fun takePhoto() = ImagePickerHelper.takePhoto()
}

actual fun createImagePicker(): ImagePicker = AndroidImagePicker
