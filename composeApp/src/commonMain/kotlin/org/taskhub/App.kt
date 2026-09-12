/**
 * Raíz de Compose de Task Hub, común a Android/iOS/JVM. Instala Koin
 * ([org.taskhub.di.appModule]), resuelve tema/idioma persistidos y aloja el
 * único [Navigator] de Voyager de la app.
 */
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
import kotlinx.coroutines.CancellationException
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
import org.taskhub.ui.models.GoogleAuthState
import org.taskhub.ui.screens.AuthGateScreen
import org.taskhub.ui.screens.HomeScreen
import org.taskhub.ui.screens.HouseholdScreen
import org.taskhub.ui.screens.SplashScreen
import org.taskhub.ui.screens.TaskDetailScreen
import org.taskhub.ui.theme.TaskHubTheme
import org.taskhub.ui.theme.TaskHubThemeType
import org.taskhub.ui.theme.Teal600

/**
 * Composable raíz de la app.
 *
 * Orden de arranque: 1) [KoinApplication] instala [org.taskhub.di.appModule]
 * (necesario ya para [SplashScreen], que lee el idioma de [SettingsStore]);
 * 2) tras el splash (1.5s) se resuelven tema/idioma reactivos desde
 * [SettingsStore] y se envuelve el árbol en [TaskHubTheme] +
 * `LocalAppSettings`; 3) sin sesión de Google ([GoogleAuthManager.state]
 * distinto de `SignedIn`), se muestra [org.taskhub.ui.screens.AuthGateScreen]
 * y el resto de este composable no se ejecuta — Task Hub es Google-only (ver
 * `docs/google-only-auth-2026-09-12.md`), no hay modo anónimo al que caer;
 * 4) ya con sesión, un `LaunchedEffect(authState)` hace la inicialización de
 * arranque en frío — resolver/crear el hogar "Personal" (determinista por
 * UID para que sea el mismo en todos los dispositivos con la misma cuenta),
 * asegurar el miembro "Yo" en él, restaurar hogares compartidos desde la
 * nube ([GoogleAuthManager.restoreFromCloudOnStartup]) y subir el token FCM
 * pendiente — todo best-effort (nunca bloquea si está offline); 5) se crea
 * el único [Navigator] de Voyager de la app con la pila inicial
 * `[HomeScreen(), destino?]` (el destino del deep link, si lo hay, ya
 * incluido para evitar un salto visual doble).
 *
 * [deepLinkHouseholdId]/[deepLinkTaskId] llegan de tocar una notificación
 * local del sistema (Android: [org.taskhub.NotificationHelper.showUpdateNotification]
 * o el recordatorio de tarea) — `null` en el arranque normal e ignorados en
 * iOS/JVM (no producen notificaciones del sistema hoy, ver
 * docs/review-panel-expertos-notificaciones-2026-09-05.md, gap B).
 * [deepLinkTaskId] vacío (no null) es el centinela de "es un mensaje de chat,
 * no una tarea" — abre [HouseholdScreen] en vez de [TaskDetailScreen].
 * [deepLinkNotificationId], si llega, se marca como leída en Firestore al
 * consumir el deep link — sin esto, tocar la notificación del sistema no
 * tenía ningún efecto sobre su estado "no leída" en la lista in-app,
 * inconsistente con tocar la card desde `NotificationListScreen` (panel de
 * notificaciones 2026-09-05, UX).
 */
