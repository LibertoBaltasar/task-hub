package org.taskhub.network

import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TaskReconciliation] detecta tareas "completadas sin puntos" — el fallo que
 * [FirestoreRepository.completeTask]/[FirestoreRepository.completeAssignment]
 * pueden dejar a medias entre marcar la tarea completada y otorgar los
 * puntos/historial (ronda de deuda aplicable 2026-09-12, punto A1). Extraída
 * a función pura por el mismo motivo que [AssignmentCompletionRulesTest]: sin
 * esto, ningún test de integración es posible sin dependencia de red.
 */
class TaskReconciliationTest {

    private fun task(
        id: String,
        completedBy: String? = null,
        lastCompletedDate: Long? = null
    ) = TaskResponse(
        id = id,
        householdId = "hh-1",
        createdBy = "member-1",
        title = "Tarea $id",
        completedBy = completedBy,
        lastCompletedDate = lastCompletedDate
    )

    private fun history(
        taskId: String,
        completedAt: Long,
        pointsApplied: Boolean = true
    ) = TaskHistoryResponse(
        id = "h-$taskId-$completedAt",
        taskId = taskId,
        memberId = "member-1",
        points = 10,
        completedAt = completedAt,
        pointsApplied = pointsApplied
    )

    /** Nunca completada: nunca es candidata, con o sin historial. */
    @Test
    fun neverCompleted_isNeverACandidate() {
        val tasks = listOf(task(id = "t1"))

        val candidates = TaskReconciliation.findTasksNeedingPointsReconciliation(tasks, emptyList())

        assertTrue(candidates.isEmpty())
    }

    /** Completada con su registro de historial correspondiente ya aplicado: no hay nada que reparar. */
    @Test
    fun completedWithMatchingAppliedHistory_isNotACandidate() {
        val tasks = listOf(task(id = "t1", completedBy = "member-1", lastCompletedDate = 1000L))
        val history = listOf(history(taskId = "t1", completedAt = 1000L, pointsApplied = true))

        val candidates = TaskReconciliation.findTasksNeedingPointsReconciliation(tasks, history)

        assertTrue(candidates.isEmpty())
    }

    /** Completada sin NINGÚN registro de historial: fallo entre el PATCH y guardar el historial — candidata. */
    @Test
    fun completedWithNoHistoryRecord_isACandidate() {
        val tasks = listOf(task(id = "t1", completedBy = "member-1", lastCompletedDate = 1000L))

        val candidates = TaskReconciliation.findTasksNeedingPointsReconciliation(tasks, emptyList())

        assertEquals(listOf("t1"), candidates.map { it.id })
    }

    /** Historial guardado pero `pointsApplied=false`: fallo entre guardar el historial y otorgar los puntos — candidata. */
    @Test
    fun completedWithUnappliedHistory_isACandidate() {
        val tasks = listOf(task(id = "t1", completedBy = "member-1", lastCompletedDate = 1000L))
        val history = listOf(history(taskId = "t1", completedAt = 1000L, pointsApplied = false))

        val candidates = TaskReconciliation.findTasksNeedingPointsReconciliation(tasks, history)

        assertEquals(listOf("t1"), candidates.map { it.id })
    }

    /** Un registro de historial de OTRA compleción de la misma tarea (completedAt distinto) no cuenta como el correspondiente. */
    @Test
    fun historyFromADifferentCompletion_doesNotMatch() {
        val tasks = listOf(task(id = "t1", completedBy = "member-1", lastCompletedDate = 2000L))
        val history = listOf(history(taskId = "t1", completedAt = 1000L, pointsApplied = true))

        val candidates = TaskReconciliation.findTasksNeedingPointsReconciliation(tasks, history)

        assertEquals(listOf("t1"), candidates.map { it.id })
    }

    /** Varias tareas: solo las que de verdad lo necesitan aparecen como candidatas. */
    @Test
    fun mixedHousehold_onlyFlagsTasksThatNeedRepair() {
        val tasks = listOf(
            task(id = "ok", completedBy = "member-1", lastCompletedDate = 1000L),
            task(id = "orphan", completedBy = "member-1", lastCompletedDate = 2000L),
            task(id = "unapplied", completedBy = "member-1", lastCompletedDate = 3000L),
            task(id = "never-completed")
        )
        val history = listOf(
            history(taskId = "ok", completedAt = 1000L, pointsApplied = true),
            history(taskId = "unapplied", completedAt = 3000L, pointsApplied = false)
        )

        val candidates = TaskReconciliation.findTasksNeedingPointsReconciliation(tasks, history)

        assertEquals(setOf("orphan", "unapplied"), candidates.map { it.id }.toSet())
    }
}
