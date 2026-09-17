# Diseño v2 — Vista de tareas simplificada (2026-09-17)

Segunda vuelta sobre `docs/revision-ux-tareas-2026-09-17.md` (commit
`efc6587`), con la dirección de diseño de Liberto ya incorporada. Los quick
wins de `docs/quickwins-ux-tareas-2026-09-17.md` (commit `57ff6c5`) ya están
en `main`; este documento diseña **sobre** ese estado, no lo revierte. **No
se ha tocado código** — solo este informe. Todas las citas archivo:línea se
verificaron leyendo el código real tras los quick wins (ver "Archivos
leídos" al final).

Objetivo de este documento: blueprint único, listo para encargos de
implementación sin más análisis. Resultado de la sección 5: **0 decisiones
pendientes** — todo lo delegado se resuelve aquí con criterio de UX.

---

## 1. Concepto "Lista" — de checklist-de-puntos a lista genérica

### Diagnóstico

Hoy la sección se llama "✅ Checklist" (icono de check, el mismo símbolo que
usa la app para "tarea completada") y su badge en las tarjetas usa "✔"
(`TaskListScreen.kt:971`, `HouseholdTaskSection.kt:221`) — el mismo glifo de
check. Nada en el copy actual sugiere "lista de la compra" o "lista de
elementos"; todo apunta a "pasos que hay que tachar para terminar la tarea",
lo que refuerza la lectura errónea "esto da puntos por pasos".

El modelo de datos (`Subtask` embebido en `TaskResponse.subtasks`,
`DTOs.kt:141-145`/`180`) ya es genérico — es literalmente `{id, text,
completed}`, no tiene ni sabe nada de puntos. El problema es 100% de copy e
iconografía, no de datos.

### Decisión de diseño

Renombrar "Checklist" → **"Lista"** (ES) / **"List"** (EN) en los tres
sitios donde aparece, y sustituir el icono ✅ (check = completado/puntos)
por **📋** (portapapeles = lista de cualquier cosa), reservando ✅ para
"tarea completada" y ⭐ para puntos — así los tres símbolos dejan de
solaparse visualmente.

**Se reutilizan las claves i18n existentes, cambiando solo su valor
literal** (no se renombran las claves) — minimiza el diff de implementación,
ningún call site cambia:

| Clave (sin cambios) | Valor ES actual | Valor ES nuevo | Valor EN actual | Valor EN nuevo |
|---|---|---|---|---|
| `create_task_section_checklist` (`AppStrings.kt:569`/`1203`) | `"✅ Checklist"` | `"📋 Lista"` | `"✅ Checklist"` | `"📋 List"` |
| `task_detail_checklist_header` (`AppStrings.kt:635`/`1269`) | `"✅ Checklist (%1/%2)"` | `"📋 Lista (%1/%2)"` | `"✅ Checklist (%1/%2)"` | `"📋 List (%1/%2)"` |
| `task_list_subtask_badge` (`AppStrings.kt:668`/`1302`) | `"✔ %1/%2"` | `"📋 %1/%2"` | `"✔ %1/%2"` | `"📋 %1/%2"` |

`create_task_add_item` (`AppStrings.kt:570`/`1204`, "Añadir ítem"/"Add item")
ya es genérico — sin cambios.

**Copy nuevo — subtítulo de ayuda** (clave nueva, se pinta bajo el título de
sección en Crear/Editar, `CreateTaskScreen.kt:438-446` y
`EditTaskScreen.kt:398-406`, y bajo el header del detalle,
`TaskDetailScreen.kt:535-546`):

- Clave nueva `create_task_section_checklist_hint`:
  - ES: `"Apunta lo que haga falta: pasos, compra, materiales…"`
  - EN: `"Add whatever you need: steps, shopping, supplies…"`

Este subtítulo, por sí solo, es el cambio que más hace por desligar
"Lista" de "puntos por subtarea" — aparece en el mismo lugar donde antes el
usuario solo veía "Checklist" a secas.

### Dónde se muestra (sin cambiar la lógica de negocio)

- **TaskCard** (`TaskListScreen.kt:964-979`) y **TaskRow**
  (`HouseholdTaskSection.kt:214-230`): mismo badge, mismo condicional
  (`task.subtasks.isNotEmpty()`), mismo formato numérico — solo cambia el
  icono vía el valor de `task_list_subtask_badge`. **[APLICA YA]**
- **Detalle** (`TaskDetailScreen.kt:530-571`): header renombrado +
  subtítulo nuevo antes de la lista de ítems; el resto (checkbox + texto +
  tachado al completar) no cambia. **[APLICA YA]**
- **Crear/Editar** (`CreateTaskScreen.kt:438-510`,
  `EditTaskScreen.kt:398-470`): header renombrado + subtítulo nuevo; el
  input+botón "Añadir ítem" y la lista con checkbox/borrar no cambian.
  **[APLICA YA]**

### Badge: ¿navegable o solo informativo?

**Decisión: solo informativo, sin `onClick` propio** (como ya está
implementado). Justificación: toda la card/row ya es clicable y lleva al
detalle (`TaskCard` en `TaskListScreen.kt:870`, `TaskRow` en
`HouseholdTaskSection.kt:175`); añadir un segundo target clicable anidado
dentro de esa misma superficie (el badge, con scroll-to-anchor) introduce un
patrón de "doble tap-target" inconsistente con el resto de badges de la
misma fila (puntos, frecuencia, vencimiento — ninguno navega). Con el
detalle reordenado (sección 2), "Lista" ya es la segunda cosa que se ve al
entrar, así que el ahorro de un scroll-to-anchor es marginal.
**[APLICA YA — DECISIÓN DE DISEÑO]** (resuelve la pregunta abierta de la
revisión v1, sección 3).

---

## 2. Patrón de desplegables — mapa pantalla por pantalla

Regla de oro aplicada en todas: **lo accionable hoy visible arriba, lo
secundario/infrecuente plegado bajo un único "Otros"**. Se reutiliza
siempre el mismo componente, `ExpandableSectionHeader`
(`ui/components/ExpandableSectionHeader.kt:54-60`), ya usado en
`TaskListScreen.kt:1204`, `HouseholdTaskSection.kt:71` y
`CreateTaskScreen.kt:1098` ("Plantillas rápidas").

### 2.1 `TaskDetailScreen.kt`

Orden actual (post quick-wins): Info → Lista → Estado completado → Quién la
completó → Calendario → Pendientes → Completadas → Comentarios.

**Orden nuevo:**

| # | Sección | Estado por defecto | Cambio respecto a hoy |
|---|---|---|---|
| 1 | Info de la tarea (`382-527`) | Visible, no plegable | Se le **quita** el bloque de Penalización (`493-525`) — se traslada a "Puntuación" (ver sección 3). Queda: título, descripción, puntos/frecuencia (`StatChip`), recurrencia, etiquetas. |
| 2 | **Lista** (`530-571`) | Visible, no plegable (si `subtasks.isNotEmpty()`) | Sin cambio de posición (ya la movió D3); solo el renombrado de la sección 1. |
| 3 | **Puntuación** (NUEVA) | Plegada, con teaser visible en la cabecera (`"⭐ Puntuación · {points} pts"`) | Ver sección 3 completa. |
| 4 | Completar (`573-651`) | Visible, no plegable | Ver D4 (sección 5): si la tarea tiene asignaciones, el botón "Hecho" de nivel-tarea **desaparece** de aquí — completar pasa a ser siempre vía la tarjeta de asignación de abajo. |
| 5 | Pendientes (`673-717`, si `pendingAssignments.isNotEmpty()`) | Visible, no plegable | Sin cambio (ya resolvió D5: sin cabecera "(0)"). |
| 6 | **"Otros"** (NUEVA, agrupa 3 secciones) | Plegada por defecto | Contiene: Sincronización con Google Calendar (`653-671`), Asignaciones completadas (`719-739`), Comentarios (`741-922`, incluye input y lista). Un único `ExpandableSectionHeader` con label `task_detail_other_section` (ES `"Otros"` / EN `"Other"`); al expandir se ven las 3 sub-secciones con sus propios sub-títulos, igual que hoy. |

Si `calendarActionState` o `sendCommentError` están en estado de error
mientras "Otros" está plegado, el header de "Otros" muestra un punto de
aviso (reutilizar `StatusDot` con `error` — mismo patrón que el badge "!" de
vencidas en `TaskListScreen.kt:1226-1229`) para no esconder un fallo real
detrás del pliegue. **[APLICA YA]**

### 2.2 `CreateTaskScreen.kt` / `EditTaskScreen.kt`

Orden actual (post quick-wins, C1 aplicado): Plantillas (ya plegable) →
Título → Descripción → Puntos → Lista → Frecuencia → Etiquetas → Asignación
→ Fecha límite (switch) → Penalización (switch). Edit añade Rotación
(switch) después de Asignación.

**Orden nuevo (igual en Create y Edit, Rotación ahora en ambas):**

| # | Sección | Estado por defecto en Create | Estado por defecto en Edit |
|---|---|---|---|
| 1 | Plantillas rápidas (`1079-1175`) | Plegada (sin cambio) | — (no existe en Edit) |
| 2 | Info básica: Título (`389-404`) → Descripción (`406-416`) → Puntos (`418-436`) | Visible, no plegable | Visible, no plegable |
| 3 | Frecuencia + días/día-del-mes + preview (`512-662`) | Visible, no plegable (define el tipo de tarea) | Visible, no plegable |
| 4 | **Lista** (`438-510` en Create / `398-470` en Edit) | Plegada | Expandida si `task.subtasks.isNotEmpty()`, si no plegada |
| 5 | **Etiquetas** (`664-757` / `621-714`) | Plegada | Expandida si `task.tags.isNotEmpty()`, si no plegada |
| 6 | **Asignación** (miembros + obligatoria, `759-858` / `716-788`+`894-915`) | Plegada, salvo que `preselectedMemberId != null` (Create) → expandida | Expandida si ya hay miembros asignados, si no plegada |
| 7 | **"Otros"** (NUEVA, agrupa 3) | Plegada, salvo que alguna de sus 3 sub-secciones ya esté activa → expandida | Igual: expandida si `hasDeadline`, `hasPenalty` o `task.assignmentRotation.isNotEmpty()` |

Contenido de "Otros" en Crear/Editar (mismo header `task_detail_other_section`,
reutilizado): **Fecha límite** (switch + date/time, `860-918`/`917-975`),
**Penalización** (switch + modo/valor/intervalo/tope, `920-1060`/`977-1110`),
**Rotación de asignación** (switch + selector por día, hoy solo en
`EditTaskScreen.kt:790-892` — **se añade también a `CreateTaskScreen.kt`**,
ver C4 en sección 5). Cada una conserva su propio `Switch` interno para
activarse/desactivarse — "Otros" solo controla si esas 3 filas de switches
son visibles, no si están activadas.

Justificación de mover Fecha límite y Penalización (hoy con cabecera+switch
siempre visible) a dentro de "Otros": son, por uso, las secciones menos
usadas del formulario (la mayoría de tareas domésticas no llevan fecha
límite exacta ni penalización) — encajan exactamente en la regla de
Liberto de agrupar "lo de uso menos frecuente". El propio switch dentro
sigue siendo la forma de activarlas; solo cambia si su fila-cabecera ocupa
espacio por defecto. **[APLICA YA]**

### 2.3 `HouseholdTaskSection.kt` (Home)

Ya es un único bloque plegable por hogar (`ExpandableSectionHeader` en
línea 71, `expanded` por defecto `true`, línea 59). No tiene sub-secciones
internas que agrupar — el badge de Lista (`214-230`) y la fecha de
vencimiento ya conviven en una sola fila compacta por tarea. **Sin cambios**
más allá del renombrado del badge (sección 1).

### 2.4 `TaskListScreen.kt`

Los grupos por fecha (Vencidas/Hoy/Completadas) ya son plegables
(`GroupHeader` → `ExpandableSectionHeader`, `1199-1230`), con "Completadas"
plegado por defecto (`719-720`) — sin cambios, ya resuelto.

**Nuevo:** la `SearchBar` (`1238-1274`) se renderiza siempre a tamaño
completo antes de ver ninguna tarea, ocupando espacio permanente para una
acción que se usa ocasionalmente. Se colapsa a un icono de lupa que se
expande a campo de texto al tocarlo (patrón estándar "icon → field"),
reutilizando el mismo `FilterChipsRow` como contenedor. Resuelve L4 de la
revisión v1. **[APLICA YA — DECISIÓN DE DISEÑO]** — justificación: encaja
directamente en el mandato "generalizar plegado para que la vista inicial
sea corta"; es el único elemento de peso visual permanente que quedaba sin
tocar en esta pantalla tras los quick wins.

---

## 3. Apartado "Puntuación"

### Ubicación

**Detalle de tarea**, como sección nueva plegable (posición 3 en la tabla
de 2.1) — es donde el usuario ya está mirando "esta tarea da X puntos" y
donde tiene más sentido explicar qué significa eso. **Además**, un
complemento ligero y sin coste en **Recompensas** (`RewardListScreen.kt`,
`RewardsBody`), que es el candidato "hogar/perfil" — ver más abajo.

### Estructura de contenido — detalle de tarea

Header (visible siempre, aunque la sección esté plegada — actúa de teaser):

- ES: `"⭐ Puntuación · {points} pts"`
- EN: `"⭐ Points · {points} pts"`
- Clave nueva `task_detail_points_section` = `"⭐ Puntuación"` (ES) /
  `"⭐ Points"` (EN); el `"· {points} pts"` se compone en código reutilizando
  `transfer_points_suffix` (`AppStrings.kt:244`/`898`, ya existe).

Contenido al expandir (todo con datos **ya cargados** en
`TaskDetailUiState.Success` salvo el punto 4, ver abajo):

1. **Cómo funciona** (clave nueva `task_detail_points_how_it_works` = "Cómo
   funciona" / "How it works"):
   - Texto (clave nueva `task_detail_points_earn_desc`):
     - ES: `"Al marcarla como hecha, quien la complete gana ⭐ %d puntos."`
     - EN: `"Whoever marks it done earns ⭐ %d points."`
   - Si `task.penaltyMode != null`: **se traslada aquí** el bloque de
     Penalización que hoy vive en la mega-card de info
     (`TaskDetailScreen.kt:493-525`, textos `create_task_penalty_section`,
     `task_detail_penalty_fixed_desc`/`_percent_desc`, `_max_desc` —
     **ninguna clave nueva**, se reutilizan tal cual). Esto además
     **reduce** el tamaño de la mega-card de info (sección 2.1, punto 1).
2. **Tu saldo** (clave nueva `task_detail_points_your_balance` = "Tu saldo"
   / "Your balance"): `"⭐ {memberPoints} puntos"` usando
   `memberMap[currentMemberId]?.totalPoints` — **dato ya disponible**, viene
   en `MemberResponse.totalPoints` (`DTOs.kt:76`) dentro de
   `state.members`, que `TaskDetailScreen.kt:177` ya construye como
   `memberMap`. **Cero peticiones nuevas.**
3. **Recompensas disponibles** (clave nueva
   `task_detail_points_rewards_header` = "Recompensas disponibles" /
   "Available rewards"): hasta 3 recompensas (`RewardResponse`,
   `DTOs.kt:370-379`) ordenadas por coste ascendente, formato icono+título+
   coste (mismo patrón visual que `MemberRewardScreen.kt:150-206`, versión
   compacta); las que el saldo actual no cubre se muestran atenuadas (sin
   bloquear, solo menor contraste) en vez de ocultarse.
   - Si el hogar no tiene recompensas: clave nueva
     `task_detail_points_rewards_empty` = `"Tu hogar aún no tiene
     recompensas creadas."` / `"Your household hasn't created any rewards
     yet."`
   - CTA final: clave nueva `task_detail_points_view_all_rewards` = `"Ver
     todas las recompensas →"` / `"See all rewards →"`, navega a
     `ExploreScreen(householdId, currentMemberId)` pestaña Recompensas.
4. **Coste de datos — recompensas.** `TaskDetailScreen` hoy **no** inyecta
   `MemberScreenModel` ni llama a `loadRewards` (ese estado vive en
   `MemberScreenModel.rewardState`, cargado hoy solo desde `RewardsBody` —
   `RewardListScreen.kt:79`). Mostrar el punto 3 sí implica una petición de
   red nueva. **Decisión: carga perezosa bajo demanda** — `TaskDetailScreen`
   inyecta `MemberScreenModel` y dispara `loadRewards(householdId)` en un
   `LaunchedEffect(expanded)` que solo se ejecuta la PRIMERA vez que el
   usuario expande "Puntuación" (no al abrir el detalle). Coste aceptado: 1
   lectura Firestore adicional, solo para quien realmente abre esa sección
   — no afecta el camino crítico de abrir una tarea. **[APLICA YA —
   DECISIÓN DE DISEÑO]** (alternativa descartada: mostrar solo el enlace
   "Ver recompensas" sin lista inline — se descarta porque Liberto pidió
   explícitamente ver "los puntos y las posibles contraprestaciones" en el
   propio apartado, no solo un enlace).

### Estructura de contenido — Recompensas (hogar)

`RewardsBody` (`RewardListScreen.kt:53-...`) ya carga rewards + members sin
petición extra. Se añade un banner explicativo de una línea, **sin
plegado** (es una sola línea, no compite por espacio), insertado justo
antes de la lista de recompensas (`RewardListScreen.kt:127`, entre el botón
"Nueva recompensa" para admins y el `when (val rState = rewardState)` de la
línea 129):

- Clave nueva `reward_list_explainer_banner`:
  - ES: `"💡 Ganas puntos completando tareas. Cámbialos aquí por recompensas."`
  - EN: `"💡 Earn points by completing tasks. Redeem them here for rewards."`

Este es el lado "hogar/perfil" del requisito 3: explica el sistema una vez,
en el sitio donde ya se ve el saldo y el catálogo completo, sin duplicar
lo que ya hace `MemberRewardScreen.kt` (coste vs. saldo, canjear). **[APLICA YA]**

---

## 4. Grupo "Otros/Adicionales" — resumen por pantalla

| Pantalla | Contenido de "Otros" |
|---|---|
| `TaskDetailScreen.kt` | Sincronización Google Calendar + Asignaciones completadas + Comentarios (input y lista incluidos) |
| `CreateTaskScreen.kt` / `EditTaskScreen.kt` | Fecha límite + Penalización + Rotación de asignación (nueva en Create) |
| `HouseholdTaskSection.kt` (Home) | No aplica — ya es un único bloque plegable, sin sub-secciones que agrupar |
| `TaskListScreen.kt` | No aplica como "Otros" — el patrón equivalente es la búsqueda colapsada a icono (sección 2.4) |

---

## 5. Decisiones delegadas — resueltas (objetivo: 0 pendientes)

Todas con criterio de UX, marcadas **[APLICA YA — DECISIÓN DE DISEÑO]**.

1. **D4 — Unificar "Hecho" con asignaciones.** Cuando la tarea tiene
   asignaciones (`assignments.isNotEmpty()`), el botón "Hecho" de
   nivel-tarea (`TaskDetailScreen.kt:586-601`) **deja de mostrarse** en la
   sección "Completar"; completar pasa a ser siempre "completar mi
   asignación" vía la tarjeta de `AssignmentCard` de "Pendientes"
   (`706-717`). Si la tarea NO tiene asignaciones, el botón "Hecho" de
   nivel-tarea sigue siendo la única vía (sin cambio para ese caso).
   Justificación: hoy conviven dos flujos de completar en la misma pantalla
   sin conexión visual (el problema #2 del resumen ejecutivo de la revisión
   v1); las asignaciones ya modelan "quién debe hacerla", así que
   completar-por-asignación es la acción correcta cuando existen. No se
   toca el modelo de datos ni `firestore.rules` — `onCompleteTask` deja de
   invocarse desde la UI en ese caso, pero el callback y
   `model.completeTask` siguen existiendo para el camino sin asignaciones.
2. **C3 — Unificar etiquetas.** Los chips predefinidos
   (`CreateTaskScreen.kt:735-757` / `EditTaskScreen.kt:692-714`) pasan a
   mostrarse primero, con un chip final **"+ Otra"** (clave nueva
   `create_task_tag_other_chip` = "+ Otra" / "+ Other") que revela el campo
   de texto libre (`674-701` / `631-658`) solo al tocarlo — antes de
   tocarlo, el campo de texto no ocupa espacio. Justificación: hoy son dos
   mecanismos simultáneos para el mismo dato sin jerarquía entre ellos.
3. **C4 — Rotación también en Crear.** Se añade el mismo bloque de
   `EditTaskScreen.kt:790-892` (switch + selector de miembro por día) a
   `CreateTaskScreen.kt`, dentro de "Otros" (sección 2.2). Elimina el paso
   oculto "crear → editar" para configurar rotación semanal.
4. **Badge de Lista — ¿navega?** No; solo informativo (ya resuelto en
   sección 1).
5. **Renombrar "Checklist".** Sí, → "Lista"/"List" (sección 1 completa).
6. **L4 — Colapsar la barra de búsqueda.** Sí, a icono expandible (sección
   2.4).
7. **Coste de red de "Recompensas disponibles" en Puntuación.** Se acepta,
   con carga perezosa bajo demanda (sección 3, punto 4).

No quedan decisiones abiertas para Liberto.

---

## 6. Blueprint de implementación

Numerado, listo para encargos separados. Todos son cambios de presentación/
copy/flujo — ninguno toca `firestore.rules`, el modelo de datos, ni la
lógica de puntos/recurrencia/asignación/calendario.

### Fase 1 — Estructura de plegados + concepto "Lista"

1. **[APLICA YA]** `AppStrings.kt`: cambiar valores (no claves) de
   `create_task_section_checklist`, `task_detail_checklist_header`,
   `task_list_subtask_badge` (ES+EN, líneas citadas en sección 1). Añadir
   clave nueva `create_task_section_checklist_hint` (ES+EN).
2. **[APLICA YA]** `CreateTaskScreen.kt` / `EditTaskScreen.kt`: pintar el
   subtítulo `create_task_section_checklist_hint` bajo el header de Lista
   (`438-446` / `398-406`).
3. **[APLICA YA]** `TaskDetailScreen.kt`: pintar el mismo subtítulo bajo el
   header de Lista (`535-546`).
4. **[APLICA YA]** `TaskDetailScreen.kt`: extraer las secciones
   "Sincronización Calendar" (`653-671`), "Asignaciones completadas"
   (`719-739`) y "Comentarios" (`741-922`) dentro de un único
   `ExpandableSectionHeader` nuevo, label `task_detail_other_section`
   (clave nueva, ES "Otros" / EN "Other"), plegado por defecto.
5. **[APLICA YA]** `TaskDetailScreen.kt`: mover el bloque de Penalización
   (`493-525`) fuera de la mega-card de info, a la nueva sección
   "Puntuación" (Fase 2, ítem 8) — reutiliza las mismas claves de copy.
6. **[APLICA YA]** `CreateTaskScreen.kt` / `EditTaskScreen.kt`: envolver
   Lista, Etiquetas y Asignación cada una en su propio
   `ExpandableSectionHeader`, con el estado por defecto de la tabla 2.2.
7. **[APLICA YA]** `CreateTaskScreen.kt` / `EditTaskScreen.kt`: crear el
   grupo "Otros" (`ExpandableSectionHeader`, misma clave
   `task_detail_other_section`) conteniendo Fecha límite, Penalización y
   Rotación; estado por defecto según tabla 2.2 (expandido si alguna
   sub-sección ya está activa).
8. **[APLICA YA]** `EditTaskScreen.kt`: mover el bloque de Rotación
   (`790-892`) dentro del nuevo "Otros"; duplicar ese mismo bloque (switch +
   selector por día) en `CreateTaskScreen.kt`, dentro de su propio "Otros".
9. **[APLICA YA]** `CreateTaskScreen.kt` / `EditTaskScreen.kt`: chip final
   "+ Otra" (clave nueva `create_task_tag_other_chip`) que revela el campo
   de texto libre de etiquetas.
10. **[APLICA YA]** `TaskListScreen.kt`: colapsar `SearchBar` (`1238-1274`)
    a icono expandible dentro de `FilterChipsRow`.
11. **[APLICA YA]** `TaskDetailScreen.kt`: si `assignments.isNotEmpty()`,
    ocultar el botón "Hecho" de nivel-tarea (`586-601`) de la sección
    "Completar".

### Fase 2 — Puntuación

12. **[APLICA YA]** `AppStrings.kt`: añadir claves nuevas
    `task_detail_points_section`, `task_detail_points_how_it_works`,
    `task_detail_points_earn_desc`, `task_detail_points_your_balance`,
    `task_detail_points_rewards_header`, `task_detail_points_rewards_empty`,
    `task_detail_points_view_all_rewards` (ES+EN, textos en sección 3).
13. **[APLICA YA]** `TaskDetailScreen.kt`: nueva sección "Puntuación"
    (`ExpandableSectionHeader`, plegada con teaser, posición 3 de la tabla
    2.1) — contenido "Cómo funciona" + (si aplica) Penalización trasladada
    + "Tu saldo" usando `memberMap[currentMemberId]?.totalPoints` (sin
    petición nueva).
14. **[APLICA YA]** `TaskDetailScreen.kt`: inyectar `MemberScreenModel` y
    disparar `loadRewards(householdId)` en `LaunchedEffect(expanded)` la
    primera vez que se expande "Puntuación"; pintar hasta 3
    `RewardResponse` (icono+título+coste) + CTA "Ver todas las recompensas".
15. **[APLICA YA]** `ExploreScreen.kt`: añadir parámetro opcional
    `initialTab: Int = 0` (usado por el CTA del ítem 14 para abrir
    directamente en la pestaña Recompensas, índice 2).
16. **[APLICA YA]** `AppStrings.kt`: añadir clave nueva
    `reward_list_explainer_banner` (ES+EN, texto en sección 3).
17. **[APLICA YA]** `RewardListScreen.kt` (`RewardsBody`): pintar el banner
    del ítem 16 entre la línea 127 y 129 (antes de la lista/grid de
    recompensas).

### Fase 3 — Resto

18. **[APLICA YA]** `AppStrings.kt`: añadir clave `task_detail_other_section`
    (ES "Otros" / EN "Other") — reutilizada por Fase 1 ítems 4 y 7.
19. **[APLICA YA]** `TaskDetailScreen.kt`: punto de aviso (`StatusDot`
    error) en el header de "Otros" cuando `calendarActionState` o
    `sendCommentError` estén en error y la sección esté plegada.
20. **[APLICA YA]** Verificación final: `./gradlew
    :composeApp:compileDebugKotlinAndroid --console=plain` y
    `./gradlew :composeApp:jvmTest --console=plain` tras cada fase.

**Recuento: 20 ítems, 20 [APLICA YA], 0 [REQUIERE DECISIÓN].**

---

## Archivos leídos (completos, para este diseño)

- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt` (1469 líneas, estado post quick-wins)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskDetailScreen.kt` (1251 líneas, estado post quick-wins)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt` (1221 líneas, estado post quick-wins)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditTaskScreen.kt` (1118 líneas)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdTaskSection.kt` (257 líneas, estado post quick-wins)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/MemberRewardScreen.kt` (302 líneas, completo)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/RewardListScreen.kt` (líneas 1-160, cuerpo de `RewardsBody` hasta el inicio del grid)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/ExploreScreen.kt` (93 líneas, completo)
- `composeApp/src/commonMain/kotlin/org/taskhub/network/models/DTOs.kt` (líneas 120-220 y 365-394: `AssignmentSlot`, `Subtask`, `TaskResponse`, `RewardResponse`, `RewardRedemption`; grep dirigido a `totalPoints`)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/ExpandableSectionHeader.kt` (firma y primeras líneas)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt` (grep dirigido a las claves citadas, ES+EN)
- `docs/revision-ux-tareas-2026-09-17.md` (informe v1, completo)
- `docs/quickwins-ux-tareas-2026-09-17.md` (informe de implementación de quick wins, completo)

No se ha ejecutado build (no se ha tocado código, según alcance de esta
tarea).
