package org.taskhub.ui.components

import androidx.compose.runtime.compositionLocalOf
import org.taskhub.ui.theme.TaskHubThemeType

/**
 * Composition local for app-wide settings state and callbacks.
 * Provided by App.kt at the root level. All screens consume it.
 */
data class AppSettingsState(
    val currentLanguage: String,
    val currentTheme: TaskHubThemeType,
    val onThemeChanged: (TaskHubThemeType) -> Unit,
    val onLanguageChanged: (String) -> Unit,
    val onExportCsv: () -> Unit
)

val LocalAppSettings = compositionLocalOf<AppSettingsState> {
    error("LocalAppSettings not provided — make sure App.kt wraps content with CompositionLocalProvider")
}