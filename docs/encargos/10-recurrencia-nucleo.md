---
workdir: /home/liberto/task-hub
max_turns: 400
allowed_tools: Read,Edit,Write,Bash,Grep,Glob
---

# Encargo: recurrencia — cerrar la asimetría completeTask/completeAssignment (3 cambios)

## Contexto
El panel v3 (`docs/review-panel-expertos-v3-2026-09-01.md`, sección "FOCO ESPECIAL
— Tareas recurrentes") dejó como PROPUESTA el núcleo de la recurrencia, pendiente
de decisión del usuario. El usuario YA HA DECIDIDO aplicarlo. Versión actual 0.7.25
(HEAD `c8d11e1`). Lee el informe antes de empezar.

El veredicto del panel fue: mantener el modelo "documento único + recálculo en
cliente", pero **añadir un campo `nextDueAt` persistido** y **unificar la
regeneración de la asignación siguiente** en un único helper que respete
`assignmentRotation`, invocado desde `completeTask` y `completeAssignment`.

## Cambios a aplicar (decisión EXPLÍCITA del usuario — no pedir confirmación)

### 1. Añadir `nextDueAt` persistido (arregla 3 bugs a la vez)
- Bugs: (a) `completeTask` no regenera la asignación de la siguiente ocurrencia de
  una tarea recurrente (rompe sync Google Calendar y deja `TaskDetailScreen` con
  datos del ciclo cerrado); (b) `assignmentRotation` no gobierna nada —
  `completeAssignment` siempre reasigna al mismo miembro; (c) la penalización por
  retraso casi nunca se aplica a recurrentes porque `task.dueDate` vale 0 para
  daily/weekly/monthly (→ `resolveCompletionOutcome` da siempre `onTime=true`).
- Solución: campo `nextDueAt` en el DTO `TaskResponse` (y donde corresponda),
  calculado con `RecurrenceRules.nextOccurrence` en el MISMO PATCH que ya
  actualiza `lastCompletedDate` (sin viaje de red extra). Migración aditiva:
  tareas antiguas sin el campo caen al recálculo actual como fallback.
- `resolveCompletionOutcome` debe usar `nextDueAt` (o la fecha límite real de la
  ocurrencia) para calcular la penalización, no `dueDate`.

### 2. Unificar la regeneración de la asignación siguiente + concurrencia optimista
- Extraer un único helper "crear/renovar la siguiente asignación respetando
  `assignmentRotation`", invocado desde AMBOS flujos (`completeTask` y
  `completeAssignment`). Hoy `completeAssignment` sí regenera pero siempre al
  mismo miembro (ignora `assignmentRotation`); `completeTask` no regenera.
- Añadir concurrencia optimista a `completeAssignment`: `currentDocument.updateTime`
  (ya lo tiene `completeTask`) para que dos dispositivos completando a la vez NO
  dupliquen puntos/historial ni bifurquen la cadena de asignaciones. Definir un
  mensaje de error claro para el "perdedor de la carrera".
- Robustecer/dedup `assignTask` según proceda.

### 3. Selector semanal de recurrencia: premarcar todos los días
- Hoy "semanal sin ningún día marcado" equivale silenciosamente a "todos los días"
  (`recurrenceDays.isEmpty()` en `RecurrenceRules.isDueToday`), sin aviso.
- Decisión del usuario: **al elegir "Semanal", los 7 días vienen premarcados por
  defecto** (en vez de bloquear guardado o avisar). Aplícalo en `CreateTaskScreen`
  / `EditTaskScreen` (el selector de chips de día).
- La lógica `recurrenceDays.isEmpty()` como "todos los días" puede mantenerse como
  fallback, pero la UI ya no debe permitir llegar a ese estado ambiguo por defecto.

## Pistas técnicas (lee siempre el código real)
- Reglas: `network/RecurrenceRules.kt` (`nextOccurrence`, `isDueToday`,
  `clampDayOfMonth`).
- DTO: `network/models/DTOs.kt` (`TaskResponse`).
- Repo: `network/TaskRepository.kt`, `network/FirestoreRepository.kt`
  (`completeTask` ~1054-1104, `completeAssignment` ~1392-1409,
  `resolveCompletionOutcome` ~1452-1492, `assignTask`).
- ScreenModel: `ui/models/TaskScreenModel.kt`.
- UI: `ui/screens/CreateTaskScreen.kt`, `EditTaskScreen.kt`, `TaskListScreen.kt`,
  `TaskDetailScreen.kt`, `CalendarScreen.kt`.
- i18n: `ui/i18n/AppStrings.kt` (ES+EN).

## Criterios
- APLICA YA: decisión explícita del usuario, no pedir confirmación.
- Es un cambio de esquema y de comportamiento de puntos en producción: hazlo con
  cuidado, con fallback para tareas antiguas y sin romper racha/streak, historial
  ni `RecurrenceRules`.
- Ajusta/añade tests en `commonTest` (`RecurrenceRulesTest.kt` y afines) para los
  cambios de lógica.
- i18n ES+EN sin texto hardcodeado. Solo `material-icons-core`.

## Verificación (OBLIGATORIO)
```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y tests en verde.

## Convenciones
- Comentarios/KDoc en español. NO hagas commit, push ni bump (lo hace el orquestador).

## Entrega (resumen final obligatorio)
1. Por cada uno de los 3 cambios: qué hiciste y con `archivo:línea`.
2. Resultado de compilación y tests.
3. Cualquier cosa que dejes a medias o como PROPUESTA, con motivo.
