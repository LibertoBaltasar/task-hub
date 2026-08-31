# Cierre de pendientes arquitectónicos (2 de 2) — 2026-08-31

Segunda pasada del encargo de cierre de pendientes, sobre los hallazgos
arquitectónicos de `docs/review-panel-expertos-v2-2026-08-31.md` (Experto 7
"Jefe de arquitectura" + sección "Deuda técnica") y
`docs/review-panel-expertos-2026-08-31.md`. Versión de partida: 0.7.23 + los
fixes de bugs/consistencia de la primera pasada (commit `07929aa`).

Orden de menor a mayor riesgo, según pedía el encargo. Los 5 primeros puntos
se aplicaron completos y verificados; los 2 últimos (mayor riesgo/superficie)
se dejan documentados con plan de fases en vez de aplicados a medias.

## 1. `FirestoreException` consistente — APLICADO

Antes solo `HouseholdScreenModel.loadHousehold` distinguía 404/403 (hogar
borrado/sin acceso) del resto de errores. Se añadió:

- `network/FirestoreException.kt`: extensión `FirestoreException.isGoneOrForbidden`
  y constante `FIRESTORE_GONE_MESSAGE`, reutilizadas donde antes había un
  `if (e.statusCode == 404 || e.statusCode == 403)` inline.
- `TaskScreenModel.loadTasks`/`loadTaskDetail`, `MemberScreenModel.loadMembers`,
  `NotificationScreenModel.loadNotifications`: añadido un `catch (e: FirestoreException)`
  específico antes del `catch (e: Exception)` genérico, con el mismo mensaje
  amigable que `HouseholdScreenModel`. Corrige además un efecto colateral real
  en `loadTasks`: antes un 404/403 (hogar borrado) marcaba `_isOffline.value = true`,
  mostrando "sin conexión" para un caso que no lo es.

No se propagó a `ProfileScreenModel` (su `getUserProfile` ya usa `orDefault(null)`
y nunca deja escapar `FirestoreException`, así que el catch tipado ahí sería
código muerto) ni a los ~20+ sitios de `catch (Exception)` sin relación con
carga de datos de un hogar (deuda ya documentada en la 1ª pasada, Senior #5).

## 2. Invalidación de `TaskCache` tras escrituras — APLICADO

`TaskCache` solo escribía en el camino de lectura. Se añadieron invalidaciones
selectivas (`clearTasks`/`clearMembers`/`clearHouseholdDoc`, más finas que el
`clearHousehold` existente que borra las 3) tras toda mutación relevante en
`FirestoreRepository`: `createTask`, `updateTask`, `updateSubtasks`, `deleteTask`,
`completeTask`, `revertTaskCompletion`, `completeAssignment`, `reassignTaskCompletion`,
`createMember`, `deleteMember`, `updateMemberRole`, `addMemberPoints`,
`appreciateMember` (además de `leaveHousehold`, que borraba miembros sin pasar
por `deleteMember` y no invalidaba nada). `redeemReward`/`donatePoints` quedan
cubiertos porque ambos delegan en `addMemberPoints`.

Efecto: si una escritura tiene éxito pero el refresh posterior falla (p. ej.
se corta la red justo después), una lectura offline ya no sirve en silencio
el estado previo a la mutación — falla de forma visible en vez de mentir.

## 3. Single source of truth para "miembro actual" — APLICADO

`FirestoreRepository.resolveCurrentMember` se memoiza ahora por `householdId`
(`currentMemberCache`, protegido con `currentMemberMutex`): la primera
resolución (identidad + `getMembers` + posible creación de miembro "Yo") se
computa una sola vez por hogar y sesión; las llamadas siguientes desde
`HouseholdScreen`, `CalendarSyncManager` y `TaskScreenModel` devuelven el
mismo valor sin repetir el trabajo. La entrada se invalida en
`leaveHousehold`/`deleteHousehold`. No se cachea un resultado vacío (fallo
transitorio), para no bloquear un reintento futuro.

De paso, se detectó y consolidó una duplicación relacionada: 3 pantallas
(`HouseholdScreen`, `TaskDetailScreen`, `RewardListScreen`) reimplementaban el
mismo cálculo de "¿soy el owner del hogar?" (`getLocalId() == household.ownerId`).
Se extrajo a `FirestoreRepository.isHouseholdOwner(householdId)`, expuesto vía
`HouseholdScreenModel`/`TaskScreenModel`/`MemberScreenModel`.

## 4. Atomicidad de `updateTask` — MITIGADO

Ver detalle en `docs/atomicidad-commit-pendiente.md` (sección añadida
"`updateTask` / reasignación de miembros al editar"). Resumen: se sustituyó
`deleteAssignments` + `assignTask` (2 llamadas independientes; si la 2ª fallaba
a mitad de camino la tarea quedaba sin ninguna asignación) por
`FirestoreRepository.replaceAssignments`, que invierte el orden — crea las
asignaciones nuevas primero y solo borra las antiguas si esa creación no
lanzó. Sigue sin ser atómico de extremo a extremo (mismo motivo que el resto
del documento: sin acceso a `:commit`/emulador Firestore para verificar un
payload transaccional), pero el peor caso pasa de "tarea sin ninguna
asignación" a "asignaciones antiguas + nuevas duplicadas" (recuperable
reeditando y guardando).

