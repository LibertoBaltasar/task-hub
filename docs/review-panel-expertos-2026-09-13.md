# Panel de expertos v9 — Task Hub (2026-09-13)

**HEAD de partida:** `3f8567e` (fix: tareas recurrentes nunca completadas
quedan atrasadas tras el día programado — último commit de la cadena
`3aeeb69` auditoría v8 → `3fd1d7b` ronda de deuda aplicable → `3f8567e`
recurrencia). Working tree limpio al empezar. Encargo: relanzamiento del
panel de 13 expertos que falló por completo en la ronda v8
(`docs/review-panel-expertos-2026-09-12.md`), más verificación punto por
punto de los 7 hallazgos abiertos/aplicados de esa cadena.

## Nota de proceso — el panel de 13 expertos volvió a fallar por completo

Se lanzaron en paralelo los 13 subagentes especialistas (uno por rol del
encargo: estética, funcionalidad end-to-end, accesibilidad WCAG AA, UI/
componentes, UX, programador senior, arquitectura, QA/bugs, seguridad
MASVS, privacidad/RGPD, rendimiento, red/offline/sync, cobertura de tests).

**Los 13 fallaron de forma inmediata**, con el mismo error que en la ronda
v8: `Agent terminated early due to an API error: You've hit your session
limit`. Ningún agente llegó a producir un solo hallazgo verificado contra
el código. **PANEL NO EJECUTADO** — es la segunda vez consecutiva que ocurre
(v8: 14/14 fallidos; v9: 13/13 fallidos), así que no parece un incidente
puntual sino una limitación estructural de esta sesión/entorno para lanzar
~13 subagentes simultáneos.

No hay hallazgos de los 13 expertos en este informe — inventarlos sería
fabricar contenido no verificado, algo que el propio encargo prohíbe
explícitamente. Ante esto, el coordinador continuó el resto del encargo
(verificación del "primer trabajo" y las correcciones objetivas que surgieron
de esa verificación) directamente contra el código real, con archivo:línea
en cada afirmación, para no dejar la ronda completamente vacía. **Este
encargo NO se da por completado en el sentido de "panel de 13 expertos
ejecutado"** — queda pendiente relanzarlo en una sesión con cuota disponible,
igual que quedó pendiente tras la v8 y no se pudo recuperar en esta v9.

---

## Estado de los hallazgos de la cadena 2026-09-12 (primer trabajo)

### 1. `RecurrenceRules`/recurrencia — ventana de "atrasada" para nunca completadas

**YA RESUELTO, sin regresiones, ancla legada segura.** Verificado contra
`network/RecurrenceRules.kt:74-181`:

- `isDueOn`/`isDueToday` ganaron el parámetro `createdAt: Long = 0`
  (líneas 74-85, 95-103), con 3 ramas en `weekly` (127-147) y `monthly`
  con `recurrenceDay` (148-167): completada antes (catch-up existente, sin
  cambios), nunca completada con `createdAt` conocido (nueva, ancla `<=`),
  nunca completada sin `createdAt` (legado, comportamiento exacto previo:
  `dow in recurrenceDays` / `date == thisMonthTarget`).
- **Sin falsos atrasos para tareas legadas**: `createdLocalDate` es `null`
  si `createdAt <= 0` (línea 110-112), lo que fuerza la rama legada (caso 3
  del `when`) — nunca abre ventana retroactiva sobre una fecha de creación
  desconocida. Confirmado con el test
  `isDueToday_weekly_neverCompletedAndDayAlreadyPassed_legacyNoCreatedAt_staysNotDueUntilNextCycle`.
- **Sin regresión en `isPending`/`previewFilter`**
  (`ui/models/HomeScreenModel.kt:247-268`): `isPending` ya propaga
  `createdAt = task.createdAt` (línea 259) al llamar a `RecurrenceRules`;
  `previewFilterTasks` reutiliza `isPending` sin lógica propia — coherente.
