package org.taskhub.server.services

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.taskhub.server.models.MemberResponse
import org.taskhub.server.plugins.FirebasePlugin
import java.util.UUID

class MemberService {

    private fun membersCollection(householdId: String) =
        FirebasePlugin.firestore
            .collection("households")
            .document(householdId)
            .collection("members")

    suspend fun listByHousehold(householdId: String): List<MemberResponse> = withContext(Dispatchers.IO) {
        val snapshot = membersCollection(householdId)
            .whereEqualTo("leftAt", null)
            .get()
            .get()

        snapshot.documents.map { it.toMemberResponse() }
    }

    suspend fun create(
        householdId: String,
        displayName: String,
        role: String = "child",
        avatarUrl: String? = null
    ): MemberResponse = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = UUID.randomUUID().toString()

        val doc = mapOf(
            "id" to id,
            "householdId" to householdId,
            "displayName" to displayName,
            "role" to role,
            "avatarUrl" to avatarUrl,
            "totalPoints" to 0,
            "joinedAt" to now,
            "leftAt" to null
        )

        membersCollection(householdId).document(id).set(doc).get()

        MemberResponse(
            id = id,
            householdId = householdId,
            displayName = displayName,
            role = role,
            avatarUrl = avatarUrl,
            totalPoints = 0,
            joinedAt = now
        )
    }

    suspend fun leave(householdId: String, memberId: String): Boolean = withContext(Dispatchers.IO) {
        val docRef = membersCollection(householdId).document(memberId)
        val doc = docRef.get().get()

        if (!doc.exists()) return@withContext false
        if (doc.getLong("leftAt") != null) return@withContext false // already left

        val now = Clock.System.now().toEpochMilliseconds()
        docRef.update("leftAt", now).get()

        return@withContext true
    }

    suspend fun exists(householdId: String, memberId: String): Boolean = withContext(Dispatchers.IO) {
        val doc = membersCollection(householdId).document(memberId).get().get()
        if (!doc.exists()) return@withContext false
        doc.getLong("leftAt") == null
    }

    private fun DocumentSnapshot.toMemberResponse(): MemberResponse {
        val data = this.data ?: emptyMap()
        return MemberResponse(
            id = data["id"] as? String ?: "",
            householdId = data["householdId"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            role = data["role"] as? String ?: "child",
            avatarUrl = data["avatarUrl"] as? String?,
            totalPoints = (data["totalPoints"] as? Number)?.toInt() ?: 0,
            joinedAt = (data["joinedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
