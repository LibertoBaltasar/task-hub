# Reorganización de navegación — Espacio Personal + Dashboard unificado

> **Para Hermes:** Implementar tarea por tarea, commiteando al final de cada una.

**Objetivo:** Reorganizar la navegación para que la pantalla principal sea un dashboard unificado con tareas de todos los hogares (incluyendo un espacio "Personal" auto-creado), en lugar de la selección de hogares actual.

**Arquitectura:** El espacio "Personal" es un hogar especial (`isPersonal = true`) creado automáticamente en Firestore la primera vez que un usuario abre la app. Es privado (sin invitaciones). El nuevo `HomeScreen` consulta tareas de TODOS los hogares del usuario y las muestra agrupadas por hogar.

**Stack:** Kotlin + Compose Multiplatform + Voyager + Firestore REST + Koin

---

## Cambios principales

### 1. Modelo de datos: flag `isPersonal` en Household
- `HouseholdResponse` gana campo `isPersonal: Boolean` (default `false`)
- `HouseholdStore` gana método `getPersonalHouseholdId(): String?`
- `SavedHousehold` gana campo `isPersonal`

### 2. Auto-creación del espacio Personal
- Al abrir la app por primera vez (sin `personalHouseholdId` guardado), crear hogar "Personal" en Firestore con `isPersonal = true`
- Guardar su ID localmente para no volver a crearlo
- Este hogar NO muestra botón de invitar, no tiene `inviteCode` funcional

### 3. Nueva pantalla `HomeScreen`
- Reemplaza `WelcomeScreen` y `HouseholdListScreen` como landing page
- Muestra tareas agrupadas por hogar en secciones colapsables:
  - Sección "Personal" (siempre arriba)
  - Una sección por cada hogar compartido
- Cada sección muestra las primeras 3-5 tareas pendientes + "Ver todas"
- FAB con opciones: "Crear hogar" / "Unirse a hogar"
- Top bar con: título "Task Hub", botón de perfil/ajustes

### 4. Cambios en navegación
- `App.kt`: `initialScreen` → siempre `HomeScreen()`
- `WelcomeScreen`: se convierte en pantalla modal/vista desde Home
- `HouseholdListScreen`: se mantiene pero como vista secundaria desde perfil

---

## Tareas

### Tarea 1: Añadir `isPersonal` al modelo de datos

**Archivos:**
- Modificar: `composeApp/src/commonMain/kotlin/org/taskhub/storage/HouseholdStore.kt`
- Modificar: `composeApp/src/commonMain/kotlin/org/taskhub/network/models/DTOs.kt`
- Modificar: `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreDtos.kt`

**Paso 1:** Añadir `isPersonal: Boolean = false` a `SavedHousehold`

```kotlin
@Serializable
data class SavedHousehold(
    val id: String,
    val name: String,
    val inviteCode: String,
    val isPersonal: Boolean = false
)
```

**Paso 2:** Añadir método `getPersonalHouseholdId()` y `savePersonalHousehold()` a `HouseholdStore`

```kotlin
fun getPersonalHouseholdId(): String? {
    return settings.getString(KEY_PERSONAL_HOUSEHOLD_ID, "").ifEmpty { null }
}

fun savePersonalHousehold(id: String, name: String) {
    settings.putString(KEY_PERSONAL_HOUSEHOLD_ID, id)
    saveHousehold(id, name, "", isPersonal = true)
}
```

Actualizar `saveHousehold` para aceptar `isPersonal`:

```kotlin
fun saveHousehold(
    householdId: String, 
    householdName: String, 
    inviteCode: String, 
    isPersonal: Boolean = false
) {
    val current = getSavedHouseholds().toMutableList()
    val existing = current.indexOfFirst { it.id == householdId }
    val entry = SavedHousehold(id = householdId, name = householdName, inviteCode = inviteCode, isPersonal = isPersonal)
    if (existing >= 0) current[existing] = entry
    else current.add(entry)
    settings.putString(KEY_SAVED_HOUSEHOLDS, json.encodeToString(current))
}
```

Añadir constante:

```kotlin
companion object {
    private const val KEY_SAVED_HOUSEHOLDS = "taskhub_saved_households"
    private const val KEY_PERSONAL_HOUSEHOLD_ID = "taskhub_personal_household_id"
}
```

