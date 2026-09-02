---
workdir: /home/liberto/task-hub
max_turns: 250
allowed_tools: Read,Edit,Write,Bash,Grep,Glob,Task
---

# Encargo: Cobertura de tests — lógica crítica (diagnóstico Experto 13)

El panel de expertos v4 (`docs/review-panel-expertos-v4.md`, Experto 13) listó
los huecos de cobertura en el código NUEVO desde v3. Cubre la lógica PURA y
crítica (testeable en jvmTest) que hoy está en 0:

1. `resolveCompletionOutcome` / `calculatePenalty` / `regenerateNextAssignment`
   — lógica de negocio de puntos, hoy métodos privados sin test.
2. `isDueToday` weekly con VARIOS `recurrenceDays` marcados a la vez
   (lunes+miércoles+viernes).
3. `endOfDueDay` con cruce de mes/año.
4. `clampDayOfMonth` con `day=29`/`30` de entrada.
5. Casos con `tz` (TimeZone) explícito distinto de `currentSystemDefault()`.
6. `SettingsStore.migrateLegacyToken` (incluido el fallback silencioso a null
   si falla el descifrado).
7. `resolveRotationAssignee` con `tz` no-default.
8. `MemberActionState` transiciones de ScreenModel (si es testeable sin
   Android; si no, márcalo como hueco no cubrible en jvmTest).
9. Sustituye el número mágico `50` en `PointsRulesTest.kt` por
   `PointsRules.WEEKLY_APPRECIATION_BUDGET`.

NO hagas una suite masiva; solo los huecos listados y testeables en jvmTest.
No añadas dependencias nuevas. Los métodos privados: si hace falta para
testearlos, considera hacerlos `internal` o extraer a objetos puros testeables
(mínima superficie). NO cambies comportamiento.

## Verificación OBLIGATORIA
```
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y TODOS los tests en verde.

## Convenciones
- Comentarios en español. NO hagas commit, push ni bump.

## Entrega (resumen final obligatorio)
1. Tests añadidos (archivo + nº de casos nuevos).
2. Resultado de jvmTest (nº total de tests).
3. Huecos que NO pudiste cubrir en jvmTest y por qué.
