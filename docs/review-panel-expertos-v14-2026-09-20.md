# Panel de expertos v14 — 2026-09-20

**Encargo:** auditoría integral de Task Hub sobre HEAD=`d90ca29` (v0.7.44),
con panel de 13 especialistas independientes en paralelo (subagentes),
lanzados en 3 oleadas de 5+4+4. Primer paso: verificación uno a uno de los
hallazgos SOLO PROPUESTA de la ronda v13 (`9ede2ec`, v0.7.38) contra el
código real — distribuida dentro del mandato de cada especialista en vez de
como paso separado, dado que la mayoría de hallazgos abiertos de v13 ya
estaban referenciados explícitamente en los mandatos de esta ronda.
Consolidación priorizada y aplicación de los fixes seguros.

**Alcance entre v13 y HEAD:** fases 0-4 de "delight" (animaciones,
celebraciones al completar tarea, transiciones entre pantallas, gráficas con
entrada animada, badges hero con gradientes), saludo puntos/racha movido a
cada espacio (`HouseholdScreen`), y — el mismo día de esta auditoría — tres
commits de fixes de fecha/calendario: `a829f7f` (fecha exacta en calendario +
sección Caducadas, fix de vibración duplicada, filtro por fecha), `7ff072a`
(fecha visible en tarjetas + grupo Pendientes en Mías), `d90ca29` (pestaña
Mías por defecto al entrar a Tareas).

Las tres oleadas se lanzaron y completaron sin cortes de sesión de API (a
diferencia de v13).

## Resumen por especialista

| # | Especialista | Hallazgos nuevos relevantes | Aplicados | Solo propuesta |
|---|---|---|---|---|
| 1 | Estética / diseño visual | 2 | 1 | 4 (heredados) |
| 2 | Funcionalidad end-to-end | 2 | 1 (test) | 2 |
| 3 | Accesibilidad WCAG AA | 3 | 1 | 2 |
| 4 | UI / componentes | 1 | 0 | 1 |
| 5 | UX | 1 nuevo + 6 heredados | 0 | 7 |
| 6 | Programador senior | 3 | 1 (test) | 2 |
| 7 | Jefe de arquitectura | 0 nuevos | 0 | 4 (evolución/heredados) |
| 8 | QA / bugs | 2 nuevos + 2 heredados | 2 | 2 |
| 9 | Seguridad / AppSec OWASP MASVS | 0 nuevos | 0 | 2 (heredados) |
| 10 | Privacidad / RGPD / menores | 1 | 1 | 1 |
| 11 | Rendimiento | 4 | 3 | 1 |
| 12 | Red / offline / sincronización | 1 nuevo + 2 heredados | 1 | 2 |
| 13 | Cobertura de pruebas | — (informe) | — | — |

**Total: 12 cambios aplicados** (10 fixes de código + 2 correcciones de
comentarios de documentación), verificados con `compileDebugKotlinAndroid`,
`jvmTest` (273/273 tests, XML real — 269 de v13 + 4 tests nuevos) y
`wasmJsMainClasses`.

---

## Estado de los hallazgos SOLO PROPUESTA de v13

Verificados contra el código real por el especialista correspondiente:

- **Estética — iOS `AppIcon`/`AccentColor` sin asset real** — SIGUE ABIERTO.
- **Estética — Onboarding con emoji genérico** — SIGUE ABIERTO, AMPLIADO:
  además de `WelcomeScreen.kt`/`CreateHouseholdScreen.kt`/
  `CreateProfileScreen.kt`, también `JoinHouseholdScreen.kt:121` (`"🔑"`) sigue
  el mismo patrón (no detectado en v13).
- **Estética — `StatsScreen`/`CalendarScreen` con `CircularProgressIndicator`
  genérico** — SIGUE ABIERTO, sin cambios.
- **UI/componentes — `EmptyState` compartido vs 6 sitios con emoji** —
  EVOLUCIONÓ, mayormente CERRADO: se creó `EmptyStateIllustrations.kt` (5
  ilustraciones Canvas) y se adoptó en 5 de los 6 sitios
  (`RankingScreen`/`RewardListScreen`/`NotificationListScreen`/`TaskListScreen`/`HomeScreen`).
  Único rezagado: `HouseholdMemberList.kt:129` sigue con `Text("👥")` inline
  — SOLO PROPUESTA (no hay ilustración existente que encaje semánticamente,
  requiere diseñar una nueva). El wrapper de layout (`Column` + título +
  subtítulo) tampoco se extrajo como componente compartido — se repite en los
  5 sitios migrados.
