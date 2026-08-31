# Panel de expertos v2 — calidad de código, arquitectura y hallazgos nuevos (2026-08-31)

2ª edición del panel de expertos de Task Hub. La 1ª edición
(`docs/review-panel-expertos-2026-08-31.md`) cubrió estética, funcionalidad/UX,
accesibilidad, QA/bugs y consistencia técnica de UI con 5 especialistas. Esta
edición amplía a **OCHO expertos**, incorporando los dos frentes que quedaron
superficiales: **programador senior** (calidad de código en `network/`,
`storage/`, `platform/`, `ui/models/`, `di/`) y **jefe de arquitectura**
(límites de capa, `FirestoreRepository`, DI, gestión de estado, seguridad,
escalabilidad). Los 8 expertos se lanzaron como subagentes en paralelo, sin
verse el trabajo entre sí, cada uno con un objetivo independiente. Versión de
partida: 0.7.23.

## Hallazgo destacado del panel

Tres expertos independientes (Funcionalidad, Programador senior, QA/bugs) y un
cuarto de forma indirecta (Arquitectura, al analizar `TaskScreenModel`)
detectaron **el mismo bug crítico por rutas de análisis distintas**:
`completeAssignment()` (completar una tarea desde el detalle, vía asignación
con fecha límite) actualizaba el documento de la propia asignación pero
**nunca sumaba los puntos al saldo real del miembro** (`addMemberPoints`) ni
guardaba el historial (`saveTaskHistory`). El usuario veía "✅ +N pts" en la
UI sin que esos puntos llegaran nunca a `totalPoints` ni pudieran canjearse
por recompensas. Es, con diferencia, el hallazgo de mayor impacto de todo el
panel — rompe el mecanismo central de la app (puntos → recompensas) para un
flujo de uso habitual. **Corregido** (ver sección de fixes aplicados), junto
con un segundo bug relacionado que el fix habría convertido en una duplicación
de puntos si no se corregía a la vez: `completeTask()` (el flujo de la lista
principal) nunca marcaba la asignación pendiente como completada, dejándola
"reabrible" desde el detalle.

## Resumen — hallazgos por experto y severidad

| Experto | CRÍTICO | IMPORTANTE | MENOR | Total |
|---|---|---|---|---|
| 1 — Estética | 1 | 5 | 2 | 8 |
| 2 — Funcionalidad | 1* | 2 | 3 | 6 |
| 3 — Accesibilidad | 2 | 3 | 1 | 6 |
| 4 — UI/Componentes | 1 | 4 | 5 | 10 |
| 5 — UX | 0 | 2 | 4 | 6 |
| 6 — Programador senior | 1* | 3 | 7 | 11 |
| 7 — Jefe de arquitectura | 1 | 3 | 9 | 13 + veredictos |
| 8 — QA/bugs | 2* | 1 | 3 | 6 |
| **Total (sin deduplicar)** | **9** | **23** | **34** | **66** |

