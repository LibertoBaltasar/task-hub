package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskInstanceResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.models.*
import org.taskhub.ui.theme.*

// ────────────────────────────────────────────────────────────
//  TaskDetailScreen
// ────────────────────────────────────────────────────────────

data class TaskDetailScreen(
    val householdId: String,
    val taskId: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<TaskScreenModel>()
        val detailState by model.detailState.collectAsState()
        val actionState by model.actionState.collectAsState()

        LaunchedEffect(taskId) {
            model.resetActionState()
            model.loadTaskDetail(householdId, taskId)
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
                            Text("← Tareas")
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "📋 Detalle",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.weight(1f))

                        Spacer(Modifier.width(64.dp))
                    }
                }

                when (val state = detailState) {
                    is TaskDetailUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Teal600)
                        }
                    }

                    is TaskDetailUiState.Success -> {
                        val memberMap = state.members.associateBy { it.id }
                        TaskDetailContent(
                            task = state.task,
                            assignments = state.assignments,
                            instances = state.instances,
                            memberMap = memberMap,
                            actionState = actionState,
                            onCompleteInstance = { instance ->
                                model.completeInstance(
                                    householdId = householdId,
                                    task = state.task,
                                    instance = instance
                                )
                            },
                            onComplete = { assignmentId, assignment ->
                                model.completeAssignment(
                                    householdId = householdId,
                                    taskId = taskId,
                                    task = state.task,
                                    assignmentId = assignmentId,
                                    assignment = assignment
                                )
                            }
                        )
                    }

                    is TaskDetailUiState.Error -> {
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
                                Button(onClick = { model.loadTaskDetail(householdId, taskId) }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    is TaskDetailUiState.Idle -> {}
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  TaskDetailContent
// ────────────────────────────────────────────────────────────

@Composable
private fun TaskDetailContent(
    task: org.taskhub.network.models.TaskResponse,
    assignments: List<TaskAssignmentResponse>,
    instances: List<TaskInstanceResponse>,
    memberMap: Map<String, MemberResponse>,
    actionState: TaskActionState,
    onCompleteInstance: (TaskInstanceResponse) -> Unit,
    onComplete: (String, TaskAssignmentResponse) -> Unit
) {
    val now = Clock.System.now().toEpochMilliseconds()
    val pendingAssignments = assignments.filter { it.status == "assigned" }
    val completedAssignments = assignments.filter { it.status == "completed" }

    // Instance stats
    val pendingInstances = instances.filter { !it.completed }
    val completedInstances = instances.filter { it.completed }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action state feedback
        if (actionState is TaskActionState.Error) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "❌ ${actionState.message}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // ── Task info card ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Teal50
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Title
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Teal900
                    )

                    // Description
                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Info rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoBadge(label = "Puntos", value = "${task.points} ⭐", color = Teal500)
                        InfoBadge(
                            label = "Frecuencia",
                            value = when (task.frequency) {
                                "daily" -> "Diaria"
                                "weekly" -> "Semanal"
                                "monthly" -> "Mensual"
                                else -> "Una vez"
                            },
                            color = Teal500
                        )
                    }

                    // Recurrence days
                    if (task.recurrenceDays.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val daysStr = task.recurrenceDays.joinToString(", ") { day ->
                            when (day) {
                                1 -> "Lunes"
                                2 -> "Martes"
                                3 -> "Miércoles"
                                4 -> "Jueves"
                                5 -> "Viernes"
                                6 -> "Sábado"
                                7 -> "Domingo"
                                else -> "?"
                            }
                        }
                        Text(
                            text = "🔄 $daysStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Teal700
                        )
                    }

                    // Tags
                    if (task.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            task.tags.forEach { tag ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = Coral50
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Coral700
                                    )
                                }
                            }
                        }
                    }

                    // Penalty info
                    if (task.penaltyMode != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Teal200)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Penalización por retraso",
                            style = MaterialTheme.typography.labelLarge,
                            color = Coral600,
                            fontWeight = FontWeight.SemiBold
                        )
                        val penaltyDesc = when (task.penaltyMode) {
                            "fixed" -> "-${task.penaltyValue} pts por cada ${intervalLabel(task.penaltyInterval)}"
                            "percentage" -> "-${task.penaltyValue}% por cada ${intervalLabel(task.penaltyInterval)}"
                            else -> ""
                        }
                        Text(
                            text = penaltyDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Coral700
                        )
                        if (task.penaltyMax > 0) {
                            Text(
                                text = "Tope máximo: -${task.penaltyMax} pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = Coral500
                            )
                        }
                    }
                }
            }
        }

        // ── Instance status ──
        item {
            Text(
                text = "📆 Instancias (${pendingInstances.size} pendientes, ${completedInstances.size} completadas)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Teal700
            )
        }

        if (task.frequency != "once" || instances.isNotEmpty()) {
            if (pendingInstances.isEmpty() && completedInstances.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "📭 Sin instancias aún",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (pendingInstances.isNotEmpty()) {
                item {
                    Text(
                        text = "⏳ Pendientes:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Coral700
                    )
                }
                items(pendingInstances.sortedBy { it.dueDate }) { instance ->
                    InstanceRow(
                        instance = instance,
                        now = now,
                        isLoading = actionState is TaskActionState.Loading,
                        onComplete = { onCompleteInstance(instance) }
                    )
                }
            }

            if (completedInstances.isNotEmpty()) {
                item {
                    Text(
                        text = "✅ Completadas:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Teal700
                    )
                }
                items(completedInstances.sortedByDescending { it.completedAt ?: 0L }.take(5)) { instance ->
                    InstanceRow(
                        instance = instance,
                        now = now,
                        isLoading = false,
                        onComplete = null
                    )
                }
                if (completedInstances.size > 5) {
                    item {
                        Text(
                            text = "... y ${completedInstances.size - 5} más",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Pending assignments ──
        item {
            Text(
                text = "📋 Pendientes (${pendingAssignments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Teal700
            )
        }

        if (pendingAssignments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = if (completedAssignments.isNotEmpty()) "✅ ¡Todo completado!" else "📭 Sin asignaciones",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(pendingAssignments) { assignment ->
                val member = memberMap[assignment.memberId]
                AssignmentCard(
                    assignment = assignment,
                    member = member,
                    now = now,
                    showComplete = true,
                    isLoading = actionState is TaskActionState.Loading,
                    onComplete = { onComplete(assignment.id, assignment) }
                )
            }
        }

        // ── Completed assignments ──
        if (completedAssignments.isNotEmpty()) {
            item {
                Text(
                    text = "✅ Completadas (${completedAssignments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Teal700
                )
            }

            items(completedAssignments) { assignment ->
                val member = memberMap[assignment.memberId]
                AssignmentCard(
                    assignment = assignment,
                    member = member,
                    now = now,
                    showComplete = false
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ────────────────────────────────────────────────────────────
//  AssignmentCard
// ────────────────────────────────────────────────────────────

@Composable
private fun AssignmentCard(
    assignment: TaskAssignmentResponse,
    member: MemberResponse?,
    now: Long,
    showComplete: Boolean,
    isLoading: Boolean = false,
    onComplete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Member avatar/info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (member?.role == "admin") Coral100 else Teal100
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (member?.role == "admin") "👑" else "🧒",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = member?.displayName ?: assignment.memberId.take(8),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Due date
                if (assignment.dueDate > 0) {
                    val isOverdue = now > assignment.dueDate
                    Text(
                        text = "⏰ Vence: ${formatDateTime(assignment.dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue && showComplete)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Mandatory badge
                if (assignment.mandatory) {
                    Text(
                        text = "🔒 Obligatoria",
                        style = MaterialTheme.typography.labelSmall,
                        color = Coral600
                    )
                }

                // Completion info
                if (assignment.status == "completed") {
                    val pts = assignment.pointsAwarded ?: 0
                    val onTime = assignment.onTime ?: true
                    Text(
                        text = if (onTime) "✅ A tiempo — +$pts pts"
                        else "⚠️ Tarde — +$pts pts",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (onTime) Teal600 else Coral600
                    )
                    if (assignment.completedAt != null) {
                        Text(
                            text = "Completado: ${formatDateTime(assignment.completedAt!!)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Complete button
            if (showComplete && onComplete != null) {
                Button(
                    onClick = onComplete,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal600
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("✅ Hecho")
                    }
                }
            } else if (assignment.status == "completed") {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Teal100
                ) {
                    Text(
                        text = "✅",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  InstanceRow
// ────────────────────────────────────────────────────────────

@Composable
private fun InstanceRow(
    instance: TaskInstanceResponse,
    now: Long,
    isLoading: Boolean,
    onComplete: (() -> Unit)?
) {
    val isOverdue = !instance.completed && instance.dueDate > 0 && instance.dueDate < now

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📅 ${formatDateTime(instance.dueDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        instance.completed -> Teal600
                        isOverdue -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (!instance.completed) FontWeight.SemiBold else FontWeight.Normal
                )

                if (instance.completed && instance.completedAt != null) {
                    Text(
                        text = "✅ Completada: ${formatDateTime(instance.completedAt!!)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal600
                    )
                }

                instance.pointsAwarded?.let { pts ->
                    Text(
                        text = "⭐ +$pts pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = Coral600
                    )
                }
            }

            if (onComplete != null && !instance.completed) {
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
                        Text("✅", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (instance.completed) {
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
    }
}

// ────────────────────────────────────────────────────────────
//  Helpers
// ────────────────────────────────────────────────────────────

@Composable
private fun InfoBadge(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val min = local.minute.toString().padStart(2, '0')
    return "$day/$month ${hour}:${min}"
}

private fun intervalLabel(interval: String): String = when (interval) {
    "week" -> "semana"
    "month" -> "mes"
    else -> "día"
}
