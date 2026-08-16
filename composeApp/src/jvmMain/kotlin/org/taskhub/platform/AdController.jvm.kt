package org.taskhub.platform

// JVM/Desktop: no hay integración de anuncios — no-op
actual fun createAdController(): AdController = NoOpAdController()
