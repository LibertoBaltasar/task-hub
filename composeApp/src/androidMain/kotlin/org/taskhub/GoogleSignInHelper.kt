package org.taskhub

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import org.taskhub.platform.GoogleSignInResultHolder

/**
 * Google Sign-In helper for linking a Google account and obtaining
 * a Calendar OAuth scope token.
 *
 * ## Setup
 *
 * 1. Call [register] from [MainActivity.onCreate] to bind the launcher.
 * 2. Call [launch] to start the sign-in flow (triggered from UI).
 * 3. The result is delivered through [GoogleSignInResultHolder].
 *
 * ### Firebase Web Client ID
 *
 * Obtain from **Firebase Console → Authentication → Sign-in method → Google →
 * Web SDK configuration** — use the **same** server client ID, not the Android one.
 */
object GoogleSignInHelper {

    /**
     * The Web SDK client ID from Firebase Console.
     * Change this to match your Firebase project's web OAuth client ID.
     */
    const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    private var signInLauncher: ActivityResultLauncher<Intent>? = null

    /**
     * Must be called from [MainActivity.onCreate] BEFORE any UI triggers [launch].
     * Registering requires the Activity because [ActivityResultLauncher] is tied
     * to the activity lifecycle.
     */
    fun register(activity: ComponentActivity) {
        signInLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleSignInResult(result.resultCode, result.data)
        }
    }

    /**
     * Start the Google Sign-In flow. Must be called after [register].
     */
    fun launch(context: Context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()

        val client = GoogleSignIn.getClient(context, gso)

        // Sign out first so the user sees the account picker every time
        client.signOut().addOnCompleteListener {
            val signInIntent = client.signInIntent
            signInLauncher?.launch(signInIntent)
        }
    }

    private fun handleSignInResult(resultCode: Int, data: Intent?) {
        if (data == null) {
            GoogleSignInResultHolder.setResult(null)
            return
        }

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account != null) {
                // idToken can be used as Bearer token for Calendar API when
                // the calendar scope was requested in the sign-in options
                val idToken = account.idToken
                GoogleSignInResultHolder.setResult(idToken)
            } else {
                GoogleSignInResultHolder.setResult(null)
            }
        } catch (e: ApiException) {
            // User cancelled or error
            GoogleSignInResultHolder.setResult(null)
        }
    }
}