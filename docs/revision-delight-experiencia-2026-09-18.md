# Revisión de delight / experiencia (animaciones, háptica, micro-interacciones) — 2026-09-18

Encargo del dueño (Liberto, transcripción de voz 2026-09-18): hacer la experiencia dentro de la app más disfrutable — animaciones, respuesta háptica, efectos visuales, transiciones entre pantallas, aparición de opciones, y "alguna celebración satisfactoria" al completar tareas y momentos afines, **sin sobrecargar** ("no quiero que sea algo demasiado sobrecargado, pero sí que tenga detalles interesantes"). Revisión completa de la app, no solo de los ejemplos citados.

**Hallazgo de partida que cambia el enfoque de este informe:** la app YA tiene una base de motion/háptica considerablemente más madura de lo que el encargo original asumía ("verificar si existe hoy CUALQUIER háptica — probablemente no"). Existe:

- `platform/Haptics.kt`: sistema háptico expect/actual completo (`HapticKind`: SUCCESS/ERROR/WARNING/LIGHT/MEDIUM/HEAVY/SELECTION), implementado en Android (Vibrator/VibrationEffect), iOS (UIFeedbackGenerator), JVM y wasmJs (no-op). Ya conectado en 4 ScreenModels (`TaskScreenModel`, `MemberScreenModel`, `HouseholdScreenModel`, `ProfileScreenModel`) a través de un helper `buzz(kind)` que respeta `SettingsStore.isVibrationEnabled()`.
- `ui/components/ShouldReduceMotion.kt`: señal de accesibilidad "reducir movimiento" expect/actual, implementada en Android (reactiva a cambios), iOS (`UIAccessibilityIsReduceMotionEnabled`) y consultada ya en 7 puntos de la app (`App.kt`, `SplashScreen`, `HomeScreen`, `TaskListScreen` ×2, `HouseholdTaskSection`, `CreateTaskScreen`).
- Transición de navegación global ya animada: `App.kt:306-339` envuelve el único `Navigator` de Voyager con `SlideTransition` (o `FadeTransition` si `reduceMotion`) — **toda** la navegación push/pop de la app ya tiene transición de deslizamiento, no hace falta rediseñarla.
- Una celebración de completar tarea **ya implementada y bien resuelta** en `TaskListScreen.kt`: check animado con rebote de resorte (`AnimatedCheckmark`, líneas 1093-1116) + confeti mínimo dibujado a mano con `Canvas` sin librería (`ConfettiOverlay`, líneas 1118-1171, 12 partículas, 1000ms) + salida de la card con escala/alpha (líneas 842-851), todo con variante `reduceMotion`.

Esto reorienta el encargo: en vez de "diseñar desde cero", el trabajo real es **auditar dónde este lenguaje visual ya resuelto NO se aplica todavía** (inconsistencias entre pantallas que hacen la misma acción) y **rellenar los huecos genuinos** (pantallas enteras sin una sola línea de animación, como `CalendarScreen.kt` y `EditTaskScreen.kt`) — manteniendo la moderación que el dueño pidió explícitamente.

Archivos leídos completos para esta auditoría: `App.kt`, `SplashScreen.kt`, `WelcomeScreen.kt`, `HomeScreen.kt`, `TaskScreenModel.kt`, `PointsBadge.kt`, `HouseholdTaskSection.kt`, `TaskListScreen.kt`, `TaskDetailScreen.kt`, `HouseholdScreen.kt`, `RewardListScreen.kt`, `MemberRewardScreen.kt`, `CreateRewardScreen.kt`, `MemberScreenModel.kt`, `CreateHouseholdScreen.kt`, `JoinHouseholdScreen.kt`, `RankingScreen.kt`, `ExploreScreen.kt`, `ProfileScreen.kt`, `NotificationListScreen.kt`, `ExpandableSectionHeader.kt`, `HouseholdMemberList.kt`, `HouseholdChatSection.kt`, `HouseholdDialogs.kt`, `DestructiveConfirmDialog.kt`, `EmptyStateIllustrations.kt`, `TaskHubTopBar.kt`, `UserAvatar.kt`, `StatusDot.kt`, `PersonalSpaceScreen.kt`, `CreateProfileScreen.kt`, `AuthGateScreen.kt`, `StatsScreen.kt`, `Achievement.kt`, `ShouldReduceMotion.kt` (+ actuals), `ShimmerPlaceholder.kt`, `Haptics.kt` (+ actuals), `SettingsSheet.kt`, `EditProfileScreen.kt`, `PublicProfileScreen.kt`. Leídos parcialmente/grep dirigido: `CreateTaskScreen.kt` (sección plantillas + imports), `CalendarScreen.kt` (cabecera + grep de patrones de animación, sin coincidencias), `EditTaskScreen.kt` (grep de patrones de animación, sin coincidencias), `Theme.kt` (paleta de colores), `AppStrings.kt` (claves de logros).

---

## 1. Resumen ejecutivo — 7 oportunidades de mayor impacto

