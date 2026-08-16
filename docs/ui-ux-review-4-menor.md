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
