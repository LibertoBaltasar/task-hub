package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.*
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.AssignmentSlot
import org.taskhub.ui.models.TaskActionState
import org.taskhub.ui.models.TaskScreenModel
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.theme.*

// ────────────────────────────────────────────────────────────
//  EditTaskScreen
// ────────────────────────────────────────────────────────────

data class EditTaskScreen(
    val householdId: String,
    val task: TaskResponse
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val taskModel = koinScreenModel<TaskScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val actionState by taskModel.actionState.collectAsState()
        val memberState by memberModel.uiState.collectAsState()

        LaunchedEffect(Unit) {
            taskModel.resetActionState()
        }

        LaunchedEffect(householdId) {
            memberModel.loadMembers(householdId)
        }

        // Form state — pre-populated from existing task
        var title by remember { mutableStateOf(task.title) }
        var description by remember { mutableStateOf(task.description) }
        var pointsText by remember { mutableStateOf(task.points.toString()) }
        var frequency by remember { mutableStateOf(task.frequency) }
        var recurrenceDays by remember { mutableStateOf(task.recurrenceDays.toSet()) }
        var tags by remember { mutableStateOf(task.tags) }
        var tagsText by remember { mutableStateOf("") }
        var hasPenalty by remember { mutableStateOf(task.penaltyMode != null) }
        var penaltyMode by remember { mutableStateOf(task.penaltyMode ?: "fixed") }
        var penaltyValue by remember { mutableStateOf(if (task.penaltyValue > 0) task.penaltyValue.toString() else "") }
        var penaltyInterval by remember { mutableStateOf(task.penaltyInterval) }
        var penaltyMax by remember { mutableStateOf(if (task.penaltyMax > 0) task.penaltyMax.toString() else "") }

        // Assignment state
        var selectedMembers by remember { mutableStateOf(setOf<String>()) }
        var hasRotation by remember { mutableStateOf(task.assignmentRotation.isNotEmpty()) }
        // rotationSlots: dayOfWeek (1..7) → memberId (empty string = not set)
        var rotationSlots by remember {
            mutableStateOf(
                (1..7).associateWith { day ->
                    task.assignmentRotation.find { it.dayOfWeek == day }?.memberId ?: ""
                }.toMutableMap()
            )
        }

        // Handle success — navigate back and refresh detail
        LaunchedEffect(actionState) {
            if (actionState is TaskActionState.Success) {
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
                    title = "Editar tarea",
                    onBack = { navigator.pop() },
                    actions = {
                        if (actionState !is TaskActionState.Loading) {
                            TextButton(
                                onClick = {
                                    val points = pointsText.toIntOrNull() ?: 10
                                    val pValue = penaltyValue.toIntOrNull() ?: 0
                                    val pMax = penaltyMax.toIntOrNull() ?: 0

                                    // Build assignment rotation from rotation slots
                                    val rotation: List<AssignmentSlot> = if (hasRotation) {
                                        rotationSlots.entries
                                            .filter { it.value.isNotBlank() }
                                            .map { (day, memberId) ->
                                                AssignmentSlot(dayOfWeek = day, memberId = memberId)
                                            }
                                    } else {
                                        emptyList()
                                    }

                                    taskModel.updateTask(
                                        householdId = householdId,
                                        taskId = task.id,
                                        title = title,
                                        description = description,
                                        points = points,
                                        frequency = frequency,
                                        recurrenceDays = recurrenceDays.toList().sorted(),
                                        tags = tags,
                                        penaltyMode = if (hasPenalty) penaltyMode else null,
                                        penaltyValue = pValue,
                                        penaltyInterval = penaltyInterval,
                                        penaltyMax = pMax,
                                        assignmentRotation = rotation
                                    )
                                },
                                enabled = actionState !is TaskActionState.Loading && title.isNotBlank(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Guardar", fontWeight = FontWeight.Bold)
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
                                Text(
                                    text = "❌ ${(actionState as TaskActionState.Error).message}",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // ── Basic info ──
                    item {
                        Text(
                            text = "📝 Información básica",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Título de la tarea *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = title.isBlank(),
                            supportingText = {
                                if (title.isBlank()) {
                                    Text("El título es obligatorio")
                                }
                            }
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Descripción") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = pointsText,
                            onValueChange = { pointsText = it },
                            label = { Text("Puntos") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pointsText.toIntOrNull() == null,
                            supportingText = {
                                if (pointsText.toIntOrNull() == null) {
                                    Text("Debe ser un número")
                                }
                            }
                        )
                    }

                    // ── Frequency ──
                    item {
                        Text(
                            text = "🔄 Frecuencia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val freqs = listOf(
                                "once" to "Una vez",
                                "daily" to "Diaria",
                                "weekly" to "Semanal",
                                "monthly" to "Mensual"
                            )
                            freqs.forEach { (key, label) ->
                                FilterChip(
                                    selected = frequency == key,
                                    onClick = { frequency = key },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Teal100,
                                        selectedLabelColor = Teal900
                                    )
                                )
                            }
                        }
                    }

                    // Recurrence days (only for weekly)
                    if (frequency == "weekly") {
                        item {
                            Text(
                                text = "Días de repetición",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val days = listOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D")
                                days.forEach { (day, label) ->
                                    FilterChip(
                                        selected = day in recurrenceDays,
                                        onClick = {
                                            recurrenceDays = if (day in recurrenceDays) {
                                                recurrenceDays - day
                                            } else {
                                                recurrenceDays + day
                                            }
                                        },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Coral100,
                                            selectedLabelColor = Coral800
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ── Tags ──
                    item {
                        Text(
                            text = "🏷️ Etiquetas",
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
                                label = { Text("Añadir etiqueta") },
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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

                    // Predefined tags
                    item {
                        val predefinedTags = listOf(
                            "limpieza", "cocina", "compras", "mascotas",
                            "mantenimiento", "niños", "exterior", "administración", "otro"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            predefinedTags.take(6).forEach { tag ->
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
                    item {
                        val predefinedTags = listOf(
                            "limpieza", "cocina", "compras", "mascotas",
                            "mantenimiento", "niños", "exterior", "administración", "otro"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            predefinedTags.drop(6).forEach { tag ->
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
                        Text(
                            text = "👥 Asignación",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                                                    checkedColor = Teal600
                                                )
                                            )
                                            Text(
                                                text = "${if (member.role == "admin") "👑" else "🧒"} ${member.displayName}",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is MemberUiState.Loading -> {
                            item {
                                CircularProgressIndicator(color = Teal600)
                            }
                        }

                        else -> {}
                    }

                    // Rotation toggle
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔄 Rotación semanal (diferente persona cada día)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = hasRotation,
                                onCheckedChange = { hasRotation = it },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Coral500
                                )
                            )
                        }
                    }

                    // Day-of-week rotation selectors
                    if (hasRotation) {
                        when (val mState = memberState) {
                            is MemberUiState.Success -> {
                                val members = mState.members
                                val days = listOf(
                                    1 to "Lunes", 2 to "Martes", 3 to "Miércoles",
                                    4 to "Jueves", 5 to "Viernes", 6 to "Sábado", 7 to "Domingo"
                                )
                                days.forEach { (day, label) ->
                                    item {
                                        var expanded by remember { mutableStateOf(false) }
                                        val selectedMember = members.find { it.id == rotationSlots[day] }
                                        val displayText = selectedMember?.displayName ?: "Sin asignar"

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.width(80.dp),
                                                fontWeight = FontWeight.Medium
                                            )

                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedButton(
                                                    onClick = { expanded = true },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = displayText,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1
                                                    )
                                                    Text("▼")
                                                }

                                                DropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Sin asignar") },
                                                        onClick = {
                                                            rotationSlots = rotationSlots.toMutableMap().apply { put(day, "") }
                                                            expanded = false
                                                        }
                                                    )
                                                    members.forEach { member ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text("${if (member.role == "admin") "👑" else "🧒"} ${member.displayName}")
                                                            },
                                                            onClick = {
                                                                rotationSlots = rotationSlots.toMutableMap().apply { put(day, member.id) }
                                                                expanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            is MemberUiState.Loading -> {
                                item {
                                    CircularProgressIndicator(color = Teal600)
                                }
                            }

                            else -> {}
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
                                text = "⚠️ Penalización por retraso",
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
                                    label = { Text("Puntos fijos") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Coral100,
                                        selectedLabelColor = Coral800
                                    )
                                )
                                FilterChip(
                                    selected = penaltyMode == "percentage",
                                    onClick = { penaltyMode = "percentage" },
                                    label = { Text("Porcentaje") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Coral100,
                                        selectedLabelColor = Coral800
                                    )
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = penaltyValue,
                                onValueChange = { penaltyValue = it },
                                label = {
                                    Text(if (penaltyMode == "fixed") "Penalización (puntos)" else "Penalización (%)")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = {
                                    Text(if (penaltyMode == "fixed")
                                        "Ej: 10 → -10 pts por cada período de retraso"
                                    else "Ej: 20 → -20% de los puntos por cada período de retraso")
                                }
                            )
                        }

                        item {
                            Text(
                                text = "Intervalo de penalización",
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
                                    label = { Text("Diario") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Coral50,
                                        selectedLabelColor = Coral700
                                    )
                                )
                                FilterChip(
                                    selected = penaltyInterval == "week",
                                    onClick = { penaltyInterval = "week" },
                                    label = { Text("Semanal") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Coral50,
                                        selectedLabelColor = Coral700
                                    )
                                )
                                FilterChip(
                                    selected = penaltyInterval == "month",
                                    onClick = { penaltyInterval = "month" },
                                    label = { Text("Mensual") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Coral50,
                                        selectedLabelColor = Coral700
                                    )
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = penaltyMax,
                                onValueChange = { penaltyMax = it },
                                label = { Text("Tope máximo (opcional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = {
                                    Text("Penalización nunca superará este valor. 0 = sin tope.")
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