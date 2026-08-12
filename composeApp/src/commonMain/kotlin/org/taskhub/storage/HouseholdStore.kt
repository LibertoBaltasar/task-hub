package org.taskhub.storage

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Locally-saved household reference (ID + display info).
 * Used to find households across app restarts without relying on
 * Firestore collection-group queries (which break when anonymous auth
 * generates a new localId each session).
 */
@Serializable
data class SavedHousehold(
    val id: String,
    val name: String,
    val inviteCode: String,
    /** True si es el espacio "Personal" auto-creado (sin invitaciones). */
    val isPersonal: Boolean = false
)

/**
 * Persists household IDs locally via [Settings] (SharedPreferences on Android,
 * NSUserDefaults on iOS).
 *
 * Households whose anonymous auth localId changes every session —
 * the local store is the stable reference for "which households have I joined?".
 */
class HouseholdStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Save a household to local storage (no duplicates; updates if already present).
     */
    fun saveHousehold(
        householdId: String,
        householdName: String,
        inviteCode: String,
        isPersonal: Boolean = false
    ) {
        val current = getSavedHouseholds().toMutableList()
        val existing = current.indexOfFirst { it.id == householdId }
        val entry = SavedHousehold(
            id = householdId,
            name = householdName,
            inviteCode = inviteCode,
            isPersonal = isPersonal
        )
        if (existing >= 0) {
            current[existing] = entry
        } else {
            current.add(entry)
        }
        settings.putString(KEY_SAVED_HOUSEHOLDS, json.encodeToString(current))
    }

    /**
     * Returns all locally-saved households.
     */
    fun getSavedHouseholds(): List<SavedHousehold> {
        val raw = settings.getString(KEY_SAVED_HOUSEHOLDS, "")
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<SavedHousehold>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Remove a household from local storage by its ID.
     */
    fun removeHousehold(householdId: String) {
        val current = getSavedHouseholds().toMutableList()
        current.removeAll { it.id == householdId }
        settings.putString(KEY_SAVED_HOUSEHOLDS, json.encodeToString(current))
    }

    // ── Personal space ─────────────────────────────────────

    /**
     * Returns the ID of the auto-created "Personal" household, or null if
     * it hasn't been created yet (first app launch).
     */
    fun getPersonalHouseholdId(): String? {
        return settings.getString(KEY_PERSONAL_HOUSEHOLD_ID, "").ifEmpty { null }
    }

    /**
     * Persist the Personal household ID separately so it can be queried
     * without scanning all households.
     */
    fun savePersonalHousehold(id: String) {
        settings.putString(KEY_PERSONAL_HOUSEHOLD_ID, id)
    }

    companion object {
        private const val KEY_SAVED_HOUSEHOLDS = "taskhub_saved_households"
        private const val KEY_PERSONAL_HOUSEHOLD_ID = "taskhub_personal_household_id"
    }
}