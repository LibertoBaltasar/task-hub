package org.taskhub.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Diálogo de confirmación destructiva genérico. Unifica los 5 diálogos casi
 * idénticos que existían por separado (los 2 pasos de borrar un hogar, salir
 * de un hogar, borrar una recompensa, borrar una tarea), que además
 * reutilizaban las claves i18n `household_delete_btn`/`household_cancel`
 * desde dominios no relacionados (recompensas, tareas) — ahora usan las
 * claves neutrales `common_delete`/`common_cancel`.
 *
 * @param confirmLabel texto del botón de confirmar. Por defecto `common_delete`
 *   ("Eliminar"), pero algunos flujos usan un verbo más específico del propio
 *   dominio (p. ej. "Sí, eliminar" en el 2º paso de borrar hogar, "Salir" al
 *   abandonar un hogar).
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    text: String,
    s: (String) -> String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = s("common_delete")
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s("common_cancel"))
            }
        }
    )
}
