package org.taskhub.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.taskhub.network.models.*

class ApiClient(baseUrl: String = DEFAULT_BASE_URL) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
            })
        }
    }

    private val apiUrl = baseUrl.trimEnd('/')

    // ── Household endpoints ────────────────────────────────

    suspend fun createHousehold(name: String): HouseholdResponse {
        return client.post("$apiUrl/api/households") {
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest(name = name))
        }.body()
    }

    suspend fun getHousehold(id: String): HouseholdResponse {
        return client.get("$apiUrl/api/households/$id").body()
    }

    suspend fun joinHousehold(inviteCode: String): HouseholdResponse {
        return client.post("$apiUrl/api/households/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinHouseholdRequest(inviteCode = inviteCode))
        }.body()
    }

    // ── Member endpoints ───────────────────────────────────

    suspend fun getMembers(householdId: String): List<MemberResponse> {
        return client.get("$apiUrl/api/households/$householdId/members").body()
    }

    suspend fun createMember(
        householdId: String,
        displayName: String,
        role: String = "child",
        avatarUrl: String? = null
    ): MemberResponse {
        return client.post("$apiUrl/api/households/$householdId/members") {
            contentType(ContentType.Application.Json)
            setBody(CreateMemberRequest(
                displayName = displayName,
                role = role,
                avatarUrl = avatarUrl
            ))
        }.body()
    }

    suspend fun deleteMember(householdId: String, memberId: String): Boolean {
        val response = client.delete("$apiUrl/api/households/$householdId/members/$memberId")
        return response.status == HttpStatusCode.NoContent
    }

    companion object {
        // 10.0.2.2 reaches host localhost from Android emulator
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
    }
}
