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
        // No se loguea el token en sí: es una credencial de targeting de push
        // (permite enviar notificaciones a este dispositivo concreto) y el
        // proyecto no tiene ninguna regla R8 que elimine android.util.Log en
        // release, así que quedaría en Logcat también en builds de producción.
        Log.d(TAG, "New FCM token received (length=${token.length})")
        // Antes solo se logueaba: el comentario decía "lo guarda la lógica
        // principal de la app", pero esa lógica no existía en ningún sitio
        // (verificado por grep) — ningún token llegaba nunca a persistirse,
        // así que las notificaciones push de "tarea asignada" no podían
        // funcionar. App.kt sube este valor a Firestore (users/{uid}) en el
        // arranque, una vez resuelta la identidad del usuario.
        org.taskhub.platform.AndroidSchedulerHolder.scheduler?.saveFcmToken(token)
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