package org.taskhub.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import org.koin.compose.koinInject
import org.taskhub.storage.HouseholdStore
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HouseholdUiState

/**
 * Nombre del hogar activo para el subtítulo de la topbar, sin el parpadeo de
 * "vacío → nombre" en el primer frame que sufrían las 9 pantallas que cargan
 * este dato por red: usa el nombre cacheado localmente en
 * [HouseholdStore.getSavedHouseholds] (`SavedHousehold.name`) como valor
 * inicial mientras [HouseholdScreenModel.loadHousehold] trae el estado fresco
 * (panel de expertos v4, Estética #1 + UI/Componentes #1).
 *
 * Dispara la carga de red por su cuenta (`LaunchedEffect(householdId)`); las
 * pantallas no necesitan volver a llamar a `householdModel.loadHousehold`.
 */
@Composable
fun rememberHouseholdName(householdId: String, householdModel: HouseholdScreenModel): String? {
    val householdStore = koinInject<HouseholdStore>()
    val cachedName = remember(householdId) {
        householdStore.getSavedHouseholds().find { it.id == householdId }?.name
    }
    val uiState by householdModel.uiState.collectAsState()
    LaunchedEffect(householdId) {
        householdModel.loadHousehold(householdId)
    }
    return (uiState as? HouseholdUiState.Success)?.household?.name ?: cachedName
}
