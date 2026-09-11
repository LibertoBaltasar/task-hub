# Panel de expertos v9 — reintento con oleadas — Task Hub (2026-09-11)

HEAD de partida: `07f935b` (auditoría panel expertos v9 2026-09-13, que
documentó que el panel de 13 subagentes falló COMPLETO dos rondas seguidas
—v8 y v9— por `Agent terminated early due to an API error: You've hit your
session limit`). Encargo: reintentar el panel de 13 expertos dividiéndolo en
oleadas de máximo 4-5 subagentes simultáneos en vez de lanzar los 13 a la
vez, esperando a que cada oleada termine y consolidando antes de avanzar a
la siguiente.

> Nota sobre el nombre de este archivo: el progreso (`docs/panel-v9-progreso.md`)
> proponía inicialmente `docs/review-panel-expertos-2026-09-11.md`, pero ese
> nombre ya estaba en uso por la auditoría general de la cadena 2026-09-11
> (ronda de deuda aplicable, sin relación con este panel de 13 expertos). Este
> informe usa el nombre `-v9-reintento-` para no colisionar con él.

## Resultado del mecanismo de oleadas: ÉXITO

A diferencia de los dos intentos anteriores (v8: 14/14 fallidos; v9: 13/13
fallidos), **esta vez el panel de 13 expertos SÍ se completó por entero**,
en 3 oleadas:

- **Oleada A (4 subagentes: estética, funcionalidad end-to-end, accesibilidad
  WCAG AA, UI/componentes).** 4/4 completados a la primera, con hallazgos
  verificados.
- **Oleada B (5 subagentes: UX, programador senior, arquitectura, QA/bugs,
  seguridad MASVS).** 5/5 completados a la primera, con hallazgos
  verificados.
- **Oleada C (4 subagentes: privacidad/RGPD, rendimiento, red/offline/sync,
  cobertura de tests).** **Falló 4/4 al primer intento**, con el mismo error
  de `session limit` que las rondas v8/v9 completas. Siguiendo la regla del
  encargo, se esperó y se reintentó la oleada UNA vez — **el reintento
  completó 4/4 con hallazgos verificados**. Ningún hallazgo de esta oleada
  se inventó durante el fallo inicial; el primer intento no produjo ningún
  hallazgo, solo el error de sesión, así que no hay nada que descartar de
  esa pasada.

**Conclusión operativa confirmada:** el límite no es "cuántos subagentes se
lanzan en total en la sesión" sino "cuántos se lanzan simultáneamente en una
misma llamada" — 4-5 a la vez funciona de forma fiable (9/9 a la primera en
las oleadas A y B), mientras que 13-14 a la vez revienta la sesión
instantáneamente en el 100% de los intentos previos. El único fallo de esta
ronda (oleada C, primer intento) se resolvió con un simple reintento tras
esperar, confirmando que también hay variabilidad puntual incluso a 4
subagentes, pero sin necesidad de replantear el mecanismo de oleadas en sí.

---

## Hallazgos por experto

Cada hallazgo indica **NUEVO** / **YA RESUELTO** / **SIGUE ABIERTO** según lo
verificado por el subagente contra el código real en HEAD `07f935b`, y al
final de cada bloque se marca **[APLICADO]** o **[PROPUESTA]** para los que
se decidió actuar en esta ronda.

### 1. Estética

- **[PROPUESTA] Logo/splash ignora los temas Naturaleza/Minimal**
  (`SplashScreen.kt`, `AppLogo.kt`, uso en `HomeScreen.kt:208`) — colores de
  marca fijos (Teal/Coral) en vez de `colorScheme` activo. No es un bug de
  seguridad ni de corrección funcional, es una decisión de diseño con
  matices (el splash pinta antes de que `themeType` esté disponible sin
  tocar el flujo de arranque) — se deja como propuesta.
- **[PROPUESTA] Ilustraciones de estado vacío (`EmptyStateIllustrations.kt`)
  ignoran el tema activo** — mismo motivo, cambio de diseño visual no
  mecánico.
- **[PROPUESTA] Acabado de "cargando" inconsistente**: `StatsScreen`/
  `CalendarScreen` usan `CircularProgressIndicator` mientras 5 pantallas ya
  usan `ShimmerPlaceholder`. Requiere diseñar el shimmer de esas 2 pantallas
  (esqueleto de gráficos/calendario), no es un cambio de una línea.
