package org.taskhub.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse
import org.taskhub.ui.i18n.AppStrings

data class MemberStatsData(
    val currentStreak: Int,
    val bestStreak: Int,
    val tasksPerDay: List<DayCount>,        // Last 7 days
    val dailyPoints: List<DayPoints>,        // Last 7 days
    val tasksByTag: List<TagCount>,          // Tag distribution
    val totalTasksCompleted: Int,
    val totalPoints: Int,
    val onTimeRate: Float,                   // 0.0 - 1.0
    val overdueCount: Int
)

data class DayCount(val dayLabel: String, val count: Int)
data class DayPoints(val dayLabel: String, val points: Int)
data class TagCount(val tag: String, val count: Int)

sealed class StatsUiState {
    data object Idle : StatsUiState()
    data object Loading : StatsUiState()
    data class Success(val data: MemberStatsData, val achievements: List<Achievement>) : StatsUiState()
    data class Error(val message: String) : StatsUiState()
}

/**
 * ScreenModel de [org.taskhub.ui.screens.StatsBody] — antes esa pantalla
 * inyectaba `FirestoreRepository` directamente y no tenía ScreenModel propio.
 */
class StatsScreenModel(
    private val repo: FirestoreRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Idle)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    fun loadStats(householdId: String, memberId: String, lang: String) {
        screenModelScope.launch {
            _uiState.value = StatsUiState.Loading
            try {
                // Load all data — including taskHistory for accurate stats
                val tasks = repo.getTasks(householdId)
                val assignments = repo.getAllAssignments(householdId)
                val history = repo.getTaskHistory(householdId)
                val members = repo.getMembers(householdId)
                val member = members.find { it.id == memberId }

                if (member != null) {
                    val data = computeStats(tasks, assignments, history, member, lang)
                    val unlocked = repo.getMemberAchievements(householdId, memberId)
                    val achievements = AchievementChecker.getAchievementsWithStatus(unlocked)
                    _uiState.value = StatsUiState.Success(data, achievements)
                } else {
                    // Sin esto, la pantalla se quedaba completamente en blanco (ni error, ni
                    // reintento) cuando memberId aún no se había resuelto (currentMemberId
                    // arranca en "" y se resuelve de forma asíncrona vía red) — el usuario no
                    // podía distinguir "cargando" de "roto".
                    _uiState.value = StatsUiState.Error(
                        AppStrings.get("transfer_error_member_not_found", lang)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = StatsUiState.Error(
                    e.message ?: AppStrings.get("stats_error_loading", lang)
                )
            }
        }
    }
}

// ── Computation ────────────────────────────────────────────

private fun computeStats(
    tasks: List<TaskResponse>,
    assignments: List<TaskAssignmentResponse>,
    history: List<TaskHistoryResponse>,
    member: MemberResponse,
    lang: String
): MemberStatsData {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(tz).date

    // Merge completions from assignments AND taskHistory
    // taskHistory captures direct completeTask() calls
    // assignments capture completeAssignment() calls
    val memberHistory = history.filter { it.memberId == member.id }

    // Tasks completed by member (from assignments). pointsAwarded > 0 excluye
    // las compleciones "fantasma" que completeTask/completeAssignment crean
    // en las asignaciones HERMANAS al cerrar el ciclo para todos los
    // miembros asignados (pointsAwarded=0 porque los puntos ya se otorgaron
    // solo a quien completó realmente) — sin este filtro, inflaban el
    // recuento de tareas completadas de miembros que no hicieron nada (panel
    // de revisión 2026-09-03/04, Experto 2/8).
    val memberAssignments = assignments.filter { it.memberId == member.id }
    val completedAssignments = memberAssignments.filter {
        it.status == "completed" && it.completedAt != null && (it.pointsAwarded ?: 0) > 0
    }

    // Combine both sources for per-day counts. taskId incluido para que la
    // distribución por categoría (más abajo) pueda contar TODAS las
    // compleciones reales, no solo las que llegaron vía completeAssignment()
    // — antes el pie chart solo miraba completedAssignments, subestimando
    // (a veces a 0) las tareas completadas por completeTask() (flujo
    // principal de la lista), que se registran en taskHistory.
    data class CompletionRecord(val completedAt: Long, val points: Int, val onTime: Boolean, val taskId: String)

    val fromAssignments = completedAssignments.map { a ->
        CompletionRecord(a.completedAt ?: 0L, a.pointsAwarded ?: 0, a.onTime ?: true, a.taskId)
    }
    val fromHistory = memberHistory.map { h ->
        CompletionRecord(h.completedAt, h.points, h.onTime, h.taskId)
    }
    val allCompletions = fromAssignments + fromHistory

    // Tasks per day (last 7 days)
    val days = (0..6).map { offset ->
        val date = today.plus(-offset, DateTimeUnit.DAY)
        date
    }.reversed() // Most recent last

    val tasksPerDay = days.map { date ->
        val dayStart = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        val count = allCompletions.count { c ->
            c.completedAt >= dayStart && c.completedAt < dayEnd
        }
        val dayLabel = "${date.dayOfMonth}/${date.monthNumber}"
        DayCount(dayLabel, count)
    }

    // Daily points (last 7 days)
    val dailyPoints = days.map { date ->
        val dayStart = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        val points = allCompletions.sumOf { c ->
            if (c.completedAt in dayStart until dayEnd) c.points else 0
        }
        DayPoints("${date.dayOfMonth}/${date.monthNumber}", points)
    }

    // Tag distribution — de TODAS las compleciones (assignments + taskHistory),
    // no solo assignments (ver comentario de CompletionRecord arriba).
    val taskMap = tasks.associateBy { it.id }
    val completedTaskIds = allCompletions.map { it.taskId }.distinct()
    val completedTasks = completedTaskIds.mapNotNull { taskMap[it] }
    val tagCounts = mutableMapOf<String, Int>()
    for (task in completedTasks) {
        for (tag in task.tags.ifEmpty { listOf(AppStrings.get("stats_no_category", lang)) }) {
            tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
        }
    }
    val tasksByTag = tagCounts.entries
        .sortedByDescending { it.value }
        .take(6)
        .map { TagCount(it.key, it.value) }

    // On-time rate — from all completions
    val onTimeCount = allCompletions.count { it.onTime }

    // Total completions = allCompletions (from both sources)
    val totalCompletions = allCompletions.size

    return MemberStatsData(
        currentStreak = member.currentStreak,
        bestStreak = member.bestStreak,
        tasksPerDay = tasksPerDay,
        dailyPoints = dailyPoints,
        tasksByTag = tasksByTag,
        totalTasksCompleted = totalCompletions,
        totalPoints = member.totalPoints,
        onTimeRate = if (totalCompletions > 0) onTimeCount.toFloat() / totalCompletions else 0f,
        overdueCount = allCompletions.count { !it.onTime }
    )
}
