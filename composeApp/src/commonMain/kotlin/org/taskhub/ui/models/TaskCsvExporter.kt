// Utilidad de exportación de tareas a formato CSV, usada desde las pantallas
// de tareas/estadísticas (`ui/screens/`) para generar el fichero que el
// usuario comparte o descarga. No es un ScreenModel ni depende de
// repositorios: opera solo sobre los [TaskResponse] que ya tiene la UI.
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

    /**
     * Genera el contenido CSV (con cabecera) para la lista de tareas dada.
     * Una fila por tarea. No hace I/O: quien llama decide si lo comparte,
     * lo guarda en disco, etc.
     */
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
            // Nota: TaskResponse no guarda un contador histórico de
            // compleciones, solo la fecha de la última. La columna "Veces
            // completada" es por tanto un booleano 1/0 (¿se completó alguna
            // vez?), no el número real de veces que se completó la tarea.
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
