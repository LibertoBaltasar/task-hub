# Panel de expertos v10 — foco sincronización entre dispositivos (2026-09-12)

HEAD de partida: `9e3641085fa59c4e2b15adff10512f716f607f78` (`feat: elimina
la auth anónima, Task Hub pasa a requerir login con Google`). HEAD final:
ver commit de este informe. Revisión NUEVA (no continuación de v9), ejecutada
en 3 oleadas de 4-5 subagentes simultáneos (límite estructural probado),
consolidando y commiteando tras cada oleada. Detalle oleada a oleada en
`docs/panel-expertos-sync-2026-09-12-progreso.md`.

## Contexto de partida

El encargo `00` (`docs/auditoria-sync-dispositivos-2026-09-12.md`, HEAD
`8c3f3d3`) diagnosticó y arregló (`8cc954d`) el huérfano anónimo→Google
vinculando la credencial de Google a la sesión anónima activa en vez de
reemplazarla. Un encargo posterior (`docs/google-only-auth-2026-09-12.md`,
commit `9e36410`, el HEAD de partida de esta ronda) fue más allá: **eliminó
la auth anónima por completo**. Task Hub ahora exige login con Google desde
el arranque (`AuthGateScreen`, gate en `App.kt`), dejando obsoleto el
mecanismo de vinculación (`AccountLinkingRules`, ya borrado). El foco de esta
ronda ha sido verificar que el flujo Google-only en sí sincroniza bien entre
dispositivos, y auditar con lupa cada subsistema relacionado.

## Estado del fix de sync (auditoría 00)

El fix de vinculación anónimo→Google del encargo 00 quedó **obsoleto por
diseño**, no roto: al eliminarse la auth anónima en el commit inmediatamente
posterior (`9e36410`), nunca vuelve a existir una sesión anónima activa que
vincular. El código de esa auditoría (`AccountLinkingRules` +
`AccountLinkingRulesTest`, 6 tests) fue borrado íntegramente en `9e36410`,
confirmado sin referencias muertas en todo `commonMain` (verificado por el
experto de arquitectura, Oleada A). El riesgo residual que el propio fix 00
ya anticipaba —el proveedor anónimo de Firebase Auth sigue activo
server-side mientras no se desactive en la consola— **sigue abierto** y se
ha vuelto más relevante tras Google-only: ver hallazgo CRÍTICO de Seguridad
más abajo (cualquiera puede seguir generando UIDs anónimos por REST directo
con la apiKey pública, sin pasar por la app).

## Estado de hallazgos de la ronda anterior (v9-reintento, 2026-09-11)

- **CRÍTICO convergente QA+Seguridad** (fix de sucesión de `ownerId` al
  expulsar al owner, `firestore.rules` exigía `isOwner(hid)` para el PATCH,
  el caller real es siempre un admin no-owner → 403 silencioso): **YA
  RESUELTO en las reglas** por el commit `f92aa0b` (posterior a v9-reintento,
  ya en el HEAD de partida de esta ronda) con `isValidOwnerSuccession(hid)`.
  Pero **SEGUÍA SIN SER ALCANZABLE DESDE LA UI** — el bloqueo de "no permitir
  expulsar al owner" de la propia ronda v9 nunca se retiró. Corregido en esta
  ronda (Oleada A, QA) reactivando el botón de expulsar sobre el owner en
  `HouseholdMemberList.kt`. **Caveat que sigue abierto**: no hay evidencia en
  el repo de que `firestore.rules` v10 se haya desplegado a producción
  (`firebase deploy --only firestore:rules`) — sin ese deploy, el fix de UI
  de esta ronda reintroduce el 403 silencioso original, ahora peor (ver
  hallazgo CRÍTICO de Funcionalidad end-to-end).
- **`saveUserHouseholds` sin `updateMaskFieldPaths`** (v9): YA RESUELTO,
  confirmado en el código actual (`FirestoreRepository.kt:240-253`).
- **`tryAuthOrApiKey` (API key en query string)** (v9 y anteriores): YA
  RESUELTO, eliminado en `b96ca6e`, confirmado ausente.
- **Retry/backoff en `FirestoreClient`** (v9): YA RESUELTO, implementado en
  `9f2bfcf` (`retryTransientReadFailure`).
- **`completeTask`/`completeAssignment` duplicación, `TaskScreenModel` god
  object, `MainActivity.consumeDeepLink` con `HouseholdStore(Settings())` al
  vuelo** (v9, programador senior): SIGUEN ABIERTOS, fuera del foco de sync
  de esta ronda, sin cambios.
- **Logo/splash ignora los temas Naturaleza/Minimal** (v9, estética):
  SIGUE ABIERTO — confirmado que ahora también afecta a `AuthGateScreen`
  (mayor visibilidad, mismo hallazgo, no nuevo).
