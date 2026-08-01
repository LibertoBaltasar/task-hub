package org.taskhub

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    const val CHANNEL_ID = "task_reminders"
    const val CHANNEL_NAME = "Recordatorios de tareas"
    private const val NOTIFICATION_TIMEOUT_MS = 60_000L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para recordar tareas pendientes"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showTaskReminder(
        context: Context,
        taskTitle: String,
        taskId: String,
        householdId: String,
        minutesBefore: Int = 60
    ) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return // Permission not granted — skip notification
            }
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("householdId", householdId)
                putExtra("taskId", taskId)
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ Recordatorio de tarea")
            .setContentText("\"$taskTitle\" vence en $minutesBefore minuto(s)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }
}