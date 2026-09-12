/**
 * Gate de login obligatorio: se muestra desde [org.taskhub.App] en vez de
 * [HomeScreen] mientras no haya sesión de Google iniciada. Task Hub es
 * Google-only (ver `docs/google-only-auth-2026-09-12.md`) — no existe ningún
 * modo anónimo al que caer, así que esta pantalla bloquea el acceso al resto
 * de la app hasta que [GoogleAuthManager.state] pase a `SignedIn`.
 */
package org.taskhub.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.taskhub.ui.components.AppLogo
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.GoogleAuthManager
import org.taskhub.ui.models.GoogleAuthState

/**
 * Pantalla de login (sin Voyager `Screen`/`Navigator`: se pinta directamente
 * desde [org.taskhub.App], igual que [SplashScreen], porque el `Navigator`
 * de la app aún no existe en este punto del arranque — se crea DESPUÉS de
 * que el login tenga éxito).
 */
@Composable
fun AuthGateScreen(
    authManager: GoogleAuthManager,
    authState: GoogleAuthState,
    lang: String
) {
    val s = { key: String -> AppStrings.get(key, lang) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo(size = 56.dp)

            Spacer(Modifier.height(24.dp))

            Text(
                text = s("auth_gate_title"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = s("auth_gate_subtitle"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            if (authState is GoogleAuthState.Error) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = s("error_icon_content_desc"),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = authState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Mismo patrón que el botón de envío de CreateHouseholdScreen/
            // JoinHouseholdScreen: el botón permanece en su sitio (mismo
            // tamaño/posición) y se deshabilita durante el envío, con el
            // spinner sustituyendo solo la etiqueta — evita el salto de
            // layout de reemplazar el botón entero por un bloque centrado
            // aparte mientras se conecta.
            Button(
                onClick = { authManager.signIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = authState !is GoogleAuthState.SigningIn,
                shape = MaterialTheme.shapes.large
            ) {
                if (authState is GoogleAuthState.SigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = s("settings_account_sign_in_google"),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
