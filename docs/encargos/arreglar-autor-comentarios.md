---
workdir: /home/liberto/task-hub
---
# Encargo: nombre real del autor en los comentarios de tarea (no "Usuario")

## Objetivo
Los comentarios de tarea muestran siempre el autor como "Usuario" porque el
nombre está hardcodeado. Hay que resolver el nombre real del miembro actual y
usarlo como autor del comentario.

## Contexto (LEER antes de tocar nada)
- El bug está en `ui/screens/TaskDetailScreen.kt` línea ~637:
  `onAddComment("Usuario")` dentro del `IconButton` de enviar comentario, con
  comentario `// Use a default author name since we don't track login state`.
- El modelo `ui/models/TaskScreenModel.kt` tiene `addComment(householdId,
  taskId, authorName)` que llama a `repo.addComment(householdId, taskId,
  authorName, text)`. El `authorName` llega desde la pantalla.
- Para resolver el nombre real:
  - `repo.resolveCurrentMember(householdId): String` → id del miembro actual.
  - `repo.getMembers(householdId): List<MemberResponse>` → miembros con
    `displayName` (y `id`).
- Firestore vía REST (Ktor). i18n `AppStrings.kt` (ES+EN). Solo
  material-icons-core.

## Trabajo
1. Resolver el nombre real del autor en el momento de enviar el comentario.
   Opciones limpias (elige la más coherente con la arquitectura):
   - Preferido: hacerlo DENTRO del modelo. Cambiar `addComment(householdId,
     taskId)` para que resuelva el nombre internamente vía
     `resolveCurrentMember` + `getMembers` (match por `id` → `displayName`),
     en vez de recibir `authorName` como parámetro. Fallback a `"Usuario"` solo
     si no se puede resolver (miembro no encontrado).
   - La pantalla deja de pasar `"Usuario"` y llama sin nombre (o pasa null).
2. Si `displayName` del miembro está vacío, caer a un fallback razonable
   (p.ej. "Miembro" o el `avatarEmoji`/inicial) — no romper.
3. Mantener el comportamiento de recarga de comentarios tras añadir.

## Definición de hecho
- `cd ~/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`
  termina en BUILD SUCCESSFUL.
- Un comentario nuevo guarda el `displayName` real del miembro que lo escribe,
  no "Usuario". Los comentarios de otros miembros ya muestran su nombre (eso ya
  funciona; es solo el autor del mensaje NUEVO lo que estaba hardcodeado).

## Entrega
- Hacer el trabajo y compilar. NO commit, push ni bump (lo hace el orquestador).
- Resumir: archivos tocados y resultado de la compilación.
