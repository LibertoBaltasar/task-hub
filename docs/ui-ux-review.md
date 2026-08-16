# Revisión UI/UX — Task Hub · Informe completo

# Revisión UI/UX — Task Hub · Resumen Ejecutivo

**Alcance:** 1 `Theme.kt`, 20 pantallas y 3 componentes de `org.taskhub.ui` (Compose Multiplatform + Material 3, Voyager, Koin). Contrastes WCAG verificados por script (fórmula de luminancia sRGB). La lista de colores del enunciado es **correcta** y los ratios previos se **confirman**, con dos matices (ver abajo).

---

## Diagnóstico general

Task Hub es una app **funcional y coherente en su lógica**, pero con un problema transversal de **accesibilidad de contraste**: la marca usa teal/coral en fondos saturados con texto blanco, y esos pares fallan AA en las acciones y la navegación más importantes. A esto se suma una **inconsistencia estructural** (dos tipos de top bar), un **formulario de creación sin validación** y un uso generalizado de **emoji como sustituto de iconos y de texto semántico**.

**Matices sobre los ratios previos:**
- Blanco/Teal500 = 3.05 y blanco/Coral500 = 3.07 **no fallan** el umbral large (≥3.0), pero se usan en botones con texto normal, así que el fallo AA se mantiene y el margen es despreciable.
- Hallazgo nuevo no listado: "HUB" del splash = Coral500 sobre Teal800 = **1.82:1** (falla incluso large).

---

## Top 10 cambios por impacto/esfuerzo

| # | Cambio | Impacto | Esfuerzo | Relación |
|---|--------|---------|----------|----------|
| 1 | Unificar top bars en un `TaskHubTopBar` basado en `TopAppBar` (fondo `surface`, `ArrowBack`) | Alto (14 pantallas, resuelve C1+I1+I3-back+M3) | Bajo-Medio | ★★★★★ |
| 2 | Cambiar CTAs a `Teal800`/`Coral700` con blanco (o texto oscuro sobre `Teal100`/`Coral100`) | Alto (todos los botones primarios) | Bajo (search-replace de `containerColor`) | ★★★★★ |
| 3 | Corregir splash "HUB" (1.82:1) → `Coral100` sobre `Teal800` | Medio (primera impresión) | Muy bajo (1 línea) | ★★★★★ |
| 4 | Crear `PointsBadge` reutilizable (fondo `Coral700`+blanco o `Coral50`+`Coral800`) | Medio (información gamificada) | Bajo | ★★★★★ |
| 5 | Validar "Nueva tarea": deshabilitar Crear sin título + `isError`/`supportingText` + `DatePicker` | Alto (flujo principal) | Medio | ★★★★☆ |
| 6 | Arreglar tema Naturaleza (`primary=Green800`, `onPrimary=blanco` 5.74:1) | Medio (tema completo) | Muy bajo (Theme.kt) | ★★★★★ |
| 7 | Sustituir emoji por `Icon` con `contentDescription` en acciones (⚙️🔔🗑️✏️📤) y roles (👑/🧒) | Medio (accesibilidad) | Medio (muchos puntos) | ★★★★☆ |
| 8 | Garantizar touch target ≥48 dp (mini-FABs 40 dp, delete 36 dp, emoji 40 dp) | Medio | Bajo (`minimumInteractiveComponentSize`) | ★★★★☆ |
| 9 | Definir `Typography` propia y eliminar `fontSize`/`sp` literales (8–72 sp) | Medio (jerarquía/legibilidad) | Medio | ★★★☆☆ |
| 10 | FAB: iconos diferenciados (`Add`/`GroupAdd`) + estado `Close` al abrir menú | Bajo-Medio | Muy bajo | ★★★★☆ |

---

## Vistazo por prioridad

