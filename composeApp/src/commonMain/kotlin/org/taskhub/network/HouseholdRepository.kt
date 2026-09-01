package org.taskhub.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MessageResponse
import org.taskhub.platform.secureRandomInt
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SavedHousehold
import org.taskhub.storage.TaskCache

/**
 * Hogares (colección `households`, subcolección `messages`) e invites.
 * Extraído de [FirestoreRepository] (ver docs/refactor-arquitectura-2026-08-31.md,
 * punto 6, fase 2.4). Lógica movida tal cual, sin cambios de comportamiento.
 *
 * NO incluye `deleteHousehold`/`leaveHousehold` ni `isMember`/`isCurrentUserMember`:
 * los dos primeros invalidan `currentMemberCache` (estado del dominio Member,
 * ver `resolveCurrentMember` en [FirestoreRepository]) y los dos últimos leen
 * `getMembers` — ambos dependen de piezas que se moverán junto con
 * `MemberRepository` en la fase 2.5. Se quedan en [FirestoreRepository] hasta
 * esa fase para no crear una dependencia circular — ver el resumen del encargo.
 */
class HouseholdRepository(
    private val baseUrl: String,
    private val firestoreClient: FirestoreClient,
    private val taskCache: TaskCache,
    private val getLocalId: () -> String?
) {
    private val client = firestoreClient.client

    private suspend fun HttpRequestBuilder.withAuth() = with(firestoreClient) { withAuth() }
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() = with(firestoreClient) { tryAuthOrApiKey() }
    private fun extractDocId(resourceName: String, operation: String): String =
        firestoreClient.extractDocId(resourceName, operation)
    private suspend fun ensureAuth() = firestoreClient.ensureAuth()

    // ────────────────────────────────────────────────────────
    //  Households
    // ────────────────────────────────────────────────────────

    /** Create a household (auto-generated doc ID). Requires auth (write). */
    suspend fun createHousehold(name: String, isPersonal: Boolean = false): HouseholdResponse {
        ensureAuth()
        val now = Clock.System.now().toEpochMilliseconds()
        val inviteCode = if (isPersonal) "PERSONAL" else generateInviteCode()
        val ownerId = getLocalId() ?: throw IllegalStateException("No autenticado")

        val fields = mapOf(
            "name" to FirestoreValue(stringValue = name),
            "inviteCode" to FirestoreValue(stringValue = inviteCode),
            "isPersonal" to FirestoreValue(booleanValue = isPersonal),
            "ownerId" to FirestoreValue(stringValue = ownerId),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createHousehold")

        // Publicar el mapa código → hogar para poder unirse sin listar hogares.
        if (!isPersonal) {
            try {
                createInvite(inviteCode, id)
            } catch (_: Exception) {
                // No crítico: sin invite, otros no pueden unirse por código.
            }
        }

        val household = HouseholdResponse(id, name, inviteCode, now, now, isPersonal, ownerId)
        // Cache immediately so getHousehold has it on first load
        taskCache.cacheHousehold(household)
        return household
    }

    /**
     * Obtiene (o crea) el espacio Personal del usuario actual con un ID DETERMINISTA
     * derivado de su identidad estable: `personal_{uid}`, donde `uid` es el UID de
     * Google si hay sesión iniciada, o el UID anónimo persistido en caso contrario.
     *
     * Esto hace el espacio Personal interdispositivo: con la misma cuenta de Google,
     * todos los dispositivos resuelven el MISMO documento `households/personal_{uid}`,
     * así que tareas/miembros/puntos se comparten automáticamente. En modo anónimo
     * (sin cuenta) sigue siendo por-dispositivo, como antes.
     */
    suspend fun getOrCreatePersonalHousehold(): HouseholdResponse {
        ensureAuth()
        val uid = getLocalId() ?: throw IllegalStateException("No autenticado")
        val personalId = personalHouseholdId(uid)

        // 1) Si ya existe (lo creó este u otro dispositivo con la misma cuenta),
        //    devolverlo tal cual y refrescar la caché local.
        val existing = try {
            getHousehold(personalId)
        } catch (_: Exception) {
            null
        }
        if (existing != null) return existing

        // 2) No existe → crearlo en el ID determinista.
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "name" to FirestoreValue(stringValue = "Personal"),
            "inviteCode" to FirestoreValue(stringValue = "PERSONAL"),
            "isPersonal" to FirestoreValue(booleanValue = true),
            "ownerId" to FirestoreValue(stringValue = uid),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )
        val response: FirestoreDocumentResponse = try {
            client.post("$baseUrl/households") {
                withAuth()
                parameter("documentId", personalId)
                contentType(ContentType.Application.Json)
                setBody(FirestoreDocument(fields))
            }.body()
        } catch (e: FirestoreException) {
            // Carrera entre dispositivos: el mismo usuario abrió la app en dos
            // sitios a la vez y ambos intentaron crear el mismo ID determinista.
            // El que llega segundo recibe ALREADY_EXISTS: no es un fallo real,
            // basta con leer el hogar que el otro dispositivo acaba de crear.
            if (e.code == "ALREADY_EXISTS" || e.statusCode == 409) {
                return getHousehold(personalId)
            }
            throw e
        }

        val household = HouseholdResponse(
            id = extractDocId(response.name, "getOrCreatePersonalHousehold"),
            name = "Personal",
            inviteCode = "PERSONAL",
            createdAt = now,
            updatedAt = now,
            isPersonal = true,
            ownerId = uid
        )
        taskCache.cacheHousehold(household)
        return household
    }

    /** ID determinista del espacio Personal para una identidad (UID) dada. */
    fun personalHouseholdId(uid: String): String = "personal_$uid"

    /**
     * Escribe invites/{code} → { householdId }. Las reglas de Firestore permiten
     * resolverlo por código (get) pero no listar la colección, de modo que el
     * código actúa como secreto compartido fuera de banda.
     */
    private suspend fun createInvite(code: String, householdId: String) {
        val fields = mapOf("householdId" to FirestoreValue(stringValue = householdId))
        client.post("$baseUrl/invites") {
            withAuth()
            parameter("documentId", code)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Get a household by id. Falls back to local cache on network/5xx failures.
     *
     * A 404/403 from Firestore is a DEFINITIVE signal — the household was deleted
     * or we lost access to it — so it must NOT fall back to the stale cache (that
     * would keep showing a "ghost" household forever). Callers that need to prune
     * local state should catch [FirestoreException] and check [FirestoreException.statusCode].
     */
    suspend fun getHousehold(id: String): HouseholdResponse {
        return try {
            val response: FirestoreDocumentResponse = client.get("$baseUrl/households/$id") {
                tryAuthOrApiKey()
            }.body()

            val household = toHouseholdResponse(response, knownId = id)
            taskCache.cacheHousehold(household)
            household
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirestoreException) {
            if (e.statusCode == 404 || e.statusCode == 403) throw e
            taskCache.getCachedHousehold(id) ?: throw e
        } catch (e: Exception) {
            taskCache.getCachedHousehold(id) ?: throw e
        }
    }

    /**
     * Reconcilia los hogares guardados localmente en [store] contra Firestore
     * (fuente de verdad). Poda (borra de [store]) los que ya no existen o a los
     * que se perdió acceso (404/403 — señal inequívoca). Ante fallos de red o de
     * servidor (5xx, timeouts, sin conexión) CONSERVA la entrada: no podar nunca
     * por un fallo transitorio, solo por una confirmación explícita de Firestore.
     *
     * Devuelve la lista de hogares que sobreviven la reconciliación.
     */
    suspend fun reconcileHouseholds(store: HouseholdStore): List<SavedHousehold> {
        val saved = store.getSavedHouseholds()
        val survivors = coroutineScope {
            saved.map { h ->
                async {
                    val stillExists = try {
                        getHousehold(h.id)
                        true
                    } catch (e: FirestoreException) {
                        if (e.statusCode == 404 || e.statusCode == 403) {
                            store.removeHousehold(h.id)
                            taskCache.clearHousehold(h.id)
                            false
                        } else {
                            true // 5xx u otro error tipado de Firestore: no es inequívoco, conservar
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        true // red/timeout/etc: conservar
                    }
                    if (stillExists) h else null
                }
            }.awaitAll()
        }
        return survivors.filterNotNull()
    }

    /** Batch-fetch multiple households by their document IDs (en paralelo). */
    suspend fun getHouseholds(ids: List<String>): List<HouseholdResponse> {
        if (ids.isEmpty()) return emptyList()
        return coroutineScope {
            ids.map { id ->
                async {
                    try {
                        getHousehold(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null // stale ID from local store — skip
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Find a household by invite code. Uses the invites/{code} map (no list). */
    suspend fun joinHousehold(inviteCode: String): HouseholdResponse {
        // 1) Resolver código → householdId vía invites/{code}.
        val inviteResponse: FirestoreDocumentResponse = client.get("$baseUrl/invites/$inviteCode") {
            tryAuthOrApiKey()
        }.body()

        val householdId = inviteResponse.fields["householdId"]?.stringValue
            ?: throw IllegalStateException("Código de invitación inválido")

        // 2) Leer el hogar por su ID.
        return getHousehold(householdId)
    }

    // ────────────────────────────────────────────────────────
    //  Messages (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** Send a chat message to a household. */
    suspend fun sendMessage(
        householdId: String,
        memberId: String,
        authorName: String,
        text: String
    ): MessageResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "memberId" to FirestoreValue(stringValue = memberId),
            "authorName" to FirestoreValue(stringValue = authorName),
            "text" to FirestoreValue(stringValue = text),
            "createdAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/messages"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "sendMessage")
        return MessageResponse(id, memberId, authorName, text, now)
    }

    /** List chat messages for a household, oldest first. */
    suspend fun getMessages(householdId: String): List<MessageResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/messages"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { doc ->
            val f = doc.fields
            MessageResponse(
                id = extractDocId(doc.name, "getMessages"),
                memberId = f["memberId"]?.stringValue ?: "",
                authorName = f["authorName"]?.stringValue ?: "",
                text = f["text"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
            )
        }.sortedBy { it.createdAt }
    }

    private fun toHouseholdResponse(
        doc: FirestoreDocumentResponse,
        knownId: String? = null,
        operation: String = "getHousehold"
    ): HouseholdResponse = FirestoreParsers.toHouseholdResponse(doc, knownId, operation)

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[secureRandomInt(chars.length)] }.joinToString("")
    }
}
