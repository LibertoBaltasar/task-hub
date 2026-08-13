package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.GoogleCalendarRepository
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.CommentResponse
import org.taskhub.network.models.AssignmentSlot
import org.taskhub.network.models.Subtask
import org.taskhub.platform.NotificationScheduler
import org.taskhub.platform.DebugFlags
import kotlinx.datetime.*

/**
 * Arquitectura de la app Task Hub (para devs nuevos):
 *
 * ┌──────────────────────────────────────────────────────────┐
 * │  UI (Voyager Screens)                                   │
 * │  TaskListScreen, HouseholdScreen, CreateTaskScreen...    │
 * │  → Observan StateFlows del ScreenModel                  │
 * │  → Toda la lógica de filtro/agrupación/vencimiento      │
 * │    está en TaskListScreen.kt (funciones privadas)       │
 * └────────────┬─────────────────────────────────────────────┘
 *              │
 * ┌────────────▼─────────────────────────────────────────────┐
 * │  ScreenModels (ViewModels de Voyager)                   │
 * │  TaskScreenModel ← este archivo                         │
 * │  → loadTasks(): dispara fetch a Firestore + actualiza   │
 * │    widget y estado                                      │
 * │  → createTask()/completeTask(): escritura + refresh     │
 * └────────────┬─────────────────────────────────────────────┘
 *              │
 * ┌────────────▼─────────────────────────────────────────────┐
 * │  FirestoreRepository (REST API directa, sin servidor)   │
 * │  → Auth anónima (signUp → idToken → Bearer)             │
 * │  → CRUD: households, tasks, members, assignments        │
 * │  → Los tasks son documentos planos (no instancias por   │
 * │    día). La recurrencia se calcula en cliente.          │
 * └────────────┬─────────────────────────────────────────────┘
 *              │
 * ┌────────────▼─────────────────────────────────────────────┐
 * │  Firestore (NoSQL)                                      │
 * │  Estructura:                                            │
 * │  households/{id}/                                       │
 * │    ├── fields: name, inviteCode, createdAt              │
 * │    ├── tasks/{id}/                                      │
 * │    │   ├── fields: title, frequency, lastCompletedDate, │
 * │    │   │          dueDate, points, tags, penalty...      │
 * │    │   └── assignments/{id}/                            │
 * │    │       └── fields: memberId, dueDate, status        │
 * │    ├── members/{id}/                                    │
 * │    │   └── fields: displayName, role, totalPoints       │
 * │    └── taskHistory/{id}/                                │
 * │        └── fields: taskId, memberId, points, completedAt│
 * └──────────────────────────────────────────────────────────┘
 *
 * Modelo de datos simplificado (sin instancias):
 *   - Las tareas NO generan documentos por cada ocurrencia.
 *   - Una tarea "daily" es UN solo documento con lastCompletedDate.
 *   - isTaskDueToday() calcula en cliente si toca hoy.
 *   - Esto evita el problema de las instancias huérfanas/duplicadas.
 *
 * Flujo de la app:
 *   1. App.kt → HomeScreen (dashboard unificado con espacio Personal + hogares)
 *   2. HouseholdScreen → botón "Ver Tareas" → TaskListScreen
 *   3. TaskListScreen → carga tareas → filtra por PENDING (default) → agrupa por día
 *   4. Al crear/completar tarea → loadTasks() refresca + actualiza widget Android
 */

// ── UI State ──────────────────────────────────────────────

sealed class TaskListUiState {
    data object Idle : TaskListUiState()
    data object Loading : TaskListUiState()
    data class Success(
        val tasks: List<TaskResponse>,
        val assignments: List<TaskAssignmentResponse>,
        val members: List<MemberResponse>
    ) : TaskListUiState()
    data class Error(val message: String) : TaskListUiState()
}

sealed class TaskDetailUiState {
    data object Idle : TaskDetailUiState()
    data object Loading : TaskDetailUiState()
    data class Success(
        val task: TaskResponse,
        val assignments: List<TaskAssignmentResponse>,
        val members: List<MemberResponse>
    ) : TaskDetailUiState()
    data class Error(val message: String) : TaskDetailUiState()
}

sealed class TaskActionState {
    data object Idle : TaskActionState()
    data object Loading : TaskActionState()
    data object Success : TaskActionState()
    data class Error(val message: String) : TaskActionState()
}

// ── Comments State ────────────────────────────────────────

sealed class CommentsUiState {
    data object Idle : CommentsUiState()
    data object Loading : CommentsUiState()
    data class Success(val comments: List<CommentResponse>) : CommentsUiState()
    data class Error(val message: String) : CommentsUiState()
}

