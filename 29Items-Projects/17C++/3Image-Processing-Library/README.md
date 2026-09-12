# imgproc — Computer Vision Library (detection & tracking)

A high-performance **C++20** library for **object detection and tracking** with
**ML model integration**, a relational **model registry** (SQLite), and a
reference **CLI**. CUDA, OpenCV, and ONNX are **optional accelerated backends** —
the default build is dependency-light and self-contained, so it compiles, tests,
and runs anywhere with just a C++20 toolchain.

> Built around graceful CPU↔GPU degradation: the same API dispatches to a custom
> CUDA kernel when a device is present, or a pure-C++ path otherwise — no caller
> code changes (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)).

---

## What's implemented (all working, tested)

| Subsystem | What it does |
| --- | --- |
| `core` | `Image` (BGR/RGB/Gray, convert), `Tensor` (NCHW float), `Status`, geometry (IoU/NMS) |
| `filters` | Separable **Gaussian blur** (pure-C++ CPU path + optional CUDA kernel) |
| `detection` | **Classical blob detector** (threshold + connected components) and a model-backed detector |
| `ml` | `ModelRunner` abstraction + deterministic **reference backend** + real **ONNX Runtime backend** (opt-in) |
| `tracking` | **SORT-style tracker**: constant-velocity prediction + greedy IoU association + lifecycle |
| `io` | Dependency-free **PPM/PGM codec** with decode-bomb hardening |
| `registry` | **SQLite** model registry — full CRUD for models, labels, detection runs |
| `util` | Self-contained **SHA-256** for model-artifact integrity checks |
| `apps/cli` | End-to-end CLI: decode → detect → track → annotate → log to registry |
| `bindings` | **pybind11** module (NumPy in/out) — optional |

**55 GoogleTest cases pass**, covering every subsystem plus two integration flows.

## Documentation

| Doc | Contents |
| --- | --- |
| [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) | File structure + (completed) TODO list |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Pattern, components, data flow, scalability, security |
| [docs/TECH-NOTES.md](docs/TECH-NOTES.md) | CI/CD, testing, deployment, env, branching, pitfalls |

---

## Build & test (default, no external deps)

Requires a C++20 compiler, CMake ≥ 3.24, and a build tool (Ninja/Make). CMake
fetches GoogleTest automatically (needs network on first configure); SQLite is
vendored in `third_party/`.

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
ctest --test-dir build --output-on-failure
```

Or with presets:

```bash
cmake --preset default && cmake --build --preset default && ctest --preset default
```

### Run the CLI

```bash
# Generate a sample frame however you like (any binary PPM/PGM works), then:
./build/apps/cli/imgproc-cli --input sample.ppm --output annotated.ppm --registry runs.db
# [info] detections=2 tracks=2 latency=0.12ms
#   - object score=0.91 box=[28,30,10,10]
#   - object score=0.89 box=[6,6,8,8]
# [info] logged run #1 to runs.db
```

## ONNX Runtime backend (real model inference)

Run actual `.onnx` models through `ml::ModelRunner`. Enable the backend at
configure time — CMake fetches the ONNX Runtime prebuilt automatically (or point
it at a local copy with `-DIMGPROC_ONNXRUNTIME_ROOT=<dir>`):

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DIMGPROC_WITH_ONNX=ON
cmake --build build -j
ctest --test-dir build -R Onnx --output-on-failure   # runs real inference
```

```cpp
imgproc::ml::ModelSpec spec;
spec.backend      = imgproc::ml::BackendKind::kOnnxRuntime;
spec.artifact_path = "model.onnx";          // must live under model_dir if set
spec.model_dir     = "/opt/models";          // allow-list root (optional)
spec.sha256        = "<hex>";                // integrity check (optional)
spec.device        = imgproc::core::Device::kCuda;   // CUDA EP if GPU ORT package

std::unique_ptr<imgproc::ml::ModelRunner> runner;
auto st = imgproc::ml::ModelRunner::create(spec, runner);   // validates + loads
imgproc::core::Tensor out;
runner->infer(input_nchw, out);              // real forward pass
```

