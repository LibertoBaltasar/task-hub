package org.taskhub.storage

import com.russhwolf.settings.Settings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists user settings: notifications, language, and theme preference.
 */
class SettingsStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Notifications ─────────────────────────────────────

    fun isNotificationsEnabled(): Boolean =
        settings.getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(enabled: Boolean) =
        settings.putBoolean(KEY_NOTIFICATIONS, enabled)

    // ── Language ──────────────────────────────────────────

    fun getLanguage(): String =
        settings.getString(KEY_LANGUAGE, "es")

    fun setLanguage(lang: String) =
        settings.putString(KEY_LANGUAGE, lang)

    // ── Theme ─────────────────────────────────────────────

    fun getTheme(): String =
        settings.getString(KEY_THEME, "DEFAULT")

    fun setTheme(theme: String) =
        settings.putString(KEY_THEME, theme)

    // ── Widget Theme ──────────────────────────────────────

    fun getWidgetTheme(): String =
        settings.getString(KEY_WIDGET_THEME, "system")

    fun setWidgetTheme(theme: String) =
        settings.putString(KEY_WIDGET_THEME, theme)

    // ── Sound & Haptics ─────────────────────────────────

    fun isSoundEnabled(): Boolean =
        settings.getBoolean(KEY_SOUND_ENABLED, true)

    fun setSoundEnabled(enabled: Boolean) =
        settings.putBoolean(KEY_SOUND_ENABLED, enabled)

    fun isVibrationEnabled(): Boolean =
        settings.getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setVibrationEnabled(enabled: Boolean) =
        settings.putBoolean(KEY_VIBRATION_ENABLED, enabled)

    // ── Google Calendar ──────────────────────────────────

    fun hasGoogleLinked(): Boolean =
        settings.getStringOrNull(KEY_GOOGLE_ACCESS_TOKEN) != null

    fun getGoogleAccessToken(): String? =
        settings.getStringOrNull(KEY_GOOGLE_ACCESS_TOKEN)

    fun setGoogleAccessToken(token: String?) {
        if (token != null) {
            settings.putString(KEY_GOOGLE_ACCESS_TOKEN, token)
        } else {
            settings.remove(KEY_GOOGLE_ACCESS_TOKEN)
        }
    }

    // ── Google Auth (login) ──────────────────────────────

    fun isGoogleLoggedIn(): Boolean =
        settings.getStringOrNull(KEY_GOOGLE_UID) != null

    fun getGoogleUid(): String? =
        settings.getStringOrNull(KEY_GOOGLE_UID)

    fun getGoogleEmail(): String? =
        settings.getStringOrNull(KEY_GOOGLE_EMAIL)

    fun setGoogleAuth(uid: String?, email: String?) {
        if (uid != null) {
            settings.putString(KEY_GOOGLE_UID, uid)
        } else {
            settings.remove(KEY_GOOGLE_UID)
        }
        if (email != null) {
            settings.putString(KEY_GOOGLE_EMAIL, email)
        } else {
            settings.remove(KEY_GOOGLE_EMAIL)
        }
    }

    /** Refresh token de Firebase del login Google (para restaurar la sesión). */
    fun getGoogleRefreshToken(): String? =
        settings.getStringOrNull(KEY_GOOGLE_REFRESH_TOKEN)

    fun setGoogleRefreshToken(token: String?) {
        if (token != null) {
            settings.putString(KEY_GOOGLE_REFRESH_TOKEN, token)
        } else {
            settings.remove(KEY_GOOGLE_REFRESH_TOKEN)
        }
    }

    fun clearGoogleAuth() {
        settings.remove(KEY_GOOGLE_UID)
        settings.remove(KEY_GOOGLE_EMAIL)
        settings.remove(KEY_GOOGLE_REFRESH_TOKEN)
    }

    /** Si el usuario ya vio el prompt de login con Google en el primer arranque. */
    fun hasSeenGooglePrompt(): Boolean =
        settings.getBoolean(KEY_GOOGLE_PROMPT_SEEN, false)

    fun setHasSeenGooglePrompt(seen: Boolean) =
        settings.putBoolean(KEY_GOOGLE_PROMPT_SEEN, seen)

    // ── Auth anónima persistente ─────────────────────────
    //
    // Firebase Auth anónimo genera un UID nuevo en cada signUp. Para que el
    // usuario anónimo conserve SU identidad (y sus datos) entre reinicios y
    // reinstalaciones, guardamos el refresh token del usuario anónimo. Con él
    // se renueva el idToken sin crear una identidad nueva (mismo UID).

    fun getAnonymousRefreshToken(): String? =
        settings.getStringOrNull(KEY_ANON_REFRESH_TOKEN)

    fun getAnonymousUid(): String? =
        settings.getStringOrNull(KEY_ANON_UID)

    fun saveAnonymousAuth(refreshToken: String, uid: String) {
        settings.putString(KEY_ANON_REFRESH_TOKEN, refreshToken)
        settings.putString(KEY_ANON_UID, uid)
    }

    fun clearAnonymousAuth() {
        settings.remove(KEY_ANON_REFRESH_TOKEN)
        settings.remove(KEY_ANON_UID)
    }

    // ── Google Calendar sync (calendarId por hogar) ──────
    //
    // Puntero LOCAL a un calendario ya creado en Google (uno por hogar/espacio
    // Personal, por-dispositivo). El calendario en sí vive en la cuenta de
    // Google del usuario; esto solo evita crear uno nuevo cada vez. Limitación
    // conocida: un segundo dispositivo crearía su propio calendario — aceptado
    // para el MVP.

    fun getCalendarId(householdId: String): String? = getCalendarIdMap()[householdId]

    fun setCalendarId(householdId: String, calendarId: String) {
        val map = getCalendarIdMap().toMutableMap()
        map[householdId] = calendarId
        settings.putString(KEY_CALENDAR_IDS, json.encodeToString(map))
    }

    private fun getCalendarIdMap(): Map<String, String> {
        val raw = settings.getString(KEY_CALENDAR_IDS, "")
        if (raw.isEmpty()) return emptyMap()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val KEY_NOTIFICATIONS = "taskhub_notifications"
        private const val KEY_LANGUAGE = "taskhub_language"
        private const val KEY_THEME = "taskhub_theme"
        private const val KEY_WIDGET_THEME = "taskhub_widget_theme"
        private const val KEY_SOUND_ENABLED = "taskhub_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "taskhub_vibration_enabled"
        private const val KEY_GOOGLE_ACCESS_TOKEN = "taskhub_google_token"
        private const val KEY_GOOGLE_UID = "taskhub_google_uid"
        private const val KEY_GOOGLE_EMAIL = "taskhub_google_email"
        private const val KEY_GOOGLE_REFRESH_TOKEN = "taskhub_google_refresh_token"
        private const val KEY_GOOGLE_PROMPT_SEEN = "taskhub_google_prompt_seen"
        private const val KEY_ANON_REFRESH_TOKEN = "taskhub_anon_refresh_token"
        private const val KEY_ANON_UID = "taskhub_anon_uid"
        private const val KEY_CALENDAR_IDS = "taskhub_calendar_ids"
    }
}