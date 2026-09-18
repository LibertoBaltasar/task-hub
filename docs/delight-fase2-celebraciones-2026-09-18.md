# Delight — Fase 2: celebraciones (2026-09-18)

Implementación de la fase 02 del blueprint de `docs/revision-delight-experiencia-2026-09-18.md` (§2.2, §2.5, §2.6, §2.8, §3). Continúa fases 0 (motion-core) y 1 (transiciones), ya en `main`.

## 1. Inventario original → nuevo (con adaptaciones)

### 1.1 `TaskDetailScreen.kt` — botón "Hecho" a nivel de tarea (§2.5)

- Citas del informe: `769-784` (botón) y `139-146` (`LaunchedEffect(actionState)`). Estado real al implementar: botón en `810-825`, `LaunchedEffect` en `140-147` — desfase de ~40 líneas por trabajo de fases previas (v13, fase 0/1), misma intención.
- Patrón `isCompleting` local replicado de `TaskListScreen.TaskCard` (fase 00): `isCompletingTask` gateado con `effectsEnabled(EffectCategory.EFFECTS)` + `shouldReduceMotion()`, muestra `AnimatedCheckmark` + `ConfettiOverlay` (12 partículas, dentro de un `Box` que envuelve solo el botón) y retrasa `onCompleteTask()` 260ms (0ms si reduce-motion).
- Añadido no explícito en el encargo pero necesario: reset de `isCompletingTask` a `false` ante `TaskActionState.Error` (si no, un fallo de red dejaba el check "congelado" en pantalla) — mismo criterio que ya usa `TaskListScreen.TaskCard` con `hasError`.
- `LaunchedEffect(actionState)` de `Content()`: el `navigator.pop()` se retrasa 260ms tras `Success` (0ms si reduce-motion). Nota: este `LaunchedEffect` es compartido con el flujo de **borrar** tarea (`deleteTask`) — el retraso aplica también ahí (mismo interruptor `actionState is Success`); no hay animación visible en el borrado, así que el único efecto es 260ms extra antes del pop, sin regresión funcional.

### 1.2 `TaskDetailScreen.kt` — botón "Hecho" de `AssignmentCard` (§2.5)

- Cita del informe: `1412-1426`. Real: `1464-1479` antes de editar (mismo desfase).
- Mismo patrón `isCompleting` local, esta vez con clave `remember(assignment.id)`. Como `AssignmentCard` no navega atrás, no hace falta retraso de navegación — el ciclo completo (check + confeti) se ve directamente.
- Adaptación: se añadió un parámetro nuevo `isError: Boolean = false` a `AssignmentCard` (no mencionado en el informe) para poder resetear `isCompleting` ante un fallo — sin él, un error de red dejaba el check visible indefinidamente sin volver al botón. Se pasa `isError = actionState is TaskActionState.Error` desde el único call-site con `showComplete = true` (lista de pendientes).

### 1.3 `MemberRewardScreen.kt` — celebrar el canje (§2.6)

- Cita del informe: `91-100`. Real: `100-109` antes de editar.
- Icono de la recompensa envuelto en `Box(Modifier.size(160.dp))` con pulso de escala vía `Animatable` (`1f → 1.15f → 1f`, `spring(DampingRatioMediumBouncy, StiffnessLow)`, `snapTo(1f)` si reduce-motion) + `ConfettiOverlay(particleCount = 8)` sobre esa misma área.
- Adaptación deliberada respecto al informe: el snackbar de texto ("refuerzo accesible") se lanza ahora en una corrutina separada (`coroutineScope.launch`) en vez de esperarse con la corrutina principal antes de continuar — el código original hacía `snackbarHostState.showSnackbar(...)` (que suspende hasta que el snackbar se autodescarta, ~4s con `SnackbarDuration.Short`) y ENTONCES hacía `pop()`; con ese patrón, insertar "retrasar el pop() 550ms" habría sido inobservable (el pop ya iba ~4s tarde por el propio snackbar, no por el retraso pedido). Se desacopla el snackbar del `pop()` para que el retraso de 550ms (0ms si reduce-motion) sea el que realmente gobierna el timing, tal y como pide el informe.

### 1.4 Toast de logro desbloqueado (§2.8 + §3)

