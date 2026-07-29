package org.taskhub.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.taskhub.network.ApiClient
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.MemberScreenModel

val appModule: Module = module {
    // Network
    single { ApiClient() }

    // ScreenModels (Voyager — each screen gets its own instance via factory)
    factory { HouseholdScreenModel(apiClient = get()) }
    factory { MemberScreenModel(apiClient = get()) }
}
