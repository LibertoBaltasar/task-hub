package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FIRESTORE_GONE_MESSAGE
import org.taskhub.network.FirestoreException
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.isGoneOrForbidden
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.RewardResponse
import org.taskhub.network.models.RewardRedemption
import org.taskhub.platform.HapticKind
import org.taskhub.platform.vibrate
import org.taskhub.storage.SettingsStore

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

/**
 * Resultado de mutaciones sobre un miembro (cambiar rol, eliminar) — separado
 * de [MemberUiState] igual que [RewardActionState] lo está de [RewardUiState].
 * Antes, un fallo en `updateMemberRole`/`removeMember` escribía directamente
 * en `_uiState` (que contenía la lista de miembros en `Success`), así que el
 * error sustituía la lista visible entera en vez de solo mostrarse como aviso
 * puntual — un fallo de red al cambiar un rol borraba de la pantalla a todos
 * los demás miembros.
 */
sealed class MemberActionState {
    data object Idle : MemberActionState()
    data object Loading : MemberActionState()
    data object Success : MemberActionState()
    data class Error(val message: String) : MemberActionState()
}

sealed class AppreciateActionState {
    data object Idle : AppreciateActionState()
    data object Loading : AppreciateActionState()
    data class Success(val remaining: Int) : AppreciateActionState()
    /** [messageKey] es una clave de [org.taskhub.ui.i18n.AppStrings], no un mensaje ya traducido. */
    data class Error(val messageKey: String) : AppreciateActionState()
}

sealed class DonateActionState {
    data object Idle : DonateActionState()
    data object Loading : DonateActionState()
    data class Success(val donorNewTotal: Int) : DonateActionState()
    /** [messageKey] es una clave de [org.taskhub.ui.i18n.AppStrings], no un mensaje ya traducido. */
    data class Error(val messageKey: String) : DonateActionState()
}

