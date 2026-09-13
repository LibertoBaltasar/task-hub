/**
 * `actual` web (wasmJs) de [createNotificationScheduler]: no hay
 * WorkManager/FCM en el navegador, así que devuelve [NoOpNotificationScheduler].
 */
package org.taskhub.platform

/** Web: sin programador de recordatorios — no-op. */
actual fun createNotificationScheduler(): NotificationScheduler = NoOpNotificationScheduler()
