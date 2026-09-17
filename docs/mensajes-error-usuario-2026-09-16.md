# Mensajes de error entendibles para el usuario (2026-09-16)

## 0. Resumen del enfoque

Se centralizó la clasificación de errores en dos capas:

- `network/ErrorCategory.kt` (nuevo): `Throwable.errorCategory()` clasifica
  cualquier excepción de red en `NO_CONNECTION` (fallo de transporte, sin
  respuesta HTTP — timeout/DNS/sin conexión), `GONE_OR_FORBIDDEN` (403/404 de
  Firestore o de una Cloud Function), `SERVER` (5xx) u `OPERATION` (el resto
  de 4xx, específico de la operación). `CloudFunctionException` (antes solo
  llevaba el `status` simbólico de la función, p.ej. `"ABORTED"`) ahora
  también conserva el `httpStatusCode` real para poder clasificarse igual que
  `FirestoreException`.
- `ui/i18n/ErrorMessages.kt` (nuevo): `Throwable.toUserMessage(lang,
  operationKey)` resuelve esa categoría a la clave de `AppStrings`
  correspondiente (`error_no_connection` / `error_gone_or_forbidden` /
  `error_server` / la clave específica de la operación) y la traduce.

Todos los `ScreenModel`/managers que capturaban `Exception` y mostraban
`e.message ?: s("...")` (texto técnico crudo de Firestore/Ktor cuando existía,
sin distinguir la causa) pasan ahora por este helper. El detalle técnico
(excepción original, código HTTP) deja de llegar a la UI — no había ningún
logging previo que capturarlo rompiera; no se ha añadido logging nuevo más
allá del `println` de depuración ya existente en `TaskScreenModel` (gateado
por `DebugFlags.isEnabled`).

## 1. Inventario auditado (mensaje original → nuevo → causa)

### 1.1 Mensajes genéricos por categoría (nuevos, compartidos por toda la app)

| Clave | Antes | Ahora (ES) | Causa |
|---|---|---|---|
| `error_no_connection` | (no existía; caía en el mensaje de la operación) | "Sin conexión a internet. Comprueba tu conexión y vuelve a intentarlo." | Sin conexión (fallo de transporte) |
| `error_gone_or_forbidden` | `FIRESTORE_GONE_MESSAGE` (constante Kotlin fija en español, fuera de `AppStrings`) | "Este espacio ya no existe o ya no tienes acceso a él." | Sin permisos / recurso borrado (403/404) |
| `error_server` | (no existía; cada operación mostraba su propio "Error al …" o el texto crudo de Firestore) | "Algo ha ido mal en el servidor. Inténtalo de nuevo en unos minutos." | Problema del servidor (5xx) |

### 1.2 Fallbacks específicos de operación (reescritos a "qué pasó + qué hacer")

Estas claves ahora solo se usan cuando la categoría es `OPERATION` (4xx no
clasificable como sin acceso) — antes eran el único mensaje, sin importar la
causa real del fallo, y en la mayoría de sitios competían con `e.message`
(texto crudo de Firestore/Ktor) que se mostraba primero si existía.

