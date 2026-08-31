# Panel de expertos — revisión UI/UX, accesibilidad, funcionalidad y bugs (2026-08-31)

Revisión de Task Hub mediante panel de 5 especialistas (subagentes en paralelo,
cada uno con objetivo independiente y sin ver el trabajo de los demás):
**Estética**, **Funcionalidad/UX**, **Accesibilidad (WCAG AA + Android)**,
**QA/bugs** y **Consistencia técnica de UI**. Versión de partida: 0.7.23. Se
excluyó explícitamente de este informe todo lo ya cerrado por las 2 auditorías
previas (`docs/audit-2026-08-30.md`, 101 hallazgos) y los encargos de i18n
completo / roles seguros / CSPRNG / contraste parcial ya aplicados.

## Resumen — hallazgos por experto y severidad

| Experto | CRÍTICO | IMPORTANTE | MENOR | Total |
|---|---|---|---|---|
| 1 — Estética | 0 | 6 (+5 propuestas) | 1 | 7 + 5 propuestas |
| 2 — Funcionalidad/UX | 0 | 4 (Alto) | 6 (Medio/Bajo) | 10 |
| 3 — Accesibilidad | 2 | 6 | 3 | 11 |
| 4 — QA/bugs | 0 | 2 (Alto) | 1 (Bajo/Medio) | 3 |
| 5 — Consistencia técnica | 0 | 5 | 1 | 6 |
| **Total** | **2** | **23** | **12** | **37 + 5 propuestas** |

Nota de escalas: cada experto usó su propia escala de severidad (el panel no
impuso una única taxonomía para no perder matices específicos de su
especialidad). "CRÍTICO" arriba = hallazgos que el propio experto marcó como
bloqueantes de uso (accesibilidad) o Crítico/Alto de bug; "IMPORTANTE" agrupa
Alto/Importante; "MENOR" agrupa Medio/Bajo salvo donde se indica.

## Tabla top-10 impacto / esfuerzo

| # | Hallazgo | Experto(s) | Impacto | Esfuerzo | Aplicado |
|---|---|---|---|---|---|
| 1 | `isAdmin` ignora `ownerId` → hogar puede quedar sin nadie con controles de admin (bloqueo permanente) | UX, QA | Alto | Medio | ✅ |
| 2 | Fallos silenciosos en borrar/salir de hogar y borrar recompensa | UX | Alto | Bajo | ✅ |
| 3 | Auto-degradación de admin sin guarda (`isSelf`) | UX, QA | Alto | Bajo | ✅ |
| 4 | Cards con fondo fijo (Teal50/Coral100) + texto de tema → ilegible en oscuro (6 sitios) | Accesibilidad | Crítico | Medio | ✅ |
| 5 | `CalendarScreen` status label/badge con contraste insuficiente | Accesibilidad | Crítico/Importante | Bajo | ✅ |
| 6 | `StatsScreen` en blanco si el miembro no resuelve a tiempo | UX | Alto | Bajo | ✅ |
| 7 | 235 literales `Teal*`/`Coral*` en 27 archivos rompen theming Naturaleza/Minimal | Estética, Consistencia | Alto | **Muy alto** | ⚠️ parcial (solo los que fallaban WCAG) |
| 8 | Rol admin/miembro solo con emoji (sin texto) en selectores de tarea | Accesibilidad | Importante | Bajo | ✅ |
| 9 | Sesgo de módulo en CSPRNG de iOS (`secureRandomInt`) | QA | Bajo/Medio | Bajo | ✅ |
| 10 | Duplicación CreateTaskScreen/EditTaskScreen (77% similitud) + roles reimplementados 7× | Consistencia | Alto | **Muy alto** | ⚠️ no aplicado (documentado) |

---

## Experto 1 — Estética / Diseño visual

### Hallazgo dominante (IMPORTANTE, aplicable — cobertura parcial en esta pasada)

