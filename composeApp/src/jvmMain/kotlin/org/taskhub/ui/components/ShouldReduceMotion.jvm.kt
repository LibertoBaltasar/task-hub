package org.taskhub.ui.components

import androidx.compose.runtime.Composable

// Desktop: no existe una señal de sistema equivalente a prefers-reduced-motion.
@Composable
actual fun shouldReduceMotion(): Boolean = false
