package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.MemberResponse

sealed class MemberUiState {
    data object Idle : MemberUiState()
    data object Loading : MemberUiState()
    data class Success(val members: List<MemberResponse>) : MemberUiState()
    data class Error(val message: String) : MemberUiState()
}

class MemberScreenModel(
    private val repo: FirestoreRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<MemberUiState>(MemberUiState.Idle)
    val uiState: StateFlow<MemberUiState> = _uiState.asStateFlow()

    private val _lastCreatedMember = MutableStateFlow<MemberResponse?>(null)
    val lastCreatedMember: StateFlow<MemberResponse?> = _lastCreatedMember.asStateFlow()

    fun loadMembers(householdId: String) {
        screenModelScope.launch {
            _uiState.value = MemberUiState.Loading
            try {
                val members = repo.getMembers(householdId)
                _uiState.value = MemberUiState.Success(members)
            } catch (e: Exception) {
                _uiState.value = MemberUiState.Error(
                    e.message ?: "Error al cargar miembros"
                )
            }
        }
    }

    fun addMember(householdId: String, displayName: String, role: String, userId: String? = null) {
        screenModelScope.launch {
            _uiState.value = MemberUiState.Loading
            try {
                val member = repo.createMember(householdId, displayName, role, userId = userId)
                _lastCreatedMember.value = member
                // Reload the full member list
                val members = repo.getMembers(householdId)
                _uiState.value = MemberUiState.Success(members)
            } catch (e: Exception) {
                _uiState.value = MemberUiState.Error(
                    e.message ?: "Error al añadir miembro"
                )
            }
        }
    }

    fun removeMember(householdId: String, memberId: String) {
        screenModelScope.launch {
            try {
                repo.deleteMember(householdId, memberId)
                val members = repo.getMembers(householdId)
                _uiState.value = MemberUiState.Success(members)
            } catch (e: Exception) {
                _uiState.value = MemberUiState.Error(
                    e.message ?: "Error al eliminar miembro"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = MemberUiState.Idle
        _lastCreatedMember.value = null
    }

    fun clearLastCreated() {
        _lastCreatedMember.value = null
    }
}
