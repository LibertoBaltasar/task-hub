package org.taskhub.platform

import androidx.compose.runtime.Composable

/**
 * Slot para el banner de anuncios.
 *
 * En Android renderiza el banner de AdMob SOLO si está habilitado (ver
 * `AdConfig.bannerEnabled` en el módulo Android). En iOS/JVM no renderiza nada.
 * De momento el banner queda preparado pero deshabilitado.
 */
@Composable
expect fun AdBannerSlot()
