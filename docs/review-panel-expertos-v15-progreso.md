# Panel de expertos v15 — progreso (2026-09-24)

HEAD de partida: `a622bcd` (v0.7.46). Sin progreso previo de v15 (arranque limpio).

Regla de oleadas: máximo 4-5 subagentes simultáneos. Plan: 3 oleadas (5+4+4)
cubriendo los 13 especialistas.

Alcance nuevo desde v14 (`d90ca29` → `a622bcd`): commit único `a622bcd` "fix: 3
críticos — contraste WCAG en gradientes, puntos duplicados por timeout, cifrado
wasmJs". Tocó: `build.gradle.kts` (bump versión), `network/ErrorCategory.kt`
(categoría `AMBIGUOUS` nueva), `network/FirestoreRepository.kt` (no borrar
redemption si error ambiguo), `network/MemberRepository.kt` (no revertir
donación si error ambiguo → `DonateErrorReason.UNCERTAIN`),
`ui/components/PointsBadge.kt` (gradiente ambos extremos hacia negro en vez de
blanco/negro), `ui/screens/HouseholdScreen.kt` (hero card gradiente mismo color
en vez de primaryContainer→secondaryContainer), `storage/SecureStore.wasmJs.kt`
(cifrado AES-256-CTR con clave efímera vía Web Crypto, antes localStorage
plano).

