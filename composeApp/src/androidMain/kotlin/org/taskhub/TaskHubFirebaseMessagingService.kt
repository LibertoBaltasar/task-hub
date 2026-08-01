package org.taskhub

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TaskHubFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "TaskHubFCM"
        const val CHANNEL_ID = "fcm_general"
    }

    override fun onCreate() {
        super.onCreate()
        createFcmChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // Token is saved by the main app logic; FCM service just receives it.
        // The app can call a Firestore update to store it per member.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message from ${message.from}")

        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "Task Hub",
                body = notification.body ?: ""
            )
        }

        message.data.isNotEmpty().let {
            Log.d(TAG, "Message data: ${message.data}")
            // Handle data payload for custom actions
        }
    }

    private fun showNotification(title: String, body: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(1001, notification)
        } catch (_: SecurityException) {
            Log.w(TAG, "Notification permission not granted")
        }
    }

    private fun createFcmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notificaciones generales",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones push de Task Hub"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}