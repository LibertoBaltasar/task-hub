# Flujos principales de Task Hub

Este documento traza, paso a paso y con referencias `archivo:línea` verificadas
contra el código real, los flujos de usuario más importantes de la app:
Screen (Voyager) → ScreenModel → Repositorio (`network/`) → Firestore REST /
API externa. No repite lo que ya cubren `docs/ARQUITECTURA.md` (visión
general del stack, DI, navegación) ni `docs/MODELO-DATOS.md` (colecciones,
campos, `firestore.rules` regla a regla) — remite a ellos cuando aplica.

Las líneas citadas corresponden al estado del código en el momento de
escribir este documento; si el archivo cambia, pueden desactualizarse.

## 1. Alta de hogar: crear / unirse por código de invitación

**Crear un hogar nuevo** (rol admin por defecto):

1. `ui/screens/WelcomeScreen.kt:116` — botón "Crear hogar" → `navigator.push(CreateHouseholdScreen())`.
2. `ui/screens/CreateHouseholdScreen.kt:108` — botón enviar → `model.createHousehold(householdName.trim())`.
3. `ui/models/HouseholdScreenModel.kt:66-85` — `createHousehold()`: llama a `repo.createHousehold(name)`, guarda el hogar en `HouseholdStore` (línea 71) y dispara `authManager.syncHouseholdsToCloud()` (línea 72).
4. `network/FirestoreRepository.kt:311-312` — delega en `householdRepository.createHousehold(...)`.
5. `network/HouseholdRepository.kt:65-103` — `createHousehold()`: genera el `inviteCode` con `generateInviteCode()` (línea 68, ver más abajo), fija `ownerId = firestoreClient.getLocalId()` (línea 69), hace `POST .../households` (líneas 80-84) y publica el mapa `invites/{code} → householdId` con `createInvite()` (línea 91, definida en líneas 180-188).
6. `network/HouseholdRepository.kt:465-468` — `generateInviteCode()`: código aleatorio de 8 caracteres (`A-Z0-9`) usando `secureRandomInt` (`platform/Platform.kt`, CSPRNG por plataforma) — no un `Random` predecible.
7. Al crearse el hogar, `CreateHouseholdScreen.kt:43-47` navega con `navigator.replaceAll(CreateProfileScreen(household.id))`.
8. `ui/screens/CreateProfileScreen.kt:28-37` (KDoc) y línea 51 — el selector de rol arranca en `"admin"` (`var role by remember { mutableStateOf("admin") }`): quien llega aquí es, por construcción, el `ownerId` del hogar recién creado, así que puede elegir rol libremente (coincide con la rama `isOwner(hid)` de `firestore.rules`, ver `docs/MODELO-DATOS.md` §3).
9. `ui/screens/CreateProfileScreen.kt:179` — botón enviar → `memberModel.addMember(householdId, displayName.trim(), role, userId = userId)`.
10. `ui/models/MemberScreenModel.kt:128-147` — `addMember()` → `repo.createMember(...)`.
11. `network/MemberRepository.kt:148-235` — `createMember()`: si `userId != null`, el documento del miembro se crea con `documentId = userId` (líneas 199-208) para que las reglas puedan verificar membresía con `exists()`; escribe `role` tal cual (línea 174, sin forzar nada en este camino).

**Unirse a un hogar existente por código** (rol `child` forzado):

1. `ui/screens/WelcomeScreen.kt:134` — botón "Unirse" → `navigator.push(JoinHouseholdScreen())`.
2. `ui/screens/JoinHouseholdScreen.kt:142` — envía el código → `householdModel.joinHousehold(inviteCode.trim())`.
3. `ui/models/HouseholdScreenModel.kt:87-114` — `joinHousehold()`: llama a `repo.joinHousehold(inviteCode)`, guarda el hogar en caché local y, si `repo.isCurrentUserMember(household.id)` ya es `true` (línea 97), pasa a `HouseholdUiState.AlreadyMember` en vez de re-crear el perfil.
4. `network/FirestoreRepository.kt:650` — delega en `householdRepository.joinHousehold(inviteCode)`.
5. `network/HouseholdRepository.kt:316-327` — `joinHousehold()`: resuelve `código → householdId` con `GET invites/{code}` (líneas 318-320, sin `list`, ver `docs/MODELO-DATOS.md` §3 sobre por qué el código actúa como secreto) y lanza `IllegalStateException("Código de invitación inválido")` si el documento no tiene `householdId` (línea 322-323); luego lee el hogar con `getHousehold(householdId)` (línea 326).
6. `ui/screens/JoinHouseholdScreen.kt:225` — al crear el perfil: `memberModel.addMember(joinedHouseholdId!!, displayName.trim(), "child", userId = userId, inviteCode = inviteCode.trim())` — el rol se pasa literal `"child"` (comentario líneas 221-224: "nadie se autoproclama admin al unirse").
7. `network/MemberRepository.kt:195-197` — `createMember()` incluye el campo `inviteCode` en el documento **solo** cuando se auto-da-de-alta (línea 195-197): `firestore.rules` valida ese código contra el `inviteCode` real del hogar antes de aceptar la creación (rama "auto-alta con código de invitación", ver `docs/MODELO-DATOS.md` §3, `households/{hid}/members/{mid}`) — el rol `"child"` está reforzado también ahí server-side, no solo en la UI.

