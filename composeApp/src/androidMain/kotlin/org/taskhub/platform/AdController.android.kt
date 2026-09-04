package org.taskhub.platform

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.taskhub.ads.AdConfig

/**
 * Implementación Android del controlador de anuncios.
 *
 * Carga un [InterstitialAd] de test y lo muestra tras completar una tarea,
 * respetando un cooldown de [INTERSTITIAL_COOLDOWN_MS] entre impresiones para
 * no molestar al usuario. Al descartarse el anuncio, se recarga el siguiente.
 */
object AdControllerImpl : AdController {

    /** Cooldown entre impresiones de interstitial (120 segundos). */
    private const val INTERSTITIAL_COOLDOWN_MS = 120_000L

    /** Anuncio interstitial cargado y listo para mostrar (null si aún carga). */
    @Volatile
    private var interstitialAd: InterstitialAd? = null

    /** Timestamp (epoch ms) del último show. 0 = nunca mostrado. */
    @Volatile
    private var lastShownAtMs: Long = 0L

    /** Evita disparar cargas duplicadas mientras una ya está en curso. */
    @Volatile
    private var isLoading: Boolean = false

    init {
        loadInterstitial()
    }

    /** Carga un interstitial nuevo y lo deja listo para mostrar. */
    private fun loadInterstitial() {
        if (isLoading) return
        val context = AndroidContextHolder.context ?: return
        isLoading = true

        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                    // Al descartarse el anuncio, se recarga el siguiente. También
                    // hay que recargar si el anuncio falla al MOSTRARSE (Activity
                    // fuera de primer plano en ese instante, anuncio ya consumido,
                    // error del SDK): sin este callback, `interstitialAd` se queda
                    // apuntando a un anuncio inválido para siempre y
                    // maybeShowInterstitial() nunca vuelve a llamar a
                    // loadInterstitial() (la guarda `if (ad == null)` no se cumple).
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            interstitialAd = null
                            loadInterstitial()
                        }

                        override fun onAdShowedFullScreenContent() {
                            lastShownAtMs = System.currentTimeMillis()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Antes, si la primera carga fallaba (típico sin red al
                    // arrancar), interstitialAd se quedaba en null para siempre:
                    // ningún llamador reintentaba. maybeShowInterstitial() ahora
                    // reintenta la carga la próxima vez que se necesite.
                    isLoading = false
                    interstitialAd = null
                }
            }
        )
    }

    override fun maybeShowInterstitial() {
        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastShownAtMs < INTERSTITIAL_COOLDOWN_MS) return

        // Se necesita una Activity en primer plano para mostrar el anuncio
        val activity = AndroidContextHolder.activity ?: return

        // lastShownAtMs se fija en onAdShowedFullScreenContent(), no aquí: si
        // ad.show() falla (ver onAdFailedToShowFullScreenContent arriba), el
        // cooldown de 120s no debe arrancar para un anuncio que nunca se vio.
        ad.show(activity)
    }

    override fun isBannerEnabled(): Boolean = AdConfig.bannerEnabled

    /**
     * Actualiza la `RequestConfiguration` global de AdMob por sesión, según
     * el rol del perfil activo — plumbing para el hallazgo "señalización
     * AdMob por sesión" (panel de revisión 2026-09-03/04, Experto 10).
     *
     * DELIBERADAMENTE no relaja el TFCD a UNSPECIFIED/FALSE cuando
     * [isChildProfile] es `false`: `TaskHubApplication.onCreate` ya fija
     * `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` de forma FIJA y GLOBAL como fix
     * CRÍTICO explícitamente "con independencia de qué perfil esté activo en
     * el dispositivo" (mismo panel, mismo experto) — un dispositivo familiar
     * compartido puede tener un perfil admin activo un momento y uno "child"
     * al siguiente, y AdMob no permite variar el TFCD por `AdRequest`
     * individual, solo por sesión/config global. Bajar la señal cuando un
     * admin está activo reabriría exactamente el hueco que ese fix CRÍTICO
     * cerró. Esta función deja el plumbing listo (wiring desde el rol del
     * miembro activo) para si el producto decide en el futuro relajarlo con
     * más garantías (p.ej. un modo "solo admin" verificado); hoy reafirma
     * TRUE siempre, ignorando [isChildProfile] a propósito.
     */
    override fun updateChildDirectedSignal(isChildProfile: Boolean) {
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build()
        )
    }
}

actual fun createAdController(): AdController = AdControllerImpl
