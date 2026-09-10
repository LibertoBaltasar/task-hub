package org.taskhub.platform

/**
 * Implementación iOS del `expect` [createAdController] (`platform/AdController.kt`).
 *
 * No hay integración de AdMob (ni de ningún otro SDK de anuncios) en el
 * target iOS todavía — devuelve [NoOpAdController], igual que en JVM/Desktop
 * (ver `AdController.jvm.kt`).
 */
actual fun createAdController(): AdController = NoOpAdController()
