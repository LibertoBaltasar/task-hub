package org.taskhub.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlin.random.Random
import org.taskhub.network.RecurrenceRules
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.models.*
import org.taskhub.ui.components.EmptyTasksIllustration
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
import org.taskhub.ui.components.ShimmerList
import org.taskhub.ui.components.shouldReduceMotion
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.theme.*
import org.taskhub.platform.shareText

// ────────────────────────────────────────────────────────────
//  TaskListScreen
// ────────────────────────────────────────────────────────────

data class TaskListScreen(
    val householdId: String,
    val memberId: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<TaskScreenModel>()
        val listState by model.listState.collectAsState()
        val actionState by model.actionState.collectAsState()
        val filter by model.filter.collectAsState()
        val sort by model.sort.collectAsState()
        val tagFilter by model.selectedTagFilter.collectAsState()
        val allTags by model.allTags.collectAsState()
        val currentMemberId by model.currentMemberId.collectAsState()
        val searchQuery by model.searchQuery.collectAsState()
        val undoState by model.undoState.collectAsState()
        val isOffline by model.isOffline.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        // ── Undo snackbar ────────────────────────────────────
        LaunchedEffect(undoState) {
            if (undoState != null) {
                val result = snackbarHostState.showSnackbar(
                    message = s("task_list_undo_snackbar_msg"),
                    actionLabel = s("task_list_undo_action"),
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    model.undoCompleteTask()
                    model.loadTasks(householdId)
                } else {
                    model.clearUndoState()
                }
            }
        }

        LaunchedEffect(householdId) {
            model.setCurrentMemberId(memberId)
            model.loadTasks(householdId)
        }

        // Reload when action completes
        LaunchedEffect(actionState) {
            val state = actionState
            if (state is TaskActionState.Success) {
                model.loadTasks(householdId)
                model.resetActionState()
            } else if (state is TaskActionState.Error) {
                // Sin esto, un fallo al completar una tarea (p.ej. red) no mostraba
                // ningún mensaje y la card de TaskCard se quedaba invisible/deshabilitada
                // para siempre (isCompleting/loadingTaskIds nunca se resetean por sí
                // solos — ver el TaskActionState.Error pasado a TaskListContent abajo).
                snackbarHostState.showSnackbar(
                    message = "❌ ${state.message}",
                    duration = SnackbarDuration.Short
                )
                model.resetActionState()
            }
        }

        // Settings dialog state
        var showSettings by remember { mutableStateOf(false) }

        // Pre-compute CSV export callback — needs access to list state
        val exportCsv: () -> Unit = {
            val state = listState
            if (state is TaskListUiState.Success) {
                val csv = model.generateCsv(state.tasks)
                shareText(csv, s("tasks_export_csv_title"))
            }
        }

        // Settings dialog
        if (showSettings) {
            Dialog(
                onDismissRequest = { showSettings = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.85f),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SettingsSheet(
                        callbacks = SettingsCallbacks(
                            onExportCsv = exportCsv,
                            onDismiss = { showSettings = false },
                            onEditProfile = { navigator.push(EditProfileScreen()) }
                        )
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = s("task_list_title"),
                    onBack = { navigator.pop() },
                    actions = {
                        IconButton(onClick = { model.loadTasks(householdId) }) {
                            Icon(Icons.Default.Refresh, contentDescription = s("task_list_refresh_content_desc"))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = s("profile_settings_label"))
                        }
                        TextButton(
                            onClick = { navigator.push(CreateTaskScreen(householdId, currentMemberId ?: "")) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(s("tasks_new"), fontWeight = FontWeight.Bold)
                        }
                    }
                )

                // Content
                // ── Offline banner ──────────────────────────────
                if (isOffline) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Coral100
                    ) {
                        Text(
                            text = s("task_list_offline_banner"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Coral800,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                when (val state = listState) {
                    is TaskListUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            ShimmerList(count = 5, itemHeight = 88.dp)
                        }
                    }

                    is TaskListUiState.Success -> {
                        TaskListContent(
                            state = state,
                            actionErrorSignal = actionState as? TaskActionState.Error,
                            filter = filter,
                            sort = sort,
                            tagFilter = tagFilter,
                            allTags = allTags,
                            searchQuery = searchQuery,
                            currentMemberId = currentMemberId,
                            s = s,
                            onFilterChange = { model.setFilter(it) },
                            onSortChange = { model.setSort(it) },
                            onTagFilterChange = { model.setTagFilter(it) },
                            onSearchQueryChange = { model.setSearchQuery(it) },
                            onTaskClick = { task ->
                                navigator.push(TaskDetailScreen(householdId, task.id))
                            },
                            onCompleteTask = { task ->
                                model.completeTask(householdId, task.id)
                            },
                            onRefresh = { model.loadTasks(householdId) },
                            onCreateFirstTask = {
                                navigator.push(CreateTaskScreen(householdId, currentMemberId ?: ""))
                            }
                        )
                    }

                    is TaskListUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "❌ ${state.message}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { model.loadTasks(householdId) }) {
                                    Text(s("tasks_retry"))
                                }
                            }
                        }
                    }

                    is TaskListUiState.Idle -> {}
                }
            }
                // ── Snackbar for undo ──────────────────────────
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Task is-due-today logic (local calculation, no instances)
// ────────────────────────────────────────────────────────────

