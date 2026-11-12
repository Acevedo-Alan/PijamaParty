/**
 * Outline drawings in the header's style for the account's navigation: a house, a parcel, a pin, a person's card, a
 * person and a door. The account area's sidebar (T-E7-60) and the header's account list (T-E7-65) share them; this
 * module imports no fonts or styles, so the header's chunk stays as it was. All decorative: each link's text names it.
 */
export const accountNavIcons = {
  summary: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 11 12 4.5l8 6.5M6.5 9.5V19h11V9.5" />
    </svg>
  ),
  orders: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 8 12 4l8 4v8l-8 4-8-4V8Zm0 0 8 4 8-4M12 12v8" />
    </svg>
  ),
  addresses: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </svg>
  ),
  data: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3.5" y="5" width="17" height="14" rx="2" />
      <circle cx="9" cy="11" r="2" />
      <path d="M6 16c.6-1.6 1.7-2.4 3-2.4s2.4.8 3 2.4M14.5 10h3M14.5 13h3" />
    </svg>
  ),
  person: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8.5" r="3.5" />
      <path d="M5 19.5c1.2-3.3 3.8-5 7-5s5.8 1.7 7 5" />
    </svg>
  ),
  signOut: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 5H6v14h4M14 8l4 4-4 4M18 12H9" />
    </svg>
  ),
};
