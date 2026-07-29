# Task Hub — Especificación del producto

> **Versión:** 0.1.0 · **Estado:** Borrador inicial · **Autor:** Liberto  
> **Stack:** Kotlin · Compose Multiplatform · SQLDelight · Ktor · Koin · Voyager

---

## 1. Visión general

### 1.1 ¿Qué es Task Hub?

Task Hub es un gestor de tareas domésticas compartidas con un sistema de gamificación integrado. Está diseñado para núcleos de convivencia — parejas, pisos de estudiantes, familias — donde las tareas del hogar necesitan repartirse, ejecutarse y recompensarse de forma justa y transparente.

### 1.2 Problema que resuelve

En un hogar compartido, las tareas domésticas son fuente frecuente de fricción: nadie sabe qué le toca, las tareas se acumulan sin responsable claro, y no hay incentivo para hacerlas. Task Hub convierte las responsabilidades del hogar en un sistema visible, medible y recompensado, eliminando ambigüedad y añadiendo un componente lúdico.

### 1.3 Principios de diseño

1. **Local-first.** La app funciona completamente offline con SQLite local. La sincronización entre dispositivos es una capa adicional, no un requisito para el funcionamiento básico.
2. **Multiplataforma real.** Misma lógica de negocio en Android e iOS (Desktop como objetivo secundario).
3. **Gamificación con propósito.** Los puntos no son un adorno: reflejan contribución real al hogar y habilitan mecánicas de penalización por incumplimiento.
4. **Transparencia radical.** Cada miembro del hogar ve las tareas, puntuaciones y estadísticas de todos los demás. Nada oculto.
5. **Soberanía del dato.** Las estadísticas de uso personal («explotar tus datos») se generan en local, no dependen de un servidor.

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

### 2.2 Épica: Tareas

**HU-05 — Crear tarea**  
Como miembro de un hogar, quiero crear una tarea con título, descripción, puntos asignados, frecuencia (única/diaria/semanal/mensual) y fecha límite.

**HU-06 — Asignar tarea obligatoria**  
Como creador del hogar, quiero asignar una tarea a un miembro concreto como obligatoria, de forma que no pueda rechazarla.

**HU-07 — Ver tareas pendientes**  
Como miembro del hogar, quiero ver la lista de tareas pendientes filtrada por responsable, fecha límite y prioridad.

**HU-08 — Marcar tarea como completada**  
Como responsable de una tarea, quiero marcarla como hecha para recibir los puntos correspondientes.

**HU-09 — Verificar tarea completada**  
Como creador de una tarea, quiero poder verificar que se ha completado correctamente antes de que se otorguen los puntos (opcional, configurable por tarea).

**HU-10 — Penalización por retraso**  
Como sistema, quiero aplicar automáticamente puntos negativos cuando una tarea no se completa antes de su fecha límite.

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

**HU-17 — Sincronizar entre dispositivos**  
Como miembro de un hogar, quiero que mis tareas y puntuaciones se sincronicen entre mis dispositivos (móvil y tablet) automáticamente.

**HU-18 — Sincronizar entre miembros**  
Como miembro de un hogar, quiero que cuando otro miembro complete una tarea, yo lo vea reflejado en mi dispositivo sin intervención manual.

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

### 3.2 Tablas SQLDelight

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
    role         TEXT NOT NULL DEFAULT 'member', -- 'owner' | 'member'
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
    requires_verification INTEGER NOT NULL DEFAULT 0, -- boolean
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
    verified      INTEGER NOT NULL DEFAULT 0, -- boolean
    verified_by   TEXT REFERENCES Member(id),
    verified_at   INTEGER,
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
CREATED → ASSIGNED → IN_PROGRESS (opcional) → COMPLETED → VERIFIED
                                                    ↘
                                                  OVERDUE → PENALIZED
