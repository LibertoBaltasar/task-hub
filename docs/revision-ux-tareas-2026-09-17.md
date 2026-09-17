# Revisión UX — Simplificar la vista de tareas (2026-09-17)

Auditoría de todo el flujo de tareas (crear → listar → detallar → editar →
completar → subtareas → recurrencia → calendario), a petición de Liberto.
**No se ha tocado código.** Todas las citas de archivo:línea se han
verificado leyendo el fichero real completo (ver "Archivos leídos" al
final). Ninguna función actual se propone eliminar de la app; solo se
reorganiza/simplifica su presentación.

---

## 1. Resumen ejecutivo — los 7 problemas de complejidad más importantes

1. **Las subtareas son invisibles fuera del detalle de la tarea.** El
   checklist embebido (`TaskResponse.subtasks`) no aparece en ningún sitio
   de `TaskListScreen.kt` ni de `HouseholdTaskSection.kt` — de ahí la queja
   "genero subtareas y no hay tareas". Ver sección 3.
2. **`TaskDetailScreen` apila 8 secciones sin plegado** (info, estado,
   quién la hizo, calendario, checklist, asignaciones pendientes,
   asignaciones completadas, comentarios) en un único scroll largo, y mezcla
   dos modelos de "completado" distintos (tarea vs. asignación por miembro).
3. **Los formularios de Crear/Editar tarea (~1200 líneas) muestran casi
   todas las secciones siempre expandidas**, salvo 2 casos con `Switch`
   (Fecha límite, Penalización) — nada de plegado para Checklist, Etiquetas
   o Asignación, aunque sean opcionales para la mayoría de tareas.
4. **El orden de secciones en Crear/Editar interrumpe el flujo natural**: el
   Checklist se cuela entre el campo Título y el campo Descripción.
5. **`TaskCard` en la lista acumula hasta 4 filas de badges** (puntos,
   frecuencia, etiquetas, vencimiento, asignación/rotación) sin jerarquía
   visual entre lo primario y lo secundario.
6. **El filtrado de la lista mezcla tres patrones de interacción distintos**
   en la misma zona: chips de estado, dropdown de etiqueta, y un dropdown de
   orden que solo muestra un emoji (sin texto visible).
7. **Editar tarea tiene una función que Crear tarea no ofrece** (rotación de
   asignación semanal), forzando un flujo oculto de 2 pasos (crear → editar)
   para configurarla.

---

## 2. Inventario por pantalla/flujo

### 2.1 `TaskListScreen.kt` — lista de tareas

| # | Problema (archivo:línea) | Propuesta | Marca | Impacto |
|---|---|---|---|---|
| L1 | `TaskCard` (787-1072) apila 4 filas de información por tarjeta: título+botón (881-934), descripción (937-946), puntos+frecuencia+etiquetas (950-992), vencimiento+asignación (996-1063). Sin jerarquía tipográfica entre "primario" (título, vencimiento) y "secundario" (frecuencia, etiquetas). | Reducir el peso visual de frecuencia/etiquetas (labelSmall ya se usa, pero con el mismo `Surface` que puntos/vencimiento) agrupándolas en una sola fila de "metadatos" con menor contraste que la fila de vencimiento, que es la que de verdad importa para decidir qué hacer hoy. | **[APLICA YA]** | Escaneo más rápido de la lista: menos "ruido" de badges iguales compitiendo por atención. |
| L2 | Ninguna fila de `TaskCard` (787-1072) muestra progreso de subtareas aunque `task.subtasks` no esté vacío — confirmado por grep, `TaskListScreen.kt` no referencia `subtasks` en ningún punto del archivo. | Añadir un badge compacto "☑ 2/5" en la fila de metadatos (950-992) cuando `task.subtasks.isNotEmpty()`. Ver sección 3 (causa raíz de la queja de Liberto). | **[APLICA YA]** | Resuelve directamente "genero subtareas y no hay tareas". |
| L3 | `FilterChipsRow` (1276-1428) combina 3 patrones de interacción en la misma zona: chips de estado siempre visibles (1293-1318), dropdown de etiqueta (1326-1362) y dropdown de orden solo-icono (1375-1425). El botón de orden (1376-1391) solo muestra un emoji ("📅↑", "⭐", "🕐"); el texto "Ordenar por" existe como string (`task_list_sort_label`, ya usado en el `contentDescription` de la línea 1378-1380) pero nunca se pinta en pantalla. | Mostrar el texto del criterio de orden actual junto al emoji (ya existe el string localizado, solo falta pintarlo) en vez de depender solo del glifo. | **[APLICA YA]** | El control de orden deja de depender de que el usuario adivine qué significa cada emoji. |
| L4 | `SearchBar` (1233-1269) se renderiza siempre a tamaño completo, justo debajo de los chips de filtro, incluso cuando la lista tiene pocas tareas o está vacía — compite por atención antes de que el usuario vea ninguna tarea. | Colapsar la barra de búsqueda a un icono que se expande al tocar (patrón estándar "search icon → field"), o mostrarla solo cuando `state.tasks.size` supera un umbral. | **[REQUIERE DECISIÓN]** | Menos elementos por delante de las tareas reales en listas cortas — pero cambia el patrón de interacción de la búsqueda. |
| L5 | 4 grupos posibles (`groupTasksByStatus`, 410-487): Vencidas / Hoy / Completadas hoy / Completadas (otro día). Ya se colapsan por defecto los dos grupos "Completadas" (719-720) — correcto, no se toca. | — (ya está bien resuelto) | — | — |

