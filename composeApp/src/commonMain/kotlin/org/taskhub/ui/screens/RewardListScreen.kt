package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import org.taskhub.network.models.RewardResponse
import org.taskhub.ui.components.BadgeTone
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.RewardUiState
import org.taskhub.ui.theme.*

data class RewardListScreen(val householdId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val memberModel = koinScreenModel<MemberScreenModel>()

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = "Recompensas",
                    onBack = { navigator.pop() }
                )

                RewardsBody(householdId, memberModel)
            }
        }
    }
}

/** Contenido reutilizable de recompensas (sin barra superior), para la pantalla combinada. */
@Composable
internal fun RewardsBody(householdId: String, memberModel: MemberScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val rewardState by memberModel.rewardState.collectAsState()
    val memberState by memberModel.uiState.collectAsState()

    // Determine if current user is admin
    var isAdmin by remember { mutableStateOf(false) }
    var currentMemberId by remember { mutableStateOf("") }

    LaunchedEffect(householdId) {
        memberModel.loadRewards(householdId)
        memberModel.loadMembers(householdId)
    }

    val localId = koinInject<org.taskhub.network.FirestoreRepository>().getLocalId()

    LaunchedEffect(memberState) {
        if (memberState is MemberUiState.Success) {
            val members = (memberState as MemberUiState.Success).members
            val myMember = members.find { it.userId == localId }
            isAdmin = myMember?.role == "admin"
            currentMemberId = myMember?.id ?: ""
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Acción de crear (solo admins)
        if (isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { navigator.push(CreateRewardScreen(householdId)) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "+ Nueva",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when (val rState = rewardState) {
            is RewardUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Teal600)
                }
            }

            is RewardUiState.Success -> {
                if (rState.rewards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🎁",
                                style = MaterialTheme.typography.displayLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No hay recompensas aún",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isAdmin) "Crea la primera recompensa con +" else "El admin aún no ha creado recompensas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(rState.rewards, key = { it.id }) { reward ->
                            RewardCard(
                                reward = reward,
                                isAdmin = isAdmin,
                                onDelete = {
                                    memberModel.deleteReward(householdId, reward.id)
                                },
                                onRedeem = {
                                    if (currentMemberId.isNotEmpty()) {
                                        navigator.push(
                                            MemberRewardScreen(
                                                householdId = householdId,
                                                memberId = currentMemberId,
                                                reward = reward
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            is RewardUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "❌ ${rState.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { memberModel.loadRewards(householdId) }) {
                            Text("Reintentar")
                        }
                    }
                }
            }

            is RewardUiState.Idle -> {}
        }
    }
}

@Composable
private fun RewardCard(
    reward: RewardResponse,
    isAdmin: Boolean,
    onDelete: () -> Unit,
    onRedeem: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji icon
            Text(
                text = reward.icon,
                style = MaterialTheme.typography.displaySmall
            )

            Spacer(Modifier.height(8.dp))

            // Title
            Text(
                text = reward.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            // Description if present
            if (reward.description.isNotEmpty()) {
                Text(
                    text = reward.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(12.dp))

            // Cost badge
            PointsBadge(
                text = "⭐ ${reward.cost}",
                tone = BadgeTone.Coral
            )

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRedeem,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Canjear",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isAdmin) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar recompensa",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar recompensa") },
            text = { Text("¿Eliminar '${reward.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
