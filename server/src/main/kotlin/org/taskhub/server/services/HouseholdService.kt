package org.taskhub.server.services

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.taskhub.server.models.HouseholdCreatedResponse
import org.taskhub.server.models.HouseholdResponse
import org.taskhub.server.plugins.FirebasePlugin
import java.util.UUID
import kotlin.random.Random

class HouseholdService {

    private val households
        get() = FirebasePlugin.firestore.collection("households")

    suspend fun create(name: String): HouseholdCreatedResponse = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()

        val doc = mapOf(
            "id" to id,
            "name" to name,
            "inviteCode" to inviteCode,
            "createdAt" to now,
            "updatedAt" to now
        )

        households.document(id).set(doc).get()

        HouseholdCreatedResponse(
            id = id,
            name = name,
            inviteCode = inviteCode,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun getById(id: String): HouseholdResponse? = withContext(Dispatchers.IO) {
        val doc = households.document(id).get().get()
        if (!doc.exists()) return@withContext null
        doc.toHouseholdResponse()
    }

    suspend fun join(inviteCode: String): HouseholdResponse? = withContext(Dispatchers.IO) {
        val snapshot = households
            .whereEqualTo("inviteCode", inviteCode)
            .limit(1)
            .get()
            .get()

        if (snapshot.isEmpty) return@withContext null
        snapshot.documents.first().toHouseholdResponse()
    }

    suspend fun exists(id: String): Boolean = withContext(Dispatchers.IO) {
        val doc = households.document(id).get().get()
        doc.exists()
    }

    private fun DocumentSnapshot.toHouseholdResponse(): HouseholdResponse {
        val data = this.data ?: emptyMap()
        return HouseholdResponse(
            id = data["id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            inviteCode = data["inviteCode"] as? String ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
