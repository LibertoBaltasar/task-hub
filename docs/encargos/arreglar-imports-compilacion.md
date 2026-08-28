---
workdir: /home/liberto/task-hub
max_turns: 40
allowed_tools: Read,Edit,Write,Bash
---
# Encargo: arreglar 2 errores de import que bloquean la compilación

## Contexto
Hay una tanda de cambios SIN commitear en el working tree (pulido de UI: colores
semánticos, empty states, shimmer, logo, accesibilidad reduce-motion, refactor de
HouseholdScreen en componentes, carga paralela de tareas en HomeScreenModel).
La compilación `:composeApp:compileDebugKotlinAndroid` falla por DOS errores de
import. Arréglalos y verifica que compila. **NO toques nada más.**

## Error 1 — `TaskListScreen.kt`: import equivocado de `graphicsLayer`
- Archivo: `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/TaskListScreen.kt`
- Línea 26: `import androidx.compose.ui.draw.graphicsLayer`  ← **MAL**
- Debe ser: `import androidx.compose.ui.graphics.graphicsLayer`  ← correcto
- Causa `Unresolved reference` en `graphicsLayer`, `scaleX`, `scaleY`, `alpha`
  (líneas ~706-709 y ~928, bloques `Modifier.graphicsLayer { scaleX = ... }`).

## Error 2 — `ShimmerPlaceholder.kt`: falta import de `getValue`
- Archivo: `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/ShimmerPlaceholder.kt`
- En `rememberShimmerBrush()` hay `val translateAnim by transition.animateFloat(...)`.
  El delegado `by` sobre un `State` necesita el operador `getValue`:
  añadir `import androidx.compose.runtime.getValue` junto a
  `import androidx.compose.runtime.Composable` (mismo patrón que usan ya
  `HouseholdMemberList.kt` y `HouseholdDialogs.kt`).

## Definición de hecho
- `cd ~/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`
  termina en **BUILD SUCCESSFUL**.

## Entrega
- Hacer SOLO estos dos arreglos y compilar. **NO commit, NO push, NO bump**
  (eso lo hace el orquestador). NO tocar ningún otro archivo.
- Resumir: archivos tocados y resultado de la compilación.
