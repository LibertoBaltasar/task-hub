package org.taskhub.platform

import androidx.compose.runtime.Composable

/**
 * Implementación iOS del `expect` [AdBannerSlot] (`platform/AdBannerSlot.kt`).
 *
 * No hay integración de AdMob (ni de ningún otro SDK de anuncios) en el
 * target iOS — igual que en JVM/Desktop (ver `AdBannerSlot.jvm.kt`). No
 * renderiza nada.
 */
@Composable
actual fun AdBannerSlot() {
    // No-op
}
