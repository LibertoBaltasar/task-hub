package org.taskhub.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-platform holder for Google Sign-In results.
 *
 * After [launchGoogleSignIn] completes (Android) or no-ops (other platforms),
 * the result is delivered here so that commonMain code can observe it.
 */
object GoogleSignInResultHolder {
    /** Current result: null = ongoing / not started, empty = no-op, token = success. */
    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    fun setResult(token: String?) {
        _result.value = token
    }

    fun reset() {
        _result.value = null
    }
}