export interface ModelManifest {
  readonly version: string;
  readonly provider: 'heuristic' | 'onnx';
  readonly modelUrl: string | null;
  readonly sha256: string | null;
  readonly input: {
    readonly width: number;
    readonly height: number;
    readonly layout: 'NCHW' | 'NHWC';
    readonly normalization: 'zero-to-one';
  };
  readonly output: {
    readonly format: 'xywh-normalized';
    readonly aspectRatio: number;
  };
}

function isSafeModelUrl(value: string): boolean {
  try {
    const base = globalThis.location?.origin ?? 'https://local.invalid';
    const url = new URL(value, base);
    return url.protocol === 'https:' || (url.protocol === 'http:' && url.origin === base);
  } catch {
    return false;
  }
}

function isManifest(value: unknown): value is ModelManifest {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Record<string, unknown>;
  const input = candidate.input as Record<string, unknown> | undefined;
  const output = candidate.output as Record<string, unknown> | undefined;
  const isPositive = (number: unknown, maximum = Number.MAX_SAFE_INTEGER) =>
    typeof number === 'number' && Number.isFinite(number) && number > 0 && number <= maximum;
  const isSha256 = (checksum: unknown) =>
    typeof checksum === 'string' && /^[a-f\d]{64}$/i.test(checksum);
  return (
    typeof candidate.version === 'string' &&
    candidate.version.length > 0 &&
    candidate.version.length <= 64 &&
    (candidate.provider === 'heuristic' || candidate.provider === 'onnx') &&
    (typeof candidate.modelUrl === 'string' || candidate.modelUrl === null) &&
    (typeof candidate.sha256 === 'string' || candidate.sha256 === null) &&
    !!input &&
    Number.isSafeInteger(input.width) &&
    isPositive(input.width, 2_048) &&
    Number.isSafeInteger(input.height) &&
    isPositive(input.height, 2_048) &&
    (input.layout === 'NCHW' || input.layout === 'NHWC') &&
    input.normalization === 'zero-to-one' &&
    !!output &&
    output.format === 'xywh-normalized' &&
    isPositive(output.aspectRatio, 10) &&
    ((candidate.provider === 'heuristic' &&
      candidate.modelUrl === null &&
      candidate.sha256 === null) ||
      (candidate.provider === 'onnx' &&
        typeof candidate.modelUrl === 'string' &&
        candidate.modelUrl.length <= 2_048 &&
        isSafeModelUrl(candidate.modelUrl) &&
        isSha256(candidate.sha256)))
  );
}

export async function fetchModelManifest(signal?: AbortSignal): Promise<ModelManifest> {
  const url = import.meta.env.VITE_MODEL_MANIFEST_URL ?? '/models/model-manifest.json';
  const response = await fetch(url, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok)
    throw new Error(`Model manifest request failed with status ${response.status}.`);
  const value: unknown = await response.json();
  if (!isManifest(value)) throw new Error('Model manifest has an unsupported shape.');
  return value;
}