### 2.2 `TaskDetailScreen.kt` — detalle de tarea

| # | Problema (archivo:línea) | Propuesta | Marca | Impacto |
|---|---|---|---|---|
| D1 | La "mega-card" de info de la tarea (382-514) mezcla 6+ tipos de dato en un único bloque sin subcabeceras: título, descripción, 2 `StatChip` (puntos/frecuencia, 415-425), días de recurrencia (429-448), día del mes (451-458), etiquetas (461-478) y sección de penalización (481-512, esta sí con su propio label). | Dar a "Recurrencia" (días/día del mes) el mismo tratamiento de sub-bloque con label que ya tiene "Penalización" (486-490), en vez de mostrarlo como texto suelto con emoji 🔄. | **[APLICA YA]** | La tarjeta pasa de "bloque de texto" a bloque escaneable con sub-secciones reconocibles. |
| D2 | La pantalla apila 8 secciones sin ningún plegado: info (382-514), estado de completado (518-547), quién la completó (549-595), sincronización de calendario (598-615), checklist de subtareas (618-654), asignaciones pendientes (656-695), asignaciones completadas (697-717), comentarios (719-900). A diferencia de `TaskListScreen`, que sí usa `ExpandableSectionHeader` para sus grupos, aquí no hay ningún control de plegado. | Envolver "Asignaciones completadas" y "Comentarios" (las dos secciones menos usadas en el día a día, ya que las pendientes y el checklist son las accionables) en un header plegable con `ExpandableSectionHeader` (mismo componente que ya usa `TaskListScreen.kt:1199` y `HouseholdTaskSection.kt:71`), colapsadas por defecto. | **[REQUIERE DECISIÓN]** | Reduce el scroll inicial sin perder acceso a nada — pero cambia qué ve el usuario por defecto al abrir el detalle. |
| D3 | El checklist de subtareas (618-654) aparece **después** de la tarjeta de sincronización de calendario (598-615), aunque el checklist es contenido propio de la tarea y el calendario es un estado auxiliar de sincronización. | Mover el bloque de subtareas (618-654) justo después de la tarjeta de info / antes del estado de completado, para que el contenido más "accionable en el día a día" no quede por debajo de un estado de integración externa. | **[APLICA YA]** | Reordenación mecánica, sin decisión de producto — sube visibilidad de las subtareas, ayuda directamente a la queja del punto 1 del resumen. |
| D4 | Dos modelos de "completado" coexisten sin conectar visualmente: el botón "Hecho" de nivel-tarea (517-547, `task.lastCompletedDate`/`task.completedBy`) y las tarjetas de asignación por miembro (656-717, `TaskAssignmentResponse.status`). Un usuario con una tarea compartida ve un botón "Hecho" arriba Y una lista de "Pendientes (N)" más abajo con sus propios botones "Hecho" por persona — dos flujos de completar distintos en la misma pantalla. | Unificar visualmente: si la tarea tiene asignaciones, no mostrar el botón "Hecho" de nivel-tarea como acción independiente arriba — dejar que completar sea siempre "completar mi asignación" (o la única acción visible si no hay asignaciones). | **[REQUIERE DECISIÓN]** | Es el cambio de mayor impacto en "sensación de complejidad" del detalle, pero toca el modelo de interacción de completar, no solo el layout. |
| D5 | Cabecera "Pendientes (%d)" (657-664) se pinta con `%d = 0` seguido inmediatamente de una tarjeta de estado vacío ("Sin asignaciones" / "Todas completadas", 666-682) — dos elementos diciendo lo mismo ("no hay pendientes") de forma redundante cuando `pendingAssignments` está vacío. | Si `pendingAssignments.isEmpty()`, no pintar la cabecera de conteo (657-664) y dejar solo la tarjeta de estado vacío. | **[APLICA YA]** | Menos redundancia visual, un solo mensaje en vez de dos. |
| D6 | El checklist de subtareas en el detalle solo permite marcar/desmarcar (630-653, `onToggleSubtask`) — añadir o quitar un ítem exige salir a "Editar tarea". Asimetría con Crear/Editar, donde sí se puede añadir/quitar en el mismo sitio. | Permitir añadir/quitar subtareas directamente desde el detalle (mismo patrón de input+botón que ya existe en `CreateTaskScreen.kt:416-444`). | **[REQUIERE DECISIÓN]** | Evita el rodeo "Detalle → Editar → Detalle" solo para añadir un ítem a la checklist — pero añade una acción de escritura nueva a una pantalla hoy mayormente de lectura+completar. |

