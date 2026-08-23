package org.taskhub

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import org.taskhub.platform.AndroidNotificationScheduler
import org.taskhub.platform.AndroidSchedulerHolder
import org.taskhub.platform.AndroidContextHolder
import org.taskhub.platform.DebugFlags
import org.taskhub.BuildConfig

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permissions result — notifications will work or skip */ }

    /**
     * Launcher para el flujo de actualización in-app (Play Core).
     *
     * El modo IMMEDIATE muestra un diálogo de pantalla completa que bloquea la
     * app hasta que la actualización se descarga e instala (o el usuario la
     * cancela). Si el usuario cancela, forzamos el cierre: no se debe seguir
     * ejecutando una versión obsoleta.
     */
    private val appUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_OK -> {
                // Aceptada: el sistema gestiona la descarga/instalación y reinicia la app.
            }
            result.resultCode == Activity.RESULT_CANCELED -> {
                // El usuario canceló la actualización — forzamos el cierre: no se debe
                // seguir ejecutando una versión obsoleta.
                Log.w(TAG, "In-App Update: cancelada por el usuario. Cerrando la app.")
                finish()
            }
            else -> {
                // Fallo de la actualización (p. ej. RESULT_IN_APP_UPDATE_FAILED) o
                // cualquier otro código: NO forzamos el cierre, para no dejar la app
                // en un bucle de cierre. Se reintentará en el siguiente arranque.
                Log.w(TAG, "In-App Update: fallo (resultCode=${result.resultCode}). Continuando.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Switch from splash theme to normal app theme before rendering
        setTheme(R.style.Theme_TaskHub)

        enableEdgeToEdge()

        // Hold a static reference to the app context for platform helpers
        AndroidContextHolder.context = applicationContext

        // Hold a reference to the current Activity (para mostrar el interstitial de AdMob)
        AndroidContextHolder.activity = this

        // Registrar el launcher de Google Sign-In (requisito para el login con Google)
        GoogleSignInHelper.register(this)

        // Registrar los launchers de galería/cámara para elegir foto de avatar
        ImagePickerHelper.register(this)

        // Set debug mode from BuildConfig (false in release builds)
        DebugFlags.isEnabled = BuildConfig.DEBUG

        // Initialize the notification scheduler
        AndroidSchedulerHolder.scheduler = AndroidNotificationScheduler(applicationContext)

        // Create FCM notification channel early
        NotificationHelper.createNotificationChannel(applicationContext)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Comprobar si hay una actualización disponible en Play Store.
        // Solo se dispara en builds instalados desde Play Store; un debug
        // sideload no reporta actualización (esperado) y NO bloquea la ejecución.
        checkForInAppUpdate()

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar la referencia a la Activity para evitar leaks
        AndroidContextHolder.activity = null
    }

    /**
     * Consulta [AppUpdateManager] y, si hay una actualización disponible que
     * permite el modo IMMEDIATE, lanza el flujo de actualización forzada.
     *
     * Si no hay actualización o falla la consulta, la app continúa con normalidad
     * (solo se registra en log).
     */
    private fun checkForInAppUpdate() {
        val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(this)

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    Log.i(TAG, "In-App Update: actualización inmediata disponible. Lanzando flujo.")
                    val started = appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        appUpdateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                    if (!started) {
                        Log.w(TAG, "In-App Update: no se pudo lanzar el flujo de actualización.")
                    }
                } else {
                    Log.i(TAG, "In-App Update: sin actualización disponible (o no permite IMMEDIATE).")
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "In-App Update: error al consultar appUpdateInfo", e)
            }
    }
}
