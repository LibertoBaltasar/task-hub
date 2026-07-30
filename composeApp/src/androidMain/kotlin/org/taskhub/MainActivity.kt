package org.taskhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.taskhub.auth.GoogleSignInHelper
import org.taskhub.auth.GoogleSignInHelperHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the Google Sign-In helper before the UI loads.
        val helper = GoogleSignInHelper()
        helper.initialize(this)

        // Store the initialized instance so the Koin module can retrieve it
        // via createGoogleSignInHelper()
        GoogleSignInHelperHolder.instance = helper

        setContent {
            App()
        }
    }
}