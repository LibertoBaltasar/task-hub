# Arquitectura de Task Hub

Este documento explica cómo está construido Task Hub para que alguien sin
contexto previo pueda entender el proyecto leyendo solo este archivo (más los
enlaces de la sección "Ver también"). Todo lo descrito aquí está verificado
contra el código real en `composeApp/src/`.

## 1. Visión de conjunto

Task Hub es una app de gestión de tareas del hogar (puntos + recompensas)
construida con **Compose Multiplatform** (Kotlin 2.1, CMP 1.7.3) sobre tres
plataformas desde una única base de código Kotlin:

- **Android** (minSdk 26, compileSdk/targetSdk 36 — ver `composeApp/build.gradle.kts`).
- **iOS** (vía Kotlin/Native + `ComposeUIViewController`).
- **JVM desktop** (vía `androidx.compose.ui.window.application`).

Cada plataforma tiene un punto de entrada mínimo que monta el mismo
composable raíz `App()`:

- Android: `composeApp/src/androidMain/kotlin/org/taskhub/MainActivity.kt` (`setContent { ... App(...) }`, con manejo de deep links de notificaciones).
- iOS: `composeApp/src/iosMain/kotlin/org/taskhub/MainViewController.kt`.
- JVM: `composeApp/src/jvmMain/kotlin/org/taskhub/Main.kt`.

### Por qué cada pieza del stack

| Pieza | Por qué |
|---|---|
| **Compose Multiplatform** | Una sola UI declarativa en Kotlin para Android/iOS/desktop, sin reescribir pantallas por plataforma. Evita mantener 2-3 UIs nativas para una app de este tamaño (equipo pequeño). |
| **Kotlin 2.1 / CMP 1.7.3** | Versión estable con soporte iOS maduro en el momento de arrancar el proyecto. |
| **Voyager** | Navegación multiplatform ligera (no depende de Android Navigation Component, que es solo-Android) con integración directa con Koin (`voyager-koin`) para inyectar `ScreenModel`s. |
| **Koin** | DI multiplatform sin generación de código (a diferencia de Hilt/Dagger, que son solo-Android/JVM). `koinInject`/`koinScreenModel` funcionan igual en las tres plataformas. |
| **multiplatform-settings** | Envoltorio común sobre `SharedPreferences` (Android), `NSUserDefaults` (iOS) y `java.util.prefs.Preferences` (JVM) — persistencia simple sin reimplementar tres backends. |
| **Ktor + REST de Firestore, NO el SDK de Firestore** | El SDK oficial de Firestore no tiene soporte first-class para Kotlin/Native (iOS) ni JVM desktop; es esencialmente solo-Android. Hablar directamente con la API REST de Firestore vía Ktor (que sí es multiplatform) permite compartir el 100% del código de red en `commonMain`, al coste de reimplementar auth/parseo de documentos a mano (ver `network/FirestoreClient.kt`). |
| **Sin backend/Cloud Functions propio** | Toda la lógica de negocio (cálculo de puntos, penalizaciones, recurrencia, validación de escrituras) vive en el cliente (`network/*Rules.kt`) y en `firestore.rules` (reglas de seguridad declarativas del lado de Firestore). No hay servidor intermedio que mantener/desplegar — a cambio, ciertas validaciones (p. ej. que `points` en `taskHistory` refleje realmente el cálculo correcto de una tarea) no pueden garantizarse del todo sin un backend; ver el comentario "limitación arquitectónica" en `firestore.rules`. |

Dependencias clave y sus versiones están fijadas en `gradle/libs.versions.toml`
(`ktor = 3.0.3`, `koin = 4.0.2`, `voyager = 1.1.0-beta03`,
`multiplatform-settings = 1.2.0`).

## 2. Estructura de directorios

Código común (compartido por las 3 plataformas):
`composeApp/src/commonMain/kotlin/org/taskhub/`

