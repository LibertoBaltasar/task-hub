package org.taskhub.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SavedHousehold
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.theme.*

class HouseholdListScreen(val savedHouseholds: List<SavedHousehold>) : Screen {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repo = koinInject<FirestoreRepository>()
        val householdStore = koinInject<HouseholdStore>()
        val householdModel = koinScreenModel<HouseholdScreenModel>()

        var households by remember { mutableStateOf<List<HouseholdResponse>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var selectedHouseholdId by remember { mutableStateOf<String?>(null) }

        // Double-confirmation dialog state
        var showConfirmDialog1 by remember { mutableStateOf(false) }
        var showConfirmDialog2 by remember { mutableStateOf(false) }
        var householdToDelete by remember { mutableStateOf<HouseholdResponse?>(null) }

        LaunchedEffect(savedHouseholds) {
            isLoading = true
            error = null
            try {
                val ids = savedHouseholds.map { it.id }
                households = repo.getHouseholds(ids)
            } catch (e: Exception) {
                error = e.message ?: "Error al cargar hogares"
            }
            isLoading = false
        }

        // ── Deletion dialogs ──
        if (showConfirmDialog1 && householdToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showConfirmDialog1 = false
                    householdToDelete = null
                },
                title = { Text("Eliminar hogar") },
                text = {
                    Text("¿Eliminar '${householdToDelete!!.name}'? Esta acción no se puede deshacer.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog1 = false
                        showConfirmDialog2 = true
                    }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showConfirmDialog1 = false
                        householdToDelete = null
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showConfirmDialog2 && householdToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showConfirmDialog2 = false
                    householdToDelete = null
                },
                title = { Text("¿Estás completamente seguro?") },
                text = {
                    Text("Se perderán todas las tareas y miembros de '${householdToDelete!!.name}'.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog2 = false
                        val h = householdToDelete!!
                        householdToDelete = null
                        selectedHouseholdId = null

                        householdModel.deleteHousehold(
                            householdId = h.id,
                            onSuccess = {
                                // Refresh saved households and reload the list
                                val updated = householdStore.getSavedHouseholds()
                                if (updated.isEmpty()) {
                                    navigator.replaceAll(WelcomeScreen())
                                } else {
                                    navigator.replaceAll(HouseholdListScreen(updated))
                                }
                            },
                            onError = { msg ->
                                error = msg
                            }
                        )
                    }) {
                        Text("Sí, eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showConfirmDialog2 = false
                        householdToDelete = null
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Teal600,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back or deselect
                        if (selectedHouseholdId != null) {
                            TextButton(
                                onClick = { selectedHouseholdId = null },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("✕ Cancelar")
                            }
                        } else {
                            Text(
                                text = "🏠 Task Hub",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        if (selectedHouseholdId != null) {
                            // Delete button when a household is selected
                            IconButton(onClick = {
                                val h = households.find { it.id == selectedHouseholdId }
                                if (h != null) {
                                    householdToDelete = h
                                    showConfirmDialog1 = true
                                }
                            }) {
                                Text("🗑️", style = MaterialTheme.typography.titleLarge)
                            }
                        } else {
                            TextButton(
                                onClick = { navigator.replaceAll(WelcomeScreen()) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Crear/Unirse")
                            }
                        }
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Teal600)
                        }
                    }

                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = "❌ $error",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    navigator.replaceAll(HouseholdListScreen(householdStore.getSavedHouseholds()))
                                }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    households.isEmpty() -> {
                        // No households — show welcome-like state (stale local IDs)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🏠",
                                style = MaterialTheme.typography.displayLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No tienes hogares aún",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Crea un hogar o únete a uno existente",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { navigator.replaceAll(WelcomeScreen()) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Teal600),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text("Comenzar")
                            }
                        }
                    }

                    else -> {
                        // Household list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = if (selectedHouseholdId != null) "Selecciona el hogar a eliminar" else "Tus hogares",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            items(households) { household ->
                                HouseholdCard(
                                    household = household,
                                    isSelected = household.id == selectedHouseholdId,
                                    onClick = {
                                        if (selectedHouseholdId != null) {
                                            // In selection mode, toggle selection
                                            selectedHouseholdId = if (selectedHouseholdId == household.id) null else household.id
                                        } else {
                                            navigator.replaceAll(HouseholdScreen(household.id))
                                        }
                                    },
                                    onLongClick = {
                                        selectedHouseholdId = household.id
                                    }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { navigator.replaceAll(WelcomeScreen()) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text("+ Unirse a otro hogar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HouseholdCard(
    household: HouseholdResponse,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator or house icon
            if (isSelected) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.error
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "🗑️",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = Teal100
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "🏠",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = household.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Código: ${household.inviteCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSelected) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleLarge,
                    color = Teal500
                )
            }
        }
    }
}