# Panel de expertos v4 — 13 especialistas, foco en regresiones + recurrencia (2026-09-02)

Cuarta edición del panel de expertos de Task Hub. A diferencia de v1-v3, esta
ronda se centró en lo que los encargos posteriores a v3 aplicaron
(`10-recurrencia-nucleo.md`, `11-privacidad-seguridad.md`,
`12-arquitectura-rendimiento.md`, `13-ux-consistencia.md`) — verificar que
quedó bien hecho y cazar regresiones — en vez de repetir una auditoría
genérica desde cero. Versión de partida: 0.7.25, HEAD `14bdb23`.

Metodología: coordinador (esta sesión) + 13 subagentes en paralelo, uno por
especialista, cada uno de solo lectura con mandato independiente. 6 de los 13
agentes de la primera tanda cayeron por límite de sesión de la API a mitad de
ejecución y se relanzaron con el mismo mandato en una segunda tanda — los 13
informes finales están completos.

## Hallazgo destacado del panel

La concurrencia optimista añadida en el encargo de recurrencia
(`currentDocument.updateTime` en `completeTask`/`completeAssignment`) tenía un
fallo de diseño real: la precondition se calculaba sobre una lectura *recién
hecha por la propia función*, así que siempre se cumplía a sí misma — no
protegía contra el caso común de dos dispositivos completando la misma tarea
con minutos de diferencia (solo contra una carrera de milisegundos entre el
GET y el PATCH de la propia función). Combinado con un segundo bug (un fallo
de red en el paso "regenerar la siguiente asignación" propagaba `Error` pese a
que los puntos ya se habían otorgado, invitando a reintentar y duplicar el
premio), el sistema de puntos podía duplicarse en escenarios nada exóticos.
**Corregido en esta ronda** — ver "Foco de recurrencia" más abajo.

También se encontró un bloqueante de privacidad real: la regla `firestore.rules`
de `delete` sobre `members/{mid}` no permitía al propio dueño del documento
borrarlo, así que cualquier miembro con rol `"child"` (el rol por defecto de
todo el que se une por código de invitación) recibía `403` al intentar
abandonar un hogar compartido — y el nuevo flujo "eliminar cuenta" reutiliza
esa misma ruta, tragándose el fallo en silencio y reportando éxito falso.
**Corregido en esta ronda.**

## Resumen — hallazgos por experto y severidad

| # | Experto | Crítico | Alto | Medio | Bajo/Menor |
|---|---|---|---|---|---|
| 1 | Estética | 0 | 0 | 2 | 3 |
| 2 | Funcionalidad | 1 | 2 | 3 | 0 |
| 3 | Accesibilidad | 0 | 0 | 1 | 2 (PROPUESTA) |
| 4 | UI/Componentes | 0 | 0 | 1 | 4 |
| 5 | UX | 0 | 4 | 2 | 0 |
| 6 | Programador senior | 0 | 0 | 2 | 1 |
| 7 | Jefe de arquitectura | 0 | 0 | 2 | 2 (info) |
| 8 | QA/bugs | 1 | 2 | 1 | 0 |
| 9 | Seguridad | 0 | 0 | 2 | 4 |
| 10 | Privacidad/RGPD/menores | 1 | 2 | 2 | 1 |
| 11 | Rendimiento | 0 | 1 | 1 | 3 |
| 12 | Fiabilidad red/offline | 1 | 1 | 1 | 0 |
| 13 | Cobertura de pruebas | — | — | — | top-10 (diagnóstico) |

Nota de solapamiento: varios expertos (Funcionalidad, QA, Fiabilidad de red)
llegaron de forma independiente al mismo cluster de bugs de duplicación de
puntos en `completeTask`/`completeAssignment` desde ángulos distintos — se
tratan como UN solo cluster de fix en la sección de recurrencia, no 3 fixes
separados, para evitar inflar el recuento.

## Tabla top-10 impacto / esfuerzo

| # | Hallazgo | Severidad | Esfuerzo | Estado |
|---|---|---|---|---|
| 1 | Duplicación de puntos: precondition tautológica + reintento tras fallo en regenerar asignación | CRÍTICO | Medio | **Aplicado** |
| 2 | `firestore.rules`: miembro no puede borrar su propio documento (`leaveHousehold`/eliminar cuenta rotos para rol "child") | CRÍTICO | Bajo | **Aplicado** (REGLAS DESPLEGADAS — ruleset `29319b00`) |
| 3 | `leaveHousehold` deja hogares huérfanos e imborrables si el último en salir no es owner | ALTO | Bajo | **Aplicado** |
| 4 | `invites/{code}` no se borra en `deleteHousehold` (código de invitación fantasma) | ALTO | Bajo | **Aplicado** |
| 5 | Mensajes de conflicto hardcodeados en español + UI no se refresca tras conflicto | ALTA (UX) | Bajo | **Aplicado** |
| 6 | Selector semanal: se puede desmarcar el último día (estado ambiguo que el encargo pedía evitar) | ALTA (UX) | Bajo | **Aplicado** |
| 7 | `TaskCard` recibe lambdas inestables — anula memoización de `TaskListContent` | MEDIO-ALTO | Bajo | **Aplicado** |
| 8 | `listAllDocuments` sin tope de páginas (bucle sin fin ante backend anómalo) | MEDIO | Bajo | **Aplicado** |
| 9 | Asignación regenerada a miembro ya eliminado (rotación no purga `assignmentRotation`) | ALTO | Medio-Alto | PROPUESTA |
| 10 | `deleteHousehold` best-effort puede borrar el hogar dejando subcolecciones huérfanas si fallan borrados intermedios | ALTO | Medio | PROPUESTA |

---

## Experto 1 — Estética / Diseño visual

### IMPORTANTE

1. **Parpadeo de topbar en las 9 pantallas anidadas.** Cada pantalla crea su
   propia instancia de `HouseholdScreenModel` y carga el nombre del hogar por
   red; el subtítulo aparece con un salto tras el primer frame. Antes ocurría
   en 2 pantallas, ahora en 9 (por la propagación del encargo 13).
   **PROPUESTA** — el fix correcto (cachear el nombre en `HouseholdStore`, que
   ya guarda `SavedHousehold.name` localmente) requiere extraer un helper
   compartido (`rememberHouseholdName`) para no duplicar la lectura de caché
   una 8ª vez en 7 archivos — la misma abstracción que el Experto 4 dejó como
   PROPUESTA. Se deja pendiente de decisión (¿merece la pena la abstracción
   nueva por un parpadeo cosmético?).