- **Funcionalidad — `AssignmentCompletionRules`/`TaskReconciliation`/
  `PenaltyRules` huérfanos** — SIGUE ABIERTO, sin cambios (cero referencias
  fuera de sus propios tests).
- **Funcionalidad — `redeemReward` timeout post-commit (CRÍTICO)** — SIGUE
  ABIERTO, mismo patrón exacto que `donatePoints` (ver abajo).
- **UX — Web login sin distinguir fallo/cancelación** — SIGUE ABIERTO.
- **UX — Desktop confunde fallo de red con cancelación** — SIGUE ABIERTO.
- **UX — Timeout desktop 5 min sin botón cancelar** — SIGUE ABIERTO.
- **UX — Compartir/CSV desktop sin confirmación** — SIGUE ABIERTO.
- **UX — Filas de miembro sin cuenta vinculada indistinguibles** — SIGUE
  ABIERTO.
- **Arquitectura — `TaskScreenModel` god object** — EVOLUCIONÓ: 1283 → 1305
  líneas (+22), sigue compartido sin `key` entre 5 pantallas.
- **Arquitectura — `FirestoreRepository` god object** — EVOLUCIONÓ (leve):
  1545 → 1557 líneas antes de los fixes de esta ronda.
- **Arquitectura — `SecureStore` wasmJs sin cifrado real (CRÍTICO)** — SIGUE
  ABIERTO, sin cambios.
- **Arquitectura — sentinela `""`/`null` de `GoogleSignInResultHolder`** —
  SIGUE ABIERTO, ligeramente peor: 7-8 call-sites (antes "5 veces"), incluido
  Swift (`ContentView.swift:30`).
- **QA/bugs — duplicación de puntos en `donatePoints`/`appreciateMember`
  (CRÍTICO)** — SIGUE ABIERTO, código sin cambios.
- **QA/bugs — caché sin invalidar en `finally`, ~20 call-sites (CRÍTICO)** —
  SIGUE ABIERTO, mismo orden de magnitud (~17 sitios restantes).
- **Seguridad — `members/{mid}` auto-edición de `totalPoints` sin tope
  (CRÍTICO)** — SIGUE ABIERTO. Confirmado: `firestore.rules` sin commits
  desde v10 (`f92aa0b`, 2026-09-12) — ninguna fase delight ni fix de hoy lo
  tocó.
- **Seguridad — `isPeerPointsTransfer` sin rate-limit (CRÍTICO)** — SIGUE
  ABIERTO, mismo motivo.
- **Privacidad — UMP/CMP de AdMob ausente (CRÍTICO)** — SIGUE ABIERTO, sin
  cambios.
- **Privacidad — anonimización de `taskHistory.memberId`/
  `rewardRedemptions.memberId` (IMPORTANTE)** — **APLICADO** esta ronda (ver
  abajo).
- **Red/offline — backoff sin jitter en `retryTransientReadFailure`
  (MENOR)** — **APLICADO** esta ronda.
- **Red/offline — concurrencia optimista en `updateTask`/`updateSubtasks`**
  — SIGUE ABIERTO, confirmado por contraste directo con `addMemberPoints`/
  `appreciateMember` (que sí usan `currentDocument.updateTime`).

---

## Hallazgos nuevos aplicados esta ronda

### Estética

**[APLICADO][MENOR]** `WelcomeScreen.kt:186` — versión hardcodeada
desincronizada otra vez (`v0.7.38` mostrado, `0.7.44` real en
`build.gradle.kts`), 6 bumps de atraso. Mismo patrón recurrente de rondas
anteriores (el propio comentario del código ya avisa de esto). Sincronizado.

### Accesibilidad

**[APLICADO][MENOR]** `CelebrationEffects.kt` (`AnimatedCheckmark`) — el
emoji "✅" quedaba expuesto al árbol de semántica sin marcarse como
decorativo; el estado "completado" ya lo anuncia la Card/checkbox que
dispara la animación, así que TalkBack/VoiceOver lo leía dos veces. Añadido
`Modifier.clearAndSetSemantics {}`, mismo patrón ya usado en las medallas de
`RankingScreen`.