## 2. Gestión de tareas: CRUD, asignación, recurrencia, completado, puntos y rachas

**Crear tarea + asignación inicial:**

1. `ui/screens/CreateTaskScreen.kt:188-206` — botón guardar → `taskModel.createTask(...)` con `memberIds = selectedMembers.toList()`.
2. `ui/models/TaskScreenModel.kt:323-406` — `createTask()`: llama a `repo.createTask(...)` (línea 347); si `memberIds` está vacío, auto-asigna a **todos** los miembros del hogar (líneas 367-371, `repo.getMembers(householdId).map { it.id }`); llama a `repo.assignTask(...)` (línea 373) y sincroniza Calendar con `syncCalendarOnAssigned` (línea 382); si hay `dueDate`, programa un recordatorio local con `notificationScheduler.scheduleReminder(...)` (línea 387).
3. `network/FirestoreRepository.kt:851-872` — `createTask()` delega tal cual en `taskRepository.createTask(...)`.
4. `network/TaskRepository.kt:151-234` — `createTask()`: si la tarea es recurrente (`daily`/`weekly`/`monthly`), calcula `nextDueAt` con `computeNextDueAt()` (línea 208, definida en líneas 127-140) — **la recurrencia se expande aquí, en el cliente, en el momento de crear/completar**, no como N documentos por ocurrencia (modelo "sin instancias", ver `docs/MODELO-DATOS.md` §2, `tasks/{tid}`).
5. `network/RecurrenceRules.kt:208-260` — `nextOccurrence()`: pura, sin I/O; calcula la medianoche local del próximo día programado según `frequency`/`recurrenceDays`/`recurrenceDay`, con `clampDayOfMonth()` (líneas 34-38) para días de mes cortos (31 en abril → 30, etc.).
6. `network/TaskRepository.kt:361-433` — `assignTask()`: crea un documento por miembro en `tasks/{tid}/assignments` y, salvo que el asignado sea quien asigna (`memberId != assignedByMemberId`, línea 401), crea una notificación vía `notificationRepository.createNotification(...)` (líneas 408-423) — ver Flujo 4.

**Completar una tarea (puntos + racha):**

> Actualizado en el panel de expertos v16 (2026-09-24, hallazgo I8): la
> versión anterior de esta sección describía `completeTask()` como
> orquestación 100% client-side dentro de `FirestoreRepository.kt` — eso ya
> **no** es así. Desde la migración documentada en
> `docs/recurrencia-backend-cloud-functions-diseno-2026-09-11.md`, otorgar
> puntos/historial/asignaciones es responsabilidad de una Cloud Function
> transaccional real (`functions/src/completeRecurringTask.ts`), no del
> cliente. Ver también `docs/ARQUITECTURA.md` §8 (nota sobre el backend).

