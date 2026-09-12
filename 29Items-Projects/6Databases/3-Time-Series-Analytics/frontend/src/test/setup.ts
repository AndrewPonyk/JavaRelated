import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// RTL auto-cleanup only engages with injected globals; we import explicitly.
afterEach(() => cleanup());

// jsdom lacks matchMedia, which useChartTheme relies on.
if (typeof window !== "undefined" && !window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }) as MediaQueryList;
}
