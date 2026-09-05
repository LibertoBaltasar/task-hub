# Panel de expertos — notificaciones (tarea asignada + mensaje nuevo) — 2026-09-05

Encargo: dejar el flujo de notificaciones FUNCIONANDO end-to-end — "cuando se
te asigna una tarea, o cuando hay un mensaje en uno de los grupos familiares
en los que estás, debe aparecer una notificación asociada a eso". Recurrencia
explícitamente FUERA DE ALCANCE (el usuario dice que ya está solucionada).

HEAD de partida: `4b5d9ef` (fix: subir targetSdk a 36), árbol de trabajo
limpio. Leídos antes de empezar: `docs/review-panel-expertos-2026-09-04.md`,
`docs/correcciones-2026-09-04-arquitectura-ux-tests.md`,
`docs/correcciones-2026-09-04-integridad-seguridad.md` y `git log --oneline
-20` — ninguno de los tres documentos toca notificaciones; no hay hallazgos
previos que duplicar.

Metodología: implementación directa (leyendo primero todo el código
relevante: `NotificationRepository`, `NotificationScreenModel`,
`TaskRepository.assignTask`, `HouseholdScreenModel.sendMessage`,
`TaskHubFirebaseMessagingService`, `TaskReminderScheduler`/
`NotificationScheduler`, `SettingsSheet`/`SettingsStore`, `AndroidManifest`,
`MainActivity`) seguida de una ronda de 6 subagentes en paralelo —uno por
rol— que revisaron el DIFF ya aplicado (no solo el diseño en abstracto) y
cuyos hallazgos reales se aplicaron en una segunda pasada, con
recompilación + tests tras cada pasada. Cada experto verificó sus hallazgos
contra el código real antes de reportarlos.

## Resumen ejecutivo

**Gaps cerrados (APLICA YA):**
- **Gap A** (mensaje nuevo en el chat de un hogar no notificaba a nadie):
  `HouseholdRepository.sendMessage` ahora crea una notificación
  (`households/{id}/notifications`) para cada miembro del hogar excepto el
  autor.
- **Gap B** (nada entregaba las notificaciones al dispositivo fuera de la
  pantalla abierta): WorkManager periódico en Android (30 min, requiere red)
  que sondea las notificaciones de cada hogar guardado localmente y muestra
  una notificación local del sistema con deep link a la tarea o al chat.
  Decisión de arquitectura: **polling, no push FCM real** (ver sección
  dedicada más abajo).
- **Gap C** (textos nuevos hardcodeados en español): todo pasa por
  `AppStrings` (ES/EN); de paso se corrigieron dos strings YA existentes que
  incumplían esto mismo (`assignTask`, `regenerateNextAssignment`).

**Encontrado y corregido durante la ronda de revisión del panel** (no en la
implementación inicial, sino gracias a los 6 expertos revisando el diff ya
aplicado): un riesgo de pérdida silenciosa de notificaciones por desfase de
reloj entre dispositivos (rediseño del marcador de sondeo), un fallo parcial
en el envío de notificaciones de chat que dejaba destinatarios sin notificar,
una fuga de privacidad potencial hacia miembros expulsados de un hogar
(mitigada en el Worker), pérdida permanente de notificaciones si el permiso
del sistema estaba denegado, falta de sincronía entre "leída en la app" y
"ya no se repite como notificación del sistema", y una prioridad de canal de
Android inconsistente con el resto de la app. Todos estos ítems se detallan
por experto más abajo y están **APLICADOS**.

**Decisión de arquitectura — mecanismo de entrega (Jefe de arquitectura):**
Task Hub no tiene backend propio ni Cloud Functions; el token FCM que se sube
a `users/{uid}` no tiene ningún emisor que lo use. Construir ese emisor
(Cloud Functions con trigger `onCreate` sobre `households/{id}/notifications`
+ Admin SDK para el push dirigido) es la solución "correcta" a largo plazo,
pero es infraestructura NUEVA (proyecto de Cloud Functions, despliegue,
gestión de credenciales de servicio, cold starts, coste) que excede el
alcance de "aplicar ya" de este encargo. El polling con WorkManager (30 min,
`NetworkType.CONNECTED`) es la opción **APLICA YA** correcta: reutiliza
infraestructura que ya existe en el proyecto (WorkManager ya es una
dependencia, ya se usa para los recordatorios de tarea), no requiere ningún
componente de servidor nuevo, y el coste (latencia de hasta 30 min) es
aceptable para un caso de uso familiar no crítico en tiempo real. **Veredicto:
push FCM real queda como SOLO PROPUESTA** para una ronda futura si el
producto decide invertir en backend propio.

