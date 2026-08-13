package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.taskhub.ui.theme.Coral500
import org.taskhub.ui.theme.Teal500
import org.taskhub.ui.theme.Teal600
import org.taskhub.ui.theme.Teal700
import org.taskhub.ui.theme.Teal900

/**
 * Pantalla dedicada al espacio Personal del usuario.
 *
 * Es la versión simplificada de [HouseholdScreen]: al ser un espacio para uno
 * mismo, no tiene sentido mostrar código de invitación, QR, recompensas,
 * ranking ni gestión de miembros. Solo tareas propias + calendario.
 *
 * Nota: no se carga [org.taskhub.ui.models.HouseholdScreenModel] ni miembros —
 * el espacio Personal se auto-crea en App.kt y sus tareas no dependen de un
 * miembro concreto (memberId = null ⇒ "todas las tareas").
 */
data class PersonalSpaceScreen(
    val householdId: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top bar ──
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Teal600,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { navigator.replaceAll(HomeScreen()) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("← Inicio")
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "👤 Mi espacio",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.weight(1f))
                    }
                }

                // ── Content ──
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Teal500.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "👤",
                                    style = MaterialTheme.typography.displaySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Mi espacio personal",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Teal900,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tus tareas y hábitos, sin compartir con nadie",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Teal700,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Ver mis tareas (acción principal)
                    item {
                        Button(
                            onClick = { navigator.push(TaskListScreen(householdId, null)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Coral500),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "📋 Ver mis tareas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Nueva tarea
                    item {
                        Button(
                            onClick = { navigator.push(CreateTaskScreen(householdId, "")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal600),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "➕ Nueva tarea",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Calendario
                    item {
                        Button(
                            onClick = { navigator.push(CalendarScreen(householdId, null)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "📅 Calendario",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
