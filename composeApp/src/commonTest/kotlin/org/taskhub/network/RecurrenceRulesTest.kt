package org.taskhub.network

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.models.AssignmentSlot
import org.taskhub.network.models.TaskAssignmentResponse
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

    @Test
    fun clampDayOfMonth_day29InFebruaryLeapYear_isUnchanged() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(29, 2024, 2)) // 2024 es bisiesto
    }

    @Test
    fun clampDayOfMonth_day29InFebruaryNonLeapYear_clampsTo28() {
        assertEquals(28, RecurrenceRules.clampDayOfMonth(29, 2023, 2))
    }

    @Test
    fun clampDayOfMonth_day30InFebruary_clampsToLastDayOfMonth() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(30, 2024, 2)) // bisiesto
        assertEquals(28, RecurrenceRules.clampDayOfMonth(30, 2023, 2)) // no bisiesto
    }

    @Test
    fun clampDayOfMonth_day30InThirtyDayMonth_isUnchanged() {
        assertEquals(30, RecurrenceRules.clampDayOfMonth(30, 2024, 4)) // abril tiene 30 días
    }

    @Test
    fun clampDayOfMonth_day29InThirtyOneDayMonth_isUnchanged() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(29, 2024, 1)) // enero tiene 31 días
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

    // ── isDueToday: weekly — VARIOS recurrenceDays a la vez (lunes+miércoles+viernes) ──

    @Test
    fun isDueToday_weeklyMultipleDays_todayIsOneOfThem_neverCompleted_isDue() {
        // Lunes(1)+miércoles(3)+viernes(5); 2024-03-13 es miércoles.
        val wednesday = epochOf(2024, 3, 13)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, null, wednesday, tz))
    }

    @Test
    fun isDueToday_weeklyMultipleDays_todayIsNotAnyOfThem_neverCompleted_isNotDue() {
        // Martes(2) no está en lunes+miércoles+viernes.
        val tuesday = epochOf(2024, 3, 12)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, null, tuesday, tz))
    }

    @Test
    fun isDueToday_weeklyMultipleDays_completedOnMonday_stillDueOnWednesday() {
        // Completar el lunes de esta semana no cubre la ocurrencia del
        // miércoles: cada día marcado tiene su propia ocurrencia semanal.
        val completedMonday = epochOf(2024, 3, 11)
        val wednesday = epochOf(2024, 3, 13)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedMonday, wednesday, tz))
    }

    @Test
    fun isDueToday_weeklyMultipleDays_completedOnWednesday_notDueAgainOnThursday() {
        // Jueves no está en la lista -> nunca toca, independientemente de compleciones.
        val completedWednesday = epochOf(2024, 3, 13)
        val thursday = epochOf(2024, 3, 14)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedWednesday, thursday, tz))
    }

    @Test
    fun isDueToday_weeklyMultipleDays_completedOnWednesday_isDueAgainOnFriday() {
        // El miércoles ya se completó, pero el viernes es una ocurrencia
        // distinta de la misma semana -> vuelve a tocar.
        val completedWednesday = epochOf(2024, 3, 13)
        val friday = epochOf(2024, 3, 15)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedWednesday, friday, tz))
    }

    @Test
    fun isDueToday_weeklyMultipleDays_completedOnFriday_notDueUntilNextMonday() {
        val completedFriday = epochOf(2024, 3, 15)
        val nextMonday = epochOf(2024, 3, 18)
        // El lunes siguiente es una ocurrencia nueva -> vuelve a tocar.
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedFriday, nextMonday, tz))
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

    // ── isDueToday: weekly — completado tardío (catch-up) ─────────

    @Test
    fun isDueToday_weekly_missedScheduledDay_withPriorCompletion_isDueLate() {
        // Lunes(1) programado; hoy viernes 2024-03-15, la última vez se
        // completó hace 2 semanas (2024-02-26, otro lunes) — el lunes de
        // esta semana (2024-03-11) se pasó sin marcar: debe seguir pendiente
        // (completado tardío), no desaparecer de la lista.
        val now = epochOf(2024, 3, 15)
        val lastCompletedTwoWeeksAgo = epochOf(2024, 2, 26)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1), null, lastCompletedTwoWeeksAgo, now, tz))
    }

    @Test
    fun isDueToday_weekly_completedOnThisWeeksScheduledDay_isNotDue() {
        // Ya se completó el lunes de esta semana (2024-03-11): no debe
        // volver a pedir completar hasta el próximo lunes.
        val now = epochOf(2024, 3, 15)
        val completedThisMonday = epochOf(2024, 3, 11)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1), null, completedThisMonday, now, tz))
    }

    @Test
    fun isDueToday_weekly_neverCompletedAndDayAlreadyPassed_staysNotDueUntilNextCycle() {
        // Nunca se completó: la ventana de "completado tardío" no se abre
        // (no hay ocurrencia previa real que se haya "perdido"), se mantiene
        // el comportamiento exacto de antes.
        val now = epochOf(2024, 3, 15) // viernes; lunes ya pasó
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1), null, null, now, tz))
    }

    // ── isDueToday: monthly con día fijo — completado tardío ───────

    @Test
    fun isDueToday_monthlyWithDay_missedScheduledDay_withPriorCompletion_isDueLate() {
        // Día 15 programado; se completó el mes anterior (2024-02-15) pero
        // no este mes, y ya estamos a día 20 (pasado el 15): sigue pendiente.
        val now = epochOf(2024, 3, 20)
        val completedLastMonth = epochOf(2024, 2, 15)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 15, completedLastMonth, now, tz))
    }

    @Test
    fun isDueToday_monthlyWithDay_completedThisMonthsScheduledDay_isNotDue() {
        val now = epochOf(2024, 3, 20)
        val completedThisMonth = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 15, completedThisMonth, now, tz))
    }

    @Test
    fun isDueToday_monthlyWithDay_beforeThisMonthsTarget_alreadyCaughtUpLastCycle_isNotDue() {
        // Antes del día 15 de este mes; el ciclo anterior (2024-02-15) ya se
        // completó a tiempo, así que todavía no toca nada.
        val now = epochOf(2024, 3, 10)
        val completedLastMonth = epochOf(2024, 2, 15)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 15, completedLastMonth, now, tz))
    }

    // ── isOverdueOccurrence ─────────────────────────────────────────

    @Test
    fun isOverdueOccurrence_weekly_todayNotScheduledDay_isOverdue() {
        val now = epochOf(2024, 3, 15) // viernes; solo lunes(1) programado
        assertTrue(RecurrenceRules.isOverdueOccurrence("weekly", listOf(1), null, now, tz))
    }

    @Test
    fun isOverdueOccurrence_weekly_todayIsScheduledDay_isNotOverdue() {
        val now = epochOf(2024, 3, 15) // viernes(5) programado
        assertFalse(RecurrenceRules.isOverdueOccurrence("weekly", listOf(5), null, now, tz))
    }

    @Test
    fun isOverdueOccurrence_weekly_emptyDays_isNeverOverdue() {
        val now = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isOverdueOccurrence("weekly", emptyList(), null, now, tz))
    }

    @Test
    fun isOverdueOccurrence_monthlyWithDay_notTargetDay_isOverdue() {
        val now = epochOf(2024, 3, 20)
        assertTrue(RecurrenceRules.isOverdueOccurrence("monthly", emptyList(), 15, now, tz))
    }

    @Test
    fun isOverdueOccurrence_monthlyWithDay_isTargetDay_isNotOverdue() {
        val now = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isOverdueOccurrence("monthly", emptyList(), 15, now, tz))
    }

    @Test
    fun isOverdueOccurrence_monthlyLegacyNoDay_isNeverOverdue() {
        val now = epochOf(2024, 3, 20)
        assertFalse(RecurrenceRules.isOverdueOccurrence("monthly", emptyList(), null, now, tz))
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

    // ── isDueOn: fecha arbitraria (usada por CalendarScreen) ──────

    @Test
    fun isDueOn_daily_sameAsIsDueToday_whenDateIsToday() {
        // isDueToday debe seguir siendo un caso particular de isDueOn con date=hoy.
        val now = epochOf(2024, 3, 15)
        val today = dateOf(now)
        val completedYesterday = epochOf(2024, 3, 14)
        assertEquals(
            RecurrenceRules.isDueToday("daily", emptyList(), null, completedYesterday, now, tz),
            RecurrenceRules.isDueOn(today, "daily", emptyList(), null, completedYesterday, tz)
        )
    }

    @Test
    fun isDueOn_daily_pastDateBeforeLastCompletion_isNotDue() {
        // CalendarScreen puede consultar una fecha PASADA anterior a la última
        // compleción (navegando a un mes anterior tras completar la tarea más
        // tarde) — no debe marcarse como pendiente retroactivamente.
        val lastCompleted = epochOf(2024, 3, 20)
        val pastDate = dateOf(epochOf(2024, 3, 10))
        assertFalse(RecurrenceRules.isDueOn(pastDate, "daily", emptyList(), null, lastCompleted, tz))
    }

    @Test
    fun isDueOn_daily_dateAfterLastCompletion_isDue() {
        val lastCompleted = epochOf(2024, 3, 10)
        val futureDate = dateOf(epochOf(2024, 3, 20))
        assertTrue(RecurrenceRules.isDueOn(futureDate, "daily", emptyList(), null, lastCompleted, tz))
    }

    @Test
    fun isDueOn_daily_exactCompletionDate_isNotDue() {
        val lastCompleted = epochOf(2024, 3, 15)
        val sameDate = dateOf(lastCompleted)
        assertFalse(RecurrenceRules.isDueOn(sameDate, "daily", emptyList(), null, lastCompleted, tz))
    }

    @Test
    fun isDueOn_monthlyLegacy_pastDateBeforeLastCompletion_isNotDue() {
        val lastCompleted = epochOf(2024, 3, 20)
        val pastDate = dateOf(epochOf(2024, 1, 10))
        assertFalse(RecurrenceRules.isDueOn(pastDate, "monthly", emptyList(), null, lastCompleted, tz))
    }

    @Test
    fun isDueOn_weekly_withRecurrenceDays_matchesScheduledDayInThePast() {
        // Lunes(1) programado, nunca completada: cualquier lunes pasado debe tocar.
        val pastMonday = dateOf(epochOf(2024, 2, 26)) // lunes
        assertTrue(RecurrenceRules.isDueOn(pastMonday, "weekly", listOf(1), null, null, tz))
    }

    @Test
    fun isDueOn_monthlyWithDay_matchesTargetDayInAFutureMonth() {
        val targetDate = dateOf(epochOf(2024, 6, 15))
        assertTrue(RecurrenceRules.isDueOn(targetDate, "monthly", emptyList(), 15, null, tz))
    }

    // ── endOfDueDay ──────────────────────────────────────────────

    @Test
    fun endOfDueDay_isMidnightOfTheFollowingDay() {
        val dueDayStart = LocalDateTime(2024, 3, 18, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2024, 3, 19), dateOf(end))
    }

    @Test
    fun endOfDueDay_completionLaterTheSameScheduledDay_isBeforeEnd() {
        // Regresión del bug: comparar `now <= dueDate` contra la medianoche de
        // INICIO del día programado marcaría como "tarde" cualquier compleción
        // real (que siempre ocurre después de las 00:00 de ese día).
        val dueDayStart = LocalDateTime(2024, 3, 18, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val completedSameDayEvening = epochOf(2024, 3, 18, hour = 20)
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertTrue(completedSameDayEvening <= end)
    }

    @Test
    fun endOfDueDay_completionNextDay_isAfterEnd() {
        val dueDayStart = LocalDateTime(2024, 3, 18, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val completedNextDay = epochOf(2024, 3, 19, hour = 1)
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertTrue(completedNextDay > end)
    }

    @Test
    fun endOfDueDay_lastDayOfMonth_crossesIntoNextMonth() {
        val dueDayStart = LocalDateTime(2024, 3, 31, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2024, 4, 1), dateOf(end))
    }

    @Test
    fun endOfDueDay_lastDayOfFebruaryLeapYear_crossesIntoMarch() {
        val dueDayStart = LocalDateTime(2024, 2, 29, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2024, 3, 1), dateOf(end))
    }

    @Test
    fun endOfDueDay_lastDayOfYear_crossesIntoNextYear() {
        val dueDayStart = LocalDateTime(2024, 12, 31, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2025, 1, 1), dateOf(end))
    }

    // ── resolveRotationAssignee ────────────────────────────────────

    @Test
    fun resolveRotationAssignee_emptyRotation_returnsFallback() {
        val nextDue = epochOf(2024, 3, 18) // lunes
        assertEquals(
            "member-A",
            RecurrenceRules.resolveRotationAssignee(emptyList(), nextDue, "member-A", tz)
        )
    }

    @Test
    fun resolveRotationAssignee_matchingSlotForDayOfWeek_returnsThatMember() {
        // 2024-03-18 es lunes (dow=1)
        val nextDue = epochOf(2024, 3, 18)
        val rotation = listOf(
            AssignmentSlot(dayOfWeek = 1, memberId = "member-monday"),
            AssignmentSlot(dayOfWeek = 3, memberId = "member-wednesday")
        )
        assertEquals(
            "member-monday",
            RecurrenceRules.resolveRotationAssignee(rotation, nextDue, "member-A", tz)
        )
    }

    @Test
    fun resolveRotationAssignee_noSlotForDayOfWeek_returnsFallback() {
        // 2024-03-15 es viernes (dow=5); rotación solo cubre lunes(1)
        val nextDue = epochOf(2024, 3, 15)
        val rotation = listOf(AssignmentSlot(dayOfWeek = 1, memberId = "member-monday"))
        assertEquals(
            "member-A",
            RecurrenceRules.resolveRotationAssignee(rotation, nextDue, "member-A", tz)
        )
    }

    // ── purgeMemberFromRotation ───────────────────────────────────

    @Test
    fun purgeMemberFromRotation_removesOnlyMatchingSlots() {
        val rotation = listOf(
            AssignmentSlot(dayOfWeek = 1, memberId = "member-A"),
            AssignmentSlot(dayOfWeek = 3, memberId = "member-B"),
            AssignmentSlot(dayOfWeek = 5, memberId = "member-A")
        )
        val purged = RecurrenceRules.purgeMemberFromRotation(rotation, "member-A")
        assertEquals(listOf(AssignmentSlot(dayOfWeek = 3, memberId = "member-B")), purged)
    }

    @Test
    fun purgeMemberFromRotation_memberNotInRotation_returnsUnchanged() {
        val rotation = listOf(AssignmentSlot(dayOfWeek = 1, memberId = "member-B"))
        assertEquals(rotation, RecurrenceRules.purgeMemberFromRotation(rotation, "member-A"))
    }

    @Test
    fun purgeMemberFromRotation_emptyRotation_returnsEmpty() {
        assertTrue(RecurrenceRules.purgeMemberFromRotation(emptyList(), "member-A").isEmpty())
    }

    // ── Timezone explícita distinta de currentSystemDefault() ─────
    //
    // Todos los tests de arriba usan `tz` = currentSystemDefault() de la
    // máquina que ejecuta el test — nunca demuestran que el parámetro [tz]
    // realmente se respeta. Aquí se usa un mismo instante UTC con dos zonas
    // de offset muy distinto (Kiritimati UTC+14 / Pago Pago UTC-11, 25h de
    // diferencia) para comprobar que el resultado depende de [tz] y no de la
    // zona del sistema (panel v4, Experto 13, hueco #5).

    private val tzFarEast = TimeZone.of("Pacific/Kiritimati") // UTC+14
    private val tzFarWest = TimeZone.of("Pacific/Pago_Pago") // UTC-11

    @Test
    fun isDueToday_explicitTz_sameInstantFallsOnDifferentLocalWeekday() {
        // 2024-03-14T23:00:00Z -> viernes 15 en Kiritimati, jueves 14 en Pago Pago.
        val instant = LocalDateTime(2024, 3, 14, 23, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(5), null, null, instant, tzFarEast)) // viernes
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(5), null, null, instant, tzFarWest)) // jueves, no viernes
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(4), null, null, instant, tzFarWest)) // jueves
    }

    @Test
    fun endOfDueDay_explicitNonDefaultTz_usesThatZonesMidnight() {
        val dueDayStartInFarEast = LocalDateTime(2024, 3, 15, 0, 0, 0).toInstant(tzFarEast).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStartInFarEast, tzFarEast)

        val endLocalDate = kotlinx.datetime.Instant.fromEpochMilliseconds(end).toLocalDateTime(tzFarEast).date
        assertEquals(LocalDate(2024, 3, 16), endLocalDate)
    }

    @Test
    fun resolveRotationAssignee_explicitTz_sameInstantResolvesDifferentSlot() {
        // Mismo instante que arriba: viernes(5) en Kiritimati, jueves(4) en Pago Pago.
        val instant = LocalDateTime(2024, 3, 14, 23, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
        val rotation = listOf(
            AssignmentSlot(dayOfWeek = 4, memberId = "member-thursday"),
            AssignmentSlot(dayOfWeek = 5, memberId = "member-friday")
        )

        assertEquals(
            "member-friday",
            RecurrenceRules.resolveRotationAssignee(rotation, instant, "fallback", tzFarEast)
        )
        assertEquals(
            "member-thursday",
            RecurrenceRules.resolveRotationAssignee(rotation, instant, "fallback", tzFarWest)
        )
    }

    // ── resolveNextAssignmentDecision ──────────────────────────────

    private fun assignment(
        memberId: String,
        dueDate: Long,
        status: String = "assigned"
    ) = TaskAssignmentResponse(id = "assignment-1", taskId = "task-1", memberId = memberId, dueDate = dueDate, status = status)

    @Test
    fun resolveNextAssignmentDecision_emptyRotation_usesFallbackAndCreates() {
        val decision = RecurrenceRules.resolveNextAssignmentDecision(
            assignmentRotation = emptyList(),
            nextDueDate = 1_000L,
            completedMemberId = "member-A",
            existingAssignments = emptyList(),
            tz = tz
        )
        assertTrue(decision.shouldCreate)
        assertEquals("member-A", decision.memberId)
    }

    @Test
    fun resolveNextAssignmentDecision_matchingAssignedDuplicate_doesNotCreate() {
        val nextDue = 1_000L
        val decision = RecurrenceRules.resolveNextAssignmentDecision(
            assignmentRotation = emptyList(),
            nextDueDate = nextDue,
            completedMemberId = "member-A",
            existingAssignments = listOf(assignment("member-A", nextDue, status = "assigned")),
            tz = tz
        )
        assertFalse(decision.shouldCreate)
        assertEquals("member-A", decision.memberId)
    }

    @Test
    fun resolveNextAssignmentDecision_existingAssignmentWithDifferentDueDate_stillCreates() {
        val decision = RecurrenceRules.resolveNextAssignmentDecision(
            assignmentRotation = emptyList(),
            nextDueDate = 2_000L,
            completedMemberId = "member-A",
            existingAssignments = listOf(assignment("member-A", 1_000L, status = "assigned")),
            tz = tz
        )
        assertTrue(decision.shouldCreate)
    }

    @Test
    fun resolveNextAssignmentDecision_existingAssignmentAlreadyCompleted_stillCreates() {
        // Solo deduplica contra asignaciones "assigned" — una "completed" para
        // el mismo miembro/fecha no cuenta como ya regenerada.
        val nextDue = 1_000L
        val decision = RecurrenceRules.resolveNextAssignmentDecision(
            assignmentRotation = emptyList(),
            nextDueDate = nextDue,
            completedMemberId = "member-A",
            existingAssignments = listOf(assignment("member-A", nextDue, status = "completed")),
            tz = tz
        )
        assertTrue(decision.shouldCreate)
    }

    @Test
    fun resolveNextAssignmentDecision_rotationResolvesDifferentMember_dedupesAgainstThatMember() {
        // 2024-03-18 es lunes (dow=1) -> la rotación asigna a member-monday,
        // no a quien completó la tarea.
        val nextDue = epochOf(2024, 3, 18)
        val rotation = listOf(AssignmentSlot(dayOfWeek = 1, memberId = "member-monday"))
        val decision = RecurrenceRules.resolveNextAssignmentDecision(
            assignmentRotation = rotation,
            nextDueDate = nextDue,
            completedMemberId = "member-A",
            existingAssignments = listOf(assignment("member-monday", nextDue, status = "assigned")),
            tz = tz
        )
        assertEquals("member-monday", decision.memberId)
        assertFalse(decision.shouldCreate)
    }
}
