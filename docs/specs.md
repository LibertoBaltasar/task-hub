# Task Hub — Especificación del producto

> **Versión:** 0.1.0 · **Estado:** Borrador inicial · **Autor:** Liberto  
> **Stack:** Kotlin · Compose Multiplatform · Ktor (server + client) · PostgreSQL · Koin · Voyager

---

## 1. Visión general

### 1.1 ¿Qué es Task Hub?

Task Hub es un gestor de tareas domésticas compartidas con un sistema de gamificación integrado. Está diseñado para núcleos de convivencia — parejas, pisos de estudiantes, familias — donde las tareas del hogar necesitan repartirse, ejecutarse y recompensarse de forma justa y transparente.

### 1.2 Problema que resuelve

En un hogar compartido, las tareas domésticas son fuente frecuente de fricción: nadie sabe qué le toca, las tareas se acumulan sin responsable claro, y no hay incentivo para hacerlas. Task Hub convierte las responsabilidades del hogar en un sistema visible, medible y recompensado, eliminando ambigüedad y añadiendo un componente lúdico.

### 1.3 Principios de diseño

1. **Backend-first.** El servidor Ktor + PostgreSQL es la fuente de verdad desde el día uno. La app cliente se comunica por HTTP/REST con el backend. Esto elimina la necesidad de sincronización entre dispositivos y conflictos de datos.
2. **Multiplataforma real.** Misma lógica de negocio en Android e iOS (Desktop como objetivo secundario). El cliente Ktor funciona en todas las plataformas.
3. **Gamificación con propósito.** Los puntos no son un adorno: reflejan contribución real al hogar y habilitan mecánicas de penalización por incumplimiento.
4. **Transparencia radical.** Cada miembro del hogar ve las tareas, puntuaciones y estadísticas de todos los demás. Nada oculto.
5. **Soberanía del dato.** Las estadísticas de uso personal se calculan desde los datos en el servidor. El usuario puede exportar sus datos en cualquier momento.
6. **Bilingüe español/inglés desde día 1.** La app soporta español e inglés desde el MVP usando string resources multiplataforma (compose-resources o similar). Otros idiomas se añadirán en el futuro, pero la base i18n se construye desde el principio.

### 1.4 Público objetivo

- Parejas que quieren repartir tareas sin discusiones
- Compañeros de piso que necesitan un sistema claro de responsabilidades
- Familias con hijos donde las tareas pueden ser también un juego educativo
- Cualquier grupo de convivencia de 2 a 10 personas

---

## 2. Historias de usuario

### 2.1 Épica: Gestión del hogar

**HU-01 — Crear hogar**  
Como usuario nuevo, quiero crear un hogar con un nombre y un código de invitación para que otros miembros puedan unirse.

**HU-02 — Unirse a un hogar**  
Como miembro de un hogar, quiero introducir un código de invitación para unirme a un hogar existente.

**HU-03 — Ver miembros del hogar**  
Como miembro de un hogar, quiero ver quiénes forman parte de mi hogar, con su avatar y puntuación actual.

**HU-04 — Abandonar hogar**  
Como miembro de un hogar, quiero poder salirme del hogar cuando deje de convivir allí.

**HU-04b — Crear perfil infantil**  
Como admin del hogar, quiero crear perfiles infantiles para que los niños del hogar solo puedan ver sus tareas asignadas y marcarlas como hechas, sin acceso a crear, editar, borrar ni modificar configuraciones.

**HU-04c — Vista simplificada infantil**  
Como perfil infantil, solo veo mis tareas asignadas en una lista simplificada con un botón «✅ Hecho» para marcarlas como completadas. No veo puntuaciones, rankings, creación de tareas ni configuración.

### 2.2 Épica: Tareas

**HU-05 — Crear tarea**  
Como miembro de un hogar, quiero crear una tarea con título, descripción, puntos asignados, frecuencia (única/diaria/semanal/mensual) y fecha límite. Si la frecuencia es recurrente, quiero elegir días específicos de la semana (ej. «Lunes, Miércoles, Viernes»).

