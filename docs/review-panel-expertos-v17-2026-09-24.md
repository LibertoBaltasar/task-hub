# Panel de expertos v17 — Auditoría desde cero (2026-09-24)

Auditoría integral de Task Hub partiendo de cero, sin leer informes de auditoría previos. HEAD de partida: `70d5a85` (v0.7.48). 13 especialistas en 3 oleadas (5+4+4), coordinados por Claude. Cada hallazgo indica su estado final: **APLICADO** (fix ya en el código, verificado con build+test), **PROPUESTA** (requiere decisión de producto/marca/infra, no aplicado), o **NO APLICADO** (de bajo riesgo/cosmético, documentado para una ronda futura).

## Resumen ejecutivo

- **Aplicados en esta ronda:** 2 CRÍTICOS de seguridad de datos (tope de puntos saltado vía Cloud Function; anonimización de privacidad rota al 100%), 1 CRÍTICO de accesibilidad (contraste en Ranking), 1 CRÍTICO de arquitectura (timezone no propagada a la rotación de asignación), 1 CRÍTICO de rendimiento (N+1 de red), 2 CRÍTICOS de fiabilidad de red (concurrencia optimista/invalidación de caché ausentes en 5 escrituras de tareas), más ~10 hallazgos IMPORTANTE/MENOR adicionales (ver detalle).
- **Propuestas pendientes de decisión del dueño:** consentimiento UMP/TCF para AdMob, opt-out de Analytics, App Check, scope de Google Calendar, purga TTL server-side, extracción de lógica de negocio de `TaskListScreen.kt`, escala de espaciado (`Spacing.kt`), identidad visual del tema Minimal, entre otras.
- **Verificación final:** `compileDebugKotlinAndroid` → BUILD SUCCESSFUL. `jvmTest --rerun-tasks` → 100% verde (commonTest + jvmTest). `functions: npm run build` → sin errores. `functions: npm test` → 54/54 tests verdes.

---

## 1. Estética / diseño visual

### IMPORTANTE — NO APLICADO (PROPUESTA)
1. **`SemanticColors` es fija verde/ámbar/azul en los 3 temas, incluido "Minimal" (monocromo por diseño)** — `ui/theme/SemanticColors.kt:37-83`, usada en `CalendarScreen.kt`, `TaskListScreen.kt`, `MemberRewardScreen.kt`, `NotificationListScreen.kt`. El propio código ya diagnostica el problema en un comentario sin resolverlo. PROPUESTA: decisión de producto (aceptar como excepción intencional, o dar a Minimal una variante en escala de grises con significado codificado por forma/icono).
2. **No existe una escala de espaciado compartida (`Spacing.kt`)** — literales `.dp` sueltos (16/20/24/12dp) sin token. PROPUESTA: introducir `object Spacing` y migrar progresivamente.
3. **Elevación de card inconsistente** entre filas de lista visualmente equivalentes (0/1/2/4dp indistintamente). PROPUESTA de convención única.
4. **`HomeScreen` reimplementa su propia `TopAppBar`** en vez de `TaskHubTopBar` (probablemente intencional — pantalla raíz con logo — pero no documentado como excepción deliberada).

### MENOR — NO APLICADO
5. `SplashScreen.kt:90,97,105` — `fontSize` literal (72sp/14sp) fuera de `Typography` (defendible, marca de una sola pantalla).
6. Tamaños de icono dispersos sin escala nombrada (14/16/18/20/24dp en contextos similares).
7. Estados vacíos "inline" en `CalendarScreen.kt` (popup de día, secciones "pendientes"/"caducadas") no reutilizan `EmptyState` y difieren entre sí.
8. Comentarios "changelog" de paneles anteriores acumulándose en archivos de producción (`Theme.kt`, `CalendarScreen.kt`, `PointsBadge.kt`, `StatsScreen.kt`, `SemanticColors.kt`) — valioso como rationale, pero ya denso; PROPUESTA moverlo a `docs/decisiones-color.md`.

---

## 2. Funcionalidad end-to-end

