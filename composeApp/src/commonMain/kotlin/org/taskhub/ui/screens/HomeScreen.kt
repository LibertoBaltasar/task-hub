package org.taskhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.storage.SavedHousehold
import org.taskhub.ui.components.AppLogo
import org.taskhub.ui.components.EmptyHouseholdsIllustration
import org.taskhub.ui.components.HouseholdTaskSection
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
import org.taskhub.ui.components.ShimmerList
import org.taskhub.ui.components.shouldReduceMotion
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.GoogleAuthManager
import org.taskhub.ui.models.GoogleAuthState
import org.taskhub.ui.models.HomeScreenModel
import org.taskhub.platform.AdBannerSlot

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
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
        val model = koinScreenModel<HomeScreenModel>()
        val uiState by model.uiState.collectAsState()
        val previewTasks by model.previewTasks.collectAsState()
        val reduceMotion = shouldReduceMotion()
        val authManager = koinInject<GoogleAuthManager>()
        val authState by authManager.state.collectAsState()

        var households by remember { mutableStateOf<List<SavedHousehold>>(emptyList()) }
        var showFabMenu by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        // Prompt de login con Google en el primer arranque (solo si aún no ha iniciado sesión)
        var showGooglePrompt by remember { mutableStateOf(model.shouldShowGooglePrompt()) }

        // Cargar hogares + tareas de todos al entrar. Reconcilia primero contra
        // Firestore para podar hogares "fantasma" (borrados o sin acceso).
        LaunchedEffect(Unit) {
            households = model.reconcileHouseholds()
            model.loadAllTasks()
        }

        // Cierra el prompt de Google solo cuando el login realmente tiene éxito.
        // Antes se cerraba de forma síncrona al pulsar el botón, así que si el
        // login fallaba el usuario nunca llegaba a ver el estado SigningIn/Error
        // (el diálogo ya había desaparecido).
        LaunchedEffect(authState) {
            if (showGooglePrompt && authState is GoogleAuthState.SignedIn) {
                showGooglePrompt = false
            }
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
                            showExportCsv = false // exportar disponible desde la lista de tareas
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
                    model.markGooglePromptSeen()
                },
                title = { Text(s("home_google_prompt_title")) },
                text = {
                    Column {
                        Text(s("home_google_prompt_body"))
                        if (authState is GoogleAuthState.SigningIn) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(s("settings_account_connecting"))
                            }
                        }
                        if (authState is GoogleAuthState.Error) {
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = s("error_icon_content_desc"),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = (authState as GoogleAuthState.Error).message,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            model.markGooglePromptSeen()
                            authManager.signIn()
                        },
                        enabled = authState !is GoogleAuthState.SigningIn,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(s("settings_account_sign_in_google"))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showGooglePrompt = false
                            model.markGooglePromptSeen()
                        }
                    ) {
                        Text(s("home_google_prompt_dismiss"))
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppLogo(size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Task Hub", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            navigator.push(ProfileScreen(households))
                        }) {
                            Icon(Icons.Default.Person, s("profile_title"))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, s("profile_settings_label"))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(
                        visible = showFabMenu,
                        enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandVertically(),
                        exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkVertically()
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    navigator.push(CreateHouseholdScreen())
                                    showFabMenu = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                icon = { Icon(Icons.Default.Add, contentDescription = s("welcome_create"), modifier = Modifier.size(20.dp)) },
                                text = { Text(s("welcome_create"), fontWeight = FontWeight.SemiBold) }
                            )
                            Spacer(Modifier.height(12.dp))
                            ExtendedFloatingActionButton(
                                onClick = {
                                    navigator.push(JoinHouseholdScreen())
                                    showFabMenu = false
                                },
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                                icon = { Icon(Icons.Default.Home, contentDescription = s("home_fab_join_space"), modifier = Modifier.size(20.dp)) },
                                text = { Text(s("home_fab_join_space"), fontWeight = FontWeight.SemiBold) }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (showFabMenu) {
                            Icon(Icons.Default.Close, s("settings_close"))
                        } else {
                            Icon(Icons.Default.Add, s("home_fab_add_space"))
                        }
                    }
                }
            }
        ) { padding ->
            if (uiState.isLoading && households.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                ) {
                    ShimmerList(count = 4, itemHeight = 96.dp)
                }
            } else if (households.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyHouseholdsIllustration()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            s("home_empty_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s("home_empty_subtitle"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { navigator.push(CreateHouseholdScreen()) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s("welcome_create"), fontWeight = FontWeight.SemiBold)
                        }
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
                            s("home_pending_count_summary")
                                .replace("%1", uiState.pendingCount.toString())
                                .replace("%2", households.size.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val personal = households.find { it.isPersonal }
                    if (personal != null) {
                        item(key = "personal") {
                            LaunchedEffect(personal.id) { model.loadHouseholdPreview(personal.id) }
                            HouseholdTaskSection(
                                household = personal,
                                previewState = previewTasks[personal.id],
                                onViewAll = { hid -> navigator.push(PersonalSpaceScreen(hid)) }
                            )
                        }
                    }

                    val shared = households.filter { !it.isPersonal }
                    if (shared.isNotEmpty()) {
                        item(key = "shared_header") {
                            Text(
                                s("home_my_spaces"),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(shared.size, key = { shared[it].id }) { index ->
                            val h = shared[index]
                            LaunchedEffect(h.id) { model.loadHouseholdPreview(h.id) }
                            HouseholdTaskSection(
                                household = h,
                                previewState = previewTasks[h.id],
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

                    // Banner de anuncios (preparado; deshabilitado por defecto).
                    // Se coloca al final del contenido principal, encima de
                    // cualquier barra inferior.
                    item(key = "ad_banner") {
                        AdBannerSlot()
                    }
                }
            }
        }
    }
}