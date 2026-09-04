# Correcciones arquitectura / rendimiento / UX / tests — 2026-09-04

Continuación de `docs/correcciones-2026-09-04-integridad-seguridad.md` (HEAD
de partida `2b7c69b`). Aplica el bloque "Arquitectura / rendimiento" (15-21),
"Estética / UX" (23-26, 28) y "Cobertura de tests" (29-31) de
`docs/review-panel-expertos-2026-09-03.md`.

## Arquitectura / rendimiento

### 15 — `HouseholdTaskSection` bypassaba el ScreenModel

`HouseholdTaskSection.kt` inyectaba `FirestoreRepository` directamente vía
`koinInject` y hacía su propio `LaunchedEffect { repo.getTasks(...) }` — el
único sitio del árbol fuera del patrón ScreenModel.

Fix: `HomeScreenModel` gana `loadHouseholdPreview(householdId)` +
`previewTasks: StateFlow<Map<String, HouseholdPreviewState>>` (mismo filtro
"primeras 5 sin completar" que antes). `HouseholdTaskSection` pasa a ser
puramente presentacional (recibe `previewState` por parámetro); `HomeScreen`
dispara la carga con un `LaunchedEffect(household.id)` por item de la
`LazyColumn` y le pasa el estado ya resuelto del ScreenModel.

### 16 — `FirestoreRepository` facade seguía creciendo

Causa raíz (diagnosticada por el panel): los repos de dominio
(`TaskRepository`, `MemberRepository`, `HouseholdRepository`,
`NotificationRepository`, `RewardsRepository`) eran campos privados
construidos a mano dentro de `FirestoreRepository`, con `MemberRepository`/
`HouseholdRepository` recibiendo `getLocalId`/`currentUserIdentities` como
**lambdas** para evitar un ciclo hacia la fachada — eso hacía imposible
registrarlos como `single` de Koin.

Fix incremental (build verde en cada paso):
1. `getLocalId()`/`currentUserIdentities()` se mueven de `FirestoreRepository`
   a `FirestoreClient` (de quien realmente dependen: `cachedLocalId` +
   `settingsStore`, nada más).
2. `MemberRepository`/`HouseholdRepository` pasan a llamar
   `firestoreClient.getLocalId()` directamente — las lambdas desaparecen, y
   con ellas el ciclo hacia la fachada.
3. `FirestoreClient` + los 5 repos de dominio se registran como `single` de
   Koin (`AppModule.kt`), con `firestoreBaseUrl()` como helper compartido
   para la URL base (antes duplicada). `FirestoreRepository` los recibe por
   constructor en vez de construirlos.
4. Orquestación cross-dominio extraída donde era seguro: el POST crudo de
   `redeemReward` (Reward+Member) se mueve a
   `RewardsRepository.createRedemption`; la orquestación en sí (crear
   redemption + `addMemberPoints`) se queda en la fachada, documentado con el
   mismo criterio que `deleteHousehold`/`leaveHousehold`.

**Pendiente**: `completeTask`/`completeAssignment` (la orquestación
Task+Member más grande) se queda deliberadamente en la fachada — extraerla
requeriría tocar la lógica de puntos/concurrencia más crítica del repo bajo
este mismo encargo, con más riesgo que beneficio inmediato. Ahora que los
repos de dominio son inyectables, un futuro refactor puede moverla a un
orquestador dedicado sin pelear con el ciclo de construcción.

### 17 — `TaskScreenModel.kt` "mini god ScreenModel"

1322 líneas → **1210 líneas** (commonTest aparte). Splits aplicados:
- **`TaskCommentsScreenModel`** (nuevo): todo el subsistema de comentarios
  (`CommentsUiState`, `newCommentText`, `loadComments`, `addComment`,
  `resolveCurrentMemberName`) — el más autocontenido, sin tocar ningún flujo
  de puntos. Registrado en Koin, inyectado en `TaskDetailScreen` junto al
  `TaskScreenModel` ya existente. `currentMemberId` se le pasa como
  parámetro en vez de duplicarse.
- **`TaskCsvExporter`** (nuevo): `generateCsv`/`escapeCsvField`, función pura
  sin estado — no necesitaba ser parte de ningún ScreenModel.
  `TaskScreenModel.generateCsv` queda como un delegado de una línea.

**Pendiente** (documentado, no aplicado esta ronda por riesgo/beneficio):
- Estado de sincronización con Google Calendar (`syncTaskToCalendarNow`,
  `CalendarActionState`) — acoplado a `_myAssignment` y a
  `loadTaskDetail()` del propio `TaskScreenModel`; separarlo requeriría un
  callback entre dos ScreenModels.
