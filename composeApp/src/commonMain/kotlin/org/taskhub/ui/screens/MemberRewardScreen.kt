package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
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
import org.taskhub.network.models.RewardResponse
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.RewardActionState
import org.taskhub.ui.theme.*
import org.taskhub.ui.components.TaskHubTopBar

data class MemberRewardScreen(
    val householdId: String,
    val memberId: String,
    val reward: RewardResponse
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val memberModel = koinScreenModel<MemberScreenModel>()
        val memberState by memberModel.uiState.collectAsState()
        val actionState by memberModel.rewardActionState.collectAsState()

        var showConfirmDialog by remember { mutableStateOf(false) }

        // Sin esto, memberState se queda para siempre en MemberUiState.Idle
        // (Voyager crea una instancia nueva de MemberScreenModel por pantalla):
        // currentMember era siempre null, memberPoints siempre 0 y el canje
        // aparecía como "Puntos insuficientes" aunque el usuario tuviera saldo.
        LaunchedEffect(householdId) {
            memberModel.loadMembers(householdId)
        }

        // Find member and their points
        val currentMember = when (val mState = memberState) {
            is MemberUiState.Success -> mState.members.find { it.id == memberId }
            else -> null
        }

        val memberPoints = currentMember?.totalPoints ?: 0
        val canAfford = memberPoints >= reward.cost

        // Navigate back on success
        LaunchedEffect(actionState) {
            if (actionState is RewardActionState.Success) {
                memberModel.clearRewardAction()
                navigator.pop()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = "Canjear",
                    onBack = { navigator.pop() }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(24.dp))

                    // Reward icon
                    Text(
                        text = reward.icon,
                        style = MaterialTheme.typography.displayLarge
                    )

                    Spacer(Modifier.height(16.dp))

                    // Reward title
                    Text(
                        text = reward.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    if (reward.description.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = reward.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    // Cost vs Points comparison
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (canAfford) Teal50 else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Coste",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "⭐ ${reward.cost}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (canAfford) Teal800 else MaterialTheme.colorScheme.error
                            )

                            Spacer(Modifier.height(8.dp))

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 32.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Tus puntos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "⭐ $memberPoints",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (canAfford) Teal800 else MaterialTheme.colorScheme.error
                            )

                            if (!canAfford) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Te faltan ⭐ ${reward.cost - memberPoints}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Error message
                    if (actionState is RewardActionState.Error) {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = (actionState as RewardActionState.Error).message,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Redeem button
                    val isRedeeming = actionState is RewardActionState.Loading

                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canAfford && !isRedeeming,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        if (isRedeeming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (canAfford) "Canjear Recompensa" else "Puntos insuficientes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!canAfford) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { navigator.pop() }) {
                            Text("← Volver a recompensas")
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Confirmation dialog
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Confirmar canje") },
                text = {
                    Text(
                        "¿Canjear '${reward.title}' por ⭐ ${reward.cost} puntos?\n\n" +
                            "Te quedarán ⭐ ${memberPoints - reward.cost} puntos."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                            memberModel.redeemReward(
                                householdId = householdId,
                                rewardId = reward.id,
                                memberId = memberId,
                                pointsSpent = reward.cost
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Sí, canjear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}