## 5. 10 pantallas sin ScreenModel propio — APLICADO (10/10)

Las 10 pantallas que el encargo listaba (`HouseholdScreen`, `TaskDetailScreen`,
`EditTaskScreen`, `StatsScreen`, `RankingScreen`, `RewardListScreen`,
`CreateRewardScreen`, `WelcomeScreen`, `HomeScreen`, `ProfileScreen`) ya no
inyectan `FirestoreRepository`/`HouseholdStore`/`SettingsStore` directamente.
Se hizo pantalla a pantalla con compilación intermedia, de menor a mayor
huella real (que no siempre coincidía con el tamaño del archivo — p. ej.
`EditTaskScreen` tiene 971 líneas pero solo 1 llamada a red; se dejó su
enorme estado de formulario tal cual, es ortogonal al problema):

- **`ProfileScreen`**: `HouseholdStore` estaba inyectado y sin usar — eliminado.
- **`CreateRewardScreen`**: `repo.getLocalId()` movido dentro de
  `MemberScreenModel.createReward` (que ahora resuelve `createdBy` por sí sola).
- **`RankingScreen`**: no tenía ScreenModel. En vez de crear uno nuevo
  redundante, `RankingBody` ahora recibe el `MemberScreenModel` que ya crea
  `ExploreScreen` (mismo dato que `RewardsBody` ya usaba) — reutiliza
  `loadMembers`/`uiState` en vez de duplicar la carga.
- **`RewardListScreen`**: usaba `MemberScreenModel` parcialmente; se movieron
  `getLocalId()`/`getHousehold()` (isOwner) al wrapper
  `MemberScreenModel.isHouseholdOwner`/`localId` (ver punto 3).
- **`HouseholdScreen`**: `resolveCurrentMember`/`getLocalId`/`appreciationRemaining`
  movidos a wrappers en `HouseholdScreenModel` (uno de ellos, `getLocalId`, ya
  existía y no se había usado aquí).
- **`WelcomeScreen`**: no tenía ScreenModel. Reutiliza `HomeScreenModel`
  (mismo `reconcileHouseholds()` que ya usa `HomeScreen`), con un wrapper
  nuevo `getSavedHouseholds()` para el valor inicial.
- **`StatsScreen`**: no tenía ScreenModel — se creó `StatsScreenModel.kt`
  nuevo, moviendo `MemberStatsData`/`DayCount`/`DayPoints`/`TagCount` y la
  función pura `computeStats` desde `screens/StatsScreen.kt` a
  `ui/models/StatsScreenModel.kt` (antes vivían en la capa de pantalla pese a
  ser lógica de dominio sin dependencia de Compose).
- **`HomeScreen`**: `HouseholdStore` inyectado y sin usar — eliminado. Las 2
  lecturas + 3 escrituras de `SettingsStore` (prompt de Google) se movieron a
  `HomeScreenModel.shouldShowGooglePrompt()`/`markGooglePromptSeen()`.
- **`TaskDetailScreen`**: `getHousehold`+`getLocalId` (isOwner) movidos a
  `TaskScreenModel.isHouseholdOwner` (mismo wrapper que el punto 3); las 3
  llamadas a `SettingsStore` (Calendar) movidas a
  `TaskScreenModel.hasGoogleLinked`/`isCalendarSyncEnabled`/`setCalendarSyncEnabled`.
  `GoogleAuthManager` se deja inyectado directo (no está en la lista del
  encargo — es un singleton de sesión, no un repo/store por-hogar).
