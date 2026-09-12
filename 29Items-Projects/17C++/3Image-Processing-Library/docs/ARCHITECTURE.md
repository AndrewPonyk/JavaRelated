# Architecture — Image Processing Library (`imgproc`)

This document describes the architecture of `imgproc`, a C++20 computer-vision
library for **object detection and tracking** with **CUDA acceleration** and
**Python bindings**.

---

## 2.1 Chosen Architectural Pattern

### Pattern: **Layered, Plugin-Oriented Library (Modular Monolith)**

`imgproc` is not a network service — it is an **embeddable library** consumed
either as a native C++ dependency or via a Python wheel. The appropriate pattern
is therefore a **layered modular monolith** with **strategy/plugin** seams at the
performance-critical and model-integration boundaries.

```mermaid
flowchart TB
    subgraph API["Public API Layer (include/imgproc)"]
        H1[core: Image / Tensor / Status]
        H2[filters / detection / tracking]
        H3[ml: ModelRunner]
    end
    subgraph DOM["Domain Layer (src)"]
        D1[Detection pipeline]
        D2[Tracking / association]
        D3[Filter dispatch]
    end
    subgraph BACK["Backend / Execution Layer"]
        B1[CPU backend - OpenCV]
        B2[CUDA backend - kernels]
        B3[ML backends - ONNXRuntime / OpenCV-DNN / TensorRT]
    end
    subgraph BIND["Consumer Surfaces"]
        P1[Python module - pybind11]
        P2[CLI app]
        P3[Native C++ consumers]
    end

    BIND --> API --> DOM --> BACK
```

### Why this pattern fits

| Requirement | How the pattern satisfies it |
| --- | --- |
| **Low-latency, in-process CV** | A library (no IPC/network hop) keeps frame data on the hot path; CUDA buffers never cross a process boundary. |
| **CPU *and* GPU execution** | A **strategy seam** (`Backend`) lets the same operation dispatch to OpenCV or a CUDA kernel based on device availability. |
| **Swappable ML models** | A **plugin seam** (`ml::Backend`) abstracts ONNXRuntime / OpenCV-DNN / TensorRT behind one interface. |
| **Python + C++ consumers** | Bindings are a thin adapter over a stable C++ API; both surfaces share one core. |
| **Maintainability at scale** | Strict layering (API → domain → backend) prevents OpenCV/CUDA leakage into the public ABI. |

> **Why not microservices?** The workload is latency-bound, GPU-memory-resident
> image data. Serializing tensors across a network would dominate runtime and
> defeat CUDA acceleration. A single optimized library is the correct unit.

---

## 2.2 Key Component Interactions

Components communicate via **direct in-process C++ calls** through interfaces.
There is **no message queue or network bus** inside the core; concurrency is
handled with **CUDA streams** and a **CPU thread pool**.

```mermaid
flowchart LR
    CLI[CLI / Python caller] -->|invoke| PIPE[DetectionPipeline]
    PIPE -->|IImage| IO[io::ImageIO]
    PIPE -->|preprocess| FILT[filters::GaussianBlur]
    FILT -->|dispatch| DISP{Device?}
    DISP -->|GPU| CUDA[CUDA kernel + stream]
    DISP -->|CPU| OCV[OpenCV impl]
    PIPE -->|infer| ML[ml::ModelRunner]
    ML -->|ExecutionProvider| ORT[(ONNXRuntime / DNN / TRT)]
    PIPE -->|detections| TRK[tracking::Tracker]
    TRK -->|tracks| OUT[Result objects]
    ML -. metadata .-> REG[(Model Registry DB)]
```

- **API calls** — synchronous C++ method calls across layers (the primary
  mechanism). Interfaces (`ObjectDetector`, `ModelRunner`, `Backend`) decouple
  callers from implementations.
- **Direct database access** — the **Model Registry** (SQLite/Postgres) is read
  at load time to resolve model versions, input specs, and class labels, and is
  written to record detection-run provenance. Accessed through a thin DAO, never
  from the hot inference loop.
- **Event bus / message queue** — *intentionally absent* in the core. For batch
  or server deployments, an **optional** outer harness (not part of the library)
  may pull frames from a queue (Kafka/SQS) and call `imgproc` per frame.
- **Concurrency channels** — CUDA streams (async H2D/D2H + kernel launches) and a
  CPU thread pool for parallel CPU-path stages.

---

## 2.3 Data Flow

Typical path of a frame from input → processing → result, with the model
registry consulted once at initialization.

