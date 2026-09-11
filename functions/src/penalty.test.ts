/**
 * Port de `composeApp/src/commonTest/kotlin/org/taskhub/network/PenaltyRulesTest.kt`
 * — MISMOS casos, mismos valores, en TS (ver sección 4 del diseño de fase 2a:
 * mitigación contra que la duplicación Kotlin/TS diverja en silencio).
 */
import { calculatePenalty, resolveCompletionOutcome, TaskForPenalty } from "./penalty.js";

const dayMs = 24 * 60 * 60 * 1000;

function task(overrides: Partial<TaskForPenalty> = {}): TaskForPenalty {
  return {
    points: 100,
    penaltyMode: null,
    penaltyValue: 0,
    penaltyInterval: "day",
    penaltyMax: 0,
    ...overrides
  };
}

describe("resolveCompletionOutcome", () => {
  test("noDueDate_isOnTimeWithFullPoints", () => {
    const outcome = resolveCompletionOutcome(task({ points: 50 }), 0, Number.MAX_SAFE_INTEGER);
    expect(outcome.onTime).toBe(true);
    expect(outcome.pointsAwarded).toBe(50);
  });

  test("completedExactlyAtDueDate_isOnTime", () => {
    const outcome = resolveCompletionOutcome(task({ points: 50 }), 1000, 1000);
    expect(outcome.onTime).toBe(true);
    expect(outcome.pointsAwarded).toBe(50);
  });

  test("completedAfterDueDate_isLate", () => {
    const outcome = resolveCompletionOutcome(task({ points: 50 }), 1000, 1001);
    expect(outcome.onTime).toBe(false);
  });

  test("lateWithoutPenaltyMode_awardsFullPointsButNotOnTime", () => {
    const outcome = resolveCompletionOutcome(task({ points: 50, penaltyMode: null }), 1000, 1000 + dayMs);
    expect(outcome.onTime).toBe(false);
    expect(outcome.pointsAwarded).toBe(50);
  });

  test("lateWithFixedPenalty_subtractsFromPoints", () => {
    const outcome = resolveCompletionOutcome(
      task({ points: 100, penaltyMode: "fixed", penaltyValue: 10, penaltyInterval: "day" }),
      1000,
      1000 + dayMs / 2
    );
    expect(outcome.pointsAwarded).toBe(90);
  });

  test("pointsNeverGoBelowZero", () => {
    const outcome = resolveCompletionOutcome(
      task({ points: 5, penaltyMode: "fixed", penaltyValue: 1000, penaltyInterval: "day" }),
      1000,
      1000 + dayMs
    );
    expect(outcome.pointsAwarded).toBe(0);
  });
});

describe("calculatePenalty", () => {
  test("noPenaltyMode_isZero", () => {
    expect(calculatePenalty(task({ penaltyMode: null }), 1000, 1000 + dayMs)).toBe(0);
  });

  test("notYetOverdue_isZero", () => {
    const overdueTask = task({ penaltyMode: "fixed", penaltyValue: 10 });
    expect(calculatePenalty(overdueTask, 1000, 500)).toBe(0);
  });

  test("fixedMode_multipleDayIntervals", () => {
    // 2.5 días tarde -> 3 intervalos de día (redondeo hacia arriba).
    const overdueTask = task({ points: 100, penaltyMode: "fixed", penaltyValue: 10, penaltyInterval: "day" });
    const overdueBy2AndHalfDays = 1000 + 2 * dayMs + dayMs / 2;
    expect(calculatePenalty(overdueTask, 1000, overdueBy2AndHalfDays)).toBe(30);
  });

  test("percentageMode_computesPercentOfPointsPerInterval", () => {
    // 10 días tarde con intervalo semanal -> 2 intervalos -> 2*20% = 40% de 100 = 40
    const overdueTask = task({ points: 100, penaltyMode: "percentage", penaltyValue: 20, penaltyInterval: "week" });
    expect(calculatePenalty(overdueTask, 0, 10 * dayMs)).toBe(40);
  });

  test("monthInterval_usesThirtyDayIntervals", () => {
    const overdueTask = task({ points: 100, penaltyMode: "fixed", penaltyValue: 5, penaltyInterval: "month" });
    // 35 días tarde -> 2 intervalos de 30 días -> -10
    expect(calculatePenalty(overdueTask, 0, 35 * dayMs)).toBe(10);
  });

  test("cappedAtPenaltyMax", () => {
    const overdueTask = task({ points: 100, penaltyMode: "fixed", penaltyValue: 50, penaltyInterval: "day", penaltyMax: 80 });
    const overdueBy2AndHalfDays = 2 * dayMs + dayMs / 2;
    expect(calculatePenalty(overdueTask, 0, overdueBy2AndHalfDays)).toBe(80);
  });

  test("zeroPenaltyMax_meansUncapped", () => {
    const overdueTask = task({ points: 1000, penaltyMode: "fixed", penaltyValue: 50, penaltyInterval: "day", penaltyMax: 0 });
    const overdueBy2AndHalfDays = 2 * dayMs + dayMs / 2;
    expect(calculatePenalty(overdueTask, 0, overdueBy2AndHalfDays)).toBe(150);
  });

  test("neverExceedsTaskPoints", () => {
    const overdueTask = task({ points: 20, penaltyMode: "fixed", penaltyValue: 1000, penaltyInterval: "day" });
    expect(calculatePenalty(overdueTask, 0, dayMs)).toBe(20);
  });
});
