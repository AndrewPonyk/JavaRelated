import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// RTL's automatic cleanup needs a global afterEach; we run vitest without
// globals, so register it explicitly or renders accumulate across tests.
afterEach(() => {
  cleanup();
});

// jsdom lacks ResizeObserver (used by SceneManager's host-size tracking).
// Rendering itself is never mounted in unit tests (TECH-NOTES §3.6 pitfall 11),
// but a no-op polyfill keeps accidental imports from exploding.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver;