// ── Filter & Sort ─────────────────────────────────────────

enum class TaskFilter {
    ALL,
    PENDING,
    COMPLETED,
    MINE
}

enum class TaskSort {
    DEADLINE_ASC,
    DEADLINE_DESC,
    POINTS_DESC,
    CREATED_DESC
}

// ── ScreenModel ───────────────────────────────────────────

class TaskScreenModel(
    private val repo: FirestoreRepository,
    private val notificationScheduler: NotificationScheduler,
    private val calendarRepo: GoogleCalendarRepository
) : ScreenModel {

    private val _listState = MutableStateFlow<TaskListUiState>(TaskListUiState.Idle)
    val listState: StateFlow<TaskListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow<TaskDetailUiState>(TaskDetailUiState.Idle)
    val detailState: StateFlow<TaskDetailUiState> = _detailState.asStateFlow()

    private val _actionState = MutableStateFlow<TaskActionState>(TaskActionState.Idle)
    val actionState: StateFlow<TaskActionState> = _actionState.asStateFlow()

    // Filter & sort state
    private val _filter = MutableStateFlow(TaskFilter.PENDING)
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(TaskSort.DEADLINE_ASC)
    val sort: StateFlow<TaskSort> = _sort.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow<String?>(null)
    val selectedTagFilter: StateFlow<String?> = _selectedTagFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Current member ID (for "mine" filter). Set externally.
    private val _currentMemberId = MutableStateFlow<String?>(null)
    val currentMemberId: StateFlow<String?> = _currentMemberId.asStateFlow()

    // Offline state — true when last load served from cache
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // All available tags (collected from tasks)
    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags.asStateFlow()

    // ── Load tasks ──────────────────────────────────────────

    fun loadTasks(householdId: String) {
        screenModelScope.launch {
            _listState.value = TaskListUiState.Loading
            try {
                val tasks = repo.getTasks(householdId)
                val assignments = repo.getAllAssignments(householdId)
                val members = repo.getMembers(householdId)

                // Check connectivity to update offline banner
                _isOffline.value = !repo.isOnline()

                // ── DEBUG LOG ──
                if (DebugFlags.isEnabled) {
                    println("[TaskScreenModel] loadTasks: ${tasks.size} tasks, ${assignments.size} assignments, ${members.size} members for household=$householdId offline=${_isOffline.value}")
                    for (t in tasks) {
                        println("[TaskScreenModel]   task: id=${t.id}, title=${t.title}, freq=${t.frequency}, lastCompleted=${t.lastCompletedDate}, dueDate=${t.dueDate}")
                    }
                }

                // Collect all unique tags
                val tagSet = mutableSetOf<String>()
                for (t in tasks) {
                    tagSet.addAll(t.tags)
                }
                _allTags.value = tagSet.toList().sorted()

                _listState.value = TaskListUiState.Success(tasks, assignments, members)
            } catch (e: Exception) {
                _isOffline.value = true
                _listState.value = TaskListUiState.Error(
                    e.message ?: "Error al cargar tareas"
                )
            }
        }
    }

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun setSort(sort: TaskSort) {
        _sort.value = sort
    }

    fun setTagFilter(tag: String?) {
        _selectedTagFilter.value = tag
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCurrentMemberId(memberId: String?) {
        _currentMemberId.value = memberId
    }

    // ── Create task ─────────────────────────────────────────

    fun createTask(
        householdId: String,
        createdBy: String,
        title: String,
        description: String,
        points: Int,
        frequency: String,
        recurrenceDays: List<Int>,
        tags: List<String>,
        subtasks: List<Subtask> = emptyList(),
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        memberIds: List<String>,
        mandatory: Boolean,
        dueDate: Long,
        assignmentRotation: List<AssignmentSlot> = emptyList()
    ) {
        screenModelScope.launch {
            _actionState.value = TaskActionState.Loading
            try {
                val task = repo.createTask(
                    householdId = householdId,
                    createdBy = createdBy,
                    title = title,
                    description = description,
                    points = points,
                    frequency = frequency,
                    recurrenceDays = recurrenceDays,
                    tags = tags,
                    subtasks = subtasks,
                    penaltyMode = penaltyMode,
                    penaltyValue = penaltyValue,
                    penaltyInterval = penaltyInterval,
                    penaltyMax = penaltyMax,
                    dueDate = dueDate,
                    assignmentRotation = assignmentRotation
                )

                // Auto-assign: if no specific members selected, assign to ALL members
                val membersToAssign = if (memberIds.isNotEmpty()) {
                    memberIds
                } else {
                    repo.getMembers(householdId).map { it.id }
                }
                if (membersToAssign.isNotEmpty()) {
                    repo.assignTask(
                        householdId = householdId,
                        taskId = task.id,
                        memberIds = membersToAssign,
                        mandatory = mandatory,
                        dueDate = dueDate
                    )
                }

                // Schedule reminder if task has a future deadline
                if (dueDate > 0) {
                    notificationScheduler.scheduleReminder(
                        taskId = task.id,
                        householdId = householdId,
                        taskTitle = title,
                        dueDateEpochMs = dueDate
                    )
                }

                _actionState.value = TaskActionState.Success
            } catch (e: Exception) {
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al crear tarea"
                )
            }
        }
    }

    // ── Complete task (sets lastCompletedDate) ───────────────

    /** Info for undo: saved before completing so we can revert. */
    data class UndoState(
        val householdId: String,
        val taskId: String,
        val previousLastCompletedDate: Long?
    )

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    fun completeTask(householdId: String, taskId: String) {
        screenModelScope.launch {
            _actionState.value = TaskActionState.Loading
            try {
                // Resolución robusta del miembro: si la UI no lo ha establecido
                // (p.ej. navegación directa al detalle desde Home o Calendario),
                // se deduce del usuario autenticado en Firestore. Así completar una
                // tarea en el espacio Personal nunca falla por falta de miembro.
                val memberId = _currentMemberId.value
                    ?: repo.resolveCurrentMember(householdId)

                // Fetch the task to get its points + save previous state for undo
                val tasks = repo.getTasks(householdId)
                val task = tasks.find { it.id == taskId }
                    ?: throw IllegalStateException("Tarea no encontrada")

                // Save undo info BEFORE completing
                val previousLcd = task.lastCompletedDate
                _undoState.value = UndoState(householdId, taskId, previousLcd)

                repo.completeTask(
                    householdId = householdId,
                    taskId = taskId,
                    memberId = memberId,
                    taskPoints = task.points
                )

                // Cancel any scheduled reminder for this task
                notificationScheduler.cancelReminder(taskId)

                // Update streak for the current member
                try {
                    updateMemberStreak(householdId, memberId)
                    checkAndAwardAchievements(householdId, memberId)
                } catch (_: Exception) { }

                _actionState.value = TaskActionState.Success
            } catch (e: Exception) {
                _undoState.value = null
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al completar tarea"
                )
            }
        }
    }

    /** Undo a task completion: restore previous lastCompletedDate. */
    fun undoCompleteTask() {
        val state = _undoState.value ?: return
        _undoState.value = null
        screenModelScope.launch {
            try {
                // Revert lastCompletedDate on the task
                repo.revertTaskCompletion(state.householdId, state.taskId, state.previousLastCompletedDate)
            } catch (_: Exception) {
                // Non-critical — task stays completed
            }
        }
    }

    fun clearUndoState() {
        _undoState.value = null
    }

    // ── Complete assignment (existing, keeps working) ────────

    fun completeAssignment(
        householdId: String,
        taskId: String,
        task: TaskResponse,
        assignmentId: String,
        assignment: TaskAssignmentResponse
    ) {
        screenModelScope.launch {
            _actionState.value = TaskActionState.Loading
            try {
                repo.completeAssignment(
                    householdId = householdId,
                    taskId = taskId,
                    task = task,
                    assignmentId = assignmentId,
                    assignment = assignment
                )
                _actionState.value = TaskActionState.Success

                // Refresh detail
                loadTaskDetail(householdId, taskId)
            } catch (e: Exception) {
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al completar tarea"
                )
            }
        }
    }

    // ── Load task detail ────────────────────────────────────

    fun loadTaskDetail(householdId: String, taskId: String) {
        screenModelScope.launch {
            _detailState.value = TaskDetailUiState.Loading
            try {
                val tasks = repo.getTasks(householdId)
                val task = tasks.find { it.id == taskId }
                    ?: throw IllegalStateException("Tarea no encontrada")

                val assignments = repo.getAssignments(householdId, taskId)
                val members = repo.getMembers(householdId)

                _detailState.value = TaskDetailUiState.Success(task, assignments, members)
            } catch (e: Exception) {
                _detailState.value = TaskDetailUiState.Error(
                    e.message ?: "Error al cargar tarea"
                )
            }
        }
    }

    // ── Assign task to members ──────────────────────────────

    fun assignMembers(
        householdId: String,
        taskId: String,
        memberIds: List<String>,
        mandatory: Boolean,
        dueDate: Long
    ) {
        screenModelScope.launch {
            _actionState.value = TaskActionState.Loading
            try {
                repo.assignTask(householdId, taskId, memberIds, mandatory, dueDate)
                _actionState.value = TaskActionState.Success

                // Refresh detail
                loadTaskDetail(householdId, taskId)
            } catch (e: Exception) {
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al asignar tarea"
                )
            }
        }
    }

    // ── Update task ─────────────────────────────────────────

    fun updateTask(
        householdId: String,
        taskId: String,
        title: String,
        description: String,
        points: Int,
        frequency: String,
        recurrenceDays: List<Int>,
        tags: List<String>,
        subtasks: List<Subtask> = emptyList(),
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        assignmentRotation: List<AssignmentSlot> = emptyList()
    ) {
        screenModelScope.launch {
            _actionState.value = TaskActionState.Loading
            try {
                repo.updateTask(
                    householdId = householdId,
                    taskId = taskId,
                    title = title,
                    description = description,
                    points = points,
                    frequency = frequency,
                    recurrenceDays = recurrenceDays,
                    tags = tags,
                    subtasks = subtasks,
                    penaltyMode = penaltyMode,
                    penaltyValue = penaltyValue,
                    penaltyInterval = penaltyInterval,
                    penaltyMax = penaltyMax,
                    assignmentRotation = assignmentRotation
                )
                _actionState.value = TaskActionState.Success
                // Refresh detail
                loadTaskDetail(householdId, taskId)
            } catch (e: Exception) {
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al actualizar tarea"
                )
            }
        }
    }

    // ── Delete task ──────────────────────────────────────────

    fun deleteTask(householdId: String, taskId: String) {
        screenModelScope.launch {
            _actionState.value = TaskActionState.Loading
            try {
                repo.deleteTask(householdId, taskId)
                _actionState.value = TaskActionState.Success
            } catch (e: Exception) {
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al eliminar tarea"
                )
            }
        }
    }

    // ── Comments ────────────────────────────────────────────

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
            } catch (e: Exception) {
                _commentsState.value = CommentsUiState.Error(
                    e.message ?: "Error al cargar comentarios"
                )
            }
        }
    }

    fun addComment(householdId: String, taskId: String, authorName: String) {
        val text = _newCommentText.value.trim()
        if (text.isEmpty()) return
        screenModelScope.launch {
            _commentsState.value = CommentsUiState.Loading
            try {
                repo.addComment(householdId, taskId, authorName, text)
                _newCommentText.value = ""
                // Reload comments
                loadComments(householdId, taskId)
            } catch (e: Exception) {
                _commentsState.value = CommentsUiState.Error(
                    e.message ?: "Error al añadir comentario"
                )
            }
        }
    }

    // ── CSV Export ─────────────────────────────────────────

    fun generateCsv(tasks: List<TaskResponse>): String {
        val sb = StringBuilder()
        sb.appendLine("Nombre,Frecuencia,Puntos,Veces completada,Último completado")
        for (task in tasks) {
            val freq = when (task.frequency) {
                "daily" -> "Diaria"
                "weekly" -> "Semanal"
                "monthly" -> "Mensual"
                else -> "Una vez"
            }
            val completions = if (task.lastCompletedDate != null) "1" else "0"
            val lastCompleted = if (task.lastCompletedDate != null) {
                val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(task.lastCompletedDate)
                val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${local.dayOfMonth}/${local.monthNumber}/${local.year}"
            } else {
                "Nunca"
            }
            val escapedTitle = "\"${task.title.replace("\"", "\"\"")}\""
            sb.appendLine("$escapedTitle,$freq,${task.points},$completions,$lastCompleted")
        }
        return sb.toString()
    }

    // ── Helpers ─────────────────────────────────────────────

    /**
     * Updates the member's streak:
     * - If today's date differs from lastStreakDate, check if it's consecutive
     * - If yesterday -> streak++
     * - If older -> streak = 1 (new streak)
     * - If same day -> no change (already counted)
     */
    private suspend fun updateMemberStreak(householdId: String, memberId: String) {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date

        val members = repo.getMembers(householdId)
        val member = members.find { it.id == memberId } ?: return

        val lastDateEpoch = member.lastStreakDate
        val todayEpoch = today.atStartOfDayIn(tz).toEpochMilliseconds()

        if (lastDateEpoch >= todayEpoch) {
            // Already counted today
            return
        }

        val newStreak: Int
        if (lastDateEpoch == 0L) {
            // First streak ever
            newStreak = 1
        } else {
            val lastDate = kotlinx.datetime.Instant.fromEpochMilliseconds(lastDateEpoch)
                .toLocalDateTime(tz).date
            val yesterday = today.plus(-1, DateTimeUnit.DAY)

            newStreak = if (lastDate == yesterday) {
                // Consecutive day
                member.currentStreak + 1
            } else {
                // Gap — new streak
                1
            }
        }

        val newBest = maxOf(newStreak, member.bestStreak)

        repo.updateMemberStreak(
            householdId = householdId,
            memberId = memberId,
            currentStreak = newStreak,
            bestStreak = newBest,
            lastStreakDate = todayEpoch
        )
    }

    /**
     * Check for newly unlocked achievements after completing a task.
     */
    private suspend fun checkAndAwardAchievements(householdId: String, memberId: String) {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val currentHour = now.toLocalDateTime(tz).hour

        val members = repo.getMembers(householdId)
        val member = members.find { it.id == memberId } ?: return

        val assignments = repo.getAllAssignments(householdId)
        val completedCount = assignments.count {
            it.memberId == memberId && it.status == "completed"
        }

        val alreadyUnlocked = repo.getMemberAchievements(householdId, memberId)

        val newlyUnlocked = AchievementChecker.checkNewAchievements(
            totalTasksCompleted = completedCount,
            totalPoints = member.totalPoints,
            currentStreak = member.currentStreak,
            lastCompletedHour = currentHour,
            alreadyUnlocked = alreadyUnlocked
        )

        for (achievementId in newlyUnlocked) {
            try {
                repo.addMemberAchievement(householdId, memberId, achievementId)
            } catch (_: Exception) {
                // Non-critical failure
            }
        }
    }

    /**
     * Returns the member ID responsible for this task today based on assignmentRotation.
     * Falls back to null if no rotation is defined — then use fixed assignments.
     */
    fun getTodayAssignee(task: TaskResponse): String? {
        if (task.assignmentRotation.isEmpty()) return null

        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        val todayDow = today.dayOfWeek.ordinal + 1 // 1=Monday..7=Sunday

        val slot = task.assignmentRotation.find { it.dayOfWeek == todayDow }
        return slot?.memberId
    }

    fun resetActionState() {
        _actionState.value = TaskActionState.Idle
    }

    // ── Toggle subtask ──────────────────────────────────────

    fun toggleSubtask(householdId: String, taskId: String, subtaskId: String) {
        screenModelScope.launch {
            try {
                val tasks = repo.getTasks(householdId)
                val task = tasks.find { it.id == taskId } ?: return@launch
                val updatedSubtasks = task.subtasks.map { st ->
                    if (st.id == subtaskId) st.copy(completed = !st.completed) else st
                }
                repo.updateSubtasks(householdId, taskId, updatedSubtasks)
                // Refresh detail
                loadTaskDetail(householdId, taskId)
            } catch (_: Exception) {
                // Non-critical; detail will be stale until next load
            }
        }
    }

    fun reset() {
        _listState.value = TaskListUiState.Idle
        _detailState.value = TaskDetailUiState.Idle
        _actionState.value = TaskActionState.Idle
    }

    // ── Google Calendar ──────────────────────────────────────

    /** Sealed state for Google Calendar send operations. */
    sealed class CalendarActionState {
        data object Idle : CalendarActionState()
        data object Sending : CalendarActionState()
        data object Success : CalendarActionState()
        data class Error(val message: String) : CalendarActionState()
    }

    private val _calendarActionState = MutableStateFlow<CalendarActionState>(CalendarActionState.Idle)
    val calendarActionState: StateFlow<CalendarActionState> = _calendarActionState.asStateFlow()

    /**
     * Sends a task to Google Calendar using the stored access token.
     * For "once" tasks, uses the task's dueDate. For recurring tasks, uses today.
     *
     * @param accessToken The Google OAuth access token (from SettingsStore).
     * @param task The task to send.
     */
    fun sendToGoogleCalendar(accessToken: String, task: TaskResponse) {
        screenModelScope.launch {
            _calendarActionState.value = CalendarActionState.Sending
            try {
                calendarRepo.createEvent(
                    accessToken = accessToken,
                    summary = task.title,
                    description = task.description.ifBlank { "Tarea de Task Hub" },
                    dueDateEpochMs = task.dueDate
                )
                _calendarActionState.value = CalendarActionState.Success
            } catch (e: Exception) {
                _calendarActionState.value = CalendarActionState.Error(
                    e.message ?: "Error al enviar a Google Calendar"
                )
            }
        }
    }

    fun resetCalendarActionState() {
        _calendarActionState.value = CalendarActionState.Idle
    }
}