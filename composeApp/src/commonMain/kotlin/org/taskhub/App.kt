package org.taskhub

import androidx.compose.runtime.Composable
import org.koin.compose.KoinContext
import org.taskhub.ui.screens.HomeScreen

@Composable
fun App() {
    KoinContext {
        HomeScreen()
    }
}
