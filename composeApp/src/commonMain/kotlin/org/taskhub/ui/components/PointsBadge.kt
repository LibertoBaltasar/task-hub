package org.taskhub.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.taskhub.ui.theme.semanticColors

/** Tono visual de un [PointsBadge] (fondo/texto accesibles tomados del tema). */
enum class BadgeTone { Coral, Teal, Neutral, Success, Warning, Info, Error }

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
    val container: Color
    val content: Color
    when (tone) {
        BadgeTone.Coral -> {
            container = MaterialTheme.colorScheme.tertiary
            content = MaterialTheme.colorScheme.onTertiary
        }
        BadgeTone.Teal -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
        BadgeTone.Neutral -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        BadgeTone.Success -> {
            container = MaterialTheme.semanticColors.successContainer
            content = MaterialTheme.semanticColors.onSuccessContainer
        }
        BadgeTone.Warning -> {
            container = MaterialTheme.semanticColors.warningContainer
            content = MaterialTheme.semanticColors.onWarningContainer
        }
        BadgeTone.Info -> {
            container = MaterialTheme.semanticColors.infoContainer
            content = MaterialTheme.semanticColors.onInfoContainer
        }
        BadgeTone.Error -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }
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
