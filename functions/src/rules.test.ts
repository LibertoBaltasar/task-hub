/**
 * Port de `composeApp/src/commonTest/kotlin/org/taskhub/network/RecurrenceRulesTest.kt`
 * — MISMOS casos, mismos valores, en TS, para el subconjunto de funciones
 * portadas en `rules.ts` (ver sección 2.6 del diseño: `isDueToday`/`isDueOn`/
 * `isOverdueOccurrence` NO se portan, así que sus casos de test tampoco).
 */
import { LocalDate, localDateTimeToEpoch, epochToLocalDate } from "./dates.js";
import {
  clampDayOfMonth,
  nextOccurrence,
  endOfDueDay,
  resolveRotationAssignee,
  resolveNextAssignmentDecision,
  purgeMemberFromRotation,
  AssignmentForDecision
} from "./rules.js";
import { AssignmentSlot } from "./types.js";

const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;

function epochOf(year: number, month: number, day: number, hour = 12, minute = 0): number {
  return localDateTimeToEpoch(year, month, day, hour, minute, 0, tz);
}

function dateOf(epochMs: number): LocalDate {
  return epochToLocalDate(epochMs, tz);
}

function expectDate(actual: LocalDate, expected: LocalDate): void {
  expect(actual).toEqual(expected);
}

// ── clampDayOfMonth ──────────────────────────────────────────

describe("clampDayOfMonth", () => {
  test("dayFitsInMonth_isUnchanged", () => {
    expect(clampDayOfMonth(15, 2024, 1)).toBe(15);
  });

  test("day31InApril_clampsTo30", () => {
    expect(clampDayOfMonth(31, 2024, 4)).toBe(30);
  });

  test("day31InFebruaryLeapYear_clampsTo29", () => {
    expect(clampDayOfMonth(31, 2024, 2)).toBe(29);
  });

  test("day31InFebruaryNonLeapYear_clampsTo28", () => {
    expect(clampDayOfMonth(31, 2023, 2)).toBe(28);
  });

  test("day29InFebruaryLeapYear_isUnchanged", () => {
    expect(clampDayOfMonth(29, 2024, 2)).toBe(29);
  });

  test("day29InFebruaryNonLeapYear_clampsTo28", () => {
    expect(clampDayOfMonth(29, 2023, 2)).toBe(28);
  });

  test("day30InFebruary_clampsToLastDayOfMonth", () => {
    expect(clampDayOfMonth(30, 2024, 2)).toBe(29);
    expect(clampDayOfMonth(30, 2023, 2)).toBe(28);
  });

  test("day30InThirtyDayMonth_isUnchanged", () => {
    expect(clampDayOfMonth(30, 2024, 4)).toBe(30);
  });

  test("day29InThirtyOneDayMonth_isUnchanged", () => {
    expect(clampDayOfMonth(29, 2024, 1)).toBe(29);
  });
});

// ── nextOccurrence: daily ────────────────────────────────────

describe("nextOccurrence daily", () => {
  test("isTomorrow", () => {
    const now = epochOf(2024, 3, 15);
    const next = nextOccurrence(now, "daily", null, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 16 });
  });
});

// ── nextOccurrence: weekly ───────────────────────────────────

describe("nextOccurrence weekly", () => {
  test("todayIsTargetDay_jumpsForwardSevenDays", () => {
    // 2024-03-15 es viernes (dow=5)
    const now = epochOf(2024, 3, 15);
    const next = nextOccurrence(now, "weekly", 5, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 22 });
  });

  test("targetDayLaterThisWeek_usesThatDay", () => {
    const now = epochOf(2024, 3, 15);
    const next = nextOccurrence(now, "weekly", 1, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 18 });
  });

  test("targetDayEarlierInWeek_wrapsToNextWeek", () => {
    const now = epochOf(2024, 3, 18);
    const next = nextOccurrence(now, "weekly", 7, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 24 });
  });

  test("multipleDays_picksEarliestUpcoming", () => {
    const now = epochOf(2024, 3, 15);
    const next = nextOccurrence(now, "weekly", null, [1, 3], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 18 });
  });

  test("multipleDays_todayIsOneOfThem_skipsToNextMatch", () => {
    const now = epochOf(2024, 3, 15);
    const next = nextOccurrence(now, "weekly", null, [1, 5], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 18 });
  });

  test("emptyDays_jumpsForwardSevenDays", () => {
    const now = epochOf(2024, 3, 15);
    const next = nextOccurrence(now, "weekly", null, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 22 });
  });
});

// ── nextOccurrence: monthly ──────────────────────────────────