**Sospecha a verificar por Seguridad(#9)/QA(#8)/Red-offline(#12):**
`SecureStore.wasmJs.kt` genera `ephemeralKey` nueva en CADA carga de módulo
(`private val ephemeralKey: ByteArray = jsRandomBytes(32)`, sin persistir) y
`jsAesCtrDecrypt` devuelve texto descifrado con la clave equivocada como
basura silenciosa (no lanza, no hay tag de autenticación al ser CTR sin GCM),
no `null`. Cualquier valor cifrado (`v1:...`) escrito en una carga de página
y leído tras recargar (nueva clave efímera) devolvería basura en vez del
valor real o `null` — posible regresión de persistencia de sesión en web
(re-login forzado en cada refresh, o peor: datos corruptos tratados como
válidos). Verificar contra código real antes de reportar.

## Estado de oleadas

- [x] Oleada 1 (5): Estética(#1), Funcionalidad(#2), Accesibilidad(#3), UI/componentes(#4), UX(#5)
- [ ] Oleada 2 (4): Programador senior(#6), Arquitectura(#7), QA/bugs(#8), Seguridad(#9)
- [ ] Oleada 3 (4): Privacidad(#10), Rendimiento(#11), Red/offline/sync(#12), Cobertura pruebas(#13)
- [ ] Consolidación informe final `docs/review-panel-expertos-v15-2026-09-24.md`
- [ ] Aplicación de fixes seguros
- [ ] Verificación build + jvmTest (XML real, --rerun-tasks, mínimo 273 tests, 0 fallos)
- [ ] Commit final

## Hallazgos SOLO PROPUESTA de v14 a verificar contra código real (primer trabajo, distribuido por experto)

Ver `docs/review-panel-expertos-v14-2026-09-20.md` completo. Lista principal:
- iOS `AppIcon`/`AccentColor` sin asset real (Estética).
- Onboarding con emoji genérico, incl. `JoinHouseholdScreen.kt:121` "🔑" (Estética).
- `StatsScreen`/`CalendarScreen` con `CircularProgressIndicator` genérico (Estética).
- `HouseholdMemberList.kt:129` `Text("👥")` sin ilustración compartida (UI/componentes).
- Wrapper de layout `EmptyState` no extraído como componente compartido (UI/componentes).
- `AssignmentCompletionRules`/`TaskReconciliation`/`PenaltyRules` huérfanos (Funcionalidad/Prog. senior).
- `redeemReward` timeout post-commit — YA APLICADO parcialmente en `a622bcd` (categoría AMBIGUOUS), verificar si queda algo abierto (Funcionalidad/QA).
- UX: login web sin distinguir fallo/cancelación; desktop confunde fallo de red con cancelación; timeout desktop 5 min sin botón cancelar; compartir/CSV desktop sin confirmación; filas de miembro sin cuenta vinculada indistinguibles (UX).
- `TaskScreenModel` god object 1305 líneas (Arquitectura).
- `FirestoreRepository` god object 1557+ líneas (Arquitectura).
- Sentinela `""`/`null` de `GoogleSignInResultHolder`, 7-8 call-sites incl. Swift (Arquitectura).
- `donatePoints`/`appreciateMember` duplicación de puntos — YA APLICADO parcialmente en `a622bcd`, verificar cobertura completa incl. `appreciateMember` (QA/Seguridad).
- Caché sin invalidar en `finally`, ~17 call-sites restantes (QA).
- `members/{mid}` auto-edición `totalPoints` sin tope; `isPeerPointsTransfer` sin rate-limit — firestore.rules sin tocar (Seguridad, NO aplicar reglas, solo documentar).
- UMP/CMP AdMob ausente (Privacidad, CRÍTICO heredado).
- Concurrencia optimista ausente en `updateTask`/`updateSubtasks` (Red/offline).
- Duplicación `formatFriendlyDate`/etc entre `TaskListScreen.kt` y `CalendarScreen.kt` (Prog. senior/UI).
- `isTaskOverdueOverall` en CalendarScreen no cubre recurrentes vencidas (Funcionalidad).
- Vibración duplicada: dos switches AND en SettingsSheet, decisión de producto (UX).
- Gradiente hero card / PointsBadge no memoizados con `remember` (Rendimiento, MENOR).
- Huecos de test: `ErrorCategory.kt`, `MemberRepository.addMemberPoints`, `OverdueSection`/`isTaskOverdueOverall`, `formatFriendlyDate` etc, integración `appreciateMember`/`donatePoints`, sin MockEngine de Ktor en el repo (Cobertura).

## Resultados por oleada

### Oleada 1 (completada)

**#1 Estética** — Los 2 fixes de contraste de a622bcd son correctos pero
sacrifican casi todo el efecto visual de degradado (PointsBadge casi plano;
hero card pierde bicromía) — SOLO PROPUESTA recalibrar con más margen visual,
no urgente. Heredados verificados: iOS AppIcon/AccentColor sin asset SIGUE
ABIERTO; onboarding con emoji SIGUE ABIERTO (4 sitios confirmados);
`CircularProgressIndicator` en StatsScreen/CalendarScreen SIGUE ABIERTO (4
líneas) pero ahora es casi mecánico migrar a `ShimmerPlaceholder` (componente
ya maduro en 6 pantallas) — APLICABLE. Versión hardcodeada en
`WelcomeScreen.kt:186` otra vez desincronizada (v0.7.44 vs 0.7.46 real) —
APLICABLE (regresión recurrente, 3ª vez).

**#2 Funcionalidad** — CRÍTICO NUEVO: clave i18n `transfer_error_uncertain`
(introducida por a622bcd) no existe en `AppStrings.kt` — el usuario ve la
clave sin traducir en pantalla. APLICABLE directo. CRÍTICO SIGUE ABIERTO:
`appreciateMember` (`MemberRepository.kt:700-706`) NO recibió el fix
AMBIGUOUS que sí tienen `donatePoints`/`redeemReward` — mensaje
`transfer_error_failed` ("tus puntos no se han visto afectados") puede ser
FALSO tras timeout real, invita a reintentar y duplicar. SOLO PROPUESTA
(toca enum público `AppreciateErrorReason` + call-sites) salvo que se decida
aplicar en esta ronda. Verificado: `ErrorCategory`/Ktor timeout coverage
SÓLIDO, sin huecos. `redeemReward` limbo de redemption huérfano SIGUE
ABIERTO (sin mecanismo de reconciliación). `AssignmentCompletionRules` et al
SIGUEN huérfanos. `isTaskOverdueOverall` SIGUE sin cubrir recurrentes.

**#3 Accesibilidad** — CRÍTICO SIGUE ABIERTO (parcial): el fix de
`PointsBadge.gradientBrush` arregla 5/6 combinaciones tema×modo pero **rompe
Naturaleza oscuro** (3.53:1/4.38:1 &lt; 4.5:1) — ese tema tiene texto oscuro
sobre fondo claro, así que oscurecer further el degradado reduce contraste
en vez de aumentarlo. Mismo defecto que antes, bajo fórmula distinta.
Hero card `HouseholdScreen.kt` YA RESUELTO (las 6 combinaciones ≥4.5:1,
margen mínimo 0.01 en Default oscuro). IMPORTANTE NUEVO: `AnimatedCounter.kt`
usa `shouldReduceMotion()` en vez de `effectsEnabled()`, no respeta "Modo
simple" (4 call-sites) — APLICABLE directo, patrón ya usado en
`ConfettiOverlay`. Heredados (`clearAndSetSemantics`, `selectableGroup()`)
confirmados sin regresión.

**#4 UI/componentes** — Sin cambios relevantes de a622bcd a este mandato.
`HouseholdMemberList.kt:129` emoji sigue sin ilustración SIGUE ABIERTO.
Wrapper EmptyState duplicado en 5 sitios SIGUE ABIERTO (SOLO PROPUESTA,
extracción de componente). IMPORTANTE NUEVO: `SplashScreen.kt` ignora el
tema del usuario (Teal/Coral fijos, literal, todos los temas) — SOLO
PROPUESTA (requiere resolver colores por tema, no mecánico de una línea).
IMPORTANTE NUEVO: "Modo simple" solo gatea animaciones en 5/22 archivos que
llaman a motion — 17 restantes usan `shouldReduceMotion()` suelto sin pasar
por `effectsEnabled()`, contradice la descripción de producto del ajuste
("Desactiva animaciones, efectos visuales y vibración") — SOLO PROPUESTA
(alcance multi-archivo).

**#5 UX** — Confirma independientemente el mismo CRÍTICO NUEVO que #2:
`transfer_error_uncertain` sin traducir — APLICABLE directo. Confirma mismo
CRÍTICO que #2: `appreciateMember` sin el fix AMBIGUOUS. Los 6 hallazgos UX
heredados (login web/desktop sin distinguir fallo/cancelación, timeout
desktop sin cancelar, CSV sin confirmación, filas sin cuenta vinculada,
doble interruptor vibración) confirmados SIGUE ABIERTO, todos SOLO PROPUESTA
(decisión de producto o refactor de contrato).

**Fixes APLICABLES identificados en oleada 1:**
1. Añadir clave `transfer_error_uncertain` ES/EN en `AppStrings.kt` (CRÍTICO, confirmado por 2 especialistas independientes).
2. `WelcomeScreen.kt:186` — sincronizar versión mostrada a 0.7.46.
3. `StatsScreen.kt:80`, `CalendarScreen.kt:331,1054,1191` — `CircularProgressIndicator` → `ShimmerPlaceholder`.
4. `AnimatedCounter.kt` — usar `effectsEnabled(EffectCategory.ANIMATIONS)` en vez de `shouldReduceMotion()` solo.

**Pendiente de decisión para esta ronda (candidatos a aplicar aunque más
grandes que "mecánico de una línea", dado que 2 especialistas independientes
confirman el mismo riesgo CRÍTICO de duplicación de puntos):**
- `appreciateMember` — aplicar mismo patrón AMBIGUOUS→UNCERTAIN que
  `donatePoints`.
- `PointsBadge.gradientBrush` — recalibrar para que Naturaleza oscuro también
  pase WCAG AA (posible: elegir dirección del lerp según luminancia relativa
  de `content` vs `base` en vez de siempre hacia negro).

Pendiente de aplicar hasta cerrar todas las oleadas y consolidar.

### Oleada 2

(pendiente)

### Oleada 3

(pendiente)
