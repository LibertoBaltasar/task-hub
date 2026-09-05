# Ronda A — propuestas aprobadas por el coordinador (2026-09-05)

Aplica los 14 fixes de la lista aprobada por el coordinador a partir de las
"PROPUESTAS pendientes" de `docs/review-panel-expertos-2026-09-04.md` y
`docs/review-panel-expertos-notificaciones-2026-09-05.md`. **Sin commit, sin
push, sin bump de versión, sin tocar `firestore.rules`** — todo queda en el
working tree.

## 1. `createdBy` vacío al crear tarea — APLICADO

`CreateTaskScreen` es un `data class Screen`: `createdBy` queda fijado al
valor que tenía `currentMemberId` en la pantalla de origen en el momento del
`navigator.push(...)`, que puede seguir siendo `""` si esa pantalla aún no
había resuelto el miembro actual (arranca en `""`, se resuelve de forma
asíncrona). Como es un `val` de constructor, un simple guard no se
autocorregía nunca aunque el origen resolviera el miembro más tarde.

Fix aplicado en `CreateTaskScreen.kt`:
- `effectiveCreatedBy` (estado Compose) arranca en el `createdBy` recibido;
  si llega vacío, se resuelve por sí misma llamando a
  `TaskScreenModel.resolveCurrentMemberId(householdId)` (método nuevo,
  delega en `FirestoreRepository.resolveCurrentMember`, mismo mecanismo que
  usa el resto de la app).
- Mientras `effectiveCreatedBy` esté vacío se muestra un aviso
  ("Espera un momento: aún se está identificando quién crea la tarea",
  `create_task_creator_not_resolved` ES/EN) y el botón "Crear" queda
  deshabilitado.
- `taskModel.createTask(...)` usa `effectiveCreatedBy`, nunca el `createdBy`
  original potencialmente vacío.

Archivos: `CreateTaskScreen.kt`, `TaskScreenModel.kt`, `AppStrings.kt`.

## 2. Doble salto visual en arranque en frío desde notificación — APLICADO

Verificada la API real de Voyager 1.1.0-beta03 (`javap` sobre el jar):
existe `Navigator(screens: List<Screen>, ...)` además de
`Navigator(screen: Screen, ...)`, así que SÍ soporta pila inicial
multi-pantalla.

Fix en `App.kt`: en vez de crear el `Navigator` solo con `HomeScreen()` y
hacer `push` al destino en un `LaunchedEffect` posterior (lo que pintaba
`HomeScreen` un frame antes de la transición), se construye la pila
`[HomeScreen(), destino]` directamente cuando hay un deep link de arranque en
frío (`deepLinkHouseholdId` no vacío). Se guarda qué deep link quedó ya
"consumido" en la pila inicial (`initialDeepLinkConsumedKey`) para que el
`LaunchedEffect` que sigue vivo dentro del `Navigator` no lo vuelva a
`push`ear una segunda vez — ese mismo efecto sigue activo para el caso
"Activity ya viva, llega un deep link nuevo por `onNewIntent`" (ahí sí debe
hacer `push` con transición, no hay doble salto porque la app ya se estaba
mostrando).

Archivo: `App.kt`.

## 3. Colección `notifications` sin límite/purga — APLICADO

`NotificationRepository.getNotifications` sigue sin `limit` (no hay cursor de
paginación real en el endpoint que usa), pero se añade
`NotificationRepository.purgeOldRead(householdId, all, maxAgeMillis)`: borra
(DELETE REST, mismo patrón que el resto del repo) las notificaciones con
`read == true` y `createdAt` anterior a 90 días. Nunca borra nada no leído ni
nada más reciente que 90 días.

Se invoca desde `NotificationPollWorker.pollHousehold`, reutilizando la
llamada a `getNotifications` que el propio sondeo YA hacía (se refactorizó
para traer la lista completa (`all`) una vez y derivar `mine` de ahí, en vez
de que `getNotifications` se llamara solo para `mine`) — **sin fetch extra**,
solo los DELETE cuando hay algo que purgar (normalmente ninguno, tras la
primera pasada). Se ejecuta para TODOS los hogares del ciclo (no solo 1): al
no añadir ninguna lectura de red adicional, el coste extra es despreciable
frente a limitarlo a 1 hogar/ciclo, y así el backlog inicial se resuelve en
un único ciclo de 30 min en vez de N. Guardado por el mismo criterio de
pertenencia (`isRealMember`) que ya usa el resto del Worker — no se toca
`firestore.rules` (la regla actual `allow read, write: if isMember(hid) ||
isOwner(hid)` ya permite el DELETE).

