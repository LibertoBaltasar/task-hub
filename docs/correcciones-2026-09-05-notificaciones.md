# Correcciones 2026-09-05 — notificaciones (tarea asignada + mensaje nuevo)

Implementa el encargo "cuando se te asigna una tarea, o cuando hay un mensaje
en uno de los grupos familiares en los que estás, debe aparecer una
notificación asociada a eso" end-to-end, más los hallazgos reales que el
panel de 6 expertos encontró revisando el diff ya aplicado. Ver
`docs/review-panel-expertos-notificaciones-2026-09-05.md` para el detalle
completo por experto. HEAD de partida: `4b5d9ef`, árbol de trabajo limpio.
Recurrencia explícitamente FUERA DE ALCANCE.

## 1. Mensaje nuevo en el chat no notificaba a nadie — APLICADO

`HouseholdRepository.sendMessage` gana dependencias `MemberRepository`/
`NotificationRepository`/`SettingsStore`. Tras guardar el mensaje, crea una
notificación (`households/{id}/notifications`, mismo mecanismo que
`TaskRepository.assignTask`) para cada miembro del hogar EXCEPTO el autor.
`taskId=""` es el centinela de "es un mensaje de chat, no una tarea" —
usado por `NotificationListScreen` y `NotificationPollWorker` para decidir a
dónde navegar. El try/catch de creación de notificación está POR
destinatario (no uno solo envolviendo todo el bucle): un fallo puntual con
un miembro no deja al resto sin notificar (hallazgo del panel, Programador
senior).

## 2. Nada entregaba las notificaciones al dispositivo — APLICADO (polling)

Decisión de arquitectura (sin backend/Cloud Functions, el token FCM de
`users/{uid}` no tiene emisor): WorkManager periódico en Android, 30 min,
`NetworkType.CONNECTED`, registrado en `TaskHubApplication.onCreate` con
`enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP)`.

`NotificationPollWorker` (nuevo, `androidMain`) construye su propio grafo de
dependencias (`Settings()`, `SettingsStore`, `HouseholdStore`, `TaskCache`,
`FirestoreClient`, `MemberRepository`, `NotificationRepository`) SIN Koin —
el contenedor de `KoinApplication` solo vive dentro del árbol de Compose, y
este Worker debe poder ejecutarse con la Activity cerrada. `Settings()` sin
argumentos es seguro (`multiplatform-settings-no-arg`, auto-inicializado vía
androidx.startup con el mismo `Application`, mismas SharedPreferences que
`AppModule`).

Por cada hogar guardado localmente (`HouseholdStore.getSavedHouseholds()`):