1. **Paridad de celebración al completar tarea entre `TaskDetailScreen` y `TaskListScreen`.** Hoy la misma acción (`completeTask`/`completeAssignment`) tiene check animado + confeti en la lista, pero en el detalle es un botón plano que además navega atrás (`pop()`) antes de que se pudiera ver ninguna animación. Impacto alto, esfuerzo bajo (reutilizar código ya escrito).
2. **Logro desbloqueado, hoy 100% silencioso.** `checkAndAwardAchievements` (TaskScreenModel.kt:1079-1110) desbloquea logros en segundo plano sin ningún aviso — el usuario solo lo descubre si entra manualmente a Estadísticas y nota que una tarjeta ya no tiene candado. Es el momento de "celebración satisfactoria" con más potencial de todo el encargo y no existe. Impacto alto, esfuerzo medio.
3. **Rotación del chevron en `ExpandableSectionHeader`.** Un único componente compartido usado en 6+ sitios (secciones de Home, miembros, tareas, plantillas, cabeceras de grupo) hoy cambia de icono de forma instantánea en vez de rotar. Arreglarlo en un solo archivo mejora la app entera. Impacto medio-alto, esfuerzo mínimo.
4. **Celebración al canjear una recompensa.** Hoy es solo un `Snackbar` de texto genérico (`MemberRewardScreen.kt:91-100`), pese a que "gastar puntos en algo divertido" es justo el tipo de momento que el dueño pidió celebrar. Impacto alto, esfuerzo bajo (reutiliza componentes de celebración ya existentes).
5. **`CalendarScreen.kt` (1256 líneas) y `EditTaskScreen.kt` (1245 líneas) no tienen ni una sola línea de animación.** Son las dos pantallas más grandes de la app y las únicas completamente estáticas — el cambio de mes/semana en el calendario es un corte duro de contenido. Impacto medio, esfuerzo medio.
6. **Contador de puntos animado (bump/count-up), ausente en toda la app.** Los totales de puntos (saldo de miembro, ranking, insignias) nunca animan su cambio de valor pese a ser el corazón del bucle de gamificación. Una sola pieza de infraestructura reutilizable en 4+ sitios. Impacto medio-alto, esfuerzo bajo-medio.
7. **Gráficas de Estadísticas sin animación de entrada.** Barras/línea/tarta se dibujan de golpe con su valor final. Una animación de entrada única (no en bucle, coherente con "no sobrecargado") en el momento de "mirar mi progreso". Impacto medio, esfuerzo medio.

---

## 2. Inventario por pantalla/flujo

Leyenda: **[APLICA YA]** = mecánica sin decisión de diseño pendiente · **[APLICA YA — DECISIÓN DE DISEÑO]** = decisión visual ya resuelta en este informe · **[REQUIERE DECISIÓN]** = veto genuino, ver sección 6.

### 2.1 Arranque: Splash → AuthGate → Welcome/Home

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `SplashScreen.kt:43-46`: fade-in único (logo+texto juntos) 800ms, ya con reduce-motion. | Mantener tal cual — ya es sutil y correcto. Único ajuste: el logo (`AppLogo`) podría entrar 100ms antes que el texto (stagger de 100ms) para dar sensación de secuencia en vez de bloque monolítico. Disparador: `visible=true` (línea 41); logo `alpha` anima con `tween(800)` sin delay, texto con `tween(800, delayMillis=100)`. | [APLICA YA] | Bajo/Muy bajo |
| `AuthGateScreen.kt` (167 líneas): completamente estático — logo, título, subtítulo y botón aparecen todos de golpe en el primer frame. Es la pantalla que ve cualquier usuario que abra la app sin sesión cacheada (arranque en frío). | Fade-in del bloque completo (`AppLogo` + textos + botón) igual que `SplashScreen`: `alpha 0→1` en `tween(400)`, sin stagger (mantiene el conjunto como una unidad, es una pantalla de espera funcional, no un momento de celebración). reduce-motion → alpha=1 directo. | [APLICA YA] | Bajo/Bajo |
| `WelcomeScreen.kt:88-173`: emoji, título, subtítulo y 2-3 botones aparecen todos en el primer frame sin ningún fade. | Fade-in del bloque de contenido (`Column` líneas 81-174) completo, `tween(300)`, sin stagger por botón (evita "sobrecargado" — un solo fade de conjunto, no 4 animaciones independientes). reduce-motion → sin cambio. | [APLICA YA] | Bajo/Bajo |

### 2.2 Crear/unirse a un hogar (momento de éxito explícitamente citado por el dueño)

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `CreateHouseholdScreen.kt:53-57`: al crear el hogar, `navigator.replaceAll(CreateProfileScreen(...))` es instantáneo — no hay ningún acuse de "hogar creado" antes de saltar al siguiente formulario. | Ninguna pantalla intermedia nueva (evita sobrecarga): el salto ya lo cubre la transición global de Voyager (`SlideTransition`, App.kt:338). El "beat" de éxito se traslada al hero card de `HouseholdScreen` (ver fila siguiente) una vez el flujo completo termina. | [APLICA YA — DECISIÓN DE DISEÑO] | — |
| `JoinHouseholdScreen.kt:199-217`: al validar el código, la card "Te uniste a {hogar}" aparece con una recomposición plana (paso 1→2 dentro de la misma pantalla). | Envolver la `Card` de las líneas 199-217 en `AnimatedVisibility(visible = joinedHouseholdId != null, enter = fadeIn(tween(250)) + scaleIn(initialScale=0.92f, animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy, stiffness=Spring.StiffnessLow)))`. Mismo spring que ya usa `AnimatedCheckmark` en TaskListScreen — coherencia de "sensación" en toda la app. reduce-motion → `EnterTransition.None`. | [APLICA YA — DECISIÓN DE DISEÑO] | Medio/Bajo |
| `CreateProfileScreen.kt:76-80`: al crear el primer miembro, `navigator.replaceAll(HouseholdScreen(...))` instantáneo — mismo patrón que arriba. | Igual que `CreateHouseholdScreen`: sin pantalla intermedia; el beat se traslada al hero card de `HouseholdScreen` (ver 2.3). | [APLICA YA — DECISIÓN DE DISEÑO] | — |
| `HouseholdScreen.kt:470-539`: hero card con nombre del hogar + código de invitación — hoy aparece igual la primera vez que se llega tras crear/unirse que en cualquier visita posterior. | **Decisión de diseño**: `HouseholdScreen` acepta un parámetro opcional `justCreated: Boolean = false` (pasado por `CreateProfileScreen`/`JoinHouseholdScreen` al hacer `replaceAll`). Si es `true`, el hero card hace una entrada única: `scaleIn(initialScale=0.9f, spring(dampingRatio=MediumBouncy, stiffness=Low)) + fadeIn(tween(300))`, disparada una vez con `LaunchedEffect(Unit)`. **Sin confeti aquí** (ver §6 razones de moderación: el confeti es la firma visual de "completar tarea", reutilizarlo en cada momento de éxito le quitaría distinción — juicio de diseño resuelto, no pendiente). El háptico `SUCCESS` ya se dispara desde `MemberScreenModel.addMember` (línea 155) — no añadir uno nuevo. | [APLICA YA — DECISIÓN DE DISEÑO] | Alto/Medio |

