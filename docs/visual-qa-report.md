# Task Hub — Auditoría Visual QA (dispositivo físico)

**Fecha:** 2026-08-17
**Dispositivo:** Xiaomi Mi A2 (Android, 1080×2160), tema oscuro, idioma español
**Build:** `composeApp-debug.apk` (`assembleDebug` desde `main`, commit actual)
**Método:** inspección por ADB — navegación real (`uiautomator` + `input tap`), captura de pantalla y análisis visual por IA de cada pantalla. No se modificó código.

---

## Resumen

| Severidad | Cantidad |
|-----------|----------|
| 🔴 Crítica | 1 |
| 🟠 Alta | 1 |
| 🟡 Media | 2 |
| ⚪ Baja | 2 |

**Hallazgo clave:** no se puede crear una recompensa en este dispositivo (el botón queda fuera de pantalla). Bloquea además el testeo visual de `MemberRewardScreen` y de `RewardListScreen` con contenido.

---

## 🔴 CRÍTICA

### 1. Botón «Crear Recompensa» inalcanzable — no se puede crear una recompensa

- **Pantalla:** `CreateRewardScreen` («Nueva recompensa»)
- **Descripción:** El formulario es una `Column` **no desplazable**. El contenido (selector de ícono + 3 campos + tarjeta «Vista previa») ocupa toda la altura útil del Mi A2. El `Spacer(Modifier.weight(1f))` que precede al botón colapsa a 0 de alto, y el botón «Crear Recompensa» (que va *después*) queda fuera de pantalla. En el árbol de accesibilidad aparece con `bounds="[0,0][0,0]"` (tamaño cero) y no se ve en pantalla. Al no haber scroll, el usuario **no puede pulsarlo de ninguna forma**.
- **Evidencia:** `docs/qa-screenshots/05-createreward-boton-inalcanzable.png`
- **Código:** `CreateRewardScreen.kt` — `Column` sin `verticalScroll` (~L72-77), `Spacer(Modifier.weight(1f))` (L273) y `Button` (L280).
- **Solución sugerida:** hacer el formulario desplazable (`LazyColumn` o `Column + verticalScroll(rememberScrollState())`) **o** quitar el `Spacer(weight(1f))` y colocar el botón inmediatamente después de la vista previa, con padding inferior para no chocar con la barra de navegación.

---

## 🟠 ALTA

### 2. Cabecera «🔔 Notificaciones» duplicada en Ajustes

- **Pantalla:** `SettingsSheet` (hoja de Ajustes)
- **Descripción:** La sección de notificaciones muestra el título «🔔 Notificaciones» **dos veces**: una como cabecera de la sección (`SettingsSection(title = s("settings_notifications"))`) y otra como etiqueta de la fila del interruptor (`Text(s("settings_notifications"))`). En pantalla se ven dos cabeceras apiladas/superpuestas.
- **Evidencia:** `docs/qa-screenshots/01-settings-notificaciones-duplicada.png`
- **Código:** `SettingsSheet.kt` — L146 (título de sección) y L154 (etiqueta interior).
- **Solución sugerida:** eliminar una de las dos repeticiones. Lo más limpio: quitar el `Text` interior (L153-157) y dejar solo la cabecera de sección, o viceversa.

---

## 🟡 MEDIA

### 3. Chips de etiquetas predefinidas se rompen («mascotas» en vertical)

- **Pantallas:** `CreateTaskScreen` y `EditTaskScreen` (misma sección «Etiquetas»)
- **Descripción:** Las etiquetas predefinidas se dibujan dentro de un `Row` (no un `FlowRow`). Al no caber 6 chips en una fila, los últimos se comprimen: «mascotas» se renderiza como una columna estrecha con el texto partido en vertical. El mismo patrón afecta a las etiquetas añadidas por el usuario (otro `Row` más arriba).
- **Evidencia:** `docs/qa-screenshots/03-createtask-tag-mascotas-roto.png`
- **Código:** `CreateTaskScreen.kt` L483-496 (predefinidas) y L457-475 (del usuario). Equivalente en `EditTaskScreen.kt`.
- **Solución sugerida:** sustituir `Row` por `FlowRow` (`androidx.compose.foundation.layout.FlowRow`) para que los chips hagan *wrap* a la siguiente línea en lugar de comprimirse.