class MemberScreenModel(
    private val repo: FirestoreRepository,
    private val settingsStore: SettingsStore
) : ScreenModel {

    private fun buzz(kind: HapticKind) {
        if (settingsStore.isVibrationEnabled()) vibrate(kind)
    }

    private val _uiState = MutableStateFlow<MemberUiState>(MemberUiState.Idle)
    val uiState: StateFlow<MemberUiState> = _uiState.asStateFlow()

    private val _lastCreatedMember = MutableStateFlow<MemberResponse?>(null)
    val lastCreatedMember: StateFlow<MemberResponse?> = _lastCreatedMember.asStateFlow()

    private val _memberActionState = MutableStateFlow<MemberActionState>(MemberActionState.Idle)
    val memberActionState: StateFlow<MemberActionState> = _memberActionState.asStateFlow()

    private val _rewardState = MutableStateFlow<RewardUiState>(RewardUiState.Idle)
    val rewardState: StateFlow<RewardUiState> = _rewardState.asStateFlow()

    private val _rewardActionState = MutableStateFlow<RewardActionState>(RewardActionState.Idle)
    val rewardActionState: StateFlow<RewardActionState> = _rewardActionState.asStateFlow()

    /** Ver [FirestoreRepository.isHouseholdOwner]. */
    suspend fun isHouseholdOwner(householdId: String): Boolean = repo.isHouseholdOwner(householdId)

    /** UID del usuario actual. Ver [FirestoreRepository.getLocalId]. */
    val localId: String? get() = repo.getLocalId()

    fun loadMembers(householdId: String) {
        screenModelScope.launch {
            _uiState.value = MemberUiState.Loading
            try {
                val members = repo.getMembers(householdId)
                _uiState.value = MemberUiState.Success(members)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirestoreException) {
                _uiState.value = MemberUiState.Error(
                    if (e.isGoneOrForbidden) FIRESTORE_GONE_MESSAGE else e.message
                )
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
                buzz(HapticKind.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = MemberUiState.Error(
                    e.message ?: "Error al añadir miembro"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    fun removeMember(householdId: String, memberId: String) {
        if (_memberActionState.value == MemberActionState.Loading) return // evita doble-tap
        screenModelScope.launch {
            _memberActionState.value = MemberActionState.Loading
            try {
                repo.deleteMember(householdId, memberId)
                val members = repo.getMembers(householdId)
                _uiState.value = MemberUiState.Success(members)
                _memberActionState.value = MemberActionState.Success
                buzz(HapticKind.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _memberActionState.value = MemberActionState.Error(
                    e.message ?: "Error al eliminar miembro"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    /**
     * Edita el rol de un miembro ("admin" | "child") sin recrearlo.
     * Recarga la lista al terminar.
     */
    fun updateMemberRole(householdId: String, memberId: String, role: String) {
        if (_memberActionState.value == MemberActionState.Loading) return // evita doble-tap
        screenModelScope.launch {
            _memberActionState.value = MemberActionState.Loading
            try {
                repo.updateMemberRole(householdId, memberId, role)
                val members = repo.getMembers(householdId)
                _uiState.value = MemberUiState.Success(members)
                _memberActionState.value = MemberActionState.Success
                buzz(HapticKind.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _memberActionState.value = MemberActionState.Error(
                    e.message ?: "Error al cambiar el rol"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    fun clearMemberAction() {
        _memberActionState.value = MemberActionState.Idle
    }

    fun reset() {
        _uiState.value = MemberUiState.Idle
        _lastCreatedMember.value = null
        _memberActionState.value = MemberActionState.Idle
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
            } catch (e: CancellationException) {
                throw e
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
        icon: String
    ) {
        screenModelScope.launch {
            _rewardActionState.value = RewardActionState.Loading
            try {
                val createdBy = repo.getLocalId() ?: ""
                repo.createReward(householdId, title, description, cost, icon, createdBy)
                _rewardActionState.value = RewardActionState.Success()
                buzz(HapticKind.SUCCESS)
                // Reload
                loadRewards(householdId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _rewardActionState.value = RewardActionState.Error(
                    e.message ?: "Error al crear recompensa"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    fun deleteReward(householdId: String, rewardId: String) {
        screenModelScope.launch {
            try {
                repo.deleteReward(householdId, rewardId)
                buzz(HapticKind.WARNING)
                loadRewards(householdId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _rewardActionState.value = RewardActionState.Error(
                    e.message ?: "Error al eliminar recompensa"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    fun redeemReward(
        householdId: String,
        rewardId: String,
        memberId: String,
        pointsSpent: Int
    ) {
        if (_rewardActionState.value == RewardActionState.Loading) return // evita doble-tap / doble descuento
        screenModelScope.launch {
            _rewardActionState.value = RewardActionState.Loading
            try {
                val redemption = repo.redeemReward(householdId, rewardId, memberId, pointsSpent)
                _rewardActionState.value = RewardActionState.Success(redemption)
                buzz(HapticKind.SUCCESS)
                // Reload members to refresh points
                loadMembers(householdId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _rewardActionState.value = RewardActionState.Error(
                    e.message ?: "Error al canjear recompensa"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    fun clearRewardAction() {
        _rewardActionState.value = RewardActionState.Idle
    }

    // ── Agradecer / Donar ──────────────────────────────────

    private val _appreciateActionState = MutableStateFlow<AppreciateActionState>(AppreciateActionState.Idle)
    val appreciateActionState: StateFlow<AppreciateActionState> = _appreciateActionState.asStateFlow()

    private val _donateActionState = MutableStateFlow<DonateActionState>(DonateActionState.Idle)
    val donateActionState: StateFlow<DonateActionState> = _donateActionState.asStateFlow()

    fun appreciateMember(householdId: String, fromMemberId: String, toMemberId: String, amount: Int) {
        if (_appreciateActionState.value == AppreciateActionState.Loading) return
        screenModelScope.launch {
            _appreciateActionState.value = AppreciateActionState.Loading
            when (val result = repo.appreciateMember(householdId, fromMemberId, toMemberId, amount)) {
                is FirestoreRepository.AppreciateResult.Ok -> {
                    loadMembers(householdId)
                    _appreciateActionState.value = AppreciateActionState.Success(result.remaining)
                    buzz(HapticKind.SUCCESS)
                }
                is FirestoreRepository.AppreciateResult.Error -> {
                    _appreciateActionState.value = AppreciateActionState.Error(appreciateErrorKey(result.reason))
                    buzz(HapticKind.ERROR)
                }
            }
        }
    }

    fun donatePoints(householdId: String, fromMemberId: String, toMemberId: String, amount: Int) {
        if (_donateActionState.value == DonateActionState.Loading) return
        screenModelScope.launch {
            _donateActionState.value = DonateActionState.Loading
            when (val result = repo.donatePoints(householdId, fromMemberId, toMemberId, amount)) {
                is FirestoreRepository.DonateResult.Ok -> {
                    loadMembers(householdId)
                    _donateActionState.value = DonateActionState.Success(result.donorNewTotal)
                    buzz(HapticKind.SUCCESS)
                }
                is FirestoreRepository.DonateResult.Error -> {
                    _donateActionState.value = DonateActionState.Error(donateErrorKey(result.reason))
                    buzz(HapticKind.ERROR)
                }
            }
        }
    }

    fun clearAppreciateAction() {
        _appreciateActionState.value = AppreciateActionState.Idle
    }

    fun clearDonateAction() {
        _donateActionState.value = DonateActionState.Idle
    }

    private fun appreciateErrorKey(reason: FirestoreRepository.AppreciateErrorReason): String = when (reason) {
        FirestoreRepository.AppreciateErrorReason.SELF -> "transfer_error_self"
        FirestoreRepository.AppreciateErrorReason.INVALID_AMOUNT -> "transfer_error_invalid_amount"
        FirestoreRepository.AppreciateErrorReason.LIMIT_EXCEEDED -> "appreciate_error_limit"
        FirestoreRepository.AppreciateErrorReason.MEMBER_NOT_FOUND -> "transfer_error_member_not_found"
    }

    private fun donateErrorKey(reason: FirestoreRepository.DonateErrorReason): String = when (reason) {
        FirestoreRepository.DonateErrorReason.SELF -> "transfer_error_self"
        FirestoreRepository.DonateErrorReason.INVALID_AMOUNT -> "transfer_error_invalid_amount"
        FirestoreRepository.DonateErrorReason.INSUFFICIENT_BALANCE -> "donate_error_insufficient_balance"
        FirestoreRepository.DonateErrorReason.MEMBER_NOT_FOUND -> "transfer_error_member_not_found"
    }
}
