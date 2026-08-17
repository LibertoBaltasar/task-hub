package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.RewardResponse
import org.taskhub.network.models.RewardRedemption

sealed class MemberUiState {
    data object Idle : MemberUiState()
    data object Loading : MemberUiState()
    data class Success(val members: List<MemberResponse>) : MemberUiState()
    data class Error(val message: String) : MemberUiState()
}

sealed class RewardUiState {
    data object Idle : RewardUiState()
    data object Loading : RewardUiState()
    data class Success(val rewards: List<RewardResponse>) : RewardUiState()
    data class Error(val message: String) : RewardUiState()
}

sealed class RewardActionState {
    data object Idle : RewardActionState()
    data object Loading : RewardActionState()
    data class Success(val redemption: RewardRedemption? = null) : RewardActionState()
    data class Error(val message: String) : RewardActionState()
}

class MemberScreenModel(
    private val repo: FirestoreRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<MemberUiState>(MemberUiState.Idle)
    val uiState: StateFlow<MemberUiState> = _uiState.asStateFlow()

    private val _lastCreatedMember = MutableStateFlow<MemberResponse?>(null)
    val lastCreatedMember: StateFlow<MemberResponse?> = _lastCreatedMember.asStateFlow()

    private val _rewardState = MutableStateFlow<RewardUiState>(RewardUiState.Idle)
    val rewardState: StateFlow<RewardUiState> = _rewardState.asStateFlow()

    private val _rewardActionState = MutableStateFlow<RewardActionState>(RewardActionState.Idle)
    val rewardActionState: StateFlow<RewardActionState> = _rewardActionState.asStateFlow()

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

    fun addMember(householdId: String, displayName: String, role: String, userId: String? = null, inviteCode: String? = null) {
        screenModelScope.launch {
            _uiState.value = MemberUiState.Loading
            try {
                val member = repo.createMember(householdId, displayName, role, userId = userId, inviteCode = inviteCode)
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

    /**
     * Edita el rol de un miembro ("admin" | "child") sin recrearlo.
     * Recarga la lista al terminar.
     */
    fun updateMemberRole(householdId: String, memberId: String, role: String) {
        screenModelScope.launch {
            try {
                repo.updateMemberRole(householdId, memberId, role)
                val members = repo.getMembers(householdId)
                _uiState.value = MemberUiState.Success(members)
            } catch (e: Exception) {
                _uiState.value = MemberUiState.Error(
                    e.message ?: "Error al cambiar el rol"
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

    // ── Rewards ──────────────────────────────────────────

    fun loadRewards(householdId: String) {
        screenModelScope.launch {
            _rewardState.value = RewardUiState.Loading
            try {
                val rewards = repo.getRewards(householdId)
                _rewardState.value = RewardUiState.Success(rewards)
            } catch (e: Exception) {
                _rewardState.value = RewardUiState.Error(
                    e.message ?: "Error al cargar recompensas"
                )
            }
        }
    }

    fun createReward(
        householdId: String,
        title: String,
        description: String,
        cost: Int,
        icon: String,
        createdBy: String
    ) {
        screenModelScope.launch {
            _rewardActionState.value = RewardActionState.Loading
            try {
                repo.createReward(householdId, title, description, cost, icon, createdBy)
                _rewardActionState.value = RewardActionState.Success()
                // Reload
                loadRewards(householdId)
            } catch (e: Exception) {
                _rewardActionState.value = RewardActionState.Error(
                    e.message ?: "Error al crear recompensa"
                )
            }
        }
    }

    fun deleteReward(householdId: String, rewardId: String) {
        screenModelScope.launch {
            try {
                repo.deleteReward(householdId, rewardId)
                loadRewards(householdId)
            } catch (e: Exception) {
                _rewardActionState.value = RewardActionState.Error(
                    e.message ?: "Error al eliminar recompensa"
                )
            }
        }
    }

    fun redeemReward(
        householdId: String,
        rewardId: String,
        memberId: String,
        pointsSpent: Int
    ) {
        screenModelScope.launch {
            _rewardActionState.value = RewardActionState.Loading
            try {
                val redemption = repo.redeemReward(householdId, rewardId, memberId, pointsSpent)
                _rewardActionState.value = RewardActionState.Success(redemption)
                // Reload members to refresh points
                loadMembers(householdId)
            } catch (e: Exception) {
                _rewardActionState.value = RewardActionState.Error(
                    e.message ?: "Error al canjear recompensa"
                )
            }
        }
    }

    fun clearRewardAction() {
        _rewardActionState.value = RewardActionState.Idle
    }
}
