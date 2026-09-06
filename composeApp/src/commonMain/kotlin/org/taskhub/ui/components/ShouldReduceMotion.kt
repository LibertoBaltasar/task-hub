// Punto de entrada `expect` para la señal de accesibilidad "reducir
// movimiento". Cada plataforma (Android/iOS/Desktop) aporta su `actual`
// (lee el ajuste del sistema operativo correspondiente); lo consumen
// composables con animaciones decorativas como ShimmerPlaceholder o las
// transiciones de secciones desplegables (ExpandableSectionHeader + AnimatedVisibility).
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
