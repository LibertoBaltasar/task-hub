---
workdir: /home/liberto/task-hub
max_turns: 250
allowed_tools: Read,Edit,Write,Bash,Grep,Glob
---

# Encargo: UX / consistencia (decisión EXPLÍCITA del usuario)

## Contexto
El panel v3 (`docs/review-panel-expertos-v3-2026-09-01.md`) dejó dos hallazgos de
UX/consistencia como PROPUESTA. El usuario ha decidido aplicarlos. Versión actual
0.7.25 (HEAD `c8d11e1`). Lee el informe antes de empezar (experto 1 Estética y
experto 5 UX).

## Cambios a aplicar (decisión del usuario — no pedir confirmación)

### 1. Nombre del hogar en la topbar de TODAS las pantallas anidadas (Estética #1)
- Hoy el nombre del hogar aparece en la topbar solo en 3 de ~9 pantallas anidadas
  (`HouseholdScreen`, `TaskListScreen`, `CalendarScreen`). Faltan:
  `CreateTaskScreen`, `EditTaskScreen`, `TaskDetailScreen`, `ExploreScreen`,
  `NotificationListScreen`, `CreateRewardScreen`, `MemberRewardScreen`.
- Propaga el nombre del hogar a esas pantallas (inyectando `HouseholdScreenModel`
  o el mecanismo que ya usan `TaskListScreen`/`CalendarScreen` — `loadHousehold`
  + `subtitle`), para que todas las pantallas hermanas sean consistentes.

### 2. `MemberScreenModel.removeMember` sin botón en la UI (UX #7)
- `removeMember` existe en `MemberScreenModel` pero ninguna pantalla lo invoca:
  código muerto o feature incompleta.
- Decisión del usuario: **conectarlo** — añade el botón/acción "eliminar miembro"
  en `HouseholdMemberList` (con confirmación, reutilizando el patrón de diálogo ya
  existente en el mismo archivo), coherente con la acción de borrar/salir del hogar.

## Criterios
- APLICA YA: decisión explícita del usuario.
- i18n ES+EN sin texto hardcodeado. Solo `material-icons-core`.
- No toques diseño salvo consistencia.

## Verificación (OBLIGATORIO)
```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y tests en verde.

## Convenciones
- Comentarios/KDoc en español. NO hagas commit, push ni bump.

## Entrega (resumen final obligatorio)
1. Por cada punto: qué hiciste, con `archivo:línea`.
2. Resultado de compilación y tests.