- `updateMemberStreak`/`checkAndAwardAchievements` — internos de
  `completeTask`/`completeAssignment`, mismo motivo que el punto 16
  pendiente.

### 18 — `getAllAssignments()` duplicaba `getTasks()`

`TaskRepository.getAllAssignments(householdId)` pedía `getTasks(householdId)`
internamente. Nuevo overload
`getAllAssignments(householdId, tasks: List<TaskResponse>)` que reutiliza una
lista ya cargada por el caller. Aplicado en las 3 rutas calientes que
llamaban a ambas por separado: `StatsScreenModel.loadStats`,
`TaskScreenModel.loadTasks`, `CalendarSyncManager.reconcile` (este último
además reordenado: `getTasks` se pide UNA vez, antes de comprobar si hay
pendientes, en vez de una segunda vez condicional).

### 19 — `loadTasks()`/`loadStats()` secuenciales

`StatsScreenModel.loadStats` y `TaskScreenModel.loadTasks`: tras el primer
`getTasks()` (necesario para el resto), las lecturas independientes
(`getAllAssignments(tasks)`, `getTaskHistory`, `getMembers`) se lanzan con
`async`/`await` en paralelo en vez de en serie.

### 20 — `deleteAllDocuments` sin límite de concurrencia

`FirestoreRepository.deleteAllDocuments` lanzaba un `async` por documento sin
límite — una colección `taskHistory` real de miles de documentos disparaba
miles de peticiones DELETE concurrentes de golpe. Fix: `Semaphore(20)`
(`MAX_CONCURRENT_DELETES`) tanto en el fan-out por documento como en el
fan-out por tarea/miembro de `deleteHousehold` (que a su vez llama a
`deleteAllDocuments` por cada uno).

### 21 — Mapeo DTO↔dominio descentralizado

Los 7 tipos que faltaban (`TaskResponse`, `TaskAssignmentResponse`,
`CommentResponse`, `TaskHistoryResponse`, `NotificationResponse`,
`RewardResponse`, `RewardRedemption`, `MessageResponse` — el mapeo de
`HouseholdResponse`/`MemberResponse` ya vivía en `FirestoreParsers` desde
antes) se centralizan en `FirestoreParsers.kt`. Los repos de dominio quedan
como delegados de una línea a `FirestoreParsers.toXxxResponse(...)`.

## Estética / UX

### 23 — 33 fallbacks de error hardcodeados en español

Recuento del panel confirmado: 33 puntos en 7 archivos
(`MemberScreenModel`, `GoogleAuthManager`, `ProfileScreenModel`,
`NotificationScreenModel`, `TaskScreenModel`, `HomeScreenModel`,
`HouseholdScreenModel`). Todos migrados a `AppStrings` (29 claves nuevas
ES/EN, 4 reutilizan claves ya existentes). Mecanismo: cada ScreenModel ya
tenía (o gana) `settingsStore: SettingsStore` inyectado y usa
`AppStrings.get(key, settingsStore.getLanguage())` internamente — más simple
que enhebrar `lang` como parámetro en cada función pública y tocar todos los
call-sites en las Screens (alternativa considerada, descartada por mayor
superficie de cambio para el mismo resultado).
`NotificationScreenModel` gana `settingsStore` en el constructor (antes no
lo tenía).

### 24 — `MemberRewardScreen` con `AlertDialog` manual

El diálogo de confirmación de canje pasa a usar
`DestructiveConfirmDialog(destructive = false)` — mismo componente compartido
que el resto de confirmaciones no destructivas (p.ej. cambio de rol).

### 25 — Patrón "cabecera expandible" duplicado + fuga de dominio i18n

4 sitios (`HouseholdTaskSection`, `HouseholdMemberList`, `CreateTaskScreen#
QuickTemplatesSection`, `TaskListScreen#GroupHeader`) implementaban a mano el
patrón "Row clicable + icono chevron arriba/abajo", 3 de los 4 reutilizando
las claves `household_task_section_collapse`/`_expand` fuera de su dominio.

Fix: nuevo composable compartido `ExpandableSectionHeader` (contenido
libre a la izquierda vía slot `content`, chevron + `contentDescription`
gestionados internamente) + claves neutrales `common_collapse`/
`common_expand` (ES/EN). Las claves `household_task_section_collapse/expand`
se eliminan (sin más usos). Los 4 sitios migrados, cada uno conservando su
estilo visual (color/tamaño de icono, padding) vía los parámetros
`chevronTint`/`chevronSize`/`modifier`.

### 26 — Cobertura de `reduce-motion` incompleta (3 sitios)

Los 3 `AnimatedVisibility` sin guardia: `HouseholdTaskSection` (contenido de
la tarjeta), `CreateTaskScreen#QuickTemplatesSection` (contenido de
plantillas), `HomeScreen` (menú del FAB). Los 3 ganan
`enter/exit = EnterTransition.None/ExitTransition.None` cuando
`shouldReduceMotion()` es true, mismo patrón ya usado en `TaskListScreen`.

