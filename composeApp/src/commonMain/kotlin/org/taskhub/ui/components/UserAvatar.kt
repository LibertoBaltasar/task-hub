package org.taskhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.taskhub.ui.theme.Teal100

/**
 * Avatar reutilizable con orden de prioridad: foto ([avatarUrl]) > emoji
 * ([fallbackEmoji]) > inicial de [displayName] > icono [Icons.Default.Person].
 *
 * Centraliza el render de avatar usado en Ranking, HouseholdScreen,
 * TaskDetailScreen, EditProfileScreen, ProfileScreen y PublicProfileScreen,
 * de modo que añadir foto de perfil solo requirió tocar este componente.
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    fallbackEmoji: String,
    displayName: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    backgroundColor: Color = Teal100
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            !avatarUrl.isNullOrBlank() -> AsyncImage(
                model = avatarUrl,
                contentDescription = contentDescription,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            fallbackEmoji.isNotBlank() -> Text(
                text = fallbackEmoji,
                style = MaterialTheme.typography.titleMedium
            )

            displayName.isNotBlank() -> Text(
                text = displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current
            )

            else -> Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contentDescription
            )
        }
    }
}
