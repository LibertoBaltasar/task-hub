package org.taskhub.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * `leadingIcon` de check para un `FilterChip` seleccionado, o null si no lo
 * está. El estado de M3 de un `FilterChip` seleccionado depende mayormente
 * del relleno de color — este icono aporta una segunda señal que no depende
 * solo del color (WCAG 1.4.1), en los FilterChip de frecuencia/días de
 * recurrencia/penalización de `CreateTaskScreen`/`EditTaskScreen` (panel v4,
 * Accesibilidad #2).
 */
fun filterChipCheckIcon(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) {
        {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    } else {
        null
    }
