# Delight — Fase 0: motion-core + Modo simple (2026-09-18)

Ejecuta el blueprint "Fase 00 — motion-core" de
`docs/revision-delight-experiencia-2026-09-18.md` (commit `a7a1743`) más el
control "Modo simple" (decisión del dueño 2026-09-18, no estaba en el
informe original). No se ha rediseñado ningún efecto ya marcado como
"[APLICA YA — DECISIÓN DE DISEÑO]" resuelto — solo se ha movido código,
añadido i18n y añadido el gating de Modo simple a lo ya existente.

## 1. Inventario auditado: original → nuevo

| # | Cambio del encargo | Cita original del informe | Estado real encontrado al implementar | Archivo(s) nuevo/modificado |
|---|---|---|---|---|
| 1 | Extraer `AnimatedCheckmark`/`ConfettiOverlay` | `TaskListScreen.kt` líneas 1093-1171 | **Coincide exacto**: `AnimatedCheckmark` en 1093-1116, `ConfettiOverlay`+`ConfettiParticle` en 1118-1171. Sin adaptación de citas necesaria. | Nuevo `ui/components/CelebrationEffects.kt` (ambos composables, ahora públicos, con `particleCount: Int = 12` añadido a `ConfettiOverlay`). `TaskListScreen.kt`: bloque original borrado, import añadido, 3 imports de Compose que quedaron sin uso tras la extracción (`Animatable`, `LinearEasing`, `Spring`, `spring`, `Canvas`, `Offset`, `Size`, `rotate`, `kotlin.random.Random`) eliminados. |
| 2 | Nuevo `AnimatedCounter.kt` | §2.10, pseudocódigo del informe | El pseudocódigo del informe describe el pulso como un segundo `animateFloatAsState` con target fijo, que en la práctica no produce un pulso real (el `targetValue` nunca cambia). Adaptado a un `Animatable` con dos `animateTo` secuenciales (1→1.08→1) disparados solo cuando `value` sube, respetando la intención del informe ("pulso 1→1.08→1 con spring, solo si sube, sin count-up ni pulso en reduce-motion"). | Nuevo `ui/components/AnimatedCounter.kt`. No aplicado a ningún call-site (es la fase 03, explícitamente fuera de alcance de esta fase). |
| 3 | Chevron rotatorio | `ExpandableSectionHeader.kt` líneas 79-84 | Coincide (bloque `Icon` en 79-85 en el archivo real, offset de 1 línea sin relevancia). | `ui/components/ExpandableSectionHeader.kt`: un único `Icon(KeyboardArrowDown)` con `Modifier.graphicsLayer { rotationZ = angle }`, `angle` animado con `tween(200, FastOutSlowInEasing)` o `tween(0)` si `shouldReduceMotion()`. Import de `Icons.Default.KeyboardArrowUp` eliminado (ya no se usa). |
| 4 | i18n de los 5 logros | `Achievement.kt` líneas 46-52 | Coincide. Ningún otro call-site usaba `Achievement.title`/`.description` salvo `StatsScreen.AchievementCard` (verificado por grep en todo `commonMain`/`commonTest`); los tests de `AchievementCheckerTest` solo usan `AchievementChecker.checkNewAchievements`/`countCompletedFromHistory` (IDs, no objetos `Achievement`), así que no hubo que tocar ningún test. | `Achievement.kt`: `data class Achievement(id, emoji, isUnlocked)` (título/descripción eliminados de los campos); añadidas `titleKey`/`descKey` (derivadas de `id`, p.ej. `achievement_first_task_title`) y `title(lang)`/`description(lang)` vía `AppStrings.get`. `AppStrings.kt`: 10 claves nuevas ES+EN (`achievement_<id>_title`/`_desc`), copy exacto de la tabla §3 del informe. `StatsScreen.kt` `AchievementCard`: añadido `val lang = LocalAppSettings.current.currentLanguage`, llamadas cambiadas a `achievement.title(lang)`/`achievement.description(lang)`. |
| 5 | Control "Modo simple" | Encargo del dueño, sin cita del informe (obligatorio, diseñado en esta fase) | — | Ver sección 2. |

