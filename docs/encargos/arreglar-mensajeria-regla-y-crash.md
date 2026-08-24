---
workdir: /home/liberto/task-hub
max_turns: 40
allowed_tools: Read,Edit,Write,Bash
---
# Encargo: arreglar mensajería (regla Firestore) + no crashear en hogares sin miembro

## Objetivo
Dos bugs bloquean la mensajería y hacen crashear la app:

1. **Falta la regla de Firestore para la subcolección `messages`.** En Firestore,
   sin regla = acceso denegado por defecto, así que `sendMessage` y `getMessages`
   fallan SIEMPRE con PERMISSION_DENIED.
2. **`resolveCurrentMember` crashea la app** cuando el hogar no tiene ningún
   documento de miembro válido: llega a `createMember`, el POST de Firestore
   falla (o devuelve respuesta sin `name`), y `extractDocId` lanza
   `IllegalStateException` sin capturar → `FATAL EXCEPTION`.

## Contexto (LEER antes de tocar nada)
- Firestore vía REST con Ktor. Repo: `network/FirestoreRepository.kt`.
- `firestore.rules` (raíz del repo) ya tiene reglas para tasks, assignments,
  comments, taskHistory, notifications, rewards, rewardRedemptions,
  achievements — pero NO para `messages`. Réplica el patrón de las demás.
- `resolveCurrentMember(householdId)` (líneas ~680-705): hace `getMembers`
  (captura excepción → emptyList), si no hay miembros llama a
  `createMember(...).id`. El fallo está en que `createMember` puede lanzar y no
  hay try/catch → crashea la UI.
- `createMember` (líneas ~583-666): POST a
  `$baseUrl/households/$householdId/members` y luego `extractDocId(response.name)`
  (línea 652). Si la respuesta no trae `name`, `extractDocId` lanza
  `IllegalStateException` ("Firestore response missing document name").
- `extractDocId` (líneas ~2150-2159): lanza si `resourceName.isBlank()`.
- Modelo: `ui/models/HouseholdScreenModel.kt` (mensajería). Pantalla:
  `ui/screens/HouseholdScreen.kt` llama a `repo.resolveCurrentMember(householdId)`
  en un `LaunchedEffect` para obtener el id del miembro que envía mensajes.
- Solo material-icons-core. i18n `AppStrings.kt` (ES+EN).

## Trabajo

### 1. Añadir la regla `messages` en `firestore.rules`
Justo junto a las demás subcolecciones del hogar, añadir:
```
match /households/{hid}/messages/{mid} {
  allow read, write: if isMember(hid) || isOwner(hid);
}
```
(No hay restricción extra: cualquier miembro/owner puede leer y escribir.)

### 2. Hacer `resolveCurrentMember` robusto (NO crashear nunca)
- Envolver la llamada a `createMember` en try/catch.
- Si `createMember` lanza, NO propagar la excepción: devolver un fallback
  seguro (p. ej. el localId del usuario, o una cadena vacía, o un ID sintético
  no persistente). Lo importante es que la UI NO reviente.
- Alternativa igual de válida: cambiar el tipo de retorno para que la pantalla
  pueda distinguir "sin miembro" y mostrar un mensaje amigable en vez de
  intentar crear. Elige la opción más limpia, pero el requisito duro es:
  **abrir un hogar sin miembros NUNCA debe crashear**.

### 3. (Opcional pero recomendable) No intentar crear miembro al abrir
La pantalla del hogar solo quiere el id del miembro para el chat. Si no hay
miembro, lo correcto es deshabilitar el envío con un aviso ("No tienes perfil
en este espacio"), no crear uno a la fuerza. Si decides este camino, asegura que
el resto de la app (completar tareas, etc.) siga funcionando con el fallback
existente.

## Definición de hecho
- `cd ~/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`
  termina en BUILD SUCCESSFUL.
- `firestore.rules` contiene la regla `messages` (idéntica en forma a `tasks`).
- `resolveCurrentMember` no lanza excepción si el hogar no tiene miembros.
- Abrir un hogar sin miembros no crashea la app.

## Entrega
- Hacer el trabajo y compilar. NO commit, push ni bump (lo hace el orquestador).
- Resumir: archivos tocados, cómo se resuelve el fallback sin miembro, y
  resultado de la compilación.
