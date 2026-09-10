# Auditoría general — sin foco concreto (2026-09-11)

Auditoría integral NUEVA de Task Hub, sin foco de producto concreto: código,
tests, docs y configuración completos, buscando SOLO hallazgos que no
estuvieran ya documentados en las 4 rondas anteriores (`docs/review-panel-expertos-2026-09-10.md`,
`docs/auditoria-completa-2026-09-06.md`, `docs/auditoria-gates-roles-2026-09-06.md`,
`docs/atomicidad-commit-pendiente.md`).

Método: 4 subagentes en paralelo, cada uno cubriendo una categoría amplia
(seguridad/privacidad, correctitud/offline-sync, accesibilidad/UI/Material3,
rendimiento/tests/arquitectura), con la lista completa de hallazgos ya
conocidos de las 4 rondas anteriores incluida en su encargo para no repetirlos.
Cada hallazgo se verificó después contra el código real (archivo:línea) antes
de decidir si aplicarlo. Al arrancar esta ronda, el árbol de trabajo ya tenía
sin commitear la ronda v7 completa (`docs/review-panel-expertos-2026-09-10.md`)
— se ha dejado intacta y sumado a los cambios de esta ronda.

**Nota de proceso**: igual que en la ronda v7, un mecanismo de checkpoint
automático del entorno (ajeno a este encargo) commiteó parte del trabajo en
dos commits `wip: checkpoint ...` (`ffacab0`, `ffe5c9c`) antes de que esta
ronda terminara — no fueron `git commit` explícitos del coordinador. El resto
de cambios de esta ronda (los aplicados después de esos checkpoints) se
commitea al final de este informe con el mensaje solicitado.

---

## Hallazgos NUEVOS — aplicados

### Correctitud

