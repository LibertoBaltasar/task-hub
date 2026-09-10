# Panel de expertos v8 — Task Hub (2026-09-12)

**HEAD de partida:** `2ffa641` (auditoría general 2026-09-11, v0.7.29). Encargo:
panel obligatorio de 13 expertos en paralelo + verificación punto por punto de
los 7 hallazgos "solo propuesta / no aplicado" de la ronda anterior
(`docs/review-panel-expertos-2026-09-11.md`) + 2 bugs reportados directamente
por el usuario durante la sesión.

## Nota de proceso — el panel de 13 expertos NO pudo completarse

Se lanzaron en paralelo los 13 subagentes especialistas (uno por rol del
encargo) más un subagente adicional para el trabajo de `imeAction` en
`CreateTaskScreen.kt`/`EditTaskScreen.kt`. **Los 14 fallaron de forma
inmediata** con el error `Agent terminated early due to an API error: You've
hit your session limit`, antes de producir ningún hallazgo verificado. No es
un fallo de la app ni del código — es un límite de la sesión de la
herramienta que coordina este encargo, ajeno al repositorio.

Ante eso, el coordinador continuó el resto del encargo (verificación del
"primer trabajo", los dos bugs reportados por el usuario, y las correcciones
[APLICA YA] descritas explícitamente en el encargo original) directamente,
sin subagentes, para no dejar la ronda a medias. **No hay hallazgos nuevos de
los 13 expertos en este informe** — sería fabricar contenido no verificado
contra el código real, algo que el propio encargo prohíbe explícitamente
("hallazgos verificables con archivo:línea"). Queda pendiente relanzar el
panel completo de 13 expertos en una sesión con cuota disponible.

---

## Estado de los hallazgos de la ronda 2026-09-11 (primer trabajo)

1. **`RecurrenceRules.isDueOn`/`isDueToday` — parpadeo de tareas
   semanales/mensuales nunca completadas** (`network/RecurrenceRules.kt:114-131`).
   **SIGUE ABIERTO, no aplicado** (decisión de producto, tal como pedía el
   encargo). Verificado contra el código actual: la rama `weekly` con
   `lastCompletedLocalDate == null` (línea ~114) sigue comprobando solo
   `dow in recurrenceDays` sin ventana de "atrasado", igual que la rama
   `monthly` equivalente (~130). Sin cambios desde el informe anterior.

2. **`Checkbox`/`RadioButton` sin rol dedicado** en `TaskDetailScreen.kt`
   (diálogo "quién lo hizo"), `CreateTaskScreen.kt` y `EditTaskScreen.kt`
   (lista de miembros asignables). **[APLICADO]** esta ronda:
   - `ui/screens/TaskDetailScreen.kt`: `.clickable(role = Role.RadioButton) { ... }`
     en la fila de selección de completador (antes `.clickable { ... }` sin rol).
   - `ui/screens/CreateTaskScreen.kt` y `ui/screens/EditTaskScreen.kt`:
     `.clickable(role = Role.Checkbox) { ... }` en la fila de cada miembro
     asignable. El `mergeDescendants` existente en cada `Row` no se tocó —
     el `role` se añade como parámetro de `clickable`, no cambia la
     agrupación semántica.

3. **Formularios grandes sin `imeAction`/`KeyboardActions`**
   (`CreateTaskScreen.kt`, `EditTaskScreen.kt`, `CreateRewardScreen.kt`).
   **[APLICADO]** esta ronda en los tres archivos, con el mismo patrón que
   `CreateHouseholdScreen.kt`/`CreateProfileScreen.kt`/`EditProfileScreen.kt`/
   `JoinHouseholdScreen.kt` (`KeyboardOptions(imeAction = ...)` +
   `KeyboardActions(onDone = { focusManager.clearFocus() })` en el último
   campo de cada secuencia):
   - `CreateRewardScreen.kt`: título → `Next`, coste → `Done` + `clearFocus()`.
   - `CreateTaskScreen.kt`/`EditTaskScreen.kt`: título → `Next`, descripción →
     `Next`, puntos → `Done` + `clearFocus()` (cadena estática siempre
     presente, en el mismo orden). Los campos que solo existen bajo un
     `Switch`/`FilterChip` condicional (día del mes para "mensual", hora de
     fecha límite, valor y tope de penalización) y los campos de
     "añadir ítem rápido" (subtarea, etiqueta) se dejaron cada uno con
     `ImeAction.Done` independiente en vez de encadenarlos con `Next` entre
     sí — encadenar una secuencia `Next` a través de secciones
     condicionales/dinámicas (que aparecen/desaparecen según el usuario
     interactúa) arriesgaba enviar el foco a un campo oculto o a un orden
     sin sentido, sin forma de probarlo con teclado real en esta ronda. Cada
     campo mantiene igualmente un botón de teclado con acción explícita en
     vez de "sin acción" (regresión cero, mejora parcial dirigida a lo
     seguro).

