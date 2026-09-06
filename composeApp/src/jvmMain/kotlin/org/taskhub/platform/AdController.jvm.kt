/**
 * `actual` JVM (desktop) de [createAdController]: no hay SDK de anuncios en
 * desktop, así que devuelve el [NoOpAdController] compartido.
 */
package org.taskhub.platform

// JVM/Desktop: no hay integración de anuncios — no-op
actual fun createAdController(): AdController = NoOpAdController()
