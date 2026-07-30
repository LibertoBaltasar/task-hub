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

data class CreateProfileScreen(val householdId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdModel = koinScreenModel<HouseholdScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val householdState by householdModel.uiState.collectAsState()
        val memberState by memberModel.uiState.collectAsState()

        var displayName by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("child") }
        val focusManager = LocalFocusManager.current

        LaunchedEffect(Unit) {
            memberModel.reset()
        }

        // Load household info if not already loaded
        LaunchedEffect(householdId) {
            if (householdState is HouseholdUiState.Idle) {
                householdModel.loadHousehold(householdId)
            }
        }

        // When member is created, navigate to household
        LaunchedEffect(memberState) {
            if (memberState is MemberUiState.Success) {
                navigator.replaceAll(HouseholdScreen(householdId))
            }
        }

        // Check if current user is already a member — skip this step
        LaunchedEffect(householdState) {
            if (householdState is HouseholdUiState.AlreadyMember) {
                navigator.replaceAll(HouseholdScreen(householdId))
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

                Text(
                    text = "👤",
                    style = MaterialTheme.typography.displayMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Crear tu perfil",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Show household name if available
                val householdName = when (val state = householdState) {
                    is HouseholdUiState.Success -> state.household.name
                    else -> null
                }

                Text(
                    text = if (householdName != null) {
                        "Crea tu perfil para el hogar \"$householdName\""
                    } else {
                        "Crea tu perfil para este hogar"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Tu nombre") },
                    placeholder = { Text("Ej: María") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
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
                        memberModel.addMember(householdId, displayName.trim(), role, userId = userId)
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
                        Text("Crear perfil", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Show errors
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
