package org.taskhub

import androidx.compose.ui.window.ComposeUIViewController
import org.taskhub.ui.theme.TaskHubTheme

fun MainViewController() = ComposeUIViewController(
    configure = { enforceStrictPlistSanity = false }
) {
    TaskHubTheme {
        App()
    }
}
