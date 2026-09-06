/**
 * Programador de recordatorios de tareas, abstraído por plataforma
 * (expect/actual). En Android usa WorkManager + FCM; en iOS/JVM no hay
 * implementación aún y se usa [NoOpNotificationScheduler].
 */
package org.taskhub.platform

/**
 * Contrato del programador de notificaciones/recordatorios de tareas.
 * En Android programa una notificación vía WorkManager ~1h antes del
 * vencimiento y persiste el token FCM; en el resto de plataformas es no-op.
 */
interface NotificationScheduler {
    /** Programa (o reprograma, si ya existía) el recordatorio para la tarea [taskId]. */
    fun scheduleReminder(
        taskId: String,
        householdId: String,
        taskTitle: String,
        dueDateEpochMs: Long
    )

    /** Cancela el recordatorio pendiente de la tarea [taskId], si lo hubiera. */
    fun cancelReminder(taskId: String)

    /** Persiste el token FCM del dispositivo para poder recibir notificaciones push. */
    fun saveFcmToken(token: String)

    /** Token FCM persistido localmente, o null si aún no se ha recibido/no aplica (iOS/JVM). */
    fun getFcmToken(): String?
}

/** Implementación no-op para plataformas sin programador de notificaciones (iOS, JVM). */
class NoOpNotificationScheduler : NotificationScheduler {
    override fun scheduleReminder(taskId: String, householdId: String, taskTitle: String, dueDateEpochMs: Long) {}
    override fun cancelReminder(taskId: String) {}
    override fun saveFcmToken(token: String) {}
    override fun getFcmToken(): String? = null
}

/** Crea el programador de notificaciones de la plataforma actual. */
expect fun createNotificationScheduler(): NotificationScheduler