- **`EditTaskScreen`**: única llamada real (`getAssignments` para precargar
  asignaciones al abrir el formulario) movida a `TaskScreenModel.getAssignments`.

**Hallazgo no documentado en la revisión, encontrado al implementar**:
`koinScreenModel<T>()` (Voyager+Koin) es una función que solo puede llamarse
desde dentro de `Screen.Content()` (o algo con ese receptor) — los
composables "Body" reutilizables sin `Screen` propio (`StatsBody`,
`RankingBody`, `RewardsBody`) no pueden invocarlo directamente. `RewardsBody`
ya lo resolvía recibiendo el `MemberScreenModel` como parámetro desde
`ExploreScreen`; se aplicó el mismo patrón a `RankingBody` (recibe
`MemberScreenModel`) y `StatsBody` (recibe el nuevo `StatsScreenModel`), en
vez de crear el ScreenModel dentro de cada "Body".

Archivos nuevos: `ui/models/StatsScreenModel.kt`. Archivos de Koin:
`di/AppModule.kt` (registro de `StatsScreenModel`, nuevo parámetro
`settingsStore` en `HomeScreenModel`).

## 6. `FirestoreRepository` god object (~2733 líneas, 66 funciones públicas) — NO APLICADO, plan de fases

### Por qué no se aplicó en esta pasada

