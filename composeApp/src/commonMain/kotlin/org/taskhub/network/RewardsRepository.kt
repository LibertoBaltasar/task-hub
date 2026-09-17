/**
 * Capa REST de Firestore para recompensas y sus canjes. Consumida por
 * `ScreenModel`s de la UI de recompensas y orquestada desde
 * [FirestoreRepository.redeemReward] para las operaciones que también tocan
 * puntos de miembro.
 */
package org.taskhub.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import org.taskhub.network.models.RewardRedemption
import org.taskhub.network.models.RewardResponse
import org.taskhub.storage.TaskCache

/**
 * Recompensas de un hogar (subcolección `households/{id}/rewards` y
 * `rewardRedemptions`). Extraído de [FirestoreRepository] (ver
 * docs/refactor-arquitectura-2026-08-31.md, punto 6, fase 2.2). Lógica movida
 * tal cual, sin cambios de comportamiento.
 *
 * `redeemReward` NO se movió aquí a propósito: descuenta puntos del miembro
 * (`addMemberPoints`) y lee `getMembers`, ambas operaciones de la capa de
 * puntos que hoy vive en [FirestoreRepository] (moverá a `MemberRepository`
 * en la fase 2.5). Moverla ahora obligaría a un ciclo `RewardsRepository` ↔
 * `MemberRepository` (este último aún no existe). Se mantiene en
 * `FirestoreRepository` hasta esa fase — ver el resumen del encargo.
 */
/**
 * Repositorio REST de recompensas de un hogar: alta/baja de recompensas y
 * registro de canjes. Delega auth y manejo de errores en [FirestoreClient].
 */
class RewardsRepository(
    private val baseUrl: String,
    private val firestoreClient: FirestoreClient,
    private val taskCache: TaskCache
) {
    private val client = firestoreClient.client

    private suspend fun HttpRequestBuilder.withAuth() = with(firestoreClient) { withAuth() }
    private fun extractDocId(resourceName: String, operation: String): String =
        firestoreClient.extractDocId(resourceName, operation)

    /**
     * Lista todas las recompensas de un hogar. Cache-first ante fallo (ronda
     * de deuda aplicable 2026-09-12, punto B11): antes usaba
     * `orDefault(emptyList())`, que no distingue "el hogar de verdad no
     * tiene recompensas" de "no se pudo leer" — un fallo de red puntual
     * vaciaba la lista de recompensas canjeables en vez de mostrar la última
     * foto conocida.
     */
    suspend fun getRewards(householdId: String): List<RewardResponse> {
        return try {
            val documents = client.listAllDocuments(
                "$baseUrl/households/$householdId/rewards"
            ) {
                withAuth()
            }
            val rewards = documents.map { doc -> FirestoreParsers.toRewardResponse(doc, householdId) }
            taskCache.cacheRewards(householdId, rewards)
            rewards
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            taskCache.getCachedRewards(householdId) ?: emptyList()
        }
    }

    /** Crea una recompensa nueva en el hogar. Requiere auth (escritura). */
    suspend fun createReward(
        householdId: String,
        title: String,
        description: String,
        cost: Int,
        icon: String,
        createdBy: String
    ): RewardResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mapOf(
            "householdId" to FirestoreValue(stringValue = householdId),
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "cost" to FirestoreValue(integerValue = cost.toString()),
            "icon" to FirestoreValue(stringValue = icon),
            "createdBy" to FirestoreValue(stringValue = createdBy),
            "createdAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/rewards"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createReward")
        taskCache.clearRewards(householdId)
        return RewardResponse(id, householdId, title, description, cost, icon, createdBy, now)
    }

    /** Borra una recompensa. Requiere auth (escritura). */
    suspend fun deleteReward(householdId: String, rewardId: String) {
        client.delete("$baseUrl/households/$householdId/rewards/$rewardId") {
            withAuth()
        }
        taskCache.clearRewards(householdId)
    }

    /**
     * Lista todos los canjes de recompensas de un hogar. Cache-first ante
     * fallo — mismo motivo que [getRewards] (punto B11).
     */
    suspend fun getRewardRedemptions(householdId: String): List<RewardRedemption> {
        return try {
            val documents = client.listAllDocuments(
                "$baseUrl/households/$householdId/rewardRedemptions"
            ) {
                withAuth()
            }
            val redemptions = documents.map { doc -> FirestoreParsers.toRewardRedemption(doc) }
            taskCache.cacheRewardRedemptions(householdId, redemptions)
            redemptions
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            taskCache.getCachedRewardRedemptions(householdId) ?: emptyList()
        }
    }

    /**
     * Escribe el registro de canje (sin tocar puntos del miembro) — usado por
     * [FirestoreRepository.redeemReward], que orquesta este escritura junto
     * con `MemberRepository.addMemberPoints` (Reward+Member, se queda en la
     * fachada por ese motivo, igual que `deleteHousehold`/`leaveHousehold` en
     * [HouseholdRepository]). Requires auth (write).
     */
    suspend fun createRedemption(
        householdId: String,
        rewardId: String,
        memberId: String,
        pointsSpent: Int,
        redeemedAt: Long
    ): RewardRedemption {
        val fields = mapOf(
            "rewardId" to FirestoreValue(stringValue = rewardId),
            "memberId" to FirestoreValue(stringValue = memberId),
            "redeemedAt" to FirestoreValue(integerValue = redeemedAt.toString()),
            "pointsSpent" to FirestoreValue(integerValue = pointsSpent.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/rewardRedemptions"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "redeemReward")
        taskCache.clearRewardRedemptions(householdId)
        return RewardRedemption(id, rewardId, memberId, redeemedAt, pointsSpent)
    }

    /**
     * Borra un registro de canje huérfano — usado por
     * [FirestoreRepository.redeemReward] para compensar cuando
     * [createRedemption] tuvo éxito pero el descuento de puntos posterior
     * falló (ver su KDoc): sin esto, un reintento del usuario creaba un
     * SEGUNDO registro de canje con un solo descuento real. Best-effort: si
     * el borrado también falla, el caller relanza igualmente la excepción
     * original (el registro huérfano queda para limpieza manual, pero el
     * usuario no pierde puntos).
     */
    suspend fun deleteRedemption(householdId: String, redemptionId: String) {
        client.delete("$baseUrl/households/$householdId/rewardRedemptions/$redemptionId") {
            withAuth()
        }
        taskCache.clearRewardRedemptions(householdId)
    }
}
