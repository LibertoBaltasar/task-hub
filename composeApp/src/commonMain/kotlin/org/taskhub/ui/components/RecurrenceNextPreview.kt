package org.taskhub.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.taskhub.network.RecurrenceRules
import org.taskhub.ui.i18n.AppStrings

/**
 * Preview de "próxima vez: X" junto al selector de recurrencia (Crear/Editar
 * tarea). Reutiliza [RecurrenceRules.nextOccurrence] (ya testeada) en vez de
 * dejar que el usuario adivine cuándo tocará la tarea según el clamp de fin
 * de mes o la rotación de días de la semana.
 *
 * No se muestra para "once" (no tiene "próxima vez", solo una fecha límite).
 */
@Composable
fun RecurrenceNextPreview(
    frequency: String,
    recurrenceDays: List<Int>,
    recurrenceDay: Int?,
    lang: String,
    modifier: Modifier = Modifier
) {
    if (frequency == "once") return

    // Coste acotado (O(1) salvo weekly, ≤14 iteraciones), pero se memoiza para
    // no recalcularlo en cada recomposición ajena (p.ej. al escribir en otro
    // campo del formulario) — mismo criterio de memoización aplicado al resto
    // de la pantalla en esta misma pasada (panel v4, UI/Componentes hallazgo
    // MENOR).
    val formattedDate = remember(frequency, recurrenceDays, recurrenceDay) {
        val tz = TimeZone.currentSystemDefault()
        val nextEpochMs = RecurrenceRules.nextOccurrence(
            nowEpochMs = Clock.System.now().toEpochMilliseconds(),
            frequency = frequency,
            day = if (frequency == "monthly") recurrenceDay else null,
            weeklyDays = if (frequency == "weekly") recurrenceDays else emptyList(),
            tz = tz
        )
        val date = Instant.fromEpochMilliseconds(nextEpochMs).toLocalDateTime(tz).date
        "${date.dayOfMonth.toString().padStart(2, '0')}/" +
            "${date.monthNumber.toString().padStart(2, '0')}/${date.year}"
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = AppStrings.get("recurrence_next_preview", lang).replace("%s", formattedDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