- **CRÍTICOS (6):** C1 top bars Teal600 3.61:1 · C2 CTAs Teal500/Coral500 3.05–3.07:1 · C3 splash "HUB" 1.82:1 · C4 badges Coral500 3.07:1 · C5 tema Naturaleza Green700 4.12:1 · C6 formulario sin validación. → `ui-ux-review-2-critico.md`
- **IMPORTANTES (6):** I1 top bars inconsistentes · I2 emoji como único diferenciador · I3 touch targets <48 dp · I4 contentDescription ausente · I5 FAB ambiguo · I6 jerarquía tipográfica. → `ui-ux-review-3-importante.md`
- **MENORES (10):** M1 texto de color insuficiente · M2 densidad de tarjetas · M3 "← Volver" textual · M4 emoji en títulos · M5 splash 5 s · M6 "Reintentar" vacío · M7 gráficas 8–10 sp · M8 Spacer 72 dp · M9 LaunchedEffect duplicado · M10 texto DEBUG. → `ui-ux-review-4-menor.md`

**Métrica global:** de ~25 pares de color auditados, ~9 fallan AA para texto normal en los flujos principales. El fix es mayoritariamente de tokens de color (bajo esfuerzo) y de una decisión de consistencia (top bar única).


---

# Revisión UI/UX — Task Hub · Hallazgos CRÍTICOS

> Convención: cada hallazgo cita `archivo:línea`. Contraste verificado con la fórmula WCAG 2.x (luminancia relativa sRGB). Umbrales: AA texto normal ≥ 4.5:1, AA texto grande (≥18pt/24px) ≥ 3.0:1.

---

## C1. Blanco sobre Teal600 en TODAS las top bars manuales — 3.61:1

**Problema →** Catorce pantallas construyen su barra superior con `Surface(color = Teal600)` y texto `onPrimary` (blanco). Verificado: blanco/`#009884` = **3.61:1**. El título usa `titleLarge` (pasa AA-large), pero los botones de navegación "← Volver" / "← Cancelar" / "← Tareas" / "← Inicio" son `TextButton` con `labelMedium`/`bodyMedium`, es decir **texto normal que falla AA**.

**Por qué importa →** Es el patrón de navegación principal de la app; aparece en el 100 % de las pantallas secundarias. Un usuario con baja visión no puede leer la acción de volver ni el título de forma fiable. Es además un fallo sistemático (no puntual).

**Fix concreto →** Sustituir el patrón manual por `TopAppBar` de Material 3 con `TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = onSurface)` (ya se hace bien en `HomeScreen.kt:159` y `ProfileScreen.kt:78`). Si se mantiene el fondo teal, usar `Teal800` (`#007660`, **5.58:1** con blanco) como fondo y conservar `onPrimary`. O bien texto oscuro (`Teal900`/`onSurface`) sobre `Teal100`. Nunca blanco sobre `Teal600`/`Teal500`.

**Archivos afectados:** `TaskListScreen.kt:146-206`, `TaskDetailScreen.kt:99-155`, `CreateTaskScreen.kt:93-169`, `EditTaskScreen.kt:92-173`, `HouseholdScreen.kt:280-368`, `PersonalSpaceScreen.kt:57-88`, `CalendarScreen.kt:206-287`, `StatsScreen.kt:87-115`, `RankingScreen.kt:54-82`, `RewardListScreen.kt:61-105`, `CreateRewardScreen.kt:63-97`, `MemberRewardScreen.kt:59-91`, `NotificationListScreen.kt:48-82`, `WelcomeScreen.kt:66-95`.

---

## C2. CTAs primarios con blanco sobre Teal500 / Coral500 / Teal600 — 3.05–3.61:1

**Problema →** Los botones de acción principal usan fondos saturados con texto blanco `titleMedium`/`labelMedium` (texto normal). Verificado: blanco/`#00A693` = **3.05:1**, blanco/`#FF5C3A` = **3.07:1**, blanco/`#009884` = **3.61:1**. Todos fallan AA para texto normal. (Nota: el enunciado los marcaba "falla incluso large"; 3.05 y 3.07 están técnicamente en el límite ≥3.0 para texto grande, pero al usarse en botones con texto normal, el fallo AA se mantiene, y el margen sobre el umbral large es despreciable.)