- **Polling sin lifecycle-awareness, `CalendarSyncManager` serie, mensajes
  sin paginación** (v9, rendimiento): SIGUEN ABIERTOS, fuera del foco
  explícito de esta ronda (arranque en frío + sync).
- **Balance no-atómico de puntos (`totalPoints` sin clamp server-side)** (v9
  y `docs/atomicidad-commit-pendiente.md`): SIGUE ABIERTO, confirmado por QA
  en esta ronda, requiere backend/Cloud Functions, no aplicable mecánicamente.

---

## 1. Red/offline/sincronización

### CRÍTICO — `ensureAuth()` deslogueaba permanentemente por un simple fallo de red — NUEVO, APLICADO
- **Problema**: `FirestoreClient.ensureAuth()` borraba la sesión de Google persistida (`settingsStore.clearGoogleAuth()`) ante CUALQUIER excepción al refrescar el token, incluido un timeout/sin red — no solo ante un refresh token realmente inválido/revocado.
- **Por qué importa**: contradice el diseño "best-effort, nunca bloquea si está offline" del propio bootstrap de `App.kt`. Cada arranque en frío offline (token en memoria vacío tras reiniciar el proceso) lanzaba una excepción de transporte y borraba el `googleRefreshToken` guardado; el siguiente arranque en frío ya no encontraba credenciales que restaurar y caía a `SignedOut` — un usuario sin conexión podía quedar deslogueado permanentemente sin haber hecho nada mal.
- **Evidencia**: `FirestoreClient.kt:162-202` (antes: `catch (_: Exception) { settingsStore.clearGoogleAuth() }` sin distinguir causa).
- **Fix aplicado**: el catch solo borra la sesión si `!e.isTransientReadFailure()`. Se amplió `isTransientReadFailure()` (`FirestoreClient.kt:437-458`) para mirar también `cause` recursivamente, porque `redactApiKey` reenvuelve timeouts/errores de conexión como `IllegalStateException`, perdiendo el tipo `IOException` original.
- **Estado**: NUEVO (introducido por el propio commit `9e36410` al quitar el fallback anónimo que enmascaraba el problema).

### IMPORTANTE — proveedor anónimo de Firebase Auth sigue activo server-side — SIGUE ABIERTO, PROPUESTA
- **Problema**: mientras coexistan instalaciones de la versión anterior (con auth anónima) y esta versión Google-only para el mismo hogar compartido, un dispositivo viejo puede seguir generando `ownerId`/`members/{mid}` con UID anónimo.
- **Por qué importa**: riesgo operativo de fragmentación de hogares durante el rollout, no una regresión de sincronización entre dispositivos de la MISMA cuenta Google (esa se verificó sin regresión).
- **Fix**: PROPUESTA de infraestructura — desactivar el proveedor anónimo en Firebase Auth console tan pronto la nueva versión esté publicada y adoptada (ya señalado por el propio encargo `google-only-auth`).
- **Estado**: SIGUE ABIERTO (documentado explícitamente como pendiente desde el encargo anterior).

### Verificado sin hallazgo
Flujo hogar compartido 2 dispositivos/misma cuenta (sin regresión); conflictos de escritura concurrente (sin cambios, `OPTIMISTIC_WRITE_MAX_RETRIES=3`); sin residuos de auth anónima en caché local (`HouseholdStore`/`SettingsStore`/`TaskCache`, grep limpio).

---

## 2. Arquitectura

### CRÍTICO — `signOut()` no limpiaba `HouseholdStore`: fuga de hogares entre cuentas en dispositivo compartido — NUEVO, APLICADO
- **Problema**: `GoogleAuthManager.signOut()` limpiaba `settingsStore`/caché de miembro/`fcmToken`/sondeo de notificaciones, pero nunca `HouseholdStore` (lista de hogares locales, sin ámbito por UID).
- **Por qué importa**: `restoreHouseholds`/`syncHouseholdsToCloud` son aditivos por diseño, y `reconcileHouseholds` solo poda un hogar si `getHousehold` devuelve 404/403 — pero `firestore.rules` permite `get` a cualquier `signedIn()` sin exigir membresía, así que un hogar de la cuenta A nunca se poda al loguear con la cuenta B: sobrevive, se muestra (nombre + código de invitación que permite unirse de verdad) en la sesión de B, y `syncHouseholdsToCloud()` lo sube PERMANENTEMENTE a `users/{uidB}.householdIds`, propagando la fuga a todos los dispositivos de B.
- **Evidencia**: `GoogleAuthManager.kt:137-167` (antes), `HouseholdStore.kt:135-138` (`clearAll()` ya existía, solo se llamaba desde `deleteAccount()`), `HouseholdRepository.kt:256-264`, `firestore.rules:411-414`.
- **Fix aplicado**: `householdStore.clearAll()` añadido a `signOut()`.
- **Estado**: NUEVO (no detectado en ninguna ronda anterior).

