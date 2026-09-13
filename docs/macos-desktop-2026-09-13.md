# Versión macOS de Task Hub (desktop JVM) — 2026-09-13

Encargo desde kanban: publicar la primera versión macOS. Evaluación contra el
código real (`composeApp/src/jvmMain`) y decisión de qué implementar.

## Hecho en este encargo

- `platform/Platform.jvm.kt`: `shareText()` ya no es un no-op — copia el
  texto al portapapeles del sistema (`java.awt.Toolkit`/`StringSelection`).
  Cubre los dos llamantes existentes (`HouseholdDialogs.kt` código de
  invitación, `TaskListScreen.kt` export CSV). El propio encargo lo marcaba
  como aceptable ("clipboard o nada").
- Notificaciones desktop: ya eran no-op, sin cambios necesarios.
- Revisión de ventana: `Main.kt` usa `Window(onCloseRequest, title = ...)`
  sin `resizable = false` ni `state` fijo — ya es redimensionable por
  defecto (parámetro `resizable` de Compose Desktop es `true` salvo que se
  desactive explícitamente). No se ha encontrado ningún padding específico
  de escritorio roto; una revisión de UI más a fondo requeriría probarlo en
  una ventana real (no disponible en este entorno Linux) para ser más que
  especulativa.

## Implementado (encargo 2026-09-13, segunda pasada) — Google Sign-In real en desktop

`launchGoogleSignIn()` (`Platform.jvm.kt`) ya no es un no-op: lanza el flujo
OAuth 2.0 authorization-code + PKCE con redirect loopback completo, en un
`CoroutineScope(Dispatchers.IO)` propio, sin bloquear el hilo de UI.

**Archivos nuevos:**
- `composeApp/src/jvmMain/kotlin/org/taskhub/platform/GoogleOAuthConfig.jvm.kt`
  — `CLIENT_ID`/`CLIENT_SECRET`, vacíos por defecto, con el KDoc de dónde
  rellenarlos.
- `composeApp/src/jvmMain/kotlin/org/taskhub/platform/GoogleDesktopSignInHelper.kt`
  — todo el flujo: genera `state` + PKCE (`code_verifier`/`code_challenge`
  S256) con `SecureRandom`, abre el navegador del sistema
  (`java.awt.Desktop.browse`) contra el `authorization_endpoint` de Google,
  levanta un `ServerSocket` efímero en `127.0.0.1` para el callback
  (`/callback?code=...&state=...`), valida `state`, sirve una página HTML de
  cierre, y canjea el `code` por tokens en `POST oauth2.googleapis.com/token`
  (Ktor, motor `ktor-client-java` ya presente en `jvmMain`) para obtener el
  `id_token`.

**Archivo modificado:**
- `composeApp/src/jvmMain/kotlin/org/taskhub/platform/Platform.jvm.kt` —
  `launchGoogleSignIn()` delega en `GoogleDesktopSignInHelper.signIn()` y
  entrega el resultado a `GoogleSignInResultHolder.setResult(...)` EXACTAMENTE
  igual que Android (`""` si falla/cancela/expira/config vacía, el id_token si
  hay éxito) — de ahí en adelante el flujo es idéntico al de Android:
  `GoogleAuthManager` lo intercambia por credenciales de Firebase vía
  `accounts:signInWithIdp` (ya existente en `commonMain`, sin tocar).

**Cómo probarlo en un Mac real:**
1. En Google Cloud Console del proyecto `task-hub-62f98` → APIs y servicios
   → Credenciales → Crear credenciales → ID de cliente de OAuth → tipo
   **"Aplicación de escritorio"**. Copiar el `CLIENT_ID`/`CLIENT_SECRET`
   resultantes en `GoogleOAuthConfig.jvm.kt`.
2. Verificar en Firebase Console → Authentication → Sign-in method → Google
   que ese nuevo client ID esté en la lista de "OAuth client IDs" aceptados
   por el proveedor (si Firebase rechaza el `id_token` por audiencia
   desconocida, añadirlo ahí) — no se ha podido comprobar este paso desde
   este entorno.
3. `./gradlew :composeApp:run` y pulsar "Iniciar sesión con Google" en
   `AuthGateScreen`: se abre el navegador por defecto, tras el consentimiento
   redirige a `http://127.0.0.1:<puerto>/callback`, la pestaña muestra "Ya
   puedes cerrar esta pestaña" y la app debería quedar en `SignedIn`.

**Qué no se ha podido verificar en este entorno** (Linux, sin client ID real
ni navegador interactivo): el flujo end-to-end completo (apertura de
navegador, consentimiento real, canje de código, y si Firebase acepta el
`id_token` de un client ID "Desktop app" sin configuración adicional en la
consola de Firebase). El código compila y las piezas (PKCE, parseo de la
query del callback, submitForm) se han revisado a mano contra la spec
(RFC 7636 para PKCE, RFC 6749 para el authorization code flow), pero no hay
sustituto de probarlo con credenciales reales en un Mac.

**Empaquetado DMG firmado/notarizado** (punto 4 del encargo original) sigue
sin implementar — requiere un Mac con Xcode/`codesign` y una cuenta de Apple
Developer, fuera del alcance de este entorno.

## Verificación

- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` → **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:compileKotlinJvm --console=plain` → **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:jvmTest --console=plain` → **BUILD SUCCESSFUL**. Conteo
  real de `composeApp/build/test-results/jvmTest/*.xml`: **257 tests, 0
  failures, 0 errors** (sin tests nuevos: el flujo OAuth no es verificable
  sin credenciales reales ni navegador interactivo en este entorno).
- No se ha hecho `git push` ni se ha desplegado nada.
