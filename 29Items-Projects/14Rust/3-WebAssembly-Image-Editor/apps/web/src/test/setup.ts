import '@testing-library/jest-dom/vitest';

if (!globalThis.ImageData) {
  class TestImageData {
    constructor(
      readonly data: Uint8ClampedArray,
      readonly width: number,
      readonly height: number,
    ) {}
  }
  Object.defineProperty(globalThis, 'ImageData', { value: TestImageData });
}
