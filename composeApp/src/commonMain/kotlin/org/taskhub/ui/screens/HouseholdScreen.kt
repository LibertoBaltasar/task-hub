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
import androidx.compose.foundation.lazy.rememberLazyListState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.MessageResponse
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import org.taskhub.ui.components.BadgeTone
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.components.SettingsCallbacks
import org.taskhub.ui.components.SettingsSheet
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.UserAvatar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.AppreciateActionState
import org.taskhub.ui.models.CalendarSyncManager
import org.taskhub.ui.models.DonateActionState
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HouseholdUiState
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.MessagesUiState
import org.taskhub.ui.models.NotificationScreenModel
import org.taskhub.ui.theme.*
import org.taskhub.platform.QrCodeImage
import org.taskhub.platform.shareText
import org.taskhub.platform.logAnalyticsEvent

data class HouseholdScreen(val householdId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val householdModel = koinScreenModel<HouseholdScreenModel>()
        val memberModel = koinScreenModel<MemberScreenModel>()
        val notificationModel = koinScreenModel<NotificationScreenModel>()
        val repo = koinInject<FirestoreRepository>()
        val calendarSync = koinInject<CalendarSyncManager>()
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

        // Miembros desplegable + rol actual del usuario
        var membersExpanded by remember { mutableStateOf(false) }
        var isAdmin by remember { mutableStateOf(false) }

        // Agradecer / Donar puntos entre miembros
        var appreciateTarget by remember { mutableStateOf<MemberResponse?>(null) }
        var donateTarget by remember { mutableStateOf<MemberResponse?>(null) }
        val appreciateActionState by memberModel.appreciateActionState.collectAsState()
        val donateActionState by memberModel.donateActionState.collectAsState()

        LaunchedEffect(householdId) {
            householdModel.loadHousehold(householdId)
            memberModel.loadMembers(householdId)
        }

        // Backfillea eventos de Calendar pendientes (best-effort, nunca bloquea la UI)
        LaunchedEffect(householdState) {
            val hState = householdState
            if (hState is HouseholdUiState.Success) {
                calendarSync.reconcile(householdId, hState.household.name, hState.household.isPersonal)
            }
        }

        // Poll for notification unread count every 30 seconds
        var memberId by remember { mutableStateOf("") }
        LaunchedEffect(memberState) {
            if (memberState is MemberUiState.Success) {
                val members = (memberState as MemberUiState.Success).members
                memberId = members.firstOrNull()?.id ?: ""
                // Determinar si el usuario actual es admin (por su identidad)
                val localId = repo.getLocalId()
                val myMember = members.firstOrNull { it.userId == localId } ?: members.firstOrNull()
                isAdmin = myMember?.role == "admin"
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

        // ── Chat de mensajes ──
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
        var currentMemberId by remember { mutableStateOf("") }
        LaunchedEffect(householdId) {
            currentMemberId = repo.resolveCurrentMember(householdId)
        }
        val myMember = (memberState as? MemberUiState.Success)?.members?.firstOrNull { it.id == currentMemberId }

        // Cierra los diálogos y limpia el estado de acción al completarse con éxito.
        LaunchedEffect(appreciateActionState) {
            if (appreciateActionState is AppreciateActionState.Success) {
                appreciateTarget = null
            }
        }
        LaunchedEffect(donateActionState) {
            if (donateActionState is DonateActionState.Success) {
                donateTarget = null
            }
        }
        val messagesState by householdModel.messagesUiState.collectAsState()
        val newMessageText by householdModel.newMessageText.collectAsState()
        LaunchedEffect(householdId) {
            householdModel.loadMessages(householdId)
            while (true) {
                kotlinx.coroutines.delay(20_000L)
                householdModel.loadMessages(householdId)
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
                            logAnalyticsEvent("invite_code_shared")
                            shareText(
                                "Únete a mi espacio en Task Hub: $inviteCode. " +
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
                title = { Text("Eliminar espacio") },
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
                title = { Text("Salir del espacio") },
                text = {
                    Text(
                        "¿Desvincularte de '$householdName'? Dejarás de verlo en tu " +
                            "dispositivo. Si eres el último miembro, el espacio se eliminará."
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

        // ── Agradecer dialog ──
        appreciateTarget?.let { target ->
            val remaining = myMember?.let { repo.appreciationRemaining(it) } ?: 0
            TransferAmountDialog(
                title = "${s("appreciate_dialog_title")} ${target.displayName}",
                budgetLabel = s("appreciate_dialog_remaining_label"),
                budget = remaining,
                pointsSuffix = s("transfer_points_suffix"),
                amountLabel = s("transfer_amount_label"),
                confirmLabel = s("transfer_confirm"),
                cancelLabel = s("transfer_cancel"),
                errorText = (appreciateActionState as? AppreciateActionState.Error)?.let { s(it.messageKey) },
                isLoading = appreciateActionState is AppreciateActionState.Loading,
                emptyBudgetText = s("appreciate_no_budget"),
                onConfirm = { amount ->
                    val fromId = myMember?.id
                    if (fromId != null) {
                        memberModel.appreciateMember(householdId, fromId, target.id, amount)
                    }
                },
                onDismiss = {
                    appreciateTarget = null
                    memberModel.clearAppreciateAction()
                }
            )
        }

        // ── Donar dialog ──
        donateTarget?.let { target ->
            val balance = myMember?.totalPoints ?: 0
            TransferAmountDialog(
                title = "${s("donate_dialog_title")} ${target.displayName}",
                budgetLabel = s("donate_dialog_balance_label"),
                budget = balance,
                pointsSuffix = s("transfer_points_suffix"),
                amountLabel = s("transfer_amount_label"),
                confirmLabel = s("transfer_confirm"),
                cancelLabel = s("transfer_cancel"),
                errorText = (donateActionState as? DonateActionState.Error)?.let { s(it.messageKey) },
                isLoading = donateActionState is DonateActionState.Loading,
                onConfirm = { amount ->
                    val fromId = myMember?.id
                    if (fromId != null) {
                        memberModel.donatePoints(householdId, fromId, target.id, amount)
                    }
                },
                onDismiss = {
                    donateTarget = null
                    memberModel.clearDonateAction()
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
                            onDismiss = { showSettings = false },
                            onEditProfile = {
                                showSettings = false
                                navigator.push(EditProfileScreen())
                            }
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
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar espacio")
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
                                                text = "👥",
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

                                // Navigation: Ver Tareas + Calendario
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val mState = memberState
                                                val mid = if (mState is MemberUiState.Success) mState.members.firstOrNull()?.id else null
                                                navigator.push(TaskListScreen(householdId, mid))
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
                                                val mid = (mState as? MemberUiState.Success)?.members?.firstOrNull()?.id
                                                navigator.push(CalendarScreen(householdId, mid))
                                            },
                                            modifier = Modifier.weight(1f),
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
                                }

                                // Estadísticas + ranking + recompensas (pantalla combinada)
                                item {
                                    Button(
                                        onClick = {
                                            val mState = memberState
                                            val mid = (mState as? MemberUiState.Success)?.members?.firstOrNull()?.id ?: ""
                                            navigator.push(ExploreScreen(householdId, mid))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = "Estadísticas, ranking y recompensas",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Members header (desplegable)
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { membersExpanded = !membersExpanded },
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

                                        Text(
                                            text = if (membersExpanded) "▲" else "▼",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Members list (desplegable)
                                if (membersExpanded) {
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
                                                    MemberCard(
                                                        member = member,
                                                        isAdmin = isAdmin,
                                                        isSelf = myMember?.id == member.id,
                                                        canTransfer = myMember != null,
                                                        s = s,
                                                        onAppreciateClick = { appreciateTarget = member },
                                                        onDonateClick = { donateTarget = member },
                                                        onRoleChange = { newRole ->
                                                            memberModel.updateMemberRole(householdId, member.id, newRole)
                                                        },
                                                        onCreateTask = {
                                                            navigator.push(
                                                                CreateTaskScreen(
                                                                    householdId = householdId,
                                                                    createdBy = memberId,
                                                                    preselectedMemberId = member.id
                                                                )
                                                            )
                                                        },
                                                        onClick = {
                                                            member.userId?.let { uid ->
                                                                navigator.push(PublicProfileScreen(uid, member))
                                                            }
                                                        }
                                                    )
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
                                }

                                // Chat de mensajes
                                item {
                                    MessagesSection(
                                        s = s,
                                        messagesState = messagesState,
                                        newMessageText = newMessageText,
                                        onTextChange = householdModel::updateNewMessageText,
                                        onSend = { householdModel.sendMessage(householdId, currentMemberId) },
                                        onRefresh = { householdModel.loadMessages(householdId) }
                                    )
                                }

                                // Salir (desvincularse) del hogar
                                item {
                                    OutlinedButton(
                                        onClick = { showLeaveDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = "🚪 Salir del espacio",
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
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (hState.removable) {
                                        Button(
                                            onClick = {
                                                householdModel.removeGhostHousehold(householdId)
                                                navigator.replaceAll(HomeScreen())
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Quitar de mis espacios")
                                        }
                                    } else {
                                        Button(onClick = { householdModel.loadHousehold(householdId) }) {
                                            Text("Reintentar")
                                        }
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
private fun MemberCard(
    member: MemberResponse,
    isAdmin: Boolean,
    isSelf: Boolean,
    canTransfer: Boolean,
    s: (String) -> String,
    onRoleChange: (String) -> Unit,
    onCreateTask: () -> Unit,
    onClick: () -> Unit,
    onAppreciateClick: () -> Unit,
    onDonateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                UserAvatar(
                    avatarUrl = member.avatarUrl,
                    fallbackEmoji = if (member.role == "admin") "👑" else "🧒",
                    displayName = member.displayName,
                    contentDescription = member.displayName,
                    size = 48.dp,
                    backgroundColor = if (member.role == "admin") Coral100 else Teal100
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Name + role — clickable to view public profile
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = member.userId != null) { onClick() }
                ) {
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

            // Acciones: editar rol (solo admins) + crear tarea
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAdmin) {
                    var roleMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { roleMenuExpanded = true }) {
                            Text(if (member.role == "admin") "👑 Admin" else "🧒 Niño/a")
                        }
                        DropdownMenu(
                            expanded = roleMenuExpanded,
                            onDismissRequest = { roleMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("👑 Admin") },
                                onClick = {
                                    roleMenuExpanded = false
                                    if (member.role != "admin") onRoleChange("admin")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🧒 Niño/a") },
                                onClick = {
                                    roleMenuExpanded = false
                                    if (member.role != "child") onRoleChange("child")
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCreateTask,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Tarea")
                }
            }

            // Agradecer / Donar — ocultos sobre uno mismo
            if (!isSelf && canTransfer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAppreciateClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(s("appreciate_action"))
                    }
                    OutlinedButton(
                        onClick = onDonateClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(s("donate_action"))
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de importe reutilizado por "Agradecer" y "Donar": ambos piden una
 * cantidad de puntos con un tope visible ([budget], presupuesto semanal o
 * saldo según el caso) y muestran el error de la última acción, si lo hay.
 */
@Composable
private fun TransferAmountDialog(
    title: String,
    budgetLabel: String,
    budget: Int,
    pointsSuffix: String,
    amountLabel: String,
    confirmLabel: String,
    cancelLabel: String,
    errorText: String?,
    isLoading: Boolean,
    emptyBudgetText: String? = null,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toIntOrNull() ?: 0
    val isValid = amount in 1..budget

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "$budgetLabel: $budget $pointsSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (budget <= 0 && emptyBudgetText != null) {
                    Text(
                        text = emptyBudgetText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter(Char::isDigit) },
                        label = { Text(amountLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amount) },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        }
    )
}

@Composable
private fun MessagesSection(
    s: (String) -> String,
    messagesState: org.taskhub.ui.models.MessagesUiState,
    newMessageText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s("messages_title"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = s("messages_refresh"))
                }
            }

            Spacer(Modifier.height(8.dp))

            when (messagesState) {
                is org.taskhub.ui.models.MessagesUiState.Loading,
                org.taskhub.ui.models.MessagesUiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal600)
                    }
                }

                is org.taskhub.ui.models.MessagesUiState.Error -> {
                    Text(
                        text = "❌ ${messagesState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                is org.taskhub.ui.models.MessagesUiState.Success -> {
                    val messages = messagesState.messages
                    if (messages.isEmpty()) {
                        Text(
                            text = s("messages_empty"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(messages.size) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages) { message -> MessageBubble(message) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newMessageText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(s("messages_hint")) },
                    singleLine = true
                )
                IconButton(
                    onClick = onSend,
                    enabled = newMessageText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = s("messages_send"))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageResponse) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message.authorName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatMessageTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatMessageTime(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val month = local.monthNumber.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val min = local.minute.toString().padStart(2, '0')
    return "$day/$month $hour:$min"
}