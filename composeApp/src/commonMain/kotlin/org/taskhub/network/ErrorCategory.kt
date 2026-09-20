/**
 * Clasificación de errores de red/Firestore para decidir qué mensaje mostrar
 * al usuario — ver `docs/mensajes-error-usuario-2026-09-16.md`. Antes cada
 * `ScreenModel` mostraba `e.message` (texto técnico crudo de Firestore/Ktor,
 * o ninguna distinción entre "sin conexión" y "sin acceso") con un fallback
 * genérico tipo "Error al cargar tareas"; esto centraliza la clasificación en
 * un único sitio para que todos los `ScreenModel`/managers la compartan.
 */
package org.taskhub.network

import kotlinx.io.IOException

/**
 * Categoría de un fallo, de más a menos específica:
 * - [AMBIGUOUS]: la petición pudo haber llegado al servidor o no (timeout de
 *   red, IOException) — usado por operaciones que mutan estado (donatePoints,
 *   redeemReward) para NO revertir cuando el servidor pudo haber completado ya
 *   la escritura (panel v14, hallazgo 2).
 * - [GONE_OR_FORBIDDEN]: Firestore respondió 403/404 — el recurso ya no
 *   existe o el usuario perdió acceso (hogar borrado, expulsado, etc.).
 * - [SERVER]: el servidor respondió pero con un 5xx (fallo del backend, no
 *   del cliente).
 * - [NO_CONNECTION]: NO hubo respuesta HTTP en absoluto — fallo de
 *   transporte (DNS, sin conexión). Ver [FirestoreClient.isOnline]
 *   para el mismo criterio aplicado a la sonda de conectividad.
 * - [OPERATION]: el servidor respondió con otro 4xx (validación, argumento
 *   inválido, etc.) — el mensaje específico de la operación que falló.
 */
enum class ErrorCategory { AMBIGUOUS, NO_CONNECTION, GONE_OR_FORBIDDEN, SERVER, OPERATION }

/**
 * Clasifica cualquier excepción lanzada por la capa de red. Reutiliza
 * [isGoneOrForbidden] para [FirestoreException] (la misma comprobación
 * 403/404 que ya usaban los `ScreenModel`) y el status HTTP conservado en
 * [CloudFunctionException.httpStatusCode] para los fallos de Cloud
 * Functions — cualquier otra excepción (sin status HTTP asociado) significa
 * que la petición ni siquiera llegó a responder, es decir, sin conexión.
 */
fun Throwable.errorCategory(): ErrorCategory = when (this) {
    is FirestoreException -> when {
        isGoneOrForbidden -> ErrorCategory.GONE_OR_FORBIDDEN
        statusCode >= 500 -> ErrorCategory.SERVER
        else -> ErrorCategory.OPERATION
    }
    is CloudFunctionException -> when {
        httpStatusCode == 403 || httpStatusCode == 404 -> ErrorCategory.GONE_OR_FORBIDDEN
        httpStatusCode >= 500 -> ErrorCategory.SERVER
        httpStatusCode > 0 -> ErrorCategory.OPERATION
        else -> ErrorCategory.AMBIGUOUS
    }
    is IOException -> ErrorCategory.AMBIGUOUS
    else -> (cause as? Exception)?.errorCategory() ?: ErrorCategory.NO_CONNECTION
}

/**
 * Clave de [org.taskhub.ui.i18n.AppStrings] a mostrar al usuario para este
 * fallo. [operationKey] es la clave específica de la operación que se
 * intentaba (p.ej. `"task_error_creating"`) — solo se usa cuando la
 * categoría es [ErrorCategory.OPERATION]; el resto de categorías tienen un
 * mensaje genérico común (sin conexión / sin acceso / problema del
 * servidor) independiente de qué operación falló. Vive en `network` (no en
 * `ui.i18n`) para que capas sin dependencia de Compose/AppStrings (p.ej.
 * [org.taskhub.ui.models.MemberScreenModel.appreciateMember], que expone
 * una CLAVE sin traducir en su estado) puedan usarla directamente.
 */
fun Throwable.toUserMessageKey(operationKey: String): String = when (errorCategory()) {
    ErrorCategory.AMBIGUOUS -> "transfer_error_uncertain"
    ErrorCategory.NO_CONNECTION -> "error_no_connection"
    ErrorCategory.GONE_OR_FORBIDDEN -> "error_gone_or_forbidden"
    ErrorCategory.SERVER -> "error_server"
    ErrorCategory.OPERATION -> operationKey
}
