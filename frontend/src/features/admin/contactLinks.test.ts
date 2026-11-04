import { describe, expect, it } from 'vitest';
import { mailLink, whatsAppLink } from './contactLinks.ts';

describe('whatsAppLink', () => {
  it('opens the chat of a 10-digit Argentine number with 549 in front', () => {
    expect(whatsAppLink('3425551234')).toBe('https://wa.me/5493425551234');
  });

  it('drops a leading 0, a 54 or a 549 already typed', () => {
    expect(whatsAppLink('03425551234')).toBe('https://wa.me/5493425551234');
    expect(whatsAppLink('543425551234')).toBe('https://wa.me/5493425551234');
    expect(whatsAppLink('5493425551234')).toBe('https://wa.me/5493425551234');
  });

  it('gives no link for a number it cannot read for sure', () => {
    expect(whatsAppLink('42555123')).toBeNull();
    expect(whatsAppLink('342155551234')).toBeNull();
    expect(whatsAppLink('0034255512')).toBeNull();
    expect(whatsAppLink('342-555-1234')).toBeNull();
  });
});

describe('mailLink', () => {
  it('opens a new message to the email', () => {
    expect(mailLink('ana@example.com')).toBe('mailto:ana@example.com');
  });

  it('encodes what would add recipients, a subject or a body to the draft', () => {
    expect(mailLink('x?cc=otro%40mal.com&body=hola@example.com')).toBe(
      'mailto:x%3Fcc%3Dotro%2540mal.com%26body%3Dhola@example.com',
    );
  });
});
