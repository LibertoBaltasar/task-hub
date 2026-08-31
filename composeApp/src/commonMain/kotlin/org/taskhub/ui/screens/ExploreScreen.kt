package org.taskhub.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.StatsScreenModel

/**
 * Pantalla combinada de un hogar: agrupa Estadísticas, Ranking y Recompensas
 * en una sola pantalla con pestañas. Sustituye a los tres botones separados
 * que había antes en [HouseholdScreen].
 */
data class ExploreScreen(
    val householdId: String,
    val memberId: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val memberModel = koinScreenModel<MemberScreenModel>()
        val statsModel = koinScreenModel<StatsScreenModel>()
        var selectedTab by remember { mutableStateOf(0) }
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TaskHubTopBar(
                    title = s("explore_title"),
                    onBack = { navigator.pop() }
                )

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(s("explore_tab_stats")) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(s("explore_tab_ranking")) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(s("explore_tab_rewards")) }
                    )
                }

                when (selectedTab) {
                    0 -> StatsBody(householdId, memberId, statsModel)
                    1 -> RankingBody(householdId, memberModel)
                    2 -> RewardsBody(householdId, memberModel)
                }
            }
        }
    }
}
