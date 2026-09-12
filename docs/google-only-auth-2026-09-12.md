# Google-only: eliminación de la auth anónima — 2026-09-12

HEAD de partida: `8cc954dcc050c8d0fe71efdda5f20c7270db2aa1` (`fix: vincula la
cuenta de Google a la sesión anónima al iniciar sesión`). Decisión de
producto de Liberto (2026-09-12): Task Hub pasa a requerir Google Sign-In —
se elimina la auth anónima. Contexto: la base de datos de producción se
vació el mismo día (sin legado que migrar) y la sincronización entre
dispositivos venía rompiéndose por los huérfanos del flujo anónimo→Google
(ver `docs/auditoria-sync-dispositivos-2026-09-12.md`, encargo previo).

**El proveedor anónimo en Firebase Auth NO se ha desactivado desde aquí** —
lo hará el orquestador cuando esta versión se publique, como se indicó en el
encargo.

## Qué se eliminó

### `network/FirestoreClient.kt`
- Eliminado `authUrl` (`accounts:signUp`, alta anónima) y el paso 3 de
  `ensureAuth()` que la invocaba.
- Eliminado el paso 2 de `ensureAuth()` (restaurar refresh token anónimo).
  `ensureAuth()` ahora solo intenta restaurar la sesión de Google persistida
  y devuelve `null` si no hay ninguna — ya no hay a qué caer.
- `getLocalId()` (línea ~124) y `currentUserIdentities()` (línea ~136) ya no
  consultan `settingsStore.getAnonymousUid()`.

### `storage/SettingsStore.kt`
- Eliminados `getAnonymousRefreshToken`/`getAnonymousUid`/`saveAnonymousAuth`/
  `clearAnonymousAuth` y las constantes `KEY_ANON_REFRESH_TOKEN`/`KEY_ANON_UID`.
- Eliminados `hasSeenGooglePrompt`/`setHasSeenGooglePrompt` y
  `KEY_GOOGLE_PROMPT_SEEN`: con el login obligatorio, el prompt dismissible
  de "conecta tu cuenta" que vivía en `HomeScreen` (ver abajo) deja de tener
  sentido — ya no se llega a `HomeScreen` sin sesión.

### `network/AccountLinkingRules.kt` + `AccountLinkingRulesTest.kt` (borrados)
El mecanismo de "vincular Google a la sesión anónima activa" (el fix del
encargo de auditoría previo) solo existía para preservar `ownerId`/`members`
de hogares creados en modo anónimo al pasar a Google. Sin auth anónima nunca
hay una sesión activa que vincular — el objeto entero (y su suite de 6 tests)
quedó muerto. `FirestoreRepository.signInWithGoogle` (~línea 176) se
simplificó a una única llamada a `accounts:signInWithIdp` sin `idToken` de
vinculación ni fallback. `FirestoreDtos.kt`: `SignInWithIdpRequest` perdió el
campo `idToken`; `FirebaseAuthRequest` (cuerpo del alta anónima) se borró
entero.

### `ui/models/GoogleAuthManager.kt`
- `GoogleAuthState.Anonymous` → **`GoogleAuthState.SignedOut`** (y se quitó
  `Idle`, que nunca se llegaba a asignar). Todas las transiciones que antes
  caían a `Anonymous` (cancelar el selector de cuenta, `signOut()`,
  `deleteAccount()`) ahora caen a `SignedOut`.
- `signOut()`: ya no dejaba usar la app "en modo anónimo" tras cerrar sesión
  — antes tampoco lo hacía explícitamente, pero con el gate nuevo (ver
  `App.kt`) ahora es visible: cerrar sesión saca al usuario a la pantalla de
  login.
- `deleteAccount()`: eliminada la recreación automática de un espacio
  "Personal" anónimo tras borrar la cuenta (antes llamaba a
  `getOrCreatePersonalHousehold()`/`ensurePersonalMember()` con una identidad
  anónima nueva). Ahora termina en `SignedOut`: el usuario debe volver a
  iniciar sesión con Google para crear una cuenta nueva.