**[CONFIRMADO SIN REGRESIÓN]** El fix de v13 (`onSurfaceVariant.copy(alpha)`
eliminado en `TaskListScreen.kt`) sigue aplicado; la nueva fila de fecha
(commit `7ff072a`) no reintrodujo el patrón. `selectableGroup()` sigue
presente en los 4 sitios de v13, sin grupos nuevos sin agrupar.

**[SOLO PROPUESTA][CRÍTICO, NUEVO]** Gradientes de `PointsBadge.kt`
(interpolación hacia blanco/negro sobre `container`) fallan WCAG AA en 5 de
6 combinaciones tema×modo (hasta 3.28:1 en Minimal, frente al 4.5:1
requerido) — el KDoc del propio archivo razona que interpolar conserva el
matiz y por tanto el contraste, pero eso no es cierto cuando el texto es
claro y el fondo se aclara hacia blanco. Usado hoy en producción
(`TaskListScreen.kt`, badge de puntos de tarea completada). **No se aplicó
un fix numérico** porque requiere recalibrar el offset de interpolación por
tema/modo con verificación visual real (dirección de ajuste opuesta entre
temas claros y oscuros) — ver recomendación de prioridad al final.

**[SOLO PROPUESTA][CRÍTICO, NUEVO]** Hero card de `HouseholdScreen.kt:536-551`
— el degradado `primaryContainer→secondaryContainer` con texto fijo
`onPrimaryContainer` falla WCAG AA en DEFAULT oscuro (~3.9:1, verificado por
cálculo directo). El comentario original afirmaba que el contraste se
mantenía "en TODO el degradado", lo cual es falso para ese caso — **se
corrigió el comentario** para no inducir a error a futuros lectores, pero no
se tocaron los colores (misma razón que el punto anterior).

### UI/componentes

Sin cambios propios aplicables esta ronda — ver estado de v13 arriba
(`HouseholdMemberList.kt:129` sigue rezagado).

### Funcionalidad / Programador senior