Es, con diferencia, el refactor de mayor riesgo de la lista. Dividir un
archivo de ~2700 líneas que concentra TODA la lógica de red de la app
(incluyendo el fix crítico de puntos de la revisión anterior) sin poder
levantar la app contra un emulador/dispositivo real para hacer QA manual de
cada dominio migrado es exactamente el escenario que el encargo pide evitar
("si ves que no puedes hacerlo sin romper la capa de puntos, déjalo
documentado"). Migrar ~66 funciones públicas y re-cablear Koin + las llamadas
desde 7 ScreenModels es un cambio de altísima superficie con cero red de
seguridad más allá de la compilación y los tests unitarios existentes (que no
cubren la integración HTTP real).

### Plan de fases propuesto

1. **Extraer `FirestoreClient`** (cliente HTTP + auth + parseo base): mover
   `HttpClient`, `ensureAuth()`/`tryAuthOrApiKey()`/`withAuth()`,
   `extractDocId`, `updateMaskFieldPaths`, `errorParsingJson`,
   `OPTIMISTIC_WRITE_MAX_RETRIES` y el manejo de `FirestoreException` a una
   clase nueva, inyectada por composición (no herencia) en los repos de
   dominio. Esta fase es la de menor riesgo porque no cambia ninguna función
   pública ni ningún call-site — solo reorganiza lo interno.
2. **Dividir por dominio, uno a la vez, empezando por el más aislado**:
   orden sugerido `NotificationRepository` (sin dependencias de puntos) →
   `RewardsRepository` → `MemberRepository` (contiene `addMemberPoints`,
   núcleo del bug ya corregido — máxima cautela aquí) → `TaskRepository` →
   `HouseholdRepository`. Cada fase: mover las funciones de ese dominio,
   mantener la firma pública idéntica, actualizar Koin, compilar + tests,
   y — critico — probar manualmente en un dispositivo/emulador real el flujo
   de puntos antes de pasar al siguiente dominio.
3. **Mantener `FirestoreRepository` como facade temporal** durante la
   migración (delega en los repos nuevos) para no tener que tocar los 7
   ScreenModels en el mismo paso que se mueve el código — separa "mover
   lógica" de "recablear consumidores" en commits distintos, cada uno
   verificable por separado.
4. Solo cuando los 5 repos de dominio estén migrados y probados, decidir si
   merece la pena eliminar la facade y actualizar los ScreenModels para
   inyectar cada repo de dominio directamente (beneficio: DI más granular;
   coste: vuelve a tocar los 7 ScreenModels).

### Coste/beneficio

Coste: alto (~2700 líneas a mover, varias sesiones de trabajo con QA manual
entre fases). Beneficio: alto — un archivo de este tamaño es exactamente el
tipo de sitio donde un bug como el "hallazgo destacado" de la revisión
anterior (puntos que nunca llegaban a `totalPoints`) puede esconderse sin que
nadie lo note al revisar el código.

## 7. Sin paginación en ninguna colección — NO APLICADO, documentado

`StructuredQuery`/`RunQueryRequest` siguen definidos en `network/FirestoreDtos.kt`
(líneas 77-82+) y sin ningún uso — confirmado de nuevo en esta pasada, código
muerto que demuestra que la paginación nunca se implementó. `getAllAssignments`
sigue lanzando una petición HTTP en paralelo por tarea sin límite de
concurrencia.

No se aplicó porque cambia el contrato de varias funciones `get*` (pasarían
de devolver `List<T>` a algo paginado — `Flow<PagingData<T>>`, un cursor
explícito, o como mínimo un parámetro `pageSize`/`pageToken`), lo que obliga a
tocar cada pantalla que las consume (listas de tareas, miembros, historial) y
verificar visualmente que el scroll/paginación funciona — no verificable sin
poder ejecutar la app. Además, como señala la propia revisión, el beneficio
es bajo hoy (hogares reales son pequeños) y alto solo a medio plazo.

Plan si se decide abordar: (1) empezar por `getTaskHistory`/`getAllAssignments`
(las colecciones que más crecen con el tiempo, a diferencia de `tasks`/`members`
que tienen un techo natural bajo), (2) usar `RunQueryRequest` con `limit` +
`orderBy` + el cursor `startAt` que ya modela `StructuredQuery`, (3) exponer
un parámetro opcional `pageSize: Int? = null` en las funciones afectadas para
no romper a los call-sites existentes que quieren "todo" (compatibilidad),
(4) migrar las pantallas de lista a paginación real solo después, con QA
visual del scroll infinito.

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   # BUILD SUCCESSFUL
./gradlew :composeApp:jvmTest --console=plain                     # BUILD SUCCESSFUL
```

Verificado en verde tras cada punto aplicado (1, 2, 3, 4, y tras cada pantalla
del punto 5), no solo al final. No se compiló iOS (sin toolchain de Xcode en
este entorno); los cambios de esta pasada son commonMain puro.

## Riesgos nuevos introducidos y mitigación

- **Memoización de `resolveCurrentMember`** (punto 3): si en el futuro se
  añade una forma de que el "miembro actual" cambie sin pasar por
  `leaveHousehold`/`deleteHousehold` (p. ej. un admin expulsa a otro
  miembro), la caché en memoria podría quedar obsoleta hasta reiniciar la
  app. Mitigación: el caso principal (el propio usuario deja el hogar) ya
  invalida; si se añade "expulsar a otro miembro" habrá que invalidar
  también ahí.
- **`replaceAssignments`** (punto 4): el peor caso posible pasó de "tarea sin
  asignaciones" a "asignaciones duplicadas" ante un fallo de red a mitad de
  camino — sigue siendo un estado incorrecto, pero recuperable por el
  usuario (reeditar y guardar), documentado en
  `docs/atomicidad-commit-pendiente.md`.
- **`StatsScreenModel`** nuevo: `computeStats` se movió tal cual (sin
  cambios de lógica) desde `screens/StatsScreen.kt`; riesgo bajo, es
  refactor puro de ubicación.
- **`RankingBody`/`StatsBody` ahora dependen de recibir su ScreenModel como
  parámetro** en vez de auto-inyectarse: si en el futuro se reutilizan estos
  composables fuera de `ExploreScreen` sin pasar el parámetro, es un error
  de compilación (no un fallo silencioso en runtime) — riesgo bajo por diseño.

## Veredicto final por subsistema (tras esta pasada)

| Subsistema | Veredicto antes | Veredicto ahora |
|---|---|---|
| Capas UI ↔ dominio ↔ red | En riesgo (10/23 pantallas se saltaban el ScreenModel) | **Aceptable** — las 10 pantallas listadas ya respetan la capa; quedan otras pantallas fuera de la lista del encargo sin auditar exhaustivamente |
| `FirestoreRepository` | En riesgo (god object) | **En riesgo** — sin cambios de fondo, documentado plan de fases (punto 6) |
| DI / Koin | Sano | **Sano** — sin cambios de fondo, 2 nuevas entradas (`StatsScreenModel`, `HomeScreenModel` con `settingsStore`) siguen el mismo patrón |
| Gestión de estado | Aceptable | **Sano** — single source of truth para miembro actual (punto 3) y caché invalidada tras escrituras (punto 2), los 2 matices que faltaban |
| Seguridad | Crítico (`firestore.rules` sin desplegar) | **Crítico** — sin cambios, fuera de alcance de este encargo (acción manual del usuario) |
| Escalabilidad | En riesgo (sin paginación) | **En riesgo** — sin cambios de fondo, documentado plan de fases (punto 7) |
