/**
 * Port a TypeScript de `composeApp/src/commonMain/kotlin/org/taskhub/network/PenaltyRules.kt`
 * — reglas puras (sin I/O) de puntuación al completar una tarea. Usado por
 * `completeRecurringTask`/`completeAssignment`/`reconcileMissingTaskPoints`.
 *
 * REGLA DE ORO: los números/umbrales/lógica deben ser IDÉNTICOS al `.kt`
 * original.
 */

import { CompletionOutcome } from "./types.js";

/** Subconjunto de `TaskResponse` que necesita esta lógica de penalización. */
export interface TaskForPenalty {
  points: number;
  penaltyMode: string | null;
  penaltyValue: number;
  penaltyInterval: string;
  penaltyMax: number;
}

/**
 * Calcula si se completó a tiempo + los puntos a otorgar (con penalización
 * por retraso si toca) — ver KDoc de `PenaltyRules.resolveCompletionOutcome`.
 *
 * `dueDate === 0` significa "sin fecha límite" y nunca penaliza.
 */
export function resolveCompletionOutcome(task: TaskForPenalty, dueDate: number, now: number): CompletionOutcome {
  const onTime = dueDate === 0 || now <= dueDate;
  const pointsAwarded = onTime ? task.points : Math.max(task.points - calculatePenalty(task, dueDate, now), 0);
  return { onTime, pointsAwarded };
}

/**
 * Calcula los puntos de penalización de una tarea vencida — ver KDoc de
 * `PenaltyRules.calculatePenalty`.
 *
 * - modo `fixed`: resta `penaltyValue` por cada intervalo vencido.
 * - modo `percentage`: resta `penaltyValue`% de `task.points` por cada intervalo vencido.
 * - el resultado se limita a `penaltyMax` (si está fijado) y nunca supera `task.points`.
 */
export function calculatePenalty(task: TaskForPenalty, dueDate: number, now: number): number {
  const mode = task.penaltyMode;
  if (mode === null || mode === undefined) return 0;
  if (now <= dueDate) return 0;

  const overdueMs = now - dueDate;
  const intervalMs =
    task.penaltyInterval === "week"
      ? 7 * 24 * 60 * 60 * 1000
      : task.penaltyInterval === "month"
        ? 30 * 24 * 60 * 60 * 1000
        : 24 * 60 * 60 * 1000; // día

  // +1 porque el primer intervalo de penalización empieza en cuanto se
  // vence, no hay que esperar a que transcurra un intervalo completo.
  const intervals = Math.floor(overdueMs / intervalMs) + 1;

  let penalty: number;
  switch (mode) {
    case "fixed":
      penalty = task.penaltyValue * intervals;
      break;
    case "percentage":
      penalty = Math.floor((task.points * task.penaltyValue * intervals) / 100);
      break;
    default:
      penalty = 0;
  }

  const capped = task.penaltyMax > 0 ? Math.min(penalty, task.penaltyMax) : penalty;
  return Math.min(capped, task.points);
}