| Clave | Antes | Ahora (ES) |
|---|---|---|
| `tasks_error_loading` | "Error al cargar espacios" | "No se pudieron cargar tus espacios. Tira hacia abajo para reintentarlo." |
| `messages_error_loading` | "Error al cargar los mensajes" | "No se pudieron cargar los mensajes. Inténtalo de nuevo." |
| `messages_error_sending` | "Error al enviar el mensaje" | "No se pudo enviar el mensaje. Inténtalo de nuevo." |
| `member_error_loading` | "Error al cargar miembros" | "No se pudieron cargar los miembros. Inténtalo de nuevo." |
| `member_error_adding` | "Error al añadir miembro" | "No se pudo añadir al miembro. Inténtalo de nuevo." |
| `member_error_removing` | "Error al eliminar miembro" | "No se pudo eliminar al miembro. Inténtalo de nuevo." |
| `member_error_role` | "Error al cambiar el rol" | "No se pudo cambiar el rol. Inténtalo de nuevo." |
| `reward_error_loading` | "Error al cargar recompensas" | "No se pudieron cargar las recompensas. Inténtalo de nuevo." |
| `reward_error_creating` | "Error al crear recompensa" | "No se pudo crear la recompensa. Revisa los datos e inténtalo de nuevo." |
| `reward_error_deleting` | "Error al eliminar recompensa" | "No se pudo eliminar la recompensa. Inténtalo de nuevo." |
| `reward_error_redeeming` | "Error al canjear recompensa" | "No se pudo canjear la recompensa. Inténtalo de nuevo." |
| `google_auth_error_sign_in` | "Error al iniciar sesión con Google" | "No se pudo iniciar sesión. Comprueba tu conexión y vuelve a intentarlo." |
| `profile_error_loading_own` | "Error al cargar tu perfil" | "No se pudo cargar tu perfil. Inténtalo de nuevo." |
| `profile_error_loading` | "Error al cargar el perfil" | "No se pudo cargar el perfil. Inténtalo de nuevo." |
| `profile_error_saving` | "Error al guardar el perfil" | "No se pudo guardar el perfil. Inténtalo de nuevo." |
| `notification_error_loading` | "Error al cargar notificaciones" | "No se pudieron cargar las notificaciones. Inténtalo de nuevo." |
| `task_error_loading` | "Error al cargar tareas" | "No se pudieron cargar las tareas. Tira hacia abajo para reintentarlo." |
| `task_error_creating` | "Error al crear tarea" | "No se pudo crear la tarea. Revisa los datos e inténtalo de nuevo." |
| `task_error_completing` | "Error al completar tarea" | "No se pudo completar la tarea. Inténtalo de nuevo." |
| `task_error_reassigning` | "Error al cambiar quién hizo la tarea" | "No se pudo cambiar quién hizo la tarea. Inténtalo de nuevo." |
| `task_error_undo` | "Error al deshacer. La tarea puede seguir marcada como completada." | "No se pudo deshacer. La tarea puede seguir marcada como completada. Inténtalo de nuevo." |
| `task_error_loading_single` | "Error al cargar tarea" | "No se pudo cargar la tarea. Inténtalo de nuevo." |
| `task_error_assigning` | "Error al asignar tarea" | "No se pudo asignar la tarea. Inténtalo de nuevo." |
| `task_error_updating` | "Error al actualizar tarea" | "No se pudieron guardar los cambios de la tarea. Inténtalo de nuevo." |
| `task_error_deleting` | "Error al eliminar tarea" | "No se pudo eliminar la tarea. Inténtalo de nuevo." |
| `task_comment_error_loading` | "Error al cargar comentarios" | "No se pudieron cargar los comentarios. Inténtalo de nuevo." |
| `task_comment_error_adding` | "Error al añadir comentario" | "No se pudo publicar el comentario. Inténtalo de nuevo." |
| `calendar_sync_error` | "Error al sincronizar con Google Calendar" | "No se pudo sincronizar con Google Calendar. Inténtalo de nuevo." |
| `household_error_creating` | "Error al crear el espacio" | "No se pudo crear el espacio. Inténtalo de nuevo." |
| `household_error_invalid_invite_code` | "Código de invitación inválido" | "Código de invitación inválido. Revísalo e inténtalo de nuevo." |
| `household_error_loading` | "Error al cargar el espacio" | "No se pudo cargar el espacio. Inténtalo de nuevo." |
| `household_error_deleting` | "Error al eliminar el espacio" | "No se pudo eliminar el espacio. Inténtalo de nuevo." |
| `household_error_leaving` | "Error al salir del espacio" | "No se pudo salir del espacio. Inténtalo de nuevo." |
| `ranking_error_loading` | "Error al cargar miembros" | "No se pudieron cargar los miembros. Inténtalo de nuevo." |
| `stats_error_loading` | "Error al cargar estadísticas" | "No se pudieron cargar las estadísticas. Inténtalo de nuevo." |

### 1.3 Claves nuevas (casos que antes no tenían mensaje vía `AppStrings`)

| Clave | Antes | Ahora (ES) | Causa |
|---|---|---|---|
| `profile_error_not_authenticated` | Español hardcodeado en `ProfileScreenModel` ("No estás autenticado. Inicia sesión primero." / "No estás autenticado", dos variantes) | "No estás autenticado. Inicia sesión de nuevo para continuar." | Sesión no iniciada (mismo texto para cargar y guardar perfil) |
| `calendar_sync_no_assignment` | Español hardcodeado en `TaskScreenModel.syncTaskToCalendarNow` ("No se pudo determinar tu asignación para esta tarea") | (mismo texto, ahora vía `AppStrings` con EN) | Estado local no resuelto aún |
| `household_error_joining` | No existía — el `catch (e: Exception)` genérico de `joinHousehold` reutilizaba por error `household_error_invalid_invite_code`, mostrando "Código de invitación inválido" ante un fallo de RED real | "No se pudo unir al espacio. Inténtalo de nuevo." | Operación (4xx) / clasificado si aplica |
| `household_error_deleting_cascade` | `HouseholdCascadeIncompleteException.message` (texto fijo en español con el ID interno del hogar embebido) mostrado crudo vía `e.message ?: s("household_error_deleting")` | "No se pudieron borrar todos los datos del espacio. El espacio no se ha eliminado; puedes reintentarlo más tarde." | Cascade-delete parcial (por tipo, no por mensaje) |