**Por qué importa →** "✅ Hecho", "Ver Tareas", "Ranking", "Recompensas", "Calendario", "Estadísticas", "Canjear", "Nueva tarea" y el FAB son las acciones que mueven todo el flujo. Si fallan el contraste, la app pierde usabilidad donde más importa.

**Fix concreto →** Unificar: botones rellenos con `containerColor = Teal700` (`#008772`, **4.46:1**… aún corto) o `Teal800` (**5.58:1**). Para Coral, usar `Coral700` (`#B33A22`, **5.92:1**) con texto blanco. Alternativa: texto `Coral900`/`Teal900` oscuro sobre `Coral100`/`Teal100`. Defínase una única regla en un `ButtonDefaults` compartido (p. ej. `containerColor = primary` del esquema) y aplíquese en todo el código.

**Archivos afectados:** `TaskCard.kt` "Hecho" `TaskListScreen.kt:716-733`, `TaskDetailScreen.kt:427-441` y `AssignmentCard` `TaskDetailScreen.kt:824-841`, `HouseholdScreen.kt:468-554`, `PersonalSpaceScreen.kt:135-179`, `MemberRewardScreen.kt:208-229,255-268`, FAB `HomeScreen.kt:204-208`.

---

## C3. Splash: "HUB" en Coral500 sobre Teal800 — 1.82:1

**Problema →** En `SplashScreen.kt:59-65`, la palabra "HUB" usa `Coral500` sobre fondo `Teal800`. Contraste verificado **1.82:1**: falla incluso el umbral de texto grande (3.0) por mucho.

**Por qué importa →** Es la primera pantalla que ve el usuario y el logotipo de la marca. Un contraste de 1.82 hace que "HUB" sea prácticamente invisible para personas con visión reducida y degrada la percepción de calidad desde el segundo cero.

**Fix concreto →** Usar `Coral50`/`Coral100` para "HUB" sobre `Teal800` (Coral100/Teal800 ≈ 7.6:1), o blanco para ambas palabras, o subir el fondo a `Teal900` con "HUB" en `Coral200`. Mantener el acento coral solo si pasa ≥3.0.

---

## C4. Badges de puntos y "!" con Coral500 + blanco — 3.07:1

**Problema →** Los chips de puntos, el badge de coste y el indicador de vencidas usan `containerColor = Coral500` con texto blanco (`onTertiary`/`onError`). Verificado **3.07:1** (falla AA normal). Se repite en: `TaskListScreen.kt:769-780` (badge "N pts"), `GroupHeader` "!" `TaskListScreen.kt:948-958`, `MemberCard` `HouseholdScreen.kt:744-756`, `RewardCard` `RewardListScreen.kt:254-265`, badge de no-leídos `HouseholdScreen.kt:330-342`.

**Por qué importa →** La información gamificada (puntos, urgencia, coste) es la esencia del producto; si no se lee, el incentivo desaparece. Además el "!" de vencidas es una señal de estado importante.

**Fix concreto →** Fondo `Coral700` + texto blanco (**5.92:1**), o fondo `Coral50`/`Coral100` + texto `Coral800`/`Coral700` (**5.34–7.02:1**), que ya se usa correctamente en las etiquetas de tags. Crear un composable `PointsBadge` reutilizable para no repetir la elección.

---

## C5. Tema "Naturaleza": blanco sobre Green700 = 4.12:1

**Problema →** `Theme.kt:158-160` define `primary = Green700` con `onPrimary = Color.White`. Verificado: blanco/`#388E3C` = **4.12:1**, por debajo de 4.5. Como el tema Naturaleza se selecciona desde Ajustes, todos los botones `primary` y top bars pasan a fallar AA.

**Por qué importa →** Un tema completo de la app queda inaccesible de fábrica; los usuarios que lo elijan reciben peor contraste sin saberlo.

**Fix concreto →** Cambiar `onPrimary` a `Green50`/`Green100` con texto oscuro, o subir el `primary` a `Green800` (`#2E7D32`, **5.74:1**). Verificar también `Brown500`/blanco (6.55:1, OK) para que no se rompa en oscuro.

---

## C6. Formulario "Nueva tarea" sin validación — crea tareas con "Sin título"

