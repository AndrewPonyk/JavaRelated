/**
 * Gap-fillers on top of jest-fixed-jsdom (docs/TECH-NOTES.md §3.6 #2).
 * jest-fixed-jsdom restores the Node globals that jest-environment-jsdom strips
 * (fetch/Request/Response, streams, TextEncoder, structuredClone, BroadcastChannel);
 * the two below are still missing from the bundled jsdom itself. Both are guarded,
 * so they become no-ops the day jsdom ships them.
 */

// jsdom lacks AbortSignal.timeout (used by httpClient request timeouts).
if (typeof AbortSignal.timeout !== 'function') {
  Object.defineProperty(AbortSignal, 'timeout', {
    value: (ms: number): AbortSignal => {
      const controller = new AbortController();
      setTimeout(() => controller.abort(new DOMException('TimeoutError', 'TimeoutError')), ms);
      return controller.signal;
    },
    writable: true,
  });
}

// Belt-and-braces: structuredClone via V8 serialization if the environment lacks it.
if (typeof globalThis.structuredClone !== 'function') {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const v8 = require('node:v8');
  Object.defineProperty(globalThis, 'structuredClone', {
    value: <T>(value: T): T => v8.deserialize(v8.serialize(value)) as T,
    writable: true,
  });
}
