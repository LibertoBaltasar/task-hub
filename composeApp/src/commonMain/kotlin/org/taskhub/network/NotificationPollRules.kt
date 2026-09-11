// Lógica pura (sin I/O) del sondeo periódico de notificaciones — usada por
// `NotificationPollWorker` (androidMain, WorkManager). Extraída para poder
// testearla desde `commonTest` sin un dispositivo/emulador Android (ronda de
// deuda aplicable 2026-09-12, punto C17).
package org.taskhub.network

import org.taskhub.network.models.NotificationResponse

/**
 * Reglas puras del sondeo de notificaciones en segundo plano. Sin I/O —
 * testable directamente en `commonTest`.
 */
object NotificationPollRules {

    /**
     * Notificaciones NUEVAS a mostrar como push local — de [mine] (ya
     * filtradas al miembro actual del dispositivo), las que:
     * - NO están en [seenIds] (aún no se mostró un push por ellas en un
     *   ciclo de sondeo anterior).
     * - Siguen sin leer (`read == false`): excluye las que el usuario ya
     *   gestionó desde `NotificationListScreen` antes de que corriera este
     *   sondeo — sin este filtro, algo ya visto en la UI podía disparar
     *   igualmente el push local del sistema.
     *
     * Ordenadas por [NotificationResponse.createdAt] ascendente (más antigua
     * primero), para mostrar los avisos en el mismo orden en que ocurrieron.
     */
    fun selectNewNotifications(
        mine: List<NotificationResponse>,
        seenIds: Set<String>
    ): List<NotificationResponse> =
        mine.filter { it.id !in seenIds && !it.read }.sortedBy { it.createdAt }
}
