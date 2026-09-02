package org.taskhub.network

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointsRulesTest {

    private val tz = TimeZone.currentSystemDefault()

    // ── mondayStartOfWeek ──────────────────────────────────────

    @Test
    fun mondayStartOfWeek_resultIsMondayMidnight() {
        // Un mediodía local arbitrario, lejos de límites de DST.
        val someWednesdayNoon = LocalDateTime(2024, 1, 10, 12, 0, 0).toInstant(tz).toEpochMilliseconds()

        val weekStart = PointsRules.mondayStartOfWeek(someWednesdayNoon)

        val localDateTime = kotlinx.datetime.Instant.fromEpochMilliseconds(weekStart).toLocalDateTime(tz)
        assertEquals(kotlinx.datetime.DayOfWeek.MONDAY, localDateTime.date.dayOfWeek)
        assertEquals(0, localDateTime.hour)
        assertEquals(0, localDateTime.minute)
        assertEquals(0, localDateTime.second)
        assertTrue(weekStart <= someWednesdayNoon)
    }

    @Test
    fun mondayStartOfWeek_isIdempotent() {
        val now = LocalDateTime(2024, 3, 15, 9, 30, 0).toInstant(tz).toEpochMilliseconds()
        val weekStart = PointsRules.mondayStartOfWeek(now)

        assertEquals(weekStart, PointsRules.mondayStartOfWeek(weekStart))
    }

    @Test
    fun mondayStartOfWeek_sundayBeforeGoesToPreviousWeek() {
        val someInstant = LocalDateTime(2024, 3, 15, 9, 30, 0).toInstant(tz).toEpochMilliseconds()
        val weekStart = PointsRules.mondayStartOfWeek(someInstant)

        // Un segundo antes del propio lunes calculado cae, por definición, en la semana previa.
        val sundayJustBefore = kotlinx.datetime.Instant.fromEpochMilliseconds(weekStart)
            .plus(-1, DateTimeUnit.SECOND, tz)
        val previousWeekStart = PointsRules.mondayStartOfWeek(sundayJustBefore.toEpochMilliseconds())

        assertEquals(weekStart - 7L * 24 * 60 * 60 * 1000, previousWeekStart)
    }

    // ── currentAppreciationBudget ───────────────────────────────

    @Test
    fun currentAppreciationBudget_withinSameWeek_keepsGivenAndWeekStart() {
        val weekStart = 1_000_000L
        val now = weekStart + 1_000L // muy dentro de la misma semana

        val budget = PointsRules.currentAppreciationBudget(
            appreciationGiven = 30,
            appreciationWeekStart = weekStart,
            now = now
        )

        assertEquals(30, budget.given)
        assertEquals(weekStart, budget.weekStart)
        assertEquals(20, budget.remaining)
    }

    @Test
    fun currentAppreciationBudget_atTop_remainingIsZero() {
        val weekStart = 1_000_000L
        val budget = PointsRules.currentAppreciationBudget(
            appreciationGiven = PointsRules.WEEKLY_APPRECIATION_BUDGET,
            appreciationWeekStart = weekStart,
            now = weekStart
        )
        assertEquals(0, budget.remaining)
    }

    @Test
    fun currentAppreciationBudget_neverGoesNegative() {
        val weekStart = 1_000_000L
        val budget = PointsRules.currentAppreciationBudget(
            appreciationGiven = 999,
            appreciationWeekStart = weekStart,
            now = weekStart
        )
        assertEquals(0, budget.remaining)
    }

    @Test
    fun currentAppreciationBudget_afterWeekExpires_resets() {
        val oldWeekStart = 0L
        val now = 7L * 24 * 60 * 60 * 1000 + 5_000L // más allá de WEEK_MILLIS desde oldWeekStart

        val budget = PointsRules.currentAppreciationBudget(
            appreciationGiven = 40,
            appreciationWeekStart = oldWeekStart,
            now = now
        )

        assertEquals(0, budget.given)
        assertEquals(PointsRules.WEEKLY_APPRECIATION_BUDGET, budget.remaining)
        // El nuevo weekStart debe ser el lunes que contiene `now`, calculado de forma consistente.
        assertEquals(PointsRules.mondayStartOfWeek(now), budget.weekStart)
    }

    @Test
    fun currentAppreciationBudget_exactlyAtWeekBoundary_isExpired() {
        val oldWeekStart = 0L
        val now = 7L * 24 * 60 * 60 * 1000 // == oldWeekStart + WEEK_MILLIS, límite inclusive

        val budget = PointsRules.currentAppreciationBudget(
            appreciationGiven = 40,
            appreciationWeekStart = oldWeekStart,
            now = now
        )

        assertEquals(0, budget.given)
    }

    // ── validateAppreciateBasic / validateAppreciateLimit ───────

    @Test
    fun validateAppreciateBasic_self_isRejected() {
        assertEquals(
            PointsRules.AppreciateError.SELF,
            PointsRules.validateAppreciateBasic("m1", "m1", 10)
        )
    }

    @Test
    fun validateAppreciateBasic_zeroOrNegativeAmount_isRejected() {
        assertEquals(
            PointsRules.AppreciateError.INVALID_AMOUNT,
            PointsRules.validateAppreciateBasic("m1", "m2", 0)
        )
        assertEquals(
            PointsRules.AppreciateError.INVALID_AMOUNT,
            PointsRules.validateAppreciateBasic("m1", "m2", -5)
        )
    }

    @Test
    fun validateAppreciateBasic_validInput_isNull() {
        assertNull(PointsRules.validateAppreciateBasic("m1", "m2", 10))
    }

    @Test
    fun validateAppreciateLimit_amountAboveRemaining_isRejected() {
        val budget = PointsRules.AppreciationBudget(given = 45, weekStart = 0L, remaining = 5)
        assertEquals(
            PointsRules.AppreciateError.LIMIT_EXCEEDED,
            PointsRules.validateAppreciateLimit(6, budget)
        )
    }

    @Test
    fun validateAppreciateLimit_amountEqualToRemaining_isAllowed() {
        val budget = PointsRules.AppreciationBudget(given = 45, weekStart = 0L, remaining = 5)
        assertNull(PointsRules.validateAppreciateLimit(5, budget))
    }

    // ── validateDonateBasic / validateDonateBalance ─────────────

    @Test
    fun validateDonateBasic_self_isRejected() {
        assertEquals(
            PointsRules.DonateError.SELF,
            PointsRules.validateDonateBasic("m1", "m1", 10)
        )
    }

    @Test
    fun validateDonateBasic_zeroOrNegativeAmount_isRejected() {
        assertEquals(
            PointsRules.DonateError.INVALID_AMOUNT,
            PointsRules.validateDonateBasic("m1", "m2", 0)
        )
    }

    @Test
    fun validateDonateBalance_insufficientBalance_isRejected() {
        assertEquals(
            PointsRules.DonateError.INSUFFICIENT_BALANCE,
            PointsRules.validateDonateBalance(amount = 100, fromBalance = 50)
        )
    }

    @Test
    fun validateDonateBalance_exactBalance_isAllowed() {
        assertNull(PointsRules.validateDonateBalance(amount = 50, fromBalance = 50))
    }

    @Test
    fun validateDonateBalance_validTransfer_isAllowed() {
        assertNull(PointsRules.validateDonateBasic("donor", "receptor", 20))
        assertNull(PointsRules.validateDonateBalance(amount = 20, fromBalance = 50))
    }
}
