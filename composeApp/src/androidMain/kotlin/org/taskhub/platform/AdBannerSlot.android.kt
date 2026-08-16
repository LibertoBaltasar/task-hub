package org.taskhub.platform

import androidx.compose.runtime.Composable
import org.taskhub.ads.AdConfig
import org.taskhub.ads.BannerAd

/**
 * Slot de banner para Android.
 *
 * Renderiza [BannerAd] SOLO si [AdConfig.bannerEnabled] está activado.
 * De momento está deshabilitado (el código queda preparado).
 */
@Composable
actual fun AdBannerSlot() {
    if (AdConfig.bannerEnabled) {
        BannerAd()
    }
}
