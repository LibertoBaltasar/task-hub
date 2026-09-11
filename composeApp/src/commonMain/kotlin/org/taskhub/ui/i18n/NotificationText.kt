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

    /**
     * Mensaje traducido vía [AppStrings.get]`(messageKey, lang)`, con
     * `messageParams["taskTitle"]` anexado si existe; o [NotificationResponse.message]
     * si no hay `messageKey` (dato legado).
     *
     * Notificaciones de chat (`authorMemberId`/`messageParams["preview"]` no
     * nulos, ver su KDoc): si el caller pasa [resolveAuthorName], el nombre
     * del autor se resuelve contra el estado ACTUAL de la lista de miembros
     * en vez de usar el `message` ya congelado con el nombre de cuando se
     * envió — así el autor abandonando/siendo expulsado después (que
     * anonimiza su `displayName` o borra su documento de miembro, ver
     * `MemberRepository.deleteMember`/`FirestoreRepository.leaveHousehold`)
     * se refleja sin tener que reescribir notificaciones ya creadas.
     * `resolveAuthorName` devolviendo `null` (miembro ya no existe, p. ej.
     * abandonó voluntariamente y su documento se borró) usa el placeholder
     * `member_deleted_name` en vez del nombre real congelado — ronda de
     * deuda aplicable 2026-09-12, punto B10.
     */
    fun message(
        notification: NotificationResponse,
        lang: String,
        resolveAuthorName: ((memberId: String) -> String?)? = null
    ): String {
        val authorId = notification.authorMemberId
        val preview = notification.messageParams?.get("preview")
        if (authorId != null && preview != null && resolveAuthorName != null) {
            val name = resolveAuthorName(authorId) ?: AppStrings.get("member_deleted_name", lang)
            return "$name: $preview"
        }
        val key = notification.messageKey ?: return notification.message
        val rendered = AppStrings.get(key, lang)
        // Único param usado hoy (aparte de "preview" de chat, ver arriba):
        // "taskTitle" se APPEND al prefijo traducido (p. ej. "Se te ha
        // asignado: " + "Sacar la basura"), igual que la concatenación que
        // hacía el caller antes de este rediseño.
        val taskTitle = notification.messageParams?.get("taskTitle")
        return if (!taskTitle.isNullOrEmpty()) rendered + taskTitle else rendered
    }
}
