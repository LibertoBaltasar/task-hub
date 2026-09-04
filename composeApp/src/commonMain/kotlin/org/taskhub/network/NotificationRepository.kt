package org.taskhub.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import org.taskhub.network.models.NotificationResponse

/**
 * Notificaciones de un hogar (subcolección `households/{id}/notifications`).
 * Extraído de [FirestoreRepository] (ver docs/refactor-arquitectura-2026-08-31.md,
 * punto 6, fase 2.1 — el dominio más aislado, sin dependencias de la capa de
 * puntos). Lógica movida tal cual, sin cambios de comportamiento.
 */
class NotificationRepository(
    private val baseUrl: String,
    private val firestoreClient: FirestoreClient
) {
    private val client = firestoreClient.client

    private suspend fun HttpRequestBuilder.withAuth() = with(firestoreClient) { withAuth() }
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() = with(firestoreClient) { tryAuthOrApiKey() }
    private fun HttpRequestBuilder.updateMaskFieldPaths(vararg fields: String) =
        with(firestoreClient) { updateMaskFieldPaths(*fields) }
    private fun extractDocId(resourceName: String, operation: String): String =
        firestoreClient.extractDocId(resourceName, operation)

    /** Create a notification document for a member. */
    suspend fun createNotification(
        householdId: String,
        memberId: String,
        taskId: String,
        title: String,
        message: String
    ): NotificationResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "memberId" to FirestoreValue(stringValue = memberId),
            "taskId" to FirestoreValue(stringValue = taskId),
            "title" to FirestoreValue(stringValue = title),
            "message" to FirestoreValue(stringValue = message),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "read" to FirestoreValue(booleanValue = false)
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/notifications"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createNotification")
        return NotificationResponse(id, memberId, taskId, title, message, now, read = false)
    }

    /** Get all notifications for a household. */
    suspend fun getNotifications(householdId: String): List<NotificationResponse> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/notifications"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc -> FirestoreParsers.toNotificationResponse(doc) }
    }

    /** Mark a notification as read. */
    suspend fun markNotificationRead(householdId: String, notificationId: String) {
        val fields = mapOf(
            "read" to FirestoreValue(booleanValue = true)
        )
        client.patch(
            "$baseUrl/households/$householdId/notifications/$notificationId"
        ) {
            withAuth()
            updateMaskFieldPaths("read")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }
}
