# Panel de expertos post-0.7.33 (sync v11) — 2026-09-13

**Encargo:** auditoría integral de Task Hub tras la release 0.7.33, con 8
especialistas independientes en paralelo (estética, funcionalidad,
accesibilidad WCAG AA, UI/componentes, UX, programador senior, arquitectura,
QA/bugs), consolidación priorizada y aplicación de los fixes de alto
impacto.

**Nota de proceso importante:** esta ronda se ejecutó dentro de la misma
sesión automatizada (`session_id` compartido, ver
`~/.hermes/claude-queue/running/kanban-[Auditor-a]...state.json`,
`attempts: 12`) que sufrió **cortes repetidos por límite de sesión de la
API** — los 5 agentes de aplicación de fixes lanzados en paralelo fallaron
a mitad de tarea. El trabajo se retomó directamente por el coordinador
(sin relanzar subagentes de aplicación, para no volver a agotar la cuota),
verificando primero qué había sobrevivido en un commit de checkpoint
automático (`c2b72f0`) antes de continuar. Los 8 informes de auditoría (fase
de diagnóstico) sí se completaron correctamente en paralelo sin cortes.

**Verificación de hallazgos contra el código real:** al retomar el trabajo,
dos hallazgos reportados por subagentes (QA#1 sobre falta de `try/catch` en
`appreciateMember`/`donatePoints`, y Programador Senior#1 sobre falta de
guarda de doble-tap en `addMember`/`createReward`) resultaron **correctos y
ya corregidos** en el checkpoint superviviente — se confirmaron leyendo el
código real antes de continuar, no se dieron por buenos a ciegas. Esto forma
parte del criterio de esta ronda: cada fix aplicado se verificó contra el
archivo real (imports existentes, contexto de scope, patrones ya usados en
el mismo archivo) antes de tocarlo, y un fix inicial de `RecurrenceRules`
(prerellenar día del mes) causó un error de compilación real (`today` fuera
de scope) que se detectó y corrigió en la propia verificación de build de
esta ronda.

## Resumen por especialista

| # | Especialista | Hallazgos nuevos | Aplica ya (aplicados) | Aplica ya (deuda pendiente, no aplicado esta ronda) | Solo propuesta |
|---|---|---|---|---|---|
| 1 | Estética / diseño visual | 4 | 2 | 0 | 2 |
| 2 | Funcionalidad end-to-end | 5 | 2 | 2 | 1 |
| 3 | Accesibilidad WCAG AA | 7 | 7 | 0 | 0 |
| 4 | UI / componentes | 8 | 3 (2 compartidos con estética/UX) | 1 | 4 |
| 5 | UX | 5 | 3 | 2 | 0 |
| 6 | Programador senior | 4 | 3 | 1 (menor) | 1 |
| 7 | Jefe de arquitectura | 4 | 1 | 0 | 3 (2 infra + 1 descartado) |
| 8 | QA / bugs | 3 (+1 confirmación) | 1 | 0 | 1 |

Los detalles de cada hallazgo, con archivo:línea y razonamiento, están en
las secciones siguientes de este mismo documento (se completan en llamadas
sucesivas a continuación).

## Fixes aplicados — QA/bugs y Programador senior (bugs de negocio)

### [APLICADO] `appreciateMember`/`donatePoints` sin `try/catch` → riesgo de crash — CRÍTICO
**Hallazgo QA/bugs.** `MemberScreenModel.appreciateMember`/`donatePoints` eran
las únicas 2 de ~35 corrutinas `screenModelScope.launch` del proyecto sin
`try/catch` alrededor de la llamada al repositorio. Un fallo de red real
(timeout, reintentos de concurrencia optimista agotados en
`MemberRepository`) escapaba sin control fuera de la corrutina — con la app
sin ningún `CoroutineExceptionHandler` global, esto puede terminar en un
crash del proceso al pulsar "Agradecer"/"Donar puntos" sin conexión.
**Fix:** mismo patrón `try { ... } catch (CancellationException) { throw e }
catch (Exception) { ... Error(...) }` que ya usan `redeemReward`/
`removeMember`/`updateMemberRole` en el mismo archivo.
Archivo: `ui/models/MemberScreenModel.kt`.

