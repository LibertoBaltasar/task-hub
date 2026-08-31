package org.taskhub.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.taskhub.ui.theme.semanticColors

/** Tono visual de un [PointsBadge]/[StatChip] (fondo/texto accesibles tomados del tema). */
enum class BadgeTone { Coral, Teal, Neutral, Success, Warning, Info, Error }

/**
 * Resuelve el par (fondo, texto) accesible de un [BadgeTone]. Compartido por
 * [PointsBadge] y [StatChip] para no duplicar el mapeo tono → color del tema.
 */
@Composable
private fun badgeToneColors(tone: BadgeTone): Pair<Color, Color> = when (tone) {
    BadgeTone.Coral -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
    BadgeTone.Teal -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    BadgeTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    BadgeTone.Success -> MaterialTheme.semanticColors.successContainer to MaterialTheme.semanticColors.onSuccessContainer
    BadgeTone.Warning -> MaterialTheme.semanticColors.warningContainer to MaterialTheme.semanticColors.onWarningContainer
    BadgeTone.Info -> MaterialTheme.semanticColors.infoContainer to MaterialTheme.semanticColors.onInfoContainer
    BadgeTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

/**
 * Chip reutilizable para puntos, urgencia y costes.
 *
 * Reemplaza los antiguos `Surface(color = Coral500)` + texto blanco que fallaban contraste
 * WCAG (blanco sobre #FF5C3A = 3.07:1). Usa colores del tema ya accesibles:
 * - [BadgeTone.Coral]: `tertiary`/`onTertiary` (coral oscuro + blanco, 5.92:1).
 * - [BadgeTone.Teal]: `primaryContainer`/`onPrimaryContainer`.
 * - [BadgeTone.Neutral]: `surfaceVariant`/`onSurfaceVariant`.
 * - [BadgeTone.Success]/[BadgeTone.Warning]/[BadgeTone.Info]: paleta semántica
 *   (`MaterialTheme.semanticColors`), coherente en los 3 themes.
 *
 * @param text  Texto corto del badge (p. ej. "10 pts").
 * @param tone  Tono visual; [BadgeTone.Coral] por defecto (puntos/urgencia).
 */
@Composable
fun PointsBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Coral,
) {
    val (container, content) = badgeToneColors(tone)
    Surface(shape = MaterialTheme.shapes.small, color = container, modifier = modifier) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Chip de estadística reutilizable: unifica los antiguos `InfoBadge`
 * (`TaskDetailScreen`, label+value con fondo tintado) y `StatItem`
 * (`StatsScreen`, emoji+value+label sin fondo) en un único componente
 * compartido, reutilizando [BadgeTone] (igual mapeo de color que [PointsBadge]).
 *
 * - [tone] = null: sin superficie/fondo (equivalente exacto al antiguo
 *   `StatItem` — mismo aspecto visual, solo cambia dónde vive el código).
 * - [tone] != null: chip con superficie tintada según [BadgeTone] (mismos
 *   pares container/onContainer ya auditados por WCAG que usa [PointsBadge]).
 *   Sustituye a `InfoBadge`, que usaba `color.copy(alpha = 0.15f)` sin
 *   auditar contraste — `BadgeTone.Teal` es el reemplazo visualmente más
 *   cercano a su único uso real (`color = colorScheme.primary`).
 *
 * @param value texto principal (obligatorio).
 * @param label texto secundario opcional, debajo de [value].
 * @param emoji emoji opcional, encima de [value] (uso de `StatItem`).
 */
@Composable
fun StatChip(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    emoji: String? = null,
    tone: BadgeTone? = BadgeTone.Coral
) {
    if (tone == null) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (emoji != null) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val (container, content) = badgeToneColors(tone)
    Surface(shape = MaterialTheme.shapes.small, color = container, modifier = modifier) {
        if (label == null) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (emoji != null) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge, color = content)
                }
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content)
                Text(label, style = MaterialTheme.typography.labelSmall, color = content)
            }
        }
    }
}