---

## Experto 1 — Notificaciones / Push

### CRÍTICO

- **Problema**: el marcador de sondeo original comparaba `createdAt` de las
  notificaciones — un timestamp generado por el RELOJ LOCAL del dispositivo
  que escribe (asignador o autor del mensaje), no un timestamp de servidor.
  **Por qué importa**: si el móvil de quien asigna una tarea tiene el reloj
  adelantado, esa notificación "infla" el marcador del destinatario; una
  notificación genuinamente posterior escrita desde OTRO dispositivo con hora
  correcta (`createdAt` menor que el marcador inflado) nunca se muestra —
  pérdida silenciosa y permanente, no solo un retraso.
  **Fix**: **APLICADO** — se sustituyó el marcador de tiempo por un conjunto
  de IDs de notificación ya vistos (`SettingsStore.getNotifiedNotificationIds`/
  `setNotifiedNotificationIds`), que no depende en absoluto del orden
  temporal para decidir "es nueva", solo de si el ID ya se procesó antes.
  Inmune al desfase de reloj entre dispositivos.

### IMPORTANTE

- **Problema**: `NotificationRepository.getNotifications` trae TODOS los
  documentos de `households/{id}/notifications` sin filtro/`limit`, y no
  existe ningún borrado de notificaciones antiguas en todo el repo.
  **Por qué importa**: la colección crece sin límite para siempre; el coste
  de red de cada sondeo (cada 30 min, por hogar) crece con la antigüedad del
  hogar, en dirección opuesta a lo que busca el intervalo de 30 min.
  **Fix propuesto**: query estructurada (`:runQuery` con `where memberId==`
  + `limit`) en vez de `GET /documents` completo, o una purga periódica de
  notificaciones leídas/antiguas. **SOLO PROPUESTA** (requiere migrar de la
  API REST simple a `structuredQuery`, cambio de mayor superficie).
- **Problema**: el título/mensaje de la notificación se localizan con el
  idioma del dispositivo que ESCRIBE (`settingsStore.getLanguage()` de quien
  asigna/envía), no el de quien lee — y se guardan ya traducidos en
  Firestore.
  **Por qué importa**: en un hogar con miembros en idiomas distintos, el
  destinatario siempre ve el idioma de quien originó la notificación.
  **Confirmado preexistente**: es el MISMO patrón que ya usaba
  `FirestoreRepository` para `member_deleted_name` antes de este encargo —
  no es una regresión nueva, es una característica del modelo de idioma
  "por dispositivo" de toda la app (`SettingsStore.getLanguage()`, no hay
  idioma por miembro en Firestore). **SOLO PROPUESTA** (arreglarlo de verdad
  requiere guardar clave+parámetros en vez de texto ya traducido, y resolver
  en el render — cambio de esquema, no aplicable a un solo punto).

**Confirmado sin problema** (tras revisar el diff real): exclusión de
auto-notificación (`assignedByMemberId`), try/catch best-effort en ambos
flujos de creación de notificación, `enqueueUniquePeriodicWork` + `KEEP`,
canal `IMPORTANCE_HIGH` original (bajado a `DEFAULT` tras el hallazgo de
Accesibilidad, ver más abajo), y el centinela `taskId=""` para "notificación
de chat" están bien aplicados y consistentes en todos los puntos revisados.

---

## Experto 2 — Programador senior

### IMPORTANTE

- **Problema**: `HouseholdRepository.sendMessage` envolvía en un ÚNICO
  try/catch todo el `forEach` sobre los destinatarios — si `createNotification`
  fallaba para un destinatario a mitad de la lista, el resto se quedaba SIN
  notificar y el error se tragaba en silencio.
  **Por qué importa**: rompe la garantía que sí implementaba bien
  `TaskRepository.assignTask` (try/catch por miembro dentro del loop): un
  fallo puntual con UN destinatario no debe cancelar el resto del batch.
  **Evidencia**: `HouseholdRepository.kt` (versión antes de este fix).
  **Fix**: **APLICADO** — try/catch movido dentro del `forEach`, por
  destinatario, relanzando `CancellationException`, igual que
  `TaskRepository.assignTask`.