### 2.3 `CreateTaskScreen.kt` / `EditTaskScreen.kt` — crear/editar tarea

Ambas comparten estructura y helpers (`parseDeadline`, `isValidTimeFormat`,
etc., definidos en `CreateTaskScreen.kt:1181-1220`), así que los hallazgos de
orden/densidad aplican a las dos por igual salvo que se indique lo contrario.

| # | Problema (archivo:línea) | Propuesta | Marca | Impacto |
|---|---|---|---|---|
| C1 | Orden de secciones: Título (Create: 389-404, Edit: 349-364) → **Checklist** (Create: 406-478, Edit: 366-438) → Descripción (Create: 480-490, Edit: 440-450) → Puntos (Create: 492-510, Edit: 452-470). El checklist se cuela entre el título y la descripción, rompiendo el orden natural "nombrar → describir → puntuar". | Mover la sección Checklist para que vaya **después** de Puntos (justo antes de "Frecuencia"), dejando Título→Descripción→Puntos como bloque contiguo de "info básica". | **[APLICA YA]** | Reordenación mecánica; el formulario deja de interrumpir el flujo de datos básicos con una función opcional. |
| C2 | Ninguna sección aparte de "Plantillas rápidas" (Create: 1079-1175, con `ExpandableSectionHeader`) usa plegado — Checklist, Etiquetas y Asignación están siempre expandidas en ambas pantallas (Create: 406-478, 664-757, 759-858 / Edit: 366-438, 621-714, 716-892), obligando a scrollear por las tres aunque la tarea sea simple. | Aplicar el mismo patrón `ExpandableSectionHeader` que ya usa "Plantillas rápidas" a Checklist, Etiquetas y Asignación, colapsadas por defecto salvo que ya tengan contenido (p.ej. en Editar, si la tarea ya tiene etiquetas, esa sección arranca expandida). | **[REQUIERE DECISIÓN]** | Reduce fuertemente el scroll para crear una tarea simple — pero cambia el modelo de interacción del formulario (hoy "todo visible", pasaría a "expandir lo que necesitas"). |
| C3 | Etiquetas: dos mecanismos distintos para añadir el mismo dato — campo de texto libre + botón "+" (Create: 674-701, Edit: 631-658) y una fila de chips predefinidos que se activan con un toque (Create: 736-757, Edit: 693-714). Un usuario puede no notar que los chips predefinidos existen bajo el campo de texto, o teclear una etiqueta que ya está ofrecida como chip. | Unificar en una sola interacción: chips predefinidos primero (con un chip final "+ Otra" que revela el campo de texto libre solo si se necesita). | **[REQUIERE DECISIÓN]** | Menos ambigüedad sobre "cómo añado una etiqueta", pero cambia el flujo de esa sección. |
| C4 | `EditTaskScreen` añade "Rotación de asignación" por día de la semana (790-892, dropdown por día) que **no existe en `CreateTaskScreen`** — quien quiere configurar rotación al crear la tarea debe crearla primero (sin rotación) y luego entrar a Editar para añadirla. | Ofrecer el mismo bloque de rotación (790-892) también en `CreateTaskScreen`, reutilizando el mismo componente. | **[REQUIERE DECISIÓN]** | Elimina el paso oculto "crear → editar" para una función de uso frecuente en tareas domésticas rotativas — implica añadir una sección nueva al formulario de creación (superficie mayor), de ahí la decisión. |
| C5 | Validaciones de "Penalización" (`penaltyValue`, `penaltyMax`) y "Puntos" usan `isError`/`supportingText` de forma consistente (Create: 501-509, 986-992, 1054-1058 / Edit: 461-469, 1038-1044, 1104-1108) — ya está bien resuelto, no se toca. | — | — | — |

