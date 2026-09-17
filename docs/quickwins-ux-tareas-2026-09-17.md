# Quick wins UX de tareas — implementación (2026-09-17)

Implementa los 7 puntos **[APLICA YA]** de `docs/revision-ux-tareas-2026-09-17.md`
(sección 4). Ningún punto **[REQUIERE DECISIÓN]** ni las dos decisiones de la
sección 3 (comportamiento del badge al tocarlo, renombrado de "Checklist") se
han tocado. No se ha modificado lógica de negocio (completar, puntos,
recurrencia, asignación, calendario): solo presentación, orden e i18n.

---

## 1. Cambios punto por punto

### L2/H1 — Badge de progreso de subtareas

- `TaskListScreen.kt` (`TaskCard`, fila de metadatos ~950-994): antes la fila
  solo mostraba puntos + frecuencia + etiquetas, sin ningún indicio de
  subtareas. Ahora, si `task.subtasks.isNotEmpty()`, se añade un badge
  `Surface` con el texto `task_list_subtask_badge` ("✔ %1/%2") entre los
  puntos y la frecuencia/etiquetas, con el contador de completadas/total.
- `HouseholdTaskSection.kt` (`TaskRow`, ~166-236): mismo badge añadido entre
  el título/tags y la fecha límite, reutilizando la misma clave i18n.
- i18n nueva: `task_list_subtask_badge` = `"✔ %1/%2"` (ES y EN, mismo formato
  agnóstico de idioma, igual que `task_detail_checklist_header`).
- No requiere petición de red nueva: `subtasks` ya viaja en `TaskResponse`.
- El badge es solo informativo — no lleva `onClick`, no navega.

### D3 — Checklist antes que el estado de calendario en el detalle

- `TaskDetailScreen.kt`: el bloque `// ── Subtasks checklist ──`
  (antes tras `// ── Google Calendar sync status ──`) se movió justo después
  de la tarjeta de info y antes de `// ── Completion status ──`. Orden
  resultante: Info → **Checklist** → Estado de completado → Quién la
  completó → Calendario → Pendientes → Completadas → Comentarios.

### C1 — Checklist después de Puntos en Crear/Editar

- `CreateTaskScreen.kt` y `EditTaskScreen.kt`: la sección Checklist (antes
  entre Título y Descripción) se movió a después del campo Puntos. Orden
  resultante en ambas pantallas: Título → Descripción → Puntos →
  **Checklist** → Frecuencia.

### L3 — Texto del criterio de orden junto al emoji

- `TaskListScreen.kt` (`FilterChipsRow`, botón de orden): antes el botón solo
  pintaba el glifo (📅↑/📅↓/⭐/🕐); el string `task_list_sort_label` +
  `currentSortLabel` solo existían en el `contentDescription` (accesibilidad,
  no visible). Ahora se pinta también `currentSortLabel` junto al emoji
  dentro del `TextButton`. No se tocó ninguna clave i18n existente.

### D5 — Sin cabecera "Pendientes (0)" redundante

- `TaskDetailScreen.kt`: la cabecera `task_detail_pending_header` ahora solo
  se pinta cuando `pendingAssignments.isNotEmpty()`. Cuando está vacío, se
  deja únicamente la tarjeta de estado vacío ("Sin asignaciones" / "Todas
  completadas"), eliminando el doble mensaje "Pendientes (0)" + estado vacío.

### D1 — Sub-bloque con label para Recurrencia

- `TaskDetailScreen.kt` (mega-card de info): antes "Recurrencia" (días de la
  semana / día del mes) se mostraba como texto suelto con emoji 🔄, sin
  separador ni label, a diferencia de "Penalización" (que sí tiene divider +
  label `create_task_penalty_section`). Ahora tiene el mismo tratamiento:
  `HorizontalDivider` + label `task_detail_recurrence_section` ("🔄
  Recurrencia") + contenido (días / día del mes), solo si aplica
  (`recurrenceDays` no vacío o mensual con `recurrenceDay`).
- i18n nueva: `task_detail_recurrence_section` = "🔄 Recurrencia" (ES) / "🔄
  Recurrence" (EN).

### L1 — Menor peso visual de frecuencia/etiquetas

- `TaskListScreen.kt` (`TaskCard`, misma fila de metadatos que L2): antes
  frecuencia y cada etiqueta eran `Surface` con `primaryContainer` /
  `tertiaryContainer` (mismo peso visual que la fila de vencimiento de
  abajo). Ahora frecuencia + hasta 2 etiquetas se agrupan en un único `Text`
  (`labelSmall`, `onSurfaceVariant` con `alpha = 0.7f`, sin `Surface`),
  separadas por " · " — menor contraste que la fila de vencimiento
  (~996-1063), que sigue igual. El título y el botón de completar no se han
  tocado.

---

## 2. Verificación

### Compilación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ **BUILD SUCCESSFUL** (11s). Sin errores; solo warnings preexistentes
(deprecaciones de Google Sign-In/EncryptedSharedPreferences, `when`
exhaustivo redundante) no relacionados con este cambio.

### Tests

```
./gradlew :composeApp:jvmTest --rerun-tasks --console=plain
```
→ **BUILD SUCCESSFUL** (12s).

Suma de `composeApp/build/test-results/jvmTest/*.xml` (23 ficheros):

```
tests=257 failures=0 errors=0 skipped=0
```

Ningún test asertaba el layout u orden anteriores, así que no hizo falta
actualizar ninguno.

---

## 3. Incidencias

- Claves i18n nuevas (ES + EN, ambas añadidas): `task_list_subtask_badge`,
  `task_detail_recurrence_section`.
- No se renombró ninguna clave existente.
- No se tocó ningún punto `[REQUIERE DECISIÓN]` (D2, D4, D6, C2, C3, C4, L4,
  ni las dos decisiones de la sección 3 del informe).
- No se tocó lógica de negocio: completar, puntos, recurrencia, asignación,
  sincronización de calendario.

---

## 4. Commit

```
git log --oneline -1
```
Ver commit `feat: quick wins UX de tareas (badge subtareas, orden de
secciones, claridad de filtros)`, creado a continuación de este informe.
