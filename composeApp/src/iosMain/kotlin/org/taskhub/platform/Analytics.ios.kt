package org.taskhub.platform

/**
 * No-op en iOS: Analytics aún no está integrado en el target iOS.
 * Se implementará cuando iOS sea publicable (requiere macOS + cuenta Apple).
 */
actual fun logAnalyticsEvent(eventName: String, params: Map<String, String>) {
    // TODO: integrar Firebase Analytics para iOS
}