### 2.4 `HouseholdTaskSection.kt` — resumen de tareas en Home

| # | Problema (archivo:línea) | Propuesta | Marca | Impacto |
|---|---|---|---|---|
| H1 | `TaskRow` (166-220), igual que `TaskCard` de la lista, no muestra progreso de subtareas aunque la tarea las tenga. Consistente con L2 — si se añade el badge de subtareas en `TaskListScreen`, esta vista previa se queda "desactualizada" respecto al mismo dato. | Extender el mismo badge compacto de subtareas (ver L2) a `TaskRow` cuando `task.subtasks.isNotEmpty()`, reutilizando el mismo formato. | **[APLICA YA]** (condicionado a que se apruebe L2/sección 3, mismo criterio mecánico) | Consistencia entre la vista previa de Home y la lista completa. |

---

## 3. Sección dedicada: el problema de las subtareas

### Por qué "generar subtareas no genera tareas visibles"

El modelo de datos es correcto y **no se toca**: `Subtask` (`DTOs.kt:141-145`)
es un elemento embebido dentro de `TaskResponse.subtasks`
(`DTOs.kt:180`) — no es un documento de Firestore propio, no tiene ID de
tarea, no aparece en `TaskRepository` como entidad independiente. Es,
literalmente, un checklist dentro de UNA tarea.

El problema es exclusivamente de **presentación**: los únicos tres sitios
donde un usuario puede ver o tocar subtareas son:
1. `CreateTaskScreen.kt:406-478` — añadirlas al crear.
2. `EditTaskScreen.kt:366-438` — añadirlas/editarlas al editar.
3. `TaskDetailScreen.kt:618-654` — marcarlas/desmarcarlas, **solo si se
   entra al detalle de esa tarea concreta**.

Ni `TaskListScreen.kt` (confirmado por búsqueda en todo el archivo: cero
referencias a `subtasks`) ni `HouseholdTaskSection.kt` (`TaskRow`,
166-220) muestran ningún indicio de que una tarea tenga subtareas o cuántas
lleva completadas. Así, el recorrido real de un usuario es: crea una tarea →
añade 3 subtareas en el formulario → pulsa "Crear" → vuelve a la lista → ve
exactamente la misma tarjeta de tarea que vería sin subtareas, sin ningún
"3 pasos" ni checklist visible. La única forma de comprobar que las
subtareas se guardaron es volver a entrar en el detalle — lo cual, para la
mayoría de usuarios, no es intuitivo ni evidente que haga falta.

### Propuesta de simplificación (sin tocar el modelo de datos)

- **[APLICA YA]** Badge de progreso de subtareas en `TaskCard`
  (`TaskListScreen.kt`, fila de metadatos 950-992) y en `TaskRow`
  (`HouseholdTaskSection.kt`, 166-220): mostrar "☑ 2/5" cuando
  `task.subtasks.isNotEmpty()`. No requiere nueva petición de red — el
  campo `subtasks` ya viaja dentro de `TaskResponse`, que la lista ya
  carga completo.
  - Copy ES: no hace falta string nuevo si se usa el formato numérico
    "%1/%2" (como ya hace `task_detail_checklist_header`,
    `AppStrings.kt:634`) — reutilizable, o si se prefiere una versión
    corta: `task_list_subtask_badge` = `"✔ %1/%2"`.
  - Copy EN: mismo formato, números agnósticos de idioma:
    `task_list_subtask_badge` = `"✔ %1/%2"`.
- **[REQUIERE DECISIÓN]** ¿El badge debe ser solo informativo, o tocarlo
  debe llevar directamente a la sección de checklist dentro del detalle
  (scroll-to-anchor en vez de abrir el detalle desde arriba)? Cambia el
  comportamiento de navegación, no solo la presentación.
- **[REQUIERE DECISIÓN]** ¿Renombrar la etiqueta "Checklist" en
  Crear/Editar/Detalle (`create_task_section_checklist`,
  `task_detail_checklist_header`, `AppStrings.kt:569/634` ES y
  `1201/1266` EN) para dejar explícito que es una lista interna de la
  tarea y no crea tareas nuevas? Ejemplos de copy alternativo:
  - ES: **"Pasos de esta tarea"** / **"Checklist interno"** (mantiene
    "Checklist" pero aclara alcance).
  - EN: **"Steps for this task"** / **"Internal checklist"**.
  Es una decisión de producto/copy porque cambia cómo se comunica el
  concepto al usuario, no un ajuste mecánico de layout.

Con el badge de progreso aplicado (L2 en la tabla 2.1), la conexión visual
"esta tarea de la lista tiene subtareas, y aquí está su avance" queda
resuelta sin mover ni un byte del modelo de datos ni cambiar el
comportamiento de recurrencia, puntos o asignación.