## 2. Diseño del "Modo simple"

### Claves de `SettingsStore`
Mismo patrón que `isVibrationEnabled()`/`setVibrationEnabled()`:

| Método | Clave persistida | Default |
|---|---|---|
| `isSimpleModeEnabled()` / `setSimpleModeEnabled()` | `simple_mode` | `false` |
| `isFxAnimationsEnabled()` / `setFxAnimationsEnabled()` | `fx_animations` | `true` |
| `isFxEffectsEnabled()` / `setFxEffectsEnabled()` | `fx_effects` | `true` |
| `isFxHapticsEnabled()` / `setFxHapticsEnabled()` | `fx_haptics` | `true` |

Se usaron los nombres de clave literales pedidos en el encargo (`simple_mode`,
`fx_animations`, `fx_effects`, `fx_haptics`), sin el prefijo `taskhub_` que sí
llevan las claves preexistentes — es una desviación intencional del patrón
de nombrado existente, siguiendo la instrucción explícita del encargo.

### UI (`SettingsSheet.kt`)
Nueva sección "Modo simple" (`SettingsSection(title = s("simple_mode"))`),
colocada justo después de "Sonido y vibración": texto descriptivo
(`simple_mode_desc`) + 4 filas con `Switch` (mismo patrón visual que el
resto del sheet — `SwitchDefaults.colors` con `primary`/`primaryContainer`):
1. **Modo simple** (maestro, clave `simple_mode`).
2. **Animaciones y transiciones** (`fx_animations`) — `enabled = !simpleModeEnabled`.
3. **Efectos visuales** (`fx_effects`) — `enabled = !simpleModeEnabled`.
4. **Vibración y háptica** (`fx_haptics`) — `enabled = !simpleModeEnabled`.

Los 3 switches de categoría quedan visualmente deshabilitados (grises, sin
interacción) cuando el maestro está activo, pero conservan su valor guardado
sin tocarlo — al desactivar el maestro, se restauran tal cual estaban.

Claves i18n nuevas (ES+EN): `simple_mode`, `simple_mode_desc`,
`fx_animations`, `fx_effects`, `fx_haptics`.

### Helper de gating: `ui/components/EffectsGating.kt`
```kotlin
enum class EffectCategory { ANIMATIONS, EFFECTS, HAPTICS }

@Composable
fun effectsEnabled(category: EffectCategory): Boolean {
    val settingsStore = koinInject<SettingsStore>()
    if (settingsStore.isSimpleModeEnabled()) return false
    return when (category) {
        EffectCategory.ANIMATIONS -> settingsStore.isFxAnimationsEnabled() && !shouldReduceMotion()
        EffectCategory.EFFECTS -> settingsStore.isFxEffectsEnabled() && !shouldReduceMotion()
        EffectCategory.HAPTICS -> hapticsEnabled(settingsStore)
    }
}

fun hapticsEnabled(settingsStore: SettingsStore): Boolean =
    !settingsStore.isSimpleModeEnabled() && settingsStore.isFxHapticsEnabled() && settingsStore.isVibrationEnabled()
```

**Decisión de diseño no explicitada en el encargo**: los `buzz()` de los 4
ScreenModels (`TaskScreenModel`, `MemberScreenModel`, `HouseholdScreenModel`,
`ProfileScreenModel`) NO son `@Composable` — no tienen acceso a
`shouldReduceMotion()` ni a `koinInject`, solo a `SettingsStore` inyectado
por constructor. Por eso `hapticsEnabled(settingsStore: SettingsStore)` existe
como función libre además de como rama de `effectsEnabled` — es la misma
fórmula, expuesta en una forma invocable sin contexto `@Composable`. Las
fases 01-04 (Composables) deben usar `effectsEnabled(category)`; código sin
contexto `@Composable` que necesite gating de háptica debe usar
`hapticsEnabled(settingsStore)` directamente.

