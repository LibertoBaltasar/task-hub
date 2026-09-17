# Task Hub — Primeros pasos

Guía de onboarding para un desarrollador que se incorpora al proyecto. Repo: `LibertoBaltasar/task-hub`. Firebase: `task-hub-62f98`.

> Esta guía cubre solo el arranque (entorno, build, tests, release, Firebase). Para arquitectura, modelo de datos y flujos de producto, ver la sección "Ver también" al final.

---

## 1. Requisitos

Confirmado inspeccionando `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` y `.github/workflows/*.yml`:

| Herramienta | Versión | Fuente |
|---|---|---|
| JDK | **21** (Temurin/OpenJDK) | `composeApp/build.gradle.kts` (`sourceCompatibility`/`targetCompatibility` = `JavaVersion.VERSION_21`, `jvmTarget = JVM_21`); confirmado también en `.github/workflows/ci.yml` y `release.yml` (`setup-java` con `java-version: '21'`) |
| Gradle | 8.12 (wrapper, no instalar aparte) | `gradle/wrapper/gradle-wrapper.properties` |
| AGP (Android Gradle Plugin) | 8.10.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.1.0 | `gradle/libs.versions.toml` |
| Compose Multiplatform | 1.7.3 | `gradle/libs.versions.toml` |
| Android compileSdk / targetSdk | **36** | `composeApp/build.gradle.kts` (`compileSdk = 36`, `targetSdk = 36`); instalado en CI vía `platforms;android-36 build-tools;36.0.0` (`release.yml`) |
| Android minSdk | 26 | `composeApp/build.gradle.kts` |
| Xcode (solo para compilar/ejecutar el target iOS, requiere macOS) | orientativo 16+ | Único dato encontrado en `README.md` ("Xcode 16+, solo macOS"); no hay una versión mínima verificada contra el toolchain de Kotlin/Native 2.1 en ningún doc del repo — **confirmar con el dueño del proyecto** antes de asumirla como definitiva |

**Nota sobre `CLAUDE.md`**: ese archivo (memoria del proyecto) indica `targetSdk 35`, pero el `build.gradle.kts` real usa `targetSdk = 36` (ver commit `4b5d9ef` — "subir targetSdk a 36 (Android 16) — Play rechazaba el AAB"). La tabla de arriba refleja el valor real del código, no el de `CLAUDE.md`.

No hace falta instalar Android SDK/Gradle manualmente si usas Android Studio (lo resuelve el wrapper + el SDK manager). El SDK de Android debe soportar API 36.

---

## 2. Clonar y compilar

```bash
git clone git@github.com:LibertoBaltasar/task-hub.git
cd task-hub
```

