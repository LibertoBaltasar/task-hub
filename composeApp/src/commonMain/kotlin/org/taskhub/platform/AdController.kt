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

    /**
     * Señaliza por SESIÓN si el perfil activo del dispositivo es un menor
     * (`role == "child"`), además de la señalización GLOBAL ya fijada al
     * arrancar la app (`TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` +
     * `MAX_AD_CONTENT_RATING_G` fijos, ver `TaskHubApplication`, que NUNCA se
     * relajan: la clasificación de contenido "G" es apta para cualquier
     * perfil, y el TFCD global sigue partiendo de TRUE por defecto — panel de
     * revisión 2026-09-03/04, Experto 10). Esta señal es un AJUSTE FINO por
     * encima de ese valor por defecto seguro, no un reemplazo: nunca debe
     * fijar explícitamente "no es tratamiento dirigido a menores" (AdMob
     * distingue TRUE/FALSE/UNSPECIFIED — solo se usa TRUE o UNSPECIFIED,
     * jamás FALSE, para no comprometerse a algo que no se puede verificar:
     * un dispositivo familiar compartido puede tener un perfil "child" activo
     * un momento y un perfil "admin" al siguiente).
     */
    fun updateChildDirectedSignal(isChildProfile: Boolean)
}

/** Implementación no-op para plataformas sin AdMob (iOS, JVM/Desktop). */
class NoOpAdController : AdController {
    override fun maybeShowInterstitial() {}
    override fun isBannerEnabled(): Boolean = false
    override fun updateChildDirectedSignal(isChildProfile: Boolean) {}
}

/** Crea el controlador de anuncios de la plataforma actual. */
expect fun createAdController(): AdController
