import { createHmac } from 'node:crypto';

/** RFC 6238 as the back office checks it (RfcTotpVerifier): HMAC-SHA1, 6 digits, 30-second steps. */
export const TOTP_PERIOD_SECONDS = 30;

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function base32Decode(secret: string): Buffer {
  const clean = secret.replaceAll(/[\s=]/g, '').toUpperCase();
  let bits = '';
  for (const character of clean) {
    const value = BASE32.indexOf(character);
    if (value < 0) {
      throw new Error('The authenticator secret is not base32');
    }
    bits += value.toString(2).padStart(5, '0');
  }
  const bytes: number[] = [];
  for (let index = 0; index + 8 <= bits.length; index += 8) {
    bytes.push(parseInt(bits.slice(index, index + 8), 2));
  }
  return Buffer.from(bytes);
}

/** The code of the time step that contains {@code at}, as an authenticator app shows it. */
export function totpCode(secret: string, at: Date): string {
  const step = Math.floor(at.getTime() / 1000 / TOTP_PERIOD_SECONDS);
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = createHmac('sha1', base32Decode(secret)).update(counter).digest();
  const offset = (hmac[hmac.length - 1] as number) & 0x0f;
  const binary = hmac.readUInt32BE(offset) & 0x7fffffff;
  return String(binary % 1_000_000).padStart(6, '0');
}