### IMPORTANTE — `FirestoreClient` puede invalidar la sesión sin avisar a `GoogleAuthManager` — SIGUE ABIERTO, PROPUESTA
- **Problema**: `GoogleAuthManager._state` solo cambia por acciones explícitas de la propia clase; `FirestoreClient.ensureAuth()` puede decidir de forma independiente que la sesión ya no es válida y limpiar credenciales sin notificar. Resultado: el usuario sigue en `SignedIn` (pasa el gate, ve `HomeScreen`) pero toda petición autenticada falla, sin ninguna vía automática de vuelta a `AuthGateScreen`.
- **Por qué importa**: con Google-only, `AuthGateScreen` es el único punto de entrada; antes este escenario tenía un aterrizaje suave (caía a modo anónimo), ahora no tiene ninguno.
- **Evidencia**: `FirestoreClient.kt:162-187`, `GoogleAuthManager.kt:70-77`.
- **PROPUESTA**: exponer un `SharedFlow` `sessionInvalidated` desde `FirestoreClient`, que `GoogleAuthManager` observe para transicionar a `SignedOut`. Atenuado (no eliminado) por el fix CRÍTICO de red/offline de esta misma ronda.
- **Estado**: NUEVO, agravado por la eliminación de la auth anónima.

### IMPORTANTE — iOS/JVM permanentemente bloqueados en `AuthGateScreen` — SIGUE ABIERTO (ya conocido)
Confirmado en código (`Platform.ios.kt`, `Platform.jvm.kt` sin `launchGoogleSignIn()` real). Ya documentado como PROPUESTA por el propio encargo `google-only-auth`; requiere decisión de producto (implementar Sign-In real o acotar Google-only a Android).

### MENOR — deep link puede sobrevivir a un cambio de cuenta en caliente — NUEVO, PROPUESTA
Caso borde de baja frecuencia: `App.kt` no comprueba `initialDeepLinkConsumedKey` al reconstruir la pila tras un signOut→signIn sin matar el proceso. PROPUESTA sin aplicar (requiere decidir semántica deseada).

### MENOR — `currentUserIdentities()`/`resolveExistingMemberId` ya sin caso de uso real — NUEVO, PROPUESTA
Deuda de diseño heredada de la era anónimo→Google: la lista de identidades es, en la práctica, siempre 0 o 1 elemento en Google-only. PROPUESTA de simplificación sin urgencia, no aplicada (toca 4 archivos + tests).

### Verificado sin hallazgo
Límites de capas `FirestoreClient`↔repos de dominio (sólido, sin ciclo); máquina de estados `GoogleAuthState` (coherente, sin referencias muertas); DI Koin (sin referencias muertas a `AccountLinkingRules`); anclaje de datos `ownerId`/`member.userId` (verificado en código, coincide con lo declarado).

---

## 3. QA y bugs

### CRÍTICO — el fix de sucesión de `ownerId` (f92aa0b) era inalcanzable desde la UI — NUEVO, APLICADO (con caveat de deploy sin resolver)
- **Problema**: el bloqueo de "no permitir expulsar al owner" de la ronda v9-reintento nunca se retiró tras arreglarse `firestore.rules` en `f92aa0b`.
- **Por qué importa**: dejaba el fix de las reglas como código muerto — un admin no-owner nunca podía disparar la transferencia de `ownerId` que la regla ya permitía.
- **Evidencia**: `HouseholdMemberList.kt:283` (antes), `firestore.rules:11-46` (cabecera v10, declara la intención de sustituir el bloqueo de UI por la regla), `FirestoreRepository.kt:784-796` (KDoc de `deleteMember` ya describía, incorrectamente hasta este fix, que la UI permitía expulsar al owner).
- **Fix aplicado**: separados los gates de "cambiar rol" (sigue bloqueado sobre el owner, porque cambiar solo `role` no transfiere `ownerId`) y "expulsar" (ahora permitido sobre el owner).
- **Caveat SIN RESOLVER**: no hay evidencia de que `firestore.rules` v10 se haya desplegado a producción. Si se despliega la UI sin desplegar las reglas, se reintroduce el 403 silencioso original — **ahora peor** que antes, porque el bloqueo de UI que lo prevenía ya no existe (ver hallazgo CRÍTICO de Funcionalidad end-to-end).
- **Estado**: NUEVO → APLICADO (parcialmente: fix de código hecho, deploy de reglas pendiente de confirmación externa).

### IMPORTANTE — pérdida silenciosa de un hogar por condición de carrera entre dos dispositivos — NUEVO, PROPUESTA
- **Problema**: `syncHouseholdsToCloud()` sube la lista COMPLETA de hogares no-personales como reemplazo total (no `arrayUnion`/merge). Si el mismo usuario crea/une un hogar en dos dispositivos en una ventana de segundos, el segundo PATCH en completarse sobrescribe el array entero y borra silenciosamente el ID que el otro dispositivo acababa de añadir.
- **Evidencia**: `GoogleAuthManager.kt:447-462`, `FirestoreRepository.kt:258-273`, `HouseholdScreenModel.kt:89,125,194,216`.
- **PROPUESTA**: usar `fieldTransforms` (`appendMissingElements`/`removeAllFromArray`) vía `:commit`, o releer `loadUserHouseholds` fresco antes de cada sync y unir localmente. Cualquiera de las dos requiere pruebas contra Firestore real/emulador.
- **Estado**: NUEVO.

