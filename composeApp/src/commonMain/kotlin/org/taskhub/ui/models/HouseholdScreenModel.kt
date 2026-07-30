package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.HouseholdResponse

sealed class HouseholdUiState {
    data object Idle : HouseholdUiState()
    data object Loading : HouseholdUiState()
    data class Success(val household: HouseholdResponse) : HouseholdUiState()
    data class Error(val message: String) : HouseholdUiState()
}

class HouseholdScreenModel(
    private val repo: FirestoreRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<HouseholdUiState>(HouseholdUiState.Idle)
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    fun createHousehold(name: String) {
        screenModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val household = repo.createHousehold(name)
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
                _uiState.value = HouseholdUiState.Success(household)
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
}
