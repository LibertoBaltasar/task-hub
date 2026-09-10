# Auditoría COMPLETA de Task Hub — panel de 13 expertos (2026-09-06)

Auditoría integral de toda la aplicación en su estado actual (HEAD `e02bf26`, árbol de
trabajo limpio — todo lo que en el encargo se describía como "cambios sin commitear de
la ronda de propuestas aprobadas" ya quedó commiteado en `412a25b` notificaciones y
`728b8aa` ronda A de 14 fixes, con el bump de versión `e02bf26` encima).

**SOLO AUDITORÍA**: no se ha editado ningún archivo, no se ha hecho ningún commit, no
se ha desplegado nada. Todo el valor entregado es este informe.

Método: 13 subagentes en paralelo, uno por rol, cada uno verificando sus hallazgos
contra el código real (archivo:línea) antes de reportar. Varios expertos investigaron
de forma independiente la misma sospecha (el posible doble conteo en Estadísticas) sin
verse entre sí, lo cual sirvió como validación cruzada — confirmado por 3 expertos
distintos con evidencia coincidente.

---

## Resumen ejecutivo — hallazgos CRÍTICOS

### 1. Doble conteo de compleciones en Estadísticas (CONFIRMADO por 3 expertos independientes)

**`StatsScreenModel.computeStats` (línea ~152)** fusiona `fromAssignments` (asignaciones
con `pointsAwarded > 0`) y `fromHistory` (`taskHistory` del miembro) **sin deduplicar**
por `taskId+completedAt`. Como `FirestoreRepository.completeTask` (~1001-1049) y
`completeAssignment` (~1518-1546) escriben la MISMA compleción tanto en `taskHistory`
(vía `saveTaskHistory`) como en la asignación propia del completer (con
`pointsAwarded > 0`), **toda compleción de tarea asignada — el camino de uso más común
de la app — se cuenta DOS VECES** en "tareas completadas", puntos diarios y contador de
retrasos de la pantalla de Estadísticas. El saldo real de puntos del miembro (usado para
canjear recompensas) **no** se ve afectado — solo las estadísticas derivadas. Los logros
(`AchievementChecker`) tampoco están afectados (usan solo `taskHistory`).

- **[APLICA YA]** — Fix: `val allCompletions = (fromAssignments + fromHistory).distinctBy { it.taskId to it.completedAt }` en `StatsScreenModel.kt:152`, o de forma más limpia, prescindir de `fromAssignments` y depender solo de `fromHistory` (ya contiene el 100% de las compleciones reales con los puntos correctos).
- Confirmado independientemente por: Experto 13 (cobertura de tests, quien lo detectó primero), Experto 2 (funcionalidad end-to-end), Experto 8 (QA/bugs), Experto 6 (programador senior, quien no necesitó re-investigarlo).

### 2. `ownerId` nunca se limpia si el owner abandona sin sucesor con cuenta vinculada → bypass permanente + suplantación de identidad

**`FirestoreRepository.leaveHousehold` (líneas 586-609)**: si el owner abandona un hogar
y ningún miembro restante tiene cuenta vinculada (`resolveOwnerSuccessor` devuelve
`null` — el caso típico de un hogar familiar con 1 padre/madre-owner + perfiles
"hijo/a" sin cuenta propia), el bloque de transferencia de `ownerId` simplemente no
hace nada, y el documento del propio owner en `members/` se borra igualmente.
`firestore.rules` define `isOwner(hid) = request.auth.uid == householdDoc(hid).ownerId`
**sin comprobar si ese documento de miembro sigue existiendo** — el ex-owner conserva
`isOwner`/`isTrusted` para siempre. Combinado con el fallback "primer miembro
existente" de `MemberRepository.resolveCurrentMemberUncached` (que no encuentra
coincidencia por identidad tras el borrado), el ex-owner puede volver a entrar al
hogar (conociendo el ID, con su cuenta de Firebase Auth aún viva) y el cliente le
atribuye silenciosamente la identidad de OTRO miembro real — pudiendo completar
tareas, comentar, chatear y sincronizar Calendar a nombre de esa persona,
indefinidamente y sin que nadie lo detecte.