- **Sin regresión en `TaskWithStatus.isCompleted`/grupos de
  `TaskListScreen`** (`ui/screens/TaskListScreen.kt:333-429,538-566`):
  `isTaskDueToday` (línea 368-376) también propaga `createdAt`; `isCompleted`
  se define como `!due && task.lastCompletedDate != null` (línea 566) — para
  una tarea nunca completada, `lastCompletedDate` es siempre `null`, así que
  el nuevo valor de `due` (que ahora puede ser `true` con más frecuencia por
  la ventana) no afecta a `isCompleted` en ningún caso. `groupTasksByStatus`
  no se tocó y sigue siendo coherente con el nuevo `due`.
- **`computeStats` no usa `isDueToday` en absoluto**
  (`ui/models/StatsScreenModel.kt:160-260`, confirmado por grep) — no hay
  superficie de regresión ahí.

### 2. `donatePoints`/`appreciateMember` — reglas ampliadas, coherencia cliente↔reglas

**PARCIALMENTE ABIERTO — la rama de `firestore.rules` está bien acotada,
pero se encontró una incoherencia real cliente↔reglas. [APLICADO] esta
ronda.**

- `firestore.rules:274-280` `isPeerPointsTransfer(hid, mid)`: exige
  `request.auth.uid != mid` (el receptor no se autoacredita),
  `diff().affectedKeys() == ['totalPoints'].toSet()` (ningún otro campo),
  `totalPoints` estrictamente creciente, y tope `+1000` por escritura. Bien
  acotada — no reabre la inflación de puntos que motivó el gate original.
  `MemberRepository.addMemberPoints` (`network/MemberRepository.kt:506-537`)
  solo hace PATCH de `totalPoints` (`updateMaskFieldPaths("totalPoints")`),
  coherente con el `affectedKeys` exigido.
- **Hallazgo nuevo — incoherencia cliente↔reglas (QA/AppSec, IMPORTANTE):**
  `PointsRules.validateDonateBalance` (`network/PointsRules.kt:85-86`, antes
  de este fix) solo comprobaba `amount <= fromBalance` — **sin tope alguno
  que reflejara el `+1000` por escritura de `isPeerPointsTransfer`**. Un
  donante SIN rol admin/owner (cuya escritura al documento del receptor pasa
  por `isPeerPointsTransfer`, no por `isTrusted(hid)`) que acumulara más de
  1000 puntos y donara su saldo completo de una vez recibía un 403 de
  Firestore en el PATCH de acreditación
  (`MemberRepository.donatePoints`, `:683-737`), capturado por el
  `catch (_: Exception)` genérico de la línea ~711 (antes del fix) y
  reportado como `TRANSFER_FAILED` — el mismo mensaje genérico que un fallo
  de red real, sin ninguna pista de que el problema era el importe. Un
  donante owner/admin del hogar NO sufre esto (su escritura pasa por
  `isTrusted(hid)`, sin tope), así que el bug es asimétrico entre roles y
  fácil de no detectar probando solo con una cuenta admin.
