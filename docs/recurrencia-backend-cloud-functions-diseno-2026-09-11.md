# Diseño: módulo de recurrencia sobre Cloud Functions — FASE 1 (solo diseño)

Encargo de diseño, 2026-09-11. HEAD de partida: `d5d2296`. **No se ha tocado
código de producción** — este documento es el único cambio de este encargo.

## Contexto y motivación

La recurrencia se ha roto 4 veces en 10 días:

- `f45f880`, `c8d11e1`, `74ae44e` — parches previos (no vistos en el log
  reciente, referenciados en el encargo).
- `3f8567e` (`fix: tareas recurrentes nunca completadas quedan atrasadas tras
  el día programado`, ver `docs/recurrencia-ventana-atrasada-2026-09-12.md`) —
  el más reciente.

Patrón común a los 4: la lógica de "¿toca hoy?", rotación de asignados y
otorgamiento de puntos vive en el cliente (`RecurrenceRules.kt`,
`FirestoreRepository.completeTask`/`completeAssignment`) sobre Firestore vía
REST, **sin transacciones multi-documento**. `docs/atomicidad-commit-pendiente.md`
documenta por qué: construir el payload `:commit` (`Write`/`DocumentTransform`/
`FieldTransform`) a mano, sin acceso a un emulador para verificarlo, se evaluó
y se descartó dos veces (auditoría 2026-08-30 y este mismo documento) por
riesgo de payload mal formado sobre un sistema de puntos en producción.

Cloud Functions cambia esa restricción: el Admin SDK de Node tiene
`db.runTransaction(...)` real, probado, documentado, y verificable con el
emulador de Firestore **sin necesitar plan Blaze ni proyecto real** — el
argumento de "no puedo verificarlo" que bloqueó el `:commit` en cliente no
aplica aquí. Esto es lo que reabre el punto 1 (modelo de datos): la decisión
de 09-12 (ventana de "atrasada", sin instancias) se tomó **explícitamente sin
backend disponible** — con transacciones reales sobre la mesa, el
coste/beneficio cambia y merece revisitarse, no solo heredarse.

Convención de marcado: **[APLICA YA]** = decisión mecánica/técnica, la tomo
yo en este diseño. **[REQUIERE DECISIÓN]** = decisión de producto, visual,
legal, infraestructura o coste — la decide Liberto antes de la fase 2.

---

## 1. Modelo de datos

### Estado actual (recordatorio)

Un documento por tarea (`households/{hid}/tasks/{tid}`), con
`lastCompletedDate`/`completedBy`/`nextDueAt`/`assignmentRotation` en el
propio documento. "¿Toca hoy?" se recalcula en cada lectura vía
`RecurrenceRules.isDueOn` (función pura, sin I/O) a partir de `frequency` +
`recurrenceDays`/`recurrenceDay` + `lastCompletedDate` (o `createdAt` si
nunca se completó — ventana de "atrasada" de 09-12). No hay documento
`(taskId, fecha)` por ocurrencia; `taskHistory` es el único rastro de
compleciones pasadas, escrito solo al completar, no al programar.

### Opción A — mantener un-documento-por-tarea (statu quo + ventana)

**Coste/beneficio:**
- ✅ Cero migración: los documentos existentes ya tienen el shape correcto.
- ✅ Lecturas baratas: 1 documento por tarea, no una query por rango de fechas.
- ✅ La ventana de "atrasada" (09-12) ya resuelve el caso de UX que motivaba
  instancias ("una ocurrencia perdida no desaparece sin rastro").
- ❌ Sigue sin poder representar *excepciones por ocurrencia* (saltar un día
  concreto, moverlo, modificar puntos solo esa vez) sin el diseño de
  `exceptions/{date}` ya esbozado (no implementado) en
  `docs/recurrencia-ventana-atrasada-2026-09-12.md`.
- ❌ La transacción de "completar" sigue tocando 1 documento de tarea + 1 de
  historial + 1 de miembro + N de asignaciones — no es un solo documento
  atómico, pero **sí** cabe en una única `runTransaction` de Firestore Admin
  (límite real: 500 escrituras por transacción, muy por encima de lo que
  necesita cualquier hogar de esta app).

### Opción B — instancias/ocurrencias materializadas

**Coste/beneficio:**
- ✅ Representa excepciones (saltar/mover/modificar) de forma nativa, sin el
  documento `exceptions/{date}` ad-hoc.
- ✅ Consultas de calendario ("¿qué toca este mes?") se vuelven una query
  directa en vez de recalcular `isDueOn` para cada día visible.
- ❌ Multiplica escrituras: generar N ocurrencias futuras (¿cuántas? ¿un mes
  vista? ¿on-demand?) es trabajo adicional que no existe hoy, y complica la
  transacción de completar (ahora toca además el documento de la ocurrencia,
  no solo el de la tarea).
- ❌ Migración no trivial: tareas recurrentes existentes no tienen
  ocurrencias, habría que backfill (ver sección 6) o soportar ambos modelos
  en paralelo indefinidamente.
- ❌ El problema real detrás de los 4 fixes NO era "faltan instancias" —
  era "no hay transacciones". Cloud Functions ya resuelve eso con el modelo
  actual (Opción A). Instancias resolverían un problema *distinto*
  (excepciones por ocurrencia), que hoy no está pedido salvo como feature
  futura ya diseñada aparte.

### Veredicto **[REQUIERE DECISIÓN]**