1. **Verificación de pertenencia real** antes de confiar en
   `resolveCurrentMember`: comprueba que la identidad actual
   (`firestoreClient.currentUserIdentities()`) coincide con el `userId` de
   algún miembro real de `getMembers(householdId)`. Sin esto, el fallback
   interno de `resolveCurrentMemberUncached` ("si no hay match por
   identidad, usa el primer miembro existente") podría hacer que el sondeo
   heredara la identidad de OTRO miembro real si el usuario ya fue expulsado
   de ese hogar — el sondeo en segundo plano ejercita ese fallback para
   CUALQUIER hogar guardado, a diferencia del resto de la app (hallazgo del
   panel, QA, CRÍTICO de privacidad).
2. Trae `getNotifications(householdId)` filtradas por `memberId == yo` y
   `!read` (excluye las ya marcadas leídas en la app — hallazgo de QA).
3. Compara contra `SettingsStore.getNotifiedNotificationIds(householdId)` —
   un CONJUNTO DE IDs, no un marcador de tiempo. Se probó primero un
   marcador de `createdAt`, pero ese timestamp lo genera el reloj LOCAL del
   dispositivo que escribe (asignador/autor), no uno de servidor — un reloj
   adelantado en el emisor podía "inflar" el marcador del receptor y ocultar
   PARA SIEMPRE una notificación genuinamente posterior de otro dispositivo
   con `createdAt` menor (hallazgo del panel, Notificaciones/Push,
   CRÍTICO). El conjunto de IDs es inmune a esto.
4. Si hay IDs nuevos, muestra una notificación local por cada uno
   (`NotificationHelper.showUpdateNotification`, canal `task_hub_updates`,
   `IMPORTANCE_DEFAULT` — bajado desde `HIGH` tras el hallazgo de
   Accesibilidad: un mensaje de chat no es tan urgente como un plazo que
   vence, y el mismo evento "tarea asignada" por FCM ya usa `DEFAULT`).
5. Si `POST_NOTIFICATIONS` está denegado, `doWork()` no toca NINGÚN estado de
   sondeo (se comprueba una vez al principio) — evita perder notificaciones
   para siempre si el usuario reactiva el permiso más tarde (hallazgo de QA).

Deep link al tocarla: `MainActivity.consumeDeepLink` lee los extras
`householdId`/`taskId`/`notificationId` del Intent (en `onCreate` Y
`onNewIntent`, para cuando la Activity ya estaba viva) y los pasa a
`App(deepLinkHouseholdId, deepLinkTaskId, deepLinkNotificationId)`. Dentro
del `Navigator` ya creado con `HomeScreen()`, un `LaunchedEffect` hace `push`
a `TaskDetailScreen`/`HouseholdScreen` según si `taskId` está vacío, y marca
la notificación como leída en Firestore (`repo.markNotificationRead`) — sin
esto, tocar la notificación del sistema no tenía ningún efecto sobre su
estado "no leída" en la lista in-app, a diferencia de tocar la card
(hallazgo de UX). De paso se corrigió un bug preexistente en
`NotificationHelper.showTaskReminder`: los extras del deep link del
recordatorio de tarea eran código muerto (`getLaunchIntentForPackage` casi
nunca devuelve null, así que la rama del `Intent` con los extras nunca se
ejecutaba) — ahora usa el mismo patrón de Intent explícito que
`showUpdateNotification`.

`NotificationListScreen` (in-app): la card ahora es clicable — marca como
leída y navega a la tarea o al chat, igual que el deep link del sistema
(antes solo el botón "Marcar como leída" tenía acción).

Auto-notificación: `TaskRepository.assignTask` gana `assignedByMemberId`
(no se notifica si coincide con el `memberId` que se está asignando).
`TaskScreenModel` lo pasa desde `createdBy` (creación de tarea) o
`_currentMemberId` (reasignación explícita). De paso, el flujo de creación
ahora también pasa el `taskTitle` real (antes quedaba en `""`, así que la
notificación de auto-asignación al crear una tarea siempre mostraba el texto
genérico en vez del título).

`GoogleAuthManager.signOut()` limpia el estado de sondeo de TODOS los
hogares (`SettingsStore.clearNotificationPollState()`) — en un dispositivo
familiar compartido, evita que el estado de la cuenta anterior oculte
notificaciones legítimas de la cuenta entrante o filtre datos de la
saliente.

## 3. Textos hardcodeados en español — APLICADO

Nuevas claves ES/EN en `AppStrings`: `notification_task_assigned_title`,
`notification_task_assigned_body_prefix`, `notification_task_assigned_body_generic`,
`notification_new_message_title`, `notification_channel_updates_name`,
`notification_channel_updates_desc`. De paso se migraron dos strings YA
hardcodeadas en español desde antes de este encargo:
`TaskRepository.assignTask` y `FirestoreRepository.regenerateNextAssignment`
(ambas construían el título/mensaje de "tarea asignada" con literales en
español). `settings_notifications_desc` se actualizó (ES/EN) para reflejar
que el interruptor ahora también gatea tareas asignadas y mensajes, no solo
recordatorios (hallazgo de UX).

## Verificación (OBLIGATORIO)

```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```
`BUILD SUCCESSFUL` — sin errores; solo warnings de deprecación preexistentes,
ninguno introducido por esta ronda. Verificado dos veces (implementación
inicial y tras aplicar los hallazgos del panel).

```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` — **160 tests, 0 fallos**, mismo recuento que antes de
esta ronda. No se añadieron tests nuevos: todo el código nuevo vive en
`androidMain` (`NotificationPollWorker`, wiring de `MainActivity`), sin motor
HTTP inyectable para simularlo desde `commonTest`/`jvmTest` (limitación ya
documentada en rondas anteriores, panel v7 Experto 13).

## PROPUESTAS pendientes (no aplicadas, ver informe del panel para detalle)

1. Push FCM real vía Cloud Functions en vez de polling — requiere backend
   nuevo, decisión de producto.
2. `NotificationRepository.getNotifications` sin filtro/`limit` — la
   colección crece sin purga, coste de red crece con el tiempo.
3. Idioma de la notificación fijado por quien la escribe, no por quien la
   lee (mismo patrón preexistente en `member_deleted_name`).
4. `createdBy` puede llegar vacío a `CreateTaskScreen` en un borde de
   navegación — auto-notificación falsa al crear tarea en ese caso.
5. Fallback "primer miembro existente" de `resolveCurrentMemberUncached`
   (código compartido) — mitigado solo en el Worker nuevo; revisar el resto
   de la app en una ronda dedicada a `MemberRepository`.
6. Doble salto visual (Home → destino) en arranque en frío desde una
   notificación — cosmético.

## Archivos tocados (14)

```
composeApp/src/androidMain/kotlin/org/taskhub/MainActivity.kt
composeApp/src/androidMain/kotlin/org/taskhub/NotificationHelper.kt
composeApp/src/androidMain/kotlin/org/taskhub/NotificationPollWorker.kt (nuevo)
composeApp/src/androidMain/kotlin/org/taskhub/TaskHubApplication.kt
composeApp/src/commonMain/kotlin/org/taskhub/App.kt
composeApp/src/commonMain/kotlin/org/taskhub/di/AppModule.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/HouseholdRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/network/TaskRepository.kt
composeApp/src/commonMain/kotlin/org/taskhub/storage/SettingsStore.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/GoogleAuthManager.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt
composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/NotificationListScreen.kt
```
