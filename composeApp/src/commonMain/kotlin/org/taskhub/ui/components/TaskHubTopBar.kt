package org.taskhub.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Barra superior única y accesible de Task Hub.
 *
 * Sustituye a las antiguas barras manuales (`Surface(color = Teal600)` + `Row` + `Spacer`)
 * que fallaban contraste WCAG (blanco sobre #009884 = 3.61:1). Usa
 * [CenterAlignedTopAppBar] con fondo `surface` y texto `onSurface`, centra el título por
 * construcción (evita los hacks de `Spacer(72.dp)`) y estandariza el botón de volver con
 * el icono [Icons.Filled.ArrowBack] + `contentDescription`.
 *
 * @param title   Título de la pantalla. Sin emoji: el texto ya es suficiente.
 * @param onBack  Acción de volver; si es `null` no se muestra el botón de atrás.
 * @param actions Contenido del área de acciones a la derecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHubTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