**HU-05b — Configurar recurrencia por días de la semana**  
Como miembro de un hogar, quiero configurar una tarea recurrente para que se repita solo en días concretos de la semana — por ejemplo «L, X, V» o «todos los días» — de forma que se genere automáticamente una instancia con deadline propio para cada ocurrencia. Al completar una instancia, la siguiente se programa automáticamente.

**HU-06 — Asignar tarea obligatoria**  
Como admin del hogar, quiero asignar una tarea a un miembro concreto como obligatoria, de forma que no pueda rechazarla.

**HU-07 — Ver tareas pendientes**  
Como miembro del hogar, quiero ver la lista de tareas pendientes filtrada por responsable, fecha límite y prioridad.

**HU-08 — Marcar tarea como completada**  
Como responsable de una tarea, quiero marcarla como hecha con un botón simple para recibir los puntos correspondientes de forma automática.

**HU-10 — Penalización configurable por retraso**  
Como admin del hogar, quiero configurar penalizaciones por retraso al crear una tarea: elegir entre reducción de puntos fijos (ej. «-10 pts por cada día de retraso») o porcentuales (ej. «-20% de los puntos por cada semana de retraso»), con un tope máximo para que la penalización nunca supere los puntos totales de la tarea.

### 2.3 Épica: Gamificación

**HU-11 — Ver ranking del hogar**  
Como miembro del hogar, quiero ver un ranking semanal y mensual con las puntuaciones de todos los miembros.

**HU-12 — Rachas (streaks)**  
Como miembro del hogar, quiero ver mi racha actual de días/semanas cumpliendo todas mis tareas, como incentivo a la consistencia.

**HU-13 — Logros / insignias**  
Como miembro del hogar, quiero desbloquear logros (ej. «5 tareas seguidas», «100 puntos en una semana», «Limpiador maestro») visibles para todos.

### 2.4 Épica: Estadísticas personales

**HU-14 — Dashboard personal**  
Como miembro del hogar, quiero acceder a un dashboard con gráficos de mi actividad: tareas completadas por semana, puntos ganados/perdidos, categorías de tareas más frecuentes, evolución histórica.

**HU-15 — Comparativa**  
Como miembro del hogar, quiero comparar mis estadísticas con la media del hogar para saber si estoy contribuyendo más o menos que el resto.

**HU-16 — Exportar datos**  
Como miembro del hogar, quiero exportar mis datos de actividad en CSV/JSON para analizarlos fuera de la app.

### 2.5 Épica: Sincronización (post-MVP)

> **Nota:** Con la arquitectura backend-first, la sincronización es inherente al sistema: todos los clientes leen y escriben contra el mismo backend. No se requiere una épica separada de sincronización. La funcionalidad multidispositivo funciona desde el día uno.

---

## 3. Modelo de datos

### 3.1 Diagrama de entidades

```
┌──────────┐       ┌──────────────┐       ┌──────────┐
│  Member   │──────▶│  Household   │◀──────│   Task   │
└──────────┘       └──────────────┘       └──────────┘
     │                                          │
     │                    ┌─────────────────────┤
     │                    │                     │
     ▼                    ▼                     ▼
┌──────────┐       ┌──────────────┐       ┌──────────┐
│  Streak   │       │TaskAssignment│       │Achievement│
└──────────┘       └──────────────┘       └──────────┘
     │
     ▼
┌──────────────┐
│TaskCompletion │
└──────────────┘
```

### 3.2 Tablas PostgreSQL