- **[REQUIERE DECISIÓN]** — Necesita definir la estrategia: (a) limpiar `ownerId` a un valor que `isOwner()` nunca pueda igualar cuando no hay sucesor elegible, ajustando la regla correspondiente, o (b) bloquear la acción "abandonar hogar" en la UI para el owner cuando no hay sucesor disponible, forzando antes a vincular una cuenta a algún perfil o a borrar el hogar entero. Es un cambio que toca `firestore.rules` (con redespliegue) y/o el flujo de onboarding — por eso es decisión, no aplicación autónoma.

### 3. `NotificationPollWorker` hace el doble de lecturas de `getMembers()` de las necesarias

**`NotificationPollWorker.pollHousehold` (androidMain)**: hace DOS lecturas de
`getMembers()` por hogar en cada ciclo de 30 min — una explícita para `isRealMember`
y otra implícita dentro de `resolveCurrentMember` (que no puede usar su caché porque
el Worker construye un `MemberRepository` nuevo en cada `doWork()`). Con N hogares
guardados, cada ciclo hace 2N lecturas en vez de N — el mismo patrón N+1 que la ronda
anterior ya corrigió en `HomeScreenModel`, pero aquí sin aplicar porque el código es
nuevo (ronda de notificaciones).

- **[APLICA YA]** — Fix: resolver el `memberId` directamente desde la lista `members` ya traída (`members.firstOrNull { it.userId != null && it.userId in identities }`) en vez de volver a llamar a `resolveCurrentMember`, fusionando el chequeo con `isRealMember`.

### 4. `reconcileHouseholds` puede resucitar un hogar que el usuario acaba de abandonar

**`HouseholdRepository.reconcileHouseholds` (líneas 235-269)**: el fix de la ronda A
cerró la carrera *interna* (varios `removeHousehold` pisándose entre sí), pero dejó
abierta una ventana (la duración del `awaitAll` de red, con N llamadas HTTP en
paralelo) durante la cual `replaceSavedHouseholds(survivorList)` sobrescribe TODA la
lista con una foto vieja. Si en esa ventana el usuario abandona un hogar a mano
(`leaveHousehold`, que no borra el documento del hogar, solo la membresía), y en el
mismo lote se podó cualquier otro hogar fantasma, la escritura final **reintroduce el
hogar que el usuario acababa de abandonar**.

- **[APLICA YA]** — Fix: no sobrescribir con `survivorList` (foto vieja); releer `store.getSavedHouseholds()` justo antes de escribir y solo quitar los `prunedIds` de lo que haya en ese momento.

---

## Listas consolidadas

### APLICA YA (implementación autónoma, para ronda de fixes posterior)

