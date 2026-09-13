/**
 * `actual` web (wasmJs) de [AdBannerSlot]: no hay integración de anuncios en
 * el build web, así que no renderiza nada.
 */
package org.taskhub.platform

import androidx.compose.runtime.Composable

/** Web: sin banner de anuncios — no renderiza nada. */
@Composable
actual fun AdBannerSlot() {
    // No-op
}
