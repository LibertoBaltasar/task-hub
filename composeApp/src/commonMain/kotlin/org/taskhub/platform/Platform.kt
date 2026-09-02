package org.taskhub.platform

/**
 * Platform-specific declarations shared across all targets.
 */

/** Share text via the native share sheet. */
expect fun shareText(text: String, title: String)

/** Save the widget theme preference to platform-specific widget cache. */
expect fun saveWidgetThemeToCache(theme: String)

/** Update the widget with the current pending tasks list (one per line). */
expect fun updateWidgetPendingTasks(taskList: String)

/** Launch the Google Sign-In flow to link a Google account for Calendar integration. */
expect fun launchGoogleSignIn()

/**
 * Obtains (or transparently refreshes) a Google Calendar OAuth **access token**
 * for the linked Google account, requesting user consent via native UI if
 * needed. Returns null if there's no linked account or the token could not be
 * obtained. Short-lived (~1h) — fetch on demand, don't treat it as durable.
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
 * Debug flag — true in debug builds, false in release.
 * Used to guard println() logs and debug UI elements (red counter, etc.).
 * Set from MainActivity in onCreate() via BuildConfig.DEBUG.
 */
object DebugFlags {
    @Volatile
    var isEnabled: Boolean = false
}