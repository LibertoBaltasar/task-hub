package org.taskhub.storage

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse
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

    // ── Tasks ───────────────────────────────────────────────

    fun cacheTasks(householdId: String, tasks: List<TaskResponse>) {
        val key = "cache_tasks_$householdId"
        settings.putString(key, json.encodeToString(tasks))
    }

    fun getCachedTasks(householdId: String): List<TaskResponse>? {
        val key = "cache_tasks_$householdId"
        val raw = settings.getStringOrNull(key) ?: return null
        return try {
            json.decodeFromString<List<TaskResponse>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    // ── Household ───────────────────────────────────────────

    fun cacheHousehold(household: HouseholdResponse) {
        val key = "cache_household_${household.id}"
        settings.putString(key, json.encodeToString(household))
    }

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

    fun cacheMembers(householdId: String, members: List<MemberResponse>) {
        val key = "cache_members_$householdId"
        settings.putString(key, json.encodeToString(members))
    }

    fun getCachedMembers(householdId: String): List<MemberResponse>? {
        val key = "cache_members_$householdId"
        val raw = settings.getStringOrNull(key) ?: return null
        return try {
            json.decodeFromString<List<MemberResponse>>(raw)
        } catch (_: Exception) {
            null
        }
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
        settings.remove("cache_tasks_$householdId")
        settings.remove("cache_household_$householdId")
        settings.remove("cache_members_$householdId")
    }
}