**El theme system apenas se usa fuera de topbar/botones principales.** ~180-235
usos de `Teal*`/`Coral*` como literales en `ui/screens/` y `ui/components/`
(top ofensores: `TaskListScreen.kt` 34, `TaskDetailScreen.kt` 29,
`EditTaskScreen.kt` 20, `CreateTaskScreen.kt` 19, `StatsScreen.kt` 14,
`EmptyStateIllustrations.kt` 15) en vez de `MaterialTheme.colorScheme.*`.
**Por qué importa:** el selector de tema (Naturaleza/Minimal) en Ajustes queda
a medio aplicar — badges, indicadores del calendario, ilustraciones de estado
vacío y chips siguen en teal/coral fijo pase lo que pase.
**Fix concreto:** mapear cada literal a su rol semántico (`primary`/
`primaryContainer` para "teal", `tertiary`/`tertiaryContainer` para "coral",
o `MaterialTheme.semanticColors` para estado). **Aplicado en esta pasada solo
donde el literal fijo causaba además un fallo WCAG objetivo** (ver hallazgos
de Accesibilidad #1-#4 más abajo); el resto (~200 ocurrencias restantes, sin
fallo de contraste, solo inconsistencia de marca) se deja como **propuesta**
por ser un cambio de gran superficie que además toca la identidad visual de
cada pantalla — ver Deuda técnica.

### IMPORTANTE

