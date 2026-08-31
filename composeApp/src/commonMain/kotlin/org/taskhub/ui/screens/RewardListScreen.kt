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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.RewardResponse
import org.taskhub.ui.components.BadgeTone
import org.taskhub.ui.components.DestructiveConfirmDialog
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.RewardUiState
import org.taskhub.ui.theme.*

/** Contenido reutilizable de recompensas (sin barra superior), para la pantalla combinada. */
@Composable
internal fun RewardsBody(householdId: String, memberModel: MemberScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val rewardState by memberModel.rewardState.collectAsState()
    val memberState by memberModel.uiState.collectAsState()
    val rewardActionState by memberModel.rewardActionState.collectAsState()
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sin esto, un fallo al borrar una recompensa (p.ej. red) no mostraba nada:
    // rewardActionState pasaba a Error pero ningún composable estaba suscrito,
    // así que la tarjeta simplemente seguía ahí sin explicación.
    LaunchedEffect(rewardActionState) {
        val state = rewardActionState
        if (state is org.taskhub.ui.models.RewardActionState.Error) {
            snackbarHostState.showSnackbar(message = "❌ ${state.message}", duration = SnackbarDuration.Short)
            memberModel.clearRewardAction()
        }
    }

    // Determine if current user is admin
    var isAdmin by remember { mutableStateOf(false) }
    var currentMemberId by remember { mutableStateOf("") }

    LaunchedEffect(householdId) {
        memberModel.loadRewards(householdId)
        memberModel.loadMembers(householdId)
    }

    // El owner del hogar es siempre "de confianza" para gestionar recompensas,
    // igual que isTrusted(hid) en firestore.rules — ver mismo fix en
    // HouseholdScreen.kt/TaskDetailScreen.kt.
    var isOwner by remember { mutableStateOf(false) }
    LaunchedEffect(householdId) {
        isOwner = memberModel.isHouseholdOwner(householdId)
    }

    LaunchedEffect(memberState) {
        if (memberState is MemberUiState.Success) {
            val members = (memberState as MemberUiState.Success).members
            val localId = memberModel.localId
            val myMember = members.find { it.userId == localId }
            isAdmin = myMember?.role == "admin" || isOwner
            currentMemberId = myMember?.id ?: ""
        }
    }
    LaunchedEffect(isOwner) {
        if (isOwner) isAdmin = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        s("tasks_new"),
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                text = s("reward_list_empty_title"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isAdmin) s("reward_list_empty_admin") else s("reward_list_empty_member"),
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
                            Text(s("tasks_retry"))
                        }
                    }
                }
            }

            is RewardUiState.Idle -> {}
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

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
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description if present
            if (reward.description.isNotEmpty()) {
                Text(
                    text = reward.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                        s("member_reward_title"),
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
                            contentDescription = s("reward_delete_title"),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DestructiveConfirmDialog(
            title = s("reward_delete_title"),
            text = s("reward_delete_confirm").replace("%s", reward.title),
            s = s,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            }
        )
    }
}
