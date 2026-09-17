/**
 * Constante compartida del flujo de Google Sign-In (idToken): el mismo Web
 * Client ID sirve tanto para `requestIdToken` en Android
 * ([org.taskhub.GoogleSignInHelper]) como para Google Identity Services (GIS)
 * en la web (`Platform.wasmJs.kt`) — Firebase exige usar el client ID de tipo
 * "Web" (no el de Android) para que el idToken resultante sea válido en
 * `signInWithIdp` (Identity Toolkit), independientemente de la plataforma que
 * lo solicite.
 */
package org.taskhub.platform

/**
 * Web Client ID de Firebase Console (Authentication → Sign-in method →
 * Google → Web SDK configuration). De google-services.json (oauth_client con
 * client_type 3 = Web).
 */
const val GOOGLE_WEB_CLIENT_ID = "278503294422-g54vhkk502dju3fp8ra8hi0h7037b0m0.apps.googleusercontent.com"
