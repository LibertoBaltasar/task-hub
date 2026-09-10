# Panel de expertos — revisión completa v7 (2026-09-10)

Auditoría integral NUEVA de Task Hub (13 subagentes en paralelo, uno por rol),
HEAD de partida `14edd8d` (v0.7.29) — con un commit adicional `ffacab0`
("wip: checkpoint...", ver nota de proceso al final) que ya contenía dos de
los fixes de este mismo informe, aplicados por el coordinador nada más
arrancar la ronda, antes de lanzar el panel.

Método: cada experto leyó `git log --oneline -25` y los dos informes de la
ronda anterior (`docs/auditoria-completa-2026-09-06.md`,
`docs/auditoria-gates-roles-2026-09-06.md`) antes de auditar, para no repetir
hallazgos ya conocidos y centrarse en NUEVOS/regresiones/pendientes. Cada
hallazgo cita archivo:línea verificado contra el código real.

---

## Estado de hallazgos de la auditoría 2026-09-06 (respuesta punto por punto)

1. **Doble conteo de compleciones en Estadísticas** (`StatsScreenModel.computeStats`)
   — **CONFIRMADO que seguía sin fix** al arrancar esta ronda (`allCompletions
   = fromAssignments + fromHistory` sin `distinctBy`, línea ~184). **APLICADO**
   por el coordinador antes de lanzar el panel:
   `.distinctBy { it.taskId to it.completedAt }` en `StatsScreenModel.kt:184-190`.
   Verificado independientemente por los Expertos 6, 7, 8, 11 y 13 tras el
   fix — correcto, sin regresión.

2. **`ownerId` nunca se limpia si el owner abandona sin sucesor** — **SIGUE
   ABIERTO**, confirmado por el coordinador y de nuevo por el Experto 9 en
   `FirestoreRepository.leaveHousehold:613-635` (bloque "no hace nada" sin
   cambios). Sigue siendo [REQUIERE DECISIÓN] — no se ha tocado. El Experto 9
   añade un matiz de impacto: combinado con el hallazgo CRÍTICO nuevo #1 de
   seguridad de esta ronda (`donatePoints`/`appreciateMember`), un ex-owner
   que vuelve a entrar conserva `isTrusted(hid)` indefinidamente, no solo
   lectura/escritura de su propia identidad.

3. **`NotificationPollWorker.pollHousehold` doble lectura de `getMembers()`**
   — **CONFIRMADO que seguía sin fix**. **APLICADO** por el coordinador:
   `memberId` se resuelve ahora directamente de la lista `members` ya
   traída (`NotificationPollWorker.kt:135-144`), sin la segunda llamada vía
   `resolveCurrentMember`. Verificado sin regresión por los Expertos 7, 11 y 13.

4. **Regresión del fix `8ae55ce` (borrar hogar solo owner)** — **SIN
   regresión**, confirmado independientemente por el coordinador y los
   Expertos 4 y 9: la variable `isOwner` de `HouseholdScreen.kt:173` coincide
   exactamente con `isOwner(hid)` de `firestore.rules:187-189`; el botón solo
   se muestra `if (isOwner)`; no existe ningún otro camino de UI (deep link,
   otra pantalla) hacia `deleteHousehold` salvo el ya gateado y el de
   `GoogleAuthManager.deleteAccount` (que solo opera sobre el hogar Personal,
   siempre propio por construcción).

5. **Refactor `edcf131` (AdMob por rol eliminado)** — **SIN código muerto**,
   confirmado por el coordinador (grep) y de nuevo por los Expertos 4 y 10:
   sin referencias huérfanas a `updateChildDirectedSignal`/`isChildProfile`
   en `composeApp/src/`. El Experto 10 evaluó además el impacto de
   producto/privacidad del cambio: la función eliminada era un no-op
   funcional (siempre fijaba TRUE, igual que el TFCD global ya fijo en
   `TaskHubApplication.onCreate`) — quitarla no reabre ningún hueco
   COPPA/TFCD.

---

## Resumen ejecutivo — hallazgos CRÍTICOS nuevos

