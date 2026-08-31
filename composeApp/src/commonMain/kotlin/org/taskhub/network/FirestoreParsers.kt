package org.taskhub.network

import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse

/**
 * Parseo puro de respuestas de la Firestore REST API. Sin I/O — testable
 * directamente en `commonTest`.
 */
object FirestoreParsers {

    /**
     * Extract the document ID from the full Firestore resource name.
     *
     * By the time this runs, the HTTP status has already been validated by the
     * [io.ktor.client.plugins.HttpResponseValidator] installed on the ktor client — any 4xx/5xx
     * surfaces as a [FirestoreException] before the body is even parsed. So a blank name here
     * means a 2xx response came back genuinely missing the 'name' field, which
     * points at [operation] rather than a transport-level failure.
     */
    fun extractDocId(resourceName: String, operation: String): String {
        if (resourceName.isBlank()) {
            throw IllegalStateException(
                "Firestore: la operación '$operation' devolvió una respuesta 200 sin el campo " +
                "'name' esperado en el documento. No es un error de permisos ni de red — " +
                "revisa la respuesta de '$operation'."
            )
        }
        return resourceName.substringAfterLast("/")
    }

    fun toHouseholdResponse(
        doc: FirestoreDocumentResponse,
        knownId: String? = null,
        operation: String = "getHousehold"
    ): HouseholdResponse {
        val f = doc.fields
        val id = if (doc.name.isNotBlank()) extractDocId(doc.name, operation)
                 else knownId ?: throw IllegalStateException(
                     "Firestore: '$operation' devolvió un documento de hogar sin 'name' y sin ID conocido"
                 )
        return HouseholdResponse(
            id = id,
            name = f["name"]?.stringValue ?: "",
            inviteCode = f["inviteCode"]?.stringValue ?: "",
            createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
            updatedAt = f["updatedAt"]?.integerValue?.toLongOrNull() ?: 0L,
            isPersonal = f["isPersonal"]?.booleanValue ?: false,
            ownerId = f["ownerId"]?.stringValue ?: ""
        )
    }

    fun toMemberResponse(
        doc: FirestoreDocumentResponse,
        householdId: String,
        operation: String = "getMembers"
    ): MemberResponse {
        val f = doc.fields
        val id = if (doc.name.isNotBlank()) extractDocId(doc.name, operation)
                 else throw IllegalStateException(
                     "Firestore: '$operation' devolvió un documento de miembro sin 'name'"
                 )
        return MemberResponse(
            id = id,
            householdId = f["householdId"]?.stringValue ?: householdId,
            displayName = f["displayName"]?.stringValue ?: "",
            avatarUrl = f["avatarUrl"]?.stringValue,
            role = f["role"]?.stringValue ?: "child",
            totalPoints = f["totalPoints"]?.integerValue?.toIntOrNull() ?: 0,
            joinedAt = f["joinedAt"]?.integerValue?.toLongOrNull() ?: 0L,
            userId = f["userId"]?.stringValue,
            currentStreak = f["currentStreak"]?.integerValue?.toIntOrNull() ?: 0,
            bestStreak = f["bestStreak"]?.integerValue?.toIntOrNull() ?: 0,
            lastStreakDate = f["lastStreakDate"]?.integerValue?.toLongOrNull() ?: 0L,
            leftAt = f["leftAt"]?.integerValue?.toLongOrNull() ?: 0L,
            appreciationGiven = f["appreciationGiven"]?.integerValue?.toIntOrNull() ?: 0,
            appreciationWeekStart = f["appreciationWeekStart"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }
}
