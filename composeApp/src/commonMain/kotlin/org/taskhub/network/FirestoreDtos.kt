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

@Serializable
data class FirestoreValue(
    val stringValue: String? = null,
    val integerValue: String? = null,   // NOTE: Firestore REST API sends ints as strings
    val booleanValue: Boolean? = null,
    val nullValue: String? = null,
    val mapValue: FirestoreMapValue? = null,
    val arrayValue: FirestoreArrayValue? = null
)

@Serializable
data class FirestoreArrayValue(
    val values: List<FirestoreValue> = emptyList()
)

@Serializable
data class FirestoreMapValue(
    val fields: Map<String, FirestoreValue> = emptyMap()
)

// ── Document envelope ──────────────────────────────────────

@Serializable
data class FirestoreDocument(
    val fields: Map<String, FirestoreValue>
)

// ── API Responses ──────────────────────────────────────────

@Serializable
data class FirestoreDocumentResponse(
    val name: String = "",  // full resource path: projects/.../documents/collection/docId
    val fields: Map<String, FirestoreValue> = emptyMap(),
    val createTime: String? = null,
    val updateTime: String? = null
)

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

@Serializable
data class FirestoreErrorEnvelope(
    val error: FirestoreErrorBody? = null
)

@Serializable
data class FirestoreErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

// ── Query types ────────────────────────────────────────────

@Serializable
data class RunQueryRequest(
    val structuredQuery: StructuredQuery
)

@Serializable
data class StructuredQuery(
    val from: List<CollectionSelector>,
    val where: Filter? = null,
    val limit: Int? = null
)

@Serializable
data class CollectionSelector(
    val collectionId: String,
    val allDescendants: Boolean = false
)

@Serializable
data class Filter(
    val fieldFilter: FieldFilter? = null,
    val compositeFilter: CompositeFilter? = null
)

@Serializable
data class CompositeFilter(
    val op: String,
    val filters: List<Filter>
)

@Serializable
data class FieldFilter(
    val field: FieldReference,
    val op: String,
    val value: FirestoreValue
)

@Serializable
data class FieldReference(
    val fieldPath: String
)

// ── Firebase Auth (Anonymous) ──────────────────────────────

@Serializable
data class FirebaseAuthRequest(
    // Sin valor por defecto: con encodeDefaults=false, un default aquí haría que
    // kotlinx.serialization OMITA el campo del body (mismo pitfall ya resuelto en
    // SignInWithIdpRequest), y el alta anónima fallaría silenciosamente sin idToken.
    val returnSecureToken: Boolean
)

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

@Serializable
data class RunQueryResponseItem(
    val document: FirestoreDocumentResponse? = null,
    val readTime: String? = null
)
