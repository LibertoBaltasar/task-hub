# iOS compilable y usable en local: Google Sign-In, share nativo, build Xcode fiable — 2026-09-18

## 0. Aviso importante

**Este entorno es Linux sin Xcode.** El target iOS (`iosMain`, el proyecto
`iosApp/iosApp.xcodeproj`) NO se puede compilar aquí. Todo lo de este informe
que dependa de Xcode/Kotlin-Native-para-iOS (puntos 3 y 4 de "Verificación
obligatoria") queda **sin verificar hasta el primer build en un Mac** — se
señaliza explícitamente en cada sección.

## 1. Qué estaba bloqueado y qué se decidió

`launchGoogleSignIn()` en iOS era un no-op (`GoogleSignInResultHolder.setResult("")`).
Con la auth Google-only (`docs/google-only-auth-2026-09-12.md`), sin login no
hay forma de pasar el gate de `App.kt` (`AuthGateScreen`) — la app iOS se
quedaba permanentemente bloqueada en esa pantalla. Se implementó el login
real, sin añadir ningún SDK (ni `GIDSignIn`, ni Firebase SDK, ni CocoaPods/SPM):

- **Flujo elegido**: abrir Safari con la URL de autorización OAuth 2.0 de
  Google pidiendo un `id_token` directamente (implicit flow,
  `response_type=id_token`), usando el client ID de tipo **iOS**
  (`GOOGLE_IOS_CLIENT_ID`, generado automáticamente por Firebase al registrar
  la app `org.taskhub`, appId `1:278503294422:ios:8e84f3b201fb3bdcc79077`).
- El callback vuelve a la app por URL scheme (el "reversed client ID",
  `com.googleusercontent.apps.278503294422-3f8u3f61l3cii42tfn6if5qiq7u7sbem`),
  registrado en `Info.plist` (`CFBundleURLTypes`).
- Swift (`ContentView.swift`, `onOpenURL`) parsea el fragmento de la URL de
  callback, extrae `id_token` y lo publica en `GoogleSignInResultHolder`
  (mismo contrato multiplataforma que Android/web: token no vacío = éxito,
  `""` = cancelado/error, `null` = en curso — no se ha tocado ese contrato).
- Kotlin (`launchGoogleSignIn()` en `Platform.ios.kt`) solo construye la URL y
  la abre; no resuelve el resultado él mismo salvo que Safari ni siquiera
  pueda abrirse (ahí sí publica `""` para no dejar el flujo colgado en
  `SigningIn`).

Se comparte también el share nativo (`shareText`, antes no-op) vía
`UIActivityViewController`, y se corrigen dos problemas del proyecto Xcode
generado en el encargo anterior que iban a bloquear el primer build real en
Mac (sandboxing de scripts + `env -i` en el script de compilación de Kotlin).

## 2. Archivos tocados/creados

| Archivo | Cambio |
|---|---|
| `composeApp/src/commonMain/kotlin/org/taskhub/platform/GoogleOAuthConfig.kt` | + `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_IOS_REVERSED_CLIENT_ID`; KDoc de `GOOGLE_WEB_CLIENT_ID` matizado (no se usa en iOS) |
| `composeApp/src/iosMain/kotlin/org/taskhub/platform/Platform.ios.kt` | `launchGoogleSignIn()` real (antes no-op); `shareText()` real con `UIActivityViewController` (antes no-op); KDoc de `hasCalendarSupport`/`getGoogleCalendarAccessToken`/`revokeGoogleCalendarAccess` actualizado (ya no cita "Sign-In no implementado" como motivo) |
| `iosApp/iosApp/ContentView.swift` | `.onOpenURL` — parsea el callback OAuth y publica el resultado en `GoogleSignInResultHolder` |
| `iosApp/iosApp/Info.plist` | **Nuevo.** `CFBundleURLTypes` con el scheme del reversed client ID iOS |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | `INFOPLIST_FILE` (Debug+Release del target); `ENABLE_USER_SCRIPT_SANDBOXING = NO` (Debug+Release del proyecto); script "Compile Kotlin Framework" sin `env -i`, con fallback de `JAVA_HOME` |
| `docs/ios-instalacion-local-2026-09-18.md` | Nuevo — este informe |
| `docs/PRIMEROS-PASOS.md` | Sección iOS: referencia a este informe en vez de "pendiente" |

**No tocado** (fuera de alcance, según reglas estrictas del encargo):
`androidMain`, `wasmJsMain`, `jvmMain`, lógica de negocio en `commonMain`,
`BUNDLE_ID`, calendar/notificaciones/ads/analytics en iOS, sin dependencias
nuevas (ni pods, ni SPM, ni SDKs).

## 3. Detalle de la implementación

### 3.1 `GoogleOAuthConfig.kt`

```kotlin
const val GOOGLE_IOS_CLIENT_ID = "278503294422-3f8u3f61l3cii42tfn6if5qiq7u7sbem.apps.googleusercontent.com"
const val GOOGLE_IOS_REVERSED_CLIENT_ID = "com.googleusercontent.apps.278503294422-3f8u3f61l3cii42tfn6if5qiq7u7sbem"
```

El KDoc de `GOOGLE_WEB_CLIENT_ID` se matizó: sigue siendo el que usan
Android/web, pero en iOS el token se pide con audience = client ID iOS (es el
flujo estándar de Firebase Auth para iOS; `signInWithIdp` lo acepta igual).

### 3.2 `launchGoogleSignIn()` (iOS)

Construye la URL de autorización con un nonce de 32 hex chars (usando el
`secureRandomInt` CSPRNG ya existente en el mismo archivo) y la abre con la
variante síncrona de `UIApplication.openURL(url:)`. Se eligió esta variante
—deprecada desde iOS 10, pero soportada— en vez de la moderna con
`options`/`completionHandler`, porque su firma de interop con Kotlin/Native
no se ha podido validar en este entorno (sin klibs de iOS localmente, ver
§4). El valor de retorno (`Bool`) indica si Safari pudo abrirse, no si el
login tuvo éxito; si es `false`, se publica `GoogleSignInResultHolder.setResult("")`
para no dejar el flujo colgado.

**No se llama a `setResult` en el caso de éxito de apertura** — ese resultado
llega de forma asíncrona por `onOpenURL` en Swift (§3.3).

### 3.3 `ContentView.swift` — `onOpenURL`

```swift
.onOpenURL { url in
    guard url.scheme == GoogleOAuthConfigKt.GOOGLE_IOS_REVERSED_CLIENT_ID else { return }
    let idToken = extractIdToken(from: url)
    GoogleSignInResultHolder.shared.setResult(token: idToken ?? "")
}
```

Se referencia la constante Kotlin compartida (`GoogleOAuthConfigKt.GOOGLE_IOS_REVERSED_CLIENT_ID`)
en vez de duplicar el literal en Swift — una única fuente de verdad. `extractIdToken`
separa el fragmento (`url.fragment`) por `&`, busca `id_token=` y hace
percent-decode.

**Convención de exportación Kotlin/Native asumida, a validar en el primer
build real:** un `object` de Kotlin (`GoogleSignInResultHolder`) se expone a
Swift como una clase con singleton `.shared`, y `fun setResult(token: String?)`
se mapea a `setResult(token:)`. Un fichero Kotlin `GoogleOAuthConfig.kt` con
`const val` a nivel de archivo se expone como clase estática `GoogleOAuthConfigKt`
con esos valores como propiedades. Ambas son las convenciones documentadas de
interop Kotlin/Native↔Objective-C/Swift, pero no se han podido comprobar
contra los klibs reales (`~/.konan/.../klib/platform/ios_*` no existe en este
entorno — ver comando de comprobación en §4). Si el nombre real difiere, el
build de Xcode lo señalará como error de símbolo no encontrado, con mensaje
claro sobre qué renombrar.

### 3.4 `shareText()` (iOS)

Implementado con `UIActivityViewController`: resuelve el `UIViewController`
visible más arriba recorriendo `presentedViewController` desde
`keyWindow?.rootViewController`, castea el `String` de Kotlin a `NSString`
(toll-free bridging documentado de Kotlin/Native, válido porque `activityItems`
espera un `List<Any>` de tipos Objective-C) y lo presenta. En iPad se ancla el
popover a `sourceView`/`sourceRect` del view raíz (sin esto, `UIActivityViewController`
crashea en iPad al no tener ancla). El parámetro `title` se ignora — la API
de iOS no tiene un campo equivalente a "asunto" del `Intent` de Android
(mismo comportamiento ya existente en wasmJs).

### 3.5 `Info.plist` + `project.pbxproj`

`Info.plist` nuevo con `CFBundleURLTypes` (un único scheme: el reversed
client ID iOS). `GENERATE_INFOPLIST_FILE = YES` sigue activo en el target —
Xcode fusiona el Info.plist generado con este archivo declarado vía
`INFOPLIST_FILE`; no hace falta duplicar las claves que Xcode ya genera
(`CFBundleDisplayName`, etc.).

### 3.6 Fixes al build de Xcode (críticos para el primer build en Mac)

Dos problemas detectados en el host app creado en el encargo anterior
(`00-ios-xcode-host-app`), ambos con alta probabilidad de romper el primer
build real:

1. **`ENABLE_USER_SCRIPT_SANDBOXING = YES`** (proyecto, Debug+Release): el
   sandbox de scripts de usuario de Xcode bloquea escrituras de Gradle fuera
   de `$DERIVED_FILE_DIR`, que es justo lo que hace
   `embedAndSignAppleFrameworkForXcode` al generar/copiar/firmar el framework
   `ComposeApp`. Cambiado a `NO`.
2. **`env -i PATH="$PATH" HOME="$HOME"` en el script "Compile Kotlin
   Framework"**: la intención original era aislar el entorno de Gradle de
   variables de Xcode que pudieran chocar con la resolución de
   toolchain/JDK, pero `env -i` limpia *todas* las variables no listadas
   explícitamente — incluidas las que Xcode exporta para que el script sepa
   qué construir y dónde (`CONFIGURATION`, `SDK_NAME`, `ARCHS`,
   `TARGET_BUILD_DIR`, `FRAMEWORKS_FOLDER_PATH`, etc.), que
   `embedAndSignAppleFrameworkForXcode` necesita. Sustituido por la forma
   canónica (sin `env -i`), con un fallback de `JAVA_HOME` vía
   `/usr/libexec/java_home` para Macs sin Java en el `PATH` del shell no
   interactivo que usa Xcode para scripts de build:

   ```sh
   cd "$SRCROOT/.."
   if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
     export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
   fi
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```

## 4. Verificación

**1. `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`**
→ `BUILD SUCCESSFUL in 29s` (solo warnings preexistentes de deprecación
`GoogleSignIn`/`EncryptedSharedPreferences`/etc. y `when` exhaustivo
redundante en `CalendarScreen.kt`/`TaskListScreen.kt` — ninguno nuevo).
Valida que `GoogleOAuthConfig.kt` compila en `commonMain` y que
`androidMain` no se rompió.

**2. `./gradlew :composeApp:jvmTest --rerun-tasks --console=plain` +
`parse-jvm-test-results.py`**
→ `BUILD SUCCESSFUL in 19s`; parser sobre los XML reales:
`TOTAL: 269 tests, 0 failures, 0 errors`.

**3. Revisión manual de `project.pbxproj`** (no hay `plutil` en Linux; se
verificó balance de llaves/paréntesis respetando strings con un script
Python ad-hoc, y `grep` de las claves añadidas):
- Balance de `{}`/`()` correcto teniendo en cuenta comillas escapadas dentro
  de `shellScript`.
- `INFOPLIST_FILE = "iosApp/Info.plist";` presente exactamente 2 veces
  (Debug y Release del target `iosApp`).
- `ENABLE_USER_SCRIPT_SANDBOXING = NO;` presente exactamente 2 veces (Debug
  y Release del **proyecto**, no del target — el target no tenía esa clave).
- `env -i` ya NO aparece como código activo del script (solo dentro del
  comentario que explica por qué se quitó).

**4. Comprobación de klibs locales de Kotlin/Native para iOS** —
`find ~/.konan -ipath "*platform/ios_*"` → **sin resultados**: este entorno
no tiene el toolchain de Kotlin/Native para iOS descargado, así que no se
pudieron grep-ear las firmas reales de `UIApplication.openURL`,
`UIActivityViewController`, etc. contra los klibs. Se usaron las firmas
documentadas de Kotlin/Native↔UIKit (ver comentarios inline en
`Platform.ios.kt` marcando qué puntos son sensibles a validar).

**Lo que queda validado SOLO en el Mac** (no se puede comprobar aquí):
- Que el proyecto Xcode compila y enlaza (`iosX64`/`iosSimulatorArm64`/
  `iosArm64`) con los cambios de `project.pbxproj`.
- Que `UIApplication.sharedApplication.openURL(url)` compila con la firma
  asumida (deprecada, un solo argumento) — la alternativa moderna con
  `completionHandler` podría requerir ajuste si Xcode se queja de la firma
  vieja como no disponible en el SDK usado.
- Que `activityItems = listOf(text as NSString)` compila (el cast `as
  NSString` sobre un `String` de Kotlin).
- Que `GoogleOAuthConfigKt.GOOGLE_IOS_REVERSED_CLIENT_ID` y
  `GoogleSignInResultHolder.shared.setResult(token:)` son los nombres
  exportados reales a Swift (convención asumida, ver §3.3).
- El flujo de login end-to-end: abrir Safari, elegir cuenta, volver a la app
  por el scheme, y que `signInWithIdp` acepte el `id_token` con audience =
  client ID iOS (documentado como flujo estándar de Firebase Auth para iOS,
  pero no probado contra el backend real de `task-hub-62f98` desde este
  entorno).
- El share sheet en dispositivo/simulador real (incluida la rama iPad del
  popover).

## 5. Plan B si `signInWithIdp` rechaza el token del client iOS

Documentado pero **NO implementado** (fuera de alcance de este encargo): si
en pruebas reales Firebase Auth devuelve `INVALID_IDP_RESPONSE` o "respuesta
de Firebase Auth incompleta" para el token del client ID iOS, la alternativa
es añadir el SDK `GIDSignIn` (Google Sign-In para iOS) inicializado con
`serverClientID = GOOGLE_WEB_CLIENT_ID` (no el client iOS), que es el patrón
recomendado por Google/Firebase cuando el backend valida contra un client
Web. Requeriría añadir la dependencia vía SPM — deliberadamente no se ha
hecho aquí para no introducir dependencias nuevas sin necesidad confirmada.

## 6. Guía de instalación local (sideload, sin canal de distribución)

### Requisitos

- Mac con **Xcode 16.x** (16.0–16.3 recomendado; versiones más nuevas pueden
  dar un warning de compatibilidad de Kotlin/Native — ver troubleshooting).
- **JDK 21**: `brew install --cask temurin@21` (o cualquier JDK 21; el script
  de build ya intenta `/usr/libexec/java_home` como fallback si `JAVA_HOME`
  no está seteado).
- Repo: `git clone https://github.com/LibertoBaltasar/task-hub`.
- **NO hace falta** CocoaPods, SPM, ni `GoogleService-Info.plist` (la auth va
  por REST, `network/FirestoreClient.kt` — no hay SDK de Firebase).
- El primer build descarga ~4–6 GB (toolchain de Kotlin/Native + dependencias
  de Gradle) — puede tardar varios minutos según la conexión.

### Pasos

1. `open iosApp/iosApp.xcodeproj` (o doble clic).
2. Configura el equipo de firma: edita `iosApp/Configuration/Config.xcconfig`
   → `TEAM_ID=<tu Team ID de 10 caracteres>`, o en Xcode → target `iosApp` →
   **Signing & Capabilities** → Team. Un Apple ID gratuito sirve (certificado
   de desarrollo válido 7 días).
3. Selecciona destino: un simulador (p. ej. "iPhone 16") o un dispositivo
   físico conectado.
4. Pulsa ▶️ (Run). El build phase "Compile Kotlin Framework" invoca
   `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` automáticamente
   — no hace falta ejecutar Gradle a mano.
5. **Solo en dispositivo físico** (no en simulador), la primera vez: Ajustes
   → General → VPN y gestión de dispositivos → confiar en el certificado de
   desarrollador.

### Avisos

- **NO cambiar `BUNDLE_ID`** (`org.taskhub`, en `Config.xcconfig`) — el OAuth
  de Google (client ID iOS, URL scheme) está ligado a ese bundle ID exacto.
  Cambiarlo rompe el login.
- El icono de la app es un placeholder vacío (aceptable, ya documentado en el
  encargo anterior).
- **Los datos van a la base de datos de producción** (`task-hub-62f98`) — no
  hay entorno de staging. Las pruebas manuales generan datos reales
  (hogares, tareas, etc.).

### Qué probar

Login/logout con Google, crear hogar, unirse por código de invitación,
invitar (botón compartir → share sheet nativo), tareas (crear/completar/
puntos), recompensas, ranking/calendario/estadísticas, perfil, cambio de
tema/idioma.

### Troubleshooting

| Síntoma | Causa / solución |
|---|---|
| `Unable to locate a Java Runtime` al hacer Run | Falta JDK 21 o `JAVA_HOME` no resuelve. Instalar `brew install --cask temurin@21`; el script ya intenta `/usr/libexec/java_home` como fallback. |
| Error de sandbox / "Sandbox: bash deny(1) file-write" durante "Compile Kotlin Framework" | Ya corregido en este encargo (`ENABLE_USER_SCRIPT_SANDBOXING = NO`). Si reaparece, comprobar que el pbxproj tiene ese valor en las 2 configs de **proyecto** (no target). |
| El link falla con un mensaje sobre versión de Xcode / Kotlin-Native incompatible | Xcode más nuevo que el validado contra Kotlin 2.1.21/CMP 1.8.0. Reportar el error exacto de `xcodebuild` — puede necesitar actualizar el plugin de Kotlin Multiplatform o fijar una versión de Xcode 16.0–16.3. |
| El login no vuelve a la app tras elegir cuenta en Safari | Revisar que `Info.plist` tiene el `CFBundleURLTypes` con el scheme correcto (`com.googleusercontent.apps.278503294422-...`), y que Safari no bloqueó la apertura (Safari > Preferencias > evitar bloqueadores de pop-ups agresivos). |
| Tras el login, error "respuesta de Firebase Auth incompleta" / `INVALID_IDP_RESPONSE` | El backend rechaza el token del client iOS. Ver plan B documentado en §5 (`GIDSignIn` con `serverClientID` = client Web) — **no implementado todavía**, requeriría una dependencia SPM nueva. |
| Error de compilación en `iosMain` (Kotlin) | Pegar el error completo de Xcode/Gradle — probablemente una firma de interop con UIKit/Foundation distinta a la asumida en `Platform.ios.kt` (ver marcas en el código y §4 de este informe sobre qué no se pudo validar aquí). |

## Commit

```
4d3b1bd feat: iOS — Google Sign-In, share nativo y build Xcode fiable (instalación local)
```
