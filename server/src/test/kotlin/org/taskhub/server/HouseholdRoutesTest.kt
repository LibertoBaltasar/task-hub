package org.taskhub.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.taskhub.server.models.HouseholdCreatedResponse
import org.taskhub.server.models.HouseholdResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HouseholdRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun uniqueDb() = "test_${UUID.randomUUID().toString().replace("-", "")}"

    @Test
    fun `create household returns 201 with invite code`() = testApplication {
        val dbName = uniqueDb()
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to ""
            )
        }

        application { module() }

        val response = client.post("/api/households") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Casa Lopez"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<HouseholdCreatedResponse>(response.bodyAsText())
        assertEquals("Casa Lopez", body.name)
        assertTrue(body.inviteCode.isNotBlank())
        assertTrue(body.id.isNotBlank())
    }

    @Test
    fun `create household with blank name returns 400`() = testApplication {
        val dbName = uniqueDb()
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to ""
            )
        }

        application { module() }

        val response = client.post("/api/households") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get household returns 200`() = testApplication {
        val dbName = uniqueDb()
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to ""
            )
        }

        application { module() }

        val createResp = client.post("/api/households") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Casa Lopez"}""")
        }
        val created = json.decodeFromString<HouseholdCreatedResponse>(createResp.bodyAsText())

        val response = client.get("/api/households/${created.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        val household = json.decodeFromString<HouseholdResponse>(response.bodyAsText())
        assertEquals("Casa Lopez", household.name)
    }

    @Test
    fun `get household not found returns 404`() = testApplication {
        val dbName = uniqueDb()
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to ""
            )
        }

        application { module() }

        val response = client.get("/api/households/nonexistent-id")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `join household with valid invite code returns 200`() = testApplication {
        val dbName = uniqueDb()
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to ""
            )
        }

        application { module() }

        val createResp = client.post("/api/households") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Casa Lopez"}""")
        }
        val created = json.decodeFromString<HouseholdCreatedResponse>(createResp.bodyAsText())

        val response = client.post("/api/households/join") {
            contentType(ContentType.Application.Json)
            setBody("""{"inviteCode":"${created.inviteCode}"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val household = json.decodeFromString<HouseholdResponse>(response.bodyAsText())
        assertEquals(created.id, household.id)
    }

    @Test
    fun `join household with invalid invite code returns 404`() = testApplication {
        val dbName = uniqueDb()
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to ""
            )
        }

        application { module() }

        val response = client.post("/api/households/join") {
            contentType(ContentType.Application.Json)
            setBody("""{"inviteCode":"INVALID1"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}