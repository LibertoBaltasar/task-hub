# Delight — fase 3: micro-interacciones y logros (2026-09-18)

Implementación del blueprint fase 03 de `docs/revision-delight-experiencia-2026-09-18.md` (§2.8, §2.9, §2.10; blueprint fase 03). Fuente de verdad = ese informe; las citas archivo:línea se han adaptado al estado real del código (panel v13 + fases 0-2 ya aplicadas). No se ha deshecho ningún trabajo de fases previas.

## 1. Inventario original → nuevo (con adaptaciones)

### 1.1 `StatsScreen.AchievementCard` — entrada al desbloquear (§2.8)

- Citado en el informe como líneas ~505-542; en el estado real del archivo la función está en `StatsScreen.kt:502-538` (sin cambios de fondo desde el informe, solo desplazamiento por ediciones previas).
- Implementado tal cual la spec: `Animatable` único (`entrance`, 0↔1) que combina fade (`alpha`) y scale (`0.92f + 0.08f * entrance`), con `tween(300)` — equivalente a `fadeIn(tween(300)) + scaleIn(initialScale=0.92f, tween(300))` del informe, pero como una sola animación en vez de dos `AnimatedVisibility` compuestas, porque la tarjeta debe permanecer visible cuando está bloqueada (candado) — no es una aparición/desaparición binaria.
- Guarda: `remember(achievement.id, achievement.isUnlocked)` para el `Animatable` + `LaunchedEffect(achievement.id, achievement.isUnlocked)`, exactamente como pide el informe — solo anima la primera vez que `isUnlocked` se observa `true` en esa composición; no se repite en recomposiciones posteriores por refresco de datos mientras siga desbloqueado.
- Gating: `effectsEnabled(EffectCategory.ANIMATIONS)` (fx_animations + reduce-motion, vía la fórmula común de `EffectsGating.kt`) — con reduce-motion la tarjeta aparece directa (`entrance` se fija en 1 sin animar).

### 1.2 `RankingScreen.kt` — rebote de medalla 🥇 (§2.9)

- Citado como líneas ~146-151; en el estado real la definición de `medalEmoji` está en `RankingScreen.kt:167-172` y el `Text` que la pinta en `RankingScreen.kt:207-219` (desplazado por las 3 fases previas más el nuevo bloque de estado añadido en este cambio).
- Solo la posición #1 recibe el rebote (no #2/#3), aplicado únicamente al `Text` del emoji de medalla (no a toda la fila), vía `graphicsLayer { scaleX/scaleY = medalScale }`.
- `medalScale`: `Animatable(0.7f)` → `1f` con `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`, exactamente la spec del informe.
- Guarda: `remember(member.id)` + `LaunchedEffect(member.id)` — si el mismo miembro sigue en 1er puesto en la siguiente carga de `RankingBody`, `LazyColumn` reutiliza el mismo slot (misma `key = member.id`) y el efecto no se repite.
- Gating: `effectsEnabled(EffectCategory.ANIMATIONS)`.

### 1.3 `AnimatedCounter` en los 4 call-sites de §2.10

| Call-site (informe) | Ubicación real | Adaptación |
|---|---|---|
| `HouseholdMemberList.MemberCard`, línea ~284 | `HouseholdMemberList.kt:283-303` | El badge usaba `PointsBadge(text: String)`, que **no admite contenido composable** (solo texto plano) — no se podía envolver el número con `AnimatedCounter` sin tocar el componente genérico, y el informe prohíbe explícitamente animar dentro de `PointsBadge`/`StatChip`. Se sustituyó la llamada por un `Surface`+`Row` local que replica el aspecto exacto de `PointsBadge(tone = Coral)` (mismo `shape`, mismo color `tertiary`/`onTertiary`, mismo padding), con `AnimatedCounter` para el número y un `Text` para el sufijo. `PointsBadge` en sí no se tocó — sigue intacto para sus demás usos (coste, urgencia). |
| `RankingScreen.RankingRow`, línea ~237 | `RankingScreen.kt:262-276` | Directo: el `Text("⭐ $totalPoints")` se dividió en `Text("⭐ ")` + `AnimatedCounter(...)`. Solo puntos, no racha (el informe solo lista puntos para este call-site). |
| `TaskDetailScreen` saldo de "Puntuación", línea ~659 | `TaskDetailScreen.kt:698-717` (informe citaba 659, el bloque real está más abajo por el desarrollo de fases previas) | Directo: `Text("⭐ $memberPoints ...")` dividido en 3 `Text`/`AnimatedCounter` (prefijo emoji, contador, sufijo "pts"). |
| `PublicProfileScreen.StatCard`, líneas ~250/257 | `PublicProfileScreen.kt:247-259` (llamadas) y `277-321` (`StatCard`) | `StatCard` es un componente **local y privado** de esta pantalla (no el `PointsBadge`/`StatChip` genérico compartido), así que sí se modificó directamente: nuevo parámetro `animatedValue: Int?` que, si no es nulo, usa `AnimatedCounter` en vez del `Text(value)` plano. Aplicado a puntos y racha actual (`animatedValue`); "Mejor racha" (`bestStreak`) sigue usando `value: String` sin animar — no estaba en la lista de 2 call-sites del informe. |

Los 4 call-sites usan `AnimatedCounter` tal cual quedó de la fase 0 (`ui/components/AnimatedCounter.kt`) sin modificarlo — su propio gating de reduce-motion (`shouldReduceMotion()` interno) ya cubre la variante sin animación; no se envolvió adicionalmente en `effectsEnabled` porque el componente ya resuelve eso internamente (igual criterio que su uso en fases previas, donde tampoco se dobla el gating).

## 2. Dependencias de fases previas verificadas

- `ui/components/AnimatedCounter.kt` — existe (fase 0), sin cambios necesarios.
- `ui/components/EffectsGating.kt` (`effectsEnabled(category)`) — existe (fase 0), usado para los dos efectos nuevos de entrada (`EffectCategory.ANIMATIONS`), que es la primera vez que esa categoría se consume en un call-site (antes solo estaba definida).

## 3. Salida de verificaciones

1. `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` → **BUILD SUCCESSFUL** (solo warnings preexistentes no relacionados: deprecaciones de Google Sign-In/Vibrator/EncryptedSharedPreferences y `when` exhaustivo redundante en `CalendarScreen.kt`/`TaskListScreen.kt`, ninguno introducido por este cambio).
2. `./gradlew :composeApp:jvmTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**; parseo con `parse-jvm-test-results.py`: **269 tests, 0 failures, 0 errors**.

## 4. Incidencias

Ninguna. El único ajuste no trivial respecto al blueprint fue el tratamiento de `HouseholdMemberList.MemberCard` (punto 1.3), motivado por que `PointsBadge` no admite contenido composable — se resolvió replicando su aspecto visual en el call-site en vez de tocar el componente compartido, en línea con la decisión explícita del informe de no animar dentro de `PointsBadge`/`StatChip`.

## Resumen final

`git log --oneline -1` (antes de este commit): `9f267d7 feat: delight — fase 2 celebraciones (2026-09-18)`