### 2.3 Home / navegación entre hogares

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `HomeScreen.kt:128-157`: menú del FAB (crear hogar / unirse) ya con `AnimatedVisibility` (`fadeIn()+expandVertically()`), reduce-motion respetado. | Ya resuelto — sin cambios. | — | — |
| `HouseholdTaskSection.kt:104-108`: tarjeta de cada hogar en Home, expandir/colapsar ya con `AnimatedVisibility(expandVertically/shrinkVertically)`, reduce-motion respetado. Su cabecera usa `ExpandableSectionHeader` (ver 2.9, chevron sin rotar). | Ya resuelto salvo el chevron (ver 2.9). | — | — |
| `HomeScreen.kt:214-283`: lista de hogares (`LazyColumn` con `item(key=...)`) sin `Modifier.animateItem()` — si el número de hogares/orden cambiara no habría transición de reordenado. Impacto bajo en la práctica (el orden es estable: Personal primero, luego compartidos), no se prioriza. | Añadir `Modifier.animateItem()` a los items de hogares compartidos (línea ~253-261), igual que ya hace `TaskListScreen`. | [APLICA YA] | Bajo/Muy bajo |

### 2.4 Lista de tareas (`TaskListScreen.kt`) — referencia, ya resuelta

Todo el flujo de completar tarea desde la lista está resuelto con buen criterio:
- `TaskCard` (líneas 797-1087): al pulsar "Hecho", `isCompleting=true` dispara `AnimatedCheckmark` (spring bounce) + `ConfettiOverlay` (12 partículas, `Canvas`, 1000ms) superpuestos a la card, y solo tras 260ms (o 0ms si reduce-motion) se llama a `onComplete()` real — la card sale con `scaleX/Y 1→0.92` + `alpha 1→0` en `tween(260)`.
- `Modifier.animateItem()` (línea 780) anima el traslado de la tarea entre grupos (p.ej. de "Hoy" a "Completadas hoy") en vez de recrearla de golpe.
- Filtros/orden con `ShimmerList` en carga, sin animación de entrada por chip — correcto, son controles de uso frecuente donde animar cada tap sería "sobrecargado".

No se propone ningún cambio aquí — es el patrón de referencia que el resto de la app debe igualar.

### 2.5 Detalle de tarea (`TaskDetailScreen.kt`) — gap #1 del resumen ejecutivo

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| Botón "Hecho" a nivel de tarea (`TaskDetailScreen.kt:769-784`): `Button` plano, sin animación; al completarse, `LaunchedEffect(actionState)` (líneas 139-146) llama `navigator.pop()` en cuanto `actionState` pasa a `Success` — no da tiempo a ver ninguna animación aunque se añadiera. | Extraer `AnimatedCheckmark` y `ConfettiOverlay` de `TaskListScreen.kt` (líneas 1093-1171) a un archivo compartido `ui/components/CelebrationEffects.kt` (mismo código, sin duplicar). Aplicar el mismo patrón `isCompleting` local que ya usa `TaskCard` (líneas 824-851): al pulsar, mostrar `AnimatedCheckmark` + `ConfettiOverlay` sobre el botón, y retrasar la llamada real a `onCompleteTask()` 260ms (0ms si reduce-motion). **Retrasar también el `navigator.pop()`** de la línea 144: cambiar el `LaunchedEffect(actionState)` para esperar esos mismos 260ms tras `Success` antes de `pop()` (o inmediato si reduce-motion) — sin este cambio, la animación seguiría sin poder verse nunca. | [APLICA YA — DECISIÓN DE DISEÑO] | Alto/Bajo (reutiliza código) |
| Botón "Hecho" de `AssignmentCard` (`TaskDetailScreen.kt:1412-1426`, usado en la sección "Pendientes"): mismo patrón plano. | Mismo tratamiento que arriba, aplicado a `AssignmentCard`. Como `AssignmentCard` no navega atrás (se queda en la pantalla, la asignación pasa a la lista de completadas), aquí SÍ se ve completo el ciclo check+confeti sin necesidad de retrasar ninguna navegación. | [APLICA YA — DECISIÓN DE DISEÑO] | Alto/Bajo |
| Sección "Puntuación" y "Otros" (`TaskDetailScreen.kt:582-594`, `888-904`): usan `ExpandableSectionHeader` — mismo gap de chevron que el resto de la app (ver 2.9). | Cubierto por el fix único de 2.9. | — | — |
| Checklist de subtareas (`TaskDetailScreen.kt:549-573`): `Checkbox` estándar de Material3 (ya tiene su propia animación de check integrada) + háptico `SELECTION` ya disparado desde `TaskScreenModel.toggleSubtask` (línea 1150). | Ya resuelto — sin cambios. | — | — |

