import { webcrypto } from 'node:crypto';

import { afterEach, describe, expect, it, vi } from 'vitest';

const runtime = vi.hoisted(() => ({ create: vi.fn() }));

vi.mock('onnxruntime-web', () => ({
  Tensor: class Tensor {
    constructor(
      readonly type: string,
      readonly data: Float32Array,
      readonly dimensions: readonly number[],
    ) {}
  },
  InferenceSession: { create: runtime.create },
}));

import { suggestCrop } from './autoCrop';
import type { ModelManifest } from './modelManifest';

const manifest: ModelManifest = {
  version: '1.0.0',
  provider: 'heuristic',
  modelUrl: null,
  sha256: null,
  input: { width: 224, height: 224, layout: 'NCHW', normalization: 'zero-to-one' },
  output: { format: 'xywh-normalized', aspectRatio: 1 },
};

describe('suggestCrop', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    runtime.create.mockReset();
  });

  it('returns a bounded, editable crop for local image data', async () => {
    const pixels = new Uint8ClampedArray(40 * 20 * 4);
    for (let index = 0; index < pixels.length; index += 4) {
      pixels[index] = index % 255;
      pixels[index + 1] = 120;
      pixels[index + 2] = 220;
      pixels[index + 3] = 255;
    }
    const suggestion = await suggestCrop(new ImageData(pixels, 40, 20), manifest);
    expect(suggestion.provider).toBe('heuristic');
    expect(suggestion.confidence).toBeGreaterThanOrEqual(0.2);
    expect(suggestion.crop.x + suggestion.crop.width).toBeLessThanOrEqual(1);
    expect(suggestion.crop.y + suggestion.crop.height).toBeLessThanOrEqual(1);
  });

  it('falls back to the local heuristic when a configured model cannot be fetched', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })));
    const image = new ImageData(new Uint8ClampedArray(4 * 4 * 4).fill(120), 4, 4);
    const suggestion = await suggestCrop(image, {
      ...manifest,
      provider: 'onnx',
      modelUrl: '/models/unavailable.onnx',
      sha256: '0'.repeat(64),
    });
    expect(suggestion.provider).toBe('heuristic');
  });

  it('verifies and executes a bounded ONNX prediction', async () => {
    const model = new Uint8Array([1, 2, 3]);
    const digest = await webcrypto.subtle.digest('SHA-256', model);
    const sha256 = [...new Uint8Array(digest)]
      .map((byte) => byte.toString(16).padStart(2, '0'))
      .join('');
    vi.stubGlobal('crypto', webcrypto);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(model, {
          status: 200,
          headers: { 'content-length': String(model.byteLength) },
        }),
      ),
    );
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      putImageData: vi.fn(),
      drawImage: vi.fn(),
      getImageData: vi.fn().mockReturnValue({ data: new Uint8ClampedArray(2 * 2 * 4).fill(128) }),
    } as unknown as CanvasRenderingContext2D);
    runtime.create.mockResolvedValue({
      inputNames: ['input'],
      outputNames: ['output'],
      run: vi.fn().mockResolvedValue({
        output: { data: new Float32Array([0.1, 0.2, 0.5, 0.6, 0.9]) },
      }),
    });

    const suggestion = await suggestCrop(
      new ImageData(new Uint8ClampedArray(2 * 2 * 4).fill(100), 2, 2),
      {
        ...manifest,
        provider: 'onnx',
        modelUrl: '/models/crop-v1.onnx',
        sha256,
        input: { ...manifest.input, width: 2, height: 2 },
      },
    );

    expect(suggestion.provider).toBe('onnx');
    expect(suggestion.confidence).toBeCloseTo(0.9);
    expect(suggestion.crop.x).toBeCloseTo(0.1);
    expect(suggestion.crop.y).toBeCloseTo(0.2);
    expect(suggestion.crop.width).toBeCloseTo(0.5);
    expect(suggestion.crop.height).toBeCloseTo(0.6);
    expect(runtime.create).toHaveBeenCalledOnce();
  });
});
