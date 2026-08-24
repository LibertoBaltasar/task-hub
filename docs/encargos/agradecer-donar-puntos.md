---
workdir: /home/liberto/task-hub
---
# Encargo: agradecer (puntos semanales) + donar puntos entre miembros

## Objetivo
Dos formas nuevas de mover puntos entre miembros de un espacio:

1. **Agradecer** — un miembro "regala" puntos a otro **acuñándolos** (no se
   descuentan del que agradece), con un tope semanal de **50 puntos que cada
   miembro puede DAR**. No se puede agradecer a uno mismo.
2. **Donar** — transferencia real de puntos del saldo del donante al receptor
   (se resta al donante y se suma al receptor). No se puede donar a uno mismo
   ni más de lo que se tiene.

## Contexto (LEER antes de tocar nada)
- `MemberResponse.totalPoints` = saldo actual del miembro (DTO en
  `network/models/DTOs.kt`).
- `FirestoreRepository.addMemberPoints(householdId, memberId, delta)` (líneas
  ~831-850) ya suma/resta un delta al `totalPoints`. Reutilizarlo como primitiva.
- `getMembers(householdId)` lista miembros. `resolveCurrentMember(householdId)`
  devuelve el id del miembro actual.
- Repo: `network/FirestoreRepository.kt`; i18n: `ui/i18n/AppStrings.kt` (ES+EN);
  pantalla de espacio: `ui/screens/HouseholdScreen.kt` + `ui/models/HouseholdScreenModel.kt`;
  ranking: `ui/screens/RankingScreen.kt` (muestra miembros + puntos).
- Solo `material-icons-core`. Iconos espejados → `Icons.AutoMirrored.Filled.*`.

## Reglas de negocio (CRÍTICAS — leer con calma)

### Agradecer
- Cada miembro tiene un presupuesto de **50 puntos por semana** para DAR.
- "Semana" = lunes 00:00 local → domingo 23:59. Guardar el epoch millis del
  lunes de la semana actual como `weekStart`.
- Prohibido agradecerse a sí mismo.
- Al agradecer `amount`:
  1. Si `now >= weekStart + 7 días` → reiniciar contador de esa semana: dar por
     gastado 0 y `weekStart` = lunes de la semana de `now`.
  2. `remaining = 50 - appreciationGiven`; exigir `1 <= amount <= remaining`.
  3. Acuñar: `addMemberPoints(receptor, +amount)` — **sin** tocar el saldo del
     que agradece.
  4. Actualizar en el miembro que agradece: `appreciationGiven += amount` y
     `appreciationWeekStart = weekStart` (PATCH con `updateMask.fieldPaths`).
- El receptor no puede ser uno mismo, así que con N miembros un miembro puede
  recibir como máximo (N−1) × 50 por semana.

### Donar
- Transferencia: `addMemberPoints(receptor, +amount)` y
  `addMemberPoints(donante, -amount)`.
- Prohibido donarse a sí mismo.
- No donar más del saldo actual del donante (leer `totalPoints` antes y validar).
- Sin tope semanal (es transferencia, no acuñación).

### Persistencia del presupuesto semanal
Añadir a `MemberResponse` (y al documento Firestore del miembro) dos campos:
`appreciationGiven: Int = 0` y `appreciationWeekStart: Long = 0`. Actualizarlos
con PATCH como hace `addMemberPoints` / `updateMemberStreak` (updateMask).

## Trabajo
1. **DTO**: añadir a `MemberResponse` los campos `appreciationGiven` y
   `appreciationWeekStart` (con default; no rompe el parseo por
   `ignoreUnknownKeys`/defaults).
2. **Repo** en `FirestoreRepository.kt`:
   - `appreciateMember(householdId, fromMemberId, toMemberId, amount): AppreciateResult`
     — valida no-self y tope semanal (con reinicio de semana), acuña puntos y
     actualiza el presupuesto. Devuelve un resultado tipado (Ok / Error con
     mensaje i18n-friendly: "límite semanal alcanzado", "no puedes agradecerte a
     ti mismo", "importe inválido", etc.) en vez de lanzar excepciones.
   - `donatePoints(householdId, fromMemberId, toMemberId, amount): DonateResult`
     — valida no-self y saldo suficiente; transfiere.
3. **Modelo** en `HouseholdScreenModel.kt` (o modelo nuevo si es más limpio):
   estado + acciones `appreciate` / `donate` con feedback de resultado
   (éxito / error traducido).
4. **UI**: en la lista de miembros (HouseholdScreen / RankingScreen), acciones
   "👍 Agradecer" y "🎁 Donar" por miembro, con diálogo de importe:
   - Agradecer: mostrar presupuesto restante de la semana.
   - Donar: mostrar saldo actual del donante.
   - Ocultar/deshabilitar las acciones sobre uno mismo.
5. **i18n**: claves para todo (agradecer, donar, importe, presupuesto restante,
   límite semanal, saldo insuficiente, no puedes hacerlo contigo mismo, etc.)
   en ES + EN.

## Definición de hecho
- `cd ~/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain`
  termina en BUILD SUCCESSFUL.
- Agradecer respeta el tope de 50/semana/miembro y no se puede a uno mismo.
- Donar transfiere y no permite saldo negativo ni donarse a sí mismo.
- La UI muestra presupuesto restante (agradecer) y saldo (donar).

## Entrega
- Hacer el trabajo y compilar. NO commit, push ni bump (lo hace el orquestador).
- Resumir: archivos tocados, dónde se persiste el presupuesto semanal y
  resultado de la compilación.