**Paso 3:** Añadir `isPersonal` al DTO de Firestore (si existe) y al `HouseholdResponse`

Buscar `HouseholdResponse` en DTOs y añadir `val isPersonal: Boolean = false`.

**Verificación:** `./gradlew :composeApp:compileDebugKotlinAndroid` compila sin errores.

---

### Tarea 2: Auto-crear espacio Personal en App.kt

**Archivos:**
- Modificar: `composeApp/src/commonMain/kotlin/org/taskhub/App.kt`
- Modificar: `composeApp/src/commonMain/kotlin/org/taskhub/network/FirestoreRepository.kt` (si necesita endpoint de creación)

**Paso 1:** En `App.kt`, dentro del `LaunchedEffect` que actualmente resuelve `initialScreen`, añadir lógica para crear el espacio Personal si no existe:

```kotlin
LaunchedEffect(Unit) {
    // 1. Ensure personal space exists
    val personalId = householdStore.getPersonalHouseholdId()
    if (personalId == null) {
        try {
            val newHousehold = repo.createHousehold("Personal", "PERSONAL")
            householdStore.savePersonalHousehold(newHousehold.id, "Personal")
        } catch (_: Exception) {
            // Silently fail — user can still use shared households
        }
    }
    
    // 2. Resolve initial screen
    val savedHouseholds = householdStore.getSavedHouseholds()
    initialScreen = HomeScreen()
}
```

**Paso 2:** Verificar que `FirestoreRepository.createHousehold` soporta un flag `isPersonal`. Si no, añadir un parámetro con default `false`.

**Verificación:** `./gradlew :composeApp:compileDebugKotlinAndroid` compila.

---

### Tarea 3: Crear HomeScreen base

**Archivos:**
- Crear: `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/HomeScreen.kt`

**Paso 1:** Crear estructura base con TopAppBar + secciones por hogar:

```kotlin
class HomeScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repo = koinInject<FirestoreRepository>()
        val householdStore = koinInject<HouseholdStore>()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
        
        var households by remember { mutableStateOf<List<SavedHousehold>>(emptyList()) }
        var showSettings by remember { mutableStateOf(false) }
        
        LaunchedEffect(Unit) {
            households = householdStore.getSavedHouseholds()
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Task Hub", fontWeight = FontWeight.Bold) },
                    actions = {
                        // Botón perfil/ajustes
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Ajustes")
                        }
                    }
                )
            },
            floatingActionButton = {
                // FAB para crear/unirse a hogar
                var showFabMenu by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.End) {
                    if (showFabMenu) {
                        // Crear hogar
                        SmallFloatingActionButton(onClick = {
                            navigator.push(CreateHouseholdScreen())
                            showFabMenu = false
                        }) {
                            Icon(Icons.Default.Add, "Crear hogar")
                        }
                        Spacer(Modifier.height(8.dp))
                        // Unirse a hogar
                        SmallFloatingActionButton(onClick = {
                            navigator.push(JoinHouseholdScreen())
                            showFabMenu = false
                        }) {
                            Icon(Icons.Default.GroupAdd, "Unirse")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                        Icon(Icons.Default.Add, "Nuevo")
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sección Personal (siempre arriba)
                val personal = households.find { it.isPersonal }
                if (personal != null) {
                    item { HouseholdTaskSection(personal, repo) }
                }
                
                // Hogares compartidos
                val shared = households.filter { !it.isPersonal }
                items(shared) { household ->
                    HouseholdTaskSection(household, repo)
                }
                
                // Si no hay hogares compartidos, mostrar CTA
                if (shared.isEmpty() && personal != null) {
                    item { EmptySharedHouseholdsCta(navigator) }
                }
            }
        }
        
        // Settings dialog
        if (showSettings) {
            Dialog(...) { SettingsSheet(...) }
        }
    }
}
```

**Verificación:** `./gradlew :composeApp:compileDebugKotlinAndroid` compila (aunque `HouseholdTaskSection` no existe aún).

---

### Tarea 4: Crear componente HouseholdTaskSection

