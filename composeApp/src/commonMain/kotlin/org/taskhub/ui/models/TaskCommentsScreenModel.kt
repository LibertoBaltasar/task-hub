package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.CommentResponse
import org.taskhub.storage.SettingsStore
import org.taskhub.ui.i18n.AppStrings

// ── Comments State ────────────────────────────────────────

sealed class CommentsUiState {
    data object Idle : CommentsUiState()
    data object Loading : CommentsUiState()
    data class Success(val comments: List<CommentResponse>) : CommentsUiState()
    data class Error(val message: String) : CommentsUiState()
}

/**
 * Comentarios de una tarea (chat por tarea), extraído de [TaskScreenModel]
 * (el "mini god ScreenModel" — 1258+ líneas, panel de revisión
 * 2026-09-03/04, Experto 7, reabierto desde v3). Comentarios es el
 * subsistema más autocontenido dentro de `TaskDetailScreen`: tiene su propio
 * estado (`CommentsUiState`/`newCommentText`) y no participa en ningún flujo
 * de puntos/compleción, así que se extrae primero (panel v7, #17).
 *
 * [currentMemberId] se recibe como parámetro en vez de mantenerse aquí
 * duplicado: [TaskScreenModel] ya es la fuente de verdad de "quién es el
 * miembro activo" para el resto de `TaskDetailScreen`.
 */
class TaskCommentsScreenModel(
    private val repo: FirestoreRepository,
    private val settingsStore: SettingsStore
) : ScreenModel {

    private fun s(key: String) = AppStrings.get(key, settingsStore.getLanguage())

    private val _commentsState = MutableStateFlow<CommentsUiState>(CommentsUiState.Idle)
    val commentsState: StateFlow<CommentsUiState> = _commentsState.asStateFlow()

    private val _newCommentText = MutableStateFlow("")
    val newCommentText: StateFlow<String> = _newCommentText.asStateFlow()

    fun setNewCommentText(text: String) {
        if (text.length <= 200) {
            _newCommentText.value = text
        }
    }

    fun loadComments(householdId: String, taskId: String) {
        screenModelScope.launch {
            _commentsState.value = CommentsUiState.Loading
            try {
                val comments = repo.getComments(householdId, taskId)
                _commentsState.value = CommentsUiState.Success(comments)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _commentsState.value = CommentsUiState.Error(
                    e.message ?: s("task_comment_error_loading")
                )
            }
        }
    }

    fun addComment(householdId: String, taskId: String, currentMemberId: String?) {
        val text = _newCommentText.value.trim()
        if (text.isEmpty()) return
        // Limpiar el campo de forma optimista, ANTES de la llamada de red: si no,
        // un doble tap en "Enviar" antes de que la primera petición complete lee
        // el mismo texto dos veces y envía el comentario duplicado.
        _newCommentText.value = ""
        screenModelScope.launch {
            _commentsState.value = CommentsUiState.Loading
            try {
                val memberId = currentMemberId ?: repo.resolveCurrentMember(householdId)
                val authorName = resolveCurrentMemberName(householdId, memberId)
                repo.addComment(householdId, taskId, memberId, authorName, text)
                // Reload comments
                loadComments(householdId, taskId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _commentsState.value = CommentsUiState.Error(
                    e.message ?: s("task_comment_error_adding")
                )
            }
        }
    }

    /** Resolves the display name of the current member, for use as comment author. */
    private suspend fun resolveCurrentMemberName(householdId: String, memberId: String): String {
        return try {
            val member = repo.getMembers(householdId).find { it.id == memberId }
            member?.displayName?.takeIf { it.isNotBlank() } ?: s("task_comment_default_author")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            s("profile_default_name")
        }
    }
}