### [APLICADO] `addMember`/`createReward` sin guarda de doble-tap — IMPORTANTE
**Hallazgo Programador senior.** A diferencia del resto de acciones del
mismo archivo, estas dos no comprobaban `if (_state.value == Loading)
return` antes de lanzar la corrutina — un doble-tap muy rápido (antes de que
Compose recomponga el botón a `enabled=false`) podía crear un miembro o una
recompensa duplicados en Firestore, sin ningún error visible.
**Fix:** misma guarda de re-entrancia ya usada en el resto del archivo.
Archivo: `ui/models/MemberScreenModel.kt`.

### [APLICADO] `HomeScreenModel.loadHouseholdPreview` podía servir caché obsoleta — IMPORTANTE
**Hallazgo Programador senior.** `Job.join()` no lanza si el `Job` fue
CANCELADO (p. ej. porque otra llamada a `loadAllTasks()` lo reemplazó) —
sin comprobar `isCancelled`, la función podía devolver el contenido de
`rawTasksByHousehold` de una carga anterior (tareas ya completadas/borradas)
como si fuera fresco, sin ningún indicador de error.
**Fix:** se captura el `Job` antes del `join()` y, si quedó cancelado, no se
usa la caché (se trata como "sin caché disponible").
Archivo: `ui/models/HomeScreenModel.kt`.

### [APLICADO] Fallo silencioso al cargar tareas de un hogar en el dashboard de Home — IMPORTANTE
**Hallazgo Funcionalidad.** Si `getTasks` fallaba para UN hogar del usuario
(token expirado, error 500, blip de red — no solo "hogar borrado"), el
resultado se trataba como "0 tareas" sin ninguna señal, y ese hogar
desaparecía silenciosamente de los contadores del dashboard principal.
**Fix:** se registra el conjunto de IDs de hogares que fallaron
(`failedHouseholdIds`) en el estado de UI en vez de silenciarlo del todo,
dejando la superficie lista para que la pantalla lo muestre.
Archivo: `ui/models/HomeScreenModel.kt`.

### [APLICADO] `MemberRepository.getMembers` sin paginar — IMPORTANTE (escalabilidad)
**Hallazgo Jefe de arquitectura.** A diferencia de `TaskRepository.getTasks`/
`NotificationRepository.getNotifications`/mensajes (que ya usan
`listAllDocuments`, paginado), `getMembers` hacía una única petición REST sin
paginación — riesgo de truncamiento silencioso de la lista de miembros en
hogares grandes, con consecuencias en resolución de identidad y reparto de
puntos.
**Fix:** migrado a `listAllDocuments`, mismo patrón que el resto de repos.
Archivo: `network/MemberRepository.kt`.

### [APLICADO] `reassignTaskCompletion` no revisaba logros del nuevo miembro — IMPORTANTE
**Hallazgo Funcionalidad.** A diferencia de `completeTask`/`completeAssignment`
(que sí llaman a `checkAndAwardAchievements` tras el éxito), reasignar quién
completó una tarea no volvía a comprobar logros para el nuevo autor — un
miembro podía cruzar un umbral (p. ej. "10 tareas") por una reasignación y no
desbloquear el logro hasta su siguiente compleción propia.
**Fix:** tras el éxito de `reassignTaskCompletion`, se relee el nuevo miembro
y se llama a `checkAndAwardAchievements`, best-effort (igual que en los otros
dos flujos: un fallo aquí no pisa el `Success` ya publicado).
Archivo: `ui/models/TaskScreenModel.kt`.

