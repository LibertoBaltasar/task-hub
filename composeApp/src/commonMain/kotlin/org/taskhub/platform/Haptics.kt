package org.taskhub.platform

/**
 * Tipos de feedback háptico. El código que dispara la vibración (ScreenModels,
 * en su mayoría, sin contexto @Composable) es responsable de comprobar
 * `SettingsStore.isVibrationEnabled()` antes de llamar a [vibrate].
 */
enum class HapticKind { SUCCESS, ERROR, WARNING, LIGHT, MEDIUM, HEAVY, SELECTION }

/** Dispara feedback háptico. No-op si la plataforma no soporta vibración o el dispositivo no tiene motor. */
expect fun vibrate(kind: HapticKind)
