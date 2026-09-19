# Delight — fase 4: gráficas de Estadísticas con entrada animada (2026-09-18)

Fuente de verdad: `docs/revision-delight-experiencia-2026-09-18.md` (commit `a7a1743`), sección 2.8, fila "`BarChartCard`/`PointsChartCard`/`PieChartCard`". Gating: `effectsEnabled(EffectCategory.ANIMATIONS)` (fase 0, `ui/components/EffectsGating.kt`) — ya existía, no hizo falta crearlo.

## 1. Inventario original → nuevo

| Cita del informe | Estado real encontrado | Adaptación |
|---|---|---|
| `rememberChartEntranceProgress(): Float` nuevo, en `StatsScreen.kt` o `ui/components/` | No existía ningún helper de entrada compartido para gráficas. | Creado como `private fun` en `StatsScreen.kt` (líneas ~239-256, justo antes de `ChartCard`), ya que solo lo usan los 3 `Canvas` de este archivo — no se generalizó a `ui/components/` para no crear una abstracción sin más de un consumidor futuro. |
| `Animatable(0f)` → `1f`, `tween(600, easing=FastOutSlowInEasing)`, `LaunchedEffect(Unit)`, sin repetir en refrescos de datos; reduce-motion → `1f` directo | — | Implementado tal cual. Gating vía `effectsEnabled(EffectCategory.ANIMATIONS)` (ya combina modo simple + interruptor `fx_animations` + `shouldReduceMotion()`) en vez de leer `shouldReduceMotion()` suelto — mismo patrón que ya usa `AchievementCard` en este mismo archivo. `remember`/`LaunchedEffect` sin claves ⇒ no se repite en recomposiciones por refresco de datos, solo si el composable sale y vuelve a entrar en composición. |
| Barras: `barHeight * progress` | `BarChartCard.kt` (ahora `StatsScreen.kt:274-343` aprox.) calculaba `barHeight` una vez por barra. | `val barHeight = (... ) * entranceProgress`. La etiqueta numérica sobre la barra usa la misma `barHeight` ya escalada, así que sube junto con la barra en vez de quedar flotando en su posición final. |
| Línea de puntos: "los últimos `(1-progress) * pointCount` puntos no se dibujan" | `PointsChartCard` dibujaba todos los segmentos y círculos de una vez. | Implementado con una interpolación adicional para que el trazo avance de forma continua en vez de a saltos discretos entre puntos: `fullyVisible = (points.size * progress).toInt()` segmentos/puntos completos + un segmento parcial interpolado (`Offset` intermedio) hacia el siguiente punto aún oculto. Las etiquetas de eje (`dp.dayLabel`) se dejaron sin animar — no son parte de la "polyline" que menciona el informe y ocultarlas habría movido el layout de forma confusa. |
| Tarta: `sweepAngle = sweepAngle * progress` por porción | `PieChartCard` dibujaba cada `drawArc` con el `sweep` final. | `sweepAngle = sweep * entranceProgress` en el `drawArc`; `startAngle` sigue acumulando el `sweep` sin escalar para que cada porción crezca desde su ángulo real, no desde 0, y todas terminen alineadas al llegar a `progress = 1`. |
| `StreakCard` sin cambios; verificar `AnimatedCounter` de fase 3 en los call-sites para no duplicar | — | Verificado: `StatsScreen.kt` no usa `AnimatedCounter` en ningún sitio (`grep` sin resultados). No se tocó `StreakCard`. |

## 2. Verificación

1. `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` → `BUILD SUCCESSFUL` (34s).
2. `./gradlew :composeApp:jvmTest --rerun-tasks --console=plain` → `BUILD SUCCESSFUL`; parseado con `parse-jvm-test-results.py`: **269 tests, 0 failures, 0 errors** (incluye `StatsScreenModelTest`, sin regresiones).

## 3. Incidencias

Ninguna. Único ajuste de diseño no explícito en el informe: la interpolación del segmento parcial en `PointsChartCard` (en vez de solo truncar la lista de puntos) para que el trazo se vea continuo — coherente con "revela la línea de izquierda a derecha" del informe, mismo espíritu, mecánica algo más suave.
