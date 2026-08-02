package org.taskhub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.taskhub.storage.SettingsStore
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.theme.TaskHubThemeType
import org.taskhub.ui.theme.Teal600
import org.taskhub.platform.saveWidgetThemeToCache

/**
 * Callbacks that the settings sheet needs from its host screen.
 */
data class SettingsCallbacks(
    val onExportCsv: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * A full-screen dialog-like settings sheet.
 * Shows: notifications toggle, theme selector, language selector, CSV export.
 * Reads/writes preferences via [SettingsStore]. Changes are applied immediately.
 * Theme and language changes bubble through [LocalAppSettings].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    callbacks: SettingsCallbacks
) {
    val settingsStore = koinInject<SettingsStore>()
    val appSettings = LocalAppSettings.current

    var notificationsEnabled by remember {
        mutableStateOf(settingsStore.isNotificationsEnabled())
    }

    var widgetTheme by remember {
        mutableStateOf(settingsStore.getWidgetTheme())
    }

    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title
        Text(
            text = s("settings_title"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(24.dp))

        // ── Notifications ────────────────────────────────
        SettingsSection(title = s("settings_notifications")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s("settings_notifications"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = s("settings_notifications_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        notificationsEnabled = enabled
                        settingsStore.setNotificationsEnabled(enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Teal600,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Theme ────────────────────────────────────────
        SettingsSection(title = s("settings_theme")) {
            ThemeOption(
                label = s("theme_default"),
                selected = appSettings.currentTheme == TaskHubThemeType.DEFAULT,
                onClick = { appSettings.onThemeChanged(TaskHubThemeType.DEFAULT) }
            )
            ThemeOption(
                label = s("theme_naturaleza"),
                selected = appSettings.currentTheme == TaskHubThemeType.NATURALEZA,
                onClick = { appSettings.onThemeChanged(TaskHubThemeType.NATURALEZA) }
            )
            ThemeOption(
                label = s("theme_minimal"),
                selected = appSettings.currentTheme == TaskHubThemeType.MINIMAL,
                onClick = { appSettings.onThemeChanged(TaskHubThemeType.MINIMAL) }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Language ─────────────────────────────────────
        SettingsSection(title = s("settings_language")) {
            LanguageOption(
                label = s("lang_spanish"),
                selected = appSettings.currentLanguage == "es",
                onClick = { appSettings.onLanguageChanged("es") }
            )
            LanguageOption(
                label = s("lang_english"),
                selected = appSettings.currentLanguage == "en",
                onClick = { appSettings.onLanguageChanged("en") }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Widget Theme ────────────────────────────────
        SettingsSection(title = "Tema del widget") {
            WidgetThemeOption(
                label = "Claro",
                selected = widgetTheme == "light",
                onClick = {
                    widgetTheme = "light"
                    settingsStore.setWidgetTheme("light")
                    saveWidgetThemeToCache("light")
                }
            )
            WidgetThemeOption(
                label = "Oscuro",
                selected = widgetTheme == "dark",
                onClick = {
                    widgetTheme = "dark"
                    settingsStore.setWidgetTheme("dark")
                    saveWidgetThemeToCache("dark")
                }
            )
            WidgetThemeOption(
                label = "Sistema",
                selected = widgetTheme == "system",
                onClick = {
                    widgetTheme = "system"
                    settingsStore.setWidgetTheme("system")
                    saveWidgetThemeToCache("system")
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Export CSV ───────────────────────────────────
        Button(
            onClick = {
                callbacks.onDismiss()
                callbacks.onExportCsv()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Teal600),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = s("settings_export_csv"),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(16.dp))

        // Close button
        OutlinedButton(
            onClick = callbacks.onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Text(s("settings_close"))
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Teal600
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Teal600
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Teal600
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun WidgetThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Teal600
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}