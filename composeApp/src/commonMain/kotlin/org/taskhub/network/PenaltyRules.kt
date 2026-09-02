package org.taskhub.network

import org.taskhub.network.models.TaskResponse

/**
 * Reglas puras de puntuación al completar una tarea (puntualidad +
 * penalización por retraso). Sin I/O — testable directamente en `commonTest`.
 *
 * Extraído de `FirestoreRepository` (donde vivía como métodos `private`)
 * para poder testear esta lógica de negocio sin depender de red — panel v4,
 * Experto 13, hueco #1.
 */
object PenaltyRules {

    /** Resultado de resolver puntos otorgados + puntualidad al completar una tarea. */
    data class CompletionOutcome(val onTime: Boolean, val pointsAwarded: Int)

    /**
     * Calcula si se completó a tiempo + los puntos a otorgar (con penalización
     * por retraso si toca), a partir de una fecha límite concreta.
     *
     * `dueDate == 0` significa "sin fecha límite" y nunca penaliza — incluye
     * tareas recurrentes antiguas sin `nextDueAt` todavía (migración
     * aditiva: fallback al comportamiento previo, sin penalización, hasta
     * que la próxima compleción puebla el campo).
     */
    fun resolveCompletionOutcome(task: TaskResponse, dueDate: Long, now: Long): CompletionOutcome {
        val onTime = dueDate == 0L || now <= dueDate
        val pointsAwarded = if (onTime) {
            task.points
        } else {
            val penalty = calculatePenalty(task, dueDate, now)
            maxOf(task.points - penalty, 0)
        }
        return CompletionOutcome(onTime, pointsAwarded)
    }

    /**
     * Calculate penalty points for an overdue task.
     *
     * - fixed mode: subtracts `penaltyValue` per interval
     * - percentage mode: subtracts `penaltyValue`% of task.points per interval
     * - Capped at `penaltyMax` (which should not exceed task.points)
     */
    fun calculatePenalty(task: TaskResponse, dueDate: Long, now: Long): Int {
        val mode = task.penaltyMode ?: return 0
        if (now <= dueDate) return 0

        val overdueMs = now - dueDate
        val intervalMs = when (task.penaltyInterval) {
            "week" -> 7L * 24 * 60 * 60 * 1000
            "month" -> 30L * 24 * 60 * 60 * 1000
            else -> 24L * 60 * 60 * 1000 // day
        }

        val intervals = (overdueMs / intervalMs).toInt() + 1 // +1 because first interval starts immediately

        val penalty = when (mode) {
            "fixed" -> task.penaltyValue * intervals
            "percentage" -> (task.points * task.penaltyValue * intervals) / 100
            else -> 0
        }

        // Cap at penaltyMax (if set) and never go below 0
        val capped = if (task.penaltyMax > 0) minOf(penalty, task.penaltyMax) else penalty
        return minOf(capped, task.points)
    }
}