```sql
-- Hogar / unidad de convivencia
CREATE TABLE Household (
    id          TEXT PRIMARY KEY,    -- UUID
    name        TEXT NOT NULL,
    invite_code TEXT NOT NULL UNIQUE, -- 6 caracteres alfanuméricos
    created_at  INTEGER NOT NULL,     -- epoch millis
    updated_at  INTEGER NOT NULL
);

-- Miembro del hogar
CREATE TABLE Member (
    id           TEXT PRIMARY KEY,    -- UUID
    household_id TEXT NOT NULL REFERENCES Household(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    avatar_url   TEXT,                -- nullable, gravatar o local
    role         TEXT NOT NULL DEFAULT 'child', -- 'admin' | 'child'
    total_points INTEGER NOT NULL DEFAULT 0,
    joined_at    INTEGER NOT NULL,
    left_at      INTEGER              -- nullable, soft-delete si abandona
);

-- Tarea (plantilla)
CREATE TABLE Task (
    id           TEXT PRIMARY KEY,    -- UUID
    household_id TEXT NOT NULL REFERENCES Household(id) ON DELETE CASCADE,
    created_by   TEXT NOT NULL REFERENCES Member(id),
    title        TEXT NOT NULL,
    description  TEXT,
    points       INTEGER NOT NULL DEFAULT 10,
    frequency    TEXT NOT NULL DEFAULT 'once', -- 'once' | 'daily' | 'weekly' | 'monthly'
    recurrence_days JSONB,            -- [1,3,5] = L,X,V (1=Lunes..7=Domingo). NULL si frequency='once'
    tags         TEXT[],               -- etiquetas libres + predefinidas (limpieza, cocina, compras, mascotas, mantenimiento, niños, exterior, administración, otro)
    penalty_mode      TEXT,            -- 'fixed' | 'percentage' | NULL (sin penalización)
    penalty_value     INTEGER,         -- cantidad de puntos o porcentaje a restar
    penalty_interval  TEXT,            -- 'hour' | 'day' | 'week'
    penalty_max       INTEGER,         -- tope máximo de penalización (nunca > puntos totales)
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL
);

-- Asignación de tarea a miembro(s)
CREATE TABLE TaskAssignment (
    id         TEXT PRIMARY KEY,
    task_id    TEXT NOT NULL REFERENCES Task(id) ON DELETE CASCADE,
    member_id  TEXT NOT NULL REFERENCES Member(id) ON DELETE CASCADE,
    mandatory  INTEGER NOT NULL DEFAULT 0, -- boolean: tarea obligatoria
    due_date   INTEGER,              -- epoch millis, nullable para tareas sin deadline
    assigned_at INTEGER NOT NULL,
    UNIQUE(task_id, member_id)
);

-- Registro de tarea completada
CREATE TABLE TaskCompletion (
    id            TEXT PRIMARY KEY,
    assignment_id TEXT NOT NULL REFERENCES TaskAssignment(id) ON DELETE CASCADE,
    completed_at  INTEGER NOT NULL,    -- epoch millis
    points_awarded INTEGER NOT NULL,   -- puede diferir de Task.points (penalizaciones)
    on_time       INTEGER NOT NULL     -- boolean: se completó antes del deadline
);

-- Penalización por retraso
CREATE TABLE Penalty (
    id              TEXT PRIMARY KEY,
    assignment_id   TEXT NOT NULL REFERENCES TaskAssignment(id) ON DELETE CASCADE,
    points_deducted INTEGER NOT NULL,
    reason          TEXT NOT NULL,      -- 'overdue' | 'incomplete' | 'rejected'
    applied_at      INTEGER NOT NULL
);

-- Rachas del miembro
CREATE TABLE Streak (
    id         TEXT PRIMARY KEY,
    member_id  TEXT NOT NULL REFERENCES Member(id) ON DELETE CASCADE,
    type       TEXT NOT NULL DEFAULT 'daily', -- 'daily' | 'weekly'
    count      INTEGER NOT NULL DEFAULT 0,
    best       INTEGER NOT NULL DEFAULT 0,
    last_updated INTEGER NOT NULL,
    UNIQUE(member_id, type)
);

-- Logros desbloqueados
CREATE TABLE Achievement (
    id          TEXT PRIMARY KEY,
    member_id   TEXT NOT NULL REFERENCES Member(id) ON DELETE CASCADE,
    key         TEXT NOT NULL,       -- 'streak_5', 'points_100_week', etc.
    unlocked_at INTEGER NOT NULL,
    UNIQUE(member_id, key)
);
```

### 3.3 Estados de una tarea

