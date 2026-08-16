package org.taskhub.platform

/**
 * Controlador de anuncios de la plataforma (AdMob en Android).
 *
 * Expone una API mínima para mostrar el interstitial tras completar una tarea y
 * para consultar si el banner está habilitado. En plataformas sin AdMob
 * (iOS/JVM) se usa [NoOpAdController], que no hace nada.
 */
interface AdController {

    /**
     * Muestra el interstitial si está cargado y ha pasado el cooldown desde el
     * último show. No-op si no hay anuncio listo o aún no toca.
     */
    fun maybeShowInterstitial()

    /** Indica si el banner está habilitado en esta plataforma. */
    fun isBannerEnabled(): Boolean
}

/** Implementación no-op para plataformas sin AdMob (iOS, JVM/Desktop). */
class NoOpAdController : AdController {
    override fun maybeShowInterstitial() {}
    override fun isBannerEnabled(): Boolean = false
}

/** Crea el controlador de anuncios de la plataforma actual. */
expect fun createAdController(): AdController
