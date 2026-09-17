# Fase 3 — Implementación diseño v2 tareas: resto (2026-09-17)

Implementa los ítems 18-20 del blueprint `docs/diseno-vista-tareas-v2-2026-09-17.md`
(sección 6, Fase 3), sobre el estado post Fase 2 (`d5da54c`,
`docs/fase2-diseno-v2-tareas-2026-09-17.md`). Alcance estrictamente
presentación/copy/flujo — sin tocar `firestore.rules`, modelo de datos ni
lógica de puntos/recurrencia/asignación/calendario.

Archivo tocado: `TaskDetailScreen.kt`.

---

## Ítem por ítem

### 18. Clave `task_detail_other_section` — verificación

Ya existía, añadida en la Fase 1 (adelantada a propósito porque sus ítems 4
y 7 la necesitaban): `AppStrings.kt:574` (ES `"Otros"`) y `:1219` (EN
`"Other"`). Verificado con `grep`; no se duplicó ni se tocó.

### 19. `TaskDetailScreen.kt` — punto de aviso en "Otros" plegado

El blueprint cita `calendarActionState` y `sendCommentError` por nombre; se
verificó que ambos siguen existiendo tal cual tras las Fases 1-2
(`TaskDetailScreen.kt:92` y `:96`, parámetros de `TaskDetailContent` en
`:330`/`:340`) — no hizo falta adaptar nombres.

En el `item` de la cabecera de "Otros" (antes `:884-898`), se añade:

```kotlin
val otrosHasError = calendarActionState is TaskScreenModel.CalendarActionState.Error ||
    sendCommentError != null
...
if (!otrosExpanded && otrosHasError) {
    StatusDot(color = MaterialTheme.colorScheme.error, size = 8.dp)
    Spacer(modifier = Modifier.width(8.dp))
}
```

Reutiliza `StatusDot` (`ui/components/StatusDot.kt`), el mismo componente ya
usado para el punto de color de `GroupHeader` en `TaskListScreen.kt:1222`
(el blueprint citaba unas líneas `1226-1229` que en el estado real
corresponden a ese mismo uso de `StatusDot`, no a un badge "!" literal —
mismo patrón, cita adaptada). Se pinta solo cuando la sección está plegada
(`!otrosExpanded`) y hay error real; al expandir, el punto desaparece porque
el error ya es visible dentro (banner de `sendCommentError`,
`:960-...`, y aviso de `CalendarActionState.Error` dentro de
`CalendarSyncStatusCard`, `:1289-1292`). Se añadió el import
`org.taskhub.ui.components.StatusDot`, ausente hasta ahora en este archivo.

No se tocó ningún ScreenModel ni callback — cambio puramente de
presentación condicional dentro de un `item` ya existente.

### 20. Verificación final

Ver sección siguiente. Recorrido de coherencia adicional pedido por el
encargo:

- `grep` de "Checklist"/"checklist"/"subtarea" en
  `composeApp/.../ui/` → todas las coincidencias son identificadores de
  código (`checklistExpanded`), comentarios internos (KDoc, comentarios de
  sección `// ── Lista (antes "Checklist") ──`) o claves i18n
  (`create_task_section_checklist`, `task_list_subtask_badge`). Ningún
  string visible al usuario usa ya "Checklist"/"subtarea" fuera de
  `AppStrings.kt`, donde los *valores* ya son `"📋 Lista"`/`"📋 List"`/
  `"📋 %1/%2"`. Sin hallazgos que corregir.
- `grep` de los valores antiguos (`"✅ Checklist"`, `"✔ %1/%2"`) → sin
  resultados, no queda ningún resto del copy/icono viejo.
- Las claves i18n nunca se renombraron (Fases 1-2 solo cambiaron valores,
  según su propio diseño) — no hay claves viejas huérfanas que buscar.

---

## Desviaciones del blueprint

