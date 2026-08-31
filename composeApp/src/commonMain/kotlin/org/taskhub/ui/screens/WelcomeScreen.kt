package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.network.FirestoreRepository
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SavedHousehold
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.i18n.AppStrings

class WelcomeScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdStore = koinInject<HouseholdStore>()
        val repo = koinInject<FirestoreRepository>()
        var savedHouseholds by remember { mutableStateOf<List<SavedHousehold>>(householdStore.getSavedHouseholds()) }
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        var showSettings by remember { mutableStateOf(false) }

        // Reconcilia contra Firestore para podar hogares "fantasma" (borrados o sin acceso).
        LaunchedEffect(Unit) {
            savedHouseholds = repo.reconcileHouseholds(householdStore)
        }

        // Settings dialog
        if (showSettings) {
            Dialog(
                onDismissRequest = { showSettings = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.85f),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SettingsSheet(
                        callbacks = SettingsCallbacks(
                            onExportCsv = { },
                            onDismiss = { showSettings = false },
                            onEditProfile = { navigator.push(EditProfileScreen()) },
                            showExportCsv = false // No tasks to export on welcome screen
                        )
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = "Task Hub",
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = s("profile_settings_label"))
                        }
                    }
                )

                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "👥",
                        style = MaterialTheme.typography.displayLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = s("welcome_title"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = s("welcome_subtitle"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = { navigator.push(CreateHouseholdScreen()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = s("welcome_create"),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { navigator.push(JoinHouseholdScreen()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = s("welcome_join"),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (savedHouseholds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { navigator.replaceAll(HomeScreen()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = s("welcome_my_households"),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        // Sin acceso multiplataforma trivial al versionName de Gradle desde
                        // commonMain: recuerda actualizar este literal en cada
                        // "chore: bump versión X.Y.Z" (ver CLAUDE.md).
                        text = "v0.7.23",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}