package org.taskhub.ui.components

import androidx.compose.runtime.Composable
import kotlinx.browser.window

/**
 * Web: lee `prefers-reduced-motion` vía `window.matchMedia`. Se consulta una
 * única vez por composición (no hay listener a cambios en caliente del SO,
 * igual de aceptable que el resto de plataformas: ver KDoc del `expect`).
 */
@Composable
actual fun shouldReduceMotion(): Boolean =
    try {
        window.matchMedia("(prefers-reduced-motion: reduce)").matches
    } catch (_: Throwable) {
        false
    }
