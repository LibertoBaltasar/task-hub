# Panel de expertos v12 — 2026-09-17

**Encargo:** auditoría integral NUEVA (no continuación) de Task Hub, con panel
de 14 especialistas independientes en paralelo (estética, funcionalidad,
accesibilidad WCAG AA, UI/componentes, UX, programador senior, arquitectura,
QA/bugs, seguridad/AppSec, privacidad/RGPD, rendimiento, red/offline/sync,
cobertura de pruebas, web wasmJs — este último nuevo desde v11), lanzados en
4 oleadas de 4-5 agentes (límite estructural verificado en rondas v8/v9:
sesiones simultáneas por encima de ese número fallan por límite de sesión de
la API). Consolidación priorizada y aplicación de los fixes seguros.

**Nota de proceso:** el especialista de QA/bugs de la oleada 2 cayó por
límite de sesión de la API a mitad de tarea (mismo tipo de corte que en
rondas anteriores) — se relanzó en solitario justo después y completó sin
problema. El resto de los 14 informes se completaron sin cortes.

## Resumen por especialista

| # | Especialista | Hallazgos nuevos | Aplicados | Solo propuesta |
|---|---|---|---|---|
| 1 | Estética / diseño visual | 5 | 3 | 2 |
| 2 | Funcionalidad end-to-end | 4 | 2 | 2 |
| 3 | Accesibilidad WCAG AA | 6 | 2 | 4 |
| 4 | UI / componentes | 4 | 2 | 2 |
| 5 | UX | 5 | 1 | 4 |
| 6 | Programador senior | 4 | 3 | 1 |
| 7 | Jefe de arquitectura | 4 | 2 | 2 |
| 8 | QA / bugs | 3 | 2 | 1 |
| 9 | Seguridad / AppSec OWASP MASVS | 2 | 0 | 2 |
| 10 | Privacidad / RGPD / menores | 5 | 1 | 4 |
| 11 | Rendimiento | 4 | 1 | 3 |
| 12 | Red / offline / sincronización | 5 | 2 | 3 |
| 13 | Cobertura de pruebas | — (informe) | 0 | — |
| 14 | Web (wasmJs) — nuevo desde v11 | 7 | 4 | 3 |

**Total: 21 fixes aplicados** en 4 oleadas, todos verificados con
`compileDebugKotlinAndroid`, `jvmTest` (257/257 tests) y `wasmJsMainClasses`
(el bump v11→v12 no había verificado nunca el target web como parte del
"mínimo de verificación" — se añadió en esta ronda dado que varios fixes lo
tocan).

## Estado de los hallazgos de la ronda v11

Verificados uno a uno contra el código real (no contra el informe anterior)
antes de lanzar el panel v12:

- **StatsScreen: purga TTL 90 días erosiona totales "de por vida"** — SIGUE
  ABIERTO. Decisión de producto (contadores persistentes independientes del
  detalle purgable), no tocado.
- **`firestore.rules` v10/v11 deploy a producción** — SIGUE ABIERTO, no
  verificable desde este entorno (infraestructura externa).
- **Proveedor de auth anónima de Firebase Auth aún activo server-side** —
  SIGUE ABIERTO (consola de Firebase); confirmado que el CÓDIGO ya no usa
  `signInAnonymously` en ningún punto de `commonMain`.
- **`isValidOwnerSuccession` permite a un admin auto-nombrarse owner sin
  expulsión real** — SIGUE ABIERTO. Verificado contra `firestore.rules:383-391`:
  la regla exige que el nuevo owner sea un miembro real, activo y vinculado,
  pero no exige que la sucesión ocurra tras una expulsión efectiva — un admin
  podría, vía REST directo (no a través de la UI), auto-promoverse. Cambio de
  `firestore.rules`, fuera de alcance de código.
- **`achievements` sin restricción de propietario/rol en `firestore.rules`**
  — SIGUE ABIERTO, verificado línea 579-581 (`allow read, write: if
  isMember(hid) || isOwner(hid)`, sin acotar a `request.auth.uid == mid`).
  Impacto bajo (cosmético, sin puntos).