Recomendación: **mantener el modelo Opción A** (un documento por tarea +
ventana de atrasada), moviendo `completeTask`/`completeAssignment` a una
transacción real de Cloud Functions. Motivo: el backend con transacciones
resuelve el problema que causó los 4 fixes (atomicidad), no el modelo de
datos en sí — reabrir instancias ahora sería resolver un problema no
diagnosticado como causa raíz, a cambio de una migración real. El diseño de
`exceptions/{date}` (09-12) queda como *complemento* futuro de la Opción A si
Liberto quiere excepciones por ocurrencia, no como argumento para cambiar de
modelo.

**Liberto debe aprobar**: (a) confirmar que se mantiene Opción A, o (b) pedir
instancias — en cuyo caso este diseño necesita una vuelta adicional antes de
fase 2 (la superficie de funciones de la sección 2 cambia sustancialmente).

---

## 2. Superficie de las Cloud Functions

Runtime: **Node 20** (LTS, soportado por `firebase-functions` v5+). Región
sugerida **[APLICA YA]**: `europe-west1` (Bélgica) — más cercana a España que
`us-central1` (default), reduce latencia percibida; el proyecto
`task-hub-62f98` no tiene Cloud Functions desplegadas hoy, así que no hay
región heredada que respetar.

### 2.1 `completeRecurringTask` (mínimo viable)

- **Disparador**: Callable HTTPS (`onCall`), no `onRequest` — `onCall` valida
  el token de Firebase Auth automáticamente antes de invocar el handler
  (`context.auth.uid`), evitando reimplementar verificación de idToken a
  mano.
- **Entrada**:
  ```ts
  interface CompleteRecurringTaskRequest {
    householdId: string;
    taskId: string;
    memberId: string;       // quien recibe los puntos (puede != auth.uid, ver nota)
    expectedUpdateTime?: string; // RFC3339, concurrencia optimista opcional
  }
  ```
- **Salida**:
  ```ts
  interface CompleteRecurringTaskResponse {
    completedAt: number;    // epoch millis
    pointsAwarded: number;
    onTime: boolean;
    nextDueAt: number | null;
  }
  ```
- **Transacción** (ver sección 4 para el diagrama): lee tarea + miembro +
  asignaciones del ciclo dentro de la misma `runTransaction`, valida, y
  escribe: `tasks/{tid}` (`lastCompletedDate`, `completedBy`, `nextDueAt`),
  `taskHistory/{new}` (`pointsApplied: true` directo — ya no hace falta el
  patrón "false → true" de dos escrituras porque la transacción es atómica),
  `members/{memberId}.totalPoints` (incremento), asignaciones del ciclo
  actual → `completed`, asignación del siguiente ciclo (ID determinista
  `next_{taskId}_{nextDueDate}`, igual que hoy) → creada si no existe.
- **Validación de seguridad**: verificar `context.auth != null`; leer
  `households/{householdId}/members/{context.auth.uid}` y comprobar que
  existe y `leftAt == 0` (equivalente a `isMember(hid)` de las reglas
  actuales); comprobar que `memberId` referenciado también es un miembro
  activo del mismo hogar (equivalente a `existsMemberDoc`). Quien llama no
  tiene por qué ser `memberId` — mismo comportamiento de producto que hoy
  ("cualquier miembro completa cualquier tarea", ver comentario v7 de
  `firestore.rules`).

### 2.2 `completeAssignment` (mínimo viable)

- **Disparador**: Callable HTTPS.
- **Entrada**:
  ```ts
  interface CompleteAssignmentRequest {
    householdId: string;
    taskId: string;
    assignmentId: string;
    expectedUpdateTime?: string;
  }
  ```
- **Salida**: igual forma que `CompleteRecurringTaskResponse` +
  `assignmentId`.
- **Transacción**: análoga a 2.1 pero ancla la lectura en el documento de
  asignación (`assignments/{assignmentId}`), no en `task.lastCompletedDate`
  — replica exactamente la lógica hoy repartida entre
  `FirestoreRepository.completeAssignment` y `regenerateNextAssignment`.
- **Validación**: mismo esquema que 2.1, más: `assignment.taskId == taskId`
  (evita IDs cruzados).

### 2.3 `reassignTaskCompletion` (mínimo viable)

- **Disparador**: Callable HTTPS.
- **Entrada**:
  ```ts
  interface ReassignTaskCompletionRequest {
    householdId: string;
    taskId: string;
    newMemberId: string;
  }
  ```
- **Salida**: `{ previousMemberId: string | null, pointsTransferred: number }`.
- **Transacción**: lee `task.completedBy` + el registro de `taskHistory` más
  reciente para esa `completedAt` (fallback a `task.points` si no hay
  historial, igual que hoy) dentro de la transacción, resta al miembro
  anterior, suma al nuevo, actualiza `completedBy` + el registro de
  historial. Reemplaza las 4 escrituras HTTP secuenciales actuales de
  `FirestoreRepository.reassignTaskCompletion`.
- **Validación**: `context.auth.uid` debe ser `isTrusted(hid)` (owner o
  admin) — igual que hoy en `firestore.rules` (la reasignación de
  `completedBy` sin cambiar `lastCompletedDate` ya está gateada a admin/owner
  en la regla v4+; la función replica esa comprobación leyendo el rol del
  llamador).

### 2.4 `undoTaskCompletion` (mínimo viable — sustituye la secuencia actual del cliente)

- **Disparador**: Callable HTTPS.
- **Entrada**:
  ```ts
  interface UndoTaskCompletionRequest {
    householdId: string;
    taskId: string;
    completedAt: number; // identifica sin ambigüedad qué compleción deshacer
  }
  ```
