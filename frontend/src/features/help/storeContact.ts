/**
 * The business's contact and social links (D-09), shown in "Contactanos" (UC-30) and the footer (UC-31). The business
 * has sent only the WhatsApp number so far (D-15): until each other one is filled in here, its link is not shown.
 * Changing them needs a deploy.
 */
export interface StoreContact {
  email?: string;
  /** In international format, digits only, as wa.me links take it: 549 + area code + number, e.g. 5493424000000. */
  whatsAppNumber?: string;
  /** As it should read, e.g. "Lunes a viernes de 9 a 18 h" (T-E7-26). */
  openingHours?: string;
  /** As it should read and as Google Maps finds it, e.g. "San Martín 2000, Santa Fe" (T-E7-26). */
  address?: string;
  instagramUrl?: string;
  tiktokUrl?: string;
}

export const STORE_CONTACT: StoreContact = {
  whatsAppNumber: '5493425304507',
};

/** The chat with the business in WhatsApp, on the phone app or on the web. */
export function whatsAppUrl(number: string): string {
  return `https://wa.me/${number}`;
}

/** The address on Google Maps, in the browser or the app (T-E7-26). */
export function mapsUrl(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
}

/** Whether the home page shows "Contactanos" (UC-30): only with the email or the WhatsApp number. */
export function hasContactSection(): boolean {
  return STORE_CONTACT.email !== undefined || STORE_CONTACT.whatsAppNumber !== undefined;
}