- **`TaskListScreen.kt:906,912`** — mezcla en la misma expresión de un color
  de tema (`semanticColors.successContainer`) y un literal fijo (`Teal100`)
  para la misma pastilla según una condición. Evidencia más clara del
  problema de arriba. *(No tocado en esta pasada — parte del mismo patrón
  grande #7 de la tabla top-10.)*
- **`PointsBadge` reinventado a mano** en `TaskListScreen.kt`, `RankingScreen.kt`,
  `HouseholdMemberList.kt`, `PublicProfileScreen.kt`, `TaskDetailScreen.kt` en
  vez de reutilizar el componente ya existente (con tonos accesibles). **Se
  reutilizó `PointsBadge` en `CalendarScreen.kt`** (ver hallazgo de
  Accesibilidad #2) como caso piloto; el resto queda como refactor pendiente.
- **`StatsScreen.kt`** usaba `RoundedCornerShape(16.dp)/(12.dp)` literal en 6
  cards en vez de `MaterialTheme.shapes.large/medium`. **Aplicado** (fix
  mecánico y de bajo riesgo).
- **Identidad "Minimal" rota por elementos incidentales** (indicador de "hoy"
  del calendario, ilustraciones de estado vacío) en teal/coral puro. No
  aplicado — mismo alcance que el hallazgo dominante.
- **`WelcomeScreen.kt:180`** — versión mostrada `"v0.7.22"` desactualizada
  (build actual: 0.7.23), pese a que el propio comentario del código avisa de
  mantenerla sincronizada. **Aplicado.**

### MENOR

- `SplashScreen.kt` con colores de marca fijos — justificado (se muestra antes
  de cargar el tema).

### PROPUESTAS (no aplicadas — decisión visual del usuario)

1. `RankingScreen.kt:164-169` — 2º y 3º puesto comparten fondo, perdiendo
   jerarquía oro/plata/bronce.
2. `StatsScreen.kt:336` — `StreakCard` con más padding (20dp) que el estándar
   de la app (16dp).
3. `HomeScreen.kt:176-197` — usa `TopAppBar` propio con logo en vez de
   `TaskHubTopBar`; puede ser intencional (lockup de marca) — confirmar con
   el usuario si es a propósito.
4. `SplashScreen.kt` — colores de marca fijos rompen continuidad si el
   usuario tiene Naturaleza/Minimal seleccionado; requeriría persistir el
   tema en storage síncrono accesible antes del splash.
5. `AppLogo.kt:24-26` — colores del isotipo fijos en Teal/Coral; defendible
   como marca invariable, pero choca con la intención monocroma de Minimal.

---

## Experto 2 — Funcionalidad / UX

Verificó primero que los fixes de las 2 auditorías previas seguían en pie
(validación de puntos, `loadMembers` en `MemberRewardScreen`, reseteo de
`isCompleting` en `TaskListScreen`, `resolveCurrentMember` en
`HouseholdScreen`, `emptyBudgetText` en `DonateDialog`, etc.) — **sin
regresiones detectadas.**

### ALTO

1. **`StatsScreen.kt:90-207`** — pantalla en blanco silenciosa si `memberId`
   no coincide con ningún miembro (se dispara en la práctica porque
   `currentMemberId` arranca en `""` y se resuelve de forma asíncrona; si el
   usuario pulsa "Explorar" antes de que resuelva, Estadísticas queda muda:
   sin loading, sin error, sin nada). **Fix aplicado:** el caso "miembro no
   encontrado" ahora fija `errorMessage` (con reintento), en vez de dejar
   `statsData`/`errorMessage` ambos en `null`.
2. **`MemberScreenModel.kt:190-203` + `RewardListScreen.kt`** — borrar una
   recompensa fallaba en silencio: `RewardActionState.Error` se publicaba
   pero `RewardsBody` no estaba suscrito a `rewardActionState`. **Fix
   aplicado:** `RewardsBody` ahora observa `rewardActionState` y muestra un
   snackbar de error.
3. **`HouseholdScreen.kt:192-201,213-222`** — eliminar o salir del hogar
   (las 2 acciones más destructivas de la app) descartaban el mensaje de
   error (`onError = { _ -> isDeleting/isLeaving = false }`): el spinner
   desaparecía sin ninguna pista de si falló o hay que reintentar. **Fix
   aplicado:** el mensaje de error ahora se muestra en un `SnackbarHost`.
4. **`HouseholdMemberList.kt:242-267`** — el desplegable de cambio de rol se
   mostraba también sobre la propia tarjeta del admin (`isSelf` no se
   comprobaba, a diferencia de Agradecer/Donar que sí lo hacen). Un admin
   podía auto-degradarse a "Miembro" sin confirmación; si era el único admin,
   nadie más podía revertirlo desde la app. **Fix aplicado:** el menú de rol
   ya no se muestra sobre uno mismo (`isAdmin && !isSelf`).

### MEDIO

5. **`TaskDetailScreen.kt:203-213`** — "Vincular cuenta" de Google Calendar
   fallaba en silencio (a diferencia de "Sincronizar ahora", que sí muestra
   error). **Fix aplicado:** nuevo `TaskScreenModel.setCalendarLinkError()`,
   invocado cuando `linkCalendar()` devuelve `false`, reutilizando la misma
   tarjeta de error.
6. **`CreateTaskScreen.kt`/`EditTaskScreen.kt`** — el campo de valor de
   penalización no validaba nada: con "Aplicar penalización" activo y el
   campo vacío/0, se guardaba una tarea "con penalización" que en realidad
   nunca descontaba puntos (fallback silencioso a 0). **Fix aplicado:**
   mismo patrón que el campo "Puntos" (`isError` + gating del botón guardar).

### BAJO

7. `StatsScreen`/`RankingScreen`/`RewardListScreen` — las 3 clases `Screen`
   (con su propia topbar) nunca se navegan; `ExploreScreen` usa directamente
   los composables `*Body` internos. Código muerto — confirmado por grep en
   todo el repo, sin más referencias. **No aplicado** (ver Deuda técnica).
8. `HouseholdScreenModel.deleteMultipleHouseholds()` — implementado
   (borrado múltiple con reporte de fallos parciales) pero sin ningún punto
   de entrada en la UI. **No aplicado.**
9. Versión hardcodeada desactualizada en `WelcomeScreen.kt` — mismo hallazgo
   que Estética #IMPORTANTE. **Aplicado.**
10. Elegir "Semanal" sin marcar ningún día no está bloqueado ni explicado
    (se comporta como "todos los días", comportamiento intencional del motor
    de recurrencia pero sorprendente sin pista visual). **No aplicado**
    (cosmético, bajo impacto).

---

## Experto 3 — Accesibilidad (WCAG AA + Android)

Ratios verificados con `wcag_contrast.py` sobre los hex reales de `Theme.kt`/
`SemanticColors.kt` y de cada archivo — no se asumió ningún ratio previo.
Confirmó que las correcciones de contraste de auditorías previas (pares
primary/onPrimary, containers, `SemanticColors`) siguen vigentes en los 3
temas × claro/oscuro.

### CRÍTICO (bloquea uso) — **todos aplicados**

1. **Patrón sistémico: `Card`/`Surface` con fondo fijo (`Teal50`, `Coral100`)
   + texto que sigue el tema (`onSurfaceVariant`/`colorScheme.primary`)** —
   ilegible en modo oscuro (hasta 1.18:1 medido). 6 sitios:
   `HouseholdScreen.kt` (card de código de invitación), `TaskDetailScreen.kt`
   (card de info de tarea + cards de comentarios), `StatsScreen.kt`
   (`StreakCard`), `CreateRewardScreen.kt` (preview de recompensa),
   `PersonalSpaceScreen.kt` (hero card). **Fix aplicado en los 6**: color de
   texto fijo emparejado con el fondo fijo (p.ej. `Teal800` sobre `Teal50`,
   ratio 4.99-7.02:1) o migración a `primaryContainer`/`onPrimaryContainer`/
   `surfaceVariant` (par ya auditado, se adapta solo).
2. **`CalendarScreen.kt:699-728`** — status label ("Completada"/"Atrasada")
   y badge de puntos con colores fijos (`Color(0xFF2E7D32)`, `Teal100`) sobre
   `colorScheme.surfaceVariant` (que sí cambia con el tema): fallaba en los 3
   temas en oscuro y en Naturaleza claro (hasta 1.30:1). **Fix aplicado:**
   sustituidos por `PointsBadge` con `BadgeTone.Success/Error/Info/Teal`
   (pares container/onContainer ya auditados ≥4.5:1 en las 6 combinaciones);
   se añadió `BadgeTone.Error` al componente (no existía).

### IMPORTANTE — **todos aplicados**

3. `CalendarScreen.kt` badge de puntos con `Teal100` (4.22:1, falla) en vez
   de `Teal50` como en `TaskListScreen.kt` — resuelto junto con #2.
4. `WelcomeScreen.kt:156-171` — botón "Mis hogares" con `Teal600` sobre
   `Teal50` (3.22:1, falla siempre por ser colores fijos). **Fix:** `Teal800`
   (7.02:1 con Teal50, mismo patrón ya usado en otros badges de la app).
5. `MinimalDarkColorScheme.surfaceVariant/onSurfaceVariant` (`Theme.kt`) —
   3.49:1, por debajo de AA; es el color de texto secundario/caption por
   defecto de todo el tema Minimal oscuro, no un caso aislado. **Fix:**
   `onSurfaceVariant` de `MonoGray400` → `MonoGray200` (8.57:1).
6. Rol admin/miembro mostrado **solo con emoji** (sin texto) en los
   selectores de miembro de `CreateTaskScreen.kt:604` y
   `EditTaskScreen.kt:634,728` — TalkBack/VoiceOver lee el nombre unicode del
   glifo ("corona"/"busto"), no "administrador"/"miembro". **Fix:**
   sustituido por `s("member_role_admin_short"/"child_short")`, que ya
   incluye emoji+texto (mismo patrón usado en `HouseholdMemberList.kt`/
   `RankingScreen.kt`).
7. `CreateRewardScreen.kt:129-158` — selector de emoji con
   `GridCells.Fixed(8)`: en móviles estrechos cada celda queda por debajo de
   48dp aunque el `Surface` interior pida `size(48.dp)` (la restricción
   "tight" de `Fixed` prevalece). **Fix:** `GridCells.Adaptive(minSize =
   48.dp)`.
8. `SplashScreen.kt:81-87` — subtítulo con `Color.White.copy(alpha=0.8f)`
   sobre `Teal800` (4.15:1, falla para 14sp normal). **Fix:** alpha subido a
   0.95 (5.20:1).

### MENOR

9. `UserAvatar.kt:58-61` — el `contentDescription` recibido por el
   componente no se aplicaba en la rama de emoji (la más común), dejando que
   el screen reader leyera el glifo crudo. **Fix aplicado:**
   `Modifier.clearAndSetSemantics` en el `Box` contenedor, que fija la
   descripción y anula la de los hijos en las 4 ramas.
10. `SemanticColors.kt` — el par base `success`/`onSuccess` e `info`/`onInfo`
    (no el "Container") sigue por debajo de AA en oscuro (~3.9:1). Hoy no se
    usa en ningún sitio (solo se usan las variantes Container, ya
    auditadas) — **no aplicado**, documentado en el propio archivo como
    riesgo si se usara en el futuro como par fondo/texto directo.
11. Colores de marca fijos (`Teal50/Teal800`) usados igual en los 3 temas en
    varios badges — el contraste en sí pasa, pero es inconsistencia visual
    (no WCAG), mismo hallazgo que el dominante de Estética. No aplicado.

---

## Experto 4 — QA / Detección de bugs

Foco explícito en los 3 commits más recientes (`b529100` roles seguros,
`316de8b` i18n completo, `0c5472d` CSPRNG/contraste) por ser la zona con más
probabilidad de bugs nuevos no auditados aún, más una revisión de
`firestore.rules` v4. Sin regresiones sobre lo ya corregido en las 2
auditorías previas.

### ALTO

1. **`TaskDetailScreen.kt:166` / `HouseholdScreen.kt:119`** — el modelo de
   "admin" de la UI ignoraba `ownerId`: `firestore.rules` v4 define
   `isTrusted(hid) = isOwner(hid) || isAdminMember(hid)` (el owner siempre
   puede gestionar roles/reasignar, sea cual sea su `role`), pero ninguna
   pantalla comparaba `ownerId` contra el usuario actual. Como
   `CreateProfileScreen.kt` deja al creador del hogar elegir libremente su
   propio rol (incluido "Miembro"), y `JoinHouseholdScreen.kt` fuerza
   `role="child"` a todo el que se une después, era posible que **ningún
   miembro llegara jamás a `role=="admin"`** en un hogar — bloqueo
   permanente del menú de gestión de roles y de "cambiar quién completó",
   solo recuperable editando Firestore a mano. **Fix aplicado:** `ownerId`
   añadido a `HouseholdResponse` (DTO + parser + los 2 constructores en
   `FirestoreRepository`); `isAdmin` en `HouseholdScreen.kt`,
   `TaskDetailScreen.kt` y `RewardListScreen.kt` ahora es
   `role == "admin" || currentUserId == household.ownerId`.
2. **`HouseholdMemberList.kt:242-266`** — corrobora el hallazgo de UX #4: sin
   guarda `isSelf`, combinado con el bug anterior, un admin auto-degradado
   (siendo el único) dejaba el hogar sin salida desde la UI. Ya cubierto por
   el mismo fix.

### BAJO/MEDIO

3. **`Platform.ios.kt:36-40` — `secureRandomInt` en iOS con sesgo de
   módulo.** Pedía 1 solo byte (256 valores) y aplicaba `% bound`: con
   `bound=36` (alfabeto del código de invitación), los primeros 4 caracteres
   del alfabeto salían con ~14% más probabilidad; además nunca podía
   devolver valores ≥256 para un `bound` mayor, sin documentarlo ni fallar
   explícitamente; tampoco comprobaba el código de retorno de
   `SecRandomCopyBytes`. Irónico por ser precisamente el código que cerró la
   deuda "CSPRNG" de la pasada anterior. **Fix aplicado:** rejection
   sampling sobre 31 bits (mismo algoritmo que
   `java.util.Random.nextInt(bound)`/Android `SecureRandom.nextInt(bound)`),
   con `check()` sobre el status de `SecRandomCopyBytes`. Impacto práctico
   previo acotado (solo se usaba con bound=36, no explotable para predecir
   el código completo), pero regresión de calidad real.

### Contexto operativo (no es un hallazgo nuevo, pero relevante para el panel)

`firestore.rules` sigue marcado **v4 — NO DESPLEGADO**. Mientras no se
despliegue, toda restricción de roles (incluida la que motiva el fix #1 de
arriba) solo existe como gating de cliente: cualquiera con las credenciales
de Firebase puede seguir escribiendo REST directo sin esas restricciones.

---

## Experto 5 — Consistencia técnica de UI

Verificó primero que no hay `Icons.Default.ArrowBack/ArrowForward` residuales
(0 ocurrencias, todo usa `AutoMirrored`) y que el i18n de textos comunes está
prácticamente limpio — confirma que las correcciones previas siguen en pie.

### IMPORTANTE

1. **235 literales de color** (`Teal*`/`Coral*`) en 27 archivos — mismo
   hallazgo dominante que Estética #1. Top ofensores listados ahí. **Fix
   aplicado solo en los casos que además fallaban WCAG** (ver Accesibilidad
   #1-#4); el resto queda documentado como deuda técnica de gran superficie.
2. **Duplicación CreateTaskScreen ↔ EditTaskScreen (~77% de líneas
   idénticas,** medido con `difflib.SequenceMatcher`, ratio 0.766 sobre
   ~1900 líneas). Incluye una lista de tags predefinidos hardcodeada en
   español **idéntica en ambos archivos** (`CreateTaskScreen.kt:532-534` /
   `EditTaskScreen.kt:562-564`, sin pasar por `AppStrings`) — es
   simultáneamente el único i18n residual real encontrado. **No aplicado**
   (extraer un `TaskFormContent` compartido es un refactor de gran
   superficie y riesgo — ver Deuda técnica); sí se corrigió el fix de
   validación de penalización y el emoji-solo-sin-texto en ambos archivos
   por separado, para no dejar pasar bugs objetivos mientras se decide el
   refactor.
3. **Lógica de "rol de miembro" (emoji + color + etiqueta) reimplementada
   7+ veces** (`HouseholdMemberList.kt`, `TaskDetailScreen.kt`,
   `RankingScreen.kt`, `PublicProfileScreen.kt`, `CreateTaskScreen.kt`,
   `EditTaskScreen.kt`) con **3 claves i18n distintas para "Admin"**
   (`member_role_admin_full`="Administrador", `ranking_role_admin`="Admin",
   `public_profile_role_admin`="👑 Administrador") — el mismo usuario ve
   texto distinto en 3 pantallas distintas. **No aplicado** (requiere
   convertir `role: String` a un `enum class MemberRole` y consolidar en un
   componente compartido — cambio de superficie amplia, ver Deuda técnica).
4. **5 diálogos de confirmación destructiva duplicados** (`HouseholdDialogs.kt`
   ×3, `RewardListScreen.kt`, `TaskDetailScreen.kt`), con las claves i18n
   `household_delete_btn`/`household_cancel` filtrándose a dominios no
   relacionados (recompensas, tareas). **No aplicado** — mismo tipo de
   refactor de superficie amplia que #2/#3.
5. **4 pantallas con `TopAppBar` de Material3 ad-hoc en vez de
   `TaskHubTopBar`** (título alineado a la izquierda, distinto al resto de
   la app que usa `CenterAlignedTopAppBar`): `EditProfileScreen.kt`,
   `ProfileScreen.kt`, `PublicProfileScreen.kt` (título dinámico) y
   `HomeScreen.kt` (con logo, caso distinto — ver propuesta de Estética #3).
   **Fix aplicado en las 3 primeras** (`TaskHubTopBar(title=..., onBack=...)`,
   sustitución mecánica de bajo riesgo); `HomeScreen.kt` se deja como
   propuesta por incluir el lockup de marca.

### MENOR

6. Tags predefinidos hardcodeados en español — ver #2 (mismo hallazgo,
   contado también como i18n residual).

---

## Fixes aplicados — archivos tocados

**Modelo de roles / `ownerId`**
- `network/models/DTOs.kt` — `HouseholdResponse.ownerId` (nuevo).
- `network/FirestoreParsers.kt` — parseo de `ownerId`.
- `network/FirestoreRepository.kt` — `ownerId` en `createHousehold`/
  `getOrCreatePersonalHousehold`.
- `ui/screens/HouseholdScreen.kt`, `TaskDetailScreen.kt`,
  `RewardListScreen.kt` — `isAdmin` honra `ownerId` (owner siempre confiado).
- `ui/components/HouseholdMemberList.kt` — guarda `isSelf` en el menú de rol.

**Fallos silenciosos**
- `ui/screens/HouseholdScreen.kt` — snackbar de error al eliminar/salir del
  hogar.
- `ui/screens/RewardListScreen.kt` — snackbar de error al borrar recompensa.
- `ui/screens/StatsScreen.kt` — estado de error explícito si el miembro no
  resuelve (antes: pantalla en blanco).
- `ui/screens/TaskDetailScreen.kt`, `ui/models/TaskScreenModel.kt` — error
  visible al fallar "Vincular cuenta" de Google Calendar
  (`setCalendarLinkError`).

**Accesibilidad (contraste WCAG AA)**
- `ui/screens/HouseholdScreen.kt` (card código invitación), `TaskDetailScreen.kt`
  (card info + comentarios), `StatsScreen.kt` (`StreakCard`),
  `CreateRewardScreen.kt` (preview), `PersonalSpaceScreen.kt` (hero card) —
  color de texto fijo/pares container ya auditados.
- `ui/screens/CalendarScreen.kt` — status label + badge de puntos migrados a
  `PointsBadge`; `ui/components/PointsBadge.kt` — nuevo `BadgeTone.Error`.
- `ui/screens/WelcomeScreen.kt`, `SplashScreen.kt` — contraste de botón y
  subtítulo.
- `ui/theme/Theme.kt` — `MinimalDarkColorScheme.onSurfaceVariant` (3.49:1 →
  8.57:1).
- `ui/screens/CreateTaskScreen.kt`, `EditTaskScreen.kt` — rol emoji+texto en
  selectores de miembro; touch target del selector de emoji en
  `CreateRewardScreen.kt` (`GridCells.Adaptive`).
- `ui/components/UserAvatar.kt` — `contentDescription` aplicado también en
  la rama de emoji.

**Funcionalidad / validación**
- `ui/screens/CreateTaskScreen.kt`, `EditTaskScreen.kt` — validación del
  campo de valor de penalización (mismo patrón que "Puntos").

**Consistencia técnica**
- `ui/screens/EditProfileScreen.kt`, `ProfileScreen.kt`,
  `PublicProfileScreen.kt` — `TaskHubTopBar` en vez de `TopAppBar` ad-hoc.
- `ui/screens/StatsScreen.kt` — `RoundedCornerShape` literal →
  `MaterialTheme.shapes.*`.
- `ui/screens/WelcomeScreen.kt` — versión mostrada actualizada a 0.7.23.

**Seguridad**
- `iosMain/.../platform/Platform.ios.kt` — `secureRandomInt` sin sesgo de
  módulo (rejection sampling).

---

## Deuda técnica / propuestas no aplicadas (y por qué)

1. **~200 literales de color restantes** (de los 235 totales, solo se
   tocaron los que fallaban WCAG) — migrar el resto a `colorScheme.*`
   requiere revisar 27 archivos uno a uno decidiendo el rol semántico
   correcto por cada uso; no se hizo mecánicamente para no arriesgar cambios
   de aspecto no solicitados en una pasada automatizada.
2. **Duplicación CreateTaskScreen/EditTaskScreen (77%)** — extraer un
   `TaskFormContent` compartido es la solución correcta, pero es un refactor
   de ~900 líneas por archivo con alto riesgo de regresión si se hace sin
   QA visual exhaustivo de ambos flujos (crear y editar) en los 3 temas;
   fuera de alcance de esta pasada de bugs/accesibilidad.
3. **Rol de miembro reimplementado 7× con 3 claves i18n distintas para
   "Admin"** — requiere introducir `enum class MemberRole` (toca DTOs,
   Firestore rules, y ~15 sitios que comparan `role == "admin"` como string)
   y un componente `MemberRoleBadge` compartido; cambio de modelo de datos,
   no solo de UI — se prefirió no tocarlo sin acuerdo explícito sobre el
   nuevo modelo.
4. **5 diálogos de confirmación destructiva duplicados** — candidato claro a
   `DestructiveConfirmDialog` genérico con claves i18n neutrales
   (`common_cancel`/`common_delete`), pero requiere renombrar claves usadas
   hoy en 5 sitios sin romper ninguna — se deja para una pasada dedicada a
   i18n/dedup.
5. **`firestore.rules` v4 sigue sin desplegar** — el fix de `ownerId`/
   `isAdmin` de esta pasada es del lado de la UI; para que sea imposible de
   saltar vía REST directo hace falta desplegar las reglas ya escritas (ver
   cabecera de `firestore.rules` y `docs/audit-2026-08-30.md`).
6. **3 clases `Screen` huertas** (`StatsScreen`, `RankingScreen`,
   `RewardListScreen` como pantallas independientes, nunca navegadas) y
   `HouseholdScreenModel.deleteMultipleHouseholds()` sin punto de entrada en
   la UI — código muerto de bajo riesgo pero no eliminado en esta pasada por
   priorizar los fixes de bug/accesibilidad con impacto directo en usuario.
7. **Propuestas estéticas de Experto 1** (5 puntos, ver esa sección) —
   cambios de identidad visual que el usuario debe decidir explícitamente.

---

## Verificación

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   # BUILD SUCCESSFUL
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain                      # BUILD SUCCESSFUL
```

No se compiló el target iOS (no hay toolchain de Xcode/macOS disponible en
este entorno) — el fix de `Platform.ios.kt` se revisó manualmente por
sintaxis pero no se verificó con el compilador de Kotlin/Native.
