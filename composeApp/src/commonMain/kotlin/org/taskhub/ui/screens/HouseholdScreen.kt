package org.taskhub.ui.screens

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
import org.taskhub.network.models.MemberResponse
import org.taskhub.storage.HouseholdStore
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HouseholdUiState
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.theme.*

data class HouseholdScreen(val householdId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdStore = koinInject<HouseholdStore>()
        val householdModel = koinScreenModel<HouseholdScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val householdState by householdModel.uiState.collectAsState()
        val memberState by memberModel.uiState.collectAsState()

        // Double-confirmation dialog state
        var showConfirmDialog1 by remember { mutableStateOf(false) }
        var showConfirmDialog2 by remember { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }

        LaunchedEffect(householdId) {
            householdModel.loadHousehold(householdId)
            memberModel.loadMembers(householdId)
        }

        // ── Deletion dialogs ──
        val householdName = when (val hState = householdState) {
            is HouseholdUiState.Success -> hState.household.name
            else -> ""
        }

        if (showConfirmDialog1) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog1 = false },
                title = { Text("Eliminar hogar") },
                text = {
                    Text("¿Eliminar '$householdName'? Esta acción no se puede deshacer.")
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
                    TextButton(onClick = { showConfirmDialog1 = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showConfirmDialog2) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog2 = false },
                title = { Text("¿Estás completamente seguro?") },
                text = {
                    Text("Se perderán todas las tareas y miembros de '$householdName'.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog2 = false
                        isDeleting = true

                        householdModel.deleteHousehold(
                            householdId = householdId,
                            onSuccess = {
                                val updated = householdStore.getSavedHouseholds()
                                if (updated.isEmpty()) {
                                    navigator.replaceAll(WelcomeScreen())
                                } else {
                                    navigator.replaceAll(HouseholdListScreen(updated))
                                }
                            },
                            onError = { _ ->
                                isDeleting = false
                            }
                        )
                    }) {
                        Text("Sí, eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog2 = false }) {
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
                        TextButton(
                            onClick = {
                                val updated = householdStore.getSavedHouseholds()
                                navigator.replaceAll(HouseholdListScreen(updated))
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("← Hogares")
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "🏠 Task Hub",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.weight(1f))

                        // Delete button
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { showConfirmDialog1 = true }) {
                                Text("🗑️", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }

                if (isDeleting) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal600)
                    }
                } else {
                    // Content
                    when (val hState = householdState) {
                        is HouseholdUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Teal600)
                            }
                        }

                        is HouseholdUiState.Success -> {
                            val household = hState.household

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Household info card
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Teal50
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "🏠",
                                                    style = MaterialTheme.typography.displaySmall
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Text(
                                                    text = household.name,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = Teal900,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Spacer(modifier = Modifier.height(16.dp))

                                                Text(
                                                    text = "Código de invitación",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Teal700
                                                )

                                                Text(
                                                    text = household.inviteCode,
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    color = Teal600,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = "Comparte este código para invitar miembros",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Teal700
                                                )
                                            }
                                        }
                                    }
                                }

                                // Navigation to tasks
                                item {
                                    Button(
                                        onClick = {
                                            // Use first member as current user (simplified)
                                            val mState = memberState
                                            val memberId = if (mState is MemberUiState.Success) mState.members.firstOrNull()?.id else null
                                            navigator.push(TaskListScreen(householdId, memberId))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Coral500),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = "📋 Ver Tareas",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Members header
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "👥 Miembros",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(Modifier.weight(1f))

                                        when (val mState = memberState) {
                                            is MemberUiState.Success -> {
                                                Text(
                                                    text = "${mState.members.size}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            else -> {}
                                        }
                                    }
                                }

                                // Members list
                                when (val mState = memberState) {
                                    is MemberUiState.Loading -> {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(100.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = Teal600)
                                            }
                                        }
                                    }

                                    is MemberUiState.Success -> {
                                        if (mState.members.isEmpty()) {
                                            item {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                ) {
                                                    Text(
                                                        text = "No hay miembros aún. ¡Invita a alguien!",
                                                        modifier = Modifier.padding(24.dp),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        } else {
                                            items(mState.members) { member ->
                                                MemberCard(member)
                                            }
                                        }
                                    }

                                    is MemberUiState.Error -> {
                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                                )
                                            ) {
                                                Text(
                                                    text = "Error: ${mState.message}",
                                                    modifier = Modifier.padding(16.dp),
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }

                                    is MemberUiState.Idle -> {}
                                }

                                // Bottom spacer
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }

                        is HouseholdUiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "❌ ${hState.message}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { householdModel.loadHousehold(householdId) }) {
                                        Text("Reintentar")
                                    }
                                }
                            }
                        }

                        is HouseholdUiState.AlreadyMember -> {
                            // Already a member — this shouldn't normally be shown on this screen
                            // Navigate directly
                            LaunchedEffect(Unit) {
                                navigator.replaceAll(HouseholdScreen(hState.household.id))
                            }
                        }

                        is HouseholdUiState.Idle -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(member: MemberResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (member.role == "admin") Coral100 else Teal100
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (member.role == "admin") "👑" else "🧒",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (member.role == "admin") "Administrador" else "Niño/a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Points badge
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Coral500
            ) {
                Text(
                    text = "⭐ ${member.totalPoints}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}