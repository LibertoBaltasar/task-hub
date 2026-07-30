package org.taskhub.auth

actual fun createGoogleSignInHelper(): GoogleSignInHelper {
    return GoogleSignInHelperHolder.instance ?: GoogleSignInHelper()
}

/**
 * Holder for the pre-initialized GoogleSignInHelper instance.
 * Set by [org.taskhub.MainActivity] during onCreate.
 */
object GoogleSignInHelperHolder {
    @Volatile
    var instance: GoogleSignInHelper? = null
}