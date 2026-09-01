package org.taskhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.ShimmerList
import org.taskhub.ui.components.UserAvatar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.theme.*

/**
 * Contenido reutilizable del ranking (sin barra superior), para la pantalla
 * combinada. Reutiliza el [MemberScreenModel] que ya crea [ExploreScreen] en
 * vez de inyectar `FirestoreRepository` directamente — antes esta pantalla
 * duplicaba su propia carga de miembros al margen de cualquier ScreenModel.
 */
@Composable
internal fun RankingBody(householdId: String, memberModel: MemberScreenModel) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    val uiState by memberModel.uiState.collectAsState()

    LaunchedEffect(householdId) {
        memberModel.loadMembers(householdId)
    }

    // Sort by totalPoints descending (getMembers ya filtra miembros que abandonaron)
    val members = remember(uiState) {
        (uiState as? MemberUiState.Success)?.members
            ?.sortedByDescending { it.totalPoints }
            ?: emptyList()
    }
    val isLoading = uiState is MemberUiState.Loading || uiState is MemberUiState.Idle
    val errorMessage = (uiState as? MemberUiState.Error)?.message

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                ShimmerList(count = 5, itemHeight = 64.dp)
            }
        }
        errorMessage != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = s("error_icon_content_desc"),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { memberModel.loadMembers(householdId) }) { Text(s("tasks_retry")) }
                }
            }
        }
        members.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        s("ranking_empty_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s("ranking_empty_subtitle"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(members, key = { _, member -> member.id }) { index, member ->
                    RankingRow(
                        position = index + 1,
                        member = member
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RankingRow(
    position: Int,
    member: MemberResponse
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    // Medal colours for top 3
    val medalEmoji = when (position) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "  $position"
    }

    val bgColor = when (position) {
        1 -> MaterialTheme.colorScheme.tertiaryContainer
        2 -> MaterialTheme.colorScheme.primaryContainer
        3 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    // tertiaryContainer/onTertiaryContainer y primaryContainer/onPrimaryContainer
    // (antes Coral100/Teal50 fijos + Coral800/Teal800 fijos): siguen el tema
    // activo (Naturaleza/Minimal) y ya son pares auditados ≥4.5:1 en las 6
    // combinaciones tema/modo, mismo criterio que otras 6+ cards de la app.
    val secondaryTextColor = when (position) {
        1 -> MaterialTheme.colorScheme.onTertiaryContainer
        2, 3 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (position <= 3) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position with text descriptor
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp)
            ) {
                Text(
                    text = medalEmoji,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = when (position) {
                        1 -> "1º"
                        2 -> "2º"
                        3 -> "3º"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.width(12.dp))

            // Avatar
            UserAvatar(
                avatarUrl = member.avatarUrl,
                fallbackEmoji = if (member.role == "admin") "👑" else "👤",
                displayName = member.displayName,
                contentDescription = member.displayName,
                backgroundColor = if (member.role == "admin") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
            )

            Spacer(Modifier.width(12.dp))

            // Name + role
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (member.role == "admin") s("ranking_role_admin") else s("member_role_child_full"),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }

            // Points
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "⭐ ${member.totalPoints}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "🔥 ${member.currentStreak}",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
        }
    }
}
