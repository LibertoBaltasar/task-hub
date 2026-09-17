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
 */
fun Throwable.toUserMessage(lang: String, operationKey: String): String =
    AppStrings.get(toUserMessageKey(operationKey), lang)