- **Salida**: `{ reverted: boolean }`.
- **Transacción**: revierte `totalPoints`/racha del miembro, borra el
  registro de `taskHistory` con ese `completedAt`, restaura
  `lastCompletedDate`/`completedBy`/`nextDueAt` previos (leídos del propio
  historial/asignaciones, no de un `UndoState` en memoria del cliente — ver
  nota de diseño abajo), revierte a `assigned` las asignaciones cerradas por
  esa compleción y borra la asignación del siguiente ciclo si sigue
  `assigned`.
- **Nota de diseño importante**: el cliente hoy guarda `UndoState` **en
  memoria** (`TaskScreenModel._undoState`) con los valores previos
  (`previousLastCompletedDate`, `previousStreak`, etc.) capturados ANTES de
  completar. Una función de servidor no tiene ese estado en memoria del
  cliente. Dos opciones:
  - **(i)** el cliente sigue mandando esos valores previos como parte del
    request (la función los usa para escribir, pero re-valida contra el
    estado real antes de aplicar — evita que un cliente modificado revierta
    a valores inventados).
  - **(ii)** el servidor deriva el estado previo leyendo el registro de
    `taskHistory` anterior a `completedAt` para esa tarea (más robusto,
    sobrevive a que el cliente cierre la app entre completar y deshacer, hoy
    imposible porque `_undoState` es volátil).
  Recomendación **[APLICA YA]**: (ii) — es estrictamente mejor (deshacer
  sobrevive a recargar la app) y no añade complejidad real (ya se lee
  `taskHistory` en la transacción para el paso de reasignación). Cambia el
  comportamiento observable: hoy "deshacer" desaparece si recargas la
  pantalla antes de pulsarlo; con (ii) podría ofrecerse más allá de esa
  sesión. Si Liberto quiere mantener el límite de "solo en la sesión
  actual" por diseño de producto, eso es **[REQUIERE DECISIÓN]** (UI: mostrar
  o no el botón "deshacer" tras recargar).

### 2.5 `reconcileMissingTaskPoints` (sugerida — reemplaza la reconciliación best-effort del cliente)

- **Disparador**: **Scheduled** (`onSchedule`, p.ej. cada 6h) + opcionalmente
  también invocable como Callable HTTPS para forzar una pasada manual desde
  un futuro panel de admin.
- **Entrada** (si Callable): `{ householdId: string }`. (Scheduled): ninguna,
  itera todos los hogares.
- **Salida**: `{ reconciled: number }`.
- **Por qué ya no hace falta desde el cliente**: hoy `TaskScreenModel.loadTasks`
  dispara `reconcileMissingTaskPoints` en cada carga — un parche para el
  hueco entre escribir `taskHistory` y otorgar puntos que las transacciones
  de 2.1/2.2 **eliminan por diseño** (todo o nada). Esta función queda como
  red de seguridad para registros **legacy** (creados antes de migrar a
  Cloud Functions, ver sección 6) o para el resto de fallos best-effort no
  cubiertos (p.ej. Google Calendar). Con Cloud Functions, no debería crecer
  después de la migración — si crece, es señal de un bug nuevo, no de una
  carrera esperada.

### 2.6 "¿Toca hoy?" — on-read, NO como función

**[APLICA YA]**: `isDueToday`/`isDueOn` **siguen siendo puras en cliente**
(`RecurrenceRules.kt`), no se convierten en función de servidor. Motivo:
- No escriben nada — no hay problema de atomicidad que resolver.
- Convertirlas en función implicaría una llamada de red por cada render de
  lista/calendario (hoy es instantáneo, sin red, por diseño — ver KDoc del
  `object RecurrenceRules`).