```
                        ┌──────────────────────────────┐
                        │   RECURRENCIA (si frequency   │
                        │   ≠ 'once')                   │
                        │   ┌──────────────────────┐    │
                        │   │ Al completar instancia │   │
                        │   │ → se genera siguiente  │   │
                        │   │ con deadline = próximo  │  │
                        │   │ día configurado         │  │
                        │   └──────────────────────┘    │
                        └──────────────────────────────┘
                                  ↑
                                  │ (nueva instancia)
                                  │
CREATED → ASSIGNED → COMPLETED
                        ↘
                      OVERDUE → PENALIZED
```

- `CREATED`: la tarea existe pero no está asignada a nadie
- `ASSIGNED`: tiene responsable y deadline. Para tareas recurrentes, cada ocurrencia genera su propia instancia de `TaskAssignment` con `due_date` calculado a partir de `recurrence_days` (ej. próximo lunes, próximo miércoles...)
- `COMPLETED`: marcada como hecha por el responsable. Los puntos se otorgan automáticamente al marcar «Hecho», aplicando la penalización configurada si la tarea está fuera de plazo.
- `OVERDUE`: el deadline pasó sin completarse → se aplica penalización automática según el modo configurado (fijo o porcentaje) y el intervalo definido (hora/día/semana), respetando el tope máximo.
- **Recurrencia:** al completar una instancia de tarea recurrente, el sistema genera automáticamente la siguiente instancia con deadline en el próximo día configurado. Si `recurrence_days = [1, 3, 5]` (L, X, V) y hoy es lunes, la siguiente instancia tendrá deadline el miércoles.

---

## 4. Arquitectura de la app

### 4.1 Estructura de módulos

```
task-hub/
├── server/                   ← Backend Ktor (PostgreSQL) — fuente de verdad
│   ├── src/main/kotlin/
│   │   ├── models/           ← Entidades JPA/Exposed
│   │   ├── routes/           ← Endpoints REST
│   │   ├── services/         ← Lógica de negocio del servidor
│   │   └── plugins/          ← Ktor plugins (auth, serialization, etc.)
│   └── src/main/resources/
│       └── db/migration/     ← Migraciones Flyway/Liquibase
├── composeApp/
│   ├── commonMain/           ← ~85% del código
│   │   ├── data/             ← Repositorios (cliente Ktor HTTP), mapeos DTO
│   │   ├── domain/           ← Casos de uso, entidades de dominio
│   │   ├── ui/               ← Pantallas, componentes, navegación (Voyager)
│   │   │   ├── screens/
│   │   │   │   ├── home/         ← Lista de tareas pendientes
│   │   │   │   ├── child/        ← Vista simplificada para perfil infantil (solo tareas + botón «Hecho»)
│   │   │   │   ├── household/    ← Gestión del hogar y miembros
│   │   │   │   ├── task/         ← Crear/editar/detalle de tarea
│   │   │   │   ├── ranking/      ← Ranking y logros
│   │   │   │   └── stats/        ← Dashboard personal
│   │   │   ├── components/   ← Componentes reutilizables
│   │   │   └── theme/        ← Tema, colores, tipografía
│   │   └── di/               ← Módulos de Koin
│   ├── androidMain/          ← Android specifics (permisos, notificaciones)
│   ├── iosMain/              ← iOS specifics
│   └── desktopMain/          ← Desktop (secundario)
├── docs/                     ← Especificaciones y documentación
└── gradle/libs.versions.toml ← Catálogo de versiones
```

### 4.2 Capas (Clean Architecture simplificada)

```
┌─────────────────────────────────────┐
│  UI Layer (Compose + Voyager)        │
│  Pantallas, navegación, ViewModels   │
├─────────────────────────────────────┤
│  Domain Layer                        │
│  UseCases, entidades, interfaces     │
├─────────────────────────────────────┤
│  Data Layer                          │
│  Repositorios, cliente Ktor HTTP     │
├─────────────────────────────────────┤
│  Backend (Ktor + PostgreSQL)         │
│  API REST, lógica de servidor, DB    │
└─────────────────────────────────────┘
```

- **UI → Domain:** las pantallas dependen de UseCases, nunca de repositorios directamente
- **Domain → Data:** los UseCases dependen de interfaces de repositorio (inversión de dependencia)
- **Data:** implementa las interfaces usando Ktor Client para llamadas HTTP al backend
- **Backend:** servidor Ktor con endpoints REST. PostgreSQL como base de datos. La lógica de negocio que afecta a múltiples usuarios (puntos, penalizaciones, rachas, recurrencia) se ejecuta en el servidor.