describe("nextOccurrence monthly", () => {
  test("dayLaterThisMonth_staysInSameMonth", () => {
    const now = epochOf(2024, 3, 10);
    const next = nextOccurrence(now, "monthly", 20, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 3, day: 20 });
  });

  test("dayAlreadyPassed_jumpsToNextMonth", () => {
    const now = epochOf(2024, 3, 20);
    const next = nextOccurrence(now, "monthly", 10, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 4, day: 10 });
  });

  test("dayIsToday_jumpsToNextMonth", () => {
    const now = epochOf(2024, 3, 20);
    const next = nextOccurrence(now, "monthly", 20, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 4, day: 20 });
  });

  test("shortMonth_clampsToLastDay", () => {
    const now = epochOf(2024, 2, 1);
    const next = nextOccurrence(now, "monthly", 31, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 2, day: 29 });
  });

  test("dayPassedInShortMonth_jumpsToNextMonthClamped", () => {
    const now = epochOf(2024, 4, 30);
    const next = nextOccurrence(now, "monthly", 31, [], tz);
    expectDate(dateOf(next), { year: 2024, month: 5, day: 31 });
  });
});

// ── endOfDueDay ──────────────────────────────────────────────

describe("endOfDueDay", () => {
  test("isMidnightOfTheFollowingDay", () => {
    const dueDayStart = localDateTimeToEpoch(2024, 3, 18, 0, 0, 0, tz);
    const end = endOfDueDay(dueDayStart, tz);
    expectDate(dateOf(end), { year: 2024, month: 3, day: 19 });
  });

  test("completionLaterTheSameScheduledDay_isBeforeEnd", () => {
    const dueDayStart = localDateTimeToEpoch(2024, 3, 18, 0, 0, 0, tz);
    const completedSameDayEvening = epochOf(2024, 3, 18, 20);
    const end = endOfDueDay(dueDayStart, tz);
    expect(completedSameDayEvening <= end).toBe(true);
  });

  test("completionNextDay_isAfterEnd", () => {
    const dueDayStart = localDateTimeToEpoch(2024, 3, 18, 0, 0, 0, tz);
    const completedNextDay = epochOf(2024, 3, 19, 1);
    const end = endOfDueDay(dueDayStart, tz);
    expect(completedNextDay > end).toBe(true);
  });

  test("lastDayOfMonth_crossesIntoNextMonth", () => {
    const dueDayStart = localDateTimeToEpoch(2024, 3, 31, 0, 0, 0, tz);
    const end = endOfDueDay(dueDayStart, tz);
    expectDate(dateOf(end), { year: 2024, month: 4, day: 1 });
  });

  test("lastDayOfFebruaryLeapYear_crossesIntoMarch", () => {
    const dueDayStart = localDateTimeToEpoch(2024, 2, 29, 0, 0, 0, tz);
    const end = endOfDueDay(dueDayStart, tz);
    expectDate(dateOf(end), { year: 2024, month: 3, day: 1 });
  });

  test("lastDayOfYear_crossesIntoNextYear", () => {
    const dueDayStart = localDateTimeToEpoch(2024, 12, 31, 0, 0, 0, tz);
    const end = endOfDueDay(dueDayStart, tz);
    expectDate(dateOf(end), { year: 2025, month: 1, day: 1 });
  });

  // ── Timezone explícita distinta de currentSystemDefault() ─────
  test("explicitNonDefaultTz_usesThatZonesMidnight", () => {
    const tzFarEast = "Pacific/Kiritimati"; // UTC+14
    const dueDayStartInFarEast = localDateTimeToEpoch(2024, 3, 15, 0, 0, 0, tzFarEast);
    const end = endOfDueDay(dueDayStartInFarEast, tzFarEast);
    expectDate(epochToLocalDate(end, tzFarEast), { year: 2024, month: 3, day: 16 });
  });
});

// ── resolveRotationAssignee ────────────────────────────────────

