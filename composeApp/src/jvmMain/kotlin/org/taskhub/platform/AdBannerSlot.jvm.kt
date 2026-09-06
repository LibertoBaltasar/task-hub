/**
 * `actual` JVM (desktop) de [AdBannerSlot]: no hay integración de anuncios
 * en desktop, así que no renderiza nada.
 */
package org.taskhub.platform

import androidx.compose.runtime.Composable

/** JVM/Desktop: sin banner de anuncios — no renderiza nada. */
@Composable
actual fun AdBannerSlot() {
    // No-op
}
