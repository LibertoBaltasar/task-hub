# Ajustes formulario tarea: apartado Puntuación unificado + penalización y rotación condicionadas

2026-09-17

## Contexto

La ronda "revisión final" (`855af43`) unificó puntos + penalización + saldo + recompensas en la pestaña **Puntuación** solo en `TaskDetailScreen.kt` (pantalla de detalle). Liberto reportó que el formulario de crear/editar tarea (`CreateTaskScreen.kt`, `EditTaskScreen.kt`) no reflejaba ese agrupado: los puntos vivían en "Información básica" y la penalización en el desplegable "Otros", sin ninguna relación visual entre ambos, y la penalización por retraso podía activarse aunque la tarea no tuviera fecha límite (sin sentido: "retraso" respecto a qué). También pidió que la rotación semanal solo apareciera con frecuencia semanal.

## 1. Estructura final del formulario

Mismo orden y mismo patrón (`ExpandableSectionHeader`) en `CreateTaskScreen.kt` y `EditTaskScreen.kt`:

1. Plantillas rápidas (solo Crear)
2. **Información básica** — ahora SOLO título + descripción (el campo de puntos salió de aquí)
3. Lista / Checklist (desplegable)
4. Frecuencia (siempre visible, no desplegable — sin cambios)
5. Etiquetas (desplegable)
6. Asignación (desplegable)
7. **Otros** (desplegable) — Fecha límite (switch + fecha/hora) + Rotación semanal (switch + selector por día), **ahora condicionada a `frequency == "weekly"`**. Ya NO contiene penalización.
8. **Puntuación** (desplegable, nuevo apartado) — campo de puntos + penalización por retraso completa (switch + modo fijo/porcentaje + valor + intervalo + tope), **la penalización condicionada a `hasDeadline == true`**.

Se colocó "Puntuación" al final (después de "Otros") a propósito: la penalización depende de que ya se haya activado la fecha límite en "Otros", así el flujo de lectura del formulario coincide con la dependencia real entre campos (primero decides si hay fecha límite, luego si hay penalización).

Líneas clave (commonMain/kotlin/org/taskhub/ui/screens/):

| Elemento | CreateTaskScreen.kt | EditTaskScreen.kt |
|---|---|---|
| Header "Información básica" (solo título+desc ahora) | ~404-429 | ~349-373 |
| Header "Lista" | 435 | 386 |
| Header "Frecuencia" | 524 | 475 |
| Header "Etiquetas" | 684 | 632 |
| Header "Asignación" | 796 | 744 |
| Header "Otros" (fecha límite + rotación) | 905 | 851 |
| Switch fecha límite (con reseteo de penalización al desactivar) | ~944-970 | ~890-916 |
| Bloque rotación, ahora `if (frequency == "weekly")` | desde 994 | desde 940 |
| Header "Puntuación" (nuevo) | 1101 | 1048 |
| Campo puntos (movido aquí) | dentro de `if (puntuacionExpanded)`, ~1124+ | ~1071+ |
| Switch penalización, `if (hasDeadline)` | 1148 | 1095 |
| Bloque completo penalización, `if (hasPenalty)` | 1168 | 1115 |
| Invariante de guardado (funciones puras, archivo `CreateTaskScreen.kt`, usadas por ambas pantallas del mismo paquete) | `resolvedPenaltyMode` línea 1467, `resolvedRotation` línea 1474 | llamadas en 261 y 274 |

## 2. Condiciones de visibilidad y reseteo de estado

- **Penalización ↔ fecha límite**: el switch de penalización y todo su bloque (modo, valor, intervalo, tope) solo se renderizan si `hasDeadline == true`. Si no hay fecha límite, en su lugar se muestra un texto explicativo (`create_task_penalty_requires_deadline_hint`). Al desactivar el switch de fecha límite (`onCheckedChange` del switch "Fecha límite" en "Otros"), se resetea explícitamente: `hasPenalty = false`, `penaltyMode = "fixed"`, `penaltyValue = ""`, `penaltyInterval = "day"`, `penaltyMax = ""` — así no queda un residuo de configuración de penalización invisible listo para colarse en un guardado posterior.
- **Rotación ↔ frecuencia semanal**: el switch de rotación y los 7 selectores por día solo se renderizan si `frequency == "weekly"`. Al elegir cualquier otra frecuencia en el `FilterChip` de frecuencia, se resetea: `hasRotation = false` y `rotationSlots` vuelve a `(1..7).associateWith { "" }`.
- **Invariante reforzada en el guardado** (además de la ocultación en UI, por si el estado local quedara inconsistente por cualquier otra vía): se extrajeron dos funciones puras y testeables, usadas tanto por `CreateTaskScreen` como por `EditTaskScreen` (mismo paquete `org.taskhub.ui.screens`):
  - `resolvedPenaltyMode(hasPenalty, hasDeadline, penaltyMode): String?` → `null` salvo que `hasPenalty && hasDeadline`.
  - `resolvedRotation(hasRotation, frequency, rotationSlots): List<AssignmentSlot>` → lista vacía salvo que `hasRotation && frequency == "weekly"`.
