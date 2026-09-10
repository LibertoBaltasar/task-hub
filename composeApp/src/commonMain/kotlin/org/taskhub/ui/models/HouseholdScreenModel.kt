/**
 * ScreenModel de gestión de hogares: crear/unirse/cargar/abandonar/borrar un
 * hogar y el chat de mensajes del hogar. Lo usan [org.taskhub.ui.screens.HouseholdScreen],
 * [org.taskhub.ui.screens.CreateHouseholdScreen] y [org.taskhub.ui.screens.JoinHouseholdScreen].
 * Tras cualquier cambio en la membresía persiste el hogar en
 * [HouseholdStore] (caché local) y sincroniza la lista en la nube vía
 * [GoogleAuthManager.syncHouseholdsToCloud].
 */
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
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.MessageResponse
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SettingsStore
import org.taskhub.platform.HapticKind
import org.taskhub.platform.logAnalyticsEvent
import org.taskhub.platform.vibrate
import org.taskhub.ui.i18n.AppStrings

/** Estados de carga/creación/unión a un hogar. */
sealed class HouseholdUiState {
    data object Idle : HouseholdUiState()
    data object Loading : HouseholdUiState()
    data class Success(val household: HouseholdResponse) : HouseholdUiState()
    data class AlreadyMember(val household: HouseholdResponse) : HouseholdUiState()
    /**
     * [removable] es true cuando Firestore confirmó (404/403) que el hogar ya
     * no existe o no es accesible — la UI puede ofrecer "quitar de mis espacios"
     * en vez de solo "reintentar", que nunca funcionaría en ese caso.
     */
    data class Error(val message: String, val removable: Boolean = false) : HouseholdUiState()
}

/** Estados de carga del chat de mensajes del hogar. */
sealed class MessagesUiState {
    data object Idle : MessagesUiState()
    data object Loading : MessagesUiState()
    data class Success(val messages: List<MessageResponse>) : MessagesUiState()
    data class Error(val message: String) : MessagesUiState()
}

/**
 * ScreenModel de un hogar concreto: expone [uiState] ([HouseholdUiState])
 * para crear/unirse/cargar/abandonar/borrar y [messagesUiState] para el
 * chat del hogar.
 */
