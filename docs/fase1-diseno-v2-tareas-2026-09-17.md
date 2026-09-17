# Fase 1 — Implementación diseño v2 tareas: plegados + concepto "Lista" (2026-09-17)

Implementa los ítems 1-11 del blueprint `docs/diseno-vista-tareas-v2-2026-09-17.md`
(sección 6, Fase 1), sobre el estado post quick-wins (`57ff6c5`). Alcance
estrictamente presentación/copy/flujo — sin tocar `firestore.rules`, modelo
de datos ni lógica de puntos/recurrencia/asignación/calendario.

Archivos tocados: `AppStrings.kt`, `CreateTaskScreen.kt`, `EditTaskScreen.kt`,
`TaskDetailScreen.kt`, `TaskListScreen.kt`.

---

## Ítem por ítem

### 1. `AppStrings.kt` — copy "Lista" + claves nuevas

Valores cambiados (mismas claves, ES+EN):

| Clave | Antes (ES/EN) | Ahora (ES) | Ahora (EN) |
|---|---|---|---|
| `create_task_section_checklist` | `"✅ Checklist"` | `"📋 Lista"` | `"📋 List"` |
| `task_detail_checklist_header` | `"✅ Checklist (%1/%2)"` | `"📋 Lista (%1/%2)"` | `"📋 List (%1/%2)"` |
| `task_list_subtask_badge` | `"✔ %1/%2"` | `"📋 %1/%2"` | `"📋 %1/%2"` |

Claves nuevas:

| Clave | ES | EN |
|---|---|---|
| `create_task_section_checklist_hint` | `"Apunta lo que haga falta: pasos, compra, materiales…"` | `"Add whatever you need: steps, shopping, supplies…"` |
| `task_detail_other_section` | `"Otros"` | `"Other"` |
| `create_task_tag_other_chip` | `"+ Otra"` | `"+ Other"` |

`task_detail_other_section` se añadió aquí en vez de esperar a la Fase 3
(ítem 18 del blueprint), tal y como pedía el encargo — se usa ya en los
ítems 4 y 7 de esta misma fase. Líneas finales: ES 569-573/638/671, EN
1206-1210/1275/1308 (`AppStrings.kt`).

### 2. `CreateTaskScreen.kt` / `EditTaskScreen.kt` — subtítulo bajo "Lista"

Añadido `Text(s("create_task_section_checklist_hint"), bodySmall,
onSurfaceVariant)` dentro del `ExpandableSectionHeader` de Lista, debajo del
título, en ambos formularios (visible siempre, expandido o no — actúa de
teaser). Implementado junto con el ítem 6 (mismo header).

### 3. `TaskDetailScreen.kt` — mismo subtítulo en el detalle

Añadido el mismo `Text` justo debajo de `task_detail_checklist_header`,
dentro del `item` de cabecera de la sección Lista (antes en
`530-546`, sección "Subtasks checklist").

### 4. `TaskDetailScreen.kt` — grupo "Otros" (Calendar + completadas + comentarios)

Nuevo `ExpandableSectionHeader` (label `task_detail_other_section`),
plegado por defecto (`otrosExpanded` inicial `false`), insertado después de
la sección "Pendientes". Al expandir se muestran, en el mismo orden que
antes: estado de sincronización con Google Calendar, "Asignaciones
completadas" y "Comentarios" (input + lista completos, incluida la gestión
de `sendCommentError`). No se tocó ningún callback ni ScreenModel — solo se
envolvió la UI existente en el `if (otrosExpanded) { ... }` del `LazyColumn`,
siguiendo el mismo patrón ya usado por `GroupHeader`/`collapsedGroups` en
`TaskListScreen.kt`.

No se implementó el punto de aviso (`StatusDot` de error cuando "Otros" está
plegado y hay un fallo de calendario/comentario) — es el ítem 19, Fase 3,
explícitamente fuera de este encargo.

### 5. `TaskDetailScreen.kt` — Penalización: NO movida (pendiente Fase 2)

Tal y como indicaba el propio encargo, el bloque de Penalización
(`create_task_penalty_section` dentro de la tarjeta de info) se ha dejado
**sin tocar**. Moverlo a la sección "Puntuación" depende de que esa sección
exista (se crea en la Fase 2, ítem 13 del blueprint) — intentarlo ahora
significaría o bien crear una sección "Puntuación" a medias fuera de
alcance, o bien dejar el bloque huérfano. Queda anotado aquí como
**pendiente para el encargo de Fase 2**, que deberá extraer
`TaskDetailScreen.kt` (bloque actual sin cambios de posición) hacia la nueva
sección "Puntuación".

### 6. `CreateTaskScreen.kt` / `EditTaskScreen.kt` — Lista/Etiquetas/Asignación plegables

Cada sección envuelta en su propio `ExpandableSectionHeader`:

