const ARGENTINA_MONTH = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Argentina/Buenos_Aires', month: 'numeric' });

/**
 * T-E7-56: whether the cherry blossoms fall on the home: spring in Argentina, from 1 September to 30 November, by the
 * date in Argentina whatever the device's time zone. The dates are fixed here; changing them is a change to the store.
 */
export function isSakuraSeason(now: Date): boolean {
  const month = Number(ARGENTINA_MONTH.format(now));
  return month >= 9 && month <= 11;
}
