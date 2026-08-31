package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.UserProfile
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.UserAvatar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.ProfileScreenModel
import org.taskhub.ui.models.ProfileUiState
import org.taskhub.ui.theme.*

/**
 * Perfil público de un miembro del espacio, visible al clicar su nombre.
 * Muestra la información global del usuario (UserProfile) y los datos
 * específicos de su membresía en este hogar (MemberResponse).
 *
 * @param userId UID del usuario (para cargar su UserProfile global).
 * @param member Datos del miembro en este hogar (rol, puntos, racha...).
 */
data class PublicProfileScreen(
    val userId: String,
    val member: MemberResponse
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ProfileScreenModel>()
        val profileState by model.otherProfileState.collectAsState()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        LaunchedEffect(userId) {
            model.loadUserProfile(userId)
        }

        Scaffold(
            // TaskHubTopBar (no TopAppBar manual): consistencia con las otras
            // pantallas — la barra manual dejaba el título alineado a la izquierda.
            topBar = {
                TaskHubTopBar(
                    title = member.displayName,
                    onBack = { navigator.pop() }
                )
            }
        ) { padding ->
            when (val state = profileState) {
                is ProfileUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is ProfileUiState.Error -> {
                    // Si falla cargar el UserProfile, mostramos lo que tenemos del Member
                    PublicProfileContent(
                        padding = padding,
                        displayName = member.displayName,
                        avatarUrl = member.avatarUrl,
                        avatarEmoji = "",
                        bio = "",
                        status = "",
                        role = member.role,
                        totalPoints = member.totalPoints,
                        currentStreak = member.currentStreak,
                        bestStreak = member.bestStreak
                    )
                }

                is ProfileUiState.Success -> {
                    val profile = state.profile
                    PublicProfileContent(
                        padding = padding,
                        displayName = profile.displayName.ifBlank { member.displayName },
                        avatarUrl = profile.avatarUrl ?: member.avatarUrl,
                        avatarEmoji = profile.avatarEmoji,
                        bio = profile.bio,
                        status = profile.status,
                        role = member.role,
                        totalPoints = member.totalPoints,
                        currentStreak = member.currentStreak,
                        bestStreak = member.bestStreak
                    )
                }

                is ProfileUiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Contenido reutilizable del perfil público, con o sin UserProfile cargado.
 */
@Composable
private fun PublicProfileContent(
    padding: PaddingValues,
    displayName: String,
    avatarUrl: String?,
    avatarEmoji: String,
    bio: String,
    status: String,
    role: String,
    totalPoints: Int,
    currentStreak: Int,
    bestStreak: Int
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Avatar grande ──
        UserAvatar(
            avatarUrl = avatarUrl,
            fallbackEmoji = avatarEmoji.ifEmpty { if (role == "admin") "👑" else "👤" },
            displayName = displayName,
            contentDescription = displayName,
            size = 100.dp,
            backgroundColor = if (role == "admin") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        )

        // ── Nombre ──
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // ── Rol en este espacio ──
        // Coral500/Teal600 fijos al 12% de alpha sobre el fondo del tema fallaban
        // AA (2.49-3.13:1 en los 3 temas claros) — tertiary/onTertiary y
        // primaryContainer/onPrimaryContainer ya están auditados ≥4.5:1, mismo
        // par que usa PointsBadge (BadgeTone.Coral/Teal) para este mismo concepto.
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (role == "admin") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = if (role == "admin") s("public_profile_role_admin") else s("member_role_child_short"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (role == "admin") MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ── Estado (si hay) ──
        if (status.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Bio ──
        if (bio.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = s("public_profile_about_me"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── Estadísticas ──
        Text(
            text = s("public_profile_stats_title"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Puntos totales
            StatCard(
                modifier = Modifier.weight(1f),
                emoji = "⭐",
                value = "$totalPoints",
                label = s("public_profile_stat_points")
            )
            // Racha actual
            StatCard(
                modifier = Modifier.weight(1f),
                emoji = "🔥",
                value = "$currentStreak",
                label = s("public_profile_stat_streak")
            )
            // Mejor racha
            StatCard(
                modifier = Modifier.weight(1f),
                emoji = "🏆",
                value = "$bestStreak",
                label = s("public_profile_stat_record")
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Tarjeta de estadística pequeña (puntos, racha, récord...).
 */
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    emoji: String,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}