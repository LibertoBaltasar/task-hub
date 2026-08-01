package org.taskhub.platform

/**
 * Platform-specific task notification scheduler.
 * On Android: schedules a WorkManager notification 1h before deadline.
 * On other platforms: no-op.
 */
interface NotificationScheduler {
    fun scheduleReminder(
        taskId: String,
        householdId: String,
        taskTitle: String,
        dueDateEpochMs: Long
    )

    fun cancelReminder(taskId: String)

    fun saveFcmToken(token: String)
}

/** No-op implementation for non-Android platforms. */
class NoOpNotificationScheduler : NotificationScheduler {
    override fun scheduleReminder(taskId: String, householdId: String, taskTitle: String, dueDateEpochMs: Long) {}
    override fun cancelReminder(taskId: String) {}
    override fun saveFcmToken(token: String) {}
}

expect fun createNotificationScheduler(): NotificationScheduler