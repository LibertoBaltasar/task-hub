# Panel de expertos v12 — progreso (checkpoint tras oleada 1)

Ronda nueva iniciada 2026-09-17. Informe final: `docs/review-panel-expertos-v12-2026-09-17.md`
(se completa al terminar todas las oleadas).

## Verificación de hallazgos v11 (SOLO PROPUESTA / pendientes)

Todos verificados contra el código real por el coordinador antes de lanzar el
panel — **ninguno se resolvió por sí solo**, siguen en el mismo estado que
dejó v11 (siguen siendo decisiones de producto/infra o refactors de alcance
amplio, no se tocan en esta ronda salvo que un experto nuevo aporte un ángulo
distinto):

- StatsScreen TTL 90 días erosiona totales de por vida — SIGUE ABIERTO (producto).
- `firestore.rules`: `isValidOwnerSuccession` permite auto-nombrarse owner sin
  expulsión real — SIGUE ABIERTO (infra/reglas), verificado línea 383-391.
- `achievements` sin restricción de propietario en reglas — SIGUE ABIERTO,
  verificado línea 579-581.
- Auth anónima de Firebase ya no se usa en el código (`signInAnonymously`
  no aparece en `commonMain`) — el hallazgo pendiente es solo sobre el
  proveedor server-side en la consola de Firebase, sigue fuera de alcance.
- Duplicación de puntos por timeout en `donatePoints`/`redeemReward` — SIGUE
  ABIERTO (requiere idempotencia con Cloud Function).
- Purga sin techo server-side de `taskHistory`/`messages`/`notifications` —
  SIGUE ABIERTO (requiere Cloud Scheduler).
- Calendario sin historial completo de recurrentes — SIGUE ABIERTO, verificado
  que `CalendarScreen.kt` solo usa `task.lastCompletedDate`, no `taskHistory`.
- Duplicación estructural CreateTaskScreen (1220 líneas) / EditTaskScreen
  (1117 líneas) — SIGUE ABIERTO, confirmado tamaño.
- Sin `WindowSizeClass`/`BoxWithConstraints` en toda la app — SIGUE ABIERTO,
  confirmado 0 usos.
- HomeScreen sin `TaskHubTopBar` compartido — SIGUE ABIERTO, confirmado
  (usa `TopAppBar` inline).
- Sin `ErrorStateBlock` compartido (~15 duplicaciones) — SIGUE ABIERTO,
  confirmado que no existe el composable.
- CSV "veces completada" es booleano derivado — SIGUE ABIERTO, confirmado
  comentario explícito en `TaskCsvExporter.kt:36-38`.
- Sin aviso de cambios sin guardar en formularios largos — SIGUE ABIERTO,
  confirmado 0 usos de `BackHandler`/dirty-state en Create/EditTaskScreen,
  CreateRewardScreen.
- Badge de notificaciones puede infracontar — SIGUE ABIERTO (requiere índice
  compuesto Firestore).

## Oleada 1 — Estética, Funcionalidad end-to-end, Accesibilidad WCAG AA, UI/componentes

4 subagentes en paralelo, modo solo diagnóstico (sin editar). Hallazgos
consolidados y fixes ya aplicados por el coordinador:

### Aplicados
1. **[Estética]** Versión hardcodeada `v0.7.33`→`v0.7.35` en `WelcomeScreen.kt:170`.
2. **[Estética]** `headlineLarge`/`headlineMedium` sin peso propio en
   `TaskHubTypography` (`Theme.kt:328`) — añadido `FontWeight.Bold` a ambos,
   coherente con `headlineSmall`.
3. **[Estética + UI/componentes, hallazgo coincidente]** `index.html` (wasmJs)
   sin `background-color` → flash blanco antes de montar Compose — añadido
   `#007660` (Teal800, mismo color que `SplashScreen`).
4. **[Funcionalidad, CRÍTICO]** `SettingsSheet.kt` "Vincular calendario" fallaba
   en silencio en iOS/web/desktop (regresión: el mismo bug ya se había
   corregido en `TaskDetailScreen.kt` pero no en este segundo call site) —
   añadido estado de error + `liveRegion`, mismo patrón que `TaskDetailScreen`.
5. **[Funcionalidad]** Página de cierre del flujo OAuth loopback de desktop
   (`GoogleDesktopSignInHelper.kt`) afirmaba "Sesión iniciada" antes de que el
   canje de código por token hubiera ocurrido — texto cambiado a neutro
   ("Procesando...").
6. **[UI/componentes]** "Punto de estado" reimplementado con 3 formas
   distintas (2 de ellas NO círculos reales) en `CalendarScreen.kt` y
   `TaskListScreen.kt` — extraído `StatusDot` compartido
   (`ui/components/StatusDot.kt`), sustituidos los 3 call sites.
7. **[UI/componentes]** `RoundedCornerShape(4.dp)` literal en `CalendarScreen.kt`
   `TaskChip` → `MaterialTheme.shapes.extraSmall` (mismo valor, token en vez
   de literal).
8. **[Accesibilidad, IMPORTANTE]** Contraste insuficiente en
   `NotificationListScreen.kt` (`secondaryColor` con `.copy(alpha=0.8f)`
   sobre `onPrimaryContainer`, 4/6 temas por debajo de 4.5:1) — alpha eliminado.
9. **[Accesibilidad, IMPORTANTE]** Mismo patrón en `StatsScreen.kt` (etiquetas
   de racha, 3/6 temas por debajo de 4.5:1) — alpha eliminado en ambos usos.

### Evaluado y NO aplicado (decisión del coordinador)
- **[Funcionalidad]** Snackbar de confirmación tras `shareText()` — descartado
  como fix mecánico: Android abre share sheet nativo, desktop copia a
  portapapeles sin ningún share real, y web es un no-op total. Un mismo
  mensaje "compartido" sería engañoso en web (nada ocurre) e impreciso en
  desktop. Requiere que `shareText` devuelva una señal por plataforma de qué
  ocurrió realmente — no es un cambio de una línea. Degradado a SOLO PROPUESTA
  para el informe final.

### SOLO PROPUESTA (no tocadas, documentadas en el informe final)
- iOS: `AppIcon`/`AccentColor` sin asset real.
- Onboarding con emoji genérico en vez de `AppLogo` (3 pantallas).
- Estados vacíos de Ranking/HouseholdMemberList sin ilustración Canvas.
- Web: `inviteCode` con PRNG no criptográfico (`Random`, no `crypto.getRandomValues`).
- Web: `<html lang="es">` fijo, no sincronizado con el selector de idioma de la app.
- Web: accesibilidad del árbol de semántica Compose-en-canvas sin verificar con lector de pantalla real.
- `TaskDetailScreen`: selector de completador sin `Modifier.selectableGroup()`.
- `RankingScreen`: medallas 🥇🥈🥉 sin `contentDescription` explícito localizado.

## Siguiente paso
Oleada 2: UX, Programador senior, Jefe de arquitectura, QA/bugs.
