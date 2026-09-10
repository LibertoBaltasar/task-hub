package org.taskhub.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * Implementación iOS del `expect` [vibrate] (`platform/Haptics.kt`) usando los
 * generadores de feedback háptico de UIKit (`UIFeedbackGenerator`), a
 * diferencia de Android que usa `Vibrator`/`VibrationEffect` (patrones de
 * ondas manuales). En iOS no se componen patrones a mano: se delega en los
 * estilos "de sistema" que UIKit ya calibra por hardware (Taptic Engine).
 *
 * [HapticKind.SUCCESS]/[HapticKind.ERROR]/[HapticKind.WARNING] usan
 * [UINotificationFeedbackGenerator] (feedback semántico de resultado);
 * el resto usa [UIImpactFeedbackGenerator] con la intensidad correspondiente.
 * [HapticKind.SELECTION] no tiene un estilo de impacto "selección" directo en
 * UIKit, así que se aproxima con el estilo Soft (el más suave disponible).
 */
actual fun vibrate(kind: HapticKind) {
    when (kind) {
        HapticKind.SUCCESS -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        HapticKind.ERROR -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeError)
        HapticKind.WARNING -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
        HapticKind.LIGHT -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
        HapticKind.MEDIUM -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
        HapticKind.HEAVY -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
        HapticKind.SELECTION -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft)
    }
}

/**
 * Dispara un [UIImpactFeedbackGenerator] con el [style] dado. `prepare()` se
 * llama justo antes de `impactOccurred()` para minimizar la latencia del
 * Taptic Engine (recomendación de Apple: preparar el generador poco antes de
 * usarlo, no mantenerlo preparado indefinidamente).
 */
private fun impact(style: UIImpactFeedbackStyle) {
    val generator = UIImpactFeedbackGenerator(style)
    generator.prepare()
    generator.impactOccurred()
}

/** Dispara un [UINotificationFeedbackGenerator] con el [type] dado (mismo motivo de `prepare()` que [impact]). */
private fun notify(type: UINotificationFeedbackType) {
    val generator = UINotificationFeedbackGenerator()
    generator.prepare()
    generator.notificationOccurred(type)
}
