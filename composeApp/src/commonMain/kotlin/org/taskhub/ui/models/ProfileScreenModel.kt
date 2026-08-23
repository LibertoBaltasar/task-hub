package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.UserProfile

/**
 * Estados de carga del perfil de usuario (propio o ajeno).
 */
sealed class ProfileUiState {
    data object Idle : ProfileUiState()
    data object Loading : ProfileUiState()
    data class Success(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

/**
 * ScreenModel para cargar y actualizar el perfil global de usuario.
 * Maneja tanto el perfil propio (editable) como el de otros (solo lectura).
 */
class ProfileScreenModel(
    private val repo: FirestoreRepository
) : ScreenModel {

    private val _myProfileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val myProfileState: StateFlow<ProfileUiState> = _myProfileState.asStateFlow()

    private val _otherProfileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val otherProfileState: StateFlow<ProfileUiState> = _otherProfileState.asStateFlow()

    private val _saveState = MutableStateFlow<ProfileSaveState>(ProfileSaveState.Idle)
    val saveState: StateFlow<ProfileSaveState> = _saveState.asStateFlow()

    private val _avatarUploadState = MutableStateFlow<AvatarUploadState>(AvatarUploadState.Idle)
    val avatarUploadState: StateFlow<AvatarUploadState> = _avatarUploadState.asStateFlow()

    /** Carga el perfil del usuario actual (para editar). */
    fun loadMyProfile() {
        val userId = repo.getLocalId() ?: run {
            _myProfileState.value = ProfileUiState.Error("No estás autenticado. Inicia sesión primero.")
            return
        }
        screenModelScope.launch {
            _myProfileState.value = ProfileUiState.Loading
            try {
                val profile = repo.getUserProfile(userId)
                if (profile != null) {
                    _myProfileState.value = ProfileUiState.Success(profile)
                } else {
                    // Perfil vacío por defecto — el usuario lo rellenará
                    _myProfileState.value = ProfileUiState.Success(
                        UserProfile(id = userId)
                    )
                }
            } catch (e: Exception) {
                _myProfileState.value = ProfileUiState.Error(
                    e.message ?: "Error al cargar tu perfil"
                )
            }
        }
    }

    /** Carga el perfil de otro usuario (vista pública). */
    fun loadUserProfile(userId: String) {
        if (userId.isBlank()) return
        screenModelScope.launch {
            _otherProfileState.value = ProfileUiState.Loading
            try {
                val profile = repo.getUserProfile(userId)
                if (profile != null) {
                    _otherProfileState.value = ProfileUiState.Success(profile)
                } else {
                    _otherProfileState.value = ProfileUiState.Success(
                        UserProfile(id = userId, displayName = "Usuario")
                    )
                }
            } catch (e: Exception) {
                _otherProfileState.value = ProfileUiState.Error(
                    e.message ?: "Error al cargar el perfil"
                )
            }
        }
    }

    /** Guarda los cambios del perfil propio. */
    fun saveProfile(
        displayName: String,
        bio: String,
        status: String,
        avatarEmoji: String
    ) {
        val userId = repo.getLocalId() ?: run {
            _saveState.value = ProfileSaveState.Error("No estás autenticado")
            return
        }
        // Preserva la avatarUrl (foto de Google) ya cargada: upsertUserProfile
        // escribe null en avatarUrl si no se le pasa explícitamente.
        val currentAvatarUrl = (_myProfileState.value as? ProfileUiState.Success)?.profile?.avatarUrl
        screenModelScope.launch {
            _saveState.value = ProfileSaveState.Saving
            try {
                repo.upsertUserProfile(
                    userId = userId,
                    displayName = displayName,
                    avatarUrl = currentAvatarUrl,
                    avatarEmoji = avatarEmoji,
                    bio = bio,
                    status = status
                )
                _saveState.value = ProfileSaveState.Saved
            } catch (e: Exception) {
                _saveState.value = ProfileSaveState.Error(
                    e.message ?: "Error al guardar el perfil"
                )
            }
        }
    }

    /**
     * Sube una foto de avatar ([jpegBytes], ya comprimida en cliente) a Firebase
     * Storage y persiste la URL resultante en el perfil global de inmediato
     * (no espera al botón "Guardar perfil"), para que no se pierda si el
     * usuario sale de la pantalla sin guardar el resto de campos.
     */
    fun uploadAvatarPhoto(jpegBytes: ByteArray) {
        val userId = repo.getLocalId() ?: run {
            _avatarUploadState.value = AvatarUploadState.Error("No estás autenticado")
            return
        }
        val current = (_myProfileState.value as? ProfileUiState.Success)?.profile
            ?: UserProfile(id = userId)
        screenModelScope.launch {
            _avatarUploadState.value = AvatarUploadState.Uploading
            try {
                val url = repo.uploadAvatarPhoto(userId, jpegBytes)
                repo.upsertUserProfile(
                    userId = userId,
                    displayName = current.displayName,
                    avatarUrl = url,
                    avatarEmoji = current.avatarEmoji,
                    bio = current.bio,
                    status = current.status
                )
                _myProfileState.value = ProfileUiState.Success(current.copy(avatarUrl = url))
                _avatarUploadState.value = AvatarUploadState.Idle
            } catch (e: Exception) {
                _avatarUploadState.value = AvatarUploadState.Error(
                    e.message ?: "Error al subir la foto"
                )
            }
        }
    }

    fun clearAvatarUploadError() {
        _avatarUploadState.value = AvatarUploadState.Idle
    }

    fun reset() {
        _myProfileState.value = ProfileUiState.Idle
        _otherProfileState.value = ProfileUiState.Idle
        _saveState.value = ProfileSaveState.Idle
        _avatarUploadState.value = AvatarUploadState.Idle
    }

    fun clearSaveState() {
        _saveState.value = ProfileSaveState.Idle
    }
}

/** Estado de la subida de la foto de avatar. */
sealed class AvatarUploadState {
    data object Idle : AvatarUploadState()
    data object Uploading : AvatarUploadState()
    data class Error(val message: String) : AvatarUploadState()
}

/** Estado del guardado del perfil. */
sealed class ProfileSaveState {
    data object Idle : ProfileSaveState()
    data object Saving : ProfileSaveState()
    data object Saved : ProfileSaveState()
    data class Error(val message: String) : ProfileSaveState()
}