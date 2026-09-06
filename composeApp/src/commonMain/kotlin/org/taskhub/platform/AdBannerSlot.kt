/**
 * Slot composable del banner de anuncios, abstraído por plataforma (patrón
 * expect/actual de KMP). Vive en `platform/` junto al resto de puentes hacia
 * APIs nativas (anuncios, analytics, notificaciones, háptica...).
 */
package org.taskhub.platform

import androidx.compose.runtime.Composable

/**
 * Contrato: renderiza (o no) el banner de anuncios de la plataforma actual;
 * cada `actual` decide si dibuja algo. En Android renderiza el banner de
 * AdMob SOLO si está habilitado (ver `AdConfig.bannerEnabled` en el módulo
 * Android). En iOS/JVM no renderiza nada. De momento el banner queda
 * preparado pero deshabilitado.
 */
@Composable
expect fun AdBannerSlot()
