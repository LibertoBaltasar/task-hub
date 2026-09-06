# Task Hub

**Gestor de tareas del hogar con gamificación** — Compose Multiplatform para Android, iOS y escritorio (JVM).

## Qué es Task Hub

Task Hub organiza las tareas del hogar entre los miembros de un "household" (hogar/piso compartido): cada tarea completada suma puntos, esos puntos se canjean por recompensas, y se llevan rachas y logros para mantener la motivación. Los datos se sincronizan en tiempo real vía Firestore (Google Firebase, proyecto `task-hub-62f98`), con autenticación por Google Sign-In o de forma anónima.

## Características principales

Basado en las pantallas reales de `composeApp/src/commonMain/kotlin/org/taskhub/ui/screens/`:

- **Hogares (households)**: crear, unirse por código/QR y gestionar miembros.
- **Tareas**: creación, edición, plantillas, recurrencia y reglas de finalización/asignación.
- **Puntos y recompensas**: catálogo de recompensas canjeables por puntos acumulados.
- **Rachas y logros (achievements)**: seguimiento de constancia (p. ej. racha de 5 días).
- **Ranking**: clasificación de miembros del hogar por puntos.
- **Estadísticas**: resumen de actividad y rendimiento por miembro.
- **Chat del hogar**: mensajería entre miembros de un mismo household.
- **Calendario**: vista de tareas programadas con sincronización a Google Calendar.
- **Espacio personal**: sección de tareas/ajustes propios del usuario, fuera del hogar compartido.
- **Perfil público y perfil editable**, con avatar.
- **Notificaciones**: lista de notificaciones in-app y notificaciones locales (tarea asignada, mensaje nuevo, etc.).
- **Exportación**: exportación de tareas a CSV.
- i18n en español e inglés, tema propio (Teal/Coral) y anuncios (AdMob) en Android.

## Plataformas y stack

- **Plataformas**: Android (minSdk 26, target 36), iOS y escritorio (JVM/Desktop).
- **Stack**: Kotlin 2.1 + Compose Multiplatform 1.7.3, Voyager (navegación), Koin (DI), Firestore vía REST con Ktor (sin el SDK de Firestore), multiplatform-settings para persistencia local, Firebase Analytics y AdMob (solo Android).
- Detalle completo de la arquitectura, capas y decisiones técnicas en **[docs/ARQUITECTURA.md](docs/ARQUITECTURA.md)**.
- Modelo de datos de Firestore (colecciones, documentos, reglas) en **[docs/MODELO-DATOS.md](docs/MODELO-DATOS.md)**.

## Estructura del repo

```
task-hub/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/org/taskhub/   ← código compartido (App.kt, di/, ui/, network/, platform/, storage/)
│       ├── androidMain/                     ← específico de Android
│       ├── iosMain/                         ← específico de iOS
│       ├── desktopMain/                     ← específico de escritorio (JVM)
│       ├── commonTest/ y jvmTest/           ← tests
├── docs/                                    ← documentación del proyecto
├── scripts/                                 ← utilidades (reglas de Firestore, migraciones)
├── firestore.rules, firebase.json
└── gradlew
```

Desglose módulo a módulo (qué hay en `ui/screens`, `ui/models`, `network/`, `platform/`, `storage/`, etc.) en **[docs/ARQUITECTURA.md](docs/ARQUITECTURA.md)**.

## Compilar, ejecutar y probar

```bash
# Compilar Android (debug, commonMain + androidMain)
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain

# Ejecutar la app de escritorio (JVM)
./gradlew :composeApp:run

# Tests (commonTest + jvmTest)
./gradlew :composeApp:allTests
```

`BUILD SUCCESSFUL` en el primer comando confirma que compila el código compartido y Android. El bundle de release (con R8) tarda unos 5 minutos.

Guía paso a paso para dejar el entorno listo (JDK, Android Studio, Xcode para iOS, credenciales de Firebase, primer build) en **[docs/PRIMEROS-PASOS.md](docs/PRIMEROS-PASOS.md)**.

## Documentación

El mapa completo de la documentación del proyecto está en **[docs/INDICE.md](docs/INDICE.md)**. Documentos clave para empezar:

- **[docs/ARQUITECTURA.md](docs/ARQUITECTURA.md)** — stack, capas y estructura del código en detalle.
- **[docs/MODELO-DATOS.md](docs/MODELO-DATOS.md)** — modelo de datos en Firestore.
- **[docs/PRIMEROS-PASOS.md](docs/PRIMEROS-PASOS.md)** — cómo montar el entorno de desarrollo y compilar por primera vez.
- **[docs/FLUJOS-PRINCIPALES.md](docs/FLUJOS-PRINCIPALES.md)** — flujos de usuario principales (alta de hogar, ciclo de una tarea, canje de recompensas, etc.).