```

- `CREATED`: la tarea existe pero no está asignada a nadie
- `ASSIGNED`: tiene responsable y deadline
- `COMPLETED`: marcada como hecha por el responsable
- `VERIFIED`: confirmada por el creador (solo si `requires_verification = true`)
- `OVERDUE`: el deadline pasó sin completarse → se aplica penalización automática

---

## 4. Arquitectura de la app

### 4.1 Estructura de módulos

```
task-hub/
├── composeApp/
│   ├── commonMain/           ← ~85% del código
│   │   ├── data/             ← Repositorios, fuentes de datos, mapeos
│   │   ├── domain/           ← Casos de uso, entidades de dominio
│   │   ├── ui/               ← Pantallas, componentes, navegación (Voyager)
│   │   │   ├── screens/
│   │   │   │   ├── home/         ← Lista de tareas pendientes
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
├── shared/                   ← SQLDelight schemas, modelos compartidos
├── server/                   ← Sincronización (post-MVP)
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
│  Repositorios, SQLDelight, mapeo     │
└─────────────────────────────────────┘
```

- **UI → Domain:** las pantallas dependen de UseCases, nunca de repositorios directamente
- **Domain → Data:** los UseCases dependen de interfaces de repositorio (inversión de dependencia)
- **Data:** implementa las interfaces usando SQLDelight como fuente de verdad local

### 4.3 Stack tecnológico

| Capa         | Tecnología              | Justificación                                      |
|-------------|------------------------|---------------------------------------------------|
| UI          | Compose Multiplatform  | UI declarativa compartida Android + iOS + Desktop  |
| Navegación  | Voyager                | Navegación type-safe multiplataforma               |
| Base datos  | SQLDelight + SQLite    | Tipado seguro, generación de código Kotlin         |
| DI          | Koin                   | Ligero, nativo Kotlin, sin anotaciones             |
| Red         | Ktor (client)          | HTTP client multiplataforma para sync futuro       |
| Testing     | kotlin.test + Turbine  | Tests unitarios + Flow testing                     |
| Corrutinas  | kotlinx.coroutines     | Async multiplataforma                              |
| Date/Time   | kotlinx-datetime       | Multiplataforma, inmutable                         |
| Serialización | kotlinx.serialization | JSON para export/import y sync                     |

### 4.4 Flujo de datos (ejemplo: completar tarea)

```
1. Usuario pulsa "Completar" en TaskDetailScreen
2. TaskDetailViewModel llama a CompleteTaskUseCase(taskId)
3. CompleteTaskUseCase:
   a. Valida que el usuario es el asignado
   b. Verifica si está dentro del deadline → puntos normales
   c. Si fuera de plazo → aplica penalización automática
   d. Inserta TaskCompletion + actualiza Member.total_points
   e. Actualiza Streak (si aplica)
   f. Verifica logros desbloqueables
