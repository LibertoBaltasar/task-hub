/**
 * Gating común de "Modo simple" (decisión del dueño, 2026-09-18, ver
 * `docs/delight-fase0-motion-core-2026-09-18.md`): un interruptor maestro
 * (`SettingsStore.isSimpleModeEnabled`) que desactiva de golpe animaciones,
 * efectos visuales y háptica, más 3 interruptores de categoría independientes
 * para quien quiera desactivar solo una parte. Los efectos NUEVOS añadidos en
 * las fases 01-04 del informe de delight deben consultar [effectsEnabled]
 * (desde un `@Composable`) en vez de leer `SettingsStore`/`shouldReduceMotion`
 * por separado, para no tener que repetir la fórmula de combinación en cada
 * call-site.
 */
package org.taskhub.ui.components

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.taskhub.storage.SettingsStore

/** Categoría de efecto a la que se le pregunta si está habilitada (ver [effectsEnabled]). */
enum class EffectCategory { ANIMATIONS, EFFECTS, HAPTICS }

/**
 * `true` si el efecto de [category] debe reproducirse ahora mismo.
 *
 * Fórmula: `!simpleMode && <interruptor de la categoría> && !shouldReduceMotion()`
 * para ANIMATIONS/EFFECTS (movimiento decorativo); para HAPTICS no se
 * consulta `shouldReduceMotion` (reducir movimiento no implica desactivar
 * vibración) sino el ajuste de vibración ya existente
 * (`SettingsStore.isVibrationEnabled`), que se mantiene como interruptor
 * independiente además del nuevo `fx_haptics`.
 *
 * Para código sin contexto `@Composable` (los `buzz()` de los ScreenModels),
 * usar [hapticsEnabled] directamente.
 */
@Composable
fun effectsEnabled(category: EffectCategory): Boolean {
    val settingsStore = koinInject<SettingsStore>()
    if (settingsStore.isSimpleModeEnabled()) return false
    return when (category) {
        EffectCategory.ANIMATIONS -> settingsStore.isFxAnimationsEnabled() && !shouldReduceMotion()
        EffectCategory.EFFECTS -> settingsStore.isFxEffectsEnabled() && !shouldReduceMotion()
        EffectCategory.HAPTICS -> hapticsEnabled(settingsStore)
    }
}

/**
 * Variante sin `@Composable` de la categoría HAPTICS de [effectsEnabled], para
 * usarla desde los `buzz()` de los ScreenModels (que solo tienen acceso a
 * [SettingsStore], inyectado por constructor, no a `shouldReduceMotion`).
 */
fun hapticsEnabled(settingsStore: SettingsStore): Boolean =
    !settingsStore.isSimpleModeEnabled() && settingsStore.isFxHapticsEnabled() && settingsStore.isVibrationEnabled()