### 4. Rol por defecto «Niño/a» al crear el primer miembro de un hogar

- **Pantalla:** `CreateProfileScreen` (tras crear un hogar)
- **Descripción:** El rol inicial es `"child"` (`var role by remember { mutableStateOf("child") }`). El creador del hogar —el primer miembro, que debería ser admin— sale por defecto como «Niño/a». Si el usuario no lo cambia manualmente, el hogar puede quedar **sin ningún admin**.
- **Evidencia:** `docs/qa-screenshots/04-createprofile-rol-nino.png`
- **Código:** `CreateProfileScreen.kt` L34. Mismo patrón en `JoinHouseholdScreen.kt` L35.
- **Solución sugerida:** inicializar en `"admin"` al menos para el primer miembro/creador del hogar.

---

## ⚪ BAJA

### 5. Etiquetas del eje X del gráfico inferior cortadas por la barra de navegación

- **Pantalla:** `StatsScreen` («Estadísticas»)
- **Descripción:** El gráfico «Puntos ganados esta semana» se extiende bajo la barra de navegación del sistema y sus etiquetas de fecha quedan parcialmente ocultas.
- **Evidencia:** `docs/qa-screenshots/06-stats-grafico-cortado.png`
- **Código:** `StatsScreen.kt` (contenedor del gráfico inferior).
- **Solución sugerida:** añadir `navigationBarsPadding()` o padding inferior al contenedor del gráfico.

### 6. Orden emoji/número inconsistente en Ranking

- **Pantalla:** `RankingScreen`
- **Descripción:** Los puntos se muestran «⭐ 10» (emoji primero) y la racha «0🔥» (número primero). Inconsistencia menor de estilo.
- **Evidencia:** `docs/qa-screenshots/07-ranking-emoji-orden.png`
- **Código:** `RankingScreen.kt` (~L183-203).
- **Solución sugerida:** unificar el orden (p. ej. siempre emoji primero: «🔥 0»).

---

## Notas (verificadas — NO son bugs)

- **Splash «TASK HUB» con poco contraste:** fue un artefacto de captura durante el fade-in (alpha 0→1 en 800 ms). El código usa `White` + `Coral100` sobre `Teal800`, contraste correcto. `SplashScreen.kt` está bien.
- **Línea roja «DEBUG: …» en la lista de tareas:** está correctamente condicionada a `DebugFlags.isEnabled`, que `MainActivity.kt` fija a `BuildConfig.DEBUG`. Solo aparece en builds de debug; **no** se empaqueta en release.
- **`WelcomeScreen`:** código muerto. `App.kt` navega siempre a `HomeScreen` y `WelcomeScreen` solo se menciona en un comentario. No es alcanzable, por lo que no se testea visualmente.

---

## Pantallas cubiertas (barrido completo)

Splash · Home · Ajustes (SettingsSheet) · Perfil · Crear hogar · Crear perfil · Hogar (detalle) · Lista de tareas (vacía y con tarea) · Nueva tarea · Detalle de tarea · Editar tarea · Estadísticas · Ranking · Recompensas (vacía) · Nueva recompensa · Notificaciones · Mi espacio personal · Calendario · Unirse a un hogar

## Pantallas no testeadas (bloqueadas)

- **`MemberRewardScreen`** (canje de recompensa) — bloqueado por el hallazgo #1 (no se puede crear una recompensa).
- **`RewardListScreen` con contenido** — bloqueado por el hallazgo #1.

> Recomendación: resolver el hallazgo #1 primero y repetir el barrido de las pantallas de recompensas, ya que su flujo no se ha podido verificar.
