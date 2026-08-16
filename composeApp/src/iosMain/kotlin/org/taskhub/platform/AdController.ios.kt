package org.taskhub.platform

// iOS: no hay integración de anuncios — no-op
actual fun createAdController(): AdController = NoOpAdController()
