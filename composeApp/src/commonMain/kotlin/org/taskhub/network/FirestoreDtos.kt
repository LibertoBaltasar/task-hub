package org.taskhub.network

import kotlinx.serialization.Serializable

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
    val documents: List<FirestoreDocumentResponse> = emptyList()
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
    val returnSecureToken: Boolean = true
)

@Serializable
data class FirebaseAuthResponse(
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String,
    val localId: String
)

// ── RunQuery response (one element per result) ─────────────

@Serializable
data class RunQueryResponseItem(
    val document: FirestoreDocumentResponse? = null,
    val readTime: String? = null
)
