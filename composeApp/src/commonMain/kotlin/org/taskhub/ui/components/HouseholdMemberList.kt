package org.taskhub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.taskhub.network.models.MemberResponse
import org.taskhub.ui.models.MemberUiState
import org.taskhub.ui.theme.Coral100
import org.taskhub.ui.theme.Teal100
import org.taskhub.ui.theme.Teal600

/**
 * Sección desplegable de miembros del hogar: cabecera con contador + lista
 * de tarjetas de miembro. Se añade directamente como items de un [LazyColumn]
 * anfitrión para conservar el scroll compartido con el resto de la pantalla.
 */
fun LazyListScope.householdMemberList(
    membersExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    memberState: MemberUiState,
    isAdmin: Boolean,
    myMember: MemberResponse?,
    s: (String) -> String,
    onAppreciateClick: (MemberResponse) -> Unit,
    onDonateClick: (MemberResponse) -> Unit,
    onRoleChange: (MemberResponse, String) -> Unit,
    onCreateTask: (MemberResponse) -> Unit,
    onMemberClick: (MemberResponse) -> Unit,
    onInviteClick: () -> Unit
) {
    // Members header (desplegable)
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s("household_members"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            when (memberState) {
                is MemberUiState.Success -> {
                    Text(
                        text = "${memberState.members.size}",
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
    if (!membersExpanded) return

    when (memberState) {
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
            if (memberState.members.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👥", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                s("household_member_list_empty_title"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                s("household_member_list_empty_desc"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onInviteClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(s("household_member_list_invite_cta"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                items(memberState.members, key = { it.id }) { member ->
                    MemberCard(
                        member = member,
                        isAdmin = isAdmin,
                        isSelf = myMember?.id == member.id,
                        canTransfer = myMember != null,
                        s = s,
                        onAppreciateClick = { onAppreciateClick(member) },
                        onDonateClick = { onDonateClick(member) },
                        onRoleChange = { newRole -> onRoleChange(member, newRole) },
                        onCreateTask = { onCreateTask(member) },
                        onClick = { onMemberClick(member) }
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
                        text = s("household_member_list_error").replace("%s", memberState.message),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        is MemberUiState.Idle -> {}
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
                    fallbackEmoji = if (member.role == "admin") "👑" else "👤",
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
                        text = if (member.role == "admin") s("member_role_admin_full") else s("member_role_child_full"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Points badge
                PointsBadge(
                    text = "${member.totalPoints} ${s("transfer_points_suffix")}",
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
                            Text(if (member.role == "admin") s("member_role_admin_short") else s("member_role_child_short"))
                        }
                        DropdownMenu(
                            expanded = roleMenuExpanded,
                            onDismissRequest = { roleMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(s("member_role_admin_short")) },
                                onClick = {
                                    roleMenuExpanded = false
                                    if (member.role != "admin") onRoleChange("admin")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(s("member_role_child_short")) },
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
                    Text(s("member_create_task_short"))
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
