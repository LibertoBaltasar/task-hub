package org.taskhub.ui.components

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.taskhub.network.models.MemberResponse
import org.taskhub.platform.QrCodeImage
import org.taskhub.platform.logAnalyticsEvent
import org.taskhub.platform.shareText
import org.taskhub.ui.models.AppreciateActionState
import org.taskhub.ui.models.DonateActionState

/**
 * Diálogo con el código QR / texto de invitación al hogar, con botón para compartir.
 */
@Composable
fun QrShareDialog(
    inviteCode: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                QrCodeImage(
                    text = inviteCode,
                    modifier = Modifier.size(220.dp)
                )

                Spacer(Modifier.height(16.dp))

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
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

/**
 * Primer paso de la doble confirmación para eliminar un hogar.
 */
@Composable
fun DeleteHouseholdConfirmDialog1(
    householdName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar espacio") },
        text = {
            Text("¿Eliminar '$householdName'? Esta acción no se puede deshacer.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Segundo paso (definitivo) de la doble confirmación para eliminar un hogar.
 */
@Composable
fun DeleteHouseholdConfirmDialog2(
    householdName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Estás completamente seguro?") },
        text = {
            Text("Se perderán todas las tareas y miembros de '$householdName'.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Sí, eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Confirmación para desvincularse (salir) de un hogar.
 */
@Composable
fun LeaveHouseholdDialog(
    householdName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salir del espacio") },
        text = {
            Text(
                "¿Desvincularte de '$householdName'? Dejarás de verlo en tu " +
                    "dispositivo. Si eres el último miembro, el espacio se eliminará."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Salir", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Hoja de ajustes de la app, mostrada como diálogo a pantalla casi completa.
 */
@Composable
fun HouseholdSettingsDialog(
    onDismiss: () -> Unit,
    onEditProfile: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                    onDismiss = onDismiss,
                    onEditProfile = onEditProfile
                )
            )
        }
    }
}

/**
 * Diálogo para agradecer (transferir puntos del presupuesto semanal) a un miembro.
 */
@Composable
fun AppreciateDialog(
    target: MemberResponse,
    s: (String) -> String,
    remaining: Int,
    state: AppreciateActionState,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    TransferAmountDialog(
        title = "${s("appreciate_dialog_title")} ${target.displayName}",
        budgetLabel = s("appreciate_dialog_remaining_label"),
        budget = remaining,
        pointsSuffix = s("transfer_points_suffix"),
        amountLabel = s("transfer_amount_label"),
        confirmLabel = s("transfer_confirm"),
        cancelLabel = s("transfer_cancel"),
        errorText = (state as? AppreciateActionState.Error)?.let { s(it.messageKey) },
        isLoading = state is AppreciateActionState.Loading,
        emptyBudgetText = s("appreciate_no_budget"),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Diálogo para donar puntos del saldo propio a un miembro.
 */
@Composable
fun DonateDialog(
    target: MemberResponse,
    s: (String) -> String,
    balance: Int,
    state: DonateActionState,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    TransferAmountDialog(
        title = "${s("donate_dialog_title")} ${target.displayName}",
        budgetLabel = s("donate_dialog_balance_label"),
        budget = balance,
        pointsSuffix = s("transfer_points_suffix"),
        amountLabel = s("transfer_amount_label"),
        confirmLabel = s("transfer_confirm"),
        cancelLabel = s("transfer_cancel"),
        errorText = (state as? DonateActionState.Error)?.let { s(it.messageKey) },
        isLoading = state is DonateActionState.Loading,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
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
