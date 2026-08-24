package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.storage.SettingsStore
import org.taskhub.ui.models.*
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.UserAvatar
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
        val settingsStore = koinInject<SettingsStore>()
        val detailState by model.detailState.collectAsState()
        val actionState by model.actionState.collectAsState()
        val reassignState by model.reassignState.collectAsState()
        val calendarActionState by model.calendarActionState.collectAsState()
        val commentsState by model.commentsState.collectAsState()
        val newCommentText by model.newCommentText.collectAsState()

        var showDeleteDialog by remember { mutableStateOf(false) }

        LaunchedEffect(taskId) {
            model.resetActionState()
            model.loadTaskDetail(householdId, taskId)
            model.loadComments(householdId, taskId)
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("🗑️ Eliminar tarea") },
                text = { Text("¿Eliminar esta tarea permanentemente?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            model.deleteTask(householdId, taskId)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Watch for complete/delete success and navigate back
        LaunchedEffect(actionState) {
            if (actionState is TaskActionState.Success && !showDeleteDialog) {
                // Refresh list state before going back so the
                // completed instance disappears from the pending list
                model.loadTasks(householdId)
                navigator.pop()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = "Detalle",
                    onBack = { navigator.pop() },
                    actions = {
                        IconButton(
                            onClick = {
                                val state = detailState
                                if (state is TaskDetailUiState.Success) {
                                    navigator.push(EditTaskScreen(householdId, state.task))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                )

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
                        val isGoogleLinked = settingsStore.hasGoogleLinked()
                        TaskDetailContent(
                            task = state.task,
                            assignments = state.assignments,
                            memberMap = memberMap,
                            actionState = actionState,
                            reassignState = reassignState,
                            commentsState = commentsState,
                            newCommentText = newCommentText,
                            isGoogleLinked = isGoogleLinked,
                            calendarActionState = calendarActionState,
                            onCommentTextChange = { model.setNewCommentText(it) },
                            onAddComment = {
                                model.addComment(householdId, taskId)
                            },
                            onCompleteTask = {
                                model.completeTask(
                                    householdId = householdId,
                                    taskId = taskId
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
                            },
                            onChangeCompletedBy = { memberId ->
                                model.reassignTaskCompletion(
                                    householdId = householdId,
                                    taskId = taskId,
                                    taskPoints = state.task.points,
                                    newMemberId = memberId
                                )
                            },
                            onToggleSubtask = { subtaskId ->
                                model.toggleSubtask(householdId, taskId, subtaskId)
                            },
                            onSendToGoogleCalendar = {
                                val token = settingsStore.getGoogleAccessToken()
                                if (token != null) {
                                    model.sendToGoogleCalendar(token, state.task)
                                }
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
//  TaskDetailContent (simplified — no instances)
// ────────────────────────────────────────────────────────────

@Composable
private fun TaskDetailContent(
    task: org.taskhub.network.models.TaskResponse,
    assignments: List<TaskAssignmentResponse>,
    memberMap: Map<String, MemberResponse>,
    actionState: TaskActionState,
    reassignState: TaskActionState = TaskActionState.Idle,
    commentsState: CommentsUiState,
    newCommentText: String,
    isGoogleLinked: Boolean = false,
    calendarActionState: TaskScreenModel.CalendarActionState = TaskScreenModel.CalendarActionState.Idle,
    onCommentTextChange: (String) -> Unit,
    onAddComment: () -> Unit,
    onCompleteTask: () -> Unit,
    onComplete: (String, TaskAssignmentResponse) -> Unit,
    onChangeCompletedBy: (String) -> Unit,
    onToggleSubtask: (String) -> Unit,
    onSendToGoogleCalendar: (() -> Unit)? = null
) {
    val now = Clock.System.now().toEpochMilliseconds()
    val pendingAssignments = assignments.filter { it.status == "assigned" }
    val completedAssignments = assignments.filter { it.status == "completed" }

    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val isCompletedToday = task.lastCompletedDate != null && run {
        val lcdDate = kotlinx.datetime.Instant.fromEpochMilliseconds(task.lastCompletedDate!!).toLocalDateTime(tz).date
        lcdDate == today
    }

    // Estado para el diálogo "¿quién ha hecho la tarea?" (editar quién la completó).
    var showChangeWhoDialog by remember { mutableStateOf(false) }
    var selectedCompleterId by remember { mutableStateOf(task.completedBy) }

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
                        InfoBadge(label = "Puntos", value = "${task.points} ⭐", color = MaterialTheme.colorScheme.primary)
                        InfoBadge(
                            label = "Frecuencia",
                            value = when (task.frequency) {
                                "daily" -> "Diaria"
                                "weekly" -> "Semanal"
                                "monthly" -> "Mensual"
                                else -> "Una vez"
                            },
                            color = MaterialTheme.colorScheme.primary
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

                    // Recurrence day of month
                    if (task.frequency == "monthly" && task.recurrenceDay != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "📆 Cada día ${task.recurrenceDay} del mes",
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
                                color = Coral700
                            )
                        }
                    }
                }
            }
        }

        // ── Completion status ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCompletedToday) "✅ Completada hoy" else "⏳ Pendiente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompletedToday) Teal600 else Coral600
                )
                if (!isCompletedToday) {
                    Button(
                        onClick = onCompleteTask,
                        enabled = actionState !is TaskActionState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (actionState is TaskActionState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("✅ Hecho")
                        }
                    }
                }
            }
        }

        if (isCompletedToday && task.lastCompletedDate != null) {
            item {
                val completer = task.completedBy?.let { memberMap[it] }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hecha por: ${completer?.displayName ?: "Alguien del espacio"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Completado: ${formatDateTime(task.lastCompletedDate!!)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (reassignState is TaskActionState.Error) {
                            Text(
                                text = "⚠️ ${reassignState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = {
                        selectedCompleterId = task.completedBy
                        showChangeWhoDialog = true
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Cambiar quién la hizo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Cambiar")
                    }
                }
            }
        }

        // ── Google Calendar button ──
        if (isGoogleLinked && onSendToGoogleCalendar != null) {
            item {
                OutlinedButton(
                    onClick = onSendToGoogleCalendar,
                    enabled = calendarActionState !is TaskScreenModel.CalendarActionState.Sending,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (calendarActionState is TaskScreenModel.CalendarActionState.Sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Teal600,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Enviando...")
                    } else {
                        Text("📅 Enviar a Google Calendar")
                    }
                }
            }
        }

        // ── Subtasks checklist ──
        if (task.subtasks.isNotEmpty()) {
            item {
                val completedCount = task.subtasks.count { it.completed }
                Text(
                    text = "✅ Checklist ($completedCount/${task.subtasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(task.subtasks) { st ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = st.completed,
                        onCheckedChange = { onToggleSubtask(st.id) },
                        colors = CheckboxDefaults.colors(checkedColor = Teal600)
                    )
                    Text(
                        text = st.text,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (st.completed) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (st.completed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ── Pending assignments ──
        item {
            Text(
                text = "📋 Pendientes (${pendingAssignments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
                    color = MaterialTheme.colorScheme.primary
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

        // ── Comments section ──
        item {
            HorizontalDivider(color = Teal200)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "💬 Comentarios",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Comment input
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = onCommentTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Añade un comentario...") },
                    maxLines = 2,
                    singleLine = false,
                    supportingText = {
                        Text("${newCommentText.length}/200")
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal600,
                        cursorColor = Teal600
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onAddComment,
                    enabled = newCommentText.isNotBlank()
                ) {
                    Text(
                        "📤",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Comments list
        when (commentsState) {
            is CommentsUiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Teal600,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            is CommentsUiState.Success -> {
                if (commentsState.comments.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "No hay comentarios aún",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(commentsState.comments) { comment ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Teal50.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = comment.authorName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Teal700
                                    )
                                    if (comment.createdAt > 0) {
                                        Text(
                                            text = formatDateTime(comment.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            is CommentsUiState.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "⚠️ ${commentsState.message}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            is CommentsUiState.Idle -> {}
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    // ── Diálogo: cambiar quién ha hecho la tarea ──
    if (showChangeWhoDialog) {
        val members = memberMap.values.toList()
        AlertDialog(
            onDismissRequest = { showChangeWhoDialog = false },
            title = { Text("¿Quién ha hecho la tarea?") },
            text = {
                Column {
                    Text(
                        text = "Los puntos se moverán a la persona que elijas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    members.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCompleterId = member.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCompleterId == member.id,
                                onClick = { selectedCompleterId = member.id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = member.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChangeWhoDialog = false
                        selectedCompleterId?.let { onChangeCompletedBy(it) }
                    },
                    enabled = selectedCompleterId != null && selectedCompleterId != task.completedBy
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeWhoDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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
                    UserAvatar(
                        avatarUrl = member?.avatarUrl,
                        fallbackEmoji = if (member?.role == "admin") "👑" else "🧒",
                        displayName = member?.displayName ?: "",
                        contentDescription = member?.displayName ?: "",
                        size = 32.dp,
                        backgroundColor = if (member?.role == "admin") Coral100 else Teal100
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (member?.role == "admin") "Admin" else "Niño/a",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
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
                        containerColor = MaterialTheme.colorScheme.primary
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
//  Helpers

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