@Composable
fun App(
    deepLinkHouseholdId: String? = null,
    deepLinkTaskId: String? = null,
    deepLinkNotificationId: String? = null
) {
    // ── Fase 1: Splash screen (1.5 segundos) ─────────────────
    var showSplash by remember { mutableStateOf(true) }

    // KoinApplication envuelve también el splash para poder leer el idioma
    // guardado (SettingsStore) y mostrar el subtítulo en el idioma correcto.
    KoinApplication(application = {
        modules(appModule)
    }) {
        val settingsStore = koinInject<SettingsStore>()

        if (showSplash) {
            SplashScreen(
                lang = settingsStore.getLanguage(),
                onFinished = { showSplash = false }
            )
            return@KoinApplication
        }

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
                val authState by authManager.state.collectAsState()

                var initialScreens by remember { mutableStateOf<List<Screen>?>(null) }
                // Deep link ya incorporado a la pila con la que se crea el
                // Navigator (arranque en frío) — evita que el LaunchedEffect
                // de más abajo lo "empuje" una segunda vez y deje una
                // pantalla duplicada en la pila (panel de notificaciones
                // 2026-09-05, UX, doble salto visual Home→destino).
                var initialDeepLinkConsumedKey by remember { mutableStateOf<Pair<String?, String?>?>(null) }

                // Task Hub es Google-only (ver docs/google-only-auth-2026-09-12.md):
                // sin sesión de Google, [AuthGateScreen] bloquea el resto de la app
                // más abajo — este bootstrap NO debe correr (todo lo que hace
                // requiere Firestore autenticado) hasta que `authState` sea
                // SignedIn. Si el usuario cierra sesión (o se elimina la cuenta)
                // mientras ya estaba dentro, `initialScreens` se resetea a null
                // para reconstruir el Navigator desde cero en el próximo login.
                LaunchedEffect(authState) {
                    if (authState !is GoogleAuthState.SignedIn) {
                        initialScreens = null
                        return@LaunchedEffect
                    }
                    // ── Resolver/crear el espacio Personal (interdispositivo) ──
                    // El ID es determinista (personal_{uid}), de modo que con la
                    // misma cuenta de Google todos los dispositivos apuntan al
                    // MISMO hogar.
                    var personalId: String? = null
                    try {
                        val personal = repo.getOrCreatePersonalHousehold()
                        householdStore.replacePersonalHousehold(personal.id)
                        personalId = personal.id
                    } catch (e: CancellationException) {
                        throw e
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
                        } catch (e: CancellationException) {
                            throw e
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Offline/transitorio: se reintenta en el próximo arranque.
                    }

                    // ── Ir siempre a HomeScreen, con el destino del deep link
                    // (si lo hay) ya incluido en la pila inicial ───────────
                    // En vez de crear el Navigator solo con HomeScreen y hacer
                    // `push` al destino en un LaunchedEffect posterior (lo que
                    // pintaba HomeScreen un frame antes de la transición), la
                    // API `Navigator(screens: List<Screen>, ...)` de Voyager
                    // 1.1.0-beta03 permite construir la pila `[HomeScreen(),
                    // destino]` directamente, mostrando ya el destino en el
                    // primer frame (con "atrás" volviendo a Home).
                    val screens = mutableListOf<Screen>(HomeScreen())
                    if (!deepLinkHouseholdId.isNullOrEmpty()) {
                        screens += if (deepLinkTaskId.isNullOrEmpty()) {
                            HouseholdScreen(deepLinkHouseholdId)
                        } else {
                            TaskDetailScreen(deepLinkHouseholdId, deepLinkTaskId)
                        }
                        initialDeepLinkConsumedKey = deepLinkHouseholdId to deepLinkTaskId
                        if (!deepLinkNotificationId.isNullOrEmpty()) {
                            try {
                                repo.markNotificationRead(deepLinkHouseholdId, deepLinkNotificationId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // No crítico: solo afecta al estado "leída" in-app.
                            }
                        }
                    }
                    initialScreens = screens
                }

                if (authState !is GoogleAuthState.SignedIn) {
                    // Google-only: sin sesión iniciada, bloquea el resto de la
                    // app con el gate de login en vez de mostrar HomeScreen.
                    AuthGateScreen(
                        authManager = authManager,
                        authState = authState,
                        lang = appSettings.currentLanguage
                    )
                    return@CompositionLocalProvider
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
                        when (val screens = initialScreens) {
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
                                Navigator(screens = screens) { navigator ->
                                    // Se ejecuta una vez por (navigator, deep
                                    // link). El deep link con el que se creó la
                                    // pila inicial (arranque en frío) ya quedó
                                    // resuelto arriba — este efecto solo debe
                                    // actuar cuando llega uno DISTINTO mientras
                                    // la Activity ya estaba viva (onNewIntent),
                                    // que sí dispara un `push` con transición
                                    // normal (aquí no hay "doble salto" porque
                                    // la app ya se estaba mostrando).
                                    LaunchedEffect(navigator, deepLinkHouseholdId, deepLinkTaskId) {
                                        val hid = deepLinkHouseholdId ?: return@LaunchedEffect
                                        if ((hid to deepLinkTaskId) == initialDeepLinkConsumedKey) return@LaunchedEffect
                                        if (deepLinkTaskId.isNullOrEmpty()) {
                                            navigator.push(HouseholdScreen(hid))
                                        } else {
                                            navigator.push(TaskDetailScreen(hid, deepLinkTaskId))
                                        }
                                        if (!deepLinkNotificationId.isNullOrEmpty()) {
                                            try {
                                                repo.markNotificationRead(hid, deepLinkNotificationId)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                // No crítico: solo afecta al estado "leída" in-app.
                                            }
                                        }
                                    }
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