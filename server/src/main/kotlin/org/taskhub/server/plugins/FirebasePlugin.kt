package org.taskhub.server.plugins

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.application.*
import java.io.FileInputStream

object FirebasePlugin {

    lateinit var firestore: Firestore
        private set

    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            firestore = FirestoreClient.getFirestore()
            return
        }

        val serviceAccountPath = System.getenv("FIREBASE_SERVICE_ACCOUNT")
            ?: System.getProperty("firebase.service.account")
            ?: "${System.getProperty("user.home")}/.hermes/firebase-service-account.json"

        val serviceAccount = FileInputStream(serviceAccountPath)
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        val app = FirebaseApp.initializeApp(options)
        firestore = FirestoreClient.getFirestore(app)
    }
}
