/**
 * Traduce cualquier excepción de red al mensaje de usuario correcto (sin
 * conexión / sin acceso / problema del servidor / la operación concreta que
 * falló). Ver [org.taskhub.network.errorCategory] para la clasificación.
 */
package org.taskhub.ui.i18n

import org.taskhub.network.toUserMessageKey

/**
 * @param operationKey clave de [AppStrings] específica de la operación que
 *   falló (p.ej. `"task_error_creating"`), usada solo si el fallo no es
 *   clasificable como sin conexión/sin acceso/servidor.
 * @param ambiguousKey clave a usar cuando el fallo es [org.taskhub.network.ErrorCategory.AMBIGUOUS]
 *   (timeout: la petición pudo haber llegado al servidor o no). Por defecto
 *   `"transfer_error_uncertain"` (redactado para transferencias de puntos);
 *   operaciones que no son una transferencia (p.ej. completar una tarea)
 *   deben pasar una clave propia — ver `"task_error_uncertain"`.
 */
fun Throwable.toUserMessage(lang: String, operationKey: String, ambiguousKey: String = "transfer_error_uncertain"): String =
    AppStrings.get(toUserMessageKey(operationKey, ambiguousKey), lang)
