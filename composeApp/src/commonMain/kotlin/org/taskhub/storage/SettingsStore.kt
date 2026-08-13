package org.taskhub.storage

import com.russhwolf.settings.Settings

/**
 * Persists user settings: notifications, language, and theme preference.
 */
class SettingsStore(private val settings: Settings) {

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

    fun clearGoogleAuth() {
        settings.remove(KEY_GOOGLE_UID)
        settings.remove(KEY_GOOGLE_EMAIL)
    }

    /** Si el usuario ya vio el prompt de login con Google en el primer arranque. */
    fun hasSeenGooglePrompt(): Boolean =
        settings.getBoolean(KEY_GOOGLE_PROMPT_SEEN, false)

    fun setHasSeenGooglePrompt(seen: Boolean) =
        settings.putBoolean(KEY_GOOGLE_PROMPT_SEEN, seen)

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
        private const val KEY_GOOGLE_PROMPT_SEEN = "taskhub_google_prompt_seen"
    }
}