class HouseholdScreenModel(
    private val repo: FirestoreRepository,
    private val householdStore: HouseholdStore,
    private val authManager: GoogleAuthManager,
    private val settingsStore: SettingsStore
) : ScreenModel {

    private fun buzz(kind: HapticKind) {
        if (settingsStore.isVibrationEnabled()) vibrate(kind)
    }

    private fun s(key: String) = AppStrings.get(key, settingsStore.getLanguage())

    private val _uiState = MutableStateFlow<HouseholdUiState>(HouseholdUiState.Idle)
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    /** Ver [FirestoreRepository.resolveCurrentMember]. */
    suspend fun resolveCurrentMember(householdId: String): String = repo.resolveCurrentMember(householdId)

    /** Ver [FirestoreRepository.appreciationRemaining]. */
    fun appreciationRemaining(member: MemberResponse): Int = repo.appreciationRemaining(member)

    /** Crea un hogar nuevo con [name] y lo guarda como hogar actual del usuario. */
    fun createHousehold(name: String) {
        screenModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val household = repo.createHousehold(name)
                householdStore.saveHousehold(household.id, household.name, household.inviteCode)
                authManager.syncHouseholdsToCloud()
                logAnalyticsEvent("household_created")
                _uiState.value = HouseholdUiState.Success(household)
                buzz(HapticKind.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HouseholdUiState.Error(
                    e.message ?: s("household_error_creating")
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    /**
     * Se une a un hogar existente mediante [inviteCode]. Si el usuario ya era
     * miembro (con cualquiera de sus identidades: anónima o Google) no
     * vuelve a crear perfil, solo emite [HouseholdUiState.AlreadyMember]
     * para que la UI navegue directo al hogar sin duplicar el alta.
     */
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
                buzz(HapticKind.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HouseholdUiState.Error(
                    e.message ?: s("household_error_invalid_invite_code")
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    /**
     * Carga los datos de un hogar por [id]. Si Firestore responde 404/403
     * (hogar borrado o sin acceso), marca el error como `removable` para que
     * la UI ofrezca quitarlo de la lista local en vez de solo "reintentar".
     */
    fun loadHousehold(id: String) {
        screenModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val household = repo.getHousehold(id)
                _uiState.value = HouseholdUiState.Success(household)
            } catch (e: FirestoreException) {
                if (e.isGoneOrForbidden) {
                    _uiState.value = HouseholdUiState.Error(
                        message = FIRESTORE_GONE_MESSAGE,
                        removable = true
                    )
                } else {
                    _uiState.value = HouseholdUiState.Error(e.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HouseholdUiState.Error(
                    e.message ?: s("household_error_loading")
                )
            }
        }
    }

    /** Quita un hogar inaccesible de la caché local (ver [HouseholdUiState.Error.removable]). */
    fun removeGhostHousehold(householdId: String) {
        householdStore.removeHousehold(householdId)
    }

    /** Vuelve [uiState] a [HouseholdUiState.Idle]. */
    fun reset() {
        _uiState.value = HouseholdUiState.Idle
    }

    /** Borra el hogar [householdId] en cascada (solo el owner puede hacerlo, ver `firestore.rules`). */
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
                buzz(HapticKind.WARNING)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                buzz(HapticKind.ERROR)
                onError(e.message ?: s("household_error_deleting"))
            }
        }
    }

    /** El usuario actual abandona el hogar [householdId] (borra su propio miembro, no el hogar). */
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
                buzz(HapticKind.WARNING)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                buzz(HapticKind.ERROR)
                onError(e.message ?: s("household_error_leaving"))
            }
        }
    }

    /** ID local (anónimo) del usuario actual, independiente de si hay sesión de Google. */
    fun getLocalId(): String? = repo.getLocalId()

    // ── Chat de mensajes ──

    private val _messagesUiState = MutableStateFlow<MessagesUiState>(MessagesUiState.Idle)
    val messagesUiState: StateFlow<MessagesUiState> = _messagesUiState.asStateFlow()

    private val _newMessageText = MutableStateFlow("")
    val newMessageText: StateFlow<String> = _newMessageText.asStateFlow()

    /** Actualiza el texto del campo de nuevo mensaje (estado del input, aún sin enviar). */
    fun updateNewMessageText(text: String) {
        _newMessageText.value = text
    }

    /** Carga los mensajes del chat del hogar [householdId]. */
    fun loadMessages(householdId: String) {
        screenModelScope.launch {
            if (_messagesUiState.value !is MessagesUiState.Success) {
                _messagesUiState.value = MessagesUiState.Loading
            }
            try {
                val messages = repo.getMessages(householdId)
                _messagesUiState.value = MessagesUiState.Success(messages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messagesUiState.value = MessagesUiState.Error(
                    e.message ?: s("messages_error_loading")
                )
            }
        }
    }

    /**
     * Envía el mensaje pendiente en [newMessageText] al chat del hogar,
     * firmado por [memberId]. Limpia el campo de texto de forma optimista
     * ANTES de la llamada de red para que un doble tap en "Enviar" no lea el
     * mismo texto dos veces y lo duplique.
     */
    fun sendMessage(householdId: String, memberId: String) {
        val text = _newMessageText.value.trim()
        if (text.isEmpty() || memberId.isEmpty()) return
        // Limpiar de forma optimista ANTES de la llamada de red: evita que un
        // doble tap en "Enviar" lea el mismo texto dos veces y lo duplique.
        _newMessageText.value = ""
        screenModelScope.launch {
            try {
                val authorName = repo.getMembers(householdId)
                    .firstOrNull { it.id == memberId }
                    ?.displayName ?: ""
                repo.sendMessage(householdId, memberId, authorName, text)
                loadMessages(householdId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messagesUiState.value = MessagesUiState.Error(
                    e.message ?: s("messages_error_sending")
                )
            }
        }
    }
}
