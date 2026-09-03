package org.taskhub

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp

/**
 * Application principal de Task Hub.
 *
 * Firebase se inicializa automáticamente vía el plugin google-services
 * (FirebaseInitProvider). Crashlytics también se auto-inicializa y reporta
 * los crashes de forma automática.
 *
 * Mantenemos esta clase para controlar la inicialización y para un punto
 * central donde añadir futura configuración (p. ej. desactivar Crashlytics
 * en debug).
 */
class TaskHubApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Asegurar que FirebaseApp se inicialice (normalmente ya lo hace
        // FirebaseInitProvider antes de onCreate, pero es idempotente).
        FirebaseApp.initializeApp(this)

        // Task Hub es una app de uso familiar con perfiles infantiles ("Miembro"
        // con role="child", ver docs/privacy.html) — señalizar todo el
        // inventario de anuncios como dirigido a menores para que AdMob NO
        // sirva publicidad conductual/personalizada, con independencia de qué
        // perfil esté activo en el dispositivo (panel de revisión 2026-09-03,
        // Experto 10, CRÍTICO #2). Debe fijarse ANTES de MobileAds.initialize().
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .build()
        )

        // Inicializar el SDK de AdMob (Google Mobile Ads). Aquí no se carga
        // ningún anuncio; solo deja el SDK listo para el interstitial (tras
        // completar tarea) y el banner (preparado, deshabilitado de momento).
        MobileAds.initialize(this)
    }
}