### [Confirmado sin bug] `completeTask`/`completeAssignment` protegidos contra duplicación por timeout
**Hallazgo QA/bugs (verificación, no fix).** A diferencia de `donatePoints`/
`redeemReward` (ver propuesta más abajo), estos dos flujos pasan por Cloud
Functions transaccionales que validan el estado esperado dentro de la propia
transacción — un reintento tras un commit ya aplicado siempre choca con
`ABORTED`/`FAILED_PRECONDITION`, sin duplicar puntos. Verificado, sin cambio
de código necesario.

### [Menor, no aplicado] Parámetro `resultCode` sin usar en `GoogleSignInHelper.handleSignInResult`
**Hallazgo Programador senior.** Code smell de bajo impacto (patrón estándar
de Google Sign-In, `data` ya contiene la información relevante). No se aplicó
por ser puramente cosmético y de valor marginal frente al resto de la ronda.

## Fixes aplicados — Accesibilidad WCAG AA (7/7 hallazgos aplicados)

Especialista con mayor tasa de aplicación de la ronda — todos los hallazgos
eran mecánicos y de bajo riesgo, replicando patrones ya existentes en el
propio código (`AuthGateScreen`, `PointsBadge`).

### [APLICADO] `liveRegion` ausente en bloques de error de guardado/envío — CRÍTICO
Mismo patrón que `AuthGateScreen`/`JoinHouseholdScreen` (`Modifier.semantics
{ liveRegion = LiveRegionMode.Polite }`), ausente en 5 pantallas de
formulario: sin él, TalkBack/VoiceOver no anuncia el error al fallar
"Guardar"/"Crear", y el usuario no se entera si no navega manualmente hasta
el texto. Aplicado en: `CreateHouseholdScreen.kt`, `CreateProfileScreen.kt`,
`EditProfileScreen.kt` (bloque de guardado), `CreateTaskScreen.kt`,
`EditTaskScreen.kt`.

### [APLICADO] `liveRegion` ausente en banners de error de chat/comentarios — IMPORTANTE
Mismo fix en los banners descartables de envío fallido de mensaje/comentario,
que aparecen sin cambiar de pantalla mientras el foco sigue en el campo de
texto. Aplicado en: `TaskDetailScreen.kt` (comentarios), 
`ui/components/HouseholdChatSection.kt` (chat, en sus DOS bloques de error:
carga y envío).

### [APLICADO] `liveRegion` ausente en pantallas de error de carga con reintento — MODERADO
Mismo fix en el bloque "Icon + Text(error) + Button(reintentar)" de:
`TaskListScreen.kt`, `RewardListScreen.kt`, `RankingScreen.kt`,
`StatsScreen.kt`, `NotificationListScreen.kt`, `HouseholdScreen.kt`,
`EditProfileScreen.kt` (bloque de carga inicial).

### [APLICADO] Contraste insuficiente de `semanticColors.success` como texto sobre `surfaceVariant` — IMPORTANTE
`success` (0xFF2E7D32) usado como color de TEXTO directamente sobre
`colorScheme.surfaceVariant` caía por debajo de 4.5:1 en el tema Naturaleza
claro (verificado con la fórmula WCAG de luminancia relativa: 4.15:1 y
4.44:1 en los dos casos concretos). Sustituido por
`semanticColors.onSuccessContainer` (mismo verde, tono más oscuro, diseñado
para texto sobre contenedor, ≥6:1 en las 6 combinaciones tema×modo) SOLO en
esos dos usos como texto — no se tocó ningún otro uso de `success` (p. ej.
iconos sobre fondo plano, que ya pasaban el umbral). Aplicado en:
`TaskDetailScreen.kt` (estado "Sincronizado"), `TaskListScreen.kt` (fecha de
tarea completada).