- El campo derivado alternativo ("mantener `isDueToday` como campo persistido
  por la función de completado") se descarta: requeriría recalcular y
  reescribir el campo en CADA cambio de reloj (medianoche), no solo al
  completar — un cron diario tocando todas las tareas de todos los hogares
  solo para mantener un booleano que ya se calcula gratis en cliente. No
  aporta nada que la Opción A del punto 1 no tenga ya.

La única función que interactúa con la ventana de "atrasada" es
indirectamente 2.1/2.2 (escriben `nextDueAt`, que es la entrada de
`isDueOn`), no una función dedicada.

---

## 3. API del cliente

### Protocolo: callable HTTPS, no REST v1 directo

**[APLICA YA]**: usar el protocolo de "callable functions" de Firebase
(`https://<region>-task-hub-62f98.cloudfunctions.net/<name>`), no re-implementar
el wire format completo del SDK cliente de Functions (que no existe para
Kotlin/KMP). El protocolo callable es HTTP+JSON simple y documentado:

```
POST https://europe-west1-task-hub-62f98.cloudfunctions.net/completeRecurringTask
Authorization: Bearer <idToken>
Content-Type: application/json

{ "data": { "householdId": "...", "taskId": "...", "memberId": "..." } }
```

Respuesta `200`: `{ "result": { ...CompleteRecurringTaskResponse } }`.
Respuesta de error: `{ "error": { "status": "PERMISSION_DENIED", "message": "..." } }`
(usa `functions.https.HttpsError` en el handler, con `code` mapeado a HTTP
status por el propio SDK de Functions — no hay que construir esa tabla a
mano).

Esto reutiliza exactamente el `idToken` que `FirestoreClient` ya mantiene
(`bearerToken`, renovado en `ensureAuth()`) — mismo header
`Authorization: Bearer $it` que usa `withAuth()` hoy para Firestore REST, sin
cambios en la gestión de sesión.

### DTOs nuevos (kotlinx.serialization) — `network/models/FunctionDtos.kt`

```kotlin
@Serializable
data class CompleteRecurringTaskRequest(
    val householdId: String,
    val taskId: String,
    val memberId: String,
    val expectedUpdateTime: String? = null
)

@Serializable
data class TaskCompletionFunctionResult(
    val completedAt: Long,
    val pointsAwarded: Int,
    val onTime: Boolean,
    val nextDueAt: Long? = null
)

@Serializable
data class CompleteAssignmentRequest(
    val householdId: String,
    val taskId: String,
    val assignmentId: String,
    val expectedUpdateTime: String? = null
)

@Serializable
data class ReassignTaskCompletionRequest(
    val householdId: String,
    val taskId: String,
    val newMemberId: String
)

@Serializable
data class ReassignTaskCompletionResult(
    val previousMemberId: String? = null,
    val pointsTransferred: Int
)

@Serializable
data class UndoTaskCompletionRequest(
    val householdId: String,
    val taskId: String,
    val completedAt: Long
)

@Serializable
data class UndoTaskCompletionResult(val reverted: Boolean)

/** Envoltorio genérico del protocolo callable: { "data": T } / { "result": R } / { "error": {...} }. */
@Serializable
data class CallableRequest<T>(val data: T)

@Serializable
data class CallableResult<R>(val result: R)

@Serializable
data class CallableError(val error: CallableErrorBody)

@Serializable
data class CallableErrorBody(val status: String, val message: String)
```

### Nuevo cliente — `network/CloudFunctionsClient.kt`

Clase nueva, paralela a `FirestoreClient`, que reutiliza el mismo `HttpClient`
Ktor y el mismo `bearerToken` (inyectar `FirestoreClient` como dependencia
para no duplicar la gestión de sesión):

```kotlin
class CloudFunctionsClient(
    private val client: HttpClient,
    private val firestoreClient: FirestoreClient,
    private val baseUrl: String // "https://europe-west1-task-hub-62f98.cloudfunctions.net"
) {
    suspend inline fun <reified T, reified R> call(name: String, data: T): R {
        val response = client.post("$baseUrl/$name") {
            with(firestoreClient) { withAuth() }
            contentType(ContentType.Application.Json)
            setBody(CallableRequest(data))
        }
        if (!response.status.isSuccess()) {
            val err: CallableError = response.body()
            throw CloudFunctionException(err.error.status, err.error.message)
        }
        return response.body<CallableResult<R>>().result
    }
}

class CloudFunctionException(val status: String, message: String) : Exception(message)
```

### Cambios en capas existentes

- **`FirestoreRepository.kt`**: `completeTask`, `completeAssignment`,
  `reassignTaskCompletion` pasan de orquestar N llamadas Firestore REST a UNA
  llamada `cloudFunctions.call<...>(...)`. `TaskCompletionConflictException`/
  `AssignmentCompletionConflictException` se mapean desde
  `CloudFunctionException` cuando `status == "ABORTED"` o `"FAILED_PRECONDITION"`
  (mismos nombres de excepción, mismo tratamiento en `TaskScreenModel` — cero
  cambio en el catch de UI). `undoTaskCompletionAssignments` +
  `revertTaskCompletion` (hoy dos llamadas separadas desde
  `TaskScreenModel.undoCompleteTask`) se colapsan en la función 2.4.
- **`TaskRepository.kt`**: `saveTaskHistory`, `markTaskHistoryPointsApplied`,
  `deleteTaskHistoryRecord` dejan de ser invocadas desde el flujo de
  completar (la función las hace dentro de su transacción) — siguen
  existiendo para otros call-sites si los hay (revisar
  `reconcileTaskPoints` legacy, sección 6). `revertTaskCompletion` (el PATCH
  directo) se elimina si 2.4 la sustituye por completo.
- **`ui/models/TaskScreenModel.kt`**: `completeTask()`
  (`TaskScreenModel.kt:525`), `undoCompleteTask()` (`:644`),
  `reassignTaskCompletion()` (`:709`), `completeAssignment()` (`:747`) — la
  llamada a `repo.completeTask(...)` etc. no cambia de firma vista desde el
  ScreenModel (sigue siendo una función suspend con los mismos parámetros),
  solo cambia qué hace `FirestoreRepository` por dentro. El código de
  `TaskScreenModel` que hoy hace lectura+comprobación+llamadas adicionales
  best-effort (Calendar sync, streak, achievements, analytics) **no cambia**
  — sigue siendo responsabilidad del cliente, fuera de la transacción del
  servidor (son efectos secundarios no críticos, igual que hoy).
- **Lecturas que siguen yendo a Firestore directo (sin cambios)**: `getTasks`,
  `getTask`, `getAssignments`, `getMembers`, `getTaskHistory`,
  `getComments`, listeners de calendario/notificaciones — nada de esto
  escribe, así que no necesita transacción ni pasa por una función. Solo las
  4-5 rutas de escritura de la sección 2 migran.

---

## 4. Transacciones y concurrencia

### Diagrama de la transacción de `completeRecurringTask`

```
db.runTransaction(async (tx) => {
  // ── FASE DE LECTURA (todas antes de cualquier escritura — regla de
  //    Firestore: una transacción no permite leer después de escribir) ──
  const taskRef = db.doc(`households/${hid}/tasks/${taskId}`);
  const taskSnap = await tx.get(taskRef);
  if (!taskSnap.exists) throw new HttpsError('not-found', 'task-not-found');
  const task = taskSnap.data();

  const memberRef = db.doc(`households/${hid}/members/${memberId}`);
  const memberSnap = await tx.get(memberRef);
  if (!memberSnap.exists) throw new HttpsError('not-found', 'member-not-found');

  const assignmentsSnap = await tx.get(
    db.collection(`households/${hid}/tasks/${taskId}/assignments`)
      .where('status', '==', 'assigned')
  );

  // ── VALIDACIÓN (equivalente al chequeo de concurrencia optimista actual) ──
  if (expectedLastCompletedDate !== undefined &&
      task.lastCompletedDate !== expectedLastCompletedDate) {
    throw new HttpsError('aborted', 'conflict');
  }

  // ── CÁLCULO PURO (reutiliza la MISMA lógica que RecurrenceRules.kt,
  //    reescrita en TS — ver nota de duplicación abajo) ──
  const outcome = resolveCompletionOutcome(task, now);
  const nextDueDate = calculateNextDueDate(task, now);
  const rotationDecision = resolveNextAssignmentDecision(
    task.assignmentRotation, nextDueDate, memberId, assignmentsSnap.docs
  );

  // ── FASE DE ESCRITURA (todo o nada) ──
  tx.update(taskRef, {
    lastCompletedDate: now,
    completedBy: memberId,
    ...(task.frequency !== 'once' ? { nextDueAt: nextDueDate } : {})
  });
  tx.update(memberRef, { totalPoints: FieldValue.increment(outcome.pointsAwarded) });
  const historyRef = db.collection(`households/${hid}/taskHistory`).doc();
  tx.set(historyRef, {
    taskId, memberId, points: outcome.pointsAwarded,
    completedAt: now, onTime: outcome.onTime, pointsApplied: true
  });
  for (const a of assignmentsSnap.docs) {
    tx.update(a.ref, {
      status: 'completed', completedAt: now,
      pointsAwarded: a.data().memberId === memberId ? outcome.pointsAwarded : 0,
      onTime: outcome.onTime
    });
  }
  if (rotationDecision.shouldCreate) {
    const nextRef = db.doc(`households/${hid}/tasks/${taskId}/assignments/next_${taskId}_${nextDueDate}`);
    tx.create(nextRef, { taskId, memberId: rotationDecision.memberId, /* ... */ });
    // tx.create (no tx.set) — falla con ALREADY_EXISTS si ya existe, mismo
    // efecto que el POST con documentId + catch ALREADY_EXISTS de hoy, pero
    // dentro de la MISMA transacción: si otra invocación concurrente ya creó
    // este documento, TODA la transacción reintenta automáticamente (el
    // Admin SDK reintenta transacciones abortadas por conflicto de lectura
    // hasta 5 veces por defecto) en vez de solo descartar ese paso aislado.
  }
});
```

### Casos que esto resuelve frente al cliente

- **Doble completado simultáneo** (dos dispositivos completando la misma
  tarea a la vez): Firestore detecta que ambas transacciones leyeron
  `taskRef`/`memberRef` y una escribió primero → la segunda transacción
  aborta y el SDK la reintenta automáticamente con datos frescos. Con datos
  frescos, `task.lastCompletedDate` ya no coincide con lo esperado →
  `HttpsError('aborted')`, el segundo dispositivo ve el mismo error de
  conflicto que hoy (`TaskCompletionConflictException` mapeado), pero SIN
  haber escrito nada a medias — hoy el primer PATCH (paso 1) sí podía
  colarse y dejar el resto de la secuencia a medio camino si la red fallaba
  después.
- **Conflicto de asignación**: mismo mecanismo — la lectura de
  `assignmentsSnap` dentro de la transacción se re-evalúa en cada reintento,
  así que la deduplicación de "¿ya existe una asignación assigned para este
  miembro+fecha?" nunca ve un estado stale.
- **Atomicidad puntos+taskHistory+nextDueAt+asignación**: por construcción —
  o se aplican las 4-6 escrituras juntas, o ninguna. Elimina por completo la
  necesidad del patrón `pointsApplied: false → true` (dos escrituras
  separadas para tener un rastro reparable) — ya no hay ventana en la que
  "tarea completada" y "puntos otorgados" puedan divergir.

### Nota de duplicación de lógica **[APLICA YA, con seguimiento]**

`resolveCompletionOutcome` (Kotlin, `PenaltyRules.kt` — no leído en detalle
en esta ronda pero referenciado desde `completeTask`) y
`RecurrenceRules.resolveNextAssignmentDecision`/`calculateNextDueDate` tendrán
que reescribirse en TypeScript para la función. Esto introduce el riesgo real
de que Kotlin y TS diverjan con el tiempo (dos implementaciones de la misma
regla). Mitigación para fase 2: portar `RecurrenceRulesTest.kt` (84 tests) a
un `recurrenceRules.test.ts` con los MISMOS casos, ejecutado en CI junto al
build de Kotlin — no elimina la duplicación pero la hace imposible de romper
en silencio. Alternativa (no recomendada para este alcance): compilar
Kotlin/JS y compartir el módulo — overhead de tooling no justificado para
~300 líneas de reglas puras.

---

## 5. Reglas Firestore

### Qué deja de escribir el cliente

Tras migrar 2.1-2.4, el cliente **ya no** escribe directamente:
- `tasks/{tid}.lastCompletedDate` / `.completedBy` / `.nextDueAt` cuando el
  motivo es "completar/deshacer/reasignar" (sigue escribiendo el resto de
  campos de la tarea — título, puntos, subtareas — al editar, eso no cambia).
- `members/{mid}.totalPoints` cuando el motivo es "puntos por tarea
  completada" (sigue escribiendo `totalPoints` para donaciones/agradecimientos
  entre iguales — `isPeerPointsTransfer`, sección "donar" — eso NO pasa por
  Cloud Functions en este diseño, queda fuera de alcance).