Archivos: `NotificationRepository.kt`, `NotificationPollWorker.kt`.

## 4. Idioma de la notificación según el LECTOR — APLICADO

- `NotificationResponse` gana `titleKey: String?`, `messageKey: String?`,
  `messageParams: Map<String, String>?` (todos opcionales, `null` en
  notificaciones antiguas). `FirestoreParsers.toNotificationResponse` los lee
  si existen.
- `NotificationRepository.createNotification` acepta estos 3 parámetros
  opcionales y los persiste (`messageParams` como `mapValue` de
  `stringValue`s) además de `title`/`message` (que se SIGUEN guardando
  traducidos al idioma de quien escribe, como fallback para datos antiguos o
  sin claves).
- `TaskRepository.assignTask` y `FirestoreRepository.regenerateNextAssignment`
  ahora guardan `titleKey = "notification_task_assigned_title"` y
  `messageKey` = `"notification_task_assigned_body_prefix"` (+
  `messageParams = {"taskTitle": ...}`) o `"notification_task_assigned_body_generic"`
  según corresponda.
- `HouseholdRepository.sendMessage` guarda `titleKey =
  "notification_new_message_title"`; `messageKey` queda `null` a propósito —
  el cuerpo (`"$authorName: $preview"`) es contenido de usuario, no
  traducible, tal y como confirmó el panel.
- Nuevo `ui/i18n/NotificationText.kt`: `title()`/`message()` resuelven el
  texto con `AppStrings.get(key, lang)` (idioma del LECTOR) si hay
  `titleKey`/`messageKey`, si no caen a `title`/`message` tal cual
  (retrocompatible).
- `NotificationListScreen` (render in-app) y `NotificationPollWorker`
  (notificación del sistema) usan `NotificationText` con el idioma del
  dispositivo que muestra (`appSettings.currentLanguage` /
  `settingsStore.getLanguage()` ya resuelto en el Worker), no el de quien
  escribió.

Archivos: `DTOs.kt`, `FirestoreParsers.kt`, `NotificationRepository.kt`,
`TaskRepository.kt`, `FirestoreRepository.kt`, `HouseholdRepository.kt`,
`ui/i18n/NotificationText.kt` (nuevo), `NotificationListScreen.kt`,
`NotificationPollWorker.kt`.

## 5. `revertTaskCompletion` no restaura `nextDueAt` — APLICADO

`TaskRepository.revertTaskCompletion`/`FirestoreRepository.revertTaskCompletion`
ganan el parámetro `previousNextDueAt: Long?` y ahora también hacen PATCH del
campo `nextDueAt` (con `NULL_VALUE` si es `null`, igual que el resto de
campos "revert"). `TaskScreenModel.UndoState` gana el campo
`previousNextDueAt` (capturado de `task.nextDueAt` ANTES de completar, igual
que `previousLastCompletedDate`/`previousCompletedBy`) y `undoCompleteTask()`
lo pasa al deshacer.

Archivos: `TaskRepository.kt`, `FirestoreRepository.kt`, `TaskScreenModel.kt`.

## 6. `reassignTaskCompletion` transfiere `task.points` en vez de puntos otorgados — APLICADO

`FirestoreRepository.reassignTaskCompletion` ahora localiza el registro de
`taskHistory` de esa compleción concreta (`findTaskHistoryRecord`, ya
existía, usado por `updateTaskHistoryMember`) y transfiere `record.points`
(los puntos REALMENTE otorgados, que pueden ser menores que la configuración
actual de la tarea por penalización de retraso). `taskPoints` (el parámetro
que llega de `TaskDetailScreen` con `state.task.points`) queda como fallback
solo para compleciones legacy sin registro de historial.

Archivo: `FirestoreRepository.kt`.

## 7. `reconcileHouseholds` race última-escritura-gana — APLICADO

