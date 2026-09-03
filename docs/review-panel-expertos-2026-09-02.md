# Panel de expertos — revisión integral (2026-09-03)

Nueva pasada completa del comité de 13 expertos sobre Task Hub, coordinada
mediante subagentes en paralelo (uno por especialista). Cada experto leyó el
código actual del repo (HEAD `b764dc6`, árbol de trabajo limpio — incluye ya
todo lo aplicado por los encargos 15-18 de seguimiento de
`docs/review-panel-expertos-v4.md`), contrastó contra las 4 revisiones
anteriores (v1-v4) y los encargos de seguimiento, y reportó hallazgos
verificables con archivo:línea.

**Esta no es una continuación parcial**: cada experto releyó su área de cero,
sin asumir que un hallazgo antiguo seguía vigente sin comprobarlo.

## Foco de regresiones desde la última revisión (v4 + encargos 15-18)

Lo que v4 dejó documentado como PROPUESTA y los encargos 15-18 debían cerrar:

| Propuesta v4 | Encargo | Estado verificado en esta ronda |
|---|---|---|
| Asignación fantasma al eliminar miembro con rotación | 15 #1 | **YA-RESUELTO**, confirmado por 3 expertos |
| `completeTask` completer≠asignado | 15 #2 | **RESUELTO en `completeTask`**, pero el mismo bug seguía sin corregir en `completeAssignment` — ver hallazgo CRÍTICO consolidado más abajo |
| Editar recurrente resetea `dueDate` a 0 | 15 #3 | **YA-RESUELTO**, confirmado |
| Hogar sin owner al eliminar cuenta | 15 #4 | **YA-RESUELTO**, confirmado |
| Sync de Calendar inmediato | 15 #5 | **YA-RESUELTO**, confirmado |
| `deleteHousehold`/`deleteAccount` no completan si el cascade falla | 16 #1-2 | **YA-RESUELTO**, confirmado |
| `authorName` anonimizado al borrar cuenta | 16 #3 | **PARCIAL** — solo mensajes de chat, no comentarios de tarea (DTO sin `memberId`), ni en la ruta de expulsión por admin |
| Revocar OAuth Calendar / reautenticación / doble confirmación | 16 #4-6 | **YA-RESUELTO**, confirmado |
| Regla `members/{mid}` create (v6) | 16 #7 | Escrita en el repo, **NO desplegada** (confirmado contra la API real de Firebase Rules — producción sigue en v5) |
| `rememberHouseholdName`, paginar `getTaskHistory`, paralelizar cascade-delete, `SecureStore` perezoso, `FilterChip` check icon, separar `remember` de `isDueToday` | 17 #1-8 | **YA-RESUELTO**, las 8 confirmadas |
| 9 huecos de cobertura de tests | 18 | **7/9 cerrados con buena cobertura**, 1 no cubrible sin dependencia nueva (`MemberActionState`), 1 parcialmente cubierto |

**Regresión nueva detectada**: la memoización de `RecurrenceNextPreview`
(aplicada en v4 para resolver un hallazgo MENOR) capturaba `Clock.System.now()`
dentro del `remember` sin incluir el día en la key — congelaba la fecha de
"próxima vez" si el formulario quedaba abierto cruzando medianoche. Corregido
en esta ronda (ver Experto 4/11 más abajo).

**Hallazgo más importante de la ronda**: el fix de "completer≠asignado" del
encargo 15 se aplicó únicamente a `completeTask` (el botón "Completar tarea"
global). El flujo hermano `completeAssignment` (botón de completar una
asignación individual, usado en tareas con varios miembros asignados a la
vez) se quedó exactamente igual que antes del encargo 15 — permitía duplicar
puntos/historial de forma determinista, sin necesidad de ninguna carrera.
Cuatro expertos independientes (2, 6, 8, 12) y el experto de cobertura de
tests (13) lo confirmaron por separado. **Corregido en esta ronda** (ver
detalle en Experto 2/8).

---

## Experto 1 — Estética y diseño visual

### IMPORTANTE

- **Problema**: solo 2 de 6 pantallas con estado vacío usan las ilustraciones
  de marca (`EmptyTasksIllustration`/`EmptyHouseholdsIllustration`); el resto
  (Recompensas, Notificaciones, Ranking, Miembros) usan un emoji Unicode
  suelto, con riesgo de render inconsistente entre plataformas.
  **Evidencia**: `RewardListScreen.kt:129`, `NotificationListScreen.kt:85`,
  `RankingScreen.kt:88`, `HouseholdMemberList.kt:109`.
  **SOLO PROPUESTA** (requiere diseñar 4 ilustraciones nuevas).
  **Estado**: PENDIENTE-DESDE-V2/V3.
- **Problema**: `Text("✕")`/`Text("▲"/"▼")`/`Text("+")` en vez de iconos
  Material establecidos en el resto de la app (4+2+2 sitios en
  Create/EditTaskScreen y HouseholdMemberList).
  **APLICADO YA** en esta ronda — ver sección "Aplicado".
  **Estado**: NUEVO → RESUELTO.
- **Problema**: `EmptyTasksIllustration`/`EmptyHouseholdsIllustration`
  hardcodean Teal/Coral en vez de `MaterialTheme.colorScheme` — chocan
  visualmente con los temas Naturaleza/Minimal.
  **Evidencia**: `EmptyStateIllustrations.kt:14-18,35,63-67,96,108,112,119`.
  **SOLO PROPUESTA** (decisión de diseño por tema).
  **Estado**: PENDIENTE-DESDE-V2/V3.

### MENOR

- `AppLogo.kt` hardcodea Teal/Coral sin política documentada de "marca fija
  vs. sigue el tema" — **SOLO PROPUESTA** (documentar la decisión).
  **Estado**: NUEVO/informativo.