- `taskHistory/{thid}` create/update relacionado con compleciones (sigue
  existiendo `taskHistory` como colección, pero la crea/actualiza la función
  con el Admin SDK, que **salta las reglas de seguridad** — no hace falta que
  el cliente tenga permiso de escritura ahí para este flujo).
- `assignments/{aid}` status → `completed` (igual: lo hace la función).

### Reglas nuevas — restringir esas rutas a solo-server

**[REQUIERE DECISIÓN]** (cambio de seguridad con impacto real si algo del
cliente no migra a tiempo): añadir una condición que bloquee al cliente
escribir esos campos específicos, forzando el paso por Cloud Functions. Dos
opciones:

- **(i) Big bang**: cambiar la regla de `tasks/{tid}` para que
  `lastCompletedDate`/`completedBy`/`nextDueAt` solo puedan cambiar si
  `request.auth == null` (las Cloud Functions con Admin SDK no llevan
  `request.auth` de usuario — hay que verificar esto contra el comportamiento
  real del Admin SDK, que por defecto se salta las reglas del todo, no las
  evalúa con `auth == null`; si se salta las reglas, esta rama de regla es
  innecesaria — ver nota abajo). Rompe inmediatamente cualquier build de
  cliente no actualizado (App Store/Play Store con versiones viejas en
  producción).
