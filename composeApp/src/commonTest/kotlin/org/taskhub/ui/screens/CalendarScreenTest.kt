package org.taskhub.ui.screens

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.taskhub.network.models.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresión 2026-09-17: las tareas "once" pendientes SIN `dueDate` se
 * marcaban como "due" en TODAS las celdas del calendario (ver antiguo KDoc de
 * `isTaskDueOnDay`). Ahora quedan fuera de la cuadrícula por completo y se
 * listan aparte en la sección "Pendientes" (`isTaskPendingWithoutDueDate`).
 * Las tareas recurrentes (daily/weekly/monthly), que tampoco tienen
 * `dueDate`, deben seguir tocando en sus días de recurrencia normales — no
 * deben verse afectadas por este cambio.
 */
class CalendarScreenTest {

    private val tz = TimeZone.of("UTC")

    private fun task(
        id: String = "t1",
        frequency: String = "once",
        dueDate: Long = 0,
        lastCompletedDate: Long? = null,
        recurrenceDays: List<Int> = emptyList(),
        recurrenceDay: Int? = null,
        createdAt: Long = 0
    ) = TaskResponse(
        id = id,
        householdId = "h1",
        createdBy = "m1",
        title = "Tarea $id",
        frequency = frequency,
        dueDate = dueDate,
        lastCompletedDate = lastCompletedDate,
        recurrenceDays = recurrenceDays,
        recurrenceDay = recurrenceDay,
        createdAt = createdAt
    )

    @Test
    fun `tarea once sin fecha limite no aparece en ningun dia del calendario`() {
        val t = task(frequency = "once", dueDate = 0, lastCompletedDate = null)
        val days = listOf(
            LocalDate(2026, 9, 15),
            LocalDate(2026, 9, 17),
            LocalDate(2026, 10, 1)
        )
        days.forEach { day ->
            assertFalse(
                isTaskDueOnDay(t, day, tz),
                "una tarea once sin dueDate no debe aparecer como due en $day"
            )
        }
    }

    @Test
    fun `tarea once sin fecha limite se recoge para la seccion Pendientes`() {
        val pending = task(frequency = "once", dueDate = 0, lastCompletedDate = null)
        val withDueDate = task(id = "t2", frequency = "once", dueDate = 1_800_000_000_000L)
        val completed = task(id = "t3", frequency = "once", dueDate = 0, lastCompletedDate = 1_700_000_000_000L)
        val recurring = task(id = "t4", frequency = "daily", dueDate = 0)

        assertTrue(isTaskPendingWithoutDueDate(pending))
        assertFalse(isTaskPendingWithoutDueDate(withDueDate))
        assertFalse(isTaskPendingWithoutDueDate(completed))
        assertFalse(isTaskPendingWithoutDueDate(recurring))
    }

    @Test
    fun `tarea diaria sin fecha limite sigue tocando cada dia en el calendario`() {
        val t = task(frequency = "daily", dueDate = 0, lastCompletedDate = null)
        assertTrue(isTaskDueOnDay(t, LocalDate(2026, 9, 17), tz))
        assertTrue(isTaskDueOnDay(t, LocalDate(2026, 9, 18), tz))
        assertFalse(isTaskPendingWithoutDueDate(t))
    }

    @Test
    fun `tarea semanal con dias concretos sigue su regla de recurrencia, sin fecha limite`() {
        // 2026-09-17 es jueves (4). Creada el 09-10, antes de su primer jueves objetivo.
        val createdAt = LocalDate(2026, 9, 10).atStartOfDayIn(tz).toEpochMilliseconds()
        val t = task(frequency = "weekly", dueDate = 0, recurrenceDays = listOf(4), createdAt = createdAt)
        assertTrue(isTaskDueOnDay(t, LocalDate(2026, 9, 17), tz))
        assertFalse(isTaskDueOnDay(t, LocalDate(2026, 9, 9), tz))
        assertFalse(isTaskPendingWithoutDueDate(t))
    }

    @Test
    fun `tarea mensual con dia concreto sigue su regla de recurrencia, sin fecha limite`() {
        // Objetivo: día 5 de cada mes. Creada el 08-20, antes del objetivo de septiembre.
        val createdAt = LocalDate(2026, 8, 20).atStartOfDayIn(tz).toEpochMilliseconds()
        val t = task(frequency = "monthly", dueDate = 0, recurrenceDay = 5, createdAt = createdAt)
        assertTrue(isTaskDueOnDay(t, LocalDate(2026, 9, 5), tz))
        assertFalse(isTaskDueOnDay(t, LocalDate(2026, 8, 1), tz))
        assertFalse(isTaskPendingWithoutDueDate(t))
    }

    @Test
    fun `groupTasksByDate no incluye la tarea once sin fecha en ningun dia del rango`() {
        val t = task(frequency = "once", dueDate = 0, lastCompletedDate = null)
        val weekRange = (15..21).map { LocalDate(2026, 9, it) }
        val result = groupTasksByDate(
            tasks = listOf(t),
            tz = tz,
            mode = CalendarMode.WEEK,
            weekRange = weekRange,
            monthGrid = emptyList()
        )
        assertTrue(result.values.flatten().none { it.task.id == t.id })
    }
}
