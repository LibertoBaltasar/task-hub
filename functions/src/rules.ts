/**
 * Port a TypeScript de `composeApp/src/commonMain/kotlin/org/taskhub/network/RecurrenceRules.kt`
 * — SOLO el subconjunto que necesitan las Cloud Functions de completar/
 * reasignar/deshacer (ver `docs/recurrencia-backend-cloud-functions-diseno-2026-09-11.md`,
 * sección 2.6): `isDueToday`/`isDueOn` NO se portan, siguen siendo
 * responsabilidad exclusiva del cliente (no escriben nada, no hay problema
 * de atomicidad que resolver aquí).
 *
 * REGLA DE ORO de este port: los números/umbrales/lógica deben ser
 * IDÉNTICOS al `.kt` original. Cualquier función de aquí debe poder
 * cotejarse línea a línea con su equivalente Kotlin.
 *
 * Divergencia respecto al cliente, documentada en el informe de la fase 2a:
 * el Kotlin usa `TimeZone.currentSystemDefault()` (zona del DISPOSITIVO del
 * usuario) como default; un Cloud Function no tiene "zona del dispositivo"
 * — se usa [DEFAULT_TZ] como default explícito en su lugar.
 */

import { AssignmentSlot, NextAssignmentDecision } from "./types.js";
import {
  LocalDate,
  addDays,
  addMonthsYM,
  compareLocalDate,
  daysInMonth,
  dayOfWeekIso,
  epochToLocalDate,
  localMidnightToEpoch
} from "./dates.js";

/** Zona horaria usada como default cuando el caller no pasa una explícita (ver KDoc de arriba). */
export const DEFAULT_TZ = "Europe/Madrid";

/** Forma mínima de `TaskAssignmentResponse` que necesita `resolveNextAssignmentDecision`. */
export interface AssignmentForDecision {
  memberId: string;
  dueDate: number;
  status: string;
}

/**
 * Ajusta `day` (1..31) al último día válido de `year`-`month` si el mes no
 * llega a tener ese día — ver KDoc de `RecurrenceRules.clampDayOfMonth`.
 */
export function clampDayOfMonth(day: number, year: number, month: number): number {
  const lastDayOfMonth = daysInMonth(year, month);
  return Math.min(Math.max(day, 1), lastDayOfMonth);
}

/** Día programado (de `recurrenceDays`) más reciente en `<= date` (a lo sumo 7 días atrás). Privada en Kotlin — sin caller aquí (solo la usa `isDueOn`, no portada), se mantiene por fidelidad. */
function mostRecentWeeklyOccurrence(date: LocalDate, recurrenceDays: number[]): LocalDate {
  let candidate = date;
  for (let i = 0; i < 7; i++) {
    const dow = dayOfWeekIso(candidate);
    if (recurrenceDays.includes(dow)) return candidate;
    candidate = addDays(candidate, -1);
  }
  return date; // no debería ocurrir con recurrenceDays válidos (1..7)
}

/**
 * Próxima ocurrencia estrictamente posterior a `nowEpochMs`, a las 00:00
 * hora local del día resultante — ver KDoc de `RecurrenceRules.nextOccurrence`.
 */