### MENOR

- **Problema**: el catch por hogar en `NotificationPollWorker.doWork()` era
  100% silencioso (sin log), indistinguible entre "hogar sin red" y un bug
  real.
  **Fix**: **APLICADO** — `Log.w(TAG, "Fallo sondeando hogar $id: ...")`.
- El Worker crea un `MemberRepository`/caché nuevos en cada `doWork()`, así
  que `resolveCurrentMember` nunca aprovecha la memoización entre
  ejecuciones — coste extra de red por hogar cada 30 min. No es un bug (cada
  ejecución del Worker es un proceso/ciclo de vida distinto), **SOLO
  PROPUESTA** si se quisiera cachear el resultado en `SettingsStore` entre
  ejecuciones.

**Confirmado sin problema**: el POST de la propia asignación en
`TaskRepository.assignTask` queda FUERA del try/catch nuevo (solo la
notificación está protegida); concurrencia del Worker sin race real
(`enqueueUniquePeriodicWork` + `KEEP` usa un único work-id, WorkManager
retrasa la siguiente ejecución en vez de solaparla si `doWork()` tarda más de
30 min); `Settings()` sin argumentos confirmado seguro
(`multiplatform-settings-no-arg`, auto-inicializado vía androidx.startup con
el mismo `Application`, sin override en el Manifest); `taskId` en
`NotificationResponse` es `String` no nulo, sin riesgo de null-safety.

---

## Experto 3 — Jefe de arquitectura

Veredicto por los 4 puntos evaluados:

1. **`network` → `AppStrings` (cruce de capas)**: aceptable. `AppStrings` es
   un `object` de puro lookup de strings, sin dependencias de
   Compose/`ui.*`. Confirmado que NO es un patrón nuevo:
   `FirestoreRepository` ya lo usaba para `member_deleted_name` antes de este
   encargo — `TaskRepository`/`HouseholdRepository` solo son consistentes con
   un precedente ya establecido, no abren una grieta nueva.
2. **`HouseholdRepository` → `MemberRepository`+`NotificationRepository`**:
   sin ciclo — confirmado que `MemberRepository` solo depende de
   `FirestoreClient`/`TaskCache`, nunca de `HouseholdRepository` (directa ni
   transitivamente). Coherente con "los repos de dominio no dependen de la
   fachada" (documentado en el KDoc de la clase).
3. **`NotificationPollWorker` construye su grafo a mano (sin Koin)**:
   decisión correcta — confirmado que `koin-androidx-workmanager` NO está en
   `gradle/libs.versions.toml` ni en `build.gradle.kts` (solo dependencias de
   Koin ligadas al árbol de Compose). Añadirlo sería infraestructura nueva
   (otro punto de arranque de Koin) para un único Worker; el wiring manual
   actual (7 líneas, documentadas) es más barato y no tiene downside real.
4. **`App.kt` con `deepLinkHouseholdId`/`deepLinkTaskId`**: lugar correcto —
   es el único punto común entre `MainActivity` (Android-only) y la
   navegación Voyager (multiplataforma); evita que `MainActivity` conozca
   pantallas concretas directamente.

### MENOR

- Re-tocar la MISMA notificación tras navegar y volver atrás no re-dispara
  el `LaunchedEffect` (sus keys no cambian). **Confirmado sin impacto
  práctico**: las notificaciones se crean con `setAutoCancel(true)`, así que
  desaparecen de la bandeja del sistema en cuanto se tocan una vez — este
  escenario no es alcanzable en el uso normal.

No hay hallazgos CRÍTICOS de arquitectura.

---

## Experto 4 — QA / bugs

### Veredicto por caso borde

1. **Auto-asignación** — bug de borde MENOR encontrado y documentado
   (no corregido, ver razón): `createdBy` en `CreateTaskScreen` puede llegar
   vacío (`""`) si el usuario navega a "crear tarea" antes de que
   `currentMemberId` resuelva de forma asíncrona. En ese caso,
   `assignedByMemberId=""` nunca coincide con ningún `memberId` real, y el
   creador SÍ recibe notificación de su propia auto-asignación (falso
   positivo, no falso negativo hacia otro miembro). **SOLO PROPUESTA**: exige
   no permitir crear una tarea con `createdBy` vacío o resolver el miembro de
   forma síncrona antes de navegar a `CreateTaskScreen` — cambio de flujo de
   navegación ajeno al alcance de notificaciones, fuera de esta ronda.
