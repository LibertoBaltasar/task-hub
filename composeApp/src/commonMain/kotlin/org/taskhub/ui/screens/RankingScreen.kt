package org.taskhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.components.ShimmerList
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.UserAvatar
import org.taskhub.ui.theme.*
import kotlinx.coroutines.launch

data class RankingScreen(val householdId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = "Ranking",
                    onBack = { navigator.pop() }
                )

                RankingBody(householdId)
            }
        }
    }
}

/** Contenido reutilizable del ranking (sin barra superior), para la pantalla combinada. */
@Composable
internal fun RankingBody(householdId: String) {
    val repo = koinInject<FirestoreRepository>()

    var members by remember { mutableStateOf<List<MemberResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // ── Función de carga extraíble para poder reintentar ──
    suspend fun loadRanking() {
        isLoading = true
        try {
            val all = repo.getMembers(householdId)
            // Sort by totalPoints descending (getMembers ya filtra miembros que abandonaron)
            members = all.sortedByDescending { it.totalPoints }
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = e.message ?: "Error al cargar miembros"
        }
        isLoading = false
    }

    LaunchedEffect(householdId) {
        loadRanking()
    }

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
                    Text("❌ $errorMessage", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { coroutineScope.launch { loadRanking() } }) { Text("Reintentar") }
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
                        "Aún no hay nadie en el ranking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Completa tareas para sumar puntos y aparecer aquí.",
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
                itemsIndexed(members) { index, member ->
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
    // Medal colours for top 3
    val medalEmoji = when (position) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "  $position"
    }

    val bgColor = when (position) {
        1 -> Coral100
        2 -> Teal50
        3 -> Teal50
        else -> MaterialTheme.colorScheme.surface
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.width(12.dp))

            // Avatar
            UserAvatar(
                avatarUrl = member.avatarUrl,
                fallbackEmoji = if (member.role == "admin") "👑" else "🧒",
                displayName = member.displayName,
                contentDescription = member.displayName,
                backgroundColor = if (member.role == "admin") Coral100 else Teal100
            )

            Spacer(Modifier.width(12.dp))

            // Name + role
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (member.role == "admin") "Admin" else "Niño/a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Points
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "⭐ ${member.totalPoints}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Coral700
                )
                Text(
                    text = "🔥 ${member.currentStreak}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