- `currentUserId()`/`reauthenticateForDeletion()`/`linkCalendar()`:
  actualizados a la nueva nomenclatura, sin cambio de comportamiento más allá
  de lo ya descrito.

### `ui/screens/HomeScreen.kt` + `ui/models/HomeScreenModel.kt`
Eliminado el `AlertDialog` "Guarda tus datos con Google" (dismissible, con
"Ahora no") y `shouldShowGooglePrompt()`/`markGooglePromptSeen()`: con el
login obligatorio ANTES de llegar a `HomeScreen`, ese prompt nunca podía
volver a dispararse (el usuario ya está `SignedIn` para cuando ve esta
pantalla). Las claves i18n `home_google_prompt_*` se borraron (ES+EN).

## Flujo nuevo de onboarding (lo que se añadió)

`App.kt` ahora observa `GoogleAuthManager.state` a nivel raíz:

- Si `authState !is GoogleAuthState.SignedIn` → se pinta
  **`ui/screens/AuthGateScreen.kt`** (nuevo, no es un `Screen` de Voyager: se
  pinta directamente desde `App.kt`, igual que `SplashScreen`, porque el
  `Navigator` de la app todavía no existe en este punto) y el resto del
  composable no se ejecuta (`return@CompositionLocalProvider`). La pantalla
  muestra logo + título/subtítulo ("Task Hub necesita tu cuenta de Google...")
  y botón "Iniciar sesión con Google" / spinner (`SigningIn`) / error
  reintentable (`Error`), reutilizando los strings ya existentes de
  `SettingsSheet`.
- Solo si `authState is GoogleAuthState.SignedIn` corre el bootstrap de
  arranque en frío (antes en `LaunchedEffect(Unit)`, ahora
  `LaunchedEffect(authState)`): resolver/crear el espacio Personal
  (`personal_{uidGoogle}`, determinista), asegurar el miembro "Yo",
  `restoreFromCloudOnStartup()`, subir el token FCM, y solo entonces se
  construye el `Navigator` con `HomeScreen()` (+ deep link si lo hay).
- Si el usuario cierra sesión o elimina la cuenta mientras ya estaba dentro
  de la app, `authState` deja de ser `SignedIn`, el mismo `LaunchedEffect`
  resetea `initialScreens = null` y la UI vuelve a `AuthGateScreen` — el
  `Navigator` completo se descarta y se reconstruye desde cero en el próximo
  login (pila de navegación limpia, sin pantallas de la sesión anterior
  colgando).
- `WelcomeScreen.kt` (pantalla de "crear/unirse a un hogar sin cuenta") ya
  estaba **muerta** antes de este encargo — no se instancia desde ningún
  sitio (su propio comentario dice "Reemplaza a WelcomeScreen" en
  `HomeScreen.kt`) — no se ha tocado, sigue sin usarse.

## Anclaje de datos — verificado

- `ownerId` de hogares (`HouseholdRepository.createHousehold`) y
  `member.userId` (`MemberRepository.createMember`) usan
  `firestoreClient.getLocalId()`, que tras este cambio SOLO puede devolver
  el UID de Google (o `null` → `IllegalStateException("No autenticado")`,
  guard ya existente). Con el gate de `App.kt`, estas llamadas solo ocurren
  ya autenticado.
- `users/{uidGoogle}.householdIds`: sin cambios, ya usaba
  `settingsStore.getGoogleUid()` (`GoogleAuthManager.syncHouseholdsToCloud`/
  `restoreHouseholds`).
- Caché local (`SettingsStore`/`HouseholdStore`/`TaskCache`): sin IDs
  anónimos que limpiar más allá de lo ya eliminado en `SettingsStore`;
  `HouseholdStore` nunca guardó nada específico de auth (solo IDs de hogar),
  se actualizaron sus comentarios (ya no mencionan auth anónima como motivo
  de existir).
