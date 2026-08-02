package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.CommentResponse
import org.taskhub.network.models.AssignmentSlot
import org.taskhub.platform.NotificationScheduler
import kotlinx.datetime.*

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
    private val notificationScheduler: NotificationScheduler
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

    // Current member ID (for "mine" filter). Set externally.
    private val _currentMemberId = MutableStateFlow<String?>(null)
    val currentMemberId: StateFlow<String?> = _currentMemberId.asStateFlow()

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

                // ── DEBUG LOG ──
                println("[TaskScreenModel] loadTasks: ${tasks.size} tasks, ${assignments.size} assignments, ${members.size} members for household=$householdId")
                for (t in tasks) {
                    println("[TaskScreenModel]   task: id=${t.id}, title=${t.title}, freq=${t.frequency}, lastCompleted=${t.lastCompletedDate}, dueDate=${t.dueDate}")
                }

                // Collect all unique tags
                val tagSet = mutableSetOf<String>()
                for (t in tasks) {
                    tagSet.addAll(t.tags)
                }
                _allTags.value = tagSet.toList().sorted()

                _listState.value = TaskListUiState.Success(tasks, assignments, members)
            } catch (e: Exception) {
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
                    penaltyMode = penaltyMode,
                    penaltyValue = penaltyValue,
                    penaltyInterval = penaltyInterval,
                    penaltyMax = penaltyMax,
                    dueDate = dueDate,
                    assignmentRotation = assignmentRotation
                )

                // Auto-assign if members selected
                if (memberIds.isNotEmpty()) {
                    repo.assignTask(
                        householdId = householdId,
                        taskId = task.id,
                        memberIds = memberIds,
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

        fun completeTask(householdId: String, taskId: String) {
            screenModelScope.launch {
                _actionState.value = TaskActionState.Loading
                try {
                    val memberId = _currentMemberId.value
                        ?: throw IllegalStateException("No se ha identificado al miembro actual")

                    // Fetch the task to get its points
                    val tasks = repo.getTasks(householdId)
                    val task = tasks.find { it.id == taskId }
                        ?: throw IllegalStateException("Tarea no encontrada")

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
                    } catch (_: Exception) {
                        // Streak update failure shouldn't block task completion
                    }

                    _actionState.value = TaskActionState.Success
                } catch (e: Exception) {
                    _actionState.value = TaskActionState.Error(
                        e.message ?: "Error al completar tarea"
                    )
                }
            }
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

    fun reset() {
        _listState.value = TaskListUiState.Idle
        _detailState.value = TaskDetailUiState.Idle
        _actionState.value = TaskActionState.Idle
    }
}