`*` — el hallazgo CRÍTICO de Funcionalidad #1, Senior #1 y QA #1/#2 son **el
mismo bug** visto desde 3 ángulos distintos (funcional, calidad de código,
QA) más una segunda mitad (QA #2, la duplicación potencial). Contado como un
único hallazgo crítico real a efectos de la tabla top-10 más abajo, aunque se
mantiene la cuenta bruta por experto arriba para reflejar fielmente el
informe de cada uno.

## Tabla top-10 impacto / esfuerzo

| # | Hallazgo | Experto(s) | Impacto | Esfuerzo | Aplicado |
|---|---|---|---|---|---|
| 1 | `completeAssignment` no otorga puntos reales (ni historial); `completeTask` no sincroniza la asignación (riesgo de duplicado una vez corregido lo anterior) | Funcionalidad, Senior, QA, Arquitectura | Crítico | Medio | ✅ |
| 2 | `redeemReward` descuenta puntos sin validar saldo en el repositorio (puede quedar negativo) | Senior, QA | Alto | Bajo | ✅ |
| 3 | 3 botones "Exportar CSV" (Home/Perfil/Ajustes del hogar) son controles muertos (`onExportCsv = {}`) | UX | Alto | Bajo | ✅ |
| 4 | Guardar en `EditTaskScreen` tras fallo de carga de asignaciones desasigna a todos los miembros en silencio | UX | Alto | Bajo | ✅ |
| 5 | `RankingScreen`/`NotificationListScreen`: mismo patrón "fondo fijo + texto de tema" ya corregido en 6 sitios en la 1ª edición, pero no cubierto ahí — hasta 1.68:1 en oscuro | Accesibilidad | Crítico | Bajo | ✅ |
| 6 | `CalendarScreen`: colores de estado fijos (`OverdueColor`/`CompletedColor`) fuera del sistema de tema, en la cuadrícula (no solo el popup ya migrado) | UI/Componentes, Estética | Crítico/Alto | Bajo | ✅ |
| 7 | `firestore.rules` v4 sigue **sin desplegar** (confirmado en el propio archivo: "NO DESPLEGADO") pese a que el cliente ya asume sus restricciones | Arquitectura | Crítico | Bajo (desplegar) | ⚠️ no aplicado — requiere acción manual del usuario |
| 8 | `FirestoreRepository.kt` god object: 2500 líneas, 9+ dominios, sin paginación en ninguna colección | Arquitectura, Senior | Alto | Muy alto | ⚠️ propuesta |
| 9 | 10 de 23 pantallas inyectan `FirestoreRepository`/stores directamente, saltándose los ScreenModels | Arquitectura | Alto | Alto | ⚠️ propuesta |
| 10 | `ensureAuth()` sin `Mutex`: llamadas paralelas pueden disparar refrescos/altas de token concurrentes | Senior | Alto | Medio (cambia flujo de auth) | ⚠️ propuesta |

---

## Experto 1 — Estética / Diseño visual

### CRÍTICO

1. **`ui/components/EmptyStateIllustrations.kt`** (usado en `HomeScreen.kt`,
   `TaskListScreen.kt`) — las dos ilustraciones "hero" de estado vacío (las
   más visibles para un usuario nuevo) están pintadas con `Canvas` +
   literales `Teal200/500/800`/`Coral300/500` fijos, ignorando
   `MaterialTheme.colorScheme`. En Naturaleza/Minimal muestran teal/coral que
   no pertenece a la paleta activa. **PROPUESTA** — no aplicado: redibujar un
   `Canvas` custom sin verificación visual real de las 3 combinaciones de
   tema es alto riesgo de regresión estética sin QA visual.

### IMPORTANTE

2. **Mezcla de iconografía emoji vs Material vector icons** en casi toda la
   app (`"❌"` como sustituto de icono de error es el caso más repetido).
   **PROPUESTA** — decisión de identidad, requiere que el usuario decida qué
   casos son "marca" vs "funcional".
3. **`WelcomeScreen.kt`/`CreateHouseholdScreen.kt`/`JoinHouseholdScreen.kt`**
   sustituyen `AppLogo` por emojis genéricos (`"👥"`, `"🔑"`) en el momento de
   mayor impresión de marca (onboarding). **PROPUESTA** — cambio de
   identidad visual.
4. Pantallas anidadas de un hogar concreto (`HouseholdScreen`,
   `TaskListScreen`, `CalendarScreen`) no muestran el nombre del hogar activo
   en la topbar — riesgo real de confundir hogares para usuarios con varios.
   **PROPUESTA** — cambio de UI en 3 pantallas, se prioriza documentarlo para
   decisión conjunta con el hallazgo de UI #6 (mismo síntoma).
5. **`ProfileScreen.kt`** — icono "ir al espacio" usaba `ArrowBack` para
   significar "entrar" (semánticamente invertido). **Aplicado** (ver Fixes;
   coincide con UI/Componentes #6).
6. **`CalendarScreen.kt`** — colores de estado del calendario
   (`OverdueColor`/`DueTodayColor`/`CompletedColor`) fijos, iguales en los 3
   temas y claro/oscuro; rompen la identidad monocroma de Minimal. **Aplicado**
   (ver Fixes; mismo hallazgo que UI/Componentes #1).

### MENOR

7. `JoinHouseholdScreen.kt` — flujo de 2 pasos sin indicador visual de
   progreso. **PROPUESTA**.
8. `PublicProfileScreen.kt` — chip de rol no reutiliza `PointsBadge`.
   **Aplicado parcialmente**: se migró a los pares `tertiary`/`onTertiary` y
   `primaryContainer`/`onPrimaryContainer` ya auditados (mismo fix que
   Accesibilidad #3), aunque no se convirtió al componente `PointsBadge` en
   sí (cambiaría su API para admitir texto libre).

---

## Experto 2 — Funcionalidad (flujos end-to-end)

### CRÍTICO/ALTO

1. **`FirestoreRepository.kt` (`completeAssignment`)** — ver "Hallazgo
   destacado" arriba. **Aplicado.**
2. **`FirestoreRepository.kt` (`completeTask`)** — el flujo de "Hecho" de la
   lista principal nunca marca la asignación correspondiente como completada,
   dejándola reabrible desde el detalle para siempre (y, tras el fix de #1,
   habría permitido duplicar puntos). **Aplicado** — ahora `completeTask`
   sincroniza la asignación pendiente del miembro como completada (sin
   volver a otorgar puntos).
3. **Penalización por retraso nunca se aplica en el flujo principal** — solo
   `completeAssignment` calculaba penalización; `completeTask` (el 100% de
   los casos reales según el hallazgo #2) siempre otorga los puntos íntegros
   con `onTime = true` fijo, ignorando `task.dueDate`/`penaltyMode`.
   **PROPUESTA — no aplicado**: mover el cálculo de penalización a
   `completeTask` cambia cuántos puntos otorga una acción ya en producción
   (comportamiento, no solo bug de plomería) — se deja para decisión
   explícita del usuario, documentado también por Arquitectura como parte de
   la deuda de duplicación penalización/recurrencia (Senior #6).

### MEDIO

4. **`StatsScreen.kt`** — el pie chart "Distribución por categoría" solo
   cuenta compleciones vía `completedAssignments`, subestimando casi todas
   las tareas reales (que llegan por `taskHistory`, la vía mayoritaria tras
   el hallazgo #2). **PROPUESTA — no aplicado**: requiere añadir `taskId` a
   `CompletionRecord`/`taskHistory` agregado, fuera del alcance de un fix
   localizado.
5. **`reassignTaskCompletion`** es un no-op silencioso para tareas completadas
   solo vía `completeAssignment` (mismo `completedBy == null` de raíz que el
   hallazgo #1). **Resuelto automáticamente** por el fix del hallazgo #1
   (ahora `completeAssignment` también fija `completedBy`/`lastCompletedDate`
   en la tarea).
6. `TaskDetailScreen.kt` — el indicador "vencida" de una asignación de tarea
   recurrente completada por la lista podía quedar obsoleto. **Resuelto
   automáticamente** por el fix del hallazgo #2.

Sin regresiones detectadas en: unirse a hogar por código, mensajería,
notificaciones, agradecer/donar/canjear (ya cubiertos y corregidos en rondas
previas).

---

## Experto 3 — Accesibilidad (WCAG AA + Android)

Ratios verificados con `wcag_contrast.py` sobre los hex reales de `Theme.kt`
(v0.7.23), cubriendo pantallas fuera del alcance de la 1ª edición.

### CRÍTICO — ambos aplicados

1. **`RankingScreen.kt` (`RankingRow`)** — 1.30-1.68:1. `Card` con
   `containerColor` fijo (`Coral100`/`Teal50`) + texto secundario
   (`onSurfaceVariant`) que sigue el tema — mismo patrón sistémico ya
   corregido en 6 sitios en la 1ª edición, pero no cubierto ahí. **Fix
   aplicado:** color de texto fijo emparejado con el fondo (`Coral800` sobre
   `Coral100` = 7.02:1, `Teal800` sobre `Teal50` = 4.99:1) para ordinal, rol y
   racha; nombre del miembro con `onSurface` explícito (antes sin color
   explícito, dependía del `contentColor` derivado del `Card`, indefinido
   para colores fuera del esquema).
2. **`NotificationListScreen.kt` (`NotificationCard`)** — 1.15-1.68:1. Mismo
   patrón: `containerColor = Teal50` fijo con título/hora/mensaje en
   `onSurfaceVariant`/sin color. **Fix aplicado:** `Teal900`/`Teal800`
   (7.35:1+) para las notificaciones no leídas.

### IMPORTANTE — todos aplicados

3. **`PublicProfileScreen.kt`** — badge de rol con `Coral500`/`Teal600` al
   12% de alpha (2.49-3.13:1, falla en los 3 temas claros). **Fix aplicado:**
   `tertiary`/`onTertiary` (admin) y `primaryContainer`/`onPrimaryContainer`
   (miembro) — mismos pares que usa `PointsBadge`.
4. **`NotificationListScreen.kt`** — contador "X sin leer" con `Coral500`
   fijo sobre `background` (2.83-3.07:1 en los 3 temas claros). **Fix
   aplicado:** `MaterialTheme.semanticColors.info`.
5. **`ProfileScreen.kt`** — icono de hogar compartido teñido con `Coral500`
   fijo (2.60-2.81:1 en claro, falla el umbral 3:1 de icono significativo).
   **Fix aplicado:** `MaterialTheme.colorScheme.tertiary`.

### MENOR

6. `HouseholdDialogs.kt` (`QrShareDialog`) — el QR no expone
   `contentDescription`; impacto acotado porque el código también se muestra
   como texto debajo. **No aplicado** — requiere tocar `platform/QrCodeImage.kt`
   (expect/actual en 3 plataformas), fuera de alcance de un fix mínimo en
   esta pasada.

Confirmado sin regresión: reduce-motion, `SettingsSheet`/`AppSettings`,
`HouseholdChatSection`, `CreateHouseholdScreen`, `JoinHouseholdScreen`,
`PersonalSpaceScreen` (patrón de referencia correcto).

---

## Experto 4 — UI / Diseño de componentes

### CRÍTICO/IMPORTANTE

1. **`CalendarScreen.kt`** — la cuadrícula semana/mes (`TaskChip`,
   `MonthDayCell`) seguía con 3 colores hex fuera del sistema de tema pese a
   que el popup de la misma pantalla ya se migró a `PointsBadge` en la 1ª
   edición. **Aplicado** (ver Fixes; mismo hallazgo que Estética #6).
2. Tres sitios con `maxLines` sin `TextOverflow.Ellipsis` (`StatsScreen.kt`
   leyenda del pie chart, `RewardListScreen.kt` descripción/título,
   `EditTaskScreen.kt` nombre en selector de rotación) — texto cortado a
   media palabra en vez de con "…". **Aplicado** en los 4 puntos.
3. **`StatsScreen.kt` (`PieChartCard`)** — leyenda sin `Modifier.weight(1f)`,
   riesgo de desborde con etiquetas largas. **Aplicado** junto con #2.
4. **`UserAvatar.kt`** — `backgroundColor: Color = Teal100` en la
   *definición* del componente compartido más reutilizado de la app.
   **Aplicado:** default cambiado a `MaterialTheme.colorScheme.primaryContainer`.
5. Tres formas de "badge/stat chip" distintas coexistiendo (`PointsBadge`,
   `InfoBadge` en `TaskDetailScreen`, `StatItem` en `StatsScreen`).
   **PROPUESTA** — unificar en un `StatChip(label, value, tone)` reutilizando
   `BadgeTone`; refactor de superficie media, no aplicado.

### MENOR

6. `ProfileScreen.kt` — icono `ArrowBack` semánticamente invertido.
   **Aplicado** (ver Fixes; mismo hallazgo que Estética #5).
7. `TaskListScreen.kt` (`FilterChipsRow`) — 4 bloques `FilterChip`
   duplicados con colores hardcodeados. **PROPUESTA** — no aplicado, se
   agrupa con la deuda de literales de color ya documentada en la 1ª edición.
8. Bloque `OutlinedTextFieldDefaults.colors(...)` duplicado 5 veces.
   **PROPUESTA** — no aplicado, mismo motivo que #7.
9. `ShimmerPlaceholder.kt` — `RoundedCornerShape(12.dp)` literal en vez de
   `MaterialTheme.shapes.medium`. **PROPUESTA** — no aplicado en esta pasada
   (bajo riesgo pero no crítico; se prioriza el resto de fixes).
10. `RewardListScreen.kt` — título de recompensa sin `maxLines`, desalinea la
    cuadrícula. **Aplicado** (ver #2, mismo cambio).

---

## Experto 5 — UX / Experiencia de uso

### ALTA

1. **Botón "Exportar CSV" muerto en 3 de 4 puntos de entrada** (`HomeScreen`,
   `ProfileScreen`, `HouseholdDialogs` — `onExportCsv = { }`), solo funcional
   desde `TaskListScreen`. **Aplicado:** nuevo flag `showExportCsv: Boolean`
   en `SettingsCallbacks`; el botón ya no se muestra donde no hay contexto de
   tareas cargado, en vez de mostrarse y no hacer nada.
2. **`EditTaskScreen.kt`** — si `getAssignments()` falla al abrir la
   pantalla, el botón "Guardar" seguía habilitado y enviaba
   `memberIds = selectedMembers.toList()` (vacío), desasignando
   silenciosamente a todos los miembros al guardar un simple cambio de
   título. **Aplicado:** el botón "Guardar" ahora se deshabilita también si
   `assignmentsLoadFailed`.

### MEDIA

3. `HouseholdScreen.kt`/`PersonalSpaceScreen.kt` — la flecha de la topbar
   hace `replaceAll(HomeScreen())` pero el botón atrás del sistema hace
   `pop()`, dos destinos distintos para la misma semántica "volver".
   **PROPUESTA** — no aplicado: decidir cuál de los dos comportamientos es el
   correcto es una decisión de navegación, no un bug objetivo de un solo
   sitio.
4. `TaskListScreen.kt` — selector de orden solo con emoji, sin
   `contentDescription` ni texto del criterio activo. **PROPUESTA** — no
   aplicado en esta pasada (se agrupa con la deuda de iconografía emoji de
   Estética #2).

### BAJA

5. Campo "Tope de penalización" sin validación (a diferencia de sus campos
   hermanos, ya corregidos en rondas previas). **PROPUESTA** — no aplicado,
   cambio menor de UX pendiente de agrupar con una pasada de formularios.
6. `CalendarScreen.kt` — semana/mes vacíos sin mensaje de "sin tareas".
   **PROPUESTA** — no aplicado, cosmético.

---

## Experto 6 — Programador senior (network/, storage/, platform/, ui/models/, di/)

### CRÍTICO

1. **`completeAssignment` no otorga puntos reales** — ver "Hallazgo
   destacado". **Aplicado.**

### ALTO

2. **`redeemReward` descuenta puntos sin validar saldo** en el repositorio
   (a diferencia de `donatePoints`, que sí valida vía `PointsRules`).
   **Aplicado:** lectura fresca del miembro + guarda de saldo antes de
   escribir, lanzando una excepción tipada que ya se propaga limpiamente al
   `RewardActionState.Error` existente.
3. **`ensureAuth()` sin `Mutex`** — check-then-act sin protección; ráfagas de
   llamadas paralelas pueden disparar refrescos/altas de token concurrentes.
   **PROPUESTA — riesgo medio, no aplicado:** cambia el flujo de auth
   (justo el tipo de cambio que el encargo pide dejar fuera de "aplica ya").
4. **`FirestoreRepository.kt` god object** (2500 líneas, ~80 funciones
   públicas, 9+ dominios). **PROPUESTA — refactor grande, no aplicado** (ver
   veredicto de Arquitectura).

### MEDIO

5. Patrón sistemático de `catch (e: Exception)` sin relanzar
   `CancellationException` en decenas de funciones. **Aplicado parcialmente:**
   los 6 puntos citados explícitamente con nombre de función
   (`tryAuthOrApiKey`, `isOnline`, `leaveHousehold` ×3 bloques,
   `findTaskHistoryRecord`, `deleteAssignments`, `GoogleCalendarRepository.validateToken`)
   corregidos con `catch (e: CancellationException) { throw e }` antes del
   catch genérico. El resto (ScreenModels: `TaskScreenModel`,
   `HouseholdScreenModel`, `MemberScreenModel`, `ProfileScreenModel` — decenas
   de sitios) queda como **deuda documentada**, no aplicado por volumen: es
   un cambio mecánico pero de alta superficie (~20+ funciones) que se
   prefiere revisar en una pasada dedicada en vez de aplicarlo a ciegas.
6. **`calculateNextDueDate` (repo) duplica `RecurrenceRules.nextOccurrence`**
   (testeada) con una implementación distinta y sin test. **PROPUESTA —
   riesgo medio, no aplicado:** sustituir una por otra cambia fechas
   calculadas en producción.
7. `GoogleAuthManager` — `CoroutineScope` propio tipo singleton sin
   cancelación explícita. **PROPUESTA** — documentado, no es un bug activo.
8. `TaskScreenModel.kt` (1077 líneas) — "mini god ScreenModel". **PROPUESTA**
   — refactor de superficie media/alta.

### BAJO

9. Valores por defecto en el parseo de Firestore ocultan fallos de escritura
   previos sin log. **PROPUESTA** — no aplicado (requiere `DebugFlags`
   condicional, se deja documentado como mejora de observabilidad).
10. `GoogleCalendarRepository.validateToken` — mismo patrón #5. **Aplicado**
    (incluido en el fix #5 de arriba).

---

## Experto 7 — Jefe de arquitectura

### Veredicto por subsistema

| Subsistema | Veredicto |
|---|---|
| Capas UI ↔ dominio ↔ red | **En riesgo** — 10 de 23 pantallas se saltan el ScreenModel e inyectan repo/stores directamente; donde sí se respeta la capa, el patrón es limpio. |
| `FirestoreRepository` | **En riesgo** — god object de ~2500 líneas / 9 dominios; mapeo DTO↔dominio parcialmente extraído a `FirestoreParsers` pero inconsistente; sin paginación. |
| DI / Koin | **Sano** — módulo único pero aceptable a este tamaño; scopes (`single`/`factory`) correctos. |
| Gestión de estado | **Aceptable** — patrón StateFlow consistente entre los 7 ScreenModels; falta single source of truth para "miembro actual" y la caché no se invalida tras escrituras, pero ninguno es bug activo hoy. |
| Seguridad | **Crítico** — `firestore.rules` v4 (cierra el robo de puntos vía `reassignTaskCompletion` y el auto-ascenso a admin) **sigue sin desplegar en producción**, confirmado en la cabecera del propio archivo. |
| Escalabilidad | **En riesgo** — sin paginación en ninguna colección; `getAllAssignments` sin límite de concurrencia (una petición HTTP por tarea, en paralelo, sin techo). |

### Hallazgos (todos PROPUESTA salvo donde se indica — refactor arquitectónico
### fuera del alcance de "aplica ya" de este encargo)

1. 10 pantallas inyectan `FirestoreRepository`/`HouseholdStore`/`SettingsStore`
   directamente vía `koinInject<...>()`, saltándose sus ScreenModels
   (`HouseholdScreen`, `TaskDetailScreen`, `EditTaskScreen`, `StatsScreen`,
   `RankingScreen`, `RewardListScreen`, `CreateRewardScreen`,
   `WelcomeScreen`, `HomeScreen`, `ProfileScreen`).
2. `FirestoreRepository.kt` god object — dividir en repositorios por dominio
   sobre un `FirestoreClient` común.
3. **`firestore.rules` v4 sin desplegar** — hallazgo de seguridad activo,
   central al mandato de esta revisión. Requiere una acción manual del
   usuario (`PATCH`, no `POST`, según la cabecera del propio archivo) tras
   revisar el impacto — no es algo que este encargo pueda aplicar
   automáticamente.
4. Manejo de errores inconsistente — `FirestoreException` (con
   `statusCode`/`code`) solo se usa en `HouseholdScreenModel`; el resto
   captura `Exception` genérica y descarta esa información.
5. Sin política de reintento/backoff para fallos de red transitorios (fuera
   del reintento de concurrencia optimista ya existente en `addMemberPoints`).
6. Sin paginación en ninguna colección — `StructuredQuery`/`RunQueryRequest`
   definidos en `FirestoreDtos.kt` y nunca usados (código muerto que confirma
   que nunca se implementó). `getAllAssignments` sin límite de concurrencia.
7. `TaskScreenModel`/`MemberScreenModel` — "mini god ScreenModels" (mismo
   patrón que #2 un nivel más arriba).
8. Sin single source of truth para "miembro actual" — `resolveCurrentMember`
   se recalcula de forma independiente en múltiples sitios sin caché
   compartida.
9. `TaskCache` solo se escribe en el camino de lectura, nunca se invalida
   tras una escritura exitosa (benigno hoy porque casi toda mutación va
   seguida de un refresh, pero es una trampa latente).
10. DI (`AppModule.kt`) — organización correcta; único matiz:
    `GoogleAuthManager`/`CalendarSyncManager` con scope de coroutine propio
    sin cancelación (mismo hallazgo que Senior #7).
11. Refresh tokens (Google y anónimo) persistidos en texto plano vía
    `multiplatform-settings`, sin cifrado adicional en Android. Riesgo
    bajo-medio (dispositivo rooteado/backup no cifrado).
12. `updateTask` hace `deleteAssignments` + `assignTask` como 2 operaciones no
    atómicas — si la 2ª falla a mitad, la tarea queda sin ninguna asignación.
    Se añade a `docs/atomicidad-commit-pendiente.md` como hallazgo nuevo (no
    estaba documentado ahí).
13. `StatsScreen`/`RankingScreen` no tienen ScreenModel propio — coincide con
    el hallazgo #1 (son 2 de las 10 pantallas que llaman al repo directo).

---

## Experto 8 — QA / Detección de bugs

Confirmado primero: el fix de `updateMask.fieldPaths` (en curso al arrancar
esta revisión) está aplicado consistentemente en las 17 llamadas `.patch(...)`
del archivo — ninguna quedó sin migrar al helper.

### CRÍTICO — ambos aplicados

1. **`completeAssignment` no otorga puntos** — ver "Hallazgo destacado".
   **Aplicado.**
2. **`completeTask` sin `currentDocument.updateTime`** — dos dispositivos
   completando la misma tarea casi a la vez pueden duplicar puntos e
   historial. **PROPUESTA — riesgo medio, no aplicado:** añadir concurrencia
   optimista a `completeTask` (leer + `currentDocument.updateTime` + reintento,
   igual que `addMemberPoints`) es exactamente el tipo de cambio de política
   de escritura/reintento que el encargo pide dejar fuera de "aplica ya" —
   requiere decidir el comportamiento ante conflicto (¿qué error mostrar?).

### ALTO

3. **`redeemReward`/`donatePoints` — saldo puede quedar negativo (TOCTOU)**.
   **Aplicado** el guard de `redeemReward` (ver Senior #2); `donatePoints` ya
   validaba vía `PointsRules.validateDonateBalance` (con la misma limitación
   de condición de carrera entre 2 escrituras concurrentes, documentada y no
   nueva).

### MEDIO

4. `appreciateMember` sin `currentDocument.updateTime` — bypass del tope
   semanal por carrera entre 2 "agradecer" casi simultáneos. **PROPUESTA —
   riesgo medio, no aplicado**, mismo motivo que el hallazgo #2.
5. `completeAssignment` sin idempotencia server-side — doble invocación
   duplica la siguiente asignación de una tarea recurrente. **PROPUESTA — no
   aplicado**, mismo motivo.

### BAJO

6. `catch (_: Exception)` en `findTaskHistoryRecord`/`deleteAssignments` traga
   `CancellationException`. **Aplicado** (mismo fix que Senior #5).

No se encontraron crashes por `!!`, parseo de fecha sin manejo de errores, ni
acceso a colección vacía sin guarda en `FirestoreRepository.kt`/`PointsRules.kt`/
`RecurrenceRules.kt`.

---

## Fixes aplicados — archivos tocados

**Puntos/dinero (el fix de mayor impacto de esta pasada)**
- `network/FirestoreRepository.kt` — `completeAssignment` ahora otorga puntos
  reales (`addMemberPoints`), guarda historial (`saveTaskHistory`) y
  sincroniza `completedBy`/`lastCompletedDate` en la tarea, igual que
  `completeTask`. Nuevo helper privado `markAssignmentCompleted` +
  función pública `syncAssignmentOnTaskCompleted`.
- `ui/models/TaskScreenModel.kt` — `completeTask` llama a
  `syncAssignmentOnTaskCompleted` tras completar, para que la asignación no
  quede "reabrible" (evita duplicar puntos entre los dos flujos de
  completado).
- `network/FirestoreRepository.kt` — `redeemReward` valida saldo del miembro
  contra una lectura fresca antes de descontar.

**Funcionalidad / controles muertos**
- `ui/components/SettingsSheet.kt` — nuevo flag `showExportCsv` en
  `SettingsCallbacks`; el botón "Exportar CSV" ya no se muestra en pantallas
  sin contexto de tareas cargado.
- `ui/screens/HomeScreen.kt`, `ProfileScreen.kt`, `WelcomeScreen.kt`,
  `ui/components/HouseholdDialogs.kt` — `showExportCsv = false` en los 4
  puntos de entrada que no tienen la lista de tareas disponible.
- `ui/screens/EditTaskScreen.kt` — el botón "Guardar" se deshabilita también
  si falló la carga de asignaciones (evita desasignar a todos los miembros
  en silencio).

**Accesibilidad (contraste WCAG AA)**
- `ui/screens/RankingScreen.kt` — texto secundario (ordinal/rol/racha) y
  nombre del miembro con colores fijos emparejados al fondo (antes
  `onSurfaceVariant` sobre `Teal50`/`Coral100` fijos).
- `ui/screens/NotificationListScreen.kt` — mismo patrón para
  título/hora/mensaje de notificaciones no leídas; contador "X sin leer"
  migrado a `semanticColors.info`.
- `ui/screens/PublicProfileScreen.kt` — badge de rol migrado a
  `tertiary`/`onTertiary` y `primaryContainer`/`onPrimaryContainer`.
- `ui/screens/ProfileScreen.kt` — icono de hogar compartido migrado de
  `Coral500` fijo a `colorScheme.tertiary`.
- `ui/components/UserAvatar.kt` — color de fondo por defecto migrado de
  `Teal100` fijo a `colorScheme.primaryContainer`.

**Consistencia de tema / UI**
- `ui/screens/CalendarScreen.kt` — colores de estado
  (`OverdueColor`/`DueTodayColor`/`CompletedColor`) sustituidos por la
  paleta semántica (`success`/`error`/`info` + sus containers), aplicados en
  los 3 puntos donde se usaban (dots del mes, `TaskChip` de la semana, dot
  del popup).
- `ui/screens/ProfileScreen.kt` — icono "ir al espacio" de `ArrowBack` a
  `ArrowForward` (semánticamente correcto).
- `ui/screens/StatsScreen.kt`, `RewardListScreen.kt`, `EditTaskScreen.kt` —
  `TextOverflow.Ellipsis` en 4 textos que antes se cortaban a media palabra;
  leyenda del pie chart con `Modifier.weight(1f)` para no desbordar.

**Calidad de código (senior) — `CancellationException`**
- `network/FirestoreRepository.kt` — `isOnline`, `leaveHousehold` (3
  bloques), `findTaskHistoryRecord`, `deleteAssignments`, `tryAuthOrApiKey`.
- `network/GoogleCalendarRepository.kt` — `validateToken`.

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   # BUILD SUCCESSFUL
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain                      # BUILD SUCCESSFUL
```

No se compiló el target iOS (no hay toolchain de Xcode/macOS en este
entorno); los cambios de esta pasada no tocan código `iosMain` salvo el
import añadido en `GoogleCalendarRepository.kt` (commonMain, se compila igual
para todas las plataformas).

## Deuda técnica / propuestas no aplicadas (por qué, y coste/beneficio)

### Seguridad — acción del usuario requerida

1. **`firestore.rules` v4 sigue sin desplegar.** Confirmado leyendo la
   cabecera del propio archivo ("v4 — NO DESPLEGADO"), pese a que el encargo
   de esta revisión asumía que ya estaba desplegado. **Esto NO es algo que
   este encargo pueda aplicar automáticamente** (requiere `PATCH` manual a
   Firebase, según indica el propio archivo) — mientras no se despliegue,
   las restricciones de rol que el cliente ya asume (incluida
   `reassignTaskCompletion`) solo existen como gating de UI, no de servidor.
   **Recomendación: desplegar cuanto antes**, es el hallazgo de seguridad de
   mayor prioridad de todo el panel.

### Arquitectónicas — refactors grandes, requieren decisión explícita del usuario

2. **`FirestoreRepository.kt` god object (~2500 líneas, 9+ dominios).**
   Dividir en repositorios por dominio (`HouseholdRepository`,
   `TaskRepository`, `MemberRepository`, `RewardsRepository`,
   `NotificationRepository`) sobre un `FirestoreClient` común. Coste alto
   (~2500 líneas a mover + re-cablear Koin/ScreenModels/screens con
   inyección directa); beneficio alto — habría hecho mucho más difícil que
   pasara desapercibido el bug de puntos del hallazgo destacado.
3. **10 de 23 pantallas se saltan sus ScreenModels** e inyectan
   `FirestoreRepository`/stores directamente. Coste medio (mover cada
   llamada a un ScreenModel existente o crear uno nuevo para
   `StatsScreen`/`RankingScreen`, que hoy no tienen); beneficio alto —
   unifica manejo de error/caché y hace testeable la lógica sin Compose.
4. **Sin paginación en ninguna colección de Firestore** —
   `StructuredQuery`/`RunQueryRequest` definidos y nunca usados;
   `getAllAssignments` sin límite de concurrencia. Con 10x datos (hogares con
   muchas tareas/miembros), tiempos de carga y número de conexiones HTTP
   simultáneas crecen sin techo. Coste alto (cambia el contrato de varias
   funciones `get*`); beneficio alto a medio-largo plazo, bajo hoy (los
   hogares reales son pequeños).

### Calidad de código (senior) — riesgo medio, cambian comportamiento

5. **`ensureAuth()` sin `Mutex`.** Ráfagas de llamadas paralelas pueden
   disparar refrescos/altas de token concurrentes. Fix de bajo coste técnico
   (`Mutex` de instancia) pero cambia el flujo de auth — se deja fuera de
   "aplica ya" según el criterio explícito del encargo.
6. **`completeTask`/`appreciateMember` sin concurrencia optimista** (a
   diferencia de `addMemberPoints`, que sí la tiene). Dos dispositivos
   completando la misma tarea o agradeciendo casi a la vez pueden duplicar
   puntos o saltarse el tope semanal. Requiere decidir la política de
   conflicto (qué error mostrar al perdedor de la carrera) — riesgo medio,
   no aplicado.
7. **`calculateNextDueDate` (repo) duplica `RecurrenceRules.nextOccurrence`**
   (testeada) con una implementación distinta, sin test, y es la que corre
   en producción para tareas recurrentes completadas vía asignación.
   Sustituir una por otra cambiaría fechas calculadas — riesgo medio.
8. **Patrón `catch (Exception)` sin relanzar `CancellationException`** en
   ~20+ funciones de los 7 ScreenModels (fuera de las 7 corregidas en
   `FirestoreRepository.kt`/`GoogleCalendarRepository.kt` en esta pasada).
   Mecánico pero de alta superficie — se deja para una pasada dedicada.
9. **Penalización por retraso nunca se aplica en `completeTask`** (el 100%
   de los casos reales de completar desde la lista) — solo
   `completeAssignment` la calculaba. Cambia cuántos puntos otorga una
   acción ya en producción — requiere decisión explícita del usuario.

### Estéticas — cambios de identidad visual, decisión del usuario

10. `EmptyStateIllustrations.kt` con colores de marca fijos en las 2
    ilustraciones "hero" de estado vacío — rompe Naturaleza/Minimal pero
    redibujar un `Canvas` custom sin QA visual es alto riesgo.
11. Mezcla de iconografía emoji vs Material vector icons en casi toda la
    app, especialmente `"❌"` como sustituto de icono de error.
12. `WelcomeScreen`/`CreateHouseholdScreen`/`JoinHouseholdScreen` sustituyen
    `AppLogo` por emojis genéricos en el onboarding.
13. Pantallas anidadas de un hogar (`HouseholdScreen`, `TaskListScreen`,
    `CalendarScreen`) sin el nombre del hogar activo en la topbar.
14. 3 formas de "badge/stat chip" distintas coexistiendo
    (`PointsBadge`/`InfoBadge`/`StatItem`) — candidato a unificar, refactor
    de superficie media.
15. Deuda de literales de color (`FilterChipsRow`,
    `OutlinedTextFieldDefaults.colors` duplicado 5 veces,
    `ShimmerPlaceholder` con `RoundedCornerShape` literal) — se agrupa con la
    deuda de ~200 literales ya documentada en la 1ª edición.

## Riesgos pendientes — resumen para el usuario

El riesgo más urgente de todo el panel es doble: (1) el bug de puntos ya
corregido en esta pasada estuvo activo en producción durante un tiempo
indeterminado para el flujo de completar tareas asignadas con fecha límite
— usuarios reales pueden tener un `totalPoints` por debajo de lo que la app
les mostró en su momento como "ganado" (no hay forma de reconstruir
retroactivamente cuántos puntos se perdieron sin un script de migración
dedicado, fuera de alcance de esta revisión); y (2) `firestore.rules` v4
sigue sin desplegar, así que las protecciones de rol que el cliente ya da
por hechas (incluida la que motivó el fix de la 1ª edición) no existen aún
del lado del servidor. Ambos deberían ser la prioridad inmediata del
usuario, por delante de cualquier refactor arquitectónico de la lista de
propuestas.
