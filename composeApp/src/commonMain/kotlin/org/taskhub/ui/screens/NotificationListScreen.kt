package org.taskhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import org.taskhub.network.models.NotificationResponse
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.NotificationScreenModel
import org.taskhub.ui.models.NotificationUiState
import org.taskhub.ui.theme.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class NotificationListScreen(
    val householdId: String,
    val memberId: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<NotificationScreenModel>()
        val state by model.uiState.collectAsState()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        LaunchedEffect(householdId, memberId) {
            model.loadNotifications(householdId, memberId)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = s("notifications_title"),
                    onBack = { navigator.pop() }
                )

                // Content
                when (val st = state) {
                    is NotificationUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    is NotificationUiState.Success -> {
                        if (st.notifications.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "🔕",
                                        style = MaterialTheme.typography.displayMedium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = s("notifications_empty"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text(
                                        text = s("notifications_unread_count").replace("%d", st.unreadCount.toString()),
                                        style = MaterialTheme.typography.labelMedium,
                                        // Coral500 fijo sobre `background` fallaba AA (2.83-3.07:1) en los 3
                                        // temas claros; semanticColors.info ya está auditado y es el mismo
                                        // tono que usa el punto indicador de "no leída" más abajo.
                                        color = if (st.unreadCount > 0) MaterialTheme.semanticColors.info else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                items(st.notifications, key = { it.id }) { notification ->
                                    NotificationCard(
                                        notification = notification,
                                        onMarkRead = {
                                            model.markAsRead(householdId, notification.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is NotificationUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = s("error_icon_content_desc"),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = st.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    model.loadNotifications(householdId, memberId)
                                }) {
                                    Text(s("tasks_retry"))
                                }
                            }
                        }
                    }

                    is NotificationUiState.Idle -> {}
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationResponse,
    onMarkRead: () -> Unit
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
    // primaryContainer/onPrimaryContainer (en vez del antiguo par fijo
    // Teal50/Teal800/900): sigue el tema activo (Naturaleza/Minimal, no solo
    // Default) y ya es un par accesible auditado en las 6 combinaciones
    // tema/modo, mismo criterio aplicado a otras 6+ cards de la app.
    val titleColor = if (!notification.read) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (!notification.read) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.read)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!notification.read) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Unread indicator
            if (!notification.read) {
                Surface(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(y = 6.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.semanticColors.info
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (!notification.read) FontWeight.Bold else FontWeight.Normal,
                        color = titleColor
                    )

                    // Time ago text
                    Text(
                        text = formatTimeAgo(notification.createdAt, appSettings.currentLanguage),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor
                )

                // Mark as read button for unread notifications
                if (!notification.read) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onMarkRead,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = s("notifications_mark_read"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple "time ago" formatter.
 */
private fun formatTimeAgo(epochMs: Long, lang: String): String {
    if (epochMs == 0L) return ""
    val now = Clock.System.now().toEpochMilliseconds()
    val diffMs = now - epochMs
    val diffMin = diffMs / (60 * 1000)
    val diffHours = diffMin / 60
    val diffDays = diffHours / 24

    return when {
        diffMin < 1 -> AppStrings.get("time_ago_now", lang)
        diffMin < 60 -> AppStrings.get("time_ago_minutes", lang).replace("%d", diffMin.toString())
        diffHours < 24 -> AppStrings.get("time_ago_hours", lang).replace("%d", diffHours.toString())
        diffDays < 7 -> AppStrings.get("time_ago_days", lang).replace("%d", diffDays.toString())
        else -> {
            val instant = Instant.fromEpochMilliseconds(epochMs)
            val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${local.dayOfMonth}/${local.monthNumber}/${local.year}"
        }
    }
}