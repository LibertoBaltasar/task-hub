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
 * client_type 3 = Web). Usado en Android y web (GIS); en iOS se usa en su
 * lugar [GOOGLE_IOS_CLIENT_ID] (el token pedido con audience = client iOS
 * también es aceptado por `signInWithIdp`, es el flujo estándar de Firebase
 * Auth para iOS).
 */
const val GOOGLE_WEB_CLIENT_ID = "278503294422-g54vhkk502dju3fp8ra8hi0h7037b0m0.apps.googleusercontent.com"

/**
 * Client ID de tipo "iOS" (Firebase Console → app iOS `org.taskhub`,
 * appId `1:278503294422:ios:8e84f3b201fb3bdcc79077`), generado
 * automáticamente por Firebase al registrar la app iOS.
 */
const val GOOGLE_IOS_CLIENT_ID = "278503294422-3f8u3f61l3cii42tfn6if5qiq7u7sbem.apps.googleusercontent.com"

/**
 * Reversed client ID del [GOOGLE_IOS_CLIENT_ID]: se usa como URL scheme de
 * callback del flujo OAuth en iOS (ver `Info.plist` y `ContentView.swift`
 * `onOpenURL`).
 */
const val GOOGLE_IOS_REVERSED_CLIENT_ID = "com.googleusercontent.apps.278503294422-3f8u3f61l3cii42tfn6if5qiq7u7sbem"