### Gating mínimo de efectos existentes (sin rediseñar)
- **`TaskListScreen.TaskCard`**: el bounce de `AnimatedCheckmark` y
  `ConfettiOverlay` se tratan como categoría `EFFECTS`. Se añadió
  `val effectsOn = effectsEnabled(EffectCategory.EFFECTS)`;
  `AnimatedCheckmark(reduceMotion = reduceMotion || !effectsOn)` (se sigue
  mostrando el check, pero sin rebote si los efectos están apagados) y
  `ConfettiOverlay` solo se renderiza si `effectsOn` (además de `!reduceMotion`,
  ya existente). La salida de la card (`scaleX/Y` + `alpha`, `tween(260)`) NO
  se ha tocado — es una transición básica de lista, fuera del alcance de
  "gating mínimo de la celebración" que pide el encargo.
- **`buzz()` en los 4 ScreenModels**: cambiado de
  `if (settingsStore.isVibrationEnabled()) vibrate(kind)` a
  `if (hapticsEnabled(settingsStore)) vibrate(kind)` — mismo comportamiento
  cuando Modo simple está desactivado (la fórmula sigue exigiendo
  `isVibrationEnabled()`), y ahora también respeta el maestro y `fx_haptics`.

**No tocado** (prohibido explícitamente): `App.kt` (transición global),
sonido (ningún código nuevo), animaciones continuas, el resto de patrones
"ya resuelto" del informe (celebración de `TaskDetailScreen` — no existe
todavía, es fase 02 — menú FAB, `HouseholdTaskSection`).

## 3. Verificación

1. `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` →
   **`BUILD SUCCESSFUL`** (13s). Sin warnings nuevos atribuibles a este
   cambio (los warnings preexistentes de `GoogleSignIn`/`EncryptedSharedPreferences`/
   `CalendarScreen`/`TaskListScreen` `when` exhaustivo ya estaban antes).
2. `./gradlew :composeApp:jvmTest --rerun-tasks --console=plain` →
   `BUILD SUCCESSFUL`, y verificado con
   `parse-jvm-test-results.py` sobre `composeApp/build/test-results/jvmTest/*.xml`:
   **`TOTAL: 269 tests, 0 failures, 0 errors`** (incluye
   `SettingsStoreTest: 10 tests` y `AchievementCheckerTest: 6 tests`, ambos
   sin cambios necesarios — ningún test asertaba el comportamiento antiguo
   de título/descripción hardcodeado ni de `isVibrationEnabled()` en `buzz()`).

## 4. Incidencias resueltas

- El pseudocódigo de `AnimatedCounter` en el informe (§2.10) no producía un
  pulso real con `animateFloatAsState` de target fijo; se sustituyó por un
  `Animatable` con dos `animateTo` en secuencia. Documentado en la fila #2
  de la tabla de la sección 1. No cambia la intención (pulso de escala solo
  al subir, spring `DampingRatioMediumBouncy`, sin pulso en reduce-motion).
- Tras extraer `AnimatedCheckmark`/`ConfettiOverlay`, quedaron 9 imports sin
  uso en `TaskListScreen.kt` (`Animatable`, `LinearEasing`, `Spring`, `spring`,
  `Canvas`, `Offset`, `Size`, `rotate`, `kotlin.random.Random`) — eliminados
  para no dejar basura de imports.
- Ningún test existente asertaba el comportamiento antiguo (título/descripción
  hardcodeados en `Achievement`, o `isVibrationEnabled()` puro en `buzz()`),
  así que no hizo falta actualizar ningún test.

## Resumen final

`git log --oneline -1` (antes de este commit): `9ede2ec auditoría panel expertos v13 2026-09-18`
