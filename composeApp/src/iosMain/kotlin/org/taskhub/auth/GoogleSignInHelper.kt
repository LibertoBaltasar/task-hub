package org.taskhub.auth

actual class GoogleSignInHelper {
    actual suspend fun signInWithGoogle(): Result<String> =
        Result.failure(Exception("Google Sign-In no está disponible en iOS"))
}