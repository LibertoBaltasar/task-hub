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
- [x] Oleada 2 (4): Programador senior(#6), Arquitectura(#7), QA/bugs(#8), Seguridad(#9)
- [x] Oleada 3 (4): Privacidad(#10), Rendimiento(#11), Red/offline/sync(#12), Cobertura pruebas(#13)
- [ ] Consolidación informe final `docs/review-panel-expertos-v15-2026-09-24.md`
- [ ] Aplicación de fixes seguros
- [ ] Verificación build + jvmTest (XML real, --rerun-tasks, mínimo 273 tests, 0 fallos)
- [ ] Commit final

### Oleada 3 (completada)

**#10 Privacidad/RGPD** — NUEVO (severidad real más alta de la ronda en esta
área): cascada de "eliminar cuenta" (`GoogleAuthManager.deleteAccount()`,
`ui/models/GoogleAuthManager.kt:223-239`) itera SOLO
`householdStore.getSavedHouseholds()` (caché local), que depende de
`syncHouseholdsToCloud()` — documentado como "aditivo, tolerante a fallos,
nunca poda" — para reflejar la membresía real. Si esa sync nunca completó
(carrera conocida en `reconcileHouseholds`), un hogar queda invisible para
el borrado y el UID del usuario permanece indefinidamente en
`taskHistory`/`rewardRedemptions`/chat de ese hogar — incumple RGPD art.
17 pese a lo que promete `privacy.html` §6. SOLO PROPUESTA (requiere
resolver membresía desde servidor, no caché local). Heredados sin cambios:
UMP/CMP ausente (SIGUE ABIERTO, PROPUESTA), scope OAuth Calendar demasiado
amplio (SIGUE ABIERTO, PROPUESTA, requiere re-consentimiento), gating de
edad ausente (SIGUE ABIERTO, decisión de producto ya tomada). NUEVO menor:
interstitial de AdMob se muestra a perfiles `role="child"` sin comprobación
(`TaskScreenModel.kt:600`) — condicionado a que se confirme que el ítem del
checklist sigue vigente (la "vista infantil" que lo justificaba no existe).
Analytics sin PII, Crashlytics, purga 90 días y anonimización al
salir/expulsar: verificados correctos, sin hallazgo.

**#11 Rendimiento** — Confirma hallazgo MENOR heredado de v14 (gradientes
sin `remember` en `PointsBadge.kt:109-111` y `HouseholdScreen.kt:546-553`,
a622bcd solo cambió colores no memoización) y lo AGRAVA: `HouseholdScreen`
lee `newMessageText` como `StateFlow` en el nivel superior de `Content()`
(833 líneas), así que cada tecla escrita en el chat recompone toda la
pantalla incluidos los `Brush.linearGradient` inline — APLICABLE el fix de
memoización con `remember`, SOLO PROPUESTA mover `newMessageText` a estado
local. NUEVO: poll de "no leídas" cada 30s trae hasta 300 documentos
completos sin atajo de caché en camino feliz (`HouseholdScreen.kt:210-219`,
`NotificationRepository.kt:25,134-151`) — SOLO PROPUESTA (subir intervalo,
agregación server-side o contador denormalizado). NUEVO: splash de 1.5s
fijo no se solapa con bootstrap real de Koin/auth/household (`App.kt:88-104`,
`SplashScreen.kt:58-61`) — SOLO PROPUESTA (reordenar arranque). Verificado
sin hallazgo: `AnimatedCounter`/`ConfettiOverlay` bien memoizados,
`groupTasksByStatus`/`tasksByDate` con `remember` correcto,
`NotificationPollWorker` ya optimizado, tamaño de build sano.

**#12 Red/offline/sync** — Explica la CAUSA RAÍZ del hallazgo QA de oleada
2: el fix AMBIGUOUS de a622bcd camufla el doble-gasto pero no lo resuelve
— no existe ningún mecanismo de reconciliación posterior para
donar/canjear/agradecer (`TaskReconciliation` existe pero está confirmado
desconectado de este flujo, `TaskScreenModel.kt:282-291`); contrasta con
`completeRecurringTask` que SÍ está resuelto de raíz vía Cloud Function
transaccional con precondición server-side (`FirestoreRepository.kt:1082-1164`)
— SOLO PROPUESTA migrar donar/canjear/agradecer al mismo patrón. NUEVO
APLICABLE: `redeemReward` reproduce el mismo bug de caché "congelada" que
#8 encontró en `addMemberPoints` pero en un call-site que ese fix no cubre
— `FirestoreRepository.kt:1563-1564` no invalida `taskCache` antes de
relanzar en AMBIGUOUS, y `MemberScreenModel.kt:327-331` (catch genérico de
`redeemReward`) no llama `loadMembers` salvo en éxito. NUEVO: `isOnline()`
casi sin usar (1 solo call-site en todo el repo), ninguna operación de
puntos comprueba conectividad antes de intentar — SOLO PROPUESTA. SIGUE
ABIERTO sin cambios: concurrencia optimista ausente en
`updateTask`/`updateSubtasks` (`TaskRepository.kt:705-710,746-751`).

