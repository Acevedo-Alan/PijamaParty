/**
 * UC-34 (decided on 2026-11-04): the WhatsApp chat of a customer's phone, as saved (digits only, UC-19). An Argentine
 * mobile is 549 followed by the 10 digits of the area code and the number; a leading 0 (trunk prefix) or a 54 country
 * code already typed is dropped. Anything that does not reduce to those 10 digits gets no link: guessing a wrong
 * number would open a chat with a stranger.
 */
export function whatsAppLink(phone: string): string | null {
  if (!/^\d+$/.test(phone)) {
    return null;
  }
  let digits = phone;
  if (digits.length === 13 && digits.startsWith('549')) {
    digits = digits.slice(3);
  } else if (digits.length === 12 && digits.startsWith('54')) {
    digits = digits.slice(2);
  } else if (digits.length === 11 && digits.startsWith('0')) {
    digits = digits.slice(1);
  }
  return digits.length === 10 && !digits.startsWith('0') ? `https://wa.me/549${digits}` : null;
}

/**
 * The mail app with a new message to the customer, and only to them. Each side of the @ is percent-encoded: a
 * registered address may hold `?`, `&` or `%` in its local part, and unencoded they would add a cc, a subject or a body
 * of the customer's choosing to the owner's draft.
 */
export function mailLink(email: string): string {
  const at = email.lastIndexOf('@');
  return `mailto:${encodeURIComponent(email.slice(0, at))}@${encodeURIComponent(email.slice(at + 1))}`;
}
