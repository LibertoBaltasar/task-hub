/**
 * Reglas puras (sin I/O) del sistema de puntos entre miembros: agradecimientos
 * (con tope semanal) y donaciones (transferencia de saldo). Usado por
 * [FirestoreRepository]/[MemberRepository] para validar antes de escribir en
 * Firestore y por `commonTest` para testear la lógica sin red.
 */
package org.taskhub.network

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Reglas puras de puntos: agradecer (acuñación con tope semanal) y donar
 * (transferencia de saldo). Sin I/O — testable directamente en `commonTest`.
 */
object PointsRules {

    /** Tope semanal de puntos que un miembro puede DAR agradeciendo a otros. */
    const val WEEKLY_APPRECIATION_BUDGET = 50
    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    /**
     * Tope de puntos que una única escritura de "donar/agradecer entre
     * iguales" puede acreditar al receptor — debe coincidir EXACTAMENTE con
     * el literal `1000` de `isPeerPointsTransfer` en `firestore.rules`
     * (cota de seguridad server-side, no un límite de producto: ver
     * comentario de esa función). Usado por [MemberRepository.donatePoints]
     * solo para CLASIFICAR el error tras un 403 esperado, no para bloquear
     * la donación antes de intentarla — un donante owner/admin del hogar no
     * está sujeto a este tope (su escritura pasa por `isTrusted(hid)`), así
     * que validar aquí de forma ciega bloquearía donaciones grandes que SÍ
     * son válidas para ellos.
     */
    const val MAX_PEER_TRANSFER_AMOUNT = 1000

    /** Motivos por los que un agradecimiento no es válido. */
    enum class AppreciateError { SELF, INVALID_AMOUNT, LIMIT_EXCEEDED }

    /** Motivos por los que una donación no es válida. */
    enum class DonateError { SELF, INVALID_AMOUNT, INSUFFICIENT_BALANCE }

    /**
     * Estado del presupuesto semanal de agradecimiento de un miembro, ya
     * normalizado a la semana vigente en el momento de la consulta.
     */
    data class AppreciationBudget(val given: Int, val weekStart: Long, val remaining: Int)

    /** Epoch millis del lunes 00:00 hora local de la semana que contiene [epochMs]. */
    fun mondayStartOfWeek(epochMs: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date
        val daysSinceMonday = date.dayOfWeek.ordinal // MONDAY=0 .. SUNDAY=6
        val monday = date.minus(daysSinceMonday, DateTimeUnit.DAY)
        return LocalDateTime(monday.year, monday.monthNumber, monday.dayOfMonth, 0, 0, 0)
            .toInstant(tz).toEpochMilliseconds()
    }

    /**
     * Presupuesto semanal de agradecimiento vigente en [now], dado lo ya dado
     * ([appreciationGiven]) y el inicio de semana registrado ([appreciationWeekStart]).
     * Si la semana registrada ya pasó, reinicia el presupuesto a 0 dado / [WEEKLY_APPRECIATION_BUDGET] restante.
     */
    fun currentAppreciationBudget(
        appreciationGiven: Int,
        appreciationWeekStart: Long,
        now: Long
    ): AppreciationBudget {
        val weekExpired = now >= appreciationWeekStart + WEEK_MILLIS
        val given = if (weekExpired) 0 else appreciationGiven
        val weekStart = if (weekExpired) mondayStartOfWeek(now) else appreciationWeekStart
        val remaining = (WEEKLY_APPRECIATION_BUDGET - given).coerceAtLeast(0)
        return AppreciationBudget(given, weekStart, remaining)
    }

    /** Valida self / importe antes de tocar red. Null = válido. */
    fun validateAppreciateBasic(fromMemberId: String, toMemberId: String, amount: Int): AppreciateError? {
        if (fromMemberId == toMemberId) return AppreciateError.SELF
        if (amount < 1) return AppreciateError.INVALID_AMOUNT
        return null
    }

    /** Valida el importe contra el presupuesto semanal restante. Null = válido. */
    fun validateAppreciateLimit(amount: Int, budget: AppreciationBudget): AppreciateError? =
        if (amount > budget.remaining) AppreciateError.LIMIT_EXCEEDED else null

    /** Valida self / importe antes de tocar red. Null = válido. */
    fun validateDonateBasic(fromMemberId: String, toMemberId: String, amount: Int): DonateError? {
        if (fromMemberId == toMemberId) return DonateError.SELF
        if (amount < 1) return DonateError.INVALID_AMOUNT
        return null
    }

    /** Valida el importe contra el saldo actual del donante. Null = válido. */
    fun validateDonateBalance(amount: Int, fromBalance: Int): DonateError? =
        if (amount > fromBalance) DonateError.INSUFFICIENT_BALANCE else null

    /**
     * True si [amount] supera [MAX_PEER_TRANSFER_AMOUNT] — el tope que
     * `firestore.rules` aplica a una única escritura de "donar/agradecer
     * entre iguales". Usado por [MemberRepository.donatePoints] para
     * clasificar un 403 esperado tras intentar acreditar al receptor como
     * `AMOUNT_EXCEEDS_LIMIT` en vez de un fallo de transferencia genérico.
     */
    fun exceedsPeerTransferLimit(amount: Int): Boolean = amount > MAX_PEER_TRANSFER_AMOUNT
}
