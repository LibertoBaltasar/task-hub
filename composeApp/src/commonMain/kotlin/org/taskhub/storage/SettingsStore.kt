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

    companion object {
        private const val KEY_NOTIFICATIONS = "taskhub_notifications"
        private const val KEY_LANGUAGE = "taskhub_language"
        private const val KEY_THEME = "taskhub_theme"
        private const val KEY_WIDGET_THEME = "taskhub_widget_theme"
    }
}