1. `ui/screens/TaskDetailScreen.kt:182-187` — botón "Completar" → `model.completeTask(householdId, taskId)`.
2. `ui/models/TaskScreenModel.kt:494-628` — `completeTask()`: resuelve el miembro actual, publica un `UndoState` OPTIMISTA (con `completedAt = 0L`, antes de conocer el resultado real — panel v16 hallazgo C4: si el usuario deshace en ese margen, el undo espera al resultado real en vez de perderse, ver `completeTaskJob`/`pendingCompletion`) y llama a `repo.completeTask(householdId, taskId, memberId, task)`.
3. `network/FirestoreRepository.kt:1129-1164` — `completeTask()`: ya NO contiene la lógica de negocio; delega en una única llamada a la Cloud Function callable `completeRecurringTask` vía `cloudFunctionsClient.call(...)`, invalidando la caché local (`TaskCache`) en `finally` (un timeout no distingue "nunca llegó" de "se aplicó pero se perdió la respuesta").
4. `functions/src/completeRecurringTask.ts` — la transacción real (Firestore `runTransaction`, todo o nada):
   - Valida que el llamador y el `memberId` destino sean miembros activos del hogar (`auth.ts`, `loadActiveMember`).
   - Guard de concurrencia optimista OBLIGATORIO sobre `expectedLastCompletedDate` (panel v16 hallazgo C1/C3: antes se saltaba cuando el valor era `null`, permitiendo duplicar puntos con dos dispositivos completando casi a la vez una tarea nunca completada) + guard de "no completar la misma tarea dos veces el mismo día de calendario" (cierra el vector de farmear puntos con llamadas directas repetidas al endpoint, sin pasar por `isDueToday` del cliente).
   - Calcula `effectiveDueDate` (`completionHelpers.ts`, equivalente a `RecurrenceRules.endOfDueDay()`) y el resultado con `resolveCompletionOutcome()` (`penalty.ts`, port de `network/PenaltyRules.kt` — **REGLA DE ORO**: debe mantenerse idéntico al `.kt` original a mano, sin test de paridad automatizado, ver `docs/review-panel-expertos-v16-2026-09-24.md` hallazgo de arquitectura).
   - Escribe atómicamente: puntos del miembro (`FieldValue.increment`), el registro de `taskHistory`, cierra como `"completed"` **todas** las asignaciones `"assigned"` de ese ciclo (no solo la del `memberId` que recibe los puntos — "cualquier miembro puede completar cualquier tarea"), y si es recurrente, regenera la asignación del siguiente ciclo respetando `assignmentRotation` (`resolveNextAssignmentDecision()`, `rules.ts`).
5. `ui/models/TaskScreenModel.kt:598-606` — tras el resultado, efectos best-effort encadenados (ninguno puede convertir la acción en error, ya que el servidor ya otorgó los puntos): cancelar el recordatorio pendiente, sincronizar Calendar, actualizar racha (`updateMemberStreak`, aún client-side — la función no la toca) y comprobar logros (`checkAndAwardAchievements`). Panel v16 hallazgo C5: el interstitial de AdMob (`adController.maybeShowInterstitial()`) se omite si el miembro que completó es un perfil `role == "child"`.

**Deshacer una compleción:**

1. `ui/screens/TaskDetailScreen.kt` (snackbar "Deshacer" tras completar) → `model.undoCompleteTask()`.
2. `ui/models/TaskScreenModel.kt:665-708` — `undoCompleteTask()`: si `completeTask()` sigue en vuelo (`completedAt == 0L` en el `UndoState` optimista), espera su resultado real (`completeTaskJob?.join()`) antes de decidir — panel v16 hallazgo C4. Llama a `repo.undoTaskCompletion(...)`, que ahora devuelve `Boolean` (`reverted`) — solo revierte la racha/da feedback háptico si el servidor confirmó `reverted == true` (panel v16 hallazgo I10: antes se revertía la racha aunque el servidor hiciera un no-op idempotente).
3. `network/FirestoreRepository.kt:1200-1214` — `undoTaskCompletion()`: delega en la Cloud Function callable `undoTaskCompletion`.
4. `functions/src/undoTaskCompletion.ts` — transacción real: el SERVIDOR deriva el estado previo leyendo el registro de `taskHistory` inmediatamente anterior (no depende de un `UndoState` volátil en memoria del cliente). Idempotente: si el registro ya no existe (undo repetido desde dos dispositivos), devuelve `{ reverted: false }` sin fallar. Panel v16 hallazgo C2: exige que quien deshace sea el propio autor de la compleción (`historyRecord.memberId`) o `isTrusted` (owner/admin) — antes cualquier miembro activo del hogar podía deshacer compleciones ajenas, restando puntos de otros miembros sin permiso.

## 3. Recompensas y ranking

**Crear una recompensa** (admin/owner):