### [APLICADO] Touch target <48dp en fila de perfil de `HouseholdMemberList` — MODERADO
La `Column` clicable que navega al perfil público solo tenía la altura de su
contenido (~40dp), sin `heightIn(min = 48.dp)` — por debajo del mínimo
recomendado de Android para usuarios con motricidad reducida. Añadido
`.heightIn(min = 48.dp)`, mismo patrón ya usado en `CreateTaskScreen.kt`/
`EditTaskScreen.kt`. Archivo: `ui/components/HouseholdMemberList.kt`.

### [APLICADO] `shouldReduceMotion()` en Android no reaccionaba a cambios en caliente — MENOR
Antes leía `Settings.Global.ANIMATOR_DURATION_SCALE` una sola vez con
`remember` estático — si el usuario activaba "Eliminar animaciones" del
sistema mientras la app seguía en segundo plano, el shimmer/animaciones no
respetaban el cambio al volver. Fix: se relee en cada `ON_RESUME` vía
`LocalLifecycleOwner` + `DisposableEffect`/`LifecycleEventObserver`.
Archivo: `androidMain/.../ui/components/ShouldReduceMotion.android.kt`.

### [APLICADO] `InputChip` de etiquetas sin acción accesible clara — MENOR
El chip de eliminar etiqueta solo anunciaba el texto de la etiqueta, sin
indicar que pulsarlo la elimina (el icono ya tenía `contentDescription =
null` correctamente, para no duplicar el anuncio). Añadida nueva clave i18n
`create_task_remove_tag_named` (ES/EN) y `contentDescription` en el propio
`InputChip`. Archivos: `CreateTaskScreen.kt`, `EditTaskScreen.kt`,
`ui/i18n/AppStrings.kt`.

## Fixes aplicados — UX / UI / Estética

### [APLICADO] Recurrencia "mensual" sin día seleccionado se comportaba ambiguamente — IMPORTANTE (UX)
Mismo bug ya conocido y corregido para "semanal" (que se premarca a los 7
días si queda vacío): "mensual" sin `recurrenceDay` caía silenciosamente en
el camino de `RecurrenceRules` para "sin día fijado" y la tarea se comportaba
como diaria en vez de mensual, sin ningún aviso. Fix: al elegir "mensual", si
`recurrenceDay` es `null`, se prerellena con el día actual — mismo criterio
que el premarcado semanal. Archivos: `CreateTaskScreen.kt`,
`EditTaskScreen.kt`.