`HouseholdRepository.reconcileHouseholds` podaba con un `store.removeHousehold(id)`
por hogar dado de baja, uno por cada `async` en paralelo — cada llamada hacía
su propio read-modify-write sobre la MISMA lista sin serializar, así que la
última en escribir podía resucitar un hogar que otra coroutine del mismo
lote ya había podado.

Fix: nuevo `HouseholdStore.replaceSavedHouseholds(list)` (una sola
escritura, idempotente). `reconcileHouseholds` ahora calcula `survivorList` y
`prunedIds` DESPUÉS de `awaitAll()` (por comparación de IDs, no mutando
ninguna colección compartida desde dentro de las coroutines paralelas — eso
habría sido la misma clase de carrera) y hace UNA sola llamada a
`replaceSavedHouseholds` si hubo poda.

Archivos: `HouseholdStore.kt`, `HouseholdRepository.kt`.

## 8. `CalendarSyncManager` duplicado de calendario en carrera — APLICADO

`GoogleCalendarRepository.ensureCalendar` hace "buscar por nombre, si no
existe crear", pero esas dos llamadas HTTP no son atómicas: dos coroutines
del mismo dispositivo llamando a `ensureCalendarId` casi a la vez (p. ej.
`onTaskAssigned` y `reconcile` disparados juntos al abrir la app) podían
buscar antes de que ninguna hubiera creado todavía, y las dos crear un
calendario duplicado.

Fix: `Mutex` en `CalendarSyncManager` alrededor de `ensureCalendarId`, con
"double-checked locking" (recomprobar la caché tras adquirir el lock, por si
otra coroutine ya terminó de crear y cachear mientras esta esperaba). No
cubre carreras entre DISTINTOS dispositivos con la misma cuenta (un `Mutex`
en memoria de proceso no puede).

Archivo: `CalendarSyncManager.kt`.

## 9. `HomeScreenModel.loadHouseholdPreview` duplica fetch — APLICADO

`loadAllTasks()` ahora guarda en `rawTasksByHousehold` (nuevo campo privado)
la lista CRUDA de tareas por hogar que ya trae (antes descartaba todo salvo
el subconjunto ya filtrado por "pendiente"). `loadHouseholdPreview(householdId)`
espera (`loadAllTasksJob?.join()`) a que un `loadAllTasks()` en curso
termine — es el mismo Job, así que es un no-op si ya terminó o si nunca se
llamó — y si hay datos cacheados para ese hogar los reutiliza aplicando el
MISMO filtro que antes (`lastCompletedDate == null || == 0L`, `take(5)`),
sin ningún fetch de red. Si no hay caché (household añadido después de la
carga inicial, o `loadHouseholdPreview` llamado sin `loadAllTasks` previo)
cae al fetch propio de antes — comportamiento sin cambios en ese borde.

Resultado visible idéntico (mismos previews); con N hogares pasa de 2N a N
lecturas de `tasks` en el camino normal (`HomeScreen` llama a `loadAllTasks()`
y luego, por cada hogar, a `loadHouseholdPreview`).

Archivo: `HomeScreenModel.kt`.

## 10. `StatsScreenModel.loadStats`: paralelizar `getMemberAchievements` — APLICADO

`getMemberAchievements(householdId, memberId)` no depende de `tasks`/
`assignments`/`history`/`members` ni de `computeStats` — solo de los
parámetros ya conocidos al entrar a la función. Se lanza como un `async` más
junto a los otros 3 en vez de después de `computeStats`, y se cancela
explícitamente (`achievementsDeferred.cancel()`) en la rama "miembro no
encontrado" para no dejarlo corriendo sin necesidad. Mismo resultado, una
lectura menos en el camino crítico.

Archivo: `StatsScreenModel.kt`.

## 11. `QrCodeImage.onError` string hardcodeado — APLICADO

El único call site (`HouseholdDialogs.kt`) no pasa `onError`, así que el
`"Error al generar QR"` hardcodeado en español nunca se mostraba (el default
era un no-op que ni siquiera leía el string). Se simplificó la firma de
`onError` de `(String) -> Unit` a `() -> Unit` — elimina el literal muerto en
vez de moverlo a `AppStrings` para una i18n que ningún caller usa hoy.