/**
 * Modelo de datos simplificado para la UI: une la tarea con su estado calculado.
 * 
 * El estado (isDueToday, isCompletedToday, isOverdue) se calcula en cliente
 * a partir de task.frequency + task.lastCompletedDate + task.dueDate.
 * No hay "instancias" en Firestore — una tarea recurrente es UN solo documento.
 */
private data class TaskWithStatus(
    val task: TaskResponse,
    val isDueToday: Boolean,
    val isCompletedToday: Boolean,
    val isOverdue: Boolean
)

private data class TaskGroup(
    val label: String,
    val sortKey: Int,
    val dateKey: String,
    val isOverdue: Boolean,
    val isDueSoon: Boolean = false,
    val isNoDate: Boolean,
    val items: List<TaskWithStatus>
)

/**
 * Determines if a task is due today based on frequency + lastCompletedDate.
 *
 * - daily: always due (if not completed today)
 * - weekly: due if today matches recurrenceDays and not completed today
 * - monthly: due if not completed this month
 * - once: due if has dueDate and not completed yet
 */
private fun isTaskDueToday(task: TaskResponse, todayStartEpoch: Long): Boolean =
    RecurrenceRules.isDueToday(
        frequency = task.frequency,
        recurrenceDays = task.recurrenceDays,
        recurrenceDay = task.recurrenceDay,
        lastCompletedDate = task.lastCompletedDate,
        nowEpochMs = todayStartEpoch
    )

/**
 * Determines if a task was completed today.
 */
private fun isTaskCompletedToday(task: TaskResponse, todayStartEpoch: Long): Boolean {
    val lcd = task.lastCompletedDate ?: return false
    return lcd >= todayStartEpoch
}

// ────────────────────────────────────────────────────────────
//  Localized day-of-week helper
// ────────────────────────────────────────────────────────────

private fun localizedDayName(dayOfWeek: DayOfWeek, lang: String): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> AppStrings.get("recurrence_day_monday", lang)
    DayOfWeek.TUESDAY -> AppStrings.get("recurrence_day_tuesday", lang)
    DayOfWeek.WEDNESDAY -> AppStrings.get("recurrence_day_wednesday", lang)
    DayOfWeek.THURSDAY -> AppStrings.get("recurrence_day_thursday", lang)
    DayOfWeek.FRIDAY -> AppStrings.get("recurrence_day_friday", lang)
    DayOfWeek.SATURDAY -> AppStrings.get("recurrence_day_saturday", lang)
    DayOfWeek.SUNDAY -> AppStrings.get("recurrence_day_sunday", lang)
    else -> ""
}

// ────────────────────────────────────────────────────────────
//  Group tasks by status (not instances — calculated locally)
// ────────────────────────────────────────────────────────────

/**
 * Comparador según la opción de orden elegida por el usuario en el menú
 * desplegable. Antes se ignoraba por completo: el icono del menú cambiaba
 * pero la lista siempre se ordenaba por fecha límite + puntos.
 */
