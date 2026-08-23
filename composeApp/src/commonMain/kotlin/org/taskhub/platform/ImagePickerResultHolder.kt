package org.taskhub.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-platform holder for the result of [ImagePicker] (gallery/camera).
 *
 * Mirrors [GoogleSignInResultHolder]: null = ongoing (no result yet), an
 * empty array = cancelled or failed, a non-empty array = a JPEG image ready
 * to upload. The empty-array sentinel (instead of re-emitting null) matches
 * the same StateFlow gotcha documented there — null→null is not a change and
 * would never notify collectors of a cancellation.
 */
object ImagePickerResultHolder {
    private val _result = MutableStateFlow<ByteArray?>(null)
    val result: StateFlow<ByteArray?> = _result.asStateFlow()

    fun setResult(bytes: ByteArray?) {
        _result.value = bytes ?: ByteArray(0)
    }

    fun reset() {
        _result.value = null
    }
}
