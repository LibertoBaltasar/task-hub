/**
 * Capa REST de Firestore para notificaciones de un hogar. Consumida por el
 * sondeo periódico de la UI (badge de notificaciones) y por los repositorios
 * que las generan (asignaciones, mensajes de chat, etc.).
 */
package org.taskhub.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import org.taskhub.network.models.NotificationResponse
import org.taskhub.storage.TaskCache

/**
 * Notificaciones de un hogar (subcolección `households/{id}/notifications`).
 * Extraído de [FirestoreRepository] (ver docs/refactor-arquitectura-2026-08-31.md,
 * punto 6, fase 2.1 — el dominio más aislado, sin dependencias de la capa de
 * puntos). Lógica movida tal cual, sin cambios de comportamiento.
 */
class NotificationRepository(
    private val baseUrl: String,
    private val firestoreClient: FirestoreClient,
    private val taskCache: TaskCache
) {
    private val client = firestoreClient.client

    private suspend fun HttpRequestBuilder.withAuth() = with(firestoreClient) { withAuth() }
    private fun HttpRequestBuilder.updateMaskFieldPaths(vararg fields: String) =
        with(firestoreClient) { updateMaskFieldPaths(*fields) }
    private fun extractDocId(resourceName: String, operation: String): String =
        firestoreClient.extractDocId(resourceName, operation)

    /**
     * Crea un documento de notificación para un miembro. Requiere auth (escritura).
     *
     * [title]/[message] siguen guardándose YA traducidos al idioma de quien
     * ESCRIBE — se mantienen como fallback para notificaciones sin
     * [titleKey]/[messageKey] (datos antiguos) y para el propio caller, que
     * los sigue necesitando para casos con contenido de usuario no traducible
     * (p. ej. el cuerpo de un mensaje de chat). [titleKey]/[messageKey]
     * (claves de [org.taskhub.ui.i18n.AppStrings]) y [messageParams], cuando
     * se indican, permiten que el LECTOR vea el texto en SU propio idioma
     * (panel de notificaciones 2026-09-05, IMPORTANTE) — ver
     * `ui/i18n/NotificationText.kt`.
     */
    suspend fun createNotification(
        householdId: String,
        memberId: String,
        taskId: String,
        title: String,
        message: String,
        titleKey: String? = null,
        messageKey: String? = null,
        messageParams: Map<String, String>? = null,
        /** Ver KDoc de [NotificationResponse.authorMemberId]. */
        authorMemberId: String? = null
    ): NotificationResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = buildMap {
            put("memberId", FirestoreValue(stringValue = memberId))
            put("taskId", FirestoreValue(stringValue = taskId))
            put("title", FirestoreValue(stringValue = title))
            put("message", FirestoreValue(stringValue = message))
            put("createdAt", FirestoreValue(integerValue = now.toString()))
            put("read", FirestoreValue(booleanValue = false))
            if (titleKey != null) put("titleKey", FirestoreValue(stringValue = titleKey))
            if (messageKey != null) put("messageKey", FirestoreValue(stringValue = messageKey))
            if (messageParams != null) {
                put(
                    "messageParams",
                    FirestoreValue(
                        mapValue = FirestoreMapValue(
                            fields = messageParams.mapValues { FirestoreValue(stringValue = it.value) }
                        )
                    )
                )
            }
            if (authorMemberId != null) put("authorMemberId", FirestoreValue(stringValue = authorMemberId))
        }

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/notifications"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createNotification")
        taskCache.clearNotifications(householdId)
        return NotificationResponse(
            id, memberId, taskId, title, message, now, read = false,
            titleKey = titleKey, messageKey = messageKey, messageParams = messageParams,
            authorMemberId = authorMemberId
        )
    }

    /**
     * Lista todas las notificaciones de un hogar. Cache-first ante fallo
     * (ronda de deuda aplicable 2026-09-12, punto B11): antes usaba
     * `orDefault(emptyList())`, que vaciaba el badge/lista ante un fallo de
     * red puntual en vez de servir la última foto conocida.
     */
    suspend fun getNotifications(householdId: String): List<NotificationResponse> {
        return try {
            val response: FirestoreListResponse = client.get(
                "$baseUrl/households/$householdId/notifications"
            ) {
                withAuth()
            }.body()
            val notifications = response.documents.map { doc -> FirestoreParsers.toNotificationResponse(doc) }
            taskCache.cacheNotifications(householdId, notifications)
            notifications
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            taskCache.getCachedNotifications(householdId) ?: emptyList()
        }
    }

    /**
     * Marca una notificación como leída. Requiere auth (escritura).
     * Usa `updateMask` con solo el campo `read` — PATCH parcial: no sobreescribe
     * el resto de campos del documento (ver [FirestoreClient.updateMaskFieldPaths]).
     */
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
        taskCache.clearNotifications(householdId)
    }

    /**
     * Purga best-effort de notificaciones ya LEÍDAS con más de [maxAgeMillis]
     * de antigüedad, a partir de una lista ya obtenida con [getNotifications]
     * (evita un segundo fetch: el llamador de sondeo ya trae la colección
     * completa para decidir qué mostrar). `getNotifications` no tiene
     * filtro/`limit` y nunca se borraba nada, así que el coste de red de la
     * colección crecía sin límite con el tiempo (panel de notificaciones
     * 2026-09-05, IMPORTANTE). Nunca borra nada NO leído ni más reciente que
     * [maxAgeMillis]. Un fallo al borrar un documento concreto (red, permisos)
     * no interrumpe el resto — se reintenta en el siguiente ciclo de sondeo.
     */
    suspend fun purgeOldRead(householdId: String, all: List<NotificationResponse>, maxAgeMillis: Long) {
        val cutoff = Clock.System.now().toEpochMilliseconds() - maxAgeMillis
        val stale = all.filter { it.read && it.createdAt < cutoff }
        if (stale.isEmpty()) return
        for (n in stale) {
            try {
                client.delete("$baseUrl/households/$householdId/notifications/${n.id}") {
                    withAuth()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort, ver KDoc de la función.
            }
        }
        taskCache.clearNotifications(householdId)
    }
}