private fun taskComparator(sort: TaskSort): Comparator<TaskWithStatus> = when (sort) {
    TaskSort.DEADLINE_ASC -> compareBy { if (it.task.dueDate > 0) it.task.dueDate else Long.MAX_VALUE }
    TaskSort.DEADLINE_DESC -> compareByDescending { it.task.dueDate }
    TaskSort.POINTS_DESC -> compareByDescending { it.task.points }
    TaskSort.CREATED_DESC -> compareByDescending { it.task.createdAt }
}

private fun groupTasksByStatus(
    items: List<TaskWithStatus>,
    sort: TaskSort,
    lang: String
): List<TaskGroup> {
    val dueItems = items.filter { it.isDueToday && !it.isCompletedToday }
    val overdueItems = dueItems.filter { it.isOverdue }
    val pendingToday = dueItems.filter { !it.isOverdue }
    val completedToday = items.filter { it.isCompletedToday }

    val groups = mutableListOf<TaskGroup>()
    val comparator = taskComparator(sort)

    // Overdue
    if (overdueItems.isNotEmpty()) {
        val sorted = overdueItems.sortedWith(comparator)
        groups.add(TaskGroup(
            label = AppStrings.get("tasks_overdue", lang),
            sortKey = 0,
            dateKey = "overdue",
            isOverdue = true,
            isNoDate = false,
            items = sorted
        ))
    }

    // Due today
    if (pendingToday.isNotEmpty()) {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val dow = today.dayOfWeek
        val dayStr = localizedDayName(dow, lang)
        val sorted = pendingToday.sortedWith(comparator)
        groups.add(TaskGroup(
            label = AppStrings.get("task_list_today_header", lang)
                .replace("%1", dayStr)
                .replace("%2", today.dayOfMonth.toString()),
            sortKey = 1,
            dateKey = "today",
            isOverdue = false,
            isDueSoon = true,
            isNoDate = false,
            items = sorted
        ))
    }

    // Completed today
    if (completedToday.isNotEmpty()) {
        val sorted = completedToday.sortedByDescending { it.task.lastCompletedDate ?: 0 }
        groups.add(TaskGroup(
            label = AppStrings.get("tasks_completed_today", lang),
            sortKey = 99,
            dateKey = "completed_today",
            isOverdue = false,
            isNoDate = false,
            items = sorted
        ))
    }

    return groups
}

