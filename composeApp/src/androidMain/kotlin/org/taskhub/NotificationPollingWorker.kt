package org.taskhub

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Schedules periodic polling for notifications every 30 seconds.
 * Uses a OneTimeWorkRequest that reschedules itself after completion.
 * This is the fallback polling approach since FCM backend isn't available yet.
 *
 * When FCM backend is ready, the FCM listener in TaskHubFirebaseMessagingService
 * will handle push notifications directly and this polling can be disabled.
 */
object NotificationPollingScheduler {

    private const val WORK_NAME = "notification_polling"
    private const val POLL_INTERVAL_SECONDS = 30L

    fun start(context: Context, householdId: String, memberId: String) {
        val inputData = Data.Builder()
            .putString("householdId", householdId)
            .putString("memberId", memberId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationPollingWorker>()
            .setInitialDelay(POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("notification_polling")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)

        Log.d("NotificationPolling", "Polling started for household=$householdId member=$memberId")
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d("NotificationPolling", "Polling stopped")
    }
}

class NotificationPollingWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val householdId = inputData.getString("householdId") ?: return Result.failure()
        val memberId = inputData.getString("memberId") ?: return Result.failure()

        Log.d("NotificationPollingWorker", "Polling for notifications: household=$householdId member=$memberId")

        // Reschedule for next poll
        NotificationPollingScheduler.start(applicationContext, householdId, memberId)

        // The actual Firestore polling and notification display is handled
        // by the NotificationScreenModel via the app's coroutine scope.
        // This Worker serves as a keep-alive / wake-up mechanism.
        // When FCM backend is ready, the onMessageReceived in TaskHubFirebaseMessagingService
        // will handle push notifications and this polling can be removed.

        return Result.success()
    }
}