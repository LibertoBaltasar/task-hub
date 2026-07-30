package org.taskhub.auth

/**
 * Platform-specific Google Sign-In helper.
 * Android: uses GoogleSignInClient + Firebase Auth to get a Firebase ID token.
 * iOS/JVM: not implemented.
 */
expect class GoogleSignInHelper {
    /**
     * Launches the Google Sign-In flow and returns a Firebase ID token.
     * The token can be used with the Firestore REST API as a Bearer token.
     */
    suspend fun signInWithGoogle(): Result<String>
}