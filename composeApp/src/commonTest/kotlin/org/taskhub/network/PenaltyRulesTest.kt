package org.taskhub.network

import org.taskhub.network.models.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PenaltyRules] es la lógica de negocio de puntos más crítica del proyecto
 * (puntos reales otorgados/penalizados al completar una tarea) — antes vivía
 * como métodos `private` de `FirestoreRepository`, sin ningún test posible
 * por depender de red (panel v4, Experto 13, hueco #1).
 */
class PenaltyRulesTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private fun task(
        points: Int = 100,
        penaltyMode: String? = null,
        penaltyValue: Int = 0,
        penaltyInterval: String = "day",
        penaltyMax: Int = 0
    ) = TaskResponse(
        id = "task-1",
        householdId = "household-1",
        createdBy = "member-1",
        title = "Test task",
        points = points,
        penaltyMode = penaltyMode,
        penaltyValue = penaltyValue,
        penaltyInterval = penaltyInterval,
        penaltyMax = penaltyMax
    )

    // ── resolveCompletionOutcome ─────────────────────────────────

    @Test
    fun resolveCompletionOutcome_noDueDate_isOnTimeWithFullPoints() {
        // dueDate == 0 significa "sin fecha límite": nunca penaliza.
        val outcome = PenaltyRules.resolveCompletionOutcome(task(points = 50), dueDate = 0L, now = Long.MAX_VALUE)
        assertTrue(outcome.onTime)
        assertEquals(50, outcome.pointsAwarded)
    }

    @Test
    fun resolveCompletionOutcome_completedExactlyAtDueDate_isOnTime() {
        val outcome = PenaltyRules.resolveCompletionOutcome(task(points = 50), dueDate = 1_000L, now = 1_000L)
        assertTrue(outcome.onTime)
        assertEquals(50, outcome.pointsAwarded)
    }

    @Test
    fun resolveCompletionOutcome_completedAfterDueDate_isLate() {
        val outcome = PenaltyRules.resolveCompletionOutcome(task(points = 50), dueDate = 1_000L, now = 1_001L)
        assertFalse(outcome.onTime)
    }

    @Test
    fun resolveCompletionOutcome_lateWithoutPenaltyMode_awardsFullPointsButNotOnTime() {
        // Sin penaltyMode configurado la penalización es 0, pero onTime sigue
        // siendo false: "a tiempo" y "puntos íntegros" son cosas distintas.
        val outcome = PenaltyRules.resolveCompletionOutcome(
            task(points = 50, penaltyMode = null), dueDate = 1_000L, now = 1_000L + dayMs
        )
        assertFalse(outcome.onTime)
        assertEquals(50, outcome.pointsAwarded)
    }

    @Test
    fun resolveCompletionOutcome_lateWithFixedPenalty_subtractsFromPoints() {
        val outcome = PenaltyRules.resolveCompletionOutcome(
            task(points = 100, penaltyMode = "fixed", penaltyValue = 10, penaltyInterval = "day"),
            dueDate = 1_000L,
            now = 1_000L + dayMs / 2 // medio día tarde -> 1 intervalo (empieza al pasar la fecha límite) -> -10
        )
        assertEquals(90, outcome.pointsAwarded)
    }

    @Test
    fun resolveCompletionOutcome_pointsNeverGoBelowZero() {
        val outcome = PenaltyRules.resolveCompletionOutcome(
            task(points = 5, penaltyMode = "fixed", penaltyValue = 1000, penaltyInterval = "day"),
            dueDate = 1_000L,
            now = 1_000L + dayMs
        )
        assertEquals(0, outcome.pointsAwarded)
    }

    // ── calculatePenalty ──────────────────────────────────────────

    @Test
    fun calculatePenalty_noPenaltyMode_isZero() {
        assertEquals(0, PenaltyRules.calculatePenalty(task(penaltyMode = null), dueDate = 1_000L, now = 1_000L + dayMs))
    }

    @Test
    fun calculatePenalty_notYetOverdue_isZero() {
        val overdueTask = task(penaltyMode = "fixed", penaltyValue = 10)
        assertEquals(0, PenaltyRules.calculatePenalty(overdueTask, dueDate = 1_000L, now = 500L))
    }

    @Test
    fun calculatePenalty_fixedMode_multipleDayIntervals() {
        // 2.5 días tarde -> 3 intervalos de día (redondeo hacia arriba: el
        // primer intervalo empieza inmediatamente al pasar la fecha límite).
        val overdueTask = task(points = 100, penaltyMode = "fixed", penaltyValue = 10, penaltyInterval = "day")
        val overdueBy2AndHalfDays = 1_000L + (2 * dayMs) + (dayMs / 2)
        assertEquals(30, PenaltyRules.calculatePenalty(overdueTask, dueDate = 1_000L, now = overdueBy2AndHalfDays))
    }

    @Test
    fun calculatePenalty_percentageMode_computesPercentOfPointsPerInterval() {
        // 10 días tarde con intervalo semanal -> 2 intervalos (1 semana + resto) -> 2*20% = 40% de 100 = 40
        val overdueTask = task(points = 100, penaltyMode = "percentage", penaltyValue = 20, penaltyInterval = "week")
        assertEquals(40, PenaltyRules.calculatePenalty(overdueTask, dueDate = 0L, now = 10 * dayMs))
    }

    @Test
    fun calculatePenalty_monthInterval_usesThirtyDayIntervals() {
        val overdueTask = task(points = 100, penaltyMode = "fixed", penaltyValue = 5, penaltyInterval = "month")
        // 35 días tarde -> 2 intervalos de 30 días -> -10
        assertEquals(10, PenaltyRules.calculatePenalty(overdueTask, dueDate = 0L, now = 35 * dayMs))
    }

    @Test
    fun calculatePenalty_cappedAtPenaltyMax() {
        val overdueTask = task(points = 100, penaltyMode = "fixed", penaltyValue = 50, penaltyInterval = "day", penaltyMax = 80)
        // 2.5 días tarde -> 3 intervalos * 50 = 150, pero el tope es 80.
        val overdueBy2AndHalfDays = (2 * dayMs) + (dayMs / 2)
        assertEquals(80, PenaltyRules.calculatePenalty(overdueTask, dueDate = 0L, now = overdueBy2AndHalfDays))
    }

    @Test
    fun calculatePenalty_zeroPenaltyMax_meansUncapped() {
        val overdueTask = task(points = 1000, penaltyMode = "fixed", penaltyValue = 50, penaltyInterval = "day", penaltyMax = 0)
        // 2.5 días tarde -> 3 intervalos * 50 = 150, sin tope.
        val overdueBy2AndHalfDays = (2 * dayMs) + (dayMs / 2)
        assertEquals(150, PenaltyRules.calculatePenalty(overdueTask, dueDate = 0L, now = overdueBy2AndHalfDays))
    }

    @Test
    fun calculatePenalty_neverExceedsTaskPoints() {
        // Sin penaltyMax, un penaltyValue alto podría superar los puntos de la
        // tarea — la penalización nunca debe superar `task.points`.
        val overdueTask = task(points = 20, penaltyMode = "fixed", penaltyValue = 1000, penaltyInterval = "day")
        assertEquals(20, PenaltyRules.calculatePenalty(overdueTask, dueDate = 0L, now = dayMs))
    }
}
