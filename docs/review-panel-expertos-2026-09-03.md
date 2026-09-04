# Panel de expertos — revisión integral (2026-09-03/04)

Nueva pasada completa del comité de 13 expertos sobre Task Hub, coordinada
mediante subagentes en paralelo (uno por especialista, cada uno con lectura
completa del repo + de `docs/review-panel-expertos-2026-09-02.md`, la última
revisión completa, sin asumir que ningún hallazgo antiguo seguía vigente sin
comprobarlo). HEAD de partida: `dd60412` (v5 + bump versión 0.7.26 +
despliegue de `firestore.rules` v6), árbol de trabajo limpio.

**No es una continuación parcial**: los 13 expertos releyeron su área de cero
contra el código actual, varios verificando además el estado real de
producción (API de Firebase Rules) en lugar de fiarse de comentarios o
mensajes de commit.

## Foco de regresiones — las 22 propuestas pendientes del panel v5

| # | Propuesta v5 | Estado verificado esta ronda |
|---|---|---|
| 1 | `completeAssignment` sin protección de concurrencia sobre doc. de tarea | **PENDIENTE**, sin cambios (Exp. 2, 6, 12) |
| 2 | `regenerateNextAssignment` puede duplicar asignación del siguiente ciclo | **PENDIENTE**, sin cambios (Exp. 2, 12) |
| 3 | `undoCompleteTask` deja asignaciones "completadas" huérfanas | **PENDIENTE**, sin cambios (Exp. 2, 8) |
| 4 | Compleciones fantasma (0 pts) cuentan en `StatsScreen` | **PENDIENTE**, agravado — ahora también infla `checkAndAwardAchievements` con logros permanentes falsos (Exp. 8, hallazgo nuevo) |
| 5 | `authorName` no anonimizado en comentarios de tarea / expulsión admin | **PARCIAL** — comentarios de tarea siguen pendientes (cambio de esquema); la mitad de "expulsión admin no anonimiza NI mensajes de chat" **APLICADA YA** esta ronda (Exp. 10) |
| 6 | `rewardRedemptions`/`taskHistory` sin validar campos en `create` | **PENDIENTE**, sin cambios (Exp. 9) |
| 7 | Regla v6 (`members/{mid}` create) sin desplegar | **RESUELTA** — confirmado por 2 expertos independientes contra la API real de Firebase Rules (diff byte a byte). Comentario de cabecera del archivo actualizado esta ronda (estaba desincronizado, decía "PENDIENTE") |
| 8 | Google Calendar access token sin cifrar | **PENDIENTE**, sin cambios (Exp. 9) |
| 9 | Señalización AdMob por sesión (según rol) | **PENDIENTE** (global ya aplicado en v5, sin regresión); `maxAdContentRating` (hallazgo relacionado nuevo) **APLICADO YA** esta ronda |
| 10 | `HouseholdTaskSection` bypassa el ScreenModel | **PENDIENTE**, sin cambios (Exp. 2, 7) |
| 11 | `FirestoreRepository.kt` facade volvió a crecer | **Sigue creciendo**: 1604→1634 líneas (Exp. 7, 11) — causa raíz diagnosticada esta ronda |
| 12 | `getAllAssignments()` duplica `getTasks()` | **PENDIENTE**, sin cambios (Exp. 11) |
| 13 | `loadTasks`/`loadStats` secuenciales | **PENDIENTE**, sin cambios (Exp. 11) |
| 14 | `deleteAllDocuments` sin límite de concurrencia | **PENDIENTE**, sin cambios (Exp. 11, 12) |
| 15 | Ilustraciones de estado vacío en 2/6 pantallas | **PARCIAL** — estilo de `RewardListScreen` unificado con el resto **APLICADO YA**; la ilustración de marca en sí sigue **PENDIENTE** (Exp. 1) |
| 16 | Fallbacks de error hardcodeados en español (~28 puntos) | **PENDIENTE**, recuento corregido a **33 puntos en 7 archivos** (Exp. 5) |
| 17 | `MemberRewardScreen` con `AlertDialog` manual sin unificar | **PENDIENTE**, sin cambios (Exp. 4) |
| 18 | `EditTaskScreen` sin botón "Reintentar" tras fallo de carga | **APLICADO YA** esta ronda (Exp. 5) |
| 19 | Cobertura de `reduce-motion` incompleta (3 sitios) | **PENDIENTE**, sin cambios (Exp. 3) |
| 20 | Mapeo DTO↔dominio descentralizado (2/7 tipos) | **PENDIENTE**, sin cambios (Exp. 7) |
| 21 | `SecureStore` sin test de fallo de descifrado | **PENDIENTE** — confirmado cubrible sin mocks nuevos (Exp. 13) |
| 22 | `TaskCache`/`SettingsStore.getCalendarId` sin cobertura | **PENDIENTE** — confirmado cubrible sin mocks nuevos (Exp. 13) |

**Hallazgo más importante de la ronda**: el fix crítico de v5 (cierre de
asignaciones hermanas en `completeAssignment`) sigue correcto y sin
regresión, pero el propio panel encontró que replicó una debilidad ya
conocida de `completeTask` (cierre de hermanas sin `expectedUpdateTime`,
Exp. 12) y que tiene un efecto secundario no evaluado: las compleciones
fantasma con `pointsAwarded=0` que genera también inflan
`checkAndAwardAchievements`, desbloqueando logros permanentes falsos para
miembros que no completaron nada (Exp. 8, nuevo).

---

## Experto 1 — Estética y diseño visual

### IMPORTANTE