2. **"Eliminar cuenta" sin distinción visual suficiente** — mismo botón que
   "Política de privacidad", sin cabecera de sección, con un emoji `🗑️` en el
   texto en vez de un icono real. **Aplicado**: envuelto en su propia
   `SettingsSection(title = "Privacidad y datos")`, emoji sustituido por
   `Icon(Icons.Default.Delete)` (coherente con el patrón ya usado en
   `HouseholdMemberList`/`HouseholdScreen`) — `SettingsSheet.kt`, nuevas claves
   i18n `settings_privacy_data_title` (ES/EN).

### MENOR — sin aplicar (cosmético, bajo impacto)

3. Icono de "eliminar miembro" con `tint = colorScheme.error` explícito,
   mientras otros `IconButton(Delete)` de la app heredan el tint por defecto —
   tercera variante del mismo patrón. PROPUESTA (decisión de sistema de
   diseño).
4. Riesgo de overflow en la fila de acciones de `MemberCard` con `fontScale`
   alto (no verificado en dispositivo real). PROPUESTA.
5. `RecurrenceNextPreview` puede mostrar una fecha que no coincide con cuándo
   realmente vence la tarea si `recurrenceDays` queda vacío — **cerrado
   indirectamente** por el fix de UX #6 (ya no se puede llegar a
   `recurrenceDays` vacío desde la UI).

---

## Experto 2 — Funcionalidad (flujos end-to-end)

### CRÍTICO

1. **Duplicación de puntos por carrera cross-función `completeTask` ↔
   `completeAssignment`.** `TaskScreenModel` se registra `factory` en Koin, así
   que `TaskListScreen` y `TaskDetailScreen` tienen instancias distintas (con
   `_actionState` propio) — la reentrancia solo protege dentro de cada
   pantalla, no entre ellas. Cada función solo protegía SU PROPIO documento
   (tarea o asignación) con `currentDocument.updateTime`, no el del otro flujo.
   **Aplicado como parte del cluster de recurrencia** — ver sección dedicada
   más abajo (el fix de "freshness check" del Experto 12 cierra este vector
   también, al comparar contra el estado que tenía el *caller*, no solo contra
   sí mismo).

### ALTO

2. **Asignación fantasma cuando `assignmentRotation` apunta a un miembro ya
   eliminado.** `deleteMember` es soft-delete y no purga `assignmentRotation`
   de las tareas existentes; la siguiente regeneración crea una asignación
   real a nombre de alguien ya invisible en `getMembers`. **PROPUESTA** —
   requiere iterar todas las tareas del hogar al eliminar un miembro (cambio
   más amplio que un fix localizado); impacto real es una asignación confusa,
   no pérdida de puntos.
3. **Sync de Calendar diferido, no huérfano permanente.** La asignación
   regenerada no dispara sync inmediato con Google Calendar, pero
   `CalendarSyncManager.reconcile()` la crea igualmente al reabrir
   `HouseholdScreen`/`PersonalSpaceScreen` — mitigado, no crítico. PROPUESTA
   de mejora (sync inmediato) de baja prioridad.

### MEDIO — PROPUESTA (fuera de alcance de esta ronda)

4. Rotación nunca gobierna el primer ciclo de una tarea nueva (`CreateTaskScreen`
   no expone `assignmentRotation`).
5. Slots de rotación para los 7 días sin relación con `recurrenceDays`/`frequency`
   en `EditTaskScreen` — confuso pero inerte (no rompe nada).
6. **Hogar compartido puede quedar sin owner tras "eliminar cuenta"** si el
   usuario que se borra era el único admin — `leaveHousehold` no reasigna rol.
   Requiere decisión de producto (¿a quién ascender? ¿bloquear el borrado de
   cuenta en ese caso con un aviso?).

### Verificado correcto (sin regresión)

- Fallback `nextDueAt=null → dueDate=0` para tareas migradas: se auto-puebla
  en la primera compleción o edición, solo exime UN ciclo, no todos los
  futuros.
- Selector semanal premarcado: funciona correctamente en el ciclo
  Semanal→Diario→Semanal.
- Paginación (`listAllDocuments`): transparente para todos los callers, sin
  ningún caller roto por el cambio de "una petición" a "N páginas internas".
- Flujo "eliminar cuenta": orden correcto (datos → cuenta Auth), navega a
  `HomeScreen` con bootstrap anónimo inmediato.
- Botón "eliminar miembro": gateado a `isAdmin && !isSelf`, refresca la lista,
  soft-delete correctamente excluido de `getMembers`.

### Estado del foco de recurrencia (Experto 2)

**Con bugs — parcialmente correcto en el momento de la auditoría, corregido
en esta ronda.** El núcleo (nextDueAt persistido, regeneración unificada
respetando `assignmentRotation`, penalización basada en `nextDueAt`, selector
premarcado) es una mejora real y bien implementada. Los dos bugs reales
(duplicación de puntos cross-función, rotación con datos huérfanos tras
eliminar miembro) no estaban cubiertos por el encargo original. El primero
se corrige en esta ronda; el segundo queda como PROPUESTA.

---

## Experto 3 — Accesibilidad (WCAG AA + Android)

Ratios verificados con `wcag_contrast.py` sobre los hex reales de
`Theme.kt`/`SemanticColors.kt` en los 6 combos (3 temas × claro/oscuro).

### IMPORTANTE — Aplicado

1. **Error de "eliminar cuenta" sin anuncio para lector de pantalla.** El
   `Text` de error no estaba marcado como región viva; TalkBack solo lo leía
   si el usuario exploraba manualmente hasta ahí, en el flujo de borrado de
   cuenta irreversible. **Aplicado**: `Modifier.semantics { liveRegion =
   LiveRegionMode.Polite }` en `SettingsSheet.kt`.

### PROPUESTA (decisión de producto/diseño)

2. "Eliminar cuenta" con una sola confirmación pese a ser al menos tan
   irreversible como "eliminar hogar" (que tiene doble confirmación). Ver
   también hallazgo UX #3 (mismo punto, coordinado).
3. Estado seleccionado de los `FilterChip` (frecuencia, días, penalización)
   depende mayormente del relleno de color — el borde de M3 sí aporta una
   señal adicional, pero un `leadingIcon` de check reforzaría WCAG 1.4.1.
   4 sitios en `CreateTaskScreen`/`EditTaskScreen`.

### Confirmado sin regresión

Contraste del subtítulo de `TaskHubTopBar` (9.12–11.18:1 según tema, PASA
holgado en los 6 combos), `RecurrenceNextPreview` sin el patrón `.copy(alpha=)`
que v3 corrigió, botón "eliminar miembro" con `contentDescription` y
touch-target correcto, chips de día de recurrencia con `contentDescription`
completo (coexiste bien con el premarcado de 7 días), contraste de chips
seleccionados (4.51–9.22:1, todos PASA), texto de advertencia "eliminar
cuenta" (5.95–12.37:1), memoización de `TaskListContent` sin romper
reduce-motion ni orden de lectura de TalkBack.

