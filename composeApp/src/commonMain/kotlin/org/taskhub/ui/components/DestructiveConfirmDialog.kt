package org.taskhub.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Diálogo de confirmación genérico para acciones de alto impacto. Unifica los
 * diálogos casi idénticos que existían por separado (los 2 pasos de borrar un
 * hogar, salir de un hogar, borrar una recompensa, borrar una tarea, cambiar
 * el rol de un miembro), que además reutilizaban las claves i18n
 * `household_delete_btn`/`household_cancel` desde dominios no relacionados
 * (recompensas, tareas) — ahora usan las claves neutrales
 * `common_delete`/`common_cancel`.
 *
 * @param confirmLabel texto del botón de confirmar. Por defecto `common_delete`
 *   ("Eliminar"), pero algunos flujos usan un verbo más específico del propio
 *   dominio (p. ej. "Sí, eliminar" en el 2º paso de borrar hogar, "Salir" al
 *   abandonar un hogar).
 * @param destructive si es true (por defecto), el botón de confirmar se pinta
 *   en `colorScheme.error` — para acciones irreversibles o de alto riesgo
 *   (borrar, salir). Pásalo a `false` para acciones de alto impacto pero NO
 *   destructivas (p. ej. cambiar el rol de un miembro), donde el color de
 *   error induciría a error sobre la gravedad real de la acción (panel v4,
 *   UI/Componentes #2).
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    text: String,
    s: (String) -> String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = s("common_delete"),
    destructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s("common_cancel"))
            }
        }
    )
}
