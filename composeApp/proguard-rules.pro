#============================================================================
# Task Hub ProGuard/R8 Rules
#============================================================================

# ── Kotlin Serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes and their fields
-keep,includedescriptorclasses class org.taskhub.**$$serializer { *; }
-keepclassmembers class org.taskhub.** {
    *** Companion;
}
-keepclasseswithmembers class org.taskhub.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all our data models (Firestore DTOs + network responses).
# NOTA: antes había también un `-keep class org.taskhub.network.** { *; }`
# que mantenía SIN ofuscar ni encoger todo el paquete network (incluida la
# lógica de negocio de FirestoreRepository, no solo los DTOs serializables),
# anulando gran parte del beneficio de R8 y facilitando la ingeniería inversa
# de los endpoints/lógica de puntos. Los DTOs (FirestoreValue, TaskResponse...)
# ya quedan protegidos por las reglas de kotlinx.serialization de arriba
# (aplican a todo org.taskhub.**) + las líneas explícitas de abajo.
-keep class org.taskhub.network.FirestoreValue { *; }
-keep class org.taskhub.network.FirestoreArrayValue { *; }
-keep class org.taskhub.network.FirestoreMapValue { *; }
-keep class org.taskhub.network.FirestoreDocument { *; }
-keep class org.taskhub.network.FirestoreDocumentResponse { *; }
-keep class org.taskhub.network.FirestoreListResponse { *; }
-keep class org.taskhub.network.FirestoreErrorEnvelope { *; }
-keep class org.taskhub.network.FirestoreErrorBody { *; }
-keep class org.taskhub.network.FirebaseAuthRequest { *; }
-keep class org.taskhub.network.FirebaseAuthResponse { *; }
-keep class org.taskhub.network.SignInWithIdpRequest { *; }
-keep class org.taskhub.network.TokenRefreshResponse { *; }
-keep class org.taskhub.network.models.** { *; }
-keep class org.taskhub.storage.SavedHousehold { *; }

# ── Ktor Client ──────────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }

# ── Koin DI ──────────────────────────────────────────────────────────────────
-keep class org.koin.** { *; }
-keep class org.taskhub.di.** { *; }

# ── Voyager Navigation ───────────────────────────────────────────────────────
-keep class cafe.adriel.voyager.** { *; }

# ── Compose ──────────────────────────────────────────────────────────────────
# Sin -keep de paquete completo: Jetpack Compose / Compose Multiplatform ya
# incluyen sus propias consumer-rules.pro dentro de cada AAR, que AGP fusiona
# automáticamente. Mantener aquí `androidx.compose.** { *; }` era redundante
# y anulaba gran parte del beneficio de R8 (mismo problema, a mayor escala,
# que el ya corregido para org.taskhub.network — ver comentario más arriba).
-dontwarn androidx.compose.**

# ── kotlinx.datetime ─────────────────────────────────────────────────────────
-keep class kotlinx.datetime.** { *; }

# ── QR Code library ──────────────────────────────────────────────────────────
-keep class io.github.g0dkar.qrcode.** { *; }

# ── Firebase / Google Play Services ──────────────────────────────────────────
# Sin -keep de paquete completo: estos SDKs traen sus propias consumer-rules.pro
# empaquetadas desde hace años (mismo razonamiento que Compose, arriba).
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── AndroidX WorkManager ─────────────────────────────────────────────────────
-keep class androidx.work.** { *; }

# ── Multiplatform Settings ───────────────────────────────────────────────────
-keep class com.russhwolf.settings.** { *; }

# ── General Android ──────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Keep the widget provider (referenced from manifest)
-keep class org.taskhub.TaskHubWidgetProvider { *; }
-keep class org.taskhub.TaskHubFirebaseMessagingService { *; }
-keep class org.taskhub.MainActivity { *; }
-keep class org.taskhub.TaskHubApplication { *; }

# ── Eliminar null-checks de Kotlin en release (no es logging pese al título
#    original de esta sección) ───────────────────────────────────────────────
# Decisión consciente: R8 elimina en release las comprobaciones de nulidad que
# el compilador de Kotlin inserta para parámetros no-nulos. Ahorra tamaño/
# rendimiento a cambio de que un `null` inesperado (interop Java, reflexión)
# falle más adentro del código con un stacktrace menos claro que en debug.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNullParameter(...);
}