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

## NO implementado — requiere decisión (bloqueo principal)

**Google Sign-In en desktop** (`launchGoogleSignIn()` en `Platform.jvm.kt`,
línea ~27) sigue señalizando "sin token" inmediatamente. Esto YA estaba
documentado como pendiente de decisión de producto en
`docs/google-only-auth-2026-09-12.md` (sección "SOLO PROPUESTA — requiere
decisión", punto 1), de cuando se eliminó el modo anónimo: sin él, JVM/
Desktop se queda permanentemente en `AuthGateScreen`.

El encargo pedía implementar el flujo OAuth completo (navegador + loopback
callback local + intercambio por id_token vía `signInWithIdp`, que ya existe
en `commonMain`). No se ha implementado porque requiere, antes que nada,
**infraestructura nueva fuera del repo**: registrar un OAuth Client ID de
tipo "Desktop app" en Google Cloud Console para el proyecto
`task-hub-62f98` (distinto del client ID Android ya configurado — usa el
flujo authorization-code + PKCE con redirect a loopback, y Google exige un
`client_secret` embebido aunque para apps instaladas no se trate como
secreto real). Eso implica acceso a la consola de Google Cloud del proyecto,
generar credenciales nuevas y decidir cómo se empaquetan/distribuyen en el
binario — justo el tipo de infraestructura/decisión de producto que este
encargo automático no debe resolver por su cuenta. Entregar el código del
flujo sin un client ID real sería además código no verificable end-to-end.

**Empaquetado DMG firmado/notarizado** (punto 4 del encargo) requiere un Mac
con Xcode/`codesign` y una cuenta de Apple Developer — despliegue externo
que no se puede hacer desde este entorno Linux ni sin esas credenciales.

## Siguiente paso sugerido (fuera de este encargo)

1. Decidir y crear en Google Cloud Console un OAuth Client ID "Desktop app"
   para `task-hub-62f98` (Liberto, con acceso al proyecto).
2. Con ese client ID, un encargo de código puede implementar el flujo
   loopback + `signInWithIdp` sobre `GoogleAuthManager`/
   `GoogleSignInResultHolder` ya existentes.
3. El empaquetado/firma del DMG es un paso manual en Mac, no automatizable
   por Claude.

## Verificación

- `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm --console=plain` → **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:jvmTest --console=plain` → **BUILD SUCCESSFUL**. Conteo
  real de `composeApp/build/test-results/jvmTest/*.xml`: **257 tests, 0
  failures, 0 errors**.
- No se ha hecho `git push` ni se ha desplegado nada.