1. `ui/screens/CreateRewardScreen.kt:300` — `memberModel.createReward(...)`.
2. `ui/models/MemberScreenModel.kt:227-252` — `createReward()` → `repo.createReward(householdId, title, description, cost, icon, createdBy)` (línea 238).
3. `network/FirestoreRepository.kt:1928` → delega en `network/RewardsRepository.kt:47-77` (`createReward`): escribe el documento en `households/{id}/rewards`.

**Canjear una recompensa:**

1. `ui/screens/MemberRewardScreen.kt:248-253` — al confirmar el diálogo → `memberModel.redeemReward(householdId, rewardId, memberId, pointsSpent = reward.cost)`.
2. `ui/models/MemberScreenModel.kt:271-289` — `redeemReward()` (con guarda anti doble-tap en línea 277) → `repo.redeemReward(...)` (línea 281).
3. `network/FirestoreRepository.kt:1941-1971` — `redeemReward()`: **valida saldo contra una lectura fresca** del miembro (líneas 1956-1960, `if (member.totalPoints < pointsSpent) throw ...`), luego `rewardsRepository.createRedemption(...)` (línea 1965, escribe primero el registro auditable) y por último `addMemberPoints(householdId, memberId, -pointsSpent)` (línea 1968, descuenta puntos). `firestore.rules` refuerza además que `pointsSpent == cost` real de la recompensa en el momento del canje (ver `docs/MODELO-DATOS.md` §3, `rewardRedemptions`).

**Ranking:** no hay cálculo de servidor ni colección dedicada — es un ordenamiento puro en cliente.

1. `ui/screens/RankingScreen.kt:39-41` — `memberModel.loadMembers(householdId)` (reutiliza el mismo `MemberScreenModel` que ya carga `ExploreScreen`).
2. `ui/screens/RankingScreen.kt:44-48` — `members = (...).sortedByDescending { it.totalPoints }` — el ranking es literalmente `getMembers()` (que ya filtra a quienes abandonaron, `MemberRepository.kt:134`) ordenado por `totalPoints` descendente; `currentStreak` se muestra en cada fila (línea 228) pero no participa en el orden.

## 4. Notificaciones (flujo completo)

**Escritura del lado del emisor** (dos orígenes posibles):

- Asignar tarea → `network/TaskRepository.kt:401-429` (`assignTask`, ver Flujo 2) crea `households/{id}/notifications/{nid}` vía `notificationRepository.createNotification(...)`, **salvo que el asignado sea quien asigna** (línea 401: `if (memberId != assignedByMemberId)` — auto-exclusión del autor).
- Enviar mensaje de chat → `network/HouseholdRepository.kt:343-409` (`sendMessage`): tras guardar el mensaje, itera `recipients = memberRepository.getMembers(householdId).filter { it.id != memberId }` (línea 371 — **auto-exclusión del autor**, filtra al propio remitente) y crea una notificación por destinatario (líneas 377-401), con `try/catch` **por destinatario** (comentario líneas 372-376) para que un fallo puntual no deje sin notificar al resto.
- Ambos casos usan `network/NotificationRepository.kt:42-87` (`createNotification`): escribe `title`/`message` ya traducidos (fallback) más `titleKey`/`messageKey`/`messageParams` opcionales para que el **lector** vea el texto en su propio idioma (`ui/i18n/NotificationText.kt`).

**Entrega al dispositivo (sin backend/push real, solo polling):**

