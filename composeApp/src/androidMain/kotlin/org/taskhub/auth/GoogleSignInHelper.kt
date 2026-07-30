package org.taskhub.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation of Google Sign-In using:
 *  - GoogleSignInClient (play-services-auth)
 *  - Firebase Auth to validate the Google credential and emit a Firebase ID token.
 *
 * The Activity is set via [initialize] before any sign-in calls.
 */
actual class GoogleSignInHelper {

    private var firebaseAuth: FirebaseAuth? = null
    private var googleSignInClient: GoogleSignInClient? = null
    private var signInLauncher: ActivityResultLauncher<Intent>? = null
    private var signInContinuation: CancellableContinuation<Intent?>? = null
    private var activity: ComponentActivity? = null

    /**
     * Must be called from MainActivity.onCreate before any sign-in.
     */
    fun initialize(activity: ComponentActivity) {
        if (this.activity != null) return // Already initialized
        this.activity = activity

        // Initialize Firebase manually (no google-services.json needed)
        if (FirebaseApp.getApps(activity.applicationContext).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(FIREBASE_API_KEY)
                .setProjectId(FIREBASE_PROJECT_ID)
                .setApplicationId(FIREBASE_APP_ID)
                .build()
            FirebaseApp.initializeApp(activity.applicationContext, options)
        }
        firebaseAuth = FirebaseAuth.getInstance()

        // Register the Activity Result launcher for sign-in
        signInLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = if (result.resultCode == Activity.RESULT_OK) result.data else null
            signInContinuation?.resume(data)
            signInContinuation = null
        }
    }

    actual suspend fun signInWithGoogle(): Result<String> {
        val act = activity ?: return Result.failure(
            Exception("GoogleSignInHelper no inicializado. Llama a initialize() en MainActivity.")
        )

        return try {
            // Configure Google Sign-In
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(FIREBASE_API_KEY)
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(act, gso)

            // Revoke previous access to force account picker
            googleSignInClient?.signOut()
            googleSignInClient?.revokeAccess()

            // Launch the sign-in UI and wait for the result
            val data = suspendCancellableCoroutine<Intent?> { continuation ->
                signInContinuation = continuation
                signInLauncher?.launch(googleSignInClient!!.signInIntent)
            }

            if (data == null) {
                return Result.failure(Exception("Inicio de sesión cancelado"))
            }

            // Get Google account from the result
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = try {
                task.getResult(ApiException::class.java)
            } catch (e: ApiException) {
                return Result.failure(Exception("Error de Google Sign-In: ${e.statusCode}"))
            }

            // Create Firebase credential with Google ID token
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            // Sign in to Firebase
            val authResult = suspendCancellableCoroutine<com.google.firebase.auth.AuthResult> { cont ->
                firebaseAuth!!.signInWithCredential(credential)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }

            // Get Firebase ID token (JWT) for Firestore REST API
            val tokenResult = suspendCancellableCoroutine<com.google.firebase.auth.GetTokenResult> { cont ->
                authResult.user?.getIdToken(false)
                    ?.addOnSuccessListener { cont.resume(it) }
                    ?.addOnFailureListener { cont.resumeWithException(it) }
                    ?: cont.resumeWithException(Exception("Usuario no disponible"))
            }

            Result.success(tokenResult.token ?: throw Exception("Token no disponible"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val FIREBASE_API_KEY = "AIzaSyCOSray4XhnZGdgT91U14KlByk6ySuyhW0"
        const val FIREBASE_PROJECT_ID = "task-hub-62f98"
        // TODO: Replace with your real Android app ID from Firebase Console
        const val FIREBASE_APP_ID = "1:1017439933996:android:placeholder"
    }
}