import type { ExportSettings, ImageMetadata } from '../types/image';

const SUPPORTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const DEFAULT_MAX_PIXELS = 40_000_000;
const DEFAULT_MAX_BYTES = 25_000_000;

export class ImageInputError extends Error {
  override readonly name = 'ImageInputError';
}

function configuredMaxPixels(): number {
  const parsed = Number(import.meta.env.VITE_MAX_IMAGE_PIXELS ?? DEFAULT_MAX_PIXELS);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : DEFAULT_MAX_PIXELS;
}

function configuredMaxBytes(): number {
  const parsed = Number(import.meta.env.VITE_MAX_IMAGE_BYTES ?? DEFAULT_MAX_BYTES);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : DEFAULT_MAX_BYTES;
}

export async function decodeImage(file: File): Promise<{
  imageData: ImageData;
  metadata: ImageMetadata;
}> {
  if (!SUPPORTED_TYPES.has(file.type)) {
    throw new ImageInputError('Choose a JPEG, PNG, or WebP image.');
  }
  if (file.size < 1 || file.size > configuredMaxBytes()) {
    throw new ImageInputError('Choose a non-empty image no larger than the configured file limit.');
  }

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch (cause) {
    throw new ImageInputError('The browser could not decode this image.', { cause });
  }

  try {
    if (bitmap.width < 1 || bitmap.height < 1) {
      throw new ImageInputError('The decoded image has invalid dimensions.');
    }
    const pixels = bitmap.width * bitmap.height;
    if (!Number.isSafeInteger(pixels) || pixels > configuredMaxPixels()) {
      throw new ImageInputError('This image is too large to process safely in this browser.');
    }

    const canvas = document.createElement('canvas');
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) {
      throw new ImageInputError('Canvas is unavailable in this browser.');
    }
    context.drawImage(bitmap, 0, 0);

    return {
      imageData: context.getImageData(0, 0, bitmap.width, bitmap.height),
      metadata: {
        fileName: file.name,
        mimeType: file.type,
        width: bitmap.width,
        height: bitmap.height,
      },
    };
  } finally {
    bitmap.close();
  }
}

export function renderImage(canvas: HTMLCanvasElement, imageData: ImageData): void {
  canvas.width = imageData.width;
  canvas.height = imageData.height;
  const context = canvas.getContext('2d');
  if (!context) {
    throw new ImageInputError('Canvas is unavailable in this browser.');
  }
  context.putImageData(imageData, 0, 0);
}

export async function exportCanvas(
  canvas: HTMLCanvasElement,
  settings: ExportSettings,
): Promise<Blob> {
  const { mimeType, quality, maxWidth } = settings;
  if (!SUPPORTED_TYPES.has(mimeType)) {
    throw new ImageInputError('Choose JPEG, PNG, or WebP as the export format.');
  }
  if (!Number.isFinite(quality) || quality < 0 || quality > 1) {
    throw new ImageInputError('Export quality must be between 0 and 1.');
  }

  if (maxWidth !== null && (!Number.isSafeInteger(maxWidth) || maxWidth < 1 || maxWidth > 20_000)) {
    throw new ImageInputError('Export width must be a whole number from 1 to 20,000 pixels.');
  }
  const scale = maxWidth === null || canvas.width <= maxWidth ? 1 : maxWidth / canvas.width;
  const output = document.createElement('canvas');
  output.width = Math.max(1, Math.round(canvas.width * scale));
  output.height = Math.max(1, Math.round(canvas.height * scale));
  const context = output.getContext('2d');
  if (!context) throw new ImageInputError('Canvas is unavailable in this browser.');
  context.drawImage(canvas, 0, 0, output.width, output.height);
  const blob = await new Promise<Blob | null>((resolve) =>
    output.toBlob(resolve, mimeType, quality),
  );
  if (!blob) {
    throw new ImageInputError('The browser could not encode the edited image.');
  }
  return blob;
}
