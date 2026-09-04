# Panel de expertos — revisión integral v7 (2026-09-04)

Nueva pasada completa del comité de 13 expertos sobre Task Hub, coordinada
mediante subagentes en paralelo (uno por especialista, cada uno con lectura
completa del repo + de `docs/review-panel-expertos-2026-09-03.md` y de las
dos rondas de correcciones ya aplicadas desde entonces:
`docs/correcciones-2026-09-04-integridad-seguridad.md` y
`docs/correcciones-2026-09-04-arquitectura-ux-tests.md`). HEAD de partida:
`33c9845` (correcciones arquitectura/rendimiento/UX/tests), árbol de trabajo
limpio.

**No es una continuación parcial**: los 13 expertos releyeron su área de cero
contra el código actual, con instrucción explícita de centrarse en
regresiones introducidas por las dos rondas de correcciones y en problemas
nuevos, sin repetir hallazgos ya resueltos.

## Resumen ejecutivo

El panel confirmó que las correcciones de integridad/concurrencia y de
arquitectura de las dos rondas anteriores se aplicaron correctamente en su
mayoría, pero encontró:

- **1 regresión encubierta de alta frecuencia de uso**: el nuevo componente
  compartido `ExpandableSectionHeader` (introducido para eliminar
  duplicación) invierte el orden de modificadores y reduce el área de toque
  real en 2 de sus 4 usos — confirmado independientemente por 4 expertos.
- **1 bug crítico nuevo en un fix de la ronda anterior**: `reassignTaskCompletion`
  transfería puntos ANTES de escribir el PATCH con precondición optimista
  añadido esta misma ronda pasada — al revés que `completeTask`/
  `completeAssignment`, que sí ponen esa escritura primero precisamente para
  que un conflicto no deje la puerta abierta a duplicar puntos en un
  reintento.
- **1 fix de la ronda anterior que, sin querer, introdujo una regresión de
  logros**: el filtro `pointsAwarded > 0` en `checkAndAwardAchievements`
  (pensado para excluir compleciones "fantasma") también excluye
  compleciones REALES penalizadas a 0 puntos por tardanza.
- **El fix de `deleteAllDocuments` (Semaphore) de la ronda anterior no
  limitaba realmente la concurrencia** — cada llamada creaba su propio
  semáforo local en vez de compartir uno con el fan-out externo.
- **1 hallazgo de privacidad de severidad alta no visto en rondas
  anteriores**: `TaskRepository.getComments` (usado también por
  `anonymizeMemberComments`, el mecanismo de "derecho al olvido" para
  comentarios) no pagina, a diferencia del resto de colecciones — en
  cualquier tarea con muchos comentarios, los de páginas siguientes nunca se
  listan ni se anonimizan.
- **1 hallazgo de privacidad/seguridad sobre dispositivos familiares
  compartidos**: la caché de "miembro actual" no se invalidaba al cerrar
  sesión, solo al abandonar/borrar un hogar — riesgo de atribuir
  compleciones/puntos al perfil equivocado tras un cambio de cuenta en el
  mismo dispositivo.

Todos estos se han corregido en esta ronda (ver "Aplicado en esta ronda" al
final). El resto de hallazgos son propuestas de mayor alcance (rediseño,
refactor arquitectónico, cambios de esquema/reglas) que se documentan pero
no se aplican, según el encargo.

---

## Experto 1 — Estética y diseño visual

### IMPORTANTE

- **Problema**: `ExpandableSectionHeader` (nuevo componente compartido de la
  ronda anterior) invierte el orden de modificadores: `Modifier.fillMaxWidth()
  .then(modifier).heightIn(min=48.dp).clickable(...)`. Como el `modifier` del
  llamador (con padding) se aplica ANTES que `.clickable`, el padding queda
  FUERA del área de hit-test/ripple real.
  **Por qué importa**: reduce el área de toque en `TaskListScreen#GroupHeader`
  (12dp horizontal / 10dp vertical de "zona muerta" sobre una fila coloreada
  de alta frecuencia de uso — cabecera de cada grupo de la lista de tareas) y
  en `CreateTaskScreen#QuickTemplatesSection` (4dp vertical, impacto menor).
  **Evidencia**: `ui/components/ExpandableSectionHeader.kt:41-48` (antes del
  fix), comparado con el orden correcto que ya usaba el código reemplazado.
  **Fix**: reordenar a `.fillMaxWidth().heightIn(min=48.dp).clickable(onClick)
  .then(modifier)`. **Estado: APLICADO YA** esta ronda.
- **Problema**: `EmptyTasksIllustration`/`EmptyHouseholdsIllustration`
  hardcodean Teal/Coral, rompiendo la premisa del tema Minimal (100%
  acromático) — solo 2/6 pantallas con estado vacío tienen ilustración de
  marca, el resto usa emoji suelto.
  **Evidencia**: `EmptyStateIllustrations.kt:14-18,35,63-67,96,108,112,119`.
  **SOLO PROPUESTA** (requiere ilustraciones nuevas). **Estado**:
  PENDIENTE-DESDE-V2/V3, confirmado sin cambios.

### MENOR

- `SemanticColors.onSuccess`/`onInfo` en dark: recalculado independientemente
  — 4.62:1 y 4.55:1, ambos pasan AA, pero siguen sin uso real en la UI (solo
  las variantes `*Container`). Código defensivo, sin impacto hoy.
  **SOLO PROPUESTA**.
- Adopción parcial de `ShimmerList` (solo 4 pantallas). **SOLO PROPUESTA**,
  sin cambios.

**Confirmado sin regresión**: split de `TaskCommentsScreenModel` sin impacto
visual en `TaskDetailScreen` (recableo puro); los 2 sitios de
`ExpandableSectionHeader` sin `modifier` (HouseholdTaskSection,
HouseholdMemberList) no están afectados por el bug de arriba; ajuste de
contraste de `SemanticColors` no rompe la armonía de paleta.

---

## Experto 2 — Funcionalidad end-to-end

### IMPORTANTE

