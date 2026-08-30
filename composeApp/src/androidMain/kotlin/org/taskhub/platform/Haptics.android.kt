package org.taskhub.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * VibrationEffect.createWaveform/createOneShot con amplitud requieren API 26
 * (minSdk de este proyecto), y ambos degradan solos a amplitud por defecto en
 * dispositivos sin control de amplitud — no hace falta comprobar
 * hasAmplitudeControl() a mano.
 */
actual fun vibrate(kind: HapticKind) {
    val vibrator = AndroidContextHolder.context
        ?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        ?: return
    if (!vibrator.hasVibrator()) return

    val effect = when (kind) {
        HapticKind.SUCCESS -> VibrationEffect.createWaveform(
            longArrayOf(0, 40, 60, 40), intArrayOf(0, 180, 0, 220), -1
        )
        HapticKind.ERROR -> VibrationEffect.createWaveform(
            longArrayOf(0, 50, 80, 50, 80, 50), intArrayOf(0, 255, 0, 255, 0, 255), -1
        )
        HapticKind.WARNING -> VibrationEffect.createWaveform(
            longArrayOf(0, 60, 100, 90), intArrayOf(0, 200, 0, 200), -1
        )
        HapticKind.LIGHT -> VibrationEffect.createOneShot(15, 100)
        HapticKind.MEDIUM -> VibrationEffect.createOneShot(30, 180)
        HapticKind.HEAVY -> VibrationEffect.createOneShot(50, 255)
        HapticKind.SELECTION -> VibrationEffect.createOneShot(10, 80)
    }
    try {
        vibrator.vibrate(effect)
    } catch (_: Exception) {
        // Best-effort — algunos OEMs lanzan en configuraciones no estándar
    }
}
