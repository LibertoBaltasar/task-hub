package org.taskhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.TaskResponse
import org.taskhub.storage.SavedHousehold
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.screens.TaskDetailScreen

@Composable
fun HouseholdTaskSection(
    household: SavedHousehold,
    onViewAll: (String) -> Unit = {}
) {
    val repo = koinInject<FirestoreRepository>()
    val navigator = LocalNavigator.currentOrThrow
    val lang = LocalAppSettings.current.currentLanguage
    val s = { key: String -> AppStrings.get(key, lang) }

    var tasks by remember { mutableStateOf<List<TaskResponse>>(emptyList()) }
    var expanded by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(household.id) {
        try {
            val allTasks = repo.getTasks(household.id)
            tasks = allTasks
                .filter { it.lastCompletedDate == null || it.lastCompletedDate == 0L }
                .take(5)
            error = null
        } catch (e: Exception) {
            error = s("household_task_section_error")
        }
        isLoading = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (household.isPersonal)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Etiqueta visual para espacio Personal
                if (household.isPersonal) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "👤",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = household.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (!isLoading && error == null) {
                    Text(
                        text = s("household_task_section_pending_count").replace("%d", "${tasks.size}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                 else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) s("household_task_section_collapse") else s("household_task_section_expand")
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))

                    val currentError = error
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = if (household.isPersonal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        currentError != null -> {
                            Text(
                                text = currentError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        tasks.isEmpty() -> {
                            Text(
                                text = if (household.isPersonal) s("household_task_section_empty_personal")
                                       else s("household_task_section_empty_shared"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        else -> {
                            tasks.forEach { task ->
                                TaskRow(task = task) {
                                    navigator.push(
                                        TaskDetailScreen(household.id, task.id)
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { onViewAll(household.id) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(s("household_task_section_view_all"))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskResponse, onClick: () -> Unit) {
    val lang = LocalAppSettings.current.currentLanguage
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "+${task.points}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.tags.isNotEmpty()) {
                    Text(
                        text = task.tags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (task.dueDate > 0) {
                Text(
                    text = formatDueDate(task.dueDate, lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDueDate(epochMillis: Long, lang: String): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diffDays = (epochMillis - now) / (24 * 60 * 60 * 1000)
    return when {
        diffDays < 0 -> AppStrings.get("due_date_overdue", lang)
        diffDays == 0L -> AppStrings.get("tasks_due_today", lang)
        diffDays == 1L -> AppStrings.get("due_date_tomorrow", lang)
        else -> {
            val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
            val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${local.dayOfMonth}/${local.monthNumber}"
        }
    }
}