### 1. `donatePoints`/`appreciateMember`: transferencia entre miembros no-admin pierde puntos y puede crashear (Experto 9, Seguridad) — **APLICADO**

Cualquier miembro puede "agradecer"/"donar" puntos a cualquier otro desde la
UI (`HouseholdMemberList.kt:353-373`, sin gate de rol), pero
`firestore.rules:308-311` solo permite escribir en el documento de OTRO
miembro si quien escribe es `isTrusted(hid)` (admin/owner). Dos hermanos
"child" con cuenta propia vinculada — el escenario por defecto de
`JoinHouseholdScreen` para cualquiera que no sea el creador — provocan que
el PATCH al donante tenga éxito y el PATCH al receptor falle con 403, sin
ningún catch que lo capturara: los puntos del donante desaparecían sin
acreditarse a nadie, y la excepción se propagaba sin control fuera del
`launch`.

- **Fix aplicado**: `MemberRepository.donatePoints`/`appreciateMember`
  (`network/MemberRepository.kt`) envuelven ahora el segundo `addMemberPoints`
  en try/catch; `donatePoints` revierte el débito al donante si el crédito
  falla; ambos devuelven `TRANSFER_FAILED` (nuevo caso de los enums
  `DonateErrorReason`/`AppreciateErrorReason`) en vez de propagar la
  excepción. Nueva clave i18n `transfer_error_failed` (ES/EN).
- **[REQUIERE DECISIÓN, SOLO PROPUESTA]**: el cierre completo es de producto
  — decidir si "agradecer/donar" debe ser una acción libre entre iguales
  (requeriría ampliar `firestore.rules:308` con una rama acotada para no
  reabrir la inflación de puntos) o restringirse a admin/owner en la UI,
  coherente con las reglas ya vigentes. No aplicado — toca reglas +
  redespliegue y/o UX de gating.

### 2. `rewardRedemptions` sin comprobación de saldo ni atomicidad con el descuento — canjes gratis repetibles vía REST directo (Experto 9, Seguridad) — **SOLO PROPUESTA**

`firestore.rules:394-398` valida `pointsSpent == reward.cost` pero no
`totalPoints >= pointsSpent`; el descuento real es una escritura aparte
orquestada solo por el cliente. Un cliente modificado (o una llamada REST
directa con el idToken real del usuario) puede crear el registro de canje
sin nunca ejecutar el descuento, repetible indefinidamente. Mismo trasfondo
arquitectónico que `docs/atomicidad-commit-pendiente.md` (que lo enmarcaba
como problema de fiabilidad ante fallos de red), pero el Experto 9 lo
reencuadra como vector de abuso deliberado. Cierre real exige `:commit`
transaccional o Cloud Function — no aplicado, requiere backend/reglas.

---

## Hallazgos IMPORTANTES nuevos — aplicados

- **`FirestoreClient.setAuthState` sin `authMutex`** (Experto 6, Programador
  senior) — escribía `bearerToken`/`cachedLocalId`/`tokenExpiry` sin el mutex
  que protege el resto de escrituras del mismo estado; una corrutina en medio
  de `ensureAuth()` podía leer una combinación a medio escribir durante un
  login con Google concurrente con otra petición en vuelo. **Aplicado**: la
  función ahora es `suspend` y envuelve su cuerpo en `authMutex.withLock`
  (`network/FirestoreClient.kt:272-284`).

- **`HouseholdRepository.reconcileHouseholds` escribe sobre una foto vieja**
  (Expertos 6 y 12, confirmado como hallazgo CRÍTICO #4 de la auditoría
  anterior sin aplicar) — si el usuario abandona un hogar a mano durante la
  ventana del `awaitAll` de red, la escritura final podía resucitar el hogar
  recién abandonado. **Aplicado** el fix localizado recomendado: releer
  `store.getSavedHouseholds()` justo antes de escribir en vez de reusar la
  lista pre-red (`network/HouseholdRepository.kt:264-280`). El Experto 12
  matiza que esto reduce la ventana de carrera de "duración de N llamadas
  HTTP" a "dos lecturas síncronas de `Settings` consecutivas" pero no la
  elimina del todo sin un `Mutex` de clase en `HouseholdStore` —
  **[REQUIERE DECISIÓN, SOLO PROPUESTA]** para ese cierre completo.

