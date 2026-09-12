# Project Plan — Image Processing Library (`imgproc`)

> A high-performance C++20 computer-vision library for **object detection and
> tracking** with **CUDA acceleration**, **OpenCV** algorithms, **ML model
> integration**, and first-class **Python bindings** (pybind11).

| Item | Value |
| --- | --- |
| **Language standard** | C++20 |
| **Core dependencies** | OpenCV ≥ 4.8, CUDA Toolkit ≥ 12.x, pybind11 ≥ 2.11 |
| **Build system** | CMake ≥ 3.24 (with CUDA language support) |
| **Package managers** | vcpkg (manifest) / Conan 2.x |
| **Test framework** | GoogleTest + GoogleMock |
| **CI/CD** | GitHub Actions (Linux + Windows, CPU + CUDA matrix) |
| **Distribution** | Static/shared C++ lib + `pip`-installable Python wheel |

---

## 1.1 Project File Structure

The repository follows a **layered library layout** with a strict separation
between *public headers* (`include/`), *implementation* (`src/`), *bindings*,
*executables*, and *quality gates* (tests, benchmarks, CI).

```
3Image-Processing-Library/
├── CMakeLists.txt                 # Top-level build orchestration
├── vcpkg.json                     # vcpkg manifest (dependency pinning)
├── conanfile.py                   # Conan 2.x alternative dependency graph
├── CMakePresets.json              # Reproducible configure/build presets
├── .clang-format                  # Code style (LLVM-derived)
├── .clang-tidy                    # Static analysis ruleset
├── .gitignore
├── .env.example                   # Runtime config template (model paths, devices)
├── README.md
│
├── docs/                          # ── Documentation (this deliverable) ──
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── include/imgproc/               # ── PUBLIC API (installed headers) ──
│   ├── core/                      #   Image/Tensor abstractions, types, status
│   │   ├── image.hpp
│   │   ├── tensor.hpp
│   │   ├── types.hpp
│   │   └── status.hpp
│   ├── filters/                   #   CPU/GPU image filters
│   │   └── gaussian_blur.hpp
│   ├── detection/                 #   Object-detection interfaces
│   │   └── object_detector.hpp
│   ├── tracking/                  #   Multi-object tracking
│   │   └── tracker.hpp
│   ├── ml/                        #   ML model runner / backend abstraction
│   │   └── model_runner.hpp
│   ├── cuda/                      #   CUDA helpers (public surface kept thin)
│   │   └── cuda_utils.hpp
│   ├── io/                        #   Image/video readers & writers
│   │   └── image_io.hpp
│   └── version.hpp.in             #   Configured → version.hpp at build time
│
├── src/                           # ── IMPLEMENTATION (private) ──
│   ├── CMakeLists.txt
│   ├── core/image.cpp
│   ├── filters/
│   │   ├── gaussian_blur.cpp      #   CPU (OpenCV) path
│   │   └── gaussian_blur.cu       #   CUDA kernel path
│   ├── detection/object_detector.cpp
│   ├── tracking/tracker.cpp
│   ├── ml/model_runner.cpp
│   └── io/image_io.cpp
│
├── bindings/python/               # ── PYTHON BINDINGS (pybind11) ──
│   ├── CMakeLists.txt
│   ├── module.cpp                 #   PYBIND11_MODULE definition
│   ├── pyproject.toml             #   scikit-build-core wheel build
│   └── imgproc/__init__.py        #   Python-side package surface
│
├── apps/cli/                      # ── EXECUTABLE / DEMO ──
│   ├── CMakeLists.txt
│   └── main.cpp                   #   Reference CLI (detect/track on a file)
│
├── tests/                         # ── QUALITY GATES ──
│   ├── CMakeLists.txt
│   ├── unit/
│   │   ├── test_image.cpp
│   │   ├── test_gaussian_blur.cpp
│   │   └── test_object_detector.cpp
│   ├── integration/
│   │   └── test_pipeline.cpp
│   └── data/                      #   Fixtures (sample frames, tiny models)
│
├── benchmarks/                    # ── PERFORMANCE ──
│   └── bench_blur.cpp             #   google/benchmark micro-benchmarks
│
├── cmake/                         # ── BUILD MODULES ──
│   ├── Dependencies.cmake         #   find_package wiring
│   ├── CompilerWarnings.cmake     #   Warnings-as-errors profile
│   └── CudaOptions.cmake          #   Architectures, separable compilation
│
├── migrations/                    # ── METADATA STORE (model registry) ──
│   └── 0001_init_model_registry.sql
│
├── config/                        # ── TOOL/RUNTIME CONFIG ──
│   └── models.example.yaml        #   Model zoo manifest
│
├── scripts/                       # ── DEVELOPER TOOLING ──
│   ├── build.ps1
│   ├── build.sh
│   └── format.sh
│
└── .github/                       # ── CI/CD ──
    ├── workflows/
    │   ├── ci.yml                 #   lint → test → build matrix
    │   └── release.yml            #   wheels + artifacts on tag
    └── ISSUE_TEMPLATE/
        └── bug_report.md
```