1. **CRÍTICO** — `StatsScreenModel.kt:152` — deduplicar `allCompletions` por `taskId+completedAt` (doble conteo de stats). *(QA/Estadísticas/Funcionalidad/Prog.senior)*
2. **CRÍTICO** — `NotificationPollWorker.kt` (androidMain) — eliminar la segunda lectura de `getMembers()`, resolver `memberId` desde la lista ya traída. *(Rendimiento)*
3. **CRÍTICO** — `HouseholdRepository.kt:235-269` (`reconcileHouseholds`) — releer `getSavedHouseholds()` antes de escribir, no usar la foto vieja `survivorList`. *(Red/offline/sync)*
4. **IMPORTANTE** — `TaskScreenModel.kt` (`completeTask`, ~línea 552) — añadir `catch (e: CancellationException) { throw e }` antes del catch exterior genérico (falta, a diferencia de su gemela `completeAssignment`). *(Programador senior)*
5. **IMPORTANTE** — `TaskScreenModel.kt` (`undoCompleteTask`, ~587-635) — separar los pasos del undo o exponer un estado de error propio; hoy puede fallar a medias (puntos revertidos, tarea sigue "completada") sin avisar. *(Funcionalidad end-to-end)*
6. **IMPORTANTE** — `CreateTaskScreen.kt` (~90-96) — el banner "espera un momento" puede quedar bloqueado para siempre sin red; añadir mensaje de error + botón "Reintentar" tras un fallo, en vez de spinner indefinido. *(UX)*
7. **IMPORTANTE** — `HouseholdRepository.sendMessage`/notificaciones de chat — extender la anonimización (`anonymizeMemberMessages`) para cubrir también `households/{id}/notifications`, o guardar `authorMemberId`/`messageParams` y resolver el nombre en el render en vez de congelarlo en texto libre. *(Privacidad)*
8. **IMPORTANTE** — `docs/privacy.html` — añadir mención explícita del scope OAuth de Google Calendar (acceso de lectura/escritura al calendario completo). *(Privacidad)*
9. **IMPORTANTE** — `NotificationListScreen.kt:201` y `TaskListScreen.kt:805` — añadir `role = Role.Button` a las cards clicables (falta, a diferencia de sus hermanas `MemberCard`/`TaskRow`/`RadioOptionRow` corregidas el mismo día). Extender también a `CalendarScreen.kt:471,529,695`. *(Accesibilidad/Material 3)*
10. **IMPORTANTE** — `NotificationListScreen.kt`/`TaskListScreen.kt` — cards con botón anidado ("Marcar como leída"/"Marcar como hecha") probablemente producen doble anuncio en TalkBack por conflicto de `mergeDescendants`; migrar la acción secundaria a `customActions` de accesibilidad. *(Accesibilidad)*
11. **MENOR** — `StatsScreen.kt:349-350` — sustituir `Color.White` hardcodeado por `MaterialTheme.colorScheme.surface` en el punto del gráfico de puntos. *(Estética)*
12. **MENOR** — `MemberRewardScreen.kt:210` — el `CircularProgressIndicator` del botón "Canjear" usa `onPrimary` en vez de `onTertiary` (desalineado en modo oscuro). *(Estética)*
13. **MENOR** — `ui/components/HouseholdDialogs.kt` (`HouseholdSettingsDialog`) — deduplicar el bloque `Dialog+Surface+SettingsSheet` copiado en `WelcomeScreen`, `HomeScreen`, `TaskListScreen`, `ProfileScreen`, reutilizando el componente ya existente. *(Material 3)*
14. **MENOR** — `WelcomeScreen.kt:176` — actualizar el literal `"v0.7.25"` a `"v0.7.28"` (desactualizado 3 bumps de versión). *(UX)*
15. **MENOR** — `MemberRewardScreen.kt:51-62` — evitar el falso negativo "puntos insuficientes" mientras `memberState` aún está cargando. *(UX)*
16. **MENOR** — `CreateRewardScreen.kt:202-210` — el campo "Coste" rechaza teclas no numéricas sin ningún feedback; añadir mensaje/`supportingText`. *(UX)*
17. **MENOR** — `TaskScreenModel.kt:236` (`_isOffline`) — eliminar el ping HTTP redundante (`repo.isOnline()`), derivar el estado offline de si `getTasks`/`getMembers` ya cayeron a caché. *(Red/offline/sync)*
18. **MENOR** — `docs/privacy.html` sección 6 — actualizar para reflejar que "Eliminar cuenta" ya es autoservicio en la app, no solo por email.

### REQUIERE DECISIÓN (para el usuario)