- El modelo de miembro "hijo/a" sin cuenta vinculada dentro de un hogar
  (`member.userId == null`) **se mantiene intacto** — no es una cuenta de
  auth, y ninguno de los cambios de este encargo toca `MemberRepository`,
  `MemberResolutionRules` ni `firestore.rules`.

## Tests

- `SettingsStoreTest.kt`: eliminados los 2 tests de
  `getAnonymousRefreshToken` (migración legacy y "sin valor"); el resto
  intacto.
- `MemberResolutionRulesTest.kt`: el test
  `resolveExistingMemberId_matchesAnyOfMultipleIdentities` usaba
  `"uid-anonymous"` como segunda identidad de ejemplo — renombrado a
  `"uid-google-legacy"` y reescrito el KDoc para no sugerir que sigue
  existiendo una identidad anónima real; la función bajo test
  (`MemberResolutionRules.resolveExistingMemberId`) es genérica y no cambia.
- `AccountLinkingRulesTest.kt`: eliminado junto con la clase que testeaba.
- No se ha añadido un test end-to-end del login Google-only
  (`signInWithGoogle`/`ensureAuth` sin sesión): igual que documentó el
  encargo de auditoría previo, el codebase no tiene infraestructura de
  mocking HTTP (`MockEngine` de Ktor) para `FirestoreClient`/
  `FirestoreRepository` en ningún test existente — no era una infraestructura
  a introducir en este encargo puntual.

### Resultados
- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` →
  **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:jvmTest --console=plain` → **BUILD SUCCESSFUL**.
  Conteo real parseado de `composeApp/build/test-results/jvmTest/*.xml`:
  **239 tests, 0 failures, 0 errors** (247 previos − 6 de
  `AccountLinkingRulesTest` − 2 de `getAnonymousRefreshToken` = 239).

## SOLO PROPUESTA — requiere decisión

1. **iOS y JVM/Desktop quedan inutilizables tal cual con este cambio.**
   `launchGoogleSignIn()` en `Platform.ios.kt` y `Platform.jvm.kt` NO tiene
   una implementación real — ambas señalizan inmediatamente "sin token"
   (`GoogleSignInResultHolder.setResult("")`, el mismo camino que "el usuario
   canceló el selector de cuenta"). Antes, eso simplemente dejaba a esas
   plataformas en modo anónimo (usable, aunque sin nube). Ahora, sin modo
   anónimo, esas dos plataformas se quedan **permanentemente** en
   `AuthGateScreen`: no hay forma de pasar el gate de login. Solo Android
   (`Platform.android.kt`) tiene Google Sign-In real. He dejado comentarios
   `IMPORTANTE` en ambos archivos señalando el bloqueo, pero no he
   implementado Google Sign-In para iOS/JVM (fuera del alcance de este
   encargo) ni he limitado el alcance de "Google-only" a Android en
   `CLAUDE.md`/`build.gradle.kts` — decisión de producto: ¿implementar
   Sign-In real en esas plataformas antes de publicar esta versión, o asumir
   que iOS/Desktop quedan en pausa/no soportados hasta entonces?
2. **`GoogleAuthState.SignedOut` como nombre**: el encargo sugería "¿o se
   replantea?" — he optado por `SignedOut` (sin sesión, gate de login) en
   vez de mantener `Anonymous` con otro significado. Si se prefiere otro
   nombre (p.ej. `RequiresSignIn`), es un rename mecánico.

## No tocado (fuera de alcance / no aplicable)

- `firestore.rules` y `functions/src/*`: no mencionaban auth anónima
  (gatean por `signedIn()`/UID genérico) — sin cambios, confirmado por grep.
- Versión de la app (`build.gradle.kts`, `WelcomeScreen.kt` literal
  `v0.7.29`): no se ha tocado, según instrucción explícita del encargo.
- No se ha hecho `git push` ni se ha desplegado nada.