- **Fix aplicado:** `PointsRules.MAX_PEER_TRANSFER_AMOUNT = 1000` (nueva
  constante, documentada como debiendo coincidir con el literal de
  `isPeerPointsTransfer`), usada en `MemberRepository.donatePoints` solo
  para CLASIFICAR el error tras el 403 (no para bloquear antes de intentar —
  bloquear ciegamente habría impedido donaciones grandes legítimas de un
  admin/owner, que sí puede superar el tope). Nuevo
  `DonateErrorReason.AMOUNT_EXCEEDS_LIMIT`, nueva clave i18n
  `donate_error_exceeds_limit` (ES/EN) con mensaje accionable ("prueba a
  dividir la donación").
- **Cliente, resto:** confirmado que la UI de agradecer/donar
  (`ui/components/HouseholdMemberList.kt`) sigue sin gate por `isAdmin` para
  el emisor (`canTransfer = myMember != null`), coherente con la decisión de
  producto ya aplicada en la ronda anterior.

### 3. Sucesión de `ownerId` — todos los caminos

**PARCIALMENTE ABIERTO — el camino de abandono voluntario funciona; el
camino de EXPULSIÓN por un admin NO transfería `ownerId`, y SÍ es alcanzable
desde la UI (no era "especulativo" como decía la ronda anterior).
[APLICADO] esta ronda.**

- **Abandono voluntario (`leaveHousehold`,
  `network/FirestoreRepository.kt:577-694`):** correcto, sin cambios — usa
  `HouseholdRules.resolveOwnerSuccessor` (admin vinculado más antiguo,
  fallback a cualquier miembro vinculado, `null` si nadie tiene cuenta),
  ANTES de borrar el propio documento de miembro (necesario porque
  `firestore.rules` `isOwner(hid)` exige seguir siendo owner para poder
  reasignar).
- **Hallazgo nuevo — expulsión del owner posible y sin sucesión (QA/
  seguridad de datos, CRÍTICO):** verificado en
  `ui/components/HouseholdMemberList.kt:263-339`: el botón de expulsar
  (`IconButton` con `Icons.Default.Delete`) solo está condicionado a
  `isAdmin && !isSelf` (línea 263) — **sin ningún chequeo de si el miembro
  objetivo es el owner del hogar**. Cualquier admin (no necesariamente el
  owner) puede expulsar al owner si son personas distintas. El callback
  (`HouseholdScreen.kt:591-593` → `MemberScreenModel.removeMember` →
  `FirestoreRepository.deleteMember`, antes de este fix) no comprobaba
  `ownerId` en absoluto. Consecuencia real: `households/{hid}.ownerId`
  seguía apuntando al UID del miembro ya expulsado —
  `firestore.rules:231-234` `isOwner(hid)` solo compara ese campo contra
  `request.auth.uid`, sin mirar si existe un documento de miembro para ese
  UID — así que el expulsado CONSERVABA permisos de owner (borrar el hogar,
  gestionar roles de otros) pese a ya no aparecer en la lista de miembros
  (soft-delete vía `leftAt`, `MemberRepository.deleteMember:351-371`, que
  además no borra `userId`), y nadie más podía sucederle nunca por esa vía.
  Un estado inconsistente y de difícil recuperación sin intervención manual
  en Firestore.
- **Fix aplicado** (`network/FirestoreRepository.kt`, `deleteMember`): antes
  del soft-delete, si el miembro objetivo es el owner actual del hogar
  (`targetMember.userId == household.ownerId`), se ejecuta la MISMA lógica
  de sucesión que `leaveHousehold` (`HouseholdRules.resolveOwnerSuccessor`
  sobre los miembros restantes, promoción a admin si hace falta,
  `householdRepository.updateHouseholdOwner`), best-effort (un fallo aquí no
  bloquea la expulsión, igual que en `leaveHousehold`). Misma limitación
  conocida documentada: si nadie más tiene cuenta vinculada, el hogar queda
  sin owner operable.
- **Gate de UI "borrar hogar solo-owner":** SIGUE INTACTO, verificado.
  `firestore.rules:307` (`allow update, delete: if isOwner(hid)`) y
  `HouseholdScreen.kt:397-408` (el botón de borrar hogar solo se pinta si
  `isOwner`) sin cambios ni regresión.

### 4. TTL 90 días de `taskHistory`/mensajes

**SIGUE ABIERTO — riesgo real confirmado, no aplicado (decisión de
producto/retención, fuera del criterio "objetivo y seguro" para aplicar
sin más). CRÍTICO, SOLO PROPUESTA.**

- **La purga NO es segura para `StatsScreen`.** `computeStats`
  (`ui/models/StatsScreenModel.kt:160-260`) deriva `totalTasksCompleted`,
  `onTimeRate`, `overdueCount` y la distribución `tasksByTag` de
  `allCompletions` — la unión de TODO `taskHistory` + TODAS las
  asignaciones completadas, **sin ninguna ventana temporal** (la única
  ventana de 7 días es para las gráficas `tasksPerDay`/`dailyPoints`, no
  para estos totales). `StatsScreenModel.loadStats` (línea 107-118) llama a
  `purgeOldTaskHistory` **justo después** de calcular y publicar esos
  totales con los datos completos — así que el usuario ve las cifras
  correctas en la sesión actual, pero cada registro purgado deja de contar
  para siempre en sesiones futuras: el "total de tareas completadas" de un
  miembro con más de 90 días de antigüedad empezará a DECRECER con el
  tiempo en vez de ser un acumulado real, algo que ningún texto de la UI
  advierte (el campo se llama `totalTasksCompleted`, no "completadas en los
  últimos 90 días"). No se detectó ningún duplicado de lecturas (la purga
  reutiliza la lista `history` ya cargada, sin fetch adicional).
- **No se aplica ningún fix de código en esta ronda**: corregirlo bien
  significa una decisión de producto (¿agregar contadores acumulativos
  persistentes en el documento de miembro para que sobrevivan a la purga de
  `taskHistory` en detalle, o reducir el alcance de la purga a las
  colecciones que la UI JAMÁS usa como fuente de agregados históricos, o
  aceptar explícitamente el decaimiento y avisarlo en la UI?) — no es un bug
  objetivo con un fix mecánico seguro. **PROPUESTA**: extraer
  `totalTasksCompleted`/`totalOnTimeCount` como contadores persistentes en
  el documento de miembro (incrementados atómicamente al completar, igual
  que `totalPoints`), independientes de cuánto `taskHistory` detallado se
  conserve — así la purga de 90 días solo afecta al detalle día a día, no al
  acumulado.

### 5. Anonimización de notificaciones (`authorMemberId`)

**YA RESUELTO, cubre ambos casos, sin romper notificaciones legadas.**
Verificado:

- `getMembers` (`network/MemberRepository.kt:140-159`) filtra
  `it.leftAt == 0L` — así que tanto la expulsión (soft-delete con `leftAt`,
  `MemberRepository.deleteMember:351-371`) como el abandono voluntario
  (`leaveHousehold`, DELETE real del documento) hacen que el miembro
  desaparezca de `getMembers` de la misma forma. El resolver de
  `NotificationListScreen.kt:74-82` (`byId[id]?.displayName`) devuelve
  `null` en ambos casos por igual, y `NotificationText.message`
  (`ui/i18n/NotificationText.kt:52-53`) usa el placeholder
  `member_deleted_name` cuando el resolver devuelve `null`. El informe de la
  ronda anterior describía el abandono como "borra el documento" y la
  expulsión como "soft-delete con displayName congelado" — correcto en los
  hechos, pero ambos convergen al mismo resultado (`null` en `getMembers`)
  así que el resolver los trata igual, sin necesitar distinguirlos.
- **Notificaciones legadas sin `authorMemberId`:** `NotificationText.message`
  (línea 50-56) solo entra en la rama de resolución si `authorId != null &&
  preview != null && resolveAuthorName != null` — si `authorMemberId` es
  `null` (dato legado), cae directamente a `messageKey`/`message`, sin
  cambios de comportamiento. Sin riesgo de romper el render de datos
  antiguos.
- Caso adicional verificado sin problema: si `resolveAuthorName` es `null`
  porque la lista de miembros aún no cargó (carrera de carga en
  `NotificationListScreen`), la condición también falla y cae al mismo
  fallback (`notification.message`, el nombre congelado en el momento del
  envío) — no hay crash ni texto roto durante la carga, solo se autocorrige
  en cuanto `MemberScreenModel` publica su estado.

### 6. Bugs de funcionalidad A1-A6

**YA RESUELTO, sin regresiones detectadas en la verificación de esta
ronda** (spot-check dirigido, no una re-auditoría completa — la ronda
anterior ya los aplicó con 210 tests en verde, y el build/test de esta
ronda sigue en verde sin tocar ese código). Verificado puntualmente:
`GoogleAuthManager.syncHouseholdsToCloud` (B12, relacionado) usa
`syncHouseholdsJob` con cancelación previa (`ui/models/GoogleAuthManager.kt:442-465`),
coherente con lo documentado.

### 7. Caché de respaldo (`orDefault`) y `syncHouseholdsToCloud` serializado

**YA RESUELTO.** `syncHouseholdsToCloud` (`ui/models/GoogleAuthManager.kt:459-465`):
`syncHouseholdsJob?.cancel()` antes de lanzar un nuevo `Job`, evita
sincronizaciones solapadas — confirmado contra el código real.

---

## Hallazgos nuevos de esta ronda (verificación directa, sin panel)

Dado que el panel de 13 expertos no pudo ejecutarse, los dos hallazgos
siguientes surgieron de la verificación del "primer trabajo" de arriba
(puntos 2 y 3) y se aplicaron por ser correcciones objetivas y seguras
(mismo criterio que rondas anteriores: bug real, fix acotado, sin
ambigüedad de producto).

### [APLICADO] `donatePoints` sin mensaje claro al superar el tope de 1000/escritura — IMPORTANTE

Ver punto 2 de arriba. Archivos: `network/PointsRules.kt`,
`network/MemberRepository.kt` (`DonateErrorReason`, `donatePoints`),
`ui/models/MemberScreenModel.kt` (`donateErrorKey`), `ui/i18n/AppStrings.kt`
(ES/EN).

### [APLICADO] Expulsar al owner del hogar no transfería `ownerId` — CRÍTICO

Ver punto 3 de arriba. Archivo: `network/FirestoreRepository.kt`
(`deleteMember`).

### [PROPUESTA] Purga TTL de 90 días erosiona los totales "de siempre" de `StatsScreen` — CRÍTICO

Ver punto 4 de arriba. Requiere decisión de producto sobre si
`totalTasksCompleted`/`onTimeRate` deben ser acumulados de por vida o una
ventana — no se aplica código sin esa decisión.

---

## Verificación

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
→ `BUILD SUCCESSFUL in 33s` (solo warnings preexistentes de APIs Android
deprecadas — GoogleSignIn, MasterKey, EncryptedSharedPreferences — no
relacionados con esta ronda).

```
./gradlew :composeApp:jvmTest --console=plain
```
→ `BUILD SUCCESSFUL in 19s`. **216 tests, 0 fallos, 0 errores** — mismo
número que el baseline de la ronda anterior (esta ronda no añadió tests
nuevos: los dos fixes aplicados son cambios pequeños sobre código de I/O ya
sin cobertura unitaria propia — `FirestoreRepository`/`MemberRepository` —
consistente con el patrón ya existente en el resto del repo, donde solo las
funciones puras de `*Rules.kt` tienen tests dedicados y
`resolveOwnerSuccessor`, la función pura reutilizada por el fix del punto 3,
ya estaba cubierta por `HouseholdRulesTest.kt`).

## Archivos modificados en esta ronda

- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt`
  (`deleteMember`: sucesión de `ownerId` antes de expulsar al owner).
- `composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt`
  (`DonateErrorReason.AMOUNT_EXCEEDS_LIMIT`, clasificación del error en
  `donatePoints`).
- `composeApp/src/commonMain/kotlin/org/taskhub/network/PointsRules.kt`
  (`MAX_PEER_TRANSFER_AMOUNT`).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/MemberScreenModel.kt`
  (`donateErrorKey`).
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt`
  (`donate_error_exceeds_limit`, ES/EN).
- `docs/review-panel-expertos-2026-09-13.md` (este informe).

## Pendiente para la próxima ronda

- **Relanzar el panel completo de 13 expertos** — falló por completo dos
  rondas consecutivas (v8: 14/14, v9: 13/13) por límite de sesión de la API.
  Si vuelve a fallar una tercera vez, vale la pena replantear el mecanismo
  (menos agentes en paralelo, o secuencial por lotes) en vez de reintentar
  el mismo patrón de 13 lanzamientos simultáneos.
- **Punto 4 (PROPUESTA)**: decisión de producto sobre contadores
  acumulativos de por vida en el documento de miembro, independientes de la
  purga TTL de `taskHistory`.
