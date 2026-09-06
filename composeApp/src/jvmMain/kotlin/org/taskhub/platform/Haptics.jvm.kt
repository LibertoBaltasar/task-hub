/**
 * `actual` JVM (desktop) de [vibrate]: los equipos de escritorio no tienen
 * motor de vibración, así que es no-op.
 */
package org.taskhub.platform

// JVM/Desktop: no hay motor de vibración — no-op
actual fun vibrate(kind: HapticKind) {
}
