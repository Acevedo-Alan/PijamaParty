/** The longest side a photo keeps when the device shrinks it (UC-06, RNF-26). */
export const MAX_SIDE = 1600;
/** JPEG quality of a shrunk photo. */
export const JPEG_QUALITY = 0.85;

export interface Size {
  width: number;
  height: number;
}

/** The size that fits within {@link MAX_SIDE} keeping the proportions, or null when it already fits. */
export function fitWithin({ width, height }: Size, maxSide = MAX_SIDE): Size | null {
  const longest = Math.max(width, height);
  if (longest <= maxSide) {
    return null;
  }
  const scale = maxSide / longest;
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

function toBlob(canvas: HTMLCanvasElement, type: string): Promise<Blob | null> {
  return new Promise((resolve) => {
    canvas.toBlob(resolve, type, JPEG_QUALITY);
  });
}

/**
 * Shrinks a photo on the device before it is uploaded: at most 1600 px on its longest side, with its EXIF orientation
 * applied, as JPEG at quality 0.85 (PNG stays PNG). A photo that already fits, or that the browser cannot decode or
 * draw, comes back as it is and the server decides about it.
 */
export async function shrinkPhoto(file: File): Promise<Blob> {
  if (typeof createImageBitmap !== 'function') {
    return file;
  }
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    return file;
  }
  try {
    const target = fitWithin(bitmap);
    if (target === null) {
      return file;
    }
    const canvas = document.createElement('canvas');
    canvas.width = target.width;
    canvas.height = target.height;
    const context = canvas.getContext('2d');
    if (context === null) {
      return file;
    }
    context.imageSmoothingQuality = 'high';
    context.drawImage(bitmap, 0, 0, target.width, target.height);
    const shrunk = await toBlob(canvas, file.type === 'image/png' ? 'image/png' : 'image/jpeg');
    return shrunk ?? file;
  } finally {
    bitmap.close();
  }
}

/** The file name sent with a photo: the original one, with the extension of a JPEG it became. */
export function uploadName(file: File, sent: Blob): string {
  if (sent === file || sent.type !== 'image/jpeg' || /\.jpe?g$/i.test(file.name)) {
    return file.name;
  }
  return `${file.name.replace(/\.[^.]*$/, '')}.jpg`;
}
