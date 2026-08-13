package org.taskhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.taskhub.di.appModule
import org.taskhub.network.FirestoreRepository
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SettingsStore
import org.taskhub.ui.components.AppSettingsState
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.screens.HomeScreen
import org.taskhub.ui.screens.SplashScreen
import org.taskhub.ui.theme.TaskHubTheme
import org.taskhub.ui.theme.TaskHubThemeType
import org.taskhub.ui.theme.Teal600

@Composable
fun App() {
    // ── Fase 1: Splash screen (5 segundos) ─────────────────
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    // ── Fase 2: App normal ─────────────────────────────────
    KoinApplication(application = {
        modules(appModule)
    }) {
        val settingsStore = koinInject<SettingsStore>()

        // Reactive theme from settings
        var themeType by remember {
            mutableStateOf(
                when (settingsStore.getTheme()) {
                    "NATURALEZA" -> TaskHubThemeType.NATURALEZA
                    "MINIMAL" -> TaskHubThemeType.MINIMAL
                    else -> TaskHubThemeType.DEFAULT
                }
            )
        }

        // Reactive language from settings
        var currentLanguage by remember {
            mutableStateOf(settingsStore.getLanguage())
        }

        // CSV export callback — default no-op unless overridden by a screen
        var exportCsvCallback by remember {
            mutableStateOf<(() -> Unit)?>(null)
        }

        val appSettings = remember(themeType, currentLanguage) {
            AppSettingsState(
                currentLanguage = currentLanguage,
                currentTheme = themeType,
                onThemeChanged = { newTheme ->
                    themeType = newTheme
                    settingsStore.setTheme(
                        when (newTheme) {
                            TaskHubThemeType.NATURALEZA -> "NATURALEZA"
                            TaskHubThemeType.MINIMAL -> "MINIMAL"
                            else -> "DEFAULT"
                        }
                    )
                },
                onLanguageChanged = { newLang ->
                    currentLanguage = newLang
                    settingsStore.setLanguage(newLang)
                },
                onExportCsv = { exportCsvCallback?.invoke() }
            )
        }

        TaskHubTheme(themeType = themeType) {
            CompositionLocalProvider(LocalAppSettings provides appSettings) {
                val householdStore = koinInject<HouseholdStore>()
                val repo = koinInject<FirestoreRepository>()

                var initialScreen by remember { mutableStateOf<Screen?>(null) }

                LaunchedEffect(Unit) {
                    // ── Auto-crear espacio Personal si no existe ──────
                    var personalId = householdStore.getPersonalHouseholdId()
                    if (personalId == null) {
                        try {
                            val personal = repo.createHousehold("Personal", isPersonal = true)
                            householdStore.savePersonalHousehold(personal.id)
                            householdStore.saveHousehold(
                                householdId = personal.id,
                                householdName = "Personal",
                                inviteCode = "",
                                isPersonal = true
                            )
                            personalId = personal.id
                        } catch (_: Exception) {
                            // Sin conexión: crear solo localmente como placeholder
                            personalId = "personal-offline"
                            householdStore.savePersonalHousehold(personalId)
                            householdStore.saveHousehold(
                                householdId = personalId,
                                householdName = "Personal",
                                inviteCode = "",
                                isPersonal = true
                            )
                        }
                    }

                    // ── Asegurar que el espacio Personal tenga un miembro "Yo" ──
                    // Para que completar tareas sepa quién las hace (cubre migración).
                    if (!personalId.isNullOrBlank() && personalId != "personal-offline") {
                        try {
                            repo.ensurePersonalMember(personalId)
                        } catch (_: Exception) {
                            // No crítico: si falla (offline), se reintenta al reabrir
                        }
                    }

                    // ── Ir siempre a HomeScreen ───────────────────────
                    initialScreen = HomeScreen()
                }

                // Surface paints the background behind system bars (edge-to-edge)
                // Inner Box applies system bar padding so content doesn't overlap
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        when (val screen = initialScreen) {
                            null -> {
                                // Still loading
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Teal600)
                                }
                            }
                            else -> {
                                Navigator(screen = screen) { navigator ->
                                    SlideTransition(navigator)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}