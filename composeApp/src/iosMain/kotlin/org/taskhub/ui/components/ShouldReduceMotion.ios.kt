package org.taskhub.ui.components

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun shouldReduceMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
