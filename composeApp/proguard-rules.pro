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

# Keep all our data models (Firestore DTOs + network responses)
-keep class org.taskhub.network.** { *; }
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
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ── kotlinx.datetime ─────────────────────────────────────────────────────────
-keep class kotlinx.datetime.** { *; }

# ── QR Code library ──────────────────────────────────────────────────────────
-keep class io.github.g0dkar.qrcode.** { *; }

# ── Firebase / Google Play Services ──────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
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

# ── Remove debug logging in release ──────────────────────────────────────────
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNullParameter(...);
}