1. `composeApp/src/androidMain/.../TaskHubApplication.kt:72-86` — `scheduleNotificationPolling()`: registra `NotificationPollWorker` como `PeriodicWorkRequest` de 30 minutos (`enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)`, líneas 81-85), con `NetworkType.CONNECTED` como constraint.
2. `composeApp/src/androidMain/.../NotificationPollWorker.kt:44-87` — `doWork()`: respeta el interruptor local de notificaciones (línea 52) y el permiso `POST_NOTIFICATIONS` (línea 60); construye sus propias dependencias (no usa Koin, comentario líneas 31-38) y sondea cada hogar guardado en `HouseholdStore` (líneas 62-85).
3. `NotificationPollWorker.kt:89-171` — `pollHousehold()`: verifica pertenencia REAL antes de confiar en `resolveCurrentMember` (líneas 97-118, evita filtrar notificaciones ajenas a un expulsado); purga notificaciones leídas con más de 90 días (`purgeOldRead`, línea 130); calcula `newOnes = mine.filter { it.id !in seenIds && !it.read }` (línea 149) contra `seenIds = settingsStore.getNotifiedNotificationIds(householdId)` (línea 135) — **el marcador de "ya notificado" es un conjunto de IDs**, no un timestamp (KDoc en `storage/SettingsStore.kt:240-252`: un timestamp es vulnerable a relojes de dispositivo desincronizados entre quien crea la notificación y quien la recibe).
4. `NotificationPollWorker.kt:155-168` — por cada notificación nueva, `NotificationHelper.showUpdateNotification(...)`.
5. `composeApp/src/androidMain/.../NotificationHelper.kt:66-111` — `showUpdateNotification()`: crea un `Intent` hacia `MainActivity` con extras `householdId`/`taskId`/`notificationId` (líneas 79-87; `taskId` vacío = centinela de "es un mensaje de chat", comentario líneas 62-64) y publica la notificación del sistema en el canal `CHANNEL_ID_UPDATES`.
6. `composeApp/src/androidMain/.../MainActivity.kt:51-55` — `consumeDeepLink(intent)`: lee esos tres extras a `mutableStateOf` (líneas 47-49) para que Compose recomponga si `onNewIntent` llega con la Activity ya viva (línea 143-147).
7. `App.kt:52-56` — `App(deepLinkHouseholdId, deepLinkTaskId, deepLinkNotificationId)` recibe los tres parámetros desde `MainActivity.kt:134-140`.
8. `App.kt:197-215` (arranque en frío) y `App.kt:252-269` (`LaunchedEffect` para deep link llegado con la app ya viva vía `onNewIntent`) — en ambos casos: si `deepLinkTaskId` es nulo/vacío navega a `HouseholdScreen(hid)`, si no a `TaskDetailScreen(hid, deepLinkTaskId)`; y si `deepLinkNotificationId` no es nulo/vacío, llama a `repo.markNotificationRead(hid, deepLinkNotificationId)` (líneas 207 y 262).
9. `network/FirestoreRepository.kt:1916-1917` → delega en `network/NotificationRepository.kt:101-113` (`markNotificationRead`): `PATCH` con `updateMaskFieldPaths("read")`.

**Marcar como leída desde la lista in-app** (ruta alternativa, sin deep link):

- `ui/screens/NotificationListScreen.kt:120,124` — al tocar una notificación → `model.markAsRead(householdId, notification.id)`.
- `ui/models/NotificationScreenModel.kt:64-84` — `markAsRead()`: llama a `repo.markNotificationRead(...)` y actualiza el `StateFlow` local en memoria (líneas 69-77) sin esperar a un recarga completa.

## 5. Calendario y sincronización con Google Calendar

**OAuth y refresco de token** (Android):

1. `composeApp/src/androidMain/.../MainActivity.kt:105` — `GoogleCalendarAuthHelper.register(this)` registra el `ActivityResultLauncher` de consentimiento en `onCreate`.
2. `ui/models/GoogleAuthManager.kt:290-294` — `ensureCalendarAccessToken()`: pide un token efímero (~1h) vía la función `expect` `getGoogleCalendarAccessToken()` (`platform/Platform.kt`) cada vez que hace falta — no se persiste como de larga duración.
3. `composeApp/src/androidMain/.../GoogleCalendarAuthHelper.kt:66-91` — `getAccessToken()`: usa `GoogleAuthUtil.getToken(...)` (línea 90), que **cachea/refresca el token de forma transparente** a nivel de Google Play services; si hace falta consentimiento explícito la primera vez, captura `UserRecoverableAuthException` (línea 70) y lanza el `Intent` de consentimiento vía `awaitConsent()` (líneas 93-104, serializado con un `Mutex` para no perder una segunda solicitud concurrente).
4. `ui/models/GoogleAuthManager.kt:302-311` — `linkCalendar()`: si no hay sesión de Google, primero llama a `signIn()` (línea 304) y espera el resultado antes de pedir el token de Calendar.

**Qué se sincroniza** (`ui/models/CalendarSyncManager.kt`, KDoc líneas 11-24): solo las **asignaciones del usuario actual** con `dueDate > 0`, una hacia un calendario dedicado por espacio ("Tareas personal" o "Tareas {hogar}", línea 34-35). El campo `googleEventId` en la asignación (Firestore) marca "ya sincronizada" — Google Calendar en sí no usa Firestore (ver `docs/MODELO-DATOS.md` §1).

