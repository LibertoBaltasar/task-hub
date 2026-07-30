package org.taskhub.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse
import kotlin.random.Random

/**
 * Talks directly to Firestore REST API — no Ktor server needed.
 *
 * Firestore REST API docs:
 *   https://firebase.google.com/docs/firestore/reference/rest
 *
 * All requests use ?key=API_KEY for unauthenticated access.
 */
class FirestoreRepository(
    private val projectId: String = "task-hub-62f98",
    private val apiKey: String = DEFAULT_API_KEY
) {
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    // ────────────────────────────────────────────────────────
    //  Households
    // ────────────────────────────────────────────────────────

    /** Create a household (auto-generated doc ID). */
    suspend fun createHousehold(name: String): HouseholdResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val inviteCode = generateInviteCode()

        val fields = mapOf(
            "name" to FirestoreValue(stringValue = name),
            "inviteCode" to FirestoreValue(stringValue = inviteCode),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households") {
            parameter("key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return HouseholdResponse(id, name, inviteCode, now, now)
    }

    /** Get a household by id. */
    suspend fun getHousehold(id: String): HouseholdResponse {
        val response: FirestoreDocumentResponse = client.get("$baseUrl/households/$id") {
            parameter("key", apiKey)
        }.body()

        return toHouseholdResponse(response)
    }

    /** Find a household by invite code (query). */
    suspend fun joinHousehold(inviteCode: String): HouseholdResponse {
        val query = RunQueryRequest(
            structuredQuery = StructuredQuery(
                from = listOf(CollectionSelector("households")),
                where = Filter(
                    fieldFilter = FieldFilter(
                        field = FieldReference("inviteCode"),
                        op = "EQUAL",
                        value = FirestoreValue(stringValue = inviteCode)
                    )
                ),
                limit = 1
            )
        )

        val items: List<RunQueryResponseItem> = client.post("$baseUrl:runQuery") {
            parameter("key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(query)
        }.body()

        val doc = items.firstOrNull()?.document
            ?: throw IllegalStateException("Código de invitación inválido")

        return toHouseholdResponse(doc)
    }

    // ────────────────────────────────────────────────────────
    //  Members (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** List members of a household. */
    suspend fun getMembers(householdId: String): List<MemberResponse> {
        val response: FirestoreListResponse = client.get("$baseUrl/households/$householdId/members") {
            parameter("key", apiKey)
        }.body()

        return response.documents.map { toMemberResponse(it, householdId) }
    }

    /** Add a member to a household. */
    suspend fun createMember(
        householdId: String,
        displayName: String,
        role: String = "child",
        avatarUrl: String? = null
    ): MemberResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "householdId" to FirestoreValue(stringValue = householdId),
            "displayName" to FirestoreValue(stringValue = displayName),
            "role" to FirestoreValue(stringValue = role),
            "totalPoints" to FirestoreValue(integerValue = "0"),
            "joinedAt" to FirestoreValue(integerValue = now.toString())
        )
        if (avatarUrl != null) {
            fields["avatarUrl"] = FirestoreValue(stringValue = avatarUrl)
        } else {
            fields["avatarUrl"] = FirestoreValue(nullValue = "NULL_VALUE")
        }

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households/$householdId/members") {
            parameter("key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return MemberResponse(id, householdId, displayName, avatarUrl, role, 0, now)
    }

    /** Remove (leave) a member — soft-delete by setting leftAt. */
    suspend fun deleteMember(householdId: String, memberId: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mapOf(
            "leftAt" to FirestoreValue(integerValue = now.toString())
        )

        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            parameter("key", apiKey)
            parameter("updateMask.fieldPaths", "leftAt")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        return true
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    /** Extract the document ID from the full Firestore resource name. */
    private fun extractDocId(resourceName: String): String =
        resourceName.substringAfterLast("/")

    private fun toHouseholdResponse(doc: FirestoreDocumentResponse): HouseholdResponse {
        val f = doc.fields
        return HouseholdResponse(
            id = extractDocId(doc.name),
            name = f["name"]?.stringValue ?: "",
            inviteCode = f["inviteCode"]?.stringValue ?: "",
            createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
            updatedAt = f["updatedAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    private fun toMemberResponse(doc: FirestoreDocumentResponse, householdId: String): MemberResponse {
        val f = doc.fields
        return MemberResponse(
            id = extractDocId(doc.name),
            householdId = f["householdId"]?.stringValue ?: householdId,
            displayName = f["displayName"]?.stringValue ?: "",
            avatarUrl = f["avatarUrl"]?.stringValue,
            role = f["role"]?.stringValue ?: "child",
            totalPoints = f["totalPoints"]?.integerValue?.toIntOrNull() ?: 0,
            joinedAt = f["joinedAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    companion object {
        /**
         * Firebase Web API Key for task-hub-62f98.
         * Find it at: Firebase Console → Project Settings → General → Web API Key
         *
         * TODO: Replace with the real key from your Firebase project,
         *       or inject it via build config / environment.
         */
        const val DEFAULT_API_KEY = "«redacted:AIza…»"
    }
}
