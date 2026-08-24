package org.taskhub

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Obtains real OAuth **access tokens** (not the Firebase idToken) for the
 * Google Calendar scope, on demand.
 *
 * [GoogleAuthUtil.getToken] talks to the on-device Google Play services account
 * manager: it returns a cached access token when one is valid, transparently
 * refreshes an expired one, and throws [UserRecoverableAuthException] the first
 * time the calendar scope needs explicit user consent (its `intent` must be
 * launched so the user can grant it). It must run off the main thread.
 *
 * ## Setup
 * Call [register] from [MainActivity.onCreate], alongside [GoogleSignInHelper.register].
 */
object GoogleCalendarAuthHelper {

    private const val CALENDAR_SCOPE = "oauth2:https://www.googleapis.com/auth/calendar"

    private var consentLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingConsent: ((Boolean) -> Unit)? = null

    fun register(activity: ComponentActivity) {
        consentLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val granted = result.resultCode == android.app.Activity.RESULT_OK
            pendingConsent?.invoke(granted)
            pendingConsent = null
        }
    }

    /**
     * Returns a fresh Calendar-scoped access token for the Google account
     * currently signed in via [GoogleSignInHelper], or null if there's no
     * signed-in account or the token could not be obtained (denied consent,
     * offline, revoked access, etc).
     */
    suspend fun getAccessToken(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return null
        return try {
            fetchToken(context, account)
        } catch (e: UserRecoverableAuthException) {
            val consentIntent = e.intent ?: return null
            val granted = awaitConsent(consentIntent)
            if (!granted) return null
            try {
                fetchToken(context, account)
            } catch (_: GoogleAuthException) {
                null
            } catch (_: IOException) {
                null
            }
        } catch (_: GoogleAuthException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun fetchToken(context: Context, account: Account): String =
        withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(context, account, CALENDAR_SCOPE)
        }

    /** Launches the consent [intent] from [UserRecoverableAuthException] and awaits the result. */
    private suspend fun awaitConsent(intent: Intent): Boolean = suspendCancellableCoroutine { cont ->
        val launcher = consentLauncher
        if (launcher == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        pendingConsent = { granted -> cont.resume(granted) }
        launcher.launch(intent)
    }
}
