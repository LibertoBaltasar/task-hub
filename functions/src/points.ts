/**
 * Tope de seguridad para `members/{mid}.totalPoints` — MISMO valor que
 * `firestore.rules` (`members/{mid}.update`, "D10"). Las 4 Cloud Functions
 * que mueven `totalPoints` usan el Admin SDK y por tanto SALTAN
 * `firestore.rules` por completo (ver cabecera de `firestore.rules`, v9):
 * sin este clamp, una tarea con `points` absurdamente alto (o negativo,
 * para drenar a otro miembro) completada vía la función callable no
 * respetaba ningún tope — hallazgo CRÍTICO del panel de expertos v17
 * (2026-09-24, especialista de seguridad/AppSec).
 */
export const MAX_TOTAL_POINTS = 100000;

/** `current + delta`, acotado a `[0, MAX_TOTAL_POINTS]`. */
export function clampTotalPoints(current: number, delta: number): number {
  return Math.min(Math.max(current + delta, 0), MAX_TOTAL_POINTS);
}
