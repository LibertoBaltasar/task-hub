# Recurrencia: ventana de "atrasada" para tareas nunca completadas — 2026-09-12

Encargo procesado tras `ronda-deuda-aplicable-2026-09-12.md`. HEAD de partida:
`3fd1d7b`. Working tree limpio al empezar y al terminar (solo el commit de
este encargo).

## Problema

`RecurrenceRules.isDueOn`/`isDueToday` (`network/RecurrenceRules.kt`) calcula
si una tarea recurrente "toca" a partir de `frequency` + `recurrenceDays`/
`recurrenceDay` + `lastCompletedDate`. Para una tarea **ya completada alguna
vez**, si el día programado de este ciclo pasa sin marcarse, la tarea sigue
apareciendo como pendiente/atrasada hasta que se completa ("completado
tardío" / catch-up) — así no desaparece sin dejar rastro.

Para una tarea **nunca completada** (`lastCompletedDate == null`), esa
ventana no existía: las ramas `weekly`/`monthly` solo comprobaban
`dow in recurrenceDays` / `date == thisMonthTarget` — el día EXACTO
programado. Ejemplo: una semanal con `recurrenceDays=[Lunes, Miércoles]`,
nunca marcada, aparecía el lunes, desaparecía el martes (sin haberse
completado) y volvía a aparecer el miércoles, sin ningún indicio de que el
lunes se perdió. Un test explícito documentaba este comportamiento
(`isDueToday_weekly_neverCompletedAndDayAlreadyPassed_staysNotDueUntilNextCycle`,
antes en `RecurrenceRulesTest.kt:364-372`).

## Cambio exacto

**`composeApp/src/commonMain/kotlin/org/taskhub/network/RecurrenceRules.kt`**

- `isDueToday` (línea 74) e `isDueOn` (línea 95): nuevo parámetro
  `createdAt: Long = 0` (por defecto al final de la firma, tras `tz`, para no
  romper las llamadas posicionales existentes en los tests que terminaban en
  `tz`).
- `isDueOn` (líneas 107-112): se deriva `createdLocalDate` (nullable) a
  partir de `createdAt` — `null` si `createdAt <= 0` (desconocido/legado).
- Rama `weekly` (líneas 127-147): antes `if (lastCompletedLocalDate == null) { dow in recurrenceDays } else { ... }`,
  ahora un `when` de 3 casos:
  1. `lastCompletedLocalDate != null` → sin cambios (catch-up existente,
     ancla en la última compleción, comparación estricta `<`).
  2. `lastCompletedLocalDate == null && createdLocalDate != null` (**nuevo**)
     → mismo cálculo de `mostRecentWeeklyOccurrence`, pero ancla en
     `createdLocalDate` con comparación **no estricta** `<=` (ver "Por qué
     `<=` y no `<`" abajo).
  3. `createdLocalDate == null` (legado) → comportamiento exacto de antes
     (`dow in recurrenceDays`).
- Rama `monthly` con `recurrenceDay` (líneas 148-167): mismo patrón de 3
  casos, extrayendo el cálculo de "target más reciente" a la función local
  `mostRecentMonthlyTarget()` (antes duplicado inline, ahora compartido entre
  el caso 1 y el nuevo caso 2).
- La rama `monthly` **legada** (sin `recurrenceDay`, líneas 168-176) y la
  rama `daily` **no cambian**: `daily` ya estaba siempre "debida" sin
  completar hoy (no tiene el problema); el modo mensual legado (toca una vez
  al mes, cualquier día) no tiene un "día programado" concreto sobre el que
  aplicar ventana, y el encargo no lo pedía.
- `isOverdueOccurrence` (línea ~200, sin cambios) — sigue funcionando sin
  tocar: compara el día de hoy contra el día programado, independientemente
  de si la tarea se completó alguna vez, así que ya distinguía "toca hoy
  normal" de "atrasada" para el caso nuevo sin necesitar `createdAt`.

**Por qué `<=` en el caso 2 y no `<` (como en el caso 1):** completar una
tarea el día programado la resuelve (por eso el catch-up con
`lastCompletedLocalDate` usa `<` estricto: completar EN el target != seguir
debiendo). Pero *crear* una tarea el día programado no la resuelve — ese día
debe seguir tocando con normalidad (caso protegido, ver KDoc
`RecurrenceRules.kt:67-72`). `createdLocalDate <= mostRecentTarget` da
exactamente eso: si el target más reciente es HOY y la tarea se creó HOY,
`hoy <= hoy` → `true` (toca, no desaparece), y por separado
`isOverdueOccurrence` no la marca como atrasada porque hoy SÍ es el día
programado.

**Call-sites actualizados** (propagación de `task.createdAt`, campo que ya
existía en `TaskResponse` — `network/models/DTOs.kt:220` — y ya se escribía/
parseaba en `TaskRepository`/`FirestoreParsers`, no fue necesario tocar
escritura ni parseo):
- `ui/screens/TaskListScreen.kt:368-376` (`isTaskDueToday`, alimenta
  `overdueItems`/`pendingToday` de `groupTasksByStatus`).
- `ui/models/HomeScreenModel.kt:247-259` (`isPending`, dashboard de
  `HomeScreen`).
- `ui/screens/CalendarScreen.kt:896-915` (`isTaskDueOnDay`, vista de
  calendario).

Ningún cambio de UI: los tres call-sites ya alimentaban los grupos/labels
existentes (`overdueItems`, dashboard, celdas de calendario); al cambiar
`isDueToday` esos mismos grupos ahora incluyen también el caso
nunca-completada-y-atrasada sin ninguna clave i18n nueva (usan las mismas
`calendar_task_status_overdue`/`calendar_task_status_pending` etc. de
siempre).

## Decisión de ancla para tareas legadas

`createdAt` ya se escribe en cada `TaskResponse` desde antes de este encargo
(`TaskRepository.kt:183,236`), pero tareas creadas antes de que ese campo
existiera (o cualquier documento donde el parseo falle) tienen
`createdAt == 0` al leerlas (`FirestoreParsers.kt`, default `?: 0L`).

Opciones consideradas para esas tareas legadas:
1. **Ancla = hoy, recalculada en cada evaluación** — descartada: al no
   persistirse, "hoy" cambia en cada llamada y el caso 2 colapsaría siempre
   al caso legado (`createdLocalDate` recalculado = `date` consultada →
   `mostRecentTarget <= date` siempre cierto solo si `mostRecentTarget ==
   date`, idéntico al comportamiento legado sin aportar nada — y con el
   riesgo de implementarlo mal y sí introducir falsos atrasos).
2. **Ancla = primer `taskHistory`** — descartada para esta ronda: requiere
   una lectura adicional (`taskHistory` es una subcolección, no un campo del
   documento de la tarea) desde una función pura sin I/O; habría que
   resolverlo en la capa de repositorio/UI antes de llamar a
   `RecurrenceRules`, con coste de complejidad no justificado para tareas que
   probablemente son pocas o ninguna en la base actual (el campo `createdAt`
   lleva escribiéndose un tiempo).
3. **Elegida: `createdAt == 0` → sin ventana, comportamiento exacto previo**
   (caso 3 del `when`, `dow in recurrenceDays` / `date == thisMonthTarget`).
   Es la opción más segura: nunca genera un falso "atrasada" retroactivo
   sobre una tarea cuya fecha real de creación se desconoce. El coste es que
   esas tareas (si existen) no se benefician de la ventana hasta que se
   complete alguna vez — momento en el que `lastCompletedDate` deja de ser
   `null` y entran directamente en la rama catch-up ya existente (caso 1),
   sin necesitar `createdAt` en absoluto.

## Tests

`composeApp/src/commonTest/kotlin/org/taskhub/network/RecurrenceRulesTest.kt`

- **Modificado**: `isDueToday_weekly_neverCompletedAndDayAlreadyPassed_staysNotDueUntilNextCycle`
  se sustituye por dos tests — el comportamiento cambia según si se conoce
  `createdAt` o no:
  - `isDueToday_weekly_neverCompletedAndDayAlreadyPassed_withCreatedAtBeforeSchedule_isDueLate`
    (nuevo comportamiento: `assertTrue`).
  - `isDueToday_weekly_neverCompletedAndDayAlreadyPassed_legacyNoCreatedAt_staysNotDueUntilNextCycle`
    (caso legado: conserva `assertFalse`, documentando la decisión de ancla).
- **Nuevos** (semanal):
  - `isDueToday_weekly_neverCompleted_createdOnScheduledDayItself_isDueNotOverdue`
    — caso protegido: creada el mismo día programado → debida, y
    `isOverdueOccurrence` confirma que NO se marca atrasada.
  - `isDueToday_weekly_neverCompleted_createdAfterThisWeeksScheduledDayAlreadyPassed_isNotDueYet`
    — creada el día siguiente al programado ya pasado → no debida todavía
    (esa ocurrencia es anterior a la existencia de la tarea).
- **Nuevos** (mensual, mismo patrón):
  - `isDueToday_monthlyWithDay_neverCompletedMissedScheduledDay_withCreatedAtBeforeSchedule_isDueLate`
  - `isDueToday_monthlyWithDay_neverCompleted_createdOnScheduledDayItself_isDueNotOverdue`
  - `isDueToday_monthlyWithDay_neverCompletedAndDayAlreadyPassed_legacyNoCreatedAt_staysNotDueUntilNextCycle`
- **Sin regresión** (ya cubiertos por tests preexistentes, no modificados):
  caso ya-completada catch-up
  (`isDueToday_weekly_missedScheduledDay_withPriorCompletion_isDueLate`,
  `isDueToday_monthlyWithDay_missedScheduledDay_withPriorCompletion_isDueLate`)
  y caso `once`
  (`isDueToday_once_neverCompleted_isDue`, `isDueToday_once_completed_isNotDue`)
  — ninguno pasa `createdAt` explícito, usan el default `0`, y su resultado
  no depende de esa rama.

## Excepciones on-demand (diseño, NO implementado)

Feature futura para cubrir casos "especiales" de un día concreto de una
serie recurrente (omitir un día sin que cuente como atrasado, moverlo a otra
fecha, o modificar puntos/descripción solo para esa ocurrencia) sin
materializar instancias completas por `(taskId, fecha)` — ese modelo se
evaluó y se descartó (ver más abajo).

**Por qué no instancias completas:** materializar un documento
`(taskId, fecha)` por cada ocurrencia duplicaría lo que `taskHistory` ya
cubre para completadas, y la ventana de "atrasada" (este encargo) ya cubre
caducadas sin persistir nada — instanciar solo aportaría valor para el caso
de "excepción", pero a cambio de multiplicar lecturas (una query por rango de
fechas en vez de leer el único documento de la tarea) y de escribir esas
instancias con `PATCH` individuales sin `:commit` (Task Hub usa Firestore vía
REST, `network/FirestoreRepository.kt`, sin transacciones multi-documento
reales) — cualquier fallo a mitad de una operación multi-instancia dejaría
estado inconsistente sin forma limpia de recuperarse.

**Diseño propuesto — documento de excepción on-demand:**

Una subcolección nueva, `households/{householdId}/tasks/{taskId}/exceptions/{date}`
(id del documento = fecha ISO `yyyy-MM-dd`, no un id generado — así una
excepción por día es naturalmente idempotente: escribir dos veces la misma
fecha sobrescribe, no duplica), creada **solo** cuando el usuario aplica una
excepción a un día concreto — nunca de forma proactiva ni en bloque.

```
exceptions/{date}  (date = "2026-09-15", ISO local, mismo día que ve el usuario)
  type: "skip" | "move" | "modify"
  # skip: ese día no cuenta como debido/atrasado, sin más campos.
  # move: la ocurrencia de {date} pasa a movedToDate (epoch millis); el
  #       cálculo de isDueOn para {date} devuelve false, y para
  #       movedToDate debe considerarse due independientemente de si
  #       coincide con recurrenceDays/recurrenceDay.
  movedToDate: Long?       # solo type="move"
  # modify: overrides puntuales solo para esa ocurrencia (no cambian la
  #         tarea base ni futuras ocurrencias).
  overridePoints: Int?     # solo type="modify"
  overrideDescription: String?  # solo type="modify"
  createdAt: Long
  createdBy: String
```

`RecurrenceRules.isDueOn`/`isDueToday` seguirían siendo funciones puras sin
I/O — el llamador (capa de repositorio/UI) resolvería primero si existe una
excepción para esa fecha (lectura puntual por id, no un scan) y, si la hay,
short-circuit antes de invocar `RecurrenceRules` (`skip`/`move` alteran el
resultado de "¿toca?"; `modify` no altera "¿toca?", solo qué puntos/
descripción se muestran si toca). Esto mantiene `RecurrenceRules` testable
sin red, igual que hoy, y limita el coste a **una lectura extra opcional por
tarea con excepciones activas** — no a todas las tareas ni a todos los días.

Queda como referencia para una ronda futura; no se ha tocado ningún archivo
de producto para esta parte del encargo.

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ `BUILD SUCCESSFUL in 10s` (25 actionable tasks: 1 executed, 24 up-to-date).
Sin errores; solo warnings preexistentes de APIs deprecadas de Android
(`GoogleSignIn`, `MasterKey`, etc.), no relacionados con este cambio.

```
./gradlew :composeApp:jvmTest --console=plain
```
→ `BUILD SUCCESSFUL in 10s`. `RecurrenceRulesTest`: 84 tests, 0 fallos, 0
errores (`composeApp/build/test-results/jvmTest/TEST-org.taskhub.network.RecurrenceRulesTest.xml`).
Total `jvmTest`: 216 tests, 0 fallos, 0 errores.

## Commit

Commit único: `fix: tareas recurrentes nunca completadas quedan atrasadas
tras el día programado`. Sin push, sin bump de versión (per instrucciones).