1. `CalendarSyncManager.kt:80-110` — `onTaskAssigned()`: al crear/reasignar, crea un evento por cada asignación propia con fecha.
2. `CalendarSyncManager.kt:119-122` — `onTaskCompleted()`: al completar, borra el evento vinculado (ya no tiene sentido).
3. `CalendarSyncManager.kt:181-217` — `reconcile()`: al abrir un hogar/Personal, hace *backfill* de asignaciones propias con fecha que aún no tienen `googleEventId` — llamado desde `ui/screens/HouseholdScreen.kt:127` y `ui/screens/PersonalSpaceScreen.kt:54`.
4. `CalendarSyncManager.kt:51-73` — `ensureCalendarId()`: cachea el `calendarId` en `SettingsStore`, protegido con `ensureCalendarMutex` (double-checked locking, línea 58-62) para no crear un calendario duplicado si dos coroutines lo piden a la vez.
5. `network/GoogleCalendarRepository.kt:26-60` — habla directamente con `www.googleapis.com/calendar/v3` (no Firestore); `ensureCalendar()` busca por nombre y crea si no existe (idempotente).

## 6. Mensajería del hogar (chat)

1. `ui/screens/HouseholdScreen.kt:185,188` — `householdModel.loadMessages(householdId)` al entrar en la pantalla y en cada refresco.
2. `ui/models/HouseholdScreenModel.kt:206-222` — `loadMessages()` → `repo.getMessages(householdId)` → `HouseholdUiState`-hermano `MessagesUiState.Success(messages)`.
3. `ui/components/HouseholdChatSection.kt:28-36` — composable con lista + campo de envío; conectado desde `HouseholdScreen.kt:574-580` (`onSend = { householdModel.sendMessage(householdId, currentMemberId) }`).
4. `ui/models/HouseholdScreenModel.kt:224-245` — `sendMessage()`: limpia el campo de texto de forma **optimista antes** de la llamada de red (línea 229, comentario: evita duplicar por doble-tap), resuelve `authorName` desde `getMembers()` (líneas 232-234) y llama a `repo.sendMessage(...)` (línea 235).
5. `network/HouseholdRepository.kt:343-409` — `sendMessage()`: escribe el documento en `households/{id}/messages` y crea notificaciones para todos los miembros salvo el autor (ver Flujo 4).

## 7. Espacio personal

1. `App.kt:127-165` — en cada arranque (`LaunchedEffect(Unit)`): `repo.getOrCreatePersonalHousehold()` (línea 134) resuelve o crea el espacio Personal; si falla (offline), cae a un ID guardado localmente o a un placeholder `"personal-offline"` (líneas 140-153).
2. `network/HouseholdRepository.kt:115-170` — `getOrCreatePersonalHousehold()`: usa un ID **determinista** `personalHouseholdId(uid) = "personal_$uid"` (línea 173) — mismo UID de Google en cualquier dispositivo resuelve el mismo hogar (interdispositivo); si dos dispositivos intentan crearlo a la vez, el que pierde la carrera recibe `ALREADY_EXISTS`/409 y simplemente lee el que ya existe (líneas 148-156).
3. `App.kt:157-165` — `repo.ensurePersonalMember(personalId)` asegura que exista un miembro "Yo" en el espacio Personal.
4. `network/FirestoreRepository.kt:679` → `network/MemberRepository.kt:308-316` (`ensurePersonalMember`) delega en `resolveCurrentMember()` (líneas 255-267 y 269-306): si no hay ningún miembro, crea uno `"Yo"` con `role = "admin"` (línea 298) vinculado al usuario actual.
5. `ui/screens/PersonalSpaceScreen.kt:34-55` — pantalla simplificada (sin código de invitación, QR, recompensas, ranking ni gestión de miembros — KDoc líneas 24-32): carga el miembro con `memberModel.loadMembers(householdId)` (línea 49) y hace *backfill* de Calendar con `calendarSync.reconcile(...)` (línea 54).

## 8. Inicio de sesión: Google Sign-In y autenticación anónima

**Autenticación anónima** (por defecto, sin fricción):

1. `network/FirestoreClient.kt:151-214` — `ensureAuth()`: si no hay refresh token de Google ni anónimo válido, hace `POST accounts:signUp` con `returnSecureToken=true` (líneas 192-195, sin email/password) y persiste el `refreshToken` en `SettingsStore` (línea 212).
2. `network/FirestoreClient.kt:224-247` — `refreshFirebaseToken()`: renueva el `idToken` a partir del `refreshToken` guardado (`POST securetoken.googleapis.com/v1/token`) sin crear una identidad nueva — mismo UID entre reinicios/reinstalaciones (con el mismo almacenamiento local).

