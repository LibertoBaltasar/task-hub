package org.taskhub.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import org.taskhub.network.models.RewardRedemption
import org.taskhub.network.models.RewardResponse

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
class RewardsRepository(
    private val baseUrl: String,
    private val firestoreClient: FirestoreClient
) {
    private val client = firestoreClient.client

    private suspend fun HttpRequestBuilder.withAuth() = with(firestoreClient) { withAuth() }
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() = with(firestoreClient) { tryAuthOrApiKey() }
    private fun extractDocId(resourceName: String, operation: String): String =
        firestoreClient.extractDocId(resourceName, operation)

    /** List all rewards for a household. */
    suspend fun getRewards(householdId: String): List<RewardResponse> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/rewards"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc ->
            val f = doc.fields
            RewardResponse(
                id = extractDocId(doc.name, "getRewards"),
                householdId = f["householdId"]?.stringValue ?: householdId,
                title = f["title"]?.stringValue ?: "",
                description = f["description"]?.stringValue ?: "",
                cost = f["cost"]?.integerValue?.toIntOrNull() ?: 0,
                icon = f["icon"]?.stringValue ?: "🎁",
                createdBy = f["createdBy"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
            )
        }
    }

    /** Create a reward. Requires auth (write). */
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
        return RewardResponse(id, householdId, title, description, cost, icon, createdBy, now)
    }

    /** Delete a reward. Requires auth (write). */
    suspend fun deleteReward(householdId: String, rewardId: String) {
        client.delete("$baseUrl/households/$householdId/rewards/$rewardId") {
            withAuth()
        }
    }

    /** Get all reward redemptions for a household. */
    suspend fun getRewardRedemptions(householdId: String): List<RewardRedemption> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/rewardRedemptions"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc ->
            val f = doc.fields
            RewardRedemption(
                id = extractDocId(doc.name, "getRewardRedemptions"),
                rewardId = f["rewardId"]?.stringValue ?: "",
                memberId = f["memberId"]?.stringValue ?: "",
                redeemedAt = f["redeemedAt"]?.integerValue?.toLongOrNull() ?: 0L,
                pointsSpent = f["pointsSpent"]?.integerValue?.toIntOrNull() ?: 0
            )
        }
    }
}
