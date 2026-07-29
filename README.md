# Task Hub

**Gamified shared household task manager** — Compose Multiplatform (Android + iOS + Desktop).

## Concept

A shared task management app where household members earn points for completing chores. Tasks can be assigned, tracked, and penalized if overdue. Users can analyze their own usage data.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Compose Multiplatform |
| Language | Kotlin |
| Database | SQLDelight (SQLite) |
| Networking | Ktor |
| DI | Koin |
| Navigation | Voyager |
| Build | Gradle |

## Architecture

```
composeApp/
├── commonMain/     ← Shared logic + UI
├── androidMain/    ← Android specifics
├── iosMain/        ← iOS specifics
└── desktopMain/    ← Desktop (secondary)

server/             ← Sync backend (future)
docs/               ← Specifications
```

## Status

🚧 **Fase 0 — Setup** — Initial project scaffolding
