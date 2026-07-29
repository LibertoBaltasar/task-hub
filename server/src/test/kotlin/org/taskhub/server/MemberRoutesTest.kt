package org.taskhub.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.taskhub.server.models.HouseholdCreatedResponse
import org.taskhub.server.models.MemberResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun uniqueDb() = "test_${UUID.randomUUID().toString().replace("-", "")}"

    private fun dbConfig(dbName: String) = MapApplicationConfig(
        "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
        "database.user" to "sa",
        "database.password" to ""
    )

    private suspend fun createHousehold(client: io.ktor.client.HttpClient): HouseholdCreatedResponse {
        val resp = client.post("/api/households") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Home"}""")
        }
        return json.decodeFromString(resp.bodyAsText())
    }

    @Test
    fun `list members of empty household returns 200 with empty list`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val household = createHousehold(client)
        val response = client.get("/api/households/${household.id}/members")

        assertEquals(HttpStatusCode.OK, response.status)
        val members = json.decodeFromString<List<MemberResponse>>(response.bodyAsText())
        assertTrue(members.isEmpty())
    }

    @Test
    fun `create member returns 201`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val household = createHousehold(client)
        val response = client.post("/api/households/${household.id}/members") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Mama Admin","role":"admin"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val member = json.decodeFromString<MemberResponse>(response.bodyAsText())
        assertEquals("Mama Admin", member.displayName)
        assertEquals("admin", member.role)
        assertEquals(household.id, member.householdId)
        assertTrue(member.id.isNotBlank())
    }

    @Test
    fun `create member with default role child`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val household = createHousehold(client)
        val response = client.post("/api/households/${household.id}/members") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Pablito"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val member = json.decodeFromString<MemberResponse>(response.bodyAsText())
        assertEquals("child", member.role)
    }

    @Test
    fun `create member with blank name returns 400`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val household = createHousehold(client)
        val response = client.post("/api/households/${household.id}/members") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create member with invalid role returns 400`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val household = createHousehold(client)
        val response = client.post("/api/households/${household.id}/members") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Pablito","role":"superadmin"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `list members returns 404 for nonexistent household`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val response = client.get("/api/households/nonexistent/members")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete member returns 204`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val household = createHousehold(client)
        val createResp = client.post("/api/households/${household.id}/members") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Pablito"}""")
        }
        val member = json.decodeFromString<MemberResponse>(createResp.bodyAsText())

        val response = client.delete("/api/households/${household.id}/members/${member.id}")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `delete nonexistent member returns 404`() = testApplication {
        val dbName = uniqueDb()
        environment { config = dbConfig(dbName) }
        application { module() }

        val household = createHousehold(client)
        val response = client.delete("/api/households/${household.id}/members/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}