**[APLICADO][IMPORTANTE]** `RecurrenceRulesTest.kt` — la rama `"once"` con
`dueDate` (parámetro añadido en los fixes de calendario de hoy) no tenía
ningún test que la ejercitara (los dos tests existentes de `"once"` usaban
el default `dueDate=0`). Añadidos 4 tests: futuro (no debida aún), pasado
(vencida/debida), exactamente hoy (`>=`, debida), y completada con
`dueDate` futuro (la compleción sigue ganando). Confirmado independientemente
por dos especialistas distintos (#2 y #6) antes de aplicarse.

**[CONFIRMADO SIN REGRESIÓN]** `@Volatile` en `AndroidSchedulerHolder` y el
`!!` eliminado en `TaskListScreen.kt` (v13) siguen aplicados; sin `!!`
nuevos ni `catch(Exception)` sin relanzar `CancellationException` en el
código de hoy.

**[SOLO PROPUESTA][IMPORTANTE, NUEVO]** `isTaskOverdueOverall`
(`CalendarScreen.kt`, sección "Caducadas" nueva de hoy) solo detecta
tareas `"once"` vencidas — las tareas recurrentes vencidas (que sí se
marcan correctamente en rojo en `TaskListScreen` vía
`RecurrenceRules.isOverdueOccurrence`) nunca aparecen en "Caducadas".
Inconsistencia de UX entre las dos pantallas para el mismo concepto;
requiere decidir si el calendario debe listar ocurrencias recurrentes
vencidas y reutilizar `isOverdueOccurrence`.

**[SOLO PROPUESTA][MENOR, NUEVO]** `formatFriendlyDate`/
`localizedDayNameAbbr`/`localizedMonthAbbr` (`TaskListScreen.kt`) duplican
casi byte a byte los mismos helpers ya existentes en `CalendarScreen.kt`
(mismas claves i18n `day_abbr_*`/`month_abbr_*`). Candidato a extraer a un
archivo de utilidades de fecha compartido — no aplicado por tocar dos
archivos y varios call-sites, más allá de lo que este encargo cubre como
mecánico.

### QA/bugs — dos bugs nuevos encontrados y corregidos

**[APLICADO][IMPORTANTE][NUEVO]** Carrera en el nuevo default `TaskFilter.MINE`:
`TaskListScreen` puede recibir `memberId=null` si el caller
(`HouseholdScreen.kt:659`, `currentMemberId.ifEmpty { null }`) navega antes
de resolver su propio `currentMemberId` de forma asíncrona. Con "Mías" como
pestaña por defecto (antes "Pendientes", que no depende de `memberId`), este
caso borde dejaba al usuario viendo la lista vacía sin ninguna vía de
recuperación. **Fix:** en `TaskListScreen.kt`, si `memberId` llega `null` se
resuelve como fallback vía `model.resolveCurrentMemberId(householdId)` —
mismo patrón ya usado en `CreateTaskScreen.resolveCreator()`.

**[APLICADO][MENOR][NUEVO]** `TaskScreenModel.reset()` seguía fijando
`_filter.value = TaskFilter.PENDING`, inconsistente con el nuevo default
`MINE` de la inicialización. Hoy es inofensivo (`reset()` es dead code sin
call-sites en producción, confirmado por grep), pero es una trampa latente
si se conecta en el futuro. Sincronizado a `MINE`; test
`TaskScreenModelTest.reset_vuelveTodosLosStateFlowsASusValoresIniciales`
actualizado en consecuencia.

**[CONFIRMADO SIN REGRESIÓN]** El filtro "Mías" NO depende de `dueDate` (solo
de `assignment.memberId`/`status`); la partición de grupos
(overdue/pendingToday/pendingOther/completedToday/completedOther) es
exhaustiva y mutuamente excluyente. `isTaskOverdueOnDay` sigue en uso activo
(no es dead code).

**[SOLO PROPUESTA][MENOR]** El fix de "vibración duplicada" de hoy
(`a829f7f`) solo renombró la etiqueta de ajustes ("Vibración y háptica" →
"Háptica"); la duplicidad real persiste: `SettingsSheet.kt` sigue mostrando
dos interruptores independientes (`settings_vibration`/`fx_haptics`)
combinados con AND — apagar solo uno no tiene el efecto esperado. Decisión
de producto, no mecánico.

### Privacidad

**[APLICADO][IMPORTANTE]** `leaveHousehold`/`deleteMember`
(`FirestoreRepository.kt`) anonimizaban el nombre en mensajes de chat y
comentarios de tarea del miembro que se va, pero dejaban su `memberId` (UID
de Google) en claro para siempre en `taskHistory`/`rewardRedemptions` de
hogares que siguen existiendo para el resto — inconsistente con la promesa
de `privacy.html` de anonimizar "tu actividad pasada" (`deleteHousehold`, que
borra el hogar entero, sí limpiaba ambas colecciones; solo faltaba en salida
individual). **Fix:** dos funciones nuevas siguiendo el mismo patrón exacto
que las de mensajes/comentarios —
`TaskRepository.anonymizeMemberTaskHistory` y
`RewardsRepository.anonymizeMemberRedemptions` — que reescriben `memberId` a
un sentinel (`"deleted_member"`, no colisiona con ningún UID real de Google)
en los registros del miembro saliente. Cableadas en los mismos dos puntos
donde ya se llamaba a `anonymizeMemberMessages`/`anonymizeMemberComments`.
Best-effort, igual que el resto de la cadena de anonimización.

**[CONFIRMADO SIN REGRESIÓN]** Los efectos delight no generan datos
personales nuevos ni requieren consentimiento adicional (componentes
puramente visuales, sin llamadas a Analytics/Firestore). "Modo simple" no
necesita mención en `privacy.html` (control de presentación, no de datos).
UMP/CMP sigue ausente (heredado). Comentario TFCD obsoleto de v13 sigue
corregido.

### Rendimiento

**[APLICADO][IMPORTANTE][NUEVO]** `CalendarScreen.kt` — el antipatrón "lambda
`s` sin `remember`" que v13 corrigió en `Content()` había reaparecido (código
preexistente, no tocado en los commits de hoy) en 3 composables anidados de
mayor multiplicidad: `MonthDayCell` (invocado ~35-42 veces por vista mes),
`DayTasksPopup` y `TaskPopupItem` (dentro de un `LazyColumn`). Envueltos en
`remember(appSettings.currentLanguage)`, mismo patrón que el resto del
archivo.

