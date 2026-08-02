package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.TaskActionState
import org.taskhub.ui.models.TaskScreenModel
import org.taskhub.ui.theme.*

// ────────────────────────────────────────────────────────────
//  CreateTaskScreen
// ────────────────────────────────────────────────────────────

data class CreateTaskScreen(
    val householdId: String,
    val createdBy: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val taskModel = koinScreenModel<TaskScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val actionState by taskModel.actionState.collectAsState()
        val memberState by memberModel.uiState.collectAsState()

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
        var tagsText by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf(listOf<String>()) }
        var selectedMembers by remember { mutableStateOf(setOf<String>()) }
        var mandatory by remember { mutableStateOf(false) }
        var hasDeadline by remember { mutableStateOf(false) }
        var deadlineDay by remember { mutableStateOf("") }
        var deadlineTime by remember { mutableStateOf("12:00") }
        var hasPenalty by remember { mutableStateOf(false) }
        var penaltyMode by remember { mutableStateOf("fixed") }
        var penaltyValue by remember { mutableStateOf("") }
        var penaltyInterval by remember { mutableStateOf("day") }
        var penaltyMax by remember { mutableStateOf("") }

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
                            Text("← Cancelar")
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "➕ Nueva tarea",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.weight(1f))

                        // Save button
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
                                        title = title.ifBlank { "Sin título" },
                                        description = description,
                                        points = points,
                                        frequency = frequency,
                                        recurrenceDays = recurrenceDays.toList().sorted(),
                                        tags = tags,
                                        penaltyMode = if (hasPenalty) penaltyMode else null,
                                        penaltyValue = pValue,
                                        penaltyInterval = penaltyInterval,
                                        penaltyMax = pMax,
                                        memberIds = selectedMembers.toList(),
                                        mandatory = mandatory,
                                        dueDate = dueDate
                                    )
                                },
                                enabled = actionState !is TaskActionState.Loading,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Crear", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

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
                            color = Teal700
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Título de la tarea *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    // ── Frequency ──
                    item {
                        Text(
                            text = "🔄 Frecuencia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Teal700
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
                            color = Teal700
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
                                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
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
                            color = Teal700
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
                                CircularProgressIndicator(color = Teal600)
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
                                    text = "🔒 Obligatoria (no rechazable)",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = mandatory,
                                    onCheckedChange = { mandatory = it },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Coral500
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
                                text = "⏰ Fecha límite",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Teal700
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = deadlineDay,
                                    onValueChange = { deadlineDay = it },
                                    label = { Text("Fecha (YYYY-MM-DD)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = deadlineTime,
                                    onValueChange = { deadlineTime = it },
                                    label = { Text("Hora (HH:MM)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
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
                                text = "⚠️ Penalización por retraso",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Teal700
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

// ────────────────────────────────────────────────────────────
//  Helpers
// ────────────────────────────────────────────────────────────

private fun parseDeadline(dateStr: String, timeStr: String): Long {
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
