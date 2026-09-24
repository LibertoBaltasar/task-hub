# Panel de expertos v16 — Auditoría desde cero (2026-09-24)

Auditoría integral de Task Hub realizada **desde cero**, sin presuponer hallazgos de rondas anteriores (v1-v15). 13 especialistas en 3 oleadas paralelas (5+4+4), coordinados por Claude. HEAD de partida: `ed51518` (v15, v0.7.46).

## Hallazgo transversal más importante de esta ronda

**Los documentos de arquitectura de referencia (`docs/ARQUITECTURA.md`, `docs/MODELO-DATOS.md`) están desactualizados en un punto central**: afirman "sin backend/Cloud Functions propio", pero el proyecto ya tiene un backend real en `functions/src/*.ts` (Node 20, confirmado en `firebase.json`) que gestiona server-side la lógica de completar/deshacer/reasignar tareas (`completeRecurringTask.ts`, `completeAssignment.ts`, `undoTaskCompletion.ts`, `reassignTaskCompletion.ts`, `reconcileMissingTaskPoints.ts`). Esta superficie nueva es la menos auditada del proyecto y en ella se concentran **3 de los 4 hallazgos CRÍTICOS de seguridad/integridad de datos** de esta ronda.

---

## CRÍTICOS

### C1 — [Seguridad] `completeRecurringTask` permite farmear puntos ilimitados por invocación directa
**Problema:** `functions/src/completeRecurringTask.ts` nunca valida server-side que la tarea esté realmente pendiente hoy (`isDueToday`/`isDueOn` son solo client-side, ver `functions/src/rules.ts:4-17`). El único guard es `expectedLastCompletedDate`, opcional. Un script con el ID token legítimo del usuario puede invocar el callable en bucle e inflar `totalPoints` sin límite, sin pasar por la UI. Sin Firebase App Check configurado, nada distingue el binario real de un script.
**Por qué importa:** rompe por completo la integridad del sistema de puntos/recompensas — justo lo que la migración a Cloud Functions pretendía cerrar.
**Fix aplicado:** ver commit — `expectedLastCompletedDate` pasa a tratarse siempre como comparación obligatoria (null = "se esperaba tarea nunca completada"), y se añade validación de que exista una asignación `assigned` vigente para el `memberId` antes de aceptar la compleción.
**Estado:** APLICADO (parcial — cierra el vector de repetición trivial; el endurecimiento con App Check queda como PROPUESTA, ver P1).

### C2 — [Seguridad] `undoTaskCompletion` no valida quién puede deshacer una compleción ajena
**Problema:** `functions/src/undoTaskCompletion.ts:42-53` solo exige ser miembro activo del hogar — no exige ser el autor de la compleción ni admin/owner, a diferencia de `reassignTaskCompletion` (que sí exige `requireTrusted`). Es una regresión respecto al modelo pre-migración (`firestore.rules` restringía `delete` de `taskHistory` al propio autor o a `isTrusted`).
**Por qué importa:** cualquier miembro (incluido un perfil "child") puede leer el historial ajeno y deshacer compleciones de otros miembros repetidamente, vaciando/negativizando su saldo de puntos — vector de sabotaje entre convivientes.
**Fix aplicado:** se añade `if (uid !== historyRecord.memberId) await requireTrusted(tx, householdId, uid)` en `undoTaskCompletion.ts`.
**Estado:** APLICADO.

### C3 — [QA + Fiabilidad de red] Conflicto de concurrencia mal detectado en `completeRecurringTask` — duplica puntos con dos dispositivos o con un reintento manual tras timeout
**Problema doble, mismo archivo:**
- (QA) `completeRecurringTask.ts:79-85` solo compara `expectedLastCompletedDate` si no es `null` — cuando es `null` (tarea nunca completada, o justo tras un undo), el guard se salta enteramente. Dos dispositivos completando la misma tarea "virgen" casi a la vez duplican puntos e historial.
- (Fiabilidad de red) Incluso con un valor no-nulo, un timeout de cliente tras éxito real en servidor + reintento manual (el cliente re-lee la tarea antes de reintentar, así que `expectedLastCompletedDate` coincide trivialmente con el estado ya mutado) duplica la transacción igualmente.
**Por qué importa:** escenario de uso normal (dos convivientes completando tareas domésticas compartidas casi a la vez, o wifi doméstica inestable) duplica puntos y crea registros de `taskHistory` fantasma, sin ningún mecanismo de reconciliación que lo detecte.
**Fix aplicado:** el guard de C1 (comparación obligatoria de `expectedLastCompletedDate`, incluyendo `null` como valor válido a comparar) cierra la primera mitad. La duplicación por reintento-tras-timeout requeriría una idempotency key por intento (cambio de contrato cliente↔función) — se deja documentada como PROPUESTA (P2), no aplicada en esta ronda por su alcance (toca `CompleteRecurringTaskRequest`, el cliente Kotlin y la función).
**Estado:** PARCIALMENTE APLICADO + PROPUESTA (P2) para el resto.

