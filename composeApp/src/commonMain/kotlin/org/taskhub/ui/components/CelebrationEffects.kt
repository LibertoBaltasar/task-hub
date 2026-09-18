/**
 * Lenguaje visual compartido de "celebración de éxito" de la app: check
 * animado con rebote + confeti mínimo dibujado a mano (sin librerías).
 * Extraído de `TaskListScreen.TaskCard` (fase 00 del informe de delight,
 * 2026-09-18) para poder reutilizarlo en otros momentos de éxito
 * (`TaskDetailScreen`, `MemberRewardScreen`, fases 02+) sin duplicar código.
 * Firma y comportamiento idénticos a la versión original.
 */
package org.taskhub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.random.Random

// ────────────────────────────────────────────────────────────
//  AnimatedCheckmark – ✅ con bounce al completar una tarea
// ────────────────────────────────────────────────────────────

@Composable
fun AnimatedCheckmark(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }
    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkBounce"
    )
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "✅",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

// ────────────────────────────────────────────────────────────
//  ConfettiOverlay – partículas mínimas al completar (sin librerías)
// ────────────────────────────────────────────────────────────

private data class ConfettiParticle(
    val startX: Float,
    val colorIndex: Int,
    val fallDelay: Float,
    val horizontalDrift: Float,
    val rotationSpeed: Float
)

@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier, particleCount: Int = 12) {
    val particles = remember(particleCount) {
        List(particleCount) {
            ConfettiParticle(
                startX = Random.nextFloat(),
                colorIndex = Random.nextInt(4),
                fallDelay = Random.nextFloat() * 0.2f,
                horizontalDrift = (Random.nextFloat() - 0.5f) * 0.5f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 540f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1000, easing = LinearEasing))
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )

    Canvas(modifier = modifier) {
        val particleSize = 6.dp.toPx()
        particles.forEach { particle ->
            val t = ((progress.value - particle.fallDelay) / (1f - particle.fallDelay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val x = (particle.startX + particle.horizontalDrift * t) * size.width
            val y = t * size.height
            val alpha = 1f - t
            rotate(degrees = particle.rotationSpeed * t, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[particle.colorIndex].copy(alpha = alpha),
                    topLeft = Offset(x - particleSize / 2f, y - particleSize / 2f),
                    size = Size(particleSize, particleSize)
                )
            }
        }
    }
}