```mermaid
sequenceDiagram
    autonumber
    participant U as Caller (CLI/Python)
    participant P as DetectionPipeline
    participant IO as io::ImageIO
    participant F as filters (CPU/CUDA)
    participant M as ml::ModelRunner
    participant DB as Model Registry
    participant T as tracking::Tracker

    U->>P: process(frame_path / ndarray)
    P->>IO: decode(frame)
    IO-->>P: Image (HxWxC)
    Note over P,DB: One-time at init
    P->>DB: resolveModel("yolov8n", v=1.3)
    DB-->>P: weights path + input spec + labels
    P->>M: load(spec) / warmup()
    P->>F: preprocess(Image) [resize, normalize, blur]
    F->>F: dispatch → CUDA stream or OpenCV
    F-->>P: Tensor (NCHW, device)
    P->>M: infer(Tensor)
    M-->>P: raw detections (boxes, scores, classes)
    P->>P: NMS + decode
    P->>T: update(detections)
    T-->>P: tracks (id, bbox, velocity)
    P-->>U: Result{detections, tracks}
    opt provenance
        P->>DB: record run(model_id, n_dets, latency)
    end
```

### Memory data flow (CPU ↔ GPU)

```mermaid
flowchart LR
    A[Host Image buffer] -->|cudaMemcpyAsync H2D| B[Device buffer]
    B --> C[CUDA kernels: blur / resize / normalize]
    C --> D[Inference on GPU]
    D -->|cudaMemcpyAsync D2H| E[Host detections]
    E --> F[CPU: NMS + tracking]
```

Image pixels move to the device **once**, are processed and inferred on-GPU, and
only compact detection results return to the host — minimizing PCIe traffic.

---

## 2.4 Scalability & Performance Strategy

| Axis | Strategy |
| --- | --- |
| **Throughput (frames/s)** | CUDA **stream pipelining** — overlap H2D copy of frame *N+1* with compute of frame *N*; **pinned (page-locked) host memory** for async transfers. |
| **Batch inference** | Accumulate frames into an `N×C×H×W` batch tensor; the ML backend runs one batched forward pass. |
| **Multi-GPU** | Round-robin pipelines across devices via a device-id-parameterized `Backend`; each GPU owns its streams and model replica. |
| **CPU fallback** | When no CUDA device is present, the same API dispatches to OpenCV CPU paths — no caller code change. |
| **Memory** | Reusable device buffer pools (avoid per-frame `cudaMalloc`); zero-copy NumPy ↔ `Image` views in Python where layout allows. |
| **Compile-time scaling** | Separable CUDA compilation + per-architecture fat binaries; unity builds optional for CI speed. |
| **Horizontal (deployment)** | The library is stateless per call; a host application scales out by running more worker processes, each pinned to a GPU. |

**Performance guardrails:** google/benchmark micro-benchmarks run in CI; a
regression > X% on the hot path (blur, inference, NMS) fails the build.

---

## 2.5 Security Considerations

Although `imgproc` is a library (not an exposed network endpoint), it processes
**untrusted media** and loads **ML model artifacts**, which carry real risk.

### Authentication & Authorization
- The library itself performs no auth. When embedded in a service, the **host
  application** owns authn/authz; `imgproc` exposes hooks to receive an
  already-authenticated context (e.g., tenant id for registry scoping).
- Model-registry access uses least-privilege DB credentials (read-only for
  inference workers; write only for the registration tool).

### Data Protection
- Frames may be PII (faces, plates). The library keeps image buffers in memory
  only; it **never** writes inputs to disk unless explicitly told to.
- Optional at-rest encryption for cached model weights and result stores is
  delegated to the deployment (LUKS / KMS-managed volumes).

### API / Input Security
- **Untrusted-image hardening:** decode through OpenCV with size/dimension limits
  and decompression-bomb guards; reject malformed or oversized inputs early.
- **Model artifact validation:** verify model file **checksums/signatures** from
  the registry before loading; load only from an allow-listed model directory to
  prevent arbitrary-file / deserialization attacks.
- Bounds-checked tensor shapes — never trust a model's declared I/O dims blindly.

### Secret Management
- No secrets in source or headers. DB connection strings, registry URLs, and
  cloud credentials come from **environment variables** (`.env`, see
  `TECH-NOTES.md`) or a secrets manager (Vault / AWS Secrets Manager).
- `.env` is git-ignored; only `.env.example` is committed.

---

## 2.6 Error Handling & Logging Philosophy

### Error handling

A **two-tier** strategy that stays exception-light on the hot path:

1. **Recoverable / expected failures** → return a `core::Status` (an
   error-code + message value type, à la `absl::Status`). Used for: file not
   found, unsupported format, model-shape mismatch, no CUDA device.
2. **Programmer errors / invariants** → assertions in debug; `std::logic_error`
   in public API entry points for contract violations.
3. **CUDA errors** → every CUDA call is wrapped in a `IMGPROC_CUDA_CHECK(...)`
   macro that converts a non-`cudaSuccess` code into a `core::Status` (or throws
   at the public boundary), capturing the kernel/op name.