// ────────────────────────────────────────────────────────────
//  TaskListContent (task-based — no instances)
// ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskListContent(
    state: TaskListUiState.Success,
    /** No-null cada vez que completar una tarea falla: limpia los spinners "en curso". */
    actionErrorSignal: TaskActionState.Error?,
    filter: TaskFilter,
    sort: TaskSort,
    tagFilter: String?,
    allTags: List<String>,
    searchQuery: String,
    currentMemberId: String?,
    s: (String) -> String,
    onFilterChange: (TaskFilter) -> Unit,
    onSortChange: (TaskSort) -> Unit,
    onTagFilterChange: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTaskClick: (TaskResponse) -> Unit,
    onCompleteTask: (TaskResponse) -> Unit,
    onRefresh: () -> Unit,
    onCreateFirstTask: () -> Unit
) {
    val taskMap = state.tasks.associateBy { it.id }
    val memberMap = state.members.associateBy { it.id }
    val assignmentsByTask = state.assignments.groupBy { it.taskId }
    val reduceMotion = shouldReduceMotion()
    val lang = LocalAppSettings.current.currentLanguage

    // Compute today start for overdue detection and due-today calculation
    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()
    val todayStartEpoch = now.toLocalDateTime(tz).date
        .atStartOfDayIn(tz).toEpochMilliseconds()

    // Filter tasks based on filter + tag
    val filteredTasks = state.tasks.filter { task ->
        // Tag filter
        if (tagFilter != null && tagFilter !in task.tags) return@filter false

        // Status filter
        when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.PENDING -> {
                // Pending = due today AND not completed today
                val due = isTaskDueToday(task, todayStartEpoch)
                val done = isTaskCompletedToday(task, todayStartEpoch)
                due && !done
            }
            TaskFilter.COMPLETED -> isTaskCompletedToday(task, todayStartEpoch)
            TaskFilter.MINE -> {
                val mid = currentMemberId ?: return@filter false
                val taskAssignments = assignmentsByTask[task.id] ?: emptyList()
                taskAssignments.any { it.memberId == mid && it.status == "assigned" }
            }
        }
    }

    // Text search filter (client-side, after status filter, before groupByStatus)
    val searchFilteredTasks = if (searchQuery.isBlank()) {
        filteredTasks
    } else {
        val q = searchQuery.trim().lowercase()
        filteredTasks.filter { task ->
            task.title.lowercase().contains(q) ||
            task.description.lowercase().contains(q)
        }
    }

    // Build TaskWithStatus list
    val tasksWithStatus = searchFilteredTasks.map { task ->
        val due = isTaskDueToday(task, todayStartEpoch)
        val done = isTaskCompletedToday(task, todayStartEpoch)
        val isOverdue = task.dueDate > 0 && task.dueDate < todayStartEpoch && task.lastCompletedDate == null
        TaskWithStatus(
            task = task,
            isDueToday = due,
            isCompletedToday = done,
            isOverdue = isOverdue
        )
    }

    // Group by status
    val groups = remember(tasksWithStatus, filter, sort, tagFilter, lang) {
        groupTasksByStatus(tasksWithStatus, sort, lang)
    }

    // Collapse state per group
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // Track which tasks are being completed (loading state)
    val loadingTaskIds = remember { mutableStateMapOf<String, Boolean>() }

    // Si completar una tarea falla, la card correspondiente quedaba con su
    // spinner/animación de salida bloqueados para siempre (nada los reseteaba
    // ante un error, solo ante éxito) — ver `isCompleting`/`onComplete` en
    // TaskCard más abajo, que observan `actionErrorSignal` para recuperarse.
    LaunchedEffect(actionErrorSignal) {
        if (actionErrorSignal != null) loadingTaskIds.clear()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Filter chips
        item {
            FilterChipsRow(
                currentFilter = filter,
                currentSort = sort,
                tagFilter = tagFilter,
                allTags = allTags,
                s = s,
                onFilterChange = onFilterChange,
                onSortChange = onSortChange,
                onTagFilterChange = onTagFilterChange
            )
        }

        // Search bar
        item {
            SearchBar(
                query = searchQuery,
                s = s,
                onQueryChange = onSearchQueryChange
            )
        }

        // ── DEBUG: always show task count (only in debug builds) ──
        if (org.taskhub.platform.DebugFlags.isEnabled) {
            item {
                Text(
                    text = "DEBUG: ${state.tasks.size} tasks, filter=$filter, groups=${groups.size}, tagFilter=$tagFilter",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        if (state.tasks.isEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyTasksIllustration()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            s("task_list_empty_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s("task_list_empty_subtitle"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onCreateFirstTask,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(s("task_list_create_first"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else if (tasksWithStatus.isEmpty() || groups.isEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = when (filter) {
                            TaskFilter.PENDING -> s("task_list_filter_empty_pending").replace("%d", state.tasks.size.toString())
                            TaskFilter.COMPLETED -> s("tasks_empty_completed")
                            TaskFilter.MINE -> s("tasks_empty_mine")
                            TaskFilter.ALL -> s("tasks_empty_all")
                        },
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            groups.forEach { group ->
                val isCollapsed = collapsedGroups[group.dateKey] ?: (group.dateKey == "completed_today")

                stickyHeader(key = "header_${group.dateKey}") {
                    GroupHeader(
                        label = group.label,
                        count = group.items.size,
                        isOverdue = group.isOverdue,
                        isDueSoon = group.isDueSoon,
                        isNoDate = group.isNoDate,
                        isCollapsed = isCollapsed,
                        onToggle = { collapsedGroups[group.dateKey] = !isCollapsed }
                    )
                }

                item(key = "spacer_${group.dateKey}") {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!isCollapsed) {
                    items(
                        items = group.items,
                        // Sin sufijo de grupo: misma key al completar una tarea permite que
                        // Compose la reconozca como el mismo ítem moviéndose de grupo (en vez
                        // de destruirla y recrearla), habilitando animateItem() para el traslado.
                        key = { "task_${it.task.id}" }
                    ) { item ->
                        TaskCard(
                            item = item,
                            assignments = assignmentsByTask[item.task.id] ?: emptyList(),
                            memberMap = memberMap,
                            isLoading = loadingTaskIds[item.task.id] == true,
                            hasError = actionErrorSignal != null,
                            onClick = { onTaskClick(item.task) },
                            onComplete = if (item.isDueToday && !item.isCompletedToday) {
                                {
                                    loadingTaskIds[item.task.id] = true
                                    onCompleteTask(item.task)
                                }
                            } else null,
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Spacer at bottom
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ────────────────────────────────────────────────────────────
//  TaskCard – shows a task with quick-complete
// ────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(
    item: TaskWithStatus,
    assignments: List<TaskAssignmentResponse>,
    memberMap: Map<String, MemberResponse>,
    isLoading: Boolean,
    hasError: Boolean,
    onClick: () -> Unit,
    onComplete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val task = item.task
    val pendingCount = assignments.count { it.status == "assigned" }
    val completedCount = assignments.count { it.status == "completed" }
    val totalAssigned = assignments.size
    val isDone = item.isCompletedToday
    val reduceMotion = shouldReduceMotion()
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    // Al pulsar "Hecho" primero se anima la card (fade out + scale down) y solo
    // entonces se dispara la finalización real, para que la tarea no desaparezca
    // bruscamente al pasar al grupo "Completadas hoy".
    var isCompleting by remember(task.id) { mutableStateOf(false) }
    LaunchedEffect(isDone) {
        // La card puede reutilizarse (misma key) al moverse de grupo — una vez el
        // backend confirma el completado, se libera la animación de salida.
        if (isDone) isCompleting = false
    }
    LaunchedEffect(hasError) {
        // Sin esto, un fallo de red al completar dejaba la card en animación de
        // salida permanente (cardAlpha=0f, clickable deshabilitado) y ningún
        // camino la recuperaba, porque solo `isDone` (éxito) reseteaba isCompleting.
        if (hasError) isCompleting = false
    }
    LaunchedEffect(isCompleting) {
        if (isCompleting) {
            if (!reduceMotion) delay(260)
            onComplete?.invoke()
        }
    }
    val cardScale by animateFloatAsState(
        targetValue = if (isCompleting) 0.92f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260),
        label = "taskCardScale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isCompleting) 0f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260),
        label = "taskCardAlpha"
    )

    Box(modifier = modifier) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = cardAlpha
            }
            .clickable(enabled = !isCompleting, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title + Complete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface
                )

                if (!isDone && onComplete != null) {
                    if (isCompleting) {
                        AnimatedCheckmark(reduceMotion = reduceMotion)
                    } else {
                        Button(
                            onClick = { isCompleting = true },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(s("task_detail_mark_done"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else if (isDone) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Teal100
                    ) {
                        Text(
                            text = "✅",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Description
            if (task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Frequency + Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Points badge
                PointsBadge(text = "${task.points} ${s("transfer_points_suffix")}")

                // Frequency badge
                val freqLabel = when (task.frequency) {
                    "daily" -> s("task_list_freq_daily")
                    "weekly" -> s("task_list_freq_weekly")
                    "monthly" -> if (task.recurrenceDay != null) s("task_list_freq_monthly_day").replace("%d", task.recurrenceDay.toString()) else s("task_list_freq_monthly")
                    else -> s("task_list_freq_once")
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Teal50
                ) {
                    Text(
                        text = freqLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Teal800
                    )
                }

                // Tags
                task.tags.take(2).forEach { tag ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Coral50
                    ) {
                        Text(
                            text = tag,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Coral700
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Due date / completion status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Due date or completion info
                if (isDone && task.lastCompletedDate != null) {
                    Text(
                        text = "✅ ${formatDeadline(task.lastCompletedDate!!)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.semanticColors.success
                    )
                } else if (task.dueDate > 0) {
                    val deadlineText = formatDeadline(task.dueDate)
                    Text(
                        text = "⏰ $deadlineText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isOverdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = s("task_list_no_deadline"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Assignment status
                if (totalAssigned > 0) {
                    val allDone = completedCount == totalAssigned
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (allDone) MaterialTheme.semanticColors.successContainer else Teal100
                    ) {
                        Text(
                            text = "✅ $completedCount/$totalAssigned",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allDone) MaterialTheme.semanticColors.onSuccessContainer else Teal800,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Show rotation assignee for today
                    val rotationMemberId = getTodayAssigneeMemberId(task)
                    if (rotationMemberId != null) {
                        val rotationMember = memberMap[rotationMemberId]
                        if (rotationMember != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Coral50
                            ) {
                                Text(
                                    text = "🧑 ${rotationMember.displayName}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Coral700
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // Confeti mínimo mientras la card se anima de salida al completar.
        if (isCompleting && !reduceMotion) {
            ConfettiOverlay(modifier = Modifier.matchParentSize())
        }
    }
}

// ────────────────────────────────────────────────────────────
//  AnimatedCheckmark – ✅ con bounce al completar una tarea
// ────────────────────────────────────────────────────────────

@Composable
private fun AnimatedCheckmark(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }
    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkBounce"
    )
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.small,
        color = Teal100
    ) {
        Text(
            text = "✅",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

// ────────────────────────────────────────────────────────────
//  ConfettiOverlay – partículas mínimas al completar (sin librerías)
// ────────────────────────────────────────────────────────────

private data class ConfettiParticle(
    val startX: Float,
    val colorIndex: Int,
    val fallDelay: Float,
    val horizontalDrift: Float,
    val rotationSpeed: Float
)

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val particles = remember {
        List(12) {
            ConfettiParticle(
                startX = Random.nextFloat(),
                colorIndex = Random.nextInt(4),
                fallDelay = Random.nextFloat() * 0.2f,
                horizontalDrift = (Random.nextFloat() - 0.5f) * 0.5f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 540f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1000, easing = LinearEasing))
    }
    val colors = listOf(Teal500, Coral500, Teal300, Coral300)

    Canvas(modifier = modifier) {
        val particleSize = 6.dp.toPx()
        particles.forEach { particle ->
            val t = ((progress.value - particle.fallDelay) / (1f - particle.fallDelay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val x = (particle.startX + particle.horizontalDrift * t) * size.width
            val y = t * size.height
            val alpha = 1f - t
            rotate(degrees = particle.rotationSpeed * t, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[particle.colorIndex].copy(alpha = alpha),
                    topLeft = Offset(x - particleSize / 2f, y - particleSize / 2f),
                    size = Size(particleSize, particleSize)
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  GroupHeader – sticky header for each day group
// ────────────────────────────────────────────────────────────

@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    isOverdue: Boolean,
    isDueSoon: Boolean = false,
    isNoDate: Boolean,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
    val semantic = MaterialTheme.semanticColors
    val backgroundColor = when {
        isOverdue -> Coral100
        isDueSoon -> semantic.warningContainer
        isNoDate -> MaterialTheme.colorScheme.surfaceVariant
        else -> Teal50
    }
    val contentColor = when {
        isOverdue -> Coral800
        isDueSoon -> semantic.onWarningContainer
        isNoDate -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Teal800
    }
    val dotColor = when {
        isOverdue -> Coral600
        isDueSoon -> semantic.warning
        isNoDate -> MaterialTheme.colorScheme.outline
        else -> Teal600
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored dot indicator
            Surface(
                modifier = Modifier.size(8.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = dotColor
            ) {}

            Spacer(modifier = Modifier.width(10.dp))

            // Label + count
            Text(
                text = "$label ($count)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            // Overdue badge
            if (isOverdue) {
                PointsBadge(text = "!")
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Collapse/expand icon
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown
                             else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isCollapsed) s("household_task_section_expand") else s("household_task_section_collapse"),
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  SearchBar
// ────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    s: (String) -> String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        placeholder = { Text(s("task_list_search_placeholder")) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = s("task_list_search_content_desc"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = s("task_list_clear_search_content_desc"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal600,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = Teal600
        ),
        shape = MaterialTheme.shapes.medium
    )
}

// ────────────────────────────────────────────────────────────
//  FilterChipsRow
// ────────────────────────────────────────────────────────────

@Composable
private fun FilterChipsRow(
    currentFilter: TaskFilter,
    currentSort: TaskSort,
    tagFilter: String?,
    allTags: List<String>,
    s: (String) -> String,
    onFilterChange: (TaskFilter) -> Unit,
    onSortChange: (TaskSort) -> Unit,
    onTagFilterChange: (String?) -> Unit
) {
    var expandedSort by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Status filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentFilter == TaskFilter.PENDING,
                onClick = { onFilterChange(TaskFilter.PENDING) },
                label = { Text(s("task_list_filter_pending")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal100,
                    selectedLabelColor = Teal900
                )
            )
            FilterChip(
                selected = currentFilter == TaskFilter.COMPLETED,
                onClick = { onFilterChange(TaskFilter.COMPLETED) },
                label = { Text(s("task_list_filter_completed")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal100,
                    selectedLabelColor = Teal900
                )
            )
            FilterChip(
                selected = currentFilter == TaskFilter.MINE,
                onClick = { onFilterChange(TaskFilter.MINE) },
                label = { Text(s("task_list_filter_mine")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal100,
                    selectedLabelColor = Teal900
                )
            )
            FilterChip(
                selected = currentFilter == TaskFilter.ALL,
                onClick = { onFilterChange(TaskFilter.ALL) },
                label = { Text(s("task_list_filter_all")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal100,
                    selectedLabelColor = Teal900
                )
            )
        }

        // Tag filter + Sort
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allTags.isNotEmpty()) {
                // Tag filter dropdown
                var tagExpanded by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = tagFilter != null,
                        onClick = { tagExpanded = true },
                        label = { Text(tagFilter ?: s("create_task_section_tags")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Coral100,
                            selectedLabelColor = Coral800
                        )
                    )
                    DropdownMenu(
                        expanded = tagExpanded,
                        onDismissRequest = { tagExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(s("task_list_filter_all")) },
                            onClick = {
                                onTagFilterChange(null)
                                tagExpanded = false
                            }
                        )
                        allTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag) },
                                onClick = {
                                    onTagFilterChange(tag)
                                    tagExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Sort button
            Box {
                TextButton(onClick = { expandedSort = true }) {
                    Text(
                        text = when (currentSort) {
                            TaskSort.DEADLINE_ASC -> "📅 ↑"
                            TaskSort.DEADLINE_DESC -> "📅 ↓"
                            TaskSort.POINTS_DESC -> "⭐"
                            TaskSort.CREATED_DESC -> "🕐"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                DropdownMenu(
                    expanded = expandedSort,
                    onDismissRequest = { expandedSort = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(s("task_list_sort_deadline_asc")) },
                        onClick = {
                            onSortChange(TaskSort.DEADLINE_ASC)
                            expandedSort = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(s("task_list_sort_deadline_desc")) },
                        onClick = {
                            onSortChange(TaskSort.DEADLINE_DESC)
                            expandedSort = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(s("task_list_sort_points")) },
                        onClick = {
                            onSortChange(TaskSort.POINTS_DESC)
                            expandedSort = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(s("task_list_sort_recent")) },
                        onClick = {
                            onSortChange(TaskSort.CREATED_DESC)
                            expandedSort = false
                        }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Helpers
// ────────────────────────────────────────────────────────────

private fun formatDeadline(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val min = local.minute.toString().padStart(2, '0')
    return "$day/$month ${hour}:${min}"
}

/**
 * Returns the member ID responsible for this task today based on assignmentRotation.
 * Returns null if no rotation is defined.
 */
private fun getTodayAssigneeMemberId(task: TaskResponse): String? {
    if (task.assignmentRotation.isEmpty()) return null

    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()
    val today = now.toLocalDateTime(tz).date
    val todayDow = today.dayOfWeek.ordinal + 1 // 1=Monday..7=Sunday

    val slot = task.assignmentRotation.find { it.dayOfWeek == todayDow }
    return slot?.memberId
}
