/**
 * `reconcileMissingTaskPoints` — ver sección 2.5 del diseño. Red de
 * seguridad para registros de `taskHistory` LEGACY (creados antes de migrar
 * a Cloud Functions) con `pointsApplied == false`: las 4 funciones
 * transaccionales de este mismo paquete ya escriben siempre
 * `pointsApplied: true` de forma atómica, así que este mecanismo no debería
 * crecer para compleciones nuevas — si crece, es señal de un bug, no de una
 * carrera esperada (ver KDoc de `FirestoreRepository.reconcileMissingTaskPoints`).
 *
 * Alcance de este port: solo la variante `onSchedule` (cada 6h) — la
 * sección 2.5 del diseño marca la variante Callable ("forzar una pasada
 * manual desde un futuro panel de admin") como "opcionalmente", y el
 * encargo de esta fase cuenta "5 funciones" en total contando esta una sola
 * vez; se deja la variante manual para cuando exista ese panel de admin.
 *
 * A diferencia de `FirestoreRepository.reconcileTaskPoints` (cliente), NO
 * recalcula desde cero compleciones que no tengan NINGÚN registro de
 * `taskHistory` (ese caso era un síntoma de las escrituras HTTP secuenciales
 * sin transacción que este mismo proyecto elimina — las 4 funciones
 * transaccionales siempre crean su `taskHistory` en la misma transacción que
 * el resto de efectos, así que no puede haber una compleción NUEVA sin
 * historial). Solo repara registros que SÍ existen pero quedaron con
 * `pointsApplied == false` (datos legacy previos a esta migración).
 */
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { db, REGION } from "./admin.js";
import { clampTotalPoints } from "./points.js";
import { MemberDoc, TaskHistoryDoc } from "./types.js";

/** Aplica los puntos pendientes de un lote de registros `pointsApplied == false`. Exportado para test. */
export async function reconcileMissingTaskPoints(): Promise<number> {
  const snap = await db.collectionGroup("taskHistory").where("pointsApplied", "==", false).get();
  let reconciled = 0;
  for (const doc of snap.docs) {
    const householdRef = doc.ref.parent.parent;
    if (!householdRef) continue;
    try {
      await db.runTransaction(async (tx) => {
        const freshSnap = await tx.get(doc.ref);
        if (!freshSnap.exists) return;
        const fresh = freshSnap.data() as TaskHistoryDoc;
        if (fresh.pointsApplied) return; // otra pasada ya lo reconcilió
        const memberRef = householdRef.collection("members").doc(fresh.memberId);
        const memberSnap = await tx.get(memberRef);
        if (memberSnap.exists) {
          // Panel v17 (hallazgo CRÍTICO de seguridad): clamp en vez de
          // FieldValue.increment ciego — ver KDoc de [clampTotalPoints].
          const member = memberSnap.data() as MemberDoc;
          tx.update(memberRef, { totalPoints: clampTotalPoints(member.totalPoints, fresh.points) });
        }
        tx.update(doc.ref, { pointsApplied: true });
      });
      reconciled++;
    } catch (e) {
      // Best-effort: un fallo reparando un registro no aborta el resto, pero
      // panel v17 (hallazgo de programador senior): antes se tragaba sin
      // ningún log — un fallo sistemático de esta "red de seguridad" podía
      // pasar desapercibido indefinidamente (0 excepciones visibles en cada
      // pasada de 6h). Se loguea con el path del documento para poder
      // investigar/alertar.
      logger.error("reconcileMissingTaskPoints: fallo reparando registro", { docPath: doc.ref.path, error: e });
    }
  }
  return reconciled;
}

export const reconcileMissingTaskPointsScheduled = onSchedule(
  { region: REGION, schedule: "every 6 hours" },
  async () => {
    await reconcileMissingTaskPoints();
  }
);
