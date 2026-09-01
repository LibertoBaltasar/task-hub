package org.taskhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.*
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.Subtask
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.TaskActionState
import org.taskhub.ui.models.TaskScreenModel
import org.taskhub.ui.models.TaskTemplate
import org.taskhub.ui.models.TaskTemplates
import org.taskhub.ui.models.TemplateCategory
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.theme.*

// ────────────────────────────────────────────────────────────
//  CreateTaskScreen
// ────────────────────────────────────────────────────────────

data class CreateTaskScreen(
    val householdId: String,
    val createdBy: String,
    /** Si se indica, la tarea sale preasignada a este miembro (crear tarea directa desde un miembro). */
    val preselectedMemberId: String? = null
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val taskModel = koinScreenModel<TaskScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val actionState by taskModel.actionState.collectAsState()
        val memberState by memberModel.uiState.collectAsState()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        LaunchedEffect(householdId) {
            taskModel.resetActionState()
            memberModel.loadMembers(householdId)
        }

        // Form state
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var pointsText by remember { mutableStateOf("10") }
        var frequency by remember { mutableStateOf("once") }
        var recurrenceDays by remember { mutableStateOf(setOf<Int>()) }
        var recurrenceDay by remember { mutableStateOf<Int?>(null) }
        var tagsText by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf(listOf<String>()) }
        var selectedMembers by remember {
            mutableStateOf(preselectedMemberId?.let { setOf(it) } ?: emptySet())
        }
        var mandatory by remember { mutableStateOf(false) }
        var hasDeadline by remember { mutableStateOf(false) }
        var deadlineDay by remember { mutableStateOf("") }
        var deadlineTime by remember { mutableStateOf("12:00") }
        var hasPenalty by remember { mutableStateOf(false) }
        var showDatePicker by remember { mutableStateOf(false) }

        // ── DatePicker para elegir la fecha límite ────────────
        if (showDatePicker) {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val initialMillis = if (deadlineDay.isValidDateFormat()) {
                val parts = deadlineDay.split("-")
                LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                    .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            } else {
                today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            }
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val date = Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC).date
                                deadlineDay = "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
                            }
                            showDatePicker = false
                        }
                    ) { Text(s("task_date_accept")) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text(s("household_cancel")) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        var penaltyMode by remember { mutableStateOf("fixed") }
        var penaltyValue by remember { mutableStateOf("") }
        var penaltyInterval by remember { mutableStateOf("day") }
        var penaltyMax by remember { mutableStateOf("") }
        var templatesExpanded by remember { mutableStateOf(false) }
        // Subtasks state
        var subtaskText by remember { mutableStateOf("") }
        var subtasks by remember { mutableStateOf(listOf<Subtask>()) }

        // Handle success
        LaunchedEffect(actionState) {
            if (actionState is TaskActionState.Success) {
                taskModel.loadTasks(householdId)
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
                    title = s("create_task_title"),
                    onBack = { navigator.pop() },
                    actions = {
                        if (actionState !is TaskActionState.Loading) {
                            TextButton(
                                onClick = {
                                    val points = pointsText.toIntOrNull() ?: 10
                                    val pValue = penaltyValue.toIntOrNull() ?: 0
                                    val pMax = penaltyMax.toIntOrNull() ?: 0
                                    val dueDate = if (hasDeadline && deadlineDay.isNotBlank()) {
                                        parseDeadline(deadlineDay, deadlineTime)
                                    } else 0L

                                    taskModel.createTask(
                                        householdId = householdId,
                                        createdBy = createdBy,
                                        title = title,
                                        description = description,
                                        points = points,
                                        frequency = frequency,
                                        recurrenceDays = recurrenceDays.toList().sorted(),
                                        recurrenceDay = if (frequency == "monthly") recurrenceDay else null,
                                        tags = tags,
                                        subtasks = subtasks,
                                        penaltyMode = if (hasPenalty) penaltyMode else null,
                                        penaltyValue = pValue,
                                        penaltyInterval = penaltyInterval,
                                        penaltyMax = pMax,
                                        memberIds = selectedMembers.toList(),
                                        mandatory = mandatory,
                                        dueDate = dueDate
                                    )
                                },
                                enabled = actionState !is TaskActionState.Loading &&
                                    title.isNotBlank() &&
                                    (pointsText.toIntOrNull() ?: -1) > 0 &&
                                    (!hasDeadline || deadlineTime.isValidTimeFormat()) &&
                                    (!hasPenalty || (penaltyValue.toIntOrNull() ?: -1) > 0) &&
                                    (!hasPenalty || penaltyMax.isBlank() || (penaltyMax.toIntOrNull() ?: -1) >= 0),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(s("create_task_submit"), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )

                // Form content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Error state
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
                                        text = (actionState as TaskActionState.Error).message,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // ── Quick templates ──
                    item {
                        QuickTemplatesSection(
                            expanded = templatesExpanded,
                            onToggle = { templatesExpanded = !templatesExpanded },
                            onTemplateSelected = { template ->
                                title = template.title
                                description = template.description
                                tags = template.tags
                                frequency = template.frequency
                                pointsText = template.points.toString()
                            }
                        )
                    }

                    // ── Basic info ──
                    item {
                        Text(
                            text = s("create_task_section_basic_info"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(s("create_task_title_field")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = title.isBlank(),
                            supportingText = {
                                if (title.isBlank()) {
                                    Text(s("create_task_title_required"))
                                }
                            }
                        )
                    }

                    // ── Checklist ──
                    item {
                        Text(
                            text = s("create_task_section_checklist"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = subtaskText,
                                onValueChange = { subtaskText = it },
                                label = { Text(s("create_task_add_item")) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val text = subtaskText.trim()
                                    if (text.isNotBlank()) {
                                        val id = kotlin.random.Random.nextLong().toString(36)
                                        subtasks = subtasks + Subtask(id = id, text = text, completed = false)
                                        subtaskText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("+")
                            }
                        }
                    }

                    if (subtasks.isNotEmpty()) {
                        items(subtasks, key = { it.id }) { st ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = st.completed,
                                    onCheckedChange = { checked ->
                                        subtasks = subtasks.map {
                                            if (it.id == st.id) it.copy(completed = checked) else it
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = st.text,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                TextButton(
                                    onClick = { subtasks = subtasks.filter { it.id != st.id } },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("✕")
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(s("create_task_description_label")) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = pointsText,
                            onValueChange = { pointsText = it },
                            label = { Text(s("public_profile_stat_points")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = (pointsText.toIntOrNull() ?: -1) <= 0,
                            supportingText = {
                                if (pointsText.toIntOrNull() == null) {
                                    Text(s("create_task_points_error_nan"))
                                } else if ((pointsText.toIntOrNull() ?: -1) <= 0) {
                                    Text(s("create_task_points_error_positive"))
                                }
                            }
                        )
                    }

                    // ── Frequency ──
                    item {
                        Text(
                            text = s("create_task_section_frequency"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val freqs = listOf(
                                "once" to s("recurrence_once"),
                                "daily" to s("recurrence_daily"),
                                "weekly" to s("recurrence_weekly"),
                                "monthly" to s("recurrence_monthly")
                            )
                            freqs.forEach { (key, label) ->
                                FilterChip(
                                    selected = frequency == key,
                                    onClick = { frequency = key },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // Recurrence days (only for weekly)
                    if (frequency == "weekly") {
                        item {
                            Text(
                                text = s("recurrence_days_label"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val days = listOf(
                                    1 to (s("day_letter_monday") to s("recurrence_day_monday")),
                                    2 to (s("day_letter_tuesday") to s("recurrence_day_tuesday")),
                                    3 to (s("day_letter_wednesday") to s("recurrence_day_wednesday")),
                                    4 to (s("day_letter_thursday") to s("recurrence_day_thursday")),
                                    5 to (s("day_letter_friday") to s("recurrence_day_friday")),
                                    6 to (s("day_letter_saturday") to s("recurrence_day_saturday")),
                                    7 to (s("day_letter_sunday") to s("recurrence_day_sunday"))
                                )
                                days.forEach { (day, labels) ->
                                    val (letter, fullName) = labels
                                    FilterChip(
                                        selected = day in recurrenceDays,
                                        onClick = {
                                            recurrenceDays = if (day in recurrenceDays) {
                                                recurrenceDays - day
                                            } else {
                                                recurrenceDays + day
                                            }
                                        },
                                        label = { Text(letter) },
                                        modifier = Modifier.semantics { contentDescription = fullName },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Recurrence day of month (only for monthly)
                    if (frequency == "monthly") {
                        item {
                            Text(
                                text = s("recurrence_day_of_month_label"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = recurrenceDay?.toString() ?: "",
                                onValueChange = { text ->
                                    val n = text.toIntOrNull()
                                    recurrenceDay = when {
                                        text.isBlank() -> null
                                        n != null -> n.coerceIn(1, 31)
                                        else -> recurrenceDay
                                    }
                                },
                                label = { Text(s("create_task_day_of_month_field")) },
                                supportingText = { Text(s("recurrence_day_of_month_hint")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(0.4f)
                            )
                        }
                    }

                    // ── Tags ──
                    item {
                        Text(
                            text = s("create_task_section_tags"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tagsText,
                                onValueChange = { tagsText = it },
                                label = { Text(s("create_task_add_tag")) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val tag = tagsText.trim()
                                    if (tag.isNotBlank() && tag !in tags) {
                                        tags = tags + tag
                                        tagsText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("+")
                            }
                        }
                    }

                    if (tags.isNotEmpty()) {
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tags.forEach { tag ->
                                    InputChip(
                                        selected = false,
                                        onClick = { tags = tags - tag },
                                        label = { Text(tag) },
                                        trailingIcon = {
                                            Text("✕", style = MaterialTheme.typography.labelSmall)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Predefined tags (FlowRow: los chips hacen wrap en vez de comprimirse)
                    item {
                        val predefinedTags = listOf(
                            "limpieza", "cocina", "compras", "mascotas",
                            "mantenimiento", "niños", "exterior", "administración", "otro"
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            predefinedTags.forEach { tag ->
                                FilterChip(
                                    selected = tag in tags,
                                    onClick = {
                                        tags = if (tag in tags) tags - tag else tags + tag
                                    },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    // ── Assignment ──
                    item {
                        Column {
                            Text(
                                text = s("create_task_section_assignment"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = s("create_task_assignment_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Members list
                    when (val mState = memberState) {
                        is MemberUiState.Success -> {
                            if (mState.members.isNotEmpty()) {
                                item {
                                    mState.members.forEach { member ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .clickable {
                                                    selectedMembers = if (member.id in selectedMembers) {
                                                        selectedMembers - member.id
                                                    } else {
                                                        selectedMembers + member.id
                                                    }
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = member.id in selectedMembers,
                                                onCheckedChange = { checked ->
                                                    selectedMembers = if (checked) {
                                                        selectedMembers + member.id
                                                    } else {
                                                        selectedMembers - member.id
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            Text(
                                                // s("member_role_*_short") ya incluye el emoji + texto de rol
                                                // ("👑 Admin"/"👤 Miembro"): un emoji sin texto de apoyo es el
                                                // único diferenciador de rol para TalkBack/VoiceOver, que solo
                                                // anuncia el nombre unicode del glifo ("corona"/"busto").
                                                text = "${s(if (member.role == "admin") "member_role_admin_short" else "member_role_child_short")} ${member.displayName}",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                text = "⭐ ${member.totalPoints}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is MemberUiState.Loading -> {
                            item {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        else -> {}
                    }

                    // Mandatory toggle
                    if (selectedMembers.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = s("create_task_mandatory_label"),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = mandatory,
                                    onCheckedChange = { mandatory = it },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            }
                        }
                    }

                    // ── Deadline ──
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = s("create_task_deadline_section"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Switch(
                                checked = hasDeadline,
                                onCheckedChange = {
                                    hasDeadline = it
                                    if (it && deadlineDay.isBlank()) {
                                        val now = Clock.System.now()
                                        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
                                        deadlineDay = "${local.year}-${local.monthNumber.toString().padStart(2,'0')}-${local.dayOfMonth.toString().padStart(2,'0')}"
                                    }
                                }
                            )
                        }
                    }

                    if (hasDeadline) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.weight(1f).height(56.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = s("create_task_pick_date"))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (deadlineDay.isBlank()) s("create_task_pick_date") else deadlineDay,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedTextField(
                                    value = deadlineTime,
                                    onValueChange = { deadlineTime = it },
                                    label = { Text(s("create_task_time_label")) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    isError = !deadlineTime.isValidTimeFormat()
                                )
                            }
                        }
                    }

                    // ── Penalty ──
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = s("create_task_penalty_section"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Switch(
                                checked = hasPenalty,
                                onCheckedChange = { hasPenalty = it }
                            )
                        }
                    }

                    if (hasPenalty) {
                        // Penalty mode
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = penaltyMode == "fixed",
                                    onClick = { penaltyMode = "fixed" },
                                    label = { Text(s("create_task_penalty_fixed")) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                                FilterChip(
                                    selected = penaltyMode == "percentage",
                                    onClick = { penaltyMode = "percentage" },
                                    label = { Text(s("create_task_penalty_percentage")) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = penaltyValue,
                                onValueChange = { penaltyValue = it },
                                label = {
                                    Text(if (penaltyMode == "fixed") s("create_task_penalty_points_label") else s("create_task_penalty_percent_label"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                // A diferencia de "Puntos", este campo no validaba nada: con
                                // "Aplicar penalización" activado y el valor vacío/0,
                                // pValue caía en un fallback silencioso a 0 (createTask()
                                // más abajo) — se guardaba "con penalización" que en
                                // realidad nunca descontaba nada.
                                isError = (penaltyValue.toIntOrNull() ?: -1) <= 0,
                                supportingText = {
                                    Text(if (penaltyMode == "fixed")
                                        s("create_task_penalty_fixed_hint")
                                    else s("create_task_penalty_percent_hint"))
                                }
                            )
                        }

                        item {
                            Text(
                                text = s("create_task_penalty_interval_label"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = penaltyInterval == "day",
                                    onClick = { penaltyInterval = "day" },
                                    label = { Text(s("create_task_interval_daily")) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                                FilterChip(
                                    selected = penaltyInterval == "week",
                                    onClick = { penaltyInterval = "week" },
                                    label = { Text(s("recurrence_weekly")) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                                FilterChip(
                                    selected = penaltyInterval == "month",
                                    onClick = { penaltyInterval = "month" },
                                    label = { Text(s("recurrence_monthly")) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = penaltyMax,
                                onValueChange = { penaltyMax = it },
                                label = { Text(s("create_task_penalty_max_label")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                // A diferencia de sus campos hermanos ("Puntos", penaltyValue),
                                // este no validaba nada: cualquier texto no numérico caía en
                                // un fallback silencioso a 0 (pMax más arriba). 0/vacío SÍ es
                                // un valor válido aquí (significa "sin tope"), solo un negativo
                                // o texto no numérico es error.
                                isError = penaltyMax.isNotBlank() && (penaltyMax.toIntOrNull() ?: -1) < 0,
                                supportingText = {
                                    Text(s("create_task_penalty_max_hint"))
                                }
                            )
                        }
                    }

                    // Bottom spacer
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Quick Templates Section
// ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickTemplatesSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    onTemplateSelected: (TaskTemplate) -> Unit
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onToggle() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uD83D\uDCCB",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = s("create_task_templates_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (expanded) "\u25B2" else "\u25BC",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = s("create_task_templates_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TemplateCategory.entries.forEach { category ->
                        val templates = TaskTemplates.byCategory[category] ?: return@forEach
                        Column {
                            Text(
                                text = "${category.emoji} ${category.label}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                templates.forEach { template ->
                                    SuggestionChip(
                                        onClick = { onTemplateSelected(template) },
                                        label = {
                                            Text(
                                                text = template.title,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        icon = {
                                            Text(
                                                text = "\u2B50${template.points}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Helpers
// ────────────────────────────────────────────────────────────

internal fun String.isValidDateFormat(): Boolean =
    Regex("""\d{4}-\d{2}-\d{2}""").matches(this)

internal fun String.isValidTimeFormat(): Boolean {
    // No basta con el formato (dos dígitos:dos dígitos): "99:99" también lo
    // cumple pero LocalDateTime(...) en parseDeadline() lanza
    // IllegalArgumentException con una hora/minuto fuera de rango — sin esta
    // validación, el botón Crear/Guardar quedaba habilitado y pulsar crasheaba.
    val match = Regex("""(\d{2}):(\d{2})""").matchEntire(this) ?: return false
    val (hourStr, minuteStr) = match.destructured
    val hour = hourStr.toIntOrNull() ?: return false
    val minute = minuteStr.toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59
}

internal fun parseDeadline(dateStr: String, timeStr: String): Long {
    val parts = dateStr.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return 0L
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return 0L
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return 0L

    val timeParts = timeStr.split(":")
    val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 12
    val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

    val tz = TimeZone.currentSystemDefault()
    val localDateTime = LocalDateTime(year, month, day, hour, minute)
    return localDateTime.toInstant(tz).toEpochMilliseconds()
}