2. **Hogares múltiples** — confirmado correcto: `doWork()` aísla cada
   `pollHousehold` en su propio try/catch; un hogar caído no afecta al resto.
3. **Cambio de cuenta / signOut** — confirmado correcto:
   `clearNotificationPollState()` borra el estado de TODOS los hogares;
   `pollHousehold` con estado vacío fija la base sin notificar, evitando
   volcar el histórico completo de la cuenta entrante.
4. **Miembro expulsado (`leftAt`)** — **bug de privacidad CRÍTICO
   encontrado**: `getMembers()` filtra `leftAt==0L`, así que un miembro
   expulsado desaparece de la lista; `resolveCurrentMemberUncached` (código
   COMPARTIDO, preexistente, usado por toda la app) entonces no encuentra
   coincidencia por identidad y cae al fallback "primer miembro existente"
   — la identidad de OTRO miembro real. Como el sondeo en segundo plano
   ejercita este camino automáticamente para CUALQUIER hogar guardado
   localmente (a diferencia del resto de la app, que solo lo resuelve cuando
   el usuario abre una pantalla de ese hogar), un dispositivo cuyo dueño fue
   expulsado podría mostrar notificaciones dirigidas a otro miembro real.
   **Fix: APLICADO**, acotado al Worker nuevo (sin tocar el
   `resolveCurrentMember` compartido, que sigue usándose igual en el resto de
   la app): antes de confiar en `resolveCurrentMember`, `NotificationPollWorker`
   verifica que la identidad actual (`firestoreClient.currentUserIdentities()`)
   coincida con el `userId` de algún miembro REAL de `getMembers(householdId)`
   — si no hay coincidencia, el hogar se salta por completo (no se sondea).
   El arreglo de fondo del fallback compartido (que afecta también a otras
   pantallas, no solo al sondeo) queda **SOLO PROPUESTA** para una ronda
   dedicada a `MemberRepository`, por el riesgo de tocar código usado en todo
   el árbol bajo el alcance de este encargo.
5. **Sin red / permisos denegados** — **bug encontrado y corregido**: el
   marcador avanzaba incondicionalmente aunque `showUpdateNotification` no
   hubiera mostrado nada por falta de `POST_NOTIFICATIONS` — esas
   notificaciones se perdían para siempre aunque el usuario reactivara el
   permiso después. **Fix: APLICADO** — `NotificationHelper.canShowNotifications()`
   se comprueba UNA vez al principio de `doWork()`; si no hay permiso, no se
   toca ningún estado de sondeo (se reintenta el ciclo completo la próxima
   vez). Fallos de red ya estaban cubiertos (try/catch en `doWork`,
   `orDefault` en los repos).
6. **Dedupe con la lista in-app** — **bug encontrado y corregido**: el
   sondeo no comprobaba el campo `read`; una notificación ya marcada como
   leída en `NotificationListScreen` antes del siguiente ciclo de 30 min
   igualmente disparaba la notificación local del sistema. **Fix: APLICADO**
   — `newOnes` ahora filtra también `!it.read`.

---

## Experto 5 — UX

### IMPORTANTE

- **Problema**: tocar la notificación del sistema navegaba correctamente
  pero nunca llamaba a `markAsRead` — inconsistente con tocar la card en
  `NotificationListScreen`, que sí la marca. El usuario volvía a la lista de
  notificaciones y seguía viéndola como "no leída".
  **Fix: APLICADO** — `NotificationHelper.showUpdateNotification` añade el
  extra `notificationId` al Intent; `MainActivity.consumeDeepLink` lo lee;
  `App.kt` llama a `repo.markNotificationRead(...)` al consumir el deep link
  (best-effort, no bloquea la navegación).
- **Problema**: `settings_notifications_desc` decía "Activar recordatorios de
  tareas" / "Enable task reminders", pero ese mismo interruptor ahora también
  gatea tareas asignadas y mensajes de chat.
  **Fix: APLICADO** — ES: "Recordatorios, tareas asignadas y mensajes
  nuevos"; EN: "Reminders, assigned tasks and new messages".

### MENOR

