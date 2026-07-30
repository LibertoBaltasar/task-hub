package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
//  TaskListContent
// ────────────────────────────────────────────────────────────

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

    // Filter and sort
    val filteredTasks = state.tasks
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
        .let { tasks ->
            when (sort) {
                TaskSort.DEADLINE_ASC -> {
                    tasks.sortedBy { task ->
                        val ass = assignmentsByTask[task.id] ?: emptyList()
                        ass.filter { it.status == "assigned" }
                            .minOfOrNull { it.dueDate } ?: Long.MAX_VALUE
                    }
                }
                TaskSort.DEADLINE_DESC -> {
                    tasks.sortedByDescending { task ->
                        val ass = assignmentsByTask[task.id] ?: emptyList()
                        ass.filter { it.status == "assigned" }
                            .maxOfOrNull { it.dueDate } ?: 0L
                    }
                }
                TaskSort.POINTS_DESC -> tasks.sortedByDescending { it.points }
                TaskSort.CREATED_DESC -> tasks.sortedByDescending { it.createdAt }
            }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

        if (filteredTasks.isEmpty()) {
            item {
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
            items(filteredTasks) { task ->
                TaskCard(
                    task = task,
                    assignments = assignmentsByTask[task.id] ?: emptyList(),
                    memberMap = memberMap,
                    onClick = { onTaskClick(task) }
                )
            }
        }

        // Spacer at bottom
        item { Spacer(modifier = Modifier.height(16.dp)) }
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
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val min = local.minute.toString().padStart(2, '0')
    return "$day/$month ${hour}:${min}"
}
