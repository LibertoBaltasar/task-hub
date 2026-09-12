# Panel de expertos v10 — foco sincronización entre dispositivos — progreso (2026-09-12)

HEAD de partida: `9e3641085fa59c4e2b15adff10512f716f607f78` (`feat: elimina
la auth anónima, Task Hub pasa a requerir login con Google`). Revisión NUEVA
(no continuación de v9). Working tree limpio al empezar.

**Cambio de contexto clave respecto a encargos anteriores**: el encargo 00
(`docs/auditoria-sync-dispositivos-2026-09-12.md`, HEAD `8c3f3d3`) diagnosticó
y arregló (`8cc954d`) el huérfano anónimo→Google vinculando la credencial. Un
encargo posterior (`docs/google-only-auth-2026-09-12.md`, commit `9e36410`,
SIN PUSH) fue más allá: **eliminó la auth anónima por completo** — Task Hub
ahora exige login con Google desde el arranque (`AuthGateScreen`). Esto deja
obsoleto el mecanismo de vinculación (`AccountLinkingRules`, borrado) porque
ya no existe una sesión anónima activa que vincular. El foco de esta ronda es
verificar que el flujo Google-only en sí sincroniza bien entre dispositivos
(2º dispositivo, mismo dispositivo tras logout/login, borrado de cuenta,
iOS/JVM sin Google Sign-In real ya documentado como propuesta pendiente).

Reglas de oleadas: máximo 4-5 subagentes Task simultáneos, secuenciales,
consolidar (commit) antes de avanzar. Si una oleada falla por `session limit`:
esperar ~10 min, reintentar UNA vez; si vuelve a fallar, documentar y seguir
con la siguiente oleada sin fabricar hallazgos.

## Reparto de oleadas

- **Oleada A (4):** (1) Red/offline/sincronización, (2) Arquitectura, (3) QA y
  bugs, (4) Seguridad/AppSec.
- **Oleada B (5):** (5) Programador senior, (6) Funcionalidad end-to-end,
  (7) Cobertura de pruebas (solo informa), (8) UX, (9) UI y componentes.
- **Oleada C (4):** (10) Estética/visual, (11) Accesibilidad WCAG AA,
  (12) Rendimiento, (13) Privacidad/RGPD/menores.

## Estado

- [x] Oleada A — COMPLETADA (falló 4/4 por `session limit` en el primer intento sin producir informe; reintentada UNA vez tras el reset, 4/4 con hallazgos):
  - **Red/offline/sync**: CRÍTICO nuevo aplicado — `FirestoreClient.ensureAuth()` borraba la sesión de Google ante CUALQUIER fallo al refrescar el token (incluido un simple timeout/sin red), no solo ante un refresh token realmente inválido; un usuario podía quedar deslogueado permanentemente por abrir la app una vez sin conexión. Fix: distinguir fallo transitorio (`isTransientReadFailure()`, ahora también mira `cause` porque `redactApiKey` reenvuelve la excepción) de uno real. 1 hallazgo IMPORTANTE documentado como propuesta (proveedor anónimo de Firebase Auth aún activo server-side, pendiente de desactivar en consola tras publicar). Resto YA RESUELTO/sin cambios.
  - **Arquitectura**: CRÍTICO nuevo aplicado — `GoogleAuthManager.signOut()` no limpiaba `HouseholdStore`, filtrando hogares de la cuenta saliente a la cuenta entrante en un dispositivo familiar compartido (agravado por `syncHouseholdsToCloud` subiendo esa fuga a `users/{uidB}.householdIds` de forma permanente). Fix: `householdStore.clearAll()` en `signOut()`. 2 PROPUESTAS documentadas sin aplicar (desincronización `FirestoreClient`↔`GoogleAuthManager.state` sin evento de invalidación de sesión; deep link que sobrevive a un cambio de cuenta en caliente). iOS/JVM bloqueados confirmado SIGUE ABIERTO (ya conocido). Deuda menor de `currentUserIdentities()`/`resolveExistingMemberId` ya sin caso de uso real, documentada sin aplicar.
  - **QA y bugs**: CRÍTICO nuevo aplicado — el fix de sucesión de `ownerId` (`firestore.rules` `isValidOwnerSuccession`, commit `f92aa0b`) era inalcanzable desde la UI porque el bloqueo de "no expulsar al owner" de la ronda v9-reintento nunca se retiró. Fix: `HouseholdMemberList.kt` separa el gate de "cambiar rol" (sigue bloqueado sobre el owner) del de "expulsar" (ahora permitido). **Caveat sin resolver**: no hay evidencia de que `firestore.rules` v10 esté desplegada a producción — sin ese deploy, este fix de UI reintroduce el 403 silencioso original. 1 IMPORTANTE documentado como propuesta (pérdida silenciosa de un hogar en `users/{uid}.householdIds` por condición de carrera entre dos dispositivos, `syncHouseholdsToCloud` hace reemplazo total sin merge). Confirma SIGUE ABIERTO el balance no-atómico de puntos (ya conocido, requiere backend).
  - **Seguridad/AppSec**: 1 CRÍTICO documentado como PROPUESTA (infra, no código) — la eliminación de auth anónima es solo de UX/cliente, `firestore.rules`/Cloud Functions no distinguen proveedor, así que cualquiera puede seguir generando UIDs anónimos por REST directo con la `apiKey` pública mientras el proveedor siga activo en Firebase Auth. 1 CRÍTICO documentado como PROPUESTA (toca `firestore.rules`) — `isValidOwnerSuccession` permite a un admin auto-nombrarse owner sin que haya expulsión real (privilegios exclusivos: borrar hogar, gestionar invites). 1 IMPORTANTE nuevo aplicado — `requestSignInWithIdp` no redactaba la apiKey de mensajes de error (podía filtrarse a `AuthGateScreen`, único punto de entrada). Resto YA RESUELTO/sin cambios.
  - Verificación combinada de las 4 correcciones: `compileDebugKotlinAndroid` BUILD SUCCESSFUL, `jvmTest` BUILD SUCCESSFUL (239 tests, 0 fallos, 0 errores).