- Verified end-to-end by `tests/unit/test_onnx_runner.cpp` against
  `tests/data/affine.onnx` (regenerate with `python scripts/make_test_onnx.py`).
- **GPU:** the auto-fetched package is CPU-only; for the CUDA execution provider,
  point `IMGPROC_ONNXRUNTIME_ROOT` at the `onnxruntime-*-gpu` package.
- **Note:** `ModelRunner` runs any ONNX model and returns the raw output tensor.
  The `ModelDetector`'s decode is still a simplified "strongest-activation"
  step — wiring a full YOLO/SSD head decoder is the remaining piece for
  production object detection from a trained model.

## Python bindings (optional)

```bash
pip install ./bindings/python
pytest bindings/python/tests
python -c "import numpy as np, imgproc; \
  f=np.zeros((48,48),np.uint8); f[6:14,6:14]=255; \
  print(imgproc.ObjectDetector(imgproc.DetectorConfig()).detect(f))"
```

## Build options

| Option | Default | Meaning |
| --- | --- | --- |
| `IMGPROC_BUILD_TESTS` | ON | Build the GoogleTest suite |
| `IMGPROC_BUILD_CLI` | ON | Build the reference CLI |
| `IMGPROC_WITH_CUDA` | OFF | Build the CUDA-accelerated paths |
| `IMGPROC_WITH_OPENCV` | OFF | Build the optional OpenCV codec/backends |
| `IMGPROC_WITH_ONNX` | OFF | Build the ONNX Runtime inference backend |
| `IMGPROC_BUILD_PYTHON` | OFF | Build the pybind11 bindings |
| `IMGPROC_BUILD_BENCH` | OFF | Build google/benchmark micro-benchmarks |
| `IMGPROC_WARNINGS_AS_ERRORS` | OFF | `-Werror` / `/WX` |

Presets: `default`, `debug`, `release-cuda`, `onnx`, `full` (see `CMakePresets.json`).

## Project layout

```
include/imgproc/   public API headers (core, filters, detection, tracking, ml, io, registry, util, cuda)
src/               implementation (+ optional .cu CUDA kernel)
third_party/sqlite vendored SQLite amalgamation (registry backend)
bindings/python/   pybind11 module + pytest suite
apps/cli/          reference executable
tests/             GoogleTest unit + integration
benchmarks/        google/benchmark
migrations/        model-registry SQL schema
```

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| Configure fails: `Could NOT find ... GTest` / download error | First configure fetches GoogleTest — you need **internet access**. Behind a proxy, set `HTTPS_PROXY`, or install GTest and CMake will use it (`FIND_PACKAGE_ARGS`). |
| Build dies: **`not enough space on the disk`** | The build tree + fetched GoogleTest need a few hundred MB. Free space, then delete the `build/` dir and reconfigure. |
| Windows: `No CMAKE_CXX_COMPILER could be found` or `rc: no such file` | Your shell isn't in the MSVC environment. Use **`scripts\win-build.ps1`** (auto-discovers the toolchain) or the *x64 Native Tools Command Prompt for VS 2022*. Opening the folder in Visual Studio handles this automatically. |
| `CMakePresets.json ... version 6 not supported` | Update CMake to ≥ 3.25 (VS 2022's bundled CMake is fine), or configure without presets: `cmake -S . -B build -DCMAKE_BUILD_TYPE=Release`. |
| CUDA path not used at runtime | The default build is CPU-only (`IMGPROC_WITH_CUDA=OFF`). Reconfigure with `-DIMGPROC_WITH_CUDA=ON` and a CUDA toolkit installed; otherwise it transparently falls back to CPU. |
| `readImage: only binary PGM (P5)/PPM (P6) supported` | The dependency-light build reads Netpbm only. Convert input to `.ppm`/`.pgm`, or build with `-DIMGPROC_WITH_OPENCV=ON` for JPEG/PNG. |
| Model load fails: `artifact escapes allow-listed IMGPROC_MODEL_DIR` | Place the model file under `IMGPROC_MODEL_DIR` (or `--model-dir`); artifacts outside the allow-listed root are rejected by design. |

## License

Apache-2.0 (placeholder).
