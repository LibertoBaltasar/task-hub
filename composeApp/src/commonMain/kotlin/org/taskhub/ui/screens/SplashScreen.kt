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
import org.taskhub.ui.theme.Teal800
import org.taskhub.ui.theme.Coral100
import androidx.compose.ui.graphics.Color

/**
 * Splash screen que muestra "TASK HUB" en grande y negrita durante 5 segundos
 * antes de pasar a la pantalla principal.
 *
 * @param onFinished callback que se invoca cuando terminan los 5 segundos
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Animación de fade-in
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 800)
    )

    // Al montar: activa el fade-in y programa el callback a los 5s
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
                text = "Organiza tu espacio, comparte las tareas",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}