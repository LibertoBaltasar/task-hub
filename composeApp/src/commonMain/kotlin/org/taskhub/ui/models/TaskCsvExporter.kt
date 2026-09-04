package org.taskhub.ui.models

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.models.TaskResponse

/**
 * Exportación de tareas a CSV, extraída de [TaskScreenModel] (el "mini god
 * ScreenModel", panel de revisión 2026-09-03/04, Experto 7, reabierto). Sin
 * estado ni dependencias — función pura, no necesita ser parte de ningún
 * ScreenModel (panel v7, #17).
 */
object TaskCsvExporter {

    fun generateCsv(tasks: List<TaskResponse>): String {
        val sb = StringBuilder()
        sb.appendLine("Nombre,Frecuencia,Puntos,Veces completada,Último completado")
        for (task in tasks) {
            val freq = when (task.frequency) {
                "daily" -> "Diaria"
                "weekly" -> "Semanal"
                "monthly" -> "Mensual"
                else -> "Una vez"
            }
            val completions = if (task.lastCompletedDate != null) "1" else "0"
            val lastCompleted = if (task.lastCompletedDate != null) {
                val instant = Instant.fromEpochMilliseconds(task.lastCompletedDate)
                val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${local.dayOfMonth}/${local.monthNumber}/${local.year}"
            } else {
                "Nunca"
            }
            val escapedTitle = escapeCsvField(task.title)
            sb.appendLine("$escapedTitle,$freq,${task.points},$completions,$lastCompleted")
        }
        return sb.toString()
    }

    /**
     * Escapa un campo CSV para exportar de forma segura. Antepone `'` si el
     * valor empieza por `=`, `+`, `-`, `@`, tab o CR, para que Excel/Sheets no
     * lo interprete como fórmula (CSV formula injection, CWE-1236) — el título
     * de tarea es texto libre que cualquier miembro del hogar puede escribir.
     */
    private fun escapeCsvField(value: String): String {
        val safeValue = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return "\"${safeValue.replace("\"", "\"\"")}\""
    }
}
