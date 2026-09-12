/// <reference lib="webworker" />

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

type WorkerRequest =
  | {
      readonly id: number;
      readonly width: number;
      readonly height: number;
      readonly buffer: ArrayBuffer;
      readonly operation: {
        readonly kind: 'filter';
        readonly filter: { readonly name: string; readonly amount: number };
      };
    }
  | {
      readonly id: number;
      readonly width: number;
      readonly height: number;
      readonly buffer: ArrayBuffer;
      readonly operation: {
        readonly kind: 'crop';
        readonly crop: {
          readonly x: number;
          readonly y: number;
          readonly width: number;
          readonly height: number;
        };
      };
    };

let wasmPromise: Promise<WasmModule> | undefined;

async function loadWasm(): Promise<WasmModule> {
  wasmPromise ??= (async () => {
    const moduleUrl = '/wasm/image_processor.js';
    const loaded = (await import(/* @vite-ignore */ moduleUrl)) as WasmModule;
    await loaded.default('/wasm/image_processor_bg.wasm');
    return loaded;
  })();
  return wasmPromise;
}

self.onmessage = (event: MessageEvent<WorkerRequest>) => {
  void (async () => {
    const request = event.data;
    try {
      const wasm = await loadWasm();
      const processor = new wasm.ImageProcessor(
        request.width,
        request.height,
        new Uint8Array(request.buffer),
      );
      try {
        const bytes =
          request.operation.kind === 'filter'
            ? processor.apply_filter(request.operation.filter.name, request.operation.filter.amount)
            : processor.crop(
                request.operation.crop.x,
                request.operation.crop.y,
                request.operation.crop.width,
                request.operation.crop.height,
              );
        const buffer = bytes.buffer.slice(
          bytes.byteOffset,
          bytes.byteOffset + bytes.byteLength,
        ) as ArrayBuffer;
        self.postMessage(
          { id: request.id, ok: true, width: processor.width, height: processor.height, buffer },
          [buffer],
        );
      } finally {
        processor.free();
      }
    } catch (error) {
      self.postMessage({
        id: request.id,
        ok: false,
        message: error instanceof Error ? error.message : 'Image processing failed.',
      });
    }
  })();
};

export {};
