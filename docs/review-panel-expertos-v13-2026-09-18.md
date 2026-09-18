# Panel de expertos v13 — 2026-09-18

**Encargo:** auditoría integral NUEVA (no continuación) de Task Hub sobre
HEAD=`71c7bce` (v0.7.38), con panel de 13 especialistas independientes en
paralelo, lanzados en 3 oleadas de 5+4+4 (límite estructural de sesiones
simultáneas de la API, verificado en rondas anteriores). Primer paso:
verificación uno a uno de los hallazgos SOLO PROPUESTA/NO APLICADO de la
ronda v12 contra el código real. Consolidación priorizada y aplicación de
los fixes seguros.

**Nota de proceso:** las oleadas 2 y 3 sufrieron cortes por límite de sesión
de la API (mismo tipo de corte que rondas anteriores): oleada 2 completa
(Programador senior, Arquitectura, QA/bugs, Seguridad) falló al lanzarse a
las 15:5x CEST, justo antes del reset de sesión de las 16:00 — reintentada
en bloque inmediatamente después del reset, completó sin problema. El
especialista de Rendimiento (oleada 3) falló individualmente por el mismo
motivo justo antes del reset de las 21:00 — reintentado en solitario tras el
reset, completó sin problema. Ningún hallazgo fue fabricado para cubrir el
corte; se esperó y reintentó tal como exige el proceso.

**Nota de alcance (decisión de producto del dueño, 2026-09-18):** los
efectos de delight (animaciones, transiciones entre pantallas,
celebraciones al completar tarea, háptica) son dirección de producto
aprobada, gestionados aparte por el encargo paralelo de "Modo simple"
(`docs/revision-delight-experiencia-2026-09-18.md`, fuera del alcance de
esta auditoría). Los especialistas de Accesibilidad y UX no los reportaron
como violaciones WCAG/reduce-motion ni propusieron eliminarlos.

## Resumen por especialista

| # | Especialista | Hallazgos nuevos | Aplicados | Solo propuesta |
|---|---|---|---|---|
| 1 | Estética / diseño visual | 3 | 2 | 1 |
| 2 | Funcionalidad end-to-end | 2 | 0 | 2 |
| 3 | Accesibilidad WCAG AA | 3 | 3 | 0 |
| 4 | UI / componentes | 2 | 1 | 1 |
| 5 | UX | 2 | 0 | 2 |
| 6 | Programador senior | 3 | 2 | 1 |
| 7 | Jefe de arquitectura | 2 | 0 | 2 |
| 8 | QA / bugs | 4 | 1 | 3 |
| 9 | Seguridad / AppSec OWASP MASVS | 2 | 0 | 2 |
| 10 | Privacidad / RGPD / menores | 2 | 1 | 1 |
| 11 | Rendimiento | 1 | 1 | 0 |
| 12 | Red / offline / sincronización | 3 | 0 | 3 |
| 13 | Cobertura de pruebas | — (informe) | 0 | — |

**Total: 11 fixes aplicados** (10 identificados por el panel + 1 hallazgo de
seguimiento propio, la sincronización de versión de `WelcomeScreen.kt`),
verificados con `compileDebugKotlinAndroid`, `jvmTest` (269/269 tests, XML
real) y `wasmJsMainClasses` (varios fixes tocan `commonMain`, verificado
también en el target web por seguridad extra).

---

## Estado de los hallazgos de la ronda v12

Verificados uno a uno contra el código real (no contra el informe anterior)
por el especialista correspondiente antes de lanzar el resto del panel:

- **Estética — iOS `AppIcon`/`AccentColor` sin asset real** — SIGUE ABIERTO,
  confirmado: `Contents.json` siguen siendo placeholders vacíos de Xcode sin
  ningún PNG.
- **Estética — Onboarding con emoji genérico en vez de `AppLogo`** — SIGUE
  ABIERTO, mismas 3 pantallas (`WelcomeScreen.kt:88`,
  `CreateHouseholdScreen.kt:76`, `CreateProfileScreen.kt:112`).
- **Funcionalidad — Snackbar tras `shareText()`** — sin cambios, sigue
  siendo una decisión de producto pendiente, no un fix mecánico.
- **Funcionalidad — `inviteCode` CSPRNG en web** — YA RESUELTO, sigue
  aplicado sin regresión.
- **Accesibilidad — `<html lang="es">` fijo en `index.html`** — SIGUE
  ABIERTO, sin cambios.
