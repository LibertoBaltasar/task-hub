package org.taskhub.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

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

private fun impact(style: UIImpactFeedbackStyle) {
    val generator = UIImpactFeedbackGenerator(style)
    generator.prepare()
    generator.impactOccurred()
}

private fun notify(type: UINotificationFeedbackType) {
    val generator = UINotificationFeedbackGenerator()
    generator.prepare()
    generator.notificationOccurred(type)
}