- **`otrosExpanded` inicial en Edit** (línea 165): ahora `hasDeadline || (task.assignmentRotation.isNotEmpty() && task.frequency == "weekly")` — antes incluía `hasPenalty` (ya no aplica, penalización vive en "Puntuación") y no comprobaba que la rotación legado siguiera siendo semanal.
- **`puntuacionExpanded` inicial**: `true` en ambas pantallas (Create y Edit) — a diferencia de Checklist/Etiquetas (que arrancan colapsadas si no tienen datos), los puntos son un campo obligatorio que antes era siempre visible sin necesidad de expandir nada; mantenerlo expandido por defecto evita una regresión de descubribilidad al meterlo en un desplegable.
- Precarga en Edit: `hasPenalty`/`hasDeadline`/`hasRotation` se siguen inicializando desde el modelo de la tarea (`task.penaltyMode != null`, `task.dueDate > 0`, `task.assignmentRotation.isNotEmpty()`) sin cambios — una tarea existente con penalización o rotación sigue abriendo el formulario mostrando esos valores correctamente en su apartado correspondiente.

## 3. Textos i18n (ES + EN, `AppStrings.kt`)

- **Reutilizada** (sin cambios): `task_detail_points_section` → ES "⭐ Puntuación" / EN "⭐ Points", como header del nuevo apartado.
- **Nueva**: `create_task_section_points_hint`
  - ES: "Puntos que otorga la tarea y penalización por retraso."
  - EN: "Points the task grants and the late penalty."
- **Nueva**: `create_task_penalty_requires_deadline_hint` (texto mostrado en el apartado Puntuación cuando `!hasDeadline`)
  - ES: "Activa la fecha límite (en «Otros») para configurar la penalización por retraso."
  - EN: "Enable the due date (in \"Other\") to configure the late penalty."

Todas las demás claves usadas (`create_task_penalty_section`, `create_task_penalty_fixed`, `create_task_penalty_percentage`, etc.) son las mismas que ya existían, solo reubicadas de composable.

## 4. Verificación

**1) Compilación**
```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ `BUILD SUCCESSFUL` (verificado dos veces: tras la reestructuración inicial y tras extraer `resolvedPenaltyMode`/`resolvedRotation`).

**2) Tests JVM**
```
./gradlew :composeApp:jvmTest --rerun-tasks --console=plain
python3 ~/.hermes/profiles/taskhub/skills/autonomous-ai-agents/claude-code-queue/scripts/parse-jvm-test-results.py
```
→ `BUILD SUCCESSFUL`. Resultado del parser:
```
TOTAL: 269 tests, 0 failures, 0 errors
```
Incluye la suite nueva `org.taskhub.ui.screens.TaskFormSaveInvariantsTest: 6 tests, 0 failures, 0 errors`, añadida como cobertura mínima de la invariante de guardado (ver siguiente punto). No existían tests previos que asertaran el comportamiento antiguo (penalización visible sin fecha límite) que hubiera que actualizar.

**Cobertura añadida**: no hay infraestructura de Compose UI testing en `commonTest` (los tests de pantallas existentes, p.ej. `TaskListScreenTest.kt`/`CalendarScreenTest.kt`, testean funciones puras extraídas del archivo, no renderizado de Composables), así que no es viable testear directamente "el switch de penalización no se pinta sin fecha límite". En su lugar se extrajo la lógica de invariante de guardado (antes inline en el `onClick` del botón Crear/Guardar) a dos funciones puras de nivel de archivo (`resolvedPenaltyMode`, `resolvedRotation` en `CreateTaskScreen.kt`, reutilizadas por `EditTaskScreen.kt` al ser mismo paquete) y se cubrieron con 6 tests en `TaskFormSaveInvariantsTest.kt`: penalización descartada sin fecha límite aunque `hasPenalty=true`, penalización guardada con fecha límite activa, penalización no guardada si está desactivada, rotación descartada si la frecuencia no es semanal aunque `hasRotation=true`, rotación guardada con frecuencia semanal y slots parcialmente asignados, rotación no guardada si está desactivada.

## 5. Incidencias

- Ninguna. No se tocó `TaskDetailScreen.kt`, lógica de negocio, modelo de datos, `firestore.rules`, repos, `RecurrenceRules.kt`, sincronización, ni cómo se aplican puntos/penalización al completar una tarea.
- Se corrigió un comentario desactualizado ("── 'Otros' (Fecha límite + Penalización + Rotación) ──" → "── 'Otros' (Fecha límite + Rotación semanal) ──") en ambos archivos, ya que la penalización dejó de vivir ahí.

## Commit

```
git log --oneline -1
```
(pendiente de ejecutar tras el commit — ver mensaje: `feat: formulario tarea — apartado Puntuación unificado, penalización ligada a fecha límite y rotación a frecuencia semanal`)
