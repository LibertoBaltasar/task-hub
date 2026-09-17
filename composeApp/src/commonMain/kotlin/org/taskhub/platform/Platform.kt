/**
 * Colección de puentes expect/actual sin agrupar en su propia interfaz:
 * compartir texto, caché del widget, Google Sign-In/Calendar, aleatoriedad
 * segura y flags de debug. Cada `actual` vive en el `*Main` de su plataforma.
 */
package org.taskhub.platform

import kotlin.concurrent.Volatile

/** Comparte texto mediante la hoja de compartir nativa de cada plataforma. */
expect fun shareText(text: String, title: String)

/** Guarda la preferencia de tema del widget en la caché específica de la plataforma. */
expect fun saveWidgetThemeToCache(theme: String)

/** Actualiza el widget con la lista actual de tareas pendientes (una por línea). */
expect fun updateWidgetPendingTasks(taskList: String)

/**
 * `true` solo en Android, la única plataforma con widget de pantalla de
 * inicio real — en el resto, [saveWidgetThemeToCache]/[updateWidgetPendingTasks]
 * son no-ops. Permite ocultar la sección "Tema del widget" de Ajustes donde
 * no tiene ningún efecto, en vez de dejar que el usuario configure algo que
 * no existe en su plataforma.
 */
expect val hasHomeScreenWidget: Boolean

/**
 * `true` solo donde [getGoogleCalendarAccessToken] puede devolver un token
 * real (Android/iOS) — en JVM/wasmJs está hardcodeado a `null` (soporte de
 * Calendar aún no implementado ahí). Permite ocultar la sección "Google
 * Calendar" de Ajustes donde vincular nunca puede tener éxito: sin esto, en
 * desktop el usuario completaba el flujo OAuth entero en el navegador (que sí
 * funciona) solo para que la app descartara el resultado al no poder pedir
 * el access token de Calendar.
 */
expect val hasCalendarSupport: Boolean

/** Lanza el flujo de Google Sign-In para vincular una cuenta de Google (integración con Calendar). */
expect fun launchGoogleSignIn()

/**
 * Obtiene (o refresca de forma transparente) un **access token** OAuth de
 * Google Calendar para la cuenta vinculada, pidiendo consentimiento con UI
 * nativa si hace falta. Devuelve null si no hay cuenta vinculada o si no se
 * pudo obtener el token. De vida corta (~1h): pedirlo bajo demanda, no
 * tratarlo como duradero.
 */
expect suspend fun getGoogleCalendarAccessToken(): String?

/**
 * Revoca el consentimiento OAuth (idToken + scope de Calendar) concedido a
 * la app para la cuenta de Google vinculada actualmente — usado al eliminar
 * la cuenta (panel v4, Experto 10 hallazgo #5), para que la app deje de
 * tener acceso al Calendar del usuario tras el borrado. Best-effort: no
 * lanza si falla (offline, sin cuenta vinculada, etc.).
 */
expect suspend fun revokeGoogleCalendarAccess()

/**
 * Índice aleatorio criptográficamente seguro en [0, bound). Usar en vez de
 * kotlin.random.Random para valores con implicaciones de seguridad (p.ej.
 * códigos de invitación a hogar), ya que Random no es un CSPRNG y sus
 * salidas son predecibles a partir de pocas observaciones.
 */
expect fun secureRandomInt(bound: Int): Int

/**
 * Flag de debug — true en builds debug, false en release.
 * Se usa para condicionar logs con println() y elementos de UI de debug
 * (contador rojo, etc.). Se fija desde MainActivity en onCreate() vía
 * BuildConfig.DEBUG.
 */
object DebugFlags {
    @Volatile
    var isEnabled: Boolean = false
}