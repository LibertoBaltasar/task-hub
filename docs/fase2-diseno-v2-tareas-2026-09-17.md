# Fase 2 — Implementación diseño v2 tareas: apartado "Puntuación" (2026-09-17)

Implementa los ítems 12-17 del blueprint `docs/diseno-vista-tareas-v2-2026-09-17.md`
(sección 6, Fase 2), sobre el estado post Fase 1 (`b7ee1a5`,
`docs/fase1-diseno-v2-tareas-2026-09-17.md`). Alcance estrictamente
presentación/copy/flujo — sin tocar `firestore.rules`, modelo de datos ni
lógica de puntos/recurrencia/asignación/calendario.

Archivos tocados: `AppStrings.kt`, `ExploreScreen.kt`, `RewardListScreen.kt`,
`TaskDetailScreen.kt`.

---

## Ítem por ítem

### 12. `AppStrings.kt` — claves nuevas de "Puntuación"

Añadidas en el bloque "Detalle de tarea" (ES tras `task_detail_late_pts`,
línea ~652; EN tras su equivalente, línea ~1289), ES+EN literales del
informe sección 3:

| Clave | ES | EN |
|---|---|---|
| `task_detail_points_section` | `"⭐ Puntuación"` | `"⭐ Points"` |
| `task_detail_points_how_it_works` | `"Cómo funciona"` | `"How it works"` |
| `task_detail_points_earn_desc` | `"Al marcarla como hecha, quien la complete gana ⭐ %d puntos."` | `"Whoever marks it done earns ⭐ %d points."` |
| `task_detail_points_your_balance` | `"Tu saldo"` | `"Your balance"` |
| `task_detail_points_rewards_header` | `"Recompensas disponibles"` | `"Available rewards"` |
| `task_detail_points_rewards_empty` | `"Tu hogar aún no tiene recompensas creadas."` | `"Your household hasn't created any rewards yet."` |
| `task_detail_points_view_all_rewards` | `"Ver todas las recompensas →"` | `"See all rewards →"` |

El teaser de la cabecera (`"⭐ Puntuación · {points} pts"`) no usa clave
propia: se compone en código como
`"${s("task_detail_points_section")} · ${task.points} ${s("transfer_points_suffix")}"`,
reutilizando `transfer_points_suffix` (ya existente, `"pts"`/`"pts"`) tal y
como pedía el blueprint.

### 13. `TaskDetailScreen.kt` — sección "Puntuación"

Nuevo `ExpandableSectionHeader` (`pointsExpanded`, inicial `false`),
insertado justo después del bloque "Lista"/subtasks y antes de "Estado de
completado" — posición 3 de la tabla 2.1, tal y como especifica el
blueprint. La cabecera (teaser) es un `item` normal del `LazyColumn`, por lo
que se pinta siempre, esté o no expandida la sección.

Contenido al expandir:
1. **"Cómo funciona"** (`task_detail_points_how_it_works`) +
   `task_detail_points_earn_desc` con `task.points`.
2. **Penalización** (si `task.penaltyMode != null`) — bloque **movido** desde
   la tarjeta de info (antes líneas 497-529, ver Fase 1 ítem 5 que lo dejó
   pendiente para esta fase): mismas claves i18n (`create_task_penalty_section`,
   `task_detail_penalty_fixed_desc`/`_percent_desc`/`_max_desc`), sin claves
   nuevas. Se eliminó el bloque original de la tarjeta de info, que ahora
   queda más corta.
3. **"Tu saldo"** (`task_detail_points_your_balance`): `"⭐ {memberPoints}
   pts"` usando `memberMap[currentMemberId]?.totalPoints ?: 0` — **cero
   peticiones nuevas**, reutiliza `memberMap` ya construido en
   `TaskDetailScreen.Content()` (línea 178) y el nuevo parámetro
   `currentMemberId` propagado a `TaskDetailContent`.

### 14. `TaskDetailScreen.kt` — `MemberScreenModel` + carga perezosa de recompensas

`TaskDetailScreen.Content()` inyecta `koinScreenModel<MemberScreenModel>()`
(mismo patrón que `CreateTaskScreen.kt`/`EditTaskScreen.kt`/`ExploreScreen.kt`,
factory ya registrada en `di/AppModule.kt:135`) y colecciona `rewardState`.

`TaskDetailContent` gana un `LaunchedEffect(pointsExpanded)` que llama a
`onExpandPoints()` (→ `memberModel.loadRewards(householdId)`) **solo cuando
`pointsExpanded` pasa a `true`** — no se dispara al abrir el detalle, solo la
primera vez (y cada vez) que el usuario expande "Puntuación"; `loadRewards`
es idempotente (mismo patrón ya usado en `RewardsBody`).

