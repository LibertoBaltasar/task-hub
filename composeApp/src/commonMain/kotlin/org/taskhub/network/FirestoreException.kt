package org.taskhub.network

/**
 * Thrown when the Firestore REST API responds with an HTTP error (4xx/5xx).
 *
 * Carries the real status/code/message from Firestore's error body instead of
 * letting the response fall through to document parsing, where a missing
 * 'name' field would otherwise mask the actual failure (e.g. PERMISSION_DENIED).
 */
class FirestoreException(
    val statusCode: Int,
    val code: String? = null,
    override val message: String
) : Exception(message)
