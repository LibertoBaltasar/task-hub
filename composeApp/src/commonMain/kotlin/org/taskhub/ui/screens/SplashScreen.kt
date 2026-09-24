/**
 * Pantalla inicial de la app: se muestra desde [org.taskhub.App] durante la
 * "Fase 1" (antes de inyectar el resto de dependencias vía Koin), mientras
 * dura la animación de 1.5s. No usa ScreenModel: lee el idioma directamente
 * de [org.taskhub.storage.SettingsStore] porque `LocalAppSettings` todavía
 * no está disponible en este punto del arranque.
 */
package org.taskhub.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.taskhub.ui.components.AppLogo
import org.taskhub.ui.components.shouldReduceMotion
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.theme.TaskHubTheme
import org.taskhub.ui.theme.TaskHubThemeType

/**
 * Splash screen que muestra "TASK HUB" en grande y negrita durante 1.5 segundos
 * antes de pasar a la pantalla principal.
 *
 * @param lang idioma activo (leído directamente de SettingsStore: este
 * composable se muestra antes de que LocalAppSettings esté disponible).
 * @param themeType tema visual activo (Default/Naturaleza/Minimal), leído
 * directamente de SettingsStore por el mismo motivo que [lang] — D9
 * (2026-09-24): el splash aplica su propio [TaskHubTheme] con este valor en
 * vez de una paleta Teal/Coral fija, para que se vea coherente con el resto
 * de la app independientemente del tema elegido.
 * @param onFinished callback que se invoca cuando terminan los 1.5 segundos
 */
@Composable
fun SplashScreen(lang: String, themeType: TaskHubThemeType = TaskHubThemeType.DEFAULT, onFinished: () -> Unit) {
    // Animación de fade-in (instantánea si el sistema pide reducir movimiento)
    var visible by remember { mutableStateOf(false) }
    val reduceMotion = shouldReduceMotion()
    // Stagger de 100ms: el logo entra primero y el texto justo después, para dar
    // sensación de secuencia en vez de bloque monolítico (informe delight §2.1).
    val logoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 800)
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 800,
            delayMillis = if (reduceMotion) 0 else 100
        )
    )

    // Al montar: activa el fade-in y programa el callback a los 1.5s
    LaunchedEffect(Unit) {
        visible = true
        delay(1500)
        onFinished()
    }

    TaskHubTheme(themeType = themeType) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppLogo(
                    modifier = Modifier.alpha(logoAlpha),
                    size = 72.dp,
                    ringColor = MaterialTheme.colorScheme.onPrimary,
                    checkColor = MaterialTheme.colorScheme.tertiaryContainer,
                    dotColor = MaterialTheme.colorScheme.tertiaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TASK",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.alpha(textAlpha)
                )
                Text(
                    text = "HUB",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.alpha(textAlpha)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = AppStrings.get("splash_subtitle", lang),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    // alpha=0.95 (no 0.8): blanco 80% sobre Teal800 daba 4.15:1, por debajo
                    // de WCAG AA para texto normal de 14sp — mismo margen aplicado aquí
                    // sobre onPrimary, que en todos los temas mantiene contraste AA con
                    // `primary` (ver Theme.kt).
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                    modifier = Modifier.alpha(textAlpha)
                )
            }
        }
    }
}
