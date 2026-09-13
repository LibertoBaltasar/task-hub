/**
 * `actual` web (wasmJs) de [vibrate]: los navegadores de escritorio no tienen
 * motor de vibración fiable (y la Vibration API no está disponible en todos),
 * así que es no-op.
 */
package org.taskhub.platform

// Web: sin motor de vibración — no-op
actual fun vibrate(kind: HapticKind) {
}
