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
 * Uses Firebase Anonymous Auth for write access.
 * The API key alone only allows reads — writes require a Bearer token.
 * Anonymous Auth requires zero user interaction (no Google Sign-In, no UI).
 */
class FirestoreRepository(
    private val projectId: String = "task-hub-62f98",
    private val apiKey: String = DEFAULT_API_KEY
) {
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    private val authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"

    // ── Auth state (in-memory, regenerated on app restart — fine for anonymous) ──
    @Volatile
    private var bearerToken: String? = null
    @Volatile
    private var tokenExpiry: Long = 0L  // epoch millis when token expires (minus safety margin)

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
    //  Auth
    // ────────────────────────────────────────────────────────

    /**
     * Ensures we have a valid anonymous auth token, signing up anonymously if needed.
     * Called lazily on first request. Token is cached in memory and refreshed
     * when within 5 minutes of expiry.
     *
     * POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=API_KEY
     * Body: {"returnSecureToken":true}
     */
    private suspend fun ensureAuth() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (bearerToken != null && now < tokenExpiry) return

        val response: FirebaseAuthResponse = client.post("$authUrl?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(FirebaseAuthRequest(returnSecureToken = true))
        }.body()

        bearerToken = response.idToken
        // expiresIn is in seconds. Refresh 5 minutes before actual expiry.
        tokenExpiry = now + (response.expiresIn.toLong() * 1000) - 300_000
    }

    /**
     * Adds Authorization header to a request builder if we already have a token.
     * Calls [ensureAuth] first so the token is always fresh.
     */
    private suspend fun HttpRequestBuilder.withAuth() {
        ensureAuth()
        bearerToken?.let { header("Authorization", "Bearer $it") }
    }

    // ────────────────────────────────────────────────────────
    //  Households
    // ────────────────────────────────────────────────────────

    /** Create a household (auto-generated doc ID). Requires auth (write). */
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
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return HouseholdResponse(id, name, inviteCode, now, now)
    }

    /** Get a household by id. Falls back to API key if auth fails (read-only). */
    suspend fun getHousehold(id: String): HouseholdResponse {
        val response: FirestoreDocumentResponse = client.get("$baseUrl/households/$id") {
            // Try Bearer token first; API key fallback for reads
            tryAuthOrApiKey()
        }.body()

        return toHouseholdResponse(response)
    }

    /** Find a household by invite code (query). Falls back to API key for reads. */
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
            tryAuthOrApiKey()
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

    /** List members of a household. Falls back to API key for reads. */
    suspend fun getMembers(householdId: String): List<MemberResponse> {
        val response: FirestoreListResponse = client.get("$baseUrl/households/$householdId/members") {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { toMemberResponse(it, householdId) }
    }

    /** Add a member to a household. Requires auth (write). */
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
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return MemberResponse(id, householdId, displayName, avatarUrl, role, 0, now)
    }

    /** Remove (leave) a member — soft-delete by setting leftAt. Requires auth (write). */
    suspend fun deleteMember(householdId: String, memberId: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mapOf(
            "leftAt" to FirestoreValue(integerValue = now.toString())
        )

        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "leftAt")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        return true
    }

    // ────────────────────────────────────────────────────────
    //  Request helpers
    // ────────────────────────────────────────────────────────

    /**
     * Tries Bearer auth first; falls back to API key parameter.
     * Used for read operations where API key alone might suffice.
     */
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() {
        try {
            ensureAuth()
            bearerToken?.let { header("Authorization", "Bearer $it") }
        } catch (_: Exception) {
            // Auth failed — fall back to API key for read-only access
            parameter("key", apiKey)
        }
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
        const val DEFAULT_API_KEY = "AIzaSyCOSray4XhnZGdgT91U14KlByk6ySuyhW0"
    }
}
