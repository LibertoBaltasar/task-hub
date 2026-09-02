---
workdir: /home/liberto/task-hub
max_turns: 400
allowed_tools: Read,Edit,Write,Bash,Grep,Glob,Task
---

# Encargo: PROPUESTAS del panel v4 — Arquitectura / rendimiento / UX

El panel de expertos v4 (`docs/review-panel-expertos-v4.md`) dejó 8 PROPUESTAs
de arquitectura/rendimiento/UX. El usuario ha ACEPTADO todas:

1. **rememberHouseholdName compartido** (Exp. 1 #1 + Exp. 4 #1). Extrae un
   helper compartido para obtener el nombre del hogar en el topbar (cacheado
   en `HouseholdStore`/`SavedHousehold.name`), y úsalo en las 7+2 pantallas que
   hoy duplican 7×5 líneas: CreateTaskScreen, EditTaskScreen, TaskDetailScreen,
   ExploreScreen, NotificationListScreen, CreateRewardScreen, MemberRewardScreen
   (y las 2 que ya lo hacían). Resuelve el parpadeo del subtítulo y la
   duplicación mecánica.

2. **Paginar getTaskHistory** (Exp. 7 #1). Aplica el mismo patrón
   `listAllDocuments` (con tope de páginas) que ya usan getTasks/getAssignments/
   messages.

3. **Paralelizar deleteHousehold/deleteAllDocuments** (Exp. 7 #2 + Exp. 11 #4).
   Borra las subcolecciones en paralelo (`coroutineScope`/`async`/`awaitAll`),
   como ya hace `HouseholdRepository.reconcileHouseholds`.

4. **Generalizar DestructiveConfirmDialog** (Exp. 4 #2). Permite usarlo para
   acciones no destructivas (sin forzar color de error), y úsalo en el diálogo
   de cambio de rol que hoy reimplementa un AlertDialog a mano.

5. **Extraer DeleteAccountSection** (Exp. 4 #5). Saca el flujo de "eliminar
   cuenta" de `SettingsSheet` a su propio componente.

6. **SecureStore perezoso en SettingsStore** (Exp. 11 #6). Usa `by lazy` para
   no construir SecureStore síncronamente en la primera composición de `App()`
   solo para leer el idioma guardado.

7. **FilterChip con leadingIcon de check** (Exp. 3 #2). Añade un leadingIcon de
   check a los FilterChip seleccionados (frecuencia, días, penalización) en los
   4 sitios de CreateTaskScreen/EditTaskScreen, para no depender solo del color
   (WCAG 1.4.1).

8. **Separar remember de isDueToday del de searchQuery** (Exp. 11 #2). En
   `TaskListContent`, separa el cálculo por-tarea de `isDueToday`/
   `isCompletedToday` (independiente de `searchQuery`) del filtro, para que
   teclear en el buscador no recalcule todas las tareas.

## Contexto técnico
- `ui/components/`, `ui/screens/`, `network/FirestoreRepository.kt`,
  `network/MemberRepository.kt`, `storage/SettingsStore.kt`,
  `ui/models/` (ScreenModels).

## Verificación OBLIGATORIA
```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y tests en verde.

## Convenciones
- Comentarios/KDoc en español. NO hagas commit, push ni bump. i18n ES+EN.
- Solo material-icons-core; iconos espejados → `Icons.AutoMirrored.Filled.*`.

## Entrega (resumen final obligatorio)
1. Propuestas aplicadas y cómo.
2. Archivos tocados.
3. Resultado de build/tests.
4. Lo que no pudieras aplicar y por qué.
