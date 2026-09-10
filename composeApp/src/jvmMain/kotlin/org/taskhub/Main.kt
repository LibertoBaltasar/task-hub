/**
 * Punto de entrada del target JVM desktop (`./gradlew :composeApp:run`).
 * Envuelve [App] en una única [Window] nativa — el resto de la app
 * (Koin, navegación, tema) es idéntico al de Android/iOS.
 */
package org.taskhub

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.taskhub.ui.theme.TaskHubTheme

/**
 * Arranca la aplicación Compose Desktop: una ventana con título "Task Hub"
 * que cierra el proceso al pulsar la X ([androidx.compose.ui.window.ApplicationScope.exitApplication]).
 * `TaskHubTheme` se aplica aquí con sus valores por defecto (sin deep link:
 * JVM no recibe intents/notificaciones del sistema).
 */
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
