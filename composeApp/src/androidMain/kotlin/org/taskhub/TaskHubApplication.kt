package org.taskhub

import android.app.Application
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
    }
}