### 4.3 Stack tecnológico

| Capa         | Tecnología              | Justificación                                      |
|-------------|------------------------|---------------------------------------------------|
| UI          | Compose Multiplatform  | UI declarativa compartida Android + iOS + Desktop  |
| Navegación  | Voyager                | Navegación type-safe multiplataforma               |
| Red (cliente) | Ktor Client          | HTTP client multiplataforma para consumir la API   |
| Red (servidor) | Ktor Server         | Backend ligero, nativo Kotlin, corrutinas           |
| Base datos  | PostgreSQL             | Robusta, relacional, JSONB para recurrence_days    |
| Migraciones | Flyway                 | Versionado de esquema de base de datos             |
| DI          | Koin                   | Ligero, nativo Kotlin, sin anotaciones             |
| Testing     | kotlin.test + Turbine  | Tests unitarios + Flow testing                     |
| Corrutinas  | kotlinx.coroutines     | Async multiplataforma                              |
| Date/Time   | kotlinx-datetime       | Multiplataforma, inmutable                         |
| Serialización | kotlinx.serialization | JSON para API REST y exportación de datos          |
| i18n         | compose-resources      | String resources multiplataforma ES/EN              |

### 4.4 Flujo de datos (ejemplo: completar tarea)

```
1. Usuario pulsa "Completar" en TaskDetailScreen
2. TaskDetailViewModel llama a CompleteTaskUseCase(taskId)
3. CompleteTaskUseCase:
   a. Valida que el usuario es el asignado (consulta al backend)
   b. Envía POST /api/tasks/{taskId}/complete al backend
   c. El backend:
      - Verifica deadline → puntos normales o penalización configurada
      - Inserta TaskCompletion + actualiza Member.total_points
      - Actualiza Streak (si aplica)
      - Verifica logros desbloqueables
      - Si la tarea es recurrente: genera la siguiente instancia
        con due_date = próximo día configurado en recurrence_days
   d. Responde con el resultado (puntos, logros nuevos, siguiente instancia)
4. ViewModel recibe resultado y actualiza UI
5. El backend emite la respuesta → UI se actualiza con datos frescos del servidor
```

---

## 5. Plan de fases

### 5.1 Visión cronológica

```
FASE 0 (Setup)        ██░░░░░░░░░░░░  Semana 1
FASE 1 (Fundación)    ████░░░░░░░░░░  Semanas 2-4
FASE 2 (Tareas)       ██████░░░░░░░░  Semanas 5-6
FASE 3 (Gamificación) ████████░░░░░░  Semanas 7-8
FASE 4 (MVP completo) ██████████░░░░  Semana 9
```

### 5.2 Fase 0 — Setup (Semana 1)

**Objetivo:** Proyecto compilable en las 4 plataformas (Android, iOS, Desktop, servidor).

- [ ] Scaffolding del proyecto CMP con Gradle + version catalog
- [ ] Configurar Ktor Server con endpoints de health-check
- [ ] Configurar PostgreSQL + Flyway con esquema inicial
- [ ] Configurar Ktor Client, Koin, Voyager, kotlinx-datetime
- [ ] Tema base (Material 3, colores, tipografía)
- [ ] Pantalla vacía "Hello Task Hub" en Android, iOS y Desktop
- [ ] CI básico con GitHub Actions (build + lint + test)

**Entregable:** Backend corriendo con health-check + app cliente que muestra pantalla vacía con el tema aplicado.

### 5.3 Fase 1 — Fundación (Semanas 2-4)

**Objetivo:** Backend con API REST de hogares/miembros + app cliente consumiéndola.

- [ ] Implementar esquema PostgreSQL completo (Household, Member, Task, etc.)
- [ ] Migraciones Flyway para todas las tablas
- [ ] Endpoints REST del backend:
  - `POST /api/households` — crear hogar
  - `POST /api/households/{id}/join` — unirse con código de invitación
  - `POST /api/households/{id}/leave` — abandonar hogar
  - `GET /api/households/{id}/members` — listar miembros
