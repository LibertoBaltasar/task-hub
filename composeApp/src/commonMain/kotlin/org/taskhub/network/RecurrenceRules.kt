package org.taskhub.network

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Reglas puras de recurrencia de tareas (daily/weekly/monthly). Sin I/O —
 * testable directamente en `commonTest`.
 *
 * Task Hub no guarda "próxima ocurrencia" en Firestore: una tarea recurrente
 * es un único documento y su estado ("¿toca hoy?") se recalcula en cliente a
 * partir de `frequency` + `recurrenceDays`/`recurrenceDay` + `lastCompletedDate`
 * (ver [isDueToday], usada por TaskListScreen). [nextOccurrence] es un cálculo
 * complementario (para mostrar/testear "próxima vez") con la misma regla.
 */
object RecurrenceRules {

    /**
     * Ajusta [day] (1..31) al último día válido de [year]-[month] si el mes no
     * llega a tener ese día (p.ej. 31 en abril → 30, 29/30/31 en febrero → 28 o 29).
     * Regla elegida para meses cortos: CLAMP al último día del mes, en vez de
     * saltarse el mes — así una tarea "día 31" no deja de aparecer en los meses
     * de 30 días, solo se ajusta.
     */
    fun clampDayOfMonth(day: Int, year: Int, month: Int): Int {
        val firstOfNextMonth = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
        val lastDayOfMonth = firstOfNextMonth.minus(1, DateTimeUnit.DAY).dayOfMonth
        return day.coerceIn(1, lastDayOfMonth)
    }

    /**
     * ¿Toca hoy esta tarea recurrente? Misma lógica que usaba (privadamente)
     * TaskListScreen, extraída aquí para ser pura y testable.
     *
     * - daily: siempre toca, salvo que ya se completara hoy.
     * - weekly: toca si hoy coincide con [recurrenceDays] (vacío = cualquier día)
     *   y no se completó hoy.
     * - monthly: si [recurrenceDay] no es null, toca solo ese día del mes
     *   (ajustado con [clampDayOfMonth]) y no completado hoy; si es null,
     *   comportamiento legado: toca una vez al mes, cualquier día.
     * - once: toca si nunca se completó.
     */
    fun isDueToday(
        frequency: String,
        recurrenceDays: List<Int>,
        recurrenceDay: Int?,
        lastCompletedDate: Long?,
        nowEpochMs: Long,
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(tz).date

        fun completedToday(): Boolean {
            val lcd = lastCompletedDate ?: return false
            val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
            return lcdDate == today
        }

        return when (frequency) {
            "daily" -> !completedToday()
            "weekly" -> {
                val todayDow = today.dayOfWeek.ordinal + 1 // 1=Lunes
                if (recurrenceDays.isNotEmpty() && todayDow !in recurrenceDays) return false
                !completedToday()
            }
            "monthly" -> {
                if (recurrenceDay != null) {
                    val targetDay = clampDayOfMonth(recurrenceDay, today.year, today.monthNumber)
                    if (today.dayOfMonth != targetDay) return false
                    !completedToday()
                } else {
                    // Legado: sin día fijado, toca una vez al mes (cualquier día).
                    val lcd = lastCompletedDate ?: return true
                    val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
                    lcdDate.month != today.month || lcdDate.year != today.year
                }
            }
            "once" -> lastCompletedDate == null
            else -> false
        }
    }

    /**
     * Próxima ocurrencia estrictamente posterior a [nowEpochMs], a las 00:00
     * hora local del día resultante.
     *
     * - daily: mañana.
     * - weekly: si [weeklyDays] no está vacío, el primer día de esa lista
     *   estrictamente posterior a hoy (escaneando hacia delante, soporta
     *   varios días por semana — p.ej. lunes+miércoles+viernes). Si
     *   [weeklyDays] está vacío, usa el día de la semana [day] (1=Lunes..
     *   7=Domingo; si hoy ya es ese día, +7 días); si [day] también es null,
     *   se usa el día de la semana actual (equivale a "dentro de 7 días").
     * - monthly: siguiente día [day] del mes (1..31, ajustado con
     *   [clampDayOfMonth]); si ese día de este mes ya pasó (o es hoy), salta
     *   al mes siguiente. Si [day] es null, se usa el día del mes actual.
     */
    fun nextOccurrence(
        nowEpochMs: Long,
        frequency: String,
        day: Int? = null,
        weeklyDays: List<Int> = emptyList(),
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): Long {
        val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(tz).date

        val targetDate = when (frequency) {
            "daily" -> today.plus(1, DateTimeUnit.DAY)
            "weekly" -> {
                if (weeklyDays.isNotEmpty()) {
                    var candidate = today.plus(1, DateTimeUnit.DAY)
                    var found: LocalDate? = null
                    var safety = 0
                    while (found == null && safety < 14) {
                        val dow = candidate.dayOfWeek.ordinal + 1
                        if (dow in weeklyDays) found = candidate
                        candidate = candidate.plus(1, DateTimeUnit.DAY)
                        safety++
                    }
                    found ?: today.plus(7, DateTimeUnit.DAY) // no debería ocurrir (rango cubre 2 semanas)
                } else {
                    val targetDow = day ?: (today.dayOfWeek.ordinal + 1)
                    val currentDow = today.dayOfWeek.ordinal + 1
                    var diff = targetDow - currentDow
                    if (diff <= 0) diff += 7
                    today.plus(diff, DateTimeUnit.DAY)
                }
            }
            "monthly" -> {
                val targetDay = day ?: today.dayOfMonth
                val thisMonthCandidate = LocalDate(
                    today.year, today.monthNumber,
                    clampDayOfMonth(targetDay, today.year, today.monthNumber)
                )
                if (thisMonthCandidate > today) {
                    thisMonthCandidate
                } else {
                    val nextMonth = today.plus(1, DateTimeUnit.MONTH)
                    LocalDate(
                        nextMonth.year, nextMonth.monthNumber,
                        clampDayOfMonth(targetDay, nextMonth.year, nextMonth.monthNumber)
                    )
                }
            }
            else -> today
        }

        return LocalDateTime(targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth, 0, 0, 0)
            .toInstant(tz).toEpochMilliseconds()
    }
}
