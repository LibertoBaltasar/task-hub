package org.taskhub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.taskhub.platform.AndroidNotificationScheduler
import org.taskhub.platform.AndroidSchedulerHolder
import org.taskhub.platform.AndroidContextHolder
import org.taskhub.platform.DebugFlags
import org.taskhub.BuildConfig

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permissions result — notifications will work or skip */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Switch from splash theme to normal app theme before rendering
        setTheme(R.style.Theme_TaskHub)

        enableEdgeToEdge()

        // Hold a static reference to the app context for platform helpers
        AndroidContextHolder.context = applicationContext

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

        setContent {
            App()
        }
    }
}