### CRÍTICO — **APLICADO**
1. **La anonimización de `taskHistory` al abandonar/expulsar de un hogar compartido estaba rota al 100%.** `firestore.rules` v9 cerró `taskHistory.update` a `if false` sin excepción, bloqueando también el PATCH legítimo de `TaskRepository.anonymizeMemberTaskHistory` (añadido 9 días después, panel v14, sin volver a probarse contra las reglas v9). El UID real de cualquier miembro que se va quedaba visible para siempre en el historial de puntos del hogar. **Fix:** `firestore.rules` v12 — nueva rama de `update` que permite ÚNICAMENTE reescribir `memberId` hacia el sentinel `"deleted_member"`, por `isTrusted(hid)` (expulsión) o por el propio dueño del registro (abandono voluntario, donde `isMember(hid)` ya no es cierto en ese punto).

### IMPORTANTE — **APLICADO**
2. **Mismo bug en `rewardRedemptions`**: `allow update: if isTrusted(hid)` sin excepción — un miembro `role == "child"` que abandona por su cuenta nunca podía anonimizar sus propios canjes. **Fix:** misma rama de excepción añadida en `firestore.rules` v12.

### MENOR — NO APLICADO
3. `docs/FLUJOS-PRINCIPALES.md` tiene referencias `archivo:línea` desactualizadas (p. ej. cita `FirestoreRepository.kt:1941-1971` para `redeemReward`, que hoy vive en `1556-1619`). PROPUESTA: regenerar el documento.
4. `functions/src/reassignTaskCompletion.ts` no rechaza explícitamente `task.completedBy == null` (tarea nunca completada) — no explotable ni alcanzable desde la UI actual, pero sería más robusto un `HttpsError('failed-precondition', ...)` explícito.

---

## 3. Accesibilidad WCAG AA

### CRÍTICO — **APLICADO**
1. **`RankingScreen.kt` — el texto de puntos ("⭐ N") usaba `colorScheme.tertiary` fijo**, sin relación con el `bgColor` real de cada fila (que varía por posición: `tertiaryContainer`/`primaryContainer`/`surface`). Fallaba WCAG AA en casi todas las combinaciones tema×modo — **2.18:1 en Default oscuro y Minimal oscuro** (muy por debajo del umbral 4.5:1). **Fix:** sustituido por `secondaryTextColor`, la misma variable `when(position)` ya usada correctamente para el resto del texto secundario de la fila (`onTertiaryContainer`/`onPrimaryContainer`/`onSurfaceVariant`).

### IMPORTANTE — **APLICADO**
2. **`TaskDetailScreen.kt` — badge "obligatoria" y texto "a tiempo/tarde" en `colorScheme.tertiary` sobre `colorScheme.surface`**, fallando AA (4.19:1, bajo el umbral 4.5:1) en el tema Naturaleza claro. **Fix:** sustituido por `MaterialTheme.semanticColors.warning`, ya auditado explícitamente para texto sobre fondos neutros en los 3 temas.
3. Mismo patrón de `colorScheme.tertiary` en botones (`CreateRewardScreen.kt:336`, `PersonalSpaceScreen.kt:133`, `MemberRewardScreen.kt:302`, `HouseholdScreen.kt:664`) — verificado que usan `ButtonDefaults.buttonColors`/`Surface` con resolución automática de `contentColor`, probablemente ya correctos; no se tocan en esta ronda (verificación de bajo riesgo, no fix).

### MENOR — NO APLICADO
4. `ShouldReduceMotion.jvm.kt` devuelve `false` siempre (sin señal de sistema equivalente portable en JVM/Linux) — limitación conocida y documentada, esfuerzo alto (JNI por SO) para beneficio bajo.

---

## 4. UI / componentes Material3

### CRÍTICO — NO APLICADO (PROPUESTA, refactor amplio)
1. **`Card(...).clickable(role = Role.Button)` en vez de `Card(onClick = ...)`** en 6 archivos (`TaskListScreen.kt:916`, `NotificationListScreen.kt:275`, `HouseholdScreen.kt:523`, `ProfileScreen.kt:186`, `HouseholdTaskSection.kt:176`, `HouseholdMemberList.kt:269`) — la elevación por press (`CardDefaults.cardElevation(pressedElevation=...)`) nunca se activa porque no hay `interactionSource` compartido. No es riesgoso funcionalmente (ripple manual ya funciona), pero migrar 6 archivos sin poder probar visualmente en este entorno se deja como PROPUESTA para una ronda con verificación visual.

