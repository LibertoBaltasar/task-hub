# Encargo: eliminar la subida de foto (Storage de pago) — dejar solo foto de Google

## Objetivo
Revertir ÚNICAMENTE la parte de "subir foto propia" de la feature de avatar,
dejando intacta la parte "mostrar foto de Google" (que es gratis y no usa
Storage). La subida requiere Firebase Storage (plan de pago) y se descarta.

## Contexto
El trabajo previo (ya commiteado localmente, sin push) añadió:
1. Carga de imagen con Coil 3.2.0 + componente `UserAvatar` (foto Google → emoji → inicial).
2. Subida de foto propia (galería/cámara) vía ImagePicker + Firebase Storage REST.

Hay que QUITAR el punto 2 y CONSERVAR el punto 1.

## QUITAR (eliminar por completo)

### Archivos a BORRAR
- `composeApp/src/commonMain/kotlin/org/taskhub/platform/ImagePicker.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/platform/ImagePickerResultHolder.kt`
- `composeApp/src/androidMain/kotlin/org/taskhub/platform/ImagePicker.android.kt`
- `composeApp/src/androidMain/kotlin/org/taskhub/ImagePickerHelper.kt`
- `composeApp/src/iosMain/kotlin/org/taskhub/platform/ImagePicker.ios.kt`
- `composeApp/src/jvmMain/kotlin/org/taskhub/platform/ImagePicker.jvm.kt`
- `composeApp/src/androidMain/res/xml/file_paths.xml`
- `storage.rules`
- `scripts/deploy_storage_rules.py`

### Ediciones para deshacer la subida
- `ui/screens/EditProfileScreen.kt`:
  - Quitar botones "Elegir foto" / "Hacer foto" y todo el estado asociado a
    subida/error (loading, mensaje de error de upload, etc.).
  - CONSERVAR el `UserAvatar` como preview (muestra la foto de Google si existe).
  - CONSERVAR el selector de emoji (sigue siendo el fallback cuando no hay foto).
- `network/FirestoreRepository.kt`:
  - Quitar el método de subida a Storage (y `storageBaseUrl`/`storageBucket` si
    solo se usaban para eso). No tocar nada de Firestore.
- `ui/i18n/AppStrings.kt`:
  - Quitar las claves de subida: `profile_photo_choose`, `profile_photo_take`,
    `profile_photo_uploading` (o los nombres que se hayan usado).
  - CONSERVAR `avatar_content_desc` (o la clave de contentDescription del avatar).
- `composeApp/src/androidMain/AndroidManifest.xml`:
  - Quitar el `<provider>` de FileProvider y el `file_paths` si se añadieron,
    y cualquier `<queries>` de cámara/visor de paquetes que se haya metido solo
    para la foto.
- `composeApp/src/androidMain/kotlin/org/taskhub/MainActivity.kt`:
  - Quitar el registro/uso del ImagePicker.
- `di/AppModule.kt`:
  - Quitar el registro Koin del ImagePicker.
- `firebase.json`:
  - Quitar la entrada `"storage"` (dejar solo `"firestore"`).

## CONSERVAR (no tocar)
- Coil 3.2.0 en `gradle/libs.versions.toml` y `composeApp/build.gradle.kts`.
- `ui/components/UserAvatar.kt`.
- Los usos de `UserAvatar` en RankingScreen, HouseholdScreen (MemberCard),
  TaskDetailScreen, PublicProfileScreen y EditProfileScreen.
- `ui/models/GoogleAuthManager.kt` y `network/FirestoreDtos.kt` (captura del
  `photoUrl` de Google) — salvo que dependan del picker, en cuyo caso quitar
  SOLO la dependencia del picker, no la captura del photoUrl.
- El resto de la app.

## Definición de hecho
- `cd ~/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`
  termina en BUILD SUCCESSFUL.
- No queda ninguna referencia a ImagePicker, storageBucket/storageBaseUrl,
  `avatars/` upload, ni las claves i18n de subida.
- `EditProfileScreen` sigue mostrando el avatar (foto Google o emoji) y permite
  elegir emoji, pero ya no ofrece subir foto.

## Entrega
- Hacer el trabajo y compilar. NO commit, push ni bump (lo hace el orquestador).
- Resumir: archivos borrados, archivos editados, y resultado de la compilación.
