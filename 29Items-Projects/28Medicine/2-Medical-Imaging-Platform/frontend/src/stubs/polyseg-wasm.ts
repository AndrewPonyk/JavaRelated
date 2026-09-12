// Stub for @icr/polyseg-wasm.
//
// Cornerstone3D/tools eagerly pulls this WASM module in for *segmentation*
// (polygon ↔ labelmap conversion), which this viewer does not use. The real
// package statically imports a .wasm file that the Vite/Rollup build can't bundle
// cleanly. Aliasing it to this no-op stub cuts that import chain; the segmentation
// code path is never exercised here.

export default class ICRPolySeg {
  _instance: unknown = null;
  async initialize(): Promise<void> {
    /* no-op: segmentation not used */
  }
  get instance(): unknown {
    return this._instance;
  }
}
