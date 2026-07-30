package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.auth.GoogleSignInHelper

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class Success(val token: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

/**
 * ScreenModel for the LoginScreen.
 * Delegates to the platform-specific [GoogleSignInHelper]
 * to perform Google Sign-In and obtain a Firebase ID token.
 */
class LoginScreenModel(
    private val signInHelper: GoogleSignInHelper
) : ScreenModel {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signInWithGoogle() {
        if (_uiState.value is LoginUiState.Loading) return

        screenModelScope.launch {
            _uiState.value = LoginUiState.Loading

            val result = signInHelper.signInWithGoogle()
            result.fold(
                onSuccess = { token ->
                    _uiState.value = LoginUiState.Success(token)
                },
                onFailure = { error ->
                    _uiState.value = LoginUiState.Error(
                        error.message ?: "Error al iniciar sesión"
                    )
                }
            )
        }
    }

    fun reset() {
        _uiState.value = LoginUiState.Idle
    }
}