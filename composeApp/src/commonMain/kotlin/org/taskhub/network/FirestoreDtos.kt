/**
 * DTOs de la API REST de Firestore/Firebase Auth (formato "wire", no los
 * modelos de dominio de la app). Usados por [FirestoreClient] y todos los
 * repositorios de `network/` para serializar/deserializar peticiones y
 * respuestas HTTP con Ktor + kotlinx.serialization.
 */
package org.taskhub.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

// ── Firestore REST API Value types ─────────────────────────
// Firestore REST API uses typed wrappers for field values

/**
 * Wrapper tipado de un valor de campo de Firestore ("Value" en la API REST).
 * Solo uno de los campos va relleno por instancia (unión discriminada por
 * presencia); el resto queda a null. Nótese que [integerValue] es un String:
 * la API REST de Firestore serializa los enteros de 64 bits como texto para
 * no perder precisión en JSON.
 */
@Serializable
data class FirestoreValue(
    val stringValue: String? = null,
    val integerValue: String? = null,   // NOTE: Firestore REST API sends ints as strings
    val booleanValue: Boolean? = null,
    val nullValue: String? = null,
    val mapValue: FirestoreMapValue? = null,
    val arrayValue: FirestoreArrayValue? = null
)

/** Valor de Firestore de tipo array: lista de [FirestoreValue] anidados. */
@Serializable
data class FirestoreArrayValue(
    val values: List<FirestoreValue> = emptyList()
)

/** Valor de Firestore de tipo mapa: campos anidados con nombre. */
@Serializable
data class FirestoreMapValue(
    val fields: Map<String, FirestoreValue> = emptyMap()
)

// ── Document envelope ──────────────────────────────────────

/** Cuerpo de una petición de escritura (create/patch) de un documento de Firestore. */
@Serializable
data class FirestoreDocument(
    val fields: Map<String, FirestoreValue>
)

// ── API Responses ──────────────────────────────────────────

/** Documento tal como lo devuelve Firestore al leer/escribir (GET/POST/PATCH). */
@Serializable
data class FirestoreDocumentResponse(
    val name: String = "",  // full resource path: projects/.../documents/collection/docId
    val fields: Map<String, FirestoreValue> = emptyMap(),
    val createTime: String? = null,
    val updateTime: String? = null
)

/** Respuesta de listar una colección (`GET .../collection`). */
@Serializable
data class FirestoreListResponse(
    val documents: List<FirestoreDocumentResponse> = emptyList(),
    // Presente si la colección tiene más documentos de los que caben en una
    // página — ver [FirestoreRepository.listDocumentIds] (borrado en cascada).
    val nextPageToken: String? = null
)

/** Body de `POST accounts:delete` (Identity Toolkit) — ver [FirestoreClient.deleteFirebaseAccount]. */
@Serializable
data class DeleteAccountRequest(val idToken: String)

// ── Error envelope ──────────────────────────────────────────
// Firestore REST errors come back as {"error": {"code": 403, "message": "...", "status": "PERMISSION_DENIED"}}

/** Envoltorio JSON de un error de la API REST de Firestore/Google Cloud. */
@Serializable
data class FirestoreErrorEnvelope(
    val error: FirestoreErrorBody? = null
)

/** Cuerpo del error: código HTTP, mensaje legible y status simbólico (p. ej. `PERMISSION_DENIED`). */
@Serializable
data class FirestoreErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

// ── Query types ────────────────────────────────────────────

/** Cuerpo de `POST .../documents:runQuery` para consultas estructuradas (no simples GET de colección). */
@Serializable
data class RunQueryRequest(
    val structuredQuery: StructuredQuery
)

/** Consulta estructurada de Firestore: de qué colección, con qué filtro y límite de resultados. */
@Serializable
data class StructuredQuery(
    val from: List<CollectionSelector>,
    val where: Filter? = null,
    val limit: Int? = null
)

/** Selector de colección origen de una query; [allDescendants] activa collection group query. */
@Serializable
data class CollectionSelector(
    val collectionId: String,
    val allDescendants: Boolean = false
)

/** Filtro de una query: o bien un filtro simple de campo, o uno compuesto (AND/OR de sub-filtros). */
@Serializable
data class Filter(
    val fieldFilter: FieldFilter? = null,
    val compositeFilter: CompositeFilter? = null
)

/** Combinación de varios [Filter] con un operador (`AND`/`OR`). */
@Serializable
data class CompositeFilter(
    val op: String,
    val filters: List<Filter>
)

/** Filtro simple: compara un campo ([field]) con [value] usando el operador [op] (p. ej. `EQUAL`). */
@Serializable
data class FieldFilter(
    val field: FieldReference,
    val op: String,
    val value: FirestoreValue
)

/** Referencia a un campo del documento por su ruta (p. ej. `"householdId"`). */
@Serializable
data class FieldReference(
    val fieldPath: String
)

// ── Firebase Auth (Google) ──────────────────────────────────

/** Serializer que acepta tanto string como número para expiresIn. */
@OptIn(ExperimentalSerializationApi::class)
object StringOrNumberSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("StringOrNumber", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) encoder.encodeString(value)
        else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): String? {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        return if (el is JsonNull) null else el.jsonPrimitive.content
    }
}

/**
 * Respuesta de `accounts:signInWithIdp` (Identity Toolkit). [expiresIn] usa
 * [StringOrNumberSerializer] porque distintos endpoints de Firebase Auth lo
 * devuelven unas veces como string y otras como número.
 */
@Serializable
data class FirebaseAuthResponse(
    val idToken: String? = null,
    val refreshToken: String? = null,
    @Serializable(with = StringOrNumberSerializer::class)
    val expiresIn: String? = null,
    val localId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

/** Cuerpo de `POST signInWithIdp` (Identity Toolkit) para iniciar sesión con Google Sign-In. */
@Serializable
data class SignInWithIdpRequest(
    val postBody: String,
    val requestUri: String,
    val returnSecureToken: Boolean
)

/** Respuesta del endpoint securetoken.googleapis.com/v1/token (refresh token). */
@Serializable
data class TokenRefreshResponse(
    val id_token: String? = null,
    val user_id: String? = null,
    val expires_in: String? = null,
    val refresh_token: String? = null
)

// ── RunQuery response (one element per result) ─────────────

/**
 * Elemento de la respuesta de `runQuery` (que es un array JSON, un elemento
 * por resultado). [document] es null en el último elemento "de cierre" que
 * Firestore a veces envía solo con [readTime], sin documento.
 */
@Serializable
data class RunQueryResponseItem(
    val document: FirestoreDocumentResponse? = null,
    val readTime: String? = null
)
