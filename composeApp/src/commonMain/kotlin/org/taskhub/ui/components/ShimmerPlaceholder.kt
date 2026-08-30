package org.taskhub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * Brush de gradiente animado que se desplaza en diagonal, para simular
 * el efecto "shimmer" sobre un placeholder mientras carga contenido real.
 *
 * Respeta la preferencia de "reducir movimiento" del sistema: en ese caso
 * devuelve un color sólido en vez de animar el barrido indefinidamente (era
 * la única animación decorativa de la app que no consultaba esta señal).
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val baseColor = colorScheme.surfaceVariant
    if (shouldReduceMotion()) {
        return SolidColor(baseColor)
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val highlightColor = colorScheme.surfaceVariant.copy(alpha = 0.4f)
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 500f, 0f),
        end = Offset(translateAnim, 300f)
    )
}

/**
 * Placeholder rectangular con animación shimmer, para sustituir contenido
 * (cards, filas, texto) mientras se carga desde red.
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 80.dp,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush = brush, shape = shape)
    ) {}
}

/**
 * Lista de [ShimmerPlaceholder] apilados, para simular varias filas/cards
 * fantasma mientras carga una pantalla completa.
 */
@Composable
fun ShimmerList(
    modifier: Modifier = Modifier,
    count: Int = 4,
    itemHeight: androidx.compose.ui.unit.Dp = 80.dp,
    spacing: androidx.compose.ui.unit.Dp = 12.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        repeat(count) {
            ShimmerPlaceholder(height = itemHeight)
        }
    }
}
