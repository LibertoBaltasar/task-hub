# Auditoría de gates por rol — 2026-09-06

Alcance: control de permisos por rol (`admin`/`child` en Firestore, UI
"Admin"/"Miembro") en el estado ACTUAL del working tree de
`composeApp/src/commonMain/kotlin/org/taskhub/`. Solo auditoría — no se ha
tocado ningún archivo de código ni se ha commiteado nada.

## Aviso previo — la "regla de producto" del encargo no coincide con el modelo ya implementado

El encargo da como regla de producto: *"SOLO el admin puede ejecutar acciones
de escritura (crear/editar/borrar tareas y recompensas, canjear, gestionar
miembros, cambiar rol, borrar hogar, crear perfil, ajustes)"*.

`firestore.rules` (v7, con historial documentado desde v3 tras varios paneles
de expertos de seguridad) implementa un modelo **distinto y más permisivo**
para tareas y canjeo, deliberadamente:

- **Tareas** (`households/{hid}/tasks/{tid}`): `allow read, create, delete: if
  isMember(hid) || isOwner(hid)` (línea 334) — **cualquier miembro**, no solo
  admin, puede crear/borrar tareas. El comentario en el propio archivo
  (líneas 323-332) lo confirma explícitamente como diseño intencional.
- **Canjear recompensa** (`rewardRedemptions` create, línea 394): `if
  isMember(hid) || isOwner(hid)` — cualquier miembro puede canjear, no solo
  admin.
- **Borrar hogar** (`households/{hid}` update/delete, línea 247): `if
  isOwner(hid)` — restringido al **owner**, no a "admin" en general (un
  miembro con rol `admin` que no sea el owner NO puede borrar el hogar según
  el backend).
- Lo que sí coincide con la regla del encargo: **gestionar miembros/cambiar
  rol** (`isTrusted(hid)` = owner o admin, línea 308/320) y **CRUD de
  recompensas como catálogo** (`rewards` write, línea 387: `isTrusted(hid)`).