Dentro de la sección, "Recompensas disponibles"
(`task_detail_points_rewards_header`) pinta según `rewardState`:
- `Loading`: spinner pequeño.
- `Success` con lista vacía: `task_detail_points_rewards_empty`.
- `Success` con recompensas: hasta 3 (`sortedBy { it.cost }.take(3)`),
  fila icono+título+coste (`"⭐ {cost}"`); las que el saldo actual no cubre se
  atenúan con `Modifier.alpha(0.5f)` (sin ocultarlas, según blueprint).
- `Error`: texto de aviso inline (mismo patrón `"⚠️ {message}"` que el resto
  de la pantalla).

CTA final `task_detail_points_view_all_rewards` → `onViewAllRewards` →
`navigator.push(ExploreScreen(householdId, currentMemberId ?: "", initialTab = 2))`.

### 15. `ExploreScreen.kt` — `initialTab`

`ExploreScreen` (data class Screen, ya recibía `householdId`/`memberId`) gana
un tercer parámetro `initialTab: Int = 0`. `selectedTab` se inicializa con
`mutableStateOf(initialTab)` en vez de `mutableStateOf(0)`. Al ser un
`data class Screen` de Voyager con un `Int` adicional, el `navigator.push`
existente en `HouseholdScreen.kt:582` (`ExploreScreen(householdId,
currentMemberId)`) sigue compilando sin cambios (usa el default `0`); el
nuevo call site del ítem 14 pasa `initialTab = 2` explícitamente para abrir
directamente en la pestaña Recompensas (índice 2, ya existente en el
`TabRow`/`when (selectedTab)`).

### 16. `AppStrings.kt` — `reward_list_explainer_banner`

Añadida en el bloque "Recompensas (RewardListScreen.kt)" (ES línea ~454, EN
línea ~1091), justo antes de `reward_list_empty_title`:

- ES: `"💡 Ganas puntos completando tareas. Cámbialos aquí por recompensas."`
- EN: `"💡 Earn points by completing tasks. Redeem them here for rewards."`

### 17. `RewardListScreen.kt` — banner en `RewardsBody`

Insertado un `Card` de una línea (fondo `surfaceVariant`, texto
`bodySmall`/`onSurfaceVariant`) justo antes del `when (val rState =
rewardState)` que pinta la rejilla/estados de recompensas — entre el botón
"Nueva recompensa" (solo admins) y la lista, sin plegado (es una sola línea,
según el blueprint).

---

## Desviaciones del blueprint

Ninguna que cambie la intención. Únicas adaptaciones al estado real del
código:
- El ítem 13 no necesitó una clave i18n para el separador `" · "` del
  teaser: se compone directamente en Kotlin, como ya anticipaba el propio
  blueprint ("el `· {points} pts` se compone en código").
- El bloque de Penalización trasladado usa `onSurface`/`onSurfaceVariant` en
  vez de `onPrimaryContainer` (color que tenía dentro de la tarjeta
  `primaryContainer` de info): al vivir ahora fuera de esa tarjeta, sobre el
  fondo normal de la pantalla, se ajustó el color al contenedor real para
  mantener el contraste — mismo criterio de contraste que ya se aplicó en
  otros ajustes de esta pantalla (p.ej. `onSuccessContainer` en
  `CalendarSyncStatusCard`), sin alterar el copy.

---

## Verificación

### Build

```
$ ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
...
BUILD SUCCESSFUL in 38s
25 actionable tasks: 1 executed, 24 up-to-date
```

Sin errores; únicos `w:` son warnings preexistentes no relacionados
(deprecaciones de Google Sign-In/EncryptedSharedPreferences, `when`
exhaustivos con `else` redundante en `CalendarScreen.kt`/`TaskListScreen.kt`).

### Tests JVM

```
$ ./gradlew :composeApp:jvmTest --rerun-tasks --console=plain
...
BUILD SUCCESSFUL in 18s

$ python3 ~/.hermes/profiles/taskhub/skills/autonomous-ai-agents/claude-code-queue/scripts/parse-jvm-test-results.py
...
TOTAL: 257 tests, 0 failures, 0 errors
```

Ningún test assertaba sobre el bloque de Penalización en la tarjeta de info
ni sobre `ExploreScreen`/`RewardListScreen` afectados por los ítems 13-17 —
no hizo falta actualizar tests existentes.

---

## Resumen final

```
$ git log --oneline -1
d5da54c feat: diseño v2 tareas — fase 2 (apartado Puntuación con recompensas canjeables)
```
