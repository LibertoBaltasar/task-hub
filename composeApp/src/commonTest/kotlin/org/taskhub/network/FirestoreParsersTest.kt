package org.taskhub.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * [FirestoreParsers] traduce las respuestas crudas de la API REST de
 * Firestore (documentos con `name`/`fields` tipados) a los modelos de
 * dominio de la app. Cubre tanto el "camino feliz" como los valores
 * ausentes/por defecto y los fallos de datos inconsistentes (documento sin
 * id resoluble), que en producción solo pueden venir de una respuesta de
 * red malformada.
 */
class FirestoreParsersTest {

    // ── extractDocId ──────────────────────────────────────────

    /** El id de un documento es el último segmento de su ruta completa de recurso. */
    @Test
    fun extractDocId_withFullResourcePath_returnsLastSegment() {
        val name = "projects/task-hub-62f98/databases/(default)/documents/households/abc123"
        assertEquals("abc123", FirestoreParsers.extractDocId(name, "getHousehold"))
    }

    /** Si ya viene solo el id (sin ruta), se devuelve tal cual. */
    @Test
    fun extractDocId_withoutSlashes_returnsWholeString() {
        assertEquals("abc123", FirestoreParsers.extractDocId("abc123", "getHousehold"))
    }

    /**
     * Un `name` vacío es un dato irrecuperable: lanza en vez de devolver un
     * id vacío silencioso, e incluye el nombre de la operación en el mensaje
     * para poder localizar el origen del fallo en los logs.
     */
    @Test
    fun extractDocId_blank_throwsWithOperationInMessage() {
        val ex = assertFailsWith<IllegalStateException> {
            FirestoreParsers.extractDocId("", "createHousehold")
        }
        assertEquals(true, ex.message?.contains("createHousehold"))
    }

    // ── toHouseholdResponse ──────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    /** Con todos los campos presentes en el documento, se parsean tal cual. */
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

    /**
     * Campos ausentes en `fields` (documento parcial o de una versión
     * anterior del esquema) no deben romper el parseo: cada uno cae a su
     * valor por defecto, y el id se resuelve a partir del `knownId` pasado
     * explícitamente cuando `name` no trae uno.
     */
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

    /** Sin `name` resoluble NI `knownId` de respaldo, el id es irrecuperable: debe lanzar. */
    @Test
    fun toHouseholdResponse_blankNameAndNoKnownId_throws() {
        val doc = FirestoreDocumentResponse(name = "", fields = emptyMap())
        assertFailsWith<IllegalStateException> {
            FirestoreParsers.toHouseholdResponse(doc, knownId = null, operation = "getHousehold")
        }
    }

    /**
     * Campos desconocidos en el JSON (de una versión más nueva del backend, o
     * metadatos de Firestore como `createTime`) se ignoran sin romper el
     * parseo — compatibilidad hacia adelante.
     */
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

    /** Con todos los campos presentes (incluido el sistema de agradecimientos), se parsean tal cual. */
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

    /**
     * Documento de miembro sin la mayoría de campos (p.ej. creado con un
     * esquema antiguo): el rol por defecto es "child" (el más restrictivo,
     * ver firestore.rules) y los contadores/`userId` caen a 0/null.
     */
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

    /** Igual que en households: sin `name` resoluble, el id es irrecuperable y debe lanzar. */
    @Test
    fun toMemberResponse_blankName_throws() {
        val doc = FirestoreDocumentResponse(name = "", fields = emptyMap())
        assertFailsWith<IllegalStateException> {
            FirestoreParsers.toMemberResponse(doc, householdId = "h1", operation = "getMembers")
        }
    }
}