- **Problema**: `TaskRepository.getComments` no pagina (única petición sin
  `pageSize`/`pageToken`), a diferencia de `getTasks`/`getTaskHistory`/
  `getMessages` (todos migrados a `client.listAllDocuments`). Afecta tanto a
  la carga normal de comentarios (`TaskCommentsScreenModel.loadComments`,
  puede mostrar solo un subconjunto en tareas con chat largo) como a
  `anonymizeMemberComments` (fix de privacidad de la ronda anterior): los
  comentarios fuera de la primera página nunca se anonimizan.
  **Evidencia**: `TaskRepository.kt:731-742` (antes del fix) vs. el patrón
  correcto en `HouseholdRepository.getMessages`.
  **Fix**: usar `client.listAllDocuments(...)` igual que el resto de
  colecciones. **Estado: APLICADO YA** esta ronda (ver también Experto 10,
  que lo clasifica como CRÍTICO desde el ángulo RGPD).
- **Problema**: `deleteAllDocuments`/`deleteHousehold`: el `Semaphore(20)`
  introducido en la ronda anterior NO limitaba realmente la concurrencia —
  cada llamada a `deleteAllDocuments` creaba su PROPIO semáforo local en vez
  de compartir uno con el `fanOutLimiter` del fan-out externo. Con hasta 20
  `taskJobs` en vuelo a la vez, cada uno con su propio semáforo interno de
  20, el pico real podía llegar a ~400 conexiones DELETE simultáneas en vez
  de 20.
  **Evidencia**: `FirestoreRepository.kt:392-405` (fan-out externo) vs.
  `:463` (semáforo interno nuevo por llamada), antes del fix.
  **Fix**: un único `Semaphore` compartido entre todas las llamadas de un
  mismo `deleteHousehold`. **Estado: APLICADO YA** esta ronda (confirmado
  independientemente también por Expertos 6, 7, 11 y 13).

### MENOR

- `regenerateNextAssignment`: el swallow de `ALREADY_EXISTS` no verifica que
  el documento existente coincida con la decisión de rotación actual —
  riesgo teórico, sin ruta de ejecución real encontrada (ambos call-sites
  están protegidos por concurrencia optimista aguas arriba). **SOLO
  PROPUESTA**.
- `HomeScreenModel.loadHouseholdPreview` usa un filtro de "pendiente"
  (`lastCompletedDate == null`) distinto al de `loadAllTasks`/`isPending`
  (que sí usa `RecurrenceRules.isDueToday`) — preexistente (confirmado con
  `git log -p` que el filtro no cambió en el refactor de esta ronda, no es
  regresión nueva), pero documentado también por Experto 7 desde el ángulo
  arquitectónico. **SOLO PROPUESTA**.

**Confirmado sin hallazgo**: los 7 fixes de concurrencia/integridad de la
ronda anterior (completeAssignment, regenerateNextAssignment ID
determinista, undoTaskCompletionAssignments, compleciones fantasma,
updateAssignmentMemberForCompletion, expectedUpdateTime en cierre de
hermanas, reassignTaskCompletion concurrencia) funcionan correctamente en
los escenarios probados (completar→deshacer→completar rápido, IDs
deterministas sin colisión cruzada); split `TaskScreenModel`→
`TaskCommentsScreenModel` sin fugas de estado (`currentMemberId` se pasa
fresco en cada acción); `HouseholdTaskSection` vía `HomeScreenModel` sin
fuga de estado entre hogares en la `LazyColumn`.

---

## Experto 3 — Accesibilidad WCAG AA

### IMPORTANTE

- **Problema**: confirmado desde el ángulo de accesibilidad el bug de
  `ExpandableSectionHeader` (ver Experto 1): el `heightIn(min=48.dp)` interno
  sigue garantizando el mínimo WCAG en el contenido, pero el padding del
  llamador queda fuera del área de toque real — en `TaskListScreen#GroupHeader`
  esto deja ~15dp superior/inferior de "zona muerta" sobre una fila que
  visualmente parece 100% tocable (fondo coloreado, elevación). Fallo de
  affordance más que de touch-target mínimo (24×24 WCAG sí se cumple).
  **Estado: APLICADO YA** esta ronda (mismo fix que Experto 1).

### MENOR

- `onSuccess`/`onInfo` en dark: recontraste independiente confirma 4.62:1 y
  4.55:1 respectivamente — pasan AA, `onInfo` con margen mínimo (0.05 sobre
  el umbral). Sin uso real hoy. **SOLO PROPUESTA** (informativo).
- Falta `role = Role.Button`/`stateDescription` explícito en varios
  controles clicables custom (`ExpandableSectionHeader`, filas de
  `HouseholdTaskSection`/`HouseholdMemberList`/`SettingsSheet`) — TalkBack
  funciona (anuncia "doble toque para activar") pero sin semántica
  estructurada de "botón" ni estado expandido/colapsado. **SOLO PROPUESTA**,
  prioridad baja.
- Icono "quitar etiqueta" (`InputChip`) con `contentDescription = null`,
  preexistente. **SOLO PROPUESTA**.
- `shouldReduceMotion()` en JVM/desktop siempre `false` (sin API fiable en
  esa plataforma) — gap conocido, no accionable de forma sencilla.

**Confirmado sin regresión**: `contentDescription` dinámico correcto
(`common_collapse`/`common_expand`) en los 4 usos de
`ExpandableSectionHeader`; los 3 guards nuevos de reduce-motion conectados
correctamente; sin otros sitios con el mismo patrón de orden de
modificadores fuera de `ExpandableSectionHeader`; `DayColumn` de
`CalendarScreen` con orden correcto (no reproduce el bug); contraste de los
3 temas × 2 modos recalculado por muestreo, sin discrepancias.

---

## Experto 4 — UI y componentes Material 3

### CRÍTICO

- Confirmado desde el ángulo de diseño de componentes: el bug de
  `ExpandableSectionHeader` es más grave que un simple defecto de
  implementación porque el propio diseño del componente invita al error (el
  parámetro `modifier` está documentado/usado para añadir espaciado, pero la
  cadena interna lo aplicaba antes de `.clickable`). Verificado que es una
  regresión AISLADA — grep de `.then(` en todo `ui/` confirma que las otras
  3 apariciones (`ProfileScreen.kt`, `CalendarScreen.kt` ×2) siguen el orden
  correcto. **Estado: APLICADO YA** esta ronda (mismo fix, una línea).