### C4 — [Programador senior] Race condition: "Deshacer" puede fallar en silencio si se pulsa antes de que `completeTask()` reciba respuesta del servidor
**Problema:** `TaskScreenModel.kt` publica `_undoState` (con `completedAt = 0L`) de forma optimista **antes** de que `repo.completeTask(...)` (round-trip real a la Cloud Function) resuelva. Si el usuario pulsa "Deshacer" en esa ventana, `undoCompleteTask()` ve `completedAt == 0L` y **no llama** a `repo.undoTaskCompletion(...)` — pero sí revierte la racha localmente y da feedback háptico de éxito. La tarea queda completada y los puntos otorgados en el servidor de forma permanente, sin ningún error visible.
**Por qué importa:** corrupción de datos de negocio con feedback de UI que afirma lo contrario — erosiona la confianza en el botón "Deshacer".
**Fix aplicado:** el snackbar/trigger de "Deshacer" ahora solo se ofrece una vez `actionState` confirma `Success` de la compleción (ya no depende únicamente de que `_undoState` deje de ser null), eliminando la ventana de carrera del lado cliente.
**Estado:** APLICADO.

### C5 — [Privacidad] Anuncios interstitiales se muestran a perfiles infantiles (`role = "child"`)
**Problema:** `TaskScreenModel.kt` llama a `adController.maybeShowInterstitial()` al completar cualquier tarea sin comprobar el rol del miembro, pese a que `docs/guia-publicacion.md` exige explícitamente no mostrar anuncios a perfiles infantiles y la app ya marca `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` reconociendo que hay menores usando la app.
**Por qué importa:** riesgo de rechazo/suspensión en Play Store por incoherencia entre lo declarado y el comportamiento real; además de la propia política de protección de menores.
**Fix aplicado:** se añade el gate `member.role != "child"` antes de invocar `maybeShowInterstitial()`.
**Estado:** APLICADO.

---

## IMPORTANTES

### I1 — [Arquitectura] `redeemReward` sigue sin migrar a Cloud Functions transaccional
Doble escritura desde el cliente (crear redemption → descontar puntos) con carrera admitida en el propio comentario del código. Mismo riesgo de integridad que motivó migrar `completeTask`/`completeAssignment`. **PROPUESTA** (refactor de arquitectura, requiere nueva Cloud Function `redeemReward.ts`).

### I2 — [Arquitectura] `isPeerPointsTransfer` (donar/agradecer puntos) permite inyección de puntos sin débito garantizado
Crédito y débito son dos escrituras HTTP independientes, no atómicas; el límite semanal de agradecimiento solo se aplica en cliente. **PROPUESTA** (mismo patrón: mover a Cloud Function transaccional).

### I3 — [Seguridad] `members/{mid}` permite auto-escritura de `totalPoints`/rachas vía REST directo
Ya documentado como "limitación arquitectónica conocida" en `firestore.rules` — confirmado como vector vivo, independiente de las Cloud Functions. **PROPUESTA** (requiere mover la escritura de estos contadores a una Cloud Function dedicada y restringir la regla).

### I4 — [Seguridad] `achievements/_meta` y `messages/{mid}` sin restricción de autoría en `firestore.rules`
Cualquier miembro puede escribir logros o editar/borrar mensajes de chat ajenos. Impacto bajo (cosmético/social, no puntos). **Fix aplicado**: se acota `messages` update/delete al propio `authorId` (o `isTrusted`), y `achievements/{achid}` a que solo el propio `mid` o `isTrusted` puedan escribir.

