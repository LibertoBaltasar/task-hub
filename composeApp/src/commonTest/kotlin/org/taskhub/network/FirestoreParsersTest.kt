package org.taskhub.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FirestoreParsersTest {

    // ── extractDocId ──────────────────────────────────────────

    @Test
    fun extractDocId_withFullResourcePath_returnsLastSegment() {
        val name = "projects/task-hub-62f98/databases/(default)/documents/households/abc123"
        assertEquals("abc123", FirestoreParsers.extractDocId(name, "getHousehold"))
    }

    @Test
    fun extractDocId_withoutSlashes_returnsWholeString() {
        assertEquals("abc123", FirestoreParsers.extractDocId("abc123", "getHousehold"))
    }

    @Test
    fun extractDocId_blank_throwsWithOperationInMessage() {
        val ex = assertFailsWith<IllegalStateException> {
            FirestoreParsers.extractDocId("", "createHousehold")
        }
        assertEquals(true, ex.message?.contains("createHousehold"))
    }

    // ── toHouseholdResponse ──────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun toHouseholdResponse_withAllFieldsPresent_parsesThem() {
        val raw = """
            {
              "name": "projects/p/databases/(default)/documents/households/h1",
              "fields": {
                "name": {"stringValue": "Casa"},
                "inviteCode": {"stringValue": "XYZ123"},
                "createdAt": {"integerValue": "1000"},
                "updatedAt": {"integerValue": "2000"},
                "isPersonal": {"booleanValue": true}
              }
            }
        """.trimIndent()
        val doc = json.decodeFromString<FirestoreDocumentResponse>(raw)

        val result = FirestoreParsers.toHouseholdResponse(doc, operation = "getHousehold")

        assertEquals("h1", result.id)
        assertEquals("Casa", result.name)
        assertEquals("XYZ123", result.inviteCode)
        assertEquals(1000L, result.createdAt)
        assertEquals(2000L, result.updatedAt)
        assertEquals(true, result.isPersonal)
    }

    @Test
    fun toHouseholdResponse_withMissingFields_usesDefaultsAndKnownId() {
        val doc = FirestoreDocumentResponse(name = "", fields = emptyMap())

        val result = FirestoreParsers.toHouseholdResponse(doc, knownId = "known-id", operation = "getHousehold")

        assertEquals("known-id", result.id)
        assertEquals("", result.name)
        assertEquals("", result.inviteCode)
        assertEquals(0L, result.createdAt)
        assertEquals(0L, result.updatedAt)
        assertFalse(result.isPersonal)
    }

    @Test
    fun toHouseholdResponse_blankNameAndNoKnownId_throws() {
        val doc = FirestoreDocumentResponse(name = "", fields = emptyMap())
        assertFailsWith<IllegalStateException> {
            FirestoreParsers.toHouseholdResponse(doc, knownId = null, operation = "getHousehold")
        }
    }

    @Test
    fun toHouseholdResponse_withUnknownExtraJsonFields_ignoresThem() {
        val raw = """
            {
              "name": "projects/p/databases/(default)/documents/households/h1",
              "fields": {
                "name": {"stringValue": "Casa"},
                "somethingNew": {"stringValue": "unexpected"}
              },
              "createTime": "2024-01-01T00:00:00Z",
              "somethingElseAtTopLevel": 42
            }
        """.trimIndent()
        val doc = json.decodeFromString<FirestoreDocumentResponse>(raw)

        val result = FirestoreParsers.toHouseholdResponse(doc, operation = "getHousehold")

        assertEquals("h1", result.id)
        assertEquals("Casa", result.name)
    }

    // ── toMemberResponse ──────────────────────────────────────

    @Test
    fun toMemberResponse_withAllFieldsPresent_parsesThem() {
        val raw = """
            {
              "name": "projects/p/databases/(default)/documents/households/h1/members/m1",
              "fields": {
                "displayName": {"stringValue": "Ana"},
                "role": {"stringValue": "admin"},
                "totalPoints": {"integerValue": "42"},
                "appreciationGiven": {"integerValue": "10"},
                "appreciationWeekStart": {"integerValue": "999"}
              }
            }
        """.trimIndent()
        val doc = json.decodeFromString<FirestoreDocumentResponse>(raw)

        val result = FirestoreParsers.toMemberResponse(doc, householdId = "h1", operation = "getMembers")

        assertEquals("m1", result.id)
        assertEquals("h1", result.householdId)
        assertEquals("Ana", result.displayName)
        assertEquals("admin", result.role)
        assertEquals(42, result.totalPoints)
        assertEquals(10, result.appreciationGiven)
        assertEquals(999L, result.appreciationWeekStart)
    }

    @Test
    fun toMemberResponse_withMissingFields_usesDefaults() {
        val doc = FirestoreDocumentResponse(
            name = "projects/p/databases/(default)/documents/households/h1/members/m1",
            fields = emptyMap()
        )

        val result = FirestoreParsers.toMemberResponse(doc, householdId = "h1", operation = "getMembers")

        assertEquals("m1", result.id)
        assertEquals("h1", result.householdId)
        assertEquals("", result.displayName)
        assertEquals("child", result.role)
        assertEquals(0, result.totalPoints)
        assertNull(result.userId)
        assertEquals(0, result.appreciationGiven)
        assertEquals(0L, result.appreciationWeekStart)
    }

    @Test
    fun toMemberResponse_blankName_throws() {
        val doc = FirestoreDocumentResponse(name = "", fields = emptyMap())
        assertFailsWith<IllegalStateException> {
            FirestoreParsers.toMemberResponse(doc, householdId = "h1", operation = "getMembers")
        }
    }
}
