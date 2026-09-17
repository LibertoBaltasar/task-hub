// Capa de persistencia (storage/): caché offline de las respuestas de
// Firestore (tareas/hogar/miembros), usada como fallback cache-first cuando
// no hay conexión. Ver [HouseholdStore] para la lista de hogares conocidos.

package org.taskhub.storage

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.NotificationResponse
import org.taskhub.network.models.RewardRedemption
import org.taskhub.network.models.RewardResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse

/**
 * Cache local transparente usando multiplatform-settings (SharedPreferences/NSUserDefaults).
 *
 * Estrategia cache-first: los datos se guardan en cada lectura exitosa de Firestore
 * y se sirven desde aquí cuando no hay conexión.
 *
 * Keys: "cache_tasks_{householdId}", "cache_household_{householdId}", etc.
 */
class TaskCache(private val settings: Settings) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    /**
     * Escribe en [settings], tragando cualquier excepción: en web,
     * `localStorage` puede lanzar `QuotaExceededError`/`SecurityError` (cuota
     * de ~5-10MB del origen superada, storage bloqueado por el navegador) —
     * sin esto, un fallo al ESCRIBIR la caché (best-effort por definición)
     * se colaba en el `catch` de los repos que la llaman justo después de una
     * lectura de red que SÍ tuvo éxito, descartando esos datos buenos y
     * cayendo a una foto de caché más vieja (o a un error) en su lugar (panel
     * v12, Web). Todos los `cache*` de abajo pasan por aquí.
     */
    private fun putSafely(key: String, value: String) {
        try {
            settings.putString(key, value)
        } catch (_: Exception) {
            // Best-effort: ver KDoc de la función.
        }
    }

    // ── Tasks ───────────────────────────────────────────────

    /** Sobrescribe la caché de tareas de [householdId] con [tasks] (llamado tras cada lectura exitosa de red). */
    fun cacheTasks(householdId: String, tasks: List<TaskResponse>) {
        val key = "cache_tasks_$householdId"
        putSafely(key, json.encodeToString(tasks))
    }

    /** Tareas cacheadas de [householdId], o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedTasks(householdId: String): List<TaskResponse>? {
        val key = "cache_tasks_$householdId"
        val raw = settings.getStringOrNull(key) ?: return null
        return try {
            json.decodeFromString<List<TaskResponse>>(raw)
        } catch (_: Exception) {
            // JSON corrupto/incompatible (p.ej. escrito por una versión anterior):
            // se trata como "sin caché" en vez de propagar la excepción.
            null
        }
    }

    // ── Household ───────────────────────────────────────────

    /** Sobrescribe la caché del documento de hogar (llamado tras cada lectura exitosa de red). */
    fun cacheHousehold(household: HouseholdResponse) {
        val key = "cache_household_${household.id}"
        putSafely(key, json.encodeToString(household))
    }

    /** Documento de hogar cacheado, o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedHousehold(householdId: String): HouseholdResponse? {
        val key = "cache_household_$householdId"
        val raw = settings.getStringOrNull(key) ?: return null
        return try {
            json.decodeFromString<HouseholdResponse>(raw)
        } catch (_: Exception) {
            null
        }
    }

    // ── Members ─────────────────────────────────────────────

    /** Sobrescribe la caché de miembros de [householdId] con [members] (llamado tras cada lectura exitosa de red). */
    fun cacheMembers(householdId: String, members: List<MemberResponse>) {
        val key = "cache_members_$householdId"
        putSafely(key, json.encodeToString(members))
    }

    /** Miembros cacheados de [householdId], o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedMembers(householdId: String): List<MemberResponse>? {
        val key = "cache_members_$householdId"
        val raw = settings.getStringOrNull(key) ?: return null
        return try {
            json.decodeFromString<List<MemberResponse>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    // ── Task history ────────────────────────────────────────
    // Añadida en la ronda de deuda aplicable 2026-09-12 (punto B11): antes
    // `TaskRepository.getTaskHistory` usaba `orDefault(emptyList())`, que no
    // distingue "el hogar de verdad no tiene historial" de "no se pudo leer"
    // — StatsScreen (rachas/gráficas de 7 días) y el ranking mostraban un
    // hogar vacío en vez de la última foto conocida ante cualquier fallo de
    // red puntual. Mismo patrón cache-first que tareas/miembros arriba.

    /** Sobrescribe la caché de historial de [householdId] con [history] (llamado tras cada lectura exitosa de red). */
    fun cacheTaskHistory(householdId: String, history: List<TaskHistoryResponse>) {
        putSafely("cache_task_history_$householdId", json.encodeToString(history))
    }

    /** Historial cacheado de [householdId], o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedTaskHistory(householdId: String): List<TaskHistoryResponse>? {
        val raw = settings.getStringOrNull("cache_task_history_$householdId") ?: return null
        return try {
            json.decodeFromString<List<TaskHistoryResponse>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Invalida la caché de historial de un hogar. Ver [clearTasks]. */
    fun clearTaskHistory(householdId: String) {
        settings.remove("cache_task_history_$householdId")
    }

    // ── Assignments ─────────────────────────────────────────
    // Mismo patrón cache-first que tareas/historial arriba (kanban "[Caché]
    // getAssignments/getAllAssignments sin caché offline"): antes
    // `TaskRepository.getAssignments` no tenía respaldo local, así que un
    // fallo de red puntual dejaba una tarea sin ninguna asignación mostrada
    // en vez de la última foto conocida. Clave por [householdId]+[taskId]
    // (a diferencia de tasks/rewards/etc., que son un único blob por hogar)
    // porque las asignaciones se leen y guardan por tarea, no por hogar.

    /** Sobrescribe la caché de asignaciones de [taskId] con [assignments] (llamado tras cada lectura exitosa de red). */
    fun cacheAssignments(householdId: String, taskId: String, assignments: List<TaskAssignmentResponse>) {
        putSafely("cache_assignments_${householdId}_$taskId", json.encodeToString(assignments))
    }

    /** Asignaciones cacheadas de [taskId], o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedAssignments(householdId: String, taskId: String): List<TaskAssignmentResponse>? {
        val raw = settings.getStringOrNull("cache_assignments_${householdId}_$taskId") ?: return null
        return try {
            json.decodeFromString<List<TaskAssignmentResponse>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Invalida la caché de asignaciones de una tarea. Debe llamarse tras cualquier escritura que las modifique. Ver [clearTasks]. */
    fun clearAssignments(householdId: String, taskId: String) {
        settings.remove("cache_assignments_${householdId}_$taskId")
    }

    // ── Rewards & redemptions ───────────────────────────────
    // Mismo motivo/patrón que el historial de arriba (punto B11).

    /** Sobrescribe la caché de recompensas de [householdId] con [rewards] (llamado tras cada lectura exitosa de red). */
    fun cacheRewards(householdId: String, rewards: List<RewardResponse>) {
        putSafely("cache_rewards_$householdId", json.encodeToString(rewards))
    }

    /** Recompensas cacheadas de [householdId], o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedRewards(householdId: String): List<RewardResponse>? {
        val raw = settings.getStringOrNull("cache_rewards_$householdId") ?: return null
        return try {
            json.decodeFromString<List<RewardResponse>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Invalida la caché de recompensas de un hogar. Ver [clearTasks]. */
    fun clearRewards(householdId: String) {
        settings.remove("cache_rewards_$householdId")
    }

    /** Sobrescribe la caché de canjes de [householdId] con [redemptions] (llamado tras cada lectura exitosa de red). */
    fun cacheRewardRedemptions(householdId: String, redemptions: List<RewardRedemption>) {
        putSafely("cache_reward_redemptions_$householdId", json.encodeToString(redemptions))
    }

    /** Canjes cacheados de [householdId], o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedRewardRedemptions(householdId: String): List<RewardRedemption>? {
        val raw = settings.getStringOrNull("cache_reward_redemptions_$householdId") ?: return null
        return try {
            json.decodeFromString<List<RewardRedemption>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Invalida la caché de canjes de un hogar. Ver [clearTasks]. */
    fun clearRewardRedemptions(householdId: String) {
        settings.remove("cache_reward_redemptions_$householdId")
    }

    // ── Notifications ───────────────────────────────────────
    // Mismo motivo/patrón que el historial de arriba (punto B11): antes
    // `NotificationRepository.getNotifications` usaba `orDefault(emptyList())`,
    // vaciando el badge/lista ante un fallo de red puntual en vez de servir
    // la última foto conocida.

    /** Sobrescribe la caché de notificaciones de [householdId] con [notifications] (llamado tras cada lectura exitosa de red). */
    fun cacheNotifications(householdId: String, notifications: List<NotificationResponse>) {
        putSafely("cache_notifications_$householdId", json.encodeToString(notifications))
    }

    /** Notificaciones cacheadas de [householdId], o `null` si no hay caché o el JSON guardado está corrupto. */
    fun getCachedNotifications(householdId: String): List<NotificationResponse>? {
        val raw = settings.getStringOrNull("cache_notifications_$householdId") ?: return null
        return try {
            json.decodeFromString<List<NotificationResponse>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Invalida la caché de notificaciones de un hogar. Ver [clearTasks]. */
    fun clearNotifications(householdId: String) {
        settings.remove("cache_notifications_$householdId")
    }

    // ── Invalidation ────────────────────────────────────────

    /**
     * Borra toda la caché local de un hogar (tareas, datos del hogar, miembros).
     * Debe llamarse al borrar el hogar o al abandonarlo — de lo contrario, sus
     * datos quedan huérfanos en disco indefinidamente aunque [HouseholdStore]
     * ya no lo liste, y una futura reutilización del mismo ID (poco probable
     * pero posible) vería datos obsoletos.
     */
    fun clearHousehold(householdId: String) {
        // Las asignaciones se cachean por tarea, no por hogar (ver
        // [cacheAssignments]) — sin un índice propio, se purgan a partir de
        // las tareas aún cacheadas antes de borrar esa caché. Si no hay
        // caché de tareas, no hay forma de conocer sus ids: los huérfanos
        // eventuales expiran solos cuando el mismo taskId se reutiliza (poco
        // probable) o dejan de leerse.
        getCachedTasks(householdId)?.forEach { clearAssignments(householdId, it.id) }
        clearTasks(householdId)
        clearHouseholdDoc(householdId)
        clearMembers(householdId)
        clearTaskHistory(householdId)
        clearRewards(householdId)
        clearRewardRedemptions(householdId)
        clearNotifications(householdId)
    }

    /**
     * Invalida solo la caché de tareas de un hogar. Debe llamarse tras
     * cualquier escritura que modifique tareas (crear/editar/borrar/completar)
     * para que una lectura offline posterior no sirva el estado previo a la
     * mutación en vez de fallar de forma visible (ver `getTasks`, que hace
     * fallback a caché ante error de red).
     */
    fun clearTasks(householdId: String) {
        settings.remove("cache_tasks_$householdId")
    }

    /** Invalida solo la caché del documento de hogar. Ver [clearTasks]. */
    fun clearHouseholdDoc(householdId: String) {
        settings.remove("cache_household_$householdId")
    }

    /**
     * Invalida solo la caché de miembros de un hogar. Debe llamarse tras
     * cualquier escritura que modifique miembros (puntos, rol, alta, baja).
     * Ver [clearTasks].
     */
    fun clearMembers(householdId: String) {
        settings.remove("cache_members_$householdId")
    }
}