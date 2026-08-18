package org.taskhub.platform

/**
 * No-op en JVM (desktop): la distribución desktop es secundaria y no
 * está pensada para analytics en esta fase.
 */
actual fun logAnalyticsEvent(eventName: String, params: Map<String, String>) {
    // No-op
}
