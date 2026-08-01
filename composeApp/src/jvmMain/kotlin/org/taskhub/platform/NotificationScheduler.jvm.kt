package org.taskhub.platform

actual fun createNotificationScheduler(): NotificationScheduler = NoOpNotificationScheduler()