### Verificado sin hallazgo
Doble creación de miembro al reintentar unirse (ya protegido, `documentId=userId` determinista + comprobación de duplicados); `WelcomeScreen.kt` confirmado código muerto; `AuthGateScreen` robusto (sin ruta alternativa que asuma usuario sin cuenta).

### SIGUE ABIERTO (ya conocido, confirmado sin cambio)
Balance no-atómico de puntos en donaciones/canjes simultáneos desde el mismo miembro (`docs/atomicidad-commit-pendiente.md`), requiere backend.

---

## 4. Seguridad/AppSec

### CRÍTICO — la eliminación de auth anónima es solo de UX/cliente, no cierra ninguna superficie server-side — SIGUE ABIERTO, PROPUESTA (infra)
- **Problema**: `firestore.rules` y Cloud Functions no distinguen NUNCA el proveedor de autenticación (`signedIn()` es solo `request.auth != null`). El proveedor anónimo de Firebase Auth sigue activo server-side. Cualquiera —no solo un cliente modificado— puede llamar directo a `accounts:signUp` con la apiKey pública ya embebida y obtener un UID anónimo + idToken para crear `households`/`members` exactamente igual que el flujo eliminado de la UI.
- **Por qué importa**: el bug de sincronización original (hogares huérfanos) puede seguir reproduciéndose por fuera de la app, sin ni siquiera pasar por Task Hub, hasta que el proveedor anónimo se desactive server-side.
- **Evidencia**: `firestore.rules` (0 menciones de proveedor), `functions/src/auth.ts:17-21`, `docs/google-only-auth-2026-09-12.md:11-13`.
- **PROPUESTA**: confirmar con el orquestador que el proveedor anónimo se desactiva ANTES o EN el mismo despliegue que publique esta versión.
- **Estado**: SIGUE ABIERTO — riesgo ya anticipado por el encargo anterior, ahora verificado con el detalle del vector concreto.

### CRÍTICO — `isValidOwnerSuccession` permite auto-nombramiento sin expulsión real — SIGUE ABIERTO, PROPUESTA
- **Problema**: la regla (`firestore.rules:383-391`) no exige que el owner actual esté siendo expulsado ni que el nuevo owner sea distinto del propio llamador — un admin puede ejecutar el mismo PATCH con su propio UID, en cualquier momento, sin que nadie sea dado de baja.
- **Por qué importa**: `ownerId` concede privilegios EXCLUSIVOS (borrar el hogar entero, gestionar invites) que ni siquiera un admin normal tiene. La cabecera de `firestore.rules` ya reconoce el escenario pero subestima su alcance (lo trata como gobierno interno, cuando en realidad gatea acciones destructivas).
- **Evidencia**: `firestore.rules:383-391,404-407,422`, `HouseholdRepository.kt:328-337`.
- **PROPUESTA**: exigir que el owner actual ya tenga `leftAt != 0` antes de permitir la transferencia — requiere invertir el orden de `deleteMember` (hoy transfiere `ownerId` ANTES del soft-delete) o resolverlo con una transacción `:commit`. No aplicado (toca `firestore.rules`, requiere deploy).
- **Nota de convergencia**: el fix de QA de esta misma ronda (reactivar expulsar al owner en la UI) hace que el camino "legítimo" hacia esta regla sea de nuevo alcanzable desde la app normal, aumentando la exposición práctica de este hallazgo.
- **Estado**: SIGUE ABIERTO — riesgo ya documentado y "aceptado" en la cabecera v10, análisis de impacto ampliado en esta ronda.

### IMPORTANTE — `requestSignInWithIdp` no redactaba la apiKey de sus errores — NUEVO, APLICADO
- **Problema**: a diferencia de `refreshFirebaseToken`/`deleteFirebaseAccount`, este endpoint (usado por `signInWithGoogle`) no pasaba sus excepciones por `redactApiKey()`.
- **Por qué importa**: `AuthGateScreen` (único punto de entrada) pinta `authState.message` literalmente; un fallo de transporte durante el login podía filtrar la apiKey al Snackbar visible y a logs de crash/analytics.
- **Evidencia**: `FirestoreRepository.kt:213-224` (antes), `FirestoreClient.kt:343-347` (`redactApiKey`, antes `private`).
- **Fix aplicado**: `redactApiKey` pasa a `internal`; `requestSignInWithIdp` envuelto en try/catch con el mismo patrón que los otros dos endpoints.
- **Estado**: NUEVO, APLICADO.