4. **`MainActivity.consumeDeepLink` instancia `HouseholdStore(Settings())` al
   vuelo** (`MainActivity.kt:81-82`). **SIGUE ABIERTO, no aplicado** (riesgo
   de regresión en deep links sin dispositivo real, tal como pedía el
   encargo). Verificado: el código sigue igual.

5. **Notificaciones de chat y borrado de asignaciones en serie**
   (`network/HouseholdRepository.kt` `sendMessage` ~402-420,
   `network/TaskRepository.kt` `deleteAssignmentDocs` ~473-483). **SIGUE
   ABIERTO, no aplicado** (optimización sin bug asociado, tal como pedía el
   encargo). Verificado: ambos siguen con `forEach` + `await` secuencial.

6. **`tryAuthOrApiKey` — 4º sitio con la API key como query param**
   (`network/FirestoreClient.kt:311-321`). **SIGUE ABIERTO, no aplicado**
   (blast radius alto sobre el choke point HTTP compartido, tal como pedía
   el encargo). Verificado: el fallback sigue añadiendo `parameter("key",
   apiKey)` sin cambios.

7. **Hueco de test — `HomeScreenModel.isPending`/`previewFilter`**.
   **[APLICADO]** esta ronda. `ui/models/HomeScreenModel.kt`: `isPending` y
   `previewFilter` (la lógica de filtrado, antes ambas `private fun` de la
   clase) se extrajeron a funciones `internal` de nivel de archivo —
   `isPending(task, now = Clock.System.now())` y `previewFilterTasks(tasks,
   now = ...)` — parametrizadas con `now: Instant` para determinismo en
   test, siguiendo el mismo patrón ya usado con `computeStats` en
   `StatsScreenModel.kt`. Nuevo
   `composeApp/src/commonTest/kotlin/org/taskhub/ui/models/HomeScreenModelTest.kt`
   (4 tests): confirma que una tarea diaria completada AYER está pendiente
   hoy según `isPending`, que `previewFilterTasks` coincide exactamente con
   `isPending` (incluida la exclusión de una tarea ya completada hoy), y un
   caso mixto de varias tareas. Cierra el hueco que el informe 09-11 señalaba
   como demostrado-pero-sin-test-propio.

---

## NUEVO — Bug reportado por el usuario: tareas completadas en días
anteriores desaparecen de toda la pantalla de lista

**[APLICADO] CRÍTICO — confirmado y corregido.**

**Problema.** En `ui/screens/TaskListScreen.kt`, el estado por tarea
calculado para la UI (`TaskWithStatus`, antes de este fix) solo distinguía
`isDueToday` e `isCompletedToday` (completada **hoy**, `lastCompletedDate >=
todayStartEpoch`, línea ~369-372 antes del fix). No existía ningún estado
"completada, punto" — así que una tarea que no estaba ni "debida hoy" ni
"completada hoy" no encajaba en NINGÚN grupo de `groupTasksByStatus` (ni
`overdueItems`, ni `pendingToday`, ni `completedToday`) y desaparecía de la
pantalla por completo.

Esto afecta a dos casos reales, ambos frecuentes:

- **Tareas "once" (no recurrentes) completadas cualquier día que no sea
  hoy.** `RecurrenceRules.kt:154` fija `"once" -> lastCompletedDate == null`
  como regla de "debida" — una vez completada, `isDueToday` es `false` para
  siempre. Combinado con `isCompletedToday` (solo hoy), una tarea "once"
  completada ayer, la semana pasada o hace un mes queda invisible en TODAS
  las vistas de `TaskListScreen`, incluido el filtro "Completadas"
  (`TaskFilter.COMPLETED`, que ANTES del fix miraba literalmente
  `isCompletedToday` en la línea 548 — por diseño solo mostraba lo
  completado el mismo día, nunca el histórico).
- **Tareas recurrentes (diaria/semanal/mensual) completadas pero aún no
  vueltas a estar "debidas".** P. ej. una semanal con `recurrenceDays =
  [lunes]`, completada el lunes: el martes, `isDueToday` es `false` (aún no
  toca) e `isCompletedToday` también es `false` (no se completó hoy, sino
  ayer) → invisible el martes, miércoles, etc., hasta que vuelve a tocar el
  lunes siguiente.