### 28 — `SemanticColors.onSuccess`/`onInfo` en dark sin AA

`onSuccess` (3.91:1 → **4.61:1**, `0xFF1B5E20` → `0xFF18521C`) y `onInfo`
(3.90:1 → **4.55:1**, `0xFF0D47A1` → `0xFF0B3E8C`) oscurecidos conservando el
mismo tono (ajuste solo de luminosidad, calculado con la fórmula de
contraste WCAG), sin tocar `success`/`info` (sí usados hoy como tinte de
icono/texto contra `surface`, auditados aparte) ni las variantes
`*Container` (ya pasaban AA).

## Pendientes documentados (fuera de alcance de este encargo)

- **22** — Ilustraciones de estado vacío: requiere assets de diseño nuevos.
- **27** — Adopción de `ShimmerList` en el resto de pantallas: no trivial
  (~8-10 archivos), no aplicado.

## Cobertura de tests

### 29 — `SecureStore` sin test de fallo de descifrado real

Nuevo `SecureStoreJvmTest.kt` (en `jvmTest`, no `commonTest`: necesita tocar
`java.util.prefs.Preferences` directamente, API solo disponible en JVM) —
corrompe el ciphertext ya guardado por `putString` y confirma que `getString`
devuelve `null` en vez de propagar la excepción (2 tests: ciphertext AES-GCM
corrupto, valor que ni siquiera es Base64 válido).

### 30 — `TaskCache`/`SettingsStore.getCalendarId`/`setCalendarId` sin test

- `TaskCacheTest.kt` (nuevo, 13 tests): cache-then-get de tasks/household/
  members, aislamiento por hogar, cada `clearXxx` borra solo lo suyo,
  `clearHousehold` borra las 3 a la vez sin afectar a otros hogares, JSON
  corrupto → `null` en vez de excepción. Duplica un `FakeSettings` local
  (`FakeCacheSettings`) en vez de reusar el de `SettingsStoreTest.kt` (es
  `private` a ese archivo — y dos clases `private` con el mismo nombre en el
  mismo paquete colisionan en JVM, así que se renombra).
- `SettingsStoreTest.kt` gana 5 tests de `getCalendarId`/`setCalendarId`:
  valor no seteado, roundtrip, aislamiento por hogar, sobrescritura, JSON
  corrupto → mapa vacío.

### 31 — `completeAssignment` sin test de integración (cierre de hermanas)

Opción elegida: extraer la lógica a función pura en vez de añadir
`ktor-client-mock` como dependencia nueva (instrucción explícita: sin mocks
nuevos). Nuevo `AssignmentCompletionRules.siblingsToClose(allAssignments,
completedAssignmentId)` (en `network/`, mismo patrón que `PenaltyRules`/
`RecurrenceRules`) con la regla exacta que antes vivía inline en
`FirestoreRepository.completeAssignment`: todas las asignaciones "assigned"
salvo la que ya se completó. `AssignmentCompletionRulesTest.kt` (5 tests)
cubre: excluye la ya completada, excluye no-"assigned", incluye todas las
demás sin importar el miembro, lista vacía cuando no hay hermanas, lista de
entrada vacía.

## Archivos tocados (30, sin contar docs)

```
composeApp/src/commonMain/kotlin/org/taskhub/di/AppModule.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/AssignmentCompletionRules.kt (nuevo)
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreClient.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreParsers.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/HouseholdRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/NotificationRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/RewardsRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/TaskRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/ExpandableSectionHeader.kt (nuevo)
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdMemberList.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdTaskSection.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/CalendarSyncManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/GoogleAuthManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HomeScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HouseholdScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/MemberScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/NotificationScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/ProfileScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/StatsScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskCommentsScreenModel.kt (nuevo)
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskCsvExporter.kt (nuevo)
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/HomeScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/MemberRewardScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskDetailScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/theme/SemanticColors.kt
composeApp/src/commonTest/kotlin/org/taskhub/network/AssignmentCompletionRulesTest.kt (nuevo)
composeApp/src/commonTest/kotlin/org/taskhub/storage/SettingsStoreTest.kt
composeApp/src/commonTest/kotlin/org/taskhub/storage/TaskCacheTest.kt (nuevo)
composeApp/src/jvmTest/kotlin/org/taskhub/storage/SecureStoreJvmTest.kt (nuevo)
```

## Verificación (OBLIGATORIO)

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL` — sin errores.

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` — 160 tests (135 base del panel v6 + 25 nuevos de esta
ronda), sin fallos.
