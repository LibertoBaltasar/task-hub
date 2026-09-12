# Auditoría de sincronización entre dispositivos — 2026-09-12

HEAD de partida: `8c3f3d33325a8b6e4a563690a88450b74c3b0c12` (`chore: bump
versión 0.7.32`). Contexto: la base de datos Firestore de producción se vació
por completo el mismo día (1.023 docs — 374 hogares, 171 huérfanos, 42
usuarios anónimos, 27 invites) por decisión de Liberto, para poder verificar
la sincronización desde cero.

## Problema reportado

Hogares **compartidos** creados en modo anónimo quedaban huérfanos al iniciar
sesión con Google en ese mismo dispositivo: `ownerId` seguía apuntando al UID
anónimo, ningún miembro estaba vinculado al UID de Google, y
`users/{uidGoogle}.householdIds` no reflejaba esos hogares — así que un
segundo dispositivo, ya logueado con esa cuenta de Google, no los veía.

## Flujo trazado

- `App.kt:148-191` — arranque en frío: crea/resuelve el espacio Personal
  determinista (`personal_{uid}`) y llama a
  `GoogleAuthManager.restoreFromCloudOnStartup()`.
- `GoogleAuthManager.kt:329-345` (`handleGoogleToken`) — al recibir el idToken
  de Google: `repo.signInWithGoogle(idToken)` → `restoreHouseholds(uid)` →
  `repointPersonalHousehold()` → `syncHouseholdsToCloud()`.
- `GoogleAuthManager.kt:373-391` (`restoreHouseholds`) — lee
  `users/{uid}.householdIds` (Firestore) y guarda cada hogar en el
  `HouseholdStore` local.
- `GoogleAuthManager.kt:459-474` (`syncHouseholdsToCloud`) — sube los hogares
  **no-personales** del `HouseholdStore` local a `users/{uid}.householdIds`.
- `FirestoreRepository.kt` (`signInWithGoogle`, antes de este fix) —
  intercambiaba el idToken de Google por credenciales de Firebase vía
  `accounts:signInWithIdp` **sin vincular la sesión anónima activa**: siempre
  pedía/devolvía un UID de Google nuevo y distinto del UID anónimo.
- `HouseholdRepository.kt:81-119` (`createHousehold`) — al crear un hogar
  compartido en modo anónimo, `ownerId = firestoreClient.getLocalId()` (el UID
  anónimo vigente en ese momento).
- `MemberRepository.kt:161-248` (`createMember`) — el miembro vinculado a
  cuenta se crea con `documentId = userId` (el UID anónimo) — `members/{mid}`
  con `mid == UID anónimo`.
- `firestore.rules:329-341` (`isOwner`/`isMember`) — ambas comprueban
  `request.auth.uid` (el UID del token ACTUAL) contra `ownerId`/el propio
  `mid` del documento de miembro.

## Causa raíz

`FirestoreRepository.signInWithGoogle` (antes del fix) llamaba a
`accounts:signInWithIdp` sin el parámetro opcional `idToken` de Identity
Toolkit (el que sirve para **vincular** una credencial de Google a la sesión
Firebase Auth *ya activa*, en vez de resolver/crear una cuenta permanente
aparte). Sin ese parámetro, el intercambio **siempre** devuelve un UID nuevo
(o el UID permanente ya existente de esa cuenta de Google, si el dispositivo
ya la había usado antes) — nunca el UID anónimo actual.

`FirestoreClient.setAuthState` (llamado justo después) sustituye por completo
el token/UID activos por los de Google, y `signInWithGoogle` limpia además
`settingsStore.clearAnonymousAuth()`. A partir de ahí **no existe ninguna vía
para volver a autenticarse como el UID anónimo**: ni su refresh token se
conserva utilizable a través de `ensureAuth()` (que ya prioriza la sesión de
Google), ni `firestore.rules` permite a un tercero (el nuevo UID de Google)
transferir `ownerId` o crear `members/{uidGoogle}` con los datos del miembro
anónimo preexistente — las reglas `isOwner(hid)`/`isValidOwnerSuccession(hid)`
exigen ser el UID vigente, y la rama de `create` de `members/{mid}` que usa
owner/admin exige `userId == null || userId == request.auth.uid` (impide
crear un miembro con el UID de OTRA persona, precisamente para prevenir
suplantación — ver cabecera de `firestore.rules`, v4/v5). Es decir: **una vez
completado el intercambio de token, la migración ya no es posible desde el
cliente bajo las reglas actuales** — el momento correcto para actuar es
ANTES del intercambio, mientras el UID anónimo todavía puede autenticarse.