### 2.6 Recompensas (crear, listar, canjear) — gap #4 del resumen ejecutivo

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `MemberRewardScreen.kt:91-100`: al canjear, `LaunchedEffect(actionState)` solo muestra `snackbarHostState.showSnackbar(...)` con texto y hace `pop()` — el icono grande de la recompensa (línea 124-127, ya visible en pantalla) no reacciona en absoluto. Háptico `SUCCESS` ya se dispara desde `MemberScreenModel.redeemReward` (línea 314). | Al recibir `RewardActionState.Success`: (1) animar el icono de la recompensa con el mismo spring bounce que `AnimatedCheckmark` (`scale 1→1.15→1`, `spring(dampingRatio=MediumBouncy, stiffness=Low)`); (2) disparar `ConfettiOverlay` (versión reducida, 8 partículas en vez de 12, sobre el área del icono con `Modifier.size(160.dp)` en vez de `matchParentSize()` — más contenido que el de completar tarea, ya que aquí no hay una card que "desaparece" de la lista); (3) retrasar el `pop()` 550ms (0ms si reduce-motion) para que la animación sea visible antes de volver a la lista. El snackbar de texto se mantiene como refuerzo accesible (TalkBack/VoiceOver no "ve" el confeti). | [APLICA YA — DECISIÓN DE DISEÑO] | Alto/Bajo (reutiliza `ConfettiOverlay` extraído en 2.5) |
| `RewardListScreen.kt` / `RewardsBody` (líneas 184-213): rejilla `LazyVerticalGrid` sin animación de entrada ni al añadir/borrar una recompensa. | Añadir `Modifier.animateItem()` a los items de la rejilla (línea 192) — igual mecánica que `TaskListScreen`, cubre tanto la entrada de una recompensa nueva como la salida de una borrada. | [APLICA YA] | Medio/Bajo |
| `CreateRewardScreen.kt:155-204`: el selector de emoji (`if (showEmojiPicker)`) aparece/desaparece de forma instantánea (no usa `AnimatedVisibility`), a diferencia del resto de secciones plegables de la app. | Envolver el bloque en `AnimatedVisibility(visible=showEmojiPicker, enter=fadeIn()+expandVertically(), exit=fadeOut()+shrinkVertically())`, mismo patrón que `HouseholdTaskSection`/`QuickTemplatesSection`. reduce-motion → `EnterTransition.None`/`ExitTransition.None`. | [APLICA YA] | Bajo/Bajo |
| `EditProfileScreen.kt:194-235`: mismo patrón — el grid de emoji de perfil también aparece/desaparece sin `AnimatedVisibility`. | Mismo fix que arriba. | [APLICA YA] | Bajo/Bajo |

### 2.7 Agradecer / Donar puntos entre miembros

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `HouseholdDialogs.kt` (`AppreciateDialog`/`DonateDialog`/`TransferAmountDialog`, líneas 216-359): `AlertDialog` estándar de Material3, sin ninguna animación propia más allá de la entrada/salida por defecto del diálogo. Al confirmar, `HouseholdScreen.kt:206-217` cierra el diálogo y muestra un snackbar de texto plano. Háptico `SUCCESS`/`ERROR` ya disparado desde `MemberScreenModel.appreciateMember`/`donatePoints` (líneas 363/394). | Mantener el `AlertDialog` tal cual (son formularios con validación de importe, no un momento de celebración en sí — animar un diálogo de introducción de datos añadiría movimiento donde el usuario necesita estabilidad, contradice la moderación pedida). Único cambio: el badge de puntos del miembro DESTINO en `HouseholdMemberList` debe reflejar el nuevo saldo con el contador animado (ver 2.10) en vez de saltar directamente al nuevo número tras `loadMembers()`. | [APLICA YA — DECISIÓN DE DISEÑO] | Bajo/Bajo |

### 2.8 Logros / Estadísticas — gaps #2 y #7 del resumen ejecutivo

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `TaskScreenModel.checkAndAwardAchievements` (líneas 1079-1110): calcula `newlyUnlocked: List<String>` (vía `AchievementChecker.checkNewAchievements`) y solo los persiste (`repo.addMemberAchievement`) — el resultado se descarta, no hay ninguna señal hacia la UI. | **Nueva pieza de estado**: añadir `_newlyUnlockedAchievement: MutableStateFlow<Achievement?>` a `TaskScreenModel`, expuesto como `newlyUnlockedAchievement: StateFlow<Achievement?>`. En `checkAndAwardAchievements`, tras `repo.addMemberAchievement(...)`, resolver el primer id de `newlyUnlocked` contra `AchievementChecker.ALL_ACHIEVEMENTS` y publicarlo (si hay varios desbloqueados a la vez, solo el primero — evita apilar varios toasts, moderación). `TaskListScreen` y `TaskDetailScreen` observan este flow con `LaunchedEffect` y muestran un **toast de logro** no bloqueante: `Card` superpuesta en la parte superior de la pantalla (no un `AlertDialog` — no debe interrumpir), `slideInVertically(initialOffsetY={-it}, spring(dampingRatio=MediumBouncy, stiffness=Low)) + fadeIn(tween(300))`, contenido = emoji del logro + `achievement_unlocked_prefix` + título; se retira sola tras 2500ms con `slideOutVertically(tween(200)) + fadeOut(tween(200))`. Tras mostrarlo, el ScreenModel limpia el flow a `null` (evita que reaparezca en una recomposición posterior). Sin confeti adicional (ya hay uno en curso por completar la tarea — apilar dos celebraciones simultáneas viola la moderación pedida) y sin háptico adicional (el `SUCCESS` de completar la tarea ya cubre el momento). reduce-motion → aparece/desaparece sin animar (alpha instantáneo), mismo timeout de 2500ms. | [APLICA YA — DECISIÓN DE DISEÑO] | Alto/Medio |
| **Dependencia de i18n**: los 5 logros en `Achievement.kt:46-52` (`ALL_ACHIEVEMENTS`) están **hardcodeados en español** ("Primera tarea", "5 días seguidos"...), sin pasar por `AppStrings` — hoy invisible porque solo se listan en `StatsScreen` sin que nadie haya notado que no hay versión EN. El toast propuesto los mostraría de forma mucho más prominente a usuarios en inglés. Corrección necesaria (no es una decisión de diseño, es completar el i18n ya existente en el resto de la app): mover título/descripción de cada logro a `AppStrings` (`achievement_first_task_title`/`_desc`, `achievement_streak_5_title`/`_desc`, `achievement_100_points_title`/`_desc`, `achievement_10_tasks_title`/`_desc`, `achievement_early_bird_title`/`_desc`) y resolver con `s(key)` tanto en `StatsScreen.AchievementCard` como en el nuevo toast. Copy exacto en la sección 5 (blueprint). | [APLICA YA] | — |
| `StatsScreen.AchievementCard` (líneas 505-542): estático, sin transición al pasar de bloqueado→desbloqueado. | Entrada única (`fadeIn(tween(300)) + scaleIn(initialScale=0.92f, tween(300))`) disparada solo la primera vez que `achievement.isUnlocked` se observa `true` en esa composición (`remember(achievement.id, achievement.isUnlocked)` como guarda) — cubre el caso de "vuelvo a Estadísticas después de desbloquear uno y quiero notarlo", complementario al toast (que cubre el momento exacto de completar la tarea). | [APLICA YA — DECISIÓN DE DISEÑO] | Medio/Bajo |
| `BarChartCard`/`PointsChartCard`/`PieChartCard` (`StatsScreen.kt:252-467`): las 3 gráficas `Canvas` dibujan su valor final en el primer frame, sin ninguna animación de entrada. | Un único helper compartido `rememberChartEntranceProgress(): Float` (nuevo, en `StatsScreen.kt` o `ui/components/`): `Animatable(0f)` animado a `1f` con `tween(600, easing=FastOutSlowInEasing)` en un `LaunchedEffect(Unit)` la primera vez que el composable entra en composición (no se repite en recomposiciones por refresco de datos); reduce-motion → devuelve `1f` directo. Aplicación: **barras** — cada barra usa `barHeight * progress` como altura real dibujada; **línea de puntos** — los últimos `(1-progress) * pointCount` puntos de la polyline no se dibujan (revela la línea de izquierda a derecha); **tarta** — `sweepAngle = sweepAngle * progress` por porción. Una sola pasada, sin bucle, coherente con "no sobrecargado". | [APLICA YA — DECISIÓN DE DISEÑO] | Medio/Medio |
| `StreakCard` (`StatsScreen.kt:182-233`): números de racha estáticos. | Sin cambio de animación de entrada (evita sobrecargar una tarjeta ya con 2 emojis grandes); si el valor de racha cambia respecto al que había en memoria en la sesión, usar el contador animado de 2.10 (`AnimatedCounter`) en vez de un salto directo — mismo mecanismo que el resto de contadores de puntos, no un efecto nuevo. | [APLICA YA — DECISIÓN DE DISEÑO] | Bajo/Bajo |

