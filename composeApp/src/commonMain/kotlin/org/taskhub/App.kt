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
import cafe.adriel.voyager.transitions.FadeTransition
import cafe.adriel.voyager.transitions.SlideTransition
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.taskhub.di.appModule
import org.taskhub.network.FirestoreRepository
import org.taskhub.platform.NotificationScheduler
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SettingsStore
import org.taskhub.ui.components.AppSettingsState
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.shouldReduceMotion
import org.taskhub.ui.models.GoogleAuthManager
import org.taskhub.ui.screens.HomeScreen
import org.taskhub.ui.screens.SplashScreen
import org.taskhub.ui.theme.TaskHubTheme
import org.taskhub.ui.theme.TaskHubThemeType
import org.taskhub.ui.theme.Teal600

@Composable
fun App() {
    // ── Fase 1: Splash screen (1.5 segundos) ─────────────────
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
                }
            )
        }

        TaskHubTheme(themeType = themeType) {
            CompositionLocalProvider(LocalAppSettings provides appSettings) {
                val householdStore = koinInject<HouseholdStore>()
                val repo = koinInject<FirestoreRepository>()
                val authManager = koinInject<GoogleAuthManager>()
                val notificationScheduler = koinInject<NotificationScheduler>()

                var initialScreen by remember { mutableStateOf<Screen?>(null) }

                LaunchedEffect(Unit) {
                    // ── Resolver/crear el espacio Personal (interdispositivo) ──
                    // El ID es determinista (personal_{uid}), de modo que con la
                    // misma cuenta de Google todos los dispositivos apuntan al
                    // MISMO hogar. En modo anónimo sigue siendo por-dispositivo.
                    var personalId: String? = null
                    try {
                        val personal = repo.getOrCreatePersonalHousehold()
                        householdStore.replacePersonalHousehold(personal.id)
                        personalId = personal.id
                    } catch (_: Exception) {
                        // Sin conexión: recurrir al guardado local o a un placeholder.
                        personalId = householdStore.getPersonalHouseholdId()
                            ?: householdStore.getSavedHouseholds()
                                .firstOrNull { it.isPersonal }?.id
                            ?: "personal-offline".also {
                                householdStore.savePersonalHousehold(it)
                                householdStore.saveHousehold(
                                    householdId = it,
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

                    // ── Restaurar hogares compartidos desde la nube ──
                    // Cubre hogares creados/unidos en OTRO dispositivo con la
                    // misma cuenta de Google (antes solo se restauraban al re-loguearse).
                    authManager.restoreFromCloudOnStartup()

                    // ── Subir el token FCM del dispositivo (si hay uno persistido) ──
                    // Sin esto, el token quedaba solo en SharedPreferences y el
                    // backend nunca podía dirigir un push de "tarea asignada" a
                    // este dispositivo. Best-effort: nunca bloquea el arranque.
                    try {
                        val uid = repo.getLocalId()
                        val fcmToken = notificationScheduler.getFcmToken()
                        if (uid != null && fcmToken != null) {
                            repo.saveFcmToken(uid, fcmToken)
                        }
                    } catch (_: Exception) {
                        // Offline/transitorio: se reintenta en el próximo arranque.
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
                                val reduceMotion = shouldReduceMotion()
                                Navigator(screen = screen) { navigator ->
                                    if (reduceMotion) {
                                        FadeTransition(navigator)
                                    } else {
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
}