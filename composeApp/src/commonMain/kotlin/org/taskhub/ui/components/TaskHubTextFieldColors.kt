// Colores compartidos de OutlinedTextField, usados por todos los
// formularios de la app (crear/editar tarea, perfil, etc.) para que el
// campo siga el color primario del tema activo en vez de un literal fijo.
package org.taskhub.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * Colores estándar de `OutlinedTextField` de la app (borde/label/cursor con
 * `colorScheme.primary` al enfocar). Antes cada pantalla repetía el mismo
 * bloque `OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal600, ...)`
 * por separado (5 sitios) con el literal `Teal600` en vez del token de tema
 * — con esto, además de deduplicar, el color sigue al tema activo
 * (Naturaleza/Minimal), no solo al Default.
 */
/** [TextFieldColors] estándar para `OutlinedTextField` en toda la app; ver KDoc de cabecera. */
@Composable
fun taskHubTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary
)
