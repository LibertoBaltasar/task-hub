/**
 * Módulo único de Koin de la app: cablea persistencia local, el cliente
 * Firestore/REST y los repos de dominio, integraciones de plataforma
 * (notificaciones, AdMob, Google Calendar) y los ScreenModels de Voyager.
 * Se instala una sola vez, en el `KoinApplication` de [org.taskhub.App].
 */
package org.taskhub.di

import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.taskhub.network.CloudFunctionsClient
import org.taskhub.network.FirestoreClient
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.GoogleCalendarRepository
import org.taskhub.network.HouseholdRepository
import org.taskhub.network.MemberRepository
import org.taskhub.network.NotificationRepository
import org.taskhub.network.RewardsRepository
import org.taskhub.network.TaskRepository
import org.taskhub.network.firestoreBaseUrl
import org.taskhub.platform.NotificationScheduler
import org.taskhub.platform.createNotificationScheduler
import org.taskhub.platform.createAdController
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SecureStore
import org.taskhub.storage.SettingsStore
import org.taskhub.storage.TaskCache
import org.taskhub.storage.createSecureStore
import org.taskhub.ui.models.CalendarSyncManager
import org.taskhub.ui.models.GoogleAuthManager
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HomeScreenModel
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.NotificationScreenModel
import org.taskhub.ui.models.ProfileScreenModel
import org.taskhub.ui.models.StatsScreenModel
import org.taskhub.ui.models.TaskCommentsScreenModel
import org.taskhub.ui.models.TaskScreenModel

/**
 * Grafo de dependencias de Task Hub.
 *
 * `single` = una única instancia para toda la vida del proceso (stores,
 * cliente de red, repos de dominio, managers compartidos entre pantallas).
 * `factory` = una instancia nueva cada vez que se inyecta — usado solo para
 * los [org.taskhub.ui.models] de Voyager, que deben nacer/morir con cada
 * pantalla en vez de compartir estado entre navegaciones repetidas a la
 * misma `Screen`.
 */
val appModule: Module = module {
    // Platform settings (SharedPreferences on Android, NSUserDefaults on iOS)
    single { Settings() }

    // Local household persistence (survives auth changes across restarts)
    single { HouseholdStore(settings = get()) }

    // Local task/offline cache (transparent, using SharedPreferences/NSUserDefaults)
    single { TaskCache(settings = get()) }

    // Almacén cifrado (EncryptedSharedPreferences/Keychain) para refresh tokens
    single { createSecureStore() }

    // User settings (theme, language, notifications). El SecureStore se
    // resuelve perezosamente (lazy { get() }): no se construye hasta el
    // primer acceso real a un refresh token, no en cuanto se inyecta
    // SettingsStore (panel v4, Experto 11 #6).
    single { SettingsStore(settings = get(), secureStoreProvider = lazy { get<SecureStore>() }) }

    // Network — talks directly to Firestore REST API.
    // FirestoreClient y los repos de dominio se registran como `single`
    // independientes (antes eran campos privados construidos a mano dentro
    // de FirestoreRepository) para que la fachada los reciba por inyección
    // en vez de ser la única forma de construirlos — causa raíz de por qué
    // seguía creciendo con cada refactor (panel v7, #16).
    single { FirestoreClient(apiKey = FirestoreRepository.DEFAULT_API_KEY, settingsStore = get()) }
    // Cloud Functions "callable HTTPS" (completar/deshacer/reasignar tareas
    // en una transacción del servidor — ver
    // docs/recurrencia-backend-cloud-functions-diseno-2026-09-11.md). Comparte
    // el mismo HttpClient que FirestoreClient (misma sesión/token, sin
    // duplicar auth).
    single { CloudFunctionsClient(client = get<FirestoreClient>().client, firestoreClient = get()) }
    single { NotificationRepository(baseUrl = firestoreBaseUrl(), firestoreClient = get(), taskCache = get()) }
    single { RewardsRepository(baseUrl = firestoreBaseUrl(), firestoreClient = get(), taskCache = get()) }
    single { TaskRepository(baseUrl = firestoreBaseUrl(), firestoreClient = get(), taskCache = get(), notificationRepository = get(), settingsStore = get()) }
    single { MemberRepository(baseUrl = firestoreBaseUrl(), firestoreClient = get(), taskCache = get()) }
    single {
        HouseholdRepository(
            baseUrl = firestoreBaseUrl(),
            firestoreClient = get(),
            taskCache = get(),
            memberRepository = get(),
            notificationRepository = get(),
            settingsStore = get()
        )
    }
    single {
        FirestoreRepository(
            taskCache = get(),
            settingsStore = get(),
            firestoreClient = get(),
            cloudFunctionsClient = get(),
            notificationRepository = get(),
            rewardsRepository = get(),
            taskRepository = get(),
            householdRepository = get(),
            memberRepository = get()
        )
    }

    // Google Calendar integration
    single { GoogleCalendarRepository() }

    // Google login / auth manager (compartido entre HomeScreen y Ajustes).
    // onClose cancela su CoroutineScope interno si Koin cierra el contenedor
    // (hoy no ocurre en producción — singleton de vida de proceso — pero deja
    // el manager cerrable, p.ej. para tests que recrean Koin entre casos).
    single { GoogleAuthManager(repo = get(), settingsStore = get(), householdStore = get()) } onClose { it?.close() }

    // Orquesta la sincronización automática de tareas ↔ Google Calendar
    single { CalendarSyncManager(repo = get(), calendarRepo = get(), settingsStore = get(), authManager = get()) }

    // Platform notification scheduler
    single { createNotificationScheduler() }

    // Platform ad controller (AdMob interstitial / banner)
    single { createAdController() }

    // ScreenModels (Voyager — each screen gets its own instance via factory)
    factory { HomeScreenModel(repo = get(), householdStore = get(), settingsStore = get()) }
    factory { HouseholdScreenModel(repo = get(), householdStore = get(), authManager = get(), settingsStore = get()) }
    factory { MemberScreenModel(repo = get(), settingsStore = get()) }
    factory { ProfileScreenModel(repo = get(), settingsStore = get()) }
    factory { NotificationScreenModel(repo = get(), settingsStore = get()) }
    factory { TaskScreenModel(repo = get(), notificationScheduler = get(), calendarSync = get(), adController = get(), settingsStore = get()) }
    factory { TaskCommentsScreenModel(repo = get(), settingsStore = get()) }
    factory { StatsScreenModel(repo = get()) }
}