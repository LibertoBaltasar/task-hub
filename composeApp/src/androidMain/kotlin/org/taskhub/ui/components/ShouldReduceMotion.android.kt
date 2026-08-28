package org.taskhub.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android expone la preferencia de accesibilidad "eliminar animaciones"
 * como la escala de duración del animador del sistema (Ajustes >
 * Accesibilidad > Eliminar animaciones fija esta escala a 0).
 */
@Composable
actual fun shouldReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
