# Revisión final (2026-09-17): pestaña "Puntuación" + calendario sin tareas sin fecha

Última ronda de revisión del diseño v2 de tareas (fases 1-3: commits `d5da54c`, `dbdfcdd`, `579ef11`). Dos directivas de Liberto tras probar la app.

## 1. Pestaña "Puntuación" (Directiva 1)

**Verificación previa:** la Fase 2 (`d5da54c`) ya había agrupado puntuación + penalización en una única sección plegable en `TaskDetailScreen.kt`. Tras leer el código actual se confirmó que:

- Solo hay **una** referencia a "penalty"/"Penalización" en todo el archivo (`TaskDetailScreen.kt:614-643`), dentro de la sección "Puntuación" — no queda huérfana en la mega-card de info ni en el grupo "Otros".
- El header de la sección (`TaskDetailScreen.kt:582-594`) ya usa la clave `task_detail_points_section` → **"⭐ Puntuación"** (ES) / **"⭐ Points"** (EN), con teaser de puntos visible aunque esté plegada.

No hizo falta ningún cambio de código: el estado actual ya cumple la directiva. Estructura final de la pestaña "Puntuación" (`TaskDetailScreen.kt:581-750`):

```
⭐ Puntuación · N pts                          (header plegable, línea 582)
└── (al expandir, línea 597)
    ├── Cómo funciona                          (línea 599, "task_detail_points_how_it_works")
    │   └── "Al marcarla como hecha, quien la complete gana ⭐ N puntos."
    ├── ⚠️ Penalización por retraso              (línea 617, solo si task.penaltyMode != null)
    │   ├── "-X pts / -X% por cada día|semana|mes"
    │   └── "Tope máximo: -X pts" (si penaltyMax > 0)
    ├── ── divider ──
    ├── Tu saldo                               (línea 651, "task_detail_points_your_balance")
    │   └── "⭐ N pts" (memberMap, sin petición nueva)
    └── Recompensas disponibles                (línea 670, carga perezosa)
        ├── hasta 3 recompensas (icono + título + coste, atenuadas si no llegan)
        └── "Ver todas las recompensas →"
```

Cada sub-bloque tiene su propio label (`titleSmall`/`labelLarge` en negrita), reconocible visualmente sin necesidad de dividers entre todos ellos (Cómo funciona + Penalización comparten bloque por estar ambos ligados a "cómo se ganan/pierden los puntos de esta tarea"; Tu saldo y Recompensas sí llevan divider por ser datos del usuario, no de la tarea).

## 2. Calendario: tareas sin fecha límite fuera de la cuadrícula (Directiva 2)

### Regla implementada

`CalendarScreen.kt:1100-1134` (antes `isTaskDueOnDay`, privada; ahora `internal` para poder testearla):

- **`frequency == "once"` sin completar:**
  - `dueDate > 0` → debida desde `dueDate` en adelante (sin cambios).
  - `dueDate <= 0` (sin fecha límite) → **`false` en TODOS los días** (antes: `true` en todos los días — el bug reportado). Estas tareas quedan completamente fuera de la cuadrícula.
- **`frequency != "once"` (daily/weekly/monthly):** **sin cambios** — sigue delegando en `RecurrenceRules.isDueOn` exactamente igual que antes. Estas tareas tampoco tienen `dueDate`, pero se consideran "con recurrencia" por definición (su `frequency` ya lo dice) y siguen aaociéndose en sus días programados, incluida la ventana de "atrasada" existente. No se tocó `RecurrenceRules.kt`.

Nueva función pura `isTaskPendingWithoutDueDate(task)` (`CalendarScreen.kt:1128-1129`): `frequency == "once" && dueDate <= 0 && lastCompletedDate == null` — exactamente el conjunto que la regla de arriba excluye de la cuadrícula, usado para alimentar la sección "Pendientes".

### Sección "Pendientes"

