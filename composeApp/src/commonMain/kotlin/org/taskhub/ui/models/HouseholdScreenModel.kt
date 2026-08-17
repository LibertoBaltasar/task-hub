package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.storage.HouseholdStore

sealed class HouseholdUiState {
    data object Idle : HouseholdUiState()
    data object Loading : HouseholdUiState()
    data class Success(val household: HouseholdResponse) : HouseholdUiState()
    data class AlreadyMember(val household: HouseholdResponse) : HouseholdUiState()
    data class Error(val message: String) : HouseholdUiState()
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
                _uiState.value = HouseholdUiState.Success(household)
            } catch (e: Exception) {
                _uiState.value = HouseholdUiState.Error(
                    e.message ?: "Error al crear el hogar"
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
                    e.message ?: "Error al cargar el hogar"
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
                onError(e.message ?: "Error al eliminar el hogar")
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
                onError(e.message ?: "Error al eliminar los hogares")
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
                onError(e.message ?: "Error al salir del hogar")
            }
        }
    }

    fun getLocalId(): String? = repo.getLocalId()
}
