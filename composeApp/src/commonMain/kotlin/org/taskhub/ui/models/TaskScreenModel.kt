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
import org.taskhub.network.models.AssignmentSlot
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    private val repo: FirestoreRepository
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
                repo.completeTask(householdId, taskId)
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

    // ── Helpers ─────────────────────────────────────────────

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