**Google Sign-In (id_token):**

1. `ui/models/GoogleAuthManager.kt:103-108` — `signIn()`: guarda de reentrancia (línea 104) y llama a la función `expect` `launchGoogleSignIn()`.
2. `composeApp/src/androidMain/.../GoogleSignInHelper.kt:58-82` — `launch()`: `GoogleSignInOptions` con `requestIdToken(WEB_CLIENT_ID)` (línea 60, el Web Client ID de Firebase, no el Android) y `requestScopes(Scope(".../auth/calendar"))` (línea 61); cierra sesión primero (línea 67) para que el selector de cuenta aparezca siempre.
3. `GoogleSignInHelper.kt:105-123` — `handleSignInResult()`: extrae el `idToken` del resultado y lo publica en `GoogleSignInResultHolder` (`""` = cancelado, nunca `null`, comentario líneas 115-117).
4. `ui/models/GoogleAuthManager.kt:70-90` — el `init` observa `GoogleSignInResultHolder.result` y, con un token no vacío, llama a `handleGoogleToken(token)` (línea 84, definida en líneas 321-337).
5. `GoogleAuthManager.kt:322-323` — `handleGoogleToken()` → `repo.signInWithGoogle(googleIdToken)`.
6. `network/FirestoreRepository.kt:146-153` — `signInWithGoogle()`: intercambia el `id_token` de Google por credenciales de Firebase Auth vía `POST accounts:signInWithIdp` con `postBody = "id_token=...&providerId=google.com"` — devuelve el UID **estable** de Google (persiste entre reinstalaciones, a diferencia del anónimo).
7. `GoogleAuthManager.kt:324-329` — tras el intercambio: `settingsStore.setGoogleAuth(...)`, `restoreHouseholds(uid)`, `repointPersonalHousehold()` (el espacio Personal pasa a resolverse por el UID de Google) y `syncGoogleAvatar(result)` (usa la foto de Google solo si el usuario no tiene una propia, líneas 344-356).

## 9. Deep links

Tres formatos, todos hacia `MainActivity` con los mismos extras (`householdId`, `taskId`, opcionalmente `notificationId`):

1. **Notificación de tarea asignada / mensaje nuevo** (`NotificationHelper.showUpdateNotification`, `composeApp/src/androidMain/.../NotificationHelper.kt:79-87`): incluye `notificationId` → al abrirse, `App.kt` marca la notificación como leída en Firestore (Flujo 4). `taskId` vacío = mensaje de chat → abre `HouseholdScreen`; `taskId` no vacío → abre `TaskDetailScreen`.
2. **Recordatorio de tarea con fecha límite** (`NotificationHelper.showTaskReminder`, `NotificationHelper.kt:127-179`, programado por `composeApp/src/androidMain/.../TaskReminderScheduler.kt`): mismos extras `householdId`/`taskId` (líneas 151-155) pero **sin** `notificationId` — no es un documento de `households/{id}/notifications`, así que no hay nada que marcar como leído.
3. **Widget de la pantalla de inicio** (`composeApp/src/androidMain/.../TaskHubWidgetProvider.kt:68-72`): abre `MainActivity` sin ningún extra de deep link (usa `getLaunchIntentForPackage`, con fallback a un `Intent` plano) — simplemente relanza la app al arranque normal (`HomeScreen`), no navega a ningún hogar/tarea concreto.

En los tres casos, `MainActivity.kt:51-55` (`consumeDeepLink`) lee los extras a estado observable y `App.kt:52-56 / 197-215 / 252-269` decide la navegación real (ver Flujo 4 para el detalle paso a paso).

## Ver también

- `docs/ARQUITECTURA.md` — visión general del stack, estructura de paquetes, DI con Koin, navegación con Voyager y el patrón `expect`/`actual` por plataforma.
- `docs/MODELO-DATOS.md` — colecciones y campos de Firestore, relaciones entre documentos y explicación regla a regla de `firestore.rules`.
- `docs/PRIMEROS-PASOS.md` — cómo clonar, compilar y ejecutar el proyecto localmente en cada plataforma.
- `docs/INDICE.md` — índice general de la documentación del proyecto.
