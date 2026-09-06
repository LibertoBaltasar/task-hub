// CompositionLocal con los ajustes globales de la app (idioma/tema activos +
// sus callbacks de cambio). App.kt lo provee una única vez en la raíz del
// árbol de Compose; el resto de pantallas y componentes lo leen vía
// `LocalAppSettings.current` en vez de recibirlo como parámetro en cascada.
package org.taskhub.ui.components

import androidx.compose.runtime.compositionLocalOf
import org.taskhub.ui.theme.TaskHubThemeType

/**
 * Estado y callbacks de ajustes globales expuestos vía [LocalAppSettings].
 *
 * @param currentLanguage código de idioma activo ("es"/"en"), usado como
 *   parámetro `lang` de [org.taskhub.ui.i18n.AppStrings.get].
 * @param currentTheme tema visual activo (Default/Naturaleza/Minimal).
 * @param onThemeChanged callback para cambiar el tema (persiste + recompone).
 * @param onLanguageChanged callback para cambiar el idioma (persiste + recompone).
 */
data class AppSettingsState(
    val currentLanguage: String,
    val currentTheme: TaskHubThemeType,
    val onThemeChanged: (TaskHubThemeType) -> Unit,
    val onLanguageChanged: (String) -> Unit
)

/**
 * Acceso al [AppSettingsState] vigente. Lanza error si se lee sin que
 * `App.kt` haya envuelto el contenido con `CompositionLocalProvider` — señal
 * de que el composable se está usando/previsualizando fuera del árbol real
 * de la app.
 */
val LocalAppSettings = compositionLocalOf<AppSettingsState> {
    error("LocalAppSettings not provided — make sure App.kt wraps content with CompositionLocalProvider")
}