package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.TaskResponse
import org.taskhub.platform.updateWidgetPendingTasks
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SavedHousehold

/**
 * ViewModel compartido para [HomeScreen].
 *
 * Carga las tareas pendientes de TODOS los hogares del usuario
 * (incluyendo el espacio Personal) y actualiza el widget Android
 * con la lista agregada.
 */
class HomeScreenModel(
    private val repo: FirestoreRepository,
    private val householdStore: HouseholdStore
) : ScreenModel {

    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    /**
     * Carga las tareas pendientes de todos los hogares y
     * actualiza el widget con la lista combinada.
     */
    fun loadAllTasks() {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val households = householdStore.getSavedHouseholds()
                val allTasks = mutableListOf<Pair<String, TaskResponse>>() // householdId → task

                for (h in households) {
                    try {
                        val tasks = repo.getTasks(h.id)
                        tasks.forEach { task ->
                            if (isPending(task)) {
                                allTasks.add(h.id to task)
                            }
                        }
                    } catch (_: Exception) {
                        // Silently skip households that fail (offline, deleted, etc.)
                    }
                }

                // Sort: overdue first, then by due date, then no-due-date last
                val now = Clock.System.now().toEpochMilliseconds()
                val sorted = allTasks.sortedBy { (_, task) ->
                    if (task.dueDate > 0 && task.dueDate < now) 0 // overdue
                    else if (task.dueDate > 0) task.dueDate
                    else Long.MAX_VALUE // no due date
                }

                // Update widget
                val widgetText = buildWidgetText(sorted, households)
                updateWidgetPendingTasks(widgetText)

                _uiState.value = HomeScreenUiState(
                    isLoading = false,
                    householdTasks = sorted.groupBy({ it.first }, { it.second }),
                    pendingCount = sorted.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al cargar tareas"
                )
            }
        }
    }

    /**
     * Determina si una tarea está pendiente (no completada hoy).
     */
    private fun isPending(task: TaskResponse): Boolean {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        val todayStartEpoch = today.atStartOfDayIn(tz).toEpochMilliseconds()

        val due = when (task.frequency) {
            "daily" -> {
                val lcd = task.lastCompletedDate
                if (lcd == null) true
                else {
                    val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
                    lcdDate != today
                }
            }
            "weekly" -> {
                val todayDow = today.dayOfWeek.ordinal + 1
                if (task.recurrenceDays.isNotEmpty() && todayDow !in task.recurrenceDays) false
                else {
                    val lcd = task.lastCompletedDate
                    if (lcd == null) true
                    else {
                        val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
                        lcdDate != today
                    }
                }
            }
            "monthly" -> {
                val lcd = task.lastCompletedDate
                if (lcd == null) true
                else {
                    val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
                    lcdDate.month != today.month || lcdDate.year != today.year
                }
            }
            "once" -> task.lastCompletedDate == null
            else -> false
        }

        val done = task.lastCompletedDate != null && task.lastCompletedDate >= todayStartEpoch
        return due && !done
    }

    /**
     * Construye el texto para el widget agrupando por hogar.
     */
    private fun buildWidgetText(
        sorted: List<Pair<String, TaskResponse>>,
        households: List<SavedHousehold>
    ): String {
        if (sorted.isEmpty()) return "🎉 ¡No hay tareas pendientes!"

        val now = Clock.System.now().toEpochMilliseconds()
        val householdNames = households.associate { it.id to it.name }

        return sorted.joinToString("\n") { (hid, task) ->
            val freqIcon = when (task.frequency) {
                "daily" -> "🔄"
                "weekly" -> "📅"
                "monthly" -> "📆"
                else -> "•"
            }
            val overdue = task.dueDate > 0 && task.dueDate < now
            val marker = if (overdue) "⚠️" else ""
            val householdName = householdNames[hid]
            val prefix = if (householdName != null && householdName != "Personal") "[$householdName] " else ""
            "$marker$freqIcon $prefix${task.title}"
        }
    }

    data class HomeScreenUiState(
        val isLoading: Boolean = true,
        val householdTasks: Map<String, List<TaskResponse>> = emptyMap(),
        val pendingCount: Int = 0,
        val error: String? = null
    )
}