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

/**
 * [RecurrenceRules] es el motor de recurrencia de tareas (diaria/semanal/
 * mensual): cuándo "toca" una tarea hoy, cuál es su próxima ocurrencia,
 * cómo se resuelve el turno rotatorio de asignación y el cálculo de fin de
 * día para decidir puntualidad. Incluye casos de "completado tardío"
 * (catch-up) y de zonas horarias explícitas distintas de la del sistema.
 */
class RecurrenceRulesTest {

    private val tz = TimeZone.currentSystemDefault()

    private fun epochOf(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute, 0).toInstant(tz).toEpochMilliseconds()

    private fun dateOf(epochMs: Long): LocalDate =
        kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date

    // ── clampDayOfMonth ──────────────────────────────────────────
    // Un día de recurrencia mensual (p.ej. "día 31") puede no existir en
    // todos los meses; estas pruebas fijan el criterio de recorte.

    /** Un día que cabe en el mes no se modifica. */
    @Test
    fun clampDayOfMonth_dayFitsInMonth_isUnchanged() {
        assertEquals(15, RecurrenceRules.clampDayOfMonth(15, 2024, 1))
    }

    /** El día 31 en un mes de 30 días se recorta al último día real del mes. */
    @Test
    fun clampDayOfMonth_day31InApril_clampsTo30() {
        // Abril tiene 30 días.
        assertEquals(30, RecurrenceRules.clampDayOfMonth(31, 2024, 4))
    }