**#13 Cobertura de pruebas** — 273 tests sin cambios desde v14, `a622bcd`
no añadió ninguna línea de test pese a tocar `ErrorCategory.kt` y
`SecureStore.wasmJs.kt` (ambos ya señalados como huecos CRÍTICOS en v14).
NUEVO: `MemberScreenModelTest.kt` prueba los 7 valores previos de
`DonateErrorReason` uno a uno pero NO el 8º (`UNCERTAIN`, añadido por
a622bcd) — hueco de menor esfuerzo/mayor retorno del informe. Confirma
`appreciateMember` sin ninguna rama AMBIGUOUS ni siquiera a nivel de tipo
(`AppreciateErrorReason` no tiene caso `UNCERTAIN`, a diferencia de
`DonateErrorReason`). SIGUE ABIERTO: `ErrorCategory.kt` sin test (lógica
pura, trivial de testear, cero cobertura); `SecureStore.wasmJs.kt` sin
ningún `wasmJsTest` en el repo; `*Repository.kt` con I/O real 0/8 con
test (sin `MockEngine` de Ktor en el repo); `OverdueSection`/
`isTaskOverdueOverall` sin test; helpers de fecha duplicados sin test;
`PointsBadge.gradientBrush` sin test de contraste WCAG automatizado (la
regresión de Naturaleza oscuro se detectó por cálculo manual, no test).
Solo informa, sin cambios aplicados.

**Fixes APLICABLES nuevos identificados en oleada 3:**
8. `PointsBadge.kt:109-111` — memoizar `gradientBrush` con `remember(base)`.
9. `HouseholdScreen.kt:546-553` — memoizar el `Brush.linearGradient` del hero card con `remember`.
10. `FirestoreRepository.kt:1563-1564` — invalidar `taskCache` antes de relanzar en el catch AMBIGUOUS de `redeemReward`.
11. `MemberScreenModel.kt:327-331` — llamar `loadMembers(householdId)` también en el catch genérico de `redeemReward`.

