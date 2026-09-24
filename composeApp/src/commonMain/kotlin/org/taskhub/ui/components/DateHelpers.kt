/**
 * Helpers de fecha localizados compartidos por [org.taskhub.ui.screens.TaskListScreen]
 * y [org.taskhub.ui.screens.CalendarScreen] — R17 (2026-09-24): antes cada
 * pantalla tenía su propia copia de la abreviatura de día/mes de la semana
 * (mismas claves i18n `day_abbr_*`/`month_abbr_*`, solo variaba si el
 * parámetro de entrada era [DayOfWeek]/[Month] o el `Int` equivalente).
 */
package org.taskhub.ui.components

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.taskhub.ui.i18n.AppStrings

/** Abreviatura de 3 letras del día de la semana en [lang] (p.ej. "lun"/"Mon"). */
internal fun localizedDayNameAbbr(dayOfWeek: DayOfWeek, lang: String): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> AppStrings.get("day_abbr_monday", lang)
    DayOfWeek.TUESDAY -> AppStrings.get("day_abbr_tuesday", lang)
    DayOfWeek.WEDNESDAY -> AppStrings.get("day_abbr_wednesday", lang)
    DayOfWeek.THURSDAY -> AppStrings.get("day_abbr_thursday", lang)
    DayOfWeek.FRIDAY -> AppStrings.get("day_abbr_friday", lang)
    DayOfWeek.SATURDAY -> AppStrings.get("day_abbr_saturday", lang)
    DayOfWeek.SUNDAY -> AppStrings.get("day_abbr_sunday", lang)
    else -> ""
}

/** Abreviatura de 3 letras del mes en [lang] (p.ej. "ene"/"Jan"). */
internal fun localizedMonthAbbr(month: Month, lang: String): String = when (month) {
    Month.JANUARY -> AppStrings.get("month_abbr_january", lang)
    Month.FEBRUARY -> AppStrings.get("month_abbr_february", lang)
    Month.MARCH -> AppStrings.get("month_abbr_march", lang)
    Month.APRIL -> AppStrings.get("month_abbr_april", lang)
    Month.MAY -> AppStrings.get("month_abbr_may", lang)
    Month.JUNE -> AppStrings.get("month_abbr_june", lang)
    Month.JULY -> AppStrings.get("month_abbr_july", lang)
    Month.AUGUST -> AppStrings.get("month_abbr_august", lang)
    Month.SEPTEMBER -> AppStrings.get("month_abbr_september", lang)
    Month.OCTOBER -> AppStrings.get("month_abbr_october", lang)
    Month.NOVEMBER -> AppStrings.get("month_abbr_november", lang)
    Month.DECEMBER -> AppStrings.get("month_abbr_december", lang)
    else -> ""
}

/**
 * Friendly date string for card display: "Hoy", "Mañana", day name (if
 * within this week), or "day month-abbr" (e.g. "25 sep").
 */
internal fun formatFriendlyDate(epochMillis: Long, lang: String): String {
    if (epochMillis <= 0) return ""
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz).date
    val daysDiff = date.toEpochDays() - today.toEpochDays()
    return when {
        daysDiff == 0 -> AppStrings.get("tasks_due_today", lang)
        daysDiff == 1 -> AppStrings.get("due_date_tomorrow", lang)
        daysDiff in (-6..-1) || daysDiff in (2..6) -> localizedDayNameAbbr(date.dayOfWeek, lang)
        else -> "${date.dayOfMonth} ${localizedMonthAbbr(date.month, lang)}"
    }
}
