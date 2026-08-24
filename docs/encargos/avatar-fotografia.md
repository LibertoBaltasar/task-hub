# Feature: avatar como fotografía (upload propio + foto de Google)

Implementar en Task Hub (Compose Multiplatform) que el avatar de los usuarios
pueda ser una FOTOGRAFÍA real, además del emoji actual.

## Objetivo (ambas cosas)
1. **Mostrar la foto que ya existe** (`avatarUrl`, p.ej. la foto de Google al
   iniciar sesión) como avatar, con fallback al emoji cuando no haya foto.
2. **Permitir subir una foto propia** desde galería/cámara y usarla como avatar.

## Contexto del proyecto (LEER antes de tocar nada)
- Stack: Compose Multiplatform (Kotlin 2.1, CMP 1.7.3) → Android (minSdk 26,
  target 35) + iOS + JVM desktop. Navegación Voyager, DI Koin, datos Firestore
  vía **REST con Ktor** (NO Firestore SDK), persistencia multiplatform-settings.
- i18n: `ui/i18n/AppStrings.kt` (ES + EN), `AppStrings.get(key, lang)`.
- Solo `material-icons-core` (NO material-icons-extended): disponibles Add,
  AddCircle, Home, Person, Settings, Edit, Delete, Close, Search, Refresh,
  Notifications, DateRange, KeyboardArrow*, Email, Lock, etc. Cualquier icono
  fuera de ese set ROMPE el build.
- Iconos espejados → `Icons.AutoMirrored.Filled.*` (ArrowBack, ExitToApp); el
  alias `Icons.Default.*` está deprecado.
- expect/actual: `platform/` para ads, notificaciones, QR, Google Sign-In.
- Verificar compilación: `cd ~/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`

## Estado actual (ya existe, NO reimplementar)
- `network/models/DTOs.kt` → `UserProfile` ya tiene `avatarUrl: String?` y
  `avatarEmoji: String`.
- `network/FirestoreRepository.kt` → `upsertUserProfile(userId, displayName,
  avatarUrl, avatarEmoji, bio, status)` ya escribe `avatarUrl` en Firestore.
- `network/FirestoreDtos.kt` → la respuesta de Firebase Auth (`FirebaseAuthResponse`)
  ya trae `photoUrl`.
- `ui/models/ProfileScreenModel.kt` → `saveProfile(...)` ya preserva `avatarUrl`.
- `ui/screens/EditProfileScreen.kt` → hoy solo deja elegir EMOJI (grid). Hay que
  añadir la opción de foto.
- NO existe ninguna librería de carga de imágenes (ni Coil, ni Kamel, ni
  AsyncImage). Hay que añadirla.

## Trabajo a hacer

### 1. Librería de carga de imágenes
- Añadir **Coil 3** (Coil Compose para CMP) a `gradle/libs.versions.toml` y a
  `composeApp/build.gradle.kts` (commonMain). Verificar que compile en las 3
  plataformas (Android real; iOS/JVM al menos que no rompa el commonMain).
- Crear un componente reutilizable de avatar (p.ej. en `ui/components/`) que:
  - Si `avatarUrl != null` y no está vacía → carga la imagen con Coil (circular,
    con `contentDescription`).
  - Si no hay foto → pinta el `avatarEmoji` (fallback).
  - Si no hay ni foto ni emoji → inicial del nombre o `Person`.

### 2. Upload de foto propia (galería/cámara)
- En `EditProfileScreen`, añadir botones "Elegir foto" (galería) y "Hacer foto"
  (cámara).
- **Picker de imagen multiplataforma**: usar un expect/actual en `platform/`
  (nueva clase, p.ej. `ImagePicker`), con implementación real en androidMain
  (ActivityResultContracts.PickVisualMedia / TakePicture) y no-op o placeholder
  en iOS/JVM desktop (que no rompa el build).
- **Storage de la imagen**: el proyecto NO usa Firebase SDK. Para subir la foto:
  - Opción preferida: Firebase Cloud Storage vía REST con token de acceso
    (resumable upload o POST directo). Reutilizar el idToken de Google del
    usuario autenticado si está disponible.
  - Si el REST de Storage es inviable sin SDK, plantear alternativa y dejar
    claro el coste. NO añadir el Firebase Storage SDK (rompe la arquitectura
    REST-only y el resto del proyecto).
  - La URL pública resultante se guarda en `avatarUrl` vía `upsertUserProfile`.
- Añadir compresión/re-escalado de la imagen antes de subir (que no suba el
  original de 12 MP; bajar a ~512–1024 px).

### 3. Renderizar el avatar nuevo en TODOS los puntos donde hoy se muestra
- Buscar TODOS los usos de `avatarEmoji` / emoji-avatar en las pantallas
  (Ranking, HouseholdScreen MemberCard, TaskDetailScreen, ProfileScreen,
  PublicProfileScreen, StatsScreen, EditProfileScreen, etc.) y sustituirlos por
  el componente reutilizable del punto 1.
- Mantener la accesibilidad: `contentDescription` significativo en cada avatar.

## Restricciones
- Mensajes de commit en español: `feat: ...`.
- No tocar arquitectura REST-only del proyecto.
- No añadir iconos fuera del set core.
- i18n: cualquier texto nuevo visible pasa por `AppStrings`.
- Añadir KDoc en los modelos/componentes nuevos.

## Definición de hecho
- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` termina en
  BUILD SUCCESSFUL.
- El componente de avatar carga foto si hay `avatarUrl`, si no cae al emoji.
- EditProfileScreen permite elegir foto de galería/cámara y la sube guardando
  `avatarUrl`.
- Todos los puntos de la app que pintaban el avatar usan el componente nuevo.

## Entrega
- Hacer el trabajo, compilar y verificar. NO hacer commit ni push ni bump de
  versión (eso lo hace el orquestador). Dejar los cambios en el working tree.
- Al terminar, resumir: archivos tocados, decisión sobre el storage de la foto,
  y resultado de la compilación.
