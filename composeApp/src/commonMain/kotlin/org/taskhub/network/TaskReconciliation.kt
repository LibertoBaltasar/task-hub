// Lógica pura (sin I/O) de detección de tareas "completadas sin puntos" —
// ver KDoc de [FirestoreRepository.reconcileMissingTaskPoints] para el
// mecanismo de reparación completo y el fallo que soluciona.
package org.taskhub.network

import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse

/**
 * Detecta tareas marcadas como completadas (`completedBy`/`lastCompletedDate`
 * fijados) cuyo registro de `taskHistory` correspondiente falta, o existe
 * pero quedó con `pointsApplied=false` — el rastro de un fallo entre el PATCH
 * que marca la tarea completada y el otorgamiento de puntos/historial (ver
 * KDoc de [FirestoreRepository.completeTask]).
 *
 * Identifica el registro de una compleción por (taskId, completedAt) — igual
 * que el resto del código de deshacer/reasignar (ver
 * [FirestoreRepository.undoTaskCompletionAssignments]), así que no puede
 * confundir dos compleciones distintas de la misma tarea entre sí.
 */
object TaskReconciliation {
    fun findTasksNeedingPointsReconciliation(
        tasks: List<TaskResponse>,
        history: List<TaskHistoryResponse>
    ): List<TaskResponse> {
        return tasks.filter { task ->
            val completedAt = task.lastCompletedDate
            if (task.completedBy == null || completedAt == null) {
                false
            } else {
                val record = history.find { it.taskId == task.id && it.completedAt == completedAt }
                record == null || !record.pointsApplied
            }
        }
    }
}