| Sección | Estado por defecto en Create | Estado por defecto en Edit |
|---|---|---|
| Lista | Plegada (`checklistExpanded = false`) | Expandida si `task.subtasks.isNotEmpty()` |
| Etiquetas | Plegada (`tagsExpanded = false`) | Expandida si `task.tags.isNotEmpty()` |
| Asignación | Expandida solo si `preselectedMemberId != null` | Expandida cuando termina de cargar la lista de asignaciones y `selectedMembers.isNotEmpty()` (dentro del `LaunchedEffect(assignmentsReloadTrigger)` existente, sin peticiones nuevas) |

### 7. `CreateTaskScreen.kt` / `EditTaskScreen.kt` — grupo "Otros"

Nuevo `ExpandableSectionHeader` (label `task_detail_other_section`)
conteniendo Fecha límite, Penalización y Rotación. Estado por defecto:

- Create: plegado (`otrosExpanded = false`) — ninguna sub-sección empieza activa.
- Edit: `otrosExpanded = hasDeadline || hasPenalty || task.assignmentRotation.isNotEmpty()` (evaluado una vez al entrar, con los valores ya precargados de `task`).

### 8. Rotación de asignación — movida en Edit, duplicada en Create

`EditTaskScreen.kt`: el bloque de switch + selector por día (antes entre
"Members list" y "Obligatoria", fuera de cualquier plegado) se trasladó
dentro de "Otros", después de Penalización.

`CreateTaskScreen.kt`: se añadió el mismo bloque (switch `hasRotation` +
`rotationSlots: MutableMap<Int, String>` + selector por día con
`DropdownMenu`), inexistente hasta ahora en creación. Al guardar, se
construye `List<AssignmentSlot>` igual que en Edit y se pasa a
`taskModel.createTask(..., assignmentRotation = rotation)` (el parámetro ya
existía en `TaskScreenModel.createTask`, con default `emptyList()` — cero
cambios de modelo/repo).

### 9. Chip "+ Otra" en Etiquetas

En ambos formularios, el `FlowRow` de etiquetas predefinidas ahora termina
con un `FilterChip` "+ Otra" (`create_task_tag_other_chip`) que alterna
`showCustomTagField`. El campo de texto libre + botón "Añadir" (antes
siempre visible) solo se pinta cuando `showCustomTagField == true`. El
resumen de etiquetas ya añadidas (`InputChip` removible) se mantiene
después, sin cambios de comportamiento.

### 10. `TaskListScreen.kt` — búsqueda colapsada a icono

`FilterChipsRow` gana un `IconButton` (lupa, `Icons.Default.Search`) al
inicio de su fila de tag-filter/orden, que alterna un nuevo estado
`searchExpanded` (dueño: `TaskListContent`, inicializado a
`searchQuery.isNotBlank()` para no esconder una búsqueda ya activa al
entrar). El `item { SearchBar(...) }` del `LazyColumn` ahora es condicional
a `searchExpanded`. El icono cambia de tinte (`primary` vs
`onSurfaceVariant`) si hay búsqueda expandida o con texto activo, para no
perder la pista de "hay un filtro de texto aplicado" al colapsar.

### 11. `TaskDetailScreen.kt` — "Hecho" unificado con asignaciones (D4)

El botón "Hecho" de nivel-tarea ahora exige `assignments.isEmpty()` además
de `!isCompletedToday`. Con asignaciones, completar pasa a ser siempre vía
`AssignmentCard` en "Pendientes"; sin asignaciones, el botón sigue siendo la
única vía (sin cambio para ese caso). No se tocó `onCompleteTask` ni
`model.completeTask` — solo su condición de visibilidad en la UI.

---

## Desviaciones del blueprint

- **Ítem 5 (Penalización → Puntuación):** no aplicado, según lo previsto por
  el propio encargo — depende de la sección "Puntuación" de Fase 2. Ver
  detalle en el punto 5 de arriba.
- El resto de citas archivo:línea del blueprint coincidían con el estado
  real del repo (verificado antes de editar); no hizo falta adaptar ningún
  ítem adicional.

---

## Verificación

### Build

```
$ ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
...
BUILD SUCCESSFUL in 9s
25 actionable tasks: 1 executed, 24 up-to-date
```

Sin errores; los únicos `w:` son warnings preexistentes no relacionados
(deprecaciones de Google Sign-In/EncryptedSharedPreferences, `when`
exhaustivos con `else` redundante en `CalendarScreen.kt`/`TaskListScreen.kt`).

### Tests JVM

```
$ ./gradlew :composeApp:jvmTest --rerun-tasks --console=plain
...
BUILD SUCCESSFUL in 10s

$ python3 ~/.hermes/profiles/taskhub/skills/autonomous-ai-agents/claude-code-queue/scripts/parse-jvm-test-results.py
...
TOTAL: 257 tests, 0 failures, 0 errors
```

No se encontró ningún test que assertara sobre el copy antiguo de
Checklist/badge/icono (`grep` sobre `commonTest` sin resultados) ni sobre el
comportamiento del botón "Hecho"/SearchBar afectado por los ítems 10-11 —
no hizo falta actualizar tests existentes.

---

## Resumen final

```
$ git log --oneline -1
<se completa tras el commit>
```
