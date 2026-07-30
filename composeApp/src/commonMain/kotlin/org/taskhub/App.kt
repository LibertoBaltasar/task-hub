package org.taskhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import org.taskhub.storage.HouseholdStore
import org.taskhub.ui.screens.HouseholdListScreen
import org.taskhub.ui.screens.WelcomeScreen
import org.taskhub.ui.theme.TaskHubTheme
import org.taskhub.ui.theme.Teal600

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        TaskHubTheme {
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

            when (val screen = initialScreen) {
                null -> {
                    // Still loading
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Teal600)
                        }
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
