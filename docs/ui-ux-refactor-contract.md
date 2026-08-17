# Contrato de refactor UI/UX — Task Hub

Refactor de accesibilidad y consistencia. Aplicar SIN cambiar lógica de negocio. Este documento es la autoridad: léelo entero antes de editar.

## Componentes compartidos (YA existen, NO los modifiques)

### `TaskHubTopBar` — package `org.taskhub.ui.components`
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHubTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
)
```
- Sustituye el patrón manual `Surface(color = Teal600, shadowElevation = 4.dp) { Row { TextButton("← …") … Spacer(weight(1f)) Text(título) Spacer(weight(1f)) … } }`.
- El título va **SIN emoji** ("Tareas", no "📋 Tareas").
- El botón "← Volver / ← Cancelar / ← Tareas" desaparece: lo gestiona el componente con `Icons.AutoMirrored.Filled.ArrowBack` + contentDescription "Volver".
- Las acciones de la derecha van en el slot `actions`. Iconos → `IconButton { Icon(..., contentDescription=…) }`. Acciones con texto (p. ej. "Crear", "+ Nueva", "Guardar") → `TextButton { Text(…) }` con `ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)`.

```kotlin
TaskHubTopBar(
    title = "Tareas",
    onBack = { navigator.pop() },
    actions = {
        IconButton(onClick = { model.loadTasks(householdId) }) {
            Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
        }
        TextButton(onClick = { navigator.push(CreateTaskScreen(...)) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
            Text("+ Nueva", fontWeight = FontWeight.Bold)
        }
    }
)
```

### `PointsBadge` + `BadgeTone` — package `org.taskhub.ui.components`
```kotlin
enum class BadgeTone { Coral, Teal, Neutral }
@Composable
fun PointsBadge(text: String, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Coral)
```
- Reemplaza badges de puntos/urgencia/coste/penalización construidos como `Surface(color = Coral500) { Text(…, color = …onTertiary) }`.
- Coral (default) = puntos/urgencia/coste. Teal = frecuencia/neutro. Neutral = genérico.

## Reglas de color (contraste WCAG AA)
| Antes (falla AA) | Después |
|---|---|
| `containerColor = Teal600` (botones/FAB) | `MaterialTheme.colorScheme.primary` |
| `containerColor = Teal500` | `MaterialTheme.colorScheme.primary` |
| `containerColor = Coral500` (botones) | `MaterialTheme.colorScheme.tertiary` |
| badge `Surface(Coral500)` + texto claro | `PointsBadge(text)` |
| badge `Surface(Teal500)` + texto claro | `PointsBadge(text, tone = BadgeTone.Coral)` |
| texto `color = Teal500` sobre claro | `org.taskhub.ui.theme.Teal800` |
| texto `color = Teal600` sobre claro | `Teal800` |
| texto `color = Teal700` (encabezados/secciones) | `Teal800` |
| top bar manual `Surface(Teal600)` | `TaskHubTopBar` |

- `MaterialTheme.colorScheme.primary` YA es `Teal800` en tema claro (accesible). `tertiary` ya es `Coral700`.
- Mantén intactos los fondos claros `Teal50 / Teal100 / Coral50 / Coral100` (son correctos como contenedores de texto oscuro).

## Iconos: SOLO set CORE (el proyecto NO incluye material-icons-extended)
Import: `androidx.compose.material.icons.filled.*` (uso `Icons.Default.*`), salvo volver que es `androidx.compose.material.icons.automirrored.filled.ArrowBack` (uso `Icons.AutoMirrored.Filled.ArrowBack`).

Mapeo emoji → icono (todo con `contentDescription` descriptivo):
- ⚙️ → `Settings` ("Ajustes")
- 🗑️ → `Delete` ("Eliminar")
- ✏️ → `Edit` ("Editar")
- 📤 → `Send` ("Enviar comentario")
- 🔄 → `Refresh` ("Actualizar")
- 🔔 → `Notifications` ("Notificaciones")
- 🔍 → `Search` ("Buscar")
- Crear hogar → `Add`
- Unirse a hogar → `AddCircle`
- Cerrar menú FAB → `Close`
- ⭐ → `Star`

**PROHIBIDO** usar iconos fuera del set core (NO existen y rompen el build): `GroupAdd`, `PersonAdd`, `AdminPanelSettings`, `ChildCare`, `EmojiEvents`, `Flag`, `QrCode`, etc.

## Touch targets ≥48dp
- Elimina `Modifier.size(36.dp)` / `size(40.dp)` en controles interactivos.
- Usa `Modifier.minimumInteractiveComponentSize()` (import `androidx.compose.foundation.layout.minimumInteractiveComponentSize`) o quita el `size` para dejar el tamaño por defecto. `SmallFloatingActionButton` ya cumple.

## contentDescription
- Todo `Icon` interactivo debe llevar `contentDescription` descriptivo (nunca `null`). Iconos decorativos sí pueden ser `null`.

## Roles de miembro (👑 / 🧒)
- El rol NO puede depender solo del emoji. Añade SIEMPRE el texto "Admin" o "Niño/a" junto al emoji (o sustituye el emoji por el texto).

## Reglas generales
1. **NO ejecutes gradle / NO compiles.** El coordinador compila al final. Revisa tus cambios releyendo los archivos.
2. NO toques archivos fuera de tu grupo. NO cambies lógica/flujos, solo presentación.
3. Preserva todos los `onClick` existentes.
4. Comenta cualquier componente nuevo con KDoc breve en español.
5. Si un archivo ya usa `TopAppBar` de Material correctamente (HomeScreen, ProfileScreen), no lo rompas; solo arregla lo señalado.
6. `ExperimentalMaterial3Api`: las pantallas que migren a `TaskHubTopBar` pueden necesitar quitar el `@OptIn` si ya no usan APIs experimentales, o mantenerlo si aún usan otras (ej. FilterChip, DatePicker). No elimines un `@OptIn` si el archivo sigue usando APIs experimentales; si lo eliminas y sobra, quítalo. Ante la duda, déjalo.