**Archivos:**
- Crear: `composeApp/src/commonMain/kotlin/org/taskhub/ui/components/HouseholdTaskSection.kt`

**Paso 1:** Componente colapsable que muestra tareas pendientes de un hogar:

```kotlin
@Composable
fun HouseholdTaskSection(
    household: SavedHousehold,
    repo: FirestoreRepository,
    onViewAll: (String) -> Unit = {}  // callback para navegar al hogar
) {
    var tasks by remember { mutableStateOf<List<TaskResponse>>(emptyList()) }
    var expanded by remember { mutableStateOf(true) }
    
    LaunchedEffect(household.id) {
        try {
            tasks = repo.getTasksForHousehold(household.id)
                .filter { it.completedAt == null }
                .take(5)
        } catch (_: Exception) { /* offline / error */ }
    }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (household.isPersonal) Icons.Default.Person else Icons.Default.Home,
                    contentDescription = null,
                    tint = if (household.isPersonal) Teal600 else Coral500
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = household.name,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("${tasks.size} pendientes", style = MaterialTheme.typography.bodySmall)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (tasks.isEmpty()) {
                    Text("No hay tareas pendientes", style = MaterialTheme.typography.bodySmall)
                } else {
                    tasks.forEach { task ->
                        TaskRow(task = task, onClick = { /* navegar a detalle */ })
                    }
                }
                
                // "Ver todas"
                TextButton(onClick = { onViewAll(household.id) }) {
                    Text("Ver todas las tareas →")
                }
            }
        }
    }
}
```

---

### Tarea 5: Conectar HomeScreen al flujo de App.kt

**Archivos:**
- Modificar: `composeApp/src/commonMain/kotlin/org/taskhub/App.kt`

**Paso 1:** Cambiar `initialScreen` para que siempre sea `HomeScreen()`, eliminando la lógica condicional de `WelcomeScreen` vs `HouseholdListScreen`.

```kotlin
LaunchedEffect(Unit) {
    // Asegurar espacio personal
    val personalId = householdStore.getPersonalHouseholdId()
    if (personalId == null) {
        try {
            val newHousehold = repo.createHousehold("Personal", "PERSONAL", isPersonal = true)
            householdStore.savePersonalHousehold(newHousehold.id, "Personal")
        } catch (_: Exception) { }
    }
    initialScreen = HomeScreen()
}
```

El bloque `when (val screen = initialScreen)` se mantiene igual, solo que ahora siempre resuelve a `HomeScreen`.

---

### Tarea 6: Añadir perfil de usuario básico

**Archivos:**
- Crear: `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/ProfileScreen.kt`

**Paso 1:** Pantalla simple con:
- Nombre del usuario (del espacio Personal o del primer miembro)
- Lista de hogares con opción de gestionar (abandonar, cambiar nombre)
- Acceso a ajustes
- Botón de "Crear/Unirse a hogar"

Conectar desde el botón de perfil en HomeScreen (sustituir el botón de ajustes por uno de perfil que lleve a ProfileScreen, y desde ahí a ajustes).

---

### Tarea 7: Compilar, verificar y commit

**Verificación final:**
```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:assembleDebug
```

**Commit:**
```bash
git add -A
git commit -m "feat: espacio Personal + HomeScreen unificada con tareas de todos los hogares"
```

---

## Riesgos y preguntas abiertas

1. **Firestore REST API para tareas multi-hogar**: Actualmente `getTasksForHousehold` consulta un hogar a la vez. ¿Queremos N consultas paralelas o un endpoint batch? → Para MVP, N consultas secuenciales + loading por sección.

2. **El espacio Personal técnicamente vive en Firestore**: Si el usuario no tiene conexión, ¿creamos el espacio offline? → Sí, crear localmente + sincronizar cuando haya red.

3. **Migración de usuarios existentes**: Los usuarios que ya tienen la app instalada necesitan que se les cree el espacio Personal al abrir la nueva versión. → `LaunchedEffect` lo maneja: si no existe, lo crea.

4. **Navegación desde HomeScreen a HouseholdScreen**: Al tocar "Ver todas", ¿navegamos a `HouseholdScreen(household.id)`? → Sí, es la misma pantalla existente, sin cambios.