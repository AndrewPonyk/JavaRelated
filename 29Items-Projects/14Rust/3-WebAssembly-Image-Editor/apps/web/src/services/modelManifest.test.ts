import { describe, expect, it, vi } from 'vitest';

import { fetchModelManifest } from './modelManifest';

describe('fetchModelManifest', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('accepts a bounded heuristic manifest', async () => {
    const manifest = {
      version: '2.0.0',
      provider: 'heuristic',
      modelUrl: null,
      sha256: null,
      input: { width: 224, height: 224, layout: 'NCHW', normalization: 'zero-to-one' },
      output: { format: 'xywh-normalized', aspectRatio: 1 },
    };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(manifest), { status: 200 })),
    );
    await expect(fetchModelManifest()).resolves.toEqual(manifest);
  });

  it('rejects a malformed manifest', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ provider: 'heuristic' }), { status: 200 }),
        ),
    );
    await expect(fetchModelManifest()).rejects.toThrow(/unsupported shape/i);
  });

  it('reports a non-successful manifest response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })));
    await expect(fetchModelManifest()).rejects.toThrow(/503/);
  });

  it('rejects unsafe ONNX metadata', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            version: '1',
            provider: 'onnx',
            modelUrl: '/model.onnx',
            sha256: 'not-a-checksum',
            input: { width: 10_000, height: 224, layout: 'NCHW', normalization: 'zero-to-one' },
            output: { format: 'xywh-normalized', aspectRatio: 1 },
          }),
          { status: 200 },
        ),
      ),
    );
    await expect(fetchModelManifest()).rejects.toThrow(/unsupported shape/i);
  });
});
