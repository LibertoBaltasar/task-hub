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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.*
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.models.*
import org.taskhub.ui.theme.*

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
        val filter by model.filter.collectAsState()
        val sort by model.sort.collectAsState()
        val tagFilter by model.selectedTagFilter.collectAsState()
        val allTags by model.allTags.collectAsState()
        val currentMemberId by model.currentMemberId.collectAsState()

        LaunchedEffect(householdId) {
            model.setCurrentMemberId(memberId)
            model.loadTasks(householdId)
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
//  Day grouping models
// ────────────────────────────────────────────────────────────

private data class TaskWithDue(
    val task: TaskResponse,
    val dueDate: Long?,
    val isOverdue: Boolean,
    val assignments: List<TaskAssignmentResponse>
)

private data class TaskGroup(
    val label: String,
    val sortKey: Int,
    val dateKey: String,
    val isOverdue: Boolean,
    val isNoDate: Boolean,
    val tasks: List<TaskWithDue>
)

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
//  Group tasks by day
// ────────────────────────────────────────────────────────────

private fun groupTasksByDay(
    tasks: List<TaskWithDue>,
    todayStartEpoch: Long
): List<TaskGroup> {
    val tz = TimeZone.currentSystemDefault()
    val todayInstant = Instant.fromEpochMilliseconds(todayStartEpoch)
    val today = todayInstant.toLocalDateTime(tz).date

    // Group key per task
    val grouped = tasks.groupBy { task ->
        when {
            task.isOverdue -> "overdue"
            task.dueDate == null -> "nodate"
            else -> {
                val dueInstant = Instant.fromEpochMilliseconds(task.dueDate!!)
                val dueDate = dueInstant.toLocalDateTime(tz).date
                val daysDiff = dueDate.toEpochDays() - today.toEpochDays()
                when {
                    daysDiff == 0 -> "today"
                    daysDiff == 1 -> "tomorrow"
                    daysDiff in 2..6 -> "week_${dueDate.toEpochDays()}"
                    else -> "later_${dueDate.toEpochDays()}"
                }
            }
        }
    }

    return grouped.entries.map { (key, groupTasks) ->
        buildGroup(key, groupTasks, today, tz)
    }.sortedBy { it.sortKey }
}

private fun buildGroup(
    key: String,
    groupTasks: List<TaskWithDue>,
    today: LocalDate,
    tz: TimeZone
): TaskGroup {
    // Sort tasks within group by due date (ascending), then by points (descending)
    val sorted = groupTasks.sortedWith(
        compareBy<TaskWithDue> { it.dueDate ?: Long.MAX_VALUE }
            .thenByDescending { it.task.points }
    )

    return when {
        key == "overdue" -> TaskGroup(
            label = "Vencidas",
            sortKey = 0,
            dateKey = "overdue",
            isOverdue = true,
            isNoDate = false,
            tasks = sorted
        )
        key == "today" -> {
            val dow = today.dayOfWeek
            val dayStr = spanishDayName(dow)
            TaskGroup(
                label = "Hoy · $dayStr ${today.dayOfMonth}",
                sortKey = 1,
                dateKey = "today",
                isOverdue = false,
                isNoDate = false,
                tasks = sorted
            )
        }
        key == "tomorrow" -> {
            val tomorrow = today.plus(1, DateTimeUnit.DAY)
            val dow = tomorrow.dayOfWeek
            val dayStr = spanishDayName(dow)
            TaskGroup(
                label = "Mañana · $dayStr ${tomorrow.dayOfMonth}",
                sortKey = 2,
                dateKey = "tomorrow",
                isOverdue = false,
                isNoDate = false,
                tasks = sorted
            )
        }
        key.startsWith("week_") -> {
            val epochDays = key.removePrefix("week_").toInt()
            val date = LocalDate.fromEpochDays(epochDays)
            val dow = date.dayOfWeek
            val dayStr = spanishDayName(dow)
            TaskGroup(
                label = "$dayStr ${date.dayOfMonth}",
                sortKey = 3 + (epochDays - today.toEpochDays()),
                dateKey = key,
                isOverdue = false,
                isNoDate = false,
                tasks = sorted
            )
        }
        key.startsWith("later_") -> {
            val epochDays = key.removePrefix("later_").toInt()
            val date = LocalDate.fromEpochDays(epochDays)
            val dow = date.dayOfWeek
            val dayStr = spanishDayName(dow)
            val monthStr = spanishMonthName(date.month)
            TaskGroup(
                label = "$dayStr ${date.dayOfMonth} $monthStr",
                sortKey = 100 + (epochDays - today.toEpochDays()),
                dateKey = key,
                isOverdue = false,
                isNoDate = false,
                tasks = sorted
            )
        }
        else -> TaskGroup(
            label = "Sin fecha",
            sortKey = Int.MAX_VALUE,
            dateKey = "nodate",
            isOverdue = false,
            isNoDate = true,
            tasks = sorted
        )
    }
}

// ────────────────────────────────────────────────────────────
//  TaskListContent
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
    onRefresh: () -> Unit
) {
    val memberMap = state.members.associateBy { it.id }

    // Build assignment map: taskId -> list of assignments
    val assignmentsByTask = state.assignments.groupBy { it.taskId }

    // Compute today start for overdue detection
    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()
    val todayStartEpoch = now.toLocalDateTime(tz).date
        .atStartOfDayIn(tz).toEpochMilliseconds()

    // Filter and build TaskWithDue
    val tasksWithDue = state.tasks
        .filter { task ->
            val taskAssignments = assignmentsByTask[task.id] ?: emptyList()

            // Tag filter
            if (tagFilter != null && tagFilter !in task.tags) return@filter false

            // Status filter
            when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.PENDING -> taskAssignments.any { it.status == "assigned" } || taskAssignments.isEmpty()
                TaskFilter.COMPLETED -> taskAssignments.any { it.status == "completed" }
                TaskFilter.MINE -> {
                    val mid = currentMemberId ?: return@filter false
                    taskAssignments.any { it.memberId == mid && it.status == "assigned" }
                }
            }
        }
        .map { task ->
            val taskAssignments = assignmentsByTask[task.id] ?: emptyList()
            // Earliest active due date
            val dueDate = taskAssignments
                .filter { it.status == "assigned" && it.dueDate > 0 }
                .minOfOrNull { it.dueDate }
            val isOverdue = dueDate != null && dueDate < todayStartEpoch
            TaskWithDue(
                task = task,
                dueDate = dueDate,
                isOverdue = isOverdue,
                assignments = taskAssignments
            )
        }

    // Group tasks by day
    val groups = remember(tasksWithDue, filter, sort, tagFilter) {
        groupTasksByDay(tasksWithDue, todayStartEpoch)
    }

    // Collapse state per group — persist across recompositions
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

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

        if (tasksWithDue.isEmpty()) {
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
                            TaskFilter.PENDING -> "🎉 ¡No hay tareas pendientes!"
                            TaskFilter.COMPLETED -> "📭 No hay tareas completadas"
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
                val isCollapsed = collapsedGroups[group.dateKey] ?: false

                stickyHeader(key = "header_${group.dateKey}") {
                    GroupHeader(
                        label = group.label,
                        count = group.tasks.size,
                        isOverdue = group.isOverdue,
                        isNoDate = group.isNoDate,
                        isCollapsed = isCollapsed,
                        onToggle = { collapsedGroups[group.dateKey] = !isCollapsed }
                    )
                }

                item(key = "spacer_${group.dateKey}") {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Animated visibility for collapse/expand
                if (!isCollapsed) {
                    items(
                        items = group.tasks,
                        key = { "task_${it.task.id}" }
                    ) { taskWithDue ->
                        TaskCard(
                            task = taskWithDue.task,
                            assignments = taskWithDue.assignments,
                            memberMap = memberMap,
                            onClick = { onTaskClick(taskWithDue.task) }
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
//  TaskCard
// ────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(
    task: TaskResponse,
    assignments: List<TaskAssignmentResponse>,
    memberMap: Map<String, MemberResponse>,
    onClick: () -> Unit
) {
    val pendingCount = assignments.count { it.status == "assigned" }
    val completedCount = assignments.count { it.status == "completed" }
    val totalAssigned = assignments.size

    // Find earliest deadline
    val earliestDeadline = assignments
        .filter { it.status == "assigned" && it.dueDate > 0 }
        .minOfOrNull { it.dueDate }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title + Points
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
                    overflow = TextOverflow.Ellipsis
                )

                // Points badge
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (task.penaltyMode != null) Coral500 else Teal500
                ) {
                    Text(
                        text = "${task.points} pts",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Description (if present)
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

                // Recurrence days
                if (task.recurrenceDays.isNotEmpty()) {
                    val daysStr = task.recurrenceDays.map { day ->
                        when (day) {
                            1 -> "L"
                            2 -> "M"
                            3 -> "X"
                            4 -> "J"
                            5 -> "V"
                            6 -> "S"
                            7 -> "D"
                            else -> "?"
                        }
                    }.joinToString(",")
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Teal50
                    ) {
                        Text(
                            text = daysStr,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal700
                        )
                    }
                }

                // Tags
                task.tags.take(3).forEach { tag ->
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
                if (task.tags.size > 3) {
                    Text(
                        text = "+${task.tags.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Deadline + Assignments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Deadline
                if (earliestDeadline != null) {
                    val deadlineText = formatDeadline(earliestDeadline)
                    val isOverdue = earliestDeadline < Clock.System.now().toEpochMilliseconds()
                    Text(
                        text = "⏰ $deadlineText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    }

                    // Penalty indicator
                    if (task.penaltyMode != null) {
                        val penaltyLabel = when (task.penaltyMode) {
                            "fixed" -> "-${task.penaltyValue}pts"
                            "percentage" -> "-${task.penaltyValue}%"
                            else -> ""
                        }
                        Text(
                            text = "⚠️ $penaltyLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = Coral600
                        )
                    }
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
