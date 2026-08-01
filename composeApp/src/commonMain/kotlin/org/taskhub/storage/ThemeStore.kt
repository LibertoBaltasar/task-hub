package org.taskhub.storage

import com.russhwolf.settings.Settings
import org.taskhub.ui.theme.TaskHubThemeType

/**
 * Persists the selected theme preference.
 */
class ThemeStore(private val settings: Settings) {

    fun getTheme(): TaskHubThemeType {
        return when (settings.getString(KEY_THEME, "DEFAULT")) {
            "NATURALEZA" -> TaskHubThemeType.NATURALEZA
            "MINIMAL" -> TaskHubThemeType.MINIMAL
            else -> TaskHubThemeType.DEFAULT
        }
    }

    fun setTheme(theme: TaskHubThemeType) {
        settings.putString(
            KEY_THEME,
            when (theme) {
                TaskHubThemeType.NATURALEZA -> "NATURALEZA"
                TaskHubThemeType.MINIMAL -> "MINIMAL"
                else -> "DEFAULT"
            }
        )
    }

    companion object {
        private const val KEY_THEME = "taskhub_theme"
    }
}