### IMPORTANTE — **APLICADO** (parcial)
2. Imports muertos: `HomeScreen.kt:32` (`TextAlign`), `NotificationListScreen.kt:31` (`TextAlign`), `TaskDetailScreen.kt:47` (`RewardResponse`) — **eliminados**.
3. `taskHubTextFieldColors()` documentado como "usado por todos los formularios" pero solo 2/10 archivos lo llaman — no es bug (defaults M3 ya resuelven bien), deuda de documentación. NO APLICADO (bajo impacto).

### MENOR — verificado sin hallazgos
4. Iconos espejados correctos (`Icons.AutoMirrored.Filled.*` en `ArrowBack`/`ArrowForward`/`Send`/`KeyboardArrowLeft/Right`); sin `material-icons-extended`; `EmptyState`/`DateHelpers` bien centralizados; sin literales `Color(0x...)` fuera de `theme/`.

---

## 5. UX

### IMPORTANTE — **APLICADO** (parcial)
1. `TaskDetailScreen.kt` — el estado de error solo ofrecía "Reintentar" (reintenta indefinidamente si el recurso ya no existe). **Fix:** añadido botón "← Volver" (`navigator.pop()`) junto a "Reintentar".
2. Deep link a un hogar sin autorización dejaba una pantalla vacía con solo un snackbar transitorio como explicación (`HouseholdScreen.kt`). NO APLICADO — requiere una pantalla de error dedicada + detección de categoría "sin permiso", alcance mayor a lo seguro para esta ronda; PROPUESTA.
3. `CalendarScreen.kt` — secciones "Pendientes sin fecha"/"Caducadas" usan `Column().forEach` en vez de `LazyColumn` dentro de un `Column().verticalScroll()` ya no perezoso. NO APLICADO — cambio de layout con riesgo visual sin verificación en pantalla real; PROPUESTA.

### MENOR — **APLICADO**
4. `JoinHouseholdScreen.kt` — el campo de código de invitación no marcaba `isError`/`supportingText` pese a que el botón se deshabilitaba silenciosamente. **Fix:** añadido `isError` + `supportingText` con hint de longitud mínima (nuevas claves i18n `join_household_code_hint`, ES/EN), mismo patrón que el resto de formularios.

### MENOR — NO APLICADO
5. `EditProfileScreen.kt` — selector de emoji con chunking manual en vez de `LazyVerticalGrid(GridCells.Adaptive)` como `CreateRewardScreen.kt` (inconsistencia de patrón, sin bug de compresión hoy). PROPUESTA: unificar en un componente compartido.

---

## 6. Programador senior

### CRÍTICO — NO APLICADO (PROPUESTA, riesgo/esfuerzo alto)
1. AES-256-CTR implementado a mano en JS opaco (`@JsFun`) en `storage/SecureStore.wasmJs.kt` — irrevisable en code review, sin tests (wasmJs no corre en CI). PROPUESTA: migrar a `SubtleCrypto`/librería auditada, requiere rediseñar el contrato `SecureStore` a `suspend`.
2. Swallowing sistemático de excepciones sin logging/observabilidad — no hay infraestructura de logging en `commonMain` (sin Crashlytics/Napier). PROPUESTA: introducir logger multiplataforma mínimo.

### IMPORTANTE — **APLICADO**
3. **`loadHouseholdTimezone` (`functions/src/auth.ts`) no validaba que el TZ IANA fuera válido** — un valor corrupto/no-IANA hacía que `Intl.DateTimeFormat` lanzara un `RangeError` sin capturar más adelante en la transacción, tumbando la función entera con un 500 genérico. **Fix:** valida el formato con un `try/catch` alrededor de `new Intl.DateTimeFormat(...)`, cae a `DEFAULT_TZ` si no es válido.
4. **`reconcileMissingTaskPoints.ts` tragaba errores del catch sin loguear nada** — un fallo sistemático de esta "red de seguridad" podía pasar desapercibido indefinidamente. **Fix:** `logger.error(...)` con el path del documento.