- **CRÍTICO → [APLICADO] `HomeScreenModel.previewFilter` usaba una definición
  de "pendiente" distinta e incompatible con `isPending`** — `ui/models/HomeScreenModel.kt:154-155`
  filtraba solo por `lastCompletedDate == null || == 0L` ("nunca completada
  jamás"), ignorando por completo la recurrencia (`frequency`/`recurrenceDays`/
  `recurrenceDay`) que sí usa `isPending` (líneas 194-217, vía
  `RecurrenceRules.isDueToday`). Una tarea diaria completada ayer y pendiente
  de nuevo hoy aparecía correctamente en el dashboard agregado (`loadAllTasks`)
  pero NUNCA en `HouseholdTaskSection` (la previsualización por hogar de
  `HomeScreen`), que quedaba permanentemente vacía o casi vacía en hogares con
  tareas mayormente recurrentes ya usadas un tiempo — falsa sensación de "todo
  al día". Coincide con el hueco de test ya señalado en el panel anterior
  ("`HomeScreenModel.isPending` vs `previewFilter`: dos definiciones que
  pueden divergir"), pero esta ronda confirma que SÍ divergen, con escenario
  concreto. **Fix**: `previewFilter` ahora llama a `isPending` en vez de tener
  su propia condición (`ui/models/HomeScreenModel.kt`).

- **MENOR → [SOLO PROPUESTA] `RecurrenceRules.isDueOn`/`isDueToday`: una tarea
  semanal/mensual NUNCA completada, con varios días programados, "parpadea"
  entre pendiente y no-pendiente en los días intermedios sin marcarse nunca
  como atrasada** — `network/RecurrenceRules.kt:114-123` (rama `weekly`,
  `lastCompletedLocalDate == null`) y `:130-131` (rama `monthly` equivalente):
  cuando la tarea nunca se completó, solo se considera "debida" el día EXACTO
  programado (`dow in recurrenceDays`), a diferencia de la rama "ya completada
  alguna vez" que sí abre una ventana de "modo atrasado" hasta la próxima
  compleción (`mostRecentTarget`/`lastCompletedLocalDate < mostRecentTarget`).
  Escenario: tarea semanal con `recurrenceDays=[Lunes, Miércoles]` creada el
  domingo; si el usuario no la marca el lunes, el martes `isDueToday` devuelve
  `false` (desaparece de `TaskListScreen`/dashboard/calendario) y solo
  reaparece el miércoles, sin ningún indicador de que se perdió el lunes.
  **No aplicado**: existe un test explícito (`RecurrenceRulesTest.kt:253-259`,
  `isDueToday_weeklyMultipleDays_todayIsNotAnyOfThem_neverCompleted_isNotDue`)
  que documenta y verifica el comportamiento ACTUAL como intencional — cambiar
  la rama "nunca completada" para que se comporte como la rama "ya completada"
  contradice ese test explícito y requeriría además saber la fecha de creación
  de la tarea (no disponible hoy en la firma de `isDueOn`) para no reabrir el
  caso ya protegido "no marcar atrasada el mismo día que se crea" (KDoc líneas
  64-67). Es una decisión de producto (¿debe una tarea nunca completada
  quedarse "pegada" como pendiente tras el primer día perdido, o parpadear
  como hoy?), no un fix de una línea.

- **MENOR → [NO APLICADO, código muerto hoy] `RewardsRepository.getRewardRedemptions`
  es un noveno sitio con el patrón `orDefault`/swallow-to-empty**, no incluido
  en el catálogo de 8 ya documentado (`network/RewardsRepository.kt:97`).
  Verificado que ningún `ScreenModel` de `ui/` lo invoca hoy — trampa latente
  sin impacto activo, se deja documentado por si se usa en el futuro (p. ej.
  histórico de canjes en `StatsScreen`).

### Seguridad / privacidad

- **IMPORTANTE → [APLICADO] La API key de Firebase podía acabar mostrándose
  en un Snackbar de usuario y en Logcat de producción** — `FirestoreClient.kt`
  construye 3 URLs con la clave en la query string (`accounts:signUp?key=`,
  `securetoken...token?key=`, `accounts:delete?key=`). Ktor mete la URL
  COMPLETA (con la clave) en el mensaje de sus excepciones de
  timeout/conexión, lanzadas antes de llegar al `HttpResponseValidator` que sí
  sanea los errores de respuesta HTTP. Ninguno de los 3 call-sites capturaba
  esa excepción, así que subía intacta hasta el patrón `e.message ?: fallback`
  usado en decenas de `ScreenModel` para poblar el Snackbar de error, y hasta
  `NotificationPollWorker.kt:97` (`Log.w`). Escenario: timeout de red durante
  el alta anónima o el refresco de sesión (p. ej. app recién instalada con
  conexión inestable) mostraba la API key directamente en la UI. **Fix**: los
  3 call-sites (`ensureAuth` paso 3, `refreshFirebaseToken`,
  `deleteFirebaseAccount`) capturan la excepción y la reenvían con la clave
  sustituida por `***` (`network/FirestoreClient.kt`, nuevo helper privado
  `redactApiKey`). Queda como **[SOLO PROPUESTA]** de menor prioridad el
  cuarto sitio (`tryAuthOrApiKey`, fallback de LECTURA que añade la key como
  query param a peticiones Firestore arbitrarias hechas fuera de esta clase):
  cerrarlo del todo tocaría el choke point HTTP compartido por todos los
  repos de dominio, blast radius mucho mayor para un caso que solo se dispara
  cuando `ensureAuth()` YA ha fallado (doble fallo, menos probable).

- **IMPORTANTE → [APLICADO] El fallback "primer miembro" de
  `resolveCurrentMemberUncached` podía atribuir la identidad de OTRO miembro
  real a un usuario sin coincidencia** — `network/MemberRepository.kt:290`
  (antes del fix): si ninguna identidad local del usuario coincidía con
  ningún miembro del hogar, se devolvía `members.first().id` sin condición
  — el primer miembro de la lista, sea quien sea, con o sin cuenta vinculada.
  Es la implementación real detrás del "single source of truth" que
  `HouseholdScreen.kt:151-160` documenta como ya corregido a nivel de UI, pero
  el mismo patrón peligroso seguía un nivel más abajo, en la función
  compartida por `TaskScreenModel`, `CalendarSyncManager`,
  `TaskCommentsScreenModel` y `HouseholdScreen`. Puede dispararse si la
  identidad local del usuario deja de coincidir con ningún `userId` de
  miembro (p. ej. el escenario ya documentado como CRÍTICO en
  `docs/auditoria-completa-2026-09-06.md` de `ownerId` sin limpiar tras
  abandonar sin sucesor) — el cliente atribuiría en silencio puntos, rachas o
  eventos de Calendar al miembro equivocado. **Fix**: el fallback ahora solo
  elige el primer miembro SIN cuenta vinculada (`userId == null`, el caso
  típico de perfil "child" del onboarding); si todos los miembros ya están
  reclamados por otra identidad real, se cae al paso 3 (crear un miembro "Yo"
  nuevo) en vez de adivinar (`network/MemberRepository.kt`).

- **IMPORTANTE → [APLICADO] El rollback de `donatePoints` podía fallar en
  silencio y el mensaje de error garantizaba algo que no siempre era
  cierto** — `network/MemberRepository.kt:699-704` (antes del fix): si el
  crédito al receptor fallaba, se intentaba revertir el débito al donante,
  pero ese intento de reversión estaba envuelto en `catch (_: Exception) {}`
  que tragaba cualquier fallo de la propia reversión sin distinguirlo. Pese a
  ello, siempre se devolvía `TRANSFER_FAILED`, cuyo texto fijo
  (`"transfer_error_failed"`) afirma *"Tus puntos no se han visto
  afectados"* — falso en ese escenario de doble fallo (p. ej. timeout de red
  transitorio que tumba tanto el crédito como su reversión). **Fix**: nuevo
  `DonateErrorReason.ROLLBACK_FAILED`, distinguido del `TRANSFER_FAILED`
  normal, mapeado a una nueva clave `transfer_error_rollback_failed`
  (ES/EN) que sí avisa de que el saldo puede haberse visto afectado y a
  contactar con el administrador (`network/MemberRepository.kt`,
  `ui/models/MemberScreenModel.kt`, `ui/i18n/AppStrings.kt`).