### Verificado sin hallazgo
Alineación cliente↔reglas tras simplificar `signInWithGoogle` (sin vía nueva de suplantación); apiKey embebida/`tryAuthOrApiKey` sin cambios; manejo de tokens en `AuthGateScreen`/`GoogleAuthManager` (sin logging sensible); puntos vía Cloud Functions sin vía de escritura directa nueva.

---

## 5. Programador senior

Sin críticos nuevos: no se encontró ningún `catch` que trague un error de red y deje al usuario en un spinner infinito sin transición a `Error`.

### IMPORTANTE — `isTransientReadFailure()` con `cause` recursivo sin test — NUEVO, APLICADO
Añadidos 3 tests a `FirestoreClientRetryTest.kt` (envoltorio 1 nivel, 2 niveles, causa no transitoria) para la lógica añadida en la Oleada A. Verificado con `jvmTest` filtrado en verde.

### Verificado sin hallazgo
`launchGoogleSignIn()` en Android con context null (inalcanzable en la práctica); `GoogleSignInHelper` (ventanas de carrera ya cubiertas); `resolveCurrentMemberUncached` (null-safe); `syncHouseholdsToCloud` (sin fuga cruzada entre cuentas por captura síncrona de UID antes del `launch`); `ensureAuth()`/`setAuthState()` (sin ventanas nuevas de concurrencia).

### SIGUE ABIERTO (ya conocido, confirmado sin cambio)
`completeTask`/`completeAssignment` duplicación; `TaskScreenModel` god object sin test; `MainActivity.consumeDeepLink` con `HouseholdStore(Settings())` al vuelo.

---

## 6. Funcionalidad end-to-end

### CRÍTICO — fallo de sucesión de `ownerId` silencioso de extremo a extremo, sin ninguna señal en la UI — SIGUE ABIERTO (converge con QA/Seguridad)
- **Problema**: `deleteMember` (`FirestoreRepository.kt:838-852`) atrapa en un `catch (_: Exception) {}` best-effort el fallo del PATCH de transferencia de `ownerId`. Si `firestore.rules` v10 no está desplegada, el soft-delete del miembro sigue adelante igual, y `MemberScreenModel.removeMember` marca `Success` sin distinguir "expulsión completa" de "expulsión con sucesión rota".
- **Qué ve el owner expulsado**: si la transferencia falló, `ownerId` sigue siendo su UID — conserva permisos reales de owner (borrar el hogar, ver la lista completa de miembros) PARA SIEMPRE, pese a estar soft-deleted como miembro. Ningún admin puede volver a sucederle.
- **Evidencia**: `FirestoreRepository.kt:838-852`, `firestore.rules:329-341,383-391,411-420`, `MemberScreenModel.kt:171-190`, `HouseholdMemberList.kt:334-368`.
- **PROPUESTA**: (a) confirmar/ejecutar el deploy de `firestore.rules` v10 antes de publicar; (b) hacer que `deleteMember` devuelva si la sucesión tuvo éxito, y que la UI muestre un aviso distinto cuando falla, en vez de un "éxito" genérico.
- **Estado**: SIGUE ABIERTO — verificado end-to-end desde la UI hasta las reglas, converge con el hallazgo de QA/Seguridad de la Oleada A pero aporta la traza completa.

### IMPORTANTE — deep link local perdido en la ventana logout→login — NUEVO, PROPUESTA
Interacción emergente entre el fix de arquitectura de esta ronda (`signOut()` limpia `HouseholdStore`) y la mitigación anti-spoofing de `MainActivity.consumeDeepLink` (descarta deep links a hogares que no estén ya en `HouseholdStore`). Un recordatorio local que sobrevive a un logout/re-login con la misma cuenta puede perderse en silencio. PROPUESTA sin aplicar (toca el único mecanismo anti-spoofing de una Activity exportada, decisión de seguridad).

### IMPORTANTE — hogar del que se perdió membresía nunca se poda de la lista de Home — NUEVO, PROPUESTA
`reconcileHouseholds`/`loadHousehold` solo detectan pérdida de acceso vía 404 de `getHousehold` (que tiene `allow get: if signedIn()` sin exigir membresía) — nunca reciben un 403 real por esta vía, así que un miembro/owner expulsado sigue viendo el hogar indefinidamente. PROPUESTA sin aplicar (requiere extender la detección a la respuesta de `getMembers`/`getTasks`).

### Verificado sin hallazgo
Timing de subida de token FCM (correcto, solo tras `SignedIn`); recompensas/recurrencia sin dependencia de auth anónima; `WelcomeScreen` código muerto confirmado.

---

## 7. Cobertura de pruebas (solo informa)

Confirmó que el test de `isTransientReadFailure` (hallazgo del programador senior) ya estaba cubierto en el working tree compartido.