### 2.9 Ranking

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `RankingScreen.kt:119-133`: `itemsIndexed(members, key={_, m -> m.id})` sin `Modifier.animateItem()` — si dos miembros intercambian posición (cambia el orden por puntos), la fila salta de sitio sin transición. | Añadir `Modifier.animateItem()` a `RankingRow` (línea 125), igual mecánica que `TaskListScreen`. | [APLICA YA] | Medio/Bajo |
| Medalla de la posición #1 (`RankingScreen.kt:146-151`, emoji 🥇): estática igual que el resto de posiciones. | **Decisión de diseño**: solo la posición #1 (no #2/#3, para mantener el efecto especial y evitar "sobrecargar" toda la lista) recibe una entrada con rebote la primera vez que esa fila se compone en esa posición: `scaleIn(initialScale=0.7f, spring(dampingRatio=MediumBouncy, stiffness=MediumLow))` sobre el emoji de medalla únicamente (no sobre toda la fila). Se dispara con `remember(member.id)` — si el mismo miembro sigue en 1er puesto en la siguiente carga, no se repite. | [APLICA YA — DECISIÓN DE DISEÑO] | Medio/Bajo |
| Puntos de cada fila (`RankingScreen.kt:236-241`): número estático. | Usar `AnimatedCounter` (2.10) en vez de texto plano. | [APLICA YA] | — |

### 2.10 Puntos / contadores — gap #6 del resumen ejecutivo

Hoy **ningún** número de puntos de la app anima su cambio de valor: ni el saldo de un miembro tras agradecer/donar (`HouseholdMemberList.kt:284`), ni el ranking (`RankingScreen.kt:237`), ni el saldo mostrado en `TaskDetailScreen` (sección Puntuación, línea 659), ni `PointsBadge` en ningún sitio donde se usa.

**Propuesta única y reutilizable**: nuevo componente `ui/components/AnimatedCounter.kt`:
```
@Composable
fun AnimatedCounter(value: Int, style: TextStyle, color: Color, fontWeight: FontWeight? = null) {
    val reduceMotion = shouldReduceMotion()
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = if (reduceMotion) tween(0) else tween(400, easing = FastOutSlowInEasing),
        label = "pointsCounter"
    )
    val scale by animateFloatAsState(...) // pulso 1→1.08→1 al cambiar `value` (spring), solo si sube
    Text("$animatedValue", style = style, color = color, fontWeight = fontWeight,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
}
```
Disparador: cambio del parámetro `value` (nuevo saldo tras `loadMembers()`/`loadStats()`). Duración count-up 400ms, easing estándar (no springy — un contador que rebota constantemente cansa). El pulso de escala (spring, `dampingRatio=MediumBouncy`) solo se aplica cuando el valor **sube** (ganar/recibir puntos), no cuando baja (donar/canjear) — bajar puntos no es un momento a celebrar. reduce-motion → sin count-up ni pulso, valor directo.

Aplicar en: `HouseholdMemberList.MemberCard` (badge de puntos, línea 284), `RankingScreen.RankingRow` (línea 237), `TaskDetailScreen` saldo de la sección Puntuación (línea 659), `PublicProfileScreen.StatCard` para puntos/racha (líneas 250, 257). **No** aplicar dentro de `PointsBadge`/`StatChip` genéricos (son también usados para valores que no son "puntos ganados", p.ej. coste de una recompensa o urgencia — animar ahí confundiría la semántica); se usa `AnimatedCounter` como wrapper del `Text` en los call-sites concretos listados arriba, no dentro del componente genérico.

Etiqueta: [APLICA YA] (mecánica reutilizable, sin decisión visual pendiente).

### 2.11 Calendario — gap #5 del resumen ejecutivo

`CalendarScreen.kt` (1256 líneas) no contiene ninguna coincidencia de `animate`/`Animat`/`graphicsLayer`/`spring`/`tween` — es la pantalla más grande de la app y la única sin ningún tipo de movimiento, pese a que el encargo cita explícitamente "cómo aparecen las distintas opciones" como ejemplo.

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| Navegación de mes/semana (flechas `KeyboardArrowLeft`/`KeyboardArrowRight`, import ya presente en `CalendarScreen.kt:22-23`): el contenido de la cuadrícula se sustituye de golpe al cambiar de mes/semana. | Envolver la cuadrícula de días en `Crossfade(targetState = visibleMonthOrWeekKey, animationSpec = tween(200))` (API ya disponible en Compose Foundation, sin dependencia nueva). reduce-motion → `Crossfade` con `tween(0)` (mismo criterio que el resto de la app: no eliminar el componente, poner duración 0). | [APLICA YA — DECISIÓN DE DISEÑO] | Alto/Medio |
| Popup de tareas de un día (`Dialog`/`DialogProperties`, import ya presente): usa el diálogo estándar de la plataforma — entrada/salida ya gestionada por Compose/Material3 por defecto. | Sin cambios — un diálogo con más movimiento propio compite con la animación de `Crossfade` de la cuadrícula que hay detrás; mantenerlo con el comportamiento por defecto es la decisión correcta. | [APLICA YA — DECISIÓN DE DISEÑO] | — |

### 2.12 Crear/editar tarea

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `CreateTaskScreen.QuickTemplatesSection` (líneas 1317-1409): ya usa `AnimatedVisibility(expandVertically/shrinkVertically)` con `reduceMotion`. | Ya resuelto — sin cambios. | — | — |
| `EditTaskScreen.kt` (1245 líneas): sin ninguna coincidencia de patrones de animación — a diferencia de `CreateTaskScreen`, ni siquiera tiene el `AnimatedVisibility` de secciones plegables (probablemente porque no incluye la sección de plantillas rápidas, pero cualquier sección plegable equivalente que SÍ tenga debería usar el mismo patrón). | Auditoría de implementación (no de diseño): al tocar este archivo, aplicar el mismo `AnimatedVisibility` + `shouldReduceMotion()` que ya usa `CreateTaskScreen` a cualquier bloque condicional plegable existente (p.ej. selector de recurrencia, penalización) — mismo patrón, no uno nuevo. | [APLICA YA] | Bajo/Bajo (según lo que se encuentre al implementar) |

### 2.13 Notificaciones

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `NotificationListScreen.NotificationCard` (líneas 249-277): `containerColor`/`elevation` cambian de golpe (rama `if (!notification.read) primaryContainer else surface`) al marcar como leída. | `containerColor` vía `animateColorAsState(targetValue = ..., tween(200))` en vez de valor directo — la card se "apaga" suavemente al marcar leída en vez de cambiar de golpe. reduce-motion → `tween(0)`. | [APLICA YA] | Bajo/Bajo |
| Punto indicador "no leída" (línea 286-295, `Surface` de 8dp): aparece/desaparece de golpe. | Sin cambio — es un indicador de estado binario pequeño; animar su aparición/desaparición para un elemento de 8dp añade complejidad sin beneficio perceptible (moderación). | [APLICA YA — DECISIÓN DE DISEÑO] | — |

### 2.14 Chat del hogar

| Momento actual | Propuesta | Etiqueta | Impacto/Esfuerzo |
|---|---|---|---|
| `HouseholdChatSection.kt:132-142`: ya hace `listState.animateScrollToItem(messages.size - 1)` al llegar un mensaje nuevo — buen detalle ya presente. Los `items(messages, key={it.id})` (línea 141) no usan `Modifier.animateItem()`, así que el mensaje nuevo aparece sin transición de entrada propia (solo el scroll). | Añadir `Modifier.animateItem()` a `MessageBubble` (línea 141) — con `LazyColumn` de Compose Foundation moderno, esto anima automáticamente la aparición de items nuevos (fade+placement), sin código adicional de diseño. | [APLICA YA] | Bajo/Bajo |

### 2.15 Ajustes (`SettingsSheet.kt`)

Ya usa `Switch` estándar de Material3 (animación de deslizamiento del thumb incluida de fábrica) para notificaciones, sincronización de Calendar, sonido y vibración. Sin cambios — es la superficie correcta para no añadir movimiento extra (una hoja de ajustes con animaciones decorativas sería "sobrecargado" en el peor sitio posible).

**Hallazgo colateral (no motion, documentado para no perderlo)**: `settingsStore.isSoundEnabled()` se lee y se persiste desde el interruptor (`SettingsSheet.kt:464`) pero **no se usa en ningún otro sitio de la app** — no existe ningún efecto de sonido implementado. El interruptor es hoy un control sin efecto real. Ver veto §6.1.

---

## 3. Celebración de completar tarea (y momentos de éxito afines)

**Patrón ya validado (no rediseñar, reutilizar)**: `TaskListScreen.AnimatedCheckmark` + `TaskListScreen.ConfettiOverlay`, extraídos a `ui/components/CelebrationEffects.kt` como primer paso del blueprint (fase 00). Specs exactas ya en producción:
- Check: `Surface` con emoji ✅, `graphicsLayer { scaleX=scale; scaleY=scale }`, `scale` animado de 0→1 con `spring(dampingRatio=Spring.DampingRatioMediumBouncy, stiffness=Spring.StiffnessLow)` (o `tween(0)` si reduce-motion).
- Confeti: `Canvas` con 12 partículas (color de las 4 tomadas de `colorScheme.primary/tertiary/primaryContainer/tertiaryContainer`), caída lineal en 1000ms (`LinearEasing`), `startX`/`colorIndex`/`fallDelay`/`horizontalDrift`/`rotationSpeed` aleatorios por partícula (`Random`, sin semilla fija — variedad en cada compleción).
- Salida de card: `scaleX/Y 1→0.92`, `alpha 1→0`, `tween(260)`; la acción real (`onComplete()`) se retrasa esos mismos 260ms tras iniciar la animación.

**Extensión a otros momentos de éxito** (todos con el MISMO lenguaje visual, no efectos nuevos por sitio — esto es lo que mantiene la app "no sobrecargada" pese a tener más celebraciones):

