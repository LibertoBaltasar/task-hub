/**
 * `actual` JVM (desktop) de [createNotificationScheduler]: no hay
 * WorkManager/FCM en desktop, así que devuelve [NoOpNotificationScheduler].
 */
package org.taskhub.platform

/** JVM/Desktop: sin programador de recordatorios — no-op. */
actual fun createNotificationScheduler(): NotificationScheduler = NoOpNotificationScheduler()