---
workdir: /home/liberto/task-hub
max_turns: 600
allowed_tools: Read,Edit,Write,Bash,Grep,Glob,Task
---

# Encargo: PANEL DE EXPERTOS (4ª edición — 13 expertos)

## Objetivo
Revisión integral y exhaustiva de Task Hub con un panel de **TRECE expertos**
(igual que la v3). Esta 4ª edición debe centrarse en lo que las rondas anteriores
y los encargos recientes NO cubrieron, y en detectar REGRESIONES de los cambios
recientes. Aplicar todos los cambios objetivos/seguros que no requieran decisión
del usuario; lo subjetivo/estructural se documenta como PROPUESTA.

## Estado actual (LEER ANTES de empezar)
- Versión actual 0.7.25. Rama limpia.
- Desde la v3 (`docs/review-panel-expertos-v3-2026-09-01.md`) han entrado cambios
  NUEVOS que debes revisar a fondo (donde más probable es que haya regresiones):
  - `f45f880` fix: recurrencia — completado tardío (weekly/monthly) + mensaje de
    calendario corregido.
  - `c8d11e1` fix: propuestas objetivas del panel v3 — estado de mutación
    separado (MemberActionState), preview de recurrencia, dedup serialización
    TaskRepository, enlace privacy.html en Ajustes, unificar isDueToday
    (CalendarScreen → RecurrenceRules).
  - Y los encargos posteriores (recurrencia `nextDueAt` + concurrencia optimista,
    privacidad/seguridad, arquitectura/rendimiento, UX/consistencia) — mira
    `git log --oneline -30` y los informes/docs nuevos en `docs/` para saber qué
    se aplicó después de este encargo.
- Informes/auditorías previos que DEBES leer para NO duplicar hallazgos ya
  resueltos (centrarte en lo NUEVO): v1, v2, v3 (`docs/review-panel-expertos-*.md`),
  `docs/audit-2026-08-30.md`, `docs/refactor-arquitectura-2026-08-31.md`,
  `docs/atomicidad-commit-pendiente.md`.

## Metodología
Igual que la v3: actúa como COORDINADOR, lanza subagentes en paralelo (tu
herramienta `Task`), uno por especialista, cada uno con mandato independiente, y
consolida tú el resultado. Si no puedes lanzar subagentes en paralelo, trabaja en
secuencia con una "voz" separada por especialista y déjalo claro.

## Los TRECE expertos
Los mismos que la v3: 1 Estética, 2 Funcionalidad, 3 Accesibilidad (verifica
ratios reales con `wcag_contrast.py`), 4 UI/Componentes, 5 UX, 6 Programador
senior, 7 Jefe de arquitectura, 8 QA/bugs, 9 Seguridad/AppSec, 10 Privacidad/RGPD/
menores, 11 Rendimiento, 12 Fiabilidad de red/offline, 13 Cobertura de pruebas.
Manda el mismo mandato detallado por experto que en la v3, con estos añadidos:
- Foco en REGRESIONES de los cambios recientes (nextDueAt, concurrencia optimista,
  MemberActionState, preview recurrencia, dedup serialización, cascade-delete,
  cifrado de tokens, paginación, memoización de TaskListContent, propagación del
  nombre del hogar, eliminar-miembro).
- Verificar que las PROPUESTAS aplicadas quedaron bien hechas y sin efectos
  colaterales.

## Foco especial — recurrencia (revisar lo aplicado)
El usuario acaba de encargar el núcleo de recurrencia (campo `nextDueAt` +
unificación de la regeneración de asignación + concurrencia optimista en
`completeAssignment` + premarcar días). Verifica a fondo que quedó correcto: sin
duplicación de puntos, sync con Google Calendar, rotación de asignación, edge
cases de timezone/DST, y que los tests cubren los casos nuevos.

## Contexto técnico
- Stack: Compose Multiplatform (Kotlin 2.1, CMP 1.7.3), Ktor (Firestore REST, NO
  SDK), Koin, Voyager, multiplatform-settings. Android minSdk 26/target 35 + iOS
  + JVM.
- Fuente: `composeApp/src/{commonMain,androidMain,iosMain,jvmMain}`.
- `ui/theme/Theme.kt` y `ui/theme/SemanticColors.kt` — paleta y temas.
- `ui/i18n/AppStrings.kt` — diccionario ES+EN. NO dejar texto hardcodeado.
- Solo `material-icons-core` (NO extended). Iconos espejados →
  `Icons.AutoMirrored.Filled.*`.

## Criterios de aplicación
- **APLICA YA** (objetivo, seguro, alto impacto, sin decisión del usuario):
  accesibilidad (contraste, touch targets, contentDescription, reduce-motion),
  bugs Crítico/Alto/Medio con fix localizado, funcionalidad rota, validación,
  dead controls, `var`→`val`, dead code, excepciones tragadas, i18n residual,
  literales→tokens, iconos espejados.
- **NO APLIQUES — SOLO PROPUESTA** (subjetivo o refactor estructural que el
  usuario debe decidir): rediseños de layout mayor, cambio de paleta de marca,
  reestructurar capas, cambiar patrón ScreenModel, reordenar módulos Koin,
  migraciones de caché/esquema, cambios de flujo de auth.

## Fases
1. **Reconocimiento**: lee el theme completo, `network/`, `storage/`, `platform/`,
   `di/`, todos los ScreenModels y screens (archivos grandes en varias lecturas).
2. **Panel**: lanza los 13 expertos en paralelo.
3. **Consolidación**: escribe `docs/review-panel-expertos-v4.md` con TODOS los
   hallazgos por experto y priorizados, cada uno con Problema → Por qué importa →
   Fix (o PROPUESTA), tabla top-10 impacto/esfuerzo, y sección dedicada a
   regresiones y al foco de recurrencia. En VARIAS llamadas `write_file`.
4. **Aplicar fixes**.
5. **Verificar** (OBLIGATORIO):
   ```
   cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
   cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
   ```
   `BUILD SUCCESSFUL` obligatorio y tests en verde.

## Convenciones
- Comentarios/KDoc en español. NO hagas commit, push ni bump.
- No toques diseño visual salvo mejora objetiva (accesibilidad/consistencia).

## Entrega (resumen final obligatorio)
1. nº de hallazgos por experto y severidad (CRÍTICO/IMPORTANTE/MENOR).
2. nº de fixes aplicados por categoría.
3. Regresiones encontradas (de los cambios recientes) y cómo se resolvieron.
4. Estado del foco de recurrencia.
5. lista de PROPUESTAS no aplicadas con coste/beneficio.
6. resultado de build/tests.
7. deuda pendiente y riesgos.
