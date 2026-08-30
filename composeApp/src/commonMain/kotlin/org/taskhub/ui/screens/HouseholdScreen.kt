package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.MemberResponse
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import org.taskhub.ui.components.AppreciateDialog
import org.taskhub.ui.components.DeleteHouseholdConfirmDialog1
import org.taskhub.ui.components.DeleteHouseholdConfirmDialog2
import org.taskhub.ui.components.DonateDialog
import org.taskhub.ui.components.HouseholdChatSection
import org.taskhub.ui.components.HouseholdSettingsDialog
import org.taskhub.ui.components.LeaveHouseholdDialog
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.components.QrShareDialog
import org.taskhub.ui.components.ShimmerList
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.components.householdMemberList
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.AppreciateActionState
import org.taskhub.ui.models.CalendarSyncManager
import org.taskhub.ui.models.DonateActionState
import org.taskhub.ui.models.HouseholdScreenModel
import org.taskhub.ui.models.HouseholdUiState
import org.taskhub.ui.models.MemberScreenModel
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.models.NotificationScreenModel
import org.taskhub.ui.theme.*

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

        // Miembros desplegable
        var membersExpanded by remember { mutableStateOf(false) }

        // Agradecer / Donar puntos entre miembros
        var appreciateTarget by remember { mutableStateOf<MemberResponse?>(null) }
        var donateTarget by remember { mutableStateOf<MemberResponse?>(null) }
        val appreciateActionState by memberModel.appreciateActionState.collectAsState()
        val donateActionState by memberModel.donateActionState.collectAsState()

        LaunchedEffect(householdId) {
            householdModel.loadHousehold(householdId)
            memberModel.loadMembers(householdId)
        }

        // Backfillea eventos de Calendar pendientes (best-effort, nunca bloquea la UI).
        // Clave = solo el id (no el objeto household completo): así no se relanza
        // cada vez que cambia otro campo del hogar (p.ej. al renombrarlo), solo al
        // entrar en Success por primera vez para este id.
        val successHouseholdId = (householdState as? HouseholdUiState.Success)?.household?.id
        LaunchedEffect(successHouseholdId) {
            val hState = householdState
            if (hState is HouseholdUiState.Success) {
                calendarSync.reconcile(householdId, hState.household.name, hState.household.isPersonal)
            }
        }

        // ── Identidad del usuario actual en este hogar ──
        // ÚNICA fuente de verdad para "qué miembro soy yo": antes, la navegación
        // a Tareas/Calendario/Explorar y el createdBy al crear tareas usaban
        // members.firstOrNull()?.id (el PRIMER miembro de la lista, no el mío),
        // así que en un hogar compartido cualquiera que no fuera el primer
        // miembro veía datos ajenos. resolveCurrentMember() sí resuelve al
        // usuario autenticado. isAdmin también se deriva de aquí (antes tenía
        // su propio LaunchedEffect con el mismo patrón buggy de "primer
        // miembro de la lista" como fallback, pudiendo mostrar/ocultar
        // controles de admin para la persona equivocada).
        var currentMemberId by remember { mutableStateOf("") }
        LaunchedEffect(householdId) {
            currentMemberId = repo.resolveCurrentMember(householdId)
        }
        val myMember = (memberState as? MemberUiState.Success)?.members?.firstOrNull { it.id == currentMemberId }
        val isAdmin = myMember?.role == "admin"

        // ── Chat de mensajes ──
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        // Poll for notification unread count every 30 seconds
        LaunchedEffect(householdId, currentMemberId) {
            if (currentMemberId.isNotEmpty()) {
                notificationModel.refreshUnreadCount(householdId, currentMemberId)
                while (true) {
                    kotlinx.coroutines.delay(30_000L)
                    notificationModel.refreshUnreadCount(householdId, currentMemberId)
                }
            }
        }

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

        val householdName = when (val hState = householdState) {
            is HouseholdUiState.Success -> hState.household.name
            else -> ""
        }

        val inviteCode = when (val hState = householdState) {
            is HouseholdUiState.Success -> hState.household.inviteCode
            else -> ""
        }

        // ── Dialogs ──
        if (showQrDialog && inviteCode.isNotEmpty()) {
            QrShareDialog(inviteCode = inviteCode, s = s, onDismiss = { showQrDialog = false })
        }

        if (showConfirmDialog1) {
            DeleteHouseholdConfirmDialog1(
                householdName = householdName,
                s = s,
                onDismiss = { showConfirmDialog1 = false },
                onConfirm = {
                    showConfirmDialog1 = false
                    showConfirmDialog2 = true
                }
            )
        }

        if (showConfirmDialog2) {
            DeleteHouseholdConfirmDialog2(
                householdName = householdName,
                s = s,
                onDismiss = { showConfirmDialog2 = false },
                onConfirm = {
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
                }
            )
        }

        if (showLeaveDialog) {
            LeaveHouseholdDialog(
                householdName = householdName,
                s = s,
                onDismiss = { showLeaveDialog = false },
                onConfirm = {
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
                }
            )
        }

        appreciateTarget?.let { target ->
            val remaining = myMember?.let { repo.appreciationRemaining(it) } ?: 0
            AppreciateDialog(
                target = target,
                s = s,
                remaining = remaining,
                state = appreciateActionState,
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

        donateTarget?.let { target ->
            val balance = myMember?.totalPoints ?: 0
            DonateDialog(
                target = target,
                s = s,
                balance = balance,
                state = donateActionState,
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

        if (showSettings) {
            HouseholdSettingsDialog(
                onDismiss = { showSettings = false },
                onEditProfile = {
                    showSettings = false
                    navigator.push(EditProfileScreen())
                }
            )
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
                                    val mid = currentMemberId
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                ShimmerList(count = 4, itemHeight = 96.dp)
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
                                                navigator.push(TaskListScreen(householdId, currentMemberId.ifEmpty { null }))
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
                                                navigator.push(CalendarScreen(householdId, currentMemberId.ifEmpty { null }))
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
                                            navigator.push(ExploreScreen(householdId, currentMemberId))
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

                                householdMemberList(
                                    membersExpanded = membersExpanded,
                                    onToggleExpanded = { membersExpanded = !membersExpanded },
                                    memberState = memberState,
                                    isAdmin = isAdmin,
                                    myMember = myMember,
                                    s = s,
                                    onAppreciateClick = { member -> appreciateTarget = member },
                                    onDonateClick = { member -> donateTarget = member },
                                    onRoleChange = { member, newRole ->
                                        memberModel.updateMemberRole(householdId, member.id, newRole)
                                    },
                                    onCreateTask = { member ->
                                        navigator.push(
                                            CreateTaskScreen(
                                                householdId = householdId,
                                                createdBy = currentMemberId,
                                                preselectedMemberId = member.id
                                            )
                                        )
                                    },
                                    onMemberClick = { member ->
                                        member.userId?.let { uid ->
                                            navigator.push(PublicProfileScreen(uid, member))
                                        }
                                    },
                                    onInviteClick = { showQrDialog = true }
                                )

                                // Chat de mensajes
                                item {
                                    HouseholdChatSection(
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
