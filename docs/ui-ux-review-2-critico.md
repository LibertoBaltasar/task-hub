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
