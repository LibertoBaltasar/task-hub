// Modelo de logros (achievements) y lógica pura para calcular cuáles se
// desbloquean según las estadísticas de un miembro. No es un ScreenModel:
// no expone StateFlow ni depende de repositorios; [TaskScreenModel] es quien
// invoca [AchievementChecker] tras completar una tarea y persiste el
// resultado. Se usa desde `ui/screens/` para pintar la lista de logros del
// miembro (bloqueados/desbloqueados).
package org.taskhub.ui.models

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskHistoryResponse

/**
 * Representa un logro desbloqueable por un miembro del hogar.
 *
 * @param id identificador estable del logro (se persiste en Firestore dentro
 *   del set de logros desbloqueados del miembro; no debe cambiarse una vez
 *   publicado o los usuarios "perderían" logros ya obtenidos).
 * @param title título corto mostrado en la UI.
 * @param description explicación de qué hay que hacer para desbloquearlo.
 * @param emoji icono textual del logro.
 * @param isUnlocked estado calculado en tiempo de presentación (no se guarda
 *   en este modelo; ver [AchievementChecker.getAchievementsWithStatus]).
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean = false
)

/**
 * Lógica pura (sin I/O ni dependencias de Firestore) para decidir qué logros
 * se desbloquean a partir de las estadísticas de un miembro. Se mantiene
 * deliberadamente separada de [TaskScreenModel] para poder testearla sin
 * mocks de red (ver nota en [countCompletedFromHistory]).
 */
object AchievementChecker {

    // ── Catálogo fijo de logros disponibles en la app. ──
    // El orden aquí es el orden en el que se muestran en la UI.
    val ALL_ACHIEVEMENTS = listOf(
        Achievement("first_task", "Primera tarea", "Completaste tu primera tarea", "🎯"),
        Achievement("streak_5", "5 días seguidos", "Mantuviste una racha de 5 días", "🔥"),
        Achievement("100_points", "100 puntos", "Alcanzaste 100 puntos totales", "⭐"),
        Achievement("10_tasks", "10 tareas", "Completaste 10 tareas", "📋"),
        Achievement("early_bird", "Madrugador", "Completaste una tarea antes de las 8am", "🌅")
    )

    /**
     * Check which achievements are newly unlocked given the member's current stats
     * and the set of already unlocked achievements.
     *
     * Parameters reflect the current state of the member.
     * Returns a list of achievement IDs that just got unlocked.
     */
    fun checkNewAchievements(
        totalTasksCompleted: Int,
        totalPoints: Int,
        currentStreak: Int,
        lastCompletedHour: Int?,
        alreadyUnlocked: Set<String>
    ): List<String> {
        val newlyUnlocked = mutableListOf<String>()

        if ("first_task" !in alreadyUnlocked && totalTasksCompleted >= 1) {
            newlyUnlocked.add("first_task")
        }
        if ("streak_5" !in alreadyUnlocked && currentStreak >= 5) {
            newlyUnlocked.add("streak_5")
        }
        if ("100_points" !in alreadyUnlocked && totalPoints >= 100) {
            newlyUnlocked.add("100_points")
        }
        if ("10_tasks" !in alreadyUnlocked && totalTasksCompleted >= 10) {
            newlyUnlocked.add("10_tasks")
        }
        if ("early_bird" !in alreadyUnlocked && lastCompletedHour != null && lastCompletedHour < 8) {
            newlyUnlocked.add("early_bird")
        }

        return newlyUnlocked
    }

    /**
     * Get the full list of achievements with unlocked status.
     */
    fun getAchievementsWithStatus(alreadyUnlocked: Set<String>): List<Achievement> {
        return ALL_ACHIEVEMENTS.map { a ->
            a.copy(isUnlocked = a.id in alreadyUnlocked)
        }
    }

    /**
     * Cuenta las tareas completadas por [memberId] desde [history]
     * (`taskHistory`, un registro por-compleción REAL) — no desde
     * `assignments`, que además de la compleción real crea entradas
     * "fantasma" en las asignaciones HERMANAS al cerrar el ciclo para todos
     * los miembros asignados. Filtrar `assignments` por `pointsAwarded > 0`
     * (como hacía la versión anterior de [TaskScreenModel.checkAndAwardAchievements])
     * evitaba esos fantasmas pero además undercontaba compleciones REALES
     * penalizadas a 0 puntos por tardanza — un miembro que completa tarde y
     * pierde todos los puntos nunca desbloqueaba logros de "N tareas
     * completadas" (panel de revisión 2026-09-04, Experto 8, IMPORTANTE).
     * Extraída como función PURA (sin I/O) para poder testear esta lógica
     * sin mocks (panel v7, Exp. 13).
     */
    fun countCompletedFromHistory(history: List<TaskHistoryResponse>, memberId: String): Int =
        history.count { it.memberId == memberId }
}