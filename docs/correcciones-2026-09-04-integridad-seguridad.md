# Correcciones 2026-09-04 — integridad / concurrencia / privacidad / seguridad

Aplica los 14 ítems de la sección "PROPUESTAS pendientes — resumen para el
usuario" (bloques "Integridad de datos / concurrencia" y "Privacidad /
seguridad") de `docs/review-panel-expertos-2026-09-03.md`. HEAD de partida:
`011f0cd` (panel v6), árbol de trabajo limpio.

## 1. `completeAssignment` sin concurrencia sobre el documento de tarea — APLICADO

`FirestoreRepository.completeAssignment`: antes del PATCH final que
sincroniza `lastCompletedDate`/`completedBy`/`nextDueAt` en el documento de
la TAREA, ahora se lee el documento fresco y se manda `currentDocument.
updateTime` como precondition. A diferencia de `completeTask` (donde ese
PATCH es el primer paso), aquí los puntos y el historial ya se otorgaron
antes — un conflicto se trata como best-effort (se descarta la
sincronización en vez de sobrescribir a ciegas un cambio más reciente),
nunca como error propagado, para no repetir el anti-patrón "reintentar
duplica puntos" que ya evitan los pasos 4-5 de la misma función.

## 2. `regenerateNextAssignment` puede duplicar la asignación del siguiente ciclo — APLICADO

Ahora crea la asignación con un ID DETERMINISTA (`next_{taskId}_{nextDueDate}`,
vía `documentId` en el POST — mismo patrón que
`HouseholdRepository.getOrCreatePersonalHousehold`) en vez de un ID
autogenerado. La deduplicación contra `existingAssignments` que ya existía
era solo un "check" en memoria, no protegía contra una carrera real entre
dos llamadas concurrentes para el mismo ciclo; ahora Firestore rechaza la
segunda creación con `ALREADY_EXISTS` (capturado y tratado como no-op
idempotente) en vez de crear un duplicado.

## 3. `undoCompleteTask` deja asignaciones "completadas" huérfanas — APLICADO

Nuevo `FirestoreRepository.undoTaskCompletionAssignments(householdId, taskId,
completedAt, task)`, llamado desde `TaskScreenModel.undoCompleteTask` (con un
`repo.getTask` adicional para conocer la frecuencia/recurrencia):

1. Revierte a `"assigned"` (limpia `completedAt`/`pointsAwarded`/`onTime`)
   toda asignación de la tarea cuyo `completedAt` coincida exactamente con el
   de la compleción deshecha — identifica sin ambigüedad las asignaciones
   (propia + hermanas) que cerró esa compleción y no otra.
2. Si la tarea es recurrente, borra la asignación del ciclo SIGUIENTE que
   `regenerateNextAssignment` ya había creado (mismo ID determinista del
   punto 2, recalculado con `calculateNextDueDate(task, completedAt)`) —
   pero solo si sigue `"assigned"` (si alguien ya la completó entretanto, ese
   estado real no se toca).

Best-effort en ambos pasos: un fallo puntual no hace fallar el undo (que ya
revirtió lo más importante: puntos/racha/historial/tarea).

## 4. Compleciones fantasma (0 puntos) en Stats y logros — APLICADO

Contrato aplicado en los DOS sitios que el informe señalaba:

- `StatsScreenModel.computeStats`: `completedAssignments` ahora exige
  `(it.pointsAwarded ?: 0) > 0` además de `status == "completed"`.
- `TaskScreenModel.checkAndAwardAchievements`: `completedCount` ahora exige
  lo mismo antes de contar una asignación como "completada" para el cálculo
  de logros.

No se ha tocado el posible doble conteo preexistente entre `taskHistory` y
`assignments` para la compleción REAL del propio miembro (ambas fuentes
registran la misma compleción real desde que `completeTask` sincroniza
también la asignación propia) — no estaba en el alcance de los 14 ítems
encargados y es un cambio de contrato más amplio; queda anotado aquí para
una futura ronda.

## 5. `reassignTaskCompletion` no toca `assignments` — APLICADO

Nuevo helper privado `updateAssignmentMemberForCompletion`: tras reasignar
`completedBy`/`taskHistory`, localiza la asignación de esa MISMA compleción
(`taskId` + `completedAt` + `memberId == oldMemberId`, `status == "completed"`)
y le actualiza `memberId` al nuevo miembro. No-op si no existía (tarea
completada sin estar asignada a nadie, caso normal). Antes la compleción
quedaba contabilizada en `StatsScreen` de AMBOS miembros (antiguo vía su
asignación stale, nuevo vía el historial ya corregido).

## 6. Cierre de asignaciones hermanas sin `expectedUpdateTime` — APLICADO

Se añadió el campo `updateTime: String?` a `TaskAssignmentResponse` (se
rellena en `TaskRepository.toTaskAssignmentResponse` desde
`FirestoreDocumentResponse.updateTime`). Tanto el paso 4 de `completeTask`
como el bucle de hermanas de `completeAssignment` ahora pasan
`expectedUpdateTime = <assignment>.updateTime` a `markAssignmentCompleted`.
Sigue siendo best-effort (un conflicto se descarta silenciosamente, no se
reintenta), pero ya no sobrescribe a ciegas una asignación hermana que otro
dispositivo hubiera tocado entretanto.

