import { crc32, deflateSync } from 'node:zlib';

/**
 * A product photo for the Lighthouse run (T-E6-02), drawn here so no binary file lives in the repository: a soft
 * gradient with grain, about as hard to compress as a real photo, so the WebP files libvips makes from it weigh
 * what a shop's photos weigh. Portrait 3:4, the shape of the catalog's photos. Deterministic for a given seed.
 */
export function photoPng(seed: number, width = 1200, height = 1600): Buffer {
  const rows: Buffer[] = [];
  let state = seed >>> 0 || 1;
  const grain = () => {
    // xorshift32: cheap, deterministic noise
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return ((state >>> 0) % 41) - 20;
  };
  for (let y = 0; y < height; y++) {
    const row = Buffer.alloc(1 + width * 3);
    // Filter type 0 (none) for every scanline
    row[0] = 0;
    for (let x = 0; x < width; x++) {
      const wave = Math.sin((x + seed * 37) / 90) * Math.cos((y - seed * 11) / 120);
      row[1 + x * 3] = clamp(200 + 40 * wave + grain());
      row[2 + x * 3] = clamp(150 + (y / height) * 70 + grain());
      row[3 + x * 3] = clamp(170 + (x / width) * 60 - 30 * wave + grain());
    }
    rows.push(row);
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  // 8 bits per channel, color type 2 (RGB), default compression, filter and interlace
  header.set([8, 2, 0, 0, 0], 8);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.concat(rows))),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function clamp(value: number): number {
  return Math.max(0, Math.min(255, Math.round(value)));
}

function chunk(type: string, data: Buffer): Buffer {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const typeAndData = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(typeAndData));
  return Buffer.concat([length, typeAndData, crc]);
}
