package org.taskhub

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Schedules a one-time notification to remind about a task deadline.
 * Uses WorkManager to reliably schedule at the exact time.
 *
 * Schedule is set for 1h before the deadline.
 * If the deadline is less than 1h away or in the past, no notification is scheduled.
 */
object TaskReminderScheduler {

    private const val WORK_NAME_PREFIX = "task_reminder_"
    private const val REMINDER_MINUTES_BEFORE = 60

    fun scheduleReminder(
        context: Context,
        taskId: String,
        householdId: String,
        taskTitle: String,
        dueDateEpochMs: Long
    ) {
        val now = System.currentTimeMillis()
        val reminderTime = dueDateEpochMs - (REMINDER_MINUTES_BEFORE * 60 * 1000L)

        if (reminderTime <= now) {
            Log.d("TaskReminderScheduler", "Deadline too soon or past, skipping reminder for $taskId")
            return
        }

        val delayMs = reminderTime - now
        val workName = "$WORK_NAME_PREFIX$taskId"

        val inputData = androidx.work.Data.Builder()
            .putString("taskTitle", taskTitle)
            .putString("taskId", taskId)
            .putString("householdId", householdId)
            .putInt("minutesBefore", REMINDER_MINUTES_BEFORE)
            .build()

        val constraints = Constraints.Builder()
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("task_reminder")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)

        Log.d("TaskReminderScheduler", "Scheduled reminder for $taskId at $reminderTime (in ${delayMs / 60000} min)")
    }

    fun cancelReminder(context: Context, taskId: String) {
        val workName = "$WORK_NAME_PREFIX$taskId"
        WorkManager.getInstance(context).cancelUniqueWork(workName)
        Log.d("TaskReminderScheduler", "Cancelled reminder for $taskId")
    }
}

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val taskTitle = inputData.getString("taskTitle") ?: "Tarea"
        val taskId = inputData.getString("taskId") ?: ""
        val householdId = inputData.getString("householdId") ?: ""
        val minutesBefore = inputData.getInt("minutesBefore", 60)

        NotificationHelper.showTaskReminder(
            applicationContext,
            taskTitle,
            taskId,
            householdId,
            minutesBefore
        )

        Log.d("ReminderWorker", "Fired reminder for $taskId")
        return Result.success()
    }
}