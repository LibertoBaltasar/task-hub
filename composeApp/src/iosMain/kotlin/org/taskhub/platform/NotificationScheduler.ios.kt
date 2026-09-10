package org.taskhub.platform

/**
 * Implementación iOS del `expect` [createNotificationScheduler]
 * (`platform/NotificationScheduler.kt`).
 *
 * No hay integración de notificaciones locales/push (UNUserNotificationCenter,
 * APNs) en el target iOS todavía — devuelve [NoOpNotificationScheduler], igual
 * que en JVM/Desktop (ver `NotificationScheduler.jvm.kt`).
 */
actual fun createNotificationScheduler(): NotificationScheduler = NoOpNotificationScheduler()