**Por qué importa.** Coincide EXACTAMENTE con el reporte del usuario: "solo
aparecen como completadas las tareas completadas el mismo día; las
completadas en días anteriores no se ven en ninguna vista, ni siquiera en el
filtro de completadas". Confirmado contra el código real, no es percepción:
`state.tasks` (el origen de datos, `ui/models/TaskScreenModel.kt:246`,
`repo.getTasks(householdId)`) SÍ trae todas las tareas del hogar sin filtrar
por fecha — el bug está en el filtrado/agrupado de la UI, no en la carga de
datos ni en Firestore. Es un bug de funcionalidad rota (una lista
"Completadas" que oculta casi todo su contenido real) e integridad de la
sensación de progreso: el usuario ve que su historial de tareas hechas se
"borra" solo, aunque los datos siguen intactos en el servidor.

**Fix aplicado** (`ui/screens/TaskListScreen.kt`):
- Nuevo campo `TaskWithStatus.isCompleted: Boolean` = `!isDueToday &&
  task.lastCompletedDate != null` — "completada y no vuelve a estar
  pendiente ahora mismo", independiente del día concreto.
- `TaskFilter.COMPLETED` (línea ~548) ahora filtra por `isCompleted` en vez
  de `isCompletedToday` — el filtro "Completadas" vuelve a mostrar TODO lo
  completado, no solo lo de hoy.
