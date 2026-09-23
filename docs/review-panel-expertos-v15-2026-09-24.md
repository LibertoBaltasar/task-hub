# Panel de expertos v15 — 2026-09-24

**Encargo:** auditoría integral EXHAUSTIVA y NUEVA de Task Hub sobre
HEAD=`a622bcd` (v0.7.46), con panel de 13 especialistas independientes en
paralelo (subagentes), lanzados en 3 oleadas de 5+4+4. Primer paso:
verificación uno a uno de los hallazgos SOLO PROPUESTA de la ronda v14
(`docs/review-panel-expertos-v14-2026-09-20.md`) contra el código real,
distribuida dentro del mandato de cada especialista. Consolidación
priorizada y aplicación de los fixes seguros.

**Alcance nuevo desde v14:** un único commit, `a622bcd` "fix: 3 críticos —
contraste WCAG en gradientes, puntos duplicados por timeout, cifrado
wasmJs". Tocó: `build.gradle.kts` (bump versión), `network/ErrorCategory.kt`
(categoría `AMBIGUOUS` nueva), `network/FirestoreRepository.kt` (no borrar
`redemption` si error ambiguo), `network/MemberRepository.kt` (no revertir
donación si error ambiguo → `DonateErrorReason.UNCERTAIN`),
`ui/components/PointsBadge.kt` (gradiente hacia negro en vez de
blanco/negro), `ui/screens/HouseholdScreen.kt` (hero card gradiente mismo
color), `storage/SecureStore.wasmJs.kt` (cifrado AES-256-CTR con clave
efímera vía Web Crypto, antes `localStorage` plano).

Las tres oleadas se lanzaron y completaron sin cortes de sesión de API.

## Resumen por especialista

