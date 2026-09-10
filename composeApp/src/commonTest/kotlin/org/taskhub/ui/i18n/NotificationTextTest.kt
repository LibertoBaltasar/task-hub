package org.taskhub.ui.i18n

import org.taskhub.network.models.NotificationResponse
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `NotificationText` es un objeto puro (sin I/O) marcado en el panel de
 * revisión 2026-09-06 como "trivial de testear, cero excusa" — hueco de
 * cobertura cerrado en el panel 2026-09-11.
 */
class NotificationTextTest {

    @Test
    fun `title usa titleKey traducido cuando existe`() {
        val notification = NotificationResponse(
            id = "n1", memberId = "m1", taskId = "t1",
            title = "legacy title", message = "legacy message",
            titleKey = "notification_task_assigned_title"
        )
        assertEquals("📋 Tarea asignada", NotificationText.title(notification, "es"))
        assertEquals("📋 Task assigned", NotificationText.title(notification, "en"))
    }

    @Test
    fun `title cae a texto legado cuando titleKey es null`() {
        val notification = NotificationResponse(
            id = "n1", memberId = "m1", taskId = "t1",
            title = "Título legado", message = "Mensaje legado"
        )
        assertEquals("Título legado", NotificationText.title(notification, "en"))
    }

    @Test
    fun `message anexa taskTitle al prefijo traducido`() {
        val notification = NotificationResponse(
            id = "n1", memberId = "m1", taskId = "t1",
            title = "legacy", message = "legacy",
            messageKey = "notification_task_assigned_body_prefix",
            messageParams = mapOf("taskTitle" to "Sacar la basura")
        )
        assertEquals(
            "Se te ha asignado: Sacar la basura",
            NotificationText.message(notification, "es")
        )
    }

    @Test
    fun `message sin taskTitle devuelve solo el texto traducido`() {
        val notification = NotificationResponse(
            id = "n1", memberId = "m1", taskId = "t1",
            title = "legacy", message = "legacy",
            messageKey = "notification_new_message_title"
        )
        assertEquals("💬 Nuevo mensaje", NotificationText.message(notification, "es"))
    }

    @Test
    fun `message cae a texto legado cuando messageKey es null`() {
        val notification = NotificationResponse(
            id = "n1", memberId = "m1", taskId = "t1",
            title = "legacy", message = "Ana: hola a todos"
        )
        assertEquals("Ana: hola a todos", NotificationText.message(notification, "es"))
    }
}