En consecuencia:
- `ownerId` del hogar y `members/{UID-anónimo}` quedan apuntando para siempre
  a una identidad que ya no puede volver a autenticarse.
- Si el dispositivo que hizo el login llega a subir esos IDs a
  `users/{uidGoogle}.householdIds` (vía `syncHouseholdsToCloud`, con la lista
  local **todavía** conteniendo esos hogares en el momento del login), un
  segundo dispositivo con la misma cuenta de Google SÍ descubre el ID del
  hogar (households/{hid}` tiene `allow get: if signedIn()`, sin exigir
  membresía) pero no puede leer `members` (`allow read: if isMember(hid) ||
  isOwner(hid)`, 403) ni crearse un miembro propio (`create` exige ya ser
  owner/admin, que tampoco es) — el hogar aparece "roto"/inaccesible o,
  dependiendo del timing de esa subida (best-effort, `fire-and-forget`), ni
  siquiera llega a aparecer.

## Fix aplicado

Se usa el mecanismo estándar de Firebase Auth para este escenario exacto
("subir de categoría" una cuenta anónima): **vincular** la credencial de
Google a la sesión anónima activa en vez de reemplazarla, para que el UID
**no cambie**. Con el mismo UID de siempre, `ownerId`, `members/{uid}` y
`users/{uid}.householdIds` creados en modo anónimo siguen siendo válidos sin
ninguna migración — no hace falta tocar `firestore.rules` en absoluto.

- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreDtos.kt:197-213`
  — `SignInWithIdpRequest` gana el campo opcional `idToken` (se omite del
  body si es `null`, gracias a `encodeDefaults=false` ya usado en el cliente).
- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt:181-292`
  (`signInWithGoogle` + el nuevo helper privado `requestSignInWithIdp`) — si
  el usuario aún no tiene sesión de Google (`AccountLinkingRules.shouldAttemptLinking`),
  se obtiene el idToken anónimo vigente vía `firestoreClient.ensureAuth()` y
  se manda como `idToken` de vinculación en `accounts:signInWithIdp`. Si
  Identity Toolkit rechaza la vinculación con
  `FEDERATED_USER_ID_ALREADY_LINKED` (la cuenta de Google ya se usó antes, en
  este u otro dispositivo, y tiene su propio UID permanente distinto —
  detectado por `AccountLinkingRules.shouldFallBackToPlainSignIn`), se
  reintenta sin vincular: mismo comportamiento que había antes de este fix
  (deja el hogar anónimo de ESE dispositivo sin migrar — limitación ya
  documentada y aceptada en `GoogleAuthManager.repointPersonalHousehold`, un
  escenario distinto al que este fix resuelve).
- `composeApp/src/commonMain/kotlin/org/taskhub/network/AccountLinkingRules.kt`
  (nuevo) — la decisión pura (sin I/O), testeada sin necesidad de mockear
  HTTP: cuándo intentar vincular y cuándo un fallo de vinculación debe
  reintentarse como login normal.

Ningún cambio en `firestore.rules` ni en Cloud Functions: no hacían falta.

### Por qué vincular y no "migrar tras el login"

Se evaluó (y se descartó) migrar el hogar/miembro DESPUÉS del login normal
—transferir `ownerId`, recrear `members/{uidGoogle}` con los mismos puntos/
rol, actualizar `users/{uidGoogle}.householdIds`— pero como se explica en
"Causa raíz", esa migración exigiría, o bien seguir pudiendo autenticarse
como el UID anónimo tras el intercambio (imposible, `setAuthState` lo
sustituye y `clearAnonymousAuth()` descarta su refresh token), o bien ampliar
`firestore.rules` con una rama nueva que permita a un UID *arbitrario* crear
su propio `members/{mid}` copiando el rol/puntos de OTRO documento — una
superficie de escritura mucho más amplia y difícil de acotar con seguridad
que simplemente no cambiar de UID. Vincular la credencial es la solución
nativa de Firebase para exactamente este caso ("upgrade" de cuenta anónima a
permanente) y no reabre ninguna superficie de seguridad nueva.

## Casos cubiertos tras el fix

1. **Login con Google desde anónimo, primera vez que se usa esa cuenta de
   Google en cualquier dispositivo** (el caso reportado): la vinculación
   tiene éxito, el UID no cambia, todos los hogares (propios o donde era
   miembro) creados/unidos en modo anónimo siguen siendo accesibles sin
   ninguna acción adicional.
2. **Segundo dispositivo, login con la MISMA cuenta de Google que ya se
   vinculó en el dispositivo 1**: Identity Toolkit devuelve el mismo UID ya
   promovido (el intento de vinculación del dispositivo 2, con SU PROPIO UID
   anónimo, falla con `FEDERATED_USER_ID_ALREADY_LINKED` porque esa cuenta de
   Google ya está vinculada al UID del dispositivo 1 → fallback a login
   normal → UID correcto, ya con `ownerId`/`members` válidos desde el paso 1).
3. **Cuenta de Google ya usada antes con un UID permanente propio, en un
   dispositivo que ahora está en modo anónimo con datos distintos**: fallback
   a login normal, mismo comportamiento que había antes del fix (los datos
   anónimos de ESE dispositivo no se fusionan — limitación de producto ya
   documentada, no un bug de sincronización).

## Tests

- `composeApp/src/commonTest/kotlin/org/taskhub/network/AccountLinkingRulesTest.kt`
  (nuevo, 6 tests) — cubre `AccountLinkingRules.shouldAttemptLinking`
  (con/sin sesión de Google previa) y `shouldFallBackToPlainSignIn`
  (colisión real, sin intento de vinculación, otro motivo de fallo, mensaje
  nulo).
- No se testea `signInWithGoogle`/`requestSignInWithIdp` de extremo a extremo
  (HTTP real): el codebase no tiene infraestructura de mocking HTTP
  (`MockEngine` de Ktor) para `FirestoreClient`/`FirestoreRepository` en
  ningún test existente — toda la cobertura de este archivo se apoya en
  extraer la lógica de decisión a funciones puras testeables (mismo patrón
  que `HouseholdRules`/`PointsRules`/`MemberResolutionRules`), consistente
  con el resto de la base de código.

### Resultados

- `./gradlew :composeApp:compileDebugKotlinAndroid --console=plain` →
  `BUILD SUCCESSFUL`.
- `./gradlew :composeApp:jvmTest --console=plain` → `BUILD SUCCESSFUL`.
  Conteo real parseado de `composeApp/build/test-results/jvmTest/*.xml`:
  **247 tests, 0 failures, 0 errors** (incluye los 6 nuevos de
  `AccountLinkingRulesTest`).

## Qué queda fuera de este fix (SOLO PROPUESTA)

- **Hogares ya huérfanos antes de este fix** (de haber sobrevivido el vaciado
  de la base de datos, que no es el caso hoy): este fix solo previene
  huérfanos NUEVOS a partir de ahora. Reparar hogares ya huérfanos exigiría sí
  o sí una Cloud Function con Admin SDK (bypasa `firestore.rules`) que, dado
  un `householdId` y un `googleUid` de destino, transfiera `ownerId` y
  reconstruya el miembro — es la migración "a posteriori" descartada arriba
  como solución GENERAL (por la superficie de escritura que abriría en las
  reglas para el cliente), pero como Cloud Function unidireccional y con
  Admin SDK sí sería segura. No implementada: no hay hogares huérfanos reales
  que reparar hoy (base de datos vaciada), y es una pieza aparte (requiere
  decidir cómo identificar qué hogares "adoptar" — ¿por invite code
  recordado?, ¿por deep link?, no hay ningún registro cliente-servidor que
  ate un UID anónimo muerto a la cuenta de Google que lo sucede si el usuario
  nunca llegó a intentar el login estando ese hogar en su `HouseholdStore`
  local).
- **Fusionar datos cuando la cuenta de Google YA tenía su propio UID
  permanente** (caso 3 de la lista de arriba): sigue sin fusionar los datos
  anónimos del dispositivo con los de la cuenta — comportamiento ya existente
  y documentado (no es una regresión de este fix, es la misma limitación que
  ya explicaba `GoogleAuthManager.repointPersonalHousehold`). Fusionar de
  verdad exigiría UI de resolución de conflictos (¿qué hogar es el "bueno"
  cuando ambos tienen tareas?) — decisión de producto, no solo técnica.