---

## Experto 2 — Funcionalidad end-to-end

### CRÍTICO

- **Problema**: `completeAssignment` (`FirestoreRepository.kt`, flujo de
  completar UNA asignación individual desde `TaskDetailScreen`) no cerraba
  las asignaciones "assigned" hermanas del mismo ciclo cuando una tarea
  recurrente tiene varios miembros asignados a la vez. Si Bob completaba su
  asignación después de que Alice ya hubiera completado la suya, el check de
  concurrencia (que compara el documento de Bob consigo mismo) pasaba sin
  detectar nada raro, y Bob volvía a cobrar puntos por el mismo ciclo ya
  cerrado. Confirmado independientemente por los Expertos 6, 8, 12 y 13.
  **Evidencia**: `network/FirestoreRepository.kt:1205-1310` (antes del fix),
  comparado con el bloque equivalente ya existente en `completeTask` (líneas
  918-938).
  **APLICADO YA** en esta ronda — ver sección "Aplicado" (bug crítico de
  integridad de puntos, corrección localizada replicando el patrón exacto ya
  usado y testeado en `completeTask`).
  **Estado**: NUEVO → RESUELTO.

### IMPORTANTE

- **Problema**: `authorName` de los comentarios de tarea (no los mensajes de
  chat) no se anonimiza al abandonar/ser expulsado de un hogar — el DTO
  `CommentResponse` ni siquiera tiene `memberId` para poder identificarlos.
  **Evidencia**: `network/HouseholdRepository.kt:355-378` (solo `messages`);
  `network/models/DTOs.kt:223-228` (`CommentResponse` sin `memberId`).
  **SOLO PROPUESTA** — requiere cambio de esquema (añadir `memberId` al
  documento de comentario) y recorrer todas las tareas del hogar; no es un
  fix de una línea.
  **Estado**: PENDIENTE-DESDE-ENCARGO-16 (aplicado solo a la mitad del
  alcance pedido).

### MENOR

- `firestore.rules` v6 (validación de campos en `create` de `members/{mid}`)
  escrita en el repo pero no desplegada en producción (confirmado contra la
  API real de Firebase Rules por el Experto 9). **SOLO PROPUESTA** (acción
  operativa de despliegue, fuera del alcance de esta sesión). **Estado**:
  PENDIENTE-DESDE-ENCARGO-16.

---

## Experto 3 — Accesibilidad WCAG AA

### IMPORTANTE

