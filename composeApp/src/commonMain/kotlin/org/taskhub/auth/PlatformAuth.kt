package org.taskhub.auth

/**
 * Platform-specific factory for GoogleSignInHelper.
 * On Android, returns the pre-initialized instance from the holder.
 * On other platforms, creates a new instance.
 */
expect fun createGoogleSignInHelper(): GoogleSignInHelper