### Huecos propuestos (sin aplicar, rol solo-informa)
- Extraer `redactApiKey()` a función testeable directamente (lógica pura sin test propio, de la que depende el fix crítico de red/offline).
- Extraer la clasificación podar/conservar de `HouseholdRepository.reconcileHouseholds` a una función pura `shouldPruneHousehold()` (misma familia de bug que motivó el fix crítico de la Oleada A, pero sin extraer ni testear).
- Extraer los gates de `HouseholdMemberList` (cambiar rol / expulsar) a un objeto `MemberActionRules` testeable — es precisamente la lógica del fix crítico de QA de esta ronda, actualmente solo verificable manualmente.
- `GoogleAuthManager` sigue con 0% de cobertura pese a orquestar todo el subsistema de sync; identificado qué parte es testeable hoy sin mocking HTTP nuevo (los efectos síncronos de `signOut()`) vs. qué parte requiere `MockEngine` de Ktor (gap arquitectónico ya conocido).

### Veredicto honesto
~60-65% de la lógica de decisión del flujo de sync ya está en funciones puras testeadas; del resto, la mitad es testeable hoy sin infraestructura nueva (simplemente no escrito), la otra mitad requiere introducir un `HttpClient` inyectable — cambio de infraestructura de test, no un simple test más.

### Tendencia (SIGUE ABIERTO, patrón recurrente)
247 tests (pre Google-only) → 239 (tras `9e36410`) → 239 tras la Oleada A (0 tests nuevos pese a 4 archivos de producción tocados) → 242 al cierre de esta ronda (por el trabajo puntual de un agente, no por disciplina sistemática). Mismo patrón ya señalado en v9-reintento.

---

## 8. UX

### CRÍTICO — `AuthGateScreen` mostraba texto de error crudo de Identity Toolkit — NUEVO, APLICADO
- **Problema**: `handleGoogleToken` usaba `e.message ?: fallback`, exponiendo códigos técnicos (`INVALID_IDP_RESPONSE`, `TOO_MANY_ATTEMPTS_TRY_LATER`) en la pantalla más crítica del arranque.
- **Evidencia**: `GoogleAuthManager.kt:339-355`.
- **Fix aplicado**: siempre el string genérico traducido `google_auth_error_sign_in`, consistente con el patrón `appreciateErrorKey`/`donateErrorKey` ya existente en `MemberScreenModel`.
- **Estado**: NUEVO, APLICADO.

### (aplicado también en esta sección) Copy condicional al expulsar al owner — NUEVO, APLICADO
Diálogo de confirmación con título/texto específico (`member_remove_confirm_title_owner`/`text_owner`, ES+EN) avisando de la transferencia automática de propiedad antes de confirmar — cierra el hueco de que el admin no sabía que estaba a punto de cambiar el dueño del hogar.

### IMPORTANTE — `signOut()` sin confirmación pese a vaciar la lista local de hogares al instante — NUEVO, PROPUESTA
Tras el fix de Arquitectura de esta ronda, cerrar sesión ahora SÍ tiene un efecto visible inmediato (antes no, ese era el bug). `SettingsSheet.kt` dispara `signOut()` con un solo tap, sin `AlertDialog` de confirmación ni copy que avise. PROPUESTA sin aplicar (decisión de producto: ¿vale la pena la fricción para una acción no destructiva de datos, solo de visibilidad local?).

### Verificado sin hallazgo
Copy de `auth_gate_subtitle` ya explica el motivo del login; botón de login sigue pulsable tras un error sin reiniciar la app; sin hueco de pantalla en blanco entre login y `HomeScreen`; mensaje visto por un usuario expulsado al reabrir su hogar (`FIRESTORE_GONE_MESSAGE` + botón de limpiar) es comprensible y accionable — el problema es CUÁNDO se dispara esa detección (ver hallazgo de Funcionalidad end-to-end), no el copy en sí.

---

## 9. UI y componentes

### IMPORTANTE — `AuthGateScreen` sin padding de barras del sistema — NUEVO, APLICADO
- **Problema**: pintado fuera del `Box` que aplica `statusBarsPadding`/`navigationBarsPadding` en el resto de `App.kt`, pese a que la app activa edge-to-edge globalmente.
- **Por qué importa**: único punto de entrada de la app; en pantallas bajas (landscape, foldables plegados) el logo/botón podían quedar bajo las barras del sistema sin ninguna vía de escape.
- **Fix aplicado**: `.statusBarsPadding()` + `.navigationBarsPadding()` en la `Column` raíz.
- **Estado**: NUEVO, APLICADO.

### (aplicado también, ronda anterior a la formal) Botón de login sin salto de layout
El botón permanece en su sitio con spinner en vez de reemplazarse por un bloque centrado aparte — mismo patrón que `CreateHouseholdScreen`/`JoinHouseholdScreen`.

### Verificado sin hallazgo
Icono de error consistente con >20 usos en el resto de la app (no cambiarlo); sin `material-icons-extended`; sin iconos espejados mal migrados; badge de owner (👑, atado a `role`, no a `isOwner`) sin inconsistencia tras la expulsión (el miembro desaparece, no queda badge huérfano); sin código huérfano en `HomeScreen.kt` tras quitar el prompt de Google; consistencia visual con `SplashScreen`.

