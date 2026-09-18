/**
 * Toast no bloqueante de "logro desbloqueado" (delight fase 2, informe
 * `docs/revision-delight-experiencia-2026-09-18.md`, §2.8/§3). Se superpone
 * en la parte superior de la pantalla (NO un `AlertDialog`: no debe
 * interrumpir) y se autodescarta a los 2500ms. Sin confeti ni háptico propio
 * — apilar celebraciones con la de completar tarea viola la moderación
 * pedida por el dueño del producto.
 */
package org.taskhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.Achievement

/**
 * Muestra [achievement] superpuesto durante 2500ms y llama a [onDismissed]
 * al terminar (el llamador debe limpiar su estado a `null` en ese callback,
 * p.ej. `TaskScreenModel.clearNewlyUnlockedAchievement()`, para que no
 * reaparezca en una recomposición posterior).
 */
@Composable
fun AchievementToast(
    achievement: Achievement?,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = shouldReduceMotion()
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    var visible by remember { mutableStateOf(false) }
    // Se conserva el último logro no-nulo mientras dura la animación de
    // salida — `achievement` ya puede haber vuelto a null en ese momento.
    var displayedAchievement by remember { mutableStateOf<Achievement?>(null) }

    LaunchedEffect(achievement) {
        if (achievement != null) {
            displayedAchievement = achievement
            visible = true
            delay(2500)
            visible = false
            if (!reduceMotion) delay(200)
            onDismissed()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else
            slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(tween(300)),
        exit = if (reduceMotion) ExitTransition.None else
            slideOutVertically(animationSpec = tween(200)) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        val a = displayedAchievement
        if (a != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = a.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = s("achievement_unlocked_prefix"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = a.title(appSettings.currentLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