4. ViewModel recibe resultado y actualiza UI
5. SQLDelight emite cambios → flujos reactivos actualizan otras pantallas
```

---

## 5. Plan de fases

### 5.1 Visión cronológica

```
FASE 0 (Setup)       ██░░░░░░░░░░░░  Semana 1
FASE 1 (Fundación)   ████░░░░░░░░░░  Semanas 2-3
FASE 2 (Tareas)      ██████░░░░░░░░  Semanas 4-5
FASE 3 (Gamificación) ████████░░░░░░  Semanas 6-7
FASE 4 (MVP completo) ██████████░░░░  Semana 8
POST-MVP (Sync)       ████████████░░  Futuro
```

### 5.2 Fase 0 — Setup (Semana 1)

**Objetivo:** Proyecto compilable en las 3 plataformas.

- [ ] Scaffolding del proyecto CMP con Gradle + version catalog
- [ ] Configurar SQLDelight con esquema inicial
- [ ] Configurar Koin, Voyager, kotlinx-datetime
- [ ] Tema base (Material 3, colores, tipografía)
- [ ] Pantalla vacía "Hello Task Hub" en Android, iOS y Desktop
- [ ] CI básico con GitHub Actions (build + lint)

**Entregable:** APK de debug que muestra pantalla vacía con el tema aplicado.

### 5.3 Fase 1 — Fundación (Semanas 2-3)

**Objetivo:** Gestión de hogares y miembros completamente funcional.

- [ ] Implementar esquema SQLDelight completo (Household, Member)
- [ ] CRUD de hogares: crear, editar, eliminar
- [ ] Código de invitación (generación y validación)
- [ ] Unirse/abandonar hogar
- [ ] Lista de miembros con avatar y puntuación
- [ ] Pantallas: `CreateHouseholdScreen`, `JoinHouseholdScreen`, `HouseholdScreen`
- [ ] Tests unitarios para UseCases de Household y Member

**Entregable:** App donde puedo crear un hogar, compartir código de invitación, y otro dispositivo puede unirse.

### 5.4 Fase 2 — Tareas (Semanas 4-5)

**Objetivo:** Ciclo completo de creación, asignación y completado de tareas.

- [ ] CRUD de tareas con todos los campos
- [ ] Asignación a miembros (individual y múltiple)
- [ ] Tareas obligatorias (no rechazables)
- [ ] Completar tarea (con y sin verificación)
- [ ] Penalización automática por deadline vencido
- [ ] Filtros y ordenación en lista de tareas
- [ ] Pantallas: `TaskListScreen`, `TaskDetailScreen`, `CreateTaskScreen`
- [ ] Notificaciones locales para recordatorios de deadline
- [ ] Tests unitarios para lógica de puntos y penalizaciones

**Entregable:** App donde creo tareas, las asigno, las completo, y veo penalizaciones si me paso el deadline.

### 5.5 Fase 3 — Gamificación (Semanas 6-7)

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

### 5.6 Fase 4 — MVP completo (Semana 8)

**Objetivo:** Pulido, edge cases, pruebas manuales, preparación para distribución.

- [ ] Testing manual exhaustivo en Android (dispositivo físico)
- [ ] Testing manual en iOS (simulador + dispositivo si está disponible)
- [ ] Corrección de bugs encontrados
- [ ] Optimización de rendimiento (listas largas, animaciones)
- [ ] Accessibility review (content descriptions, contraste)
- [ ] Documentación de build y release
- [ ] Tag `v0.1.0` en GitHub

**Entregable:** APK/AAB listo para distribuir a testers. App funcional y pulida.

### 5.7 Post-MVP — Sincronización (futuro)

- [ ] Backend Ktor con WebSockets para sync en tiempo real
- [ ] Conflict resolution (CRDT o last-write-wins)
- [ ] Multi-dispositivo (mismo usuario en varios dispositivos)
- [ ] Push notifications para cambios del hogar

---

## 6. Criterios de aceptación por fase

### 6.1 Fase 0 — Setup

| ID    | Criterio                                                     |
|-------|--------------------------------------------------------------|
| AC-00 | `./gradlew build` compila sin errores en las 3 plataformas   |
| AC-01 | La app abre en emulador Android y muestra texto "Task Hub"   |
| AC-02 | La app abre en simulador iOS y muestra texto "Task Hub"      |
| AC-03 | El tema Material 3 está aplicado con colores de marca        |
| AC-04 | CI de GitHub Actions pasa (build + lint)                     |

### 6.2 Fase 1 — Fundación

| ID    | Criterio                                                                 |
|-------|--------------------------------------------------------------------------|
| AC-05 | Puedo crear un hogar con nombre, se persiste en SQLite                   |
| AC-06 | Se genera un código de invitación único de 6 caracteres                  |
| AC-07 | Otro dispositivo puede introducir el código y unirse al hogar            |
| AC-08 | La lista de miembros se actualiza al unirse alguien nuevo                |
| AC-09 | El creador del hogar tiene rol `owner`; los invitados, `member`          |
| AC-10 | Un miembro puede abandonar el hogar (soft delete, `left_at` no null)     |
| AC-11 | Si abandono el hogar, dejo de ver sus tareas y miembros                  |

### 6.3 Fase 2 — Tareas

| ID    | Criterio                                                                 |
|-------|--------------------------------------------------------------------------|
| AC-12 | Puedo crear una tarea con título, puntos, frecuencia y deadline          |
| AC-13 | Puedo asignar una tarea a uno o varios miembros                          |
| AC-14 | Una tarea marcada como obligatoria no puede ser rechazada                |
| AC-15 | Al completar una tarea a tiempo, recibo los puntos configurados          |
| AC-16 | Al completar una tarea fuera de plazo, se aplica penalización (-X pts)   |
| AC-17 | Si requires_verification=true, el creador debe verificar antes de puntos |
| AC-18 | La lista de tareas filtra por: pendientes, completadas, mías, todas      |
| AC-19 | Las tareas se ordenan por deadline más próximo primero                   |
| AC-20 | Recibo notificación local 1h antes del deadline de mis tareas            |

### 6.4 Fase 3 — Gamificación

| ID    | Criterio                                                                    |
|-------|-----------------------------------------------------------------------------|
| AC-21 | El ranking muestra miembros ordenados por puntos (total, semanal, mensual)  |
| AC-22 | La racha diaria aumenta al completar todas mis tareas del día               |
| AC-23 | La racha se rompe (vuelve a 0) si fallo un día                              |
| AC-24 | Se desbloquea insignia «5 días seguidos» al alcanzar racha 5                |
| AC-25 | Se desbloquea insignia «100 puntos» al acumular 100 puntos totales          |
| AC-26 | El dashboard muestra gráfico de barras con tareas/semana (últimas 4)        |
| AC-27 | El dashboard muestra evolución de puntos ganados vs perdidos                |
| AC-28 | Puedo exportar mis datos en CSV desde la pantalla de estadísticas           |

### 6.5 Fase 4 — MVP completo

| ID    | Criterio                                                            |
|-------|---------------------------------------------------------------------|
| AC-29 | 0 crashes en flujo completo: crear hogar → tareas → completar → stats |
| AC-30 | La app funciona sin conexión a internet (local-first comprobado)     |
| AC-31 | El rendimiento de scroll en listas de 50+ tareas es fluido (60fps)  |
| AC-32 | Todos los textos tienen content description para accesibilidad       |
| AC-33 | README tiene instrucciones claras de build y release                 |
| AC-34 | Tag `v0.1.0` apuntando al commit del MVP                            |

---

## 7. Definiciones y glosario

| Término           | Definición                                                                 |
|-------------------|-----------------------------------------------------------------------------|
| **Hogar**         | Unidad de convivencia: grupo de personas que comparten tareas               |
| **Miembro**       | Persona que pertenece a un hogar. Tiene rol `owner` o `member`             |
| **Owner**         | Creador del hogar. Puede asignar tareas obligatorias y verificar completados|
| **Puntos**        | Moneda interna de gamificación. Se ganan al completar tareas a tiempo       |
| **Penalización**  | Puntos negativos automáticos cuando una tarea no se completa a tiempo       |
| **Racha (streak)**| Días/semanas consecutivas cumpliendo todas las tareas asignadas             |
| **Logro**         | Insignia desbloqueable al alcanzar hitos (rachas, puntos totales, etc.)     |
| **Verificación**  | Paso opcional donde el creador confirma que la tarea se hizo correctamente  |
| **Local-first**   | La app funciona completamente offline; los datos viven en SQLite local      |

---

## 8. Riesgos y decisiones pendientes

### 8.1 Riesgos técnicos

| Riesgo                              | Impacto | Mitigación                                              |
|-------------------------------------|---------|---------------------------------------------------------|
| SQLDelight en iOS con multiplataforma | Medio  | Probar early en Fase 0 con driver nativo SQLite para iOS |
| Compose Multiplatform en iOS (beta) | Alto    | Mantener UI simple, probar en dispositivo real en Fase 1 |
| Sincronización multi-dispositivo    | Alto    | Postergar a post-MVP; local-first como red de seguridad  |
| Rendimiento con muchos datos        | Bajo    | SQLite local es rápido; paginación si >1000 tareas       |

### 8.2 Decisiones pendientes

- [ ] **Verificación de tareas:** ¿debe ser por foto o solo confirmación? → Simplificar: solo confirmación manual en MVP
- [ ] **Puntuación de penalización:** ¿fija (-10) o proporcional al retraso? → Proporcional: -20% de los puntos por día de retraso
- [ ] **Avatar de miembros:** ¿Gravatar, cámara, o iniciales? → Iniciales en MVP, Gravatar como opción futura
- [ ] **Categorías de tareas:** ¿etiquetas libres o predefinidas? → Predefinidas: limpieza, cocina, compras, mascotas, otros
- [ ] **Idiomas:** ¿solo español o multi-idioma? → Solo español para MVP; i18n es deuda técnica aceptada

---

## 9. Referencias

- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [SQLDelight](https://cashapp.github.io/sqldelight/)
- [Voyager](https://voyager.adriel.cafe/)
- [Koin](https://insert-koin.io/)
- [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime)