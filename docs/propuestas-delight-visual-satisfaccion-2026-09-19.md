# Propuestas de delight visual y satisfacción de uso — 2026-09-19

Encargo del dueño (Liberto): revisión con "ojos frescos" de Task Hub para proponer mejoras que la hagan más **vistosa** y **satisfactoria** de usar. Solo informe — **no se ha implementado nada de código**.

**Punto de partida distinto al de la revisión anterior**: `docs/revision-delight-experiencia-2026-09-18.md` (y sus 5 fases de implementación, `delight-fase0` a `delight-fase4`, ya en `main`) agotaron el terreno de **motion/háptica/celebraciones** — transiciones de pantalla, check+confeti al completar tarea/canjear recompensa, toast de logro desbloqueado, contador de puntos animado, entrada de gráficas, chevrones rotatorios, etc. Todo eso ya existe y funciona; **no se repite aquí**. Este informe mira un ángulo distinto y complementario: **diseño visual estático y arquitectura de satisfacción** — qué se ve, qué información falta al primer vistazo, y qué recompensas visuales permanentes (no solo momentáneas) puede tener la app — dejando el terreno de animación/háptica intacto salvo donde una propuesta lo mencione explícitamente por integración con el sistema de gating existente.

Archivos leídos para esta revisión: `docs/revision-delight-experiencia-2026-09-18.md` + sus 5 docs de fase (`delight-fase0`…`fase4`), `docs/INDICE.md`, `ui/theme/Theme.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/RankingScreen.kt`, `ui/screens/StatsScreen.kt` (cabecera + imports), `ui/screens/RewardListScreen.kt` (completo), `ui/screens/ProfileScreen.kt` (cabecera), `ui/screens/TaskListScreen.kt` (`TaskCard`, `GroupHeader`), `ui/screens/CalendarScreen.kt` (grep dirigido de celdas de día), `ui/screens/NotificationListScreen.kt` (grep dirigido de estado vacío), `ui/components/HouseholdTaskSection.kt` (cabecera), `ui/components/EmptyStateIllustrations.kt`, `ui/components/PointsBadge.kt`, `ui/components/UserAvatar.kt`, `ui/components/AppLogo.kt`, `ui/components/StatusDot.kt`, `ui/components/TaskHubTopBar.kt`, `ui/models/Achievement.kt`.

**Nota sobre el gating de Modo simple** (`ui/components/EffectsGating.kt`, fase 0 del informe anterior): el interruptor maestro "Modo simple" + sus 3 categorías (`fx_animations`, `fx_effects`, `fx_haptics`) gatean *efectos disparados por una acción* (animaciones, confeti, vibración). La mayoría de propuestas de este informe son **diseño visual estático** (algo que se ve igual en reposo, no una animación) — esas **no encajan en ninguna categoría existente y no necesitan gating nuevo**, igual que hoy no se gatea el color de un `Card` o de un tema. Se indica explícitamente en cada propuesta si es estática (sin gating) o si añade algún efecto que sí debería integrarse en una categoría existente.

---

## 1. Resumen ejecutivo — 5 propuestas de mayor impacto

1. **`AppLogo` no seguía el tema activo** — inconsistencia visual real entre los 3 temas del selector de Ajustes (Naturaleza/Minimal muestran el isotipo en teal/coral igualmente). Impacto medio, esfuerzo bajo, corrección casi mecánica.
2. **Indicador de asequibilidad en la rejilla de recompensas** — hoy hay que pulsar "Canjear" para descubrir si no llegan los puntos; un vistazo a la rejilla no lo dice. Impacto alto (frustración evitable), esfuerzo bajo-medio.
3. **Progreso numérico hacia los logros bloqueados** — hoy solo hay candado/desbloqueado binario; no se sabe si a un logro le faltan 2 tareas o 8. Es el hueco de gamificación más grande de la app. Impacto alto, esfuerzo medio.
4. **Indicador de cambio de posición en el Ranking** (↑/↓ desde la última vez que se vio) — el ranking se mira, se compara mentalmente y se olvida; sin memoria de la posición anterior no hay sensación de progreso/competencia. Impacto alto, esfuerzo medio-alto.
5. **`TopAppBar` sin ninguna señal de profundidad al hacer scroll** — el contenido pasa por debajo de la barra sin ninguna sombra/cambio de color que la distinga, en casi todas las pantallas de la app. Impacto medio, esfuerzo bajo.