| Momento | Check bounce | Confeti | Retraso de navegación | Notas |
|---|---|---|---|---|
| Completar tarea desde lista (`TaskListScreen`) | Ya existe | Ya existe (12 partículas) | No navega (se queda en la lista) | Referencia |
| Completar tarea desde detalle (`TaskDetailScreen`, botón nivel-tarea) | Nuevo (reutilizado) | Nuevo (12 partículas) | Sí — retrasar `pop()` 260ms | §2.5 |
| Completar asignación desde detalle (`AssignmentCard`) | Nuevo (reutilizado) | Nuevo (12 partículas) | No navega | §2.5 |
| Canjear recompensa (`MemberRewardScreen`) | Nuevo — pulso de escala del icono en vez de check (no hay checkmark conceptual en "comprar algo") | Nuevo (8 partículas, área reducida) | Sí — retrasar `pop()` 550ms | §2.6 |
| Logro desbloqueado | No (toast propio, ver abajo) | No (evita apilar con el confeti de completar tarea) | N/A (overlay no bloqueante) | §2.8 |
| Hogar creado/unido (hero card) | No | No (moderación — ver §6 razón) | N/A | §2.2 |

**Toast de logro desbloqueado** (único elemento verdaderamente nuevo, no reutiliza check/confeti): `Card` flotante en la parte superior de la pantalla, `slideInVertically + fadeIn` con el mismo spring que el check (coherencia de "sensación" aunque la forma sea distinta), contenido = emoji del logro (`achievement.emoji`) + copy fijo + título del logro, autodescarte a los 2500ms con `slideOutVertically + fadeOut` en `tween(200)`. Ver specs completas en §2.8 y copy en §5.

**Copy ES/EN necesario** (nuevas claves `AppStrings`):

| Clave | ES | EN |
|---|---|---|
| `achievement_unlocked_prefix` | ¡Logro desbloqueado! | Achievement unlocked! |
| `achievement_first_task_title` | Primera tarea | First task |
| `achievement_first_task_desc` | Completaste tu primera tarea | You completed your first task |
| `achievement_streak_5_title` | 5 días seguidos | 5-day streak |
| `achievement_streak_5_desc` | Mantuviste una racha de 5 días | You kept a 5-day streak going |
| `achievement_100_points_title` | 100 puntos | 100 points |
| `achievement_100_points_desc` | Alcanzaste 100 puntos totales | You reached 100 total points |
| `achievement_10_tasks_title` | 10 tareas | 10 tasks |
| `achievement_10_tasks_desc` | Completaste 10 tareas | You completed 10 tasks |
| `achievement_early_bird_title` | Madrugador | Early bird |
| `achievement_early_bird_desc` | Completaste una tarea antes de las 8am | You completed a task before 8am |

---

## 4. Quick wins [APLICA YA] priorizados (sin decisión pendiente)

1. Rotación de chevron en `ExpandableSectionHeader.kt` (un archivo, 6+ sitios beneficiados).
2. `Modifier.animateItem()` en `RankingScreen` (RankingRow), `HouseholdChatSection` (MessageBubble), `RewardListScreen`/`RewardsBody` (grid items), `HomeScreen` (lista de hogares).
3. `AnimatedVisibility` en el selector de emoji de `CreateRewardScreen` y `EditProfileScreen` (hoy sin animar, a diferencia del resto de secciones plegables).
4. `animateColorAsState` en `NotificationCard` al marcar como leída.
5. `AnimatedCounter` (nuevo componente) aplicado en `HouseholdMemberList`, `RankingScreen`, `TaskDetailScreen` (saldo), `PublicProfileScreen`.
6. Corrección de i18n de los 5 logros (`Achievement.kt` → `AppStrings`) — necesaria como dependencia del toast de logro, pero es una corrección mecánica de cobertura EN, no una decisión de diseño.
7. Extracción de `AnimatedCheckmark`/`ConfettiOverlay` a `ui/components/CelebrationEffects.kt` (paso previo mecánico a las fases de celebración).

---

## 5. Blueprint de implementación por fases

### Fase 00 — motion-core (infraestructura común)
- **Archivo nuevo** `ui/components/CelebrationEffects.kt`: mover `AnimatedCheckmark` y `ConfettiOverlay` desde `TaskListScreen.kt` (líneas 1093-1171) tal cual, sin cambiar su firma ni comportamiento; actualizar `TaskListScreen.kt` para importarlos. Añadir parámetro `particleCount: Int = 12` a `ConfettiOverlay` (por defecto igual que hoy) para poder pedir 8 desde `MemberRewardScreen`.
- **Archivo nuevo** `ui/components/AnimatedCounter.kt`: composable descrito en §2.10.
- **Cambio mínimo** `ui/components/ExpandableSectionHeader.kt` líneas 79-84: sustituir el `if (expanded) Up else Down` por un único `Icon(imageVector = Icons.Default.KeyboardArrowDown, ...)` envuelto en `Modifier.graphicsLayer { rotationZ = angle }`, con `angle by animateFloatAsState(targetValue = if (expanded) 180f else 0f, animationSpec = tween(200, easing = FastOutSlowInEasing))` (o `tween(0)` si `shouldReduceMotion()`).
- **Cambio mínimo** `ui/models/Achievement.kt` + `ui/i18n/AppStrings.kt`: mover título/descripción de `ALL_ACHIEVEMENTS` a claves i18n (tabla en §3); `Achievement` pasa a construirse con `id`+`emoji` fijos y título/descripción resueltos en el call-site vía `s(key)` (en `StatsScreen.AchievementCard` y en el nuevo toast).