- **MENOR → [APLICADO] `TaskHubFirebaseMessagingService` logueaba el payload
  `data` completo de FCM en producción** — `TaskHubFirebaseMessagingService.kt:74`,
  inconsistente con el cuidado ya aplicado dos líneas antes al token (se trunca
  a su longitud, con comentario explícito de que no hay regla R8 que elimine
  `Log` en release). Hoy sin backend que envíe payload `data` dirigido, pero
  trampa latente para cuando se implemente. **Fix**: se loguean solo las
  claves (`message.data.keys`), no los valores.

### Accesibilidad / Material3

- **IMPORTANTE → [APLICADO] Tarjeta de invitación de `HouseholdScreen` sin
  rol ni descripción semántica** — `ui/screens/HouseholdScreen.kt:450-509`,
  el `Card` clicable que abre el diálogo del QR no tenía `role = Role.Button`
  ni `contentDescription` (el archivo ni siquiera importaba
  `androidx.compose.ui.semantics.Role`, a diferencia de
  `TaskListScreen`/`CalendarScreen`). TalkBack la leía como texto suelto sin
  indicar que era pulsable. **Fix**: `clickable(role = Role.Button)` +
  `semantics(mergeDescendants = true) { contentDescription = ... }` con el
  nombre del hogar y el código de invitación (nueva clave i18n
  `household_invite_card_description`, ES/EN).

- **MENOR → [APLICADO] `HouseholdProfileCard` sin rol en `ProfileScreen`** —
  `ui/screens/ProfileScreen.kt:197`, el `Card` clicable (cuando `onNavigate`
  no es null) carecía de `role = Role.Button`. **Fix**: añadido.

- **MENOR → [APLICADO] Selector de icono (Card toggle) sin rol/estado en
  `CreateRewardScreen`** — `ui/screens/CreateRewardScreen.kt:109-140`, el
  `Card` que alterna `showEmojiPicker` no exponía `role = Role.Button` ni el
  estado expandido/colapsado. **Fix**: `clickable(role = Role.Button)` +
  `stateDescription` reutilizando las claves ya existentes `state_expanded`/
  `state_collapsed` (mismo patrón ya usado en `ExpandableSectionHeader.kt`).

- **IMPORTANTE → [APLICADO] Selección de emoji no transmitía el estado
  "seleccionado" a lectores de pantalla, en `CreateRewardScreen` y
  `EditProfileScreen`** — cada emoji de la cuadrícula solo se distinguía por
  color de fondo/borde visual (`if (selectedIcon == emoji) ...`), sin
  `Modifier.semantics { selected = ... }`. Un usuario de TalkBack no podía
  saber cuál estaba activo sin recorrer visualmente la cuadrícula. **Fix**:
  `selected = selectedIcon == emoji` (y equivalente en `EditProfileScreen`)
  añadido al `semantics{}` ya existente de cada `Surface`, más
  `role = Role.Button` en su `clickable` (`ui/screens/CreateRewardScreen.kt`,
  `ui/screens/EditProfileScreen.kt`).