- **Problema**: estados vacíos con emoji suelto en vez de ilustración de
  marca — riesgo de render inconsistente entre plataformas.
  **Evidencia**: `RewardListScreen.kt:129`, `NotificationListScreen.kt:85`,
  `RankingScreen.kt:88`, `HouseholdMemberList.kt:111`.
  **SOLO PROPUESTA** (requiere 4 ilustraciones nuevas). **Estado**:
  PENDIENTE-DESDE-V2/V3, sin cambios.
- **Problema**: `EmptyTasksIllustration`/`EmptyHouseholdsIllustration`
  hardcodean Teal/Coral — bajo el tema Minimal (100% acromático) rompen la
  premisa de diseño del tema, no solo "chocan" como se documentó antes.
  **Evidencia**: `EmptyStateIllustrations.kt:14-18,35,63-67,96,108,112,119`
  vs. `Theme.kt:243-309` (paleta Minimal 100% acromática).
  **SOLO PROPUESTA**. **Estado**: PENDIENTE-DESDE-V2/V3, evidencia reforzada.
- **Problema (NUEVO)**: `CalendarScreen.kt` usaba glifos Unicode `◀`/`▶` en
  vez de iconos Material, el único sitio de la app que quedó fuera del
  barrido de iconografía de v5.
  **Evidencia**: `CalendarScreen.kt:219,252` (antes del fix).
  **APLICADO YA** en esta ronda — `Icon(Icons.AutoMirrored.Filled.
  KeyboardArrowLeft/Right, ...)`. **Estado**: NUEVO → RESUELTO.
- **Problema (NUEVO)**: los 4 estados vacíos con emoji no compartían
  jerarquía tipográfica entre sí (tamaño de emoji y peso/color del título
  distintos entre `RewardListScreen` y `RankingScreen`/`HouseholdMemberList`).
  **Evidencia**: `RewardListScreen.kt:129-142` (antes del fix) vs.
  `RankingScreen.kt:87-100`.
  **APLICADO YA** para `RewardListScreen` (unificado al patrón de
  Ranking/HouseholdMemberList); `NotificationListScreen` requeriría además
  partir una clave i18n → **SOLO PROPUESTA** para ese caso. **Estado**:
  NUEVO → PARCIALMENTE RESUELTO.

### MENOR

- `AppLogo.kt` sin política documentada de "marca fija vs. sigue el tema"
  — usado en `HomeScreen.kt:181` (topbar, sí sigue el tema) con colores fijos.
  **SOLO PROPUESTA**. **Estado**: PENDIENTE-DESDE-V5, evidencia ampliada.
- **Problema (NUEVO)**: adopción parcial de `ShimmerList` (skeleton loading)
  — solo 4 pantallas lo usan, el resto sigue con `CircularProgressIndicator`
  centrado plano. **SOLO PROPUESTA** (~8-10 archivos). **Estado**: NUEVO.

**Confirmado sin regresión**: los 3 temas siguen bien estructurados con
ajustes AA documentados intactos; el fix de v5 de glifos → iconos en
Create/EditTaskScreen y HouseholdMemberList sigue aplicado correctamente.

---

## Experto 2 — Funcionalidad end-to-end

### CRÍTICO

- Confirmado sin regresión: el fix crítico de v5 (`completeAssignment`
  cierra asignaciones hermanas) sigue presente y correcto
  (`FirestoreRepository.kt:1296-1313`).
- **Problema (NUEVO, instancia adicional de un hallazgo conocido)**: el
  propio fix de v5 replicó en `completeAssignment` la misma debilidad que
  ya tenía `completeTask` (cierre de hermanas sin `expectedUpdateTime`) —
  ahora existe en dos sitios en vez de uno.
  **Evidencia**: `FirestoreRepository.kt:1303-1313`.
  **SOLO PROPUESTA** (impacto bajo, solo un campo de visualización). **Estado**:
  NUEVO (ver también Experto 12, que trata este mismo punto como IMPORTANTE).

### IMPORTANTE

- `completeAssignment` sin protección de concurrencia sobre el documento de
  TAREA — **PENDIENTE-DESDE-V5**, sin cambios (`FirestoreRepository.kt:1278-1283`).
  **SOLO PROPUESTA**.
- `regenerateNextAssignment` puede duplicar la asignación del siguiente ciclo
  — **PENDIENTE-DESDE-V5**, sin cambios. **SOLO PROPUESTA**.
- `undoCompleteTask` deja asignaciones huérfanas — **PENDIENTE-DESDE-V5**,
  sin cambios, con matiz nuevo: si la tarea era recurrente, la asignación del
  ciclo SIGUIENTE ya pudo haberse creado y el undo no la ajusta.
  **SOLO PROPUESTA**.
- Compleciones fantasma (0 pts) en `StatsScreen` — **PENDIENTE-DESDE-V5**,
  con más volumen potencial ahora que `completeAssignment` también las genera.
  **SOLO PROPUESTA**.
- `authorName` de comentarios de tarea no anonimizado — **PENDIENTE-DESDE-V4/
  ENCARGO-16**, cambio de esquema. **SOLO PROPUESTA**.
- `HouseholdTaskSection` bypassa el ScreenModel — **PENDIENTE-DESDE-V5**, sin
  cambios. **SOLO PROPUESTA**.
- `rewardRedemptions`/`taskHistory` sin validar campos en `create` —
  **PENDIENTE-DESDE-V5**, confirmado contra reglas realmente desplegadas.
  **SOLO PROPUESTA**.

### MENOR

- **Problema**: regla v6 confirmada desplegada en producción (verificación
  byte a byte contra `firebaserules.googleapis.com`), pero el comentario de
  cabecera de `firestore.rules` seguía diciendo "PENDIENTE DE DESPLIEGUE".
  **APLICADO YA** esta ronda — comentario actualizado con fecha/ID de
  ruleset real. **Estado**: NUEVO → RESUELTO.
