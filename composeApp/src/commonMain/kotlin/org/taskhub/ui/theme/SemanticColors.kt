package org.taskhub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta semántica (éxito / aviso / información), común a los 3 themes
 * (DEFAULT, NATURALEZA, MINIMAL) para que su significado sea reconocible
 * en cualquiera de ellos.
 *
 * Cada tono trae variante de superficie (para texto/iconos sobre fondos
 * neutros) y variante "container" (fondo de chip/badge) + su "on" con
 * contraste accesible, igual que los tonos nativos de Material3.
 */
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

val LightSemanticColors = SemanticColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color.White,
    successContainer = Color(0xFFC8E6C9),
    onSuccessContainer = Color(0xFF1B5E20),

    warning = Color(0xFFF9A825),
    onWarning = Color(0xFF3E2E00),
    warningContainer = Color(0xFFFFECB3),
    onWarningContainer = Color(0xFF7A5900),

    info = Color(0xFF1565C0),
    onInfo = Color.White,
    infoContainer = Color(0xFFBBDEFB),
    onInfoContainer = Color(0xFF0D47A1),
)

val DarkSemanticColors = SemanticColors(
    success = Color(0xFF81C784),
    // onSuccess oscurecido de 0xFF1B5E20 (3.91:1, por debajo del umbral AA de
    // 4.5:1) a 0xFF18521C (4.61:1) conservando el mismo tono verde — no se usa
    // hoy como texto en ningún sitio (solo *Container), pero queda accesible
    // por si se usa en el futuro (panel v7, #28).
    onSuccess = Color(0xFF18521C),
    // successContainer más oscuro que en v1 (era 0xFF2E7D32, 3.81:1 con
    // onSuccessContainer — por debajo del umbral WCAG AA de 4.5:1). 0xFF1B5E20
    // da 5.85:1.
    successContainer = Color(0xFF1B5E20),
    onSuccessContainer = Color(0xFFC8E6C9),

    warning = Color(0xFFFFD54F),
    onWarning = Color(0xFF3E2E00),
    warningContainer = Color(0xFF7A5900),
    onWarningContainer = Color(0xFFFFECB3),

    info = Color(0xFF64B5F6),
    // onInfo oscurecido de 0xFF0D47A1 (3.90:1, por debajo del umbral AA de
    // 4.5:1) a 0xFF0B3E8C (4.55:1) conservando el mismo tono azul — mismo
    // motivo que onSuccess arriba (panel v7, #28).
    onInfo = Color(0xFF0B3E8C),
    // infoContainer más oscuro que en v1 (era 0xFF1565C0, 4.09:1 con
    // onInfoContainer — por debajo del umbral WCAG AA de 4.5:1). 0xFF0D47A1
    // da 6.15:1.
    infoContainer = Color(0xFF0D47A1),
    onInfoContainer = Color(0xFFBBDEFB),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/**
 * Acceso a los colores semánticos vigentes, igual que `MaterialTheme.colorScheme`.
 * Uso: `MaterialTheme.semanticColors.warning`.
 */
val MaterialTheme.semanticColors: SemanticColors
    @Composable
    get() = LocalSemanticColors.current
