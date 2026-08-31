package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
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
import org.taskhub.network.models.Subtask
import org.taskhub.platform.NotificationScheduler
import org.taskhub.platform.DebugFlags
import org.taskhub.platform.AdController
import org.taskhub.platform.HapticKind
import org.taskhub.platform.logAnalyticsEvent
import org.taskhub.platform.vibrate
import org.taskhub.storage.SettingsStore
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
    private val calendarSync: CalendarSyncManager,
    private val adController: AdController,
    private val settingsStore: SettingsStore
) : ScreenModel {

    private fun buzz(kind: HapticKind) {
        if (settingsStore.isVibrationEnabled()) vibrate(kind)
    }

    private val _listState = MutableStateFlow<TaskListUiState>(TaskListUiState.Idle)
    val listState: StateFlow<TaskListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow<TaskDetailUiState>(TaskDetailUiState.Idle)
    val detailState: StateFlow<TaskDetailUiState> = _detailState.asStateFlow()

    private val _actionState = MutableStateFlow<TaskActionState>(TaskActionState.Idle)
    val actionState: StateFlow<TaskActionState> = _actionState.asStateFlow()

    // Estado específico para "cambiar quién hizo la tarea". Separado de
    // actionState para no disparar la navegación de vuelta (navigator.pop)
    // que sí disparan completar/eliminar.
    private val _reassignState = MutableStateFlow<TaskActionState>(TaskActionState.Idle)
    val reassignState: StateFlow<TaskActionState> = _reassignState.asStateFlow()

    // Asignación de la tarea del detalle actual que pertenece al usuario en
    // sesión — base para el indicador de estado de sincronización con Calendar.
    // Null mientras se resuelve o si la tarea no está asignada a este usuario.
    private val _myAssignment = MutableStateFlow<TaskAssignmentResponse?>(null)
    val myAssignment: StateFlow<TaskAssignmentResponse?> = _myAssignment.asStateFlow()

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

    private var loadTasksJob: Job? = null

    fun loadTasks(householdId: String) {
        // Cancela la carga anterior en curso: sin esto, dos loadTasks() solapadas
        // (p.ej. pull-to-refresh seguido de un cambio de filtro) pueden resolverse
        // fuera de orden y la respuesta más antigua sobrescribe con datos stale
        // el StateFlow tras la más reciente.
        loadTasksJob?.cancel()
        loadTasksJob = screenModelScope.launch {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Relanzar: si no, una loadTasks() más reciente que ya canceló este
                // Job ve su propia cancelación tratada como un error normal aquí
                // (ver `loadTasksJob?.cancel()` arriba) y puede sobrescribir el
                // resultado correcto de la carga nueva con un "Error" obsoleto.
                throw e
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
        recurrenceDay: Int? = null,
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
        if (_actionState.value == TaskActionState.Loading) return
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
                    recurrenceDay = recurrenceDay,
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
                    val created = repo.assignTask(
                        householdId = householdId,
                        taskId = task.id,
                        memberIds = membersToAssign,
                        mandatory = mandatory,
                        dueDate = dueDate
                    )
                    syncCalendarOnAssigned(householdId, created)
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
                buzz(HapticKind.SUCCESS)
            } catch (e: Exception) {
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al crear tarea"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    // ── Complete task (sets lastCompletedDate) ───────────────

    /**
     * Info for undo: saved before completing so we can revert not just the
     * "completada" flag sino también los puntos/racha que otorgó completarla
     * — de lo contrario, completar+deshacer en bucle permite farmear puntos
     * y racha sin límite (la tarea vuelve a estar disponible mientras el
     * usuario conserva las recompensas de cada intento).
     */
    data class UndoState(
        val householdId: String,
        val taskId: String,
        val memberId: String,
        val previousLastCompletedDate: Long?,
        val previousCompletedBy: String?,
        val pointsAwarded: Int,
        val previousStreak: Int,
        val previousBestStreak: Int,
        val previousLastStreakDate: Long,
        /** `completedAt` devuelto por [FirestoreRepository.completeTask] — localiza el registro de taskHistory a borrar al deshacer. */
        val completedAt: Long
    )

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    fun completeTask(householdId: String, taskId: String) {
        if (_actionState.value == TaskActionState.Loading) return // evita doble-tap / doble suma de puntos
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
                val memberBefore = repo.getMembers(householdId).find { it.id == memberId }

                // Save undo info BEFORE completing (puntos/racha previos incluidos)
                _undoState.value = UndoState(
                    householdId = householdId,
                    taskId = taskId,
                    memberId = memberId,
                    previousLastCompletedDate = task.lastCompletedDate,
                    previousCompletedBy = task.completedBy,
                    pointsAwarded = task.points,
                    previousStreak = memberBefore?.currentStreak ?: 0,
                    previousBestStreak = memberBefore?.bestStreak ?: 0,
                    previousLastStreakDate = memberBefore?.lastStreakDate ?: 0L,
                    completedAt = 0L
                )

                val completedAt = repo.completeTask(
                    householdId = householdId,
                    taskId = taskId,
                    memberId = memberId,
                    taskPoints = task.points
                )
                _undoState.value = _undoState.value?.copy(completedAt = completedAt)

                // Cancel any scheduled reminder for this task.
                // La tarea ya está completada y los puntos ya se otorgaron en el
                // servidor en este punto: un fallo aquí (WorkManager/AlarmManager)
                // es un efecto secundario no crítico, nunca debe marcar la acción
                // como error (eso invitaría a reintentar completeTask() y duplicar
                // los puntos ya otorgados).
                try {
                    notificationScheduler.cancelReminder(taskId)
                } catch (_: Exception) { }

                // Tarea hecha → borrar el evento de Calendar vinculado, si lo hay
                try {
                    val myAssignment = repo.getAssignments(householdId, taskId)
                        .find { it.memberId == memberId }
                    if (myAssignment != null) {
                        calendarSync.onTaskCompleted(householdId, myAssignment)
                    }
                } catch (_: Exception) { }

                // Update streak + achievements reusing memberBefore (evita 2
                // lecturas extra de getMembers): la racha aún no se ha tocado
                // en el servidor, así que memberBefore es el estado correcto de
                // partida; el total de puntos post-premio se calcula en local.
                try {
                    if (memberBefore != null) {
                        val streakUpdated = updateMemberStreak(householdId, memberBefore)
                        val memberForAchievements = streakUpdated.copy(
                            totalPoints = memberBefore.totalPoints + task.points
                        )
                        checkAndAwardAchievements(householdId, memberForAchievements)
                    }
                } catch (_: Exception) { }

                _actionState.value = TaskActionState.Success
                buzz(HapticKind.SUCCESS)

                // Registrar el evento (métrica clave de engagement/racha) y mostrar
                // el interstitial son efectos secundarios no críticos — un fallo
                // aquí (analytics no inicializado, error interno de AdMob) no debe
                // sobrescribir el TaskActionState.Success que ya se ha publicado.
                try {
                    logAnalyticsEvent("task_completed")
                    adController.maybeShowInterstitial()
                } catch (_: Exception) { }
            } catch (e: Exception) {
                _undoState.value = null
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al completar tarea"
                )
                buzz(HapticKind.ERROR)
            }
        }
    }

    /**
     * Undo a task completion: restore previous points, streak and lastCompletedDate.
     *
     * Orden deliberado: puntos/racha/historial se revierten ANTES que el flag de
     * completada de la tarea. Si una escritura falla a mitad de camino (red), el
     * peor caso posible es que la tarea SIGA marcada como completada con los
     * puntos ya revertidos (recuperable reintentando el undo) — nunca que quede
     * "pendiente" mientras el miembro conserva los puntos, que permitiría
     * volver a completarla y duplicar el premio (el mismo bug que UndoState fue
     * diseñado para evitar, ver su KDoc).
     */
    fun undoCompleteTask() {
        val state = _undoState.value ?: return
        _undoState.value = null
        buzz(HapticKind.LIGHT)
        screenModelScope.launch {
            try {
                repo.addMemberPoints(state.householdId, state.memberId, -state.pointsAwarded)
                repo.updateMemberStreak(
                    householdId = state.householdId,
                    memberId = state.memberId,
                    currentStreak = state.previousStreak,
                    bestStreak = state.previousBestStreak,
                    lastStreakDate = state.previousLastStreakDate
                )
                if (state.completedAt != 0L) {
                    repo.deleteTaskHistoryRecord(state.householdId, state.taskId, state.completedAt)
                }
                repo.revertTaskCompletion(
                    state.householdId,
                    state.taskId,
                    state.previousLastCompletedDate,
                    state.previousCompletedBy
                )
            } catch (_: Exception) {
                // Non-critical — la tarea puede quedar como completada (ver KDoc arriba)
            }
        }
    }

    fun clearUndoState() {
        _undoState.value = null
    }

    // ── Reasignar quién completó (corrección de errores) ─────

    /**
     * Cambia quién ha hecho una tarea ya completada. Transfiere los puntos del
     * miembro anterior al nuevo (coherente con "quien marca hecho recibe los
     * puntos") y refresca el detalle al terminar.
     */
    fun reassignTaskCompletion(
        householdId: String,
        taskId: String,
        taskPoints: Int,
        newMemberId: String
    ) {
        if (_reassignState.value == TaskActionState.Loading) return
        screenModelScope.launch {
            _reassignState.value = TaskActionState.Loading
            try {
                repo.reassignTaskCompletion(
                    householdId = householdId,
                    taskId = taskId,
                    taskPoints = taskPoints,
                    newMemberId = newMemberId
                )
                _reassignState.value = TaskActionState.Success
                loadTaskDetail(householdId, taskId)
            } catch (e: Exception) {
                _reassignState.value = TaskActionState.Error(
                    e.message ?: "Error al cambiar quién hizo la tarea"
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
        if (_actionState.value == TaskActionState.Loading) return
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
                try {
                    calendarSync.onTaskCompleted(householdId, assignment)
                } catch (_: Exception) { }
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
            _myAssignment.value = null
            try {
                val tasks = repo.getTasks(householdId)
                val task = tasks.find { it.id == taskId }
                    ?: throw IllegalStateException("Tarea no encontrada")

                val assignments = repo.getAssignments(householdId, taskId)
                val members = repo.getMembers(householdId)

                _detailState.value = TaskDetailUiState.Success(task, assignments, members)

                val myMemberId = try { repo.resolveCurrentMember(householdId) } catch (_: Exception) { null }
                _currentMemberId.value = myMemberId
                _myAssignment.value = assignments.find { it.memberId == myMemberId }
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
        if (_actionState.value == TaskActionState.Loading) return
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
        recurrenceDay: Int? = null,
        tags: List<String>,
        subtasks: List<Subtask> = emptyList(),
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        assignmentRotation: List<AssignmentSlot> = emptyList(),
        memberIds: List<String> = emptyList(),
        mandatory: Boolean = false,
        dueDate: Long = 0
    ) {
        if (_actionState.value == TaskActionState.Loading) return
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
                    recurrenceDay = recurrenceDay,
                    tags = tags,
                    subtasks = subtasks,
                    penaltyMode = penaltyMode,
                    penaltyValue = penaltyValue,
                    penaltyInterval = penaltyInterval,
                    penaltyMax = penaltyMax,
                    assignmentRotation = assignmentRotation,
                    dueDate = dueDate
                )

                // Sincronizar asignaciones: borrar las existentes y reasignar.
                // Si no se seleccionó a nadie, se asigna a todos (misma semántica
                // que al crear).
                syncCalendarOnUnassigned(householdId, taskId)
                repo.deleteAssignments(householdId, taskId)
                val membersToAssign = if (memberIds.isNotEmpty()) {
                    memberIds
                } else {
                    repo.getMembers(householdId).map { it.id }
                }
                if (membersToAssign.isNotEmpty()) {
                    val created = repo.assignTask(
                        householdId = householdId,
                        taskId = taskId,
                        memberIds = membersToAssign,
                        mandatory = mandatory,
                        dueDate = dueDate,
                        taskTitle = title
                    )
                    syncCalendarOnAssigned(householdId, created)
                }

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
        if (_actionState.value == TaskActionState.Loading) return
        screenModelScope.launch {
            _actionState.value = TaskActionState.Loading
            try {
                syncCalendarOnUnassigned(householdId, taskId)
                repo.deleteTask(householdId, taskId)
                _actionState.value = TaskActionState.Success
                buzz(HapticKind.WARNING)
            } catch (e: Exception) {
                _actionState.value = TaskActionState.Error(
                    e.message ?: "Error al eliminar tarea"
                )
                buzz(HapticKind.ERROR)
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

    fun addComment(householdId: String, taskId: String) {
        val text = _newCommentText.value.trim()
        if (text.isEmpty()) return
        // Limpiar el campo de forma optimista, ANTES de la llamada de red: si no,
        // un doble tap en "Enviar" antes de que la primera petición complete lee
        // el mismo texto dos veces y envía el comentario duplicado.
        _newCommentText.value = ""
        screenModelScope.launch {
            _commentsState.value = CommentsUiState.Loading
            try {
                val authorName = resolveCurrentMemberName(householdId)
                repo.addComment(householdId, taskId, authorName, text)
                // Reload comments
                loadComments(householdId, taskId)
            } catch (e: Exception) {
                _commentsState.value = CommentsUiState.Error(
                    e.message ?: "Error al añadir comentario"
                )
            }
        }
    }

    /** Resolves the display name of the current member, for use as comment author. */
    private suspend fun resolveCurrentMemberName(householdId: String): String {
        return try {
            val memberId = _currentMemberId.value ?: repo.resolveCurrentMember(householdId)
            val member = repo.getMembers(householdId).find { it.id == memberId }
            member?.displayName?.takeIf { it.isNotBlank() } ?: "Miembro"
        } catch (_: Exception) {
            "Usuario"
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
     *
     * Recibe [member] ya cargado (en vez de volver a pedirlo a Firestore) y
     * devuelve la versión actualizada, para que el llamador pueda encadenar
     * [checkAndAwardAchievements] sin otra llamada de red redundante.
     */
    private suspend fun updateMemberStreak(householdId: String, member: MemberResponse): MemberResponse {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date

        val lastDateEpoch = member.lastStreakDate
        val todayEpoch = today.atStartOfDayIn(tz).toEpochMilliseconds()

        if (lastDateEpoch >= todayEpoch) {
            // Already counted today
            return member
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
            memberId = member.id,
            currentStreak = newStreak,
            bestStreak = newBest,
            lastStreakDate = todayEpoch
        )
        return member.copy(currentStreak = newStreak, bestStreak = newBest, lastStreakDate = todayEpoch)
    }

    /**
     * Check for newly unlocked achievements after completing a task.
     * Recibe [member] ya actualizado (puntos/racha post-premio) para no volver
     * a pedirlo a Firestore.
     */
    private suspend fun checkAndAwardAchievements(householdId: String, member: MemberResponse) {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val currentHour = now.toLocalDateTime(tz).hour

        val assignments = repo.getAllAssignments(householdId)
        val completedCount = assignments.count {
            it.memberId == member.id && it.status == "completed"
        }

        val alreadyUnlocked = repo.getMemberAchievements(householdId, member.id)

        val newlyUnlocked = AchievementChecker.checkNewAchievements(
            totalTasksCompleted = completedCount,
            totalPoints = member.totalPoints,
            currentStreak = member.currentStreak,
            lastCompletedHour = currentHour,
            alreadyUnlocked = alreadyUnlocked
        )

        for (achievementId in newlyUnlocked) {
            try {
                repo.addMemberAchievement(householdId, member.id, achievementId)
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

    // Tareas con un toggle de subtarea en curso: evita el lost-update de marcar
    // dos subtareas de la misma tarea con taps rápidos (la segunda llamada leía
    // la lista antes de que la primera escritura se confirmara y la sobrescribía).
    private val subtaskTogglesInFlight = mutableSetOf<String>()

    fun toggleSubtask(householdId: String, taskId: String, subtaskId: String) {
        if (taskId in subtaskTogglesInFlight) return
        subtaskTogglesInFlight += taskId
        screenModelScope.launch {
            try {
                val task = repo.getTask(householdId, taskId)
                val updatedSubtasks = task.subtasks.map { st ->
                    if (st.id == subtaskId) st.copy(completed = !st.completed) else st
                }
                repo.updateSubtasks(householdId, taskId, updatedSubtasks)
                buzz(HapticKind.SELECTION)
                // Refresh detail
                loadTaskDetail(householdId, taskId)
            } catch (_: Exception) {
                // Non-critical; detail will be stale until next load
            } finally {
                subtaskTogglesInFlight -= taskId
            }
        }
    }

    fun reset() {
        // Cancela una loadTasks() en vuelo: si no, puede resolver después de este
        // reset() y sobrescribir el Idle recién puesto con datos del hogar anterior
        // (reutilización del ScreenModel al cambiar de hogar).
        loadTasksJob?.cancel()
        _listState.value = TaskListUiState.Idle
        _detailState.value = TaskDetailUiState.Idle
        _actionState.value = TaskActionState.Idle
        _reassignState.value = TaskActionState.Idle
        _undoState.value = null
        _myAssignment.value = null
        _filter.value = TaskFilter.PENDING
        _sort.value = TaskSort.DEADLINE_ASC
        _selectedTagFilter.value = null
        _searchQuery.value = ""
        _currentMemberId.value = null
        _isOffline.value = false
        _allTags.value = emptyList()
        _commentsState.value = CommentsUiState.Idle
        _newCommentText.value = ""
        _calendarActionState.value = CalendarActionState.Idle
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
     * Sincroniza manualmente la asignación actual del usuario ("Sincronizar
     * ahora" en el detalle de tarea) cuando tiene fecha pero aún no tiene
     * evento en Calendar. Al terminar, recarga el detalle para que el estado
     * de sincronización mostrado se actualice con el `googleEventId` nuevo (o
     * con el flag "vinculado" si el token resultó revocado).
     */
    fun syncTaskToCalendarNow(householdId: String, task: TaskResponse) {
        val assignment = _myAssignment.value
        if (assignment == null) {
            // Antes retornaba en silencio, dejando el botón "Sincronizar ahora"
            // sin ningún feedback si el usuario lo pulsa antes de que se resuelva
            // su asignación (o si la tarea no está asignada a él).
            _calendarActionState.value = CalendarActionState.Error(
                "No se pudo determinar tu asignación para esta tarea"
            )
            return
        }
        screenModelScope.launch {
            _calendarActionState.value = CalendarActionState.Sending
            try {
                val household = repo.getHousehold(householdId)
                val synced = calendarSync.syncNow(
                    householdId = householdId,
                    householdName = household.name,
                    isPersonal = household.isPersonal,
                    assignment = assignment,
                    task = task
                )
                _calendarActionState.value = if (synced) {
                    CalendarActionState.Success
                } else {
                    CalendarActionState.Error("No se pudo sincronizar con Google Calendar")
                }
            } catch (e: Exception) {
                _calendarActionState.value = CalendarActionState.Error(
                    e.message ?: "Error al sincronizar con Google Calendar"
                )
            }
            loadTaskDetail(householdId, task.id)
        }
    }

    fun resetCalendarActionState() {
        _calendarActionState.value = CalendarActionState.Idle
    }

    /**
     * Reporta un fallo del flujo de "Vincular cuenta" de Google Calendar (fuera
     * del propio ScreenModel, ya que el consentimiento OAuth lo dispara
     * [org.taskhub.ui.models.GoogleAuthManager] desde la pantalla). Sin esto,
     * un `linkCalendar()` fallido solo apagaba el spinner y volvía a "No
     * vinculado" sin explicación, a diferencia de "Sincronizar ahora" que sí
     * reutiliza esta misma tarjeta de error.
     */
    fun setCalendarLinkError(message: String) {
        _calendarActionState.value = CalendarActionState.Error(message)
    }

    // ── Sync automático con Google Calendar (best-effort) ────

    /** Tras asignar/reasignar: crea eventos para las asignaciones mías con fecha. */
    private suspend fun syncCalendarOnAssigned(householdId: String, assignments: List<TaskAssignmentResponse>) {
        try {
            val household = repo.getHousehold(householdId)
            calendarSync.onTaskAssigned(householdId, household.name, household.isPersonal, assignments)
        } catch (_: Exception) {
            // Best-effort: se reintenta en el próximo reconcile.
        }
    }

    /** Antes de desasignar/borrar: borra los eventos de Calendar vinculados a las asignaciones actuales. */
    private suspend fun syncCalendarOnUnassigned(householdId: String, taskId: String) {
        try {
            val assignments = repo.getAssignments(householdId, taskId)
            for (assignment in assignments) {
                calendarSync.onTaskUnassigned(householdId, assignment)
            }
        } catch (_: Exception) {
            // Best-effort: el evento huérfano queda hasta el próximo reconcile.
        }
    }
}