- **Problema (NUEVO)**: `completeAssignment` no ofrece "deshacer" en la UI,
  a diferencia de `completeTask` — asimetría de producto.
  **SOLO PROPUESTA** (decisión de producto + extender `UndoState`). **Estado**: NUEVO.

**Confirmado sin hallazgo**: sin regresiones de funcionalidad introducidas
por `dd60412` (solo bump de versión); regeneración de siguiente asignación
unificada y correcta en ambos flujos de compleción.

---

## Experto 3 — Accesibilidad WCAG AA

### IMPORTANTE

- **Problema (NUEVO, regresión encubierta)**: 6 botones icon-only ("añadir/
  quitar subtarea", "añadir etiqueta" en Create/EditTaskScreen) quedaron sin
  `contentDescription` tras el fix de iconografía de v5 (que migró
  `Text("+")`/`Text("✕")` a `Icon(...)` pero solo tocó el glifo visual, no la
  accesibilidad del botón) — TalkBack los anuncia como "botón" sin etiqueta.
  **Evidencia**: `CreateTaskScreen.kt:320,353,564` (antes del fix),
  `EditTaskScreen.kt:362,395,603` (antes del fix).
  **APLICADO YA** esta ronda — `contentDescription` añadido reutilizando
  claves i18n ya existentes (`create_task_add_item`, `create_task_add_tag`,
  `common_delete`). **Estado**: NUEVO (regresión encubierta de v5) →
  RESUELTO.

### MENOR

- Cobertura de `reduce-motion` incompleta (3 sitios) — **PENDIENTE-DESDE-V5**,
  sin cambios. **SOLO PROPUESTA**.
- Tint `colorScheme.error` aislado en icono "eliminar miembro" —
  **PENDIENTE-DESDE-V4/V5**, sin cambios. **SOLO PROPUESTA**.
- **Problema (NUEVO)**: `SemanticColors.kt` — pares base `onSuccess`/`success`
  y `onInfo`/`info` en modo oscuro no pasan AA (3.91:1 y 3.90:1) si se
  usaran como texto sobre su propio color de relleno; confirmado que
  actualmente NO se usan en ningún sitio de la UI (solo las variantes
  `*Container`, que sí pasan AA). Trampa latente para desarrollo futuro, sin
  impacto real hoy. **SOLO PROPUESTA**. **Estado**: NUEVO, sin impacto actual.
- **Problema (NUEVO)**: en la vista semanal del calendario, el área clicable
  de `DayColumn` en días sin tareas queda en ~44dp (por debajo de la
  recomendación Material de 48dp, aunque por encima del mínimo WCAG AA de
  24px). **SOLO PROPUESTA**, bajo impacto. **Estado**: NUEVO, informativo.

**Confirmado sin hallazgo**: contraste recalculado independientemente en los
3 temas × 2 modos (47 combinaciones), todas ≥4.5:1; sin regresión de touch
targets; las 20 llamadas a `FilterChip` del árbol tienen `leadingIcon` de
check (WCAG 1.4.1); `liveRegion` de `DeleteAccountSection` intacto; sin
iconos direccionales sin `AutoMirrored`.

---

## Experto 4 — UI y componentes Material 3

### IMPORTANTE

- **Problema (NUEVO)**: patrón "cabecera expandible con chevron" duplicado
  en 4 sitios (`HouseholdTaskSection`, `HouseholdMemberList`,
  `CreateTaskScreen` — `QuickTemplatesSection`, `TaskListScreen`) con
  implementación manual repetida, y 3 de los 4 reutilizan claves i18n del
  dominio "hogar" (`household_task_section_collapse`/`_expand`) fuera de su
  dominio original — mismo tipo de fuga de dominio que v5 ya corrigió una
  vez para `household_delete_btn`/`household_cancel`.
  **Evidencia**: `HouseholdTaskSection.kt:100-103`,
  `HouseholdMemberList.kt:76-80`, `CreateTaskScreen.kt:970-974`,
  `TaskListScreen.kt:1165-1172`.
  **SOLO PROPUESTA** (introduce un composable compartido nuevo). **Estado**:
  NUEVO.

### MENOR

- `MemberRewardScreen.kt` con `AlertDialog` manual sin unificar —
  **PENDIENTE-DESDE-V5**, sin cambios. **SOLO PROPUESTA**.
- Tint fijo `colorScheme.error` en icono "eliminar miembro" —
  **PENDIENTE-DESDE-V4/V5**, mismo hallazgo que Accesibilidad #4.
- **Problema (NUEVO)**: emoji `📋` en `Text` en vez de `Icon` en
  `QuickTemplatesSection`, inconsistente con el criterio "solo iconos
  Material" aplicado al resto de la pantalla en v5.
  **Evidencia**: `CreateTaskScreen.kt:957-961` (antes del fix).
  **APLICADO YA** esta ronda — sustituido por
  `Icon(Icons.AutoMirrored.Filled.List, ...)`. **Estado**: NUEVO → RESUELTO.

**Confirmado sin regresión**: sin iconos direccionales sin `AutoMirrored`;
sin literales de color fuera de `Theme.kt`/`SemanticColors.kt`; `AlertDialog`
manual justificado en el resto de sitios (contenido/selección genuina, no
confirmación destructiva); fix de `RecurrenceNextPreview` (regresión de v5)
verificado correcto.

---

## Experto 5 — UX

### IMPORTANTE

- **Problema (NUEVO)**: validación "acusatoria" prematura — los campos
  Título (Create/EditTaskScreen, CreateRewardScreen) y Coste
  (CreateRewardScreen) mostraban error rojo antes de que el usuario
  escribiera nada, porque `isError`/`supportingText` se evaluaban ya en el
  primer frame sobre un valor vacío inicial. `CreateRewardScreen` heredó el
  patrón "buggy" de `CreateTaskScreen` al aplicar el fix de v5 (#validación
  de formularios) sin detectar que ya era problemático; contrasta con
  `CreateProfileScreen`/`JoinHouseholdScreen`, que resuelven el mismo caso
  solo deshabilitando el botón, sin pintar en rojo.
  **Evidencia**: `CreateTaskScreen.kt:81,277` (antes del fix),
  `CreateRewardScreen.kt:47,49,179,212` (antes del fix).
  **APLICADO YA** esta ronda — estado `titleTouched`/`costTouched` que solo
  activa el error tras interacción (escritura o pérdida de foco) en ambos
  archivos. **Estado**: NUEVO (agravado por el propio fix de v5) → RESUELTO.
- Mensajes de error con fallback hardcodeado en español — recuento
  verificado y corregido: **33 puntos en 7 archivos** (no ~28 como decía
  v5). Un precedente de solución ya existe en el propio repo
  (`StatsScreenModel.kt:84-87`, que ya recibe `lang`). **PENDIENTE-DESDE-V4/
  V5**, conteo corregido. **SOLO PROPUESTA** (volumen).
- **Problema**: `EditTaskScreen` sin botón "Reintentar" tras fallo de carga
  de asignaciones — la única vía era salir y reentrar.
  **Evidencia**: `EditTaskScreen.kt:127,137-150,665-671` (antes del fix).
  **APLICADO YA** esta ronda — carga de asignaciones extraída a un
  `LaunchedEffect` retriggable por estado + botón "Reintentar" junto al
  mensaje de error. **Estado**: PENDIENTE-DESDE-V5 → RESUELTO.

### MENOR

- Mismo patrón de validación prematura en `penaltyValue`
  (`CreateTaskScreen.kt:843`) — impacto menor (campo gated tras un toggle
  explícito). **SOLO PROPUESTA**. **Estado**: NUEVO, informativo.
- Inconsistencia de patrón entre pantallas que deshabilitan el botón en
  silencio vs. las que muestran error rojo inmediato para "campo
  obligatorio vacío". **SOLO PROPUESTA** (decisión de sistema de diseño).
  **Estado**: NUEVO.

**Confirmado sin regresión**: reautenticación de eliminar cuenta, spinners
de eliminar/salir de hogar, resto de validación de `CreateRewardScreen`,
pantallas con "Reintentar" ya existentes, sin dead-ends de navegación.

---

## Experto 6 — Programador senior

### CRÍTICO

- Confirmado sin cambios: `completeAssignment` sigue sin protección de
  concurrencia sobre el documento de tarea (`FirestoreRepository.kt:1278-1283`).
  **PENDIENTE-DESDE-V5**. **SOLO PROPUESTA**.

### IMPORTANTE

- **Problema (NUEVO)**: `FirestoreClient.bearerToken`/`tokenExpiry` leídos
  fuera de la sección protegida por `authMutex` en los 3 llamadores de
  `ensureAuth()` — mismo patrón de "double-checked locking sobre estructura
  no thread-safe" que v5 documentó para `MemberRepository`, pero aquí es el
  token Bearer de CADA petición REST de la app.
  **Evidencia**: `FirestoreClient.kt:219-222,228-231,270-272`.
  **SOLO PROPUESTA** (cambia el contrato de `ensureAuth()`). **Estado**: NUEVO.
- `MemberRepository.currentMemberCache` — matiz nuevo: `invalidateCurrentMember`
  hace una **escritura** totalmente desprotegida (no solo una lectura fuera
  de mutex, como decía v5).
  **Evidencia**: `MemberRepository.kt:69-71`.
  **PENDIENTE-DESDE-V5**, matizado (más grave de lo documentado). **SOLO PROPUESTA**.
- `LaunchedEffect` de UI que traga `CancellationException` (App.kt,
  EditTaskScreen.kt, HouseholdTaskSection.kt) — **PENDIENTE-DESDE-V5**, sin
  cambios. **SOLO PROPUESTA**, impacto bajo.
- Confirma el hallazgo de `CalendarSyncManager.kt` (10 sitios, ver Experto
  12) — **APLICADO YA** esta ronda (ver más abajo).
- **Problema (NUEVO)**: `runCatching` dentro del interceptor HTTP de
  `FirestoreClient.kt` también traga `CancellationException` al leer el
  body de una respuesta de error.
  **Evidencia**: `FirestoreClient.kt:74-77` (antes del fix).
  **APLICADO YA** esta ronda — sustituido por el helper `orDefault` ya
  existente en el mismo archivo (que sí relanza `CancellationException`).
  **Estado**: NUEVO → RESUELTO.

**Confirmado sin hallazgo** (barrido completo del árbol, ~130 sitios de
`catch (e: Exception)`): sin `catch (e: Throwable)`, sin `GlobalScope.launch`,
sin `lateinit var`, los 8 `!!` del árbol guardados correctamente, DTOs
inmutables (`val`, sin colecciones mutables expuestas). Todos los fixes de
`CancellationException` de v5 (16 puntos en 4 archivos) siguen intactos.

---

## Experto 7 — Jefe de arquitectura

### IMPORTANTE

- `FirestoreRepository.kt` sigue creciendo (1604→1634 líneas) — causa raíz
  diagnosticada esta ronda: los repos de dominio son campos privados
  construidos a mano dentro de la facade (no `single {}` de Koin), lo que
  fuerza estructuralmente a que toda orquestación cross-dominio (como
  `completeTask`/`completeAssignment`) viva ahí. Decisión de diseño
  documentada, no descuido — pero explica por qué el split de v4 no frenó
  el crecimiento. **PENDIENTE-DESDE-V5**, con diagnóstico nuevo. **SOLO PROPUESTA**.
- `HouseholdTaskSection` sigue bypasseando el ScreenModel —
  **PENDIENTE-DESDE-V5**, sin cambios; confirmado que es el ÚNICO caso en
  todo el árbol. **SOLO PROPUESTA**.
- **Problema (NUEVO, reabierto)**: `TaskScreenModel.kt` (1258 líneas) es un
  "mini god ScreenModel" señalado en v2/v3 pero fuera del radar del panel
  desde entonces — acumula CRUD de tareas, completar/deshacer/reasignar,
  comentarios tipo chat, exportación CSV, subtareas y sync de Calendar.
  Congelado en su máximo histórico desde v4, sin reducirse.
  **SOLO PROPUESTA** (refactor de mayor superficie). **Estado**:
  PENDIENTE-DESDE-V3, reabierto.

### MENOR

- Mapeo DTO↔dominio descentralizado (2/7 tipos) — **PENDIENTE-DESDE-V3**,
  sin cambios. **SOLO PROPUESTA**.

**Veredicto por subsistema**: `network/` sano en repos de dominio, facade en
tendencia de crecimiento sostenido a vigilar; `ui/` consistente salvo
`HouseholdTaskSection` y el "mini god ScreenModel" reabierto; `storage/`
sano, tamaño contenido; DI (Koin) sano.

---

## Experto 8 — QA y bugs

### CRÍTICO

- Confirmado sin regresión: el fix crítico de v5 sigue correcto
  (`FirestoreRepository.kt:1296-1313`).

### IMPORTANTE

- **Problema (NUEVO)**: `checkAndAwardAchievements` cuenta compleciones
  fantasma (`pointsAwarded=0`, generadas por el propio fix de cierre de
  hermanas de v5/esta ronda) como tareas completadas reales — a diferencia
  de `StatsScreenModel` (donde el hallazgo #4 de v5 solo distorsiona un
  número en pantalla), aquí el efecto es una escritura PERMANENTE de logro
  desbloqueado que no se revierte nunca, ni siquiera si luego se arregla el
  conteo.
  **Evidencia**: `TaskScreenModel.kt:1059-1067` (sin filtrar
  `pointsAwarded>0`, sin fusionar con `taskHistory`).
  **SOLO PROPUESTA** (requiere decidir el contrato de "qué cuenta como
  compleción" de forma consistente en Stats y en Achievements). **Estado**: NUEVO.
- **Problema (NUEVO)**: `reassignTaskCompletion` (corregir "quién hizo la
  tarea") transfiere puntos y actualiza `taskHistory`, pero nunca toca el
  documento de `assignments` correspondiente — la compleción queda
  duplicada permanentemente en `StatsScreen` de AMBOS miembros (el antiguo
  vía su asignación stale, el nuevo vía el historial corregido).
  **Evidencia**: `FirestoreRepository.kt:1001-1042` vs.
  `StatsScreenModel.kt:111-129` (fusión sin deduplicar).
  **SOLO PROPUESTA** (decisión de contrato de datos). **Estado**: NUEVO.

### MENOR

- `undoCompleteTask` tampoco revierte un logro que la propia compleción
  deshecha pudiera haber desbloqueado — matiz nuevo del hallazgo #3 de v5
  (mismo pendiente, contrato de reversión debe evaluar también logros).

**Confirmado sin hallazgo**: `redeemReward`/`donatePoints`/`appreciateMember`/
`addMemberPoints` con concurrencia optimista correcta; `PenaltyRules`/
`PointsRules`/`RecurrenceRules` sin crashes por división por cero/NPE/índice
fuera de rango.

---

## Experto 9 — Seguridad / AppSec (OWASP MASVS)

Verificación código-vs-producción hecha contra la API real de Firebase
Rules: **ruleset activo `45e73f01-088f-4344-aef0-c97a101938f2`, desplegado
2026-09-03T23:45:46Z, idéntico byte a byte al `firestore.rules` del repo**
— confirma que `dd60412` sí desplegó v6 como decía su mensaje de commit.

### IMPORTANTE

- `rewardRedemptions`/`create` sin validar `pointsSpent`/`memberId` contra
  el coste real — **PENDIENTE-DESDE-V5**, sin cambios. **SOLO PROPUESTA**.
- `taskHistory`/`create` sin validar `memberId == request.auth.uid` —
  **PENDIENTE-DESDE-V5**, sin cambios. **SOLO PROPUESTA**.
- **Problema**: regla v6 confirmada desplegada, pero el propio archivo en
  producción seguía con el comentario "PENDIENTE DE DESPLIEGUE" —
  **APLICADO YA** esta ronda (mismo fix que Experto 2, consolidado). **Estado**:
  NUEVO → RESUELTO.

### MENOR

- Google Calendar access token sin cifrar en `SettingsStore` —
  **PENDIENTE-DESDE-V5**. Mitigación adicional confirmada esta ronda: el
  dominio `sharedpref` está excluido de `allowBackup`/`dataExtractionRules`
  en Android, reduciendo el vector de backup (aunque no cifra en el propio
  dispositivo). **SOLO PROPUESTA**.
- `notifications/create` sin validar `memberId` — **PENDIENTE-DESDE-V5**,
  impacto bajo. **SOLO PROPUESTA**.

**Confirmado sin hallazgo**: auth/tokens sin regresión, los 9 puntos de
`CancellationException` de v5 intactos; sin inyección en construcción de
URLs/queries REST (incluyendo `GoogleCalendarRepository.kt`, auditado por
primera vez explícitamente esta ronda); CSPRNG en invitaciones; API key de
Firebase pública, no-issue.

---

## Experto 10 — Privacidad / RGPD / menores

### CRÍTICO

- `authorName` de comentarios de tarea no anonimizado (DTO sin `memberId`)
  — **[PROPUESTA LEGAL/PRODUCTO]**, cambio de esquema + backfill.
  **PENDIENTE-DESDE-V4/ENCARGO-16**, sin cambios.
- **Problema**: `deleteMember` (expulsión por admin) no anonimizaba los
  mensajes de chat del expulsado, a diferencia de `leaveHousehold` —
  **[BUG TÉCNICO]**, no cambio de esquema (a diferencia del punto de
  comentarios, `MessageResponse` ya tiene `memberId`).
  **Evidencia**: `MemberRepository.kt:311-331`,
  `FirestoreRepository.kt:619-629` (sin llamada a `anonymizeMemberMessages`,
  antes del fix).
  **APLICADO YA** esta ronda — mismo patrón exacto ya usado y probado en
  `leaveHousehold`. **Estado**: PENDIENTE-DESDE-ENCARGO-16 → RESUELTO
  (parcialmente — la parte de comentarios de tarea sigue pendiente).

### IMPORTANTE

- Estado de despliegue de `firestore.rules` v6 — en el momento en que este
  experto corrió (antes de que Experto 9 verificara contra la API real), no
  pudo confirmarlo por falta de credenciales en su sandbox; **resuelto por
  Experto 2/9 en esta misma ronda** (ver arriba). Sin acción adicional.

### MENOR

- **Problema (NUEVO)**: `AdRequest` no fijaba `maxAdContentRating` — TFCD
  por sí solo evita anuncios personalizados pero no garantiza que el
  CONTENIDO del anuncio sea apto para audiencia infantil.
  **Evidencia**: `TaskHubApplication.kt` (antes del fix),
  `AdController.android.kt:44-47`.
  **APLICADO YA** esta ronda — `setMaxAdContentRating(MAX_AD_CONTENT_RATING_G)`
  añadido al mismo `RequestConfiguration.Builder()` ya existente. **Estado**:
  NUEVO → RESUELTO.
- **Problema (NUEVO)**: `fcmToken` no se limpia al hacer `signOut()` local
  — en un dispositivo familiar compartido, el token de push queda asociado
  indefinidamente a la cuenta anterior. Impacto real hoy: ninguno (no existe
  infraestructura de Cloud Functions que lo consuma todavía).
  **SOLO PROPUESTA**, severidad baja. **Estado**: NUEVO.
- Sin retención de datos ni age-gating real —
  **[PROPUESTA LEGAL/PRODUCTO]**, sin cambios desde v3.

**Confirmado sin hallazgo**: Analytics sin PII en los 4 call-sites reales;
reglas de lectura de `messages`/`comments`/`members`/etc. correctamente
acotadas a miembros del hogar; `deleteAccount` sin completar si el cascade
es parcial (comportamiento correcto).

---

## Experto 11 — Rendimiento

### CRÍTICO

- **Problema (NUEVO)**: `TaskScreenModel.completeTask` y `.loadTaskDetail`
  — las dos rutas más transitadas de la app — pedían TODAS las tareas del
  hogar con `getTasks(householdId).find { it.id == taskId }` para localizar
  UNA sola, en vez de `getTask(householdId, taskId)` (lectura de un único
  documento), que ya existe en el propio repo con el KDoc explícito *"avoids
  an N+1 full-list fetch"* y ya se usa correctamente en `toggleSubtask`.
  **Evidencia**: `TaskScreenModel.kt:420-422,714-716` (antes del fix) vs.
  `TaskRepository.kt:260-265` y el uso correcto en `TaskScreenModel.kt:1117`.
  **APLICADO YA** esta ronda — ambos sitios migrados a `repo.getTask(...)`.
  **Estado**: NUEVO → RESUELTO.

### IMPORTANTE

- `getAllAssignments()` duplica `getTasks()` en 3 rutas calientes —
  **PENDIENTE-DESDE-V5**, sin cambios. **SOLO PROPUESTA**.
- `loadTasks()`/`loadStats()` encadenan lecturas en serie —
  **PENDIENTE-DESDE-V5**, sin cambios. **SOLO PROPUESTA**.
- `deleteAllDocuments` sin límite de concurrencia — **PENDIENTE-DESDE-V5**,
  sin cambios. **SOLO PROPUESTA**.

### MENOR

- Regresión de `RecurrenceNextPreview` (v5) confirmada resuelta, sin
  regresión adicional en todo el árbol (grep de `Clock.System.now()` dentro
  de `remember` sin más resultados).

**Confirmado sin hallazgo**: todas las `LazyColumn`/`items()` del árbol usan
`key = { ... }` estable; lambdas memoizadas en `TaskListScreen` sin
regresión; tamaño de la facade no creció adicionalmente por causas de
rendimiento (mismo diff que reportó Experto 7).

---

## Experto 12 — Red / offline / sincronización

### CRÍTICO

- `completeAssignment` sigue con PATCH incondicional sobre el documento de
  la TAREA (sin `currentDocument.updateTime`) — **PENDIENTE-DESDE-V5**, sin
  cambios. **SOLO PROPUESTA** (requiere decidir política de conflicto).
- `regenerateNextAssignment` puede duplicar la asignación del siguiente
  ciclo (TOCTOU) — **PENDIENTE-DESDE-V5**, sin cambios. **SOLO PROPUESTA**
  (requiere ID determinista de asignación).

### IMPORTANTE

- El cierre de asignaciones hermanas (en `completeTask` Y en
  `completeAssignment`, tras el fix de v5) no usa `expectedUpdateTime` —
  la superficie del problema se duplicó en vez de cerrarse. Impacto
  acotado: no afecta el saldo real de puntos. **PENDIENTE-DESDE-V5**,
  matizado (ahora simétrico en ambos flujos). **SOLO PROPUESTA**.
- Sin retry/backoff genérico ante timeout/5xx transitorio —
  **PENDIENTE-DESDE-V4**, sin cambios. **SOLO PROPUESTA**.
- `redeemReward` permite saldo negativo con canjes concurrentes del mismo
  miembro — **PENDIENTE-DESDE-V4**, sin cambios. **SOLO PROPUESTA**.
- **Problema**: `CalendarSyncManager.kt` tragaba `CancellationException` en
  10 puntos, sin relanzarla — no cubierto por el barrido de v5 (que sí cubrió
  `FirestoreClient`/`MemberRepository`/`HouseholdRepository`/`GoogleAuthManager`).
  **Evidencia**: `CalendarSyncManager.kt:46,70,75,137,162,167,198,220,232,236`
  (antes del fix).
  **APLICADO YA** esta ronda — mismo patrón mecánico ya aplicado
  repetidamente en el resto del código base. **Estado**: NUEVO → RESUELTO.

### MENOR

- DELETE final de `deleteHousehold` sin try/catch — **PENDIENTE-DESDE-V5**,
  sin cambios. **SOLO PROPUESTA**.
- **Problema (NUEVO)**: `reassignTaskCompletion` sin protección de
  concurrencia optimista — probabilidad real muy baja (función de
  corrección manual, uso esporádico). **SOLO PROPUESTA**, bajo impacto/
  probabilidad. **Estado**: NUEVO, MENOR.

**Confirmado sin hallazgo**: `isOnline()`, `addMemberPoints`/
`appreciateMember`/`addMemberAchievement`, `TaskCache`, `GoogleCalendarRepository.kt`
(su único `catch` sí relanza `CancellationException` correctamente) — todos
sin regresión desde v5.

---

## Experto 13 — Cobertura de pruebas (solo informa)

Confirmado que no hay diff de código de producción entre el cierre de v5
(`d61ae70`) y HEAD (`dd60412`) salvo el bump de versión — el mapa de
cobertura de v5 sigue vigente al 100%, **135 tests** sin variación.

Los 4 huecos de v5 siguen en el mismo estado exacto (ninguno resuelto,
ninguno empeorado):

| Prioridad | Hueco | ¿Cubrible en jvmTest sin mocks? |
|---|---|---|
| CRÍTICO | `completeAssignment` sin test de integración (cierre de hermanas) | No — requiere `ktor-client-mock` o extracción a función pura |
| IMPORTANTE | `SecureStore` sin test de fallo de descifrado real | **Sí** — reutilizando el patrón de `SecureStoreTest.kt` |
| MENOR | `TaskCache.kt` en 0% de cobertura | **Sí** — reutilizando el doble `FakeSettings` de `SettingsStoreTest.kt` |
| MENOR | `SettingsStore.getCalendarId`/`setCalendarId` sin test | **Sí** — mismo patrón que el resto de `SettingsStoreTest.kt` |

No se identificaron huecos nuevos (sin superficie de código nueva desde v5).

---

## Resumen — aplicado en esta ronda

### Rendimiento (el hallazgo CRÍTICO de la ronda)

- **`ui/models/TaskScreenModel.kt`** — `completeTask` y `loadTaskDetail`
  ahora usan `repo.getTask(householdId, taskId)` en vez de
  `repo.getTasks(householdId).find { ... }`, eliminando una lectura N+1 de
  toda la colección de tareas en las dos rutas más transitadas de la app.

### Accesibilidad

- **`ui/screens/CreateTaskScreen.kt`**, **`ui/screens/EditTaskScreen.kt`** —
  `contentDescription` añadido a los 6 botones icon-only de
  añadir/quitar subtarea y añadir etiqueta (regresión encubierta del fix de
  iconografía de v5).

### Iconos espejados / glifos de texto → iconos Material

- `Text("◀")`/`Text("▶")` → `Icon(Icons.AutoMirrored.Filled.
  KeyboardArrowLeft/Right)` en `CalendarScreen.kt`.
- Emoji `📋` → `Icon(Icons.AutoMirrored.Filled.List)` en
  `CreateTaskScreen.kt` (`QuickTemplatesSection`).

### UX

- **`ui/screens/CreateTaskScreen.kt`**, **`ui/screens/CreateRewardScreen.kt`**
  — validación de campos obligatorios (título, coste) ya no se muestra en
  rojo hasta que el usuario interactúa con el campo (`titleTouched`/
  `costTouched`), en vez de aparecer ya en el primer frame.
- **`ui/screens/EditTaskScreen.kt`** — carga de asignaciones ahora
  reintentable (botón "Reintentar" junto al mensaje de error), sin tener
  que salir y volver a entrar en la pantalla.
- **`ui/screens/RewardListScreen.kt`** — estilo del estado vacío unificado
  con el patrón ya usado en `RankingScreen`/`HouseholdMemberList`.
- Clave i18n `common_retry` añadida (ES/EN), reutilizable por futuros
  botones "Reintentar" sin fuga de dominio.

### Programador senior / concurrencia (CancellationException)

- **`network/FirestoreClient.kt`** — el interceptor HTTP de validación de
  respuestas (`runCatching` × 2) ahora usa el helper `orDefault` ya
  existente en el propio archivo, que relanza `CancellationException`
  correctamente.
- **`ui/models/CalendarSyncManager.kt`** — 10 puntos corregidos (todo el
  archivo, que no tenía ningún guard).

### Privacidad / seguridad

- **`network/FirestoreRepository.kt`** — `deleteMember` (expulsión por
  admin) ahora anonimiza también los mensajes de chat del miembro expulsado,
  mismo patrón ya usado en `leaveHousehold` (antes solo cubría abandono
  voluntario).
- **`androidMain/.../TaskHubApplication.kt`** — `RequestConfiguration`
  ahora también fija `setMaxAdContentRating(MAX_AD_CONTENT_RATING_G)`,
  además del `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` ya aplicado en v5.
- **`firestore.rules`** — comentario de cabecera corregido: v6 confirmada
  desplegada en producción (verificado contra la API real por 2 expertos
  independientes), ya no dice "PENDIENTE DE DESPLIEGUE".

**Archivos tocados** (12):
```
firestore.rules
composeApp/src/androidMain/kotlin/org/taskhub/TaskHubApplication.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreClient.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/CalendarSyncManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CalendarScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateRewardScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/RewardListScreen.kt
```

## Verificación (OBLIGATORIO)

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL in 17s` — sin errores; solo warnings de deprecación
preexistentes (Google Sign-In, Vibrator, EncryptedSharedPreferences/MasterKey),
ninguno introducido por esta ronda.

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` — sin fallos (135 tests, sin variación).

## PROPUESTAS pendientes — resumen para el usuario

### Integridad de datos / concurrencia (requieren decisión de producto)

1. `completeAssignment` sin protección de concurrencia sobre el documento
   de tarea (Exp. 2/6/12, CRÍTICO).
2. `regenerateNextAssignment` puede duplicar la asignación del siguiente
   ciclo (Exp. 2/12, CRÍTICO).
3. `undoCompleteTask` deja asignaciones "completadas" huérfanas (Exp. 2/8).
4. Compleciones fantasma (0 puntos) cuentan en `StatsScreen` **y ahora
   también en `checkAndAwardAchievements`** — logros permanentes falsos
   (Exp. 2/8, agravado esta ronda).
5. **NUEVO**: `reassignTaskCompletion` no toca `assignments` → doble
   contabilización de la misma compleción en Stats de dos miembros (Exp. 8).
6. Cierre de asignaciones hermanas (en ambos flujos de compleción) sin
   `expectedUpdateTime` (Exp. 12).
7. `reassignTaskCompletion` sin concurrencia optimista (Exp. 12, MENOR).
8. **NUEVO**: `FirestoreClient.bearerToken`/`tokenExpiry` leídos fuera de
   `authMutex` en 3 llamadores (Exp. 6).
9. `MemberRepository.currentMemberCache` — escritura sin mutex en
   `invalidateCurrentMember`, más grave de lo documentado en v5 (Exp. 6).

### Privacidad / seguridad (requieren cambio de esquema o despliegue)

10. `authorName` no anonimizado en comentarios de tarea (Exp. 2/10,
    cambio de esquema — la mitad de mensajes de chat en expulsión admin ya
    se resolvió esta ronda).
11. `rewardRedemptions`/`taskHistory` sin validar campos en `create`
    (Exp. 9, cambio de reglas + despliegue).
12. Google Calendar access token sin cifrar (Exp. 9).
13. Señalización AdMob por sesión según rol del perfil activo (Exp. 10,
    plumbing nuevo — el global y `maxAdContentRating` ya están aplicados).
14. **NUEVO**: `fcmToken` no se limpia en `signOut()` local (Exp. 10, sin
    impacto real hoy).

### Arquitectura / rendimiento (refactors de mayor superficie)

15. `HouseholdTaskSection` bypassa el ScreenModel (Exp. 2/7).
16. `FirestoreRepository.kt` facade sigue creciendo (1634 líneas), causa
    raíz diagnosticada esta ronda (Exp. 7).
17. **NUEVO/reabierto**: `TaskScreenModel.kt` "mini god ScreenModel"
    (1258 líneas), fuera del radar desde v3 (Exp. 7).
18. `getAllAssignments()` duplica `getTasks()` en 3 rutas calientes (Exp. 11).
19. `loadTasks`/`loadStats` secuenciales en vez de paralelos (Exp. 11).
20. `deleteAllDocuments` sin límite de concurrencia (Exp. 11/12).
21. Mapeo DTO↔dominio descentralizado (Exp. 7).

### Estética / UX de menor prioridad

22. Ilustraciones de estado vacío en solo 2/6 pantallas, sin seguir el
    tema (Exp. 1) — el estilo de `RewardListScreen` ya se unificó esta
    ronda.
23. Fallbacks de error hardcodeados en español (33 puntos, recuento
    corregido) (Exp. 5).
24. `MemberRewardScreen` con `AlertDialog` manual sin unificar (Exp. 4).
25. **NUEVO**: patrón "cabecera expandible" duplicado en 4 sitios con fuga
    de dominio i18n (Exp. 4).
26. Cobertura de `reduce-motion` incompleta en 3 sitios (Exp. 3).
27. **NUEVO**: adopción parcial de `ShimmerList` (Exp. 1).
28. `SemanticColors.onSuccess`/`onInfo` en dark no pasan AA (sin uso
    actual) (Exp. 3, NUEVO).

### Cobertura de tests (solo informa, Exp. 13)

29. `SecureStore` sin test de fallo de descifrado real — cubrible sin
    mocks nuevos.
30. `TaskCache`/`SettingsStore.getCalendarId` sin cobertura — cubrible sin
    mocks nuevos.
31. `completeAssignment` sin test de integración (cierre de hermanas) —
    requiere `ktor-client-mock` o refactor.