- **Duplicación de puntos por timeout post-commit en `donatePoints`/
  `redeemReward`** — matizado esta ronda: el especialista de QA/bugs encontró
  el ángulo concreto de `redeemReward` borrando el `redemption` aunque el
  PATCH sí hubiera aplicado (ver Red/offline #1 más abajo, relacionado pero
  no idéntico). El caso general de idempotencia vía Cloud Function SIGUE
  ABIERTO (infraestructura nueva).
- **Purga de `taskHistory`/`messages`/`notifications` sin techo natural
  server-side** — SIGUE ABIERTO (requeriría Cloud Scheduler). Esta ronda SÍ
  corrigió el texto de `privacy.html` que afirmaba purga "automática" cuando
  en realidad es on-demand (ver Privacidad #2).
- **Calendario sin historial completo de compleciones recurrentes** — SIGUE
  ABIERTO, confirmado que `CalendarScreen.kt` solo usa `task.lastCompletedDate`.
- **Duplicación estructural `CreateTaskScreen`/`EditTaskScreen`** — SIGUE
  ABIERTO, confirmado 1220/1117 líneas respectivamente.
- **Sin `WindowSizeClass`/`BoxWithConstraints` en toda la app** — SIGUE
  ABIERTO, confirmado 0 usos.
- **`HomeScreen` sin `TaskHubTopBar` compartido** — SIGUE ABIERTO.
- **`ErrorStateBlock` compartido (~15 duplicaciones)** — SIGUE ABIERTO.
- **CSV "veces completada" es booleano derivado** — SIGUE ABIERTO.
- **Formularios sin aviso de cambios sin guardar** — SIGUE ABIERTO.
- **Badge de notificaciones puede infracontar** — SIGUE ABIERTO (requiere
  índice compuesto Firestore).
- **`NotificationPollWorker` sin límite en sondeo de fondo** — sigue
  descartado a propósito (rompería la purga de leídas antiguas), documentado
  en v11, no revisitado esta ronda.

Ningún hallazgo de v11 se dio por resuelto sin verificar el código real
primero.

---

## Hallazgos aplicados (por oleada)

### Oleada 1 — Estética, Funcionalidad, Accesibilidad, UI/componentes

**[Estética][APLICADO]** Versión hardcodeada `v0.7.33` → `v0.7.35` en
`WelcomeScreen.kt:170` (4 bumps de atraso).

**[Estética][APLICADO]** `headlineLarge`/`headlineMedium` sin peso propio en
`TaskHubTypography` (`Theme.kt:328`): título "hero" de 4 pantallas
consecutivas del alta de hogar salía en peso regular, más débil visualmente
que títulos más pequeños contiguos. Añadido `FontWeight.Bold`, coherente con
`headlineSmall`.

**[Estética + UI/componentes, hallazgo coincidente][APLICADO]** `index.html`
(wasmJs) sin `background-color`: flash blanco antes de montar Compose,
notorio en tema oscuro. Añadido `#007660` (Teal800, mismo color que
`SplashScreen`).

**[Estética][SOLO PROPUESTA]** iOS: `AppIcon`/`AccentColor` sin asset real
(`iosApp/iosApp/Assets.xcassets/`) — requiere el PNG de marca.

**[Estética][SOLO PROPUESTA]** Onboarding (`WelcomeScreen`/
`CreateHouseholdScreen`/`CreateProfileScreen`) usa emoji genérico en vez del
`AppLogo` vectorial que sí usan `SplashScreen`/`AuthGateScreen` justo antes —
decisión de identidad visual.

**[Funcionalidad][APLICADO][CRÍTICO]** `SettingsSheet.kt` "Vincular
calendario" fallaba en silencio en iOS/web/desktop — **regresión**: el mismo
bug ya se había corregido en `TaskDetailScreen.kt` (otro call site del mismo
flujo) pero no en este segundo sitio. Añadido estado de error + `liveRegion`,
mismo patrón ya usado en `TaskDetailScreen`.

**[Funcionalidad][APLICADO]** Página de cierre del flujo OAuth loopback de
desktop (`GoogleDesktopSignInHelper.kt`) afirmaba "Sesión iniciada" ANTES de
que el canje de código por token hubiera siquiera empezado — si
`exchangeCodeForIdToken` fallaba después, el usuario ya había visto un
mensaje de éxito falso. Texto cambiado a neutro ("Procesando...").

**[Funcionalidad][SOLO PROPUESTA]** Snackbar de confirmación tras
`shareText()` — evaluado y descartado como fix mecánico: Android abre share
sheet nativo, desktop copia a portapapeles sin ningún share real, y web es
un no-op total. Un mismo mensaje "compartido" sería engañoso en web e
impreciso en desktop; requiere que `shareText` devuelva una señal real por
plataforma, no un cambio de una línea.

**[Funcionalidad][SOLO PROPUESTA]** Web: `inviteCode` con PRNG no
criptográfico — **corregido esta misma ronda** (ver QA/bugs #3, se marcó
independientemente por dos especialistas).

**[Accesibilidad][APLICADO][IMPORTANTE]** Contraste insuficiente en
`NotificationListScreen.kt` (`secondaryColor` con `.copy(alpha=0.8f)` sobre
`onPrimaryContainer`, 4/6 temas por debajo de 4.5:1 — Default claro 4.06:1,
Default oscuro 3.50:1, Naturaleza claro 3.89:1, Naturaleza oscuro 3.56:1).
Alpha eliminado.

**[Accesibilidad][APLICADO][IMPORTANTE]** Mismo patrón en `StatsScreen.kt`
(etiquetas de racha, `onTertiaryContainer.copy(alpha=0.8f)`, 3/6 temas por
debajo de 4.5:1). Alpha eliminado en ambos usos.

**[Accesibilidad][SOLO PROPUESTA]** `<html lang="es">` fijo en
`wasmJsMain/resources/index.html`, no sincronizado con el selector de idioma
de la app — lectores de pantalla usarían reglas fonéticas de español aunque
el usuario cambie a inglés.

**[Accesibilidad][SOLO PROPUESTA]** Árbol de semántica de Compose-en-canvas
(wasmJs) sin verificar con un lector de pantalla real (NVDA/VoiceOver) — CMP
1.8.0 tiene soporte ARIA/DOM para Wasm en estado experimental/incompleto;
todo el trabajo de `liveRegion`/`contentDescription` de rondas anteriores
podría no llegar realmente a un lector de pantalla en el navegador.

**[Accesibilidad][SOLO PROPUESTA]** `TaskDetailScreen`: selector de
completador (`Role.RadioButton`) sin `Modifier.selectableGroup()` en el
contenedor — un lector de pantalla no anuncia "1 de N" del grupo.

**[Accesibilidad][SOLO PROPUESTA]** `RankingScreen`: medallas 🥇🥈🥉 sin
`contentDescription` explícito localizado — depende de si el motor TTS del
sistema conoce el nombre Unicode del emoji.

**[UI/componentes][APLICADO]** "Punto de estado" reimplementado con 3 formas
distintas en `CalendarScreen.kt`/`TaskListScreen.kt` (dos de ellas NO
círculos reales: `RoundedCornerShape(3.dp)`/`(6.dp)` sobre cajas de 6dp/12dp,
y un cuadrado `shapes.extraSmall` de 8dp) — regresión parcial del mismo
anti-patrón que v11 ya corrigió una vez para `DayNumberBadge`. Extraído
`StatusDot` compartido (`ui/components/StatusDot.kt`), sustituidos los 3
call sites.

**[UI/componentes][APLICADO]** `RoundedCornerShape(4.dp)` literal en
`CalendarScreen.kt` `TaskChip` → `MaterialTheme.shapes.extraSmall` (mismo
valor, token en vez de literal).

**[UI/componentes][SOLO PROPUESTA]** Ilustraciones de estado vacío
incompletas: `RankingScreen`/`HouseholdMemberList` siguen con emoji crudo en
vez de la ilustración Canvas temática que sí tienen `TaskListScreen`/
`HomeScreen` — requiere diseñar 2 ilustraciones nuevas.

### Oleada 2 — UX, Programador senior, Jefe de arquitectura

**[UX][APLICADO]** Sección "Tema del widget" de Ajustes se mostraba en las 4
plataformas aunque solo Android tiene widget de pantalla de inicio real.
Nuevo `expect val hasHomeScreenWidget: Boolean` (true solo en Android),
sección ocultada en `SettingsSheet.kt` donde es `false`.

**[UX][SOLO PROPUESTA]** Web: login con Google falla en silencio, sin
diferenciar "no soportado en esta plataforma" de "cancelado por el usuario".

**[UX][SOLO PROPUESTA]** Desktop: cualquier fallo de login (red, sin
navegador) se confunde con "canceló" — contradice el propio KDoc del código
("falla con un error claro").

**[UX][SOLO PROPUESTA]** Login desktop puede quedarse colgado hasta 5
minutos sin botón de cancelar visible si el usuario cierra la pestaña del
navegador sin completar el flujo.

**[UX][SOLO PROPUESTA]** Desktop: "Compartir"/"Exportar CSV" copian al
portapapeles sin ninguna confirmación visible (mismo hallazgo que
Funcionalidad #1 de oleada 1, coincidente).

**[Programador senior][APLICADO][crítico real]** Callback OAuth loopback de
desktop sin timeout de LECTURA: `serverSocket.soTimeout` solo cubre
`accept()`, el `Socket` que devuelve NO lo hereda — una conexión que llega
pero nunca envía la petición (pre-connect del navegador, escáner de puerto
local) dejaba `readCallbackParams`/`readLine()` bloqueado indefinidamente,
sin ningún límite, pese al KDoc que prometía "nunca colgar `SigningIn` para
siempre". Fijado `socket.soTimeout` tras `accept()`.

**[Programador senior][APLICADO]** `GoogleDesktopSignInHelper.signIn()`
prometía en su KDoc "nunca lanza salvo config vacía" pero no tenía ningún
`catch` alrededor de `exchangeCodeForIdToken` (llamada HTTP real). Envuelto
en `try/catch(CancellationException) → throw / catch(Exception) → null`.

**[Programador senior][evaluado, revertido]** Se intentó fijar
`material-icons-core` a la versión del catálogo (`1.8.0`) en vez de un
literal desfasado (`1.7.3`) — **revertido**: ese artefacto no publica release
`1.8.0` (verificado con un build real que falló resolviendo la dependencia);
el literal es deliberado, documentado con el motivo exacto.

**[Programador senior][SOLO PROPUESTA]** Cero tests para
`codeChallengeFor`/`buildAuthorizationUrl`/`readCallbackParams` (funciones
puras del flujo OAuth desktop) — requiere subirlas a `internal` para ser
testables desde `jvmTest`.

**[Arquitectura][APLICADO]** `RewardsRepository.getRewards`/
`getRewardRedemptions` sin paginar (única excepción al resto de repos, que
ya usan `listAllDocuments` desde v11) — migrado al mismo patrón.

**[Arquitectura][APLICADO]** `GoogleCalendarRepository` instanciaba su
propio `HttpClient` en vez de compartir el de `FirestoreClient` (mismo
patrón ya usado por `CloudFunctionsClient`) — duplicaba engine/pool de
conexiones Ktor, más relevante con 4 targets. Ahora inyectado vía Koin.

**[Arquitectura][SOLO PROPUESTA][CRÍTICO]** `SecureStore` en wasmJs rompe su
propio contrato de cifrado: guarda el refresh token de Google/Firebase SIN
cifrado real en `localStorage` (ya documentado en el propio código como
placeholder) — accesible desde DevTools/XSS sin decompilar nada. Requiere
WebCrypto (AES-GCM + clave no extraíble), diseño nuevo.

**[Arquitectura][SOLO PROPUESTA]** Capacidad de plataforma para Google
Sign-In duplicada en cada `actual` (iOS/web reimplementan por separado el
mismo workaround de "token vacío") en vez de un `expect val
supportsGoogleSignIn: Boolean` consultable desde `commonMain`.

### Oleada 2 (retry) — QA/bugs

**[QA/bugs][APLICADO][CRÍTICO — cerrado con Red/offline]** Ver Red/offline
#1: dos canjes/donaciones concurrentes desde dos dispositivos podían dejar
`totalPoints` negativo.

**[QA/bugs][APLICADO]** Sección "Google Calendar" de Ajustes se mostraba en
desktop/web pese a que `getGoogleCalendarAccessToken()` está hardcodeado a
`null` ahí — en desktop, el usuario podía completar un flujo OAuth real
entero en el navegador (funciona) solo para que la app descartara el
resultado por falta de soporte de Calendar. Nuevo `expect val
hasCalendarSupport: Boolean` (true solo Android), sección ocultada.

**[QA/bugs][APLICADO]** `secureRandomInt` en wasmJs usaba `kotlin.random.Random`
(no CSPRNG) para `inviteCode` — único código de invitación de hogar, la
única barrera para unirse sin invitación explícita. Ahora usa
`crypto.getRandomValues` del navegador vía `@JsFun`, verificado con
`wasmJsMainClasses`.

### Oleada 3 — Seguridad, Privacidad, Rendimiento, Red/offline

**[Seguridad AppSec][SOLO PROPUESTA]** Asimetría en `firestore.rules`: la
rama de auto-edición de `members/{mid}` no tiene tope ni restricción de
campos (a diferencia de `isPeerPointsTransfer`, que sí topa a 1000/escritura)
— `appreciationGiven`/`appreciationWeekStart` (presupuesto semanal de 50
puntos) es una validación puramente de cliente, nunca aplicada por la regla.
Cambio de `firestore.rules`.

**[Seguridad AppSec][SOLO PROPUESTA]** Confirmar en GCP Console que la API
key pública de Firebase (esperada, no un secreto) tiene restricción de "HTTP
referrers" configurada — más relevante en web, donde la key es trivialmente
visible en `view-source`. Infraestructura, no código.

**[Seguridad AppSec][confirmaciones positivas, sin acción]** PKCE+state+
timeouts del loopback desktop, `GoogleOAuthConfig` sin secretos reales,
`JvmSecureStore` AES-256-GCM correcto, `allowBackup` mitigado, CSV injection
(CWE-1236) cubierto, `redactApiKey` cubre todos los endpoints — todo
verificado sin hallazgos.

**[Privacidad/RGPD][CRÍTICO, recomendación fuerte — NO aplicado directo]**
Falta por completo la UMP (User Messaging Platform) de Google pese a tener
AdMob activo — es un requisito de la propia política de AdMob para tráfico
UE/EEE/Reino Unido, no una interpretación legal externa; activar IDs de
producción sin esto puede derivar en suspensión de cuenta AdMob. **No se
implementó en esta ronda**: integrar `user-messaging-platform` es una
funcionalidad nueva no trivial (nueva dependencia, flujo de consentimiento,
gate sobre `MobileAds.initialize()`) que no puede verificarse sin un entorno
AdMob/dispositivo real — corresponde a un encargo dedicado, no a un fix
mecánico de esta auditoría. Se documenta aquí con prioridad alta para que el
propietario lo priorice explícitamente.

**[Privacidad/RGPD][APLICADO]** `privacy.html` afirmaba purga "automática" a
90 días de historial de tareas/mensajes/notificaciones; en realidad solo
notificaciones (Android) tienen un worker periódico real — historial y
mensajes se purgan al abrir la pantalla correspondiente. Texto corregido
para reflejar el mecanismo real (ver Red/offline y Arquitectura para el
código, no tocado a propósito: cambiar el comportamiento a periódico real
para las 3 colecciones sería una decisión de alcance mayor, no un fix de
texto).

**[Privacidad/RGPD][SOLO PROPUESTA]** Añadir "UMP/CMP implementado" al
checklist de `guia-publicacion.md §4`, antes del ítem de IDs de producción
— de proceso/documentación, depende del hallazgo crítico de arriba.

**[Privacidad/RGPD][SOLO PROPUESTA, sin acción necesaria]** Ausencia de
gating de edad — coherente con el posicionamiento actual ("Todas las
edades", no dirigida a familias/niños); no es un bug, solo pendiente de
quedar documentado como decisión consciente.

**[Privacidad/RGPD][SOLO PROPUESTA, trabajo futuro]** iOS/desktop/web sin
AdMob/Analytics/consentimiento — sin riesgo hoy (solo Android en
producción), pero requerirá App Tracking Transparency + UMP iOS cuando se
publique esa plataforma.

**[Rendimiento][APLICADO]** Dependencia `compose.components.uiToolingPreview`
en `commonMain` sin ningún `@Preview` en todo el proyecto — eliminada.

**[Rendimiento][SOLO PROPUESTA]** `SplashScreen` con `delay(1500)` fijo,
no cancelable — decisión de marca/branding vs. tiempo-hasta-interactivo,
más relevante en web (compite con la descarga del bundle).

**[Rendimiento][confirmación positiva, sin acción]** El ~17MB del bundle web
es mayormente runtime Skia (`skiko.wasm`, 8.4MB, fijo por CMP) + `.wasm` ya
optimizado con `wasm-opt` (6.6MB, bajó de 21.4MB sin optimizar) — no hay
nada evitable con cambios de código de la app. Recomposición y lecturas de
Firestore en buen estado general (herencia de rondas v4/v7/v10/v11), sin
hallazgos críticos nuevos.

**[Red/offline][APLICADO][CRÍTICO]** `redeemReward`/`donatePoints` validaban
saldo contra una única lectura (`getMembers`) sin revalidar en el momento
real de la escritura — dos canjes/donaciones concurrentes desde dos
dispositivos podían dejar `totalPoints` negativo (cada escritura es
individualmente válida vista contra SU propia lectura fresca, ninguna sabe
del descuento concurrente del otro). **Fix:** `addMemberPoints()` acepta
ahora un `floor` opcional, revalidado contra el valor FRESCO en cada
reintento de concurrencia optimista (no solo la lectura inicial del
caller); `redeemReward`/`donatePoints` lo usan con `floor = 0`.
`InsufficientBalanceException` se movió de anidada en `FirestoreRepository`
a nivel de paquete en `network/` para que `MemberRepository` pueda lanzarla
sin depender de la fachada.

**[Red/offline][APLICADO][IMPORTANTE]** `completeTask`/`undoTaskCompletion`
solo invalidaban la caché local en el camino feliz — un timeout de red no
distingue "nunca llegó al servidor" de "sí se aplicó pero la respuesta se
perdió"; en ese segundo caso, la app podía servir la foto PRE-mutación
indefinidamente si el dispositivo quedaba offline justo después. Invalidación
movida a un `finally`.

**[Red/offline][SOLO PROPUESTA]** `isOnline()`/`isOffline` solo lo consume
`TaskScreenModel` — `MemberScreenModel` (canjes/donaciones/agradecimientos)
no avisa preventivamente de falta de conexión. Decisión de arquitectura
(dónde vive el estado compartido).

**[Red/offline][SOLO PROPUESTA]** Desktop: red caída a mitad del intercambio
de código OAuth es indistinguible de cancelación de usuario — cambia el
contrato de `GoogleSignInResultHolder`, compartido con Android/iOS.

**[Red/offline][SOLO PROPUESTA]** wasmJs: `connectTimeoutMillis` del
`HttpClient` compartido probablemente no tiene efecto (limitación conocida
del engine Js de Ktor) — requiere verificación empírica en navegador real
antes de tocar código.

### Oleada 4 — Cobertura de pruebas (informe), Web (wasmJs)

**[Cobertura de pruebas][informe, sin cambios — regla del propio rol]** Mapa
completo en la sección dedicada más abajo. Huecos priorizados: `addMemberPoints`
con `floor` (lógica nueva de esta ronda, sin test), funciones puras de
`GoogleDesktopSignInHelper` (sin test, requieren visibilidad `internal`),
orquestación de `appreciateMember`/`donatePoints` (reintentos/rollback, sin
test de integración), `AchievementChecker.getAchievementsWithStatus` (sin
test directo, trivial hoy), ausencia total de `MockEngine` para probar
flujos de red reales.

**[Web][SOLO PROPUESTA][CRÍTICO]** Ninguna llamada a Firestore REST desde el
navegador tiene CORS verificado — el propio commit que introdujo el target
(`de0178e`) lo marca explícitamente como pendiente ("corresponde al
despliegue externo"). Sin esto la app web podría no funcionar en absoluto.
Requiere abrir el build servido en un navegador real y confirmar cabeceras
`Access-Control-Allow-*`, o un proxy/rewrite de Firebase Hosting — fuera de
alcance de un cambio en `composeApp`.

**[Web][APLICADO][CRÍTICO]** Acceso a `localStorage` sin proteger en el
arranque de `App()`/`WasmJsSecureStore` — si el storage no está disponible
(Safari con cookies bloqueadas, storage particionado en iframe, política de
privacidad de empresa), la excepción no tenía dónde ir y Compose nunca
montaba nada. `WasmJsSecureStore` y `SettingsStore.getLanguage()`/`getTheme()`
ahora capturan cualquier fallo y caen a valores por defecto.

**[Web][APLICADO][IMPORTANTE]** Un fallo al ESCRIBIR en caché (`localStorage.
setItem` puede lanzar `QuotaExceededError`, más alcanzable en web que en
Android/iOS) descartaba datos de red recién obtenidos con éxito — el
`catch` genérico de los repos trataba ese fallo igual que un fallo de
lectura real, cayendo a una foto de caché más vieja. Nuevo helper
`TaskCache.putSafely()` usado por los 8 `cache*()` de escritura: un fallo al
persistir la caché ya no afecta al valor devuelto por una lectura de red
exitosa.

**[Web][SOLO PROPUESTA]** Sin `beforeunload`/service worker: recargar o
cerrar la pestaña a medio escribir pierde el formulario en curso sin aviso,
y no hay app-shell offline. Requiere verificación de UX en navegador real.

**[Web][APLICADO][MENOR]** Interruptor "Notificaciones" de Ajustes se
mostraba en web (y JVM/iOS) sin efecto real —
`createNotificationScheduler()` siempre es `NoOpNotificationScheduler` fuera
de Android. Mismo patrón que Calendar/widget: nuevo `hasNotificationSupport`,
sección ocultada donde es `false`.

---

## Mapa de cobertura de pruebas (informe del especialista #13, sin cambios aplicados)

257 tests, 0 fallos, repartidos en:

| Archivo | Cubre |
|---|---|
| `RecurrenceRulesTest.kt` (77) | `RecurrenceRules` casi al completo — el más exhaustivo del repo. |
| `PointsRulesTest.kt` (21) | Presupuesto de agradecer, validaciones de donar, límites de transferencia. |
| `PenaltyRulesTest.kt` (14) | Cálculo de penalización por tarea tardía. |
| `HouseholdRulesTest.kt` (14) | Sucesión de owner. |
| `FirestoreClientRetryTest.kt`/`PaginationTest.kt` (14) | Helpers puros de reintento/paginación. |
| `FirestoreParsersTest.kt` (10) | Parseo de documentos Firestore→modelos. |
| `TaskCsvExporterTest.kt` (7) | Export CSV, incl. anti CSV-injection. |
| `AchievementCheckerTest.kt` (6) | Desbloqueo de logros. |
| `NotificationPollRulesTest.kt`/`TaskReconciliationTest.kt`/`AssignmentCompletionRulesTest.kt`/`MemberResolutionRulesTest.kt` (22) | Reglas puras varias. |
| `HomeScreenModelTest.kt`/`TaskListScreenTest.kt`/`StatsScreenModelTest.kt`/`MemberScreenModelTest.kt`/`TaskScreenModelTest.kt` | Lógica de ScreenModels vía `FakeFirestoreRepository`. |
| `SettingsStoreTest.kt`/`TaskCacheTest.kt`/`SecureStoreTest.kt`/`SecureStoreJvmTest.kt` | Persistencia, migración de tokens, cifrado JVM. |

**Sin ningún `MockEngine`/HTTP falso en todo el repo** — la cobertura de red
se limita a helpers puros extraídos; ningún flujo real de
`FirestoreClient`/`MemberRepository`/`HouseholdRepository` está probado
contra HTTP simulado.

**Huecos priorizados:**
1. **CRÍTICO** — `addMemberPoints` con `floor` (nueva esta ronda, race de
   concurrencia optimista): sin `MockEngine` para simular 409/reintento, la
   validación podría extraerse a una función pura testable sin HTTP.
2. **CRÍTICO** — `GoogleDesktopSignInHelper`: `codeChallengeFor`/
   `buildAuthorizationUrl`/`readCallbackParams` son puras y deterministas
   pero `private`; subirlas a `internal` las haría testables desde `jvmTest`
   sin abrir navegador ni socket.
3. **IMPORTANTE** — Orquestación de `appreciateMember`/`donatePoints` (qué
   pasa si el segundo paso de una operación de dos pasos falla) sin test de
   integración.
4. **IMPORTANTE** — `AchievementChecker.getAchievementsWithStatus` sin test
   directo (trivial hoy, riesgo de regresión silenciosa).
5. **MENOR** — Sin test que documente la interacción entre la validación
   optimista (`PointsRules.validateDonateBalance`) y la nueva revalidación
   pesimista (`floor`).

---

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ **BUILD SUCCESSFUL** tras cada oleada (solo warnings preexistentes:
GoogleSignIn/MasterKey/EncryptedSharedPreferences deprecados, `when`
exhaustivo con `else` redundante — ninguno de esta ronda).

```
./gradlew :composeApp:jvmTest --console=plain
```
→ **BUILD SUCCESSFUL**, verificado contra los XML de
`build/test-results/jvmTest/*.xml` (no solo el resumen de Gradle): **257
tests, 0 fallos, 0 errores**, estable en las 4 oleadas.

```
./gradlew :composeApp:wasmJsMainClasses --console=plain
```
→ **BUILD SUCCESSFUL** — añadido a la verificación de esta ronda (no estaba
en el mínimo de v11) porque varios fixes tocan `wasmJsMain/` directamente
(interop `@JsFun` con `crypto.getRandomValues`, nuevos `expect`/`actual`).

Un intento de fijar `material-icons-core` a la versión del catálogo (1.8.0)
se detectó como rotura real en build (`Could not find
...material-icons-core:1.8.0`) y se revirtió antes de continuar — mismo
criterio de "verificar antes de dar por buena una corrección" que rondas
anteriores.

## Archivos modificados en esta ronda

**Oleada 1:**
- `ui/screens/WelcomeScreen.kt`, `ui/theme/Theme.kt`,
  `wasmJsMain/resources/index.html`, `ui/components/SettingsSheet.kt`,
  `jvmMain/platform/GoogleDesktopSignInHelper.kt`,
  `ui/components/StatusDot.kt` (nuevo), `ui/screens/CalendarScreen.kt`,
  `ui/screens/TaskListScreen.kt`, `ui/screens/NotificationListScreen.kt`,
  `ui/screens/StatsScreen.kt`.

**Oleada 2:**
- `jvmMain/platform/GoogleDesktopSignInHelper.kt`, `composeApp/build.gradle.kts`,
  `platform/Platform.kt` + `actual` en `androidMain`/`jvmMain`/`iosMain`/
  `wasmJsMain`, `network/RewardsRepository.kt`,
  `network/GoogleCalendarRepository.kt`, `di/AppModule.kt`,
  `ui/components/SettingsSheet.kt`.

**Oleada 3:**
- `network/MemberRepository.kt`, `network/FirestoreRepository.kt`,
  `ui/models/MemberScreenModel.kt`, `docs/privacy.html`,
  `composeApp/build.gradle.kts`.

**Oleada 4:**
- `wasmJsMain/storage/SecureStore.wasmJs.kt`, `storage/SettingsStore.kt`,
  `storage/TaskCache.kt`, `platform/Platform.kt` + `actual` en las 4
  plataformas, `ui/components/SettingsSheet.kt`.

**Documentación:**
- `docs/review-panel-expertos-v12-2026-09-17.md` (este informe).
- `docs/review-panel-expertos-v12-progreso.md` (checkpoint de oleada 1,
  conservado como registro del proceso).

## Recomendación de prioridad para el propietario

De todo lo dejado como SOLO PROPUESTA, dos merecen atención antes que el
resto por su impacto potencial si se ignoran:

1. **UMP/CMP de AdMob ausente (Privacidad #1, CRÍTICO)** — riesgo real de
   suspensión de cuenta AdMob si se activan IDs de producción sin resolverlo
   antes. No es opcional una vez se publique con anuncios reales.
2. **CORS de Firestore REST sin verificar en el target web (Web #1,
   CRÍTICO)** — sin esto, la app web podría no funcionar en absoluto al
   desplegarla; ya estaba marcado como pendiente desde el commit que
   introdujo el target, sigue sin resolverse.

El resto de propuestas (rediseños visuales, refactors arquitectónicos,
`firestore.rules`) son de menor urgencia relativa y pueden esperar a una
ronda dedicada o a una decisión de producto explícita.
