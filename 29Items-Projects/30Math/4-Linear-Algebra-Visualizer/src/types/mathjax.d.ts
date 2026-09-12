/** Minimal typing for the MathJax v3 global loaded from CDN in index.html. */
export {};

declare global {
  interface Window {
    MathJax?: {
      typesetPromise?: (elements?: HTMLElement[]) => Promise<void>;
      typesetClear?: (elements?: HTMLElement[]) => void;
      startup?: { promise?: Promise<void> };
      [key: string]: unknown;
    };
  }
}