---

## Experto 4 — UI / Diseño de componentes

### MEDIO

1. **Duplicación mecánica de 7×5 líneas** para obtener/pedir el nombre del
   hogar en topbar, copiada literalmente en `CreateTaskScreen`,
   `EditTaskScreen`, `TaskDetailScreen`, `ExploreScreen`,
   `NotificationListScreen`, `CreateRewardScreen`, `MemberRewardScreen`.
   PROPUESTA (extraer `rememberHouseholdName` — mismo hallazgo que Estética
   #1, coordinado).

### MENOR — 2 aplicados, 2 propuestas

2. Diálogo de cambio de rol reimplementa manualmente un `AlertDialog` en vez
   de generalizar `DestructiveConfirmDialog` (que fuerza color de error,
   inadecuado para una acción no destructiva). PROPUESTA (cambia la API del
   componente compartido).
3. Nombre de parámetro obsoleto `roleChangePending` en `MemberCard`, que ahora
   también gobierna el nuevo botón "eliminar miembro". **Aplicado**:
   renombrado a `actionPending` en `HouseholdMemberList.kt`.
4. `RecurrenceNextPreview` recalcula `nextOccurrence` en cada recomposición
   sin memoizar (coste acotado pero inconsistente con el criterio de
   memoización aplicado al resto de la pantalla). **Aplicado**: envuelto en
   `remember(frequency, recurrenceDays, recurrenceDay)`.
5. `SettingsSheet` acumula demasiadas responsabilidades (ahora incluye un
   flujo de negocio RGPD completo). PROPUESTA (extraer `DeleteAccountSection`
   — refactor estructural).

### Verificado sin hallazgo

Claves de `remember` en `TaskListContent` correctas y completas (`sort`
correctamente excluida de `tasksWithStatus`, incluida en `groups`); flujo
"eliminar miembro" reutiliza correctamente `DestructiveConfirmDialog`; API de
`householdMemberList(...)` sin rotura de compatibilidad; i18n completo ES+EN
para todas las claves nuevas; sin colores hardcodeados nuevos, sin iconos
fuera de `material-icons-core`.

---

## Experto 5 — UX / Experiencia de uso

### ALTA — 4 hallazgos, todos aplicados

1. **Mensajes de conflicto de concurrencia hardcodeados en español**,
   ignorando el idioma de la app. **Aplicado**: `TaskScreenModel.kt` detecta
   `TaskCompletionConflictException`/`AssignmentCompletionConflictException`
   específicamente y usa `AppStrings.get("task_completion_conflict_error",
   lang)` en vez de `e.message`. Nueva clave i18n ES/EN.
2. **Tras el error de conflicto, la pantalla no se refresca** — el usuario se
   queda con datos obsoletos y puede reintentar a ciegas. **Aplicado**: el
   mismo catch llama a `loadTasks`/`loadTaskDetail` cuando el error es de
   conflicto.
3. "Eliminar cuenta" con una sola confirmación pese a ser al menos tan grave
   como "eliminar hogar" (que tiene doble confirmación). **PROPUESTA** — no
   aplicado (añadir fricción a un flujo ya construido es una decisión de
   producto, coordinado con Accesibilidad #2).
4. **Selector semanal: nada impedía desmarcar los 7 días**, reproduciendo el
   estado ambiguo que el propio encargo de recurrencia pedía evitar por UI —
   y con `RecurrenceNextPreview` mostrando una fecha activamente engañosa en
   ese estado. **Aplicado**: desmarcar el último día vuelve a marcar los 7
   automáticamente, en `CreateTaskScreen.kt` y `EditTaskScreen.kt`.

### MEDIA — 2 hallazgos, ambos aplicados

5. "¿Eliminar miembro?" no explicaba que sus tareas asignadas quedan a nombre
   de "Miembro eliminado" (soft-delete, no borra las asignaciones). **Aplicado**:
   texto de confirmación ampliado (`member_remove_confirm_text`, ES/EN).
6. El premarcado de 7 días no cubría tareas semanales legado ya guardadas con
   `recurrenceDays` vacío al ABRIRLAS para editar (el `onClick` del chip
   "Semanal" nunca se dispara si la frecuencia ya era "weekly" al entrar).
   **Aplicado**: `EditTaskScreen.kt` inicializa `recurrenceDays` a los 7 días
   si `task.frequency == "weekly" && task.recurrenceDays.isEmpty()`.

---

## Experto 6 — Programador senior / Calidad de código

### MEDIO — ambos aplicados

1. **`listAllDocuments` sin límite de seguridad de iteraciones** — mismo
   patrón defensivo que `RecurrenceRules.nextOccurrence` (`safety < 14`) no se
   aplicó aquí. **Aplicado**: tope de 200 páginas (`FirestoreClient.kt`).
2. **`updateMemberStreak` es la única mutación de `MemberRepository` que no
   invalida `taskCache`** (deuda heredada del archivo pre-split, movida tal
   cual en la extracción). **Aplicado**: `taskCache.clearMembers(householdId)`
   añadido.

### BAJO

3. `catch (_: Exception)` sin relanzar `CancellationException` en 3 puntos de
   `MemberRepository.kt` (mismo hueco ya documentado en v3 para
   `HouseholdRepository.kt`, propagado por copia mecánica a un tercer
   archivo). **Aplicado** en los 3 puntos de `MemberRepository.kt`
   (`createMember` dedup, `resolveCurrentMemberUncached` ×2) — los puntos
   restantes en `HouseholdRepository.kt`/`FirestoreRepository.kt` quedan como
   PROPUESTA de pasada dedicada (ya documentado en v3, sin cambios).

### Verificado sin hallazgo

`MemberRepository` no duplica lógica con la fachada; lambdas
`getLocalId`/`currentUserIdentities` no capturan estado obsoleto;
`completeTask`/`completeAssignment`/`regenerateNextAssignment` bien
cohesionados; `RecurrenceRules` puras y testeadas; `SecureStore` sin ruta NO
documentada de fallback sin cifrar; memoización de `TaskListContent`
correctamente parametrizada, sin código muerto.

---

## Experto 7 — Jefe de arquitectura

### Veredicto por subsistema (comparado con v3, partida 0.7.25)

| Subsistema | Veredicto v3 | Veredicto v4 | Tendencia |
|---|---|---|---|
| `FirestoreRepository` / god object | En riesgo, mejor delimitado | **Sano** — 1421 líneas, sin bloque de dominio sin dividir | Mejora, objetivo cumplido |
| DI/Koin | Sano | **Sano, confirmado** — `MemberRepository` NO registrado en Koin, composición interna | Igual |
| Ciclos de dependencia | Sano | **Sano** — lambdas, mismo patrón que `HouseholdRepository` | Igual |
| Mapeo DTO↔dominio | En riesgo (2/7 tipos) | **Sin cambio** — la extracción no lo tocó | Sin cambio |
| Escalabilidad/paginación | En riesgo | **Mejora parcial, real pero incompleta** — ver hallazgo 1 | Mejora parcial |
| Gestión de estado (ActionState) | No evaluado | **Sano** — `MemberActionState` sigue el mismo patrón ya establecido por `RewardActionState`/`TaskActionState` | Consistente |
| Cascade-delete | No existía | **Patrón coherente, techo de rendimiento no evaluado** | Riesgo nuevo, acotado |

### MEDIO/PROPUESTA

1. **`getTaskHistory` y `getMembers` quedaron fuera de la paginación** —
   `getTaskHistory` es la colección de mayor riesgo real (crece sin techo
   natural, a diferencia de `members`/`tasks`). El encargo 12 pidió
   explícitamente `getTasks`/`getAssignments`/`messages`, cumplido tal cual;
   `getTaskHistory` se quedó fuera sin quedar documentado como pendiente.
   PROPUESTA de bajo coste (mismo patrón de una línea, 4 veces ya aplicado).
2. **`deleteHousehold` borra documentos secuencialmente, no en paralelo**,
   pese a que el patrón paralelo (`coroutineScope`/`async`/`awaitAll`) ya
   existe en el mismo módulo (`HouseholdRepository.reconcileHouseholds`).
   Para un hogar con mucho `taskHistory`, esto son miles de round-trips en
   serie con la UI bloqueada. PROPUESTA (coste bajo, beneficio alto para el
   caso de borrar cuenta/hogar con historial real).

### BAJO/INFO

3. Members/Points sigue siendo el bloque que más responsabilidades cruzadas
   orquesta en la fachada — evaluado explícitamente si debería extraerse un
   `TaskCompletionService`: **no se recomienda**, no reduciría el
   acoplamiento real, solo movería código.
4. `TaskListContent` memoizado pero sin `@Immutable`/`@Stable` en los modelos
   compartidos — mejora parcial válida, no agota el espacio de optimización.
   PROPUESTA de menor prioridad.

### Resumen del experto

El encargo de arquitectura se aplicó fielmente: `MemberRepository` extraído
con el mismo patrón que sus 4 predecesores, sin bean de Koin propio, sin
ciclo de dependencia, paginación añadida donde se pidió con contrato público
sin cambios. El "god object" ya no tiene un bloque de dominio sin dividir.
Los dos huecos reales (`getTaskHistory` sin paginar, cascade-delete
secuencial) son locales y de bajo coste — ninguno es un bug activo hoy
(hogares reales pequeños), pero son la clase de deuda que duele exactamente
cuando un hogar crece.

---

## Experto 8 — QA / Detección de bugs

### CRÍTICO — Aplicado

1. **Reintentar `completeTask`/`completeAssignment` tras un fallo en
   "regenerar siguiente asignación" duplica los puntos.** El paso 5
   (`regenerateNextAssignment`) no estaba envuelto en try/catch, a diferencia
   del paso 4 justo encima. Un timeout de red ahí (nada exótico) propagaba
   `Error` pese a que los puntos YA se otorgaron; el usuario reintenta
   razonablemente y `addMemberPoints` se ejecuta una segunda vez. **Aplicado**:
   mismo patrón best-effort (`catch (CancellationException) { throw e } catch
   (_: Exception) {}`) en ambas funciones — ver cluster de recurrencia.

### ALTO

2. **`deleteHousehold` borra el documento del hogar aunque hayan fallado
   borrados individuales de subcolecciones** → huérfanos irrecuperables,
   contradice el objetivo del propio cambio (RGPD). **PROPUESTA** — requiere
   decidir la política (¿no borrar el hogar si hubo fallos? ¿reintentar?),
   fuera del criterio "fix localizado sin decisión del usuario".
3. **`completeTask` empareja "quién completa" con la asignación por
   `memberId`, no por ciclo/fecha** — si Alice completa una tarea asignada a
   Bob (permitido, cualquier miembro puede completar cualquier tarea), la
   asignación real de Bob nunca se marca completada (queda "assigned" para
   siempre) y la regeneración usa a Alice como fallback de rotación.
   **PROPUESTA** — el fix requiere decidir semántica de matching cuando hay
   varias asignaciones "assigned" simultáneas (una tarea puede asignarse a
   varios miembros a la vez); aplicarlo sin esa decisión arriesga introducir
   un bug distinto (marcar como completada la asignación de otra persona que
   aún no ha hecho nada).

### MEDIO — PROPUESTA

4. Editar una tarea recurrente asignada resetea `dueDate` de la asignación a
   0 (si no hay "fecha límite manual"), desactivando silenciosamente la
   penalización por retraso de ese ciclo. Requiere pasar `nextDueAt` recién
   calculado a `replaceAssignments` en vez de `0` — cambia el contrato de
   `updateTask`, se deja como PROPUESTA para revisar con más superficie de
   pruebas.

### Confirmado sin bug

Bifurcación de `regenerateNextAssignment` por cambio de rotación a mitad de
ciclo: no alcanzable en la práctica (`replaceAssignments` siempre borra las
asignaciones "assigned" antes de crear las nuevas). Paginación en los bordes
exactos (300/301/600 documentos): sin bug, el corte depende de
`nextPageToken` del servidor, no de un heurístico local. Migración de
refresh tokens a `SecureStore`: sin duplicado ni pérdida de sesión. Selector
semanal premarcado: ciclo Semanal→Diario→Semanal correcto.

---

## Experto 9 — Seguridad (AppSec / OWASP MASVS)

### Verificación de lo aplicado en encargo 11

| Punto | Veredicto |
|---|---|
| Regla M2 (`firestore.rules`, límites en create de `members/{mid}`) | **Correcto** — simétrico entre ramas owner/admin y auto-alta, no rompe altas legítimas. Caveat: `update` bajo `isTrusted` sigue sin restricción de campos completa (limitación ya conocida, no introducida aquí). |
| Cascade-delete (`deleteHousehold`) | **Correcto** desde el ángulo de seguridad — hoja-primero, best-effort sin reintentos (sin vector de auto-DoS), gateado por `isOwner`. |
| Eliminar cuenta | Funcionalmente correcto (orden datos→cuenta). Hallazgo: no exige reautenticación reciente antes de la acción — consistente con el resto de la app, oportunidad de hardening. |
| Cifrado de tokens (SecureStore) | **Correcto** en diseño (Android EncryptedSharedPreferences/AES256_GCM, iOS Keychain, JVM AES-256-GCM). Migración borra el valor legado tras copiarlo. `backup_rules.xml`/`data_extraction_rules.xml` sin rutas nuevas sin cubrir. |

### MEDIO

1. Android cae silenciosamente a `Settings()` sin cifrar si el Keystore
   falla, sin logging. **Aplicado**: `Log.w` en el catch de
   `SecureStore.android.kt` (solo logcat, sin subir a Analytics).
2. `firestore.rules`: las ramas `create` de `members/{mid}` no validan
   `appreciationGiven`/`appreciationWeekStart`/`leftAt` — impacto bajo por
   redundancia con la limitación arquitectónica ya conocida (sin Cloud
   Functions no se puede validar `totalPoints` contra tareas reales).
   PROPUESTA — requiere cambio de regla adicional, bajo impacto real.

### BAJO — PROPUESTA (hardening, no bloqueante)

3. iOS Keychain no fija `kSecAttrAccessible` explícitamente (usa el default
   razonable, pero no a prueba de cambios futuros de iOS).
4. Access token OAuth de Google Calendar queda fuera de la migración a
   `SecureStore` (TTL ~1h, impacto bajo).
5. Sin reautenticación reciente antes de "eliminar cuenta".
6. `update` de `members/{mid}` bajo `isTrusted` sin restricción de campos
   completa (limitación ya documentada, no nueva).

`MemberRepository.kt` (extracción completa): sin pérdida de validaciones de
auth/ownership. Sin URLs hardcodeadas nuevas ni logs que filtren PII/tokens en
los cambios recientes.

---

## Experto 10 — Privacidad / RGPD / menores

### CRÍTICO — Aplicado

1. **`leaveHousehold` no podía borrar el propio documento de miembro si el
   rol es "child"** (el rol por defecto de casi todo el mundo). `firestore.rules`
   solo permitía `delete` a `isTrusted(hid)` (admin/owner), sin cláusula "o el
   propio dueño del documento" — a diferencia de `update`, que sí la tiene.
   Como `deleteAccount` reutiliza esta ruta y el fallo se traga en silencio
   (`catch (_: Exception) {}`, "best-effort"), la app reportaba **éxito falso**
   al usuario mientras su membresía en hogares compartidos seguía existiendo
   — incumplimiento directo del derecho de supresión (RGPD art. 17) camuflado.
   También rompía la función normal "Abandonar hogar" para cualquier
   no-admin. **Aplicado**: `allow delete: if isTrusted(hid) || request.auth.uid
   == mid;` en `firestore.rules` — **REGLAS DESPLEGADAS** (ruleset
   `29319b00-f081-48db-bf41-20d0e431afc4`, 2026-09-02).

### ALTO — Ambos aplicados

2. **Orden de operaciones en `leaveHousehold` dejaba hogares huérfanos e
   imborrables** cuando el último en salir no era el owner: borraba primero
   el propio documento de miembro (perdiendo `isMember`) y comprobaba después
   si el hogar quedaba vacío para borrarlo — para entonces ya no tenía
   permiso. **Aplicado**: reordenado — si todos los miembros restantes son
   nuestros, se llama a `deleteHousehold` directamente (que borra también
   nuestro propio documento dentro de su cascade) MIENTRAS aún se tiene
   `isMember`/`isOwner`.
3. **`invites/{code}` no se borraba en `deleteHousehold`** — colección de
   nivel superior sin relación padre-hijo con `households/{id}`, así que
   Firestore no la borraba sola; el KDoc de `deleteHousehold` decía cubrir
   "TODOS los datos asociados" pero no la incluía. **Aplicado**: se borra
   `invites/{household.inviteCode}` antes del resto del cascade.

### MEDIO — PROPUESTA (decisión de producto)

4. `authorName` denormalizado en `messages`/`comments` no se anonimiza al
   borrar/anonimizar un miembro — el nombre real del propio usuario que se
   borra queda visible en el histórico de chat/comentarios de hogares
   compartidos para siempre.
5. El vínculo OAuth con Google Calendar no se revoca al "eliminar cuenta" —
   la app queda como aplicación de terceros autorizada en
   `myaccount.google.com/permissions` sin que el usuario lo sepa.

### BAJO/informativo

6. Eventos ya sincronizados a Google Calendar sobreviven al borrado del hogar
   (viven en el calendario propio del usuario, no en datos de la app —
   impacto bajo).

### Veredicto: ¿el borrado real cumple ahora lo que promete `privacy.html`?

Antes de los fixes de esta ronda: **no, de forma frágil** — funcionaba para
el caso simple (usuario con solo su espacio Personal) pero fallaba
silenciosamente para el caso central de la app (hogares compartidos) en
cualquier miembro con rol "child". **Con los fixes 1-3 aplicados**, el
borrado real ahora cubre correctamente ambos casos (pendiente del despliegue
de la regla). Los hallazgos MEDIO (4-5) son limitaciones conocidas y
documentables, no bloqueantes para la promesa central de "puedes eliminar
tus datos desde la app".

---

## Experto 11 — Rendimiento / eficiencia

### MEDIO-ALTO — Aplicado

1. **`TaskCard` recibía lambdas recién creadas en cada pasada**, anulando
   buena parte del beneficio de la memoización de `TaskListContent`: el
   builder de `LazyListScope` se reejecuta entero ante cambios ajenos
   (colapsar OTRO grupo, `loadingTaskIds` de OTRA tarea), y `onClick`/
   `onComplete` construidos inline en el call-site tenían identidad nueva
   cada vez. **Aplicado**: `remember(item.task.id)`/`remember(item.task.id,
   canComplete)` en `TaskListScreen.kt`.

### MEDIO — PROPUESTA

2. `RecurrenceRules.isDueToday`/`isCompletedToday` se siguen calculando 2×
   por tarea con el filtro por defecto (`PENDING`), y como `searchQuery` es
   parte de la key del `remember`, cada pulsación en el buscador reejecuta el
   cálculo para TODAS las tareas del hogar. El encargo 12 mantuvo
   deliberadamente "comportamiento idéntico" (mover de sitio, no cambiar
   lógica) — separar en dos `remember` (estado por tarea independiente de
   `searchQuery`, filtro aparte) es un cambio algo más profundo, se deja como
   PROPUESTA de bajo riesgo para una pasada dedicada.

### BAJO/informativo (confirmado, fuera de alcance del encargo 12)

3. `getAllAssignments` sigue con patrón N+1 (1 round-trip por tarea) —
   correcto que la paginación no lo tocara, no estaba en el alcance.
4. `deleteHousehold` sigue siendo secuencial (mismo hallazgo que Arquitectura
   #2).
5. `MemberRepository.getMembers` no usa `listAllDocuments` — coherente con el
   alcance declarado del encargo (`getTasks`/`getAssignments`/`messages`).
6. `SecureStore` se construye síncronamente en la primera composición de
   `App()` (antes del splash) solo para leer el idioma guardado, que no
   necesita el `secureStore`. PROPUESTA (`by lazy` en `SettingsStore`, cambia
   el constructor de una clase usada en varios sitios).

### Veredicto explícito

**Memoización de `TaskListContent`: bien aplicada dentro de su alcance
declarado**, con el hallazgo del punto 1 como el más importante (ya
corregido). **Paginación: bien aplicada y verificablemente neutra** para el
tamaño de hogar actual — cubre exactamente lo que el encargo 12 pidió, sin
overhead perceptible hoy.

---

## Experto 12 — Fiabilidad de red / offline / sincronización

### CRÍTICO — Aplicado

1. **La concurrencia optimista nueva NO evitaba puntos duplicados en el caso
   general** — solo en la carrera de milisegundos entre el GET y el PATCH de
   la propia función. `completeTask`/`completeAssignment` volvían a leer el
   documento (`current`/`currentAssignmentDoc`) justo antes de escribir, y
   usaban ESA MISMA lectura como precondition — tautológicamente siempre se
   cumplía a sí misma. El escenario real más probable en una app familiar
   (dispositivo B con la tarea cargada desde hace minutos, sin refrescar,
   pulsa "completar" después de que A ya la completara) no quedaba cubierto.
   **Aplicado**: tras el GET fresco, se compara contra el estado que tenía el
   *caller* (`task.lastCompletedDate`/`assignment.status`, la copia que el
   ScreenModel cargó al abrir la pantalla) — si difiere, se rechaza como
   conflicto ANTES de otorgar puntos, en vez de solo proteger la escritura.

### ALTO

2. **`deleteAccount` puede borrar la cuenta Firebase Auth de forma
   irreversible tras un cascade-delete interrumpido por red** — el bucle de
   `leaveHousehold`/`deleteHousehold` por hogar es best-effort silencioso, y
   el borrado de la cuenta Auth (paso final e irreversible) no comprueba si
   el bucle anterior tuvo fallos. Para cuentas anónimas, el hogar queda
   huérfano para siempre (sin credencial futura que pueda re-autenticarse
   como su dueño). **PROPUESTA** — cambia el contrato de `deleteAccount`
   (acumular fallos y no borrar la cuenta Auth si los hubo, o reintentar con
   backoff), no es un catch localizado.

### MEDIO — Aplicado (parcialmente, vía el mismo cluster)

3. Un fallo de red en el paso NO esencial "regenerar siguiente asignación"
   revertía el resultado a `Error` y destruía el `undoState`, pese a que los
   puntos ya se habían otorgado — invitando al reintento y a la duplicación.
   **Aplicado** (mismo fix del cluster CRÍTICO de recurrencia).
4. `TaskListScreen`/`TaskDetailScreen` no refrescaban tras un error de
   compleción, dejando el botón "completar" activo sobre una tarea que el
   servidor ya tenía completada. **Aplicado** (mismo fix de UX #1/#2: recarga
   automática en el catch de conflicto).

### Confirmación de hallazgos v3 (sin cambios)

`redeemReward`/`donatePoints` TOCTOU: sigue sin cambios, ya documentado en el
propio código como limitación conocida (PROPUESTA). Sin retry/backoff
genérico ante timeout/5xx: sin cambios, PROPUESTA de mayor alcance
(decisión de política de reintentos en toda la capa HTTP).

### Confirmaciones positivas

`isOnline()` sigue distinguiendo correctamente 404/403 (hay red) de fallo de
transporte real, sin regresión. Paginación: si una página falla, la
excepción se propaga completa (sin truncado silencioso); `getTasks` cae a
caché COMPLETA, nunca mezclada. `deleteHousehold`: mejor diseño de lo
esperado — cada borrado es best-effort por documento, y si la propia
*lista* de una subcolección falla, la función propaga sin checkpoint que
perder (lo ya borrado queda borrado, reintentar "eliminar hogar" desde cero
es idempotente).

---

## Experto 13 — Cobertura de pruebas

`./gradlew :composeApp:jvmTest` (antes de aplicar los fixes de esta ronda):
**BUILD SUCCESSFUL, 85 tests, 0 fallos** — `RecurrenceRulesTest` (53, creció
desde 23 en v3), `PointsRulesTest` (18), `FirestoreParsersTest` (10),
`SecureStoreTest` (4, nuevo). Sigue en 0 la cobertura de `ui/models/*ScreenModel.kt`,
`SettingsStore` y reglas de Firestore.

### Top-10 priorizado de huecos en código NUEVO desde v3 (diagnóstico, no aplicado — el encargo no pide suite masiva)

1. `resolveCompletionOutcome`/`calculatePenalty`/`regenerateNextAssignment`
   sin ningún test — lógica de negocio más crítica del encargo 10 (puntos
   reales), pura y ya extraída como métodos privados, cero tests.
2. `completeTask`/`completeAssignment` no son testeables unitariamente tal
   como están (`HttpClient` sin engine inyectable) — ningún test cubre el
   flujo completo, incluida la concurrencia optimista nueva.
3. `isDueToday` weekly con VARIOS `recurrenceDays` marcados a la vez (p.ej.
   lunes+miércoles+viernes) — pedido explícitamente por el encargo, sigue sin
   test.
4. `endOfDueDay` sin caso de cruce de mes/año.
5. Timezone explícita no-default — ningún test de los 53 usa un `tz` distinto
   de `currentSystemDefault()` (mismo hueco #2 de v3, no cerrado).
6. `clampDayOfMonth` con `day=29`/`30` como entrada (mismo hueco #1 de v3, no
   cerrado).
7. `SettingsStore.migrateLegacyToken` sin ningún test — justo lo que pedía el
   encargo 11.4; el caso de fallo de descifrado (fallback silencioso a
   `null`) no está cubierto.
8. `resolveRotationAssignee` con `tz` no-default.
9. `MemberActionState` sin tests de transición de `ScreenModel`.
10. Regla `firestore.rules` M2 (y ahora también el fix de `delete` de esta
    ronda) — hueco conocido, no testeable con `jvmTest`; no hay
    `@firebase/rules-unit-testing` en el repo.

**Nota adicional (menor):** el número mágico `50` en `PointsRulesTest.kt`
sigue sin sustituirse por `PointsRules.WEEKLY_APPRECIATION_BUDGET` (mismo
hueco #4 de v3, no bloqueante).

---

## FOCO ESPECIAL — Estado de la recurrencia (nextDueAt + rotación + concurrencia)

### Lo que quedó bien hecho (verificado por 3 expertos independientes)

- `nextDueAt` persistido correctamente, calculado en el mismo PATCH que
  `lastCompletedDate` (sin viaje de red extra), con fallback aditivo para
  tareas migradas.
- `regenerateNextAssignment` unificado, invocado desde AMBOS flujos
  (`completeTask`/`completeAssignment`), respeta `assignmentRotation` para
  los 7 días de la semana correctamente (`resolveRotationAssignee`, pura y
  testeada).
- Penalización por retraso ahora se calcula correctamente para recurrentes
  (`resolveCompletionOutcome` usa `nextDueAt` ajustado con `endOfDueDay`, no
  `dueDate` que siempre valía 0).
- Selector semanal premarcado a 7 días al elegir "Semanal" — funciona
  correctamente en el ciclo Semanal↔Diario.
- Sync con Google Calendar: diferido (no inmediato) para la asignación
  regenerada, pero NO huérfano — `CalendarSyncManager.reconcile()` lo cubre
  al reabrir pantallas principales.
- Edge cases de timezone/DST: `RecurrenceRules` usa
  `TimeZone.currentSystemDefault()` de forma consistente en todos los
  cálculos relacionados; no se encontró un escenario reproducible de bug por
  cambio de zona horaria entre completar y la siguiente carga (aunque la
  cobertura de test con `tz` no-default sigue en 0 — Experto 13, huecos 5/8).

### Bugs encontrados y su estado tras esta ronda

| Bug | Severidad | Estado |
|---|---|---|
| Precondition de concurrencia tautológica (no detecta compleción minutos antes) | CRÍTICO | **Corregido** |
| Reintento tras fallo en "regenerar asignación" duplica puntos | CRÍTICO | **Corregido** |
| Selector semanal permitía llegar a "0 días marcados" (desde uncheck, o al editar legado) | ALTA (UX) | **Corregido** |
| Asignación fantasma si `assignmentRotation` apunta a miembro eliminado | ALTO | PROPUESTA |
| `completeTask` empareja completer↔asignación por `memberId`, no por ciclo | ALTO | PROPUESTA |
| Editar tarea recurrente asignada resetea `dueDate` de la asignación a 0 | MEDIO | PROPUESTA |
| Rotación inerte en el primer ciclo de una tarea nueva | MEDIO | PROPUESTA (gap preexistente, fuera del alcance del encargo original) |
| Sync de Calendar diferido (no inmediato) para asignación regenerada | MEDIO | PROPUESTA (mitigado por `reconcile()`) |

**Veredicto global: el núcleo de recurrencia (nextDueAt + rotación +
concurrencia) queda correcto tras esta ronda de fixes.** Los dos bugs
CRÍTICOS que hacían que la propia "concurrencia optimista" no cumpliera su
promesa (duplicación de puntos) están cerrados. Quedan bugs ALTO/MEDIO reales
pero de menor frecuencia (interacción rotación×eliminar-miembro,
completer≠asignado) documentados como PROPUESTA por requerir decisiones de
semántica de datos que no son un "fix localizado" seguro.

---

## Fixes aplicados — archivos tocados

**Integridad de puntos / recurrencia (cluster CRÍTICO, 3 hallazgos independientes → 1 fix coordinado)**
- `network/FirestoreRepository.kt` — `completeTask`: comprobación de
  "freshness" contra `task.lastCompletedDate` del caller antes de otorgar
  puntos (en vez de una precondition tautológica); paso 5
  (`regenerateNextAssignment`) envuelto en try/catch best-effort.
- `network/FirestoreRepository.kt` — `completeAssignment`: misma
  comprobación contra `assignment.status` del caller; cola
  (`regenerateNextAssignment`) envuelta en try/catch best-effort.

**Privacidad / RGPD (borrado real)**
- `firestore.rules` — `members/{mid}`: `allow delete` ahora también permite
  `request.auth.uid == mid` (propio dueño del documento). **REGLAS
  DESPLEGADAS** (ruleset `29319b00-f081-48db-bf41-20d0e431afc4`).
- `network/FirestoreRepository.kt` — `leaveHousehold`: reordenado para
  borrar el hogar completo ANTES de perder `isMember` cuando el usuario que
  sale se queda como único miembro.
- `network/FirestoreRepository.kt` — `deleteHousehold`: borra
  `invites/{household.inviteCode}` antes del resto del cascade.

**Programador senior / robustez**
- `network/FirestoreClient.kt` — `listAllDocuments`: tope de seguridad de
  200 páginas.
- `network/MemberRepository.kt` — `updateMemberStreak` invalida
  `taskCache`; 3 puntos con `catch (e: CancellationException) { throw e }`
  añadido antes del `catch (_: Exception)`.
- `storage/SecureStore.android.kt` — log (`Log.w`) en el fallback silencioso
  a almacenamiento sin cifrar ante fallo de Keystore.

**UX — mensajes de conflicto y selector de recurrencia**
- `ui/models/TaskScreenModel.kt` — `completeTask`/`completeAssignment`:
  detectan las excepciones de conflicto específicamente, usan
  `AppStrings.get("task_completion_conflict_error", lang)` y recargan
  (`loadTasks`/`loadTaskDetail`) en vez de dejar la UI con datos obsoletos.
- `ui/i18n/AppStrings.kt` — nueva clave `task_completion_conflict_error`
  (ES/EN).
- `ui/screens/CreateTaskScreen.kt` / `EditTaskScreen.kt` — el chip de día no
  permite llegar a `recurrenceDays` vacío (se re-premarcan los 7 al
  desmarcar el último).
- `ui/screens/EditTaskScreen.kt` — inicializa `recurrenceDays` a los 7 días
  si la tarea es "weekly" legado con `recurrenceDays` vacío al abrir para
  editar.
- `ui/i18n/AppStrings.kt` — `member_remove_confirm_text` (ES/EN) ampliado
  para explicar que las tareas asignadas quedan a nombre de "Miembro
  eliminado".

**Estética / Accesibilidad / UI**
- `ui/components/SettingsSheet.kt` — sección "Privacidad y datos" con
  cabecera propia (`SettingsSection`), icono real (`Icons.Default.Delete`)
  en vez de emoji, `liveRegion` en el texto de error de "eliminar cuenta".
- `ui/i18n/AppStrings.kt` — `settings_privacy_data_title` (nueva, ES/EN),
  `settings_delete_account_button` sin emoji (ES/EN).
- `ui/components/HouseholdMemberList.kt` — parámetro `roleChangePending`
  renombrado a `actionPending` (ahora también gobierna "eliminar miembro").
- `ui/components/RecurrenceNextPreview.kt` — cálculo de la próxima ocurrencia
  memoizado con `remember(frequency, recurrenceDays, recurrenceDay)`.

**Rendimiento**
- `ui/screens/TaskListScreen.kt` — `onClick`/`onComplete` de `TaskCard`
  memoizados por `item.task.id` (antes se recreaban en cada recomposición
  del bloque de `items`, anulando el skip de recomposición).

## Verificación (OBLIGATORIO)

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL in 31s` — sin errores; solo warnings de deprecación
preexistentes (Google Sign-In, Vibrator, EncryptedSharedPreferences/MasterKey
de AndroidX Security), ninguno introducido por esta ronda.

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` — 85 tests, 0 fallos (mismo recuento que antes de esta
ronda; ningún test nuevo se añadió, ninguno se rompió).

## PROPUESTAS no aplicadas — veredicto y coste/beneficio

### Recurrencia / integridad de datos
- **Asignación fantasma tras eliminar miembro con rotación activa** (Exp. 2
  #2, Exp. 8 relacionado) — coste medio-alto (iterar todas las tareas del
  hogar al eliminar un miembro), beneficio medio (confusión de UI, no
  pérdida de puntos).
- **`completeTask` empareja completer↔asignación por `memberId`** (Exp. 8
  #3) — coste medio (decidir semántica cuando hay varias asignaciones
  "assigned" para distintos miembros), beneficio medio-alto (rotación
  íntegra en hogares donde cualquiera completa las tareas de cualquiera).
- **Editar tarea recurrente asignada resetea `dueDate` de la asignación a 0**
  (Exp. 8 #4) — coste bajo-medio, beneficio medio (penalización silenciosa
  desactivada un ciclo).
- **Hogar compartido sin owner tras "eliminar cuenta"** (Exp. 2 #6) — coste
  medio (decidir política de reasignación de rol o bloqueo), beneficio
  medio (edge case, hogares con un solo admin que se borra).
- **Sync de Calendar inmediato para asignación regenerada** (Exp. 2 #3) —
  coste bajo, beneficio bajo (ya mitigado por `reconcile()`).

### Privacidad / seguridad — requieren decisión de producto o backend
- `deleteHousehold` no debería borrar el documento del hogar si hubo fallos
  en subcolecciones (Exp. 8 #2) — coste medio, beneficio alto (evita
  huérfanos RGPD), pero cambia el contrato observable ("¿qué ve el usuario si
  el borrado queda incompleto?").
- `deleteAccount` no debería borrar la cuenta Auth si el cascade-delete tuvo
  fallos de red (Exp. 12 #2) — coste medio, mismo motivo.
- `authorName` en mensajes/comentarios no se anonimiza al borrar cuenta (Exp.
  10 #4) — decisión de producto (¿reescribir a placeholder o documentar como
  limitación?).
- Revocar el vínculo OAuth de Google Calendar al eliminar cuenta (Exp. 10
  #5) — coste bajo-medio (llamada a `revoke` específica de plataforma).
- Reautenticación reciente antes de "eliminar cuenta" (Exp. 9, Exp. 3 #2) —
  hardening, no bloqueante.
- Doble confirmación para "eliminar cuenta" (Exp. 3/5, coordinado) — friction
  de producto, decisión del usuario.
- `firestore.rules`: validar `appreciationGiven`/`appreciationWeekStart`/
  `leftAt` en ramas `create` de `members/{mid}` (Exp. 9 #2) — bajo impacto
  real (redundante con limitación conocida sin Cloud Functions).

### Arquitectónicas / escalabilidad — refactors de mayor superficie
- Extraer `rememberHouseholdName` compartido para las 7+2 pantallas con
  topbar (Exp. 1 #1, Exp. 4 #1) — resolvería tanto el parpadeo como la
  duplicación de 35 líneas, pero es una abstracción nueva compartida (fuera
  de "APLICA YA" por decisión explícita del encargo).
- Paginar `getTaskHistory` (Exp. 7 #1) — coste bajo (mismo patrón ya
  aplicado 4 veces), beneficio medio-alto a futuro.
- Paralelizar `deleteAllDocuments`/`deleteHousehold` (Exp. 7 #2, Exp. 11 #4)
  — coste bajo-medio, beneficio alto para hogares con historial real.
- Generalizar `DestructiveConfirmDialog` para acciones no destructivas (Exp.
  4 #2) — cambia la API de un componente compartido.
- Extraer `DeleteAccountSection` de `SettingsSheet` (Exp. 4 #5) — refactor
  estructural, sin urgencia funcional.
- `SecureStore` perezoso en `SettingsStore` (Exp. 11 #6) — cambia el
  constructor de una clase usada en varios sitios.
- `FilterChip` con `leadingIcon` de check para no depender solo del color
  (Exp. 3 #2) — decisión de sistema de diseño, 4 sitios.
- Separar el `remember` de `isDueToday` del de `searchQuery` en
  `TaskListContent` (Exp. 11 #2) — bajo riesgo pero requiere restructurar el
  bloque, se prefiere una pasada dedicada con más margen de verificación
  visual.

## Deuda pendiente y riesgos — resumen para el usuario

1. **Regla `firestore.rules` desplegada.** El fix crítico de privacidad
   (`members/{mid}` delete) está en producción (ruleset
   `29319b00-f081-48db-bf41-20d0e431afc4`, desplegado 2026-09-02). El borrado
   real de cuenta/abandonar-hogar ya funciona en producción para miembros con
   rol "child".
2. **Dos bugs ALTO de recurrencia quedan documentados pero sin corregir**
   (asignación fantasma tras eliminar miembro con rotación; completer≠
   asignado) — de menor frecuencia que los CRÍTICOS ya cerrados, pero reales.
3. **Cobertura de tests sigue en el mismo punto que v3** para las áreas más
   sensibles (penalización/rotación puras sin test, `ScreenModel`s sin test,
   reglas de Firestore sin test de emulador) — ver top-10 del Experto 13.
4. **Deuda arquitectónica de escalabilidad conocida y acotada**:
   `getTaskHistory` sin paginar, cascade-delete secuencial — sin impacto hoy
   (hogares pequeños), a vigilar si el uso real crece.
5. **7 PROPUESTAs de privacidad/seguridad de hardening** (reautenticación,
   revocar OAuth, anonimizar `authorName`, doble confirmación) quedan a
   decisión del usuario — ninguna es bloqueante para el cumplimiento RGPD
   central ya corregido en esta ronda.
