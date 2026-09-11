# Ronda de deuda aplicable — 2026-09-12

Aplicación punto por punto del encargo de la misma fecha, verificando cada
uno contra el código real antes de tocarlo (HEAD de partida: `3aeeb69`, v0.7.29).
Formato: qué se aplicó (con `archivo:línea`), qué no y por qué exacto, tests
añadidos, y el resultado de compilación/tests al final.

## A. Bugs de funcionalidad

### A1 — `completeTask`/`completeAssignment`: reconciliación de puntos tras fallo parcial

**Aplicado.** Mecanismo automático de reconciliación en carga, con
idempotencia por `(taskId, completedAt)`:

- `TaskHistoryResponse.pointsApplied: Boolean = true`
  (`network/models/DTOs.kt`) — `false` mientras el otorgamiento de puntos no
  se ha confirmado; `true` por defecto para registros legacy (el código
  previo otorgaba puntos ANTES de guardar el historial, así que su sola
  existencia ya implicaba puntos aplicados).
- `FirestoreRepository.completeTask` (`network/FirestoreRepository.kt:988`) y
  `.completeAssignment` (`:1642`) se REORDENARON: ahora guardan el registro
  de `taskHistory` PRIMERO (`pointsApplied=false`), otorgan los puntos
  DESPUÉS, y marcan `pointsApplied=true` al final
  (`TaskRepository.markTaskHistoryPointsApplied`, `network/TaskRepository.kt`).
  Antes el orden era al revés (puntos primero), lo que hacía ambigua la
  ausencia de historial ("¿fallaron los puntos, o solo el guardado del
  historial tras haberlos otorgado?") — ambigüedad que habría hecho insegura
  cualquier reconciliación automática.
- `TaskReconciliation.findTasksNeedingPointsReconciliation` (nuevo archivo
  `network/TaskReconciliation.kt`, pura, sin I/O): detecta tareas
  `completedBy`/`lastCompletedDate` fijados sin registro de historial
  correspondiente, o con `pointsApplied=false`.
- `FirestoreRepository.reconcileMissingTaskPoints`/`reconcileTaskPoints`
  (`:1267`, `:1310`): repara cada candidata — si no hay registro, recalcula
  el resultado con `PenaltyRules` y crea uno; si lo hay pero
  `pointsApplied=false`, usa los puntos ya calculados. Ambos casos otorgan
  puntos y marcan `pointsApplied=true`; nunca duplica en reintentos SALVO la
  ventana residual documentada en el propio KDoc (ver más abajo).
- `TaskScreenModel.loadTasks` (`ui/models/TaskScreenModel.kt:233`) dispara la
  reconciliación tras publicar `Success`, solo si hay candidatos en memoria
  (sin coste de red si nadie tiene `completedBy` fijado).
- `firestore.rules` v8: `taskHistory/{thid}` gana el campo `pointsApplied` y
  su `allow update` gana una rama que permite a CUALQUIER miembro (no solo
  admin/owner) voltear ESE campo de `false` a `true` — sin esto, la
  compleción NORMAL de un miembro sin rol admin habría empezado a fallar con
  403 en el nuevo paso 3 de `completeTask`/`completeAssignment`.

**Ventana residual documentada, no eliminada** (ver KDoc de
`reconcileTaskPoints`): si `addMemberPoints` tiene éxito pero el PATCH de
`markTaskHistoryPointsApplied` posterior falla, una reconciliación futura
vería `pointsApplied=false` de nuevo y otorgaría los puntos una segunda vez.
Eliminarla del todo requeriría el `:commit` transaccional ya evaluado y
descartado en `docs/atomicidad-commit-pendiente.md` — fuera de alcance de
esta ronda (ver sección D).

**Tests:** `network/TaskReconciliationTest.kt` (6 casos: nunca completada,
completada con historial aplicado, sin historial, con historial no aplicado,
historial de otra compleción, caso mixto).

### A2 — `redeemReward`: compensación tras fallo parcial

**Aplicado.** `RewardsRepository.deleteRedemption` (nuevo, `network/RewardsRepository.kt`).
`FirestoreRepository.redeemReward` (`:2125`): si `createRedemption` tiene
éxito pero el descuento de puntos posterior falla, se borra el registro de
canje huérfano en el `catch` antes de relanzar la excepción original.
**Garantía que cambia** (documentada en el propio código): ya no queda
rastro auditable de un intento fallido, pero tampoco puede haber doble
registro con un único descuento. Si el borrado de compensación también
falla, se prioriza relanzar el error original (best-effort, como el resto
del archivo).

### A3 — Chat/comentarios: texto perdido y lista sustituida por error

**Aplicado** en ambos flujos:

- `TaskCommentsScreenModel.addComment`/`sendCommentError`
  (`ui/models/TaskCommentsScreenModel.kt`): el borrador se RESTAURA si falla
  el envío (antes se perdía), y el error se expone en un `StateFlow`
  independiente (`sendCommentError`) sin pisar `commentsState` (que sigue
  mostrando la lista ya cargada). `TaskDetailScreen.kt` renderiza un banner
  descartable sobre el campo de comentario.
- `HouseholdScreenModel.sendMessage`/`sendMessageError`
  (`ui/models/HouseholdScreenModel.kt`): mismo patrón. `HouseholdChatSection.kt`
  gana el parámetro `sendMessageError`/`onDismissSendMessageError` y renderiza
  el mismo tipo de banner.

### A4 — `undoCompleteTask` sin estado de error observable

**Aplicado.** `TaskScreenModel.undoError`/`clearUndoError`
(`ui/models/TaskScreenModel.kt:471`, función en `:644`): el `catch` que antes
era completamente silencioso ahora publica el mensaje. `TaskListScreen.kt`
lo muestra en el mismo `SnackbarHost` que el snackbar de deshacer.

### A5 — Banner "creador no resuelto" sin retry + rama 3 de `ensureAuth()` sin try/catch

**Aplicado** — ambas partes son la MISMA ruta de código:
`CreateTaskScreen.kt`, `LaunchedEffect(householdId)` llama a
`taskModel.resolveCurrentMemberId(householdId)`, que internamente puede
alcanzar la rama 3 de `FirestoreClient.ensureAuth()` (alta anónima nueva) —
antes esa llamada no tenía try/catch en el `LaunchedEffect`, así que un fallo
ahí (red, respuesta incompleta de Firebase) podía propagar una excepción no
capturada fuera de la corrutina de Compose. Ahora `resolveCreator()` (función
local nueva) envuelve la llamada en try/catch, expone `creatorResolveError`,
y el banner cambia de "cargando" (spinner) a "error + botón Reintentar"
(`common_retry`) en vez de quedarse atascado para siempre.

### A6 — Retry inconsistente entre pantallas de error de red

**Aplicado** en las 2 que faltaban (`NotificationListScreen` ya lo tenía):

- `CalendarScreen.kt`, rama `TaskListUiState.Error`: botón "Reintentar" nuevo
  que llama a `model.loadTasks(householdId)`.
- `HouseholdChatSection.kt`, rama `MessagesUiState.Error`: botón "Reintentar"
  nuevo que reutiliza `onRefresh` (ya existía como callback del icono de
  refrescar).

Ninguno de los dos pisa una lista ya cargada (la rama `Error` de sus propios
`sealed class` solo se alcanza cuando NO hay una lista previa que mostrar —
ver punto A3 para el caso distinto de "error de ENVÍO con lista ya cargada").

## B. Decisiones de producto/seguridad

### B7 — `donatePoints`/`appreciateMember` libre entre iguales

**Aplicado.** `firestore.rules` v8 (cabecera del archivo y
`firestore.rules:274` función `isPeerPointsTransfer`, aplicada en
`allow update` de `members/{mid}`, `firestore.rules:373`): cualquier
`isMember(hid)` puede escribir en el documento de OTRO miembro, acotado a
`totalPoints` únicamente (`diff().affectedKeys()`), solo al alza, con un
tope de 1000 puntos por escritura (mitigación de seguridad, no límite de
producto — ver comentario en el propio archivo sobre la ventana no-atómica
entre débito y crédito), y `request.auth.uid != mid` (el receptor nunca
puede auto-acreditarse). Antes, esa rama solo la cubría `isTrusted(hid)`, así
que un miembro normal recibía 403 al agradecer/donar a otro no-admin (el
código cliente ya tenía el catch defensivo para ese 403, ver comentarios
"panel de revisión 2026-09-10, Experto 9" en `MemberRepository`, pero no lo
evitaba).

**Cliente: sin cambios necesarios.** Verificado que la UI de "agradecer/donar"
(`HouseholdMemberList.kt`, botones en `MemberCard`) YA es visible para
cualquier miembro (`canTransfer = myMember != null`, sin gate por `isAdmin`)
— solo oculta sobre uno mismo (`!isSelf`). El pedido de "ajusta lo que haga
falta en el cliente" ya estaba satisfecho.

**No verificado contra Firestore emulator/producción** (sin credenciales en
este entorno): la sintaxis de `diff().affectedKeys()` se revisó a mano contra
la documentación de Firestore Security Rules y es consistente con el resto
del archivo, pero el orquestador debe desplegar y confirmar antes de dar por
bueno el comportamiento real.

### B8 — Sucesión de `ownerId`: admin más antiguo primero

**Aplicado.** `HouseholdRules.resolveOwnerSuccessor`
(`network/HouseholdRules.kt`): antes elegía sin más al miembro con cuenta
vinculada MÁS ANTIGUO de cualquier rol. Ahora prioriza admins con cuenta
vinculada (el más antiguo entre ellos); si no hay ninguno, cae al miembro con
cuenta vinculada más antiguo de cualquier rol; si no hay ninguno con cuenta,
`null` (documentado en el propio KDoc: el hogar queda sin owner operable
hasta que alguien se vincule). El caller (`FirestoreRepository.leaveHousehold`)
ya promocionaba al sucesor a `admin` si no lo era — sin cambios ahí, sigue
siendo correcto con la nueva prioridad.

**Tests añadidos:** 4 nuevos en `network/HouseholdRulesTest.kt` (admin gana a
child más antiguo, el más antiguo entre varios admins, fallback sin admins,
admin sin cuenta se ignora).

**No aplicado / fuera de alcance:** expulsión del OWNER por un admin (vía
`deleteMember`) no transfiere `ownerId` — no se tocó porque el encargo habla
de "abandono/eliminación del owner" (que mapea a `leaveHousehold`, la única
ruta que ya orquestaba esta transferencia); si la UI permite expulsar al
propio owner es un escenario distinto y más especulativo, no confirmado
como posible hoy, y se documenta aquí en vez de ampliar el alcance sin
confirmación explícita.

### B9 — TTL de retención de 90 días para `taskHistory` y mensajes de chat

**Aplicado**, mismo patrón que `NotificationRepository.purgeOldRead`:

- `TaskRepository.purgeOldTaskHistory` y `HouseholdRepository.purgeOldMessages`
  (nuevas funciones) — best-effort, DELETE secuencial (sin límite de
  concurrencia, a diferencia del cascade-delete de `deleteHousehold`).
- `RETENTION_90_DAYS_MILLIS` (`network/FirestoreRepository.kt`, constante
  compartida).
- Llamadas desde `StatsScreenModel.loadStats` (taskHistory, ya trae la
  colección completa) y `HouseholdScreenModel.loadMessages` (mensajes, ídem)
  — ambas DESPUÉS de publicar el estado de éxito, en un `try/catch`
  best-effort que nunca puede convertir una carga correcta en error.
- **Hallazgo durante la verificación:** `NotificationRepository.purgeOldRead`
  YA estaba desplegado y en uso — no vía la fachada `FirestoreRepository`
  (que nunca la delegaba), sino directamente desde
  `androidMain/NotificationPollWorker.kt:154` (worker de WorkManager, con su
  propia constante local `NOTIFICATION_MAX_AGE_MILLIS = 90 días`). Se
  documenta la corrección: el encargo decía "mismo patrón que
  notificaciones" asumiendo que ya pasaba por la fachada; no era así. Se
  añadió igualmente `FirestoreRepository.purgeOldNotifications` y se conectó
  también desde `NotificationScreenModel.loadNotifications`, así que ahora la
  purga de notificaciones ocurre tanto en el sondeo de fondo (cada ~30 min)
  como al abrir la pantalla de notificaciones interactivamente.

**Coste documentado** (en el KDoc de cada función): 1 DELETE por registro
purgado; el primer ciclo tras desplegar puede purgar un backlog considerable
(las colecciones crecían sin límite hasta ahora); en ciclos posteriores, solo
lo que acaba de cruzar el umbral. Para miembros SIN rol admin/owner, la regla
de `delete` de `taskHistory`/`notifications` (ya existente, sin tocar) solo
permite borrar el registro PROPIO — la purga de registros de OTROS miembros
solo se completa cuando un admin/owner abre la pantalla correspondiente;
best-effort, autocorregible.

### B10 — Notificaciones de chat sin anonimizar al abandonar/expulsar

**Aplicado, con un diseño mejor que el literal del encargo.** En vez de
añadir una rescritura por lotes tipo `anonymizeMemberMessages` (que solo
corrige las notificaciones YA CREADAS en el momento del abandono, dejando sin
cubrir cualquier notificación de chat previa cuyo autor luego se va), se
implementó **resolución en el RENDER**, tal como pedía literalmente el
encargo ("guarda authorMemberId... y resuelve el nombre en el render"):

- `NotificationResponse.authorMemberId` (nuevo campo) +
  `messageParams["preview"]` (el texto SIN el nombre del autor incrustado) se
  guardan en `HouseholdRepository.sendMessage` al crear la notificación de
  cada destinatario.
- `NotificationText.message` (`ui/i18n/NotificationText.kt`) gana un
  parámetro opcional `resolveAuthorName: ((memberId) -> String?)?`: si se
  indica y la notificación es de chat, compone `"$name: $preview"` con el
  nombre ACTUAL del miembro (no el congelado en `message`); si el resolver
  devuelve `null` (miembro ya no existe — abandono voluntario, que hace
  borrado duro del documento), usa el placeholder `member_deleted_name`.
- `NotificationListScreen.kt` carga la lista de miembros
  (`MemberScreenModel`) y pasa un resolver basado en ella.

Esto cubre automáticamente AMBOS casos sin ninguna rescritura adicional:
- Expulsión (`MemberRepository.deleteMember`): soft-delete que ya pone
  `displayName = "Miembro eliminado"` en el documento del miembro — el
  resolver lo recoge solo.
- Abandono voluntario (`FirestoreRepository.leaveHousehold`): borra el
  documento del miembro — el resolver devuelve `null` → placeholder.

### B11 — `orDefault` swallow-to-empty: caché de respaldo

**Aplicado** (rewards/historial, como pedía el encargo):

- `TaskCache` (`storage/TaskCache.kt`) gana caché cache-first para
  `taskHistory`, `rewards`, `rewardRedemptions` y `notifications` (mismo
  patrón que tareas/miembros ya existente), con invalidación en cada
  escritura correspondiente.
- `TaskRepository.getTaskHistory`, `RewardsRepository.getRewards`/
  `getRewardRedemptions`, `NotificationRepository.getNotifications`: ya NO
  usan `orDefault(emptyList())` — ahora cache-first (un 404/403 sigue siendo
  señal definitiva y se relanza, igual que `getTasks`).
- `RewardsRepository`/`NotificationRepository` ganaron un parámetro
  `TaskCache` en el constructor — actualizado en los 4 sitios de
  construcción: `FirestoreRepository` (default), `AppModule.kt` (Koin), y
  `NotificationPollWorker.kt` (Android, construcción manual).

**Evaluado y NO aplicado, documentado en el propio código (`MemberRepository.kt`):**
- `isMember`: debe FALLAR CERRADO (`false`) ante fallo — es una comprobación
  de ACCESO, servir un `true` cacheado obsoleto podría dejar entrar a un
  miembro ya expulsado.
- `getUserProfile`: `null` ya es un resultado válido y frecuente ("perfil
  aún no creado"); cachear introduciría la MISMA ambigüedad que esta ronda
  busca resolver en otras lecturas.
- `getMemberAchievements`: alimenta una comprobación de escritura condicional
  (`checkAndAwardAchievements`) — `emptySet()` ante fallo ya es el
  comportamiento seguro (evita re-otorgar), sin el sesgo de "logro real
  oculto" que introduciría una caché stale ahí.
- `loadUserHouseholds`: es un mecanismo de DESCUBRIMIENTO de hogares de otro
  dispositivo (`GoogleAuthManager.syncHouseholdsToCloud`), no la lista local
  del dispositivo (esa ya vive en `HouseholdStore`, con su propia
  resiliencia); cachearlo duplicaría esa responsabilidad sin un escenario
  real que lo justifique.

### B12 — `GoogleAuthManager.syncHouseholdsToCloud` sin serializar

**Aplicado.** `syncHouseholdsJob: Job?` (nuevo campo,
`ui/models/GoogleAuthManager.kt`) cancela la sincronización anterior en
curso antes de lanzar una nueva — mismo patrón que `TaskScreenModel.loadTasksJob`.
Un único punto de fix dentro de la propia función cubre los 4 call-sites de
`HouseholdScreenModel.kt` (además del interno).

## C. Limpiezas mecánicas

### C13 — `ButtonDefaults.buttonColors(containerColor = primary)` redundante

**Aplicado.** Eliminadas las 23 ocurrencias reales (20 en una línea + 3 en
formato multi-línea que un primer barrido con `sed` de una sola línea no
capturó — `TaskListScreen.kt`, `WelcomeScreen.kt`, `TaskDetailScreen.kt` —
detectadas y corregidas al toparme con una de ellas durante el trabajo de
C16). Verificado que las 3 restantes con `ButtonDefaults.buttonColors(...)`
en `WelcomeScreen.kt` NO se tocaron por tener colores explícitos distintos
del default (`primaryContainer`/`onPrimaryContainer`).

### C14 — Duplicación "Dialog+Surface+SettingsSheet" en 6 pantallas

**NO APLICABLE — premisa no confirmada contra el código real.** Verificado
`archivo:línea` de las 6 pantallas citadas:
- `TaskListScreen.kt:173-192` — SÍ tiene `Dialog+Surface+SettingsSheet`.
- `CalendarScreen.kt:676-686` — tiene un `Dialog+Surface` con la misma FORMA
  (mismos modifiers/shape), pero el contenido es `DayTasksPopup` (detalle de
  tareas de un día), NO `SettingsSheet`.
- `MemberRewardScreen.kt`, `NotificationListScreen.kt`, `StatsScreen.kt`,
  `RankingScreen.kt` — ninguna de las 4 tiene NINGÚN `Dialog(...)` ni
  `SettingsSheet` (verificado por grep de `SettingsSheet|Dialog(|DialogProperties`
  en cada archivo: 0 coincidencias).

No hay una duplicación de 6 vías que extraer. Lo único real es una
COINCIDENCIA DE FORMA entre 2 sitios (`TaskListScreen`/`CalendarScreen`) con
contenido distinto — extraer un wrapper genérico "modal sheet grande" de solo
2 usos con contenido distinto habría sido inventar un alcance distinto al
pedido; se deja documentado en vez de reinterpretar la tarea.

### C15 — Estados de carga con `ShimmerPlaceholder`/`ShimmerList`

**Aplicado** en las 2 pantallas de LISTA que aún usaban
`CircularProgressIndicator` genérico y encajan 1:1 con el patrón ya usado en
`TaskListScreen`/`HouseholdScreen`/`HomeScreen`/`RankingScreen`:
- `NotificationListScreen.kt`, rama `NotificationUiState.Loading`.
- `RewardListScreen.kt`, rama `RewardUiState.Loading`.

**NO aplicado, documentado:**
- `StatsScreen.kt` (dashboard de tarjetas/gráficas de forma variada, no una
  lista de filas homogéneas — un `ShimmerList` no sería "claramente
  equivalente" a la forma final, sería forzar una forma que no representa el
  contenido real).
- `MemberRewardScreen.kt` — su `CircularProgressIndicator` es un spinner
  DENTRO de un botón de acción (canjear) mientras se procesa, no un estado
  de carga de LISTA; no hay equivalente razonable.
- El resto de apariciones de `CircularProgressIndicator` en pantallas de
  detalle/formulario (`TaskDetailScreen`, `CreateTaskScreen`, `EditTaskScreen`,
  etc.) son spinners de carga de un ÚNICO recurso o de envío de formulario,
  no listas — fuera del alcance "claramente equivalente" pedido.

### C16 — Botón anidado dentro de card clicable (TalkBack)

**NO aplicado — documentado con la pauta exacta**, por no poder verificarse
en este entorno (sin dispositivo/emulador con lector de pantalla). Ubicaciones
exactas del patrón:
- `NotificationListScreen.kt:238-241` (`Card` con
  `.clickable(role = Role.Button, onClick = onClick)`) contiene un
  `TextButton` anidado en `:300-303` (`onMarkRead`, "Marcar como leída").
- `TaskListScreen.kt:850-860` (`TaskCard`, `Card` con
  `.clickable(enabled = !isCompleting, role = Role.Button, onClick = onClick)`)
  contiene un `Button` anidado en `:892` ("Hecho", `onComplete`).

**Pauta exacta:** Android Accessibility developer guide — "Avoid nested
actionable/focusable elements"
(https://developer.android.com/guide/topics/ui/accessibility/apps#nested-controls);
el remedio estándar en Compose es sustituir el control anidado por un
elemento no interactivo visualmente idéntico y exponer su acción como
`customActions` en `Modifier.semantics { customActions = listOf(CustomAccessibilityAction(label, action)) }`
sobre el `Card` exterior, de forma que TalkBack exponga un único punto de
foco con la acción secundaria accesible desde su menú local (gesto arriba/abajo
en el borde derecho de la pantalla). Verificar con Accessibility Scanner o un
pase real de TalkBack antes de aplicar: un cambio mal hecho puede empeorar la
experiencia (ocultar la acción a usuarios que no conocen el gesto de
`customActions`) en vez de mejorarla — por eso se documenta en vez de
aplicarse a ciegas.

### C17 — Tests que faltan (TOP-5 restantes)

**Aplicado, ambos sub-puntos:**
- `NotificationPollRules.selectNewNotifications` (nuevo,
  `network/NotificationPollRules.kt`): extraída de
  `NotificationPollWorker.pollHousehold` (androidMain) la selección de
  `newOnes` (filtra no-vistas y no-leídas, ordena por `createdAt`). Test:
  `network/NotificationPollRulesTest.kt` (6 casos).
- `MemberResolutionRules.resolveExistingMemberId` (nuevo,
  `network/MemberResolutionRules.kt`): extraídos los pasos 1-2 (los
  puramente decisionales, sin I/O) de
  `MemberRepository.resolveCurrentMemberUncached` — el paso 3 (crear
  miembro nuevo, I/O) se queda donde estaba. Cubre explícitamente el caso
  que el propio comentario del código marcaba como el bug histórico
  (panel 2026-09-11: no robar en silencio la identidad de OTRO miembro
  real). Test: `network/MemberResolutionRulesTest.kt` (5 casos, incluido el
  caso exacto del bug).

## D. Decisiones del orquestador — no tocadas

Sin cambios, tal como se pidió: canjes sin balance-check, UMP/consentimiento
EEE, splash/logo fijo, FCM sin emisor, polling sin lifecycle-awareness,
`getNotifications` structuredQuery/paginación, retry/backoff genérico en
`FirestoreClient`, `tryAuthOrApiKey`, `consumeDeepLink`, notificaciones/borrado
de asignaciones en serie, god objects, clamp server-side de puntos/rachas y
atomicidad `:commit`.

## Tests añadidos (resumen)

- `network/TaskReconciliationTest.kt` — 6 tests (A1).
- `network/HouseholdRulesTest.kt` — 4 tests nuevos (B8).
- `network/NotificationPollRulesTest.kt` — 6 tests (C17).
- `network/MemberResolutionRulesTest.kt` — 5 tests (C17).

## Resultado de compilación y tests

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
BUILD SUCCESSFUL

./gradlew :composeApp:jvmTest --console=plain
BUILD SUCCESSFUL
tests=210 skipped=0 failures=0 errors=0
```

(Baseline antes de esta ronda: 199 tests verdes; +11 tests nuevos de esta
ronda, 0 fallos.)

## Nota sobre `firestore.rules`

Los dos cambios de reglas (B7 `isPeerPointsTransfer`, A1 rama de
`pointsApplied`) se revisaron a mano por sintaxis y consistencia con el resto
del archivo, pero NO se desplegaron ni verificaron contra el emulador o
producción (sin credenciales de Firebase en este entorno) — el orquestador
despliega y verifica después, como ya establece el encargo.