```mermaid
flowchart TD
    OP[Operation] --> R{Result}
    R -->|cudaSuccess / ok| OK[Return value / ok Status]
    R -->|recoverable| ST[Return core::Status with code+msg]
    R -->|invariant broken| EX[Throw at API boundary]
    ST --> LOG[Log at WARN/ERROR]
    EX --> LOG
```

- **Exceptions cross the public boundary only**, where they are also translated
  into Python exceptions by the pybind11 layer (`Status` → `RuntimeError` /
  custom `ImgprocError`). Internal hot loops avoid throwing.

### Logging

- A **logging facade** (`core::log`) wraps a fast backend (spdlog) so the rest of
  the code never hard-depends on a logger.
- **Levels:** `TRACE` (per-frame timings, dev only) → `DEBUG` → `INFO`
  (lifecycle: model loaded, device selected) → `WARN` (CPU fallback, retried op)
  → `ERROR` (failed op) → `CRITICAL`.
- **Structured fields** for machine parsing: `frame_id`, `model_id`, `device`,
  `latency_ms`, `n_detections`.
- **No logging in tight kernels/inner loops** — sample or aggregate instead.
- Library default = `WARN`; the host app raises verbosity via env
  (`IMGPROC_LOG_LEVEL`).

---

## 2.7 Implementation Notes (as built)

The shipped implementation realises the architecture above with a
**dependency-light default** so the library compiles, tests, and runs with only
a C++20 toolchain. OpenCV / CUDA / ONNXRuntime / TensorRT are **optional
accelerated backends** behind feature flags — consistent with §2.4's CPU
fallback strategy.

### Module map

```mermaid
flowchart TB
    subgraph core["core"]
        IMG[Image]:::done
        TEN[Tensor]:::done
        GEO[geometry: iou / NMS]:::done
        ST[Status]:::done
    end
    subgraph proc["processing"]
        BLUR[filters: GaussianBlur CPU]:::done
        DET[detection: ClassicalBlob + Model]:::done
        TRK[tracking: SORT]:::done
    end
    subgraph infra["integration"]
        ML[ml: ModelRunner + Reference backend]:::done
        IO[io: PPM/PGM codec]:::done
        REG[(registry: SQLite CRUD)]:::done
        SHA[util: SHA-256]:::done
    end
    subgraph opt["optional backends (flagged)"]
        CUDA[CUDA kernels]:::opt
        OCV[OpenCV codec/DNN]:::opt
        ONNX[ONNXRuntime / TensorRT]:::opt
    end
    core --> proc --> infra
    BLUR -.IMGPROC_WITH_CUDA.-> CUDA
    IO -.IMGPROC_WITH_OPENCV.-> OCV
    ML -.IMGPROC_WITH_ONNX.-> ONNX
    classDef done fill:#d4edda,stroke:#28a745;
    classDef opt fill:#fff3cd,stroke:#ffc107,stroke-dasharray:4 3;
```

### Concrete backend choices

| Seam | Default (built & tested) | Optional acceleration |
| --- | --- | --- |
| Filter dispatch | Pure-C++ separable convolution | CUDA separable kernel (`.cu`) |
| Detector | Classical blob (threshold + connected components) | Model-backed (`ml::ModelRunner`) |
| ML backend | Deterministic `ReferenceRunner` | **ONNX Runtime** (`IMGPROC_WITH_ONNX`, implemented & tested) · OpenCV-DNN / TensorRT (future) |
| Image IO | Netpbm (PPM/PGM) codec | OpenCV `imread`/`imwrite` |
| Registry | Embedded **SQLite** (vendored amalgamation) | PostgreSQL (same schema) |

The **ONNX Runtime backend** (`src/ml/onnx_runner.cpp`) loads a real `.onnx`
model into an `Ort::Session` and runs genuine forward passes; it is verified
end-to-end in CI-style tests against a tiny fixture model. CMake fetches the
ONNX Runtime prebuilt (`cmake/Onnxruntime.cmake`) and stages its shared library
next to the executables. The CUDA execution provider is wired for the GPU ORT
package; the auto-fetched CPU package falls back to the CPU provider.

### Error handling & logging — as implemented

- The `core::Status` value type is used throughout the hot path exactly as
  described; the pybind11 layer translates a non-OK `Status` into a Python
  `RuntimeError`, and the CLI prints `Status::message()` to `stderr`.
- Current logging is lightweight `stderr` reporting in the CLI plus rich
  `Status` messages from the library. The structured spdlog facade (§2.6) is the
  documented target for the service-embedding milestone and is not yet wired in.
- CUDA calls are wrapped by `IMGPROC_CUDA_CHECK` in the `.cu` translation unit,
  converting a failing `cudaError_t` into a `core::CudaError` Status.
