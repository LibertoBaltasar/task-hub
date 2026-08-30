package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.network.FirestoreRepository
import org.taskhub.ui.components.BadgeTone
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.RewardActionState
import org.taskhub.ui.theme.*

data class CreateRewardScreen(val householdId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val memberModel = koinScreenModel<MemberScreenModel>()
        val actionState by memberModel.rewardActionState.collectAsState()
        val repo = koinInject<FirestoreRepository>()

        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var costText by remember { mutableStateOf("") }
        var selectedIcon by remember { mutableStateOf("🎁") }

        // Emoji picker state
        var showEmojiPicker by remember { mutableStateOf(false) }

        val commonEmojis = listOf(
            "🎁", "⭐", "🏆", "🎮", "🎬", "🎵", "🎨", "🎯",
            "🍕", "🍦", "🍿", "🍩", "☕", "🍪", "🧁", "🍭",
            "📱", "💻", "🎧", "📚", "🎲", "🧩", "🎸", "⚽",
            "🏀", "🎾", "🚲", "🛹", "🎪", "🎢", "🎠", "🦄",
            "🌟", "💎", "🔥", "❤️", "🎉", "✨", "💫", "🌈"
        )

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
                    title = "Nueva recompensa",
                    onBack = { navigator.pop() }
                )

                // Form
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Icon selector
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEmojiPicker = !showEmojiPicker },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ícono",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = selectedIcon,
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Toca para cambiar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Emoji picker
                    if (showEmojiPicker) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(8),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(commonEmojis, key = { it }) { emoji ->
                                    Surface(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .semantics { contentDescription = "Emoji $emoji" }
                                            .clickable {
                                                selectedIcon = emoji
                                                showEmojiPicker = false
                                            },
                                        shape = MaterialTheme.shapes.small,
                                        color = if (selectedIcon == emoji) Teal100
                                            else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = emoji,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        placeholder = { Text("Ej: 1 hora de videojuegos") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal600,
                            focusedLabelColor = Teal600,
                            cursorColor = Teal600
                        )
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción (opcional)") },
                        placeholder = { Text("Elige cuándo y cómo se entrega...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal600,
                            focusedLabelColor = Teal600,
                            cursorColor = Teal600
                        )
                    )

                    // Cost
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { newValue ->
                            // Only allow digits
                            if (newValue.all { it.isDigit() } && newValue.length <= 6) {
                                costText = newValue
                            }
                        },
                        label = { Text("Coste en puntos") },
                        placeholder = { Text("50") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        prefix = { Text("⭐ ") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal600,
                            focusedLabelColor = Teal600,
                            cursorColor = Teal600
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Preview card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Teal50
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Vista previa",
                                style = MaterialTheme.typography.labelSmall,
                                color = Teal700
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = selectedIcon,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = title.ifEmpty { "Título" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (description.isNotEmpty()) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = Coral500
                            ) {
                                Text(
                                    text = "⭐ ${costText.ifEmpty { "0" }}",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Error message
                    if (actionState is RewardActionState.Error) {
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

                    Spacer(Modifier.height(16.dp))

                    // Create button
                    val cost = costText.toIntOrNull() ?: 0
                    val isCreating = actionState is RewardActionState.Loading
                    val isValid = title.isNotBlank() && cost > 0

                    Button(
                        onClick = {
                            val localId = repo.getLocalId() ?: ""
                            memberModel.createReward(
                                householdId = householdId,
                                title = title.trim(),
                                description = description.trim(),
                                cost = cost,
                                icon = selectedIcon,
                                createdBy = localId
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isValid && !isCreating,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Crear Recompensa",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}