- `groupTasksByStatus`: nuevo grupo `completedOther` (`items.filter {
  it.isCompleted && !it.isCompletedToday }`), con cabecera propia ("✅
  Completadas", clave i18n nueva `tasks_completed_other`, ES/EN), ordenado
  después de "Completadas hoy" (`sortKey = 100`) y colapsado por defecto
  igual que "Completadas hoy". Sin este grupo, `TaskFilter.ALL` seguía
  ocultando las tareas completadas antiguas aunque ya pasaran el filtro
  (el bug estaba también en el agrupado, no solo en el filtro).
- `TaskCard`: `isDone` (controla tachado, color atenuado, ocultar botón
  "Hecho") pasó de `item.isCompletedToday` a `item.isCompleted`, para que
  una tarjeta en el nuevo grupo "Completadas" se vea igual de "hecha" que
  una completada hoy. `canComplete` no se tocó (ya usaba `isDueToday &&
  !isCompletedToday`, correcto).
- i18n: `tasks_empty_completed` ("No hay tareas completadas hoy" → "No hay
  tareas completadas") en ES y EN, porque el filtro ya no está limitado al
  día actual.

`TaskWithStatus`/`TaskGroup`/`groupTasksByStatus` pasaron de `private` a
`internal` (mismo patrón que `computeStats`/`isPending` en rondas
anteriores) para poder testearlos. Nuevo
`composeApp/src/commonTest/kotlin/org/taskhub/ui/screens/TaskListScreenTest.kt`
(4 tests): una tarea "once" completada ayer aparece en algún grupo (no
desaparece), va al grupo `completed_other` y no al de hoy, una completada
HOY sigue yendo a `completed_today`, y una recurrente debida hoy con
`lastCompletedDate` previo NO cuenta como completada (evita falsos
positivos del fix).

---

## NUEVO — Bug reportado por el usuario: pantalla principal en blanco al
volver de un hogar sin cobertura

**[APLICADO] CRÍTICO — confirmado y corregido.**

**Problema.** En `ui/screens/HomeScreen.kt:77` (antes del fix), el estado
local `households` se inicializaba siempre vacío:
`var households by remember { mutableStateOf<List<SavedHousehold>>(emptyList()) }`.
Es un `remember` **local a esta composición** de `HomeScreen.Content()` — no
vive en el `ScreenModel`, así que se reinicia a `emptyList()` cada vez que
Voyager vuelve a componer `HomeScreen` desde cero, lo cual ocurre típicamente
al hacer `pop()` desde un hogar de vuelta a la pantalla principal (la
composición anterior de `HomeScreen` se había descartado al hacer `push()`
hacia el hogar).

La única forma de rellenar `households` era el `LaunchedEffect(Unit)` de la
línea 86-89, que llama a `model.reconcileHouseholds()` —
`network/HouseholdRepository.kt:253-295` — una función `suspend` que hace UNA
petición de red por hogar guardado (`getHousehold(h.id)`, en paralelo vía
`async`/`awaitAll`) para comprobar que cada uno sigue existiendo. Sin
cobertura, cada una de esas peticiones tarda en fallar por timeout en vez de
resolver al instante (aunque el resultado final, gracias al `catch (_:
Exception) { true }` de la línea 269-270, SÍ conserva la lista local
completa — el dato no se pierde, pero tarda).

Mientras esa `suspend fun` está en vuelo, el render (líneas 263-306, antes
del fix) evalúa:
```
if (uiState.isLoading && households.isEmpty()) { /* shimmer */ }
else if (households.isEmpty()) { /* "no tienes hogares, crea uno" */ }
else { /* lista real */ }
```
`uiState` es el `StateFlow` del `HomeScreenModel` (instancia de Koin
persistida entre navegaciones), así que en el momento de volver a
`HomeScreen`, `uiState.isLoading` normalmente sigue siendo `false` (quedó
así la última vez que `loadAllTasks()` completó, antes de irse al hogar) —
la primera rama no aplica. Con `households` recién reiniciada a vacía, cae
en la SEGUNDA rama: la pantalla muestra la ilustración de "no tienes
hogares, crea el primero" en vez de la lista real, durante toda la ventana
en la que `reconcileHouseholds()` sigue esperando la red (offline, eso puede
ser varios segundos por timeout). Es el "en blanco" percibido por el
usuario: el contenido principal desaparece y se sustituye por una pantalla
de "hogar vacío" incorrecta, aunque el usuario SÍ tiene hogares guardados
localmente.

**Por qué importa.** Además de la mala UX inmediata (parece que se han
perdido los hogares), el botón visible en esa pantalla es "Crear hogar" —
justo lo que un usuario confundido podría pulsar por error, creando un hogar
duplicado sin darse cuenta de que los suyos siguen ahí.

**Fix aplicado** (`ui/screens/HomeScreen.kt:77`): `households` se inicializa
ahora desde `model.getSavedHouseholds()` — el método SÍNCRONO y sin red que
ya existía en `HomeScreenModel.kt:64` (`fun getSavedHouseholds(): List<SavedHousehold>
= householdStore.getSavedHouseholds()`), en vez de `emptyList()`. Así, cada
vez que se vuelve a `HomeScreen`, la lista de hogares guardados localmente
se pinta al instante (misma lista que `reconcileHouseholds()` acabará
devolviendo si hay red, o conservando si no la hay — ver el `catch` de
`HouseholdRepository.reconcileHouseholds` citado arriba, que nunca poda por
fallo de red). El `LaunchedEffect(Unit)` sigue llamando a
`reconcileHouseholds()` para podar hogares realmente borrados/inaccesibles
cuando SÍ hay red; simplemente ya no es la única fuente de la primera
pintura.

No se ha podido añadir un test automatizado de este fix: es un
comportamiento de composición de Compose (`remember` reiniciado entre
navegaciones de Voyager) que requiere un test instrumentado de UI, no una
función pura extraíble a `commonTest`. Verificación manual recomendada:
entrar a un hogar, activar modo avión, volver atrás — la lista de hogares
debe seguir visible de inmediato.

---

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   → BUILD SUCCESSFUL
./gradlew :composeApp:jvmTest --console=plain                     → BUILD SUCCESSFUL
```
**189 tests, 0 fallos** (181 heredados de la ronda 09-11 + 4 de
`HomeScreenModelTest` nuevo + 4 de `TaskListScreenTest` nuevo).

## Archivos modificados en esta ronda

Ya commiteados por el checkpoint automático del entorno (`15ceaaa`, antes de
que esta ronda terminara — mismo mecanismo ajeno al coordinador ya descrito
en rondas anteriores): primer trabajo puntos 2, 3 (parcial, solo
`CreateRewardScreen.kt`) y 7.

Pendientes de commit al final de esta ronda:
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditTaskScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/HomeScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt`
- `composeApp/src/commonTest/kotlin/org/taskhub/ui/screens/TaskListScreenTest.kt` (nuevo)
- `docs/review-panel-expertos-2026-09-12.md` (este informe)

## Pendiente para la próxima ronda

- **Relanzar el panel completo de 13 expertos** (estética, funcionalidad
  end-to-end, accesibilidad WCAG AA, UI/componentes, UX, programador senior,
  arquitectura, QA/bugs, seguridad MASVS, privacidad/RGPD, rendimiento,
  red/offline/sync, cobertura de tests) — no se ejecutó esta ronda por el
  límite de sesión descrito arriba.
- Los 4 puntos "SIGUE ABIERTO" de la lista de verificación (1, 4, 5, 6)
  siguen tal cual, documentados arriba con archivo:línea.
