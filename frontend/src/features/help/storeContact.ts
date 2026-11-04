import type { StoreContact } from './storeContactApi.ts';

/** The chat with the business in WhatsApp, on the phone app or on the web. */
export function whatsAppUrl(number: string): string {
  return `https://wa.me/${number}`;
}

/** The address on Google Maps, in the browser or the app (T-E7-26). */
export function mapsUrl(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
}

/** Whether the home page shows "Contactanos" (UC-30): only with the email or the WhatsApp number. */
export function hasContactSection(contact: StoreContact): boolean {
  return contact.email !== undefined || contact.whatsAppNumber !== undefined;
}