`CalendarScreen.kt:880-1015` (`PendingWithoutDueDateSection` + `PendingTaskRow`), pintada debajo de la vista semana/mes (`CalendarScreen.kt:396`), dentro de la misma `Column` desplazable que ahora envuelve toda la pantalla (antes `WeekView`/`MonthView` hacían scroll cada una por su cuenta con `fillMaxSize()`, lo que dejaba sin espacio a cualquier contenido posterior — se quitó ese scroll interno y `MonthView` pasó de `fillMaxSize()` a `fillMaxWidth()`, ya que su altura ya está acotada por fila fija de 100dp).

- **Header:** "📋 Pendientes (N)" + subtítulo "Tareas sin fecha límite — no aparecen en el calendario".
- **Cada fila:** título (1 línea, ellipsis) + `PointsBadge` "⭐ N" + botón "Hecho" (mismo patrón de accesibilidad que `TaskCard` de `TaskListScreen`: customAction en la Card + botón interno con `clearAndSetSemantics`, para no duplicar controles ante TalkBack/VoiceOver). Tocar la fila navega a `TaskDetailScreen`; tocar "Hecho" llama a `model.completeTask(householdId, taskId)`, que reutiliza el mismo `TaskActionState` que el resto de la app — al llegar a `Success` se recarga `listState` (`model.loadTasks`) y se resetea el estado.
- **Estado vacío:** card con "No hay tareas sin fecha límite".
- **Error de completar:** banner inline (mismo patrón `errorContainer` que otras pantallas) justo encima de la sección.

### i18n (ES/EN, `AppStrings.kt`)

| Clave | ES | EN |
|---|---|---|
| `calendar_pending_section` | 📋 Pendientes | 📋 Pending |
| `calendar_pending_section_subtitle` | Tareas sin fecha límite — no aparecen en el calendario | Tasks without a due date — not shown on the calendar |
| `calendar_pending_empty` | No hay tareas sin fecha límite | No tasks without a due date |

No se reutilizó ninguna clave existente con significado distinto; `task_detail_mark_done` se reutiliza tal cual (mismo texto "Hecho" que ya usa el resto de la app).

## 3. Verificación obligatoria

**Build:**
```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
→ BUILD SUCCESSFUL in 16s
```

**Tests:**
```
./gradlew :composeApp:jvmTest --rerun-tasks --console=plain → BUILD SUCCESSFUL in 11s
python3 .../parse-jvm-test-results.py
→ TOTAL: 263 tests, 0 failures, 0 errors
  (incluye org.taskhub.ui.screens.CalendarScreenTest: 6 tests, 0 failures, 0 errors — nuevo)
```

No existía ningún test previo de `isTaskDueOnDay`/calendario (ni de `groupTasksByDate`), así que no hubo que actualizar ninguno con el comportamiento antiguo. Se creó `composeApp/src/commonTest/kotlin/org/taskhub/ui/screens/CalendarScreenTest.kt` con cobertura nueva:

1. tarea "once" sin `dueDate` → `false` en varios días distintos (incl. meses distintos).
2. `isTaskPendingWithoutDueDate` distingue correctamente: sin fecha+sin completar (true) vs. con fecha (false) vs. ya completada (false) vs. recurrente (false).
3. tarea diaria sin `dueDate` sigue tocando cada día (recurrencia preservada) y nunca se clasifica como "pendiente sin fecha".
4. tarea semanal con día concreto sigue su regla de recurrencia (`RecurrenceRules`) sin verse afectada por el cambio.
5. tarea mensual con día concreto ídem.
6. `groupTasksByDate` de integración: la tarea "once" sin fecha no aparece en ningún día de un rango semanal completo.

Para poder testear, se cambiaron de `private` a `internal` (mismo patrón que `TaskListScreen.kt`/`groupTasksByStatus`): `isTaskDueOnDay`, `isTaskPendingWithoutDueDate` (nueva), `groupTasksByDate`, `DayTaskEntry`, `CalendarMode`. Solo visibilidad — sin cambios de comportamiento salvo el descrito arriba.

## 4. Incidencias

Ninguna. No se tocó `firestore.rules`, el modelo de datos, los repos, ni la lógica de recurrencia/puntos/asignación/sincronización con Google Calendar.

## Commit

```
git log --oneline -1
```
(ver hash tras el commit de este cambio, tal y como pide el encargo)