| Paquete | Responsabilidad |
|---|---|
| `App.kt` (raíz del paquete) | Composable raíz `App()`: monta `KoinApplication`, resuelve el splash, el tema, el idioma, arranca el `Navigator` de Voyager y resuelve deep links de notificaciones. Es el único sitio donde se decide la pila de pantallas inicial. |
| `di/` | `AppModule.kt` — único módulo de Koin de la app; declara todos los `single`/`factory`. |
| `ui/screens/` | Cada pantalla de la app como una clase `Screen` de Voyager (`HomeScreen`, `HouseholdScreen`, `TaskDetailScreen`, `CreateTaskScreen`, etc.). Contienen composables y la lógica de presentación (filtros, agrupación) que no necesita sobrevivir a recomposición/rotación. |
| `ui/components/` | Composables reutilizables entre pantallas: diálogos (`HouseholdDialogs.kt`, `DestructiveConfirmDialog.kt`), badges (`PointsBadge.kt`), avatares (`UserAvatar.kt`), ajustes (`SettingsSheet.kt`, `AppSettings.kt`), estados vacíos, shimmer, etc. |
| `ui/models/` | Los `ScreenModel` de Voyager (equivalentes a ViewModel): `TaskScreenModel`, `HouseholdScreenModel`, `HomeScreenModel`, `MemberScreenModel`, `ProfileScreenModel`, `NotificationScreenModel`, `StatsScreenModel`, `TaskCommentsScreenModel`. También managers de orquestación que no son ScreenModel: `GoogleAuthManager`, `CalendarSyncManager`. Exponen `StateFlow` que las `Screen` observan. |
| `ui/theme/` | `Theme.kt` — paleta de colores (`Teal*`, `Coral*`, `Sand*`, además de una paleta "Naturaleza"), `TaskHubThemeType` (`DEFAULT`/`NATURALEZA`/`MINIMAL`) y el composable `TaskHubTheme`. |
| `ui/i18n/` | `AppStrings.kt` (mapa de traducciones ES/EN) y `NotificationText.kt` (textos de notificaciones). |
| `network/` | Toda la comunicación con Firestore: `FirestoreClient` (transporte HTTP + auth de bajo nivel), `FirestoreRepository` (fachada de dominio) y repos especializados (`TaskRepository`, `HouseholdRepository`, `MemberRepository`, `RewardsRepository`, `NotificationRepository`, `GoogleCalendarRepository`), más las reglas de negocio puras (`PointsRules.kt`, `PenaltyRules.kt`, `RecurrenceRules.kt`, `HouseholdRules.kt`, `AssignmentCompletionRules.kt`) y los DTOs (`FirestoreDtos.kt`, `models/DTOs.kt`). |
| `platform/` | Declaraciones `expect` que abstraen capacidades nativas (analytics, anuncios, háptica, notificaciones locales, compartir, Google Sign-In, RNG seguro) — implementadas por plataforma en `androidMain`/`iosMain`/`jvmMain`. |
| `storage/` | Persistencia local: `SettingsStore` (preferencias de usuario), `HouseholdStore` (hogares guardados localmente), `TaskCache` (cache offline de tareas/hogar/miembros), `SecureStore` (interfaz `expect`/`actual` para credenciales cifradas). |

Código específico de plataforma (mismo paquete `org.taskhub`, distinto
source set): `composeApp/src/androidMain/`, `composeApp/src/iosMain/`,
`composeApp/src/jvmMain/` — cada uno con sus propios `platform/*.android.kt`,
`*.ios.kt`, `*.jvm.kt` que implementan los `expect` de `commonMain`, además de
código exclusivo de esa plataforma (p. ej. `MainActivity.kt`,
`TaskHubFirebaseMessagingService.kt`, `TaskHubWidgetProvider.kt`,
`GoogleSignInHelper.kt` solo existen en `androidMain`).

## 3. Flujo de datos

Patrón unidireccional clásico: la `Screen` solo observa estado y despacha
eventos; toda la lógica de negocio vive en el `ScreenModel`; toda la
comunicación de red vive en `network/`.

