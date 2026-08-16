package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.taskhub.network.models.MemberResponse
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import org.taskhub.ui.components.BadgeTone
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HouseholdUiState
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.NotificationScreenModel
import org.taskhub.ui.theme.*
import org.taskhub.platform.QrCodeImage
import org.taskhub.platform.shareText

data class HouseholdScreen(val householdId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdModel = koinScreenModel<HouseholdScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val notificationModel = koinScreenModel<NotificationScreenModel>()
        val householdState by householdModel.uiState.collectAsState()
        val memberState by memberModel.uiState.collectAsState()
        val notificationUnreadCount by notificationModel.unreadCount.collectAsState()

        // Double-confirmation dialog state
        var showConfirmDialog1 by remember { mutableStateOf(false) }
        var showConfirmDialog2 by remember { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }

        // Leave (desvincularse) dialog state
        var showLeaveDialog by remember { mutableStateOf(false) }
        var isLeaving by remember { mutableStateOf(false) }

        // QR / Share dialog state
        var showQrDialog by remember { mutableStateOf(false) }

        // Settings dialog state
        var showSettings by remember { mutableStateOf(false) }
        val appSettings = LocalAppSettings.current

        LaunchedEffect(householdId) {
            householdModel.loadHousehold(householdId)
            memberModel.loadMembers(householdId)
        }

        // Poll for notification unread count every 30 seconds
        var memberId by remember { mutableStateOf("") }
        LaunchedEffect(memberState) {
            if (memberState is MemberUiState.Success) {
                memberId = (memberState as MemberUiState.Success).members.firstOrNull()?.id ?: ""
            }
        }
        LaunchedEffect(householdId, memberId) {
            if (memberId.isNotEmpty()) {
                notificationModel.refreshUnreadCount(householdId, memberId)
                while (true) {
                    kotlinx.coroutines.delay(30_000L)
                    notificationModel.refreshUnreadCount(householdId, memberId)
                }
            }
        }

        // ── Deletion dialogs ──
        val householdName = when (val hState = householdState) {
            is HouseholdUiState.Success -> hState.household.name
            else -> ""
        }

        val inviteCode = when (val hState = householdState) {
            is HouseholdUiState.Success -> hState.household.inviteCode
            else -> ""
        }

        // QR / Share dialog
        if (showQrDialog && inviteCode.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                title = {
                    Text(
                        "Código de invitación",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // QR Code
                        QrCodeImage(
                            text = inviteCode,
                            modifier = Modifier.size(220.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        // Code text
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Comparte este código para invitar miembros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            shareText(
                                "Únete a mi hogar en Task Hub: $inviteCode. " +
                                    "Descárgala en: https://play.google.com/store/apps/details?id=org.taskhub",
                                "Invitación a Task Hub"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Compartir")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQrDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }

        if (showConfirmDialog1) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog1 = false },
                title = { Text("Eliminar hogar") },
                text = {
                    Text("¿Eliminar '$householdName'? Esta acción no se puede deshacer.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog1 = false
                        showConfirmDialog2 = true
                    }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog1 = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showConfirmDialog2) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog2 = false },
                title = { Text("¿Estás completamente seguro?") },
                text = {
                    Text("Se perderán todas las tareas y miembros de '$householdName'.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog2 = false
                        isDeleting = true

                        householdModel.deleteHousehold(
                            householdId = householdId,
                            onSuccess = {
                                navigator.replaceAll(HomeScreen())
                            },
                            onError = { _ ->
                                isDeleting = false
                            }
                        )
                    }) {
                        Text("Sí, eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog2 = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showLeaveDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text("Salir del hogar") },
                text = {
                    Text(
                        "¿Desvincularte de '$householdName'? Dejarás de verlo en tu " +
                            "dispositivo. Si eres el último miembro, el hogar se eliminará."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        isLeaving = true

                        householdModel.leaveHousehold(
                            householdId = householdId,
                            onSuccess = {
                                navigator.replaceAll(HomeScreen())
                            },
                            onError = { _ ->
                                isLeaving = false
                            }
                        )
                    }) {
                        Text("Salir", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // ── Settings dialog ──
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
                            onExportCsv = { /* CSV export available from task list */ },
                            onDismiss = { showSettings = false }
                        )
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = "Task Hub",
                    onBack = { navigator.replaceAll(HomeScreen()) },
                    actions = {
                        // Notification bell with badge
                        Box {
                            IconButton(
                                onClick = {
                                    val mid = memberId
                                    if (mid.isNotEmpty()) {
                                        navigator.push(NotificationListScreen(householdId, mid))
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                            }
                            if (notificationUnreadCount > 0) {
                                PointsBadge(
                                    text = if (notificationUnreadCount > 99) "99+" else notificationUnreadCount.toString(),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                )
                            }
                        }
                        // Settings
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                        // Delete
                        if (isDeleting || isLeaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { showConfirmDialog1 = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar hogar")
                            }
                        }
                    }
                )

                if (isDeleting || isLeaving) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal600)
                    }
                } else {
                    // Content
                    when (val hState = householdState) {
                        is HouseholdUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Teal600)
                            }
                        }

                        is HouseholdUiState.Success -> {
                            val household = hState.household

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Household info card
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showQrDialog = true },
                                        colors = CardDefaults.cardColors(
                                            containerColor = Teal50
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "🏠",
                                                style = MaterialTheme.typography.displaySmall
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = household.name,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = Teal900,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = "Código de invitación",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Text(
                                                text = household.inviteCode,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = "Comparte este código para invitar miembros",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                // Navigation to tasks + stats + ranking
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val mState = memberState
                                                val memberId = if (mState is MemberUiState.Success) mState.members.firstOrNull()?.id else null
                                                navigator.push(TaskListScreen(householdId, memberId))
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text(
                                                text = "Ver Tareas",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val mState = memberState
                                                val memberId = (mState as? MemberUiState.Success)?.members?.firstOrNull()?.id ?: return@Button
                                                navigator.push(StatsScreen(householdId, memberId))
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text(
                                                text = "Estadísticas",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Ranking button
                                item {
                                    Button(
                                        onClick = { navigator.push(RankingScreen(householdId)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = "Ranking",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Rewards button
                                item {
                                    Button(
                                        onClick = { navigator.push(RewardListScreen(householdId)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = "Recompensas",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Calendar button
                                item {
                                    Button(
                                        onClick = {
                                            val mState = memberState
                                            val memberId = (mState as? MemberUiState.Success)?.members?.firstOrNull()?.id
                                            navigator.push(CalendarScreen(householdId, memberId))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = "Calendario",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Members header
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "👥 Miembros",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(Modifier.weight(1f))

                                        when (val mState = memberState) {
                                            is MemberUiState.Success -> {
                                                Text(
                                                    text = "${mState.members.size}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            else -> {}
                                        }
                                    }
                                }

                                // Members list
                                when (val mState = memberState) {
                                    is MemberUiState.Loading -> {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(100.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = Teal600)
                                            }
                                        }
                                    }

                                    is MemberUiState.Success -> {
                                        if (mState.members.isEmpty()) {
                                            item {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                ) {
                                                    Text(
                                                        text = "No hay miembros aún. ¡Invita a alguien!",
                                                        modifier = Modifier.padding(24.dp),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        } else {
                                            items(mState.members) { member ->
                                                MemberCard(member)
                                            }
                                        }
                                    }

                                    is MemberUiState.Error -> {
                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                                )
                                            ) {
                                                Text(
                                                    text = "Error: ${mState.message}",
                                                    modifier = Modifier.padding(16.dp),
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }

                                    is MemberUiState.Idle -> {}
                                }

                                // Salir (desvincularse) del hogar
                                item {
                                    OutlinedButton(
                                        onClick = { showLeaveDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = "🚪 Salir del hogar",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                // Bottom spacer
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }

                        is HouseholdUiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "❌ ${hState.message}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { householdModel.loadHousehold(householdId) }) {
                                        Text("Reintentar")
                                    }
                                }
                            }
                        }

                        is HouseholdUiState.AlreadyMember -> {
                            // Already a member — this shouldn't normally be shown on this screen
                            // Navigate directly
                            LaunchedEffect(Unit) {
                                navigator.replaceAll(HouseholdScreen(hState.household.id))
                            }
                        }

                        is HouseholdUiState.Idle -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(member: MemberResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (member.role == "admin") Coral100 else Teal100
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (member.role == "admin") "👑 Admin" else "🧒 Niño/a",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (member.role == "admin") "Administrador" else "Niño/a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Points badge
            PointsBadge(
                text = "${member.totalPoints} pts",
                modifier = Modifier
            )
        }
    }
}