### MENOR

- Imports muertos `KeyboardArrowUp`/`KeyboardArrowDown` en `TaskListScreen.kt`
  y `CreateTaskScreen.kt` tras migrar a `ExpandableSectionHeader` (el icono
  ahora se dibuja dentro del componente compartido).
  **Estado: APLICADO YA** esta ronda (4 líneas eliminadas).
- Nombre del componente `DestructiveConfirmDialog` algo contradictorio
  cuando se usa con `destructive = false` (caso de uso ya documentado en su
  KDoc, y semánticamente correcto en `MemberRewardScreen`/
  `HouseholdMemberList` — el botón no se pinta en rojo). Nit de naming.
  **SOLO PROPUESTA**.
- `MaterialTheme.typography.titleLarge + FontWeight.Bold` repetido inline
  como "estilo de título" en ≥10 archivos — candidato a `TextStyle`
  semántico, sin urgencia. **SOLO PROPUESTA**.

**Confirmado sin regresión**: `HouseholdTaskSection.kt` 100% presentacional
(sin `FirestoreRepository`/`koinInject` real); sin literales de color fuera
de `ui/theme/`; `*Card` composables (~19 archivos) sin duplicación que
amerite un componente base; `AlertDialog` crudo en `HomeScreen`/
`TaskDetailScreen` justificado (flujos distintos de confirmación
destructiva).

---

## Experto 5 — UX

### IMPORTANTE

- **Problema**: fallback de nombre "Usuario"/"Miembro" hardcodeado en
  español, dentro de archivos que la propia ronda anterior tocó para migrar
  otros fallbacks a `AppStrings` (i18n residual quedó fuera del barrido).
  **Por qué importa**: es texto visible (nombre mostrado), no solo un
  mensaje de error de borde — con la app en inglés, el usuario ve "Usuario"/
  "Miembro" en español incrustado en una UI en inglés.
  **Evidencia**: `ProfileScreenModel.kt:90` (a 7 líneas de una clave ya
  migrada); `TaskCommentsScreenModel.kt:100,104` (archivo nuevo del split de
  esta ronda, con claves i18n migradas justo al lado).
  **Fix**: nuevas claves `profile_default_name`/`task_comment_default_author`
  (ES/EN). **Estado: APLICADO YA** esta ronda.

### MENOR

