/**
 * Credenciales del OAuth Client ID usado por el flujo de Google Sign-In en
 * JVM/desktop (`GoogleDesktopSignInHelper`, loopback + PKCE) — ver
 * `docs/macos-desktop-2026-09-13.md`.
 */
package org.taskhub.platform

/**
 * Rellenar en Google Cloud Console del proyecto `task-hub-62f98` → **APIs y
 * servicios → Credenciales → Crear credenciales → ID de cliente de OAuth** →
 * tipo **"Aplicación de escritorio"** (distinto del client ID Android/Web ya
 * configurados). La consola entrega un CLIENT_ID y un CLIENT_SECRET; para
 * apps instaladas (desktop/móvil) ese secret no se trata como secreto real —
 * ver https://developers.google.com/identity/protocols/oauth2/native-app —
 * pero hay que copiar ambos valores tal cual los da la consola.
 *
 * Con las constantes vacías (estado por defecto), [launchGoogleSignIn] falla
 * con un error claro al pulsar login (en vez de colgar el estado
 * `SigningIn`) — ver `GoogleDesktopSignInHelper.signIn`.
 */
object GoogleOAuthConfig {
    const val CLIENT_ID: String = ""
    const val CLIENT_SECRET: String = ""
}