1. **CRÍTICO** — `ownerId` sin limpiar al abandonar sin sucesor — decisión de esquema/reglas de Firestore (ver hallazgo #2 del resumen ejecutivo). *(Seguridad)*
2. **IMPORTANTE** — `resolveCurrentMemberUncached`: replicar en el helper compartido la verificación de pertenencia real que hoy solo tiene `NotificationPollWorker`, para no depender de que cada call-site nuevo recuerde añadirla. Riesgo real menor de lo esperado para expulsados no-owner (Firestore ya devuelve 403 antes de que se dispare el fallback peligroso), pero sí aplica al escenario del hallazgo CRÍTICO #2. *(Seguridad)*
3. **IMPORTANTE** — Abandonar `JoinHouseholdScreen` en el paso 2 deja un hogar guardado localmente sin membresía real, que `reconcileHouseholds` no poda (solo comprueba que el documento del hogar siga existiendo, no la pertenencia) — expone el mismo fallback de identidad que el hallazgo de seguridad. Decisión: podar por pertenencia real, o deshacer el `saveHousehold` si se sale sin completar. *(UX/Seguridad, mismo trasfondo)*
4. **IMPORTANTE** — Sin UMP/CMP (consentimiento de anuncios) para tráfico EEE/UK — mitigado parcialmente por TFCD=TRUE, pero la política de AdMob exige un CMP certificado igualmente. *(Privacidad)*
5. **IMPORTANTE** — `NotificationRepository.getNotifications` sin `limit`/query estructurada — más caliente de lo documentado hasta ahora: se ejecuta también cada 30 SEGUNDOS en primer plano (`HouseholdScreen`, polling preexistente desde agosto), no solo cada 30 min en el Worker. Requiere migrar a `structuredQuery` o `count()` agregado. *(Rendimiento/Red)*
6. **IMPORTANTE** — `NotificationRepository.purgeOldRead` sin límite de concurrencia — hoy coste cero (feature de ayer), pero a 90 días vista puede generar un pico de cientos de DELETE secuenciales en un solo ciclo. Acotar con `Semaphore`/`take(N)` antes de que sea observable. *(Rendimiento)*
7. **IMPORTANTE** — Splash (`SplashScreen.kt`), `AppLogo` y `EmptyStateIllustrations.kt` ignoran el tema Minimal/modo oscuro elegido por el usuario, pese a que el dato ya está disponible en el arranque. Decisión de producto: ¿el logo/marca debe respetar el tema o mantenerse fijo? *(Estética)*
8. **IMPORTANTE** — Sin retry/backoff de transporte en `FirestoreClient` para fallos de red transitorios — requiere decidir qué operaciones son seguras de reintentar (solo idempotentes). *(Red/offline)*
9. **IMPORTANTE** — `getAssignments`/`getAllAssignments` sin fallback de caché offline (a diferencia de tasks/members) — asignaciones desaparecen silenciosamente sin red. *(Red/offline)*
10. **IMPORTANTE** — Carrera de saldo en `redeemReward` entre dos dispositivos — ya analizada y diferida en `docs/atomicidad-commit-pendiente.md` por falta de emulador Firestore contra el que verificar un `:commit` transaccional; sigue abierta, no es regresión nueva. *(Red/offline, decisión ya tomada de diferir)*
11. **IMPORTANTE** — Peso muerto de infraestructura FCM (dependencias + servicio) sin ningún emisor que la use — decisión de si se invierte en backend/Cloud Functions para push real o se retira. *(Rendimiento, ya documentado en ronda de notificaciones)*
12. **MENOR** — Sin límite de retención para `taskHistory`/mensajes de chat (a diferencia de la purga de 90 días de notificaciones) — decisión de producto sobre si aplicar TTL similar. *(Privacidad)*
13. **MENOR** — `HouseholdStore` sin ninguna sincronización interna (más allá del fix ya aplicado en `reconcileHouseholds`) — evaluar si merece un `Mutex` de clase. *(Programador senior)*
14. **MENOR** — `EmptyStateIllustrations`/ilustraciones de marca en estado vacío no siguen el tema Minimal — mismo trasfondo que el punto 7. *(Material 3/Estética)*
15. **MENOR** — `Button` con `contentPadding` reducido por debajo de 48dp de altura efectiva (40dp default de M3) — decisión de densidad visual vs. touch target. *(Accesibilidad)*
16. **MENOR** — Arranque en frío puede mostrar spinner sin mensaje durante ~1-2 min en redes lentas (no caídas) — paralelizar llamadas independientes y/o mostrar mensaje de progreso. *(UX)*

---

## Veredicto por subsistema (Jefe de arquitectura)

Sin árbol de trabajo con cambios pendientes que auditar (todo commiteado); veredicto
sobre el código resultante de `412a25b` + `728b8aa` + `e02bf26`.

- **Red/Firestore**: **con deuda técnica leve**. La capa de acceso (auth, parsing,
  paginación, cascada de borrado) está sana tras el split de v7 y los fixes de
  concurrencia de la ronda A. La deuda es la orquestación de negocio
  (`completeTask`/`completeAssignment`/`reassignTaskCompletion`, ~170 líneas cada
  una) que sigue viviendo en `FirestoreRepository` (1996 líneas, +76 desde v7) en vez
  de en `TaskRepository`, y que ha crecido en vez de reducirse.
- **Notificaciones**: **con deuda técnica leve, con dos hallazgos nuevos de esta
  ronda** (N+1 de `NotificationPollWorker`, anonimización incompleta del contenido de
  chat). Arquitectura de polling justificada y bien ejecutada en lo demás: dedupe por
  ID inmune a desfase de reloj, verificación de pertenencia real antes de resolver
  miembro, purga de 90 días, i18n por lector.
- **Autenticación**: **sana**. `ensureAuth()`/`authMutex` sin fugas; `signOut()`
  invalida cachés correctamente.
- **Almacenamiento local**: **con deuda técnica leve** (revisado a la baja en esta
  ronda tras el hallazgo nuevo de Red/offline): `HouseholdStore` no tiene ninguna
  sincronización interna propia más allá del fix puntual de `reconcileHouseholds`, y
  ese mismo fix deja abierta la ventana de carrera con escrituras externas
  concurrentes (hallazgo CRÍTICO #4 del resumen ejecutivo).
- **Navegación**: **sana**. Pila inicial multi-pantalla de Voyager con deduplicación
  correcta, sin doble navegación.
- **DI**: **sano**. Sin ciclos, `single`/`factory` consistente,
  `NotificationPollWorker` sin Koin justificado.
- **i18n**: **sano**. `AppStrings`/`NotificationText` como objetos puros sin
  dependencias de Compose, consumidos correctamente desde `network/`.
- **Calendario**: **con deuda técnica leve**. Mutex con double-checked locking cierra
  la carrera intra-dispositivo; la carrera inter-dispositivo queda fuera de alcance
  por diseño, documentada como tal.
- **Seguridad/autorización (subsistema añadido tras esta ronda)**: **con deuda técnica
  seria**. El hallazgo CRÍTICO de `ownerId` sin limpiar es un fallo de diseño de
  reglas que sobrevive a cualquier refactor de código cliente mientras no se corrija
  en `firestore.rules` — es el único hallazgo de esta auditoría con capacidad de
  escritura no autorizada persistente, no solo de lectura o de UX.
- **Estadísticas/lógica derivada (subsistema añadido tras esta ronda)**: **con deuda
  técnica seria**. El doble conteo en `computeStats` afecta a una pantalla completa
  de la app (todas sus métricas) por un problema estructural (dos colecciones
  redundantes sin deduplicación), no por un caso de borde aislado.

God objects — veredicto sin cambios respecto a v7, con cifras actualizadas:
`FirestoreRepository.kt` 1996 líneas (era 1920), `TaskScreenModel.kt` 1230 líneas (sin
cambio). Ambos [REQUIERE DECISIÓN] por ser refactors de gran superficie, no aplicados
en esta ronda ni exigidos por ningún hallazgo funcional nuevo — ver detalle en el
informe del Experto 7 más abajo.

---

## Foco de regresión obligatorio — flujo de notificaciones

Verificado el flujo completo `assignTask → notifications/{id} → NotificationPollWorker
(30 min) → notificación del sistema → deep link (MainActivity.consumeDeepLink →
App.kt) → markNotificationRead`; `sendMessage → notificación a los demás miembros`;
auto-exclusión del autor; conjunto de IDs vs. marcador temporal; privacidad con
miembros expulsados; permiso `POST_NOTIFICATIONS`.

**Confirmado SIN regresión** (verificado por múltiples expertos de forma
independiente contra el código real):
- Dedupe por conjunto de IDs (`getNotifiedNotificationIds`/`setNotifiedNotificationIds`),
  inmune a desfase de reloj entre dispositivos.
- Verificación de pertenencia real (`isRealMember`) antes de confiar en
  `resolveCurrentMember` dentro del Worker — la fuga de privacidad hacia miembros
  expulsados sigue mitigada en este punto concreto.
- Exclusión de auto-notificación (`assignedByMemberId`), try/catch por destinatario en
  `sendMessage` (no todo-o-nada), permiso `POST_NOTIFICATIONS` comprobado antes de
  tocar el marcador de sondeo (no se pierden notificaciones si estaba denegado),
  dedupe con la lista in-app (`!it.read`).
- Deep link marca la notificación como leída al consumirse (`consumeDeepLink` →
  `App.kt` → `markNotificationRead`), consistente con tocar la card in-app.
- Idioma de la notificación resuelto por el LECTOR (`NotificationText.kt`), no por
  quien la escribe — la ronda A cerró esta propuesta pendiente de la ronda anterior.
- Purga de notificaciones leídas >90 días aplicada y correcta en su lógica de filtro.
- Canal `task_hub_updates` en `IMPORTANCE_DEFAULT`, consistente con `fcm_general`.

**Regresiones/gaps NUEVOS encontrados en esta ronda** (no estaban en el informe de
notificaciones del 2026-09-05, surgen de auditar la app completa):
1. **CRÍTICO [APLICA YA]** — `NotificationPollWorker` hace 2N lecturas de
   `getMembers()` en vez de N (ver hallazgo #3 del resumen ejecutivo) — no es un bug
   de corrección, es un N+1 de rendimiento/batería no detectado en la ronda anterior.
2. **IMPORTANTE [APLICA YA]** — el contenido de las notificaciones de chat
   (`"$authorName: $preview"`) no se anonimiza cuando el autor abandona/es expulsado
   del hogar, a diferencia de `messages`/comentarios — fuga de privacidad de nombre
   real no cubierta por el mecanismo de anonimización existente.
3. **IMPORTANTE [REQUIERE DECISIÓN]** — `getNotifications` sin `limit` resultó ser
   más caliente de lo documentado: además del sondeo de 30 min, `HouseholdScreen` lo
   invoca cada 30 segundos en primer plano (polling preexistente desde agosto, no
   introducido por la ronda de notificaciones, pero que agrava el mismo problema ya
   señalado como "solo propuesta").
4. **IMPORTANTE [REQUIERE DECISIÓN]** — `purgeOldRead` sin límite de concurrencia:
   inofensivo hoy, riesgo latente a 90 días vista.
5. **MENOR [APLICA YA]** — `NotificationCard` en `NotificationListScreen.kt:201`
   carece de `role = Role.Button` (sus hermanas `MemberCard`/`TaskRow`/
   `RadioOptionRow` sí lo ganaron el mismo día de la ronda A) — descuido de alcance,
   no un fallo funcional.
6. **IMPORTANTE [APLICA YA]** — la card de notificación con el botón "Marcar como
   leída" anidado probablemente produce doble anuncio en TalkBack por conflicto de
   `mergeDescendants` (hallazgo nuevo de accesibilidad, matiza la conclusión "sin
   conflicto real" de la ronda anterior, que solo evaluó el toque, no el anuncio de
   lectura).

**Veredicto global del foco de regresión**: el flujo funcional de notificaciones
(entrega, dedupe, deep link, permisos, i18n) se confirma sano y sin regresiones de
corrección. Los gaps nuevos son de rendimiento (N+1), privacidad (anonimización
incompleta) y accesibilidad (role/anuncio TalkBack) — ninguno rompe la promesa
funcional "cuando se te asigna una tarea o hay un mensaje nuevo, te llega una
notificación", pero sí merecen entrar en la próxima ronda de fixes.

---

## Informes detallados por experto

Los 13 informes completos (con cita archivo:línea, cálculos de contraste, escenarios
de explotación y evidencia verificada) se generaron como parte de esta auditoría y
están resumidos arriba en el resumen ejecutivo y las listas consolidadas. Índice de
expertos y su hallazgo más relevante:

1. **Estética/diseño visual** — sin CRÍTICOS; splash/logo/ilustraciones no respetan
   tema Minimal/oscuro (REQUIERE DECISIÓN); 2 fixes menores de color (APLICA YA).
2. **Funcionalidad end-to-end** — confirma el CRÍTICO de doble conteo de forma
   independiente; encuentra `undoCompleteTask` con fallo parcial silencioso
   (IMPORTANTE); mensaje de chat perdido si falla el envío (MENOR).
3. **Accesibilidad WCAG AA** — sin CRÍTICOS; contraste verificado matemáticamente
   correcto en los 3 temas × 2 modos; `role=Role.Button` faltante en 5 sitios y
   probable doble anuncio TalkBack en cards con botón anidado (IMPORTANTE, nuevo).
4. **UI/Material 3** — sin CRÍTICOS; confirma el gap de `role` en `NotificationCard`
   y lo extiende a `TaskCard` (la card más usada de la app); duplicación de
   `Dialog+Surface+SettingsSheet` en 4 pantallas con componente ya existente sin usar.
5. **UX** — sin CRÍTICOS de bloqueo total; banner de `CreateTaskScreen` puede
   quedarse pegado sin retry (IMPORTANTE); hogar "fantasma" al abandonar
   `JoinHouseholdScreen` a medias, mismo trasfondo que el hallazgo de seguridad
   (IMPORTANTE, REQUIERE DECISIÓN).
6. **Programador senior** — hallazgo nuevo: `completeTask` traga
   `CancellationException` en su catch exterior, a diferencia de su gemela
   `completeAssignment` (IMPORTANTE, APLICA YA); resto de la base de código maneja
   cancelación cooperativa de forma ejemplar.
7. **Jefe de arquitectura** — sin CRÍTICOS; veredicto por subsistema (ver sección
   arriba); god objects confirmados sin agravarse de forma sustancial pero sin
   reducirse tampoco.
8. **QA/bugs** — confirma como CRÍTICO el doble conteo con la evidencia más completa
   (rastro de escritura en ambas colecciones); resto de fixes de la ronda A
   verificados correctos.
9. **Seguridad/AppSec** — hallazgo CRÍTICO nuevo: `ownerId` sin limpiar tras abandono
   sin sucesor, con capacidad de escritura no autorizada persistente; resto
   (tokens, secretos, CSV injection, alineación con reglas) confirmado correcto.
10. **Privacidad/RGPD/menores** — sin CRÍTICOS; anonimización incompleta de
    notificaciones de chat y ausencia de mención de Google Calendar en `privacy.html`
    (IMPORTANTE, APLICA YA); sin CMP/UMP para EEE/UK (REQUIERE DECISIÓN).
11. **Rendimiento** — hallazgo CRÍTICO nuevo: N+1 en `NotificationPollWorker`;
    `purgeOldRead` sin límite de concurrencia y `getNotifications` sin límite más
    caliente de lo documentado (REQUIERE DECISIÓN, ambos).
12. **Red/offline/sincronización** — hallazgo CRÍTICO nuevo: `reconcileHouseholds`
    puede resucitar un hogar recién abandonado; sin retry/backoff de transporte y sin
    caché offline para `assignments` (REQUIERE DECISIÓN).
13. **Cobertura de pruebas** (solo informa) — detectó primero la sospecha del doble
    conteo (origen del hallazgo CRÍTICO #1); top-10 de huecos priorizado, con
    `computeStats`, `HomeScreenModel` (dos definiciones de "pendiente"),
    `NotificationText`, y la lógica de selección de `NotificationPollWorker` como
    las piezas puras de mayor riesgo sin test.

---

## Nota de proceso

Esta auditoría se ejecutó en dos oleadas debido a límites de sesión del proveedor de
IA entre subagentes (sin relación con el contenido técnico): la primera oleada de 13
subagentes falló casi en su totalidad por un límite de cuota que se reseteaba a las
3:30am/8:30am/1:30pm (hora de Madrid); se relanzaron los subagentes fallidos en cada
ventana hasta completar los 13 informes. No afecta a la validez de los hallazgos, que
son 100% independientes de ese incidente operativo.