- [ ] Cliente Ktor en la app consumiendo todos los endpoints
- [ ] Código de invitación (generación y validación en el servidor)
- [ ] Unirse/abandonar hogar desde la app
- [ ] Lista de miembros con avatar y puntuación
- [ ] Pantallas: `CreateHouseholdScreen`, `JoinHouseholdScreen`, `HouseholdScreen`
- [ ] Tests unitarios para UseCases de Household y Member
- [ ] Tests de integración para endpoints REST del backend

**Entregable:** Backend desplegable con API REST de hogares + app donde puedo crear un hogar, compartir código, y otro dispositivo puede unirse (todo a través del servidor).

### 5.4 Fase 2 — Tareas (Semanas 5-6)

**Objetivo:** Ciclo completo de creación, asignación y completado de tareas, incluyendo recurrencia por días de la semana y penalizaciones configurables.

- [ ] CRUD de tareas con todos los campos, incluyendo `recurrence_days`, `tags` y configuración de penalizaciones (`penalty_mode`, `penalty_value`, `penalty_interval`, `penalty_max`)
- [ ] Endpoints REST del backend:
  - `POST /api/tasks` — crear tarea
  - `GET /api/tasks?household_id=...` — listar tareas del hogar
  - `POST /api/tasks/{id}/assign` — asignar a miembros
  - `POST /api/tasks/{id}/complete` — completar (con lógica de recurrencia y penalización configurable)
- [ ] Asignación a miembros (individual y múltiple)
- [ ] Tareas obligatorias (no rechazables)
- [ ] Completar tarea con botón simple → puntos automáticos
- [ ] Penalización configurable por retraso: modo fijo (-X pts/intervalo) o porcentual (-X%/intervalo), con tope máximo
- [ ] **Recurrencia:** al completar instancia de tarea recurrente, se genera automáticamente la siguiente con deadline en el próximo día configurado en `recurrence_days`
- [ ] Selector de días de la semana en UI de creación de tarea (checkboxes L M X J V S D)
- [ ] Filtros y ordenación en lista de tareas
- [ ] Pantallas: `TaskListScreen`, `TaskDetailScreen`, `CreateTaskScreen`
- [ ] Notificaciones locales para recordatorios de deadline
- [ ] Tests unitarios para lógica de puntos, penalizaciones y recurrencia

**Entregable:** App donde creo tareas (incluyendo recurrentes con días específicos), las asigno, las completo, y veo cómo se genera la siguiente instancia automáticamente.

### 5.5 Fase 3 — Gamificación (Semanas 7-8)

**Objetivo:** Ranking, rachas, logros y dashboard de estadísticas.

- [ ] Sistema de rachas (daily/weekly) con UI
- [ ] Logros predefinidos (10-15 insignias iniciales)
- [ ] Ranking del hogar (semanal y mensual)
- [ ] Dashboard personal con gráficos:
  - Tareas completadas por semana (barras)
  - Puntos ganados vs perdidos (líneas)
  - Distribución por categoría (tarta)
- [ ] Pantallas: `RankingScreen`, `AchievementsScreen`, `StatsDashboardScreen`
- [ ] Exportación de datos personales (CSV)

**Entregable:** App con gamificación completa — ranking, logros, dashboard de estadísticas.

### 5.6 Fase 4 — MVP completo (Semana 9)

**Objetivo:** Pulido, edge cases, pruebas manuales, preparación para distribución.

- [ ] Testing manual exhaustivo en Android (dispositivo físico)
- [ ] Testing manual en iOS (simulador + dispositivo si está disponible)
- [ ] Testing de integración backend con varios clientes simultáneos
- [ ] Corrección de bugs encontrados
- [ ] Optimización de rendimiento (listas largas, animaciones)
- [ ] Accessibility review (content descriptions, contraste)
- [ ] Documentación de build y release (backend + app)
- [ ] Tag `v0.1.0` en GitHub

**Entregable:** Backend desplegable + APK/AAB listo para distribuir a testers.

