package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SavedHousehold
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.theme.Coral500
import org.taskhub.ui.theme.Teal600

import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Pantalla de perfil del usuario. Muestra:
 * - Sus hogares (Personal + compartidos)
 * - Acceso a crear/unirse a hogares
 * - Acceso a ajustes
 */
class ProfileScreen(private val households: List<SavedHousehold>) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdStore = koinInject<HouseholdStore>()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        var showSettings by remember { mutableStateOf(false) }

        if (showSettings) {
            Dialog(
                onDismissRequest = { showSettings = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.85f),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SettingsSheet(
                        callbacks = SettingsCallbacks(
                            onExportCsv = { },
                            onDismiss = { showSettings = false },
                            onEditProfile = { navigator.push(EditProfileScreen()) }
                        )
                    )
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Perfil", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cabecera
                item {
                    Text(
                        "Tus espacios",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Espacio Personal
                val personal = households.find { it.isPersonal }
                if (personal != null) {
                    item {
                        HouseholdProfileCard(
                            household = personal,
                            icon = Icons.Default.Person,
                            color = Teal600
                        )
                    }
                }

                // Hogares compartidos
                val shared = households.filter { !it.isPersonal }
                items(shared, key = { it.id }) { household ->
                    HouseholdProfileCard(
                        household = household,
                        icon = Icons.Default.Edit,
                        color = Coral500,
                        onNavigate = { navigator.push(HouseholdScreen(household.id)) }
                    )
                }

                // Acciones
                item { Spacer(Modifier.height(8.dp)) }

                item {
                    Button(
                        onClick = { navigator.push(CreateHouseholdScreen()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Crear nuevo espacio")
                        Spacer(Modifier.width(8.dp))
                        Text("Crear nuevo espacio")
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { navigator.push(JoinHouseholdScreen()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Unirse a un espacio")
                        Spacer(Modifier.width(8.dp))
                        Text("Unirse a un espacio")
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }

                // Ajustes
                item {
                    OutlinedButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        Spacer(Modifier.width(8.dp))
                        Text("Ajustes")
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de hogar en el perfil.
 */
@Composable
private fun HouseholdProfileCard(
    household: SavedHousehold,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onNavigate: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (onNavigate != null) Modifier.clickable { onNavigate() } else Modifier
        ),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = household.name, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    household.name,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (household.isPersonal) {
                    Text(
                        "Espacio privado · Solo tú",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Código: ${household.inviteCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onNavigate != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Ir al espacio",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                )
            }
        }
    }
}