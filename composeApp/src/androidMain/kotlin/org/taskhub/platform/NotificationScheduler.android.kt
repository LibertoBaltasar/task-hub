package org.taskhub.platform

import android.content.Context
import android.content.SharedPreferences
import org.taskhub.TaskReminderScheduler

/**
 * Android implementation using WorkManager.
 * Also persists FCM token to SharedPreferences and will sync to Firestore when possible.
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("task_hub_fcm", Context.MODE_PRIVATE)

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

    override fun cancelReminder(taskId: String) {
        TaskReminderScheduler.cancelReminder(context, taskId)
    }

    override fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun getFcmToken(): String? = prefs.getString("fcm_token", null)
}

/** Singleton holder — initialized from MainActivity.onCreate(). */
object AndroidSchedulerHolder {
    var scheduler: AndroidNotificationScheduler? = null
}

actual fun createNotificationScheduler(): NotificationScheduler {
    return AndroidSchedulerHolder.scheduler ?: NoOpNotificationScheduler()
}