```
┌──────────────────────────────────────────────────────────┐
│  UI — Voyager Screen (ui/screens/*.kt)                    │
│  Ej: TaskDetailScreen, HouseholdScreen                     │
│  → koinScreenModel<XScreenModel>()                         │
│  → observa StateFlow del ScreenModel (collectAsState)      │
│  → llama funciones del ScreenModel ante eventos de usuario  │
└───────────────────────┬─────────────────────────────────────┘
                         │ llamadas suspend / lectura de StateFlow
┌───────────────────────▼─────────────────────────────────────┐
│  ScreenModel (ui/models/*.kt) — factory de Koin              │
│  Ej: TaskScreenModel, HouseholdScreenModel                    │
│  → screenModelScope.launch { ... }                            │
│  → orquesta: valida entrada, aplica reglas puras               │
│    (PointsRules/PenaltyRules/RecurrenceRules), llama al repo,  │
│    actualiza MutableStateFlow, dispara side-effects            │
│    (vibrate(), logAnalyticsEvent(), notificationScheduler)     │
└───────────────────────┬─────────────────────────────────────┘
                         │ suspend fun (Ktor HttpClient)
┌───────────────────────▼─────────────────────────────────────┐
│  Repositorio (network/*.kt) — single de Koin                  │
│  FirestoreRepository (fachada) delega en repos de dominio:      │
│  TaskRepository, HouseholdRepository, MemberRepository,         │
│  RewardsRepository, NotificationRepository                       │
│  → usa FirestoreClient para auth (Bearer token) + HTTP           │
│  → cachea en TaskCache tras cada lectura exitosa (cache-first)   │
└───────────────────────┬─────────────────────────────────────┘
                         │ HTTPS (Ktor HttpClient)
┌───────────────────────▼─────────────────────────────────────┐
│  Firestore REST API                                             │
│  https://firestore.googleapis.com/v1/projects/{id}/databases/    │
│  (default)/documents/...                                         │
│  → documentos planos: households/{id}/tasks/{id}/assignments/{id} │
│  → firestore.rules valida cada escritura del lado del servidor    │
└───────────────────────────────────────────────────────────────┘
```

Puntos relevantes de este flujo, verificados en el código:

- **Auth transparente**: `FirestoreClient.ensureAuth()` (en
  `network/FirestoreClient.kt`) resuelve automáticamente la sesión (Google si
  hay refresh token guardado, si no anónima) antes de cualquier llamada,
  protegido por un `Mutex` para no disparar altas/refrescos concurrentes.
- **Cache-first offline**: `TaskCache` (en `storage/TaskCache.kt`) guarda la
  última respuesta buena de tareas/hogar/miembros; si Firestore no responde,
  los repos de dominio caen a la cache en vez de fallar.
- **Reglas de negocio puras y testables**: cálculo de puntos, penalizaciones
  y próximas ocurrencias de tareas recurrentes viven en archivos sin I/O
  (`network/PointsRules.kt`, `PenaltyRules.kt`, `RecurrenceRules.kt`,
  `HouseholdRules.kt`), separados de los repos que sí hacen peticiones HTTP.
- **Paginación**: `listAllDocuments()` (en `FirestoreClient.kt`) recorre
  colecciones con `pageToken` en vez de asumir que una sola petición trae
  todos los documentos.
- El propio código deja un diagrama equivalente y más detallado del modelo de
  datos de Firestore en el KDoc de cabecera de
  `ui/models/TaskScreenModel.kt` — consultar también
  `docs/MODELO-DATOS.md` (ver "Ver también").

## 4. DI con Koin

Todo el grafo de dependencias se declara en un único módulo:
`composeApp/src/commonMain/kotlin/org/taskhub/di/AppModule.kt` (`val appModule: Module = module { ... }`).

Se instala en `App.kt` con `KoinApplication(application = { modules(appModule) })`,
que envuelve toda la UI (incluido el splash, para poder leer el idioma
guardado antes de la primera pantalla real).

### Qué está registrado

**`single` (una única instancia, vida de proceso)** — todo lo que representa
estado compartido o un recurso caro de crear:

