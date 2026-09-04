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
import org.taskhub.network.models.NotificationResponse
import org.taskhub.storage.SettingsStore
import org.taskhub.ui.i18n.AppStrings

sealed class NotificationUiState {
    data object Idle : NotificationUiState()
    data object Loading : NotificationUiState()
    data class Success(
        val notifications: List<NotificationResponse>,
        val unreadCount: Int
    ) : NotificationUiState()
    data class Error(val message: String) : NotificationUiState()
}

class NotificationScreenModel(
    private val repo: FirestoreRepository,
    private val settingsStore: SettingsStore
) : ScreenModel {

    private val _uiState = MutableStateFlow<NotificationUiState>(NotificationUiState.Idle)
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun loadNotifications(householdId: String, memberId: String) {
        screenModelScope.launch {
            _uiState.value = NotificationUiState.Loading
            try {
                val all = repo.getNotifications(householdId)
                // Filter notifications for this member
                val memberNotifications = all.filter { it.memberId == memberId }
                    .sortedByDescending { it.createdAt }
                val unread = memberNotifications.count { !it.read }
                _unreadCount.value = unread
                _uiState.value = NotificationUiState.Success(memberNotifications, unread)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirestoreException) {
                _uiState.value = NotificationUiState.Error(
                    if (e.isGoneOrForbidden) FIRESTORE_GONE_MESSAGE else e.message
                )
            } catch (e: Exception) {
                _uiState.value = NotificationUiState.Error(
                    e.message ?: AppStrings.get("notification_error_loading", settingsStore.getLanguage())
                )
            }
        }
    }

    fun markAsRead(householdId: String, notificationId: String) {
        screenModelScope.launch {
            try {
                repo.markNotificationRead(householdId, notificationId)
                // Update local state
                val current = _uiState.value
                if (current is NotificationUiState.Success) {
                    val updated = current.notifications.map {
                        if (it.id == notificationId) it.copy(read = true) else it
                    }
                    val unread = updated.count { !it.read }
                    _unreadCount.value = unread
                    _uiState.value = NotificationUiState.Success(updated, unread)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-critical, ignore
            }
        }
    }

    fun refreshUnreadCount(householdId: String, memberId: String) {
        screenModelScope.launch {
            try {
                val all = repo.getNotifications(householdId)
                val unread = all.count { it.memberId == memberId && !it.read }
                _unreadCount.value = unread
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore polling errors
            }
        }
    }
}