---

## 6. Criterios de aceptación por fase

### 6.1 Fase 0 — Setup

| ID    | Criterio                                                     |
|-------|--------------------------------------------------------------|
| AC-00 | `./gradlew build` compila sin errores en las 4 plataformas (app + server) |
| AC-01 | El servidor arranca y responde a `GET /health`                             |
| AC-02 | La app abre en emulador Android y muestra texto "Task Hub"                  |
| AC-03 | La app abre en simulador iOS y muestra texto "Task Hub"                     |
| AC-04 | El tema Material 3 está aplicado con colores de marca                       |
| AC-05 | CI de GitHub Actions pasa (build + lint + test)                             |

### 6.2 Fase 1 — Fundación

| ID    | Criterio                                                                 |
|-------|--------------------------------------------------------------------------|
| AC-06 | Puedo crear un hogar mediante `POST /api/households`, se persiste en PostgreSQL  |
| AC-07 | Se genera un código de invitación único de 6 caracteres                           |
| AC-08 | Otro dispositivo puede introducir el código y unirse al hogar (vía API)           |
| AC-09 | La lista de miembros se actualiza al unirse alguien nuevo (consultando al backend)|
| AC-10 | El creador del hogar tiene rol `admin`; los invitados, `child` por defecto. El admin puede promover a otros miembros a `admin`               |
| AC-11 | Un miembro puede abandonar el hogar (soft delete, `left_at` no null)              |
| AC-12 | Si abandono el hogar, dejo de ver sus tareas y miembros                           |

### 6.3 Fase 2 — Tareas

| ID    | Criterio                                                                 |
|-------|--------------------------------------------------------------------------|
| AC-13 | Puedo crear una tarea con título, puntos, frecuencia, deadline y días de recurrencia |
| AC-14 | Puedo asignar una tarea a uno o varios miembros                                       |
| AC-15 | Una tarea marcada como obligatoria no puede ser rechazada                             |
| AC-16 | Al completar una tarea a tiempo, recibo los puntos configurados                       |
| AC-17 | Al completar una tarea fuera de plazo, se aplica la penalización configurada (modo fijo o porcentual, según intervalo) respetando el tope máximo |
| AC-18 | Una tarea puede tener etiquetas predefinidas y/o personalizadas; el autocompletado sugiere tags ya usados en el hogar |
| AC-19 | La lista de tareas filtra por: pendientes, completadas, mías, todas y por etiquetas   |
| AC-20 | Las tareas se ordenan por deadline más próximo primero                                |
| AC-21 | Recibo notificación local 1h antes del deadline de mis tareas                         |
| AC-22 | Una tarea recurrente con `recurrence_days = [1, 3, 5]` genera instancias para L, X, V |
| AC-23 | Al completar una instancia recurrente, se genera automáticamente la siguiente         |
| AC-24 | Un perfil `admin` puede crear perfiles `child` desde la pantalla de miembros          |
| AC-25 | Un perfil `child` solo ve sus tareas asignadas con un botón «✅ Hecho», sin acceso a crear, editar, puntuaciones ni configuración |
| AC-26 | La app y todos sus textos funcionan en español e inglés, seleccionables desde ajustes |

### 6.4 Fase 3 — Gamificación

| ID    | Criterio                                                                    |
|-------|-----------------------------------------------------------------------------|
| AC-27 | El ranking muestra miembros ordenados por puntos (total, semanal, mensual)  |
| AC-28 | La racha diaria aumenta al completar todas mis tareas del día               |
| AC-29 | La racha se rompe (vuelve a 0) si fallo un día                              |
| AC-30 | Se desbloquea insignia «5 días seguidos» al alcanzar racha 5                |
| AC-31 | Se desbloquea insignia «100 puntos» al acumular 100 puntos totales          |
| AC-32 | El dashboard muestra gráfico de barras con tareas/semana (últimas 4)        |
| AC-33 | El dashboard muestra evolución de puntos ganados vs perdidos                |
| AC-34 | Puedo exportar mis datos en CSV desde la pantalla de estadísticas           |

### 6.5 Fase 4 — MVP completo