describe("resolveRotationAssignee", () => {
  test("emptyRotation_returnsFallback", () => {
    const nextDue = epochOf(2024, 3, 18); // lunes
    expect(resolveRotationAssignee([], nextDue, "member-A", tz)).toBe("member-A");
  });

  test("matchingSlotForDayOfWeek_returnsThatMember", () => {
    // 2024-03-18 es lunes (dow=1)
    const nextDue = epochOf(2024, 3, 18);
    const rotation: AssignmentSlot[] = [
      { dayOfWeek: 1, memberId: "member-monday" },
      { dayOfWeek: 3, memberId: "member-wednesday" }
    ];
    expect(resolveRotationAssignee(rotation, nextDue, "member-A", tz)).toBe("member-monday");
  });

  test("noSlotForDayOfWeek_returnsFallback", () => {
    // 2024-03-15 es viernes (dow=5); rotación solo cubre lunes(1)
    const nextDue = epochOf(2024, 3, 15);
    const rotation: AssignmentSlot[] = [{ dayOfWeek: 1, memberId: "member-monday" }];
    expect(resolveRotationAssignee(rotation, nextDue, "member-A", tz)).toBe("member-A");
  });

  // ── Timezone explícita distinta de currentSystemDefault() ─────
  test("explicitTz_sameInstantResolvesDifferentSlot", () => {
    const tzFarEast = "Pacific/Kiritimati"; // UTC+14
    const tzFarWest = "Pacific/Pago_Pago"; // UTC-11
    // 2024-03-14T23:00:00Z -> viernes 15 en Kiritimati, jueves 14 en Pago Pago.
    const instant = Date.UTC(2024, 2, 14, 23, 0, 0);
    const rotation: AssignmentSlot[] = [
      { dayOfWeek: 4, memberId: "member-thursday" },
      { dayOfWeek: 5, memberId: "member-friday" }
    ];
    expect(resolveRotationAssignee(rotation, instant, "fallback", tzFarEast)).toBe("member-friday");
    expect(resolveRotationAssignee(rotation, instant, "fallback", tzFarWest)).toBe("member-thursday");
  });
});

// ── purgeMemberFromRotation ───────────────────────────────────

describe("purgeMemberFromRotation", () => {
  test("removesOnlyMatchingSlots", () => {
    const rotation: AssignmentSlot[] = [
      { dayOfWeek: 1, memberId: "member-A" },
      { dayOfWeek: 3, memberId: "member-B" },
      { dayOfWeek: 5, memberId: "member-A" }
    ];
    const purged = purgeMemberFromRotation(rotation, "member-A");
    expect(purged).toEqual([{ dayOfWeek: 3, memberId: "member-B" }]);
  });

  test("memberNotInRotation_returnsUnchanged", () => {
    const rotation: AssignmentSlot[] = [{ dayOfWeek: 1, memberId: "member-B" }];
    expect(purgeMemberFromRotation(rotation, "member-A")).toEqual(rotation);
  });

  test("emptyRotation_returnsEmpty", () => {
    expect(purgeMemberFromRotation([], "member-A")).toEqual([]);
  });
});

// ── resolveNextAssignmentDecision ──────────────────────────────

function assignment(memberId: string, dueDate: number, status = "assigned"): AssignmentForDecision {
  return { memberId, dueDate, status };
}

describe("resolveNextAssignmentDecision", () => {
  test("emptyRotation_usesFallbackAndCreates", () => {
    const decision = resolveNextAssignmentDecision([], 1000, "member-A", [], tz);
    expect(decision.shouldCreate).toBe(true);
    expect(decision.memberId).toBe("member-A");
  });

  test("matchingAssignedDuplicate_doesNotCreate", () => {
    const nextDue = 1000;
    const decision = resolveNextAssignmentDecision(
      [],
      nextDue,
      "member-A",
      [assignment("member-A", nextDue, "assigned")],
      tz
    );
    expect(decision.shouldCreate).toBe(false);
    expect(decision.memberId).toBe("member-A");
  });

  test("existingAssignmentWithDifferentDueDate_stillCreates", () => {
    const decision = resolveNextAssignmentDecision(
      [],
      2000,
      "member-A",
      [assignment("member-A", 1000, "assigned")],
      tz
    );
    expect(decision.shouldCreate).toBe(true);
  });

  test("existingAssignmentAlreadyCompleted_stillCreates", () => {
    const nextDue = 1000;
    const decision = resolveNextAssignmentDecision(
      [],
      nextDue,
      "member-A",
      [assignment("member-A", nextDue, "completed")],
      tz
    );
    expect(decision.shouldCreate).toBe(true);
  });

  test("rotationResolvesDifferentMember_dedupesAgainstThatMember", () => {
    // 2024-03-18 es lunes (dow=1) -> la rotación asigna a member-monday.
    const nextDue = epochOf(2024, 3, 18);
    const rotation: AssignmentSlot[] = [{ dayOfWeek: 1, memberId: "member-monday" }];
    const decision = resolveNextAssignmentDecision(
      rotation,
      nextDue,
      "member-A",
      [assignment("member-monday", nextDue, "assigned")],
      tz
    );
    expect(decision.memberId).toBe("member-monday");
    expect(decision.shouldCreate).toBe(false);
  });
});
