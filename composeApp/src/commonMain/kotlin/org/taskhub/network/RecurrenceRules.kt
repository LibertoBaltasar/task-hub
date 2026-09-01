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
import org.taskhub.network.models.AssignmentSlot

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
     *   y no se completó hoy. Si ya se completó alguna vez antes (hay
     *   [lastCompletedDate]) y desde entonces pasó un día programado sin
     *   completar, sigue tocando en modo "atrasada" hasta que se complete
     *   (completado tardío) — así una ocurrencia no marcada a tiempo no
     *   desaparece de la lista al día siguiente.
     * - monthly: si [recurrenceDay] no es null, toca ese día del mes
     *   (ajustado con [clampDayOfMonth]); igual que weekly, si ya hubo un
     *   completado previo y desde entonces pasó el día objetivo sin marcar,
     *   sigue tocando en modo atrasado. Si [recurrenceDay] es null,
     *   comportamiento legado: toca una vez al mes, cualquier día.
     * - once: toca si nunca se completó.
     *
     * Nota: la ventana de "completado tardío" solo se abre si la tarea ya
     * se había completado alguna vez ([lastCompletedDate] no nulo) — así una
     * tarea recién creada, cuyo primer día programado aún no llegó, no
     * aparece falsamente como "atrasada" el mismo día que se crea.
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
        return isDueOn(today, frequency, recurrenceDays, recurrenceDay, lastCompletedDate, tz)
    }

    /**
     * Igual que [isDueToday], pero para una fecha [date] arbitraria (no
     * necesariamente hoy) — usada por la vista de calendario para saber si
     * una tarea recurrente toca en una celda de día concreta, sin
     * reimplementar esta misma lógica por segunda vez (antes
     * `CalendarScreen.isTaskDueOnDay` duplicaba daily/weekly/monthly con
     * riesgo real de divergencia respecto a esta regla).
     */
    fun isDueOn(
        date: LocalDate,
        frequency: String,
        recurrenceDays: List<Int>,
        recurrenceDay: Int?,
        lastCompletedDate: Long?,
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val lastCompletedLocalDate = lastCompletedDate?.let {
            Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date
        }

        // "Sin recurrenceDays/recurrenceDay concreto": due si nunca se completó,
        // o si [date] es estrictamente posterior a la última compleción. Con
        // isDueToday, [date] es siempre "hoy" (nunca anterior a la última
        // compleción real), así que esto coincide con el `!=` que usaba antes
        // esta función; la diferencia solo importa para [isDueOn] con fechas
        // pasadas anteriores a la última compleción (uso de CalendarScreen al
        // navegar a meses anteriores), donde "posterior a" es la comparación
        // correcta — `!=` marcaría erróneamente esos días pasados como pendientes.
        fun dueAfterLastCompletion(): Boolean =
            lastCompletedLocalDate == null || date > lastCompletedLocalDate

        return when (frequency) {
            "daily" -> dueAfterLastCompletion()
            "weekly" -> {
                if (recurrenceDays.isEmpty()) return dueAfterLastCompletion()
                if (lastCompletedLocalDate == null) {
                    val dow = date.dayOfWeek.ordinal + 1 // 1=Lunes
                    dow in recurrenceDays
                } else {
                    val mostRecentTarget = mostRecentWeeklyOccurrence(date, recurrenceDays)
                    lastCompletedLocalDate < mostRecentTarget
                }
            }
            "monthly" -> {
                if (recurrenceDay != null) {
                    val thisMonthTarget = LocalDate(
                        date.year, date.monthNumber,
                        clampDayOfMonth(recurrenceDay, date.year, date.monthNumber)
                    )
                    if (lastCompletedLocalDate == null) {
                        date == thisMonthTarget
                    } else {
                        val mostRecentTarget = if (date >= thisMonthTarget) {
                            thisMonthTarget
                        } else {
                            val prevMonth = date.minus(1, DateTimeUnit.MONTH)
                            LocalDate(
                                prevMonth.year, prevMonth.monthNumber,
                                clampDayOfMonth(recurrenceDay, prevMonth.year, prevMonth.monthNumber)
                            )
                        }
                        lastCompletedLocalDate < mostRecentTarget
                    }
                } else {
                    // Legado: sin día fijado, toca una vez al mes (cualquier día).
                    // Due si el mes de [date] es estrictamente posterior al mes de
                    // la última compleción (no solo "distinto" — ver comentario de
                    // dueAfterLastCompletion más arriba, mismo motivo).
                    if (lastCompletedLocalDate == null) return true
                    date.year > lastCompletedLocalDate.year ||
                        (date.year == lastCompletedLocalDate.year && date.monthNumber > lastCompletedLocalDate.monthNumber)
                }
            }
            "once" -> lastCompletedDate == null
            else -> false
        }
    }

    /** Día programado (de [recurrenceDays]) más reciente en `<= date` (a lo sumo 7 días atrás). */
    private fun mostRecentWeeklyOccurrence(date: LocalDate, recurrenceDays: List<Int>): LocalDate {
        var candidate = date
        repeat(7) {
            val dow = candidate.dayOfWeek.ordinal + 1
            if (dow in recurrenceDays) return candidate
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return date // no debería ocurrir con recurrenceDays válidos (1..7)
    }

    /**
     * ¿La ocurrencia pendiente de hoy corresponde a un día programado ya
     * pasado (completado tardío)? Solo tiene sentido si [isDueToday] ya dio
     * `true` — sirve para que la UI distinga "toca hoy" de "se pasó el día y
     * sigue pendiente", sin reimplementar el cálculo de fecha programada.
     */
    fun isOverdueOccurrence(
        frequency: String,
        recurrenceDays: List<Int>,
        recurrenceDay: Int?,
        nowEpochMs: Long,
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(tz).date
        return when (frequency) {
            "weekly" -> {
                if (recurrenceDays.isEmpty()) return false
                val todayDow = today.dayOfWeek.ordinal + 1
                todayDow !in recurrenceDays
            }
            "monthly" -> {
                if (recurrenceDay == null) return false
                val targetDay = clampDayOfMonth(recurrenceDay, today.year, today.monthNumber)
                today.dayOfMonth != targetDay
            }
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

    /**
     * Convierte la "medianoche del día programado" que devuelve [nextOccurrence]
     * (y que se persiste tal cual en `nextDueAt`/`assignment.dueDate` para
     * mostrar/sincronizar Calendar) en la fecha límite REAL a efectos de
     * penalizar retrasos: medianoche del día SIGUIENTE (fin del día
     * programado).
     *
     * Sin este ajuste, comparar `now <= dueDate` directamente contra la
     * medianoche de inicio del día programado marcaría como "tarde" a
     * CUALQUIER compleción real (que siempre ocurre en algún momento DURANTE
     * ese día, nunca exactamente a las 00:00) — habría sido imposible
     * completar una tarea recurrente con penalización sin que se penalizara
     * cada vez.
     */
    fun endOfDueDay(dueDayStartMs: Long, tz: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = Instant.fromEpochMilliseconds(dueDayStartMs).toLocalDateTime(tz).date
        val nextDay = date.plus(1, DateTimeUnit.DAY)
        return LocalDateTime(nextDay.year, nextDay.monthNumber, nextDay.dayOfMonth, 0, 0, 0)
            .toInstant(tz).toEpochMilliseconds()
    }

    /**
     * Miembro que debe ocupar la siguiente asignación de una tarea recurrente,
     * respetando `assignmentRotation` (quién le toca cada día de la semana).
     *
     * Único punto de decisión de rotación, compartido por `completeTask` y
     * `completeAssignment` en `FirestoreRepository` (antes cada uno lo hacía
     * a su manera — `completeTask` no regeneraba nada y `completeAssignment`
     * siempre reasignaba al mismo miembro que acababa de completarla,
     * ignorando esta lista por completo).
     *
     * Si [assignmentRotation] está vacía (sin rotación configurada) o no hay
     * ningún slot para el día de la semana de [nextDueMs], devuelve
     * [fallbackMemberId] — comportamiento legado: la siguiente ocurrencia se
     * asigna a quien acaba de completar la anterior.
     */
    fun resolveRotationAssignee(
        assignmentRotation: List<AssignmentSlot>,
        nextDueMs: Long,
        fallbackMemberId: String,
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        if (assignmentRotation.isEmpty()) return fallbackMemberId
        val dow = Instant.fromEpochMilliseconds(nextDueMs).toLocalDateTime(tz).date.dayOfWeek.ordinal + 1
        return assignmentRotation.find { it.dayOfWeek == dow }?.memberId ?: fallbackMemberId
    }
}
