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
