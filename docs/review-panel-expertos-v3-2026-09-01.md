# Panel de expertos v3 — 13 especialistas + foco en recurrencia (2026-09-01)

3ª edición del panel de revisión de Task Hub. La 1ª (`docs/review-panel-expertos-2026-08-31.md`,
5 expertos) y la 2ª (`docs/review-panel-expertos-v2-2026-08-31.md`, 8 expertos) partían de
v0.7.23. Esta edición amplía a **TRECE expertos** más un **subagente dedicado a tareas
recurrentes** (pedido explícito del usuario), y se centra en lo que las dos rondas anteriores
NO pudieron ver: los commits `df6741b` (refactor arquitectura + reglas v4), `f2521d9`
(god-object split + estéticas objetivas), `b529100` (roles seguros Cloud + renombrar
"Niño/a"→"Miembro") y `316de8b` (i18n completo + háptico), todos posteriores al checkpoint de
la v2. Versión de partida: **0.7.25**. Los 14 subagentes se lanzaron en paralelo, sin verse
entre sí, cada uno con mandato independiente.

## Hallazgo destacado del panel

Ningún bug de la gravedad del "puntos que nunca llegaban a `totalPoints`" de la v2 ha
reaparecido. El god-object split (`f2521d9`) es, según el jefe de arquitectura, un split
**bien hecho y no a medias**: extrae `FirestoreClient` (transporte/auth) y 4 dominios
(`TaskRepository`, `HouseholdRepository`, `RewardsRepository`, `NotificationRepository`) sobre
una fachada (`FirestoreRepository`, 1726 líneas, bajó de ~2733) que delega limpiamente, sin
duplicar inyección en Koin ni romper el trabajo de ScreenModel de la ronda anterior. El
hallazgo de seguridad **crítico** de la v2 (`firestore.rules` v4 sin desplegar) está
**confirmado resuelto** — desplegado el 2026-08-31, alineado con las suposiciones del cliente.

El hallazgo funcional más importante de esta ronda: **`completeTask` (el flujo principal de
"Marcar hecho") no regenera la asignación de la siguiente ocurrencia para tareas recurrentes
asignadas** — solo `completeAssignment` (el flujo secundario, poco usado) lo hace. Esto rompe
la sincronización con Google Calendar y dejar "huérfana" la UI de asignaciones a partir del
segundo ciclo de una tarea recurrente asignada. Se documenta como PROPUESTA (ver sección de
recurrencia) por su interacción con otro bug no resuelto (`assignmentRotation` no gobierna
realmente quién es el siguiente asignado) — aplicarlo a medias sería peor que no aplicarlo.

## Resumen — hallazgos por experto y severidad

| Experto | CRÍTICO | IMPORTANTE/ALTO | MENOR/MEDIO/BAJO | Total | Aplicados |
|---|---|---|---|---|---|
| 1 — Estética | 0 | 4 | 2 | 6 | 4 |
| 2 — Funcionalidad | 1 | 0 | 1 | 2 | 0 (propuesta, ver recurrencia) |
| 3 — Accesibilidad | 1 | 2 | 3 | 6 | 6 |
| 4 — UI/Componentes | 0 | 1 | 0 | 1 | 1 |
| 5 — UX | 0 | 2 | 4 | 6 | 3 |
| 6 — Programador senior | 0 | 3 | 5 | 8 | 3 |
| 7 — Jefe de arquitectura | 0 | 0 | 3 | 3 (+ veredictos) | 0 (todo propuesta/verificación) |
| 8 — QA/bugs | 0 | 0 | 2 | 2 | 0 (propuesta) |
| 9 — Seguridad | 0 | 1 | 6 | 7 | 3 |
| 10 — Privacidad/RGPD | 0 | 3 | 4 | 7 | 0 (todo propuesta, legal/producto) |
| 11 — Rendimiento | 0 | 5 | 3 | 8 | 3 |
| 12 — Fiabilidad de red | 1 | 2 | 0 | 3 | 0 (propuesta, cambia política) |
| 13 — Cobertura de tests | — | — | — | mapa + top-10 | 0 (no se pide aplicar) |
| Foco — Recurrencia | 0 | 4 | 3 | 7 | 1 |
| **Total** | **3** | **27** | **36** | **66** | **24** |

## Tabla top-10 impacto / esfuerzo

