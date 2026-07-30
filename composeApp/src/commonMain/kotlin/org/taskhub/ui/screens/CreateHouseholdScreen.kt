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

class CreateHouseholdScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<HouseholdScreenModel>()
        val uiState by model.uiState.collectAsState()

        var householdName by remember { mutableStateOf("") }
        val focusManager = LocalFocusManager.current

        LaunchedEffect(Unit) {
            model.reset()
        }

        // Navegar a la pantalla de crear perfil cuando se crea exitosamente
        LaunchedEffect(uiState) {
            if (uiState is HouseholdUiState.Success) {
                navigator.replaceAll(CreateProfileScreen((uiState as HouseholdUiState.Success).household.id))
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
                // Header con back button
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
                    text = "🏠",
                    style = MaterialTheme.typography.displayMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Crear nuevo hogar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Dale un nombre a tu hogar para empezar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = householdName,
                    onValueChange = { householdName = it },
                    label = { Text("Nombre del hogar") },
                    placeholder = { Text("Ej: Casa López") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    enabled = uiState !is HouseholdUiState.Loading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        model.createHousehold(householdName.trim())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = householdName.isNotBlank() && uiState !is HouseholdUiState.Loading,
                    shape = MaterialTheme.shapes.large
                ) {
                    if (uiState is HouseholdUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Crear hogar", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Show errors
                when (val state = uiState) {
                    is HouseholdUiState.Error -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "❌ ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}
