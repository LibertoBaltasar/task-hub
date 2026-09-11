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

/**
 * [PointsRules] cubre el sistema de "agradecimientos" (puntos que un miembro
 * regala a otro con un tope semanal) y de donaciones (transferencia directa
 * de puntos propios), incluyendo el cálculo del inicio de semana (lunes) que
 * determina cuándo se resetea el presupuesto semanal.
 */
class PointsRulesTest {

    private val tz = TimeZone.currentSystemDefault()

    // ── mondayStartOfWeek ──────────────────────────────────────

    /** El resultado siempre cae en un lunes a medianoche, y nunca es posterior al instante consultado. */
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

    /** Aplicar la función sobre su propio resultado no debe cambiar el valor (ya es un inicio de semana). */
    @Test
    fun mondayStartOfWeek_isIdempotent() {
        val now = LocalDateTime(2024, 3, 15, 9, 30, 0).toInstant(tz).toEpochMilliseconds()
        val weekStart = PointsRules.mondayStartOfWeek(now)

        assertEquals(weekStart, PointsRules.mondayStartOfWeek(weekStart))
    }

    /** Un instante justo antes del lunes calculado pertenece, por definición, a la semana anterior. */
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

    /** Dentro de la misma semana, el presupuesto ya consumido y su fecha de inicio no cambian. */
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

    /** Al consumir exactamente el tope semanal, el saldo restante es 0 (no negativo). */
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

    /** Un `appreciationGiven` corrupto/superior al tope (dato inconsistente) tampoco produce saldo negativo. */
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

    /** Al cruzar a una nueva semana, el presupuesto se resetea a 0 y el saldo vuelve al tope completo. */
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

    /** El límite exacto de una semana (weekStart + 7 días) ya cuenta como expirado, no como "todavía dentro". */
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

    /** Nadie puede agradecerse puntos a sí mismo. */
    @Test
    fun validateAppreciateBasic_self_isRejected() {
        assertEquals(
            PointsRules.AppreciateError.SELF,
            PointsRules.validateAppreciateBasic("m1", "m1", 10)
        )
    }

    /** La cantidad a agradecer debe ser estrictamente positiva (0 o negativa se rechaza). */
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

    /** Un agradecimiento entre dos miembros distintos con cantidad positiva no tiene error básico. */
    @Test
    fun validateAppreciateBasic_validInput_isNull() {
        assertNull(PointsRules.validateAppreciateBasic("m1", "m2", 10))
    }

    /** Agradecer más de lo que queda de presupuesto semanal se rechaza. */
    @Test
    fun validateAppreciateLimit_amountAboveRemaining_isRejected() {
        val budget = PointsRules.AppreciationBudget(given = 45, weekStart = 0L, remaining = 5)
        assertEquals(
            PointsRules.AppreciateError.LIMIT_EXCEEDED,
            PointsRules.validateAppreciateLimit(6, budget)
        )
    }

    /** Agradecer exactamente el saldo restante (agotarlo del todo) sí está permitido. */
    @Test
    fun validateAppreciateLimit_amountEqualToRemaining_isAllowed() {
        val budget = PointsRules.AppreciationBudget(given = 45, weekStart = 0L, remaining = 5)
        assertNull(PointsRules.validateAppreciateLimit(5, budget))
    }

    // ── validateDonateBasic / validateDonateBalance ─────────────

    /** Nadie puede donarse puntos a sí mismo. */
    @Test
    fun validateDonateBasic_self_isRejected() {
        assertEquals(
            PointsRules.DonateError.SELF,
            PointsRules.validateDonateBasic("m1", "m1", 10)
        )
    }

    /** La cantidad a donar debe ser estrictamente positiva. */
    @Test
    fun validateDonateBasic_zeroOrNegativeAmount_isRejected() {
        assertEquals(
            PointsRules.DonateError.INVALID_AMOUNT,
            PointsRules.validateDonateBasic("m1", "m2", 0)
        )
    }

    /** No se puede donar más puntos de los que el donante tiene disponibles. */
    @Test
    fun validateDonateBalance_insufficientBalance_isRejected() {
        assertEquals(
            PointsRules.DonateError.INSUFFICIENT_BALANCE,
            PointsRules.validateDonateBalance(amount = 100, fromBalance = 50)
        )
    }

    /** Donar el saldo completo (vaciarlo del todo) sí está permitido. */
    @Test
    fun validateDonateBalance_exactBalance_isAllowed() {
        assertNull(PointsRules.validateDonateBalance(amount = 50, fromBalance = 50))
    }

    /** Camino feliz: donante/receptor distintos, cantidad positiva y saldo suficiente pasan ambas validaciones. */
    @Test
    fun validateDonateBalance_validTransfer_isAllowed() {
        assertNull(PointsRules.validateDonateBasic("donor", "receptor", 20))
        assertNull(PointsRules.validateDonateBalance(amount = 20, fromBalance = 50))
    }

    // ── exceedsPeerTransferLimit ─────────────────────────────────

    /** Un importe por encima del tope de `firestore.rules` se clasifica como excedido. */
    @Test
    fun exceedsPeerTransferLimit_amountAboveLimit_isTrue() {
        assertTrue(PointsRules.exceedsPeerTransferLimit(PointsRules.MAX_PEER_TRANSFER_AMOUNT + 1))
    }

    /** El tope exacto todavía es válido (no lo supera), igual que en las demás validaciones de esta clase. */
    @Test
    fun exceedsPeerTransferLimit_amountAtLimit_isFalse() {
        assertEquals(false, PointsRules.exceedsPeerTransferLimit(PointsRules.MAX_PEER_TRANSFER_AMOUNT))
    }

    /** Un importe por debajo del tope no se clasifica como excedido. */
    @Test
    fun exceedsPeerTransferLimit_amountBelowLimit_isFalse() {
        assertEquals(false, PointsRules.exceedsPeerTransferLimit(10))
    }
}
