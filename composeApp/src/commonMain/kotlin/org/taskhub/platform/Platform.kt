package org.taskhub.platform

/**
 * Platform-specific declarations shared across all targets.
 */

/** Share text via the native share sheet. */
expect fun shareText(text: String, title: String)

/** Save the widget theme preference to platform-specific widget cache. */
expect fun saveWidgetThemeToCache(theme: String)