- Clave i18n huérfana `household_task_section_error` (el error ya llega
  resuelto desde `HomeScreenModel`, la clave dejó de usarse tras el refactor
  #15 de la ronda anterior). **Estado: APLICADO YA** esta ronda (dead code).
- `QrCodeImage.onError` con string hardcodeado en español que además nunca
  se muestra (el único call-site no pasa `onError`) — si falla la
  generación del QR de invitación, el usuario ve un hueco vacío sin
  explicación. **SOLO PROPUESTA** (requiere threading de UI de error nueva).
- Mismo patrón de validación prematura sin `touched` en `penaltyValue`
  (impacto menor, campo gated tras toggle). **PENDIENTE-DESDE-V6**, sin
  cambios.

**Confirmado sin regresión**: migración de 33 fallbacks a `AppStrings`
(ronda anterior) con paridad ES/EN verificada programáticamente, sin claves
huérfanas ni fuera de dominio; `NotificationScreenModel` con `settingsStore`
inyectado correctamente (único punto de construcción vía Koin); patrón de
`HouseholdTaskSection` vía `HomeScreenModel` con el mismo comportamiento de
carga/parpadeo que el composable anterior (no peor); validación no
acusatoria (`titleTouched`/`costTouched`) y botón "Reintentar" de
`EditTaskScreen` intactos.

---

## Experto 6 — Programador senior

### IMPORTANTE

- **Problema (NUEVO)**: `HouseholdRepository.reconcileHouseholds` lanza un
  `async` por hogar y, dentro de esas corrutinas EN PARALELO, llama a
  `HouseholdStore.removeHousehold` (read-modify-write NO atómico sobre
  `Settings`). Si el reconcile detecta 404/403 en dos o más hogares a la vez
  (p.ej. tras estar offline mientras un admin expulsa al usuario de varios
  hogares), la última escritura gana y algún hogar "fantasma" sobrevive en
  la caché local.
  **Por qué importa**: autocurable en el siguiente reconcile (cada
  arranque); solo produce un hogar fantasma que fallará al abrirse (mismo
  404/403), sin corrupción de datos remotos ni de puntos.
  **Evidencia**: `HouseholdRepository.kt:216-242` + `HouseholdStore.kt:75-79`.
  **Fix propuesto**: serializar con `Mutex` (mismo patrón que
  `MemberRepository.currentMemberCache`) o acumular IDs y hacer una única
  escritura tras `awaitAll()`. **SOLO PROPUESTA** — impacto acotado, cambio
  de firma de `HouseholdStore` merece revisión propia.

**Confirmado sin hallazgo/regresión**: `ensureAuth()→String?` propagado
correctamente a los 3 llamadores, sin ningún sitio que lea `bearerToken`
mutable tras soltar el mutex (grep exhaustivo); registro de repos de
dominio como Koin `single` sin problema de scope/lifecycle nuevo (su tiempo
de vida ya era el de todo el proceso antes del refactor); mapeo DTO
centralizado en `FirestoreParsers.kt` idéntico campo a campo al código
inline que reemplazó; `AssignmentCompletionRules.siblingsToClose` réplica
fiel de la lógica inline previa (complemento booleano exacto); sin `!!`,
`lateinit var`, `GlobalScope.launch` ni DTOs mutables en todo el ámbito
auditado.

---

## Experto 7 — Jefe de arquitectura

### IMPORTANTE

- **Problema**: `FirestoreRepository.kt` NO se ha reducido — 1920 líneas
  actuales, no las 1634 que documentaba el informe de la ronda anterior como
  punto de partida. El salto real (+270 líneas) ocurrió en el commit de
  integridad/seguridad (concurrencia optimista, IDs deterministas, cifrado
  de token), no en el de arquitectura; el commit de arquitectura, pese a
  extraer `getLocalId`/`currentUserIdentities` y `createRedemption`, solo
  compensó -1 línea neta.
  **Por qué importa**: la tendencia de crecimiento NO está frenada, solo
  momentáneamente estancada — `completeTask`/`completeAssignment` (~150
  líneas cada uno) siguen siendo los mayores bloques monolíticos, y ahora
  que los repos de dominio son inyectables, un futuro refactor podría
  moverlos a un orquestador dedicado sin pelear con el ciclo de
  construcción que antes lo impedía.
  **SOLO PROPUESTA**.
- **Problema**: `HomeScreenModel` tiene ahora dos definiciones de "tarea
  pendiente" distintas en el mismo objeto: `loadAllTasks()` usa `isPending()`
  (vía `RecurrenceRules.isDueToday`), `loadHouseholdPreview()` usa un filtro
  ad-hoc (`lastCompletedDate == null`) que ignora recurrencia/vencimiento —
  una tarea recurrente completada una vez desaparece del preview para
  siempre aunque vuelva a estar pendiente hoy.
  **Por qué importa**: más profundo que el N+1 de rendimiento ya señalado
  por Experto 11 (mismo síntoma, causa distinta) — es lógica de negocio
  duplicada y divergente dentro del mismo ScreenModel, no solo una llamada
  de red repetida.
  **Evidencia**: `HomeScreenModel.kt:82` vs. `:142`.
  **SOLO PROPUESTA** (documentar la intención o unificar el predicado).

**Veredicto por subsistema**: `network/` con mejora estructural real en DI
(sin ciclos, sin construcción manual fuera de Koin) pero sin reducción de
tamaño de la fachada; `ui/` (models) con mejora parcial (bypass de
`HouseholdTaskSection` eliminado, 2 extracciones limpias de
`TaskScreenModel`) pero el núcleo de 1210 líneas intacto; `ui/` (screens)
sin cambios relevantes; `storage/` sano, sin hallazgos; `di/` bien —
`AppModule.kt` como único punto de construcción, separación `single`/
`factory` consistente.

**Confirmado sin hallazgo**: registro Koin `single` de repos de dominio sin
ciclo nuevo y sin construcción manual en ningún otro sitio del árbol;
extracción de `RewardsRepository.createRedemption` deja la orquestación
consistente; `HouseholdTaskSection` confirmado el único caso ya resuelto de
bypass.

---

## Experto 8 — QA y bugs

### IMPORTANTE

- **Problema**: `checkAndAwardAchievements` (filtro `pointsAwarded > 0`,
  añadido la ronda anterior para excluir compleciones fantasma) también
  excluye compleciones REALES penalizadas a 0 puntos — a diferencia de
  `StatsScreenModel.computeStats`, que está a salvo porque fusiona con
  `taskHistory` (sin filtro de puntos), `checkAndAwardAchievements` NO
  fusionaba con `taskHistory`, solo contaba `assignments` filtradas.
  **Por qué importa**: un miembro que completa tareas tarde y pierde todos
  los puntos por penalización nunca desbloqueaba logros de "N tareas
  completadas" — regresión silenciosa del propio fix que se suponía debía
  arreglar (no crear) este tipo de conteo incorrecto.
  **Evidencia**: `PenaltyRules.kt:27-36` (penalización puede dar
  `pointsAwarded=0` en una compleción real) vs.
  `TaskScreenModel.kt:1002-1016` (antes del fix, sin fallback a
  `taskHistory`).
  **Fix**: contar desde `taskHistory` (que SÍ registra toda compleción real,
  puntos incluidos, y NUNCA se escribe para asignaciones hermanas
  "fantasma" — verificado que `saveTaskHistory` solo se llama una vez por
  compleción real en `completeTask`/`completeAssignment`, nunca en el cierre
  de hermanas). **Estado: APLICADO YA** esta ronda.
- **Problema**: `undoTaskCompletionAssignments` recalcula `nextDueDate` con
  el `task` FRESCO actual, no el de cuando se completó — si el usuario edita
  `frequency`/`recurrenceDay(s)` entre completar y deshacer, el ID
  determinista recalculado no coincide con el que `regenerateNextAssignment`
  realmente creó, y la asignación huérfana del ciclo siguiente no se borra.
  **Evidencia**: `FirestoreRepository.kt:1099-1101` vs.
  `TaskScreenModel.kt:602-611`. Borde estrecho (requiere edición manual en
  la ventana de undo), higiene de datos, no duplica puntos.
  **SOLO PROPUESTA**.
- **Problema**: `revertTaskCompletion` (usado por el undo) no restaura
  `nextDueAt` al deshacer — `completeTask` fija `lastCompletedDate` y
  `nextDueAt` en el mismo PATCH, pero el revert solo revierte
  `lastCompletedDate`/`completedBy`. Si el usuario deshace y luego completa
  de verdad, el `nextDueAt` obsoleto (demasiado futuro) puede hacer que la
  nueva compleción se marque incorrectamente "a tiempo" o sin penalización.
  Preexistente, no uno de los 7 fixes de la ronda anterior, pero relevante
  para integridad de puntos.
  **Evidencia**: `TaskRepository.kt:274-299` vs. `FirestoreRepository.kt:938-944`.
  **SOLO PROPUESTA**.

### MENOR

- `reassignTaskCompletion` transfiere `task.points` (config fija de la
  tarea), no los puntos realmente otorgados (`outcome.pointsAwarded`, que
  puede ser menor por penalización) — corregir "quién lo hizo" puede
  transferir MÁS puntos de los otorgados originalmente. Función de
  corrección manual, uso esporádico. **SOLO PROPUESTA**.
- `ALREADY_EXISTS` en `regenerateNextAssignment` no verifica que el
  documento existente coincida con la decisión de rotación vigente (ventana
  teórica, sin ruta de ejecución real encontrada). **SOLO PROPUESTA**.
- Crecimiento no acotado de `assignments` (sin purga de documentos
  antiguos) amplifica el coste de los fixes de esta y la ronda anterior —
  ya documentado como ítem de arquitectura pendiente. **SOLO PROPUESTA**.

**Confirmado sin hallazgo/regresión (los 7 fixes de la ronda anterior, uno
por uno)**: ID determinista de `regenerateNextAssignment` sin colisión
incorrecta (el `taskId` en el ID descarta colisión cruzada, `nextOccurrence`
siempre avanza a fecha futura); `undoTaskCompletionAssignments` paso 1 sin
ambigüedad real (`completedAt` con precisión de MILISEGUNDO, no de segundo
como se planteaba verificar); `updateAssignmentMemberForCompletion` unívoco
por construcción (`memberId+completedAt+status`, sin la misma ambigüedad
teórica); `reassignTaskCompletion` concurrencia optimista sobre el doc de
tarea correcta en aislamiento (ver hallazgo relacionado de Experto 12,
CRÍTICO, sobre su efecto combinado con el ORDEN de las operaciones — ya
corregido esta ronda).

---

## Experto 9 — Seguridad / AppSec (OWASP MASVS)

Verificación código-vs-producción hecha contra la API real de Firebase
Rules con credenciales disponibles: **ruleset activo confirmado
`12666cb9-8133-4715-8816-352ae3410f06`, desplegado 2026-09-04T00:39:39Z,
idéntico byte a byte al `firestore.rules` del repo**.

### IMPORTANTE

- **Problema (NUEVO)**: `MemberRepository.currentMemberCache` (Koin
  `single`, vida de proceso) indexado solo por `householdId`, no invalidado
  en `GoogleAuthManager.signOut()` (solo en `deleteHousehold`/
  `leaveHousehold`). En un dispositivo familiar compartido: Padre A cierra
  sesión, Padre B (miembro real del mismo hogar) inicia sesión con otra
  cuenta sin reiniciar el proceso — `resolveCurrentMember(hid)` puede seguir
  devolviendo el `memberId` cacheado de A. Como `taskHistory`/create no
  exige `memberId==auth.uid` (deliberado, para el flujo "padre completa por
  hijo"), se puede crear un registro "tarea completada" atribuido a A
  mientras actúa B; si B es admin, los puntos de B pueden acreditarse
  silenciosamente a A.
  **Evidencia**: `MemberRepository.kt:55-82` (único punto de invalidación,
  antes solo `deleteHousehold`/`leaveHousehold`); `GoogleAuthManager.kt:119-134`
  (signOut sin invalidación, antes del fix).
  **Fix**: invalidar toda la caché en `signOut()`. **Estado: APLICADO YA**
  esta ronda (mismo hallazgo confirmado independientemente por Experto 10
  desde el ángulo de privacidad).
- **Problema**: `rewardRedemptions`/`taskHistory` create permiten forjar
  registros atribuidos a OTRO miembro sin ser `trusted` — `existsMemberDoc`
  solo comprueba que el `memberId` pertenece a un miembro real, no que sea
  `auth.uid` ni que el llamante sea `isTrusted`. Un miembro no confiable con
  cliente REST modificado podría crear canjes/historial "legítimos"
  atribuidos a un tercero sin tocar sus puntos reales.
  Confirmado además que NO hay TOCTOU en el coste de `rewardRedemptions`
  (el `get()` de la regla lee el coste en vivo, mismo request atómico) ni
  forma de canjear una recompensa ya eliminada.
  **Evidencia**: `firestore.rules:349-356` y `:319-323`.
  **Fix propuesto**: añadir `(memberId == auth.uid || isTrusted(hid))` a
  ambas reglas de `create`. **SOLO PROPUESTA** (cambio de reglas +
  despliegue).

### MENOR

- `inviteCode` interpolado sin encode/validación de charset en la URL REST
  de `HouseholdRepository.kt:290` — entrada no saneada, pero sin bypass de
  autorización real (Firestore evalúa contra el path final, `invites`/`get`
  ya es de acceso abierto a cualquier `signedIn()`). **SOLO PROPUESTA**.

**Confirmado sin hallazgo**: migración del token de Calendar a `secureStore`
sin resto en texto plano (`migrateLegacyToken` borra el valor legado en el
mismo paso); `SecureStore` correcto en las 3 plataformas; `backup_rules.xml`/
`data_extraction_rules.xml` excluyen `sharedpref`; CSPRNG en
`generateInviteCode`; API key de Firebase pública, no-issue; sin módulos
huérfanos; permisos Android mínimos.

---

## Experto 10 — Privacidad / RGPD / menores

### CRÍTICO

- **Problema**: `TaskRepository.getComments` (usado por
  `anonymizeMemberComments`, el mecanismo de "derecho al olvido" para
  comentarios tras expulsión/abandono) no pagina — en cualquier tarea con
  más comentarios que el tamaño de página, los de páginas siguientes nunca
  se listan ni se anonimizan.
  **Por qué importa**: más grave que la limitación ya documentada de
  comentarios pre-`memberId` (esa SÍ está explicada como límite conocido).
  Esta es un fallo SILENCIOSO, no documentado como límite, cuya probabilidad
  crece con el uso real de la app (hilos de comentarios familiares largos)
  — justo el escenario para el que la función se diseñó. Discrepancia
  directa entre lo que `docs/privacy.html` promete (borrado/anonimización
  desde la app) y lo que el código ejecuta.
  **[BUG TÉCNICO]** — **Estado: APLICADO YA** esta ronda (mismo fix que
  Experto 2).

### IMPORTANTE

- **Problema**: `MemberRepository.currentMemberCache` no invalidada en
  `signOut()` — en dispositivo familiar compartido, atribución incorrecta
  de compleciones/puntos/comentarios al perfil anterior tras cambio de
  cuenta sin reiniciar el proceso. Problema de exactitud de datos personales
  (Art. 5(1)(d) RGPD), agravado porque uno de los perfiles puede ser un
  menor ("child") — actividad de un adulto podría atribuirse a un perfil
  infantil o viceversa.
  **[BUG TÉCNICO]** — **Estado: APLICADO YA** esta ronda (mismo fix que
  Experto 9).

### MENOR / INFORMATIVO

- Revisado `TaskHistoryResponse`/`RewardRedemption`/`NotificationResponse`:
  ninguno guarda nombre propio (solo `memberId`, resuelto en UI), y las
  notificaciones push no interpolan nombre de miembro — no hay otro lugar
  con nombre sin cubrir por la anonimización existente. **Sin hallazgo**.
- `AdController.updateChildDirectedSignal` con `isChildProfile` ignorado
  deliberadamente en Android — no es código muerto (se ejecuta y reafirma
  TFCD en cada `loadTasks()`), decisión de producto ya documentada y
  correcta. Único matiz: llamar a `setRequestConfiguration()` con los mismos
  valores en cada carga de tareas es redundante aunque inofensivo. **SOLO
  PROPUESTA**, cosmético.
- Sin retención de datos ni age-gating real más allá de la declaración
  textual de la política de privacidad. **[PROPUESTA LEGAL/PRODUCTO]**, sin
  cambios desde v3.

**Confirmado sin regresión**: Analytics sin PII; reglas de lectura
acotadas a miembros del hogar; `deleteAccount` sin completar si el cascade
es parcial (comportamiento correcto); flujo de chat (`anonymizeMemberMessages`)
ya paginado correctamente, sin el mismo problema que comentarios.

---

## Experto 11 — Rendimiento

### CRÍTICO

- **Problema**: `HomeScreenModel.loadHouseholdPreview` vuelve a hacer su
  propio `getTasks()` por hogar en vez de reusar lo que `loadAllTasks()` ya
  trajo — con N hogares, entrar en `HomeScreen` dispara 2N lecturas de la
  colección `tasks` (una desde `loadAllTasks`, otra desde cada
  `loadHouseholdPreview`) en vez de N. No es un N+1 nuevo introducido por el
  fix arquitectónico de la ronda anterior (el patrón "cada
  `HouseholdTaskSection` hace su propio fetch" ya existía antes, cuando
  inyectaba `FirestoreRepository` directamente) — el fix corrigió la
  violación de capas pero no eliminó la duplicación de red subyacente.
  **Evidencia**: `HomeScreenModel.kt:66-123` vs. `:137-155`.
  **Fix propuesto**: derivar `previewTasks` de la lista cruda que
  `loadAllTasks()` ya trae, o un guard que evite refetch si ya hay
  `Success` reciente. **SOLO PROPUESTA** (requiere retener listas crudas
  por hogar, cambia semántica de refresco automático — decisión de diseño).
- **Problema relacionado**: scroll rápido en la `LazyColumn` de `HomeScreen`
  cancela y re-dispara el `LaunchedEffect(h.id)` de cada item sin ningún
  cache, produciendo refetch + parpadeo visual en cada ida-y-vuelta.
  **SOLO PROPUESTA** (mismo fix que el punto anterior).

### IMPORTANTE

- `StatsScreenModel.loadStats`: `getMemberAchievements` se lanza en serie
  DESPUÉS del bloque ya paralelizado con `async`, pese a depender solo de
  parámetros ya disponibles — añade un round-trip extra evitable.
  **Evidencia**: `StatsScreenModel.kt:67-78`.
  **SOLO PROPUESTA** (bajo riesgo, cambio pequeño).

### MENOR

- `_previewTasks` como `Map` único en un solo `StateFlow` causa
  recomposición cruzada de TODOS los items de la `LazyColumn` cuando
  resuelve uno solo — con N típico bajo (2-5 hogares) el impacto es
  limitado. **SOLO PROPUESTA**.

**Confirmado sin regresión**: paralelización de `loadTasks`/`loadStats` con
`async/await` sin renders intermedios inconsistentes (el `StateFlow` se
actualiza de forma atómica al final, no incrementalmente);
`CalendarSyncManager.reconcile` reutiliza `tasks` correctamente; arranque de
la app sin trabajo de red adicional.

---

## Experto 12 — Red / offline / sincronización

### CRÍTICO

- **Problema**: `reassignTaskCompletion` transfería puntos (paso 1,
  `addMemberPoints` incondicional) ANTES de escribir el PATCH de
  `completedBy` con precondición optimista (paso 2, fix de la ronda
  anterior) — al revés que `completeTask`/`completeAssignment`, que ponen su
  PATCH con precondición como PRIMER paso precisamente para evitar el
  anti-patrón "reintentar duplica puntos". Si el paso 2 fallaba por
  conflicto (más probable ahora que tiene precondición, antes casi nunca
  fallaba), la excepción se propagaba sin revertir el paso 1 ya confirmado
  — un reintento del usuario con el mismo estado (`oldMemberId` sin cambiar,
  porque el PATCH nunca llegó a aplicarse) volvía a ejecutar la
  transferencia de puntos por duplicado.
  **Evidencia**: `FirestoreRepository.kt:1180-1203` (antes del fix) vs. el
  orden correcto ya usado en `completeTask` (`:918-960`).
  **Fix**: invertir el orden — PATCH de `completedBy` con precondición
  primero, transferencia de puntos después, solo si el PATCH tuvo éxito.
  **Estado: APLICADO YA** esta ronda.

### IMPORTANTE

- **Problema**: `completeAssignment`: la caché de tareas (`taskCache.clearTasks`)
  solo se invalidaba dentro del `try` del PATCH best-effort de sincronización
  del doc de tarea (fix de la ronda anterior) — si el PATCH se descartaba
  por conflicto (`FAILED_PRECONDITION`/`ABORTED`), la caché local quedaba
  con la instantánea previa, sin reflejar el cambio del escritor que ganó
  la carrera. Ventana estrecha (conflicto + desconexión inmediata antes de
  la siguiente lectura con red), pero real.
  **Evidencia**: `FirestoreRepository.kt:1513-1533` (antes del fix).
  **Fix**: mover `taskCache.clearTasks(householdId)` fuera del `try/catch`.
  **Estado: APLICADO YA** esta ronda.
- `redeemReward` con saldo negativo por canjes concurrentes — confirmado que
  la regla v7 (`pointsSpent == cost`) es IRRELEVANTE para este problema (cierra
  la vía de "coste falseado", no la de "gastar más de lo que tienes por una
  carrera"; no hay validación `totalPoints >= 0` en `members` update).
  **PENDIENTE-DESDE-V6**, confirmado sin cambios. **SOLO PROPUESTA**.
- `CalendarSyncManager`/`GoogleCalendarRepository.ensureCalendar`: sin
  protección contra creación duplicada de calendario si dos flujos
  concurrentes del mismo dispositivo (`reconcile()` + `onTaskAssigned()`)
  llaman a `ensureCalendarId` antes de que el primero persista el
  `calendarId` — puede crear 2 calendarios "Tareas X" duplicados en Google
  Calendar, uno huérfano. **SOLO PROPUESTA**, severidad baja (cosmético).

### MENOR

- `App.kt`/`EditTaskScreen.kt`: 4 `LaunchedEffect` con `catch (_: Exception)`
  sin relanzar `CancellationException` — impacto bajo (arranque/recarga sin
  fuga de recursos real), pero por consistencia con el resto del árbol
  (barrido ya limpio en `ScreenModel`s y `network/`).
  **Estado: APLICADO YA** esta ronda.
- `ALREADY_EXISTS`/`ABORTED` en `regenerateNextAssignment`: ambos mapean a
  HTTP 409, riesgo teórico de tratar un `ABORTED` real como no-op
  idempotente si el body de error no es parseable. Confianza baja/teórica,
  sin repro encontrado. **SOLO PROPUESTA**.

**Confirmado sin hallazgo**: la app no tiene cola de escritura offline
(sin `WorkManager`/outbox) — el escenario "dos dispositivos offline
sincronizan horas después" no puede ocurrir con esta arquitectura, solo la
carrera de ventana estrecha (dos dispositivos online casi simultáneos), ya
cubierta por los fixes de concurrencia; `CalendarSyncManager.kt` completo
sin fugas de `CancellationException`; `ensureAuth()`/`bearerToken` dentro de
`authMutex`; `addMemberPoints`/`appreciateMember`/etc. con concurrencia
optimista sólida.

---

## Experto 13 — Cobertura de pruebas (solo informa)

Confirmado por ejecución (`./gradlew :composeApp:jvmTest`) que los 25 tests
nuevos de la ronda anterior existen, compilan y pasan de verdad — **160
tests reales**, coincide con lo documentado.

### Mapa de huecos priorizado

- **CRÍTICO**: ningún `ScreenModel` tiene test (`TaskScreenModel`,
  `HomeScreenModel`, `StatsScreenModel`, `TaskCommentsScreenModel`, etc.) —
  es la capa donde viven varios de los hallazgos de esta ronda
  (`checkAndAwardAchievements`, `loadHouseholdPreview`,
  `undoTaskCompletionAssignments`). Requiere extraer los repos a interfaces
  o introducir fakes manuales (no hay `ktor-client-mock`/`mockk` en el
  proyecto, y `FirestoreClient` construye su `HttpClient` internamente sin
  motor inyectable). Inversión de mayor apalancamiento del panel.
- **IMPORTANTE**: `TaskCsvExporter` (función pura, nueva del split de la
  ronda anterior) sin ningún test — mismo patrón que
  `AssignmentCompletionRulesTest`/`PenaltyRulesTest`, cero mocks
  necesarios, hueco barato de cerrar.
- **IMPORTANTE**: la lógica ahora corregida en `checkAndAwardAchievements`
  (conteo desde `taskHistory`) es extraíble a función pura testeable sin
  mocks, siguiendo el patrón `AssignmentCompletionRules`.
- **MENOR**: `reassignTaskCompletion`, `deleteAllDocuments`,
  `getComments`/paginación, `undoTaskCompletionAssignments`, invalidación de
  caché en `signOut` — todos requieren infraestructura de test nueva
  (motor HTTP simulable) para cubrirse de forma realista; no cubribles hoy
  sin esa inversión.

`TaskCommentsScreenModel` (nuevo, del split de la ronda anterior) sin tests
propios — no es un hueco introducido por el split en sí (ningún
`ScreenModel` del proyecto tiene test), el split solo hizo explícito un
hueco preexistente de toda la capa.

---

## Aplicado en esta ronda

### Bug de alta frecuencia de uso (componente compartido)

- **`ui/components/ExpandableSectionHeader.kt`** — orden de modificadores
  corregido (`clickable` antes de `.then(modifier)`), restaurando el área
  de toque completa en los 4 usos del componente (Expertos 1, 3, 4).

### Concurrencia / integridad de puntos

- **`network/FirestoreRepository.kt` — `reassignTaskCompletion`** —
  reordenado: el PATCH de `completedBy` con precondición optimista ahora es
  el PRIMER paso; la transferencia de puntos solo ocurre si ese PATCH tiene
  éxito — evita duplicar puntos en un reintento tras conflicto (Experto 12,
  CRÍTICO).
- **`network/FirestoreRepository.kt` — `completeAssignment`** —
  `taskCache.clearTasks(householdId)` movido fuera del `try`, para que se
  invalide también cuando el PATCH de sincronización se descarta por
  conflicto (Experto 12).
- **`network/FirestoreRepository.kt` — `deleteAllDocuments`/`deleteHousehold`** —
  un único `Semaphore(20)` compartido por todo el cascade-delete en vez de
  uno nuevo por llamada, cerrando de verdad el límite de concurrencia que
  el fix de la ronda anterior no lograba (Expertos 2, 6, 7, 11, 13).
- **`ui/models/TaskScreenModel.kt` — `checkAndAwardAchievements`** — el
  conteo de tareas completadas para logros ahora usa `taskHistory` (sin
  entradas fantasma, incluye compleciones reales con 0 puntos por
  penalización) en vez de `assignments` filtradas por `pointsAwarded > 0`
  (Experto 8).

### Privacidad / seguridad

- **`network/TaskRepository.kt` — `getComments`** — migrado a
  `client.listAllDocuments(...)` (paginado), igual que el resto de
  colecciones — corrige tanto la carga normal de comentarios como
  `anonymizeMemberComments` (Expertos 2, 10 — CRÍTICO desde el ángulo RGPD).
- **`network/MemberRepository.kt`** (nuevo `invalidateAllCurrentMembers`),
  **`network/FirestoreRepository.kt`** (delegado), **`ui/models/GoogleAuthManager.kt`**
  (`signOut()` la invoca) — la caché de "miembro actual" se invalida
  también al cerrar sesión, no solo al abandonar/borrar un hogar, evitando
  atribución incorrecta de actividad en dispositivos familiares compartidos
  (Expertos 9, 10).

### Programador senior / concurrencia (CancellationException)

- **`App.kt`** (3 puntos) y **`ui/screens/EditTaskScreen.kt`** (1 punto) —
  los `LaunchedEffect` con `catch (_: Exception)` ahora relanzan
  `CancellationException` primero, por consistencia con el resto del árbol
  (Experto 12).

### UX / i18n / dead code

- **`ui/models/ProfileScreenModel.kt`**, **`ui/models/TaskCommentsScreenModel.kt`** —
  fallbacks de nombre "Usuario"/"Miembro" hardcodeados en español migrados a
  `AppStrings` (`profile_default_name`, `task_comment_default_author`)
  (Experto 5).
- **`ui/i18n/AppStrings.kt`** — clave huérfana `household_task_section_error`
  eliminada (ES/EN) (Experto 5).
- **`ui/screens/TaskListScreen.kt`**, **`ui/screens/CreateTaskScreen.kt`** —
  imports muertos `KeyboardArrowUp`/`KeyboardArrowDown` eliminados (Experto 4).

**Archivos tocados (13)**:
```
composeApp/src/commonMain/kotlin/org/taskhub/App.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/TaskRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/ExpandableSectionHeader.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/GoogleAuthManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/ProfileScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskCommentsScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt
```

## Verificación (OBLIGATORIO)

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL in 28s` — sin errores; solo warnings de deprecación
preexistentes (Google Sign-In, Vibrator, EncryptedSharedPreferences/MasterKey),
ninguno introducido por esta ronda.

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL in 18s` — **160 tests, 0 fallos** (mismo recuento que
antes de esta ronda; no se añadieron tests nuevos, ver huecos identificados
por Experto 13 para una futura ronda).

## PROPUESTAS pendientes — resumen para el usuario

### Integridad de datos / concurrencia (requieren decisión de producto)

1. `completeAssignment` sin protección de concurrencia sobre el documento de
   TAREA en sí (distinto del PATCH de sincronización, ya protegido) —
   PENDIENTE-DESDE-V6.
2. `undoTaskCompletionAssignments` puede fallar en localizar la asignación
   del ciclo siguiente si el usuario edita la tarea entre completar y
   deshacer (Exp. 8, NUEVO, borde estrecho).
3. `revertTaskCompletion` no restaura `nextDueAt` al deshacer, pudiendo
   afectar el cálculo de puntualidad de la siguiente compleción real
   (Exp. 8, NUEVO).
4. `reassignTaskCompletion` transfiere `task.points` (config), no los
   puntos realmente otorgados (Exp. 8, MENOR).
5. `redeemReward` permite saldo negativo con canjes concurrentes — la regla
   v7 no lo cubre (Exp. 12, confirmado sin cambios).
6. `HouseholdRepository.reconcileHouseholds` con race de última-escritura-gana
   en la caché local de hogares (Exp. 6, NUEVO, autocurable).
7. `CalendarSyncManager` sin protección contra creación duplicada de
   calendario en carreras del mismo dispositivo (Exp. 12, NUEVO, cosmético).

### Privacidad / seguridad (requieren cambio de esquema o reglas)

8. `authorName` de comentarios PRE-existentes (antes del fix de memberId)
   sigue sin poder anonimizarse — límite de esquema documentado.
9. `rewardRedemptions`/`taskHistory` sin `memberId == auth.uid` — permite
   forjar registros atribuidos a otro miembro sin ser `trusted` (Exp. 9,
   NUEVO, requiere despliegue de reglas).
10. Google Calendar access token — ya cifrado; sin cambios pendientes.
11. Sin retención de datos ni age-gating real (propuesta legal/producto).

### Arquitectura / rendimiento (refactors de mayor superficie)

12. `FirestoreRepository.kt` sigue en 1920 líneas — el fix de arquitectura
    de la ronda anterior no redujo el tamaño neto (Exp. 7, cifra corregida).
13. `TaskScreenModel.kt` "mini god ScreenModel" (núcleo de ~1150 líneas
    intacto tras el split parcial) — PENDIENTE-DESDE-V3.
14. `HomeScreenModel` con dos definiciones divergentes de "tarea pendiente"
    en el mismo objeto (Exp. 7, NUEVO).
15. `HomeScreenModel.loadHouseholdPreview` duplica el fetch de
    `loadAllTasks()` (2N en vez de N lecturas con N hogares) — el fix de la
    ronda anterior corrigió la violación de capas pero no esta duplicación
    (Exp. 11, CRÍTICO de rendimiento, NUEVO).
16. `StatsScreenModel.loadStats`: `getMemberAchievements` sin paralelizar
    junto al resto (Exp. 11, NUEVO, bajo riesgo).
17. `_previewTasks` como `Map` único causa recomposición cruzada en la
    `LazyColumn` de `HomeScreen` (Exp. 11, MENOR).

### Estética / UX de menor prioridad

18. Ilustraciones de estado vacío en solo 2/6 pantallas, sin seguir el tema
    (Exp. 1) — sin cambios desde v2/v3.
19. Adopción parcial de `ShimmerList` (Exp. 1) — sin cambios.
20. `QrCodeImage.onError` con string hardcodeado que nunca se muestra
    (Exp. 5, NUEVO).
21. Nombre `DestructiveConfirmDialog` algo contradictorio con
    `destructive=false` — nit de naming (Exp. 4, NUEVO).
22. `role=Role.Button`/`stateDescription` explícito ausente en varios
    controles clicables custom (Exp. 3, NUEVO, pulido menor).
23. `SemanticColors.onSuccess`/`onInfo` en dark sin uso real (Exp. 1/3,
    informativo).

### Cobertura de tests (solo informa, Exp. 13)

24. Ningún `ScreenModel` tiene test — requiere extraer interfaces en los
    repos o fakes manuales (no hay mocking framework en el proyecto).
25. `TaskCsvExporter` sin test — barato de cerrar, mismo patrón que
    `AssignmentCompletionRulesTest`.
26. Lógica corregida de `checkAndAwardAchievements` extraíble a función pura
    testeable sin mocks.
