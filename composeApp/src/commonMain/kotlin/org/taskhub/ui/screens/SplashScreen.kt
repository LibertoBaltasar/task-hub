package org.taskhub.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import org.taskhub.ui.theme.Teal800
import org.taskhub.ui.theme.Coral100
import androidx.compose.ui.graphics.Color

/**
 * Splash screen que muestra "TASK HUB" en grande y negrita durante 1.5 segundos
 * antes de pasar a la pantalla principal.
 *
 * @param lang idioma activo (leído directamente de SettingsStore: este
 * composable se muestra antes de que LocalAppSettings esté disponible).
 * @param onFinished callback que se invoca cuando terminan los 1.5 segundos
 */
@Composable
fun SplashScreen(lang: String, onFinished: () -> Unit) {
    // Animación de fade-in (instantánea si el sistema pide reducir movimiento)
    var visible by remember { mutableStateOf(false) }
    val reduceMotion = shouldReduceMotion()
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 800)
    )

    // Al montar: activa el fade-in y programa el callback a los 1.5s
    LaunchedEffect(Unit) {
        visible = true
        delay(1500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Teal800),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo(
                modifier = Modifier.alpha(alpha),
                size = 72.dp,
                ringColor = Color.White,
                checkColor = Coral100,
                dotColor = Coral100
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "TASK",
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.alpha(alpha)
            )
            Text(
                text = "HUB",
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = Coral100,
                modifier = Modifier.alpha(alpha)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = AppStrings.get("splash_subtitle", lang),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}