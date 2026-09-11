package org.taskhub.network

import org.taskhub.network.models.NotificationResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [NotificationPollRules] decide qué notificaciones dispara
 * `NotificationPollWorker` (androidMain, WorkManager) como push local en
 * cada ciclo de sondeo — extraída a función pura por el mismo motivo que
 * [AssignmentCompletionRulesTest]: sin esto, ningún test es posible sin un
 * dispositivo/emulador Android (ronda de deuda aplicable 2026-09-12, punto
 * C17).
 */
class NotificationPollRulesTest {

    private fun notification(
        id: String,
        createdAt: Long,
        read: Boolean = false
    ) = NotificationResponse(
        id = id,
        memberId = "member-1",
        taskId = "task-1",
        title = "Título",
        message = "Mensaje",
        createdAt = createdAt,
        read = read
    )

    /** Una notificación nunca antes vista (no está en seenIds) y sin leer es candidata. */
    @Test
    fun selectNewNotifications_includesUnseenUnread() {
        val mine = listOf(notification(id = "n1", createdAt = 100))

        val result = NotificationPollRules.selectNewNotifications(mine, seenIds = emptySet())

        assertEquals(listOf("n1"), result.map { it.id })
    }

    /** Una notificación ya notificada en un ciclo anterior (en seenIds) no se repite. */
    @Test
    fun selectNewNotifications_excludesAlreadySeen() {
        val mine = listOf(notification(id = "n1", createdAt = 100))

        val result = NotificationPollRules.selectNewNotifications(mine, seenIds = setOf("n1"))

        assertTrue(result.isEmpty())
    }

    /** Una notificación ya marcada leída desde la UI (antes de este sondeo) no dispara push, aunque no esté en seenIds. */
    @Test
    fun selectNewNotifications_excludesAlreadyRead() {
        val mine = listOf(notification(id = "n1", createdAt = 100, read = true))

        val result = NotificationPollRules.selectNewNotifications(mine, seenIds = emptySet())

        assertTrue(result.isEmpty())
    }

    /** El resultado se ordena por fecha de creación ascendente (más antigua primero), no por el orden de entrada. */
    @Test
    fun selectNewNotifications_sortsByCreatedAtAscending() {
        val mine = listOf(
            notification(id = "newest", createdAt = 300),
            notification(id = "oldest", createdAt = 100),
            notification(id = "middle", createdAt = 200)
        )

        val result = NotificationPollRules.selectNewNotifications(mine, seenIds = emptySet())

        assertEquals(listOf("oldest", "middle", "newest"), result.map { it.id })
    }

    /** Caso mixto: solo las candidatas reales (ni vistas ni leídas) sobreviven, en orden. */
    @Test
    fun selectNewNotifications_mixedCase_onlyReturnsGenuineCandidates() {
        val mine = listOf(
            notification(id = "seen", createdAt = 100),
            notification(id = "read", createdAt = 200, read = true),
            notification(id = "new-1", createdAt = 300),
            notification(id = "new-2", createdAt = 250)
        )

        val result = NotificationPollRules.selectNewNotifications(mine, seenIds = setOf("seen"))

        assertEquals(listOf("new-2", "new-1"), result.map { it.id })
    }

    /** Caso límite defensivo: sin notificaciones propias, no hay candidatas. */
    @Test
    fun selectNewNotifications_withNoMine_returnsEmpty() {
        assertTrue(NotificationPollRules.selectNewNotifications(emptyList(), seenIds = emptySet()).isEmpty())
    }
}
