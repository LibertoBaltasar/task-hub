---
workdir: /home/liberto/task-hub
max_turns: 300
allowed_tools: Read,Edit,Write,Bash,Grep,Glob,Task
---

# Encargo: PROPUESTAS del panel v4 — Recurrencia / integridad de datos

El panel de expertos v4 (`docs/review-panel-expertos-v4.md`) dejó 5 PROPUESTAs
sobre recurrencia e integridad de datos. El usuario ha ACEPTADO todas.
Decisiones de producto YA TOMADAS — no inventes otras opciones:

1. **Asignación fantasma al eliminar miembro** (Exp. 2 #2). Al eliminar un
   miembro (soft-delete en `deleteMember`), purga sus referencias en
   `assignmentRotation` de las tareas del hogar y en las asignaciones
   "assigned" existentes, para que la siguiente regeneración no cree una
   asignación a nombre de alguien invisible en `getMembers`.

2. **completer≠asignado** (Exp. 8 #3). "Cualquier miembro puede completar
   cualquier tarea". Al completar una tarea, marca como completadas TODAS las
   asignaciones "assigned" del ciclo actual (completar la tarea descarga el
   ciclo para todos los asignados), no solo la del miembro que completa.

3. **Editar recurrente asignada resetea dueDate a 0** (Exp. 8 #4). Pasa
   `nextDueAt` recién calculado a `replaceAssignments` en vez de `0` para no
   desactivar silenciosamente la penalización por retraso de ese ciclo.

4. **Hogar compartido sin owner al eliminar cuenta** (Exp. 2 #6). En
   `deleteAccount`/`leaveHousehold`, para cada hogar compartido donde el
   usuario que se borra es el único admin/owner y quedan otros miembros:
   transfiere el rol owner al miembro más antiguo restante. Si no quedan
   miembros, borra el hogar.

5. **Sync de Calendar inmediato** (Exp. 2 #3). Tras regenerar la asignación,
   dispara sync inmediato con Google Calendar (o marca para reconcile
   inmediato), sin esperar a reabrir HouseholdScreen/PersonalSpaceScreen.

## Contexto técnico
- Stack: Compose Multiplatform, Ktor (Firestore REST, NO SDK), Koin, Voyager.
- `network/FirestoreRepository.kt`, `network/MemberRepository.kt`,
  `ui/models/TaskScreenModel.kt`, `network/GoogleCalendarRepository.kt`.
- Recurrencia: `RecurrenceRules` (puro), `nextDueAt`, `assignmentRotation`,
  `resolveRotationAssignee`.

## Verificación OBLIGATORIA
```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y tests en verde. Añade tests para la lógica
nueva (recurrencia pura).

## Convenciones
- Comentarios/KDoc en español. NO hagas commit, push ni bump.
- i18n en `ui/i18n/AppStrings.kt` (ES+EN), sin texto hardcodeado.
- Solo material-icons-core.

## Entrega (resumen final obligatorio)
1. Propuestas aplicadas y cómo.
2. Archivos tocados.
3. Resultado de build/tests.
4. Cualquier cosa que no pudieras aplicar y por qué.
