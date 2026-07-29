# Task Hub

**Gamified shared household task manager** — Compose Multiplatform (Android + iOS + Desktop) + Ktor Server (PostgreSQL).

## Concepto

Task Hub convierte las tareas del hogar en un sistema gamificado donde los miembros ganan puntos, mantienen rachas y desbloquean logros. Pensado para parejas, pisos de estudiantes y familias.

## Stack Tecnológico

| Capa         | Tecnología                     |
|-------------|-------------------------------|
| UI          | Compose Multiplatform (Material 3) |
| Navegación  | Voyager                       |
| DI          | Koin                          |
| Red (cliente) | Ktor Client                  |
| Red (servidor) | Ktor Server + Netty         |
| Base de datos | PostgreSQL                   |
| Migraciones | Flyway                        |
| i18n        | compose-resources (ES/EN)     |
| CI/CD       | GitHub Actions                |

## Estructura del proyecto

```
task-hub/
├── composeApp/
│   ├── commonMain/         ← Código compartido (~85%)
│   │   ├── kotlin/org/taskhub/
│   │   │   ├── App.kt
│   │   │   ├── di/AppModule.kt
│   │   │   └── ui/
│   │   │       ├── screens/HomeScreen.kt
│   │   │       └── theme/Theme.kt
│   │   └── composeResources/
│   │       ├── values/strings.xml        (ES — default)
│   │       ├── values-es/strings.xml     (ES)
│   │       ├── values-en/strings.xml     (EN)
│   │       └── drawable/ic_task.xml
│   ├── androidMain/        ← Android specifics
│   ├── iosMain/            ← iOS specifics
│   └── desktopMain/        ← Desktop specifics
├── server/
│   └── src/main/kotlin/org/taskhub/server/
│       ├── Application.kt
│       ├── plugins/
│       └── routes/HealthRoutes.kt
├── docs/specs.md           ← Especificación completa
├── gradle/libs.versions.toml
└── .github/workflows/ci.yml
```

## Requisitos

- **JDK 21** (OpenJDK o Temurin)
- **Android Studio** (para build de Android)
- **Xcode 16+** (para build de iOS, solo macOS)
- **PostgreSQL 16+** (para el backend)

## Build

### ComposeApp

```bash
# Desktop (JVM)
./gradlew :composeApp:desktopJar

# Android (requiere Android SDK)
./gradlew :composeApp:assembleDebug

# iOS (solo macOS)
./gradlew :composeApp:iosSimulatorArm64Binaries
```

### Server

```bash
# Build
./gradlew :server:build

# Ejecutar (local)
./gradlew :server:run

# Verificar health-check
curl http://localhost:8080/health
# → {"status":"ok"}
```

### Build completo

```bash
./gradlew build
```

## Base de datos

El servidor espera PostgreSQL corriendo en `localhost:5432`. Variables de entorno:

- `DATABASE_URL` — JDBC URL (default: `jdbc:postgresql://localhost:5432/taskhub`)
- `DATABASE_USER` — Usuario (default: `taskhub`)
- `DATABASE_PASSWORD` — Contraseña (default: `taskhub`)

Flyway ejecuta las migraciones automáticamente al arrancar.

## CI

GitHub Actions ejecuta build + lint en cada push a `main`. Ver `.github/workflows/ci.yml`.

## Fase actual

🚧 **Fase 0 — Setup** — Proyecto compilable con pantalla "Hello Task Hub" y backend con health-check.
