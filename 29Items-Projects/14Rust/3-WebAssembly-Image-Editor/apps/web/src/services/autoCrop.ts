import type * as Ort from 'onnxruntime-web';

import type { NormalizedCrop } from '../types/image';
import type { ModelManifest } from './modelManifest';

type OnnxRuntime = typeof Ort;
const MAX_MODEL_BYTES = 100_000_000;

export interface AutoCropSuggestion {
  readonly crop: NormalizedCrop;
  readonly confidence: number;
  readonly provider: ModelManifest['provider'];
  readonly modelVersion: string;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

function cropAround(centerX: number, centerY: number, aspectRatio: number): NormalizedCrop {
  let width = 0.82;
  let height = width / aspectRatio;
  if (height > 0.82) {
    height = 0.82;
    width = height * aspectRatio;
  }
  width = clamp(width, 0.1, 1);
  height = clamp(height, 0.1, 1);
  return {
    x: clamp(centerX - width / 2, 0, 1 - width),
    y: clamp(centerY - height / 2, 0, 1 - height),
    width,
    height,
  };
}

function heuristicSuggestion(imageData: ImageData, manifest: ModelManifest): AutoCropSuggestion {
  const stride = Math.max(1, Math.floor(Math.min(imageData.width, imageData.height) / 48));
  let totalWeight = 0;
  let weightedX = 0;
  let weightedY = 0;
  let samples = 0;
  let variance = 0;
  for (let y = 0; y < imageData.height; y += stride) {
    for (let x = 0; x < imageData.width; x += stride) {
      const offset = (y * imageData.width + x) * 4;
      const red = imageData.data[offset] ?? 0;
      const green = imageData.data[offset + 1] ?? 0;
      const blue = imageData.data[offset + 2] ?? 0;
      const luminance = (red * 0.299 + green * 0.587 + blue * 0.114) / 255;
      const chroma = (Math.max(red, green, blue) - Math.min(red, green, blue)) / 255;
      const weight = 0.05 + luminance * 0.45 + chroma * 0.5;
      totalWeight += weight;
      weightedX += x * weight;
      weightedY += y * weight;
      variance += Math.abs(luminance - 0.5);
      samples += 1;
    }
  }
  const centerX = totalWeight > 0 ? weightedX / totalWeight / imageData.width : 0.5;
  const centerY = totalWeight > 0 ? weightedY / totalWeight / imageData.height : 0.5;
  return {
    crop: cropAround(centerX, centerY, manifest.output.aspectRatio),
    confidence: clamp(samples > 0 ? (variance / samples) * 2 : 0, 0.2, 0.95),
    provider: 'heuristic',
    modelVersion: manifest.version,
  };
}

async function verifiedModelBytes(manifest: ModelManifest): Promise<Uint8Array> {
  if (!manifest.modelUrl || !manifest.sha256)
    throw new Error('ONNX model configuration is incomplete.');
  const response = await fetch(manifest.modelUrl, { cache: 'force-cache' });
  if (!response.ok)
    throw new Error(`Auto-crop model request failed with status ${response.status}.`);
  const declaredSize = Number(response.headers.get('content-length'));
  if (Number.isFinite(declaredSize) && declaredSize > MAX_MODEL_BYTES) {
    throw new Error('Auto-crop model exceeds the supported size limit.');
  }
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (bytes.byteLength > MAX_MODEL_BYTES) {
    throw new Error('Auto-crop model exceeds the supported size limit.');
  }
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const actual = [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
  if (actual !== manifest.sha256.toLowerCase())
    throw new Error('Auto-crop model checksum did not match its manifest.');
  return bytes;
}

function tensorFor(
  imageData: ImageData,
  manifest: ModelManifest,
  runtime: OnnxRuntime,
): Ort.Tensor {
  const canvas = document.createElement('canvas');
  canvas.width = manifest.input.width;
  canvas.height = manifest.input.height;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) throw new Error('Canvas is unavailable for auto-crop preprocessing.');
  const source = document.createElement('canvas');
  source.width = imageData.width;
  source.height = imageData.height;
  source.getContext('2d')?.putImageData(imageData, 0, 0);
  context.drawImage(source, 0, 0, canvas.width, canvas.height);
  const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
  const values = new Float32Array(manifest.input.width * manifest.input.height * 3);
  for (let y = 0; y < manifest.input.height; y += 1) {
    for (let x = 0; x < manifest.input.width; x += 1) {
      const sourceOffset = (y * manifest.input.width + x) * 4;
      const destination = y * manifest.input.width + x;
      const red = pixels[sourceOffset] ?? 0;
      const green = pixels[sourceOffset + 1] ?? 0;
      const blue = pixels[sourceOffset + 2] ?? 0;
      if (manifest.input.layout === 'NCHW') {
        values[destination] = red / 255;
        values[manifest.input.width * manifest.input.height + destination] = green / 255;
        values[2 * manifest.input.width * manifest.input.height + destination] = blue / 255;
      } else {
        values[destination * 3] = red / 255;
        values[destination * 3 + 1] = green / 255;
        values[destination * 3 + 2] = blue / 255;
      }
    }
  }
  return new runtime.Tensor(
    'float32',
    values,
    manifest.input.layout === 'NCHW'
      ? [1, 3, manifest.input.height, manifest.input.width]
      : [1, manifest.input.height, manifest.input.width, 3],
  );
}

async function onnxSuggestion(
  imageData: ImageData,
  manifest: ModelManifest,
): Promise<AutoCropSuggestion> {
  const bytes = await verifiedModelBytes(manifest);
  const runtime = await import('onnxruntime-web');
  const session = await runtime.InferenceSession.create(bytes, { executionProviders: ['wasm'] });
  const inputName = session.inputNames[0];
  if (!inputName) throw new Error('Auto-crop model has no input tensor.');
  const outputs = await session.run({ [inputName]: tensorFor(imageData, manifest, runtime) });
  const output = outputs[session.outputNames[0] ?? ''];
  if (!output || output.data.length < 5)
    throw new Error('Auto-crop model returned an invalid prediction.');
  const values = Array.from(output.data as Float32Array)
    .slice(0, 5)
    .map(Number);
  const [
    x = Number.NaN,
    y = Number.NaN,
    width = Number.NaN,
    height = Number.NaN,
    confidence = Number.NaN,
  ] = values;
  if (![x, y, width, height, confidence].every(Number.isFinite) || width <= 0 || height <= 0) {
    throw new Error('Auto-crop model returned non-finite crop coordinates.');
  }
  const cropX = clamp(x, 0, 0.95);
  const cropY = clamp(y, 0, 0.95);
  const crop: NormalizedCrop = {
    x: cropX,
    y: cropY,
    width: Math.min(clamp(width, 0.05, 1), 1 - cropX),
    height: Math.min(clamp(height, 0.05, 1), 1 - cropY),
  };
  return {
    crop,
    confidence: clamp(confidence, 0, 1),
    provider: 'onnx',
    modelVersion: manifest.version,
  };
}

export async function suggestCrop(
  imageData: ImageData,
  manifest: ModelManifest,
): Promise<AutoCropSuggestion> {
  if (manifest.provider === 'heuristic') return heuristicSuggestion(imageData, manifest);
  try {
    return await onnxSuggestion(imageData, manifest);
  } catch {
    return heuristicSuggestion(imageData, manifest);
  }
}
