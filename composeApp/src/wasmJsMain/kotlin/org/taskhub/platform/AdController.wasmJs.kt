/**
 * `actual` web (wasmJs) de [createAdController]: no hay SDK de anuncios en el
 * build web, así que devuelve el [NoOpAdController] compartido.
 */
package org.taskhub.platform

// Web: no hay integración de anuncios — no-op
actual fun createAdController(): AdController = NoOpAdController()
