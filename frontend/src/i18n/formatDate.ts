/** The shop's time zone (Santa Fe, Argentina) and 24-hour clock, so every device shows the same hour. */
const TIME_ZONE = 'America/Argentina/Buenos_Aires';

const time = new Intl.DateTimeFormat('es-AR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: TIME_ZONE,
});
const dateTime = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'short',
  timeStyle: 'short',
  hourCycle: 'h23',
  timeZone: TIME_ZONE,
});

const date = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeZone: TIME_ZONE });

/** The day in Argentina time, e.g. "11/10/26". */
export function formatDate(day: Date): string {
  return date.format(day);
}

/** Hours and minutes in Argentina time, e.g. "14:30". */
export function formatTime(date: Date): string {
  return time.format(date);
}

/** Day and time in Argentina time, e.g. "11/10/26, 14:30". */
export function formatDateTime(date: Date): string {
  return dateTime.format(date);
}