export function nextOccurrence(
  nowEpochMs: number,
  frequency: string,
  day: number | null = null,
  weeklyDays: number[] = [],
  tz: string = DEFAULT_TZ
): number {
  const today = epochToLocalDate(nowEpochMs, tz);
  const todayDow = dayOfWeekIso(today);

  let targetDate: LocalDate;
  switch (frequency) {
    case "daily":
      targetDate = addDays(today, 1);
      break;
    case "weekly": {
      if (weeklyDays.length > 0) {
        let candidate = addDays(today, 1);
        let found: LocalDate | null = null;
        let safety = 0;
        while (found === null && safety < 14) {
          const dow = dayOfWeekIso(candidate);
          if (weeklyDays.includes(dow)) found = candidate;
          candidate = addDays(candidate, 1);
          safety++;
        }
        targetDate = found ?? addDays(today, 7); // no debería ocurrir (rango cubre 2 semanas)
      } else {
        const targetDow = day ?? todayDow;
        const currentDow = todayDow;
        let diff = targetDow - currentDow;
        if (diff <= 0) diff += 7;
        targetDate = addDays(today, diff);
      }
      break;
    }
    case "monthly": {
      const targetDay = day ?? today.day;
      const thisMonthCandidate: LocalDate = {
        year: today.year,
        month: today.month,
        day: clampDayOfMonth(targetDay, today.year, today.month)
      };
      if (compareLocalDate(thisMonthCandidate, today) > 0) {
        targetDate = thisMonthCandidate;
      } else {
        const nextMonth = addMonthsYM(today.year, today.month, 1);
        targetDate = {
          year: nextMonth.year,
          month: nextMonth.month,
          day: clampDayOfMonth(targetDay, nextMonth.year, nextMonth.month)
        };
      }
      break;
    }
    default:
      targetDate = today;
  }

  return localMidnightToEpoch(targetDate, tz);
}

/**
 * Convierte la medianoche del día programado (que devuelve [nextOccurrence])
 * en la fecha límite REAL a efectos de penalizar retrasos: medianoche del
 * día SIGUIENTE — ver KDoc de `RecurrenceRules.endOfDueDay`.
 */
export function endOfDueDay(dueDayStartMs: number, tz: string = DEFAULT_TZ): number {
  const date = epochToLocalDate(dueDayStartMs, tz);
  const nextDay = addDays(date, 1);
  return localMidnightToEpoch(nextDay, tz);
}

/**
 * Miembro que debe ocupar la siguiente asignación de una tarea recurrente —
 * ver KDoc de `RecurrenceRules.resolveRotationAssignee`.
 */
export function resolveRotationAssignee(
  assignmentRotation: AssignmentSlot[],
  nextDueMs: number,
  fallbackMemberId: string,
  tz: string = DEFAULT_TZ
): string {
  if (assignmentRotation.length === 0) return fallbackMemberId;
  const dow = dayOfWeekIso(epochToLocalDate(nextDueMs, tz));
  const slot = assignmentRotation.find((s) => s.dayOfWeek === dow);
  return slot?.memberId ?? fallbackMemberId;
}

/**
 * Decide quién debe quedar asignado a la siguiente ocurrencia de una tarea
 * recurrente y si hace falta crear esa asignación — ver KDoc de
 * `RecurrenceRules.resolveNextAssignmentDecision`.
 */
export function resolveNextAssignmentDecision(
  assignmentRotation: AssignmentSlot[],
  nextDueDate: number,
  completedMemberId: string,
  existingAssignments: AssignmentForDecision[],
  tz: string = DEFAULT_TZ
): NextAssignmentDecision {
  const nextMemberId = resolveRotationAssignee(assignmentRotation, nextDueDate, completedMemberId, tz);
  const alreadyExists = existingAssignments.some(
    (a) => a.memberId === nextMemberId && a.dueDate === nextDueDate && a.status === "assigned"
  );
  return { shouldCreate: !alreadyExists, memberId: nextMemberId };
}

/**
 * Purga las referencias a `memberId` de una `assignmentRotation` — ver KDoc
 * de `RecurrenceRules.purgeMemberFromRotation`.
 */
export function purgeMemberFromRotation(rotation: AssignmentSlot[], memberId: string): AssignmentSlot[] {
  return rotation.filter((slot) => slot.memberId !== memberId);
}

// Referencia interna para que `mostRecentWeeklyOccurrence` (portada por
// fidelidad, ver KDoc arriba) no quede completamente huérfana en build tools
// que sí marcan funciones de módulo no exportadas como muertas.
export const __internal = { mostRecentWeeklyOccurrence };