---

## 2. Lista de propuestas

### 2.1 Identidad visual y componentes base

**1. `AppLogo` con colores fijos en vez de seguir el tema activo**
- El isotipo (`ui/components/AppLogo.kt:36-38`) usa `Teal500`/`Teal800`/`Coral500` como *default* de sus parámetros de color, y todos los call-sites actuales (`SplashScreen`, `HomeScreen`, `AuthGateScreen`, `WelcomeScreen`) lo invocan sin pasar color — así que el logo se ve siempre teal/coral aunque el usuario tenga activo el tema Naturaleza (verde/marrón) o Minimal (blanco/negro/grises) en Ajustes. `EmptyStateIllustrations.kt` ya resolvió exactamente este mismo problema para sus 2 ilustraciones (comentario explícito: "Colores del tema, no literales Teal*/Coral*, para que se adapte a los 3 themes") — `AppLogo` quedó fuera de ese barrido.
- Cambiar los defaults de `ringColor`/`checkColor`/`dotColor` a `MaterialTheme.colorScheme.primary`/`primaryContainer`(o variante oscura)/`tertiary` en vez de los literales `Teal500`/`Teal800`/`Coral500`.
- Esfuerzo: **bajo**. Marcador: **[APLICA YA]**.

**2. `TaskHubTopBar`/`TopAppBar` sin señal de profundidad al hacer scroll**
- `TaskHubTopBar.kt` (usado en casi toda la app) y el `TopAppBar` propio de `HomeScreen.kt:103-124` fijan `containerColor = MaterialTheme.colorScheme.surface` sin `scrollBehavior` — cuando el contenido de debajo hace scroll, no hay ninguna sombra, línea divisoria ni cambio de tono que separe visualmente la barra del contenido. Es un patrón estándar de Material3 (`TopAppBarDefaults.pinnedScrollBehavior()` + `scrolledContainerColor`) que hoy no se usa en ningún sitio de la app.
- Añadir `scrollBehavior` a `TaskHubTopBar` (parámetro opcional, `null` por defecto para no romper los call-sites que no pasan `LazyListState`/`nestedScroll`) y un `scrolledContainerColor` ligeramente distinto (`surfaceContainer` o `surface` con `tonalElevation`) para dar sensación de que el contenido "pasa por debajo" en vez de "desaparece bajo" la barra.
- Es un cambio estático de color/sombra ligado al scroll (no un "efecto" en el sentido de Modo simple — no se apaga con `fx_effects`, es una convención estándar de Material3 igual que la sombra de un `Card`).
- Esfuerzo: **bajo-medio** (toca un componente compartido usado en ~15 pantallas, pero el cambio es aditivo y opcional). Marcador: **[APLICA YA]**.