- **`TaskScreenModel.completeTask` no relanzaba `CancellationException`**
  (Expertos 2 y 6, confirmado sin aplicar desde la ronda anterior) —
  **Aplicado**: `catch (e: CancellationException) { throw e }` antes del
  catch genérico (`ui/models/TaskScreenModel.kt:574-577`).

- **`role = Role.Button` faltante en 5 cards clicables** (Experto 3,
  Accesibilidad — confirmado sin aplicar desde la ronda anterior, y extendido
  a `TaskListScreen.kt` por el Experto 4) — **Aplicado** en
  `NotificationListScreen.kt:215`, `TaskListScreen.kt:814`,
  `CalendarScreen.kt` (`DayColumn`, `MonthDayCell`, `TaskPopupItem`).

- **`MonthDayCell` del calendario mensual sin `contentDescription`** (Experto
  3, NUEVO) — la celda entera era muda para TalkBack (solo se leía el número
  de día; los indicadores de estado por color no se anunciaban). **Aplicado**:
  `Modifier.semantics(mergeDescendants = true) { contentDescription = ... }`
  con resumen de tareas/completadas/atrasadas del día
  (`ui/screens/CalendarScreen.kt`), nueva clave i18n `calendar_today`.

- **Mensajes/errores con texto fijo en español que sortean `AppStrings`**
  (Experto 2, NUEVO) — `redeemReward`/`joinHousehold` lanzaban
  `IllegalStateException` con texto en español; como `e.message` nunca es
  null, el fallback de i18n del catch (`e.message ?: s(...)`) nunca se
  disparaba, y un usuario con la app en otro idioma veía el error en español
  en dos de los flujos más frecuentes de la app (canjear recompensa, código
  de invitación inválido). **Aplicado**: nuevas excepciones tipadas
  `FirestoreRepository.InsufficientBalanceException` y
  `HouseholdRepository.InvalidInviteCodeException` (mismo patrón que
  `TaskCompletionConflictException`), catcheadas por tipo en
  `MemberScreenModel.redeemReward`/`HouseholdScreenModel.joinHousehold` y
  mapeadas a claves de `AppStrings` ya existentes. El tercer sitio señalado
  por el experto (`"No autenticado"` en `createHousehold`) se deja sin tocar
  por ser un caso de borde infrecuente (solo se dispara si `ensureAuth()` ya
  falló), documentado aquí como **SOLO PROPUESTA** de bajo valor.

---

## Hallazgos MENORES nuevos — aplicados

- **Estética** (Experto 1, confirmados sin aplicar desde la ronda anterior):
  `MemberRewardScreen.kt:223` `onPrimary`→`onTertiary` en el spinner del botón
  "Canjear" (el Experto 1 verificó que afecta a 4 de 6 combinaciones
  tema/modo, no solo una); `StatsScreen.kt:360` `Color.White`→
  `MaterialTheme.colorScheme.surface` en el punto del gráfico; `WelcomeScreen.kt:190`
  literal de versión `"v0.7.25"`→`"v0.7.29"` (4 bumps desfasado).

- **UX** (Experto 5, confirmados sin aplicar): `MemberRewardScreen.kt` — falso
  negativo "Puntos insuficientes" mientras `memberState` aún carga (nuevo
  flag `isLoadingMember`, botón muestra spinner neutro en vez del texto de
  error durante la carga); `CreateRewardScreen.kt` — campo "Coste" ahora usa
  `KeyboardOptions(keyboardType = KeyboardType.Number)` y muestra un
  `supportingText` fijo ("Solo números") cuando no hay error, en vez de
  descartar teclas no numéricas en silencio.

- **Rendimiento** (Experto 11, NUEVO, mitigación de bajo riesgo) —
  `HouseholdScreen` refrescaba el historial COMPLETO de mensajes cada 20s sin
  cursor/límite (`getMessages` trae la subcolección entera). Subido el
  intervalo a 60s (`ui/screens/HouseholdScreen.kt:205-217`); migrar a
  `structuredQuery` con paginación real queda como **SOLO PROPUESTA** (la
  solución de fondo).

