package org.taskhub.ui.models

import org.taskhub.network.models.TaskHistoryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AchievementChecker.countCompletedFromHistory] es la lógica CORREGIDA de
 * `TaskScreenModel.checkAndAwardAchievements` (contar desde `taskHistory` en
 * vez de `assignments`, panel de revisión 2026-09-04, Experto 8) extraída a
 * función pura testeable sin mocks (panel v7, Exp. 13, hueco cerrado) — mismo
 * patrón que [org.taskhub.network.AssignmentCompletionRulesTest].
 * [AchievementChecker.checkNewAchievements] ya era pura pero tampoco tenía
 * test propio.
 */
class AchievementCheckerTest {

    private fun history(memberId: String, completedAt: Long = 0L, points: Int = 10) =
        TaskHistoryResponse(
            id = "history-$memberId-$completedAt",
            taskId = "task-1",
            memberId = memberId,
            points = points,
            completedAt = completedAt
        )

    @Test
    fun countCompletedFromHistory_countsOnlyRecordsOfThatMember() {
        val history = listOf(
            history(memberId = "alice", completedAt = 1L),
            history(memberId = "bob", completedAt = 2L),
            history(memberId = "alice", completedAt = 3L)
        )

        assertEquals(2, AchievementChecker.countCompletedFromHistory(history, "alice"))
        assertEquals(1, AchievementChecker.countCompletedFromHistory(history, "bob"))
        assertEquals(0, AchievementChecker.countCompletedFromHistory(history, "carol"))
    }

    @Test
    fun countCompletedFromHistory_includesZeroPointRecords() {
        // Compleción real penalizada a 0 puntos por tardanza — antes (contando
        // desde `assignments` con `pointsAwarded > 0`) esta compleción NO
        // contaba, así que nunca se desbloqueaban logros de "N tareas"
        // (panel de revisión 2026-09-04, Experto 8, IMPORTANTE).
        val history = listOf(history(memberId = "alice", completedAt = 1L, points = 0))

        assertEquals(1, AchievementChecker.countCompletedFromHistory(history, "alice"))
    }

    @Test
    fun checkNewAchievements_unlocksFirstTaskOnFirstCompletion() {
        val newlyUnlocked = AchievementChecker.checkNewAchievements(
            totalTasksCompleted = 1,
            totalPoints = 10,
            currentStreak = 1,
            lastCompletedHour = 12,
            alreadyUnlocked = emptySet()
        )

        assertTrue("first_task" in newlyUnlocked)
    }

    @Test
    fun checkNewAchievements_skipsAlreadyUnlockedAchievements() {
        val newlyUnlocked = AchievementChecker.checkNewAchievements(
            totalTasksCompleted = 1,
            totalPoints = 10,
            currentStreak = 1,
            lastCompletedHour = 12,
            alreadyUnlocked = setOf("first_task")
        )

        assertTrue("first_task" !in newlyUnlocked)
    }

    @Test
    fun checkNewAchievements_unlocksEarlyBirdOnlyBeforeEightAm() {
        val early = AchievementChecker.checkNewAchievements(
            totalTasksCompleted = 1,
            totalPoints = 0,
            currentStreak = 0,
            lastCompletedHour = 7,
            alreadyUnlocked = emptySet()
        )
        val late = AchievementChecker.checkNewAchievements(
            totalTasksCompleted = 1,
            totalPoints = 0,
            currentStreak = 0,
            lastCompletedHour = 9,
            alreadyUnlocked = emptySet()
        )

        assertTrue("early_bird" in early)
        assertTrue("early_bird" !in late)
    }

    @Test
    fun checkNewAchievements_unlocksMultipleAtOnce() {
        val newlyUnlocked = AchievementChecker.checkNewAchievements(
            totalTasksCompleted = 10,
            totalPoints = 100,
            currentStreak = 5,
            lastCompletedHour = 7,
            alreadyUnlocked = emptySet()
        )

        assertEquals(
            setOf("first_task", "streak_5", "100_points", "10_tasks", "early_bird"),
            newlyUnlocked.toSet()
        )
    }
}