Archivo: `QrCodeImage.kt`.

## 12. `role`/`stateDescription` en controles clicables custom — APLICADO

- `ExpandableSectionHeader`: `clickable(role = Role.Button, ...)` +
  `Modifier.semantics { stateDescription = ... }` con las claves nuevas
  `state_expanded`/`state_collapsed` (ES/EN) — antes solo el
  `contentDescription` del icono chevron hijo daba alguna pista de expandido/
  colapsado. El orden de modificadores (clickable antes de `.then(modifier)`,
  fix de la ronda anterior) se conserva: `semantics` se insertó DESPUÉS de
  `clickable`, sin afectar el área de toque.
- `HouseholdTaskSection#TaskRow` y `HouseholdMemberList` (fila de miembro):
  `role = Role.Button` en su `clickable`.
- `SettingsSheet#RadioOptionRow`: migrado de `clickable` genérico a
  `Modifier.selectable(selected, role = Role.RadioButton, onClick)` — más
  correcto que `role = Role.Button` para un grupo de radio-opciones (tema/
  idioma/tema del widget), expone también el estado `selected` a TalkBack/
  VoiceOver.

Sin cambio visual en ningún caso.

Archivos: `ExpandableSectionHeader.kt`, `HouseholdTaskSection.kt`,
`HouseholdMemberList.kt`, `SettingsSheet.kt`, `AppStrings.kt`.

## 13. `TaskCsvExporter` sin test — APLICADO

Nuevo `commonTest/.../ui/models/TaskCsvExporterTest.kt` (7 tests, mismo
patrón sin mocks que `AssignmentCompletionRulesTest`): cabecera, "Nunca"/0
compleciones, fecha formateada/1 compleción, mapeo de las 4 etiquetas de
frecuencia, escapado de comillas dobles, neutralización de CSV formula
injection (`=`/`+`/`-`/`@`), inclusión de puntos.

## 14. `checkAndAwardAchievements` extraíble a función pura + test — APLICADO

Nuevo `AchievementChecker.countCompletedFromHistory(history, memberId)`
(función pura, `ui/models/Achievement.kt`): encapsula el conteo "desde
`taskHistory`, no desde `assignments`" que corrigió la ronda anterior (panel
2026-09-04, Experto 8). `TaskScreenModel.checkAndAwardAchievements` la usa en
vez del `.count { }` inline — sin cambio de comportamiento.

Nuevo `commonTest/.../ui/models/AchievementCheckerTest.kt` (6 tests): cubre
`countCompletedFromHistory` (incluida la compleción penalizada a 0 puntos,
el caso que motivó el fix de la ronda anterior) y `checkNewAchievements`
(que ya era pura pero tampoco tenía test): primer logro, no reabrir un logro
ya desbloqueado, "madrugador" según la hora, varios logros a la vez.

## Verificación

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL` — sin errores; solo warnings de deprecación preexistentes
(Google Sign-In, Vibrator, EncryptedSharedPreferences/MasterKey), ninguno
introducido por esta ronda.

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` — **173 tests, 0 fallos** (160 previos + 7 de
`TaskCsvExporterTest` + 6 de `AchievementCheckerTest`).

## Sin aplicar

Ninguno de los 14 ítems de la lista aprobada quedó sin aplicar.

## Archivos tocados

```
composeApp/src/androidMain/kotlin/org/taskhub/NotificationPollWorker.kt
composeApp/src/commonMain/kotlin/org/taskhub/App.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreParsers.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/HouseholdRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/NotificationRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/TaskRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/models/DTOs.kt
composeApp/src/commonMain/kotlin/org/taskhub/platform/QrCodeImage.kt
composeApp/src/commonMain/kotlin/org/taskhub/storage/HouseholdStore.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/ExpandableSectionHeader.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdMemberList.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdTaskSection.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/SettingsSheet.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/NotificationText.kt (nuevo)
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/Achievement.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/CalendarSyncManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HomeScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/StatsScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/NotificationListScreen.kt
composeApp/src/commonTest/kotlin/org/taskhub/ui/models/TaskCsvExporterTest.kt (nuevo)
composeApp/src/commonTest/kotlin/org/taskhub/ui/models/AchievementCheckerTest.kt (nuevo)
```
