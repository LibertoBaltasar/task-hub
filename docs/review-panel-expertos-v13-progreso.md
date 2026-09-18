# Panel de expertos v13 — progreso (2026-09-18)

HEAD de partida: `71c7bce` (v0.7.38). Sin progreso previo de v13 (arranque limpio).

Regla de oleadas: máximo 4-5 subagentes simultáneos. Plan: 3 oleadas (5+4+4)
cubriendo los 13 especialistas.

Nota decisión producto 2026-09-18: efectos de delight/motion (animaciones,
transiciones, celebraciones, háptica) son dirección de producto aprobada,
controlados por un futuro "Modo simple" (encargo paralelo
`revision-delight-experiencia-2026-09-18.md`). Accesibilidad (#3) y UX (#5)
NO deben reportarlos como violaciones reduce-motion/WCAG salvo que transmitan
información esencial sin alternativa estática.

## Estado de oleadas

- [x] Oleada 1 (5): Estética(#1), Funcionalidad(#2), Accesibilidad(#3), UI/componentes(#4), UX(#5)
- [x] Oleada 2 (4): Programador senior(#6), Arquitectura(#7), QA/bugs(#8), Seguridad(#9) — falló por session limit (reset 16:00 CEST), reintento único exitoso
- [x] Oleada 3 (4): Privacidad(#10), Rendimiento(#11 — falló por session limit, reintento tras reset 21:00 CEST), Red/offline/sync(#12), Cobertura pruebas(#13)
- [x] Consolidación informe final `docs/review-panel-expertos-v13-2026-09-18.md`
- [x] Aplicación de fixes seguros (11 fixes)
- [x] Verificación build + jvmTest (XML real): BUILD SUCCESSFUL, 269/269 tests, 0 fallos; wasmJsMainClasses también verificado
- [x] Commit final

## Hallazgos SOLO PROPUESTA de v12 a verificar contra código real (asignados por experto)

Ver `docs/review-panel-expertos-v12-2026-09-17.md` completo para contexto.
Lista de verificación entregada a cada experto en su oleada; resultados se
consolidan aquí tras cada oleada.

## Resultados por oleada

### Oleada 1 (completada)

**#1 Estética** — v12 solo-propuesta: 2/2 siguen abiertos (iOS AppIcon/AccentColor sin asset; onboarding con emoji en vez de AppLogo). Nuevo CRÍTICO: `Theme.kt:328-335` `TaskHubTypography` — 6 roles (`headlineLarge/Medium/Small`, `titleLarge/Medium`, `labelSmall`) construidos como `TextStyle(...)` sueltos en vez de `.copy()` sobre el `Typography()` base → pierden `fontSize`/`lineHeight`/`letterSpacing` del type-scale M3, colapsan a 14sp por defecto de Compose (afecta `TaskHubTopBar` y toda la app). APLICABLE directo. Menor: versión hardcodeada `WelcomeScreen.kt:170` desincronizada otra vez (v0.7.35 vs 0.7.38 real); `StatsScreen`/`CalendarScreen` sin `ShimmerList` (usan `CircularProgressIndicator` genérico) a diferencia de 6 pantallas hermanas.

**#2 Funcionalidad** — v12: shareText() snackbar sigue igual (decisión de producto, no tocar); inviteCode CSPRNG web sigue corregido. Nuevo IMPORTANTE: `AssignmentCompletionRules.kt`/`TaskReconciliation.kt`/parcialmente `PenaltyRules.kt` huérfanos (no invocados desde `commonMain`, lógica real vive portada a `functions/src/*.ts`, KDoc desactualizado); `completeAssignment.ts` reimplementa inline sin test TS dedicado. PROPUESTA: `redeemReward` timeout post-commit puede borrar un `redemption` que sí se cobró (ángulo nuevo del hueco de atomicidad ya documentado, requiere Cloud Function idempotente).

**#3 Accesibilidad** — v12: `<html lang="es">` sigue fijo (abierto); semántica Compose-canvas wasmJs sigue sin verificar (informativo); `TaskDetailScreen` selector sin `selectableGroup()` confirmado y AMPLIADO a `SettingsSheet.kt` (3 grupos radio más); medallas `RankingScreen` sin contentDescription pero mitigado por texto ordinal redundante. Nuevo IMPORTANTE: `TaskListScreen.kt:1003` alpha 0.7 sobre `onSurfaceVariant` (mismo patrón que v12 ya corrigió 2 veces), 3/6 temas bajo 4.5:1 — APLICABLE directo. `selectableGroup()` ausente en 4 sitios (`TaskDetailScreen.kt:1142`, `SettingsSheet.kt:598`×3 usos) — APLICABLE directo. Confirmaciones positivas: Theme.kt 54 combinaciones de color pasan WCAG AA, touch targets OK, contentDescription=null todos justificados.

**#4 UI/componentes** — v12: ilustraciones estado vacío (RankingScreen/HouseholdMemberList) siguen con emoji — abierto, ampliado a RewardListScreen/NotificationListScreen también (4 sitios). PROPUESTA: extraer `EmptyState` compartido (6 duplicaciones con deriva de tipografía titleLarge/titleMedium inconsistente). MENOR APLICABLE: `StatsScreen.kt:451-455` reimplementa inline lo que ya hace `StatusDot` (creado en v12 para evitar justo esto).

**#5 UX** — v12: login web ahora parcialmente resuelto (ya soportado desde commit 8302d99) pero mismo problema de fondo (no distingue notDisplayed/dismissed) — reencuadrado, sigue abierto. Desktop fallo=cancelación sigue abierto (KDoc ya no contradice tras v12, pero comportamiento sí). Timeout 5min sin botón cancelar sigue abierto. Compartir/CSV desktop sin confirmación sigue abierto. Nuevo IMPORTANTE: filas de miembro sin cuenta vinculada (perfiles infantiles) parecen clicables pero no hacen nada (`HouseholdMemberList.kt:266`). MENOR: spinner post-login sin texto/liveRegion (`App.kt:295-304`).

**Fixes APLICABLES identificados en oleada 1 (mecánicos, sin ambigüedad de diseño):**
1. `Theme.kt:328-335` — Typography con `.copy()` sobre base en vez de `TextStyle()` suelto (CRÍTICO).
2. `TaskListScreen.kt:1003` — quitar `.copy(alpha=0.7f)` de `onSurfaceVariant`.
3. `TaskDetailScreen.kt:1142`, `SettingsSheet.kt:598` (3 usos) — añadir `.selectableGroup()`.
4. `StatsScreen.kt:451-455` — usar `StatusDot` en vez de reimplementación inline.
5. `RankingScreen.kt:181-202` — fusionar semántica del ordinal, evitar doble lectura del emoji (menor).

Pendiente de aplicar hasta cerrar todas las oleadas y consolidar.

### Oleada 2 (completada, tras 1 reintento por session limit)

**#6 Programador senior** — v12: tests de funciones OAuth puras sigue abierto (sin cambios); material-icons-core 1.7.3 confirmado sin regresión. Nuevo IMPORTANTE APLICABLE: `AndroidSchedulerHolder.scheduler` sin `@Volatile` (`NotificationScheduler.android.kt:56-58`), inconsistente con otros holders del mismo paquete que sí lo usan (`AndroidContextHolder`, `AdControllerImpl`) — se escribe en `onCreate` y se lee desde `TaskHubFirebaseMessagingService.onNewToken()` sin sincronización. MENOR APLICABLE: `!!` innecesario en `TaskListScreen.kt:1020` (smart-cast ya cubre el caso). PROPUESTA: `TaskDetailContent` ~888 líneas, candidato a descomponer. Confirmación positiva: auditoría sistemática de ~150 catch(Exception) sin CancellationException tragada sin relanzar.

**#7 Arquitectura** — v12 CRÍTICO: SecureStore wasmJs sin cifrado real sigue abierto sin cambios. Capacidad Google Sign-In duplicada: matizado (las 4 plataformas ya funcionan, pero el contrato sentinela `""`/`null` de `GoogleSignInResultHolder` sigue duplicado 5 veces). Nuevo IMPORTANTE (propuesta, refactor grande): `TaskScreenModel` (1283 líneas) compartido sin key entre 5 pantallas no relacionadas (listado/detalle/alta/edición/calendario) — god object, documentado como decisión consciente pero con coste creciente. MENOR: contrato sentinela sin tipo sellado. Veredicto por subsistema: red/Firestore sólido, auth funcional pero con deuda de tipado, UI/navegación con límites de capas respetados salvo TaskScreenModel, persistencia local consistente salvo wasmJs sin cifrado.

**#8 QA/bugs** — Los 4 fixes críticos de v12 (floor revalidado, finally en completeTask/undoTaskCompletion, hasCalendarSupport, CSPRNG wasmJs) SIGUEN APLICADOS sin regresión. Nuevo CRÍTICO: `donatePoints` (`MemberRepository.kt:757-788`) puede DUPLICAR puntos de la nada — timeout de red tras crédito real al receptor se interpreta como fallo (IOException no es FirestoreException, no lo captura el catch específico) y dispara rollback del débito al donante, dejando ambos con los puntos. Mismo patrón (IMPORTANTE) en `appreciateMember` sin rollback pero con riesgo de duplicar crédito en reintento manual. IMPORTANTE APLICABLE: `completeAssignment`/`reassignTaskCompletion` NO invalidan caché en `finally` (a diferencia de completeTask/undoTaskCompletion que v12 sí arregló) — `FirestoreRepository.kt:1206-1220` y `1293-1318`. MENOR: carrera en `addMemberAchievement` primera creación de `achievements/_meta` (dos logros simultáneos, el segundo PATCH sobrescribe al primero sin merge) — `MemberRepository.kt:832-865`. Confirma que el hallazgo ya visto en #2 Funcionalidad (redeemReward borra redemption cobrado) comparte la misma causa raíz (IOException no clasificado como ambiguo) — fix compartido en los 3 call-sites.

**#9 Seguridad** — v12: asimetría `firestore.rules` en auto-edición de `members/{mid}` SIGUE ABIERTO y es MÁS AMPLIO de lo descrito: permite subir `totalPoints` propio directamente (no solo appreciation), ya documentado como riesgo aceptado en la cabecera del propio archivo (v9, "cero usuarios reales") — premisa a revalidar. Nuevo CRÍTICO: `isPeerPointsTransfer` sin límite de FRECUENCIA (solo de magnitud +1000/escritura) — nada impide repetir la escritura de crédito muchas veces por segundo, la operación no es atómica; requiere Cloud Function transaccional (PROPUESTA, backend nuevo). IMPORTANTE: presupuesto semanal de agradecer (50pts) bypasseable vía REST directo por la misma vía de auto-edición sin restricción de campos (PROPUESTA de regla concreta incluida en el informe del experto). Confirmaciones positivas: tokens/secretos bien gestionados en las 3 plataformas nativas, CSV injection sigue cubierto, Cloud Functions calculan puntos server-side sin confiar en cliente. Sin cambio: SecureStore wasmJs, `achievements` sin restricción de propietario, `isValidOwnerSuccession`.

**Fixes APLICABLES nuevos identificados en oleada 2:**
6. `NotificationScheduler.android.kt:56-58` — añadir `@Volatile` a `AndroidSchedulerHolder.scheduler`.
7. `TaskListScreen.kt:1020` — quitar `!!` innecesario (smart-cast ya cubre).
8. `FirestoreRepository.kt` `reassignTaskCompletion`/`completeAssignment` — mover invalidación de caché a `finally` (mismo patrón que completeTask/undoTaskCompletion de v12).

**Hallazgos CRÍTICOS que requieren decisión/backend (NO aplicables mecánicamente, documentar como PROPUESTA fuerte):**
- `donatePoints` puede duplicar puntos ante timeout ambiguo (requiere reclasificar IOException vs fallo definitivo en 3 call-sites: donatePoints, appreciateMember, redeemReward).
- `isPeerPointsTransfer` sin rate-limit (requiere Cloud Function transaccional).
- Auto-edición `members/{mid}` sin tope en `totalPoints` (requiere decisión de producto sobre revalidar la premisa "cero usuarios reales" antes de tocar `firestore.rules`, cambio de regla propuesto por el experto de seguridad).

### Oleada 3 (completa, 4/4 tras reintento de Rendimiento)

**#11 Rendimiento** — v12: uiToolingPreview sigue eliminado sin regresión (YA RESUELTO). SplashScreen delay(1500) sigue igual (propuesta de marca, sin acción). Bundle web sin cambios de conclusión. Nuevo MENOR APLICABLE: `CalendarScreen.kt:133` recrea lambda `s` de traducción y callbacks inline en cada recomposición (mismo antipatrón que `HouseholdScreen` ya corrigió con `remember`). Confirmación positiva extensa: keys en todos los LazyColumn/LazyRow, memoización correcta en TaskListScreen, sin lecturas Firestore duplicadas, NotificationPollWorker sin regresión.



**#10 Privacidad** — v12: UMP/CMP sigue ausente (CRÍTICO, sin cambios); texto privacy.html sobre purga sigue correcto sin regresión; checklist guia-publicacion.md §4 sigue sin mención UMP; gating de edad sin cambios de posicionamiento; iOS/desktop/web siguen sin AdMob/Analytics. Nuevo IMPORTANTE APLICABLE (parcial): borrado de cuenta anonimiza mensajes/comentarios pero NO `taskHistory.memberId`/`rewardRedemptions.memberId` en hogares compartidos (`FirestoreRepository.kt:906-937`) — UID de Google queda incrustado tras borrado "completo", inconsistente con promesa de privacy.html; mitigado porque UI actual no resuelve nombres de miembros no existentes. MENOR APLICABLE: comentario obsoleto en `TaskScreenModel.kt:815` sobre TFCD que no refleja el comportamiento real (siempre child-directed, más conservador de lo que dice, sin riesgo real). Confirmación positiva: borrado de cuenta con cascade real, Analytics sin PII.

**#12 Red/offline** — v12: isOnline() solo en TaskScreenModel sigue abierto; desktop OAuth red caída = cancelación sigue abierto; wasmJs connectTimeoutMillis sigue sin verificar (no accionable sin navegador real). Nuevo CRÍTICO (PROPUESTA, ~20 call sites, alto volumen): el patrón "invalidar caché en `finally`" que v12 aplicó SOLO a completeTask/undoTaskCompletion falta en el resto: `completeAssignment`/`reassignTaskCompletion` (`FirestoreRepository.kt:1308-1311`,`1216-1219`, coincide con hallazgo QA#8), y de forma más amplia en `TaskRepository.kt` (createTask/updateTask/updateSubtasks/deleteTask/assignTask/etc.) y `MemberRepository.kt` (createMember/deleteMember/updateMemberRole/updateMemberStreak/**addMemberPoints mismo**) y `RewardsRepository.kt`. Nuevo IMPORTANTE (PROPUESTA, refactor): `updateTask`/`updateSubtasks`/`updateAssignmentRotation` sin precondition de concurrencia optimista (a diferencia de addMemberPoints/appreciateMember) — "last write wins" silencioso entre 2 dispositivos editando la misma tarea. MENOR: backoff sin jitter en retryTransientReadFailure (ráfaga sincronizada si varios dispositivos reconectan a la vez).

**#13 Cobertura de pruebas** (informe, sin cambios de código) — 269 tests en 26 archivos (vs 257 de v12), incluye 2 archivos nuevos desde v12 (`CalendarScreenTest.kt`, `TaskFormSaveInvariantsTest.kt`) que SÍ cubren el trabajo reciente de calendario/formulario. Los 4 huecos priorizados de v12 (addMemberPoints con floor, funciones puras GoogleDesktopSignInHelper, orquestación appreciateMember/donatePoints, AchievementChecker.getAchievementsWithStatus) siguen TODOS abiertos, código intacto sin diffs desde v12. Hueco NUEVO CRÍTICO: `network/ErrorCategory.kt` (archivo nuevo, `errorCategory()`/`toUserMessageKey()`) sin ningún test pese a ser función pura consumida por 8 ScreenModels/managers — alto radio de impacto en clasificación de errores de toda la app. Sigue sin existir ningún MockEngine en el repo.

**Fixes APLICABLES nuevos identificados en oleada 3:**
9. `TaskScreenModel.kt:815` — corregir/borrar comentario obsoleto sobre TFCD (menor, cosmético).

**Todo lo demás de oleada 3 son PROPUESTAS de alto volumen o requieren decisión de producto/legal — no aplicables mecánicamente en esta ronda** (UMP/CMP, anonimización completa de taskHistory/rewardRedemptions, finally en ~20 call sites de caché, concurrencia optimista en updateTask, tests nuevos para ErrorCategory — este último SÍ podría aplicarse como test nuevo si el análisis final decide priorizarlo).