---

## 10. Estética/visual

### IMPORTANTE — spinner de login casi invisible sobre el botón deshabilitado — NUEVO, APLICADO
- **Problema**: contraste ~1.3:1 del `CircularProgressIndicator` (`onPrimary`) sobre el `disabledContainerColor` (`onSurface` al 12%).
- **Fix aplicado**: color cambiado a `onSurface.copy(alpha=0.38f)`, el `disabledContentColor` estándar de M3.
- **Estado**: NUEVO, APLICADO.

### MENOR — mismo patrón en `CreateHouseholdScreen`/`JoinHouseholdScreen` — NUEVO, PROPUESTA
Mismo bug de contraste del spinner en 3 puntos más, fuera del foco de sync de esta ronda. PROPUESTA sin aplicar.

### Verificado sin hallazgo
Jerarquía visual y modo oscuro de `AuthGateScreen` (sin colores hardcodeados); sin hueco visual tras eliminar el prompt de Google; espaciado de `HouseholdMemberList` tras separar los gates; `DestructiveConfirmDialog` con el texto largo del owner (M3 hace scroll interno, sin desbordamiento).

### SIGUE ABIERTO (ya conocido)
Logo/splash con colores de marca hardcodeados, ignora temas Naturaleza/Minimal — ahora también visible en `AuthGateScreen`.

---

## 11. Accesibilidad WCAG AA

### CRÍTICO — mensaje de error de login sin `liveRegion` — NUEVO, APLICADO
TalkBack/VoiceOver no anunciaban el error de login; único punto de entrada, sin otra pantalla a la que navegar para "descubrirlo". Fix aplicado: `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`, mismo patrón que el resto de la app desde `5202d42`.

### CRÍTICO — botón de login sin nombre accesible durante `SigningIn` — NUEVO, APLICADO
Al desaparecer el `Text` interior (sustituido por el spinner), el botón se queda sin nombre accesible; TalkBack anuncia "botón, deshabilitado" sin contexto. Fix aplicado: `contentDescription` condicional + nueva clave i18n `auth_gate_signing_in` (ES/EN).

### CRÍTICO — contraste del spinner sigue bajo 3:1 tras el fix de estética — NUEVO, PROPUESTA de refinamiento
Recalculado el contraste tras el fix de Estética (`onSurface.copy(alpha=0.38f)`): mejora sustancialmente pero sigue sin llegar a 3:1 en 5/6 combinaciones tema×modo (entre 2.09:1 y 3.00:1). PROPUESTA sin aplicar (para no pisar el fix en curso del otro experto): usar `disabledContainerColor=primary`/`disabledContentColor=onPrimary` durante `SigningIn`, ya que "deshabilitado" aquí solo significa "ocupado", no "no disponible".

### MENOR — `contentDescription` de expulsar no distingue owner — NUEVO, PROPUESTA
No bloqueante (SC 4.1.2 no exige anticipar consecuencias en el nombre accesible, y el diálogo de confirmación sí las aclara). PROPUESTA de mejora, no aplicada.

### Verificado sin hallazgo
Contraste de subtítulo/error contra fondo (todas las combinaciones >4.5:1); touch target del botón (56dp, sobre el mínimo); orden de foco de teclado (JVM/Desktop, trivial); foco de accesibilidad al abrir `DestructiveConfirmDialog` (gestionado automáticamente por M3); `MemberCard` sin patrón de botón anidado problemático.

---

## 12. Rendimiento

### CRÍTICO — `restoreFromCloudOnStartup()` repetía una llamada de red que `App.kt` ya había hecho — NUEVO, APLICADO
- **Problema**: `repointPersonalHousehold()` (llamado dentro de `restoreFromCloudOnStartup`) volvía a hacer el mismo `GET households/{personal_uid}` que `App.kt` ya acababa de resolver un instante antes.
- **Por qué importa**: duplicaba un round-trip de red completo en TODOS los arranques en frío.
- **Fix aplicado**: eliminada la llamada redundante dentro de `restoreFromCloudOnStartup` (el único llamante, `App.kt`, ya resuelve el hogar Personal con más matices, incluido fallback offline).
- **Estado**: NUEVO, APLICADO.

### IMPORTANTE — `restoreHouseholds` secuencial en vez de batch paralelo — NUEVO, APLICADO
Sustituido el `for` con `getHousehold(id)` uno a uno por `repo.getHouseholds(ids)` (batch `async`/`awaitAll` ya existente en el codebase para el mismo propósito). Con varios hogares compartidos, evita encadenar N round-trips en serie.

### IMPORTANTE — subida de token FCM al final de una cadena secuencial sin necesidad — NUEVO, APLICADO
Movida a un `launch` en paralelo dentro del mismo `LaunchedEffect`, ya que escribe en un documento (`users/{uid}.fcmToken`) que ningún otro paso del bootstrap toca.

