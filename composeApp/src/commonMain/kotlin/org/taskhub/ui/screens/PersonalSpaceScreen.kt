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
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.CalendarSyncManager
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.theme.Coral500
import org.taskhub.ui.theme.Teal500
import org.taskhub.ui.theme.Teal600
import org.taskhub.ui.theme.Teal700
import org.taskhub.ui.theme.Teal800
import org.taskhub.ui.theme.Teal900

/**
 * Pantalla dedicada al espacio Personal del usuario.
 *
 * Es la versión simplificada de [HouseholdScreen]: al ser un espacio para uno
 * mismo, no tiene sentido mostrar código de invitación, QR, recompensas,
 * ranking ni gestión de miembros. Solo tareas propias + calendario.
 *
 * El espacio Personal tiene un único miembro "Yo" (auto-creado en App.kt), de
 * modo que completar tareas sabe quién las hace (points/rachas/historial).
 */
data class PersonalSpaceScreen(
    val householdId: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val memberModel = koinScreenModel<MemberScreenModel>()
        val memberState by memberModel.uiState.collectAsState()
        val calendarSync = koinInject<CalendarSyncManager>()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        // Cargar el miembro "Yo" para pasarlo a las pantallas de tareas/calendario
        LaunchedEffect(householdId) {
            memberModel.loadMembers(householdId)
        }

        // Backfillea eventos de Calendar pendientes (best-effort, nunca bloquea la UI)
        LaunchedEffect(householdId) {
            calendarSync.reconcile(householdId, householdName = "Personal", isPersonal = true)
        }

        val memberId = (memberState as? MemberUiState.Success)?.members?.firstOrNull()?.id

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = s("personal_space_title"),
                    onBack = { navigator.replaceAll(HomeScreen()) }
                )

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
                                    text = s("personal_space_hero_title"),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Teal900,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = s("personal_space_hero_subtitle"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Ver mis tareas (acción principal)
                    item {
                        Button(
                            onClick = { navigator.push(TaskListScreen(householdId, memberId)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = s("personal_space_view_tasks"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Nueva tarea
                    item {
                        Button(
                            onClick = { navigator.push(CreateTaskScreen(householdId, memberId ?: "")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = s("personal_space_new_task"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Calendario
                    item {
                        Button(
                            onClick = { navigator.push(CalendarScreen(householdId, memberId)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = s("personal_space_calendar"),
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
