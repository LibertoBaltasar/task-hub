package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.UserAvatar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.ProfileScreenModel
import org.taskhub.ui.models.ProfileUiState
import org.taskhub.ui.models.ProfileSaveState
import org.taskhub.ui.theme.Teal600

/**
 * Pantalla para editar el perfil GLOBAL del usuario actual.
 * Accesible desde Ajustes → "Editar perfil".
 *
 * Campos editables:
 * - Nombre público (displayName)
 * - Avatar emoji (selector rápido de emojis)
 * - Bio (frase corta)
 * - Estado (mensaje efímero)
 */
class EditProfileScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ProfileScreenModel>()
        val profileState by model.myProfileState.collectAsState()
        val saveState by model.saveState.collectAsState()
        val focusManager = LocalFocusManager.current
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        // Form fields — filled from loaded profile
        var displayName by remember { mutableStateOf("") }
        var avatarEmoji by remember { mutableStateOf("") }
        var bio by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("") }

        // Emoji picker — grid de emojis comunes
        val emojiOptions = remember {
            listOf("🧑", "👩", "👨", "👦", "👧", "🧒", "🐱", "🐶", "🐼", "🦊", "🐸", "🐵",
                   "🌟", "🔥", "💎", "🎮", "📚", "🎨", "⚽", "🍕", "☕", "🦸", "🧙", "🤖")
        }

        // Cargar perfil al entrar
        LaunchedEffect(Unit) {
            model.loadMyProfile()
        }

        // Rellenar campos cuando el perfil cargue
        LaunchedEffect(profileState) {
            if (profileState is ProfileUiState.Success) {
                val p = (profileState as ProfileUiState.Success).profile
                if (displayName.isEmpty()) displayName = p.displayName
                if (avatarEmoji.isEmpty()) avatarEmoji = p.avatarEmoji
                if (bio.isEmpty()) bio = p.bio
                if (status.isEmpty()) status = p.status
            }
        }

        // Navegar atrás al guardar
        LaunchedEffect(saveState) {
            if (saveState is ProfileSaveState.Saved) {
                model.clearSaveState()
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Editar perfil", fontWeight = FontWeight.Bold) },
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
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "❌ ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { model.loadMyProfile() }) {
                                Text("Reintentar")
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // ── Avatar preview: foto > emoji > inicial ──
                        val currentAvatarUrl = (state as? ProfileUiState.Success)?.profile?.avatarUrl
                        UserAvatar(
                            avatarUrl = currentAvatarUrl,
                            fallbackEmoji = avatarEmoji,
                            displayName = displayName,
                            contentDescription = s("profile_avatar_content_desc"),
                            size = 96.dp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Text(
                            text = "Elige tu avatar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Emoji grid
                        var showEmojiGrid by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showEmojiGrid = !showEmojiGrid },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (avatarEmoji.isNotEmpty()) "Avatar: $avatarEmoji" else "Seleccionar emoji...")
                        }

                        if (showEmojiGrid) {
                            // Grid de 6 columnas
                            val rows = emojiOptions.chunked(6)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                rows.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        row.forEach { emoji ->
                                            Surface(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .semantics { contentDescription = "Emoji $emoji" }
                                                    .clickable {
                                                        avatarEmoji = emoji
                                                        showEmojiGrid = false
                                                    },
                                                shape = MaterialTheme.shapes.medium,
                                                color = if (avatarEmoji == emoji)
                                                    Teal600.copy(alpha = 0.15f)
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                border = if (avatarEmoji == emoji)
                                                    androidx.compose.foundation.BorderStroke(2.dp, Teal600)
                                                else null
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // ── Nombre ──
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Nombre público") },
                            placeholder = { Text("Ej: María, Papá...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // ── Bio ──
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 100) bio = it },
                            label = { Text("Bio") },
                            placeholder = { Text("Ej: Papá, profe de mates y cocinero") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            supportingText = { Text("${bio.length}/100") }
                        )

                        // ── Estado ──
                        OutlinedTextField(
                            value = status,
                            onValueChange = { if (it.length <= 80) status = it },
                            label = { Text("Estado") },
                            placeholder = { Text("Ej: 🍳 Preparando la cena") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            supportingText = { Text("${status.length}/80") }
                        )

                        Spacer(Modifier.height(8.dp))

                        // ── Save button ──
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                model.saveProfile(
                                    displayName = displayName.trim(),
                                    bio = bio.trim(),
                                    status = status.trim(),
                                    avatarEmoji = avatarEmoji
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = displayName.isNotBlank() &&
                                     saveState !is ProfileSaveState.Saving,
                            shape = MaterialTheme.shapes.large
                        ) {
                            if (saveState is ProfileSaveState.Saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Guardar perfil", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        // Error al guardar
                        if (saveState is ProfileSaveState.Error) {
                            Text(
                                "❌ ${(saveState as ProfileSaveState.Error).message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}