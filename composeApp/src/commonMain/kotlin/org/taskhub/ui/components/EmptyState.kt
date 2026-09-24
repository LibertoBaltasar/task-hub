// Layout compartido para los estados vacíos de listas, ver KDoc de [EmptyState].
package org.taskhub.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Ilustración + título opcional + mensaje, centrados, con acción opcional al
 * final (p. ej. "Crear la primera tarea"). Unifica el `Box` +
 * `Column(horizontalAlignment = CenterHorizontally)` que se repetía casi
 * idéntico en TaskListScreen/HomeScreen/RewardListScreen/RankingScreen/
 * NotificationListScreen (R14).
 *
 * @param modifier se aplica al `Box` contenedor — cada pantalla decide si
 *   ocupa `fillMaxSize()`/`fillMaxWidth()` y su propio padding.
 * @param titleStyle Home/TaskList usan `titleLarge` (van con [action] debajo);
 *   Rewards/Ranking usan `titleMedium` (por defecto).
 * @param messageStyle NotificationListScreen no pasa [title] y usa
 *   `bodyLarge` para que el mensaje solo no quede perdido; el resto usa
 *   `bodyMedium` (por defecto).
 */
@Composable
fun EmptyState(
    illustration: @Composable () -> Unit,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    messageStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    action: @Composable (() -> Unit)? = null
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            illustration()
            Spacer(Modifier.height(16.dp))
            if (title != null) {
                Text(
                    title,
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                message,
                style = messageStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(24.dp))
                action()
            }
        }
    }
}
