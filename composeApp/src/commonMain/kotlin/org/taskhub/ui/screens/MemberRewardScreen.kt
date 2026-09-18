/**
 * Pantalla de detalle/canje de una recompensa concreta para un miembro. Lee
 * los puntos actuales del miembro vía [org.taskhub.ui.models.MemberScreenModel]
 * y delega el canje en ese mismo ScreenModel (`rewardActionState`). Se navega
 * aquí desde [RewardListScreen] al tocar una recompensa.
 */
package org.taskhub.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.taskhub.network.models.RewardResponse
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.RewardActionState
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.theme.*
import org.taskhub.ui.components.ConfettiOverlay
import org.taskhub.ui.components.DestructiveConfirmDialog
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.effectsEnabled
import org.taskhub.ui.components.EffectCategory
import org.taskhub.ui.components.rememberHouseholdName
import org.taskhub.ui.components.shouldReduceMotion
import org.taskhub.ui.i18n.AppStrings

/**
 * Pantalla de canje de la recompensa [reward] para el miembro [memberId] del
 * hogar [householdId]. Muestra coste vs. puntos disponibles del miembro y
 * habilita el botón de canje solo si le alcanzan los puntos; pide
 * confirmación con [DestructiveConfirmDialog] (no destructivo: solo descuenta
 * puntos) antes de llamar a `redeemReward`.
 */
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
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        var showConfirmDialog by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        val householdModel = koinScreenModel<HouseholdScreenModel>()
        val householdName = rememberHouseholdName(householdId, householdModel)

        // Celebración del canje (delight fase 2, §2.6): pulso de escala del
        // icono + confeti reducido (8 partículas) sobre su área, gateados con
        // effectsEnabled(fx_effects) igual que el resto de celebraciones.
        val reduceMotion = shouldReduceMotion()
        val effectsOn = effectsEnabled(EffectCategory.EFFECTS)
        var isCelebrating by remember { mutableStateOf(false) }
        val iconScale = remember { Animatable(1f) }
        LaunchedEffect(isCelebrating) {
            if (isCelebrating) {
                if (reduceMotion) {
                    iconScale.snapTo(1f)
                } else {
                    val bounce = spring<Float>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                    iconScale.animateTo(1.15f, animationSpec = bounce)
                    iconScale.animateTo(1f, animationSpec = bounce)
                }
            }
        }

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

        // Mientras memberState no ha llegado a Success (primer frame tras
        // navegar a esta pantalla), currentMember es null y memberPoints cae
        // a 0 — sin distinguir este caso, el botón mostraba "Puntos
        // insuficientes" aunque el usuario sí tuviera saldo (panel v7
        // 2026-09-10, Exp. 5, MENOR, SIGUE ABIERTO).
        val isLoadingMember = memberState !is MemberUiState.Success
        val memberPoints = currentMember?.totalPoints ?: 0
        val canAfford = memberPoints >= reward.cost

        // Confirmación de éxito antes de volver a la lista — sin esto, el canje
        // se veía como una simple vuelta atrás sin ninguna señal de que se
        // había descontado el coste (encargo kanban "Snackbars de éxito en
        // canjear/donar/agradecer puntos", 2026-09-12).
        LaunchedEffect(actionState) {
            if (actionState is RewardActionState.Success) {
                isCelebrating = true
                // El snackbar se lanza en paralelo (no se espera su duración
                // completa): el pop() se retrasa 550ms de forma explícita
                // (0ms si reduce-motion) para que la animación del icono sea
                // visible antes de volver a la lista, independientemente de
                // cuánto tarde el snackbar en autodescartarse.
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = s("member_reward_redeemed_success"),
                        duration = SnackbarDuration.Short
                    )
                }
                if (!reduceMotion) delay(550)
                memberModel.clearRewardAction()
                navigator.pop()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = s("member_reward_title"),
                    subtitle = householdName,
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
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = reward.icon,
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale.value
                                scaleY = iconScale.value
                            }
                        )
                        if (isCelebrating && !reduceMotion && effectsOn) {
                            ConfettiOverlay(modifier = Modifier.matchParentSize(), particleCount = 8)
                        }
                    }

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
                            containerColor = if (canAfford) MaterialTheme.semanticColors.successContainer else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = s("member_reward_cost_label"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "⭐ ${reward.cost}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (canAfford) MaterialTheme.semanticColors.onSuccessContainer else MaterialTheme.colorScheme.error
                            )

                            Spacer(Modifier.height(8.dp))

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 32.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = s("member_reward_your_points_label"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "⭐ $memberPoints",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (canAfford) MaterialTheme.semanticColors.onSuccessContainer else MaterialTheme.colorScheme.error
                            )

                            if (!canAfford) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = s("member_reward_missing_points").replace("%d", (reward.cost - memberPoints).toString()),
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
                                modifier = Modifier
                                    .padding(12.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
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
                        enabled = canAfford && !isRedeeming && !isLoadingMember,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        if (isRedeeming || isLoadingMember) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                // El botón usa containerColor = tertiary; onPrimary
                                // desentonaba en modo oscuro (panel v7 2026-09-10,
                                // Exp. 1/4, MENOR-IMPORTANTE, SIGUE ABIERTO).
                                color = MaterialTheme.colorScheme.onTertiary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (canAfford) s("member_reward_redeem_btn") else s("member_reward_insufficient"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!canAfford) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { navigator.pop() }) {
                            Text(s("member_reward_back_to_rewards"))
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            }
        }

        // Confirmation dialog — unificado con DestructiveConfirmDialog (no es
        // una acción destructiva: canjear una recompensa gasta puntos pero no
        // borra ni pierde datos, panel v7, #24).
        if (showConfirmDialog) {
            DestructiveConfirmDialog(
                title = s("member_reward_confirm_title"),
                text = s("member_reward_confirm_text")
                    .replace("%1", reward.title)
                    .replace("%2", reward.cost.toString())
                    .replace("%3", (memberPoints - reward.cost).toString()),
                s = s,
                onDismiss = { showConfirmDialog = false },
                onConfirm = {
                    showConfirmDialog = false
                    memberModel.redeemReward(
                        householdId = householdId,
                        rewardId = reward.id,
                        memberId = memberId,
                        pointsSpent = reward.cost
                    )
                },
                confirmLabel = s("member_reward_confirm_yes"),
                destructive = false
            )
        }
    }
}