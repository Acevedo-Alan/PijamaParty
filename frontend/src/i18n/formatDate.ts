/** The shop's time zone (Santa Fe, Argentina) and 24-hour clock, so every device shows the same hour. */
const TIME_ZONE = 'America/Argentina/Buenos_Aires';

const time = new Intl.DateTimeFormat('es-AR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: TIME_ZONE,
});
// A four-digit year: "11/10/26" can be read as day/month/year or as year/month/day (T-E8-25).
const dateTime = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: TIME_ZONE,
});

const date = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: TIME_ZONE,
});

/** The day in Argentina time, e.g. "11/10/2026". */
export function formatDate(day: Date): string {
  return date.format(day);
}

/** Hours and minutes in Argentina time, e.g. "14:30". */
export function formatTime(date: Date): string {
  return time.format(date);
}

/** Day and time in Argentina time, e.g. "11/10/2026, 14:30". */
export function formatDateTime(date: Date): string {
  return dateTime.format(date);
}

const isoDay = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: TIME_ZONE });

/** The store's date (Argentina) at that instant, as yyyy-mm-dd: what a date field takes. */
export function storeDay(date: Date): string {
  return isoDay.format(date);
}

// A calendar date or month that the server already counted in Argentina time (UC-33): read as UTC so it never shifts.
const dayMonth = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const monthYear = new Intl.DateTimeFormat('es-AR', { month: 'short', year: 'numeric', timeZone: 'UTC' });

/** A calendar day, "2026-10-15" to "15 oct". */
export function formatDayMonth(isoDay: string): string {
  return dayMonth.format(new Date(`${isoDay}T00:00:00Z`));
}

/** A calendar month, "2026-10" to "oct 2026". */
export function formatMonthYear(isoMonth: string): string {
  return monthYear.format(new Date(`${isoMonth}-01T00:00:00Z`));
}