**Problema →** `CreateTaskScreen.kt:138` hace `title.ifBlank { "Sin título" }` y habilita "Crear" siempre (no hay `enabled = title.isNotBlank()`). El campo puntos acepta cualquier texto y silenciosamente usa 10 (`:128`), la fecha se escribe a mano como texto "YYYY-MM-DD" sin `DatePicker` (`:626-640`), y no hay mensajes de error inline en los campos.

**Por qué importa →** Es el formulario más complejo de la app y, aun así, permite guardar datos inválidos/incompletos sin avisar. El usuario descubre el error tarde (o nunca, con tareas "Sin título" acumuladas). Compárese con `CreateRewardScreen.kt:304-321`, que SÍ valida (`isValid = title.isNotBlank() && cost > 0`) — inconsistencia de criterio.

**Fix concreto →** Deshabilitar "Crear" hasta que el título no esté en blanco; usar `isError` + `supportingText` en `OutlinedTextField` para título y puntos; sustituir los campos de fecha/hora por `DatePicker`/`TimePicker` de Material 3. Replicar en `EditTaskScreen.kt:145` (mismo `ifBlank`).


---

# Revisión UI/UX — Task Hub · Hallazgos IMPORTANTE

---

## I1. Top bars manuales Teal600 vs `TopAppBar` de Material — inconsistencia estructural

**Problema →** Solo `HomeScreen.kt:159` y `ProfileScreen.kt:78` usan `TopAppBar` (fondo `surface`, iconos Material). Las otras 14 pantallas repiten a mano un `Surface(color = Teal600)` + `Row` con `TextButton("← …")`, `Spacer(weight(1f))`, título y `Spacer(weight(1f))` (ver C1). El resultado son dos estilos de cabecera distintos conviviendo: fondo claro con flecha `ArrowBack` en Home/Perfil, fondo teal con "← Volver" textual en el resto.

**Por qué importa →** Rompe el modelo mental: la misma acción (volver) se ve y se comporta distinto según la pantalla. Además duplica ~40 líneas de layout por pantalla y centraliza el defecto de contraste de C1.

**Fix concreto →** Extraer un único `TaskHubTopBar(title, onBack, actions)` construido sobre `TopAppBar`/`CenterAlignedTopAppBar` y usarlo en todas partes. Esto resuelve a la vez C1 e I1. Mantener un solo estilo (recomendado: `surface` con `onSurface` y `navigationIcon = Icon(Icons.AutoMirrored.Filled.ArrowBack)`).

---

## I2. Emoji como único diferenciador de rol y estado

**Problema →** El rol de miembro se distingue solo por emoji: "👑" admin vs "🧒" niño, en `MemberCard` (`HouseholdScreen.kt:723`), `RankingRow` (`RankingScreen.kt:189`), listas de asignación (`CreateTaskScreen.kt:546`, `EditTaskScreen.kt:455`), y avatares (`TaskDetailScreen.kt:767`). El estado de tarea también depende de emoji ("✅ Hecho", "⏳ Pendiente", "📌", "⚠️").

**Por qué importa →** El emoji no comunica a lectores de pantalla (se lee "corona"/"cara de niño", no "admin"/"niño"), no tiene `contentDescription`, y la diferencia 👑/🧒 puede ser sutil para personas con baja visión o daltonismo. La información semántica queda atrapada en un pictograma.

**Fix concreto →** Usar iconos Material (`Icons.Default.AdminPanelSettings` / `Icons.Default.ChildCare`) con `contentDescription`, o texto explícito ("Admin"/"Niño/a") junto al emoji. Para estados, acompañar siempre el emoji con la etiqueta de texto (ya se hace en `TaskDetailScreen.kt:421`, mantenerlo en todos lados).

---

## I3. Touch targets < 48 dp en acciones frecuentes

**Problema →** Múltiples controles interactivos quedan por debajo del mínimo recomendado de 48×48 dp de Material/A11y: mini-FABs del menú de hogar en `HomeScreen.kt:186,197` (`Modifier.size(40.dp)`), botón borrar recompensa `RewardListScreen.kt:291` (`size(36.dp)`), celdas del selector de emoji `CreateRewardScreen.kt:158-160` (`size(40.dp)`), chips del calendario (`CalendarScreen.kt:569-583` con padding 1–3 dp), botones "+" de checklist/etiquetas (`CreateTaskScreen.kt:253-265`).

