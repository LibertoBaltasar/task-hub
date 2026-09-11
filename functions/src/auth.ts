/**
 * Validación de seguridad compartida por las funciones callable — ver
 * sección 2 del diseño de fase 2a: `context.auth != null` + membresía
 * activa (`leftAt == 0`, equivalente a `isMember(hid)` en `firestore.rules`)
 * + para `reassignTaskCompletion`, rol owner/admin del llamador
 * (`isTrusted(hid)`).
 *
 * El Admin SDK ignora `firestore.rules` por completo (ver sección 5 del
 * diseño): toda esta validación vive aquí, en código de la función, no en
 * las reglas.
 */
import type { Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./admin.js";
import { MemberDoc } from "./types.js";

/** `context.auth != null` — sin esto no hay `uid` que validar contra el hogar. */
export function requireAuth(uid: string | undefined): string {
  if (!uid) throw new HttpsError("unauthenticated", "auth-required");
  return uid;
}

/** Lee `households/{householdId}/members/{memberId}` dentro de la transacción y exige que sea un miembro activo (`leftAt == 0`). */
export async function loadActiveMember(tx: Transaction, householdId: string, memberId: string): Promise<MemberDoc> {
  const snap = await tx.get(db.doc(`households/${householdId}/members/${memberId}`));
  if (!snap.exists) throw new HttpsError("not-found", "member-not-found");
  const data = snap.data() as MemberDoc;
  if ((data.leftAt ?? 0) !== 0) throw new HttpsError("permission-denied", "member-not-active");
  return data;
}

/** `isOwner(hid) || isAdminMember(hid)` — ver `firestore.rules`. */
export async function requireTrusted(tx: Transaction, householdId: string, uid: string): Promise<void> {
  const householdSnap = await tx.get(db.doc(`households/${householdId}`));
  if (!householdSnap.exists) throw new HttpsError("not-found", "household-not-found");
  const ownerId = householdSnap.data()?.ownerId as string | undefined;
  if (ownerId === uid) return;
  const member = await loadActiveMember(tx, householdId, uid);
  if (member.role === "admin") return;
  throw new HttpsError("permission-denied", "not-trusted");
}
