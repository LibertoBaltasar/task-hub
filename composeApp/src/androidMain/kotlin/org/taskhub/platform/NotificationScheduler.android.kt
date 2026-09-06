/**
 * `actual` Android de [NotificationScheduler]: delega la programación real
 * en [TaskReminderScheduler] (WorkManager) y persiste el token FCM en
 * SharedPreferences.
 */
package org.taskhub.platform

import android.content.Context
import android.content.SharedPreferences
import org.taskhub.TaskReminderScheduler

/**
 * Implementación Android usando WorkManager para programar los recordatorios.
 * También persiste el token FCM en SharedPreferences (se sincroniza a
 * Firestore aparte cuando es posible).
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("task_hub_fcm", Context.MODE_PRIVATE)

    /** Delega en [TaskReminderScheduler], que programa el WorkManager correspondiente. */
    override fun scheduleReminder(
        taskId: String,
        householdId: String,
        taskTitle: String,
        dueDateEpochMs: Long
    ) {
        TaskReminderScheduler.scheduleReminder(
            context = context,
            taskId = taskId,
            householdId = householdId,
            taskTitle = taskTitle,
            dueDateEpochMs = dueDateEpochMs
        )
    }

    /** Delega en [TaskReminderScheduler] para cancelar el WorkManager pendiente. */
    override fun cancelReminder(taskId: String) {
        TaskReminderScheduler.cancelReminder(context, taskId)
    }

    /** Persiste el token FCM en SharedPreferences (clave "task_hub_fcm"/"fcm_token"). */
    override fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun getFcmToken(): String? = prefs.getString("fcm_token", null)
}

/**
 * Contenedor del singleton de [AndroidNotificationScheduler] — se
 * inicializa desde MainActivity.onCreate() (necesita un [Context] Android
 * que no está disponible al cargar la clase estáticamente).
 */
object AndroidSchedulerHolder {
    var scheduler: AndroidNotificationScheduler? = null
}

/**
 * Implementación Android: devuelve el scheduler ya inicializado desde
 * MainActivity, o [NoOpNotificationScheduler] si aún no se ha inicializado
 * (p.ej. si se invoca antes de onCreate).
 */
actual fun createNotificationScheduler(): NotificationScheduler {
    return AndroidSchedulerHolder.scheduler ?: NoOpNotificationScheduler()
}