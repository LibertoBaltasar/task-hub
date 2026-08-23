package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.taskhub.ui.components.UserAvatar
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

        LaunchedEffect(userId) {
            model.loadUserProfile(userId)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(member.displayName, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            when (val state = profileState) {
                is ProfileUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal600)
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
                        CircularProgressIndicator(color = Teal600)
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
            fallbackEmoji = avatarEmoji.ifEmpty { if (role == "admin") "👑" else "🧒" },
            displayName = displayName,
            contentDescription = displayName,
            size = 100.dp,
            backgroundColor = if (role == "admin") Coral100 else Teal100
        )

        // ── Nombre ──
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // ── Rol en este espacio ──
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (role == "admin") Coral500.copy(alpha = 0.12f) else Teal600.copy(alpha = 0.12f)
        ) {
            Text(
                text = if (role == "admin") "👑 Administrador" else "🧒 Niño/a",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (role == "admin") Coral500 else Teal600,
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
                        text = "Sobre mí",
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
            text = "📊 Estadísticas en este espacio",
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
                label = "Puntos"
            )
            // Racha actual
            StatCard(
                modifier = Modifier.weight(1f),
                emoji = "🔥",
                value = "$currentStreak",
                label = "Racha"
            )
            // Mejor racha
            StatCard(
                modifier = Modifier.weight(1f),
                emoji = "🏆",
                value = "$bestStreak",
                label = "Récord"
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