### PROPUESTA sin aplicar
Paralelizar más la cadena hogar Personal / restaurar hogares compartidos requeriría antes serializar las escrituras a `HouseholdStore` (sin lock hoy) para evitar que se pisen entre sí.

### Verificado sin hallazgo
Coste de `isTransientReadFailure()` en el camino caliente (nulo, solo se evalúa en la rama de excepción); recomposición de `authState` (solo en transiciones discretas, no periódica); coste de `HouseholdStore.clearAll()` (barato, no bloquea el hilo principal); `syncHouseholdsToCloud()` ya hace una sola escritura con `updateMaskFieldPaths`.

### SIGUE ABIERTO (ya conocido, fuera de foco de esta ronda)
`HouseholdScreen` sin memoización en polling; `CalendarSyncManager` en serie; mensajes/notificaciones sin paginación.

---

## 13. Privacidad/RGPD/menores

### CRÍTICO — `docs/privacy.html` afirmaba falsamente que se podía usar la app "de forma anónima" — NUEVO, APLICADO
- **Problema**: la política describía el login de Google como opcional, pese a que `9e36410` lo hizo obligatorio desde el primer uso.
- **Por qué importa**: declaración de privacidad objetivamente falsa sobre un cambio de postura significativo (RGPD Art. 13 exige información exacta).
- **Evidencia**: `docs/privacy.html:44` (versión anterior).
- **Fix aplicado**: reescrita la viñeta de "Datos de cuenta" para reflejar que el login es obligatorio y que también se guarda la foto de perfil (ya cierto desde antes, nunca documentado explícitamente).
- **Estado**: NUEVO (introducido por `9e36410`, no auditado hasta ahora).

### IMPORTANTE — `AuthGateScreen` no enlaza la política de privacidad antes del login — NUEVO, PROPUESTA
El único enlace a la política vive en `SettingsSheet`, accesible solo DESPUÉS del login obligatorio. Antes, con auth anónima, el usuario podía explorar la app antes de compartir su identidad real; ahora el OAuth de Google ocurre en la primerísima interacción sin el aviso visible en ese mismo paso. PROPUESTA de producto/UX, no aplicada (riesgo de colisión con otros expertos tocando la misma pantalla).

### Verificado sin hallazgo
Borrado de cuenta en cascada intacto (`GoogleAuthManager.deleteAccount()`, ya no recrea un "Personal" anónimo tras borrar, coherente con la política); modelo de "hijo/a sin cuenta propia" (`member.userId == null`) no afectado por el gate — el login obligatorio es solo para el adulto/dispositivo, no fuerza a cada persona a tener cuenta de Google propia.

### SIGUE ABIERTO (ya conocido, sin regresión)
Sin SDK de consentimiento UMP/CMP para tráfico EEE/UK; mitigación parcial intacta (`TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` + `MAX_AD_CONTENT_RATING_G` antes de `MobileAds.initialize()`), sin cambio respecto a antes del gate de login.

---

## Verificación final

- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` → **BUILD SUCCESSFUL**.
- `./gradlew :composeApp:jvmTest --console=plain` → **BUILD SUCCESSFUL**. Conteo real parseado de `composeApp/build/test-results/jvmTest/*.xml`: **242 tests, 0 failures, 0 errors** (239 al HEAD de partida + 3 nuevos de `FirestoreClientRetryTest`).

## Resumen de archivos modificados (las 3 oleadas)

- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreClient.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdMemberList.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/GoogleAuthManager.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/App.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/AuthGateScreen.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt`
- `composeApp/src/commonTest/kotlin/org/taskhub/network/FirestoreClientRetryTest.kt`
- `docs/privacy.html`
- `docs/panel-expertos-sync-2026-09-12-progreso.md` (progreso oleada a oleada)

## Propuestas pendientes de mayor prioridad (requieren decisión, no aplicadas)

1. **Confirmar/ejecutar el deploy de `firestore.rules` v10** (`isValidOwnerSuccession`) antes de publicar esta versión — sin él, expulsar al owner falla en silencio y deja un owner fantasma con control total (CRÍTICO, QA + Funcionalidad end-to-end + Seguridad convergen en esto).
2. **Desactivar el proveedor anónimo de Firebase Auth en la consola** al publicar — sin ello, la eliminación de auth anónima en el cliente no cierra la superficie real (Seguridad, Red/offline).
3. **Endurecer `isValidOwnerSuccession`** para exigir expulsión real (`leftAt != 0`) antes de permitir la transferencia de `ownerId` — evita auto-nombramiento sin expulsión (Seguridad).
4. **Decisión de producto sobre iOS/JVM**: implementar Google Sign-In real o acotar "Google-only" a Android en la documentación/build (Arquitectura, ya señalado por el encargo anterior).
5. Resto de propuestas menores/UX/rendimiento/tests documentadas por sección arriba.
