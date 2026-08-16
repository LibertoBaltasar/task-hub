package org.taskhub.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner de AdMob renderizado dentro de Compose mediante [AndroidView].
 *
 * Usa el ID de banner de PRUEBA de [AdConfig]. El banner queda PREPARADO pero
 * solo se renderiza cuando [AdConfig.bannerEnabled] es `true` (ver el actual
 * `AdBannerSlot` en el paquete `org.taskhub.platform`).
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdConfig.BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