- **Problema**: el `leadingIcon` de check en `FilterChip` (encargo 17 #7) se
  aplicó solo a los 4 grupos explícitos del encargo, pero no a los chips de
  etiquetas predefinidas de Create/EditTaskScreen, ni a los filtros de
  estado/etiqueta de `TaskListScreen`, ni al selector de rol de
  `CreateProfileScreen` — mismo patrón `FilterChip(selected=...)` dependiendo
  solo de color para indicar selección (WCAG 1.4.1).
  **Evidencia**: `CreateTaskScreen.kt:599-605`, `EditTaskScreen.kt:639-645`,
  `TaskListScreen.kt:1256-1262,1275-1283`, `CreateProfileScreen.kt:156-167`.
  **APLICADO YA** en esta ronda — ver sección "Aplicado".
  **Estado**: PENDIENTE-DESDE-V4 (alcance del fix original más estrecho que
  el problema real) → RESUELTO.
- **Problema**: el desplegable "Plantillas rápidas" de `CreateTaskScreen` usa
  un glifo Unicode `▲`/`▼` sin `contentDescription`/`stateDescription` —
  TalkBack no anuncia expandido/colapsado, a diferencia del patrón correcto
  ya usado en `HouseholdTaskSection`.
  **Evidencia**: `CreateTaskScreen.kt:940-967` (antes del fix).
  **APLICADO YA** en esta ronda (mismo cambio que resolvió el hallazgo de
  Estética #2, reutilizando `Icon` con `contentDescription`).
  **Estado**: NUEVO → RESUELTO.

### MENOR

- Cobertura de `reduce-motion` inconsistente: 3 sitios con `AnimatedVisibility`
  genuina (`HouseholdTaskSection.kt:107`, `HomeScreen.kt:203`,
  `CreateTaskScreen.kt:970-973`) no consultan `shouldReduceMotion()`.
  **SOLO PROPUESTA** (tocar 3 archivos para un efecto de bajo impacto).
  **Estado**: NUEVO.
- Tint `colorScheme.error` en el icono "eliminar miembro" sigue siendo una
  variante aislada del patrón. **SOLO PROPUESTA**, sin cambios.
  **Estado**: PENDIENTE-DESDE-V4.

**Confirmado sin regresión**: contrastes recalculados independientemente en
los 3 temas (DEFAULT/NATURALEZA/MINIMAL) — todos pasan AA con margen. Las 72
llamadas a `Icon(...)` de `ui/screens`+`ui/components` tienen
`contentDescription` real. `DeleteAccountSection.kt` conserva el `liveRegion`
de accesibilidad tras el refactor del encargo 17.

---

## Experto 4 — UI y componentes Material 3

### MENOR

- **Problema (regresión)**: `RecurrenceNextPreview` memoiza la fecha de
  "próxima vez" con `remember(frequency, recurrenceDays, recurrenceDay)`,
  pero el cálculo usa `Clock.System.now()` capturado dentro del `remember` —
  si el formulario permanece abierto cruzando medianoche, la fecha mostrada
  queda congelada.
  **Evidencia**: `ui/components/RecurrenceNextPreview.kt:46-58` (antes del
  fix).
  **APLICADO YA** en esta ronda — se añadió el día actual (`todayEpochDay`)
  a la key del `remember`.
  **Estado**: NUEVO (introducido por el propio fix de v4) → RESUELTO.
- Claves i18n `household_delete_btn`/`household_cancel` seguían usándose en
  5 sitios fuera del dominio "hogar" pese a que el propio `DestructiveConfirmDialog`
  documenta que fueron sustituidas por `common_delete`/`common_cancel`.
  **APLICADO YA** en esta ronda — los 5 sitios migrados y las claves
  duplicadas eliminadas de `AppStrings.kt` (dead code).
  **Estado**: PENDIENTE-DESDE-V4 → RESUELTO.
- `MemberRewardScreen.kt` sigue con un `AlertDialog` manual (confirmación de
  canje) no cubierto por la generalización de `DestructiveConfirmDialog`
  (necesitaría un modo de color "acento positivo", no solo
  destructivo/neutro). **SOLO PROPUESTA** (cambia la API del componente
  compartido). **Estado**: NUEVO.
- Tint fijo `colorScheme.error` en icono de "eliminar miembro" —
  mismo hallazgo que Accesibilidad #4, **SOLO PROPUESTA**.

**Confirmado sin regresión**: `rememberHouseholdName` usado consistentemente
en las 9 pantallas previstas; `DestructiveConfirmDialog` generalizado y
reutilizado en el diálogo de cambio de rol; `DeleteAccountSection` extraído
limpiamente; `filterChipCheckIcon` aplicado en los 4 grupos originales; sin
`Icons.Default.*` para iconos direccionales/espejados en ningún sitio; sin
`Color(0x...)` hardcodeado en `ui/screens`/`ui/components`; build y tests
verdes antes de esta ronda.

---

## Experto 5 — UX

### IMPORTANTE

- **Problema**: el paso de reautenticación de "eliminar cuenta" se mostraba
  con el mismo texto "Eliminando cuenta…" que el borrado real, aunque
  internamente relanza el selector de cuenta de Google — sorpresa sin
  explicar en un flujo irreversible ya de por sí cargado de fricción.
  **Evidencia**: `ui/components/DeleteAccountSection.kt:113-125` (antes del
  fix).
  **APLICADO YA** en esta ronda — nuevo estado `isReauthenticating` con texto
  distinto ("Confirmando tu identidad…").
  **Estado**: NUEVO → RESUELTO.
- **Problema**: "Eliminar hogar"/"Salir del hogar" mostraban un spinner de
  pantalla completa sin ningún texto durante el borrado (a diferencia de
  "eliminar cuenta", que sí explica qué está pasando).
  **Evidencia**: `ui/screens/HouseholdScreen.kt:379-385` (antes del fix).
  **APLICADO YA** en esta ronda — texto distinto según `isDeleting`/`isLeaving`.
  **Estado**: NUEVO → RESUELTO.
- **Problema**: `CreateRewardScreen` calcula `isValid` pero no marca
  `isError`/`supportingText` en los campos de título/coste — el botón
  "Crear" queda deshabilitado sin ninguna pista de qué falta, a diferencia
  del formulario hermano `CreateTaskScreen`.
  **Evidencia**: `ui/screens/CreateRewardScreen.kt:172-209` (antes del fix).
  **APLICADO YA** en esta ronda — mismo patrón de `isError`/`supportingText`
  que ya usa `CreateTaskScreen`.
  **Estado**: NUEVO → RESUELTO.
- Mensajes de error con fallback hardcodeado en español (`e.message ?: "Error
  al..."`) en ~28 puntos de 7 `ScreenModel`s — si `e.message` es null, el
  usuario con la app en inglés ve un error en español.
  **SOLO PROPUESTA** (volumen: toca ~10 archivos, requiere pasar `lang` a
  varios `ScreenModel`s que no lo tienen inyectado).
  **Estado**: PENDIENTE-DESDE-V4 (v4 solo cerró el caso de conflicto de
  compleción, no el patrón general).

### MENOR

- `EditTaskScreen` sin botón "Reintentar" tras fallo de carga de
  asignaciones (el único camino es salir y volver a entrar). **SOLO
  PROPUESTA**. **Estado**: NUEVO.
- Botón "Política de privacidad" con emoji `🔒` residual pese a que el botón
  hermano "Eliminar cuenta" ya se corrigió en v4.
  **APLICADO YA** en esta ronda — emoji quitado, icono `Lock` real añadido.
  **Estado**: PENDIENTE-DESDE-V4 → RESUELTO.

**Confirmado sin regresión**: mensajes de conflicto de compleción vía
`AppStrings`, selector semanal sin 0 días, texto de confirmación de eliminar
miembro, doble confirmación + reautenticación de "eliminar cuenta",
`rememberHouseholdName` en las 9 pantallas.

---

## Experto 6 — Programador senior

### CRÍTICO

- Confirma de forma independiente los dos problemas de `completeAssignment`
  (PATCH incondicional sobre el documento de tarea sin `currentDocument.updateTime`,
  y ausencia de cierre de asignaciones hermanas) desde el ángulo de
  concurrencia/manejo de excepciones. El segundo (cierre de hermanas) se
  **APLICÓ YA** en esta ronda (ver Experto 2/8). El primero (protección de
  concurrencia sobre el documento de tarea en `completeAssignment`) queda
  **SOLO PROPUESTA** — requiere decidir la semántica de conflicto cuando el
  documento de la asignación ya se marcó completado pero el de la tarea
  pierde la carrera (ver detalle en Experto 12).

### IMPORTANTE

- `FirestoreClient.ensureAuth()` capturaba `Exception` genérica sin relanzar
  `CancellationException` en 2 puntos (restauración de sesión Google y
  anónima) — núcleo de autenticación de toda la capa de red.
  **Evidencia**: `network/FirestoreClient.kt:113,129` (antes del fix).
  **APLICADO YA** en esta ronda.
  **Estado**: NUEVO → RESUELTO.
- `ui/models/GoogleAuthManager.kt` — 6 puntos adicionales con el mismo patrón
  (no cubiertos por el barrido de v4, que solo auditó `MemberRepository.kt`):
  `signInWithGoogle`, `syncGoogleAvatar`, `restoreHouseholds`,
  `restoreFromCloudOnStartup` (×2), `repointPersonalHousehold`,
  `syncHouseholdsToCloud`.
  **Evidencia**: líneas 287, 313, 330, 351, 356, 373, 392 (antes del fix).
  **APLICADO YA** en esta ronda, los 7 puntos.
  **Estado**: NUEVO → RESUELTO.
- `MemberRepository.currentMemberCache` — lectura de un `mutableMapOf` fuera
  del `Mutex` que protege su escritura (double-checked locking sobre una
  estructura no thread-safe).
  **Evidencia**: `network/MemberRepository.kt:61-62,228-240`.
  **SOLO PROPUESTA** — cambiar la estrategia de caché sin medir el impacto de
  contención en el hot path merece revisión propia.
  **Estado**: NUEVO.
- `MemberRepository.createMember` — 4º punto de `CancellationException` no
  relanzada, en la misma función donde v4 dice haber cerrado "3 puntos".
  **Evidencia**: `network/MemberRepository.kt:198-202` (antes del fix).
  **APLICADO YA** en esta ronda.
  **Estado**: NUEVO (matiza la afirmación de v4 de que el barrido estaba
  completo) → RESUELTO.

### MENOR

- `HouseholdRepository.kt` — 2 puntos de `CancellationException` no
  relanzada (creación de invite, `getOrCreatePersonalHousehold`), ya
  documentados en v3/v4 como PROPUESTA sin aplicar.
  **APLICADO YA** en esta ronda (mecánico, mismo patrón).
  **Estado**: PENDIENTE-DESDE-V4 → RESUELTO.
- Varios `LaunchedEffect` de UI (`App.kt`, `HouseholdTaskSection.kt`,
  `EditTaskScreen.kt`) con el mismo patrón, impacto bajo en la práctica
  (Compose descarta escrituras de estado de una corrutina cancelada). **SOLO
  PROPUESTA** de pasada dedicada. **Estado**: NUEVO, impacto bajo.

**Confirmado sin hallazgo**: todos los `!!` del árbol están guardados por un
`if (x != null)` inmediatamente anterior; sin `lateinit var` en `commonMain`;
sin `GlobalScope.launch`; DTOs inmutables (`val`, sin colecciones mutables
expuestas).

---

## Experto 7 — Jefe de arquitectura

### IMPORTANTE

- `HouseholdTaskSection` (un `ui/components/`, no un ScreenModel) inyecta
  `FirestoreRepository` directamente y hace su propio fetch de tareas con un
  filtro de "pendiente" distinto (y más pobre) al que ya usa `HomeScreenModel`
  — dos fuentes de verdad para "tareas pendientes del hogar" en la misma
  pantalla, con una lectura de red redundante.
  **Evidencia**: `ui/components/HouseholdTaskSection.kt:33,45` vs.
  `ui/models/HomeScreenModel.kt:79-90` y `ui/screens/HomeScreen.kt:306,324`
  (que ya tiene el dato cargado y no lo usa).
  **SOLO PROPUESTA** — cambiar la firma del componente para recibir la lista
  de tareas en vez de inyectar el repo cambia semántica de loading/error
  por-household; se prefiere verificación visual antes de aplicarlo a ciegas.
  **Estado**: PENDIENTE-DESDE-V4 (deuda preexistente, no introducida por los
  encargos 17/18).
- `FirestoreRepository.kt` (facade) volvió a crecer de 1421 líneas (tras el
  god-object split de v4) a 1604 — la orquestación cross-dominio de cada
  ronda de fixes reales (cascade-delete, integridad de puntos) se sigue
  añadiendo a la facade en vez de a los repos de dominio.
  **SOLO PROPUESTA** — decidir qué vive en la facade vs. repos de dominio no
  es un fix localizado.
  **Estado**: REGRESIÓN relativa al veredicto "Sano" de v4 (aunque razonable
  en términos absolutos frente a las 1959 líneas originales).

### MENOR

- Mapeo DTO↔dominio sigue descentralizado (`FirestoreParsers.kt` solo cubre
  2 de 7 tipos). **SOLO PROPUESTA**, sin cambios desde v3/v4.

**Veredicto por subsistema**: `network/` sano con tendencia a vigilar en la
facade; `ui/` consistente salvo el caso puntual de `HouseholdTaskSection`;
DI (Koin) sano; `storage/` de tamaño contenido.

---

## Experto 8 — QA y bugs

### CRÍTICO

- Confirma y reclasifica como CRÍTICO (en vez de ALTO) el bug de
  `completeAssignment` no cerrando asignaciones hermanas, con reproducción
  determinista paso a paso (sin necesidad de ninguna carrera).
  **APLICADO YA** en esta ronda (ver Experto 2).
  **Estado**: NUEVO → RESUELTO.
- **Problema**: `undoCompleteTask` revierte puntos/racha/historial pero NO
  toca los documentos de `assignments` que `completeTask` marcó
  `status="completed"` — quedan huérfanos para siempre. `StatsScreenModel`
  fusiona `assignments` completadas + `taskHistory` sin deduplicar, así que
  las estadísticas siguen contando una compleción ya deshecha
  permanentemente, mientras el saldo real de puntos ya no la incluye.
  **Evidencia**: `ui/models/TaskScreenModel.kt:556-585` (undo no toca
  `assignments`); `ui/models/StatsScreenModel.kt:106-129,176-189`.
  **SOLO PROPUESTA** — requiere localizar y revertir la(s) asignación(es)
  correcta(s) del ciclo deshecho sin tocar la asignación de la SIGUIENTE
  ocurrencia (que `regenerateNextAssignment` ya pudo haber creado); no es un
  fix mecánico de una línea.
  **Estado**: NUEVO.

### IMPORTANTE

- El propio fix de "cerrar asignaciones hermanas" (aplicado en `completeTask`
  desde el encargo 15, y ahora también en `completeAssignment` en esta
  ronda) marca esas asignaciones como `"completed"` con `pointsAwarded=0` —
  pero `StatsScreenModel` no filtra por `pointsAwarded>0`, así que cuenta esa
  fila como una compleción real (tag, `onTimeRate`) para un miembro que no
  hizo nada.
  **Evidencia**: `network/FirestoreRepository.kt:927-937`;
  `ui/models/StatsScreenModel.kt:111-113,137-145,160-167,174,188`.
  **SOLO PROPUESTA** — requiere decidir si un cierre de ciclo sin puntos debe
  contar en las métricas de Stats del no-completer (cambia el contrato de
  qué representa una fila `"completed"`).
  **Estado**: NUEVO, causado por el propio fix del encargo 15 (efecto
  secundario no evaluado entonces).

**Confirmado sin hallazgo nuevo**: sin crashes por división por cero, índice
fuera de rango o NPE explotable en `PenaltyRules`/`RecurrenceRules`/
`PointsRules`/`HouseholdRules`. `addMemberPoints` usa correctamente
concurrencia optimista con reintento acotado.

---

## Experto 9 — Seguridad / AppSec (OWASP MASVS)

Verificación código-vs-producción hecha contra la API real de Firebase Rules
(`firebaserules.googleapis.com`, solo lectura): confirmado que producción
sirve el ruleset v5 (`29319b00`, desplegado 2026-09-02), NO la v6 local.

### IMPORTANTE

- `rewardRedemptions/{rrid}` — `create` sin validar `pointsSpent`/`memberId`
  contra el coste real de la recompensa: un cliente modificado puede crear un
  registro de "canje realizado" con `pointsSpent=0` sin descontar puntos,
  indistinguible de uno legítimo.
  **Evidencia**: `firestore.rules:348-351` vs. `network/FirestoreRepository.kt:1559-1575`
  (dos escrituras separadas, sin vínculo forzado por regla).
  **SOLO PROPUESTA** (cambio de regla, requiere despliegue fuera de alcance).
  **Estado**: NUEVO.
- `taskHistory/{thid}` — `create` sin validar `memberId == request.auth.uid`:
  se puede falsificar el historial de auditoría atribuyendo una compleción a
  otro miembro.
  **Evidencia**: `firestore.rules:318-320`.
  **SOLO PROPUESTA** (cambio de regla). **Estado**: NUEVO.
- Regla v6 (validación de `members/{mid}` create) confirmada sin desplegar,
  con el matiz de que su impacto real es marginal (redundante con la rama
  `update`, que permite el mismo efecto vía `PATCH` posterior). **SOLO
  PROPUESTA**. **Estado**: PENDIENTE-DESDE-V4, confirmado con evidencia
  directa de producción.

### MENOR

- Access token OAuth de Google Calendar guardado en `settings` (sin cifrar)
  en vez de `secureStore` — único token de sesión fuera de la migración a
  cifrado. TTL corto (~1h) mitiga el impacto.
  **Evidencia**: `storage/SettingsStore.kt:82-91,233`.
  **SOLO PROPUESTA** — cambiar el storage de un token en uso activo (no solo
  refresh tokens) requiere revisar todos los call-sites de lectura/escritura
  con cuidado adicional; se deja documentado en vez de aplicarlo sin esa
  revisión completa.
  **Estado**: PENDIENTE-DESDE-V4.
- `notifications/{nid}` — `create` sin validar `memberId` (impacto bajo,
  spam social dentro de un hogar de confianza implícita). **SOLO PROPUESTA**.
  **Estado**: NUEVO, severidad baja.
- API key de Firebase hardcodeada — confirmado que NO es una vulnerabilidad
  (clave pública de proyecto, no secreta; seguridad real recae en
  `firestore.rules` + Auth). Sin secretos reales, sin service account
  trackeado en git. **Estado**: no-issue, informativo.

**Confirmado sin hallazgo**: cifrado de tokens correcto en las 3 plataformas;
CSPRNG en generación de código de invitación; sin inyección en construcción
de URLs/queries REST; `WEB_CLIENT_ID` es público por diseño (OAuth Android).

---

## Experto 10 — Privacidad / RGPD / menores

### CRÍTICO

- `authorName` no anonimizado en comentarios de tarea (mismo hallazgo que
  Experto 2, ampliado): el DTO no tiene `memberId`, y además `deleteMember`
  (expulsión por admin) tampoco anonimiza ni siquiera los MENSAJES de chat
  (a diferencia de `leaveHousehold`, que sí lo hace parcialmente).
  **Evidencia**: `network/models/DTOs.kt:223-228`;
  `network/FirestoreRepository.kt:619-630`; `network/MemberRepository.kt:309-329`
  (sin llamada a `anonymizeMemberMessages`).
  **SOLO PROPUESTA** — cambio de esquema (añadir `memberId` a comentarios) +
  extender la anonimización a la ruta de expulsión.
  **Estado**: PENDIENTE-DESDE-V4/ENCARGO-16 (aplicado a menos de la mitad del
  alcance pedido).
- AdMob sin `tagForChildDirectedTreatment` pese a que la app declara
  explícitamente un "perfil infantil gestionado por un adulto"
  (`docs/privacy.html`). Impacto real hoy bajo (IDs de test, banner
  desactivado), pero sin nada en el código que lo bloquee al pasar a IDs de
  producción.
  **Evidencia**: `androidMain/.../TaskHubApplication.kt` (antes del fix);
  `androidMain/.../AdController.android.kt:44-47`; `docs/privacy.html:85-88`.
  **APLICADO YA** en esta ronda — `RequestConfiguration.setTagForChildDirectedTreatment`
  fijado globalmente antes de `MobileAds.initialize()` (bug técnico
  acotado). La señalización *por sesión* según el rol del perfil activo
  queda **SOLO PROPUESTA** (requiere plumbing nuevo + decisión de negocio).
  **Estado**: PENDIENTE-DESDE-V3 → APLICADO (señalización global).

### IMPORTANTE

- Regla v6 no desplegada — mismo hallazgo que Experto 2/9. **SOLO
  PROPUESTA/operativo**.

### MENOR

- Sin retención de datos ni age-gating real — decisiones de producto/legal
  conocidas y sin cambios desde v3. **SOLO PROPUESTA** en ambos casos,
  explícitamente marcadas como decisión de producto, no bug técnico.

**Confirmado sin hallazgo**: eventos de Analytics sin PII; `deleteHousehold`/
`deleteAccount` no completan el borrado si el cascade fue parcial; enlace a
`privacy.html` ya presente en la app.

---

## Experto 11 — Rendimiento

### IMPORTANTE

- **Problema**: `getAllAssignments()` vuelve a pedir TODAS las tareas por
  dentro (`val tasks = getTasks(householdId)`); 3 de sus 4 llamadores
  (`TaskScreenModel.loadTasks`, `StatsScreenModel.loadStats`,
  `CalendarSyncManager.reconcile`) YA habían pedido `getTasks` justo antes o
  después, duplicando la lectura de la colección `tasks` en las rutas más
  transitadas de la app.
  **Evidencia**: `network/TaskRepository.kt:495-497`;
  `ui/models/TaskScreenModel.kt:232-233`; `ui/models/StatsScreenModel.kt:62-63`;
  `ui/models/CalendarSyncManager.kt:151,162`.
  **SOLO PROPUESTA** — cambiar la firma de `getAllAssignments` para aceptar
  una lista de tareas ya conocida toca 4 archivos; bajo riesgo pero no
  localizado a una línea.
  **Estado**: NUEVO.
- `loadTasks()`/`loadStats()` encadenan 3-4 lecturas independientes en serie
  en vez de en paralelo (`async`/`awaitAll`, patrón ya usado en otros sitios
  del propio repo). **SOLO PROPUESTA** (agrupa con el hallazgo anterior para
  una pasada dedicada). **Estado**: NUEVO.

### MENOR

- Confirma la regresión de `RecurrenceNextPreview` (ver Experto 4).
  **APLICADO YA**. **Estado**: REGRESIÓN → RESUELTO.
- `deleteAllDocuments` sin límite de concurrencia (fan-out ilimitado de
  `async` por documento) — consecuencia colateral de la paralelización del
  encargo 17, sin impacto observado hoy (hogares pequeños). **SOLO
  PROPUESTA**. **Estado**: NUEVO, deuda a vigilar.

**Confirmado**: las 4 propuestas de rendimiento del encargo 17 (paginar
`getTaskHistory`, paralelizar cascade-delete, `SecureStore` perezoso,
separar `remember`) están bien aplicadas y no introdujeron regresiones
propias.

---

## Experto 12 — Red / offline / sincronización

### CRÍTICO

- `completeAssignment` hacía PATCH incondicional sobre el documento de la
  TAREA (sin `currentDocument.updateTime`), a diferencia de `completeTask`,
  que sí protege ese mismo documento — lost-update real si dos asignaciones
  del mismo ciclo se completaban casi a la vez.
  **Evidencia**: `network/FirestoreRepository.kt:1278-1283` (antes del fix)
  vs. `:849-890` (patrón correcto en `completeTask`).
  **SOLO PROPUESTA** — cerrar esto bien requiere decidir la política de
  conflicto (qué pasa si la asignación propia ya se marcó completada pero el
  documento de tarea pierde la carrera); no se aplica a ciegas sin esa
  decisión. El hallazgo de integridad de datos hermano (no cerrar
  asignaciones hermanas) SÍ se aplicó en esta ronda.
  **Estado**: NUEVO.
- `regenerateNextAssignment` puede crear DOS asignaciones para el siguiente
  ciclo si dos asignaciones hermanas se completan casi a la vez (TOCTOU en
  el dedup, alcanzable a través de `completeAssignment` porque no tiene la
  misma barrera que protege a `completeTask`). **SOLO PROPUESTA** — requiere
  cambiar el contrato de creación de asignaciones (ID determinista) para
  cerrarlo del todo. **Estado**: NUEVO.

### IMPORTANTE

- `completeTask` puede sobrescribir en silencio `pointsAwarded`/`onTime` de
  una asignación que otro miembro está completando concurrentemente vía
  `completeAssignment` (el loop de cierre de hermanas de `completeTask` no
  usa `expectedUpdateTime`). Impacto bajo en la práctica (no afecta el saldo
  real, solo un campo de visualización). **SOLO PROPUESTA**. **Estado**:
  NUEVO.
- Sin retry/backoff genérico ante timeout/5xx transitorio fuera de los
  patrones de concurrencia optimista ya existentes. **SOLO PROPUESTA**
  (decisión de política de reintentos a nivel de toda la capa HTTP).
  **Estado**: PENDIENTE-DESDE-V4, sin cambios.
- `redeemReward` sigue permitiendo saldo negativo con canjes concurrentes del
  mismo miembro (limitación ya documentada en el propio código). **SOLO
  PROPUESTA**. **Estado**: PENDIENTE-DESDE-V4, sin cambios.

### MENOR

- El DELETE final de `deleteHousehold` (tras confirmar que no hubo fallos en
  el cascade) no está envuelto en try/catch — si falla justo ahí, la caché
  local no se limpia. Bajo impacto (reintentar es idempotente). **SOLO
  PROPUESTA** — el propio experto señala que el fallo debe ser visible; no
  hay una decisión objetivamente mejor sin más contexto de producto.
  **Estado**: NUEVO, MENOR.

**Confirmado sin hallazgo**: `deleteHousehold`/`deleteAccount` con manejo de
fallos parciales correcto; `addMemberPoints`/`appreciateMember`/`donatePoints`
con concurrencia optimista y reintento; `isOnline()` distingue correctamente
red vs. 403/404; `TaskCache` invalida correctamente en las mutaciones
revisadas; `GoogleCalendarRepository`/`CalendarSyncManager` best-effort sin
dejar Firestore inconsistente.

---

## Experto 13 — Cobertura de pruebas (solo informa)

135 tests totales (subida desde 85 en v4). Cobertura de lógica pura:
`RecurrenceRules`, `PenaltyRules`, `PointsRules`, `HouseholdRules`,
`FirestoreParsers` al 100% de sus funciones públicas relevantes.
`SettingsStore.migrateLegacyToken` cubierto (incluido el fallback silencioso).

**7 de los 9 huecos de v4/encargo 18 cerrados con buena cobertura** (casos
límite reales, no solo cobertura de línea — p. ej. el bloque de `tz` no
default usa dos husos con 25h de diferencia). 1 confirmado no cubrible sin
dependencia nueva (`MemberActionState`, requiere `ktor-client-mock` o
refactor a interfaz). 1 (`regenerateNextAssignment` como función de red)
parcialmente cubierto vía su lógica pura extraída.

### Huecos nuevos identificados

| Prioridad | Hueco | Estado |
|---|---|---|
| CRÍTICO | `completeAssignment` sin test para el cierre de asignaciones hermanas (bug ya corregido en esta ronda, sigue sin test de integración — no cubrible en `jvmTest` sin mock de `HttpClient`, igual que `completeTask`) | NUEVO, no cubrible en jvmTest tal como está el código |
| IMPORTANTE | `SecureStore` (implementación JVM real) sin test del camino de fallo de descifrado (dato corrupto/manipulado) | NUEVO |
| MENOR | `TaskCache.kt` en 0% de cobertura | PENDIENTE, preexistente |
| MENOR | `SettingsStore.getCalendarId`/`setCalendarId` (merge de JSON multi-household) sin test | PENDIENTE, preexistente |

`ui/models/*ScreenModel.kt` confirmado no testeable en `jvmTest` sin añadir
`ktor-client-mock` o refactorizar a interfaz — no es un hueco "olvidado", es
estructural.

---

## Resumen — aplicado en esta ronda

### Corrección de integridad de puntos (el hallazgo más importante)

- **`network/FirestoreRepository.kt`** — `completeAssignment` ahora cierra
  todas las asignaciones "assigned" hermanas del mismo ciclo (mismo patrón
  ya usado y testeado en `completeTask`), marcándolas `pointsAwarded=0` para
  no inflar las estadísticas de otros miembros. Corrige el bug CRÍTICO de
  duplicación de puntos confirmado por 5 expertos independientes (2, 6, 8,
  12, 13).

### CancellationException tragada (cancelación cooperativa)

- **`network/FirestoreClient.kt`** — `ensureAuth()`: 2 puntos.
- **`network/MemberRepository.kt`** — `createMember`: 1 punto (4º del
  archivo, no cubierto por el barrido de v4).
- **`network/HouseholdRepository.kt`** — `createHousehold`,
  `getOrCreatePersonalHousehold`: 2 puntos.
- **`ui/models/GoogleAuthManager.kt`** — `signInWithGoogle`,
  `syncGoogleAvatar`, `restoreHouseholds`, `restoreFromCloudOnStartup` (×2),
  `repointPersonalHousehold`, `syncHouseholdsToCloud`: 7 puntos.

### Accesibilidad

- **`ui/components/FilterChipCheckIcon.kt`** reutilizado (`leadingIcon`) en
  5 sitios adicionales: tags predefinidas de Create/EditTaskScreen, filtros
  de estado/etiqueta de `TaskListScreen`, selector de rol de
  `CreateProfileScreen`.
- Desplegable "Plantillas rápidas" de `CreateTaskScreen` ahora anuncia
  expandido/colapsado a lectores de pantalla.

### Iconos espejados / glifos de texto → iconos Material

- `Text("✕")` → `Icon(Icons.Default.Close)` en 4 sitios (Create/EditTaskScreen,
  botón quitar subtarea + trailingIcon de chip de etiqueta).
- `Text("+")` → `Icon(Icons.Default.Add)` en 4 sitios (añadir subtarea/tag,
  Create/EditTaskScreen).
- `Text("▲"/"▼")` → `Icon(KeyboardArrowUp/Down)` con `contentDescription` en
  `HouseholdMemberList.kt` y en el desplegable de plantillas de
  `CreateTaskScreen.kt`.
- `Text("▼")` → `Icon(Icons.Default.ArrowDropDown)` en el selector de
  rotación de `EditTaskScreen.kt`.

### i18n residual / dead code

- Claves `household_delete_btn`/`household_cancel` (usadas fuera de su
  dominio en 5 sitios) migradas a `common_delete`/`common_cancel` y
  eliminadas de `AppStrings.kt` (ES/EN).
- Emoji `🔒` quitado de `settings_privacy_policy` (ES/EN), sustituido por
  `Icon(Icons.Default.Lock)` real en `SettingsSheet.kt`.

### UX

- `DeleteAccountSection.kt` — nuevo estado `isReauthenticating` con texto
  distinto ("Confirmando tu identidad…") durante el paso de reautenticación,
  reservando "Eliminando cuenta…" para el borrado real.
- `HouseholdScreen.kt` — el spinner de "eliminar/salir del hogar" ahora
  muestra texto explicativo (`household_deleting_progress`/
  `household_leaving_progress`).
- `CreateRewardScreen.kt` — campos de título y coste con `isError`/
  `supportingText`, mismo patrón que `CreateTaskScreen`.

### Regresión corregida

- `ui/components/RecurrenceNextPreview.kt` — la key del `remember` ahora
  incluye el día actual (`todayEpochDay`), evitando que la fecha de "próxima
  vez" quede congelada si el formulario queda abierto cruzando medianoche.

### Privacidad / menores

- `androidMain/.../TaskHubApplication.kt` — `RequestConfiguration.setTagForChildDirectedTreatment(TRUE)`
  fijado globalmente antes de `MobileAds.initialize()`, dado que Task Hub es
  una app familiar con perfiles infantiles reales (no solo posibles).

**Archivos tocados** (18):
```
composeApp/src/androidMain/kotlin/org/taskhub/TaskHubApplication.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreClient.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/HouseholdRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/MemberRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/DeleteAccountSection.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdMemberList.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/RecurrenceNextPreview.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/components/SettingsSheet.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/GoogleAuthManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateProfileScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateRewardScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditTaskScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/HouseholdScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/MemberRewardScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskDetailScreen.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt
```

## Verificación (OBLIGATORIO)

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL in 33s` — sin errores; solo warnings de deprecación
preexistentes (Google Sign-In, Vibrator, EncryptedSharedPreferences/MasterKey),
ninguno introducido por esta ronda.

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` — sin fallos.

## PROPUESTAS pendientes — resumen para el usuario

### Integridad de datos / concurrencia (requieren decisión de producto)

1. **`completeAssignment` sin protección de concurrencia sobre el documento
   de tarea** (Exp. 6/12, CRÍTICO) — requiere decidir la política de
   conflicto cuando la asignación propia ya se completó pero el documento de
   tarea pierde la carrera.
2. **`regenerateNextAssignment` puede duplicar la asignación del siguiente
   ciclo** en carrera entre asignaciones hermanas (Exp. 12) — requiere ID
   determinista en la creación de asignaciones.
3. **`undoCompleteTask` deja asignaciones "completadas" huérfanas** que
   desincronizan permanentemente `StatsScreen` del saldo real (Exp. 8,
   CRÍTICO) — requiere lógica de reversión cuidadosa que no toque la
   asignación de la siguiente ocurrencia.
4. **Compleciones fantasma (0 puntos) cuentan en `StatsScreen`** para
   miembros que no completaron nada (Exp. 8) — requiere decidir si deben
   contar o no en las métricas.

### Privacidad / seguridad (requieren cambio de esquema o despliegue)

5. **`authorName` no anonimizado en comentarios de tarea** ni en la ruta de
   expulsión por admin (Exp. 2/10, CRÍTICO de privacidad) — requiere añadir
   `memberId` al esquema de comentarios.
6. **`rewardRedemptions`/`taskHistory` sin validar campos en `create`** (Exp.
   9) — cambios de `firestore.rules`, requieren despliegue.
7. **Regla v6 (`members/{mid}` create)** escrita pero no desplegada (Exp.
   2/9/10) — acción operativa, no de código.
8. **Google Calendar access token sin cifrar** (Exp. 9) — cambiar el storage
   de un token en uso activo requiere revisión completa de call-sites.
9. **Señalización AdMob por sesión** (según rol del perfil activo, no
   global) — requiere plumbing nuevo + decisión de negocio (la señalización
   global ya se aplicó en esta ronda).

### Arquitectura / rendimiento (refactors de mayor superficie)

10. **`HouseholdTaskSection` bypassa el ScreenModel** con fetch y regla de
    negocio duplicados (Exp. 7) — cambia semántica de loading/error
    por-household, se prefiere verificación visual antes de aplicar.
11. **`FirestoreRepository.kt` facade volvió a crecer** a 1604 líneas (Exp.
    7) — vigilar tendencia, sin acción inmediata.
12. **`getAllAssignments()` duplica la lectura de `getTasks()`** en 3 rutas
    calientes (Exp. 11) — cambio de firma que toca 4 archivos.
13. **`loadTasks`/`loadStats` secuenciales en vez de paralelos** (Exp. 11).
14. **`deleteAllDocuments` sin límite de concurrencia** (fan-out ilimitado,
    Exp. 11/12) — sin impacto hoy, vigilar con hogares grandes.

### Estética / UX de menor prioridad

15. Ilustraciones de estado vacío en solo 2/6 pantallas, y sin seguir el
    tema activo (Exp. 1).
16. Fallbacks de error hardcodeados en español en ~28 puntos (Exp. 5).
17. `MemberRewardScreen` con `AlertDialog` manual sin unificar (Exp. 4).
18. `EditTaskScreen` sin botón "Reintentar" tras fallo de carga (Exp. 5).
19. Cobertura de `reduce-motion` incompleta en 3 sitios (Exp. 3).
20. Mapeo DTO↔dominio descentralizado (Exp. 7).

### Cobertura de tests (solo informa, Exp. 13)

21. `SecureStore` sin test del camino de fallo de descifrado real.
22. `TaskCache`/`SettingsStore.getCalendarId` en 0% de cobertura.