| # | Hallazgo | Experto(s) | Impacto | Esfuerzo | Estado |
|---|---|---|---|---|---|
| 1 | `completeTask` no regenera asignación de la siguiente ocurrencia recurrente (rompe sync Calendar) | Funcionalidad, Recurrencia | Alto | Medio | ⚠️ PROPUESTA (interactúa con #2) |
| 2 | `assignmentRotation` no gobierna nada — siempre reasigna al mismo miembro | Recurrencia | Alto | Medio-Alto | ⚠️ PROPUESTA (decisión de producto) |
| 3 | Contraste roto por `.copy(alpha=0.6-0.85f)` sobre pares ya auditados (7 sitios, incl. toda la tarjeta de recurrencia) | Accesibilidad | Crítico | Bajo | ✅ Aplicado |
| 4 | `completeAssignment`/`redeemReward` sin concurrencia optimista (duplicar puntos en carrera de 2 dispositivos) | Fiabilidad de red, QA | Crítico | Medio | ⚠️ PROPUESTA (cambia política de conflicto) |
| 5 | Inyección de fórmulas CSV (CWE-1236) | Seguridad | Medio | Bajo | ✅ Aplicado |
| 6 | Penalización por retraso casi nunca se aplica a recurrentes (`dueDate` vacío) | Recurrencia | Alto | Medio | ⚠️ PROPUESTA (requiere `nextDueAt`) |
| 7 | `orDefault` cuadruplicado byte-a-byte en 4 archivos | Senior, Arquitectura | Medio | Bajo | ✅ Aplicado |
| 8 | Cambio de rol de miembro sin confirmación (única acción de alto impacto sin diálogo) | UX | Alto | Bajo | ✅ Aplicado |
| 9 | `reconcileHouseholds` secuencial en el arranque (pantalla más usada) | Rendimiento | Alto | Bajo | ✅ Aplicado |
| 10 | Módulo `server/` huérfano con Admin SDK sin autenticación (bypass total de `firestore.rules`) | Seguridad | Latente | Bajo | ✅ Eliminado |

---

## Experto 1 — Estética / Diseño visual

Confirmado: `f2521d9` ("estéticas objetivas") reemplazó `"❌ ..."` por `Icon(Close)+Text` en 13
pantallas y añadió `ErrorAwareSnackbarHost` con tokens correctos en los 3 temas — trabajo
bien hecho. Hallazgos nuevos, todos verificados contra el estado real del código en 0.7.25:

### IMPORTANTE — 3 aplicados, 1 diferido
1. **Nombre del hogar en topbar solo en 3 de ~9 pantallas anidadas** (`HouseholdScreen`,
   `TaskListScreen`, `CalendarScreen` sí; `CreateTaskScreen`, `EditTaskScreen`,
   `TaskDetailScreen`, `ExploreScreen`, `NotificationListScreen`, `CreateRewardScreen`,
   `MemberRewardScreen` no). **PROPUESTA — no aplicado**: requiere tocar 7 pantallas
   (inyectar `HouseholdScreenModel` + `loadHousehold` + `subtitle`), fuera de una pasada
   mecánica de una línea; se documenta para una pasada dedicada.
2. `CreateProfileScreen.kt:159` — emoji de admin (`"👨‍👩‍👧"`) distinto al resto de la app
   (`"👑"`). **Aplicado**: usa ahora `member_role_admin_short`, clave ya auditada; se borró
   la clave `create_profile_role_admin` huérfana.
3. `WelcomeScreen.kt:176` — versión hardcodeada `"v0.7.23"`, desincronizada 2 versiones pese
   al comentario explícito que pide actualizarla en cada bump. **Aplicado**: `"v0.7.25"`.
4. Selector de días de recurrencia (`CreateTaskScreen.kt:422`, `EditTaskScreen.kt:450`) usa
   `Row` sin wrap (7 `FilterChip`), inconsistente con el `FlowRow` del selector de
   frecuencia justo encima — riesgo de recorte en pantallas estrechas/fontScale alto.
   **Aplicado**: `Row`→`FlowRow` en ambos archivos.

### MENOR — sin aplicar (decisión de identidad)
5. `Icons.Default.Close` usado como icono de "error" (semánticamente es "cerrar/descartar").
   **PROPUESTA** — cambiaría el glifo en toda la app a la vez.
6. Mezcla emoji/Material vector sigue presente en `AppStrings.kt` (`✅`/`⚠️`). Mismo hallazgo
   que v1 Estética #2, sin cambio de clasificación.

Confirmado sin regresión: `EmptyStateIllustrations.kt` (colores fijos, sigue como propuesta
v2), emojis de onboarding (`WelcomeScreen`/`CreateHouseholdScreen`/`JoinHouseholdScreen`,
sigue como propuesta v2), UI de recurrencia en `CalendarScreen`/`TaskDetailScreen` usa
`colorScheme`/`semanticColors` consistentemente, renombrado "Miembro" sin cadenas huérfanas.

---

## Experto 2 — Funcionalidad (flujos end-to-end)

Confirmado resuelto desde v2: penalización por retraso ya se calcula en `completeTask`
(entró en `07929aa`, antes del checkpoint de esta ronda). God-object split verificado
arquitectónicamente sano para funcionalidad — sin lógica duplicada entre `FirestoreRepository`
y los 4 repos nuevos, sin plomería DI rota.

### CRÍTICO/ALTO
1. **`completeTask` no regenera la asignación de la siguiente ocurrencia para tareas
   recurrentes** (`FirestoreRepository.kt:1054-1104`) — solo `completeAssignment`
   (`FirestoreRepository.kt:1392-1409`) lo hace, pero es el flujo minoritario. Como
   `CalendarSyncManager.onTaskAssigned` solo se dispara al crear una asignación nueva, una
   tarea recurrente completada por el botón principal de la lista **deja de generar eventos
   de Google Calendar a partir del segundo ciclo**, sin error visible; y `TaskDetailScreen`
   queda mostrando la asignación del ciclo ya cerrado. Ver sección de Recurrencia — marcado
   **SOLO PROPUESTA** por su interacción con el hallazgo #2 de la rotación de asignación
   (aplicar uno sin el otro podría empeorar el problema).

### MEDIO
2. `CalendarScreen.isTaskDueOnDay` (líneas 837-881) reimplementa la lógica de
   `RecurrenceRules.isDueToday` en vez de reutilizarla (generalizada a fecha arbitraria).
   Coinciden en resultado hoy, pero es una segunda copia mantenida a mano que puede divergir
   si `RecurrenceRules` cambia. **PROPUESTA** — requiere generalizar la firma de
   `RecurrenceRules` (fuera de un fix mínimo).

Confirmado sin regresión: canjear recompensa, unirse a hogar, mensajería, agradecer/donar
puntos, exportar CSV, cambio de rol, háptico (sin dobles disparos ni disparo en fallo).

---

## Experto 3 — Accesibilidad (WCAG AA + Android)

Ratios verificados con `wcag_contrast.py` sobre los hex reales de los 6 combos (3 temas ×
claro/oscuro) de `Theme.kt`/`SemanticColors.kt` v0.7.25.

### CRÍTICO — Aplicado
1. **Patrón sistémico: `.copy(alpha=0.6-0.85f)` sobre pares `on*Container`/`onSurfaceVariant`
   ya auditados a ≥4.5:1, roto por la opacidad añadida.** 7 sitios:
   - `TaskDetailScreen.kt:365,407,417,464,470` — toda la tarjeta "Task info" (descripción,
     recurrencia, penalización), exactamente la UI de recurrencia del foco de esta ronda.
     Fallaba en DEFAULT oscuro (3.73:1) y Naturaleza claro/oscuro (4.32/3.74:1).
   - `HouseholdScreen.kt:432,451` — código de invitación del hogar. Fallaba en DEFAULT
     claro/oscuro (4.06/3.50:1) y Naturaleza claro/oscuro (3.89/3.50:1).
   - `CreateRewardScreen.kt:239` — descripción en preview de recompensa. Mismos ratios que
     el caso anterior.
   - `TaskListScreen.kt:795` y `TaskDetailScreen.kt:611` — texto de tarea/subtarea
     "completada" con `onSurfaceVariant.copy(alpha=0.6f)`: **fallaba en los 6 temas**
     (3.00-4.48:1), no un caso de borde de un tema. El tachado (`TextDecoration.LineThrough`)
     ya comunica "completado" sin necesitar bajar opacidad.
   **Fix aplicado**: quitar el `.copy(alpha=...)` en los 7 sitios, color a alpha=1.0
   (verificado ≥4.5:1 en los 6 combos).

### IMPORTANTE — Aplicado
2. Chips de día de la semana (recurrencia) con nombre accesible ambiguo en inglés
   (`day_letter_*` da "T" para martes/jueves y "S" para sábado/domingo — TalkBack no puede
   distinguirlos). **Fix aplicado**: `Modifier.semantics { contentDescription = ... }` con
   el nombre completo del día (`recurrence_day_*`, ya existente) en
   `CreateTaskScreen.kt`/`EditTaskScreen.kt`, manteniendo la letra como label visible.
3. Cabeceras de grupo/plantillas sin `heightIn(min=48.dp)` pese a que el patrón correcto ya
   existe 4 veces en el código (`HouseholdMemberList.kt:45`, `HouseholdTaskSection.kt:69,170`,
   `SettingsSheet.kt:514`). `TaskListScreen.kt:1098` (~40dp real) y `CreateTaskScreen.kt:891`
   (~32dp real). **Fix aplicado**: `heightIn(min=48.dp)` en ambos.

### MENOR — sin aplicar
4. `CalendarScreen.MonthDayCell` — puntos de color sin `contentDescription` de apoyo en la
   vista de mes compacta. **PROPUESTA** — rediseño de celda, patrón estándar de calendario.
5. `FilterChip` a 32dp por defecto (M3) en selectores de recurrencia. **PROPUESTA** —
   decisión de sistema de diseño transversal, no puntual.

Confirmado sin regresión: contraste base de los 6 temas, `UserAvatar`, `CalendarScreen`
popup/colores de estado, reduce-motion, i18n sin literales fuera de `s(...)`.

---

## Experto 4 — UI / Diseño de componentes

Confirmado: los 4 hallazgos "PROPUESTA" de v2 (unificar `PointsBadge`/`InfoBadge`/`StatItem`
en `StatChip`, deduplicar `FilterChipsRow`, `taskHubTextFieldColors()`,
`ShimmerPlaceholder` con `MaterialTheme.shapes.medium`) **ya estaban aplicados** en un commit
intermedio anterior a esta ronda (`07929aa`) — no repetidos.

### MENOR — Aplicado
1. `EditTaskScreen.kt:705-710` — etiqueta de día en el selector de rotación de asignación
   sin `maxLines`/`overflow`, único punto de la pantalla que se quedó sin el patrón ya
   aplicado 2 veces más abajo en el mismo archivo (`:720-722`, `:830-832`). Con i18n en
   inglés ("Wednesday") el riesgo de *wrap* a 2 líneas rompe la alineación vertical de las
   7 filas. **Fix aplicado**: `maxLines = 1, overflow = TextOverflow.Ellipsis`.

Verificado sin hallazgo: Material3 vs custom (`Canvas` solo en gráficos legítimos), colores
hardcodeados (`grep "Color(0xFF"` sin resultados fuera de `Theme.kt`), UI de recurrencia
(`FlowRow` correcto en frecuencia, `Row` de 7 letras seguro sin overflow), renombrado
"Miembro" sin overflow en ningún contenedor de ancho fijo.

---

## Experto 5 — UX / Experiencia de uso

Confirmado: los 4 hallazgos de v2 (topbar `pop()` unificado, botón de orden con
`contentDescription`, validación de tope de penalización, mensajes "sin tareas" en
Calendario) **ya estaban aplicados**, no repetidos.

### ALTA — 2 aplicados
1. **Cambio de rol sin confirmación** (`HouseholdMemberList.kt`, `DropdownMenuItem`
   "Admin"/"Miembro") — única acción de alto impacto (da/quita control total del hogar) sin
   ningún paso intermedio, inconsistente con borrar/salir del hogar (que sí tienen diálogo).
   **Fix aplicado**: `AlertDialog` de confirmación con nombre del miembro + rol nuevo,
   reutilizando el patrón ya existente en el mismo archivo.
2. Fallo al cambiar rol/eliminar miembro borra toda la lista visible (`MemberScreenModel`
   reutiliza el mismo `_uiState` para lista y para el resultado de la mutación).
   **PROPUESTA — no aplicado**: requiere un `MemberActionState` separado (nuevo sealed
   class + StateFlow + wiring en `HouseholdScreen`), refactor de superficie media que se
   prefiere no aplicar sin QA visual dedicada en esta pasada.

### MEDIA — 1 aplicado, 2 propuestas
3. `updateMemberRole`/`removeMember` sin `buzz(SUCCESS/ERROR)`, inconsistente con las 8
   mutaciones vecinas del mismo archivo que sí lo hacen. **Fix aplicado**:
   `buzz(HapticKind.SUCCESS/ERROR)` en ambas funciones (`MemberScreenModel.kt`).
4. Sin preview "próxima vez: X" en el formulario de recurrencia — `RecurrenceRules
   .nextOccurrence` ya existe y está testeada, pero ninguna pantalla la usa. **PROPUESTA**
   — requiere nueva clave i18n y decidir posición en un formulario ya largo.
5. "Semanal sin ningún día marcado" equivale silenciosamente a "todos los días"
   (`recurrenceDays.isEmpty()` en `RecurrenceRules.isDueToday`), sin ningún aviso en la UI.
   **PROPUESTA** — decidir semántica (bloquear guardado vs. avisar) es decisión de producto.

### BAJA — 1 aplicado
6. Campo "Día del mes" sin explicar el clamp de fin de mes, pese a que el propio KDoc de
   `clampDayOfMonth` reconoce que no es obvio. **Fix aplicado**: `supportingText` en
   `CreateTaskScreen.kt`/`EditTaskScreen.kt` con la nueva clave `recurrence_day_of_month_hint`.
7. `MemberScreenModel.removeMember` sin ningún botón que lo invoque en la UI — código muerto
   o feature incompleta. **PROPUESTA** — requiere decidir si se conecta o se retira.

---

## Experto 6 — Programador senior / Calidad de código

Confirmado resuelto desde v2 (sin repetir): `ensureAuth()` ya tiene `Mutex`;
`calculateNextDueDate` ya delega en `RecurrenceRules.nextOccurrence` (ya no duplica).

### MEDIO — 2 aplicados, 2 propuestas
1. **`orDefault` cuadruplicado byte-a-byte** en `FirestoreRepository.kt`, `TaskRepository.kt`,
   `RewardsRepository.kt`, `NotificationRepository.kt` — función pura sin dependencia de
   estado de clase, duplicada "para no acoplar al god object". **Fix aplicado**: movida a
   función `internal` de paquete en `FirestoreClient.kt`; las 4 copias privadas borradas.
2. `createTask`/`updateTask` en `TaskRepository.kt` (131/109 líneas) duplican bloques
   idénticos de serialización (tags, subtasks, `assignmentRotation`, penalty). **PROPUESTA**
   — extraer 3-4 helpers privados, refactor de superficie media en funciones con 15
   parámetros cada una.
3. **`GoogleCalendarRepository.apiKey`/`DEFAULT_API_KEY` sin usar** — declarado y asignado
   pero ninguna petición lo adjunta (todo usa OAuth Bearer). **Fix aplicado**: eliminado
   parámetro y constante (coincide con hallazgo B3 de Seguridad).
4. `TaskScreenModel.kt` sigue creciendo (1172 líneas, era 1077 en v2). **PROPUESTA** — ya
   documentado, sin cambio de fondo.

### BAJO — 1 aplicado, resto documentado
5. `isMember(householdId, userIds)` sin call-sites externos, candidato a `private`.
   **No aplicado** en esta pasada (bajo valor, riesgo cero cualquiera de las dos opciones).
6. Boilerplate de forwarding repetido en los 4 repos de dominio (`withAuth()`,
   `tryAuthOrApiKey()`, etc.) — necesario porque son extension functions miembro de
   `FirestoreClient`. **Sin acción** — concesión razonable, no un bug.
7. `catch(Exception)` sin relanzar `CancellationException` en sitios movidos por el split
   (`HouseholdRepository.kt:74-78,104-108`, `FirestoreRepository.kt:582,605,471,533`) — misma
   deuda documentada en v2, ahora repartida en 2 archivos. **PROPUESTA** — pasada dedicada.

Verificado sin hallazgo: cero `!!` en todo el ámbito revisado; un único cast `as` (patrón
estándar de `KSerializer` custom, correcto); sin `var`/colecciones mutables expuestas en los
6 archivos nuevos del split; alineación roles cliente↔`firestore.rules` sin discrepancia.

---

## Experto 7 — Jefe de arquitectura

### Veredicto por subsistema (comparado con v2, partida v0.7.23)

| Subsistema | Veredicto v2 | Veredicto ahora (0.7.25) | Tendencia |
|---|---|---|---|
| Capas UI↔dominio↔red | En riesgo (10/23 saltaban ScreenModel) | **Sano** — 0 pantallas se saltan su ScreenModel tras el split (único `koinInject` directo son 2 singletons de sesión ya aceptados) | Mejora, se consolida |
| `FirestoreRepository` / god object | En riesgo (~2500 líneas/9 dominios) | **En riesgo, mejor delimitado** — 1726 líneas, 4/5 dominios extraídos como facade limpia; el 20% que queda (Members/Points) tiene justificación técnica sólida (evita ciclo con `MemberRepository` inexistente) | Mejora parcial, real |
| Mapeo DTO↔dominio (`FirestoreParsers`) | Parcial/inconsistente | **En riesgo** — solo cubre 2 de ~7 tipos de documento; el split multiplicó los sitios con parseo inline en vez de consolidar | Sin cambio de fondo |
| DI/Koin | Sano | **Sano** — cero duplicación (repos de dominio no están en Koin, son composición interna de la fachada) | Igual, se confirma |
| Gestión de estado (cachés, single source of truth) | Aceptable | **Sano** — invalidación de `TaskCache` repartida correctamente por dominio, memoización de `resolveCurrentMember` intacta | Igual, se confirma |
| Seguridad | **Crítico** (rules sin desplegar) | **Sano** — v4 desplegado 2026-08-31, alineado con `reassignTaskCompletion`/`deleteMember` | **Mejora crítica — resuelto** |
| Escalabilidad | En riesgo (sin paginación) | **En riesgo** — sin cambios; `getAllAssignments` sigue sin límite de concurrencia | Sin cambio |

### Hallazgos (todos PROPUESTA/verificación — refactor estructural fuera de "aplica ya")
1. Bloque Members/Points (~555 líneas) sigue íntegro en `FirestoreRepository.kt` — es hoy
   el chunk sin dividir más grande de `network/`. La razón de dejarlo fuera (evitar ciclo
   `TaskRepository`/`RewardsRepository`↔`MemberRepository` inexistente) es sólida, no una
   excusa: el mismo patrón sí se resolvió para `HouseholdRepository` inyectando `getLocalId`
   como lambda. **PROPUESTA** — extraer `MemberRepository` primero, inyectar lambdas.
2. `TaskListScreen`/`CalendarScreen` inyectan un `HouseholdScreenModel` extra solo para el
   nombre del hogar en la topbar — duplicación de fetch, no violación de capa. Citado también
   por Rendimiento #2.1.
3. `updateTask` sigue haciendo `deleteAssignments`+`assignTask` no atómico (documentado en
   `docs/atomicidad-commit-pendiente.md`), sin cambios desde v2.

---

## Experto 8 — QA / Detección de bugs

El split (`f2521d9`) es mecánico: firmas idénticas, mismo `taskCache`, mismos `catch
(CancellationException) { throw e }` conservados en las 4 clases nuevas. Verificado línea a
línea sin encontrar ningún bug CRÍTICO/ALTO nuevo introducido por el split, el rename de
roles, o el commit de i18n/háptico. `role == "child"`/`"admin"` siguen comparando contra el
valor interno persistido (nunca contra el texto localizado) — sin comparaciones rotas.

### MEDIO/BAJO — ambos PROPUESTA
1. `CalendarScreen.isTaskDueOnDay` duplica `RecurrenceRules.isDueToday` (mismo hallazgo que
   Funcionalidad #2) — riesgo de divergencia silenciosa, no bug activo hoy.
2. `Haptics.ios.kt:8-30` no envuelve `UIImpactFeedbackGenerator` en `try/catch`, asimetría
   con la variante Android (que sí lo hace). Riesgo real de crash bajo en uso normal de
   UIKit. **No aplicado** — sin toolchain de Xcode en este entorno para verificar en iOS.

No se encontraron: crashes por `!!`, parseo de fecha sin fallback, errores REST enmascarados,
leaks de `ScreenModel`/`CoroutineScope`, ni ningún camino NUEVO que duplique/pierda puntos
más allá de los ya conocidos por Fiabilidad de red.

---

## Experto 9 — Seguridad (AppSec / OWASP MASVS)

Confirmado (no repetido): `firestore.rules` v4 desplegada, cierra `reassignTaskCompletion` y
auto-ascenso a admin.

### MEDIO — 1 aplicado (M1), 1 propuesta
- **M1 — Inyección de fórmulas CSV (CWE-1236).** `generateCsv()` solo escapaba comillas del
  título, no neutralizaba `=`/`+`/`-`/`@` inicial — un título de tarea (texto libre, cualquier
  miembro puede escribirlo) tipo `=HYPERLINK(...)` se ejecuta al abrir el CSV en
  Excel/Sheets. **Fix aplicado**: `escapeCsvField()` antepone `'` si el campo empieza por
  esos caracteres, antes de envolver en comillas (`TaskScreenModel.kt`).
- M2 — La rama `isOwner`/`isAdminMember` de `create` en `members/{mid}` no valida los mismos
  límites (`totalPoints==0`, etc.) que la rama de auto-alta por invitación; un owner/admin
  con cliente modificado podría crear un miembro con cualquier UID y puntos arbitrarios.
  **PROPUESTA** — requiere editar y desplegar `firestore.rules`, no aplicable desde el repo.

### BAJO — 3 aplicados, 3 propuestas
- B2 — **Módulo `server/` huérfano**: API REST sin autenticación sobre Firebase Admin SDK
  (bypass total de `firestore.rules` si se reactivara). Confirmado no incluido en
  `settings.gradle.kts` ni en CI (comentario explícito: "el antiguo `:server` fue
  eliminado"). **Fix aplicado**: directorio `server/` eliminado del repo (`git rm`).
- B3 — `GoogleCalendarRepository` con API key sin usar. **Fix aplicado** (mismo cambio que
  Senior #3, un solo fix, dos hallazgos independientes).
- B1 — Refresh tokens en texto plano vía `multiplatform-settings` (Android
  `SharedPreferences`/iOS `NSUserDefaults` sin cifrar), mitigado parcialmente por
  `backup_rules.xml` (excluye `sharedpref` de backups). **PROPUESTA** — migrar a
  `EncryptedSharedPreferences`/Keychain, cambio de mayor alcance en 3 plataformas.
- B4 — API key de Firebase Web hardcodeada: **no es un secreto** (diseñada para ir en el
  cliente); recomendación de higiene (restricciones de API en Cloud Console) —
  **PROPUESTA**, acción manual fuera del repo.
- B5 — Scope OAuth de Calendar más amplio de lo necesario (`calendar` completo en vez de
  `calendar.app.created`). **PROPUESTA** — requiere re-consentimiento de usuarios existentes.
- B6 — `messages/{mid}` sin límite de tamaño en las reglas. **PROPUESTA** — riesgo de
  abuso de cuota, no urgente para el modelo de amenaza actual (usuarios del propio hogar).

Verificado sin hallazgo: mensajería sin XSS (`Text()` de Compose, no WebView/HTML), sin
secretos/keystores commiteados, backup Android correcto, códigos de invitación con
`SecureRandom` + no enumerables, validación de idToken de Google delegada al servidor.

---

## Experto 10 — Privacidad / RGPD / menores

Primera vez que este frente se revisa en el panel. Todos los hallazgos son **PROPUESTA**
(decisiones legales/de producto, ninguna es un fix de código de bajo riesgo aplicable sin
supervisión):

1. **AdMob sin flujo de consentimiento (UMP)** — el interstitial se precarga en `init{}` sin
   pedir consentimiento; sin `tagForChildDirectedTreatment` pese al "Perfil infantil" que
   anuncia la ficha de Play. Severidad Alta.
2. Inventario de eventos Analytics (`household_created`, `household_joined`,
   `invite_code_shared`, `task_completed`) coherente con `privacy.html` — sin hallazgo.
3. **Sin flujo de "eliminar cuenta"** en la app — `privacy.html` remite a email manual.
   Severidad Alta.
4. **El "borrado" de hogares/miembros no borra datos reales** — `deleteHousehold` no hace
   cascade-delete (comentario explícito en el código); `deleteMember` es soft-delete
   (`leftAt`). Contradice `privacy.html` ("puedes eliminar tus hogares... desde la app").
   Severidad Alta.
5. Sin captura de edad ni gating por edad en ningún formulario — el rol "Miembro" puede ser
   un menor con cuenta propia (vía código de invitación) sin ninguna barrera técnica.
   Severidad Media-Alta.
6. Tensión entre marketing ("Perfil infantil"/"Kids' profile") y clasificación deliberada
   como "no diseñada para niños" en Play Console. Severidad Media.
7. `privacy.html` no enlazada desde dentro de la app (solo desde la ficha de Play). Severidad
   Media — el propio experto lo marca "trivial" pero aun así como PROPUESTA, no aplicado en
   esta pasada.

---

## Experto 11 — Rendimiento / eficiencia

Primera revisión a fondo de este frente.

### Aplicados (mecánicos, bajo riesgo)
1. `HouseholdChatSection.kt:111` — `LazyColumn` de chat sin `key`. **Fix aplicado**:
   `items(messages, key = { it.id })`.
2. `HouseholdRepository.reconcileHouseholds` (líneas 206-224) — `filter` con `suspend`
   dentro, secuencial (un `GET` tras otro) en el arranque de `HomeScreen`, la pantalla más
   usada. El patrón paralelo (`async`/`awaitAll`) ya existe 20 líneas más abajo en
   `getHouseholds`, solo no se reutilizó. **Fix aplicado**: convertido a paralelo con
   `coroutineScope`/`async`/`awaitAll`, mismo patrón que su función vecina.
3. `RankingScreen.kt:44-46` — `sortedByDescending` sin memoizar (impacto bajo, hogares
   pequeños). **Fix aplicado**: envuelto en `remember(uiState)`.

### PROPUESTA — cambian firma pública o contrato observable
4. `TaskListContent` (`TaskListScreen.kt:479-541`) recalcula filtrado/agrupado/
   `RecurrenceRules.isDueToday` (2× por tarea) sin `remember`; `TaskCard` recibe `List`/`Map`
   inestables recreados cada vez. Impacto alto (pantalla más usada tras Home, escala con nº
   de tareas). **No aplicado** — refactor de un composable central sin QA visual real es
   alto riesgo de regresión sutil de recomposición; se documenta para pasada dedicada.
5. `TaskRepository.getAllAssignments` repite `getTasks` ya obtenido por el caller (N+3/N+4/
   N+5 requests por apertura de pantalla, según la pantalla). **PROPUESTA** — cambia la
   firma de una función usada en 4 sitios.
6. `TaskListScreen`/`CalendarScreen` inyectan `HouseholdScreenModel` extra solo para el
   nombre del hogar (mismo hallazgo que Arquitectura #2). **PROPUESTA**.
7. `getOrCreatePersonalHousehold()` llamado dos veces en el arranque para usuarios con
   Google (`App.kt:107,139`). **PROPUESTA** — cambia firma de `restoreFromCloudOnStartup`.
8. Polling de mensajes (20s) y no-leídos (30s) en `HouseholdScreen` mientras está abierta,
   incondicional. **PROPUESTA** — cambia frescura observable del chat/notificaciones.

---

## Experto 12 — Fiabilidad de red / offline / sincronización

Confirmado (no repetido): la invalidación de `TaskCache` tras escrituras sobrevivió el
god-object split correctamente, repartida por dominio sin huecos ni duplicación. `isOnline()`
correcta (distingue 404/403 de fallo de transporte real). Timeout de Ktor configurado
(30s `requestTimeoutMillis`), sin UI colgada indefinidamente.

### CRÍTICO — SOLO PROPUESTA
1. **`completeAssignment`/`markAssignmentCompleted` sin `currentDocument.updateTime`**
   (`FirestoreRepository.kt:1302-1417`) — a diferencia de `completeTask`, que sí lo tiene
   desde una ronda anterior. Dos dispositivos completando la misma asignación casi a la vez
   duplican puntos, historial y (para recurrentes) la siguiente asignación. **PROPUESTA** —
   mismo motivo que quedó fuera en v2 para `completeTask`: cambia política de conflicto,
   requiere decidir el mensaje de error ante colisión.

### ALTO/MEDIO — SOLO PROPUESTA
2. `redeemReward`/`donatePoints` — TOCTOU residual entre 2 escrituras concurrentes del mismo
   miembro (ya documentado en v2, sin cambios, riesgo práctico bajo).
3. Sin política de retry/backoff genérica ante timeout/5xx — solo existen los 3 reintentos
   de concurrencia optimista (`addMemberPoints`/`appreciateMember`/`addMemberAchievement`),
   que reintentan solo ante conflicto, no ante fallo transitorio de transporte.

---

## Experto 13 — Cobertura de pruebas

Mapa de cobertura actual: `RecurrenceRulesTest.kt` (23 tests, buena calidad de aserciones),
`FirestoreParsersTest.kt` (11 tests, solo household/member), `PointsRulesTest.kt` (17 tests).
**Cero tests en los 7 `ui/models/*ScreenModel.kt`** y en los repos de dominio nuevos
(`TaskRepository`, `HouseholdRepository`, `RewardsRepository`, `NotificationRepository` —
su parseo DTO inline no pasa por `FirestoreParsers` y no está testeado).

Huecos en `RecurrenceRulesTest.kt` respecto al checklist del encargo: clamp con `day=29/30`
como entrada (solo se prueba `31`); `isDueToday` weekly con varios días a la vez (solo
`nextOccurrence` lo prueba); **timezone explícita distinta a la del sistema** (el parámetro
`tz` ya existe y es inyectable, pero ningún test lo usa con un valor no-default).

Tests frágiles: `PointsRulesTest.kt` usa el número mágico `50` en vez de
`PointsRules.WEEKLY_APPRECIATION_BUDGET` en varios asserts.

### Top-10 priorizado (no aplicado — el encargo pide no añadir suite masiva)
1. `clampDayOfMonth` con `day=29`/`30` como entrada.
2. `isDueToday`/`nextOccurrence` con timezone explícita no-default.
3. `isDueToday` weekly con `recurrenceDays` de varios días.
4. Sustituir el `50` mágico por la constante en `PointsRulesTest`.
5. `calculatePenalty`/`resolveCompletionOutcome` (`FirestoreRepository.kt:1452-1492`) — lógica
   pura de penalización sin ningún test, afecta puntos reales.
6. `TaskRepository.toTaskResponse` — parseo inline sin test (mismo patrón que
   `FirestoreParsersTest` ya cubre para household/member).
7. Parseo inline de `RewardsRepository`/`NotificationRepository`.
8. Extraer y testear la regla "es admin" (duplicada inline en ≥5 pantallas).
9. `PointsRules.mondayStartOfWeek` con `tz` inyectable (hoy hardcodea
   `TimeZone.currentSystemDefault()`, a diferencia de `RecurrenceRules`).
10. `HomeScreenModel.isPending` — requiere inyectar reloj primero.

---

## FOCO ESPECIAL — Tareas recurrentes

### Modelo actual
Un único documento Firestore por tarea; el estado "¿toca hoy?" se recalcula en cliente vía
`RecurrenceRules.isDueToday` (pura, testeada, 23 tests). Dos vías de completar, asimétricas:
`completeTask` (flujo principal, con concurrencia optimista, NO regenera asignación
siguiente) y `completeAssignment` (flujo secundario, SIN concurrencia optimista, SÍ regenera
la asignación siguiente pero siempre al mismo `assignment.memberId`, ignorando
`assignmentRotation`). Convención ISO de semana (lunes=1) y timezone
(`currentSystemDefault()`) verificadas **consistentes** en los 6+ puntos donde se usan
(UI incluida) — sin bug ahí.

### Bugs encontrados

| # | Hallazgo | Severidad | Estado |
|---|---|---|---|
| 1 | `completeTask` no crea la asignación de la siguiente ocurrencia (rompe sync Calendar y deja `TaskDetailScreen` con datos del ciclo cerrado) | Alto | ⚠️ PROPUESTA |
| 2 | `assignmentRotation` no gobierna nada — `completeAssignment` siempre reasigna al mismo miembro que completó; `CreateTaskScreen` nunca crea asignaciones reales para tareas con rotación (queda como badge cosmético) | Alto | ⚠️ PROPUESTA |
| 3 | Penalización por retraso casi nunca se aplica a recurrentes — `task.dueDate` vale `0` para daily/weekly/monthly (documentado así en el KDoc del DTO), así que `resolveCompletionOutcome` siempre da `onTime=true` en `completeTask` | Alto | ⚠️ PROPUESTA |
| 4 | `completeAssignment` sin `currentDocument.updateTime` — en carrera de 2 dispositivos, cada uno crea su propia "siguiente asignación" → bifurcación persistente de la cadena, no solo un punto duplicado una vez | Alto | ⚠️ PROPUESTA (coordinado con Fiabilidad de red) |
| 5 | `completeAssignment` no actualizaba racha ni logros (a diferencia de `completeTask`) — miembros que solo completan vía asignación/rotación nunca acumulaban racha | Medio | ✅ **Aplicado** |
| 6 | Tres reimplementaciones independientes de "¿toca/completada hoy?" (`RecurrenceRules`, `TaskListScreen.isTaskCompletedToday`, `CalendarScreen.isTaskDueOnDay`) — coinciden hoy, riesgo de divergencia futura | Bajo | ⚠️ PROPUESTA |
| 7 | Editar `recurrenceDay` de una tarea monthly ya completada este mes puede permitir "doble cobro" en el mismo mes (la rama con día fijo compara por igualdad exacta, no por "ya cumplida este mes" como la rama legado) | Bajo | ⚠️ PROPUESTA |

**Fix #5 aplicado**: `TaskScreenModel.completeAssignment` ahora lee `memberBefore`, llama
`updateMemberStreak`/`checkAndAwardAchievements` tras el éxito (mismo patrón exacto que
`completeTask`), y añade `buzz(SUCCESS/ERROR)` para simetría con el resto del archivo. No
requiere cambios en `RecurrenceRulesTest.kt` (es lógica de `TaskScreenModel`, no de las
reglas puras).

### Análisis de la mejor solución — Veredicto

Los bugs #1, #2 y #3 comparten causa raíz: **las tareas recurrentes no tienen ninguna fecha
límite de ocurrencia persistida**, solo `lastCompletedDate`. Evaluadas las alternativas:

- **(a) Modelo de instancias** (un documento por ocurrencia): resolvería de raíz
  penalización/rotación/multi-dispositivo, pero es una migración grande (rediseño de DTOs y
  de todos los flujos citados), más coste Firestore, y necesita generación/limpieza de
  instancias. Sobredimensionado para el tamaño de esta app (hogares pequeños).
- **(b) Campo `nextDueAt` precalculado y persistido** en el propio documento, calculado con
  `RecurrenceRules.nextOccurrence` en el mismo PATCH que ya actualiza `lastCompletedDate`
  (sin viaje de red extra). Da a `completeTask`/`completeAssignment` una fecha límite real
  para `resolveCompletionOutcome` (arregla #3) y una fuente única para "¿toca hoy?" (mitiga
  #6). Coste Firestore mínimo, migración aditiva (tareas antiguas sin el campo caen al
  recálculo actual como fallback), mantiene el diseño "documento único + reglas puras" que
  el proyecto ya eligió deliberadamente.

**VEREDICTO: (b).** El modelo "documento único + recálculo en cliente" es correcto para esta
app — no se recomienda migrar a instancias. La prioridad real no es el modelo de datos (que
está bien diseñado y bien testeado), sino **cerrar la asimetría entre `completeTask` y
`completeAssignment`**: añadir `nextDueAt` y unificar en un único helper "crear/renovar la
siguiente asignación respetando `assignmentRotation`", invocado desde ambos flujos. No se
aplica en esta pasada por ser, con `nextDueAt` incluido, un cambio de esquema y de
comportamiento de puntos en producción — exactamente lo que el encargo pide dejar como
PROPUESTA para decisión explícita del usuario.

---

## Fixes aplicados — archivos tocados (24 fixes)

**Accesibilidad (7)**
- `ui/screens/TaskDetailScreen.kt` — 5 sitios con `.copy(alpha=0.85f)` sobre
  `onPrimaryContainer` → alpha 1.0 (tarjeta de recurrencia/penalización); texto de subtarea
  completada `.copy(alpha=0.6f)` → `onSurfaceVariant` sin atenuar.
- `ui/screens/HouseholdScreen.kt` — 2 sitios `.copy(alpha=0.8f)` (código de invitación).
- `ui/screens/CreateRewardScreen.kt` — 1 sitio `.copy(alpha=0.8f)` (preview de recompensa).
- `ui/screens/TaskListScreen.kt` — texto de tarea completada `.copy(alpha=0.6f)` → sin
  atenuar; `heightIn(min=48.dp)` en cabecera de grupo.
- `ui/screens/CreateTaskScreen.kt` — `Row`→`FlowRow` + `contentDescription` en chips de día;
  `supportingText` de clamp; `heightIn(min=48.dp)` en toggle de plantillas.
- `ui/screens/EditTaskScreen.kt` — mismos 3 fixes que `CreateTaskScreen.kt`; `maxLines`/
  `overflow` en etiqueta de rotación de asignación.
- `ui/i18n/AppStrings.kt` — nueva clave `recurrence_day_of_month_hint` (ES/EN).

**UX (3)**
- `ui/components/HouseholdMemberList.kt` — diálogo de confirmación antes de cambiar el rol
  de un miembro (`AlertDialog` + 3 claves i18n nuevas `member_role_change_confirm_*`).
- `ui/models/MemberScreenModel.kt` — `buzz(SUCCESS/ERROR)` en `updateMemberRole`/
  `removeMember`.

**Estética (4)**
- `ui/screens/CreateProfileScreen.kt` — emoji de admin unificado a `member_role_admin_short`.
- `ui/i18n/AppStrings.kt` — clave huérfana `create_profile_role_admin` eliminada (ES/EN).
- `ui/screens/WelcomeScreen.kt` — versión `v0.7.23`→`v0.7.25`.

**Seguridad (2)**
- `ui/models/TaskScreenModel.kt` — `escapeCsvField()`: neutraliza inyección de fórmulas CSV.
- `server/` — directorio completo eliminado (API Admin SDK sin auth, no incluida en el build).

**Senior / calidad de código (3)**
- `network/FirestoreClient.kt` — nueva función `internal orDefault(...)` compartida.
- `network/FirestoreRepository.kt`, `TaskRepository.kt`, `RewardsRepository.kt`,
  `NotificationRepository.kt` — las 4 copias privadas de `orDefault` eliminadas.
- `network/GoogleCalendarRepository.kt` — parámetro `apiKey`/constante `DEFAULT_API_KEY`
  sin usar, eliminados.

**Rendimiento (3)**
- `ui/components/HouseholdChatSection.kt` — `key = { it.id }` en `LazyColumn` de chat.
- `network/HouseholdRepository.kt` — `reconcileHouseholds` de secuencial a paralelo
  (`coroutineScope`/`async`/`awaitAll`, mismo patrón que `getHouseholds`).
- `ui/screens/RankingScreen.kt` — `sortedByDescending` envuelto en `remember(uiState)`.

**Recurrencia (1)**
- `ui/models/TaskScreenModel.kt` — `completeAssignment` ahora actualiza racha/logros y
  dispara háptico, igual que `completeTask`.

## FOCO de recurrencia — resumen

Modelo elegido: **documento único + recálculo en cliente, con propuesta de añadir un campo
`nextDueAt` persistido** (no aplicado, cambio de esquema). Bug corregido: racha/logros
ausentes en `completeAssignment`. Bugs documentados como PROPUESTA (interdependientes,
aplicar uno sin los otros empeoraría el estado): `completeTask` no regenera asignación
siguiente, `assignmentRotation` no gobierna nada, penalización casi nunca aplicada a
recurrentes, `completeAssignment` sin concurrencia optimista.

## PROPUESTAS no aplicadas — veredicto y coste/beneficio

### Seguridad/legal — requieren acción manual o decisión de producto
1. **`firestore.rules`: rama owner/admin de `create` en `members/{mid}` sin validar límites**
   (M2, Seguridad). Coste bajo (editar regla), pero requiere desplegar — no aplicable desde
   el repo. Beneficio medio (cierra un vector de escritura arbitraria por owner/admin ya
   confiado, impacto práctico limitado).
2. **Privacidad/RGPD** (Experto 10, 7 hallazgos): consentimiento AdMob (UMP), borrado de
   cuenta real, cascade-delete de datos, gating de edad, coherencia marketing↔clasificación
   Play Console. Todos requieren decisión legal/de producto — coste variable (desde trivial,
   enlazar `privacy.html` en Ajustes, hasta alto, Cloud Function de borrado en cascada).
   Veredicto: priorizar el borrado en cascada y el enlace a `privacy.html` por ser los de
   menor coste con mayor beneficio de cumplimiento.
3. Refresh tokens sin cifrar en `multiplatform-settings` (B1). Coste medio-alto (3
   plataformas), riesgo bajo-medio (mitigado parcialmente por exclusión de backup).

### Arquitectónicas — refactors grandes
4. **Extraer `MemberRepository`** del bloque Members/Points de `FirestoreRepository.kt`
   (~555 líneas). Coste alto, beneficio alto (completaría el god-object split).
5. **`nextDueAt` persistido para recurrencia** (ver FOCO). Coste medio, beneficio alto
   (arregla 3 bugs de una vez: penalización, regeneración de asignación, divergencia de
   "¿toca hoy?").
6. **Concurrencia optimista en `completeAssignment`** + dedup en `assignTask`. Coste medio,
   beneficio alto (cierra el hueco de duplicación de puntos más serio que queda). Requiere
   decidir la política de conflicto (qué error mostrar al perdedor de la carrera).
7. Sin paginación en ninguna colección de Firestore — sin cambios desde v2. Coste alto,
   beneficio bajo hoy (hogares reales pequeños), alto a medio-largo plazo.
8. `TaskListContent` sin memoizar (Rendimiento #4) — coste medio, beneficio alto, pero alto
   riesgo de regresión sutil sin QA visual; se prioriza documentar en vez de aplicar a ciegas.

### Calidad de código / UX — riesgo medio o superficie alta
9. `MemberActionState` separado para no perder la lista de miembros ante un error de mutación
   (UX #2). Coste medio, beneficio alto (consistencia con el patrón ya usado en Rewards).
10. `createTask`/`updateTask` con bloques de serialización duplicados (Senior #2). Coste
    medio, beneficio medio (legibilidad, no corrige ningún bug activo).
11. Preview "próxima vez: X" en formularios de recurrencia (UX #4) — lógica ya existe y está
    testeada, solo falta engancharla a UI. Coste bajo, beneficio medio-alto (reduce confusión
    del usuario sobre el clamp/rotación).
12. Nombre del hogar en topbar propagado a 7 pantallas más (Estética #1). Coste medio (7
    archivos), beneficio medio (consistencia entre pantallas hermanas).

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   # BUILD SUCCESSFUL
./gradlew :composeApp:jvmTest --console=plain                      # BUILD SUCCESSFUL
```

No se compiló el target iOS (no hay toolchain de Xcode/macOS en este entorno); ninguno de
los cambios de esta pasada toca código `iosMain` salvo por herencia de `commonMain`, que se
compila igual para todas las plataformas objetivo verificadas.

## Riesgos pendientes / deuda técnica — resumen para el usuario

El riesgo más importante que queda activo tras esta pasada es la **combinación de tres bugs
de recurrencia interdependientes** (completar desde la lista principal no renueva la
asignación → la rotación de asignación no gobierna nada → la penalización no se aplica a
recurrentes): ninguno es crítico aislado, pero juntos hacen que la asignación
rotatoria/recurrente sea, en la práctica, poco más que un badge informativo. Es el trabajo
recomendado de mayor impacto para una próxima pasada, junto con la concurrencia optimista de
`completeAssignment` (el hueco de duplicación de puntos que queda tras esta ronda). En
seguridad, el riesgo activo más relevante es la asimetría de `firestore.rules` en la
creación de miembros por owner/admin (M2) — impacto práctico bajo pero conceptualmente
inconsistente con el resto de las reglas v4. En privacidad, el borrado de datos no-real pese
a lo que `privacy.html` promete es el hallazgo más urgente a nivel de cumplimiento, por
delante de cualquier refactor.