### IMPORTANTE/MENOR — NO APLICADO (refactor, bajo riesgo funcional)
5. `TaskScreenModel.completeTask` (~156 líneas, 5 responsabilidades mezcladas) — PROPUESTA de extracción a funciones privadas testeables.
6. Patrón `when { x is Y -> ... }` con casts `as` redundantes en ~10 pantallas — no crashea hoy, PROPUESTA de reescritura a `when(x) { is Y -> ... }` con smart-cast.
7. `!!` redundantes tras comprobación null explícita (8 sitios) — no crash-prone, cosmético.
8. `Clock.System.now()` no inyectable de forma consistente en varios repos (patrón correcto SÍ existe en 2 sitios) — PROPUESTA de uniformizar para testabilidad.

---

## 7. Jefe de arquitectura

### CRÍTICO — **APLICADO**
1. **`resolveNextAssignmentDecision` no recibía `tz` en `completeRecurringTask.ts`/`completeAssignment.ts`**, cayendo silenciosamente al `DEFAULT_TZ` ("Europe/Madrid") aunque `tz` ya se hubiera cargado del hogar para el resto de la misma transacción — para hogares con TZ distinta y rotación configurada, la asignación del siguiente ciclo podía calcularse con el día de calendario equivocado cerca de medianoche. **Fix:** se pasa `tz` como 5º argumento en ambas llamadas.

### IMPORTANTE — NO APLICADO (PROPUESTA/decisión de producto)
2. Divergencia cliente/servidor sobre qué TZ manda para "hoy"/"a quién le toca" (`TaskScreenModel.kt` usa TZ del dispositivo, el servidor usa TZ del hogar) — trade-off ya documentado; PROPUESTA es decidir si el cliente debería leer `household.timezone` para estos cálculos de UI.
3. Port manual TS↔Kotlin de reglas de negocio (`penalty.ts`/`rules.ts`) sin test de paridad automatizado — el hallazgo #1 de esta misma sección es la prueba de que la disciplina manual ya falló una vez sin que ningún test lo detectara. PROPUESTA: fixture compartido o eliminar el código Kotlin huérfano (`PenaltyRules.kt` ya no tiene call-sites de producción) y declarar `functions/src/` única fuente de verdad.
4. Lógica de negocio pura (`isTaskDueToday`, `groupTasksByStatus`, `taskComparator`, etc.) viviendo en `ui/screens/TaskListScreen.kt` (1464 líneas) en vez de una capa de reglas testeable sin Compose — diseño deliberado documentado, pero invierte la separación de capas. PROPUESTA de extracción a `TaskListRules.kt`.

### MENOR — verificado sin hallazgos
5. `FirestoreRepository.kt` (1652 líneas) confirmado como fachada delgada (no god object) — la mayoría de sus ~90 métodos delegan en repos especializados sin ciclos de dependencia.
6. Migración híbrida a Cloud Functions coherente y bien cerrada en `firestore.rules` (criterio de qué migra es consistente).
7. `wasmJsMain` (9 archivos) no documentado en `CLAUDE.md` — nota de documentación, no arquitectónica.

---

## 8. QA / bugs

### IMPORTANTE — **APLICADO**
1. **`MemberRepository.addMemberAchievement` — el PATCH que crea `achievements/_meta` por primera vez se mandaba SIN precondición alguna** (`current?.updateTime` es `null` cuando el doc no existe aún) — dos dispositivos desbloqueando logros DISTINTOS por primera vez casi a la vez podían pisarse (el segundo PATCH, último-en-escribir-gana sobre el array completo, borraba en silencio el logro del primero). **Fix:** se manda `currentDocument.exists = false` como precondición cuando el documento aún no existe; el conflicto se maneja con el mismo retry ya existente para el resto de la función.

