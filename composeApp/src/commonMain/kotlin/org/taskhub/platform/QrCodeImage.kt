package org.taskhub.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a QR code for the given [text] using Compose Canvas.
 *
 * Uses a pure-Kotlin QR encoder that works on all KMP targets.
 * The QR is drawn as black modules on a white background with a quiet zone border.
 */
@Composable
fun QrCodeImage(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    onError: @Composable (String) -> Unit = { err -> }
) {
    val matrix = remember(text) {
        try {
            QrEncoder.encode(text)
        } catch (e: Exception) {
            null
        }
    }

    if (matrix == null) {
        onError("Error al generar QR")
        return
    }

    val moduleCount = matrix.size
    val quietZone = 4  // modules of quiet zone on each side
    val totalModules = moduleCount + 2 * quietZone

    Canvas(
        modifier = modifier.size(size)
    ) {
        val moduleSize = size.toPx() / totalModules

        // White background
        drawRect(
            color = Color.White,
            topLeft = Offset.Zero,
            size = Size(size.toPx(), size.toPx())
        )

        // Draw modules
        for (r in matrix.indices) {
            for (c in matrix[r].indices) {
                if (matrix[r][c]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(
                            (quietZone + c) * moduleSize,
                            (quietZone + r) * moduleSize
                        ),
                        size = Size(moduleSize, moduleSize)
                    )
                }
            }
        }
    }
}