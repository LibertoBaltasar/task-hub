/**
 * Renderizado i18n de notificaciones: convierte una [NotificationResponse]
 * (que guarda claves de [AppStrings] + parámetros, no texto ya traducido)
 * en el título/mensaje final en el idioma de quien la lee.
 */
package org.taskhub.ui.i18n

import org.taskhub.network.models.NotificationResponse

/**
 * Renderiza el título/mensaje de una [NotificationResponse] en el idioma del
 * LECTOR (`lang` = idioma del dispositivo que MUESTRA la notificación), no en
 * el de quien la escribió — panel de notificaciones 2026-09-05, IMPORTANTE.
 *
 * Si [NotificationResponse.titleKey]/[NotificationResponse.messageKey] son
 * `null` (notificaciones ANTIGUAS, creadas antes de este rediseño, o mensajes
 * de chat cuyo cuerpo es contenido de usuario no traducible), se usa
 * [NotificationResponse.title]/[NotificationResponse.message] tal cual, igual
 * que antes — compatible con datos existentes.
 */
object NotificationText {

    /** Título traducido vía [AppStrings.get]`(titleKey, lang)`, o [NotificationResponse.title] si no hay `titleKey` (dato legado). */
    fun title(notification: NotificationResponse, lang: String): String =
        notification.titleKey?.let { AppStrings.get(it, lang) } ?: notification.title

    /** Mensaje traducido vía [AppStrings.get]`(messageKey, lang)`, con `messageParams["taskTitle"]` anexado si existe; o [NotificationResponse.message] si no hay `messageKey` (dato legado). */
    fun message(notification: NotificationResponse, lang: String): String {
        val key = notification.messageKey ?: return notification.message
        val rendered = AppStrings.get(key, lang)
        // Único param usado hoy: "taskTitle" se APPEND al prefijo traducido
        // (p. ej. "Se te ha asignado: " + "Sacar la basura"), igual que la
        // concatenación que hacía el caller antes de este rediseño.
        val taskTitle = notification.messageParams?.get("taskTitle")
        return if (!taskTitle.isNullOrEmpty()) rendered + taskTitle else rendered
    }
}
