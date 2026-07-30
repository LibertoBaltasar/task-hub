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
import com.russhwolf.settings.Settings
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.taskhub.di.appModule
import org.taskhub.network.FirestoreRepository
import org.taskhub.ui.screens.HouseholdListScreen
import org.taskhub.ui.screens.WelcomeScreen
import org.taskhub.ui.theme.TaskHubTheme
import org.taskhub.ui.theme.Teal600

private const val KEY_LOCAL_ID = "taskhub_local_id"

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        TaskHubTheme {
            val settings = koinInject<Settings>()
            val repo = koinInject<FirestoreRepository>()

            var initialScreen by remember { mutableStateOf<Screen?>(null) }

            LaunchedEffect(Unit) {
                val savedLocalId = settings.getString(KEY_LOCAL_ID, "")

                if (savedLocalId.isNotEmpty()) {
                    // User has been here before — check for households
                    repo.setLocalId(savedLocalId)
                    try {
                        val households = repo.getMyHouseholds(savedLocalId)
                        initialScreen = if (households.isNotEmpty()) {
                            HouseholdListScreen(savedLocalId)
                        } else {
                            WelcomeScreen()
                        }
                    } catch (_: Exception) {
                        initialScreen = WelcomeScreen()
                    }
                } else {
                    // First time — show welcome
                    initialScreen = WelcomeScreen()
                }
            }

            // When localId becomes available (after anonymous auth), persist it to settings
            LaunchedEffect(repo.getLocalId()) {
                val localId = repo.getLocalId()
                if (localId != null && settings.getString(KEY_LOCAL_ID, "").isEmpty()) {
                    settings.putString(KEY_LOCAL_ID, localId)
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