- **MENOR → [APLICADO] Imports muertos en `RankingScreen.kt`** —
  `androidx.compose.foundation.background` y
  `androidx.compose.foundation.shape.CircleShape` (líneas 9 y 13) no se usaban
  en ningún punto del archivo. **Fix**: eliminados.

- **MENOR → [SOLO PROPUESTA] Filas `Checkbox`/`RadioButton` envueltas en
  `clickable` genérico sin rol dedicado** — `ui/screens/TaskDetailScreen.kt:870-888`
  (diálogo "quién lo hizo"), `ui/screens/CreateTaskScreen.kt:707-733` y
  `ui/screens/EditTaskScreen.kt:711-734` (lista de miembros asignables): el
  `Row` exterior usa `.clickable {}` sin `role = Role.RadioButton`/
  `Role.Checkbox`. No aplicado en esta ronda por tocar 3 pantallas con
  interacción de selección múltiple/única — riesgo de romper el
  `mergeDescendants` ya existente sin poder verificar con lector de pantalla
  real.

- **IMPORTANTE → [SOLO PROPUESTA] Formularios grandes sin
  `imeAction`/`KeyboardActions`, inconsistente con el resto de la app** —
  `CreateTaskScreen.kt`/`EditTaskScreen.kt` (todos sus `OutlinedTextField`,
  hasta 8-9 campos) y `CreateRewardScreen.kt` (título/descripción/coste) no
  encadenan `ImeAction.Next`/`Done`, a diferencia de
  `CreateHouseholdScreen.kt`/`CreateProfileScreen.kt`/`EditProfileScreen.kt`/
  `JoinHouseholdScreen.kt`, que sí lo hacen. Afecta a navegación por teclado
  físico (target JVM desktop) y a la comodidad táctil en los formularios más
  largos de la app. No aplicado por blast radius (3 pantallas, muchos
  campos) sin poder probar el flujo de foco completo en esta ronda.

### Rendimiento / arquitectura

- **IMPORTANTE → [APLICADO] `HomeScreenModel.buildWidgetText` decidía "¿es el
  espacio Personal?" comparando el NOMBRE del hogar con el literal
  `"Personal"`, en vez de usar el flag `isPersonal`** —
  `ui/models/HomeScreenModel.kt:242` (antes del fix): `if (householdName !=
  null && householdName != "Personal")`, ignorando `SavedHousehold.isPersonal`
  que el propio parámetro ya trae (y que sí usan `CalendarSyncManager`/
  `PersonalSpaceScreen`). `CreateHouseholdScreen` no restringe en absoluto el
  nombre de un hogar compartido nuevo — un hogar COMPARTIDO llamado
  literalmente "Personal" (por accidente o a propósito) perdía el prefijo
  `[Personal]` en el widget de Android, mezclando visualmente sus tareas con
  las del espacio Personal real, sin excepción ni log. **Fix**: la
  comparación ahora usa `household.isPersonal` (`ui/models/HomeScreenModel.kt`).

- **MENOR → [APLICADO] `Regex` recompilada en cada recomposición del
  formulario de crear/editar tarea** — `ui/screens/CreateTaskScreen.kt:1104-1117`
  (`isValidDateFormat`/`isValidTimeFormat`) construían un `Regex(...)` nuevo
  en cada invocación; al llamarse desde el cuerpo del composable principal
  (condición `enabled` del botón Crear/Guardar, `isError` del campo de hora),
  cualquier tecleo en CUALQUIER campo del formulario recompilaba ambas regex
  desde cero. **Fix**: hoisted a constantes `private val` a nivel de archivo
  (`DATE_FORMAT_REGEX`/`TIME_FORMAT_REGEX`); `EditTaskScreen.kt` reutiliza las
  mismas funciones `internal`, se beneficia sin cambios propios.