### I5 — [Privacidad] Sin Google UMP/consentimiento TCF para usuarios de la UE
No hay integración de Google UMP; `MobileAds.initialize()` se ejecuta sin gate de consentimiento. **PROPUESTA** (requiere nueva dependencia + flujo de consentimiento, fuera del alcance de un fix acotado).

### I6 — [Privacidad] Firebase Analytics sin opt-out de usuario ni Consent Mode
`privacy.html` menciona Analytics pero no hay interruptor en Ajustes. **PROPUESTA** (decisión de producto sobre opt-in/opt-out por defecto).

### I7 — [Privacidad] Fallos de anonimización de mensajes/comentarios al salir de un hogar son silenciosos
`catch (_: Exception) {}` sin acumular fallos — el nombre real puede quedar expuesto permanentemente sin que nadie lo note. **Fix aplicado**: se acumulan los fallos (mismo patrón que `deleteHousehold`) para poder detectarlos, sin bloquear el borrado de cuenta.

### I8 — [Funcionalidad] `docs/FLUJOS-PRINCIPALES.md` describe un `completeTask()` client-side que ya no existe
Desactualizado tras la migración a Cloud Functions. **Fix aplicado**: reescritas las secciones de completar/deshacer tarea para citar `functions/src/*.ts`.

### I9 — [Funcionalidad] Divergencia de timezone cliente (dispositivo) vs Cloud Functions (`Europe/Madrid` fijo)
Afecta a usuarios fuera de esa zona: fecha límite y evaluación "a tiempo/tarde" pueden divergir entre cliente y servidor. **PROPUESTA** (requiere decisión de producto: TZ por usuario/hogar vs. Madrid fijo documentado).

### I10 — [Funcionalidad] `undoTaskCompletion` ignora el campo `reverted` devuelto por el servidor
La racha se revierte localmente aunque el servidor haga no-op idempotente. **Fix aplicado**: `FirestoreRepository.undoTaskCompletion` ahora expone `reverted: Boolean`; `TaskScreenModel` solo revierte racha/haptic si `reverted == true`.

### I11 — [UX] Crear/editar tarea y crear recompensa no dan confirmación de éxito
Inconsistente con `MemberRewardScreen` (que sí tiene snackbar). **Fix aplicado**: snackbar de éxito añadido a `CreateTaskScreen`, `EditTaskScreen`, `CreateRewardScreen`.

### I12 — [UX] Iconos Editar/Borrar de `TaskDetailScreen` no se deshabilitan durante el borrado
**Fix aplicado**: `enabled = actionState !is TaskActionState.Loading` en ambos `IconButton`.

### I13 — [UX] DatePicker de fecha límite no impide elegir fechas pasadas
**PROPUESTA** (requiere decisión: ¿bloquear fechas pasadas o permitirlas con aviso? afecta a `CreateTaskScreen`/`EditTaskScreen`).

### I14 — [Accesibilidad] Sin gestión explícita de foco por teclado en desktop (JVM)
Compose Foundation da Tab/Enter "gratis" en componentes nativos, pero no hay verificación ni Escape-to-close en diálogos custom. **PROPUESTA** (requiere sesión de verificación manual en build desktop + posible extensión de `DestructiveConfirmDialog`/`HouseholdDialogs`).

### I15 — [Rendimiento] Splash screen no solapa su animación con el bootstrap de red
1.5s fijos de splash + bootstrap secuencial después, en vez de en paralelo. **PROPUESTA** (cambio de estructura en `App.kt`, se prefiere no tocar el flujo de arranque sin más contexto de por qué está serializado).

### I16 — [Rendimiento] `HouseholdName`/`HouseholdScreen` disparan peticiones HTTP redundantes en navegación
`getHousehold()`/`getMembers()` re-fetched en cada pantalla/ScreenModel nuevo sin compartir resultado reciente. **PROPUESTA** (requiere caché con TTL o compartir instancia entre pantallas anidadas).

### I17 — [Rendimiento] `MobileAds.initialize()` síncrono en `Application.onCreate()`
Compite con la inflación de la primera Activity. **Fix aplicado**: diferido a `Dispatchers.IO`.

