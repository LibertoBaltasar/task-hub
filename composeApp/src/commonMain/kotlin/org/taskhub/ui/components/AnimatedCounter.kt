/**
 * Contador numérico con animación de cambio de valor ("count-up" + pulso de
 * escala al subir). Ver §2.10 del informe de delight
 * (`docs/revision-delight-experiencia-2026-09-18.md`) — pieza de
 * infraestructura reutilizable, sin aplicar todavía a ningún call-site (eso
 * es la fase 03: `HouseholdMemberList`, `RankingScreen`, `TaskDetailScreen`,
 * `PublicProfileScreen`). No usar dentro de componentes genéricos como
 * `PointsBadge`/`StatChip` — ver razón en el informe.
 */
package org.taskhub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Texto de un número que anima su transición al cambiar [value]: cuenta de
 * forma progresiva (400ms, `FastOutSlowInEasing`) y, solo cuando [value]
 * **sube**, añade un pulso de escala 1→1.08→1 (spring
 * `DampingRatioMediumBouncy`) — bajar puntos (donar/canjear) no es un momento
 * a celebrar, así que no pulsa. Con `shouldReduceMotion()` activo, el valor
 * se muestra directo, sin count-up ni pulso.
 */
@Composable
fun AnimatedCounter(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    val reduceMotion = shouldReduceMotion()
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = if (reduceMotion) tween(0) else tween(400, easing = FastOutSlowInEasing),
        label = "animatedCounterValue"
    )

    val scale = remember { Animatable(1f) }
    var previousValue by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (!reduceMotion && value > previousValue) {
            val pulseSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
            scale.snapTo(1f)
            scale.animateTo(1.08f, animationSpec = pulseSpec)
            scale.animateTo(1f, animationSpec = pulseSpec)
        }
        previousValue = value
    }

    Text(
        text = "$animatedValue",
        style = style,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    )
}
