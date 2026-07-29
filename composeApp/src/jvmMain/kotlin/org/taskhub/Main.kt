package org.taskhub

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.taskhub.ui.theme.TaskHubTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Task Hub"
    ) {
        TaskHubTheme {
            App()
        }
    }
}