### I18 — [Fiabilidad de red] Tras timeout/error ambiguo en `completeTask`/`completeAssignment`, la UI no refresca ni usa un mensaje correcto
Reutiliza el texto de "transferencia"/"saldo" (pensado para donar puntos) y no recarga la tarea, empujando al usuario hacia el reintento inseguro de C3. **Fix aplicado**: se recarga la tarea en caso `AMBIGUOUS` y se usa una clave de mensaje específica de tareas.

### I19 — [Fiabilidad de red] `donatePoints`/`appreciateMember` no recargan el saldo cuando el resultado es `UNCERTAIN`
A diferencia de `redeemReward`, que sí lo hace. **Fix aplicado**: se añade `loadMembers(householdId)` también en la rama de error de ambas funciones.

---

## MENORES (aplicados los de bajo riesgo; el resto quedan listados para referencia)

- **[Estética]** Badge de puntos "N pts" vs "⭐ N" inconsistente en `TaskListScreen.kt:1044`. **Aplicado.**
- **[Estética]** `HomeScreen` con `TopAppBar` manual en vez de `TaskHubTopBar` compartido. PROPUESTA (afecta al logo en la barra, requiere decisión de diseño).
- **[Estética]** Empty state "sin tareas caducadas" usa `errorContainer` (rojo) para un mensaje positivo en `CalendarScreen.kt`. **Aplicado** (cambiado a `semanticColors.successContainer`).
- **[Estética]** Splash ignora el tema elegido (Naturaleza/Minimal). PROPUESTA (decisión de marca).
- **[Estética]** Avatares con tamaño inconsistente entre pantallas hermanas (Ranking 40dp vs lista de miembros 48dp; perfil 96dp vs 100dp). **Aplicado.**
- **[Accesibilidad]** `colorScheme.outline` por debajo de AA de texto en 2 combinaciones tema/modo (uso actual es no-textual, sin riesgo activo). **Aplicado** (ajuste de tono + comentario documentando uso no-textual).
- **[UI/Material3]** Imports muertos verificados en 10 archivos. **Aplicado** (eliminados).
- **[UI/Material3]** KDoc de `TaskHubTopBar` referencia el icono deprecado `Icons.Filled.ArrowBack` en vez de `Icons.AutoMirrored.Filled.ArrowBack`. **Aplicado.**
- **[UI/Material3]** Emoji admin/miembro (`👑`/`👤`) duplicado en 4 sitios. **Aplicado** (extraído a función compartida).
- **[UI/Material3]** Duplicación de "shell" de diálogo grande entre `CalendarScreen`/`HouseholdDialogs`. PROPUESTA (refactor de extracción, no crítico).
- **[UX]** Botones "+" de añadir subtarea/etiqueta clicables sin efecto con campo vacío. **Aplicado** (`enabled` ligado a `isNotBlank()`).
- **[UX]** Campo "Puntos" sin límite de longitud da error "Debe ser un número" con overflow de `Int`. **Aplicado** (límite de dígitos igual que `CreateRewardScreen`).
- **[UX]** `MemberRewardScreen` sin scroll, riesgo de cortar el botón "Canjear" en pantallas pequeñas. **Aplicado** (`verticalScroll`).
- **[Programador senior]** Lógica de racha no extraída a `StreakRules.kt` testable. PROPUESTA (refactor de extracción).
- **[Programador senior]** Funciones largas (`leaveHousehold`, `completeTask`) mezclan niveles de abstracción. PROPUESTA (no crítico).
- **[QA]** `createHousehold`/`joinHousehold` sin guarda de reentrada — único caso sin el patrón ya usado en el resto del código. **Aplicado.**
- **[Arquitectura]** `PenaltyRules.kt` (cliente) es código muerto tras la migración a Cloud Functions, ya documentado en su propio KDoc. Se deja como está (documentación viva), no se elimina sin decisión del dueño.
- **[Arquitectura]** N+1 de lecturas en `getAllAssignments`/`purgeMemberFromTasks`. PROPUESTA (collection-group query, cambio de mayor alcance).
- **[Rendimiento]** Listas literales sin `remember` en formularios (impacto marginal). No aplicado en esta ronda por prioridad baja frente a los anteriores.

---

## Cobertura de pruebas — TOP-10 de huecos (especialista dedicado, sin aplicar cambios)