- **Accesibilidad — semántica Compose-canvas wasmJs sin verificar** — SIGUE
  ABIERTO (informativo, no accionable desde código).
- **Accesibilidad — `TaskDetailScreen` selector sin `selectableGroup()`** —
  confirmado y AMPLIADO: el mismo patrón afecta también a 3 grupos de radio
  en `SettingsSheet.kt` (tema, idioma, tema del widget) que v12 no había
  detectado. **APLICADO** esta ronda en los 4 sitios.
- **Accesibilidad — medallas `RankingScreen` sin `contentDescription`** —
  confirmado pero mitigado (el ordinal "1º"/"2º"/"3º" en texto plano ya
  transmite la misma información) — **APLICADO** de todas formas (se oculta
  el emoji a lectores de pantalla para evitar doble lectura).
- **UI/componentes — ilustraciones de estado vacío con emoji** — SIGUE
  ABIERTO, ampliado: además de `RankingScreen`/`HouseholdMemberList` (ya
  conocidos), también `RewardListScreen`/`NotificationListScreen` tienen el
  mismo patrón — 4 sitios en total, todos SOLO PROPUESTA (requiere diseñar
  ilustraciones nuevas).
- **UX — Web login sin diferenciar causa de fallo** — PARCIALMENTE
  RESUELTO/reencuadrado: la web ya soporta login real (commit `8302d99`,
  posterior a v12), pero el problema de fondo (no distinguir "bloqueado" de
  "cancelado") persiste bajo una forma nueva.
- **UX — Desktop confunde fallo de red con cancelación** — SIGUE ABIERTO; el
  KDoc ya no contradice el comportamiento (v12 lo corrigió), pero el
  comportamiento en sí sigue sin distinguir los casos.
- **UX — Login desktop sin botón cancelar en 5 min** — SIGUE ABIERTO, sin
  cambios.
- **UX — Compartir/CSV desktop sin confirmación** — SIGUE ABIERTO, sin
  cambios.
- **Programador senior — sin tests para funciones OAuth puras** — SIGUE
  ABIERTO, sin cambios.
- **Programador senior — `material-icons-core` 1.7.3** — confirmado sin
  regresión (nadie volvió a intentar el bump a 1.8.0).
- **Arquitectura — `SecureStore` wasmJs sin cifrado real (CRÍTICO)** — SIGUE
  ABIERTO, sin cambios; sigue siendo el hallazgo de seguridad/arquitectura
  más serio pendiente del repo.
- **Arquitectura — capacidad Google Sign-In duplicada por plataforma** —
  matizado: las 4 plataformas ya implementan sign-in real (mejora desde
  v12), pero el contrato de sentinela `""`/`null` de
  `GoogleSignInResultHolder` sigue reimplementado 5 veces sin tipo sellado.
- **Seguridad — asimetría `firestore.rules` en auto-edición de
  `members/{mid}`** — SIGUE ABIERTO y es MÁS AMPLIO de lo que describía v12:
  permite subir `totalPoints` propio directamente vía REST, no solo bordear
  el presupuesto de agradecer. Ya documentado como riesgo aceptado en la
  cabecera del propio `firestore.rules` desde v9 ("cero usuarios reales") —
  premisa que merece revalidarse, no una sorpresa nueva.
- **Seguridad — API key pública / HTTP referrers en GCP** — confirmado que
  sigue como nota de infraestructura pendiente, no reinvestigado.
- **Privacidad — UMP/CMP de AdMob ausente (CRÍTICO)** — SIGUE ABIERTO, sin
  cambios. Sigue siendo la recomendación de mayor prioridad para el
  propietario antes de activar IDs de producción de AdMob.
- **Privacidad — texto de purga "automática" en `privacy.html`** — YA
  RESUELTO, confirmado sin regresión.
- **Privacidad — checklist UMP en `guia-publicacion.md`** — SIGUE ABIERTO,
  sin cambios.
- **Privacidad — gating de edad** — sin cambios de posicionamiento
  ("Todas las edades"), decisión consciente y coherente en ambos documentos.
- **Privacidad — iOS/desktop/web sin AdMob/Analytics** — confirmado que
  sigue siendo cierto (solo Android en producción).
- **Rendimiento — `uiToolingPreview` eliminado** — YA RESUELTO, confirmado
  sin regresión (cero `@Preview` en el repo).