- 3 hallazgos de la ronda 2026-09-10 verificados como **YA RESUELTOS** (color
  `onTertiary` en `MemberRewardScreen`, `surface` en `StatsScreen`, versión
  sincronizada en `WelcomeScreen`).

### 2. Funcionalidad end-to-end

- **[APLICADO] Degradar el rol del owner del hogar dejaba su badge
  incorrecto de forma auto-uncorregible** — cualquier admin podía cambiar el
  rol de OTRO miembro (incluido el owner real) sin gate. Corregido junto con
  el hallazgo convergente de QA/seguridad (#8/#9 de abajo): ver sección
  "Fix aplicado — bloqueo de acciones sobre el owner".
- Verificación explícita de los 4 puntos de la cadena 2026-09-12/13
  encargada: **RecurrenceRules** (ventana de atrasada) YA RESUELTO, ampliado
  con verificación adicional en `CalendarScreen`; **donatePoints** tope 1000
  YA RESUELTO; **sucesión de ownerId al expulsar** — ver hallazgo CRÍTICO
  de QA/seguridad (#8/#9), el fix de la ronda 09-13 NO funciona realmente;
  **TTL 90 días erosiona StatsScreen** SIGUE ABIERTO, sin cambios (decisión
  de producto pendiente, ya documentada, no se toca en esta ronda).

### 3. Accesibilidad WCAG AA

- **[APLICADO] Checkbox/RadioButton anidados dentro de Row ya clicable** —
  `CreateTaskScreen.kt`, `EditTaskScreen.kt`, `TaskDetailScreen.kt`: el
  widget nativo tenía su propio `onCheckedChange`/`onClick` duplicando el
  nodo interactivo de la Row exterior (que ya tiene `role=Checkbox`/
  `RadioButton`). Fix: `onCheckedChange = null` / `onClick = null` en el
  widget nativo.
- **[APLICADO] Botones de "eliminar" con `contentDescription` genérico** —
  `HouseholdMemberList.kt` (expulsar miembro) y `RewardListScreen.kt`
  (borrar recompensa) usaban el mismo texto fijo en todas las filas/tarjetas.
  Fix: nuevas claves i18n `member_remove_action_named`/
  `reward_delete_action_named` (ES/EN) interpoladas con el nombre real.
- **[APLICADO] Título compartido `TaskHubTopBar` sin `heading()`** — ninguna
  pantalla de la app marcaba ningún texto como encabezado, inutilizando el
  gesto de "navegar por encabezados" de TalkBack/VoiceOver. Fix: 
  `Modifier.semantics { heading() }` en el título de `TaskHubTopBar`
  (componente compartido por casi todas las pantallas — fix de alto impacto
  con un solo cambio).
- **[PROPUESTA] Campos con solo `placeholder`, sin `label` persistente**
  (búsqueda de tareas, comentario, chat) — cambio de UI visible, se deja
  como propuesta para revisión de diseño del formulario.
- **[PROPUESTA] Mensajes de error dinámicos sin `liveRegion`**
  (`JoinHouseholdScreen`, `MemberRewardScreen`, `HouseholdDialogs`) — mismo
  patrón ya usado en `DeleteAccountSection`, mecánico pero afecta a 3
  archivos adicionales; se deja documentado para una ronda de accesibilidad
  dedicada en vez de mezclarlo con las correcciones críticas de esta.
- **[PROPUESTA, ya conocido desde 09-10] Botón anidado en card clicable**
  (`NotificationCard`, `TaskCard`) — requiere `customActions` de semántica,
  cambio más involucrado.
- 8 patrones de rondas previas verificados como **YA RESUELTOS** sin
  regresión (roles semánticos, contraste, `stateDescription`, etc. — ver
  detalle en el informe del subagente).

### 4. UI/Componentes

- **[APLICADO] Nombre de miembro sin `maxLines`/`overflow`** —
  `HouseholdMemberList.kt` y `RankingScreen.kt`: nombres largos podían
  envolver a 2+ líneas y descuadrar la fila. Fix: `maxLines = 1,
  overflow = TextOverflow.Ellipsis`, igual que en `TaskCard`/`RewardCard`.
- **[PROPUESTA] Duplicación `ChartCard` en `StatsScreen`** (3 tarjetas de
  gráfica con el mismo wrapper `Card+Column+Text(title)`) — refactor
  mecánico pero afecta a un archivo de 2000+ líneas ya sensible; se propone
  para una ronda de refactor dedicada.
- **[PROPUESTA, ya conocido desde 09-10] Duplicación
  `Dialog+Surface+SettingsSheet`** en 4 pantallas pese a existir ya
  `HouseholdSettingsDialog` — esta ronda confirmó que la extracción ya
  funciona en producción (`HouseholdScreen`), debilitando el argumento de
  "blast radius" para no aplicarlo, pero sigue siendo un cambio de 4-5
  archivos fuera del alcance de "corrección mecánica de una línea".
- **[PROPUESTA] Ausencia total de `@Preview`** — inversión de productividad
  futura, no bloquea nada hoy, requiere infraestructura previa
  (`CompositionLocalProvider` de prueba).

### 5. UX

- **[APLICADO] `CreateRewardScreen` — campo Descripción sin `imeAction`** —
  único campo residual sin `imeAction = Next` de los 3 formularios grandes
  ya cubiertos en rondas previas. Fix de una línea.
- **[PROPUESTA] Canjear/donar/agradecer puntos sin confirmación de éxito**
  — afecta a 3 pantallas (`MemberRewardScreen`, `RewardListScreen`,
  `HouseholdScreen`), requiere añadir snackbars de éxito coherentes con el
  resto de la app; se propone como mejora de UX para una ronda dedicada en
  vez de mezclarla con las correcciones de bugs de esta.
- 4 hallazgos de rondas previas verificados como **YA RESUELTOS** sin
  regresión.

### 6. Programador senior

- **[PROPUESTA] Duplicación de lógica entre `completeTask` y
  `completeAssignment`** (`FirestoreRepository.kt`, ~150-180 líneas cada
  una) — refactor de alto valor pero alto riesgo si se hace deprisa (flujo
  más sensible de la app: puntos, concurrencia optimista, recurrencia). Se
  propone para una ronda dedicada con tests de regresión antes/después.
- **[SIGUE ABIERTO, sin cambio] God object `TaskScreenModel`** sin test de
  clase — requiere introducir fakes de repos, cambio de infraestructura de
  test, no mecánico.
- **[SIGUE ABIERTO, sin cambio] `MainActivity.consumeDeepLink` con
  `HouseholdStore(Settings())` al vuelo** — ya documentado como pendiente de
  probar en dispositivo real antes de tocar el flujo de deep link.
- **[PROPUESTA] `tryAuthOrApiKey` sin redactar la API key** — ver hallazgo
  reforzado de seguridad MASVS (#el fallback nunca puede tener éxito contra
  las reglas actuales); se deja como propuesta conjunta en la sección de
  seguridad para no duplicar el análisis.

### 7. Arquitectura

- Sin hallazgos nuevos puros; 3 matices de deuda ya rastreada (lógica de
  negocio de `completeTask`/`completeAssignment` no delegada a
  `TaskRepository` pese al refactor declarado en `AppModule.kt`; DTOs de red
  usados directamente como modelo de UI sin capa de dominio; `MainActivity`
  fuera de Koin) — todos **[PROPUESTA]**, deuda de mantenibilidad a medio
  plazo, no bugs de corrección inmediata.
- 2 hallazgos históricos verificados como **YA RESUELTOS**: mapeo DTO↔dominio
  centralizado en `FirestoreParsers.kt` (pendiente desde 2026-09-02/03);
  ausencia de recurrencia del antipatrón de estado local vacío que causó el
  bug de "pantalla en blanco" de la ronda 09-12.

### 8-9. QA/bugs y seguridad MASVS — hallazgo CRÍTICO convergente

**Dos expertos independientes (QA/bugs y seguridad MASVS), sin verse el uno
al otro, llegaron a la misma conclusión: el fix de sucesión de `ownerId` al
expulsar al owner (aplicado en la ronda 2026-09-13 como `[APLICADO]`) NO
funciona en el escenario real que pretendía resolver.**

- **Problema:** `FirestoreRepository.deleteMember` intenta reasignar
  `households/{hid}.ownerId` llamando a `updateHouseholdOwner` cuando un
  admin expulsa al owner. Pero `firestore.rules:307` exige `isOwner(hid)`
  (comparado contra el `ownerId` VIGENTE) para ese PATCH — y quien ejecuta
  `deleteMember` en este escenario es siempre un admin DISTINTO del owner
  (la UI ya excluye `isSelf`). El PATCH recibe 403, capturado en un
  `catch (_: Exception) {}` "best-effort" silencioso. El sucesor SÍ queda
  promovido a `role: admin` (esa escritura sí pasa), pero `ownerId` NUNCA
  cambia. El owner expulsado conserva permisos reales de owner
  (`isOwner(hid)` no comprueba `leftAt`) para siempre si vuelve a
  autenticarse, y nadie puede sucederle nunca más por esta vía.
- **Por qué es peor que antes del fix de 09-13:** ahora el estado final es
  MÁS confuso (sucesor promovido a admin sin necesidad, `ownerId` sin
  cambiar) y el diff de esa ronda APARENTA haber cerrado un hallazgo CRÍTICO
  cuando en realidad no lo hizo — sin desplegar contra Firestore
  real/emulador, el error de permisos no se detecta.
- **[APLICADO] Fix de esta ronda — bloqueo de acciones sobre el owner en la
  UI, en vez de tocar `firestore.rules`:** cambiar `firestore.rules` para
  permitir que `isTrusted(hid)` reasigne `ownerId` requeriría un
  `firebase deploy --only firestore:rules` (acción externa de despliegue,
  fuera del alcance de esta ronda — no se hace deploy sin que el usuario lo
  confirme explícitamente) y abre superficie nueva de abuso si no se acota
  con mucho cuidado. La corrección segura y mecánica aplicada en su lugar
  **impide que la UI pueda generar el estado roto**: `HouseholdMemberList.kt`
  ahora recibe `ownerUserId` desde `HouseholdScreen.kt` y bloquea tanto el
  botón de expulsar como el desplegable de cambio de rol cuando el miembro
  objetivo es el owner actual (`member.userId == ownerUserId`), con el mismo
  patrón ya usado para `isSelf`. Quien quiera dejar de ser owner debe seguir
  la vía que SÍ funciona: abandonar el hogar voluntariamente
  (`leaveHousehold`), que transfiere la propiedad ANTES de borrar su propio
  documento (mientras `isOwner(hid)` todavía es cierto para él).
- El código de sucesión dentro de `deleteMember` se deja intacto (no hace
  daño: en el futuro nunca se disparará desde la UI actualizada, pero sigue
  siendo una defensa best-effort razonable ante datos legados/corruptos).
  **[PROPUESTA]** para una ronda futura: si se quiere permitir la expulsión
  directa del owner sin pasar por "abandonar", ampliar `firestore.rules` con
  una función acotada tipo `isValidOwnerSuccession(hid)` (ver detalle en el
  informe del subagente de seguridad), con su propio despliegue y revisión.

Además:

- **[APLICADO] `SecureStore.jvm.kt` — orden write-then-chmod de la clave
  AES** — la clave se escribía en disco ANTES de restringir permisos,
  dejando una ventana breve con permisos por defecto del filesystem. Fix:
  `createNewFile()` + restringir permisos ANTES de escribir los bytes de la
  clave.
- **[PROPUESTA] `MAX_PEER_TRANSFER_AMOUNT`** verificado **YA RESUELTO**, sin
  regresión.
- **[PROPUESTA, sin cambio] `tryAuthOrApiKey`** — el fallback a API key en
  query string nunca puede tener éxito hoy (todas las colecciones exigen
  `signedIn()`), así que quitarlo no cambia comportamiento observable, pero
  es un cambio sobre un choke point HTTP compartido — se deja para revisión
  dedicada en vez de tocarlo de pasada.
- **[SIGUE ABIERTO, requiere backend] `rewardRedemptions` sin balance-check
  atómico** y **`members/{mid}.totalPoints` sin clamp server-side** — ambos
  ya documentados, requieren Cloud Functions, sin fix mecánico disponible.

### 10. Privacidad/RGPD

- **[APLICADO] Achievements/assignmentRotation huérfanos tras abandonar un
  hogar compartido** — `leaveHousehold` borraba el documento de miembro pero
  NUNCA borraba `members/{uid}/achievements` ni purgaba
  `assignmentRotation`/asignaciones pendientes (a diferencia de
  `deleteMember`, que sí lo hace desde la ronda 2026-09-03/04). Como
  `mid == uid` para miembros vinculados a cuenta, el logro quedaba
  **indexado literalmente por el UID de la cuenta ya borrada** — Art. 17
  RGPD. Fix: `leaveHousehold` ahora borra la subcolección `achievements` de
  cada miembro que se va (`deleteAllDocuments`, mismo helper usado por el
  cascade de `deleteHousehold`) y llama a `purgeMemberFromTasks` (antes solo
  usada por `deleteMember`), best-effort.
- **[APLICADO] `docs/privacy.html` desactualizado sobre el TTL de 90 días**
  — la política no mencionaba la purga automática ya implementada en la
  ronda 2026-09-12 (Art. 13.2.a RGPD). Fix: una frase añadida a la sección 6.
- **[SIGUE ABIERTO, ya conocido desde 09-10] Sin SDK de consentimiento
  (UMP/CMP)** para tráfico EEE/UK — mitigación parcial ya existente
  (TFCD global fuerza anuncios no personalizados), pero sigue sin el
  formulario de consentimiento propiamente dicho. Requiere integrar una
  dependencia nueva y flujo de onboarding — fuera de alcance mecánico.
- **[PROPUESTA, prioridad baja] Exportación de datos personales incompleta**
  — el CSV existente solo exporta tareas del hogar, no perfil/puntos/logros;
  el canal por email ya cumple el mínimo legal de Art. 15/20.
- 2 hallazgos verificados como **YA RESUELTOS**: anonimización de
  notificaciones de chat; retención TTL de 90 días con borrado real (no
  soft-delete).

### 11. Rendimiento

- **[PROPUESTA] `HouseholdScreen` recompone toda la lista de miembros en
  cada tick de polling** (30-60s) por lambdas sin memoizar — fix mecánico
  (`remember(member.id)`) pero de scope amplio sobre una pantalla compleja;
  se propone para una ronda de rendimiento dedicada con medición antes/
  después.
- **[PROPUESTA] `CalendarSyncManager` crea eventos de Google Calendar en
  serie (N+1 a una API externa)** — paralelizar con
  `async`/`awaitAll`+`Semaphore` es mecánico pero toca una integración
  externa sensible a rate-limiting; se propone con acotación de concurrencia
  explícita en vez de aplicarlo sin poder probarlo contra la API real.
- **[SIGUE ABIERTO, sin cambio] Polling sin lifecycle-awareness**,
  **`getMessages`/`getNotifications` sin paginación/límite**, **envíos/
  borrados en bucle secuencial** — todos ya documentados en rondas previas,
  sin cambios.

### 12. Red/offline/sync — hallazgo CRÍTICO nuevo

- **[APLICADO] `saveUserHouseholds` sin `updateMaskFieldPaths` — borraba el
  perfil global del usuario en cada sincronización de hogares.** Es el
  ÚNICO `client.patch` de todo el código base que omitía la máscara de
  actualización parcial (verificado contra los otros 12 sitios, todos con
  `updateMaskFieldPaths`). Sin ella, el PATCH de la API REST de Firestore
  **reemplaza el documento `users/{uid}` entero** por los campos del body
  (`householdIds`, `updatedAt`), borrando `displayName`, `avatarUrl`,
  `avatarEmoji`, `bio`, `status`, `fcmToken` y `createdAt`. Se invoca desde
  `GoogleAuthManager.handleGoogleToken` (cada login) y desde
  `HouseholdScreenModel` (crear/unirse/eliminar/abandonar hogar) — es decir,
  **cualquier usuario con sesión de Google que creara o abandonara un hogar
  perdía su perfil completo**, fallo silencioso (fire-and-forget) sin
  ningún test que lo cubriera. Fix: añadido
  `updateMaskFieldPaths("householdIds", "updatedAt")`, igual que en
  `upsertUserProfile`/`saveFcmToken` sobre el mismo documento.
- **[SIGUE ABIERTO, sin cambio] `FirestoreClient` sin retry/backoff de
  transporte** — requiere decisión de producto sobre qué operaciones son
  seguras de reintentar automáticamente (no-idempotentes vs idempotentes).
- **[SIGUE ABIERTO, parcial] Patrón `orDefault` sin caché de respaldo** en 4
  lecturas (`isMember`, `getUserProfile`, `getMemberAchievements`,
  `loadUserHouseholds`) — de las 8 originales, 4 ya tienen caché desde la
  ronda 09-12; las 4 restantes se dejaron sin caché por juicio de producto,
  pendiente de confirmar.
- **[SIGUE ABIERTO, sin cambio] `getAssignments`/`getAllAssignments` sin
  caché offline.**
- **YA RESUELTO** confirmado: `syncHouseholdsToCloud` serializado sin
  solapes.

### 13. Cobertura de tests

- **[PROPUESTA] `MemberRepository.donatePoints` — clasificación
  `AMOUNT_EXCEEDS_LIMIT` sin ningún test**, lógica pura embebida en función
  de I/O. Se propone extraer a `PointsRules.classifyDonateFailure(amount)`
  para poder testearla sin mockear red — no se hace en esta ronda para no
  mezclar un refactor de producción con la consolidación del panel.
- **[PROPUESTA] `MemberScreenModel` sin ningún test**, incluida la función
  `donateErrorKey` (nueva de la ronda 09-13) — mismo patrón que ya causó un
  bug real en `computeStats` antes de hacerla `internal` y testeable.
- **[PROPUESTA, gap menor] `RecurrenceRules` mensual sin el caso "creada
  después de que el target de este mes ya pasara"** — la rama semanal sí lo
  tiene, la mensual no; riesgo bajo (lógica simétrica ya revisada).
- 2 hallazgos verificados como **YA RESUELTOS**: cobertura de
  `RecurrenceRules` con `createdAt` (3 ramas, ambas frecuencias) y de
  `HouseholdRules.resolveOwnerSuccessor` (8 tests, todas las ramas).
- **[SIGUE ABIERTO, riesgo ya reconocido]** orquestación de sucesión de
  owner en `FirestoreRepository.deleteMember` sin test propio de I/O.

**Nota:** los 3 gaps de test identificados (#1, #2, #3 de esta sección) NO
se cerraron en esta ronda — priorizar aplicar y verificar las correcciones
de bugs críticos (saneamiento de perfil, GDPR, bloqueo de acciones sobre el
owner) dejando el ciclo de compilación/test limpio, en vez de añadir tests
nuevos sin la misma revisión dedicada que merece cada uno. Quedan
documentados como trabajo pendiente concreto para la próxima ronda.

---

## APLICA YA — resumen de correcciones aplicadas en esta ronda

Todas objetivas, mecánicas y de bajo riesgo (bugs reales con fix acotado, sin
ambigüedad de producto ni necesidad de despliegue externo):

1. **CRÍTICO — `FirestoreRepository.saveUserHouseholds`**: añadido
   `updateMaskFieldPaths("householdIds", "updatedAt")`. Corrige un bug de
   pérdida de datos (borraba el perfil completo del usuario en cada sync de
   hogares).
2. **CRÍTICO — bloqueo de acciones sobre el owner del hogar**:
   `HouseholdMemberList.kt`/`HouseholdScreen.kt` ya no permiten expulsar ni
   degradar el rol del owner desde la UI (el fix de `deleteMember` de la
   ronda 09-13 no podía funcionar contra `firestore.rules` real; esto impide
   que la UI genere el estado roto).
3. **RGPD — `FirestoreRepository.leaveHousehold`**: ahora borra
   `achievements` y purga `assignmentRotation`/asignaciones pendientes del
   miembro que se va, igual que ya hacía `deleteMember`.
4. **`docs/privacy.html`**: añadida mención del TTL de 90 días ya
   implementado.
5. **Accesibilidad**: `onCheckedChange = null`/`onClick = null` en 3
   Checkbox/RadioButton anidados (`CreateTaskScreen`, `EditTaskScreen`,
   `TaskDetailScreen`); `contentDescription` interpolado con el nombre real
   en 2 botones de eliminar (`HouseholdMemberList`, `RewardListScreen`);
   `heading()` en el título de `TaskHubTopBar` (componente compartido).
6. **UI**: `maxLines`/`overflow` en nombre de miembro
   (`HouseholdMemberList`, `RankingScreen`).
7. **UX**: `imeAction = Next` en el campo Descripción de
   `CreateRewardScreen`.
8. **Seguridad menor — `SecureStore.jvm.kt`**: la clave AES ahora se
   escribe después de restringir permisos del fichero, no antes.

## SOLO PROPUESTA — pendiente de decisión de producto o ronda dedicada

Ver detalle en cada sección de experto arriba. Resumen por prioridad:

- **Alto valor, requiere despliegue externo (fuera de alcance sin
  confirmación explícita del usuario):** ampliar `firestore.rules` para
  permitir sucesión de `ownerId` por un admin de confianza (no solo el
  propio owner), si se quiere reactivar la expulsión directa del owner.
- **Alto valor, refactor de scope amplio:** unificar `completeTask`/
  `completeAssignment`; extraer `ChartCard` en `StatsScreen`; aplicar
  `HouseholdSettingsDialog` en las 4 pantallas restantes; mover
  `completeTask`/`completeAssignment` a `TaskRepository`.
- **Deuda de test conocida:** `MemberScreenModel`, clasificación de
  `donatePoints`, gap menor de `RecurrenceRules` mensual, orquestación de
  `deleteMember`.
- **Rendimiento, requiere medición:** memoización de `HouseholdScreen`,
  paralelización de `CalendarSyncManager`, paginación de `getMessages`/
  `getNotifications`, lifecycle-awareness del polling.
- **Producto/decisión pendiente ya documentada:** TTL 90 días erosionando
  `StatsScreen`; SDK de consentimiento UMP/CMP; `rewardRedemptions`/
  `totalPoints` sin validación atómica server-side (requiere Cloud
  Functions).

---

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ **BUILD SUCCESSFUL in 30s** (solo warnings preexistentes de APIs Android
deprecadas — GoogleSignIn, MasterKey, EncryptedSharedPreferences — no
relacionados con esta ronda).

```
./gradlew :composeApp:jvmTest --console=plain
```
→ **BUILD SUCCESSFUL in 19s. 216 tests, 0 fallos, 0 errores** — mismo número
que el baseline de la ronda anterior (esta ronda no añadió tests nuevos; los
gaps de cobertura identificados por el experto #13 quedan documentados como
propuesta, no aplicados, para no mezclar un refactor de test con la
consolidación de este panel).

## Archivos modificados en esta ronda

- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt`
  (`saveUserHouseholds` con `updateMaskFieldPaths`; `leaveHousehold` purga
  `achievements`/tareas del miembro que se va).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdMemberList.kt`
  (parámetro `ownerUserId`, bloqueo de expulsar/degradar al owner,
  `contentDescription` con nombre, `maxLines`/`overflow`).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/HouseholdScreen.kt`
  (pasa `ownerUserId` a `householdMemberList`).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/TaskHubTopBar.kt`
  (`heading()` en el título).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt`
  (`member_remove_action_named`, `reward_delete_action_named`, ES/EN).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateRewardScreen.kt`
  (`imeAction` en Descripción).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt`,
  `EditTaskScreen.kt`, `TaskDetailScreen.kt` (Checkbox/RadioButton
  no-interactivos, ya cubiertos por la Row exterior).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/RankingScreen.kt`
  (`maxLines`/`overflow` en nombre).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/RewardListScreen.kt`
  (`contentDescription` con nombre de recompensa).
- `composeApp/src/jvmMain/kotlin/org/taskhub/storage/SecureStore.jvm.kt`
  (orden write-then-chmod de la clave AES).
- `docs/privacy.html` (mención del TTL de 90 días).
- `docs/panel-v9-progreso.md` (progreso de las 3 oleadas).
- `docs/review-panel-expertos-v9-reintento-2026-09-11.md` (este informe).

## Pendiente para la próxima ronda

- Decidir si se quiere permitir la expulsión directa del owner (requiere
  ampliar `firestore.rules` + despliegue) o mantener el bloqueo de UI
  aplicado en esta ronda como solución permanente (más simple, sin
  superficie nueva de abuso).
- Cerrar los 3 gaps de cobertura de tests documentados (#13).
- Evaluar en una ronda dedicada de rendimiento las 2 propuestas nuevas
  (memoización de `HouseholdScreen`, paralelización de `CalendarSyncManager`)
  con medición antes/después.
- Punto ya arrastrado de rondas anteriores: decisión de producto sobre el
  TTL de 90 días erosionando `totalTasksCompleted`/`onTimeRate` de
  `StatsScreen`.
