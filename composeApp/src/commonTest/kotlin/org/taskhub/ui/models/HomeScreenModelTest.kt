package org.taskhub.ui.models

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.taskhub.network.models.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test de regresión para la divergencia entre [isPending] y `previewFilter`
 * corregida en el panel 2026-09-11: antes del fix, `HomeScreenModel.previewFilter`
 * (usada por `HouseholdTaskSection`) miraba solo `lastCompletedDate == null`,
 * ignorando la recurrencia — una tarea diaria completada AYER y de nuevo
 * pendiente HOY aparecía en el dashboard agregado ([HomeScreenModel.loadAllTasks],
 * vía [isPending]) pero nunca en la previsualización por hogar. Desde el fix,
 * [previewFilterTasks] delega en [isPending], así que este test verifica que
 * ambas funciones producen el mismo resultado para el mismo escenario.
 */
class HomeScreenModelTest {

    private val tz = TimeZone.currentSystemDefault()
    private val today = LocalDate(2026, 9, 10)
    private val now: Instant = today.atStartOfDayIn(tz)
    private val yesterday = LocalDate(2026, 9, 9).atStartOfDayIn(tz).toEpochMilliseconds()

    private fun task(
        id: String = "t1",
        frequency: String = "once",
        recurrenceDays: List<Int> = emptyList(),
        recurrenceDay: Int? = null,
        lastCompletedDate: Long? = null
    ) = TaskResponse(
        id = id,
        householdId = "h1",
        createdBy = "m1",
        title = "Tarea $id",
        frequency = frequency,
        recurrenceDays = recurrenceDays,
        recurrenceDay = recurrenceDay,
        lastCompletedDate = lastCompletedDate
    )

    @Test
    fun `tarea diaria completada ayer esta pendiente hoy segun isPending`() {
        val daily = task(frequency = "daily", lastCompletedDate = yesterday)

        assertTrue(isPending(daily, now))
    }

    @Test
    fun `previewFilterTasks coincide con isPending para una tarea diaria completada ayer`() {
        val daily = task(frequency = "daily", lastCompletedDate = yesterday)

        val fromIsPending = isPending(daily, now)
        val fromPreview = previewFilterTasks(listOf(daily), now).contains(daily)

        assertEquals(fromIsPending, fromPreview)
        assertTrue(fromPreview, "una tarea diaria completada ayer debe seguir apareciendo en la previsualización por hogar")
    }

    @Test
    fun `previewFilterTasks no incluye una tarea ya completada hoy`() {
        val completedToday = task(frequency = "daily", lastCompletedDate = now.toEpochMilliseconds())

        assertEquals(emptyList(), previewFilterTasks(listOf(completedToday), now))
    }

    @Test
    fun `previewFilterTasks y isPending coinciden en un conjunto mixto de tareas`() {
        val tasks = listOf(
            task(id = "once-pendiente"),
            task(id = "diaria-completada-hoy", frequency = "daily", lastCompletedDate = now.toEpochMilliseconds()),
            task(id = "diaria-completada-ayer", frequency = "daily", lastCompletedDate = yesterday),
            task(id = "semanal-nunca-completada", frequency = "weekly", recurrenceDays = listOf(today.dayOfWeek.ordinal + 1))
        )

        val expectedIds = tasks.filter { isPending(it, now) }.map { it.id }.toSet()
        val actualIds = previewFilterTasks(tasks, now).map { it.id }.toSet()

        assertEquals(expectedIds, actualIds)
    }
}