Esto importa porque varios de los "SIN gate" que reporto en la sección 2 son
en realidad coherentes con el modelo de `firestore.rules` (tareas, canjeo) y
puede que NO sean bugs sino que el encargo parta de una premisa de producto
distinta a la ya vigente. Los reporto de todos modos, tal y como pide el
encargo, pero marcados con su discrepancia respecto al backend. El único caso
donde el gate de UI está objetivamente mal incluso bajo el modelo YA vigente
del backend es **borrar hogar** (sección 2, #1): ahí ni siquiera hay gate de
"admin", así que ni admins-no-owner ni miembros normales deberían ver ese
botón, y hoy lo ven todos.

## 1) Acciones correctamente gated

| Acción | Archivo:línea | Gate | Resuelto por hogar |
|---|---|---|---|
| Crear recompensa | `ui/screens/RewardListScreen.kt:103,111` (botón) | `isAdmin` (`myMember?.role=="admin" \|\| isOwner`, líneas 82-98) | Sí — `myMember` sale de `memberState` cargado con `loadMembers(householdId)`; `isOwner` viene de `isHouseholdOwner(householdId)` |
| Borrar recompensa | `ui/screens/RewardListScreen.kt:322-332` | `isAdmin` (mismo cálculo) | Sí |
| Reasignar quién completó una tarea (mueve puntos entre miembros) | `ui/screens/TaskDetailScreen.kt:571` (botón), gate calculado en línea 178 | `isAdmin` (`memberMap[currentMemberId]?.role=="admin" \|\| isOwner`) | Sí — `memberMap` sale de `state.members` cargado para ESTE `householdId` vía `loadTaskDetail`; `isOwner` vía `isHouseholdOwner(householdId)` |
| Cambiar rol de un miembro | `ui/components/HouseholdMemberList.kt:264` (`if (isAdmin && !isSelf)`), disparo en `ui/screens/HouseholdScreen.kt:566-567` | `isAdmin` calculado en `HouseholdScreen.kt:171-172` (`myMember?.role=="admin" \|\| currentUserId==ownerHousehold.ownerId`) | Sí — `myMember` sale de `memberState` de ESTE hogar; `ownerHousehold` es el household actual |
| Expulsar miembro | `ui/components/HouseholdMemberList.kt:264,319-341`, disparo en `HouseholdScreen.kt:569-570` | Mismo `isAdmin` que cambio de rol | Sí |
| `isHouseholdOwner()` (base de todos los `isOwner` de arriba) | `network/FirestoreRepository.kt:141` | recibe `householdId` como parámetro y consulta ESE hogar | Sí, por construcción |

Nota: crear/borrar tarea (FAB "Nueva" en `TaskListScreen.kt:201-208`, botón
"Editar"/"Borrar" en `TaskDetailScreen.kt:145-157`) y canjear recompensa
(`RewardListScreen.kt:308-319`, `MemberRewardScreen.kt:212-233`) están SIN
gate — ver sección 2 — pero eso coincide con `firestore.rules` (sección de
aviso previo), así que el backend no lo bloquea; solo lo marco como
discrepancia frente al encargo, no como vulnerabilidad de datos.

## 2) Acciones SIN gate (bugs, priorizadas)

1. **[CRÍTICO] Borrar hogar — sin ningún gate, ni siquiera "admin"**
   `ui/screens/HouseholdScreen.kt:381-392`. El `IconButton` que abre
   `DeleteHouseholdConfirmDialog1`/`2` (que a su vez llama a
   `householdModel.deleteHousehold(...)`, línea 245) está en las `actions` de
   `TaskHubTopBar` sin ninguna condición — la variable `isAdmin` se calcula en
   la línea 171 pero **nunca se usa para ocultar este botón**. Cualquier
   miembro (rol `child`/"Miembro") ve el icono de papelera y puede lanzar el
   flujo de doble confirmación de borrado del hogar completo. El backend
   (`firestore.rules:247`, `isOwner(hid)`) sí lo bloquea para quien no sea el
   owner, así que el borrado real fallará con un error de permisos para
   miembros y para admins-no-owner — pero la UI no debería ni ofrecer la
   opción, y el fallo llega como snackbar de error genérico
   (`householdErrorMessage`, línea 250-253) en vez de nunca mostrar el botón.
   Esto es un bug tanto contra la regla del encargo (solo admin) como contra
   el modelo real del backend (solo owner): ninguno de los dos se refleja en
   la UI.

2. **[ALTO, pero coincide con backend] Crear tarea — sin gate `isAdmin`**
   `ui/screens/TaskListScreen.kt:201-208` (`TextButton` "Nueva" en la topbar)
   y `TaskListScreen.kt:263-265` (botón "crear primera tarea" en estado
   vacío) y `ui/components/HouseholdMemberList.kt:344-349` ("crear tarea"
   preasignada a un miembro, visible a cualquiera). No hay ninguna
   comprobación de rol antes de navegar a `CreateTaskScreen`. Coincide con
   `firestore.rules:334` (`allow create: if isMember(hid) || isOwner(hid)`),
   así que cualquier miembro autenticado puede crear la tarea igualmente en
   el backend — el gate de UI faltante no abre ninguna vía nueva de escritura
   que el backend no permita ya. Reportado porque contradice la regla del
   encargo.

3. **[ALTO, pero coincide con backend] Editar/borrar tarea — sin gate
   `isAdmin`** `ui/screens/TaskDetailScreen.kt:145-157`. Los `IconButton` de
   editar (`Icons.Default.Edit` → `navigator.push(EditTaskScreen(...))`) y
   borrar (`Icons.Default.Delete` → `showDeleteDialog = true` →
   `model.deleteTask(...)`, línea 119) están siempre visibles, sin condición
   de rol. Coincide con `firestore.rules:334` (delete abierto a cualquier
   miembro; update también abierto salvo la reasignación de `completedBy`,
   que sí está gateada — ver sección 1). Mismo comentario que el punto 2:
   contradice el encargo, no al backend.

4. **[MEDIO, pero coincide con backend] Canjear recompensa — sin gate**
   `ui/screens/RewardListScreen.kt:178-188,308-319` (botón "Canjear" en la
   tarjeta, siempre visible) y `ui/screens/MemberRewardScreen.kt:212-233`
   (pantalla de confirmación de canje, sin ninguna comprobación de rol).
   Coincide con `firestore.rules:394` (`rewardRedemptions` create abierto a
   `isMember(hid) || isOwner(hid)`). Contradice la regla del encargo
   ("canjear" listado como acción admin-only), pero un miembro "child"
   canjeando su propia recompensa con sus propios puntos es, de hecho, el
   flujo de producto obvio — vale la pena confirmar con el encargo si esa
   regla es realmente la intención, porque de aplicarse literalmente
   impediría a cualquier "Miembro" gastar sus propios puntos.

No se encontraron acciones de "ajustes del hogar" separadas de los ajustes
personales (perfil propio, exportar CSV, borrar cuenta) — `HouseholdSettingsDialog`
(`ui/components/HouseholdDialogs.kt:172-198`) solo envuelve `SettingsSheet`,
que no expone ninguna acción de escritura a nivel de hogar (nombre, config),
así que no hay "ajustes del hogar" que auditar como acción de escritura
gateable hoy. "Crear perfil" (`CreateProfileScreen.kt`) solo es alcanzable
por el `ownerId` recién creado (por construcción de la navegación, ver
comentario líneas 35-43 del propio archivo) y el backend
(`firestore.rules:263-281`) exige `isOwner(hid) || isAdminMember(hid)` para
esa rama de `create` — coherente, no es una acción abierta a cualquier rol.

## 3) Roles resueltos globalmente (bug potencial admin-en-un-hogar/child-en-otro)

No se ha encontrado ningún caso de resolución global de rol en los gates
auditados. Todos los `isAdmin`/`isOwner` revisados (`HouseholdScreen.kt:171-172`,
`TaskDetailScreen.kt:96-101,178`, `RewardListScreen.kt:82-98`) se derivan de:
- `myMember`/`memberMap[currentMemberId]`, extraído siempre de la lista de
  miembros cargada para el `householdId` de la pantalla actual
  (`loadMembers(householdId)` / `loadTaskDetail(householdId, taskId)`), y
- `isHouseholdOwner(householdId)` (`FirestoreRepository.kt:141`), que recibe
  el `householdId` como parámetro explícito y consulta el documento de ESE
  hogar (`ownerId`) — no hay ningún campo de rol global cacheado ni
  reutilizado entre hogares.

No se detectó, por tanto, el patrón de riesgo que describe el encargo (admin
en un hogar visto como admin en otro).

## Veredicto global

El modelo **NO** cumple literalmente la regla de producto tal y como la
describe el encargo: crear/editar/borrar tareas y canjear recompensas están
sin gate de rol en la UI (y así lo permite también el backend,
deliberadamente, según el propio historial de `firestore.rules`). El único
gate ausente que es un bug incluso bajo el modelo YA vigente del backend es
el botón de borrar hogar (sección 2, #1), que no comprueba ni "admin" ni
"owner" en la UI. El resto de acciones de alto impacto (gestionar
miembros/cambiar rol, CRUD de recompensas-catálogo) SÍ están correctamente
gateadas por `isAdmin`, y ese gate se resuelve siempre por hogar, no de forma
global — no hay bug de "admin en un hogar, child en otro" en lo auditado.
Antes de "corregir" los puntos 2-4 de la sección 2, conviene confirmar si la
regla de producto del encargo es un cambio deseado o una descripción
desactualizada del modelo real (que ya pasó varios paneles de seguridad para
llegar a su estado actual).
