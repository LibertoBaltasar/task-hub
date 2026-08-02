package org.taskhub.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.datetime.*
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.models.*
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
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

        LaunchedEffect(householdId) {
            model.setCurrentMemberId(memberId)
            model.loadTasks(householdId)
        }

        // Reload when returning to screen (lifecycle resume)  
        // or when action completes
        LaunchedEffect(householdId) {
            model.setCurrentMemberId(memberId)
            model.loadTasks(householdId)
        }

        // Reload when action completes
        LaunchedEffect(actionState) {
            if (actionState is TaskActionState.Success) {
                model.loadTasks(householdId)
                model.resetActionState()
            }
        }

        // Settings dialog state
        var showSettings by remember { mutableStateOf(false) }
        val appSettings = LocalAppSettings.current

        // Pre-compute CSV export callback — needs access to list state
        val exportCsv: () -> Unit = {
            val state = listState
            if (state is TaskListUiState.Success) {
                val csv = model.generateCsv(state.tasks)
                shareText(csv, "Tareas Task Hub")
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
                            onDismiss = { showSettings = false }
                        )
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Teal600,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { navigator.pop() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("← Volver")
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "📋 Tareas",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.weight(1f))

                        // Settings icon
                        TextButton(
                            onClick = { showSettings = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("⚙️", style = MaterialTheme.typography.titleLarge)
                        }

                        TextButton(
                            onClick = { navigator.push(CreateTaskScreen(householdId, currentMemberId ?: "")) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("+ Nueva", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Content
                when (val state = listState) {
                    is TaskListUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Teal600)
                        }
                    }

                    is TaskListUiState.Success -> {
                        TaskListContent(
                            state = state,
                            filter = filter,
                            sort = sort,
                            tagFilter = tagFilter,
                            allTags = allTags,
                            currentMemberId = currentMemberId,
                            onFilterChange = { model.setFilter(it) },
                            onSortChange = { model.setSort(it) },
                            onTagFilterChange = { model.setTagFilter(it) },
                            onTaskClick = { task ->
                                navigator.push(TaskDetailScreen(householdId, task.id))
                            },
                            onCompleteTask = { task ->
                                model.completeTask(householdId, task.id)
                            },
                            onRefresh = { model.loadTasks(householdId) }
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
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    is TaskListUiState.Idle -> {}
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Task is-due-today logic (local calculation, no instances)
// ────────────────────────────────────────────────────────────

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
private fun isTaskDueToday(task: TaskResponse, todayStartEpoch: Long): Boolean {
    val tz = TimeZone.currentSystemDefault()
    val todayInstant = Instant.fromEpochMilliseconds(todayStartEpoch)
    val today = todayInstant.toLocalDateTime(tz).date

    when (task.frequency) {
        "daily" -> {
            // Daily tasks are always due. Check if completed today.
            val lcd = task.lastCompletedDate
            if (lcd == null) return true
            val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
            return lcdDate != today
        }
        "weekly" -> {
            val todayDow = today.dayOfWeek.ordinal + 1 // 1=Monday
            if (task.recurrenceDays.isNotEmpty() && todayDow !in task.recurrenceDays) {
                return false // Not a matching day
            }
            // Check if completed today
            val lcd = task.lastCompletedDate
            if (lcd == null) return true
            val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
            return lcdDate != today
        }
        "monthly" -> {
            val lcd = task.lastCompletedDate
            if (lcd == null) return true
            val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
            return lcdDate.month != today.month || lcdDate.year != today.year
        }
        "once" -> {
            // Due if not completed yet (with or without dueDate)
            return task.lastCompletedDate == null
        }
        else -> return false
    }
}

/**
 * Determines if a task was completed today.
 */
private fun isTaskCompletedToday(task: TaskResponse, todayStartEpoch: Long): Boolean {
    val lcd = task.lastCompletedDate ?: return false
    return lcd >= todayStartEpoch
}

// ────────────────────────────────────────────────────────────
//  Spanish day-of-week helper
// ────────────────────────────────────────────────────────────

private fun spanishDayName(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.TUESDAY -> "Martes"
    DayOfWeek.WEDNESDAY -> "Miércoles"
    DayOfWeek.THURSDAY -> "Jueves"
    DayOfWeek.FRIDAY -> "Viernes"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

private fun spanishMonthName(month: Month): String = when (month) {
    Month.JANUARY -> "Ene"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Abr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Ago"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dic"
}

// ────────────────────────────────────────────────────────────
//  Group tasks by status (not instances — calculated locally)
// ────────────────────────────────────────────────────────────

private fun groupTasksByStatus(
    items: List<TaskWithStatus>
): List<TaskGroup> {
    val dueItems = items.filter { it.isDueToday && !it.isCompletedToday }
    val overdueItems = dueItems.filter { it.isOverdue }
    val pendingToday = dueItems.filter { !it.isOverdue }
    val completedToday = items.filter { it.isCompletedToday }

    val groups = mutableListOf<TaskGroup>()

    // Overdue
    if (overdueItems.isNotEmpty()) {
        val sorted = overdueItems.sortedWith(
            compareBy<TaskWithStatus> { it.task.dueDate }
                .thenByDescending { it.task.points }
        )
        groups.add(TaskGroup(
            label = "Vencidas",
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
        val dayStr = spanishDayName(dow)
        val sorted = pendingToday.sortedWith(
            compareBy<TaskWithStatus> { it.task.dueDate }
                .thenByDescending { it.task.points }
        )
        groups.add(TaskGroup(
            label = "Hoy · $dayStr ${today.dayOfMonth}",
            sortKey = 1,
            dateKey = "today",
            isOverdue = false,
            isNoDate = false,
            items = sorted
        ))
    }

    // Completed today
    if (completedToday.isNotEmpty()) {
        val sorted = completedToday.sortedByDescending { it.task.lastCompletedDate ?: 0 }
        groups.add(TaskGroup(
            label = "✅ Completadas hoy",
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
    filter: TaskFilter,
    sort: TaskSort,
    tagFilter: String?,
    allTags: List<String>,
    currentMemberId: String?,
    onFilterChange: (TaskFilter) -> Unit,
    onSortChange: (TaskSort) -> Unit,
    onTagFilterChange: (String?) -> Unit,
    onTaskClick: (TaskResponse) -> Unit,
    onCompleteTask: (TaskResponse) -> Unit,
    onRefresh: () -> Unit
) {
    val taskMap = state.tasks.associateBy { it.id }
    val memberMap = state.members.associateBy { it.id }
    val assignmentsByTask = state.assignments.groupBy { it.taskId }

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

    // Build TaskWithStatus list
    val tasksWithStatus = filteredTasks.map { task ->
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
    val groups = remember(tasksWithStatus, filter, sort, tagFilter) {
        groupTasksByStatus(tasksWithStatus)
    }

    // Collapse state per group
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // Track which tasks are being completed (loading state)
    val loadingTaskIds = remember { mutableStateMapOf<String, Boolean>() }

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
                onFilterChange = onFilterChange,
                onSortChange = onSortChange,
                onTagFilterChange = onTagFilterChange
            )
        }

        if (tasksWithStatus.isEmpty() || groups.isEmpty()) {
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
                            TaskFilter.PENDING -> "🎉 ¡No hay tareas pendientes! (${state.tasks.size} cargadas)"
                            TaskFilter.COMPLETED -> "📋 No hay tareas completadas hoy"
                            TaskFilter.MINE -> "👤 No tienes tareas asignadas"
                            TaskFilter.ALL -> "📋 No hay tareas aún. ¡Crea la primera!"
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
                        key = { "task_${it.task.id}_${group.dateKey}" }
                    ) { item ->
                        TaskCard(
                            item = item,
                            assignments = assignmentsByTask[item.task.id] ?: emptyList(),
                            memberMap = memberMap,
                            isLoading = loadingTaskIds[item.task.id] == true,
                            onClick = { onTaskClick(item.task) },
                            onComplete = if (item.isDueToday && !item.isCompletedToday) {
                                {
                                    loadingTaskIds[item.task.id] = true
                                    onCompleteTask(item.task)
                                }
                            } else null
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
    onClick: () -> Unit,
    onComplete: (() -> Unit)?
) {
    val task = item.task
    val pendingCount = assignments.count { it.status == "assigned" }
    val completedCount = assignments.count { it.status == "completed" }
    val totalAssigned = assignments.size
    val isDone = item.isCompletedToday

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    Button(
                        onClick = onComplete,
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Teal600
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
                            Text("✅ Hecho", style = MaterialTheme.typography.labelMedium)
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
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (task.penaltyMode != null) Coral500 else Teal500
                ) {
                    Text(
                        text = "${task.points} pts",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Frequency badge
                val freqLabel = when (task.frequency) {
                    "daily" -> "🔄 Diaria"
                    "weekly" -> "📅 Semanal"
                    "monthly" -> "📆 Mensual"
                    else -> "• Una vez"
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Teal50
                ) {
                    Text(
                        text = freqLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Teal700
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
                        color = Teal600
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
                        text = "Sin deadline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Assignment status
                if (totalAssigned > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Teal100
                    ) {
                        Text(
                            text = "✅ $completedCount/$totalAssigned",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal800,
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
}

// ────────────────────────────────────────────────────────────
//  GroupHeader – sticky header for each day group
// ────────────────────────────────────────────────────────────

@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    isOverdue: Boolean,
    isNoDate: Boolean,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor = when {
        isOverdue -> Coral100
        isNoDate -> MaterialTheme.colorScheme.surfaceVariant
        else -> Teal50
    }
    val contentColor = when {
        isOverdue -> Coral800
        isNoDate -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Teal800
    }
    val dotColor = when {
        isOverdue -> Coral600
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
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Coral500
                ) {
                    Text(
                        text = "!",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Collapse/expand icon
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown
                             else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isCollapsed) "Expandir" else "Colapsar",
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
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
                label = { Text("Pendientes") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal100,
                    selectedLabelColor = Teal900
                )
            )
            FilterChip(
                selected = currentFilter == TaskFilter.COMPLETED,
                onClick = { onFilterChange(TaskFilter.COMPLETED) },
                label = { Text("Completadas") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal100,
                    selectedLabelColor = Teal900
                )
            )
            FilterChip(
                selected = currentFilter == TaskFilter.MINE,
                onClick = { onFilterChange(TaskFilter.MINE) },
                label = { Text("Mías") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal100,
                    selectedLabelColor = Teal900
                )
            )
            FilterChip(
                selected = currentFilter == TaskFilter.ALL,
                onClick = { onFilterChange(TaskFilter.ALL) },
                label = { Text("Todas") },
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
                        label = { Text(tagFilter ?: "🏷️ Etiquetas") },
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
                            text = { Text("Todas") },
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
                        text = { Text("Deadline más próximo") },
                        onClick = {
                            onSortChange(TaskSort.DEADLINE_ASC)
                            expandedSort = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Deadline más lejano") },
                        onClick = {
                            onSortChange(TaskSort.DEADLINE_DESC)
                            expandedSort = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Más puntos") },
                        onClick = {
                            onSortChange(TaskSort.POINTS_DESC)
                            expandedSort = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Más recientes") },
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
