package org.taskhub.platform

/**
 * Platform-specific declarations shared across all targets.
 */

/** Share text via the native share sheet. */
expect fun shareText(text: String, title: String)