- **Seguridad, MENOR** (Experto 9, NUEVO) — `MainActivity` es `exported`
  (obligatorio por el intent-filter LAUNCHER) sin comprobación de firmante;
  cualquier app del dispositivo podía lanzar un Intent explícito con extras
  `householdId`/`taskId`/`notificationId` arbitrarios y forzar navegación o
  marcar notificaciones como leídas sin interacción del usuario (impacto
  acotado: `firestore.rules` sigue bloqueando cualquier lectura/escritura no
  autorizada). **Aplicado**: `consumeDeepLink` (`MainActivity.kt`) ahora
  descarta los extras si `householdId` no corresponde a un hogar guardado
  localmente (`HouseholdStore.getSavedHouseholds()`) antes de exponerlos a
  Compose.

- **Privacidad** (Experto 10, confirmados sin aplicar desde la ronda
  anterior) — `docs/privacy.html`: añadida mención explícita del scope OAuth
  de Google Calendar (lectura/escritura completa, cómo revocarlo) en la
  sección 4; sección 6 actualizada para reflejar que "Eliminar cuenta" ya es
  autoservicio en la app, no solo por email.

---

## SOLO PROPUESTA — no aplicado en esta ronda (requiere decisión/rediseño/backend)

### Seguridad
- **`rewardRedemptions` sin balance-check/atomicidad** (CRÍTICO #2 arriba) —
  backend/reglas.
- **`donatePoints`/`appreciateMember` como acción libre entre iguales vs.
  admin-only** (CRÍTICO #1 arriba) — decisión de producto + `firestore.rules`.
- **`ownerId` sin limpiar al abandonar sin sucesor** — decisión de
  esquema/reglas, sigue exactamente como en la ronda anterior.
- **`members/{mid}` update sin clamp de `totalPoints`/rachas** — deuda
  arquitectónica ya documentada y diferida (requiere backend/Cloud Functions
  para validar server-side); sin cambios.

### Privacidad
- **Notificaciones de chat sin anonimizar** al abandonar/expulsar — el
  patrón que ya funciona para `messages` (`anonymizeMemberMessages`) no es
  directamente aplicable: el documento de notificación no guarda el
  `memberId` del AUTOR (solo el del destinatario), y el nombre real ya quedó
  congelado en el texto libre `"$authorName: $preview"` en el momento de
  enviar. Cerrarlo bien requiere guardar `authorMemberId` en el documento de
  notificación (cambio de escritura en `sendMessage`) y resolver el nombre en
  el render, en vez de un find-replace de texto libre (frágil, riesgo de
  coincidencias no intencionadas). No aplicado por ese motivo — es un cambio
  de esquema, no un fix localizado.
- **Sin UMP/CMP para tráfico EEE/UK** — decisión legal/producto, sin SDK de
  consentimiento instalado.
- **Sin límite de retención para `taskHistory`/mensajes de chat** — decisión
  de producto (TTL similar al de notificaciones, 90 días).

### Rendimiento
- **Polling en primer plano sin lifecycle-awareness** (Experto 11, NUEVO) —
  `HouseholdScreen`/`CalendarScreen` usan `LaunchedEffect { while(true) {
  delay(...) } }` sin `repeatOnLifecycle`; en Android, la Composition no se
  destruye al pasar a segundo plano, así que el polling de notificaciones
  (30s) y mensajes (60s tras el fix de esta ronda) sigue corriendo con la app
  en segundo plano hasta que el proceso muera. Requiere un mecanismo
  `expect/actual` de "app en primer plano" multiplataforma que hoy no existe
  en el proyecto — no es un fix de una línea.
- **`getNotifications` sin `limit`/query estructurada** — sigue invocándose
  cada 30s en primer plano además del sondeo de 30 min del Worker; REQUIERE
  DECISIÓN (migrar a `structuredQuery`/`count()` agregado), sin cambios desde
  la ronda anterior.
- **`purgeOldRead` sin límite de concurrencia** — riesgo latente a 90 días
  vista, sin cambios.
- **Peso muerto de infraestructura FCM sin emisor real** — decisión de
  producto, sin cambios.

### Red/offline/sincronización
- **`orDefault` (patrón swallow-to-empty) en 8 lecturas sin caché** (Expertos
  8 y 12, NUEVO, generaliza el hallazgo ya conocido de `getAssignments`) —
  `RewardsRepository`, `NotificationRepository.getNotifications`,
  `MemberRepository.getUserProfile`/`getMemberAchievements`/`isMember`,
  `TaskRepository.getTaskHistory`, `FirestoreRepository.loadUserHouseholds`
  convierten CUALQUIER fallo (offline, 5xx, o un documento corrupto que
  dispara `extractDocId`) en "lista vacía" silenciosa, sin distinguir "vacío
  real" de "no se pudo leer". Decidir cuáles de los 8 merecen caché de
  respaldo (rewards/historial sí; `isMember`/perfil probablemente no) es
  trabajo de diseño, no un fix mecánico único.
- **`GoogleAuthManager.syncHouseholdsToCloud` fire-and-forget sin
  serializar** (Experto 12, NUEVO) — 4 call-sites lanzan una corrutina
  independiente sin cancelar la anterior; respuestas desordenadas pueden
  sobrescribir el espejo `users/{uid}.householdIds` con una foto vieja
  (afecta solo a la restauración tras reinstalación, no al estado local).
  Patrón de fix conocido (cancelar `Job` anterior, como ya hace
  `TaskScreenModel.loadTasksJob`) pero toca un flujo compartido por 4
  pantallas — se deja como propuesta para no tocarlo sin más contexto de
  producto.
- **`FirestoreClient` sin retry/backoff de transporte** — sin cambios,
  REQUIERE DECISIÓN (mezclar reintentos con escrituras no-idempotentes es
  arriesgado sin filtrar por operación).
- **`getAssignments`/`getAllAssignments` sin caché offline** — sin cambios.
- **Carrera de saldo en `redeemReward`/`donatePoints` entre dispositivos** —
  ya diferida en `docs/atomicidad-commit-pendiente.md`, sin regresión, no se
  repropone.

### Funcionalidad end-to-end / QA
- **`completeTask`/`completeAssignment`: fallo entre el PATCH de la tarea y
  `addMemberPoints`/`saveTaskHistory` deja "completada sin puntos" de forma
  permanente** (Experto 8, NUEVO) — el orden de escrituras evita duplicar
  puntos en un reintento, pero no hay ningún camino de UI para "reintentar
  solo el otorgamiento de puntos" de una tarea que el servidor ya marcó
  completada. Requiere una excepción/flujo de recuperación dedicado, no un
  fix de una línea.
- **`redeemReward`: reintento tras fallo parcial puede duplicar la
  recompensa sin duplicar el descuento** (Experto 8, NUEVO) — si
  `createRedemption` tiene éxito y el descuento posterior falla, un segundo
  intento del usuario crea un SEGUNDO registro de canje con un solo
  descuento real aplicado. Requiere una compensación en el catch (borrar el
  registro huérfano) o invertir el orden de escrituras — cualquiera de las
  dos cambia la garantía ya documentada en el código, así que se deja para
  decisión en vez de aplicarse a ciegas.
- **`undoCompleteTask` sin estado de error propio ante fallo parcial** —
  confirmado sin aplicar desde la ronda anterior (Experto 2); requiere
  exponer un nuevo estado observable y su manejo en la UI, no es un fix de
  una línea.
- **Fallo al enviar un mensaje de chat o un comentario de tarea pierde el
  texto Y oculta el historial ya cargado** (Expertos 2 y 5, NUEVO,
  coincidente) — `HouseholdScreenModel.sendMessage`/`TaskCommentsScreenModel.addComment`
  limpian el campo de texto de forma optimista ANTES de la llamada de red
  (para evitar doble-tap) y no lo restauran si falla; además, el estado
  `Error` sustituye por completo la rama `Success` que pinta la lista, así
  que un fallo transitorio de un solo envío "esconde" toda la conversación
  ya cargada. Se deja como propuesta porque el fix correcto (no perder el
  borrador, no pisar `Success` con `Error`) toca el modelo de estado de dos
  ScreenModels y su UI correspondiente, con riesgo de introducir una
  regresión de UX si se hace deprisa.
- **Banner "creador no resuelto" de `CreateTaskScreen` sin retry** —
  confirmado sin aplicar; además el Experto 2 encontró que la rama 3 de
  `ensureAuth()` (alta anónima nueva) no tiene try/catch, así que en el peor
  caso puede propagar una excepción no capturada en vez de solo quedarse
  esperando. Requiere try/catch + UI de retry, no aplicado por tocar un
  flujo de auth sensible sin poder probarlo end-to-end en esta ronda.
- **Inconsistencia de botón "Reintentar"** entre pantallas con error de red
  (`CalendarScreen`, `HouseholdChatSection` sin él; `NotificationListScreen`
  sí lo tiene) — MENOR, NUEVO (Experto 5), no aplicado por quedar ligado al
  mismo rediseño de estado de error del punto anterior.

### UI/Material 3 y arquitectura
- **Duplicación `Dialog+Surface+SettingsSheet`** en 6 pantallas (extendido
  por el Experto 4 a `CalendarScreen.kt`, antes eran 4+1) — refactor de
  extracción de componente, toca 6 archivos.
- **`ButtonDefaults.buttonColors(containerColor = primary)` redundante en
  ~20 sitios** (Experto 4, NUEVO) — 100% no-op frente al default de M3;
  cleanup mecánico pero de blast radius amplio para valor puramente
  cosmético/de mantenibilidad, se deja para una pasada dedicada.
- **Nested button dentro de card clicable pierde alcance independiente en
  TalkBack** (Experto 3, IMPORTANTE, confirmado sin aplicar) — migrar a
  `customActions` de accesibilidad en `NotificationListScreen`/`TaskListScreen`;
  no aplicado en esta ronda por requerir verificación manual con lector de
  pantalla que no se pudo hacer aquí.
- **God objects** `FirestoreRepository.kt` (2023 líneas) y
  `TaskScreenModel.kt` (1268 líneas) — el Experto 7 confirmó que el
  crecimiento desde la ronda anterior es 100% documentación (KDoc), cero
  lógica de negocio nueva; `TaskScreenModel` incluso redujo lógica real al
  quitar la duplicación del bloque AdMob. Sin urgencia nueva, sigue siendo
  refactor de gran superficie.
- **Estados de carga/vacío inconsistentes entre pantallas** (Experto 1,
  NUEVO) — `ShimmerPlaceholder` bien implementado pero usado solo en 4
  pantallas; el resto sigue con `CircularProgressIndicator` genérico.
  Ilustraciones de marca a medida en 2 pantallas vs. emoji de plataforma en
  3 — trabajo de diseño, no mecánico.
- **Splash/logo/ilustraciones ignoran el tema activo** — confirmado sin
  cambios, sigue [REQUIERE DECISIÓN] de producto (¿el logo debe respetar el
  tema Minimal/oscuro o mantenerse fijo?).

---

## Veredicto por subsistema (Experto 7, Jefe de arquitectura)

Sin cambios respecto a la ronda anterior salvo los dos ya señalados:
**Notificaciones** y **Estadísticas** pasan de "deuda técnica seria"/"con
hallazgo abierto" a sanos en el punto concreto que motivaba esa nota (N+1 y
doble conteo, ambos corregidos y verificados). **Seguridad/autorización**
sigue con deuda técnica seria (`ownerId` sin limpiar, sin cambios) y suma el
hallazgo CRÍTICO nuevo de `donatePoints`/`appreciateMember` (mitigado en el
cliente esta ronda, decisión de fondo pendiente). El resto de subsistemas
(Red/Firestore, Autenticación, Almacenamiento local, Navegación, DI, i18n,
Calendario) se mantiene igual que en la auditoría 2026-09-06.

---

## Cobertura de pruebas (Experto 13 — solo informa, sin cambios aplicados)

`./gradlew :composeApp:jvmTest` en verde: **173 tests, 0 fallos**, sin
`@Ignore` ni asserts vacíos, en 12 archivos. Los objetos `*Rules` extraídos en
rondas anteriores (Points, Penalty, Recurrence, Household,
AssignmentCompletion) están bien cubiertos (5/5). **0% de los 8 ScreenModels
y 0% de los 7 repositorios tienen test directo** (esperable en los
repositorios por ser I/O; menos esperable en los ScreenModels).

**Hallazgo más importante**: ninguno de los dos fixes críticos de esta ronda
(dedupe de `computeStats`, N+1 de `NotificationPollWorker`) tiene test de
regresión — y `computeStats` sigue siendo literalmente imposible de testear
por ser `private fun` en un archivo de UI. Es la misma función cuyo bug
(doble conteo) estuvo abierto en producción varios días sin que nada lo
detectara; el síntoma se corrigió pero el hueco de test que lo habría
atrapado sigue abierto.

**TOP 5 huecos priorizados** (tabla completa en el hallazgo original del
experto, resumida aquí):

| # | Qué falta | Por qué es de alto riesgo | Dificultad | Prioridad |
|---|---|---|---|---|
| 1 | `StatsScreenModel.computeStats` | Ya produjo un bug real en producción; sigue `private`, bloqueado para testear sin extraerla | Media (bloqueada por visibilidad) | ALTA |
| 2 | `HomeScreenModel.isPending` vs `previewFilter` | Dos definiciones de "pendiente" que pueden divergir silenciosamente | Media | ALTA |
| 3 | `NotificationText.title`/`message` | Objeto público, puro, trivial de testear — cero excusa | Trivial | ALTA |
| 4 | `NotificationPollWorker` — selección de `newOnes` | Ya hubo un bug (N+1) sin detectar una ronda entera en el mismo archivo | Alta (necesita extraer lógica pura) | ALTA |
| 5 | `MemberRepository.resolveCurrentMemberUncached` — fallback "primer miembro" | Mismo tipo de bug de privacidad que motivó el fix del Worker | Alta (I/O) | ALTA |

---

## Archivos modificados en esta ronda

Ya commiteados por el checkpoint automático `ffacab0` (aplicados por el
coordinador antes de lanzar el panel):
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/StatsScreenModel.kt`
- `composeApp/src/androidMain/kotlin/org/taskhub/NotificationPollWorker.kt`

Sin commitear (working tree, aplicados tras consolidar el panel):
- `composeApp/src/androidMain/kotlin/org/taskhub/MainActivity.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreClient.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/HouseholdRepository.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HouseholdScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/MemberScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CalendarScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateRewardScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/HouseholdScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/MemberRewardScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/NotificationListScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/StatsScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/WelcomeScreen.kt`
- `docs/privacy.html`

## Verificación

- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` →
  **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:jvmTest --console=plain` → **BUILD SUCCESSFUL**,
  173 tests, 0 fallos.

## Nota de proceso

El panel se lanzó dos veces por límites de cuota del proveedor de IA entre
subagentes (sin relación con el contenido técnico): la primera oleada de 13
subagentes falló casi en su totalidad al agotarse la cuota de sesión (reset
7:00 Madrid); se relanzaron los 13 a las 7:00 y de nuevo 2 de ellos (QA,
Seguridad) fallaron por el mismo motivo con el siguiente reset (12:00
Madrid), relanzados entonces con éxito. No afecta a la validez de los
hallazgos. Se detectó además que un mecanismo de checkpoint automático del
entorno (ajeno a este encargo) commiteó los dos primeros fixes aplicados
como `ffacab0` ("wip: checkpoint...") antes de que el panel terminara — no
fue una acción de `git commit` explícita del coordinador; se documenta aquí
por transparencia. El resto de cambios de esta ronda se ha dejado sin
commitear en el working tree, tal como pedía el encargo.
