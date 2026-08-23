package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MessageResponse
import org.taskhub.storage.HouseholdStore
import org.taskhub.platform.logAnalyticsEvent

sealed class HouseholdUiState {
    data object Idle : HouseholdUiState()
    data object Loading : HouseholdUiState()
    data class Success(val household: HouseholdResponse) : HouseholdUiState()
    data class AlreadyMember(val household: HouseholdResponse) : HouseholdUiState()
    data class Error(val message: String) : HouseholdUiState()
}

sealed class MessagesUiState {
    data object Idle : MessagesUiState()
    data object Loading : MessagesUiState()
    data class Success(val messages: List<MessageResponse>) : MessagesUiState()
    data class Error(val message: String) : MessagesUiState()
}

class HouseholdScreenModel(
    private val repo: FirestoreRepository,
    private val householdStore: HouseholdStore,
    private val authManager: GoogleAuthManager
) : ScreenModel {

    private val _uiState = MutableStateFlow<HouseholdUiState>(HouseholdUiState.Idle)
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    fun createHousehold(name: String) {
        screenModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val household = repo.createHousehold(name)
                householdStore.saveHousehold(household.id, household.name, household.inviteCode)
                authManager.syncHouseholdsToCloud()
                logAnalyticsEvent("household_created")
                _uiState.value = HouseholdUiState.Success(household)
            } catch (e: Exception) {
                _uiState.value = HouseholdUiState.Error(
                    e.message ?: "Error al crear el espacio"
                )
            }
        }
    }

    fun joinHousehold(inviteCode: String) {
        screenModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val household = repo.joinHousehold(inviteCode)

                householdStore.saveHousehold(household.id, household.name, household.inviteCode)

                // Si ya somos miembros (con cualquiera de nuestras identidades),
                // no volvemos a crear perfil: navegamos directo al hogar.
                if (repo.isCurrentUserMember(household.id)) {
                    _uiState.value = HouseholdUiState.AlreadyMember(household)
                } else {
                    logAnalyticsEvent("household_joined")
                    _uiState.value = HouseholdUiState.Success(household)
                }
                authManager.syncHouseholdsToCloud()
            } catch (e: Exception) {
                _uiState.value = HouseholdUiState.Error(
                    e.message ?: "Código de invitación inválido"
                )
            }
        }
    }

    fun loadHousehold(id: String) {
        screenModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val household = repo.getHousehold(id)
                _uiState.value = HouseholdUiState.Success(household)
            } catch (e: Exception) {
                _uiState.value = HouseholdUiState.Error(
                    e.message ?: "Error al cargar el espacio"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = HouseholdUiState.Idle
    }

    fun deleteHousehold(
        householdId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        screenModelScope.launch {
            try {
                repo.deleteHousehold(householdId)
                householdStore.removeHousehold(householdId)
                authManager.syncHouseholdsToCloud()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error al eliminar el espacio")
            }
        }
    }

    fun deleteMultipleHouseholds(
        householdIds: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        screenModelScope.launch {
            try {
                for (id in householdIds) {
                    repo.deleteHousehold(id)
                    householdStore.removeHousehold(id)
                }
                authManager.syncHouseholdsToCloud()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error al eliminar los espacios")
            }
        }
    }

    fun leaveHousehold(
        householdId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        screenModelScope.launch {
            try {
                repo.leaveHousehold(householdId, authManager.currentUserId())
                householdStore.removeHousehold(householdId)
                authManager.syncHouseholdsToCloud()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error al salir del espacio")
            }
        }
    }

    fun getLocalId(): String? = repo.getLocalId()

    // ── Chat de mensajes ──

    private val _messagesUiState = MutableStateFlow<MessagesUiState>(MessagesUiState.Idle)
    val messagesUiState: StateFlow<MessagesUiState> = _messagesUiState.asStateFlow()

    private val _newMessageText = MutableStateFlow("")
    val newMessageText: StateFlow<String> = _newMessageText.asStateFlow()

    fun updateNewMessageText(text: String) {
        _newMessageText.value = text
    }

    fun loadMessages(householdId: String) {
        screenModelScope.launch {
            if (_messagesUiState.value !is MessagesUiState.Success) {
                _messagesUiState.value = MessagesUiState.Loading
            }
            try {
                val messages = repo.getMessages(householdId)
                _messagesUiState.value = MessagesUiState.Success(messages)
            } catch (e: Exception) {
                _messagesUiState.value = MessagesUiState.Error(
                    e.message ?: "Error al cargar mensajes"
                )
            }
        }
    }

    fun sendMessage(householdId: String, memberId: String) {
        val text = _newMessageText.value.trim()
        if (text.isEmpty() || memberId.isEmpty()) return
        screenModelScope.launch {
            try {
                val authorName = repo.getMembers(householdId)
                    .firstOrNull { it.id == memberId }
                    ?.displayName ?: ""
                repo.sendMessage(householdId, memberId, authorName, text)
                _newMessageText.value = ""
                loadMessages(householdId)
            } catch (e: Exception) {
                _messagesUiState.value = MessagesUiState.Error(
                    e.message ?: "Error al enviar mensaje"
                )
            }
        }
    }
}