**Por qué importa →** Objetivos de 36–40 dp son difíciles de pulsar (fat-finger), especialmente para personas mayores o niños (público objetivo de una app de hogar) y en accesibilidad motora.

**Fix concreto →** Garantizar área táctil de al menos 48 dp: en `FloatingActionButton` usar `SmallFloatingActionButton` (que ya cumple) o añadir `Modifier.minimumInteractiveComponentSize()`; en `IconButton` no fijar `size(36.dp)`; en las celdas de emoji usar `minimumInteractiveComponentSize()` o `size(48.dp)`. Verificar todo `clickable` sin tamaño mínimo.

---

## I4. `contentDescription` ausente en iconos de acción

**Problema →** Muchos iconos se pintan con `Text(emoji)` (sin semántica accesible) o con `Icon(..., contentDescription = null)`. Casos: "⚙️"/"🔔"/"🗑️"/"✏️" como `TextButton` o `IconButton` en top bars (`HouseholdScreen.kt:324-365`, `TaskListScreen.kt:188-195`, `TaskDetailScreen.kt:141-151`), el botón enviar comentario "📤" (`TaskDetailScreen.kt:616-627`), iconos `contentDescription = null` en `ProfileScreen.kt:136,147,161,193` y en `HouseholdProfileCard` (`ProfileScreen.kt:216-221`).

**Por qué importa →** Con TalkBack, estos controles se anuncian como "botón sin etiqueta" o con el nombre del emoji ("engranaje", "papelera"), no con su función ("Ajustes", "Eliminar"). El usuario con lector de pantalla no puede navegar.

**Fix concreto →** Sustituir emoji por `Icon` con `contentDescription` significativo, o envolver el emoji en `Icon`/`Semantics { contentDescription = … }`. Regla: todo control iconográfico que no sea decorativo debe describir su acción.

---

## I5. FAB principal ambiguo (tres iconos "+" idénticos)

**Problema →** En `HomeScreen.kt:176-210`, el FAB principal (`contentDescription = "Nuevo hogar"`) solo abre/cierra un menú, y los dos FABs que revela usan el MISMO icono `Icons.Default.Add` ("Crear hogar", "Unirse a hogar"). Tres botones "+" con significados distintos y un FAB cuya etiqueta no coincide con su comportamiento real (no crea nada, alterna un menú).

**Por qué importa →** Confusión de affordance: el usuario no distingue "crear hogar" de "unirse" (ambos son "+"), y el FAB anuncia una acción que no ejecuta.

**Fix concreto →** Diferenciar iconos: `Add` para crear hogar, `GroupAdd`/`PersonAdd` para unirse. Cuando el menú esté abierto, cambiar el icono del FAB principal a `Close` (rotación 45º). Alternativa: `ExtendedFloatingActionButton` con texto "Crear hogar". Ajustar `contentDescription` dinámicamente.

---

## I6. Jerarquía tipográfica débil: `Typography()` por defecto + uso ad hoc de `sp`/`fontWeight`

**Problema →** `Theme.kt:304` usa `typography = Typography()` sin escala propia. En consecuencia, el diseño recurre a `fontSize = 72.sp` (`SplashScreen.kt:54,61`), `fontSize = 10.sp`/`9.sp` (gráficas `StatsScreen.kt:408,423`), `fontSize = 8.sp`/`9.sp` (calendario `CalendarScreen.kt:542,577`) y `fontWeight` sueltos, en lugar de estilos tipográficos (`displayLarge`, `headline*`, `label*`).

**Por qué importa →** Sin escala tipográfica no hay sistema de jerarquía: los tamaños divergen entre pantallas, los textos de gráficas y calendario quedan ilegibles (8–9 sp), y el splash usa sp brutos en lugar de la escala responsiva de Material.