- **(ii) Gradual (recomendado)**: **no** cambiar las reglas en el mismo
  despliegue que las funciones. Desplegar las funciones, migrar el cliente,
  verificar en producción unos días, y SOLO ENTONCES cerrar la ruta directa
  en las reglas (evita romper usuarios con la app vieja instalada mientras
  la nueva se propaga por las stores — Android/iOS no fuerzan actualización
  inmediata). Mientras tanto, las reglas actuales (v8) siguen siendo la única
  protección — ya validan que un cliente no pueda inflar puntos arbitrarios
  más allá de lo razonable, así que no hay regresión de seguridad por
  posponer el cierre.

**Nota técnica importante**: el Admin SDK de Cloud Functions, por defecto,
**ignora completamente** `firestore.rules` (opera con privilegios de
servidor, como ya advierte el comentario del encargo: "admin SDK las salta").
Esto significa que la validación de "¿quién puede llamar a esta función?"
vive ENTERAMENTE en el código de la función (`context.auth`, lectura del
documento de miembro dentro de la transacción — ver sección 2), no en
`firestore.rules`. Las reglas solo importan para lo que el cliente sigue
escribiendo directo.

### Qué NO cambia

- `create`/`delete` de tareas: sigue siendo el cliente directo (crear una
  tarea nueva no tiene problema de atomicidad — es un solo documento).
- Edición de campos no relacionados con completar (título, puntos,
  descripción, subtasks, assignmentRotation cuando se edita manualmente, no
  como resultado de completar): sigue siendo el cliente directo vía
  `updateTask`.
- `isPeerPointsTransfer` (donar/agradecer): fuera de alcance de este diseño
  — sigue como está, con su propia limitación conocida documentada en la
  cabecera de `firestore.rules` (v8).

---

## 6. Migración de datos

**Respuesta corta: NO hace falta migración de datos.** Las Cloud Functions
leen y escriben exactamente el mismo shape de documento
(`lastCompletedDate`/`completedBy`/`nextDueAt`/`assignmentRotation`) que el
cliente escribe hoy — la Opción A del punto 1 mantiene el modelo actual. Una
tarea recurrente creada por el cliente actual es indistinguible, para la
función, de una creada después de migrar.

### Plan por fases (sin downtime)

1. **Fase 2a — desplegar funciones, sin tocar cliente ni reglas.** Las
   funciones existen pero nada las llama todavía. Verificar con el emulador
   (sección 7) y, tras desplegar, con llamadas manuales (`curl`/Postman)
   contra un hogar de pruebas real.
2. **Fase 2b — cliente nuevo llama a las funciones.** Nueva versión de la
   app (Android/iOS/desktop) con `FirestoreRepository.completeTask` etc.
   delegando en `CloudFunctionsClient`. Mientras esta versión se propaga,
   **conviven** clientes viejos (escritura directa a Firestore, como hoy) y
   nuevos (vía función) — ambos escriben el mismo shape de documento, así
   que no hay conflicto de formato, solo se pierde la atomicidad para los
   usuarios que aún no actualizaron (ni mejor ni peor que la situación
   actual para ellos).