    /** El día 31 en febrero de un año bisiesto se recorta a 29. */
    @Test
    fun clampDayOfMonth_day31InFebruaryLeapYear_clampsTo29() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(31, 2024, 2)) // 2024 es bisiesto
    }

    /** El día 31 en febrero de un año no bisiesto se recorta a 28. */
    @Test
    fun clampDayOfMonth_day31InFebruaryNonLeapYear_clampsTo28() {
        assertEquals(28, RecurrenceRules.clampDayOfMonth(31, 2023, 2))
    }

    /** El día 29 en febrero de un año bisiesto SÍ existe: no se recorta. */
    @Test
    fun clampDayOfMonth_day29InFebruaryLeapYear_isUnchanged() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(29, 2024, 2)) // 2024 es bisiesto
    }

    /** El día 29 en febrero de un año no bisiesto no existe: se recorta a 28. */
    @Test
    fun clampDayOfMonth_day29InFebruaryNonLeapYear_clampsTo28() {
        assertEquals(28, RecurrenceRules.clampDayOfMonth(29, 2023, 2))
    }

    /** El día 30 tampoco existe en febrero en ningún año: se recorta al último día real, sea 28 o 29. */
    @Test
    fun clampDayOfMonth_day30InFebruary_clampsToLastDayOfMonth() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(30, 2024, 2)) // bisiesto
        assertEquals(28, RecurrenceRules.clampDayOfMonth(30, 2023, 2)) // no bisiesto
    }

    /** El día 30 en un mes de 30 días no se modifica. */
    @Test
    fun clampDayOfMonth_day30InThirtyDayMonth_isUnchanged() {
        assertEquals(30, RecurrenceRules.clampDayOfMonth(30, 2024, 4)) // abril tiene 30 días
    }

    /** El día 29 en un mes de 31 días no se modifica. */
    @Test
    fun clampDayOfMonth_day29InThirtyOneDayMonth_isUnchanged() {
        assertEquals(29, RecurrenceRules.clampDayOfMonth(29, 2024, 1)) // enero tiene 31 días
    }

    // ── nextOccurrence: daily ────────────────────────────────────

    /** Una tarea diaria siempre "vuelve a tocar" al día siguiente del instante consultado. */
    @Test
    fun nextOccurrence_daily_isTomorrow() {
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "daily")
        assertEquals(LocalDate(2024, 3, 16), dateOf(next))
    }

    // ── nextOccurrence: weekly ───────────────────────────────────

    /** Si hoy es el día programado, la siguiente ocurrencia salta a la semana que viene, no a hoy mismo. */
    @Test
    fun nextOccurrence_weekly_todayIsTargetDay_jumpsForwardSevenDays() {
        // 2024-03-15 es viernes (dow=5)
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", day = 5)
        assertEquals(LocalDate(2024, 3, 22), dateOf(next))
    }

    /** Si el día objetivo cae más adelante en esta misma semana, se usa esa fecha (no la semana siguiente). */
    @Test
    fun nextOccurrence_weekly_targetDayLaterThisWeek_usesThatDay() {
        // 2024-03-15 es viernes (dow=5); pedir lunes (dow=1) siguiente
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", day = 1)
        assertEquals(LocalDate(2024, 3, 18), dateOf(next))
    }

    /** Si el día objetivo ya pasó esta semana, la ocurrencia se calcula en la semana siguiente. */
    @Test
    fun nextOccurrence_weekly_targetDayEarlierInWeek_wrapsToNextWeek() {
        // 2024-03-18 es lunes (dow=1); pedir domingo (dow=7) -> el domingo que viene
        val now = epochOf(2024, 3, 18)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", day = 7)
        assertEquals(LocalDate(2024, 3, 24), dateOf(next))
    }

    /** Con varios días programados por semana, se elige el más próximo de todos ellos. */
    @Test
    fun nextOccurrence_weekly_multipleDays_picksEarliestUpcoming() {
        // 2024-03-15 es viernes (dow=5); días pedidos lunes(1)+miércoles(3) -> el lunes que viene
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", weeklyDays = listOf(1, 3))
        assertEquals(LocalDate(2024, 3, 18), dateOf(next))
    }

    /** Si hoy es uno de los días programados, se salta a la siguiente ocurrencia futura, no se repite hoy. */
    @Test
    fun nextOccurrence_weekly_multipleDays_todayIsOneOfThem_skipsToNextMatch() {
        // 2024-03-15 es viernes (dow=5); días pedidos lunes(1)+viernes(5) -> el lunes (no hoy)
        val now = epochOf(2024, 3, 15)
        val next = RecurrenceRules.nextOccurrence(now, "weekly", weeklyDays = listOf(1, 5))
        assertEquals(LocalDate(2024, 3, 18), dateOf(next))
    }

    /** Sin días configurados, se asume el mismo comportamiento que "un solo día por semana": salta 7 días. */
    @Test
    fun nextOccurrence_weekly_emptyDays_jumpsForwardSevenDays() {
        val now = epochOf(2024, 3, 15) // viernes
        val next = RecurrenceRules.nextOccurrence(now, "weekly", weeklyDays = emptyList())
        assertEquals(LocalDate(2024, 3, 22), dateOf(next))
    }

    // ── nextOccurrence: monthly ──────────────────────────────────

    /** Si el día del mes objetivo todavía no ha llegado, la ocurrencia queda en el mismo mes. */
    @Test
    fun nextOccurrence_monthly_dayLaterThisMonth_staysInSameMonth() {
        val now = epochOf(2024, 3, 10)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 20)
        assertEquals(LocalDate(2024, 3, 20), dateOf(next))
    }

    /** Si el día del mes objetivo ya pasó, la ocurrencia salta al mes siguiente. */
    @Test
    fun nextOccurrence_monthly_dayAlreadyPassed_jumpsToNextMonth() {
        val now = epochOf(2024, 3, 20)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 10)
        assertEquals(LocalDate(2024, 4, 10), dateOf(next))
    }

    /** Si hoy es exactamente el día objetivo, la próxima ocurrencia es el mes siguiente, no hoy. */
    @Test
    fun nextOccurrence_monthly_dayIsToday_jumpsToNextMonth() {
        val now = epochOf(2024, 3, 20)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 20)
        assertEquals(LocalDate(2024, 4, 20), dateOf(next))
    }

    /** Al pedir un día que no existe en el mes actual, se aplica el recorte de [RecurrenceRules.clampDayOfMonth]. */
    @Test
    fun nextOccurrence_monthly_shortMonth_clampsToLastDay() {
        // Pedir día 31 en febrero (2024, bisiesto) -> 29 de febrero
        val now = epochOf(2024, 2, 1)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 31)
        assertEquals(LocalDate(2024, 2, 29), dateOf(next))
    }

    /** Al saltar de mes, el recorte de día se recalcula para el mes de destino (no arrastra el recorte del mes anterior). */
    @Test
    fun nextOccurrence_monthly_dayPassedInShortMonth_jumpsToNextMonthClamped() {
        // Ya pasó el (clamp de) día 31 en abril (30 días) -> siguiente ocurrencia en mayo, día 31 real
        val now = epochOf(2024, 4, 30)
        val next = RecurrenceRules.nextOccurrence(now, "monthly", day = 31)
        assertEquals(LocalDate(2024, 5, 31), dateOf(next))
    }

    // ── isDueToday: daily ────────────────────────────────────────

    /** Una tarea diaria nunca completada siempre está pendiente hoy. */
    @Test
    fun isDueToday_daily_neverCompleted_isDue() {
        val now = epochOf(2024, 3, 15)
        assertTrue(RecurrenceRules.isDueToday("daily", emptyList(), null, null, now, tz))
    }

    /** Una tarea diaria ya completada hoy no vuelve a estar pendiente hasta mañana. */
    @Test
    fun isDueToday_daily_completedToday_isNotDue() {
        val now = epochOf(2024, 3, 15, hour = 18)
        val completedEarlierToday = epochOf(2024, 3, 15, hour = 8)
        assertFalse(RecurrenceRules.isDueToday("daily", emptyList(), null, completedEarlierToday, now, tz))
    }

    /** Completar una tarea diaria ayer no cubre la ocurrencia de hoy: vuelve a estar pendiente. */
    @Test
    fun isDueToday_daily_completedYesterday_isDue() {
        val now = epochOf(2024, 3, 15)
        val completedYesterday = epochOf(2024, 3, 14)
        assertTrue(RecurrenceRules.isDueToday("daily", emptyList(), null, completedYesterday, now, tz))
    }

    // ── isDueToday: weekly ───────────────────────────────────────

    /** Un día de la semana que no está en la lista de recurrencia nunca está pendiente. */
    @Test
    fun isDueToday_weekly_todayNotInRecurrenceDays_isNotDue() {
        // 2024-03-15 es viernes (dow=5); solo aplica lunes (1)
        val now = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1), null, null, now, tz))
    }

    /** Si hoy es un día programado y nunca se completó, está pendiente. */
    @Test
    fun isDueToday_weekly_todayInRecurrenceDaysAndNotCompleted_isDue() {
        val now = epochOf(2024, 3, 15) // viernes
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(5), null, null, now, tz))
    }

    // ── isDueToday: weekly — VARIOS recurrenceDays a la vez (lunes+miércoles+viernes) ──

    /** Con varios días programados, si hoy es uno de ellos y nunca se completó, está pendiente. */
    @Test
    fun isDueToday_weeklyMultipleDays_todayIsOneOfThem_neverCompleted_isDue() {
        // Lunes(1)+miércoles(3)+viernes(5); 2024-03-13 es miércoles.
        val wednesday = epochOf(2024, 3, 13)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, null, wednesday, tz))
    }

    /** Un día no incluido en la lista de recurrencia nunca está pendiente, aunque nunca se haya completado. */
    @Test
    fun isDueToday_weeklyMultipleDays_todayIsNotAnyOfThem_neverCompleted_isNotDue() {
        // Martes(2) no está en lunes+miércoles+viernes.
        val tuesday = epochOf(2024, 3, 12)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, null, tuesday, tz))
    }

    /** Completar el lunes de esta semana no cubre la ocurrencia del miércoles: cada día marcado es una ocurrencia independiente. */
    @Test
    fun isDueToday_weeklyMultipleDays_completedOnMonday_stillDueOnWednesday() {
        val completedMonday = epochOf(2024, 3, 11)
        val wednesday = epochOf(2024, 3, 13)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedMonday, wednesday, tz))
    }

    /** El jueves no está en la lista de días programados -> nunca toca, independientemente de compleciones previas. */
    @Test
    fun isDueToday_weeklyMultipleDays_completedOnWednesday_notDueAgainOnThursday() {
        val completedWednesday = epochOf(2024, 3, 13)
        val thursday = epochOf(2024, 3, 14)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedWednesday, thursday, tz))
    }

    /** El miércoles ya completado no bloquea el viernes: es una ocurrencia distinta de la misma semana. */
    @Test
    fun isDueToday_weeklyMultipleDays_completedOnWednesday_isDueAgainOnFriday() {
        val completedWednesday = epochOf(2024, 3, 13)
        val friday = epochOf(2024, 3, 15)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedWednesday, friday, tz))
    }

    /** El lunes de la semana siguiente es una ocurrencia nueva, independiente del viernes ya completado. */
    @Test
    fun isDueToday_weeklyMultipleDays_completedOnFriday_notDueUntilNextMonday() {
        val completedFriday = epochOf(2024, 3, 15)
        val nextMonday = epochOf(2024, 3, 18)
        // El lunes siguiente es una ocurrencia nueva -> vuelve a tocar.
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1, 3, 5), null, completedFriday, nextMonday, tz))
    }

    // ── isDueToday: monthly (con recurrenceDay) ───────────────────

    /** Con un día fijo del mes configurado, cualquier otro día del mes no está pendiente. */
    @Test
    fun isDueToday_monthlyWithDay_notTargetDay_isNotDue() {
        val now = epochOf(2024, 3, 10)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 20, null, now, tz))
    }

    /** El día del mes objetivo, sin compleción previa, está pendiente. */
    @Test
    fun isDueToday_monthlyWithDay_isTargetDayAndNotCompleted_isDue() {
        val now = epochOf(2024, 3, 20)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 20, null, now, tz))
    }

    /** En un mes corto, el día 31 pedido se recorta al último día real del mes para decidir si toca hoy. */
    @Test
    fun isDueToday_monthlyWithDay_shortMonth_matchesClampedLastDay() {
        // Día pedido 31 en abril (30 días) -> toca el 30
        val now = epochOf(2024, 4, 30)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 31, null, now, tz))
    }

    // ── isDueToday: monthly (legado, sin recurrenceDay) ────────────

    /** Sin día fijo configurado (esquema legado), basta con no haberla completado ya este mes para que esté pendiente. */
    @Test
    fun isDueToday_monthlyLegacy_notCompletedThisMonth_isDue() {
        val now = epochOf(2024, 3, 15)
        val completedLastMonth = epochOf(2024, 2, 10)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), null, completedLastMonth, now, tz))
    }

    /** En modo legado, ya haberla completado este mes basta para que no esté pendiente. */
    @Test
    fun isDueToday_monthlyLegacy_completedThisMonth_isNotDue() {
        val now = epochOf(2024, 3, 15)
        val completedEarlierThisMonth = epochOf(2024, 3, 2)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), null, completedEarlierThisMonth, now, tz))
    }

    // ── isDueToday: weekly — completado tardío (catch-up) ─────────

    /**
     * Si el día programado de esta semana ya pasó sin marcarse y la última
     * compleción fue en un ciclo anterior, la tarea debe seguir pendiente
     * ("completado tardío"), no desaparecer de la lista.
     */
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

    /** Ya haberla completado el día programado de esta semana la deja resuelta hasta el próximo ciclo. */
    @Test
    fun isDueToday_weekly_completedOnThisWeeksScheduledDay_isNotDue() {
        // Ya se completó el lunes de esta semana (2024-03-11): no debe
        // volver a pedir completar hasta el próximo lunes.
        val now = epochOf(2024, 3, 15)
        val completedThisMonday = epochOf(2024, 3, 11)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1), null, completedThisMonday, now, tz))
    }

    /**
     * CAMBIO DE COMPORTAMIENTO (2026-09-12): una tarea nunca completada
     * también abre la ventana de "atrasada" una vez pasa su día programado,
     * igual que la rama ya-completada — ancla en [createdAt] en vez de en
     * `lastCompletedDate`. Antes esta tarea desaparecía el martes y volvía a
     * aparecer el lunes siguiente sin rastro del día perdido (test previo:
     * `isDueToday_weekly_neverCompletedAndDayAlreadyPassed_staysNotDueUntilNextCycle`,
     * ahora sustituido por este + el caso legado de abajo).
     */
    @Test
    fun isDueToday_weekly_neverCompletedAndDayAlreadyPassed_withCreatedAtBeforeSchedule_isDueLate() {
        // Lunes(1) programado; tarea creada antes (2024-02-01), nunca
        // completada; hoy viernes 2024-03-15, el lunes de esta semana
        // (2024-03-11) se pasó sin marcar: debe seguir pendiente (atrasada).
        val now = epochOf(2024, 3, 15) // viernes
        val createdAt = epochOf(2024, 2, 1)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1), null, null, now, tz, createdAt))
    }

    /** Creada el MISMO día programado: toca hoy con normalidad, no como "atrasada" (caso protegido). */
    @Test
    fun isDueToday_weekly_neverCompleted_createdOnScheduledDayItself_isDueNotOverdue() {
        // 2024-03-11 es lunes; recurrenceDays=[1]; creada ese mismo lunes.
        val monday = epochOf(2024, 3, 11)
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(1), null, null, monday, tz, createdAt = monday))
        // Y no se marca como ocurrencia atrasada (el día de hoy SÍ es el programado).
        assertFalse(RecurrenceRules.isOverdueOccurrence("weekly", listOf(1), null, monday, tz))
    }

    /** Creada DESPUÉS de que el día programado de esa semana ya hubiera pasado: esa ocurrencia anterior a la creación no cuenta como "perdida". */
    @Test
    fun isDueToday_weekly_neverCompleted_createdAfterThisWeeksScheduledDayAlreadyPassed_isNotDueYet() {
        // Lunes(1) programado (2024-03-11); tarea creada el martes siguiente
        // (2024-03-12) — el lunes ya había pasado antes de que la tarea
        // existiera, así que el martes no debe aparecer como atrasada.
        val tuesday = epochOf(2024, 3, 12)
        val createdOnTuesday = epochOf(2024, 3, 12)
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1), null, null, tuesday, tz, createdOnTuesday))
    }

    /**
     * Comportamiento legado: tareas sin `createdAt` conocido (0 = tareas
     * creadas antes de que este campo existiera) NO abren la ventana de
     * "atrasada" en la rama nunca-completada — se mantiene el comportamiento
     * previo (solo toca el día exacto programado) para no generar falsos
     * atrasos retroactivos sobre tareas antiguas cuya fecha real de creación
     * se desconoce. Ver decisión de ancla en el informe adjunto.
     */
    @Test
    fun isDueToday_weekly_neverCompletedAndDayAlreadyPassed_legacyNoCreatedAt_staysNotDueUntilNextCycle() {
        val now = epochOf(2024, 3, 15) // viernes; lunes ya pasó
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(1), null, null, now, tz)) // createdAt=0 por defecto
    }

    // ── isDueToday: monthly con día fijo — completado tardío ───────

    /** Mismo catch-up que en semanal, pero para el día fijo mensual: si se pasó sin completar, sigue pendiente. */
    @Test
    fun isDueToday_monthlyWithDay_missedScheduledDay_withPriorCompletion_isDueLate() {
        // Día 15 programado; se completó el mes anterior (2024-02-15) pero
        // no este mes, y ya estamos a día 20 (pasado el 15): sigue pendiente.
        val now = epochOf(2024, 3, 20)
        val completedLastMonth = epochOf(2024, 2, 15)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 15, completedLastMonth, now, tz))
    }

    /** Ya haber completado el día fijo de este mes la deja resuelta. */
    @Test
    fun isDueToday_monthlyWithDay_completedThisMonthsScheduledDay_isNotDue() {
        val now = epochOf(2024, 3, 20)
        val completedThisMonth = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 15, completedThisMonth, now, tz))
    }

    /** Antes de llegar al día objetivo de este mes, si el ciclo anterior ya se completó a tiempo, todavía no toca nada. */
    @Test
    fun isDueToday_monthlyWithDay_beforeThisMonthsTarget_alreadyCaughtUpLastCycle_isNotDue() {
        // Antes del día 15 de este mes; el ciclo anterior (2024-02-15) ya se
        // completó a tiempo, así que todavía no toca nada.
        val now = epochOf(2024, 3, 10)
        val completedLastMonth = epochOf(2024, 2, 15)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 15, completedLastMonth, now, tz))
    }

    /** Mismo cambio de comportamiento que en semanal, para el día fijo mensual: nunca completada, ancla en [createdAt]. */
    @Test
    fun isDueToday_monthlyWithDay_neverCompletedMissedScheduledDay_withCreatedAtBeforeSchedule_isDueLate() {
        // Día 15 programado; tarea creada antes (2024-01-01), nunca
        // completada; ya estamos a día 20 (pasado el 15): sigue pendiente.
        val now = epochOf(2024, 3, 20)
        val createdAt = epochOf(2024, 1, 1)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 15, null, now, tz, createdAt))
    }

    /** Creada el mismo día objetivo del mes: toca hoy con normalidad, no como "atrasada". */
    @Test
    fun isDueToday_monthlyWithDay_neverCompleted_createdOnScheduledDayItself_isDueNotOverdue() {
        val day15 = epochOf(2024, 3, 15)
        assertTrue(RecurrenceRules.isDueToday("monthly", emptyList(), 15, null, day15, tz, createdAt = day15))
        assertFalse(RecurrenceRules.isOverdueOccurrence("monthly", emptyList(), 15, day15, tz))
    }

    /** Creada DESPUÉS de que el día objetivo de este mes ya hubiera pasado: esa ocurrencia anterior a la creación no cuenta como "perdida" (análogo mensual del caso semanal de arriba). */
    @Test
    fun isDueToday_monthlyWithDay_neverCompleted_createdAfterThisMonthsScheduledDayAlreadyPassed_isNotDueYet() {
        // Día 15 programado; tarea creada el día 16 (el 15 ya había pasado
        // antes de que la tarea existiera); hoy día 20, sin completar nunca:
        // no debe aparecer como atrasada.
        val now = epochOf(2024, 3, 20)
        val createdAfterTarget = epochOf(2024, 3, 16)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 15, null, now, tz, createdAfterTarget))
    }

    /** Legado (sin `createdAt`): nunca completada, sin ventana retroactiva — solo toca el día exacto. */
    @Test
    fun isDueToday_monthlyWithDay_neverCompletedAndDayAlreadyPassed_legacyNoCreatedAt_staysNotDueUntilNextCycle() {
        val now = epochOf(2024, 3, 20)
        assertFalse(RecurrenceRules.isDueToday("monthly", emptyList(), 15, null, now, tz)) // createdAt=0 por defecto
    }

    // ── isOverdueOccurrence ─────────────────────────────────────────

    /** Un día distinto del programado ya cuenta como "vencido" (la ocurrencia de hoy no es la esperada). */
    @Test
    fun isOverdueOccurrence_weekly_todayNotScheduledDay_isOverdue() {
        val now = epochOf(2024, 3, 15) // viernes; solo lunes(1) programado
        assertTrue(RecurrenceRules.isOverdueOccurrence("weekly", listOf(1), null, now, tz))
    }

    /** Si hoy es el día programado, la ocurrencia de hoy no está vencida. */
    @Test
    fun isOverdueOccurrence_weekly_todayIsScheduledDay_isNotOverdue() {
        val now = epochOf(2024, 3, 15) // viernes(5) programado
        assertFalse(RecurrenceRules.isOverdueOccurrence("weekly", listOf(5), null, now, tz))
    }

    /** Sin días configurados no hay ocurrencia programada que pueda vencer. */
    @Test
    fun isOverdueOccurrence_weekly_emptyDays_isNeverOverdue() {
        val now = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isOverdueOccurrence("weekly", emptyList(), null, now, tz))
    }

    /** Análogo mensual: un día distinto del día fijo configurado cuenta como vencido. */
    @Test
    fun isOverdueOccurrence_monthlyWithDay_notTargetDay_isOverdue() {
        val now = epochOf(2024, 3, 20)
        assertTrue(RecurrenceRules.isOverdueOccurrence("monthly", emptyList(), 15, now, tz))
    }

    /** El propio día fijo del mes no está vencido. */
    @Test
    fun isOverdueOccurrence_monthlyWithDay_isTargetDay_isNotOverdue() {
        val now = epochOf(2024, 3, 15)
        assertFalse(RecurrenceRules.isOverdueOccurrence("monthly", emptyList(), 15, now, tz))
    }

    /** El esquema mensual legado (sin día fijo) no tiene concepto de "ocurrencia vencida": nunca lo está. */
    @Test
    fun isOverdueOccurrence_monthlyLegacyNoDay_isNeverOverdue() {
        val now = epochOf(2024, 3, 20)
        assertFalse(RecurrenceRules.isOverdueOccurrence("monthly", emptyList(), null, now, tz))
    }

    // ── isDueToday: once ─────────────────────────────────────────

    /** Una tarea de una sola vez, nunca completada, está pendiente cualquier día. */
    @Test
    fun isDueToday_once_neverCompleted_isDue() {
        assertTrue(RecurrenceRules.isDueToday("once", emptyList(), null, null, epochOf(2024, 3, 15), tz))
    }

    /** Una tarea de una sola vez, una vez completada, no vuelve a estar pendiente nunca. */
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

    /** Tarea "once" con `dueDate` futuro: no está pendiente todavía (antes de la fecha límite). */
    @Test
    fun isDueToday_once_withFutureDueDate_isNotDueYet() {
        assertFalse(
            RecurrenceRules.isDueToday(
                "once", emptyList(), null,
                lastCompletedDate = null,
                nowEpochMs = epochOf(2024, 3, 15),
                tz = tz,
                dueDate = epochOf(2024, 3, 20)
            )
        )
    }

    /** Tarea "once" con `dueDate` en el pasado: sigue pendiente (vencida, no autocompletada). */
    @Test
    fun isDueToday_once_withPastDueDate_isDue() {
        assertTrue(
            RecurrenceRules.isDueToday(
                "once", emptyList(), null,
                lastCompletedDate = null,
                nowEpochMs = epochOf(2024, 3, 15),
                tz = tz,
                dueDate = epochOf(2024, 3, 10)
            )
        )
    }

    /** Tarea "once" con `dueDate` exactamente hoy: ya está pendiente (`>=`, no `>`). */
    @Test
    fun isDueToday_once_withDueDateToday_isDue() {
        assertTrue(
            RecurrenceRules.isDueToday(
                "once", emptyList(), null,
                lastCompletedDate = null,
                nowEpochMs = epochOf(2024, 3, 15),
                tz = tz,
                dueDate = epochOf(2024, 3, 15)
            )
        )
    }

    /** `dueDate` futuro no anula la regla "completada = nunca más pendiente". */
    @Test
    fun isDueToday_once_completedWithFutureDueDate_isNotDue() {
        assertFalse(
            RecurrenceRules.isDueToday(
                "once", emptyList(), null,
                lastCompletedDate = epochOf(2024, 1, 1),
                nowEpochMs = epochOf(2024, 3, 15),
                tz = tz,
                dueDate = epochOf(2024, 3, 20)
            )
        )
    }

    // ── isDueOn: fecha arbitraria (usada por CalendarScreen) ──────

    /** `isDueToday` debe seguir siendo un caso particular de `isDueOn` con fecha = hoy. */
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

    /**
     * CalendarScreen puede consultar una fecha PASADA anterior a la última
     * compleción (navegando a un mes anterior tras completar la tarea más
     * tarde) — no debe marcarse como pendiente retroactivamente.
     */
    @Test
    fun isDueOn_daily_pastDateBeforeLastCompletion_isNotDue() {
        val lastCompleted = epochOf(2024, 3, 20)
        val pastDate = dateOf(epochOf(2024, 3, 10))
        assertFalse(RecurrenceRules.isDueOn(pastDate, "daily", emptyList(), null, lastCompleted, tz))
    }

    /** Una fecha consultada posterior a la última compleción sí está pendiente. */
    @Test
    fun isDueOn_daily_dateAfterLastCompletion_isDue() {
        val lastCompleted = epochOf(2024, 3, 10)
        val futureDate = dateOf(epochOf(2024, 3, 20))
        assertTrue(RecurrenceRules.isDueOn(futureDate, "daily", emptyList(), null, lastCompleted, tz))
    }

    /** El mismo día exacto de la última compleción no está pendiente. */
    @Test
    fun isDueOn_daily_exactCompletionDate_isNotDue() {
        val lastCompleted = epochOf(2024, 3, 15)
        val sameDate = dateOf(lastCompleted)
        assertFalse(RecurrenceRules.isDueOn(sameDate, "daily", emptyList(), null, lastCompleted, tz))
    }

    /** Mismo criterio de "no retroactivo" que en diaria, aplicado al esquema mensual legado. */
    @Test
    fun isDueOn_monthlyLegacy_pastDateBeforeLastCompletion_isNotDue() {
        val lastCompleted = epochOf(2024, 3, 20)
        val pastDate = dateOf(epochOf(2024, 1, 10))
        assertFalse(RecurrenceRules.isDueOn(pastDate, "monthly", emptyList(), null, lastCompleted, tz))
    }

    /** Sin compleción previa, cualquier lunes pasado que coincida con el día programado cuenta como pendiente en esa fecha. */
    @Test
    fun isDueOn_weekly_withRecurrenceDays_matchesScheduledDayInThePast() {
        // Lunes(1) programado, nunca completada: cualquier lunes pasado debe tocar.
        val pastMonday = dateOf(epochOf(2024, 2, 26)) // lunes
        assertTrue(RecurrenceRules.isDueOn(pastMonday, "weekly", listOf(1), null, null, tz))
    }

    /** `isDueOn` también resuelve correctamente fechas futuras para el día fijo mensual. */
    @Test
    fun isDueOn_monthlyWithDay_matchesTargetDayInAFutureMonth() {
        val targetDate = dateOf(epochOf(2024, 6, 15))
        assertTrue(RecurrenceRules.isDueOn(targetDate, "monthly", emptyList(), 15, null, tz))
    }

    // ── endOfDueDay ──────────────────────────────────────────────

    /** El fin del día de vencimiento es la medianoche del día siguiente al de inicio. */
    @Test
    fun endOfDueDay_isMidnightOfTheFollowingDay() {
        val dueDayStart = LocalDateTime(2024, 3, 18, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2024, 3, 19), dateOf(end))
    }

    /**
     * Regresión del bug: comparar `now <= dueDate` contra la medianoche de
     * INICIO del día programado marcaría como "tarde" cualquier compleción
     * real (que siempre ocurre después de las 00:00 de ese día). Completar
     * más tarde el mismo día programado debe seguir contando como puntual.
     */
    @Test
    fun endOfDueDay_completionLaterTheSameScheduledDay_isBeforeEnd() {
        val dueDayStart = LocalDateTime(2024, 3, 18, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val completedSameDayEvening = epochOf(2024, 3, 18, hour = 20)
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertTrue(completedSameDayEvening <= end)
    }

    /** Completar al día siguiente del programado ya es tarde. */
    @Test
    fun endOfDueDay_completionNextDay_isAfterEnd() {
        val dueDayStart = LocalDateTime(2024, 3, 18, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val completedNextDay = epochOf(2024, 3, 19, hour = 1)
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertTrue(completedNextDay > end)
    }

    /** El fin de día del último día del mes cruza correctamente al primer día del mes siguiente. */
    @Test
    fun endOfDueDay_lastDayOfMonth_crossesIntoNextMonth() {
        val dueDayStart = LocalDateTime(2024, 3, 31, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2024, 4, 1), dateOf(end))
    }

    /** Caso límite de calendario: el 29 de febrero de un año bisiesto cruza a marzo. */
    @Test
    fun endOfDueDay_lastDayOfFebruaryLeapYear_crossesIntoMarch() {
        val dueDayStart = LocalDateTime(2024, 2, 29, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2024, 3, 1), dateOf(end))
    }

    /** Caso límite de calendario: el 31 de diciembre cruza al 1 de enero del año siguiente. */
    @Test
    fun endOfDueDay_lastDayOfYear_crossesIntoNextYear() {
        val dueDayStart = LocalDateTime(2024, 12, 31, 0, 0, 0).toInstant(tz).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStart, tz)
        assertEquals(LocalDate(2025, 1, 1), dateOf(end))
    }

    // ── resolveRotationAssignee ────────────────────────────────────

    /** Sin rotación configurada, se asigna siempre al miembro de respaldo (`fallback`). */
    @Test
    fun resolveRotationAssignee_emptyRotation_returnsFallback() {
        val nextDue = epochOf(2024, 3, 18) // lunes
        assertEquals(
            "member-A",
            RecurrenceRules.resolveRotationAssignee(emptyList(), nextDue, "member-A", tz)
        )
    }

    /** Si la rotación tiene un slot para el día de la semana de la próxima ocurrencia, se asigna a ese miembro. */
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

    /** Si la rotación no cubre el día de la semana de la próxima ocurrencia, se cae al miembro de respaldo. */
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

    /** Al purgar un miembro (p.ej. tras abandonar el hogar), solo se eliminan SUS slots, dejando intactos los de otros. */
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

    /** Purgar un miembro que no tiene slots en la rotación no modifica la lista. */
    @Test
    fun purgeMemberFromRotation_memberNotInRotation_returnsUnchanged() {
        val rotation = listOf(AssignmentSlot(dayOfWeek = 1, memberId = "member-B"))
        assertEquals(rotation, RecurrenceRules.purgeMemberFromRotation(rotation, "member-A"))
    }

    /** Caso límite defensivo: purgar sobre una rotación vacía no rompe nada. */
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

    /** Un mismo instante UTC puede caer en días de la semana distintos según la zona horaria: el resultado depende de [tz], no del reloj del sistema. */
    @Test
    fun isDueToday_explicitTz_sameInstantFallsOnDifferentLocalWeekday() {
        // 2024-03-14T23:00:00Z -> viernes 15 en Kiritimati, jueves 14 en Pago Pago.
        val instant = LocalDateTime(2024, 3, 14, 23, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(5), null, null, instant, tzFarEast)) // viernes
        assertFalse(RecurrenceRules.isDueToday("weekly", listOf(5), null, null, instant, tzFarWest)) // jueves, no viernes
        assertTrue(RecurrenceRules.isDueToday("weekly", listOf(4), null, null, instant, tzFarWest)) // jueves
    }

    /** `endOfDueDay` calcula la medianoche siguiente en la zona horaria explícita pasada, no en la del sistema. */
    @Test
    fun endOfDueDay_explicitNonDefaultTz_usesThatZonesMidnight() {
        val dueDayStartInFarEast = LocalDateTime(2024, 3, 15, 0, 0, 0).toInstant(tzFarEast).toEpochMilliseconds()
        val end = RecurrenceRules.endOfDueDay(dueDayStartInFarEast, tzFarEast)

        val endLocalDate = kotlinx.datetime.Instant.fromEpochMilliseconds(end).toLocalDateTime(tzFarEast).date
        assertEquals(LocalDate(2024, 3, 16), endLocalDate)
    }

    /** La rotación resuelve un miembro distinto para el mismo instante según la zona horaria pasada. */
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

    /** Sin rotación configurada, la próxima asignación se crea para el miembro que acaba de completar la tarea. */
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

    /** Si ya existe una asignación "assigned" idéntica (mismo miembro y fecha), no se duplica. */
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

    /** Una asignación existente con una fecha de vencimiento distinta no cuenta como duplicado: sí se crea la nueva. */
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

    /** Solo deduplica contra asignaciones "assigned" — una "completed" para el mismo miembro/fecha no cuenta como ya regenerada. */
    @Test
    fun resolveNextAssignmentDecision_existingAssignmentAlreadyCompleted_stillCreates() {
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

    /** Si la rotación asigna la próxima ocurrencia a otro miembro, la deduplicación se comprueba contra ESE miembro, no contra quien completó la tarea. */
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