- `TaskScreenModel.kt`: `_newlyUnlockedAchievement: MutableStateFlow<Achievement?>` + `newlyUnlockedAchievement: StateFlow<Achievement?>` añadidos junto a `_myAssignment` (línea ~203). En `checkAndAwardAchievements` (real: `1079-1110`, coincide con la cita del informe), tras el bucle de `repo.addMemberAchievement(...)`, se resuelve `newlyUnlocked.firstOrNull()` contra `AchievementChecker.ALL_ACHIEVEMENTS` y se publica. Nuevo método público `clearNewlyUnlockedAchievement()` para que la UI limpie el flow tras mostrarlo.
- **Dependencia ya resuelta en fase 0** (no re-diagnosticada): los logros ya usan `titleKey`/`descKey` + `AppStrings` (i18n ES/EN) en `Achievement.kt` — el informe pedía esta migración como paso previo, pero ya estaba hecha. Solo faltaba la clave `achievement_unlocked_prefix` (ES: "¡Logro desbloqueado!", EN: "Achievement unlocked!"), añadida en `AppStrings.kt`.
- **Archivo nuevo** `ui/components/AchievementToast.kt`: `Card` flotante, `slideInVertically(initialOffsetY = { -it }, spring(DampingRatioMediumBouncy, StiffnessLow)) + fadeIn(tween(300))`, autodescarte a 2500ms con `slideOutVertically(tween(200)) + fadeOut(tween(200))`, reduce-motion → `EnterTransition.None`/`ExitTransition.None` (mismo timeout 2500ms). Sin confeti ni háptico propio (moderación, como pide el informe).
- Observado en `TaskListScreen.kt` y `TaskDetailScreen.kt` (ambos ya tenían un `Box` exterior por el `SnackbarHost`/confirmación de canje; se añadió `AchievementToast` alineado `TopCenter` dentro de ese mismo `Box`).

### 1.5 Hero card de hogar "justCreated" (§2.2)

- `HouseholdScreen` gana `justCreated: Boolean = false`. `CreateProfileScreen.kt` lo pasa `true` solo en el `replaceAll` que sigue a `MemberUiState.Success` (creación real del primer miembro) — el otro `replaceAll` (rama `AlreadyMember`, "saltar este paso") se deja en `false` porque no es un alta nueva.
- `JoinHouseholdScreen.kt`: mismo criterio — `true` solo en el `replaceAll` que sigue a `addMember` con éxito; la rama `AlreadyMember` (ya era miembro) se deja en `false`.
- Cita del informe: hero card en `470-539`. Real: `483-539` antes de editar. Entrada con `scaleIn(0.9f → 1f, spring(...)) + fadeIn(tween(300))` disparada una vez con `LaunchedEffect(Unit)` cuando `justCreated`. Sin confeti (decisión de diseño explícita del informe, §6: el confeti es la firma de "completar tarea").

### 1.6 `JoinHouseholdScreen.kt` — card "Te uniste a..." (§2.2)

- Cita del informe: `199-217`. Real: mismo rango antes de editar.
- **Adaptación importante no anticipada por el informe**: envolver la `Card` en `AnimatedVisibility` DENTRO del `if (joinedHouseholdId != null && ...)` existente no habría animado nada — Compose no reproduce la animación de entrada de un `AnimatedVisibility` que se compone por primera vez ya con `visible = true` (solo anima transiciones true↔false dentro de la misma composición viva). Se movió el bloque "Te uniste a..." fuera del `if` de paso 2 para que `AnimatedVisibility` esté siempre presente en el árbol con `visible = joinedHouseholdId != null && household != null`, permitiendo la transición real `false → true`. El resto del paso 2 (campo de nombre + botón) se mantiene gateado por el `if` original, sin cambios de comportamiento.

## 2. Verificación

1. `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` → **BUILD SUCCESSFUL** (solo warnings preexistentes no relacionados: `when` exhaustivo redundante en `CalendarScreen.kt`/`TaskListScreen.kt`, deprecaciones de Google Sign-In/EncryptedSharedPreferences).
2. `./gradlew :composeApp:jvmTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**. El script `parse-jvm-test-results.py` referenciado no existe en este entorno; se parsearon manualmente los XML de `composeApp/build/test-results/jvmTest/*.xml` (25 suites): **0 failures, 0 errors** en todas. No hay tests que ejerciten `TaskDetailScreen`/`MemberRewardScreen`/`JoinHouseholdScreen`/`HouseholdScreen` (son Composables sin cobertura de UI en este proyecto — solo `AchievementCheckerTest.kt` toca lógica relacionada, sin cambios), así que no hizo falta actualizar ningún test por timing de `pop()`.

## 3. Incidencias

- Ninguna bloqueante. Todo lo listado como `[APLICA YA]`/`[APLICA YA — DECISIÓN DE DISEÑO]` en el alcance de fase 02 se implementó tal cual, con las adaptaciones documentadas arriba (línea real vs. citada, snackbar desacoplado del pop en `MemberRewardScreen`, reordenamiento de `AnimatedVisibility` en `JoinHouseholdScreen`, parámetro `isError` nuevo en `AssignmentCard`).
- Script de parseo de resultados de test (`~/.hermes/skills/.../parse-jvm-test-results.py`) no existe en este entorno — verificado manualmente vía `grep` sobre los XML de JUnit, mismo resultado (0/0).

## Resumen final

`git log --oneline -1` (antes de este commit): `3f2c376 feat: delight — fase 1 transiciones y entradas (2026-09-18)`
