package org.taskhub.platform

/**
 * Implementación iOS del `expect` [logAnalyticsEvent] (`platform/Analytics.kt`).
 *
 * No-op en iOS: Analytics aún no está integrado en el target iOS (no se
 * enlaza el SDK de Firebase Analytics para iOS). Se implementará cuando iOS
 * sea publicable (requiere macOS + cuenta Apple).
 */
actual fun logAnalyticsEvent(eventName: String, params: Map<String, String>) {
    // TODO: integrar Firebase Analytics para iOS
}
