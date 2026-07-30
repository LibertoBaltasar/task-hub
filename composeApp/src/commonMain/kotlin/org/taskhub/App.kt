package org.taskhub

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.koin.compose.KoinApplication
import org.taskhub.di.appModule
import org.taskhub.ui.screens.WelcomeScreen
import org.taskhub.ui.theme.TaskHubTheme

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        TaskHubTheme {
            Navigator(screen = WelcomeScreen()) { navigator ->
                SlideTransition(navigator)
            }
        }
    }
}