### IMPORTANTE — NO APLICADO (requiere análisis más profundo de las 2 Cloud Functions)
2. Cierre de asignaciones "hermanas" en `completeRecurringTask.ts`/`completeAssignment.ts` no acotado por `dueDate`/ciclo (solo por `status == "assigned"`) — durante la ventana de `TaskRepository.replaceAssignments` (crea asignaciones nuevas antes de borrar las antiguas), completar la tarea puede cerrar en silencio, con 0 puntos, una asignación de OTRO ciclo. Riesgo real pero acotado a una ventana estrecha; requiere tocar la lógica transaccional central de ambas funciones — se deja como PROPUESTA con prioridad alta para la siguiente ronda en vez de aplicarlo sin poder ejercitar el escenario con un test de integración real (el hueco de cobertura #1 del mapa de tests, sección 13, es precisamente esto).

### MENOR — cubierto por la sección 12 (red/offline)
3. Divergencia TZ "¿toca hoy?" — ver arquitectura #2.
4. `toggleSubtask`/`updateSubtasks` lost-update entre dispositivos — **APLICADO**, ver sección 12 #1.

---

## 9. Seguridad / AppSec (OWASP MASVS)

### CRÍTICO — **APLICADO**
1. **El tope de 100.000 puntos de `firestore.rules` no existía en las Cloud Functions** (`completeRecurringTask.ts`, `completeAssignment.ts`, `reassignTaskCompletion.ts`, `reconcileMissingTaskPoints.ts` usan Admin SDK y saltan `firestore.rules` por completo). Un miembro podía crear una tarea con `points` arbitrariamente grande (o negativo, para drenar el saldo de OTRO miembro asignándole la compleción) y completarla vía la función callable, saltándose el tope por completo — sin suelo de 0 tampoco. **Fix (defensa en profundidad, 2 capas):**
   - `functions/src/points.ts` (nuevo) — `clampTotalPoints(current, delta)` acota `[0, 100000]`; aplicado dentro de la misma transacción en las 4 funciones (leyendo el `totalPoints` fresco ya cargado por `loadActiveMember`/una lectura añadida para el miembro saliente en `reassignTaskCompletion`), sustituyendo los `FieldValue.increment(...)` ciegos.
   - `firestore.rules` v12 — `tasks/{tid}.points` acotado a `[0, 100000]` en `create`/`update`, cerrando también la vía directa de cliente.

### IMPORTANTE — NO APLICADO (PROPUESTA, infraestructura/decisión de producto)
2. Ausencia total de Firebase App Check — cualquier cuenta válida (incluida anónima) puede llamar a Firestore REST o a las funciones callable con un script, sin certificar que la petición viene de la app real. PROPUESTA: activar Play Integrity/App Attest + `enforceAppCheck: true`.
3. `isPeerPointsTransfer` (donar/agradecer) sigue permitiendo que el propio miembro edite su `totalPoints` directamente vía REST (limitación arquitectónica ya documentada en la cabecera de `firestore.rules`) — cerrarla del todo rompería el flujo legítimo de donar/agradecer sin backend; requiere migrar también esa escritura a Cloud Function. PROPUESTA de alcance mayor.

### MENOR — NO APLICADO (trade-off ya documentado / no explotable hoy)
4. Fallback de `SecureStore` Android a `Settings()` sin cifrar si el Keystore falla — trade-off consciente y ya documentado (mejor no-cifrado que bloquear login).
5. Códigos de invitación sin rate-limit explícito — no explotable con la entropía actual (8 chars, ~2.8×10¹² combinaciones).

### Verificado sin hallazgos
Tokens cifrados correctamente en las 3 plataformas (Keystore/Keychain/AES-256-GCM con IV aleatorio); sin secretos hardcodeados reales; CSV injection ya mitigada (`TaskCsvExporter.escapeCsvField`); OAuth desktop con PKCE + state validado; `rewardRedemptions.pointsSpent` ya validado contra el coste real.

---

## 10. Privacidad / RGPD / menores

### CRÍTICO — NO APLICADO (PROPUESTA, integración de SDK/infraestructura)
1. **Sin flujo de consentimiento UMP/TCF v2 para AdMob** — se sirven anuncios en el EEE/UK sin CMP certificado, incumpliendo la "EU User Consent Policy" de Google (riesgo real de suspensión de cuenta AdMob) además de exposición RGPD. Requiere integrar `com.google.android.ump:user-messaging-platform` y gatear `MobileAds.initialize`/`InterstitialAd.load` — fuera del alcance seguro de aplicar sin poder probar el flujo de consentimiento en un dispositivo real.
2. **Firebase Analytics sin opt-out** — no hay ningún toggle en Ajustes que llame a `setAnalyticsCollectionEnabled(false)`; el RGPD exige poder retirar el consentimiento tan fácil como se dio. PROPUESTA para la siguiente ronda (requiere nueva clave de `SettingsStore` + i18n + wiring en `Analytics.android.kt` + `SettingsSheet`).

### IMPORTANTE — NO APLICADO (PROPUESTA)
3. Scope de Google Calendar es el completo (`auth/calendar`) en vez de `calendar.events` — viola minimización de datos; requiere confirmar primero que `GoogleCalendarRepository` no usa ningún endpoint que necesite el scope completo (gestión de calendarios/ACLs) antes de estrechar, para no romper la sincronización — investigación pendiente, no aplicado sin esa verificación.
4. Purga TTL de 90 días es "lazy" (solo se dispara si un usuario abre la pantalla correspondiente), sin Cloud Function `onSchedule` que purgue datos de hogares inactivos. PROPUESTA de nueva función programada.
5. `MemberRepository.deleteMember` conserva el `userId` real (UID de Google) indefinidamente tras `leave`/expulsión, solo anonimiza `displayName`/`avatarUrl`. PROPUESTA — interactúa con lógica de resolución de miembro, requiere análisis de impacto.

### MENOR — NO APLICADO
6. Texto de confirmación de borrado de cuenta no menciona explícitamente la revocación del acceso a Calendar (transparencia informativa). PROPUESTA de copy, bajo impacto.

### Verificado sin hallazgos
Borrado de cuenta/hogar es cascade-delete real (no soft-delete indefinido); TFCD (Tag For Child Directed Treatment) aplicado globalmente + gate adicional por rol sin bypass verificado; revocación de acceso a Calendar real al borrar cuenta.

---

## 11. Rendimiento

### CRÍTICO — **APLICADO**
1. **N+1 real en `TaskRepository.getAllAssignments`** — una petición HTTP por tarea, todas lanzadas a la vez sin límite de concurrencia, disparado desde 3 rutas calientes (`TaskScreenModel.loadTasks`, `StatsScreenModel.loadStats`, `CalendarSyncManager.reconcile` — este último en CADA apertura de `HouseholdScreen`/`PersonalSpaceScreen`). Un hogar con 30 tareas disparaba 30 peticiones concurrentes. **Fix:** `Semaphore(4)` acotando la concurrencia, mismo patrón ya usado en `CalendarSyncManager.createEventSemaphore`.

### IMPORTANTE — **APLICADO**
2. **`CalendarSyncManager.reconcile` se repetía en cada apertura de hogar** sin ningún throttle — Voyager crea una instancia nueva de `Screen` en cada `push`, así que no es "solo la primera vez de la sesión". **Fix:** nuevo `SettingsStore.getLastCalendarReconcileAt`/`setLastCalendarReconcileAt` (mapa por hogar, mismo patrón que `getCalendarId`), throttle de 15 minutos antes de repetir el fetch completo.

### MENOR — **APLICADO**
3. `NotificationPollWorker` sondeaba los hogares guardados EN SERIE — con varios hogares, el ciclo de ~30min tardaba proporcionalmente más. **Fix:** `coroutineScope { households.map { async { ... } }.awaitAll() }`, manejo de error ya existente por hogar preservado.
4. `HomeScreen.kt` — `households.filter { !it.isPersonal }` recalculado en cada recomposición dentro del bloque de contenido de `LazyColumn`. **Fix:** hoisted a `remember(households) { ... }` en el scope `@Composable` padre (el bloque de contenido de `LazyColumn` no es un contexto composable, así que no podía ir ahí directamente).

### Verificado sin hallazgos
Keys correctas en todas las `LazyColumn`/`LazyRow` revisadas; arranque ya paraleliza lo paralelizable; minify/shrinkResources activos en release; sin gradientes pesados sin `remember`.

---

## 12. Fiabilidad de red / offline / sync

### CRÍTICO — **APLICADO**
1. **`updateSubtasks`/`toggleSubtask` — lost-update silencioso entre dos dispositivos.** El PATCH mandaba el array `subtasks` completo sin `currentDocument.updateTime`, calculado sobre una lectura ya potencialmente obsoleta. Dos dispositivos marcando subtareas DISTINTAS de la misma tarea casi a la vez podían pisarse sin ningún error visible. **Fix:** `TaskRepository.updateSubtasks` reescrito con concurrencia optimista real (mismo patrón que `MemberRepository.addMemberPoints`): recibe un `transform: (List<Subtask>) -> List<Subtask>` en vez de una lista pre-calculada, relee el documento fresco en cada reintento (hasta `OPTIMISTIC_WRITE_MAX_RETRIES`), aplica el transform sobre ESE array y manda `currentDocument.updateTime` como precondición. `TaskScreenModel.toggleSubtask` simplificado (ya no necesita su propio `getTask` previo). `FirestoreRepository`/`FakeFirestoreRepository` actualizados a la nueva firma.
2. **`updateTask`/`updateSubtasks`/`deleteTask`/`updateAssignmentRotation`/`updateAssignmentGoogleEventId` invalidaban la caché SOLO tras un `client.patch/delete` exitoso** — si la llamada lanzaba por timeout/IOException ambiguo (el servidor pudo haber aplicado el cambio pese al error visto por el cliente), la caché quedaba sirviendo datos obsoletos indefinidamente, a diferencia de `completeTask`/`undoTaskCompletion`/etc. (que ya usan `try/finally` precisamente por este motivo). **Fix:** las 5 escrituras envueltas en `try { ... } finally { taskCache.clear...() }`.

### IMPORTANTE — NO APLICADO (PROPUESTA, decisión de UX de conflicto)
3. `updateTask` (reescritura completa de una tarea) sigue sin concurrencia optimista — dos ediciones concurrentes de campos DISTINTOS de la misma tarea se pisan en silencio (el segundo PATCH reescribe TODOS los campos editables, no solo los tocados). Añadir `currentDocument.updateTime` aquí convertiría ese silent-overwrite en un conflicto explícito que la UI (`EditTaskScreen`) tendría que saber comunicar — cambio de comportamiento de cara al usuario que requiere trabajo de UX (mensaje de "alguien más editó esta tarea"), se deja como PROPUESTA de prioridad alta en vez de aplicarse sin ese diseño.
4. `createTask` + `assignTask` no es atómico — si la app se cierra entre ambas llamadas, la tarea queda creada sin ninguna asignación ("huérfana"), visible para todo el hogar sin indicación de que el flujo se cortó. PROPUESTA: reconciliación en `loadTasks` o mover a Cloud Function.

### MENOR — NO APLICADO (documentado, trade-off ya aceptado)
5. `isOnline()` no se consulta antes de escrituras (solo alimenta el banner) — el usuario espera el timeout completo (30s) antes de ver el error si está offline.
6. Escrituras (POST/PATCH/DELETE) nunca se reintentan automáticamente, ni ante 5xx transitorio — decisión deliberada para no duplicar efectos no-idempotentes; documentado en el propio `FirestoreClient.kt`.

---

## 13. Cobertura de pruebas (solo mapa, sin cambios de código)

**Mapa de cobertura actual:** las reglas de negocio puras (`network/*Rules.kt`) están excepcionalmente bien cubiertas — todas tienen su test, y `RecurrenceRulesTest.kt`/`penalty.test.ts`/`rules.test.ts` mantienen paridad Kotlin↔TypeScript documentada. `TaskScreenModelTest.kt` es el ScreenModel mejor cubierto (con `FakeFirestoreRepository`). No existen tests de UI/Compose (screenshot o de interacción) en todo el proyecto.

**TOP-10 huecos priorizados** (orden de riesgo — dinero/puntos primero):
1. `functions/src/completeRecurringTask.ts`/`completeAssignment.ts` (orquestación transaccional completa) — sin test de integración; solo sus funciones puras internas están testeadas por separado.
2. `functions/src/undoTaskCompletion.ts` — sin test; revierte puntos/racha.
3. `functions/src/reassignTaskCompletion.ts` — sin test; toca balances de 2 miembros a la vez.
4. `functions/src/reconcileMissingTaskPoints.ts` — sin test; "red de seguridad" de puntos legacy.
5. `network/TaskRepository.kt`/`FirestoreRepository.kt` — sin test directo (solo indirecto vía `FakeFirestoreRepository`, que es un doble, no el repo real).
6. `network/MemberRepository.kt`/`HouseholdRepository.kt` — orquestación de altas/bajas/sucesión sin test (la parte pura sí lo tiene).
7. `HouseholdScreenModel.kt`/`NotificationScreenModel.kt`/`ProfileScreenModel.kt`/`TaskCommentsScreenModel.kt` — sin ningún test.
8. `RewardsRepository.kt`/`GoogleCalendarRepository.kt`/`NotificationRepository.kt` — sin test (Rewards prioritario por tocar puntos).
9. `CalendarSyncManager.kt`/`GoogleAuthManager.kt` — sin test (riesgo más bajo, no puntos).
10. Ausencia total de tests de UI/Compose — ninguna pantalla/componente/tema tiene test de renderizado.

Recomendación para la siguiente ronda: priorizar 1-4 con un test de integración contra el emulador de Firestore (`@firebase/rules-unit-testing`), dado que son exactamente la superficie que movió `totalPoints` sin red de seguridad automatizada — coincide con el hallazgo QA #2 de esta misma ronda (cierre de asignaciones hermanas sin acotar por ciclo).

---

## Archivos modificados en esta ronda

**Reglas de seguridad:**
- `firestore.rules` (v11 → v12): anonimización de `taskHistory`/`rewardRedemptions`, bounds de `tasks.points`.

**Cloud Functions (`functions/src/`):**
- `points.ts` (nuevo): `clampTotalPoints`.
- `completeRecurringTask.ts`: clamp de puntos, `tz` en `resolveNextAssignmentDecision`.
- `completeAssignment.ts`: clamp de puntos, `tz` en `resolveNextAssignmentDecision`.
- `reassignTaskCompletion.ts`: clamp de puntos (ambos miembros).
- `reconcileMissingTaskPoints.ts`: clamp de puntos, logging de errores.
- `auth.ts`: validación de TZ IANA en `loadHouseholdTimezone`.

**Composeapp (`composeApp/src/commonMain/`):**
- `ui/screens/RankingScreen.kt`: fix de contraste (puntos).
- `ui/screens/TaskDetailScreen.kt`: fix de contraste (badges), botón "volver" en error, import muerto.
- `ui/screens/HomeScreen.kt`: `remember` hoisted, import muerto.
- `ui/screens/NotificationListScreen.kt`: import muerto.
- `ui/screens/JoinHouseholdScreen.kt`: `isError`/`supportingText` en código de invitación.
- `ui/i18n/AppStrings.kt`: nueva clave `join_household_code_hint` (ES/EN).
- `ui/models/TaskScreenModel.kt`: `toggleSubtask` simplificado (concurrencia optimista delegada al repo).
- `ui/models/CalendarSyncManager.kt`: throttle de `reconcile`.
- `network/TaskRepository.kt`: semáforo en `getAllAssignments`; `try/finally` en 4 escrituras; `updateSubtasks` reescrito con concurrencia optimista.
- `network/FirestoreRepository.kt`: firma de `updateSubtasks` actualizada.
- `network/MemberRepository.kt`: precondición `exists=false` en `addMemberAchievement`.
- `storage/SettingsStore.kt`: `getLastCalendarReconcileAt`/`setLastCalendarReconcileAt`.

**Android (`composeApp/src/androidMain/`):**
- `NotificationPollWorker.kt`: sondeo de hogares en paralelo.

**Tests:**
- `commonTest/kotlin/org/taskhub/ui/models/FakeFirestoreRepository.kt`: firma de `updateSubtasks` actualizada.

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   → BUILD SUCCESSFUL
./gradlew :composeApp:jvmTest --rerun-tasks --console=plain       → BUILD SUCCESSFUL (100% tests verdes)
cd functions && npm run build                                     → sin errores
cd functions && npm test                                          → 54/54 tests verdes
```

No se hizo bump de versión ni commit (corresponde al orquestador tras revisar este informe).
