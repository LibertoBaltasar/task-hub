package org.taskhub.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.taskhub.network.FirestoreRepository
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.MemberScreenModel

val appModule: Module = module {
    // Network — talks directly to Firestore REST API
    single { FirestoreRepository() }

    // ScreenModels (Voyager — each screen gets its own instance via factory)
    factory { HouseholdScreenModel(repo = get()) }
    factory { MemberScreenModel(repo = get()) }
}
