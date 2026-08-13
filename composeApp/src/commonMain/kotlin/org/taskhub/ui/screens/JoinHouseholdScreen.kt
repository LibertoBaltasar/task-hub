package org.taskhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HouseholdUiState
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState

class JoinHouseholdScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdModel = koinScreenModel<HouseholdScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val householdState by householdModel.uiState.collectAsState()
        val memberState by memberModel.uiState.collectAsState()

        var inviteCode by remember { mutableStateOf("") }
        var displayName by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("child") }
        val focusManager = LocalFocusManager.current

        // Track the joined household
        var joinedHouseholdId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            householdModel.reset()
            memberModel.reset()
        }

        // When household join succeeds, capture the household id
        LaunchedEffect(householdState) {
            when (val state = householdState) {
                is HouseholdUiState.Success -> {
                    if (joinedHouseholdId == null) {
                        joinedHouseholdId = (state).household.id
                    }
                }
                is HouseholdUiState.AlreadyMember -> {
                    navigator.replaceAll(HouseholdScreen(state.household.id))
                }
                else -> {}
            }
        }

        // When member is created, navigate to household
        LaunchedEffect(memberState) {
            if (memberState is MemberUiState.Success && joinedHouseholdId != null) {
                navigator.replaceAll(HouseholdScreen(joinedHouseholdId!!))
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { navigator.pop() }) {
                        Text("← Volver")
                    }
                    Spacer(Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Step indicator
                val step = when {
                    joinedHouseholdId == null -> 1
                    householdState is HouseholdUiState.Success && memberState !is MemberUiState.Success -> 2
                    else -> 1
                }

                Text(
                    text = "🔑",
                    style = MaterialTheme.typography.displayMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Unirse a un hogar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (step == 1) "Paso 1: Introduce el código de invitación"
                    else "Paso 2: Crea tu perfil",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Step 1: Enter invite code
                if (joinedHouseholdId == null) {
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase().take(8) },
                        label = { Text("Código de invitación") },
                        placeholder = { Text("Ej: ABC12345") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        enabled = householdState !is HouseholdUiState.Loading
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            householdModel.joinHousehold(inviteCode.trim())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = inviteCode.trim().length >= 4 && householdState !is HouseholdUiState.Loading,
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (householdState is HouseholdUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Unirse", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (householdState is HouseholdUiState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "❌ ${(householdState as HouseholdUiState.Error).message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Step 2: Create profile (after joining)
                if (joinedHouseholdId != null && householdState is HouseholdUiState.Success) {
                    val household = (householdState as HouseholdUiState.Success).household

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "✅ Te has unido a:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = household.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Tu nombre") },
                        placeholder = { Text("Ej: María") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = memberState !is MemberUiState.Loading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Rol en el hogar:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = role == "admin",
                            onClick = { role = "admin" },
                            label = { Text("👨‍👩‍👧 Admin") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = role == "child",
                            onClick = { role = "child" },
                            label = { Text("🧒 Niño/a") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val userId = householdModel.getLocalId()
                            memberModel.addMember(joinedHouseholdId!!, displayName.trim(), role, userId = userId, inviteCode = inviteCode.trim())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = displayName.isNotBlank() && memberState !is MemberUiState.Loading,
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (memberState is MemberUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Entrar al hogar", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (memberState is MemberUiState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "❌ ${(memberState as MemberUiState.Error).message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
