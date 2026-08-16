package org.taskhub.ads

/**
 * Configuración central de AdMob para Task Hub.
 *
 * Todos los IDs son los IDs de PRUEBA oficiales de Google. Sustituirlos por los
 * IDs de producción antes de publicar la app.
 *
 * El banner queda PREPARADO pero DESHABILITADO de momento (ver [bannerEnabled]).
 */
object AdConfig {

    /** ID de aplicación de AdMob (test). */
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"

    /** ID de la unidad de anuncio interstitial (test). */
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    /** ID de la unidad de anuncio de banner (test). */
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    /**
     * Controla si el banner se muestra en pantalla.
     *
     * `false` de momento: el banner queda preparado (todo el código listo) pero
     * deshabilitado. Poner a `true` para activarlo.
     */
    @Volatile
    var bannerEnabled: Boolean = false
}
