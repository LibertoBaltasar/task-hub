# Panel de expertos v13 — progreso (2026-09-18)

HEAD de partida: `71c7bce` (v0.7.38). Sin progreso previo de v13 (arranque limpio).

Regla de oleadas: máximo 4-5 subagentes simultáneos. Plan: 3 oleadas (5+4+4)
cubriendo los 13 especialistas.

Nota decisión producto 2026-09-18: efectos de delight/motion (animaciones,
transiciones, celebraciones, háptica) son dirección de producto aprobada,
controlados por un futuro "Modo simple" (encargo paralelo
`revision-delight-experiencia-2026-09-18.md`). Accesibilidad (#3) y UX (#5)
NO deben reportarlos como violaciones reduce-motion/WCAG salvo que transmitan
información esencial sin alternativa estática.

## Estado de oleadas

- [x] Oleada 1 (5): Estética(#1), Funcionalidad(#2), Accesibilidad(#3), UI/componentes(#4), UX(#5)
- [ ] Oleada 2 (4): Programador senior(#6), Arquitectura(#7), QA/bugs(#8), Seguridad(#9)
- [ ] Oleada 3 (4): Privacidad(#10), Rendimiento(#11), Red/offline/sync(#12), Cobertura pruebas(#13)
- [ ] Consolidación informe final `docs/review-panel-expertos-v13-2026-09-18.md`
- [ ] Aplicación de fixes seguros
- [ ] Verificación build + jvmTest (XML real)
- [ ] Commit final

## Hallazgos SOLO PROPUESTA de v12 a verificar contra código real (asignados por experto)

Ver `docs/review-panel-expertos-v12-2026-09-17.md` completo para contexto.
Lista de verificación entregada a cada experto en su oleada; resultados se
consolidan aquí tras cada oleada.

## Resultados por oleada

### Oleada 1 (completada)

**#1 Estética** — v12 solo-propuesta: 2/2 siguen abiertos (iOS AppIcon/AccentColor sin asset; onboarding con emoji en vez de AppLogo). Nuevo CRÍTICO: `Theme.kt:328-335` `TaskHubTypography` — 6 roles (`headlineLarge/Medium/Small`, `titleLarge/Medium`, `labelSmall`) construidos como `TextStyle(...)` sueltos en vez de `.copy()` sobre el `Typography()` base → pierden `fontSize`/`lineHeight`/`letterSpacing` del type-scale M3, colapsan a 14sp por defecto de Compose (afecta `TaskHubTopBar` y toda la app). APLICABLE directo. Menor: versión hardcodeada `WelcomeScreen.kt:170` desincronizada otra vez (v0.7.35 vs 0.7.38 real); `StatsScreen`/`CalendarScreen` sin `ShimmerList` (usan `CircularProgressIndicator` genérico) a diferencia de 6 pantallas hermanas.

**#2 Funcionalidad** — v12: shareText() snackbar sigue igual (decisión de producto, no tocar); inviteCode CSPRNG web sigue corregido. Nuevo IMPORTANTE: `AssignmentCompletionRules.kt`/`TaskReconciliation.kt`/parcialmente `PenaltyRules.kt` huérfanos (no invocados desde `commonMain`, lógica real vive portada a `functions/src/*.ts`, KDoc desactualizado); `completeAssignment.ts` reimplementa inline sin test TS dedicado. PROPUESTA: `redeemReward` timeout post-commit puede borrar un `redemption` que sí se cobró (ángulo nuevo del hueco de atomicidad ya documentado, requiere Cloud Function idempotente).

**#3 Accesibilidad** — v12: `<html lang="es">` sigue fijo (abierto); semántica Compose-canvas wasmJs sigue sin verificar (informativo); `TaskDetailScreen` selector sin `selectableGroup()` confirmado y AMPLIADO a `SettingsSheet.kt` (3 grupos radio más); medallas `RankingScreen` sin contentDescription pero mitigado por texto ordinal redundante. Nuevo IMPORTANTE: `TaskListScreen.kt:1003` alpha 0.7 sobre `onSurfaceVariant` (mismo patrón que v12 ya corrigió 2 veces), 3/6 temas bajo 4.5:1 — APLICABLE directo. `selectableGroup()` ausente en 4 sitios (`TaskDetailScreen.kt:1142`, `SettingsSheet.kt:598`×3 usos) — APLICABLE directo. Confirmaciones positivas: Theme.kt 54 combinaciones de color pasan WCAG AA, touch targets OK, contentDescription=null todos justificados.

**#4 UI/componentes** — v12: ilustraciones estado vacío (RankingScreen/HouseholdMemberList) siguen con emoji — abierto, ampliado a RewardListScreen/NotificationListScreen también (4 sitios). PROPUESTA: extraer `EmptyState` compartido (6 duplicaciones con deriva de tipografía titleLarge/titleMedium inconsistente). MENOR APLICABLE: `StatsScreen.kt:451-455` reimplementa inline lo que ya hace `StatusDot` (creado en v12 para evitar justo esto).

**#5 UX** — v12: login web ahora parcialmente resuelto (ya soportado desde commit 8302d99) pero mismo problema de fondo (no distingue notDisplayed/dismissed) — reencuadrado, sigue abierto. Desktop fallo=cancelación sigue abierto (KDoc ya no contradice tras v12, pero comportamiento sí). Timeout 5min sin botón cancelar sigue abierto. Compartir/CSV desktop sin confirmación sigue abierto. Nuevo IMPORTANTE: filas de miembro sin cuenta vinculada (perfiles infantiles) parecen clicables pero no hacen nada (`HouseholdMemberList.kt:266`). MENOR: spinner post-login sin texto/liveRegion (`App.kt:295-304`).

**Fixes APLICABLES identificados en oleada 1 (mecánicos, sin ambigüedad de diseño):**
1. `Theme.kt:328-335` — Typography con `.copy()` sobre base en vez de `TextStyle()` suelto (CRÍTICO).
2. `TaskListScreen.kt:1003` — quitar `.copy(alpha=0.7f)` de `onSurfaceVariant`.
3. `TaskDetailScreen.kt:1142`, `SettingsSheet.kt:598` (3 usos) — añadir `.selectableGroup()`.
4. `StatsScreen.kt:451-455` — usar `StatusDot` en vez de reimplementación inline.
5. `RankingScreen.kt:181-202` — fusionar semántica del ordinal, evitar doble lectura del emoji (menor).

Pendiente de aplicar hasta cerrar todas las oleadas y consolidar.

### Oleada 2 (en curso)

Programador senior(#6), Arquitectura(#7), QA/bugs(#8), Seguridad(#9).