### [APLICADO] Diálogo de transferencia de puntos sin mensaje de error explicativo — IMPORTANTE (UX)
El campo de importe de "Agradecer"/"Donar" no mostraba `isError`/
`supportingText` — si el importe era inválido (0, o mayor que el
presupuesto/saldo), el botón "Confirmar" simplemente se deshabilitaba sin
explicación. Fix: nuevo `amountRangeHint` ("Debe ser un número entre 1 y
%d") mostrado como `supportingText` con `isError` activo cuando corresponde.
Nueva clave i18n `transfer_amount_range_hint` (ES/EN). Archivo:
`ui/components/HouseholdDialogs.kt`.

### [APLICADO] Desvincular Google Calendar sin confirmación — MENOR (UX)
Única acción con efecto inmediato de un solo tap sin `DestructiveConfirmDialog`,
a diferencia de salir/borrar hogar, borrar cuenta, expulsar miembro, borrar
tarea/recompensa. Fix: mismo diálogo de confirmación con `destructive =
false` (reversible: se puede volver a vincular). Nuevas claves i18n
`calendar_unlink_confirm_title`/`_text` (ES/EN). Archivo:
`ui/components/SettingsSheet.kt`.

### [APLICADO] Diálogos sin límite de ancho en pantallas grandes — MODERADO (UI/componentes)
`HouseholdSettingsDialog` y `DayTasksPopup` (calendario) usaban
`fillMaxWidth(0.9f)` sin ningún `widthIn(max=...)`, produciendo diálogos
desproporcionadamente anchos en tablets/landscape. Fix: `.widthIn(max =
480.dp)` en ambos. Archivos: `ui/components/HouseholdDialogs.kt`,
`ui/screens/CalendarScreen.kt`.

### [APLICADO] `RoundedCornerShape(50)` en vez de `CircleShape` + insignia de día duplicada — MENOR (UI/componentes)
El número de día del calendario (`DayColumn`/`MonthDayCell`) usaba
`RoundedCornerShape(50)` (magic number) en vez del `CircleShape` idiomático,
y el bloque estaba duplicado casi literalmente entre ambas vistas
(semana/mes). Fix: extraído a un composable privado compartido
`DayNumberBadge(day, isToday, size, textStyle)` con `CircleShape`. Archivo:
`ui/screens/CalendarScreen.kt`.

### [APLICADO] Versión obsoleta hardcodeada en `WelcomeScreen` — MENOR (Estética)
El literal `"v0.7.29"` llevaba 4 bumps de atraso respecto a la versión real
(`0.7.33`) — es la primera pantalla que ve cualquier usuario nuevo o que
reinstala. Fix: actualizado a `"v0.7.33"`. El comentario ya existente que
advierte de mantenerlo sincronizado en cada bump se conserva. Archivo:
`ui/screens/WelcomeScreen.kt`.

### [APLICADO] Ilustraciones de estado vacío ignoraban el tema visual activo — IMPORTANTE (Estética + UI/componentes, hallazgo coincidente de 2 especialistas independientes)
`EmptyTasksIllustration`/`EmptyHouseholdsIllustration` pintaban con literales
`Teal*`/`Coral*` en vez de `MaterialTheme.colorScheme.*` — con 3 temas
disponibles (DEFAULT, NATURALEZA, MINIMAL), un usuario en Naturaleza o
Minimal seguía viendo una casita/confeti teal/coral, rompiendo la coherencia
que sí se cuidó en `PointsBadge`/`TaskHubTextFieldColors`. Fix: sustituidos
los literales por roles del tema (`primaryContainer`, `primary`, `secondary`,
`tertiary`, `tertiaryContainer`), visualmente equivalente en el tema DEFAULT.
Archivo: `ui/components/EmptyStateIllustrations.kt`.

## Hallazgo evaluado y DESCARTADO tras verificación — Arquitectura

### `NotificationPollWorker` sin límite en el sondeo de fondo — reconsiderado, NO aplicado
El especialista de arquitectura señaló que `NotificationPollWorker.getNotifications(householdId)`
(sondeo de WorkManager en segundo plano, ~cada 30 min) no pasa ningún
`limit`, a diferencia del sondeo en primer plano (`refreshUnreadCount`, ya
corregido en un commit reciente con `MAX_POLLED_NOTIFICATIONS = 300`) — y
propuso aplicar el mismo límite ahí.

**Al leer el código completo de la función antes de aplicar el fix, se
encontró un conflicto real que el hallazgo no contemplaba:** el resultado
`all` de esa misma llamada se reutiliza inmediatamente después para
`purgeOldRead(householdId, all, maxAgeMillis = ...)`, la purga best-effort de
notificaciones LEÍDAS con más de 90 días. Si se limitara la lectura a las
300 más recientes, la purga NUNCA vería las notificaciones más antiguas que
ese tope — justamente las que más necesitan purgarse en un hogar con mucho
historial — dejando de purgarlas para siempre y reintroduciendo el
crecimiento sin cota que el propio fix pretendía evitar, con el agravante de
ser un fix silencioso (nadie lo notaría hasta auditar Firestore
directamente).

**Decisión:** no se aplica un límite ciego a esta llamada. El límite SÍ se
promovió a una constante compartida (`MAX_POLLED_NOTIFICATIONS`, ahora en
`NotificationRepository.kt`) y se aplicó correctamente donde SÍ es seguro
(`NotificationScreenModel.refreshUnreadCount`, que no purga nada). Resolver
bien el caso del Worker (leer solo lo necesario para "hay algo nuevo que
notificar" sin sacrificar el alcance de la purga) requeriría separar ambas
responsabilidades — SOLO PROPUESTA, fuera de un fix mecánico de una línea.

## Propuestas no aplicadas (requieren decisión de producto/diseño/infraestructura)

- **StatsScreen: purga TTL de 90 días erosiona los totales "de por vida"**
  (`totalTasksCompleted`, `onTimeRate`) — ya documentado en la ronda v9
  (2026-09-13), sigue abierto. Requiere decisión de producto: contadores
  persistentes en el documento de miembro, independientes del detalle de
  `taskHistory` que sí se purga.
- **Deploy de `firestore.rules` v10 / v11 a producción** — no verificable
  desde este entorno de auditoría (toca infraestructura externa).
- **Proveedor de auth anónima de Firebase Auth aún activo server-side** —
  requiere desactivación en la consola de Firebase, fuera de alcance de un
  cambio de código.
- **`isValidOwnerSuccession` permite a un admin auto-nombrarse owner sin
  expulsión real** — toca `firestore.rules`, fuera de alcance.
- **`achievements` es la única subcolección de miembro sin restricción de
  propietario/rol en `firestore.rules`** (cualquier miembro puede escribir
  logros de otro) — impacto bajo (cosmético, sin puntos), pero toca
  `firestore.rules`.
- **Duplicación de puntos por timeout post-commit en `donatePoints`/
  `redeemReward`** (variante de un único dispositivo, no solo la carrera
  multi-dispositivo ya conocida) — requiere idempotencia vía Cloud Function
  con clave de operación, infraestructura nueva.
- **Purga de `taskHistory`/`messages`/`notifications` sin techo natural
  server-side** — depende enteramente de que un cliente abra la pantalla
  correspondiente; requeriría Cloud Scheduler + Cloud Function de limpieza.
- **Calendario no muestra el historial completo de compleciones de tareas
  recurrentes** (solo la última fecha) — requeriría traer `taskHistory` a
  `CalendarScreen` y cruzar por fecha, alcance moderado.
- **Duplicación estructural `CreateTaskScreen.kt`/`EditTaskScreen.kt`**
  (validaciones casi idénticas) — refactor de alto riesgo sin tests de UI
  que cubran ambos flujos hoy.
- **Ausencia total de layout adaptable a tablets/foldables/landscape**
  (cero usos de `WindowSizeClass`/`BoxWithConstraints` en toda la app) y
  **calendario sin vista de 2 paneles en pantallas anchas** — refactor
  estructural grande, requiere estrategia de diseño responsive explícita.
- **Estados vacíos con lenguaje visual inconsistente** (ilustración Canvas
  vs. emoji simple entre pantallas) y **Stats/TaskDetail sin migrar al
  patrón shimmer** de las demás listas — decisiones de identidad visual,
  el usuario debe decidir viendo capturas, no a ciegas.
- **`HomeScreen` no usa el `TaskHubTopBar` compartido** (única pantalla con
  título alineado a la izquierda) — requeriría un parámetro
  `titleContent` opcional en el componente compartido; bajo impacto visual,
  se prioriza el resto de la ronda.
- **Bloque de "estado de error" duplicado ~15 veces** en vez de un
  composable `ErrorStateBlock` compartido — refactor mecánico pero de
  superficie amplia (15 archivos), se documenta para una ronda dedicada.

## Aplicables pero no implementadas en esta ronda (deuda pendiente objetiva)

Estos hallazgos SÍ son de bajo riesgo y no requieren decisión de producto —
se documentan como pendientes por alcance/tiempo dentro de esta ronda, no
como propuestas que necesiten aprobación:

- **Badge de notificaciones no leídas puede infracontar en hogares muy
  activos** (`refreshUnreadCount` trae las 300 notificaciones más recientes
  de TODO el hogar, no por miembro — un miembro inactivo puede quedar fuera
  de esa ventana). Fix real requiere índice compuesto `memberId, createdAt`
  en Firestore o cambiar la estrategia de query, más que un cambio trivial
  de una línea. Archivo: `network/NotificationRepository.kt`/
  `ui/models/NotificationScreenModel.kt`.
- **Exportación CSV: columna "Veces completada" es en realidad un booleano**
  (`TaskCsvExporter`, ya documentado en el propio código como limitación
  conocida) — el fix requiere pasar el historial real de compleciones
  (`getTaskHistory`) hasta el exportador en vez de derivarlo de
  `lastCompletedDate`. Archivo: `ui/models/TaskCsvExporter.kt`.
- **Formularios largos sin aviso al abandonar con cambios sin guardar**
  (crear/editar tarea, crear recompensa) — requiere seguimiento de "dirty
  state" + `BackHandler` en 3 pantallas, siguiendo el patrón de
  `DestructiveConfirmDialog` ya existente.
- **Formularios de alta sin mensaje inline de campo obligatorio**
  (crear hogar, crear primer perfil, unirse a hogar) — el patrón
  `touched` + `supportingText` ya existe en `CreateTaskScreen`/
  `CreateRewardScreen`, replicarlo en las 4 pantallas de onboarding es
  mecánico pero de superficie amplia para esta ronda.

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ **BUILD SUCCESSFUL** (solo warnings preexistentes de APIs Android
deprecadas — GoogleSignIn, MasterKey, EncryptedSharedPreferences — no
relacionados con esta ronda). Un error de compilación real se detectó y
corrigió durante esta misma verificación: el fix de recurrencia mensual
usaba una variable `today` fuera de scope (estaba `val` dentro de un
`if (showDatePicker)`, no visible en el `onClick` del selector de
frecuencia) en `CreateTaskScreen.kt`/`EditTaskScreen.kt` — corregido
computando la fecha localmente en el punto de uso.

```
./gradlew :composeApp:jvmTest --console=plain
```
→ **BUILD SUCCESSFUL**. Verificado además contra los XML de
`test-results/jvmTest` (no solo el resumen de Gradle, que puede mentir con
UP-TO-DATE): **257 tests, 0 fallos, 0 errores, 0 omitidos**, en 23 archivos
de resultados todos regenerados en esta misma ejecución (`-newer
build.gradle.kts`, confirmando que no son resultados obsoletos).

## Archivos modificados en esta ronda

**De negocio/bugs:**
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/MemberScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HomeScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/NotificationRepository.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/NotificationScreenModel.kt`

**Accesibilidad:**
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateHouseholdScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateProfileScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditProfileScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskDetailScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdChatSection.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/RewardListScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/RankingScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/StatsScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/NotificationListScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/HouseholdScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdMemberList.kt`
- `composeApp/src/androidMain/kotlin/org/taskhub/ui/components/ShouldReduceMotion.android.kt`

**UX/UI/Estética:**
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditTaskScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdDialogs.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/SettingsSheet.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CalendarScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/WelcomeScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/EmptyStateIllustrations.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt` (nuevas claves ES/EN)

**Documentación:**
- `docs/review-panel-expertos-2026-09-13-post-0733.md` (este informe).

## Deuda pendiente para la próxima ronda

1. Las 4 mejoras de "aplicables pero no implementadas" de la sección
   anterior (badge de notificaciones, CSV real, unsaved-changes warning,
   validación inline de onboarding).
2. Todas las propuestas listadas arriba, en particular las que tocan
   `firestore.rules`/infraestructura (deploy de reglas, proveedor anónimo,
   `isValidOwnerSuccession`, reglas de `achievements`) — necesitan decisión
   y despliegue fuera de este entorno.
3. Separar en `NotificationPollWorker` la responsabilidad de "detectar
   notificaciones nuevas para avisar" (sí puede acotarse a un límite) de la
   de "purgar leídas antiguas" (necesita ver el historial completo), para
   poder aplicar con seguridad el límite que esta ronda descartó.