**Fix concreto →** Definir una `Typography` propia (mínimo ajustar `displayLarge`, `titleLarge`, `labelSmall` con pesos y `letterSpacing` coherentes) y reemplazar todos los `fontSize`/`sp` literales por estilos del tema. Las gráficas Canvas deben usar `MaterialTheme.typography.labelSmall` en el `TextMeasurer` y subir a ≥11 sp.


---

# Revisión UI/UX — Task Hub · Hallazgos MENOR

---

## M1. Texto de color insuficiente sobre fondo blanco (cuerpo)

**Problema →** Varios textos de cuerpo usan colores que fallan AA sobre `surface`/blanco: `Teal500` (3.05:1) en "Completado: …" (`TaskDetailScreen.kt:451`), `Teal700` (4.46:1, justo bajo el umbral) en los encabezados de sección del formulario (`CreateTaskScreen.kt:216,233,331,400,503,604`, `EditTaskScreen.kt:206,247,317,418,584`, `TaskDetailScreen.kt:356,489,523,566,590`) y `Teal600` (3.61:1) en "Marcar como leída" (`NotificationListScreen.kt:241`) y flechas de acordeón.

**Por qué importa →** Los títulos de sección son la guía de lectura del formulario largo; con 4.46:1 están técnicamente por debajo de AA. El texto "Completado" en Teal500 (3.05) es claramente insuficiente.

**Fix concreto →** Usar `Teal800`/`Teal900` para texto sobre blanco (`Teal900`/blanco = 8.22:1). Para títulos de sección usar `color = Teal800` o `onSurface` con peso bold. Reservar `Teal500`/`Teal600` solo para elementos grandes o fondos.

---

## M2. Densidad y padding inconsistente entre tarjetas

**Problema →** El padding interior de las tarjetas varía sin criterio: `12.dp` (`HouseholdTaskSection.kt:64`), `16.dp` (TaskCard, MemberCard, RewardCard), `20.dp` (`TaskDetailScreen.kt:299` info card, `HouseholdScreen.kt:411`), `24.dp` (`PersonalSpaceScreen.kt:105`). Las tarjetas de tarea usan `elevation` 0/1/2 dp de forma ad hoc y `RoundedCornerShape(12/16)` vs `shapes.large` mezclados.

**Por qué importa →** La inconsistencia espacial hace que la app parezca "cosida" y dificulta el escaneo visual; el ojo no aprende dónde empieza/termina cada elemento.

**Fix concreto →** Definir tokens de espaciado (p. ej. 16 dp para tarjetas estándar, 20 dp para tarjetas hero) y usar siempre `MaterialTheme.shapes` (no `RoundedCornerShape` sueltos). Unificar `CardDefaults.cardElevation` a un valor base (0–1 dp).

---

## M3. "← Volver"/"← Cancelar" como texto en lugar de icono estándar

**Problema →** La acción de volver se implementa como `TextButton { Text("← Volver") }` en casi todas las pantallas (p. ej. `TaskListScreen.kt:157-164`, `StatsScreen.kt:98-105`, `JoinHouseholdScreen.kt:82-84`, `CreateHouseholdScreen.kt:57-59`), mientras Home/Perfil usan `Icons.Default.ArrowBack`.

**Por qué importa →** Inconsistencia con la convención Android/Material (flecha a la izquierda en `navigationIcon`), y texto "← Volver"/"← Cancelar"/"← Inicio"/"← Tareas" con palabras variables que no siempre describen el destino.

**Fix concreto →** Unificar en el `TaskHubTopBar` propuesto (I1) con `navigationIcon` de flecha + `contentDescription` ("Volver"). Eliminar los `TextButton("← …")` dispersos.

---

## M4. Emoji en títulos de pantalla (doble codificación visual)

**Problema →** Los títulos mezclan emoji con texto: "➕ Nueva tarea", "📋 Tareas", "🏆 Ranking", "🎁 Recompensas", "📊 Estadísticas", "👤 Mi espacio" (top bars de `CreateTaskScreen.kt:116`, `TaskListScreen.kt:169`, `RankingScreen.kt:75`, etc.).

