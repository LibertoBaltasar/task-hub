package org.taskhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Theme enum ────────────────────────────────────────────

enum class TaskHubThemeType {
    DEFAULT,
    NATURALEZA,
    MINIMAL
}

// ── Colores base ──────────────────────────────────────────

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

// ── Naturaleza colors ─────────────────────────────────────

val Green50 = Color(0xFFE8F5E9)
val Green100 = Color(0xFFC8E6C9)
val Green200 = Color(0xFFA5D6A7)
val Green300 = Color(0xFF81C784)
val Green400 = Color(0xFF66BB6A)
val Green500 = Color(0xFF4CAF50)
val Green600 = Color(0xFF43A047)
val Green700 = Color(0xFF388E3C)
val Green800 = Color(0xFF2E7D32)
val Green900 = Color(0xFF1B5E20)

val Brown50 = Color(0xFFEFEBE9)
val Brown100 = Color(0xFFD7CCC8)
val Brown200 = Color(0xFFBCAAA4)
val Brown300 = Color(0xFFA1887F)
val Brown400 = Color(0xFF8D6E63)
val Brown500 = Color(0xFF795548)
val Brown600 = Color(0xFF6D4C41)
val Brown700 = Color(0xFF5D4037)
val Brown800 = Color(0xFF4E342E)
val Brown900 = Color(0xFF3E2723)

val Earth50 = Color(0xFFF9F5F0)
val Earth100 = Color(0xFFF0E6D8)

// ── Minimal colors ────────────────────────────────────────

val MonoWhite = Color(0xFFFFFFFF)
val MonoGray50 = Color(0xFFF5F5F5)
val MonoGray100 = Color(0xFFE0E0E0)
val MonoGray200 = Color(0xFFBDBDBD)
val MonoGray400 = Color(0xFF757575)
val MonoGray600 = Color(0xFF424242)
val MonoGray800 = Color(0xFF212121)
val MonoGray900 = Color(0xFF121212)
val MonoBlack = Color(0xFF000000)

// ── Default schemes ───────────────────────────────────────

private val DefaultLightColorScheme = lightColorScheme(
    primary = Teal800,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,

    secondary = Teal700,
    onSecondary = Color.White,
    secondaryContainer = Teal50,
    onSecondaryContainer = Teal700,

    tertiary = Coral700,
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

private val DefaultDarkColorScheme = darkColorScheme(
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

// ── Naturaleza schemes (verdes, marrones) ─────────────────

private val NaturalezaLightColorScheme = lightColorScheme(
    primary = Green800,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,

    secondary = Brown500,
    onSecondary = Color.White,
    secondaryContainer = Brown100,
    onSecondaryContainer = Brown900,

    tertiary = Green700,
    onTertiary = Color.White,
    tertiaryContainer = Green50,
    onTertiaryContainer = Green800,

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Earth50,
    onBackground = Brown900,
    surface = Earth50,
    onSurface = Brown900,
    surfaceVariant = Earth100,
    onSurfaceVariant = Brown700,

    outline = Color(0xFF8D6E63),
    outlineVariant = Color(0xFFBCAAA4),
)

private val NaturalezaDarkColorScheme = darkColorScheme(
    primary = Green300,
    onPrimary = Green900,
    primaryContainer = Green800,
    onPrimaryContainer = Green100,

    secondary = Brown300,
    onSecondary = Brown900,
    secondaryContainer = Brown700,
    onSecondaryContainer = Brown100,

    tertiary = Green200,
    onTertiary = Green900,
    tertiaryContainer = Green700,
    onTertiaryContainer = Green50,

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF1B1A18),
    onBackground = Color(0xFFE6E2DD),
    surface = Color(0xFF1B1A18),
    onSurface = Color(0xFFE6E2DD),
    surfaceVariant = Color(0xFF2D2A25),
    onSurfaceVariant = Color(0xFFCBC5BA),

    outline = Color(0xFF958F86),
    outlineVariant = Color(0xFF4A453D),
)

// ── Minimal schemes (blanco y negro) ──────────────────────

private val MinimalLightColorScheme = lightColorScheme(
    primary = MonoGray800,
    onPrimary = MonoWhite,
    primaryContainer = MonoGray100,
    onPrimaryContainer = MonoGray900,

    secondary = MonoGray600,
    onSecondary = MonoWhite,
    secondaryContainer = MonoGray50,
    onSecondaryContainer = MonoGray800,

    tertiary = MonoGray400,
    onTertiary = MonoWhite,
    tertiaryContainer = MonoGray50,
    onTertiaryContainer = MonoGray600,

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = MonoWhite,
    onBackground = MonoGray900,
    surface = MonoWhite,
    onSurface = MonoGray900,
    surfaceVariant = MonoGray50,
    onSurfaceVariant = MonoGray600,

    outline = MonoGray400,
    outlineVariant = MonoGray200,
)

private val MinimalDarkColorScheme = darkColorScheme(
    primary = MonoGray100,
    onPrimary = MonoGray900,
    primaryContainer = MonoGray600,
    onPrimaryContainer = MonoGray50,

    secondary = MonoGray200,
    onSecondary = MonoGray800,
    secondaryContainer = MonoGray600,
    onSecondaryContainer = MonoGray50,

    tertiary = MonoGray400,
    onTertiary = MonoWhite,
    tertiaryContainer = MonoGray600,
    onTertiaryContainer = MonoGray50,

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = MonoBlack,
    onBackground = MonoGray100,
    surface = MonoBlack,
    onSurface = MonoGray100,
    surfaceVariant = MonoGray800,
    onSurfaceVariant = MonoGray400,

    outline = MonoGray400,
    outlineVariant = MonoGray600,
)

// ── Tipografía ────────────────────────────────────────────

private val TaskHubTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

// ── Theme composable ──────────────────────────────────────

@Composable
fun TaskHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeType: TaskHubThemeType = TaskHubThemeType.DEFAULT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        TaskHubThemeType.DEFAULT -> if (darkTheme) DefaultDarkColorScheme else DefaultLightColorScheme
        TaskHubThemeType.NATURALEZA -> if (darkTheme) NaturalezaDarkColorScheme else NaturalezaLightColorScheme
        TaskHubThemeType.MINIMAL -> if (darkTheme) MinimalDarkColorScheme else MinimalLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TaskHubTypography,
        content = content
    )
}