---

## 4. Quick wins [APLICA YA] — priorizados (impacto vs. esfuerzo)

1. **L2 / H1** — Badge de progreso de subtareas en `TaskCard` y `TaskRow`.
   *Impacto: alto (resuelve la queja original). Esfuerzo: bajo.*
2. **D3** — Mover el checklist de subtareas antes de la tarjeta de
   calendario en `TaskDetailScreen`. *Impacto: alto (refuerza #1).
   Esfuerzo: mínimo (reordenar bloques `item {}`).*
3. **C1** — Mover la sección Checklist después de Puntos en
   Crear/Editar tarea. *Impacto: medio (orden más natural).
   Esfuerzo: mínimo.*
4. **L3** — Mostrar el texto del criterio de orden junto al emoji en el
   botón de orden de la lista (el string ya existe). *Impacto: medio
   (accesibilidad/claridad). Esfuerzo: mínimo.*
5. **D5** — Quitar la cabecera "Pendientes (0)" redundante cuando no hay
   asignaciones pendientes. *Impacto: bajo-medio. Esfuerzo: mínimo.*
6. **D1** — Dar a "Recurrencia" el mismo tratamiento de sub-bloque con
   label que ya tiene "Penalización" en la tarjeta de info del detalle.
   *Impacto: bajo-medio (escaneabilidad). Esfuerzo: bajo.*
7. **L1** — Reducir el peso visual de frecuencia/etiquetas en `TaskCard`
   frente a la fila de vencimiento. *Impacto: bajo (pulido visual).
   Esfuerzo: bajo.*

---

## 5. Decisiones que necesita Liberto [REQUIERE DECISIÓN]

1. **Unificar "Hecho" con las asignaciones (D4).** Cuando una tarea tiene
   asignaciones a varios miembros, ¿el botón "Hecho" de nivel-tarea (arriba
   del detalle) debe desaparecer y dejar que completar sea siempre "completar
   mi asignación" (la tarjeta de abajo), o prefieres mantener los dos
   caminos de completar como hoy?
2. **Plegar secciones poco usadas del detalle (D2).** ¿Apruebas que
   "Asignaciones completadas" y "Comentarios" en `TaskDetailScreen`
   arranquen colapsadas por defecto (con el mismo componente plegable que
   ya usa la lista de tareas), o prefieres que todo siga visible de
   entrada?
3. **Progressive disclosure en Crear/Editar (C2).** ¿Quieres que
   "Checklist", "Etiquetas" y "Asignación" pasen a ser secciones plegables
   (como ya es "Plantillas rápidas"), colapsadas por defecto salvo que ya
   tengan contenido?
4. **Unificar las dos formas de añadir etiquetas (C3).** ¿Prefieres que
   la fila de chips predefinidos sea el único camino principal, con un
   chip final "+ Otra" que revele el campo de texto libre, en vez de
   mostrar ambos mecanismos siempre visibles?
5. **Rotación de asignación también en Crear (C4).** ¿Quieres poder
   configurar la rotación semanal de asignados directamente al crear la
   tarea (en vez de tener que crear primero y editar después)?
6. **Enlace directo desde el badge de subtareas (sección 3).** Cuando se
   añada el badge "☑ 2/5" en la lista, ¿tocarlo debe llevar directamente
   a la sección de checklist dentro del detalle (scroll automático), o
   basta con abrir el detalle normal desde arriba?
7. **Renombrar "Checklist" (sección 3).** ¿Mantienes la etiqueta actual
   ("Checklist") o prefieres un copy que aclare que es interno a la tarea
   (p.ej. "Pasos de esta tarea" / "Checklist interno")?

---

## Archivos leídos (completos, para esta auditoría)

- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt` (1458 líneas)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskDetailScreen.kt` (1228 líneas)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/CreateTaskScreen.kt` (1220 líneas)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/EditTaskScreen.kt` (1118 líneas)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdTaskSection.kt` (236 líneas)
- `composeApp/src/commonMain/kotlin/org/taskhub/network/models/DTOs.kt` (secciones `AssignmentSlot`/`Subtask`/`TaskResponse`, líneas 120-210)
- `composeApp/src/commonMain/kotlin/org/taskhub/ui/i18n/AppStrings.kt` (grep dirigido a claves `task_list_*`, `task_detail_checklist_*`, `create_task_section_checklist` en ambos bloques ES/EN, para verificar copy existente citado en este informe)

No se ha ejecutado build (no se ha tocado código, según alcance de esta
tarea).
