---
workdir: /home/liberto/task-hub
max_turns: 350
allowed_tools: Read,Edit,Write,Bash,Grep,Glob
---

# Encargo: arquitectura + rendimiento (decisión EXPLÍCITA del usuario)

## Contexto
El panel v3 (`docs/review-panel-expertos-v3-2026-09-01.md`) dejó varios refactors
estructurales como PROPUESTA. El usuario ha decidido aplicarlos. Versión actual
0.7.25 (HEAD `c8d11e1`). Lee el informe antes de empezar (experto 7 Arquitectura y
experto 11 Rendimiento).

## Cambios a aplicar (decisión del usuario — no pedir confirmación)

### 1. Extraer `MemberRepository` (hallazgo Arquitectura #1)
- El bloque Members/Points (~555 líneas) sigue íntegro en
  `network/FirestoreRepository.kt`. Es el chunk sin dividir más grande.
- Extrae `MemberRepository` siguiendo el mismo patrón que ya se usó para
  `TaskRepository`/`HouseholdRepository`/`RewardsRepository`/`NotificationRepository`
  (repos por dominio como composición interna de la fachada, NO en Koin), e
  inyecta `getLocalId` como lambda si es necesario para evitar ciclos.
- El split `f2521d9` fue un buen precedente: firmas idénticas, mismo `taskCache`,
  `catch (CancellationException) { throw e }` conservados.

### 2. Paginación en colecciones Firestore (hallazgo Escalabilidad)
- Sin paginación en ninguna colección. Implementa paginación con `pageSize` y
  cursor/pageToken donde más impacto tenga (`getTasks`, `getAssignments`,
  `messages`), manteniendo compatibilidad hacia atrás (los callers existentes no
  deben romperse). Sé pragmático: hogares pequeños hoy, pero preparado para crecer.

### 3. Memoizar `TaskListContent` (hallazgo Rendimiento #4)
- `TaskListContent` (`TaskListScreen.kt:479-541`) recalcula filtrado/agrupado/
  `RecurrenceRules.isDueToday` (2× por tarea) sin `remember`; `TaskCard` recibe
  `List`/`Map` inestables recreados cada vez.
- Aplica `remember`/`derivedStateOf` y estabiliza los parámetros. OJO: es un
  composable central de la pantalla más usada tras Home. Haz el cambio con
  cuidado, manteniendo comportamiento idéntico, y compila (el orquestador hará QA
  visual en dispositivo después).

## Criterios
- APLICA YA: decisión explícita del usuario.
- No rompas la API pública de componentes usados en varias pantallas sin revisar
  todos los call-sites.
- Comentarios/KDoc en español. NO hagas commit, push ni bump.

## Verificación (OBLIGATORIO)
```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y tests en verde.

## Entrega (resumen final obligatorio)
1. Por cada punto: qué hiciste, con `archivo:línea`.
2. Resultado de compilación y tests.
3. Cualquier riesgo de regresión detectado (para que el orquestador haga QA visual
   dirigido).