3. **Fase 2c — cerrar la ruta directa en `firestore.rules`** (la opción (ii)
   de la sección 5), una vez la telemetría (Analytics/Crashlytics) confirme
   que la fracción de sesiones en versión vieja es despreciable. Esto es lo
   único que requiere una decisión de "cuándo" — **[REQUIERE DECISIÓN]**:
   ¿qué umbral de adopción (p.ej. "menos del 5% de sesiones en versión
   vieja durante 7 días seguidos") dispara el cierre?
4. **Sin fase de backfill**: no hay campo nuevo que popular, no hay
   documento nuevo que crear por tarea existente. `reconcileMissingTaskPoints`
   (2.5) sigue cubriendo cualquier resto de `taskHistory` legacy con
   `pointsApplied: false` que quedara de ANTES de la migración (el mecanismo
   ya existente, no algo nuevo de este plan).

---

## 7. Verificación y despliegue

### Tests (fase 2, no en este encargo)

- **Unitarios de las funciones (Node/TS)**: `functions/src/*.test.ts` con
  Jest o Vitest — testear `resolveCompletionOutcome`,
  `resolveNextAssignmentDecision`, `calculateNextDueDate` como funciones
  puras (mismo patrón que `RecurrenceRulesTest.kt`: sin I/O, portando los
  mismos 84 casos donde aplique — ver nota de duplicación, sección 4).
- **Tests del emulador local** (`firebase emulators:start --only functions,firestore`
  — confirmado: no requiere plan Blaze ni ninguna API habilitada en el
  proyecto real, corre 100% local): tests de integración que invocan la
  función contra el emulador de Firestore, sembrando documentos de
  tarea/miembro/asignación y verificando el resultado transaccional
  (incluye el caso de conflicto: dos invocaciones concurrentes del emulador
  para forzar el retry/abort real, no simulado).
- **Tests KMP que cambian**: `FirestoreRepositoryTest`/equivalentes (si
  existen — no confirmados en esta ronda, revisar en fase 2) que hoy mockean
  las N llamadas REST secuenciales de `completeTask` pasan a mockear UNA
  llamada a `CloudFunctionsClient.call(...)`. `RecurrenceRulesTest.kt` (84
  tests) **no cambia** — la lógica pura sigue viviendo en cliente para
  `isDueToday`/`isDueOn` (sección 2.6), solo se duplica (no se mueve) el
  subconjunto de `resolveNextAssignmentDecision`/`calculateNextDueDate` hacia
  TS.

### Pasos de deploy (documentados, NO ejecutados en esta fase)

Requisitos previos (ninguno cumplido hoy en este entorno, confirmar antes de
fase 2):
1. Plan **Blaze** (pay-as-you-go) habilitado en `task-hub-62f98` — Cloud
   Functions v2 no está disponible en el plan Spark gratuito.
2. API `Cloud Functions` (y `Cloud Build`, que Functions usa internamente
   para el build) habilitadas en Google Cloud Console para ese proyecto.
3. `firebase login` con una cuenta que tenga rol de editor/owner sobre
   `task-hub-62f98` (mismo patrón que `scripts/deploy_firestore_rules.py`
   usa para reglas, pero Functions no tiene un script Python equivalente
   hoy — se despliega con la CLI de Firebase directamente).

Comandos (fase 2, cuando lo anterior esté listo):
```bash
cd functions && npm install
firebase deploy --only functions:completeRecurringTask,functions:completeAssignment,functions:reassignTaskCompletion,functions:undoTaskCompletion,functions:reconcileMissingTaskPoints
```
Deploy incremental por función (no `--only functions` a secas) para poder
desplegar 2.1-2.4 primero y 2.5 (scheduled) por separado, ya que 2.5 crea
además un recurso de Cloud Scheduler (coste/permiso distinto).

---

## 8. Riesgos y alcance

### Riesgos

- **Duplicación de lógica Kotlin/TS** (sección 4): el riesgo real más
  probable de causar un QUINTO fix. Mitigado por portar los tests, no
  eliminado.
- **Cold starts de Cloud Functions**: la primera invocación tras un periodo
  de inactividad añade latencia (~1-2s típico en Node 20 con Admin SDK). Para
  una app de "completar tarea" (acción no ultra-frecuente por usuario), es
  aceptable, pero cambia la sensación de "instantáneo" que hoy tiene
  `completeTask` cuando el cliente ya tiene el token cacheado. **[REQUIERE
  DECISIÓN]** si Liberto quiere `minInstances: 1` (evita cold start, tiene
  coste fijo mensual — ver estimación abajo) o aceptar la latencia
  ocasional.
- **Reintentos automáticos de transacción**: si `resolveCompletionOutcome`
  tuviera efectos no deterministas (no debería, pero hay que garantizarlo al
  portar a TS — p.ej. NO usar `Date.now()` dentro del cuerpo de la
  transacción en múltiples puntos que puedan divergir entre reintentos; leer
  `now` una vez al principio del handler, fuera de `runTransaction`, y
  pasarlo como parámetro puro).
- **Ventana de cliente viejo sin migrar** (sección 6, fase 2b): ya cubierta,
  riesgo bajo porque ambos formatos de escritura son compatibles.

### Coste estimado (uso realista)

Firebase Cloud Functions (2nd gen) plan Blaze, tier gratuito incluido:
2M invocaciones/mes, 400,000 GB-s de cómputo, 200,000 GHz-s. Para una app de
gestión de tareas domésticas (hogares de ~2-5 miembros, unas pocas tareas
completadas al día por hogar): incluso con **varios miles de hogares
activos**, el volumen de invocaciones de "completar tarea" se queda muy por
debajo del tier gratuito (miles de invocaciones/mes por hogar activo serían
necesarias para agotarlo). **Estimación: $0/mes** en el rango de usuarios
actual y previsible a medio plazo, salvo que se active `minInstances: 1`
(coste fijo ~$5-15/mes por función mantenida caliente, dependiendo de memoria
asignada) — **[REQUIERE DECISIÓN]** solo si se opta por eliminar cold starts.

### Qué se descarta/reescribe

- **Se reescribe**: el cuerpo de `completeTask`/`completeAssignment`/
  `reassignTaskCompletion` en `FirestoreRepository.kt` (pasan de orquestar
  HTTP secuencial a delegar en `CloudFunctionsClient`). El patrón
  `pointsApplied: false → true` dentro de esas 3 funciones desaparece (ya no
  hace falta con transacciones reales) — pero el CAMPO `pointsApplied` en
  `TaskHistoryResponse` **se mantiene** para compatibilidad con registros
  legacy y con `reconcileMissingTaskPoints` (2.5).
- **Se descarta**: la guarda de reentrancia en `TaskScreenModel` (`A8`)
  puede simplificarse pero no eliminarse — sigue siendo necesaria para
  evitar doble-tap disparando dos llamadas a la función (la transacción
  del servidor resuelve la CARRERA entre dos dispositivos, no evita que el
  MISMO dispositivo mande la petición dos veces por un doble-tap de UI).
- **No se toca**: `RecurrenceRules.kt` (motor de "¿toca hoy?", sigue en
  cliente, sección 2.6), reglas de creación/edición/borrado de tareas no
  relacionadas con completar, el flujo de donación entre iguales
  (`isPeerPointsTransfer`), Google Calendar sync, notificaciones, streak,
  achievements — todos siguen siendo responsabilidad del cliente como
  efectos best-effort tras recibir la respuesta de la función.
- **Vestigio no relacionado, detectado durante la lectura de contexto**:
  existe un directorio `server/` en la raíz del repo (Kotlin, con su propio
  `build/`) que los comentarios de `DTOs.kt` describen como vestigio de "un
  diseño previo con un backend intermedio... antes de hablar con Firestore
  REST directamente". No se ha inspeccionado su contenido en esta ronda —
  si sigue sin usarse, podría limpiarse en un encargo aparte (fuera de
  alcance aquí, no relacionado con Cloud Functions).

---

## Resumen — decisiones para Liberto

### [REQUIERE DECISIÓN] — RESUELTAS (ver "Decisiones de Liberto" al final)

1. **Modelo de datos** (sección 1): ~~confirmar que se mantiene
   un-documento-por-tarea (recomendado) en vez de reabrir
   instancias/ocurrencias.~~ **RESUELTO → Opción A**, ver nota final.
2. **Cierre de la ruta de escritura directa en `firestore.rules`** (sección
   5): ~~confirmar el enfoque gradual (desplegar función → migrar cliente →
   cerrar regla después de verificar adopción) y qué umbral de adopción
   dispara el cierre.~~ **RESUELTO → big bang**, ver nota final.
3. **`minInstances` de las funciones de completar** (sección 8): ~~aceptar
   cold starts ocasionales (gratis) o pagar por mantenerlas calientes
   (~$5-15/mes).~~ **RESUELTO → cold starts aceptados**, ver nota final.
4. **Alcance de `undoTaskCompletion`** (sección 2.4): ~~si deshacer una
   compleción debe seguir limitado a la sesión actual (como hoy, con
   `UndoState` en memoria) o puede sobrevivir a recargar la app (derivando
   el estado previo del historial en servidor — recomendado, pero cambia el
   comportamiento visible).~~ **RESUELTO → sobrevive a recargar (opción ii)**,
   ver nota final.

### [APLICA YA] — propuestas para fase 2

1. Mantener Opción A del modelo de datos salvo que el punto 1 de arriba diga
   lo contrario.
2. 5 funciones: `completeRecurringTask`, `completeAssignment`,
   `reassignTaskCompletion`, `undoTaskCompletion` (callable HTTPS) +
   `reconcileMissingTaskPoints` (scheduled), región `europe-west1`, Node 20.
3. `isDueToday`/`isDueOn` permanecen en cliente (`RecurrenceRules.kt`), sin
   convertirse en función ni en campo persistido.
4. Protocolo callable HTTPS de Firebase (no REST v1), reutilizando el
   `bearerToken` que `FirestoreClient` ya gestiona.
5. Sin migración de datos — el shape de documento no cambia.
6. Portar `RecurrenceRulesTest.kt` (el subconjunto relevante) a TS junto con
   las funciones, para que la duplicación de lógica Kotlin/TS no diverja en
   silencio.

---

## Decisiones de Liberto (2026-09-11)

Liberto respondió a las 4 preguntas [REQUIERE DECISIÓN] de arriba. Estado
final, para que fase 2 arranque directamente sobre esto sin reabrir debate:

1. **Modelo de datos → Opción A (un documento por tarea + ventana de
   atrasada), DECIDIDO.** Liberto delegó la decisión ("lo que consideres...
   no tengo usuarios activos más allá de los tester"). Se mantiene Opción A:
   la causa raíz de los 4 fixes previos era la falta de transacciones, no el
   modelo de datos; con cero usuarios reales no hay argumento de migración
   que justifique reabrir instancias/ocurrencias para un problema que nunca
   se diagnosticó como el real. Cero migración de datos (coherente con la
   sección 6).
2. **Cierre de la ruta directa en `firestore.rules` → BIG BANG, DECIDIDO.**
   Se cierra la escritura directa del cliente a
   `lastCompletedDate`/`completedBy`/`nextDueAt`/`totalPoints` (por
   completar tarea)/`taskHistory`/`assignments.status` en el MISMO
   despliegue que las Cloud Functions (opción (i) de la sección 5, no la
   gradual (ii)) — sin usuarios reales en producción no hay versiones viejas
   en las stores que romper, así que el riesgo que motivaba el enfoque
   gradual no aplica. Se mantiene `isPeerPointsTransfer` (donar/agradecer
   entre iguales sigue escribiendo `totalPoints` directo desde el cliente,
   fuera de alcance de este diseño, sin cambios).
3. **Cold starts → ACEPTADOS, DECIDIDO.** Sin `minInstances`. Se acepta la
   latencia ocasional (~1-2s en la primera invocación tras inactividad) a
   cambio de coste $0/mes, en vez de pagar ~$5-15/mes por mantener las
   funciones calientes.
4. **`undoTaskCompletion` → SOBREVIVE a recargar la app, DECIDIDO.** Se
   adopta la opción (ii) de la sección 2.4: el servidor deriva el estado
   previo (lastCompletedDate/completedBy/nextDueAt/puntos/racha) leyendo el
   registro de `taskHistory` anterior a `completedAt` para esa tarea, en vez
   de depender del `UndoState` volátil en memoria de `TaskScreenModel`. El
   botón "deshacer" deja de desaparecer solo porque el usuario recargó la
   pantalla o cerró la app entre completar y deshacer.

Con estas 4 decisiones resueltas, el diseño de este documento queda cerrado
para pasar a fase 2 (implementación) sin bloqueantes de producto pendientes.
