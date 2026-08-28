package org.taskhub.ui.components

import androidx.compose.runtime.Composable

/**
 * Indica si el sistema tiene activada la preferencia de accesibilidad
 * "reducir movimiento" (equivalente a `prefers-reduced-motion` en web).
 *
 * Cuando devuelve `true`, las animaciones decorativas (transiciones de
 * navegación, fades, etc.) deben omitirse o sustituirse por una alternativa
 * instantánea/estática.
 */
@Composable
expect fun shouldReduceMotion(): Boolean
