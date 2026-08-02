package org.taskhub.platform

/**
 * Platform-specific declarations shared across all targets.
 */

/** Share text via the native share sheet. */
expect fun shareText(text: String, title: String)

/** Save the widget theme preference to platform-specific widget cache. */
expect fun saveWidgetThemeToCache(theme: String)

/** Update the widget with the current pending tasks list (one per line). */
expect fun updateWidgetPendingTasks(taskList: String)

/**
 * Debug flag — true in debug builds, false in release.
 * Used to guard println() logs and debug UI elements (red counter, etc.).
 * Set from MainActivity in onCreate() via BuildConfig.DEBUG.
 */
object DebugFlags {
    @Volatile
    var isEnabled: Boolean = false
}