- `Settings()` — el backend de multiplatform-settings.
- `HouseholdStore`, `TaskCache`, `SecureStore` (vía `createSecureStore()`), `SettingsStore` — persistencia local. `SettingsStore` recibe `SecureStore` como `Lazy` para no tocar Keystore/Keychain hasta el primer acceso real a un token.
- `FirestoreClient` — cliente HTTP + estado de auth (token en memoria); debe ser único porque el token y el mutex de auth son compartidos por todas las peticiones.
- Los repos de dominio (`NotificationRepository`, `RewardsRepository`, `TaskRepository`, `MemberRepository`, `HouseholdRepository`) y la fachada `FirestoreRepository` — se inyectan unos a otros (p. ej. `HouseholdRepository` recibe `MemberRepository` y `NotificationRepository`) para poder orquestar operaciones cruzadas sin duplicar lógica.
- `GoogleCalendarRepository`, `GoogleAuthManager` (con `onClose { it?.close() }` para cancelar su `CoroutineScope` si Koin cerrase el contenedor), `CalendarSyncManager`.
- `createNotificationScheduler()` y `createAdController()` — resuelven la implementación `actual` de la plataforma en tiempo de ejecución.

**`factory` (una instancia nueva por inyección)** — todos los `ScreenModel`:
`HomeScreenModel`, `HouseholdScreenModel`, `MemberScreenModel`,
`ProfileScreenModel`, `NotificationScreenModel`, `TaskScreenModel`,
`TaskCommentsScreenModel`, `StatsScreenModel`. Van como `factory` (no
`single`) porque cada `Screen` de Voyager debe obtener su propia instancia
ligada a su propio ciclo de vida, no compartir una global.

### Cómo se inyecta

- Dentro de un `@Composable` que no es una `Screen` (p. ej. `App()`):
  `koinInject<FirestoreRepository>()`.
- Dentro de una `Screen` de Voyager, para obtener su `ScreenModel` (con el
  ciclo de vida correcto — sobrevive a recomposición, se destruye al salir de
  la pantalla): `koinScreenModel<TaskScreenModel>()` (de `cafe.adriel.voyager.koin`),
  usado en `ui/screens/TaskDetailScreen.kt`, `HouseholdScreen.kt`,
  `ExploreScreen.kt`, etc.
- Dentro de módulos/clases plano-Kotlin (repos, managers): inyección por
  constructor, resuelta con `get()` dentro del bloque `module { }`.

## 5. Navegación con Voyager

- Cada pantalla es una clase que implementa `cafe.adriel.voyager.core.screen.Screen`
  (paquete `ui/screens/`), con su `@Composable override fun Content()`.
- La raíz del árbol de navegación se crea en `App.kt`: tras resolver el
  splash y (en un `LaunchedEffect`) el hogar Personal del usuario y un
  eventual deep link de notificación, se construye la pila inicial:
  ```kotlin
  val screens = mutableListOf<Screen>(HomeScreen())
  // + HouseholdScreen(id) o TaskDetailScreen(id, taskId) si hay deep link
  Navigator(screens = screens) { navigator -> ... }
  ```
  Se usa la sobrecarga `Navigator(screens: List<Screen>, ...)` (no
  `Navigator(HomeScreen())` + `push` posterior) precisamente para que, en frío
  con deep link, la pantalla destino ya aparezca en el primer frame en vez de
  parpadear Home antes de la transición.
- Transición: `SlideTransition(navigator)` normalmente, o
  `FadeTransition(navigator)` si el sistema pide reducir movimiento
  (`shouldReduceMotion()`, un `expect`/`actual` en `ui/components/`).
- Navegación entre pantallas, desde dentro de cualquier `Screen`, vía el
  `Navigator` local (`LocalNavigator.currentOrThrow` es el patrón estándar de
  Voyager): `navigator.push(OtraScreen(args))`, `navigator.pop()`,
  `navigator.replaceAll(HomeScreen())` para volver a la raíz descartando toda
  la pila (p. ej. tras cerrar sesión o borrar un hogar).