- Ítem 19: la referencia `TaskListScreen.kt:1226-1229` del blueprint (para
  el patrón de "punto de aviso") corresponde en el código real al uso de
  `StatusDot` dentro de `GroupHeader` (línea `1222` en el estado actual, no
  un badge "!" independiente) — mismo componente, misma intención
  (indicador de color por estado), cita ajustada a la línea real.
- Sin más desviaciones: `calendarActionState`/`sendCommentError` existen
  con los mismos nombres que cita el blueprint.

---

## Verificación

### Build

```
$ ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
...
BUILD SUCCESSFUL in 11s
25 actionable tasks: 1 executed, 24 up-to-date
```

Sin errores; únicos `w:` son los warnings preexistentes ya conocidos
(deprecaciones de Google Sign-In/EncryptedSharedPreferences, `when`
exhaustivos con `else` redundante en `CalendarScreen.kt`/`TaskListScreen.kt`).

### Tests JVM

```
$ ./gradlew :composeApp:jvmTest --rerun-tasks --console=plain
...
BUILD SUCCESSFUL in 11s

$ python3 ~/.hermes/profiles/taskhub/skills/autonomous-ai-agents/claude-code-queue/scripts/parse-jvm-test-results.py
...
TOTAL: 257 tests, 0 failures, 0 errors
```

Mismo recuento que en Fases 1 y 2 (257 tests); ningún test cubre el punto
de aviso de "Otros" ni el resto de cambios de esta fase, no hizo falta
actualizar tests existentes.

---

## CHECKLIST de cierre — blueprint completo (20 ítems)

**Fase 1 — plegados + concepto "Lista"** (commit `b7ee1a5`)

1. DONE — `AppStrings.kt`: valores "Lista"/"List" + `create_task_section_checklist_hint`.
2. DONE — Subtítulo de ayuda en Crear/Editar bajo header de Lista.
3. DONE — Mismo subtítulo en el detalle.
4. DONE — Grupo "Otros" en `TaskDetailScreen.kt` (Calendar + completadas + comentarios).
5. DONE — Penalización movida fuera de la tarjeta de info (completado en Fase 2, ítem 13; Fase 1 lo dejó explícitamente pendiente).
6. DONE — Lista/Etiquetas/Asignación plegables en Crear/Editar.
7. DONE — Grupo "Otros" en Crear/Editar (Fecha límite + Penalización + Rotación).
8. DONE — Rotación movida en Edit y duplicada en Create.
9. DONE — Chip "+ Otra" en Etiquetas.
10. DONE — Búsqueda colapsada a icono en `TaskListScreen.kt`.
11. DONE — "Hecho" unificado con asignaciones (D4).

**Fase 2 — Puntuación** (commit `d5da54c`)

12. DONE — Claves nuevas de "Puntuación" en `AppStrings.kt`.
13. DONE — Sección "Puntuación" en `TaskDetailScreen.kt` (Cómo funciona + Penalización trasladada + Tu saldo).
14. DONE — `MemberScreenModel` + carga perezosa de recompensas al expandir.
15. DONE — `ExploreScreen.kt` con `initialTab`.
16. DONE — Clave `reward_list_explainer_banner`.
17. DONE — Banner explicativo en `RewardsBody`.

**Fase 3 — resto** (este commit)

18. DONE (ya existente desde Fase 1, verificado sin duplicar) — clave `task_detail_other_section`.
19. DONE — punto de aviso (`StatusDot` error) en header de "Otros" plegado, condicionado a `calendarActionState is CalendarActionState.Error || sendCommentError != null`.
20. DONE — verificación final: build OK, 257 tests JVM OK (0 fallos), recorrido de coherencia sin hallazgos (sin copy "Checklist"/"subtarea" visible, sin claves huérfanas).

**Recuento: 20/20 ítems DONE. Diseño v2 completo.**

---

## Resumen final

```
$ git log --oneline -1
<se completa tras el commit>
```
