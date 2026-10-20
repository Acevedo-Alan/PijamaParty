/**
 * The business's contact and social links (D-09), shown in "Contactanos" (UC-30) and the footer (UC-31). The business
 * has not sent them yet (D-15): until each one is filled in here, its link is not shown. Changing them needs a deploy.
 */
export interface StoreContact {
  email?: string;
  /** In international format, digits only, as wa.me links take it: 549 + area code + number, e.g. 5493424000000. */
  whatsAppNumber?: string;
  instagramUrl?: string;
  tiktokUrl?: string;
}

export const STORE_CONTACT: StoreContact = {};

/** The chat with the business in WhatsApp, on the phone app or on the web. */
export function whatsAppUrl(number: string): string {
  return `https://wa.me/${number}`;
}