**[APLICADO][IMPORTANTE][NUEVO]** `overdueTasks` (`CalendarScreen.kt:209`,
sección "Caducadas" de hoy) usaba `remember(listState)` pero la lambda
también lee `today` (que se actualiza cada 60s) — si la pantalla queda
abierta cruzando la medianoche, una tarea recién vencida no se reclasificaba
como caducada hasta el siguiente `loadTasks`. Añadido `today` a la clave del
`remember`.

**[CONFIRMADO SIN REGRESIÓN]** Animaciones/celebraciones/gráficas respetan
`reduceMotion`/`effectsEnabled()` en todos los call-sites; gráficas de
`StatsScreen` cargan lazy dentro de `LazyColumn` (solo animan al entrar en
viewport); `RecurrenceRules` con el nuevo parámetro `dueDate` no añade
llamadas extra al perfil existente.

**[SOLO PROPUESTA][MENOR]** Gradiente de la hero card de `HouseholdScreen.kt`
y `gradientBrush()` de `PointsBadge.kt` no están memoizados con `remember` —
impacto real insignificante hoy (un solo call-site cada uno, fuera de
bucles), documentado para si se generaliza su uso.

### Red/offline

**[APLICADO][MENOR]** `retryTransientReadFailure`
(`FirestoreClient.kt`) — backoff exponencial sin jitter, heredado de v13:
varios dispositivos del mismo hogar reconectando a la vez tras un corte de
red doméstico reintentaban en ráfaga sincronizada. Añadido jitter de ±25%
sobre cada delay (`Random.nextLong`), sin cambiar el resto del contrato
(mismo `maxAttempts`/`initialDelayMillis`, tests existentes sin cambios
necesarios — no aserta timing exacto).

**[CONFIRMADO SIN REGRESIÓN]** `TaskCache` serializa los mismos DTOs que la
red; el filtrado por fecha/calendario es puro sobre `TaskResponse` sin mirar
el origen de los datos — `PendingWithoutDueDateSection`/`OverdueSection`
funcionan igual offline que online. La comparación exacta de día en el
calendario (`date == dueDate`) y la comparación acumulativa de
`RecurrenceRules.isDueOn` (`>=`) son primitivas con propósitos distintos a
propósito (celda de día concreto vs. "pendiente hasta hoy"); no se encontró
divergencia real de resultado final entre lo que el calendario marca
"vencida" y lo que la lista marca lo mismo.

---

## Mapa de cobertura de pruebas (informe del especialista #13)

**273 tests en 25 archivos** (269 de v13 + 4 nuevos de esta ronda, en
`RecurrenceRulesTest.kt`). Confirmado por diff directo (`git diff
71c7bce..d90ca29 -- composeApp/src/commonTest`) que ninguno de los commits
de delight ni de fecha/calendario de hoy había añadido un solo test antes de
esta ronda — dos días de trabajo sobre lógica de fechas/recurrencia (área
históricamente frágil) sin cobertura nueva hasta ahora.

**Huecos priorizados que siguen abiertos:**

1. **CRÍTICO** — `network/ErrorCategory.kt` sigue sin ningún test pese a ser
   lógica de clasificación pura consumida por varios ScreenModels/managers
   — persiste desde v13, sin mitigar.
2. **CRÍTICO** — `MemberRepository.addMemberPoints` (reintento optimista +
   revalidación) sigue sin `MockEngine`/test — el hueco de mayor riesgo,
   justo la función en el centro de los hallazgos CRÍTICOS de QA/bugs y
   Seguridad de esta misma ronda.
3. **IMPORTANTE (nuevo)** — `OverdueSection`/`isTaskOverdueOverall`
   (`CalendarScreen.kt`, funcionalidad nueva de hoy) sin ningún test;
   `CalendarScreenTest.kt` (6 tests) no la referencia.
4. **IMPORTANTE (nuevo)** — `formatFriendlyDate`/`localizedDayNameAbbr`/
   `localizedMonthAbbr` (funciones puras tocadas por los fixes de hoy) sin
   test dedicado.
5. **IMPORTANTE** — orquestación de `appreciateMember`/`donatePoints` sin
   test de integración.