- Deep links que llegan **después** de que la app ya esté viva (Android
  `onNewIntent`) se manejan con un `LaunchedEffect(navigator, deepLinkHouseholdId, deepLinkTaskId)`
  dentro del bloque del `Navigator`, que hace `push` normal (con transición) —
  distinto del deep link de arranque en frío, que ya viene incluido en la
  pila inicial para evitar un doble salto visual.

## 6. i18n

`ui/i18n/AppStrings.kt` es un objeto con un mapa de mapas:
`Map<idioma, Map<clave, texto>>`, con entradas para `"es"` y `"en"`. La API
pública es `AppStrings.get(key, lang)`, que se llama desde cualquier
composable o `ScreenModel` para resolver el texto visible al usuario en el
idioma activo.

- El idioma activo se guarda en `SettingsStore.getLanguage()` /
  `setLanguage()` (por defecto `"es"`) y se propaga por
  `CompositionLocalProvider(LocalAppSettings provides appSettings)` en
  `App.kt`, de modo que cambiarlo en Ajustes recompone toda la UI sin
  reiniciar la app.
- Las claves siguen una convención de prefijo por dominio/pantalla
  (`settings_*`, `household_list_*`, `calendar_*`, `theme_*`, `common_*`,
  etc.), agrupadas con comentarios de sección dentro del propio mapa.
- Algunas entradas usan placeholders `%s` (p. ej.
  `"settings_account_connected_prefix" to "✅ Conectado como %s"`),
  resueltos por el llamador con `String.format`/interpolación en el
  composable, no dentro de `AppStrings`.
- `ui/i18n/NotificationText.kt` es un módulo hermano específico para
  construir los textos (título/cuerpo) de las notificaciones push/locales,
  reutilizando el mismo mecanismo de idioma.

## 7. `expect`/`actual` por plataforma

`platform/` en `commonMain` declara contratos `expect` para todo lo que no
puede implementarse igual en Android/iOS/JVM. Cada plataforma provee su
`actual` en el source set correspondiente.

| Abstracción (`commonMain`) | Android (`androidMain`) | iOS (`iosMain`) | JVM (`jvmMain`) |
|---|---|---|---|
| `logAnalyticsEvent()` (`platform/Analytics.kt`) | Firebase Analytics real (`FirebaseAnalytics.getInstance(context).logEvent(...)`, `platform/Analytics.android.kt`) | No-op — analytics aún no integrado en iOS (`platform/Analytics.ios.kt`) | No-op (`platform/Analytics.jvm.kt`) |
| `AdController` / `createAdController()` (`platform/AdController.kt`) | `AdControllerImpl`: carga y muestra interstitial de AdMob con cooldown de 120s, señal de contenido para menores (`platform/AdController.android.kt`) | `NoOpAdController` | `NoOpAdController` |
| `vibrate(kind: HapticKind)` (`platform/Haptics.kt`) | Vibración nativa vía `Vibrator`/`VibratorManager` (`platform/Haptics.android.kt`) | Haptics de iOS (`platform/Haptics.ios.kt`) | No-op (`platform/Haptics.jvm.kt`, desktop no tiene motor háptico) |
| `NotificationScheduler` / `createNotificationScheduler()` (`platform/NotificationScheduler.kt`) | `AndroidNotificationScheduler`: agenda recordatorios con WorkManager y persiste el token FCM en `SharedPreferences` (`platform/NotificationScheduler.android.kt`) | Implementación iOS (`platform/NotificationScheduler.ios.kt`) | `NoOpNotificationScheduler` (`platform/NotificationScheduler.jvm.kt`) — desktop no genera notificaciones del sistema hoy |
| `SecureStore` / `createSecureStore()` (`storage/SecureStore.kt`) | `EncryptedSharedPreferences` (Jetpack Security, AES-256-GCM/SIV), con fallback a `Settings()` sin cifrar si el Keystore falla (`storage/SecureStore.android.kt`) | Keychain de iOS vía `SecItemAdd`/`SecItemCopyMatching` (`storage/SecureStore.ios.kt`) | AES-256-GCM con clave propia en `~/.taskhub/.taskhub_secure_key`, valor cifrado guardado en `java.util.prefs.Preferences` (`storage/SecureStore.jvm.kt`) |
| `secureRandomInt(bound)` (`platform/Platform.kt`) | CSPRNG nativo | CSPRNG nativo | CSPRNG nativo (`java.security.SecureRandom`) |
| `shareText()`, `saveWidgetThemeToCache()`, `updateWidgetPendingTasks()`, `launchGoogleSignIn()`, `getGoogleCalendarAccessToken()`, `revokeGoogleCalendarAccess()` (`platform/Platform.kt`) | Comparte nativo, widget de Android (`TaskHubWidgetProvider.kt`), Google Sign-In real (`GoogleSignInHelper.kt`) | Implementaciones iOS correspondientes | Sin equivalente pleno (desktop no tiene widget de home screen ni el mismo flujo de Google Sign-In) |

