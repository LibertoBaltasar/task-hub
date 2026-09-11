/**
 * `completeRecurringTask` — ver sección 2.1 y el diagrama de transacción de
 * la sección 4 de `docs/recurrencia-backend-cloud-functions-diseno-2026-09-11.md`.
 *
 * Reemplaza `FirestoreRepository.completeTask` (secuencia de N llamadas
 * REST) por una única `runTransaction`: lee tarea + miembro + asignaciones
 * del ciclo, valida, calcula el resultado con las MISMAS reglas puras que el
 * cliente (`rules.ts`/`penalty.ts`, portadas de Kotlin) y escribe todo junto
 * o nada.
 *
 * `now` se lee UNA vez fuera de `runTransaction` y se pasa como parámetro
 * puro (sección 8 del diseño: evita efectos no deterministas entre
 * reintentos automáticos de la transacción).
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db, REGION } from "./admin.js";
import { requireAuth, loadActiveMember } from "./auth.js";
import { resolveCompletionOutcome } from "./penalty.js";
import { resolveNextAssignmentDecision } from "./rules.js";
import { calculateNextDueDate, effectiveDueDateForTask } from "./completionHelpers.js";
import { TaskAssignmentDoc, TaskDoc } from "./types.js";

export interface CompleteRecurringTaskRequest {
  householdId: string;
  taskId: string;
  memberId: string;
  /**
   * Concurrencia optimista opcional: `lastCompletedDate` que el caller tenía
   * cargado antes de completar. Si no coincide con el valor fresco leído en
   * la transacción, es que otro dispositivo ya completó/modificó la tarea
   * entretanto -> HttpsError('aborted').
   *
   * Nota de fidelidad con el diseño: la sección 2.1 nombra este campo
   * `expectedUpdateTime` (string RFC3339, heredado del mecanismo REST
   * `currentDocument.updateTime` que usa hoy el cliente), pero el diagrama
   * de transacción de la sección 4 compara literalmente
   * `expectedLastCompletedDate` contra `task.lastCompletedDate` (un long).
   * Dentro de una `runTransaction` real ya tenemos el valor de campo fresco
   * a mano (no hace falta ir a buscar el `updateTime` del documento), así
   * que se implementa la sección 4 tal cual — ver informe de la fase 2a.
   */
  expectedLastCompletedDate?: number | null;
}

export interface CompleteRecurringTaskResponse {
  completedAt: number;
  pointsAwarded: number;
  onTime: boolean;
  nextDueAt: number | null;
}

export const completeRecurringTask = onCall<CompleteRecurringTaskRequest, Promise<CompleteRecurringTaskResponse>>(
  { region: REGION },
  async (request) => {
    const uid = requireAuth(request.auth?.uid);
    const { householdId, taskId, memberId, expectedLastCompletedDate } = request.data;
    if (!householdId || !taskId || !memberId) {
      throw new HttpsError("invalid-argument", "householdId/taskId/memberId son obligatorios");
    }
    const now = Date.now();

    return db.runTransaction(async (tx) => {
      const taskRef = db.doc(`households/${householdId}/tasks/${taskId}`);
      const taskSnap = await tx.get(taskRef);
      if (!taskSnap.exists) throw new HttpsError("not-found", "task-not-found");
      const task = taskSnap.data() as TaskDoc;

      // Validación de seguridad (sección 2.1): llamador miembro activo +
      // memberId (quien recibe los puntos) también miembro activo del mismo hogar.
      await loadActiveMember(tx, householdId, uid);
      await loadActiveMember(tx, householdId, memberId);
      const memberRef = db.doc(`households/${householdId}/members/${memberId}`);

      const assignmentsSnap = await tx.get(
        db.collection(`households/${householdId}/tasks/${taskId}/assignments`).where("status", "==", "assigned")
      );

      if (
        expectedLastCompletedDate !== undefined &&
        expectedLastCompletedDate !== null &&
        (task.lastCompletedDate ?? null) !== expectedLastCompletedDate
      ) {
        throw new HttpsError("aborted", "conflict");
      }

      const effectiveDueDate = effectiveDueDateForTask(task);
      const outcome = resolveCompletionOutcome(task, effectiveDueDate, now);
      const nextDueDate = calculateNextDueDate(task, now);

      const assignedThisCycle = assignmentsSnap.docs.map((d) => ({ ref: d.ref, data: d.data() as TaskAssignmentDoc }));
      const existingAssignmentForMember = assignedThisCycle.find((a) => a.data.memberId === memberId);

      // ── Escritura (todo o nada) ──
      const taskUpdate: Record<string, unknown> = { lastCompletedDate: now, completedBy: memberId };
      if (task.frequency !== "once") taskUpdate.nextDueAt = nextDueDate;
      tx.update(taskRef, taskUpdate);

      tx.update(memberRef, { totalPoints: FieldValue.increment(outcome.pointsAwarded) });

      const historyRef = db.collection(`households/${householdId}/taskHistory`).doc();
      tx.set(historyRef, {
        taskId,
        memberId,
        points: outcome.pointsAwarded,
        completedAt: now,
        onTime: outcome.onTime,
        pointsApplied: true
      });

      for (const a of assignedThisCycle) {
        tx.update(a.ref, {
          status: "completed",
          completedAt: now,
          pointsAwarded: a.data.memberId === memberId ? outcome.pointsAwarded : 0,
          onTime: outcome.onTime
        });
      }

      if (task.frequency !== "once" && nextDueDate !== null) {
        const decision = resolveNextAssignmentDecision(
          task.assignmentRotation ?? [],
          nextDueDate,
          memberId,
          assignedThisCycle.map((a) => a.data)
        );
        if (decision.shouldCreate) {
          const nextRef = db.doc(`households/${householdId}/tasks/${taskId}/assignments/next_${taskId}_${nextDueDate}`);
          tx.create(nextRef, {
            taskId,
            memberId: decision.memberId,
            mandatory: existingAssignmentForMember?.data.mandatory ?? false,
            dueDate: nextDueDate,
            status: "assigned",
            assignedAt: now
          });
        }
      }

      return {
        completedAt: now,
        pointsAwarded: outcome.pointsAwarded,
        onTime: outcome.onTime,
        nextDueAt: task.frequency !== "once" ? nextDueDate : null
      };
    });
  }
);
