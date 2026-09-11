/**
 * Aritmética de fechas de calendario (sin hora) + conversión a/desde epoch
 * millis en una zona horaria IANA explícita — equivalente mínimo de
 * `kotlinx.datetime.LocalDate`/`Instant`/`TimeZone` usado por
 * `RecurrenceRules.kt`, implementado sobre `Intl.DateTimeFormat` (sin
 * dependencias nuevas: Node 20 trae datos de zonas horarias completos vía
 * ICU, a diferencia del cliente KMP donde kotlinx-datetime SÍ hace falta).
 */

export interface LocalDate {
  year: number;
  month: number; // 1-12
  day: number;
}

/** Último día real de `year`-`month` (1-12). */
export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

/** Suma (o resta, si negativo) `delta` días de calendario a `date`. */
export function addDays(date: LocalDate, delta: number): LocalDate {
  const ms = Date.UTC(date.year, date.month - 1, date.day) + delta * 86_400_000;
  const d = new Date(ms);
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1, day: d.getUTCDate() };
}

/** Suma (o resta) `delta` meses de calendario, sin ajustar el día (ver KDoc de uso en rules.ts). */
export function addMonthsYM(year: number, month: number, delta: number): { year: number; month: number } {
  const totalMonths0 = year * 12 + (month - 1) + delta;
  const newYear = Math.floor(totalMonths0 / 12);
  const newMonth = ((totalMonths0 % 12) + 12) % 12 + 1;
  return { year: newYear, month: newMonth };
}

/** -1 si a < b, 0 si iguales, 1 si a > b (orden de calendario). */
export function compareLocalDate(a: LocalDate, b: LocalDate): number {
  if (a.year !== b.year) return a.year < b.year ? -1 : 1;
  if (a.month !== b.month) return a.month < b.month ? -1 : 1;
  if (a.day !== b.day) return a.day < b.day ? -1 : 1;
  return 0;
}

/** Día de la semana ISO: 1=Lunes..7=Domingo (independiente de zona horaria: es aritmética de calendario pura). */
export function dayOfWeekIso(date: LocalDate): number {
  const utcDow = new Date(Date.UTC(date.year, date.month - 1, date.day)).getUTCDay(); // 0=Domingo..6=Sábado
  return utcDow === 0 ? 7 : utcDow;
}

interface WallClockParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function wallClockPartsAt(epochMs: number, tz: string): WallClockParts {
  const dtf = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  });
  const parts: Record<string, string> = {};
  for (const p of dtf.formatToParts(new Date(epochMs))) {
    if (p.type !== "literal") parts[p.type] = p.value;
  }
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour === "24" ? "0" : parts.hour),
    minute: Number(parts.minute),
    second: Number(parts.second)
  };
}

/** Fecha de calendario (sin hora) que corresponde a `epochMs` en la zona horaria `tz`. */
export function epochToLocalDate(epochMs: number, tz: string): LocalDate {
  const p = wallClockPartsAt(epochMs, tz);
  return { year: p.year, month: p.month, day: p.day };
}

/** Desplazamiento (ms) entre "lo que marca el reloj de pared en `tz`" y `epochMs`, tratado como si fuera UTC. */
function wallClockOffsetMs(epochMs: number, tz: string): number {
  const p = wallClockPartsAt(epochMs, tz);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return asUtc - epochMs;
}

/**
 * Epoch millis del instante cuya hora de reloj de pared en `tz` es
 * exactamente year-month-day hour:minute:second — equivalente a
 * `LocalDateTime(...).toInstant(tz).toEpochMilliseconds()`. Dos iteraciones
 * (igual que `date-fns-tz`) para resolver correctamente cerca de un cambio
 * de horario de verano/invierno.
 */
export function localDateTimeToEpoch(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  second: number,
  tz: string
): number {
  const guess = Date.UTC(year, month - 1, day, hour, minute, second);
  const offset1 = wallClockOffsetMs(guess, tz);
  const epoch1 = guess - offset1;
  const offset2 = wallClockOffsetMs(epoch1, tz);
  return offset2 === offset1 ? epoch1 : guess - offset2;
}

/** Medianoche (00:00:00 hora local) de `date` en `tz`, como epoch millis. */
export function localMidnightToEpoch(date: LocalDate, tz: string): number {
  return localDateTimeToEpoch(date.year, date.month, date.day, 0, 0, 0, tz);
}
