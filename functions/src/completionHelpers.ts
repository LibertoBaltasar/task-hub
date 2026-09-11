/**
 * Helpers compartidos por `completeRecurringTask`/`completeAssignment`/
 * `reconcileMissingTaskPoints` — port de los métodos `private` equivalentes
 * de `FirestoreRepository.kt` (`calculateNextDueDate`, y el cálculo de
 * `effectiveDueDate` que aparece inline en `completeTask`/`completeAssignment`).
 */
import { TaskDoc } from "./types.js";
import { nextOccurrence, endOfDueDay, DEFAULT_TZ } from "./rules.js";

const RECURRING_FREQUENCIES = new Set(["daily", "weekly", "monthly"]);

/** Port de `FirestoreRepository.calculateNextDueDate`. `null` para tareas "once" o frecuencias desconocidas. */
export function calculateNextDueDate(
  task: Pick<TaskDoc, "frequency" | "recurrenceDay" | "recurrenceDays">,
  afterMs: number,
  tz: string = DEFAULT_TZ
): number | null {
  if (!RECURRING_FREQUENCIES.has(task.frequency)) return null;
  return nextOccurrence(afterMs, task.frequency, task.recurrenceDay, task.recurrenceDays, tz);
}

/** Port del cálculo inline de `effectiveDueDate` en `FirestoreRepository.completeTask`. */
export function effectiveDueDateForTask(
  task: Pick<TaskDoc, "frequency" | "dueDate" | "nextDueAt">,
  tz: string = DEFAULT_TZ
): number {
  if (task.frequency === "once") return task.dueDate;
  return task.nextDueAt != null ? endOfDueDay(task.nextDueAt, tz) : task.dueDate;
}

/** Port del cálculo inline de `effectiveDueDate` en `FirestoreRepository.completeAssignment`. */
export function effectiveDueDateForAssignment(
  task: Pick<TaskDoc, "frequency">,
  assignmentDueDate: number,
  tz: string = DEFAULT_TZ
): number {
  if (task.frequency === "once") return assignmentDueDate;
  if (assignmentDueDate === 0) return 0;
  return endOfDueDay(assignmentDueDate, tz);
}
