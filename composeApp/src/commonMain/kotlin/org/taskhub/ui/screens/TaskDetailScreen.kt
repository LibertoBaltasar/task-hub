package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.launch
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.models.*
import org.taskhub.ui.components.BadgeTone
import org.taskhub.ui.components.DestructiveConfirmDialog
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.StatChip
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.taskHubTextFieldColors
import org.taskhub.ui.components.UserAvatar
import org.taskhub.ui.i18n.AppStrings
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
        val authManager = koinInject<GoogleAuthManager>()
        val coroutineScope = rememberCoroutineScope()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
        val detailState by model.detailState.collectAsState()
        val actionState by model.actionState.collectAsState()
        val reassignState by model.reassignState.collectAsState()
        val currentMemberId by model.currentMemberId.collectAsState()
        val calendarActionState by model.calendarActionState.collectAsState()
        val myAssignment by model.myAssignment.collectAsState()
        val commentsState by model.commentsState.collectAsState()
        val newCommentText by model.newCommentText.collectAsState()

        var isLinkingCalendar by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        // El owner del hogar (quien lo creó) es siempre "de confianza" para
        // reasignar completados, igual que isTrusted(hid) en firestore.rules,
        // independientemente de qué rol se auto-asignara al crear su propio
        // perfil — ver el mismo fix en HouseholdScreen.kt.
        var isOwner by remember { mutableStateOf(false) }
        LaunchedEffect(householdId) {
            // best-effort: si falla, isOwner se queda en false (solo se pierde
            // el atajo de "owner", el rol "admin" normal sigue igual) — ver
            // FirestoreRepository.isHouseholdOwner.
            isOwner = model.isHouseholdOwner(householdId)
        }

        LaunchedEffect(taskId) {
            model.resetActionState()
            model.loadTaskDetail(householdId, taskId)
            model.loadComments(householdId, taskId)
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            DestructiveConfirmDialog(
                title = s("task_detail_delete_title"),
                text = s("task_detail_delete_confirm"),
                s = s,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    showDeleteDialog = false
                    model.deleteTask(householdId, taskId)
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
                    title = s("task_detail_title"),
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
                            Icon(Icons.Default.Edit, contentDescription = s("task_detail_edit_content_desc"))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = s("household_delete_btn"))
                        }
                    }
                )

                when (val state = detailState) {
                    is TaskDetailUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    is TaskDetailUiState.Success -> {
                        val memberMap = state.members.associateBy { it.id }
                        val isGoogleLinked = model.hasGoogleLinked()
                        val isCalendarSyncEnabled = model.isCalendarSyncEnabled()
                        // Reasignar quién hizo una tarea mueve puntos entre miembros:
                        // gateado a admin/owner tanto aquí como en firestore.rules
                        // (ver el `update` de households/{hid}/tasks/{tid}).
                        val isAdmin = memberMap[currentMemberId]?.role == "admin" || isOwner
                        TaskDetailContent(
                            task = state.task,
                            assignments = state.assignments,
                            memberMap = memberMap,
                            actionState = actionState,
                            reassignState = reassignState,
                            isAdmin = isAdmin,
                            commentsState = commentsState,
                            newCommentText = newCommentText,
                            s = s,
                            myAssignment = myAssignment,
                            isGoogleLinked = isGoogleLinked,
                            isCalendarSyncEnabled = isCalendarSyncEnabled,
                            isLinkingCalendar = isLinkingCalendar,
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
                            onSyncCalendarNow = {
                                model.syncTaskToCalendarNow(householdId, state.task)
                            },
                            onLinkCalendar = {
                                isLinkingCalendar = true
                                coroutineScope.launch {
                                    val linked = authManager.linkCalendar()
                                    isLinkingCalendar = false
                                    if (linked) {
                                        model.setCalendarSyncEnabled(true)
                                    } else {
                                        // Antes fallaba en silencio: solo se apagaba el
                                        // spinner y volvía a "No vinculado" sin ninguna
                                        // pista de si el usuario canceló o hubo un fallo
                                        // real — reutiliza la misma tarjeta de error que
                                        // ya usa "Sincronizar ahora".
                                        model.setCalendarLinkError(s("calendar_link_error"))
                                    }
                                    model.loadTaskDetail(householdId, taskId)
                                }
                            },
                            onEnableCalendarSync = {
                                model.setCalendarSyncEnabled(true)
                                model.loadTaskDetail(householdId, taskId)
                            }
                        )
                    }

                    is TaskDetailUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = s("error_icon_content_desc"),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { model.loadTaskDetail(householdId, taskId) }) {
                                    Text(s("tasks_retry"))
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
    isAdmin: Boolean = false,
    commentsState: CommentsUiState,
    newCommentText: String,
    s: (String) -> String = { it },
    myAssignment: TaskAssignmentResponse? = null,
    isGoogleLinked: Boolean = false,
    isCalendarSyncEnabled: Boolean = false,
    isLinkingCalendar: Boolean = false,
    calendarActionState: TaskScreenModel.CalendarActionState = TaskScreenModel.CalendarActionState.Idle,
    onCommentTextChange: (String) -> Unit,
    onAddComment: () -> Unit,
    onCompleteTask: () -> Unit,
    onComplete: (String, TaskAssignmentResponse) -> Unit,
    onChangeCompletedBy: (String) -> Unit,
    onToggleSubtask: (String) -> Unit,
    onSyncCalendarNow: () -> Unit = {},
    onLinkCalendar: () -> Unit = {},
    onEnableCalendarSync: () -> Unit = {}
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
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = s("error_icon_content_desc"),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = actionState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // ── Task info card ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Title
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Description
                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Info rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatChip(label = s("public_profile_stat_points"), value = "${task.points} ⭐", tone = BadgeTone.Teal)
                        StatChip(
                            label = s("task_detail_frequency_label"),
                            value = when (task.frequency) {
                                "daily" -> s("recurrence_daily")
                                "weekly" -> s("recurrence_weekly")
                                "monthly" -> s("recurrence_monthly")
                                else -> s("recurrence_once")
                            },
                            tone = BadgeTone.Teal
                        )
                    }

                    // Recurrence days
                    if (task.recurrenceDays.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val daysStr = task.recurrenceDays.joinToString(", ") { day ->
                            when (day) {
                                1 -> s("recurrence_day_monday")
                                2 -> s("recurrence_day_tuesday")
                                3 -> s("recurrence_day_wednesday")
                                4 -> s("recurrence_day_thursday")
                                5 -> s("recurrence_day_friday")
                                6 -> s("recurrence_day_saturday")
                                7 -> s("recurrence_day_sunday")
                                else -> "?"
                            }
                        }
                        Text(
                            text = "🔄 $daysStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Recurrence day of month
                    if (task.frequency == "monthly" && task.recurrenceDay != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = s("task_detail_monthly_recurrence").replace("%d", task.recurrenceDay.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Tags
                    if (task.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            task.tags.forEach { tag ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Penalty info
                    if (task.penaltyMode != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = s("create_task_penalty_section"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        val penaltyDesc = when (task.penaltyMode) {
                            "fixed" -> s("task_detail_penalty_fixed_desc")
                                .replace("%1", task.penaltyValue.toString())
                                .replace("%2", intervalLabel(task.penaltyInterval, s))
                            "percentage" -> s("task_detail_penalty_percent_desc")
                                .replace("%1", task.penaltyValue.toString())
                                .replace("%2", intervalLabel(task.penaltyInterval, s))
                            else -> ""
                        }
                        Text(
                            text = penaltyDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (task.penaltyMax > 0) {
                            Text(
                                text = s("task_detail_penalty_max_desc").replace("%d", task.penaltyMax.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
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
                    text = if (isCompletedToday) s("task_detail_completed_today") else s("task_detail_status_pending"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompletedToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
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
                            Text(s("task_detail_mark_done"))
                        }
                    }
                }
            }
        }

        if (isCompletedToday) {
            item {
                val completer = task.completedBy?.let { memberMap[it] }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s("task_detail_done_by").replace("%s", completer?.displayName ?: s("task_detail_someone_in_space")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = s("task_detail_completed_at").replace("%s", formatDateTime(task.lastCompletedDate!!)),
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
                    // Reasignar quién completó la tarea mueve puntos entre
                    // miembros: solo admin/owner (ver firestore.rules v4).
                    if (isAdmin) {
                        TextButton(onClick = {
                            selectedCompleterId = task.completedBy
                            showChangeWhoDialog = true
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = s("task_detail_change_completer_desc"),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(s("task_detail_change_btn"))
                        }
                    }
                }
            }
        }

        // ── Google Calendar sync status ──
        item {
            val calendarStatus = when {
                myAssignment == null || myAssignment.dueDate <= 0 -> CalendarSyncStatus.NoDueDate
                myAssignment.googleEventId != null -> CalendarSyncStatus.Synced
                !isGoogleLinked -> CalendarSyncStatus.NotLinked
                !isCalendarSyncEnabled -> CalendarSyncStatus.SyncDisabled
                else -> CalendarSyncStatus.Pending
            }
            CalendarSyncStatusCard(
                status = calendarStatus,
                calendarActionState = calendarActionState,
                isLinkingCalendar = isLinkingCalendar,
                s = s,
                onSyncNow = onSyncCalendarNow,
                onLinkAccount = onLinkCalendar,
                onEnableSync = onEnableCalendarSync
            )
        }

        // ── Subtasks checklist ──
        if (task.subtasks.isNotEmpty()) {
            item {
                val completedCount = task.subtasks.count { it.completed }
                Text(
                    text = s("task_detail_checklist_header")
                        .replace("%1", completedCount.toString())
                        .replace("%2", task.subtasks.size.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(task.subtasks, key = { it.id }) { st ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = st.completed,
                        onCheckedChange = { onToggleSubtask(st.id) },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = st.text,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (st.completed) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (st.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ── Pending assignments ──
        item {
            Text(
                text = s("task_detail_pending_header").replace("%d", pendingAssignments.size.toString()),
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
                        text = if (completedAssignments.isNotEmpty()) s("task_detail_all_completed") else s("task_detail_no_assignments"),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(pendingAssignments, key = { it.id }) { assignment ->
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
                    text = s("task_detail_completed_header").replace("%d", completedAssignments.size.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(completedAssignments, key = { it.id }) { assignment ->
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = s("task_detail_comments_title"),
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
                    placeholder = { Text(s("task_detail_comment_placeholder")) },
                    maxLines = 2,
                    singleLine = false,
                    supportingText = {
                        Text("${newCommentText.length}/200")
                    },
                    colors = taskHubTextFieldColors()
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onAddComment,
                    enabled = newCommentText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = s("messages_send"))
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
                            color = MaterialTheme.colorScheme.primary,
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
                                text = s("task_detail_no_comments"),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(commentsState.comments, key = { it.id }) { comment ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            // surfaceVariant (no Teal50 con alpha 0.5f): el alpha compuesto sobre
                            // el fondo oscuro daba un contenedor de contraste insuficiente para
                            // onSurfaceVariant/onSurface (2.1-2.8:1) — surfaceVariant ya está
                            // pensado para combinarse con esos colores "on*" en los 3 temas/modos.
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            title = { Text(s("task_detail_who_did_title")) },
            text = {
                Column {
                    Text(
                        text = s("task_detail_who_did_hint"),
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
                    Text(s("edit_task_submit"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeWhoDialog = false }) {
                    Text(s("household_cancel"))
                }
            }
        )
    }
}

// ────────────────────────────────────────────────────────────
//  CalendarSyncStatusCard
// ────────────────────────────────────────────────────────────

private enum class CalendarSyncStatus { NoDueDate, Synced, Pending, NotLinked, SyncDisabled }

/**
 * Indicador del estado de sincronización con Google Calendar de la
 * asignación del usuario actual para esta tarea. Sustituye al antiguo botón
 * manual "Enviar a Google Calendar" — ahora la sincronización es automática
 * (ver [org.taskhub.ui.models.CalendarSyncManager]) y este componente solo
 * informa y ofrece un backfill puntual ("Sincronizar ahora") cuando aplica.
 */
@Composable
private fun CalendarSyncStatusCard(
    status: CalendarSyncStatus,
    calendarActionState: TaskScreenModel.CalendarActionState,
    isLinkingCalendar: Boolean,
    s: (String) -> String,
    onSyncNow: () -> Unit,
    onLinkAccount: () -> Unit,
    onEnableSync: () -> Unit
) {
    if (status == CalendarSyncStatus.NoDueDate) {
        Text(
            text = s("calendar_status_no_due_date"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (status) {
                        CalendarSyncStatus.Synced -> s("calendar_status_synced")
                        CalendarSyncStatus.Pending -> s("calendar_status_pending")
                        CalendarSyncStatus.NotLinked -> s("calendar_status_not_linked")
                        CalendarSyncStatus.SyncDisabled -> s("calendar_status_sync_disabled")
                        CalendarSyncStatus.NoDueDate -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (status == CalendarSyncStatus.Synced) MaterialTheme.semanticColors.success
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (status) {
                    CalendarSyncStatus.Pending -> {
                        TextButton(
                            onClick = onSyncNow,
                            enabled = calendarActionState !is TaskScreenModel.CalendarActionState.Sending
                        ) {
                            if (calendarActionState is TaskScreenModel.CalendarActionState.Sending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(s("calendar_status_syncing"))
                            } else {
                                Text(s("calendar_status_sync_now"))
                            }
                        }
                    }
                    CalendarSyncStatus.NotLinked -> {
                        TextButton(onClick = onLinkAccount, enabled = !isLinkingCalendar) {
                            if (isLinkingCalendar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(s("calendar_status_link_cta"))
                            }
                        }
                    }
                    CalendarSyncStatus.SyncDisabled -> {
                        TextButton(onClick = onEnableSync) {
                            Text(s("calendar_status_enable_cta"))
                        }
                    }
                    else -> {}
                }
            }

            if (calendarActionState is TaskScreenModel.CalendarActionState.Error) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "⚠️ ${calendarActionState.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
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
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

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
                        fallbackEmoji = if (member?.role == "admin") "👑" else "👤",
                        displayName = member?.displayName ?: "",
                        contentDescription = member?.displayName ?: "",
                        size = 32.dp,
                        backgroundColor = if (member?.role == "admin") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (member?.role == "admin") s("ranking_role_admin") else s("member_role_child_full"),
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
                        text = s("task_detail_due_at").replace("%s", formatDateTime(assignment.dueDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue && showComplete)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Mandatory badge
                if (assignment.mandatory) {
                    Text(
                        text = s("task_detail_mandatory_badge"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                // Completion info
                if (assignment.status == "completed") {
                    val pts = assignment.pointsAwarded ?: 0
                    val onTime = assignment.onTime ?: true
                    Text(
                        text = if (onTime) s("task_detail_on_time_pts").replace("%d", pts.toString())
                        else s("task_detail_late_pts").replace("%d", pts.toString()),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (onTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    if (assignment.completedAt != null) {
                        Text(
                            text = s("task_detail_completed_at").replace("%s", formatDateTime(assignment.completedAt!!)),
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
                        Text(s("task_detail_mark_done"))
                    }
                }
            } else if (assignment.status == "completed") {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
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

private fun formatDateTime(epochMillis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val min = local.minute.toString().padStart(2, '0')
    return "$day/$month ${hour}:${min}"
}

private fun intervalLabel(interval: String, s: (String) -> String): String = when (interval) {
    "week" -> s("task_detail_interval_week")
    "month" -> s("task_detail_interval_month")
    else -> s("task_detail_interval_day")
}