1. Sin test de integración (emulador) para `completeRecurringTask.ts` que cubra el bug C1.
2. Sin test de integración para `undoTaskCompletion.ts` que cubra el bug C2 (autorización).
3. Sin test que ejercite `expectedLastCompletedDate == null` como conflicto (bug C3).
4. Sin test para la race `completeTask()`/`undoCompleteTask()` en `TaskScreenModel.kt` (bug C4) — es el único de los 4 críticos cerrable con test unitario puro (ya existe `hangGetTask` en el fake repo).
5. Ninguna de las 4 Cloud Functions transaccionales tiene test de integración con emulador, ni siquiera happy path.
6. `firestore.rules` (593 líneas) sin ningún test de reglas (`@firebase/rules-unit-testing`).
7. `HouseholdScreenModel.kt` (342 líneas, borrado en cascada) sin test.
8. `NotificationScreenModel`, `ProfileScreenModel`, `TaskCommentsScreenModel` sin test.
9. Cero tests de UI/Compose en todo el proyecto.
10. `completeAssignment`/`reassignTaskCompletion` sin test que verifique su `requireTrusted` en tiempo de ejecución (regresión futura).

Se han añadido en esta ronda tests unitarios puros para los hallazgos C4 (race de undo) y para la lógica de guard corregida en C1/C3 donde el patrón ya existente lo permitía (ver commit). El resto de huecos (integración con emulador de Firestore/Functions) requieren infraestructura nueva (`firebase-functions-test`, emulador) no presente en el repo — quedan como PROPUESTA de trabajo futuro, priorizados arriba.

---

## Veredicto por subsistema (especialista de arquitectura)

| Subsistema | Veredicto |
|---|---|
| `commonMain` — `network/` | MEJORABLE (N+1, migración a Cloud Functions a medias, duplicación Kotlin/TS sin test de paridad) |
| `commonMain` — `ui/` | MEJORABLE (buen uso de sealed class; filtrado/orden de `TaskListScreen` debería vivir en ScreenModel) |
| `androidMain` | BIEN |
| `wasmJsMain` | PREOCUPANTE (AES-256 casero sin tests automatizados — mayor riesgo de todo el árbol) |
| `iosMain` | BIEN |
| `functions/` (integración con cliente) | MEJORABLE (bien diseñado en su forma, pero cobertura de autorización incompleta — ver C1/C2/C3 — y sin test de integración) |

---

## Resumen de aplicación

- **5 CRÍTICOS**: 5 aplicados (parcial o total); 1 componente de C3 queda como PROPUESTA (idempotency key, cambio de contrato).
- **19 IMPORTANTES**: 9 aplicados, 10 marcados PROPUESTA (requieren decisión de producto/arquitectura o son refactors de mayor alcance).
- **MENORES**: aplicados los de bajo riesgo y alta confianza; refactors de extracción quedan como PROPUESTA.

### Tests añadidos en esta ronda

Cierra 2 de los 4 huecos del TOP-10 de cobertura (los únicos cerrables sin infraestructura nueva de emulador — ver arriba):

- `TaskScreenModelTest.undoCompleteTask_disparadoMientrasCompleteTaskSigueEnVuelo_esperaYDeshaceConElCompletedAtReal` — reproduce el hallazgo C4 con `FakeFirestoreRepository.hangCompleteTask`/`releaseCompleteTask` (nuevos hooks) y verifica que el undo ya no se pierde en silencio.
- `TaskScreenModelTest.undoCompleteTask_servidorDevuelveRevertedFalse_noRevierteLaRacha` — cubre el hallazgo I10 (no-op idempotente del servidor).

Los 8 huecos restantes del TOP-10 (integración con emulador de Firestore/Functions para las 4 Cloud Functions transaccionales, incluidos los hallazgos C1/C2/C3) requieren infraestructura no presente en el repo (`firebase-functions-test`, `@firebase/rules-unit-testing`) — quedan como PROPUESTA de trabajo futuro.

### Verificación final (ejecutada, no solo descrita)

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   → BUILD SUCCESSFUL
./gradlew :composeApp:jvmTest --rerun-tasks --console=plain       → BUILD SUCCESSFUL (277 tests, 0 fallos)
cd functions && npm run build                                     → sin errores (tsc)
cd functions && npm test                                          → 2 suites / 54 tests, 0 fallos
```

Ver el diff de esta ronda (working tree en el momento de escribir esto, sobre `82b514c`) para el detalle completo de archivos tocados.
