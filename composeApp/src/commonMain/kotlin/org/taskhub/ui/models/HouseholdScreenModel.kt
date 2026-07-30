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
    private val householdStore: HouseholdStore
) : ScreenModel {

    private val _uiState = MutableStateFlow<HouseholdUiState>(HouseholdUiState.Idle)
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    fun createHousehold(name: String) {
        screenModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val household = repo.createHousehold(name)
                householdStore.saveHousehold(household.id, household.name, household.inviteCode)
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

                // Check if current user is already a member
                val localId = repo.getLocalId()
                if (localId != null && repo.isMember(household.id, localId)) {
                    householdStore.saveHousehold(household.id, household.name, household.inviteCode)
                    _uiState.value = HouseholdUiState.AlreadyMember(household)
                } else {
                    householdStore.saveHousehold(household.id, household.name, household.inviteCode)
                    _uiState.value = HouseholdUiState.Success(household)
                }
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

    fun getLocalId(): String? = repo.getLocalId()
}
