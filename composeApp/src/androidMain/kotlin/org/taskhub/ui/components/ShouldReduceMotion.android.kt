// Implementación Android de `shouldReduceMotion` (expect/actual multiplatform):
// respeta la preferencia de accesibilidad del sistema para desactivar animaciones.
package org.taskhub.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Android expone la preferencia de accesibilidad "eliminar animaciones"
 * como la escala de duración del animador del sistema (Ajustes >
 * Accesibilidad > Eliminar animaciones fija esta escala a 0).
 *
 * Se relee en cada ON_RESUME (no solo una vez con `remember`): el usuario
 * puede activar esa preferencia en Ajustes del sistema mientras la app
 * sigue en segundo plano, y al volver esperamos que el shimmer/las
 * animaciones respeten el cambio sin necesitar recrear la Activity.
 */
@Composable
actual fun shouldReduceMotion(): Boolean {
    val context = LocalContext.current
    fun readSystemSetting() = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    var reduceMotion by remember { mutableStateOf(readSystemSetting()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reduceMotion = readSystemSetting()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduceMotion
}
