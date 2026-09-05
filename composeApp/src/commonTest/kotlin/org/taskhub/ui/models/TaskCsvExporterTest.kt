package org.taskhub.ui.models

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.models.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TaskCsvExporter] es una función pura extraída de `TaskScreenModel` (panel
 * v7, #17) sin ningún test hasta ahora (panel v7, Exp. 13, hueco barato de
 * cerrar) — mismo patrón sin mocks que [org.taskhub.network.AssignmentCompletionRulesTest].
 */
class TaskCsvExporterTest {

    private fun task(
        title: String = "Sacar la basura",
        frequency: String = "once",
        points: Int = 10,
        lastCompletedDate: Long? = null
    ) = TaskResponse(
        id = "task-1",
        householdId = "household-1",
        createdBy = "member-1",
        title = title,
        frequency = frequency,
        points = points,
        lastCompletedDate = lastCompletedDate
    )

    @Test
    fun generateCsv_includesHeaderRow() {
        val csv = TaskCsvExporter.generateCsv(emptyList())

        assertEquals("Nombre,Frecuencia,Puntos,Veces completada,Último completado\n", csv)
    }

    @Test
    fun generateCsv_neverCompletedTask_showsNuncaAndZeroCompletions() {
        val csv = TaskCsvExporter.generateCsv(listOf(task(lastCompletedDate = null)))

        assertTrue(csv.contains(",0,Nunca"))
    }

    @Test
    fun generateCsv_completedTask_showsFormattedDateAndOneCompletion() {
        val completedAt = 1_700_000_000_000L
        val expectedDate = Instant.fromEpochMilliseconds(completedAt)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val expected = "${expectedDate.dayOfMonth}/${expectedDate.monthNumber}/${expectedDate.year}"

        val csv = TaskCsvExporter.generateCsv(listOf(task(lastCompletedDate = completedAt)))

        assertTrue(csv.contains(",1,$expected"))
    }

    @Test
    fun generateCsv_mapsFrequencyLabels() {
        val csv = TaskCsvExporter.generateCsv(
            listOf(
                task(title = "a", frequency = "daily"),
                task(title = "b", frequency = "weekly"),
                task(title = "c", frequency = "monthly"),
                task(title = "d", frequency = "once")
            )
        )

        assertTrue(csv.contains("\"a\",Diaria,"))
        assertTrue(csv.contains("\"b\",Semanal,"))
        assertTrue(csv.contains("\"c\",Mensual,"))
        assertTrue(csv.contains("\"d\",Una vez,"))
    }

    @Test
    fun generateCsv_escapesDoubleQuotesInTitle() {
        val csv = TaskCsvExporter.generateCsv(listOf(task(title = "Tarea \"urgente\"")))

        assertTrue(csv.contains("\"Tarea \"\"urgente\"\"\""))
    }

    @Test
    fun generateCsv_prependsQuoteToNeutralizeFormulaInjection() {
        // CSV formula injection (CWE-1236): un título que empieza por
        // "=" / "+" / "-" / "@" se interpretaría como fórmula al abrir el CSV
        // en Excel/Sheets si no se neutraliza con un `'` inicial.
        val csv = TaskCsvExporter.generateCsv(listOf(task(title = "=cmd|'/c calc'!A1")))

        assertTrue(csv.contains("\"'=cmd|'/c calc'!A1\""))
    }

    @Test
    fun generateCsv_includesPoints() {
        val csv = TaskCsvExporter.generateCsv(listOf(task(points = 42)))

        assertTrue(csv.contains(",42,"))
    }
}