**Confirmado CRÍTICO por 4+ especialistas independientes a través de las 3
oleadas (#2, #5, #8, #12) — se decide APLICAR esta ronda pese a no ser
"mecánico de una línea", por ser el mismo patrón ya validado en
`donatePoints`:**
- `appreciateMember` sin fix AMBIGUOUS→UNCERTAIN.

**Confirmado como bug de accesibilidad WCAG AA (#3) que sigue abierto tras
el propio fix de a622bcd — se decide APLICAR recalibración dirigida por
luminancia relativa en vez de dirección fija hacia negro:**
- `PointsBadge.gradientBrush` — Naturaleza oscuro.

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

### Oleada 2 (completada)

**HALLAZGO ESTRELLA — convergencia de 4 especialistas independientes (#6, #7,
#8, #9): `SecureStore.wasmJs.kt` (fix "cifrado" de a622bcd) tiene DOS bugs
CRÍTICOS NUEVOS:**
1. El AES-256-CTR casero NO es AES real — Seguridad (#9) lo verificó
   ejecutándolo contra el vector de prueba oficial NIST SP800-38A y **falló**;
   Programador senior (#6) localizó 3 bugs independientes en key-schedule
   (Rcon combinado con OR en vez de XOR por precedencia de operadores JS),
   ShiftRows (término que se anula a 0 por construcción) y MixColumns (byte
   XOR espurio sin correspondencia en la matriz FIPS-197). Es autoconsistente
   (roundtrip put/get funciona) pero la resistencia criptográfica real es
   desconocida — probablemente muy inferior a AES genuino. Sin ningún test
   (no existe `wasmJsTest` en el repo).
2. `ephemeralKey` se regenera en cada carga de módulo/recarga de página, sin
   persistir — `SettingsStore.kt` usa `SecureStore` para el access/refresh
   token de Google Sign-In (sync de Calendar). Tras cualquier recarga en web,
   se intenta descifrar con la clave nueva → basura silenciosa (CTR sin
   autenticación, `TextDecoder` sin `fatal:true` no lanza) tratada como
   token válido no-nulo, en vez de fallar limpio a `null`. Confirmado por
   QA (#8) como regresión de disponibilidad, por Arquitectura (#7) como
   contradicción del contrato de persistencia de `SecureStore`.

**Veredicto de los 4 especialistas: el fix NO mejora la postura de
seguridad de forma neta** (documenta "AES-256" falsamente) **y SÍ introduce
una regresión funcional real** (pérdida/corrupción silenciosa de sesión web
en cada F5). Recomendación unánime: migrar a `SubtleCrypto` nativo
(AES-GCM, async/suspend) — SOLO PROPUESTA, refactor no trivial. Fix
acotado y seguro identificado por #6 y #9: `TextDecoder('utf-8',
{fatal:true})` en `jsAesCtrDecrypt` para que clave-incorrecta lance
excepción → el `catch` Kotlin ya existente devuelve `null` limpio en vez de
basura — candidato a APLICAR esta ronda (no arregla el AES roto ni la
pérdida de persistencia, pero convierte corrupción silenciosa en fallo
limpio y recuperable).

**#6 Programador senior** — Ver hallazgo estrella arriba (bugs AES
verificados línea a línea). Heredados: `TaskDetailContent` ~930 líneas
SIGUE ABIERTO (creció desde ~888); duplicación helpers de fecha
`TaskListScreen`/`CalendarScreen` SIGUE ABIERTO. Resto del diff de a622bcd
(`ErrorCategory`/`FirestoreRepository`/`MemberRepository`) revisión limpia,
`CancellationException` bien relanzada, sin `!!` nuevos.

**#7 Arquitectura** — Ver hallazgo estrella arriba (ángulo arquitectónico:
AES casero embebido en string JS es deuda técnica aislada, no revisable en
diff normal, sin capa de test wasmJs). `FirestoreRepository` 1557→1608
líneas (+51). `TaskScreenModel` sin cambios (1305, sigue sin `key` entre 5
pantallas). `GoogleSignInResultHolder` sentinela `""`/`null` SIGUE ABIERTO,
8 call-sites confirmados incl. Swift.

**#8 QA/bugs** — CRÍTICO SIGUE ABIERTO (mitigado parcialmente): doble-tap
verificado SIN vulnerabilidad (botón se deshabilita correctamente,
`Dispatchers.Main.immediate`), pero **reintento manual tras
`transfer_error_uncertain`/error AMBIGUOUS SÍ puede duplicar puntos** — el
diálogo reactiva el botón "Confirmar" sin ninguna advertencia (agravado por
la clave i18n inexistente) y sin invalidar caché (`MemberRepository.kt:555`
`addMemberPoints` no invalida en el camino AMBIGUOUS, el saldo mostrado
queda "congelado" reforzando la falsa sensación de que no pasó nada).
Confirma (3ª vez, independiente) `transfer_error_uncertain` ausente de
`AppStrings.kt` y `appreciateMember` sin el fix AMBIGUOUS. `extractDocId`
verificado: NO enmascara errores (lanza `IllegalStateException` explícito) —
descartado como hallazgo. Caché sin invalidar en `finally`: 28 call-sites
reales (más que "~17" de v14), mayoría bajo riesgo salvo
`MemberRepository.kt:555`.

**#9 Seguridad** — Ver hallazgo estrella arriba (verificación empírica
contra NIST SP800-38A, la evidencia más fuerte de toda la ronda). Heredados
confirmados SIN cambios: `members/{mid}` auto-edición `totalPoints` sin
tope (`firestore.rules`, CRÍTICO); `isPeerPointsTransfer` sin rate-limit
(CRÍTICO) — ambos NO se editan (fuera de mandato). Verificado: el fix
"no revertir en AMBIGUOUS" es puramente client-side, no amplía ningún
permiso de `firestore.rules`, sin vector nuevo de inflación más allá de los
2 heredados. CSV export YA RESUELTO (escapado correcto contra CWE-1236,
`TaskCsvExporter.kt:59-62`). `PenaltyRules`/etc. huérfanos sin implicación
de seguridad (solo deuda de documentación).

**Fixes APLICABLES identificados en oleada 2:**
5. `SecureStore.wasmJs.kt` `jsAesCtrDecrypt` — `TextDecoder('utf-8', {fatal: true})` para fallar limpio (→`null`) en vez de basura silenciosa ante clave incorrecta tras reload.
6. `MemberRepository.kt` `addMemberPoints` (~línea 548-556) — invalidar caché también en el camino de error AMBIGUOUS (o envolver en `finally`), para no mostrar un saldo "congelado" engañoso.
7. Corregir KDoc desactualizado de `PenaltyRules`/`AssignmentCompletionRules`/`TaskReconciliation` que afirma integración inexistente.

**Confirmado por 3+ especialistas independientes (aplicar con alta
confianza):**
- Clave `transfer_error_uncertain` en `AppStrings.kt` (#2, #5, #8).
- `appreciateMember` sin fix AMBIGUOUS (#2, #5, #8).

**SOLO PROPUESTA (requiere refactor grande, no mecánico esta ronda):**
- Migrar `SecureStore.wasmJs.kt` a `SubtleCrypto` nativo (AES-GCM, suspend) — el fix real del problema criptográfico y de persistencia.
- `PointsBadge.gradientBrush` — Naturaleza oscuro sigue bajo WCAG AA (accesibilidad #3), requiere fórmula consciente del tema con validación visual.

### Oleada 3

(pendiente)
