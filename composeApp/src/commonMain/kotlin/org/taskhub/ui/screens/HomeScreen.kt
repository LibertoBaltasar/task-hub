package org.taskhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SavedHousehold
import org.taskhub.storage.SettingsStore
import org.taskhub.ui.components.HouseholdTaskSection
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
import org.taskhub.ui.models.GoogleAuthManager
import org.taskhub.ui.models.GoogleAuthState
import org.taskhub.ui.models.HomeScreenModel
import org.taskhub.ui.theme.Teal600

/**
 * Pantalla principal de la app — dashboard unificado que muestra las tareas
 * de todos los hogares del usuario (incluyendo el espacio Personal).
 *
 * Reemplaza a [WelcomeScreen] como landing page.
 */
class HomeScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdStore = koinInject<HouseholdStore>()
        val appSettings = LocalAppSettings.current
        val model = koinScreenModel<HomeScreenModel>()
        val uiState by model.uiState.collectAsState()
        val settingsStore = koinInject<SettingsStore>()
        val authManager = koinInject<GoogleAuthManager>()
        val authState by authManager.state.collectAsState()

        var households by remember { mutableStateOf<List<SavedHousehold>>(emptyList()) }
        var showFabMenu by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        // Prompt de login con Google en el primer arranque (solo si aún no ha iniciado sesión)
        var showGooglePrompt by remember {
            mutableStateOf(!settingsStore.hasSeenGooglePrompt() && !settingsStore.isGoogleLoggedIn())
        }

        // Cargar hogares + tareas de todos al entrar
        LaunchedEffect(Unit) {
            households = householdStore.getSavedHouseholds()
            model.loadAllTasks()
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
                            onDismiss = { showSettings = false }
                        )
                    )
                }
            }
        }

        // Google login prompt (primer arranque)
        if (showGooglePrompt) {
            AlertDialog(
                onDismissRequest = {
                    showGooglePrompt = false
                    settingsStore.setHasSeenGooglePrompt(true)
                },
                title = { Text("Guarda tus datos con Google") },
                text = {
                    Column {
                        Text(
                            "Inicia sesión con Google para que tus tareas y hogares se " +
                                "guarden en la nube. Así no los pierdes si cambias de móvil o reinstalas."
                        )
                        if (authState is GoogleAuthState.SigningIn) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Teal600
                                )
                                Text("Conectando con Google...")
                            }
                        }
                        if (authState is GoogleAuthState.Error) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "❌ ${(authState as GoogleAuthState.Error).message}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            settingsStore.setHasSeenGooglePrompt(true)
                            showGooglePrompt = false
                            authManager.signIn()
                        },
                        enabled = authState !is GoogleAuthState.SigningIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                    ) {
                        Text("Iniciar sesión con Google")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showGooglePrompt = false
                            settingsStore.setHasSeenGooglePrompt(true)
                        }
                    ) {
                        Text("Ahora no")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Task Hub", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = {
                            navigator.push(ProfileScreen(households))
                        }) {
                            Icon(Icons.Default.Person, "Perfil")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(visible = showFabMenu) {
                        Column(horizontalAlignment = Alignment.End) {
                            FloatingActionButton(
                                onClick = {
                                    navigator.push(CreateHouseholdScreen())
                                    showFabMenu = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Add, "Crear hogar", modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            FloatingActionButton(
                                onClick = {
                                    navigator.push(JoinHouseholdScreen())
                                    showFabMenu = false
                                },
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Add, "Unirse a hogar", modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = Teal600
                    ) {
                        Icon(Icons.Default.Add, "Nuevo hogar")
                    }
                }
            }
        ) { padding ->
            if (uiState.isLoading && households.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Teal600)
                        Spacer(Modifier.height(16.dp))
                        Text("Preparando tu espacio...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Contador total
                    item {
                        Text(
                            "${uiState.pendingCount} tareas pendientes en ${households.size} espacios",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val personal = households.find { it.isPersonal }
                    if (personal != null) {
                        item(key = "personal") {
                            HouseholdTaskSection(
                                household = personal,
                                onViewAll = { hid -> navigator.push(PersonalSpaceScreen(hid)) }
                            )
                        }
                    }

                    val shared = households.filter { !it.isPersonal }
                    if (shared.isNotEmpty()) {
                        item(key = "shared_header") {
                            Text(
                                "Mis hogares",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(shared.size, key = { shared[it].id }) { index ->
                            HouseholdTaskSection(
                                household = shared[index],
                                onViewAll = { hid -> navigator.push(HouseholdScreen(hid)) }
                            )
                        }
                    }

                    // Mensaje de error si lo hay
                    if (uiState.error != null) {
                        item {
                            Text(
                                uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}