6. **MENOR** — sigue sin existir ningún `MockEngine` de Ktor en todo el
   repo; componentes delight nuevos (`CelebrationEffects.kt`,
   `EffectsGating.kt`, `AnimatedCounter.kt`, `PointsBadge.kt`) sin test.

---

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ **BUILD SUCCESSFUL** (solo warnings preexistentes: GoogleSignIn/MasterKey/
EncryptedSharedPreferences deprecados, `when` exhaustivo con `else`
redundante — ninguno de esta ronda).

```
./gradlew :composeApp:jvmTest --console=plain
```
→ **BUILD SUCCESSFUL**; verificado además contra los XML reales de
`composeApp/build/test-results/jvmTest/*.xml` (25 archivos): **273 tests, 0
failures, 0 errors** (269 de v13 + 4 nuevos de `RecurrenceRulesTest.kt`).
Detectó una regresión real durante el proceso: el test de `reset()`
esperaba el antiguo default `TaskFilter.PENDING`, corregido a `MINE` junto
con el fix de `reset()`.

```
./gradlew :composeApp:wasmJsMainClasses --console=plain
```
→ **BUILD SUCCESSFUL** — verificación extra por seguridad, ya que varios
fixes tocan `commonMain` compartido con el target web.

## Archivos modificados en esta ronda

- `ui/screens/WelcomeScreen.kt` — versión mostrada sincronizada.
- `ui/components/CelebrationEffects.kt` — `clearAndSetSemantics` en checkmark.
- `ui/screens/HouseholdScreen.kt` — comentario de contraste corregido (hero card).
- `network/RecurrenceRules` — sin cambios de código; `RecurrenceRulesTest.kt` +4 tests.
- `ui/screens/TaskListScreen.kt` — fallback de `resolveCurrentMemberId` ante `memberId` null.
- `ui/models/TaskScreenModel.kt` — `reset()` sincronizado al default `MINE`.
- `ui/models/TaskScreenModelTest.kt` — expectativa de test actualizada.
- `ui/screens/CalendarScreen.kt` — `remember()` en `MonthDayCell`/`DayTasksPopup`/`TaskPopupItem` + clave `today` en `overdueTasks`.
- `network/TaskRepository.kt` — `anonymizeMemberTaskHistory` (nuevo).
- `network/RewardsRepository.kt` — `anonymizeMemberRedemptions` (nuevo).
- `network/FirestoreRepository.kt` — cableado de ambas anonimizaciones en `leaveHousehold`/`deleteMember` + constante `ANONYMIZED_MEMBER_ID`.
- `network/FirestoreClient.kt` — jitter en `retryTransientReadFailure`.
- `docs/review-panel-expertos-v14-2026-09-20.md` (este informe).

## Recomendación de prioridad para el propietario

1. **Gradientes de `PointsBadge.kt`/hero card de `HouseholdScreen.kt` fallan
   WCAG AA (Accesibilidad, CRÍTICO, NUEVO)** — a diferencia de la mayoría de
   hallazgos de esta ronda, esto ya está en producción hoy (badge de puntos
   de tarea completada). Requiere recalibrar los colores del degradado con
   validación visual — no es un fix de una línea.
2. **`donatePoints`/`redeemReward` pueden duplicar puntos de la nada
   (QA/bugs y Funcionalidad, CRÍTICO, heredado)** — un timeout de red real en
   el momento equivocado produce el bug con el código actual. Requiere Cloud
   Function idempotente.
3. **`isPeerPointsTransfer`/auto-edición de `totalPoints` sin rate-limit
   (Seguridad, CRÍTICO, heredado)** — `firestore.rules` sin tocar desde
   2026-09-12; sigue siendo la superficie de inflación de puntos más amplia.
4. **UMP/CMP de AdMob ausente (Privacidad, CRÍTICO, heredado)** — riesgo de
   suspensión de cuenta AdMob si se activan IDs de producción antes de
   implementarlo.
5. **`SecureStore` wasmJs sin cifrado real (Arquitectura, CRÍTICO,
   heredado)** — sigue siendo el hallazgo de seguridad más antiguo sin
   resolver del repo.

El resto (refactors arquitectónicos, `firestore.rules`, ~17 call-sites de
invalidación de caché restantes, rediseños visuales de onboarding/empty
states, extracción de `EmptyState` compartido) son de menor urgencia
relativa y pueden esperar a una ronda dedicada.
