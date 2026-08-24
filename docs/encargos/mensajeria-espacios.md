---
workdir: /home/liberto/task-hub
---
# Encargo: mensajería por espacio (chat entre miembros del hogar)

## Objetivo
Añadir un chat sencillo dentro de cada espacio (hogar) para que los miembros
puedan comunicarse entre sí. Sin tiempo real ni push: carga al entrar +
refresco manual y/o periódico ligero.

## Contexto (LEER antes de tocar nada)
- Stack: Compose Multiplatform (Kotlin 2.1, CMP 1.7.3) + Firestore vía **REST
  con Ktor** (NO Firestore SDK). Patrón de escritura: `FirestoreDocument` /
  `FirestoreValue`, `withAuth()`, `client.post("$baseUrl/...")`; listar con
  `client.get(...)` → `FirestoreListResponse.documents`; `extractDocId(doc.name)`.
- Modelos `@Serializable` en `network/models/DTOs.kt` con campos por defecto.
- Repo: `network/FirestoreRepository.kt`. **`addComment`/`getComments` (líneas
  ~1702-1745) son la plantilla EXACTA del patrón subcolección** (ya escriben y
  listan bajo `households/{id}/tasks/{taskId}/comments`). Réplicalo para mensajes.
- i18n: `ui/i18n/AppStrings.kt` (ES + EN), `AppStrings.get(key, lang)`. TODO
  texto visible nuevo pasa por ahí.
- Solo `material-icons-core` (NO extended). Iconos espejados →
  `Icons.AutoMirrored.Filled.*`.
- Navegación Voyager, DI Koin (`di/AppModule.kt`).
- Pantalla del espacio: `ui/screens/HouseholdScreen.kt` + su modelo
  `ui/models/HouseholdScreenModel.kt`. El miembro actual se resuelve con
  `repo.resolveCurrentMember(householdId)`.

## Trabajo
1. **DTO** en `network/models/DTOs.kt`: añadir
   `MessageResponse(id, memberId, authorName, text, createdAt)`.
2. **Repo** en `FirestoreRepository.kt`, subcolección
   `households/{householdId}/messages`:
   - `sendMessage(householdId, memberId, authorName, text): MessageResponse` (POST).
   - `getMessages(householdId): List<MessageResponse>` (GET), ordenar por
     `createdAt` ascendente.
3. **Modelo** en `ui/models/HouseholdScreenModel.kt`: estado `MessagesUiState`
   (Idle / Loading / Success(list) / Error) + `loadMessages(householdId)`,
   `sendMessage(householdId, memberId, text)`, `newMessageText`.
   - El `authorName` debe ser el nombre real del miembro actual (resolverlo;
     NO hardcodear "Usuario").
4. **UI** en `HouseholdScreen.kt`: sección "💬 Mensajes" con:
   - Lista de mensajes (nombre del autor + hora + texto), scroll al final.
   - Campo de entrada + botón enviar.
   - Carga al entrar + botón de refresco + refresco automático ~cada 20s
     mientras la pantalla esté visible (LaunchedEffect + delay).
5. **i18n**: claves nuevas en ES + EN (mensajes, enviar, escribir mensaje,
   sin mensajes todavía, error al cargar/enviar mensaje, etc.).

## Definición de hecho
- `cd ~/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`
  termina en BUILD SUCCESSFUL.
- Un miembro puede escribir un mensaje y verlo (y ver los de otros) en su espacio.
- No se rompe tasks/comments/points.

## Entrega
- Hacer el trabajo y compilar. NO commit, push ni bump (lo hace el orquestador).
- Resumir: archivos tocados y resultado de la compilación.