### Verificación rápida (Android, sin empaquetar)

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```

`BUILD SUCCESSFUL` aquí **no** significa que toda la app esté verificada: solo confirma que compilan `commonMain` + `androidMain`. No compila `iosMain` ni `jvmMain`, y no ejecuta tests. Es el comando de referencia que usa el propio equipo para iterar rápido (ver `CLAUDE.md` y la skill `task-hub`).

### JVM Desktop

```bash
./gradlew :composeApp:jvmJar        # compila y empaqueta el jar del target JVM (commonMain + jvmMain)
./gradlew :composeApp:run           # ejecuta la app de escritorio (Compose Desktop)
```

Confirmado con `./gradlew tasks --all`: existen también `composeApp:runDistributable`, `composeApp:runRelease` y las tareas de empaquetado `composeApp:package`, `packageDmg`, `packageMsi`, `packageDeb`, `packageDistributionForCurrentOS` (definidas por el bloque `compose.desktop` en `composeApp/build.gradle.kts`).

### Android (APK completo)

```bash
./gradlew :composeApp:assembleDebug
```

### iOS

**Requiere macOS + Xcode** (orientativo 16+, ver tabla de requisitos arriba). Este repo incluye ya el proyecto host `iosApp/iosApp.xcodeproj`, que consume el framework `ComposeApp` generado por `composeApp/build.gradle.kts` (`baseName = "ComposeApp"`, `isStatic = true`, deployment target iOS 13.0).

**Abrir y ejecutar en simulador/dispositivo:**

1. Clona/actualiza el repo en un Mac con Xcode instalado.
2. Abre `iosApp/iosApp.xcodeproj` con Xcode (doble clic, o `open iosApp/iosApp.xcodeproj`).
3. Selecciona el target **iosApp** y, en el desplegable de destino, un simulador (p. ej. "iPhone 15") o un dispositivo físico conectado.
4. Si vas a ejecutar en un dispositivo físico (no en simulador), rellena `TEAM_ID` en `iosApp/Configuration/Config.xcconfig` con tu Team ID de Apple Developer (10 caracteres), o configúralo directamente en Xcode → target `iosApp` → pestaña **Signing & Capabilities** → Team. En simulador no hace falta.
5. Pulsa ▶️ (Run). El primer build phase, **"Compile Kotlin Framework"**, invoca automáticamente `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` para compilar el framework Kotlin/Native y copiarlo a `composeApp/build/xcode-frameworks/<Configuration>/<SDK>`; después Xcode compila y enlaza la app Swift contra ese framework. No hace falta ejecutar Gradle a mano — Xcode lo dispara en cada build.

**Regenerar el framework manualmente** (por ejemplo para depurar el paso de compilación sin abrir Xcode):

```bash
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
# o, solo para compilar sin firmar/incrustar:
./gradlew :composeApp:iosSimulatorArm64Binaries   # solo macOS, requiere Xcode + toolchain de Kotlin/Native
```

**Google Sign-In en iOS** (URL scheme, `Info.plist`, client ID) ya está implementado — ver guía completa de instalación local y troubleshooting en [`ios-instalacion-local-2026-09-18.md`](ios-instalacion-local-2026-09-18.md).

Ningún entorno de este repo tiene Xcode/macOS disponible para compilar o ejecutar el target iOS realmente (confirmado con varios docs, p. ej. `docs/refactor-arquitectura-2026-08-31.md`); el proyecto Xcode se ha creado siguiendo la plantilla canónica de Compose Multiplatform 1.7.x pero su compilación real debe verificarse en un Mac.

---

## 3. Tests

Suite de tests confirmada en `composeApp/src/commonTest/` (reglas de negocio: `PointsRulesTest`, `RecurrenceRulesTest`, `PenaltyRulesTest`, `HouseholdRulesTest`, `AssignmentCompletionRulesTest`, `FirestoreParsersTest`, etc.) y `composeApp/src/jvmTest/` (`SecureStoreJvmTest`).

```bash
./gradlew :composeApp:jvmTest --console=plain
```

Esta es la tarea que usa `.github/workflows/ci.yml` ("Run tests (JVM)"). Ejecuta los tests de `commonTest` + `jvmTest` sobre el target JVM (no requiere Android SDK ni Xcode).

Otras tareas de test detectadas con `./gradlew tasks --all` (Verification tasks):
- `composeApp:allTests` — agrega el resultado de todos los targets (incluye iOS si el toolchain está disponible).
- `composeApp:testDebugUnitTest` / `composeApp:testReleaseUnitTest` — tests unitarios Android (via JVM, sin dispositivo).
- `composeApp:iosX64Test` / `composeApp:iosSimulatorArm64Test` — tests Kotlin/Native, solo con toolchain de Xcode.
- `composeApp:connectedDebugAndroidTest` — instrumentados, requiere dispositivo/emulador conectado.

**Dónde leer el resultado:**
- Reporte HTML: `composeApp/build/reports/tests/jvmTest/index.html` (ruta estándar de Gradle/Kotlin Multiplatform; ábrelo en el navegador).
- Resultados XML (para CI/herramientas): `composeApp/build/test-results/jvmTest/`.

**Fallo de test vs. error de compilación:**
- Si Gradle se detiene en una tarea `compileTestKotlin*` (antes de llegar a `jvmTest`), es un **error de compilación** en el propio código de test o en el código que testea — no hay reporte HTML útil, el error sale directo en consola (archivo:línea).
- Si Gradle llega a ejecutar `jvmTest` y termina con `FAILED` señalando tests concretos, es un **fallo de test** (aserción incorrecta) — el detalle está en el HTML de arriba, por clase de test.

**Lint / detekt — hallazgo:** `ci.yml` invoca `./gradlew :composeApp:detekt` con `continue-on-error: true`, pero no se ha encontrado el plugin de detekt aplicado en `build.gradle.kts` (raíz) ni en `composeApp/build.gradle.kts`, ni una entrada `detekt` en `gradle/libs.versions.toml`. Con `./gradlew tasks --all` no aparece ninguna tarea `detekt`. Es probable que ese paso de CI falle por "tarea no encontrada" y quede oculto por `continue-on-error: true`. Confirmar con el dueño del proyecto si detekt se retiró intencionalmente o si falta re-aplicar el plugin (ver también sección HALLAZGOS del informe de esta tarea).

---

## 4. Bundle de release (AAB)

```bash
./gradlew :composeApp:bundleRelease
```

Genera el AAB firmado en `composeApp/build/outputs/bundle/release/composeApp-release.aab` (ruta usada también por `release.yml`). Requiere firma configurada vía `keystore.properties` en la raíz (no versionado, ver `.gitignore`: `keystore.properties`, `*.keystore`, `*.jks`) — sin él, `signingConfigs.release` queda vacío y el build de release puede fallar al firmar. Pide el keystore de subida y sus credenciales al dueño del proyecto.

Tiempo orientativo: **~5 min**, dominado por la minificación/optimización con R8 (`isMinifyEnabled = true`, `isShrinkResources = true` en el `buildType release`) — cifra tomada de `CLAUDE.md`, no remedida en esta tarea (se evitó lanzar el build completo a propósito, ver más abajo).

> Nota de esta guía: no se ha ejecutado `bundleRelease` al redactar este documento para no disparar un build largo en un worktree paralelo; el comando y la ruta de salida están verificados por inspección de `composeApp/build.gradle.kts` y `release.yml`, no por ejecución real.

---

## 5. Setup de Firebase local

- El proyecto Firebase es **`task-hub-62f98`**.
- El fichero de configuración de Google Services va en **`composeApp/google-services.json`**. A diferencia de lo habitual, en este repo **sí está trackeado en git** (no aparece en `.gitignore`; confirmado con `git log -- '**/google-services.json'`, con commits como `chore: sync google-services.json con Firebase`). Si tu checkout no lo trae por algún motivo, pídelo al dueño del proyecto o descárgalo desde la consola de Firebase: proyecto `task-hub-62f98` → Configuración del proyecto → tus apps → Android (`org.taskhub`) → descargar `google-services.json`.
- **Nota sobre la API key**: `docs/guia-publicacion.md` documenta un pitfall conocido — `google-services.json` **redacta `DEFAULT_API_KEY`** (aparece como valor no funcional). Si regeneras/descargas el archivo de nuevo desde la consola de Firebase, **debes restaurar manualmente ese valor** o se rompe la autenticación (`FirestoreRepository.kt` usa esa misma constante). Un panel de revisión (`docs/review-panel-expertos-2026-09-02.md`) confirma que esta API key no es secreta en sí (es la clave pública del proyecto; la seguridad real depende de `firestore.rules` + Auth), pero sigue siendo necesario que el valor coincida con el del proyecto para que el login funcione.
- No se ha encontrado en el repo un valor de referencia de esa API key ni un archivo de ejemplo (`google-services.json.example`) — para restaurarla, pide el valor correcto al dueño del proyecto.

---

## 6. Service account de solo lectura

Se ha buscado en `docs/guia-publicacion.md` y en el resto de `docs/*.md` una referencia a un service account de solo lectura para consultas administrativas. Lo encontrado:

- `docs/review-panel-expertos-2026-09-02.md` (línea ~407) menciona que una verificación de reglas de producción se hizo "contra la API real de Firebase Rules (`firebaserules.googleapis.com`, solo lectura)", y (línea ~447) aclara que en el repo no hay ningún service account trackeado en git.
- `docs/review-panel-expertos-2026-09-03.md` (línea ~140) repite una verificación "byte a byte" contra `firebaserules.googleapis.com`.
- `docs/guia-publicacion.md` (línea 43) da por hecho que ya existe "un *service account* con permisos de publicación" guardado como secret de GitHub (ese es el de **publicación** en Play Store, usado por `release.yml` vía el secret `SERVICE_ACCOUNT_JSON` — ver sección 7, no es de solo lectura).

**No se ha encontrado documentación de cómo obtener o configurar un service account de solo lectura para consultas administrativas** (nombre del service account, ruta del JSON, rol de IAM concreto, o proceso de alta). Los documentos anteriores confirman que ese tipo de consulta se ha hecho puntualmente en revisiones, pero no documentan el procedimiento para un desarrollador nuevo. **Confirmar con el dueño del proyecto** si existe un service account de solo lectura reutilizable o si cada consulta administrativa se resuelve caso por caso con credenciales del propietario.

---

## 7. Pipeline de release

Definido en `.github/workflows/release.yml` ("Release — Build & Deploy to Play Store").

**Disparadores:**
- Push de un tag semántico `vX.Y.Z` (regex `v[0-9]+.[0-9]+.[0-9]+`, sin sufijos tipo `-alpha`).
- `workflow_dispatch` manual desde la UI de GitHub Actions, con inputs `track` (`internal` / `alpha` / `production`, default `internal`) y `version_name` opcional.

**Pasos principales del job `build-and-publish`:**
1. Checkout.
2. Setup JDK 21 (Temurin) y Android SDK (`platforms;android-36 build-tools;36.0.0`).
3. Setup Gradle.
4. Decodifica el keystore de subida desde el secret `UPLOAD_KEYSTORE` (base64) a `composeApp/upload-keystore.jks`.
5. Resuelve la versión: usa `version_name` del input si existe, o el tag `vX.Y.Z`; si no hay ninguno, falla explícitamente. `versionCode` se calcula como `date +%s` (epoch) — es decir, **crece siempre**, no coincide con el `versionCode` de `build.gradle.kts`.
6. Genera notas de versión mínimas (`distribution/whatsnew/whatsnew-es-ES` y `whatsnew-en-US`).
7. Compila el AAB firmado: `./gradlew :composeApp:bundleRelease` con las credenciales de firma inyectadas por propiedades (`-Pandroid.injected.signing.*`) y el override de versión (`-PversionNameOverride`, `-PversionCodeOverride`).
8. Determina el track: el del input si viene de `workflow_dispatch`, o `internal` si el disparador fue un push de tag (un push de tag **nunca** publica directo a `production`).
9. **Sube a Play Store** con la acción `r0adkll/upload-google-play@v1`, usando el secret `SERVICE_ACCOUNT_JSON` (service account de publicación, no el de solo lectura de la sección 6), `status: completed` (publica de inmediato, no como borrador).
10. Crea una GitHub Release con notas y enlace a la ficha de Play Store.
11. Sube el AAB como artifact de GitHub Actions (retención 30 días).
12. Limpieza: borra el keystore descifrado del runner (`if: always()`).

**Verificación post-release del paso "Upload to Play Store":**

No se ha encontrado en `release.yml` ni en el resto del repo un mensaje literal tipo "Successfully committed" emitido por el propio workflow: ese texto no aparece en ningún `run:`/`with:` del step. El step usa la acción de terceros `r0adkll/upload-google-play@v1`, cuyo log de salida (visible en la pestaña **Actions → run → job "Build AAB & Publish to Play Store" → step "Upload to Play Store"** de GitHub) es el que hay que revisar para confirmar el resultado real de la subida a Play Console — el mensaje de éxito concreto depende de la versión de esa acción/API de Google Play y no está documentado en este repo. Para verificar que la publicación funcionó: (a) revisar que el step termine en verde sin excepción de la API de Android Publisher, y (b) confirmar en Google Play Console → el track correspondiente (`internal`/`alpha`/`production`) que aparece la nueva versión. **Confirmar con el dueño del proyecto** cuál es el texto exacto que suele ver en un run exitoso, si quiere que quede documentado literalmente.

---

## Ver también

- `docs/ARQUITECTURA.md` — arquitectura de la app (aún en elaboración por otro compañero).
- `docs/MODELO-DATOS.md` — modelo de datos de Firestore (aún en elaboración).
- `docs/FLUJOS-PRINCIPALES.md` — flujos principales de producto (aún en elaboración).
- `docs/INDICE.md` — índice general de la documentación (aún en elaboración).
- `docs/guia-publicacion.md` — checklist completo de publicación en Google Play (Data Safety, AdMob, IARC, etc.).
- `CLAUDE.md` — memoria rápida del proyecto (stack, estructura, convenciones); ver nota de la sección 1 sobre el `targetSdk` desactualizado ahí.
