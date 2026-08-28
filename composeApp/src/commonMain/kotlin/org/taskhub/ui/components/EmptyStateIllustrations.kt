package org.taskhub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.taskhub.ui.theme.Coral300
import org.taskhub.ui.theme.Coral500
import org.taskhub.ui.theme.Teal200
import org.taskhub.ui.theme.Teal500
import org.taskhub.ui.theme.Teal800
import org.taskhub.ui.theme.semanticColors

/**
 * Ilustración geométrica para el estado vacío "sin tareas": un checkmark
 * grande con confeti alrededor, en los colores de marca + éxito.
 */
@Composable
fun EmptyTasksIllustration(modifier: Modifier = Modifier) {
    val successColor = MaterialTheme.semanticColors.success
    Canvas(modifier = modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = size.minDimension * 0.34f

        // Círculo de fondo
        drawCircle(color = Teal200.copy(alpha = 0.4f), radius = radius * 1.35f, center = center)

        // Anillo de éxito
        drawCircle(
            color = successColor,
            radius = radius,
            center = center,
            style = Stroke(width = size.minDimension * 0.07f)
        )

        // Checkmark
        val check = Path().apply {
            moveTo(w * 0.34f, h * 0.52f)
            lineTo(w * 0.46f, h * 0.64f)
            lineTo(w * 0.68f, h * 0.38f)
        }
        drawPath(
            path = check,
            color = successColor,
            style = Stroke(
                width = size.minDimension * 0.09f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Confeti: pequeños cuadrados/círculos dispersos alrededor del anillo
        val confetti = listOf(
            Triple(Offset(w * 0.12f, h * 0.18f), Coral500, false),
            Triple(Offset(w * 0.88f, h * 0.22f), Teal500, true),
            Triple(Offset(w * 0.82f, h * 0.82f), Coral300, false),
            Triple(Offset(w * 0.14f, h * 0.80f), Teal800, true),
            Triple(Offset(w * 0.90f, h * 0.55f), Coral500, true),
        )
        confetti.forEach { (offset, color, isCircle) ->
            if (isCircle) {
                drawCircle(color = color, radius = size.minDimension * 0.035f, center = offset)
            } else {
                val s = size.minDimension * 0.06f
                drawRect(
                    color = color,
                    topLeft = Offset(offset.x - s / 2f, offset.y - s / 2f),
                    size = androidx.compose.ui.geometry.Size(s, s)
                )
            }
        }
    }
}

/**
 * Ilustración geométrica para el estado vacío "sin hogares": una casita
 * sencilla en los colores de marca teal/coral.
 */
@Composable
fun EmptyHouseholdsIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(120.dp)) {
        val w = size.width
        val h = size.height

        // Círculo de fondo
        drawCircle(
            color = Teal200.copy(alpha = 0.4f),
            radius = size.minDimension * 0.46f,
            center = Offset(w / 2f, h / 2f)
        )

        // Tejado (triángulo)
        val roof = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.26f)
            lineTo(w * 0.78f, h * 0.52f)
            close()
        }
        drawPath(path = roof, color = Coral500)

        // Cuerpo de la casa
        drawRect(
            color = Teal800,
            topLeft = Offset(w * 0.30f, h * 0.52f),
            size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.28f)
        )

        // Puerta
        drawRect(
            color = Teal200,
            topLeft = Offset(w * 0.45f, h * 0.60f),
            size = androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.20f)
        )
    }
}
