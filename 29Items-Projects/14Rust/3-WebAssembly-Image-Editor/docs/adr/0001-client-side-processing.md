# ADR 0001: Keep image pixel processing in the browser

- **Status:** Accepted
- **Date:** 2026-07-18

## Context

The editor performs interactive transformations on potentially sensitive user images. Server-side processing would require upload infrastructure, storage lifecycle controls, higher latency, and a larger compliance surface.

## Decision

Decode, transform, infer crop suggestions, and export in the browser. Use Rust compiled to WebAssembly for deterministic compute kernels, Canvas/browser codecs for I/O, and ONNX Runtime Web for ML inference. Any serverless API is limited to non-image metadata.

## Consequences

- Image pixels remain local and most editing remains available without an account.
- Browser memory, codec support, and device performance become explicit product constraints.
- Large computations must move to workers and use transferables to protect responsiveness.
- A future cloud-processing feature requires a new ADR and privacy/security review; it is not an incremental implementation detail.