### 1.4 Eliminados (redundantes tras la reescritura)

- `household_member_list_error` / `calendar_error_prefix` — ambos eran
  `"Error: %s"` envolviendo un mensaje que ya viene completo y traducido desde
  el `ScreenModel`; con los mensajes nuevos (ya una frase completa tipo "Sin
  conexión a internet…") el prefijo quedaba redundante/confuso. Se muestra
  el mensaje del estado directamente (`HouseholdMemberList.kt`,
  `CalendarScreen.kt`).
- `FIRESTORE_GONE_MESSAGE` (constante en `network/FirestoreException.kt`) —
  sustituida por la clave `error_gone_or_forbidden` de `AppStrings` (mismo
  texto, ahora con traducción EN real en vez de español fijo). Se conserva
  `isGoneOrForbidden` (reutilizado por `errorCategory()`).

## 2. Archivos modificados

- `composeApp/src/commonMain/kotlin/org/taskhub/network/ErrorCategory.kt` (nuevo) — clasificación `Throwable.errorCategory()` / `toUserMessageKey()`.
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/ErrorMessages.kt` (nuevo) — `Throwable.toUserMessage(lang, operationKey)`.
- `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreException.kt` — quita `FIRESTORE_GONE_MESSAGE`.
- `composeApp/src/commonMain/kotlin/org/taskhub/network/CloudFunctionsClient.kt` — `CloudFunctionException` conserva `httpStatusCode`.
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt` — claves nuevas/reescritas (ES+EN), ver inventario.
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HouseholdScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/MemberScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/NotificationScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/ProfileScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/TaskCommentsScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/HomeScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/StatsScreenModel.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/models/GoogleAuthManager.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdMemberList.kt`
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CalendarScreen.kt`
- `composeApp/src/commonTest/kotlin/org/taskhub/ui/models/TaskScreenModelTest.kt` — test actualizado (ver incidencias).

No se tocó lógica de negocio: retries, flujos de completar/deshacer/reasignar
tarea, atomicidad de transferencias, ni `isOnline()`.

## 3. Verificación

```
$ ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
...
BUILD SUCCESSFUL in 38s
25 actionable tasks: 1 executed, 24 up-to-date
```

```
$ ./gradlew :composeApp:jvmTest --rerun-tasks --console=plain
...
BUILD SUCCESSFUL in 19s
15 actionable tasks: 15 executed
```

## 4. Incidencias

- **Test actualizado**: `TaskScreenModelTest.loadTasks_recursoBorradoOSinAcceso_marcaErrorSinTocarOffline`
  asertaba `FIRESTORE_GONE_MESSAGE` (la constante eliminada). Se actualizó a
  `AppStrings.get("error_gone_or_forbidden", "es")`, mismo texto en español,
  ahora vía i18n.
- **Ninguna clave renombrada** — todas las claves de `AppStrings` tocadas
  mantienen su nombre; solo cambió el VALOR (texto) o se añadieron claves
  nuevas. No hizo falta grep de usos huérfanos por renombrado.
- **Bug encontrado de paso**: `HouseholdScreenModel.joinHousehold` reutilizaba
  la clave `household_error_invalid_invite_code` como fallback genérico de
  CUALQUIER excepción, así que un fallo de red real al unirse a un hogar
  mostraba "Código de invitación inválido" (mensaje de validación
  incorrecto). Corregido con la clave nueva `household_error_joining` +
  clasificación por categoría.
- **`CloudFunctionException` ampliada**: se le añadió `httpStatusCode` (antes
  solo tenía el `status` simbólico de la función, p.ej. `"ABORTED"`) para que
  los fallos de `completeTask`/`undoTaskCompletion`/`reassignTaskCompletion`/
  `completeAssignment` (Cloud Functions) se puedan clasificar exactamente
  igual que un fallo directo de Firestore. Es plumbing puro — no cambia
  ningún comportamiento existente (los conflictos de concurrencia optimista
  se siguen mapeando por `status` simbólico, sin tocar).

## 5. Último commit

```
938b0fe wip: checkpoint 02-mensajes-error-entendibles-usuario-2026-09-16.md
```