- **MENOR → [SOLO PROPUESTA] `MainActivity.consumeDeepLink` instancia
  `HouseholdStore(Settings())` al vuelo en vez de reutilizar la instancia de
  Koin** — `MainActivity.kt:81-82` (añadido en el fix de seguridad de deep
  links de la ronda v7): crea un `Settings()`/`HouseholdStore` nuevos en cada
  `onCreate`/`onNewIntent`, en vez del que el resto de la app obtiene vía
  Koin. Impacto real bajo (se ejecuta solo al abrir la app o tocar una
  notificación, no en un bucle); no aplicado por requerir extraer Koin del
  árbol de Compose a un punto anterior de `onCreate`, con riesgo de
  regresión en el flujo de deep link recién corregido, sin poder probarlo en
  un dispositivo real en esta ronda.

- **MENOR → [SOLO PROPUESTA] Notificaciones de chat y borrado de asignaciones
  se envían/borran en serie en vez de en paralelo** —
  `network/HouseholdRepository.kt:402-420` (`sendMessage`) y
  `network/TaskRepository.kt:473-483` (`deleteAssignmentDocs`) recorren listas
  con `forEach` + `await` secuencial por iteración (aislamiento de fallos por
  `try/catch` correcto, eso no es el problema), a diferencia de
  `HomeScreenModel.loadAllTasks`, que sí usa `async`+`awaitAll`. Impacto
  marginal con 4-6 miembros; no aplicado por ser optimización sin bug
  asociado, se deja como candidato futuro si algún hogar crece mucho.

- Confirmado sin cambios sustanciales: god objects `FirestoreRepository.kt`
  (2034 líneas, +11 desde la ronda v7) y `TaskScreenModel.kt` (1270, +2) —
  crecimiento marginal consistente con documentación, no lógica nueva.

### Cobertura de tests — 2 de los 5 huecos "ALTA prioridad" cerrados

El panel v7 (`docs/review-panel-expertos-2026-09-10.md`) dejó un TOP-5 de
huecos de test priorizados. Esta ronda cierra los 2 más triviales/de mayor
retorno:

- **[APLICADO]** `StatsScreenModel.computeStats` — pasó de `private` a
  `internal` (`ui/models/StatsScreenModel.kt`) específicamente para poder
  testearla; nuevo `StatsScreenModelTest.kt` con 3 tests de regresión sobre el
  doble conteo (el mismo bug que estuvo en producción varios días sin
  detectarse, ahora con cobertura directa).
- **[APLICADO]** `NotificationText.title`/`message` — nuevo
  `NotificationTextTest.kt`, 5 tests (traducción por `titleKey`/`messageKey`,
  fallback a texto legado, interpolación de `taskTitle`).
- **[SOLO PROPUESTA, sin cambios]** Los otros 3 del TOP-5 siguen abiertos:
  selección de `newOnes` en `NotificationPollWorker` (requiere extraer la
  lógica pura del `doWork()` de un Worker de Android, no trivial desde
  `commonTest`), y `MemberRepository.resolveCurrentMemberUncached` (I/O,
  aunque su rama más peligrosa se corrigió esta ronda — ver hallazgo de
  seguridad arriba). `HomeScreenModel.isPending`/`previewFilter` ya no es un
  hueco de "definiciones que podrían divergir": esta ronda demostró que SÍ
  divergían y lo corrigió (ver Correctitud), pero sigue sin test de
  regresión propio — candidato natural para la próxima ronda.

---

## Verificación

- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` →
  **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:jvmTest --console=plain` → **BUILD SUCCESSFUL**,
  **181 tests, 0 fallos** (173 heredados + 8 nuevos: 3 de `computeStats`, 5 de
  `NotificationText`).

## Archivos modificados en esta ronda

Ya commiteados por el checkpoint automático del entorno (`ffacab0`, `ffe5c9c`
— ver nota de proceso al principio): toda la ronda v7 (`docs/review-panel-expertos-2026-09-10.md`)
más los primeros fixes de esta ronda (`StatsScreenModel.kt` → `internal`,
`StatsScreenModelTest.kt`, `NotificationTextTest.kt`,
`HomeScreenModel.kt`/`previewFilter`, `HouseholdScreen.kt`/`ProfileScreen.kt`
roles de accesibilidad).

Commiteados al final de esta ronda con el mensaje solicitado:
- `composeApp/src/androidMain/kotlin/org/taskhub/TaskHubFirebaseMessagingService.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreClient.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HomeScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/MemberScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateRewardScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditProfileScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/RankingScreen.kt`
- `docs/review-panel-expertos-2026-09-11.md` (este informe)