## 7. `reassignTaskCompletion` sin concurrencia optimista — APLICADO

El PATCH de `completedBy` en el documento de la tarea (paso 2) ahora lee el
documento fresco primero y manda `currentDocument.updateTime`. Severidad
MENOR confirmada (función de corrección manual, uso esporádico): un
conflicto se propaga como excepción normal — el `ScreenModel` ya trata
cualquier fallo aquí como error recargable, sin reintento automático, así
que no hay riesgo de duplicar la transferencia de puntos.

## 8. `FirestoreClient.bearerToken`/`tokenExpiry` leídos fuera de `authMutex` — APLICADO

`ensureAuth()` ahora devuelve `String?` (el bearer token vigente),
calculado y devuelto DENTRO de la sección protegida por `authMutex`. Los 3
llamadores (`withAuth()`, `tryAuthOrApiKey()`, `deleteFirebaseAccount()`) ya
no leen `bearerToken` en su propio cuerpo tras soltar el lock — usan el
valor de retorno.

## 9. `MemberRepository.invalidateCurrentMember` sin mutex — APLICADO

Ahora es `suspend fun` y envuelve el `currentMemberCache.remove(...)` en
`currentMemberMutex.withLock { ... }` — mismo mutex que ya protegía
`resolveCurrentMember`. Los 2 call-sites (`deleteHousehold`/`leaveHousehold`
en `FirestoreRepository`) ya eran `suspend`, sin cambios de firma pública
hacia fuera de esos dos.

## 10. `authorName` no anonimizado en comentarios de tarea — APLICADO (parcial, documentado)

- `CommentResponse` gana el campo `memberId: String? = null` (nullable:
  comentarios ya existentes en producción no lo tienen y NO se pueden
  retro-migrar — el documento no guardaba esa referencia).
- `TaskRepository.addComment` ahora recibe `memberId` y lo persiste;
  `TaskScreenModel.addComment` lo resuelve igual que `authorName`
  (`_currentMemberId` o `resolveCurrentMember`).
- Nuevo `TaskRepository.anonymizeMemberComments(householdId, memberIds,
  anonymizedName)`: recorre TODAS las tareas del hogar en paralelo
  (`coroutineScope`/`async`/`awaitAll`, mismo patrón que
  `getAllAssignments`) y anonimiza `authorName` de los comentarios de los
  miembros dados — mismo patrón que
  `HouseholdRepository.anonymizeMemberMessages` (mensajes de chat).
- Conectado a `FirestoreRepository.deleteMember` (expulsión) y
  `leaveHousehold` (abandono), junto a la llamada ya existente para
  mensajes de chat.

**Pendiente documentado**: comentarios creados ANTES de este cambio no
tienen `memberId` y quedan sin poder anonimizarse (limitación de esquema
irreversible sin backfill manual, que el propio informe descarta como fuera
de alcance).

## 11. `rewardRedemptions`/`taskHistory` sin validar campos en `create` — APLICADO Y DESPLEGADO

`firestore.rules` → v7:

- `taskHistory/{thid}` `create`: exige `existsMemberDoc(hid,
  request.resource.data.memberId)` (el `memberId` debe ser un miembro real
  del hogar) y `request.resource.data.points >= 0`.
- `rewardRedemptions/{rrid}` `create`: exige `existsMemberDoc(...)` +
  `request.resource.data.pointsSpent == get(.../rewards/{rewardId}).data.cost`
  (el coste declarado debe coincidir EXACTAMENTE con el coste real de la
  recompensa referenciada en ese instante).

**Desviación deliberada respecto al `p.ej.` del informe**: NO se exige
`memberId == request.auth.uid`. Motivo: `completeTask`/`completeAssignment`
permiten a "cualquier miembro completar cualquier tarea" (un padre/madre
completando/canjeando en nombre de un perfil "hijo/a" sin cuenta propia
desde el mismo dispositivo) — forzar esa igualdad habría roto ese flujo de
producto ya existente y probado. `existsMemberDoc` (integridad referencial)
sí cierra la vía real que señalaba el informe (inventar un `memberId`/
`pointsSpent` arbitrario vía REST directo).

Verificado que el código cliente ya cumple la regla nueva: `saveTaskHistory`
siempre manda un `memberId` real con `points = outcome.pointsAwarded` (puede
ser 0 por penalización, nunca negativo); `MemberRewardScreen` siempre manda
`pointsSpent = reward.cost` tal cual.

**Despliegue** (flujo PATCH documentado en la skill `task-hub`, release
`cloud.firestore` ya existente):

```
$ python3 scripts/deploy_firestore_rules.py
ruleset: projects/task-hub-62f98/rulesets/12666cb9-8133-4715-8816-352ae3410f06
publish status: 200

$ python3 scripts/check_live_rules.py
GET release status: 200
live rulesetName: projects/task-hub-62f98/rulesets/12666cb9-8133-4715-8816-352ae3410f06
updateTime:        2026-09-04T00:39:39.879087Z
```

