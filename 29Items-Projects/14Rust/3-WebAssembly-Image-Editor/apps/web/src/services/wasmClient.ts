import type { CropRect, FilterRecipe } from '../types/image';

type Operation =
  | { readonly kind: 'filter'; readonly filter: FilterRecipe }
  | { readonly kind: 'crop'; readonly crop: CropRect };

interface WorkerRequest {
  readonly id: number;
  readonly width: number;
  readonly height: number;
  readonly buffer: ArrayBuffer;
  readonly operation: Operation;
}

interface WorkerSuccess {
  readonly id: number;
  readonly ok: true;
  readonly width: number;
  readonly height: number;
  readonly buffer: ArrayBuffer;
}

interface WorkerFailure {
  readonly id: number;
  readonly ok: false;
  readonly message: string;
}

type WorkerResponse = WorkerSuccess | WorkerFailure;

interface GeneratedProcessor {
  readonly width: number;
  readonly height: number;
  apply_filter(name: string, amount: number): Uint8Array;
  crop(x: number, y: number, width: number, height: number): Uint8Array;
  free(): void;
}

interface WasmModule {
  default(input?: string | URL | Request): Promise<unknown>;
  ImageProcessor: new (width: number, height: number, pixels: Uint8Array) => GeneratedProcessor;
}

let wasmPromise: Promise<WasmModule> | undefined;
let worker: Worker | undefined;
let requestId = 0;
const pending = new Map<
  number,
  {
    resolve: (value: ImageData) => void;
    reject: (reason: Error) => void;
    timeout: ReturnType<typeof setTimeout>;
  }
>();
const PROCESSING_TIMEOUT_MS = 30_000;

function copyBuffer(imageData: ImageData): ArrayBuffer {
  return imageData.data.buffer.slice(
    imageData.data.byteOffset,
    imageData.data.byteOffset + imageData.data.byteLength,
  ) as ArrayBuffer;
}

function toImageData(buffer: ArrayBuffer, width: number, height: number): ImageData {
  return new ImageData(new Uint8ClampedArray(buffer), width, height);
}

function failPending(error: Error): void {
  for (const task of pending.values()) {
    clearTimeout(task.timeout);
    task.reject(error);
  }
  pending.clear();
  worker?.terminate();
  worker = undefined;
}

async function loadWasm(): Promise<WasmModule> {
  wasmPromise ??= (async () => {
    const moduleUrl = '/wasm/image_processor.js';
    const loaded = (await import(/* @vite-ignore */ moduleUrl)) as WasmModule;
    await loaded.default('/wasm/image_processor_bg.wasm');
    return loaded;
  })();
  return wasmPromise;
}

async function processInline(imageData: ImageData, operation: Operation): Promise<ImageData> {
  const wasm = await loadWasm();
  const processor = new wasm.ImageProcessor(
    imageData.width,
    imageData.height,
    new Uint8Array(copyBuffer(imageData)),
  );
  try {
    if (operation.kind === 'filter') {
      return toImageData(
        processor.apply_filter(operation.filter.name, operation.filter.amount),
        processor.width,
        processor.height,
      );
    }
    return toImageData(
      processor.crop(
        operation.crop.x,
        operation.crop.y,
        operation.crop.width,
        operation.crop.height,
      ),
      processor.width,
      processor.height,
    );
  } finally {
    processor.free();
  }
}

function getWorker(): Worker | null {
  if (typeof Worker === 'undefined') return null;
  worker ??= new Worker(new URL('../workers/imageProcessor.worker.ts', import.meta.url), {
    type: 'module',
  });
  worker.onmessage = (event: MessageEvent<WorkerResponse>) => {
    const message = event.data;
    const task = pending.get(message.id);
    if (!task) return;
    pending.delete(message.id);
    clearTimeout(task.timeout);
    if (message.ok) task.resolve(toImageData(message.buffer, message.width, message.height));
    else task.reject(new Error(message.message));
  };
  worker.onerror = (event) => {
    failPending(new Error(event.message || 'Image processing worker failed.'));
  };
  worker.onmessageerror = () => failPending(new Error('Image processing returned invalid data.'));
  return worker;
}

async function process(imageData: ImageData, operation: Operation): Promise<ImageData> {
  const imageWorker = getWorker();
  if (!imageWorker) return processInline(imageData, operation);
  const id = ++requestId;
  const buffer = copyBuffer(imageData);
  return new Promise<ImageData>((resolve, reject) => {
    const timeout = setTimeout(() => {
      failPending(new Error('Image processing timed out. Try a smaller image.'));
    }, PROCESSING_TIMEOUT_MS);
    pending.set(id, { resolve, reject, timeout });
    const request: WorkerRequest = {
      id,
      width: imageData.width,
      height: imageData.height,
      buffer,
      operation,
    };
    try {
      imageWorker.postMessage(request, [buffer]);
    } catch (error) {
      clearTimeout(timeout);
      pending.delete(id);
      reject(error instanceof Error ? error : new Error('Image processing could not start.'));
    }
  });
}

export function applyWasmFilter(imageData: ImageData, filter: FilterRecipe): Promise<ImageData> {
  return process(imageData, { kind: 'filter', filter });
}

export function cropWithWasm(imageData: ImageData, crop: CropRect): Promise<ImageData> {
  return process(imageData, { kind: 'crop', crop });
}

export function disposeImageProcessor(): void {
  failPending(new Error('Image processor was disposed.'));
}
