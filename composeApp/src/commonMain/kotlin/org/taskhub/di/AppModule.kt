package org.taskhub.di

import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.GoogleCalendarRepository
import org.taskhub.platform.NotificationScheduler
import org.taskhub.platform.createNotificationScheduler
import org.taskhub.platform.createAdController
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SettingsStore
import org.taskhub.storage.TaskCache
import org.taskhub.storage.ThemeStore
import org.taskhub.ui.models.GoogleAuthManager
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HomeScreenModel
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.NotificationScreenModel
import org.taskhub.ui.models.TaskScreenModel

val appModule: Module = module {
    // Platform settings (SharedPreferences on Android, NSUserDefaults on iOS)
    single { Settings() }

    // Local household persistence (survives auth changes across restarts)
    single { HouseholdStore(settings = get()) }

    // Local task/offline cache (transparent, using SharedPreferences/NSUserDefaults)
    single { TaskCache(settings = get()) }

    // User settings (theme, language, notifications)
    single { SettingsStore(settings = get()) }

    // Theme persistence
    single { ThemeStore(settings = get()) }

    // Network — talks directly to Firestore REST API
    single { FirestoreRepository(taskCache = get(), settingsStore = get()) }

    // Google Calendar integration
    single { GoogleCalendarRepository() }

    // Google login / auth manager (compartido entre HomeScreen y Ajustes)
    single { GoogleAuthManager(repo = get(), settingsStore = get(), householdStore = get()) }

    // Platform notification scheduler
    single { createNotificationScheduler() }

    // Platform ad controller (AdMob interstitial / banner)
    single { createAdController() }

    // ScreenModels (Voyager — each screen gets its own instance via factory)
    factory { HomeScreenModel(repo = get(), householdStore = get()) }
    factory { HouseholdScreenModel(repo = get(), householdStore = get(), authManager = get()) }
    factory { MemberScreenModel(repo = get()) }
    factory { NotificationScreenModel(repo = get()) }
    factory { TaskScreenModel(repo = get(), notificationScheduler = get(), calendarRepo = get(), adController = get()) }
}