- **Problema**: en arranque en frío desde una notificación, se ve `HomeScreen`
  un frame antes de la transición animada hacia el destino (doble salto).
  **SOLO PROPUESTA**: inicializar el `Navigator` directamente con la pila
  `[HomeScreen(), destino]` en vez de hacer `push` tras montar — requiere
  confirmar que la API de Voyager en uso soporta pila inicial de más de un
  `Screen`; no aplicado esta ronda por el riesgo de tocar la inicialización
  de navegación bajo presión de tiempo, dado que es un efecto puramente
  cosmético (un frame + una transición).

**Confirmado sin problema**: emoji+tono de `📋 Tarea asignada`/`💬 Nuevo
mensaje` coherente con `⏰ Recordatorio de tarea` ya existente; dejar el
cuerpo del mensaje de chat sin traducir (`"$authorName: $preview"`) es la
decisión correcta, es contenido del usuario, no UI; el destino de navegación
(`taskId` vacío → chat, si no → detalle de tarea) es coherente entre la card
in-app y el deep link del sistema.

---

## Experto 6 — Accesibilidad

### IMPORTANTE

- **Problema**: el canal `task_hub_updates` usaba `IMPORTANCE_HIGH` para
  TANTO "tarea asignada" como "mensaje de chat nuevo" — igual que
  `task_reminders` (plazos que vencen), pero el mismo evento lógico "tarea
  asignada" ya se notifica también vía FCM en
  `TaskHubFirebaseMessagingService` con el canal `fcm_general` en
  `IMPORTANCE_DEFAULT`.
  **Por qué importa**: mismo tipo de evento produce heads-up+vibración por un
  camino y silencioso por otro; y un mensaje de chat (no tan urgente como un
  plazo) con HIGH es una interrupción excesiva.
  **Fix: APLICADO** — `CHANNEL_ID_UPDATES` bajado a `IMPORTANCE_DEFAULT`,
  reservando HIGH solo para `task_reminders` (plazos), unificando criterio
  con `fcm_general`.

### MENOR

- Card clicable en `NotificationListScreen` con el `TextButton` "Marcar como
  leída" anidado dentro: **confirmado sin conflicto real** — el `TextButton`
  mantiene su propio nodo de semántica, TalkBack lo anuncia como parada
  independiente y el gesto de toque lo consume el hijo antes que el `Card`.
  Mismo patrón ya usado por `TaskCard` (`TaskListScreen.kt`) y por
  `ExpandableSectionHeader` (`Card`/fila con `clickable` plano sin
  `role = Role.Button`, más un control anidado) — coherente con la
  convención ya establecida en el proyecto, no una regresión.

Sin elementos interactivos nuevos sin `contentDescription`. Sin hallazgos
CRÍTICOS de accesibilidad.

---

## Aplicado en esta ronda

### Gap A — mensaje nuevo en el chat

- **`network/HouseholdRepository.kt`** — `sendMessage` gana dependencias
  `MemberRepository`/`NotificationRepository`/`SettingsStore`; tras guardar
  el mensaje, crea una notificación para cada miembro del hogar excepto el
  autor, con try/catch POR destinatario (fix del hallazgo del Programador
  senior).

### Gap B — entrega al dispositivo (polling + deep link)

- **`androidMain/NotificationPollWorker.kt`** (nuevo) — `CoroutineWorker`
  que construye su propio grafo de dependencias (sin Koin, justificado),
  verifica pertenencia real al hogar antes de confiar en
  `resolveCurrentMember` (fix del hallazgo de privacidad de QA), respeta el
  interruptor de notificaciones y el permiso `POST_NOTIFICATIONS` (sin
  perder notificaciones si el permiso está denegado), y usa un conjunto de
  IDs ya notificados (no un marcador de tiempo) para decidir qué es nuevo.
- **`androidMain/TaskHubApplication.kt`** — registra el WorkManager periódico
  (`enqueueUniquePeriodicWork`, 30 min, `NetworkType.CONNECTED`, `KEEP`).
- **`androidMain/NotificationHelper.kt`** — nuevo canal `task_hub_updates`
  (`IMPORTANCE_DEFAULT`) + `showUpdateNotification`/`canShowNotifications`;
  corregido un bug preexistente en `showTaskReminder` donde los extras del
  deep link (`householdId`/`taskId`) eran código muerto
  (`getLaunchIntentForPackage` casi nunca devuelve null, así que la rama con
  los extras nunca se ejecutaba).
- **`androidMain/MainActivity.kt`** — `consumeDeepLink` (extras
  `householdId`/`taskId`/`notificationId`) en `onCreate` Y `onNewIntent`
  (para cuando la Activity ya está viva).