**3. `UserAvatar` sin anillo/borde para roles o estados destacados**
- Hoy el avatar (`ui/components/UserAvatar.kt`) es siempre un círculo plano con fondo `primaryContainer`. No hay ninguna señal visual permanente (borde de color) para: el admin del hogar, el miembro con la racha activa más larga, o el líder actual del ranking — toda esa información solo vive en texto al lado del avatar.
- Añadir un parámetro opcional `ringColor: Color? = null` que dibuje un `Modifier.border(2.dp, ringColor, CircleShape)` alrededor del avatar; aplicarlo desde `RankingRow` (posición #1, con `colorScheme.tertiary`) y opcionalmente desde `HouseholdMemberList` para el admin. Es un refuerzo *permanente* (no un efecto puntual como el rebote de medalla ya implementado) — visible cada vez que se mira la pantalla, no solo en el momento de entrada.
- Esfuerzo: **bajo**. Marcador: **[REQUIERE DECISIÓN]** — introduce una nueva convención visual (a quién se le pone anillo y de qué color) que conviene que decida el dueño antes de generalizarla a más sitios.

### 2.2 Recompensas y economía de puntos

**4. Indicador de asequibilidad en la rejilla de recompensas**
- `RewardCard` (`ui/screens/RewardListScreen.kt:265-357`) muestra el coste (`PointsBadge("⭐ ${reward.cost}")`) y un botón "Canjear" con el mismo aspecto activo para **todas** las recompensas, tenga el miembro puntos suficientes o no — hoy no hay ningún grep de `cost >`/`Afford`/`totalPoints` en este archivo. El miembro solo descubre que no le llegan los puntos al pulsar (`MemberRewardScreen` lo rechazará ahí). Para un usuario infantil especialmente, esto es una fuente de frustración evitable: mirar la rejilla debería decir de un vistazo qué se puede permitir hoy.
- `RewardsBody` ya carga `memberState` (con `totalPoints` del miembro actual) en el mismo composable que renderiza la rejilla — pasar ese valor a `RewardCard` es un hilo de datos que ya existe, no uno nuevo. Tratamiento visual sugerido: si `reward.cost > memberPoints`, atenuar la tarjeta (`alpha ≈ 0.6`) y cambiar el botón a un estilo `OutlinedButton` con texto "Te faltan N⭐" en vez de "Canjear" (sigue siendo pulsable — no bloquea, solo informa; el rechazo real de servidor se mantiene como red de seguridad).
- Esfuerzo: **bajo-medio**. Marcador: **[REQUIERE DECISIÓN]** — cambia lo que se comunica en pantalla (revela explícitamente "no te llega"), decisión de producto más que mecánica.

**5. Estados vacíos de Recompensas/Notificaciones/Ranking sin ilustración propia**
- `EmptyTasksIllustration`/`EmptyHouseholdsIllustration` (`ui/components/EmptyStateIllustrations.kt`) son ilustraciones geométricas dibujadas a mano con `Canvas`, ya con buen acabado y adaptadas a los 3 temas. Pero `RewardListScreen.kt:167` (emoji 🎁/similar suelto), `NotificationListScreen.kt:131` y `RankingScreen.kt:109` (`Text("🏆", displayMedium)`) usan solo un emoji grande suelto para su estado vacío — un salto de calidad visual notable frente a las dos pantallas que sí tienen ilustración.
- Añadir `EmptyRewardsIllustration`/`EmptyRankingIllustration`/`EmptyNotificationsIllustration` al mismo archivo, mismo estilo (`Canvas`, colores del tema, ~120dp) — mismo patrón mecánico que las 2 ya existentes, sin assets externos.
- Esfuerzo: **medio** (3 ilustraciones nuevas, cada una es un dibujo geométrico distinto). Marcador: **[APLICA YA]** (mecánico, sigue un patrón ya validado en el código).

### 2.3 Gamificación (logros, racha, ranking)

**6. Progreso numérico hacia los logros bloqueados, no solo candado**
- `ui/models/Achievement.kt:28-32`: `Achievement` solo tiene `isUnlocked: Boolean` — no existe ningún campo ni cálculo de "cuánto falta". `StatsScreen.AchievementCard` (ya con animación de entrada al desbloquear, fase 3) muestra el logro bloqueado con el mismo aspecto sea cual sea el progreso real del miembro (0 tareas completadas o 9 de 10 se ven exactamente igual, ambos "candado"). De los 5 logros del catálogo (`first_task`, `streak_5`, `100_points`, `10_tasks`, `early_bird`), 3 tienen un umbral numérico claro (`streak_5`, `100_points`, `10_tasks`) que sí se puede expresar como progreso fraccionario; `first_task` y `early_bird` son binarios por naturaleza (no tienen "a medias" con sentido) y quedarían igual que hoy.
- Añadir una función pura en `AchievementChecker` (p.ej. `progressFor(id, totalTasksCompleted, totalPoints, currentStreak): Float?`, `null` para los 2 logros binarios) y en `AchievementCard` pintar una `LinearProgressIndicator` fina bajo el título cuando el logro está bloqueado y tiene progreso fraccionario disponible.
- Esfuerzo: **medio** (nueva función pura + testeable igual que el resto de `AchievementChecker`, más el cambio visual en `AchievementCard`). Marcador: **[REQUIERE DECISIÓN]** — expone información nueva (progreso exacto) que hoy no se comunica; decisión de diseño de gamificación, no solo mecánica.

**7. Indicador de cambio de posición en el Ranking (↑/↓)**
- `RankingRow` (`ui/screens/RankingScreen.kt:148-298`) muestra la posición actual (medalla o número) pero no hay memoria de la posición anterior — cada vez que se abre la pestaña Ranking es una foto fija, sin ninguna sensación de "subiste" o "te alcanzaron". Es precisamente el tipo de refuerzo que hace un ranking "enganchar": ver una flechita verde subiendo es más satisfactorio que ver solo el número absoluto.
- Requiere una pieza de estado nueva (no solo visual): persistir la última posición vista por miembro (p.ej. en `SettingsStore`/`TaskCache`, clave `last_seen_rank_<householdId>_<memberId>`) y comparar contra la posición actual al cargar `RankingBody`. Pintar una flecha pequeña (▲/▼, `Icons.AutoMirrored`... revisar si hay icono direccional en `material-icons-core`, si no, usar el propio emoji "▲"/"▼" como ya se hace con medallas) junto al número de posición, verde/rojo (colores semánticos ya existentes `success`/`error`).
- Esfuerzo: **medio-alto** (nuevo estado persistido + lógica de comparación, no solo pintar algo). Marcador: **[REQUIERE DECISIÓN]** — cambia el modelo de datos local y puede generar fricción social en un hogar (ver competencia entre hermanos como algo bueno o incómodo es una decisión de producto del dueño).

**8. Escalado visual estático del emoji de racha 🔥 según magnitud**
- Hoy `RankingRow` y `StatsScreen.StreakCard` muestran `"🔥 $currentStreak"` con el mismo tamaño de emoji sea la racha de 1 día o de 60. El informe de delight anterior (§6.2) descartó explícitamente una animación *continua* de pulso para rachas altas (por petición del dueño de "nada de animaciones continuas"), pero no consideró un **cambio estático de tamaño/tono** (sin animación, sin bucle): p.ej. `fontSize` del emoji sube un escalón en 3 tiers fijos (racha 1-6 → tamaño normal; 7-29 → +15%; 30+ → +30%, mismo criterio de "hito raro" que ya usó el informe anterior para justificar tratamiento especial en rachas de 30+).
- Es un `if/else` sobre `currentStreak`, sin `Animatable` ni `LaunchedEffect` — no es un "efecto" a efectos de Modo simple, es una regla de estilo estática como el color de fondo de `GroupHeader` según urgencia.
- Esfuerzo: **bajo**. Marcador: **[APLICA YA]**.

### 2.4 Pantalla principal (Home)

**9. `HomeScreen` sin saludo ni resumen visual del día — solo una línea de texto**
- `HomeScreen.kt:220-229`: el único resumen que se muestra antes de la lista de hogares es un `Text` de una línea (`home_pending_count_summary`, "%1 pendientes de %2 hogares") en `bodySmall` gris — funcional pero anodino para ser lo primero que ve el usuario cada vez que abre la app. No hay saludo personalizado, ni una vista rápida de puntos/racha del propio usuario (esa información solo vive en Estadísticas/Perfil, requiere navegar).
- Sustituir esa línea por una cabecera breve: saludo (`"Hola, {nombre}"`, ya se tiene el nombre del miembro vía `ProfileScreen`/sesión) + una fila compacta de 2-3 `StatChip` (puntos totales del usuario en su hogar principal, racha actual) — reutilizando `StatChip` ya existente (`ui/components/PointsBadge.kt:87-134`), sin componente nuevo. Mantiene la moderación (no es una tarjeta grande ni animada, es una fila de texto+chips que ya existen en otros sitios).
- Esfuerzo: **medio-alto** (requiere traer datos de puntos/racha del usuario a `HomeScreenModel`, que hoy solo carga hogares/tareas, no estadísticas de miembro). Marcador: **[REQUIERE DECISIÓN]** — cambia qué carga `HomeScreenModel` al abrir la app (posible impacto en tiempo de carga/llamadas de red) y es una decisión de qué mostrar en la pantalla más vista de la app.

### 2.5 Lenguaje visual (color, textura)

**10. `PointsBadge`/tarjetas hero siempre planas — sin ninguna textura/degradado sutil**
- Todo el sistema de color de la app (`Theme.kt`, `PointsBadge.badgeToneColors`) usa colores sólidos de Material3 — ningún `Brush.linearGradient`/`radialGradient` en ningún sitio (`grep -r "Brush\." ui/` sin resultados). Esto es coherente y accesible (los pares container/onContainer están auditados WCOG), pero da a toda la app un acabado uniformemente "plano" — un badge de puntos ganados, el hero card de "hogar creado" o la tarjeta de racha alta podrían tener un degradado sutil de 2 tonos del propio `colorScheme` (p.ej. `primary` → `primaryContainer`) sin salir de la paleta ni añadir dependencias, dando una sensación ligeramente más "premium"/festiva en los 2-3 sitios de mayor peso emocional, sin tocar el resto de la UI (que se beneficia de mantenerse plana y legible).
- Esfuerzo: **bajo-medio** (Compose `Brush` es nativo, sin librería). Marcador: **[REQUIERE DECISIÓN]** — es un cambio de lenguaje visual (introduce un elemento nuevo, degradados, que no existe hoy en ningún sitio) que conviene decidir de forma centralizada antes de aplicarlo, para no acabar con 3 estilos de degradado distintos por pantalla.

---

## 3. Tabla resumen

| # | Propuesta | Pantalla/archivo | Esfuerzo | Marcador |
|---|---|---|---|---|
| 1 | `AppLogo` sigue el tema activo | `AppLogo.kt` | Bajo | [APLICA YA] |
| 2 | `TopAppBar` con señal de profundidad al hacer scroll | `TaskHubTopBar.kt`, `HomeScreen.kt` | Bajo-medio | [APLICA YA] |
| 3 | Anillo de color en `UserAvatar` para roles/estados destacados | `UserAvatar.kt`, `RankingScreen.kt` | Bajo | [REQUIERE DECISIÓN] |
| 4 | Indicador de asequibilidad en rejilla de recompensas | `RewardListScreen.kt` | Bajo-medio | [REQUIERE DECISIÓN] |
| 5 | Ilustraciones propias para estados vacíos de Recompensas/Notificaciones/Ranking | `EmptyStateIllustrations.kt` + 3 pantallas | Medio | [APLICA YA] |
| 6 | Progreso numérico hacia logros bloqueados | `Achievement.kt`, `StatsScreen.kt` | Medio | [REQUIERE DECISIÓN] |
| 7 | Indicador ↑/↓ de cambio de posición en Ranking | `RankingScreen.kt` + persistencia nueva | Medio-alto | [REQUIERE DECISIÓN] |
| 8 | Escalado estático del emoji 🔥 de racha por tiers | `RankingScreen.kt`, `StatsScreen.kt` | Bajo | [APLICA YA] |
| 9 | Saludo + resumen visual (puntos/racha) en `HomeScreen` | `HomeScreen.kt`, `HomeScreenModel` | Medio-alto | [REQUIERE DECISIÓN] |
| 10 | Degradado sutil en badges/tarjetas hero de mayor peso emocional | `PointsBadge.kt` + 2-3 call-sites | Bajo-medio | [REQUIERE DECISIÓN] |

---

## Resumen final

- Informe: `docs/propuestas-delight-visual-satisfaccion-2026-09-19.md`
- `git log --oneline -1` (antes de este commit): `20cadfb chore: bump versión 0.7.39`