### Fase 01 — transiciones
- `CalendarScreen.kt`: envolver la cuadrícula de días en `Crossfade(targetState = visibleMonthKey, animationSpec = if (reduceMotion) tween(0) else tween(200))`.
- `NotificationListScreen.kt` línea ~271: `containerColor` vía `animateColorAsState`.
- `HouseholdChatSection.kt` línea 141: añadir `Modifier.animateItem()` a `MessageBubble`.
- `RankingScreen.kt` línea 125: añadir `Modifier.animateItem()` a `RankingRow`.
- `RewardListScreen.kt` línea 192: añadir `Modifier.animateItem()` a `RewardCard`.
- `HomeScreen.kt` líneas ~253-261: añadir `Modifier.animateItem()` a los items de hogares compartidos.
- `CreateRewardScreen.kt` líneas 155-204 y `EditProfileScreen.kt` líneas 194-235: envolver el grid de emoji en `AnimatedVisibility(fadeIn()+expandVertically() / fadeOut()+shrinkVertically())`.
- `SplashScreen.kt`/`AuthGateScreen.kt`/`WelcomeScreen.kt`: fade-in de bloque descrito en §2.1.

### Fase 02 — celebraciones
- `TaskDetailScreen.kt`: aplicar `AnimatedCheckmark`+`ConfettiOverlay` (de `CelebrationEffects.kt`) al botón "Hecho" (líneas 769-784) y a `AssignmentCard` (líneas 1412-1426), con el patrón `isCompleting` local igual que `TaskListScreen.TaskCard`. Retrasar `navigator.pop()` del `LaunchedEffect(actionState)` (líneas 139-146) 260ms tras `Success` (0ms si reduce-motion).
- `MemberRewardScreen.kt`: al recibir `RewardActionState.Success`, animar el icono de la recompensa (pulso de escala) + `ConfettiOverlay(particleCount=8)` sobre su área, retrasar `pop()` 550ms (0ms si reduce-motion).
- `TaskScreenModel.kt`: añadir `_newlyUnlockedAchievement: MutableStateFlow<Achievement?>` + getter público; publicar en `checkAndAwardAchievements` (líneas 1079-1110) el primer logro nuevo resuelto contra `AchievementChecker.ALL_ACHIEVEMENTS`.
- **Archivo nuevo** `ui/components/AchievementToast.kt`: composable `AchievementToast(achievement: Achievement?, onDismissed: () -> Unit)` con las specs de §2.8/§3 (slide+fade, 2500ms, auto-limpieza).
- `TaskListScreen.kt` y `TaskDetailScreen.kt`: observar `model.newlyUnlockedAchievement` y renderizar `AchievementToast` superpuesto.
- `HouseholdScreen.kt`: parámetro `justCreated: Boolean = false` en el `data class`; `CreateProfileScreen.kt`/`JoinHouseholdScreen.kt` lo pasan a `true` en su `replaceAll`; entrada con rebote del hero card (líneas 470-539) descrita en §2.2.
- `JoinHouseholdScreen.kt`: `AnimatedVisibility` sobre la card "Te uniste a..." (líneas 199-217).

### Fase 03 — micro-interacciones y logros
- `StatsScreen.AchievementCard` (líneas 505-542): entrada única al pasar bloqueado→desbloqueado (`remember` guard descrito en §2.8).
- `RankingScreen.kt`: entrada con rebote de la medalla 🥇 en posición #1 únicamente (§2.9).
- Aplicar `AnimatedCounter` en los 4 call-sites listados en §2.10.

### Fase 04 — gráficas
- `StatsScreen.kt`: `rememberChartEntranceProgress()` compartido; aplicar a `BarChartCard`, `PointsChartCard`, `PieChartCard` según specs de §2.8.

---

## 6. Vetos genuinos [REQUIERE DECISIÓN]

1. **Efectos de sonido.** `SettingsStore.isSoundEnabled()` ya existe como ajuste persistido y visible en `SettingsSheet.kt:462-497`, pero hoy no dispara ningún sonido en ningún punto de la app — es un control sin efecto real. ¿Quiere Liberto que un encargo posterior añada sonidos reales (completar tarea, logro, canjear recompensa)? Requiere seleccionar/licenciar assets de audio nuevos (fuera del alcance de "sin nuevas dependencias/assets" de este encargo) y decidir volumen/mezcla con el sistema. No se propone ninguna implementación aquí.
2. **Animación continua/ambiental para rachas largas.** Se consideró (y se descarta por defecto en este informe, ver §2.8 `StreakCard`) un pulso continuo del emoji 🔥 para rachas muy altas (p.ej. ≥30 días) como refuerzo de fidelidad. El dueño pidió explícitamente "nada de animaciones continuas" como regla general — este es el único caso donde podría justificarse una excepción (una racha de 30 días es en sí un logro raro), pero implica: (a) riesgo real de batería si no se limita agresivamente el framerate/duración del bucle, y (b) riesgo de resultar molesto para quien vea esa pantalla a diario. ¿Se quiere esta excepción puntual, o se mantiene la regla general sin excepciones (recomendación por defecto de este informe)?
3. **Detección real de "reducir movimiento" en JVM/desktop.** `ShouldReduceMotion.jvm.kt:7` devuelve `false` de forma fija (ya señalado en un panel de revisión previo, `docs/review-panel-expertos-2026-09-04.md:183`, y sigue sin resolver). Investigar una señal real de accesibilidad en JVM/Swing/AWT (si existe alguna fiable multiplataforma) es trabajo de infraestructura con alcance incierto en un target secundario de la app (desktop). ¿Vale la pena el esfuerzo de investigación, o se acepta la limitación conocida dado que JVM es el target menos usado?
4. **Ilustración/asset dedicado para el toast de "logro desbloqueado".** Este informe resuelve el toast reutilizando el emoji ya asociado a cada logro (`Achievement.emoji`, ya existente) — sin ilustración nueva. Si en el futuro se quisiera un tratamiento visual más rico (p.ej. una insignia ilustrada por logro en vez de un emoji), eso sí requeriría diseñar assets nuevos, fuera del alcance de "sin nuevos assets externos" de este encargo.

---

## Resumen final

- Informe: `docs/revision-delight-experiencia-2026-09-18.md`
- `git log --oneline -1` (antes de este commit): `71c7bce docs: completar hash de commit en informe de iOS Google Sign-In`
