package org.taskhub.ui.models

import kotlinx.datetime.Clock
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test de regresión para `computeStats` (`internal` desde el panel de
 * revisión 2026-09-11 para poder testearla) — el doble conteo de
 * compleciones (panel 2026-09-06/09-10, `distinctBy taskId+completedAt`)
 * estuvo en producción varios días sin detectarse porque, siendo `private`,
 * no existía forma de escribirle un test de regresión.
 */
class StatsScreenModelTest {

    private fun member(id: String = "m1") = MemberResponse(
        id = id,
        householdId = "h1",
        displayName = "Ana",
        role = "child",
        totalPoints = 100
    )

    private fun task(id: String, tags: List<String> = emptyList()) = TaskResponse(
        id = id,
        householdId = "h1",
        createdBy = "m1",
        title = "Tarea $id",
        tags = tags
    )

    @Test
    fun `una compleción registrada en assignments Y taskHistory se cuenta una sola vez`() {
        val now = Clock.System.now().toEpochMilliseconds()
        val tasks = listOf(task("t1"))
        // completeAssignment() escribe la MISMA compleción en la asignación
        // del completer (pointsAwarded > 0) y en taskHistory — el escenario
        // que producía el doble conteo antes del fix.
        val assignments = listOf(
            TaskAssignmentResponse(
                id = "a1", taskId = "t1", memberId = "m1",
                status = "completed", completedAt = now, pointsAwarded = 10, onTime = true
            )
        )
        val history = listOf(
            TaskHistoryResponse(id = "h1", taskId = "t1", memberId = "m1", points = 10, completedAt = now, onTime = true)
        )

        val stats = computeStats(tasks, assignments, history, member(), "es")

        assertEquals(1, stats.totalTasksCompleted)
    }

    @Test
    fun `compleciones distintas taskId ambas cuentan`() {
        val now = Clock.System.now().toEpochMilliseconds()
        val tasks = listOf(task("t1"), task("t2"))
        val assignments = listOf(
            TaskAssignmentResponse(
                id = "a1", taskId = "t1", memberId = "m1",
                status = "completed", completedAt = now, pointsAwarded = 10, onTime = true
            )
        )
        val history = listOf(
            TaskHistoryResponse(id = "h1", taskId = "t2", memberId = "m1", points = 5, completedAt = now, onTime = true)
        )

        val stats = computeStats(tasks, assignments, history, member(), "es")

        assertEquals(2, stats.totalTasksCompleted)
    }

    @Test
    fun `asignaciones fantasma con pointsAwarded 0 de miembros hermanos no cuentan`() {
        val now = Clock.System.now().toEpochMilliseconds()
        val tasks = listOf(task("t1"))
        // El miembro auditado (m1) no completó nada — pointsAwarded=0 es la
        // asignación "fantasma" que se crea en las asignaciones hermanas al
        // cerrar el ciclo para todos los asignados.
        val assignments = listOf(
            TaskAssignmentResponse(
                id = "a1", taskId = "t1", memberId = "m1",
                status = "completed", completedAt = now, pointsAwarded = 0, onTime = true
            )
        )

        val stats = computeStats(tasks, assignments, emptyList(), member(), "es")

        assertEquals(0, stats.totalTasksCompleted)
    }
}
