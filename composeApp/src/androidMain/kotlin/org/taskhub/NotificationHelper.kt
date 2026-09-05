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
import org.taskhub.ui.i18n.AppStrings

object NotificationHelper {
    const val CHANNEL_ID = "task_reminders"
    const val CHANNEL_NAME = "Recordatorios de tareas"
    private const val NOTIFICATION_TIMEOUT_MS = 60_000L

    /** Canal para tareas asignadas y mensajes nuevos (NotificationPollWorker). */
    const val CHANNEL_ID_UPDATES = "task_hub_updates"

    /**
     * True si se puede mostrar una notificación local ahora mismo — usado
     * también por [org.taskhub.NotificationPollWorker] para decidir SI
     * avanzar su estado de sondeo: si no hay permiso, el Worker no marca
     * nada como "ya notificado" y reintenta el hogar entero en el siguiente
     * ciclo en vez de perder la notificación para siempre en cuanto el
     * usuario reactive el permiso (panel de notificaciones 2026-09-05, QA).
     */
    fun canShowNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun createUpdatesChannel(context: Context, lang: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_UPDATES,
                AppStrings.get("notification_channel_updates_name", lang),
                // DEFAULT (no HIGH): un mensaje de chat o una tarea asignada no
                // son tan urgentes como un plazo a punto de vencer
                // (CHANNEL_ID, sí HIGH) — y el mismo evento "tarea asignada"
                // también puede llegar por FCM (TaskHubFirebaseMessagingService,
                // canal "fcm_general", DEFAULT) si algún día hay push real;
                // usar HIGH aquí sería inconsistente según el mecanismo de
                // entrega (panel de notificaciones 2026-09-05, Accesibilidad).
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = AppStrings.get("notification_channel_updates_desc", lang)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Muestra la notificación local del sistema para una notificación de
     * Firestore (tarea asignada o mensaje nuevo) ya vista por
     * `NotificationPollWorker`. [taskId] vacío es el centinela de "mensaje de
     * chat" (ver `HouseholdRepository.sendMessage`) — determina a qué
     * pantalla lleva el deep link al tocarla. Devuelve `true` si se mostró.
     */
    fun showUpdateNotification(
        context: Context,
        notificationDocId: String,
        title: String,
        message: String,
        householdId: String,
        taskId: String,
        lang: String
    ): Boolean {
        createUpdatesChannel(context, lang)

        if (!canShowNotifications(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("householdId", householdId)
            putExtra("taskId", taskId)
            // Permite a App.kt marcarla como leída al abrirla desde el deep
            // link (antes solo se marcaba al tocarla en la lista in-app —
            // panel de notificaciones 2026-09-05, UX).
            putExtra("notificationId", notificationDocId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationDocId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationDocId.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

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

        // Antes usaba `getLaunchIntentForPackage(...) ?: Intent(...).apply {
        // putExtra(...) }` — en la práctica `getLaunchIntentForPackage` casi
        // nunca devuelve null (la app está instalada), así que la rama con
        // los extras de deep link era código muerto: MainActivity nunca
        // recibía "householdId"/"taskId" al tocar este recordatorio (panel de
        // notificaciones 2026-09-05, gap B — mismo deep link que ahora sí
        // consume `MainActivity.consumeDeepLink`/`App.kt`).
        val intent = Intent(context, MainActivity::class.java).apply {
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
            .setContentTitle("\u23F0 Recordatorio de tarea")
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