**Por qué importa →** Redundancia ruidosa: el emoji no aporta información nueva y compite con el título; se lee dos veces en TalkBack.

**Fix concreto →** Quitar el emoji de los títulos (el título en texto ya es suficiente) o sustituirlo por un `leadingIcon` con `contentDescription = null` (decorativo).

---

## M5. Splash fijo de 5 segundos

**Problema →** `SplashScreen.kt:38` bloquea la entrada 5000 ms con `delay(5000)`.

**Por qué importa →** 5 segundos de espera forzosa en cada arranque (y se añade al arranque real de la app) es mala UX; las guías recomiendan splashes de marca muy breves (<2 s) o un único frame.

**Fix concreto →** Reducir a ~1.5 s o vincular el fin a que el contenido esté listo (no a un temporizador fijo). Usar la API `androidx.core.splashscreen` si es Android puro.

---

## M6. Botón "Reintentar" sin acción en StatsScreen

**Problema →** `StatsScreen.kt:134` define `Button(onClick = { /* retry */ }) { Text("Reintentar") }` con el cuerpo vacío. Lo mismo en `RankingScreen.kt:101`.

**Por qué importa →** Es un control muerto: el usuario pulsa "Reintentar" y no ocurre nada, tras un error real de carga. Rompe la confianza y es un bug funcional.

**Fix concreto →** Extraer la lógica de carga a una lambda `loadStats()` invocada en `LaunchedEffect` y en `onClick`. En `RankingScreen` re-cargar miembros.

---

## M7. Gráficas Canvas con texto 8–10 sp y sin contraste garantizado

**Problema →** Los gráficos dibujan etiquetas con `TextStyle(fontSize = 9.sp/10.sp, color = Color.Gray/DarkGray)` (`StatsScreen.kt:408,423,489`), y el calendario usa `fontSize = 8.sp/9.sp` en chips y "+" (`CalendarScreen.kt:542,577`).

**Por qué importa →** Texto por debajo del tamaño mínimo legible y `Color.Gray`/`DarkGray` son colores fijos que no responden al tema oscuro ni garantizan contraste.

**Fix concreto →** Subir a ≥11 sp, usar colores del `colorScheme` (`onSurfaceVariant`), y medir con `MaterialTheme.typography.labelSmall`. Añadir descripción textual alternativa de los datos para lectores de pantalla.

---

## M8. Hacks de espaciado con `Spacer(width/height 72.dp)` para "simetría"

**Problema →** `CreateRewardScreen.kt:95` y `MemberRewardScreen.kt:89` insertan `Spacer(Modifier.width(72.dp))` para equilibrar el botón "← Cancelar"/"← Volver" de la derecha.

**Por qué importa →** Es un equilibrio frágil: el ancho del botón contrario varía con la localización (EN/ES), rompiendo la "simetría" buscada y dejando el título descentrado.

**Fix concreto →** Usar `CenterAlignedTopAppBar` (que centra el título por construcción) en lugar de `Row + Spacer(weight) + Spacer fijo`.

---

## M9. Duplicación de `LaunchedEffect(householdId)` en TaskListScreen

**Problema →** `TaskListScreen.kt:83-93` declara DOS `LaunchedEffect(householdId)` idénticos (carga tareas dos veces) más un tercer efecto en `:96-101` que también recarga.

**Por qué importa →** Provoca doble fetch y doble render al entrar, y es deuda de código que puede reintroducir bugs de estado.

**Fix concreto →** Consolidar en un único `LaunchedEffect(householdId) { setCurrentMemberId; loadTasks }`.

---

## M10. Texto DEBUG en rojo hardcodeado en producción

**Problema →** `TaskListScreen.kt:583-592` inyecta `Text("DEBUG: …", fontSize = 10.sp, color = Color.Red)` condicionado a `DebugFlags.isEnabled`, con color y tamaño fijos.

**Por qué importa →** Si se filtra en un build, rompe la interfaz con texto rojo de 10 sp fuera del tema.

**Fix concreto →** Envolver en `if (BuildConfig.DEBUG)` de la plataforma y usar estilos del tema; o eliminar.