| # | Especialista | Hallazgos nuevos relevantes | Aplicados | Solo propuesta |
|---|---|---|---|---|
| 1 | Estética / diseño visual | 2 | 3 (heredados aplicados) | 3 |
| 2 | Funcionalidad end-to-end | 1 crítico nuevo | 2 | 3 |
| 3 | Accesibilidad WCAG AA | 1 crítico (recalibrado) + 1 importante | 2 | 1 |
| 4 | UI / componentes | 2 | 0 | 3 |
| 5 | UX | 0 nuevos (confirma #2) | 0 | 6 (heredados) |
| 6 | Programador senior | 1 hallazgo estrella (AES roto) | 0 | 1 |
| 7 | Jefe de arquitectura | 0 nuevos | 0 | 3 (heredados) |
| 8 | QA / bugs | 1 crítico (reintento manual duplica) | 1 | 0 |
| 9 | Seguridad / AppSec OWASP MASVS | 1 hallazgo estrella (AES roto, verificado NIST) | 1 (mitigación parcial) | 2 (heredados) |
| 10 | Privacidad / RGPD / menores | 1 nuevo (cascada de borrado) | 0 | 4 |
| 11 | Rendimiento | 2 nuevos + 1 confirmado | 2 | 2 |
| 12 | Red / offline / sincronización | 3 nuevos + 1 heredado | 1 | 3 |
| 13 | Cobertura de pruebas | — (informe) | 1 (2 assertions) | — |

**Total: 14 cambios aplicados** (11 fixes de código + 3 correcciones de
KDoc desactualizado), verificados con `compileDebugKotlinAndroid` y
`jvmTest` (273/273 tests, XML real, 0 fallos).

---

## Estado de los hallazgos SOLO PROPUESTA de v14

Verificados contra el código real por el especialista correspondiente:

- **Estética — iOS `AppIcon`/`AccentColor` sin asset real** — SIGUE ABIERTO.
- **Estética — Onboarding con emoji genérico** (`WelcomeScreen.kt`,
  `CreateHouseholdScreen.kt`, `CreateProfileScreen.kt`,
  `JoinHouseholdScreen.kt:121` "🔑") — SIGUE ABIERTO, sin cambios.
- **Estética — `StatsScreen`/`CalendarScreen` con `CircularProgressIndicator`
  genérico** — APLICADO parcialmente: migrados los 2 spinners de página
  completa (`StatsScreen.kt:80`, `CalendarScreen.kt:331`) a `ShimmerList`,
  coherente con las otras 6 pantallas que ya usan ese patrón. Los spinners
  de 16dp dentro de botones de acción (`CalendarScreen.kt:1054,1191`) NO se
  tocaron: tras revisar `TaskDetailScreen.kt` se confirmó que ese es el
  patrón establecido en todo el repo para "acción en curso dentro de un
  botón" (7+ sitios idénticos), no el hallazgo de "spinner genérico de
  carga de página" que señalaba v14 — sustituirlos por shimmer habría sido
  inconsistente con el resto de la app.
- **`EmptyState` compartido vs sitios con emoji** (`HouseholdMemberList.kt:129`)
  — SIGUE ABIERTO, sin cambios.
- **`AssignmentCompletionRules`/`TaskReconciliation`/`PenaltyRules`
  huérfanos** — SIGUE ABIERTO (confirmado sin call-sites de producción,
  0 referencias fuera de sus tests). APLICADO: corregido el KDoc de
  cabecera de los 3 archivos, que afirmaba integración con
  `FirestoreRepository`/`completeAssignment` inexistente desde que esa
  lógica se migró a Cloud Functions (panel v10-v12) — ahora documentan
  explícitamente que están huérfanos.
- **`redeemReward` timeout post-commit** — YA RESUELTO en `a622bcd`
  (categoría `AMBIGUOUS`, no borra el redemption). Verificado íntegro, sin
  regresión.
- **`donatePoints`/`appreciateMember` duplicación de puntos** — `donatePoints`
  YA RESUELTO en `a622bcd`. `appreciateMember` SIGUE ABIERTO → **APLICADO
  esta ronda**: mismo patrón `AMBIGUOUS`→`AppreciateErrorReason.UNCERTAIN`
  (confirmado como el mismo riesgo crítico por 4 especialistas
  independientes a través de las 3 oleadas: #2, #5, #8, #12).
- **UX: login web/desktop sin distinguir fallo/cancelación, timeout desktop
  sin cancelar, CSV sin confirmación, filas sin cuenta vinculada, doble
  interruptor vibración** — SIGUEN ABIERTOS, sin cambios (decisión de
  producto/refactor de contrato, fuera de alcance mecánico).
- **`TaskScreenModel` god object** — sin cambios (1305 líneas).
- **`FirestoreRepository` god object** — creció 1557→1608 líneas (+51, por
  `a622bcd`). SIGUE ABIERTO.
- **`GoogleSignInResultHolder` sentinela `""`/`null`** — SIGUE ABIERTO,
  8 call-sites confirmados incl. Swift.
- **Caché sin invalidar en `finally`** — recontado en 28 call-sites (más que
  "~17" de v14). El más crítico (`MemberRepository.addMemberPoints`, camino
  AMBIGUOUS) → **APLICADO** esta ronda (ver abajo). El resto SIGUE ABIERTO.
- **`members/{mid}` auto-edición `totalPoints` sin tope / `isPeerPointsTransfer`
  sin rate-limit** (`firestore.rules`) — SIGUEN ABIERTOS, NO editados (fuera
  de mandato).
- **UMP/CMP AdMob ausente** — SIGUE ABIERTO (heredado desde v3).
- **Concurrencia optimista ausente en `updateTask`/`updateSubtasks`** — SIGUE
  ABIERTO, verificado sin cambios.
- **Duplicación `formatFriendlyDate` entre pantallas** — SIGUE ABIERTO.
- **`isTaskOverdueOverall` no cubre recurrentes vencidas** — SIGUE ABIERTO.
- **Vibración duplicada (SettingsSheet)** — SIGUE ABIERTO, decisión de
  producto pendiente.
- **Gradiente hero card / `PointsBadge` sin `remember`** — **APLICADO** esta
  ronda (ver Rendimiento #11).
- **Huecos de test** (`ErrorCategory.kt`, `MemberRepository.addMemberPoints`,
  `OverdueSection`, `formatFriendlyDate`, sin `MockEngine` de Ktor) — SIGUEN
  ABIERTOS. **APLICADO parcialmente**: 2 assertions nuevas para el caso
  `UNCERTAIN` en `MemberScreenModelTest.kt` (el hueco de menor
  esfuerzo/mayor retorno identificado por #13).

---

## #1 Estética y diseño visual

**[MENOR, APLICADO]** `WelcomeScreen.kt:186` mostraba `v0.7.44` pese a que
`build.gradle.kts` ya estaba en `0.7.46` — 3ª vez que este literal se
desincroniza tras un bump de versión (regresión recurrente). **Fix
aplicado:** actualizado a `v0.7.46`.

**[MENOR, APLICADO]** `StatsScreen.kt`/`CalendarScreen.kt` usaban
`CircularProgressIndicator` genérico para la carga de página completa
mientras 6 pantallas ya usan `ShimmerList`. **Fix aplicado:** migrados
ambos a `ShimmerList`, ver detalle en "Estado de hallazgos v14" arriba.

**[MENOR, SOLO PROPUESTA]** Los 2 fixes de contraste de `a622bcd`
(`PointsBadge`/hero card) son correctos pero sacrificaban casi todo el
efecto visual de degradado. Con la recalibración de accesibilidad #3
aplicada esta ronda (dirección de lerp por luminancia relativa) el efecto
visual se preserva mejor en la mayoría de temas — no se tocó más allá de
eso; una recalibración estética adicional queda como propuesta no
urgente.

**[SIGUE ABIERTO, PROPUESTA]** iOS `AppIcon`/`AccentColor` sin asset real;
onboarding con emoji genérico (4 sitios); `HouseholdMemberList.kt:129`
emoji sin ilustración compartida.

---

## #2 Funcionalidad end-to-end

**[CRÍTICO, NUEVO, APLICADO]** La clave i18n `transfer_error_uncertain`
(necesaria por el nuevo `DonateErrorReason.UNCERTAIN` de `a622bcd`) no
existía en `AppStrings.kt` — el usuario veía la clave sin traducir en
pantalla tras un timeout de donación. Confirmado independientemente por 3
especialistas (#2, #5, #8). **Fix aplicado:** añadida en ES/EN.

**[CRÍTICO, SIGUE ABIERTO → APLICADO]** `appreciateMember`
(`MemberRepository.kt`) no había recibido el fix `AMBIGUOUS`→`UNCERTAIN`
que sí tienen `donatePoints`/`redeemReward`: un timeout tras acreditar al
receptor devolvía `TRANSFER_FAILED` ("tus puntos no se han visto
afectados"), mensaje potencialmente falso que invita a reintentar y
duplicar puntos. Confirmado por 4 especialistas independientes (#2, #5,
#8, #12). **Fix aplicado:** nuevo caso `AppreciateErrorReason.UNCERTAIN`,
mismo patrón que `donatePoints` (`MemberRepository.kt`, función
`appreciateMember`) + clave `appreciateErrorKey` actualizada
(`MemberScreenModel.kt`).

**[SIGUE ABIERTO, PROPUESTA]** `redeemReward` limbo de `redemption`
huérfano sin mecanismo de reconciliación automática (ver Red/offline #12,
hallazgo 1 — causa raíz). `AssignmentCompletionRules` et al. huérfanos
(KDoc corregido, ver arriba). `isTaskOverdueOverall` sin cubrir recurrentes
vencidas.

---

## #3 Accesibilidad WCAG AA

**[CRÍTICO, SIGUE ABIERTO parcial → APLICADO]** El fix de
`PointsBadge.gradientBrush` en `a622bcd` arreglaba 5/6 combinaciones
tema×modo pero rompía **Naturaleza oscuro** (3.53:1/4.38:1 < 4.5:1): ese
tema tiene `onTertiary` (texto) más OSCURO que `tertiary` (fondo) —
`Green200`/`Green900` — al revés que el resto de temas, así que oscurecer
further el degradado reducía el contraste en vez de aumentarlo. **Fix
aplicado:** la dirección del `lerp` ahora depende de la luminancia
relativa de `content` frente a `base` (`Color.luminance()`): si el texto es
más claro que el fondo, se oscurece further; si es más oscuro (el caso de
Naturaleza oscuro), se aclara further — en ambos casos el degradado se
aleja de la luminancia del texto. Verificado numéricamente (fórmula WCAG
oficial) contra las 6 combinaciones tema×modo reales del repo: todas
≥4.84:1, con margen sobrado (antes 3.53:1 en el caso que fallaba).

**[IMPORTANTE, NUEVO, APLICADO]** `AnimatedCounter.kt` usaba
`shouldReduceMotion()` en vez de `effectsEnabled(EffectCategory.ANIMATIONS)`,
así que no respetaba el interruptor "Modo simple" (que sí desactiva el
resto de animaciones "delight" en 5 sitios). **Fix aplicado:** cambiado a
`!effectsEnabled(EffectCategory.ANIMATIONS)`.

**[SIGUE ABIERTO, PROPUESTA]** "Modo simple" solo gatea animaciones en
5/22 archivos que llaman a motion — 17 restantes usan `shouldReduceMotion()`
suelto (alcance multi-archivo, no mecánico esta ronda).

---

## #4 UI y componentes

**[IMPORTANTE, NUEVO, SOLO PROPUESTA]** `SplashScreen.kt` ignora el tema
del usuario (Teal/Coral fijos, literal, en los 3 temas) — requiere resolver
colores por tema, no mecánico de una línea.

**[IMPORTANTE, NUEVO, SOLO PROPUESTA]** "Modo simple" descrito como
"Desactiva animaciones, efectos visuales y vibración" pero solo cubre
5/22 sitios de motion (ver #3).

**[SIGUE ABIERTO, PROPUESTA]** `HouseholdMemberList.kt:129` emoji sin
ilustración; wrapper `EmptyState` duplicado en 5 sitios sin extraer a
componente compartido.

---

## #5 UX

Confirma independientemente (sin hallazgos propios nuevos) los 2 CRÍTICOS
de #2 (`transfer_error_uncertain` ausente, `appreciateMember` sin fix
AMBIGUOUS) — ambos APLICADOS. Los 6 hallazgos heredados de v13/v14 (login
web/desktop sin distinguir fallo/cancelación, timeout desktop sin cancelar,
CSV sin confirmación, filas sin cuenta vinculada, doble interruptor
vibración) confirmados SIGUE ABIERTO, todos SOLO PROPUESTA (decisión de
producto o refactor de contrato).

---

## #6 Programador senior

**[CRÍTICO, NUEVO — hallazgo estrella, ver #9]** `SecureStore.wasmJs.kt`
implementa AES-256-CTR "a mano" en una función JS embebida
(`@JsFun`), y contiene 3 bugs independientes localizados línea a línea:
key-schedule (`Rcon` combinado con `OR` en vez de `XOR` por precedencia de
operadores JS), `ShiftRows` (un término se anula a 0 por construcción) y
`MixColumns` (byte XOR espurio sin correspondencia en la matriz FIPS-197).
Es autoconsistente (roundtrip put/get funciona) pero la resistencia
criptográfica real es desconocida — probablemente muy inferior a AES
genuino. Sin ningún test (`wasmJsTest` no existe en el repo). Ver
veredicto conjunto en #9.

**[SIGUE ABIERTO, PROPUESTA]** `TaskDetailContent` ~930 líneas; duplicación
de helpers de fecha `TaskListScreen`/`CalendarScreen`. Resto del diff de
`a622bcd` (`ErrorCategory`/`FirestoreRepository`/`MemberRepository`):
revisión limpia, `CancellationException` bien relanzada, sin `!!` nuevos.

---

## #7 Jefe de arquitectura

Sin hallazgos nuevos de arquitectura en el diff (`a622bcd` es
client-side, no amplía superficie de reglas). Confirma el ángulo
arquitectónico del hallazgo estrella (#6/#9): AES casero embebido en
string JS es deuda técnica aislada, no revisable en diff normal, sin capa
de test wasmJs. `FirestoreRepository` 1557→1608 líneas (+51).
`TaskScreenModel` sin cambios (1305 líneas, sin `key` entre 5 pantallas).
`GoogleSignInResultHolder` sentinela `""`/`null` SIGUE ABIERTO (8
call-sites, incl. Swift) — todos SOLO PROPUESTA (refactor grande).

---

## #8 QA y bugs

**[CRÍTICO, NUEVO, APLICADO]** Doble-tap verificado SIN vulnerabilidad
(botón se deshabilita correctamente). Pero **el reintento manual tras
`transfer_error_uncertain`/error AMBIGUOUS SÍ puede duplicar puntos**: el
diálogo reactiva el botón "Confirmar" sin advertencia, y sin invalidar
caché (`MemberRepository.kt`, `addMemberPoints`), el saldo mostrado queda
"congelado" reforzando la falsa sensación de que la operación no tuvo
efecto. **Fix aplicado:** `addMemberPoints` invalida ahora `taskCache`
también en el camino AMBIGUOUS (antes de relanzar), y
`MemberScreenModel.redeemReward` recarga miembros también en su catch
genérico (no solo en éxito) — cubre transitivamente `donatePoints`,
`appreciateMember` y `redeemReward`, que llaman todos a `addMemberPoints`.

Confirmado (3ª vez, independiente): `transfer_error_uncertain` ausente y
`appreciateMember` sin fix AMBIGUOUS — ambos ya APLICADOS. `extractDocId`
verificado: NO enmascara errores (lanza `IllegalStateException` explícito),
descartado como hallazgo. Caché sin invalidar en `finally`: 28 call-sites
reales confirmados (más que "~17" de v14), el más crítico ya corregido.

---

## #9 Seguridad / AppSec OWASP MASVS

**[CRÍTICO, NUEVO — hallazgo estrella, verificación empírica más fuerte de
la ronda, mitigado parcialmente]** El AES-256-CTR casero de
`SecureStore.wasmJs.kt` (fix "cifrado" de `a622bcd`) se ejecutó contra el
vector de prueba oficial NIST SP800-38A y **falló** — no es AES real pese a
documentarse como tal. Además, `ephemeralKey` se regenera en cada carga de
módulo/recarga de página sin persistir: tras cualquier recarga en web, se
intenta descifrar el token de Google Sign-In (usado por
`SettingsStore`/sync de Calendar) con la clave nueva → basura silenciosa
(CTR sin autenticación, `TextDecoder` sin `fatal:true` no lanza) tratada
como token válido no-nulo en vez de fallar limpio a `null`.

**Veredicto conjunto de 4 especialistas (#6, #7, #8, #9): el fix de
`a622bcd` NO mejora la postura de seguridad de forma neta y SÍ introduce
una regresión funcional real (pérdida/corrupción silenciosa de sesión web
en cada F5).**

**Fix APLICADO esta ronda (mitigación parcial, no arregla el AES roto ni la
pérdida de persistencia):** `jsAesCtrDecrypt` ahora usa
`TextDecoder('utf-8', {fatal: true})`, así que una clave incorrecta lanza
en vez de devolver basura decodificada — el `catch` de Kotlin en
`getString()` ya existente la convierte en `null` limpio, recuperable
(re-login), en vez de un token corrupto tratado como válido.

**[SIGUE ABIERTO, PROPUESTA, refactor grande]** Migrar
`SecureStore.wasmJs.kt` a `SubtleCrypto` nativo (AES-GCM, async/suspend) —
el fix real del problema criptográfico y de persistencia de sesión; fuera
de alcance mecánico de esta ronda.

**[SIGUE ABIERTO, PROPUESTA, `firestore.rules`, NO editado]**
`members/{mid}` auto-edición `totalPoints` sin tope; `isPeerPointsTransfer`
sin rate-limit — ambos heredados, sin cambios de superficie por el fix de
`a622bcd` (puramente client-side). CSV export YA RESUELTO
(`TaskCsvExporter.kt:59-62`, escapado correcto contra CWE-1236).

---

## #10 Privacidad / RGPD / menores

**[IMPORTANTE, NUEVO, SOLO PROPUESTA]** La cascada de "eliminar cuenta"
(`GoogleAuthManager.deleteAccount()`) itera SOLO
`householdStore.getSavedHouseholds()` (caché local), que depende de
`syncHouseholdsToCloud()` — documentado como "aditivo, tolerante a fallos,
nunca poda" — para reflejar la membresía real del usuario. Si esa sync
nunca completó (carrera ya conocida en `reconcileHouseholds`), un hogar
queda invisible para el borrado y el UID del usuario permanece
indefinidamente en `taskHistory`/`rewardRedemptions`/chat de ese hogar —
incumple RGPD art. 17 pese a lo que promete `privacy.html` §6. Requiere
resolver membresía desde el servidor en vez de caché local: refactor de
arquitectura, no aplicado esta ronda.

**[MENOR, NUEVO, SOLO PROPUESTA — condicionado a decisión de producto]**
El interstitial de AdMob se muestra a perfiles `role="child"` sin ninguna
comprobación (`TaskScreenModel.kt:600`), pese a que `guia-publicacion.md`
dice que no debería — pero la "vista simplificada infantil" que ese
checklist referencia nunca se construyó, así que el ítem del checklist
podría estar obsoleto. No aplicado sin confirmación de que el requisito
sigue vigente.

**[SIGUE ABIERTO, PROPUESTA, legal/producto]** UMP/CMP de AdMob ausente
(heredado desde v3); scope OAuth de Google Calendar más amplio del
necesario (requiere re-consentimiento); gating de edad ausente (decisión
de producto ya tomada, "Todas las edades").

**Verificado SIN hallazgo:** Analytics sin PII (4 eventos, params vacíos),
Crashlytics real, purga de retención 90 días, anonimización al
salir/expulsar de un hogar (fix de v14, íntegro).

---

## #11 Rendimiento

**[MENOR, SIGUE ABIERTO → APLICADO, AGRAVADO]** Ni `PointsBadge.gradientBrush()`
ni el `Brush.linearGradient` del hero card de `HouseholdScreen.kt` estaban
memoizados con `remember` — `a622bcd` solo cambió los colores, no la
memoización. Agravante encontrado esta ronda: `HouseholdScreen` lee
`newMessageText` como `StateFlow` en el nivel superior de `Content()` (833
líneas), así que cada tecla escrita en el chat del hogar recompone toda la
pantalla, incluidos ambos gradientes inline. **Fix aplicado:** ambos
`Brush` envueltos en `remember(...)` con las claves de color relevantes.

**[SOLO PROPUESTA, no mecánico esta ronda]** Mover `newMessageText` de
`StateFlow` de ScreenModel a estado local `remember`, o extraer el chat a
su propio `@Composable`, para que escribir un mensaje no recomponga toda
`HouseholdScreen` — el gradiente era solo el síntoma más visible de un
problema de arquitectura de recomposición más amplio.

**[IMPORTANTE, NUEVO, SOLO PROPUESTA]** Poll de "no leídas" cada 30s trae
hasta 300 documentos completos sin atajo de caché en el camino feliz
(`HouseholdScreen.kt:210-219`, `NotificationRepository.kt:25,134-151`) — el
doble de frecuencia y mayor límite que el poll de chat de la misma
pantalla (ya mitigado en panel anterior a 60s). Requiere subir intervalo,
agregación server-side o contador denormalizado — no mecánico.

**[IMPORTANTE, NUEVO, SOLO PROPUESTA]** Splash de 1.5s fijo
(`SplashScreen.kt:58-61`) no se solapa con el bootstrap real de
Koin/auth/household (`App.kt:88-104`), que arranca solo después — tiempo
percibido de arranque en frío mayor de lo necesario. Requiere reordenar el
flujo de arranque, no mecánico.

**Verificado SIN hallazgo:** `AnimatedCounter`/`ConfettiOverlay` bien
memoizados; `groupTasksByStatus`/`tasksByDate` con `remember` correcto;
`NotificationPollWorker` ya optimizado; tamaño de build sano
(`material-icons-core` únicamente).

---

## #12 Red / offline / sincronización

**[CRÍTICO, NUEVO — causa raíz del hallazgo de QA #8, SOLO PROPUESTA]** El
fix `AMBIGUOUS` de `a622bcd` camufla el doble-gasto, no lo resuelve: no
existe ningún mecanismo de reconciliación posterior para
donar/canjear/agradecer (`TaskReconciliation` existe pero está confirmado
desconectado de este flujo). Contrasta con `completeRecurringTask`, que SÍ
está resuelto de raíz vía Cloud Function transaccional con precondición
server-side — un reintento no puede duplicar puntos ahí porque el servidor
rechaza la segunda escritura lógica (`FAILED_PRECONDITION`/`ABORTED`).
Migrar donar/canjear/agradecer al mismo patrón es el fix estructural real,
pero es un refactor grande (Cloud Function nueva), fuera de alcance
mecánico de esta ronda.

**[IMPORTANTE, NUEVO, APLICADO]** `redeemReward` reproducía el mismo bug de
caché "congelada" que #8 encontró en `addMemberPoints`, en un call-site
que ese fix por sí solo no cubría. **Fix aplicado:** ver #8 arriba —
`addMemberPoints` ahora invalida caché en su propio catch AMBIGUOUS
(cubre a `redeemReward` transitivamente, porque `redeemReward` llama
directamente a `addMemberPoints`), y `MemberScreenModel.redeemReward`
recarga miembros en cualquier error.

**[IMPORTANTE, NUEVO, SOLO PROPUESTA]** `isOnline()` casi sin usar (1 solo
call-site en todo el repo, `TaskScreenModel.kt:263`); ninguna operación de
puntos comprueba conectividad antes de intentar, así que un usuario
claramente offline espera el timeout completo (30s) para llegar al mismo
estado "incierto" que un timeout genuino — un chequeo previo reduciría la
frecuencia de casos ambiguos reales. Requiere gatear varias pantallas, no
mecánico.

**[SIGUE ABIERTO, PROPUESTA]** Concurrencia optimista ausente en
`updateTask`/`updateSubtasks` (`TaskRepository.kt:705-710,746-751`),
verificado sin cambios desde v14 — dos dispositivos editando la misma
tarea a la vez producen "el último que escribe gana" sin aviso.

---

## #13 Cobertura de pruebas

273 tests sin cambios desde v14 — `a622bcd` no añadió ninguna línea de test
pese a tocar `ErrorCategory.kt` y `SecureStore.wasmJs.kt` (ambos ya
señalados como huecos CRÍTICOS en v14). Patrón estructural confirmado: la
lógica de dominio pura (`*Rules.kt`, 10 archivos) está bien testeada; la
capa de orquestación con I/O real (`*Repository.kt`, 0/8 con test) tiene
0% de cobertura, por ausencia total de `MockEngine` de Ktor en el repo.

**[APLICADO]** `MemberScreenModelTest.kt` probaba los 7 valores previos de
`DonateErrorReason` y 4 de `AppreciateErrorReason` uno a uno pero NO el
caso `UNCERTAIN` (nuevo en `a622bcd`/esta ronda) — hueco de menor
esfuerzo/mayor retorno. Añadidas 2 assertions (`donateErrorKey`/
`appreciateErrorKey` con `UNCERTAIN`).

**[SIGUE ABIERTO, solo informa, sin cambios]**, priorizado:
- Riesgo ALTO: `SecureStore.wasmJs.kt` sin ningún `wasmJsTest` (0%
  cobertura del AES roto verificado por #6/#9); `appreciateMember`/
  `donatePoints`/`redeemReward` sin test de integración del camino
  AMBIGUOUS (sin `MockEngine` de Ktor); `ErrorCategory.kt` sin ningún test
  (lógica pura, trivial de testear).
- Riesgo MEDIO: `PointsBadge.gradientBrush` sin test de contraste WCAG
  automatizado (la regresión de Naturaleza oscuro se detectó por cálculo
  manual, dos veces bajo fórmulas distintas); `OverdueSection`/
  `isTaskOverdueOverall` sin test; helpers de fecha duplicados sin test;
  `HouseholdStore.kt` y 7 `ScreenModel`/managers sin ningún test.
- Riesgo BAJO: `AssignmentCompletionRules`/`PenaltyRules`/
  `TaskReconciliation` SÍ tienen test unitario pese a estar huérfanos en
  producción — cobertura de código muerto, no un hueco real.

---

## Fixes aplicados esta ronda (14)

1. `ui/i18n/AppStrings.kt` — clave `transfer_error_uncertain` ES/EN.
2. `ui/screens/WelcomeScreen.kt:186` — versión sincronizada a v0.7.46.
3. `ui/screens/StatsScreen.kt` — `CircularProgressIndicator` de página
   completa → `ShimmerList`.
4. `ui/screens/CalendarScreen.kt` — ídem (solo el spinner de página
   completa; los de botón se mantienen, patrón establecido en el repo).
5. `ui/components/AnimatedCounter.kt` — `shouldReduceMotion()` →
   `!effectsEnabled(EffectCategory.ANIMATIONS)` (respeta "Modo simple").
6. `network/MemberRepository.kt` — `appreciateMember`: nuevo
   `AppreciateErrorReason.UNCERTAIN`, mismo patrón AMBIGUOUS que
   `donatePoints`.
7. `ui/models/MemberScreenModel.kt` — `appreciateErrorKey` cubre
   `UNCERTAIN`.
8. `network/MemberRepository.kt` — `addMemberPoints` invalida caché en el
   camino AMBIGUOUS antes de relanzar (cubre transitivamente
   `donatePoints`/`appreciateMember`/`redeemReward`).
9. `ui/models/MemberScreenModel.kt` — `redeemReward` recarga miembros en
   cualquier error, no solo en éxito.
10. `ui/components/PointsBadge.kt` — `gradientBrush` recalibrado por
    luminancia relativa (arregla WCAG AA en Naturaleza oscuro) + memoizado
    con `remember`.
11. `ui/screens/HouseholdScreen.kt` — hero card gradient memoizado con
    `remember`.
12. `storage/SecureStore.wasmJs.kt` — `TextDecoder('utf-8', {fatal: true})`
    para fallar limpio ante clave incorrecta tras reload.
13. `network/AssignmentCompletionRules.kt`, `network/PenaltyRules.kt`,
    `network/TaskReconciliation.kt` — KDoc corregido (ya no afirman
    integración inexistente).
14. `commonTest/.../MemberScreenModelTest.kt` — 2 assertions nuevas para
    `UNCERTAIN`.

## Propuestas pendientes (no aplicadas, requieren decisión/refactor mayor)

- Migrar `donatePoints`/`appreciateMember`/`redeemReward` a Cloud Functions
  transaccionales (el fix estructural real del doble-gasto por reintento
  manual).
- Migrar `SecureStore.wasmJs.kt` a `SubtleCrypto` nativo (AES-GCM).
- Resolver membresía de hogares desde servidor (no caché local) en
  `GoogleAuthManager.deleteAccount()`.
- Concurrencia optimista en `updateTask`/`updateSubtasks`.
- UMP/CMP de AdMob; scope OAuth de Calendar; gating de edad (legal/producto).
- Mover `newMessageText` fuera del `StateFlow` de nivel superior en
  `HouseholdScreen`; poll de notificaciones (30s/300 docs); reordenar
  arranque para solapar splash con bootstrap real.
- `isOnline()` antes de operaciones de puntos.
- `firestore.rules`: tope de `totalPoints`, rate-limit de
  `isPeerPointsTransfer` (documentado, NO editado).
- Resto de hallazgos heredados de v13/v14 sin cambios (UX de
  errores/cancelación, iOS assets, onboarding, `EmptyState` compartido,
  god objects, sentinela de `GoogleSignInResultHolder`, duplicación de
  helpers de fecha, huecos de test de riesgo ALTO/MEDIO).

## Verificación

- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` →
  **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:jvmTest --rerun-tasks --console=plain` → **BUILD
  SUCCESSFUL**; XML real (`composeApp/build/test-results/jvmTest/*.xml`):
  **273 tests, 0 failures, 0 errors, 0 skipped**.