- **Rendimiento — `SplashScreen` con `delay(1500)` fijo** — SIGUE ABIERTO,
  decisión de marca, sin acción.
- **Rendimiento — bundle web ~17MB** — confirmado sin cambios de conclusión
  (nada evitable por código de la app).
- **Red/offline — `isOnline()`/`isOffline` solo en `TaskScreenModel`** —
  SIGUE ABIERTO, sin cambios.
- **Red/offline — desktop OAuth: red caída = cancelación** — SIGUE ABIERTO,
  sin cambios (mismo hallazgo que UX #2 de arriba desde el ángulo de red).
- **Red/offline — wasmJs `connectTimeoutMillis` sin efecto real** — SIGUE
  ABIERTO, no verificable sin navegador real, sin cambios.

Ningún hallazgo de v12 se dio por resuelto sin verificar el código real
primero. Ninguno de los hallazgos "sigue abierto" es una sorpresa: todos
estaban ya documentados como pendientes en el informe anterior.

---

## Hallazgos nuevos aplicados esta ronda

### Estética

**[APLICADO][CRÍTICO]** `TaskHubTypography` (`ui/theme/Theme.kt:328-335`)
construía 6 roles del type-scale Material3 (`headlineLarge/Medium/Small`,
`titleLarge/Medium`, `labelSmall`) como `TextStyle(...)` sueltos en vez de
`.copy()` sobre una instancia base de `Typography()`. El constructor
`Typography(...)` de Material3 usa el argumento nombrado tal cual cuando se
especifica explícitamente — no lo fusiona con el type-scale por defecto —
así que esos 6 roles quedaban con `fontSize`/`lineHeight` en `Unspecified`,
y Compose los resolvía a 14sp (el fallback absoluto de layout) en vez de sus
tamaños reales (headlineLarge 57sp, titleLarge 22sp, etc.). Afecta a
`TaskHubTopBar` (hereda `titleLarge`) y a los 4 "hero" del alta de hogar que
la propia v12 acababa de retocar. **Fix:** cada rol parte ahora de
`Typography().copy(...)`, preservando el type-scale M3 y sobreescribiendo
solo peso/tracking. Verificado con `compileDebugKotlinAndroid` y
`wasmJsMainClasses`.

**[APLICADO][MENOR]** Versión hardcodeada `WelcomeScreen.kt:170`
desincronizada otra vez (`v0.7.35` mostrado, `0.7.38` real en
`build.gradle.kts:166`) — 3 bumps de atraso, mismo patrón recurrente que
rondas anteriores. Sincronizado el literal. (No es un bump de versión del
build, solo re-sincroniza el texto de la pantalla con la versión ya
publicada.)

**[SOLO PROPUESTA]** `StatsScreen`/`CalendarScreen` siguen con
`CircularProgressIndicator` genérico en su estado de carga, a diferencia de
6 pantallas hermanas que ya usan `ShimmerList` — requiere diseñar la forma
del shimmer para gráfico/calendario, no un fix mecánico.

### Accesibilidad

**[APLICADO][IMPORTANTE]** `TaskListScreen.kt:1003` — regresión del mismo
patrón "alpha sobre color de texto" que v12 corrigió dos veces
(`NotificationListScreen`, `StatsScreen`), reaparecido en un tercer sitio:
`onSurfaceVariant.copy(alpha = 0.7f)` en la fila de metadatos de cada
tarjeta de tarea, 3 de 6 combinaciones tema/modo por debajo de 4.5:1 (DEFAULT
claro 4.01:1, NATURALEZA claro 3.92:1, MINIMAL claro 4.23:1). Alpha
eliminado.

**[APLICADO][IMPORTANTE]** `Modifier.selectableGroup()` ausente en los 4
grupos de radio-botones reales de la app: `TaskDetailScreen.kt:1142`
(selector "quién ha hecho la tarea") y `SettingsSheet.kt` (tema, idioma,
tema del widget — 3 usos). Sin él, un lector de pantalla no anuncia "N de M"
del grupo. Añadido en los 4 sitios.

**[APLICADO][MENOR]** `RankingScreen.kt` — el ordinal "1º"/"2º"/"3º" en
texto plano ya transmite la misma información que el emoji de medalla
🥇🥈🥉; se oculta el emoji al árbol de accesibilidad
(`Modifier.clearAndSetSemantics {}`, solo para posiciones 1-3, dejando
accesible el número plano para posiciones 4+) para evitar doble lectura por
TalkBack/VoiceOver.

### UI/componentes

**[APLICADO][MENOR]** `StatsScreen.kt:451-455` reimplementaba inline
exactamente el mismo patrón que `StatusDot` (`ui/components/StatusDot.kt`,
extraído en v12 para evitar justo este anti-patrón). Sustituido por
`StatusDot(color = ..., size = 12.dp)`; eliminados los imports
`background`/`CircleShape` que quedaron sin uso.

**[SOLO PROPUESTA]** Patrón de "estado vacío" (icono + título + subtítulo)
reimplementado casi idéntico en 6 archivos con deriva de tokens (título
alterna entre `titleLarge`/`titleMedium` sin razón de diseño aparente,
segundo `Spacer` entre 8dp/4dp según el archivo) — candidato a extraer un
`EmptyState` compartido; refactor no trivial, se documenta como propuesta.

### Programador senior

**[APLICADO][IMPORTANTE]** `AndroidSchedulerHolder.scheduler`
(`NotificationScheduler.android.kt:56-58`) sin `@Volatile`, inconsistente
con los otros 2 holders estáticos del mismo paquete que sí lo usan por el
mismo motivo (`AndroidContextHolder`, `AdControllerImpl`) — se escribe una
vez en `MainActivity.onCreate()` y se lee desde
`TaskHubFirebaseMessagingService.onNewToken()` (callback del SDK de FCM, no
garantizado en el mismo hilo) sin sincronización. Añadido `@Volatile`.

**[APLICADO][MENOR]** `!!` innecesario en `TaskListScreen.kt:1020`
(`task.lastCompletedDate!!`) — el smart-cast del compilador ya cubre el
caso (`task` es un `val` local, `lastCompletedDate` un `val` de data class
sin getter custom, sin cruce de lambda entremedias). Eliminado.

**[SOLO PROPUESTA]** `TaskDetailContent` (`TaskDetailScreen.kt:321`) ~888
líneas — candidato a descomponer en sub-composables; deuda de
mantenibilidad, no bug funcional.

### Privacidad

**[APLICADO][MENOR]** Comentario obsoleto en `TaskScreenModel.kt:815`
afirmaba que `loadTaskDetail` "refresca la señalización TFCD de AdMob según
el rol del perfil activo" — esa lógica no existe en ese método ni en ningún
otro sitio de `commonMain`; TFCD se fija una única vez e incondicionalmente
en `TaskHubApplication.kt:55-60` (siempre child-directed, más conservador
que lo que decía el comentario, sin riesgo real de privacidad). Comentario
corregido para no inducir a error a futuros lectores del código.

**[SOLO PROPUESTA][IMPORTANTE]** Borrado de cuenta anonimiza mensajes y
comentarios (`HouseholdRepository.anonymizeMemberMessages`,
`TaskRepository.anonymizeMemberComments`) pero NO
`taskHistory.memberId`/`rewardRedemptions.memberId` en hogares compartidos
(`FirestoreRepository.kt:906-937`) — el UID de Google del miembro borrado
queda permanentemente incrustado en el historial de un hogar compartido.
Mitigado porque la UI actual no resuelve nombres de miembros que ya no
existen, pero inconsistente con la promesa de `privacy.html` de anonimizar
"tu actividad pasada". Requiere extender el mismo patrón de anonimización a
las 2 colecciones restantes — se documenta como propuesta dado el volumen
(no es un one-liner, toca el flujo de borrado de cuenta).

### Rendimiento

**[APLICADO][MENOR]** `CalendarScreen.kt:133` recreaba en cada
recomposición la lambda de traducción `s` y los callbacks
`onDayClick`/`onComplete`/`onTaskClick` pasados a
`WeekView`/`MonthView`/`PendingWithoutDueDateSection`, replicando el mismo
antipatrón que `HouseholdScreen` ya había corregido con `remember`. Cada
interacción que mueve `actionState`/cierra un diálogo forzaba recomposición
de esos composables aunque sus datos no hubieran cambiado. Envueltos en
`remember(appSettings.currentLanguage)`/`remember { }` con el mismo patrón
ya usado en `HouseholdScreen.kt:189`.

---

## Hallazgos nuevos — SOLO PROPUESTA (requieren decisión de producto, backend nuevo, o refactor de volumen)

### Funcionalidad

**[IMPORTANTE]** `AssignmentCompletionRules.kt`/`TaskReconciliation.kt` y
parcialmente `PenaltyRules.kt` han quedado huérfanos tras la migración a
Cloud Functions: no se invocan desde ningún punto de producción de
`commonMain` (solo desde su propio archivo y sus tests), y su KDoc sigue
describiendo una función Kotlin (`FirestoreRepository.reconcileMissingTaskPoints`)
que ya no existe. La lógica real vive portada a `functions/src/*.ts`;
`completeAssignment.ts` reimplementa inline sin test TS dedicado (a
diferencia de `penalty.ts`, que sí es un port disciplinado y verificado
línea a línea). Los 22 tests Kotlin de "reglas puras" que v12 contaba como
cobertura validan un camino que el cliente ya no ejecuta. Requiere decidir:
actualizar KDoc para marcarlos como "especificación de referencia", o
migrar sus tests a Jest sobre el código TS real.

**[CRÍTICO, mismo diagnóstico raíz que QA/bugs #1 de abajo]**
`redeemReward` puede borrar un `redemption` que en realidad sí se cobró si
el PATCH de descuento tiene un timeout ambiguo — mismo patrón que el
CRÍTICO de `donatePoints` documentado más abajo. Requiere el mismo fix
compartido (reclasificar `IOException`/timeout como resultado ambiguo, no
como fallo confirmado).

### UX

**[IMPORTANTE]** Filas de miembro sin cuenta vinculada (perfiles
infantiles, `userId == null`) son visualmente indistinguibles de las filas
navegables pero no hacen nada al pulsarlas (`HouseholdMemberList.kt:266`,
`.clickable(enabled = member.userId != null, ...)` sin ninguna señal
visual diferenciada). Rompe la heurística "lo que parece pulsable,
responde" — caso frecuente en una app familiar, no un borde raro.

**[MENOR]** Spinner de bootstrap post-login (`App.kt:295-304`) sin texto ni
`liveRegion`, encadenando varias llamadas de red secuenciales que pueden
sumar 60-90s en red degradada, sin vía de escape (cerrar sesión) si algo se
atasca.

### Arquitectura

**[IMPORTANTE]** `TaskScreenModel` (1283 líneas) se comparte sin `key` entre
5 pantallas funcionalmente no relacionadas (listado, detalle, alta,
edición, calendario) — decisión consciente y documentada en el propio KDoc
(continuidad de filtros al navegar) pero con coste de acoplamiento
creciente: cualquier cambio en la lógica de alta de tarea obliga a revisar
el mismo archivo que gobierna detalle y calendario. Refactor grande, se
documenta como propuesta.

**[MENOR]** Contrato de sentinela `String?` (`null`=en curso, `""`=fallo)
de `GoogleSignInResultHolder` duplicado sin tipo en 5 call-sites de 3
plataformas — candidato a sealed class (`GoogleSignInOutcome`), no aplicado
por ser un cambio de contrato compartido entre Android/iOS/JVM/wasmJs.

### QA/bugs

**[CRÍTICO]** `donatePoints` (`MemberRepository.kt:757-788`) puede
**duplicar puntos de la nada**: tras debitar al donante, el crédito al
receptor se envuelve en un `catch(Exception)` genérico que, ante CUALQUIER
excepción, revierte el débito asumiendo que el crédito falló. Un timeout de
red lanza `IOException` (no `FirestoreException`, que es lo único que
captura el único `catch` interno de `addMemberPoints`) — si el PATCH de
crédito sí llegó al servidor pero la respuesta se perdió, el rollback
automático deja al donante con sus puntos recuperados Y al receptor con el
crédito ya aplicado, sin ningún error visible que delate la inconsistencia.
Mismo patrón (menos severo, sin rollback automático pero con riesgo de
duplicar en un reintento manual del usuario) en `appreciateMember`. Fix
propuesto por el panel: reclasificar fallos de transporte/timeout como
resultado "ambiguo" (no dispara rollback) en los 3 call-sites afectados
(`donatePoints`, `appreciateMember`, `redeemReward`) — requiere una Cloud
Function idempotente para resolverse de raíz, ya evaluado y aparcado en
`docs/atomicidad-commit-pendiente.md`.

**[MENOR]** Carrera en `addMemberAchievement`
(`MemberRepository.kt:832-865`): si dos logros se desbloquean casi
simultáneamente antes de que exista el documento `achievements/_meta` de un
miembro, ambas lecturas devuelven 404 y ambos PATCH se escriben sin
precondición — el segundo sobrescribe el array del primero sin fusionar, el
primer logro se pierde silenciosamente. Requiere precondición
`exists=false` + reintento ante colisión.

**[MENOR]** `donatePoints`/`appreciateMember` devuelven el nuevo total
calculado desde una lectura previa a la escritura con reintentos, pudiendo
mostrar un número momentáneamente incorrecto en el snackbar de confirmación
— cosmético, la pantalla se recarga con datos frescos justo después.

### Seguridad

**[CRÍTICO]** `isPeerPointsTransfer` (`firestore.rules:372-378`) topa la
magnitud por escritura (+1000) pero no tiene límite de FRECUENCIA — nada
impide repetir la misma escritura de crédito muchas veces por segundo desde
un cliente scriptado, ya que la operación donante↔receptor no es atómica.
Requiere una Cloud Function transaccional con rate-limit (mismo patrón que
`completeAssignment`/`completeRecurringTask`), backend nuevo.

**[IMPORTANTE]** El presupuesto semanal de agradecer (50 pts,
`PointsRules.WEEKLY_APPRECIATION_BUDGET`) es puramente cosmético frente a un
cliente con REST directo: la rama de auto-edición de `members/{mid}`
(`firestore.rules:488-493`) no acota `appreciationGiven`/
`appreciationWeekStart`, solo exige que `role` no cambie. El experto de
seguridad incluyó el cambio de regla exacto propuesto (distinguir "misma
semana" de "nueva semana" con límites explícitos) — no aplicado porque
modificar `firestore.rules` requiere despliegue a infraestructura externa y
pruebas contra el emulador que este entorno no tiene disponibles, mismo
criterio que rondas anteriores.

### Red/offline

**[CRÍTICO, alto volumen — ~20 call sites]** El patrón "invalidar caché en
`finally`" que v12 aplicó solo a `completeTask`/`undoTaskCompletion` (y que
esta ronda se extendió a `completeAssignment`/`reassignTaskCompletion`, ver
aplicados arriba) sigue faltando en el resto de escrituras del repo:
`TaskRepository.kt` (`createTask`, `updateTask`, `updateSubtasks`,
`deleteTask`, `assignTask`, etc.), `MemberRepository.kt` (`createMember`,
`deleteMember`, `updateMemberRole`, `updateMemberStreak`, y — más notable —
**`addMemberPoints` mismo**, la función que v12 citó como el ejemplo del
patrón correcto) y `RewardsRepository.kt`. Mecánico y de bajo riesgo
individualmente, pero de volumen suficiente (~20 sitios) para no aplicarse
de golpe en esta ronda sin una pasada dedicada.

**[IMPORTANTE]** `updateTask`/`updateSubtasks`/`updateAssignmentRotation`
(`TaskRepository.kt`) hacen `PATCH` incondicional sin precondición de
concurrencia optimista, a diferencia de `addMemberPoints`/`appreciateMember`
que sí validan `updateTime` fresco — dos dispositivos editando la misma
tarea a la vez sufren "last write wins" silencioso. Requiere decidir la UX
del conflicto antes de aplicar el mismo patrón.

**[MENOR]** Backoff sin jitter en `retryTransientReadFailure` — ráfaga
sincronizada de reintentos si varios dispositivos del mismo hogar
reconectan a la vez tras un corte de red doméstico. Mejora barata pero de
impacto bajo dado el tamaño típico de un hogar.

---

## Mapa de cobertura de pruebas (informe del especialista #13, sin cambios aplicados)

269 tests en 26 archivos (vs. 257 en v12), incluyendo 2 archivos nuevos
desde v12 que sí cubren el trabajo reciente de calendario/formulario:
`CalendarScreenTest.kt` (6, tareas "once" sin fecha límite) y
`TaskFormSaveInvariantsTest.kt` (6, invariantes de penalización/rotación).

**Huecos priorizados (todos siguen abiertos, código intacto sin diffs desde
v12 salvo lo indicado):**

1. **CRÍTICO** — `MemberRepository.addMemberPoints` con `floor` (reintento
   optimista + revalidación) sin ningún `MockEngine`/test — sigue siendo el
   hueco de mayor riesgo, justo la función en el centro de los hallazgos
   CRÍTICOS de QA/bugs y Red/offline de esta misma ronda.
2. **CRÍTICO** — funciones puras de `GoogleDesktopSignInHelper`
   (`codeChallengeFor`/`buildAuthorizationUrl`/`readCallbackParams`) siguen
   `private`, sin test.
3. **CRÍTICO (nuevo desde v12)** — `network/ErrorCategory.kt`
   (`errorCategory()`/`toUserMessageKey()`), archivo añadido tras v12, cero
   tests pese a ser funciones puras deterministas consumidas por 8
   ScreenModels/managers distintos — un bug de clasificación se propagaría
   silenciosamente a la clasificación de errores de toda la app.
4. **IMPORTANTE** — orquestación de `appreciateMember`/`donatePoints` sin
   test de integración (`FakeFirestoreRepository` ni siquiera stubea esos
   métodos).
5. **IMPORTANTE** — `AchievementChecker.getAchievementsWithStatus` sin test
   directo.
6. **MENOR** — interacción entre `PointsRules.validateDonateBalance`
   (optimista) y la revalidación pesimista de `floor` sin documentar con
   test.

Sigue sin existir ningún `MockEngine`/HTTP falso en todo el repo.

---

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ **BUILD SUCCESSFUL** (solo warnings preexistentes: GoogleSignIn/MasterKey/
EncryptedSharedPreferences deprecados, `when` exhaustivo con `else`
redundante — ninguno de esta ronda).

```
./gradlew :composeApp:jvmTest --rerun-tasks --console=plain
```
→ Gradle reporta BUILD SUCCESSFUL; verificado además contra los XML reales
de `composeApp/build/test-results/jvmTest/*.xml` (25 archivos): **269
tests, 0 failures, 0 errors**.

```
./gradlew :composeApp:wasmJsMainClasses --console=plain
```
→ **BUILD SUCCESSFUL** — verificación extra no exigida por el mínimo de
esta ronda, añadida porque varios fixes tocan `commonMain` ampliamente
(tipografía, accesibilidad) y ese código se comparte con el target web.

## Archivos modificados en esta ronda

- `ui/theme/Theme.kt` — fix crítico de tipografía.
- `ui/screens/TaskListScreen.kt` — alpha de accesibilidad + `!!` innecesario.
- `ui/screens/TaskDetailScreen.kt` — `selectableGroup()`.
- `ui/components/SettingsSheet.kt` — `selectableGroup()` ×3.
- `ui/screens/StatsScreen.kt` — `StatusDot` + limpieza de imports.
- `ui/screens/RankingScreen.kt` — semántica de medallas.
- `platform/NotificationScheduler.android.kt` — `@Volatile`.
- `network/FirestoreRepository.kt` — `finally` en `reassignTaskCompletion`/`completeAssignment`.
- `ui/models/TaskScreenModel.kt` — comentario obsoleto corregido.
- `ui/screens/CalendarScreen.kt` — `remember()` en lambdas/callbacks.
- `ui/screens/WelcomeScreen.kt` — versión mostrada sincronizada.
- `docs/review-panel-expertos-v13-progreso.md` — bitácora de proceso.
- `docs/review-panel-expertos-v13-2026-09-18.md` (este informe).

## Recomendación de prioridad para el propietario

De todo lo dejado como SOLO PROPUESTA, estas merecen atención antes que el
resto:

1. **`donatePoints` puede duplicar puntos de la nada (QA/bugs, CRÍTICO)** —
   a diferencia de casi todo lo demás, este no es un hallazgo teórico: un
   timeout de red real en el momento equivocado ya produce el bug con el
   código actual. Requiere Cloud Function idempotente.
2. **`isPeerPointsTransfer` sin rate-limit (Seguridad, CRÍTICO)** — combinado
   con el punto anterior, la superficie de inflación de puntos vía red
   sigue siendo mayor de lo que rondas anteriores habían acotado.
3. **UMP/CMP de AdMob ausente (Privacidad, CRÍTICO, heredado de v12)** —
   sigue sin resolver; riesgo de suspensión de cuenta AdMob si se activan
   IDs de producción antes de implementarlo.
4. **`SecureStore` wasmJs sin cifrado real (Arquitectura, CRÍTICO, heredado
   de v11/v12)** — sigue siendo el hallazgo de seguridad más antiguo sin
   resolver del repo.

El resto (refactors arquitectónicos, `firestore.rules`, ~20 call-sites de
invalidación de caché, rediseños visuales) son de menor urgencia relativa y
pueden esperar a una ronda dedicada.