Verificado además byte a byte (`get_ruleset_content.py` contra el ruleset
activo, diff contra `firestore.rules` local) — contenido idéntico salvo una
línea en blanco final, artefacto del script de lectura.

## 12. Google Calendar access token sin cifrar — APLICADO

`SettingsStore.getGoogleAccessToken`/`setGoogleAccessToken`/
`unlinkGoogleCalendar` migrados de `settings` (texto plano) a `secureStore`
— mismo mecanismo y patrón de migración automática de valor legado
(`migrateLegacyToken`) ya usado por `getGoogleRefreshToken`/
`getAnonymousRefreshToken`. `hasGoogleLinked()` reutiliza el getter ya
migrado en vez de leer `settings` directamente.

## 13. Señalización AdMob por sesión según rol — APLICADO (plumbing, con salvaguarda documentada)

`AdController` gana `updateChildDirectedSignal(isChildProfile: Boolean)`.
Wired desde `TaskScreenModel.loadTasks`/`loadTaskDetail` (los dos puntos
donde ya se resuelve el miembro activo + su `role`), best-effort.

**Desviación deliberada**: la implementación Android (`AdControllerImpl`)
IGNORA a propósito el valor de `isChildProfile` y siempre reafirma
`TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` + `MAX_AD_CONTENT_RATING_G`. Motivo:
`TaskHubApplication.onCreate` ya fija ese mismo TFCD=TRUE como fix CRÍTICO
de una ronda anterior, explícitamente "con independencia de qué perfil esté
activo en el dispositivo" (un dispositivo familiar compartido puede tener un
perfil admin activo un momento y uno "child" al siguiente). Relajar la señal
cuando un admin está activo reabriría el hueco que ese fix CRÍTICO cerró.
Se deja el plumbing (wiring desde el rol real del miembro activo) listo para
que el producto decida en el futuro si quiere relajarlo con más garantías —
hoy no lo hace.

## 14. `fcmToken` no se limpia en `signOut()` local — APLICADO

Nuevo `FirestoreRepository.clearFcmToken(uid)` (PATCH de
`users/{uid}.fcmToken` a `NULL_VALUE`). `GoogleAuthManager.signOut()` ahora
captura el UID ANTES de `clearGoogleAuth()` y, si no era null, lanza la
limpieza en segundo plano (`scope.launch`, best-effort, mismo patrón que
`syncHouseholdsToCloud`).

---

## Verificación

```
$ ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
BUILD SUCCESSFUL in 11s
```
Sin errores; solo warnings de deprecación preexistentes (Google Sign-In,
Vibrator, EncryptedSharedPreferences/MasterKey), ninguno introducido por
esta ronda.

```
$ ./gradlew :composeApp:jvmTest --console=plain
BUILD SUCCESSFUL in 10s
```
135 tests, mismo recuento que antes de esta ronda — sin fallos, sin tests
rotos. No se añadieron tests nuevos (los 14 ítems eran de comportamiento de
red/reglas, no lógica pura nueva aislable sin mocks — ver Experto 13 del
informe sobre los huecos de cobertura ya conocidos y no incluidos en este
encargo).

## Archivos tocados (12)

```
firestore.rules
composeApp/src/androidMain/kotlin/org/taskhub/platform/AdController.android.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreClient.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/TaskRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/models/DTOs.kt
composeApp/src/commonMain/kotlin/org/taskhub/platform/AdController.kt
composeApp/src/commonMain/kotlin/org/taskhub/storage/SettingsStore.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/GoogleAuthManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/StatsScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt
```

## Pendiente (fuera de alcance de este encargo)

Los 3 bloques restantes del informe (`docs/review-panel-expertos-2026-09-03.md`,
sección final) NO se han tocado — no estaban entre los 14 ítems encargados:

- **Arquitectura/rendimiento** (ítems 15-21): `HouseholdTaskSection` bypassa
  el ScreenModel, `FirestoreRepository` facade sigue creciendo,
  `TaskScreenModel` "mini god ScreenModel", `getAllAssignments` duplica
  `getTasks`, `loadTasks`/`loadStats` secuenciales, `deleteAllDocuments` sin
  límite de concurrencia, mapeo DTO↔dominio descentralizado.
- **Estética/UX menor** (ítems 22-28): ilustraciones de estado vacío,
  fallbacks de error hardcodeados en español (33 puntos), `AlertDialog`
  manual en `MemberRewardScreen`, patrón "cabecera expandible" duplicado,
  cobertura de `reduce-motion`, `SemanticColors` sin uso actual.
- **Cobertura de tests** (ítems 29-31, solo informativos): `SecureStore` sin
  test de fallo de descifrado, `TaskCache`/`SettingsStore.getCalendarId` sin
  cobertura, `completeAssignment` sin test de integración.

Tampoco se ha resuelto el doble conteo preexistente entre `assignments` y
`taskHistory` para la compleción REAL del propio miembro (ver nota del
ítem 4) — es un hallazgo colateral descubierto durante esta ronda, no uno de
los 14 ítems encargados.
