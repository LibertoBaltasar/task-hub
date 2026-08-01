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
import com.russhwolf.settings.Settings
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.taskhub.di.appModule
import org.taskhub.storage.HouseholdStore
import org.taskhub.ui.screens.HouseholdListScreen
import org.taskhub.ui.screens.WelcomeScreen
import org.taskhub.ui.theme.TaskHubTheme
import org.taskhub.ui.theme.TaskHubThemeType
import org.taskhub.ui.theme.Teal600

private const val KEY_THEME = "taskhub_theme"

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        val settings = remember { Settings() }
        var themeType by remember {
            mutableStateOf(
                when (settings.getString(KEY_THEME, "DEFAULT")) {
                    "NATURALEZA" -> TaskHubThemeType.NATURALEZA
                    "MINIMAL" -> TaskHubThemeType.MINIMAL
                    else -> TaskHubThemeType.DEFAULT
                }
            )
        }

        TaskHubTheme(themeType = themeType) {
            val householdStore = koinInject<HouseholdStore>()

            var initialScreen by remember { mutableStateOf<Screen?>(null) }

            LaunchedEffect(Unit) {
                val savedHouseholds = householdStore.getSavedHouseholds()
                initialScreen = if (savedHouseholds.isNotEmpty()) {
                    HouseholdListScreen(savedHouseholds)
                } else {
                    WelcomeScreen()
                }
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