Este patrón permite que **todo** `ui/`, `network/` y la mayoría de `storage/`
vivan en `commonMain` sin `if (platform == ...)`: el código común solo llama
a la función `expect`, ajeno a qué `actual` se resuelve en tiempo de
compilación para cada target.

## 8. Decisiones de arquitectura y su porqué

- **REST de Firestore vía Ktor en vez del SDK oficial**: el SDK de Firestore
  no es multiplatform (no soporta Kotlin/Native ni JVM desktop de forma
  nativa); ir por REST permite compartir el 100% de la capa de red entre las
  tres plataformas a cambio de implementar a mano auth (`FirestoreClient.ensureAuth`),
  parseo de documentos (`FirestoreParsers.kt`) y paginación
  (`listAllDocuments`).
- **Sin backend/Cloud Functions propio**: menos infraestructura que operar;
  la validación de escrituras se hace declarativamente en `firestore.rules`
  (desplegadas vía `scripts/deploy_firestore_rules.py`, según el propio
  historial de comentarios del archivo). Limitación reconocida en el propio
  `firestore.rules`: no puede validarse en servidor que un `points`/`pointsSpent`
  concreto sea el resultado *correcto* del cálculo de una tarea (solo que no
  sea negativo o no exceda el coste real) — un backend cerraría ese hueco,
  pero no existe hoy.
- **multiplatform-settings para persistencia simple**: evita reimplementar
  `SharedPreferences`/`NSUserDefaults`/`Preferences` a mano; se usa para todo
  lo que no es sensible (tema, idioma, notificaciones, hogares guardados,
  cache de tareas). Lo sensible (refresh tokens) se separa a `SecureStore`
  (cifrado) precisamente para no ampliar la superficie de lo que hay que
  cifrar correctamente por plataforma.
- **Compose Multiplatform para las 3 plataformas**: con un equipo pequeño y
  una sola app de complejidad media, mantener una UI declarativa compartida
  es más barato que tres UIs nativas (Jetpack Compose visual solo-Android,
  SwiftUI, y algo para desktop). El coste es la abstracción `platform/` para
  lo que sí es genuinamente distinto por plataforma.
- **Auth anónima por defecto + Google Sign-In opcional**: permite usar la app
  sin fricción de registro desde el primer arranque (Firebase Anonymous
  Auth), con upgrade a cuenta de Google cuando el usuario quiere que sus
  datos sobrevivan a una reinstalación o se sincronicen entre dispositivos
  (`FirestoreRepository.signInWithGoogle`, `GoogleAuthManager`).

## Ver también

- `docs/MODELO-DATOS.md` — estructura de las colecciones/documentos de
  Firestore (households, tasks, assignments, members, taskHistory,
  rewards...) y los DTOs que los representan en Kotlin.
- `docs/FLUJOS-PRINCIPALES.md` — flujos de usuario de punta a punta (crear
  hogar, completar tarea, canjear recompensa, notificaciones...).
- `docs/PRIMEROS-PASOS.md` — cómo clonar, compilar y ejecutar el proyecto
  localmente en cada plataforma.
- `docs/INDICE.md` — índice general de la documentación del proyecto.