### Rationale

- **`include/` vs `src/` split** — Only `include/imgproc/**` is installed and
  forms the ABI surface. Implementation details (CUDA kernels, OpenCV calls)
  stay in `src/`, so consumers never transitively depend on CUDA headers unless
  they opt in.
- **Subsystem folders mirror the public API** — `filters`, `detection`,
  `tracking`, `ml`, `io` map 1:1 between headers, sources, and tests, keeping
  navigation predictable as the codebase grows.
- **Bindings are a separate target** — Python wheels build independently from the
  C++ library so a binding break never blocks native consumers.
- **`migrations/` for the model registry** — This CV library integrates ML
  models; their metadata, versions, and detection-result provenance live in a
  small relational store (SQLite by default, Postgres for shared deployments).

---

## 1.2 Implementation TODO List

> **Status:** Phases 1 & 2 are **implemented and tested** (55 GoogleTest cases,
> all green). The **default build is dependency-light** — pure C++20 + a vendored
> SQLite amalgamation + FetchContent GoogleTest. OpenCV / CUDA / ONNX became
> *optional accelerated backends* (graceful CPU fallback was always the
> documented philosophy), which is why CPU paths are pure-C++ and IO uses a
> self-contained PPM/PGM codec instead of mandating OpenCV.

### ✅ Phase 1 — Foundation (HIGH priority)

- [x] Scaffold CMake build with C++20, optional CUDA language, and presets.
- [x] Dependency wiring: vendored SQLite + FetchContent GoogleTest (vcpkg/Conan kept for the OpenCV/CUDA `full` build).
- [x] Implement `core::Image` (convert BGR/RGB/Gray) / `core::Tensor` (NCHW float).
- [x] Implement `core::Status` error type + geometry (`iou`, `nonMaxSuppression`).
- [x] Establish `cuda::CudaUtils` (device query, RAII `Stream`, graceful no-CUDA fallback).
- [x] Add `.clang-format`, `.clang-tidy`, and `CompilerWarnings.cmake`.
- [x] Stand up GoogleTest harness + `test_image` (and 10 more test files).
- [x] Author GitHub Actions `ci.yml` (lint + CPU build/test matrix + CUDA compile).

### ✅ Phase 2 — Core Features (MEDIUM priority)

- [x] Gaussian blur: pure-C++ separable CPU path + dispatch (+ optional CUDA kernel).
- [x] `detection::ObjectDetector`: classical blob detector **and** model-backed detector.
- [x] `ml::ModelRunner` (load/validate, warmup, infer) with a deterministic reference backend.
- [x] `tracking::Tracker`: SORT-style constant-velocity prediction + greedy IoU association + lifecycle.
- [x] `io::ImageIO`: PPM/PGM decode/encode with decode-bomb hardening.
- [x] `bindings/python/module.cpp` pybind11 bindings + `imgproc` package + pytest suite.
- [x] Reference CLI in `apps/cli` (decode → detect → track → annotate → registry log).
- [x] Integration test: end-to-end detect→track across frames + decode→detect→registry.
- [x] Model-registry over SQLite + `migrations/0001_init_model_registry.sql` (full CRUD).
- [x] `util::Sha256` model-artifact integrity verification (allow-list + checksum).
- [x] Extend CI with a CUDA compile job (CPU build/test matrix runs on Linux + Windows).

### 🟢 Phase 3 — Polish & Optimization (LOWER priority)

- [x] google/benchmark micro-benchmark target (`benchmarks/bench_blur.cpp`).
- [x] Optional CUDA Gaussian-blur kernel (separable, behind `IMGPROC_WITH_CUDA`).
- [x] **ONNX Runtime backend** wired into `ml::ModelRunner` (`IMGPROC_WITH_ONNX`),
      real inference verified against a fixture model; CUDA EP wired for the GPU package.
- [ ] CUDA stream pipelining + pinned-memory async transfers for batch inference.
- [ ] Real YOLO/SSD head decoder in `ModelDetector` (currently strongest-activation).
- [ ] TensorRT execution provider + OpenCV-DNN backend.
- [ ] Multi-stream / multi-GPU scheduling for the tracking pipeline.
- [ ] Doxygen API docs + Sphinx (breathe) site published from CI.
- [ ] Conan/vcpkg published package + semantic-versioned releases.
- [ ] AddressSanitizer / compute-sanitizer jobs; coverage reporting (≥ 80%).
- [ ] Cross-platform wheels (manylinux, Windows) via `release.yml`.

---

## Definition of Done (per feature)

1. Public header documented (Doxygen `///` comments).
2. Implementation + unit test (CPU path) + GPU path test guarded by `IMGPROC_WITH_CUDA`.
3. `clang-tidy` clean; warnings-as-errors passing.
4. Benchmarked if it sits on the hot path.
5. Exposed in Python bindings where user-facing.