- [x] Oleada B — COMPLETADA (programador senior, funcionalidad end-to-end y cobertura de tests 3/3 a la primera; UX y UI/componentes fallaron por `session limit` a mitad, reintentados UNA vez tras el reset — el trabajo parcial que ya habían aplicado antes de fallar sobrevivió en un checkpoint automático y se verificó correcto):
  - **Programador senior**: sin críticos nuevos. Añadió 3 tests unitarios a `FirestoreClientRetryTest.kt` para `isTransientReadFailure()` con `cause` recursivo (lógica de la Oleada A que se había quedado sin cobertura). Confirmó SIGUE ABIERTO deuda ya conocida (completeTask/completeAssignment duplicado, TaskScreenModel god object, tryAuthOrApiKey YA RESUELTO).
  - **Funcionalidad end-to-end**: CRÍTICO verificado end-to-end (converge con QA/Seguridad de Oleada A, no duplicado) — si `firestore.rules` v10 (`isValidOwnerSuccession`) no está desplegada a producción, expulsar al owner falla en silencio (`deleteMember` traga el 403 del PATCH de `ownerId`) y el owner expulsado conserva permisos reales para siempre, sin ningún aviso en la UI. 2 IMPORTANTES nuevos documentados como propuesta: deep link local perdido en la ventana logout→login (interacción nueva entre el fix de Oleada A y la mitigación anti-spoofing de `MainActivity`); hogar del que se perdió membresía nunca se poda de la lista de Home (solo se detecta 404, nunca "403 de members"). Confirmado YA RESUELTO: timing de subida de token FCM, recompensas/recurrencia sin dependencia de auth anónima, `WelcomeScreen` muerta.
  - **Cobertura de tests** (solo informa): confirmó que el test de `isTransientReadFailure` ya estaba cubierto (por el agente de programador senior, en paralelo). Propuso extraer `redactApiKey` y la clasificación podar/conservar de `reconcileHouseholds` a funciones puras testeables (mismo patrón que `isTransientReadFailure`), y extraer los gates de `HouseholdMemberList` (cambiar rol / expulsar) a un objeto `MemberActionRules` testeable. Veredicto: ~60-65% de la lógica de decisión de sync ya está en funciones puras testeadas; del resto, la mitad es testeable hoy sin mocking HTTP nuevo (simplemente no escrito), la otra mitad requiere infraestructura de `MockEngine` de Ktor (gap arquitectónico ya conocido).
  - **UX** (tras reintento): CRÍTICO nuevo aplicado — `GoogleAuthManager.handleGoogleToken` mostraba `e.message` crudo (código de error de Identity Toolkit sin traducir) en `AuthGateScreen`, el único punto de entrada de la app; ahora siempre usa el string genérico `google_auth_error_sign_in`. Además aplicó copy condicional en el diálogo de confirmación de expulsar al owner (`member_remove_confirm_title_owner`/`text_owner`, ES+EN) antes de fallar por session limit — sobrevivió en el checkpoint, verificado correcto. 1 IMPORTANTE documentado como propuesta: `signOut()` sin confirmación pese a que ahora (tras el fix de Oleada A) vacía la lista local de hogares compartidos al instante.
  - **UI y componentes** (tras reintento): IMPORTANTE nuevo aplicado — `AuthGateScreen` sin `statusBarsPadding()`/`navigationBarsPadding()` pese a ser el único punto de entrada pintado fuera del `Box` que aplica ese padding en el resto de la app; en pantallas bajas (landscape, foldables plegados) el logo/botón podían quedar bajo las barras del sistema sin ninguna vía de escape. También aplicó (antes del fallo por session limit) que el botón de login permanezca en su sitio con spinner en vez de bloque separado — sobrevivió en el checkpoint, verificado correcto. Confirmó YA RESUELTO/sin hallazgo: icono de error consistente con el resto de la app, sin iconos extended/mal migrados, sin código huérfano tras quitar el prompt de Google, badge de owner sin inconsistencia tras expulsión.
  - Verificación combinada: `compileDebugKotlinAndroid` BUILD SUCCESSFUL, `jvmTest` BUILD SUCCESSFUL (242 tests, 0 fallos, 0 errores).
- [ ] Oleada C — pendiente
- [ ] Consolidación final + informe `docs/review-panel-expertos-sync-2026-09-12.md`
- [ ] Verificación final (`compileDebugKotlinAndroid`, `jvmTest`)
- [ ] Commit único final

(Este archivo se actualiza tras cada oleada para poder retomar si la sesión
se corta a mitad.)
