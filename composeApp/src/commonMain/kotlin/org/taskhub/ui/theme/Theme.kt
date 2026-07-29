package org.taskhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// === Marca Task Hub: paleta verde menta / teal con acentos cálidos ===

// Colores primarios: teal (verde azulado, fresco y moderno)
val Teal50 = Color(0xFFE0F7F4)
val Teal100 = Color(0xFFB2EBE2)
val Teal200 = Color(0xFF80D8CC)
val Teal300 = Color(0xFF4DC6B5)
val Teal400 = Color(0xFF26B6A3)
val Teal500 = Color(0xFF00A693)
val Teal600 = Color(0xFF009884)
val Teal700 = Color(0xFF008772)
val Teal800 = Color(0xFF007660)
val Teal900 = Color(0xFF005A48)

// Acento cálido: coral / naranja suave (para botones de acción, CTAs)
val Coral50 = Color(0xFFFFF0EC)
val Coral100 = Color(0xFFFFD8CF)
val Coral200 = Color(0xFFFFB8A8)
val Coral300 = Color(0xFFFF9580)
val Coral400 = Color(0xFFFF775D)
val Coral500 = Color(0xFFFF5C3A)
val Coral600 = Color(0xFFE64A2E)
val Coral700 = Color(0xFFB33A22)
val Coral800 = Color(0xFF802B18)
val Coral900 = Color(0xFF4D1A0E)

// Superficies
val Sand50 = Color(0xFFFEFCF8)
val Sand100 = Color(0xFFFDF6EE)

// Esquemas
private val LightColorScheme = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,

    secondary = Teal400,
    onSecondary = Color.White,
    secondaryContainer = Teal50,
    onSecondaryContainer = Teal700,

    tertiary = Coral500,
    onTertiary = Color.White,
    tertiaryContainer = Coral100,
    onTertiaryContainer = Coral800,

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Sand50,
    onBackground = Color(0xFF1C1B1F),
    surface = Sand50,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Sand100,
    onSurfaceVariant = Color(0xFF49454F),

    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
)

private val DarkColorScheme = darkColorScheme(
    primary = Teal300,
    onPrimary = Teal900,
    primaryContainer = Teal800,
    onPrimaryContainer = Teal100,

    secondary = Teal200,
    onSecondary = Teal900,
    secondaryContainer = Teal700,
    onSecondaryContainer = Teal50,

    tertiary = Coral300,
    onTertiary = Coral900,
    tertiaryContainer = Coral700,
    onTertiaryContainer = Coral100,

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2D2C2F),
    onSurfaceVariant = Color(0xFFCAC4D0),

    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

@Composable
fun TaskHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