| ID    | Criterio                                                            |
|-------|---------------------------------------------------------------------|
| AC-35 | 0 crashes en flujo completo: crear hogar → tareas → completar → stats           |
| AC-36 | El backend responde correctamente con múltiples clientes simultáneos             |
| AC-37 | El rendimiento de scroll en listas de 50+ tareas es fluido (60fps)               |
| AC-38 | Todos los textos tienen content description para accesibilidad                    |
| AC-39 | README tiene instrucciones claras de build y release (backend + app)             |
| AC-40 | Tag `v0.1.0` apuntando al commit del MVP                                         |

---

## 7. Definiciones y glosario

| Término           | Definición                                                                 |
|-------------------|-----------------------------------------------------------------------------|
| **Hogar**         | Unidad de convivencia: grupo de personas que comparten tareas               |
| **Miembro**       | Persona que pertenece a un hogar. Tiene rol `admin` o `child`              |
| **Admin**         | Perfil adulto. Puede crear/editar/eliminar tareas, asignarlas, configurar puntuaciones y penalizaciones, invitar miembros, y ver todo |
| **Child**         | Perfil infantil. Solo ve sus tareas asignadas y puede marcarlas como hechas. No puede crear, editar, borrar ni cambiar configuraciones |
| **Puntos**        | Moneda interna de gamificación. Se ganan al completar tareas a tiempo       |
| **Penalización**  | Reducción de puntos configurable por el admin: modo fijo (-X pts por intervalo) o porcentual (-X% por intervalo), con tope máximo |
| **Racha (streak)**| Días/semanas consecutivas cumpliendo todas las tareas asignadas             |
| **Logro**         | Insignia desbloqueable al alcanzar hitos (rachas, puntos totales, etc.)     |
| **Etiquetas**     | Tags libres que el usuario asigna a las tareas. Predefinidas: limpieza, cocina, compras, mascotas, mantenimiento, niños, exterior, administración, otro. El usuario puede añadir las suyas propias con autocompletado desde tags ya usados en el hogar |
| **Backend-first**  | La app cliente se comunica con un servidor Ktor + PostgreSQL. El backend es la fuente de verdad; no hay base de datos local. |

---

## 8. Riesgos y decisiones pendientes

### 8.1 Riesgos técnicos

| Riesgo                              | Impacto | Mitigación                                              |
|-------------------------------------|---------|---------------------------------------------------------|
| Compose Multiplatform en iOS (beta) | Alto    | Mantener UI simple, probar en dispositivo real en Fase 1 |
| Disponibilidad del backend          | Alto    | Backend en servidor propio/VPS; health-check en CI; la app muestra estado de conexión |
| Rendimiento de red en móvil         | Medio   | Ktor Client con caché HTTP; paginación en listas largas  |
| Complejidad de recurrencia          | Medio   | Algoritmo sencillo: next_day_of_week(); tests exhaustivos de edge cases (cambio de mes, año bisiesto) |

### 8.2 Decisiones pendientes

- [ ] **Puntuación y penalización:** ¿debe el usuario poder configurar la penalización? → Sí: dos modos configurables al crear la tarea — puntos fijos por intervalo (-10 pts/día) o porcentaje (-20%/semana), con tope máximo. Ver §3.2 tabla Task.
- [ ] **Avatar de miembros:** ¿Gravatar, cámara, o iniciales? → Iniciales en MVP, Gravatar como opción futura
- [ ] **Etiquetas de tareas:** ¿etiquetas libres o predefinidas? → Ambas: predefinidas (limpieza, cocina, compras, mascotas, mantenimiento, niños, exterior, administración, otro) + libres con autocompletado desde tags del hogar
- [ ] **Idiomas:** ¿solo español o multi-idioma? → Bilingüe español/inglés desde el MVP usando string resources multiplataforma. Otros idiomas post-MVP.

---

## 9. Referencias

- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor](https://ktor.io/) — Server + Client
- [PostgreSQL](https://www.postgresql.org/)
- [Flyway](https://flywaydb.org/)
- [Voyager](https://voyager.adriel.cafe/)
- [Koin](https://insert-koin.io/)
- [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)