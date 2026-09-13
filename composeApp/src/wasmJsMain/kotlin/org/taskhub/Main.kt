/**
 * Punto de entrada del target web (Compose for Web / wasmJs). Monta [App]
 * sobre el `<body>` del documento vía `ComposeViewport` — el resto de la app
 * (Koin, navegación, tema) es idéntico al de Android/iOS/JVM.
 */
package org.taskhub

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}
