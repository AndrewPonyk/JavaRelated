import { afterEach, describe, expect, it, vi } from 'vitest';

import { decodeImage, exportCanvas, ImageInputError } from './canvas';

describe('canvas image boundaries', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('rejects unsupported and empty input files before decoding', async () => {
    await expect(
      decodeImage(new File(['text'], 'notes.txt', { type: 'text/plain' })),
    ).rejects.toBeInstanceOf(ImageInputError);
    await expect(decodeImage(new File([], 'empty.png', { type: 'image/png' }))).rejects.toThrow(
      /non-empty/i,
    );
  });

  it('decodes a valid bitmap and always closes the browser resource', async () => {
    const close = vi.fn();
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: 2, height: 1, close }));
    const imageData = new ImageData(new Uint8ClampedArray(8), 2, 1);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(),
      getImageData: vi.fn().mockReturnValue(imageData),
    } as unknown as CanvasRenderingContext2D);

    const decoded = await decodeImage(new File(['png'], 'safe.png', { type: 'image/png' }));
    expect(decoded.imageData).toBe(imageData);
    expect(decoded.metadata).toMatchObject({ fileName: 'safe.png', width: 2, height: 1 });
    expect(close).toHaveBeenCalledOnce();
  });

  it('validates export settings before allocating an output canvas', async () => {
    const canvas = document.createElement('canvas');
    canvas.width = 10;
    canvas.height = 10;
    await expect(
      exportCanvas(canvas, { mimeType: 'image/webp', quality: 2, maxWidth: null }),
    ).rejects.toThrow(/quality/i);
    await expect(
      exportCanvas(canvas, { mimeType: 'image/png', quality: 1, maxWidth: 0 }),
    ).rejects.toThrow(/width/i);
    await expect(
      exportCanvas(canvas, {
        mimeType: 'image/gif' as 'image/png',
        quality: 1,
        maxWidth: null,
      }),
    ).rejects.toThrow(/format/i);
  });

  it('scales and encodes a valid export', async () => {
    const source = document.createElement('canvas');
    source.width = 100;
    source.height = 50;
    const drawImage = vi.fn();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage,
    } as unknown as CanvasRenderingContext2D);
    const blob = new Blob(['image'], { type: 'image/webp' });
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) =>
      callback(blob),
    );

    await expect(
      exportCanvas(source, { mimeType: 'image/webp', quality: 0.8, maxWidth: 50 }),
    ).resolves.toBe(blob);
    expect(drawImage).toHaveBeenCalledWith(source, 0, 0, 50, 25);
  });
});