- **`commonMain/App.kt`** — nuevos parámetros `deepLinkHouseholdId`/
  `deepLinkTaskId`/`deepLinkNotificationId`; tras resolver `HomeScreen`,
  hace `push` a `TaskDetailScreen`/`HouseholdScreen` según corresponda y
  marca la notificación como leída (fix del hallazgo de UX).
- **`commonMain/storage/SettingsStore.kt`** — marcador de sondeo por hogar
  rediseñado como conjunto de IDs (`getNotifiedNotificationIds`/
  `setNotifiedNotificationIds`/`clearNotificationPollState`) tras el hallazgo
  CRÍTICO de desfase de reloj.
- **`commonMain/ui/models/GoogleAuthManager.kt`** — `signOut()` limpia el
  estado de sondeo de todos los hogares.
- **`commonMain/ui/screens/NotificationListScreen.kt`** — la card ahora es
  clicable: marca como leída y navega a la tarea o al chat según
  corresponda (coherencia con el deep link del sistema).

### Gap C — i18n

- **`ui/i18n/AppStrings.kt`** — nuevas claves ES/EN:
  `notification_task_assigned_title`, `notification_task_assigned_body_prefix`,
  `notification_task_assigned_body_generic`, `notification_new_message_title`,
  `notification_channel_updates_name`, `notification_channel_updates_desc`;
  `settings_notifications_desc` actualizada para reflejar el alcance real del
  interruptor (fix de UX).
- **`network/TaskRepository.kt`** (`assignTask`) y
  **`network/FirestoreRepository.kt`** (`regenerateNextAssignment`) —
  migrados de strings hardcodeadas en español (`"📋 Tarea asignada"`, etc.,
  YA existentes antes de este encargo) a `AppStrings`.

### Otros fixes de programador senior/QA aplicados dentro del mismo diff

- `TaskRepository.assignTask` gana `assignedByMemberId` (no te notifica tu
  propia asignación) y un try/catch por miembro alrededor de la creación de
  notificación (antes, un fallo ahí propagaba la excepción pese a que la
  asignación ya se había creado). `TaskScreenModel` pasa `createdBy`/
  `_currentMemberId` según el flujo, y ahora también pasa el `taskTitle` real
  al auto-asignar en la creación de tarea (antes quedaba en `""`, mostrando
  siempre el texto genérico).

## Verificación (OBLIGATORIO)

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL` — sin errores; solo warnings de deprecación preexistentes
(Google Sign-In, Vibrator, EncryptedSharedPreferences/MasterKey), ninguno
introducido por esta ronda. Verificado DOS veces: tras la implementación
inicial y tras aplicar los hallazgos del panel.

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` — **160 tests, 0 fallos** (mismo recuento que antes de
esta ronda; no se añadieron tests nuevos — el código nuevo vive en
`androidMain` sin motor HTTP inyectable, ver Experto 13 de rondas anteriores
sobre esa limitación general del proyecto).

## PROPUESTAS pendientes — resumen para el usuario

1. Push FCM real vía Cloud Functions (en vez de polling) — requiere backend
   nuevo, decisión de producto (Jefe de arquitectura).
2. `NotificationRepository.getNotifications` sin filtro/`limit` — colección
   sin purga, coste de red crece con el tiempo (Notificaciones/Push,
   IMPORTANTE).
3. Idioma de la notificación fijado por quien la escribe, no por quien la
   lee — mismo patrón preexistente en `member_deleted_name`, requiere
   guardar idioma por miembro (Notificaciones/Push, IMPORTANTE).
4. `createdBy` puede llegar vacío a `CreateTaskScreen` si se navega antes de
   resolver el miembro actual — auto-notificación falsa al crear tarea en
   ese borde (QA, MENOR).
5. Fallback "primer miembro existente" de `resolveCurrentMemberUncached`
   (código compartido, preexistente) — mitigado solo en el Worker nuevo;
   una ronda dedicada a `MemberRepository` debería revisar si otras
   pantallas están expuestas al mismo riesgo con un miembro expulsado
   (QA, hallazgo derivado del CRÍTICO ya mitigado).
6. Doble salto visual (Home → destino) en arranque en frío desde una
   notificación — cosmético, requiere confirmar soporte de pila inicial
   multi-pantalla en la versión de Voyager en uso (UX, MENOR).
