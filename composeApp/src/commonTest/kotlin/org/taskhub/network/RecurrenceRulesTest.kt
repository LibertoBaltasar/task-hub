package org.taskhub.network

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurrenceRulesTest {

    private val tz = TimeZone.currentSystemDefault()

    private fun epochOf(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute, 0).toInstant(tz).toEpochMilliseconds()

    private fun dateOf(epochMs: Long): LocalDate =
        kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date

    // ── clampDayOfMonth ──────────────────────────────────────────

    @Test
    fun clampDayOfMonth_dayFitsInMonth_isUnchanged() {
        assertEquals(15, RecurrenceRules.clampDayOfMonth(15, 2024, 1))
    }

    @Test
    fun clampDayOfMonth_day31InApril_clampsTo30() {
        // Abril tiene 30 días.
        assertEquals(30, RecurrenceRules.clampDayOfMonth(31, 2024, 4))
    }

    @Test
    fun clampDayOfMonth_day31InFebruaryLeapYear_clampsTo29() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(31, 2024, 2)) // 2024 es bisiesto
    }

    @Test
    fun clampDayOfMonth_day31InFebruaryNonLeapYear_clampsTo28() {
        assertEquals(28, RecurrenceRules.clampDayOfMonth(31, 2023, 2))
    }

    // ── nextOccurrence: daily ────────────────────────────────────

    @Test
    fun nextOccurrence_daily_isTomorrow() {
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "daily")
        assertEquals(LocalDate(2024, 3, 16), dateOf(next))
    }

    // ── nextOccurrence: weekly ───────────────────────────────────

    @Test
    fun nextOccurrence_weekly_todayIsTargetDay_jumpsForwardSevenDays() {
        // 2024-03-15 es viernes (dow=5)
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", day = 5)
        assertEquals(LocalDate(2024, 3, 22), dateOf(next))
    }

    @Test
    fun nextOccurrence_weekly_targetDayLaterThisWeek_usesThatDay() {
        // 2024-03-15 es viernes (dow=5); pedir lunes (dow=1) siguiente
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", day = 1)
        assertEquals(LocalDate(2024, 3, 18), dateOf(next))
    }

    @Test
    fun nextOccurrence_weekly_targetDayEarlierInWeek_wrapsToNextWeek() {
        // 2024-03-18 es lunes (dow=1); pedir domingo (dow=7) -> el domingo que viene
        val now = epochOf(2024, 3, 18)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", day = 7)
        assertEquals(LocalDate(2024, 3, 24), dateOf(next))
    }

    @Test
    fun nextOccurrence_weekly_multipleDays_picksEarliestUpcoming() {
        // 2024-03-15 es viernes (dow=5); días pedidos lunes(1)+miércoles(3) -> el lunes que viene
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", weeklyDays = listOf(1, 3))
        assertEquals(LocalDate(2024, 3, 18), dateOf(next))
    }

    @Test
    fun nextOccurrence_weekly_multipleDays_todayIsOneOfThem_skipsToNextMatch() {
        // 2024-03-15 es viernes (dow=5); días pedidos lunes(1)+viernes(5) -> el lunes (no hoy)
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", weeklyDays = listOf(1, 5))
        assertEquals(LocalDate(2024, 3, 18), dateOf(next))
    }

    @Test
    fun nextOccurrence_weekly_emptyDays_jumpsForwardSevenDays() {
        val now = epochOf(2024, 3, 15) // viernes
        val next = RecurrenceRules.nextOccurrence(now, "weekly", weeklyDays = emptyList())
        assertEquals(LocalDate(2024, 3, 22), dateOf(next))
    }

    // ── nextOccurrence: monthly ──────────────────────────────────

    @Test
    fun nextOccurrence_monthly_dayLaterThisMonth_staysInSameMonth() {
        val now = epochOf(2024, 3, 10)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 20)
        assertEquals(LocalDate(2024, 3, 20), dateOf(next))
    }

    @Test
    fun nextOccurrence_monthly_dayAlreadyPassed_jumpsToNextMonth() {
        val now = epochOf(2024, 3, 20)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 10)
        assertEquals(LocalDate(2024, 4, 10), dateOf(next))
    }

    @Test
    fun nextOccurrence_monthly_dayIsToday_jumpsToNextMonth() {
        val now = epochOf(2024, 3, 20)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 20)
        assertEquals(LocalDate(2024, 4, 20), dateOf(next))
    }

    @Test
    fun nextOccurrence_monthly_shortMonth_clampsToLastDay() {
        // Pedir día 31 en febrero (2024, bisiesto) -> 29 de febrero
        val now = epochOf(2024, 2, 1)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 31)
        assertEquals(LocalDate(2024, 2, 29), dateOf(next))
    }

    @Test
    fun nextOccurrence_monthly_dayPassedInShortMonth_jumpsToNextMonthClamped() {
        // Ya pasó el (clamp de) día 31 en abril (30 días) -> siguiente ocurrencia en mayo, día 31 real
        val now = epochOf(2024, 4, 30)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 31)
        assertEquals(LocalDate(2024, 5, 31), dateOf(next))
    }

    // ── isDueToday: daily ────────────────────────────────────────

    @Test
    fun isDueToday_daily_neverCompleted_isDue() {
        val now = epochOf(2024, 3, 15)
        assertTrue(RecurrenceRules.isDueToday("daily", emptyList(), null, null, now, tz))
    }

    @Test
    fun isDueToday_daily_completedToday_isNotDue() {
        val now = epochOf(2024, 3, 15, hour = 18)
        val completedEarlierToday = epochOf(2024, 3, 15, hour = 8)
        assertFalse(RecurrenceRules.isDueToday("daily", emptyList(), null, completedEarlierToday, now, tz))
    }

    @Test
    fun isDueToday_daily_completedYesterday_isDue() {
        val now = epochOf(2024, 3, 15)
        val completedYesterday = epochOf(2024, 3, 14)
        assertTrue(RecurrenceRules.isDueToday("daily", emptyList(), null, completedYesterday, now, tz))
    }

    // ── isDueToday: weekly ───────────────────────────────────────

    @Test
    fun isDueToday_weekly_todayNotInRecurrenceDays_isNotDue() {
        // 2024-03-15 es viernes (dow=5); solo aplica lunes (1)
        val now = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1), null, null, now, tz))
    }

    @Test
    fun isDueToday_weekly_todayInRecurrenceDaysAndNotCompleted_isDue() {
        val now = epochOf(2024, 3, 15) // viernes
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(5), null, null, now, tz))
    }

    // ── isDueToday: monthly (con recurrenceDay) ───────────────────

    @Test
    fun isDueToday_monthlyWithDay_notTargetDay_isNotDue() {
        val now = epochOf(2024, 3, 10)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 20, null, now, tz))
    }

    @Test
    fun isDueToday_monthlyWithDay_isTargetDayAndNotCompleted_isDue() {
        val now = epochOf(2024, 3, 20)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 20, null, now, tz))
    }

    @Test
    fun isDueToday_monthlyWithDay_shortMonth_matchesClampedLastDay() {
        // Día pedido 31 en abril (30 días) -> toca el 30
        val now = epochOf(2024, 4, 30)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 31, null, now, tz))
    }

    // ── isDueToday: monthly (legado, sin recurrenceDay) ────────────

    @Test
    fun isDueToday_monthlyLegacy_notCompletedThisMonth_isDue() {
        val now = epochOf(2024, 3, 15)
        val completedLastMonth = epochOf(2024, 2, 10)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), null, completedLastMonth, now, tz))
    }

    @Test
    fun isDueToday_monthlyLegacy_completedThisMonth_isNotDue() {
        val now = epochOf(2024, 3, 15)
        val completedEarlierThisMonth = epochOf(2024, 3, 2)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), null, completedEarlierThisMonth, now, tz))
    }

    // ── isDueToday: once ─────────────────────────────────────────

    @Test
    fun isDueToday_once_neverCompleted_isDue() {
        assertTrue(RecurrenceRules.isDueToday("once", emptyList(), null, null, epochOf(2024, 3, 15), tz))
    }

    @Test
    fun isDueToday_once_completed_isNotDue() {
        assertFalse(
            RecurrenceRules.isDueToday(
                "once", emptyList(), null,
                lastCompletedDate = epochOf(2024, 1, 1),
                nowEpochMs = epochOf(2024, 3, 15),
                tz = tz
            )
        )
    }
}
