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

/**
 * True cuando el error indica que el recurso ya no existe o el usuario perdió
 * acceso a él (p. ej. un hogar borrado o del que se expulsó al miembro). Los
 * `ScreenModel` que cargan datos de un hogar concreto deben distinguir este
 * caso de un fallo de red transitorio para no confundir "sin acceso" con
 * "sin conexión".
 */
val FirestoreException.isGoneOrForbidden: Boolean
    get() = statusCode == 404 || statusCode == 403

/** Mensaje legible para mostrar al usuario cuando el recurso ya no existe o no hay acceso. */
const val FIRESTORE_GONE_MESSAGE = "Este espacio ya no existe o ya no tienes acceso a él."

/**
 * Señala que [FirestoreRepository.deleteHousehold] no pudo borrar TODAS las
 * subcolecciones del hogar (fallo puntual en algún documento — red, timeout,
 * etc.) — por eso el documento `households/{id}` en sí NO se ha borrado, para
 * poder reintentar el cascade-delete más tarde de forma idempotente en vez de
 * dejar el hogar a medio borrar sin ninguna forma de completarlo (panel v4,
 * Experto 8 hallazgo #2).
 */
class HouseholdCascadeIncompleteException(householdId: String) :
    Exception("No se pudieron borrar todos los datos del hogar $householdId; el hogar no se ha eliminado, se reintentará más adelante.")
