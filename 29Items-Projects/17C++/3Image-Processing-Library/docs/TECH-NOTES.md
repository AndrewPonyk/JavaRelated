# Technical Notes — Image Processing Library (`imgproc`)

Actionable guidance for building, testing, deploying, and maintaining a CUDA-
accelerated C++20 / OpenCV / pybind11 computer-vision library.

> **As built (current state).** The default configuration needs **no vcpkg/Conan
> and no OpenCV/CUDA** — GoogleTest is pulled via CMake `FetchContent`, SQLite is
> vendored, and the active CI matrix builds + runs `ctest` on Linux and Windows.
> The vcpkg/Conan/cibuildwheel guidance below applies to the `full` preset that
> adds the optional OpenCV/CUDA/Python backends.
>
> **No Docker.** This is an embeddable library, not a service — there is no
> "stack" to bring up — so containers are not part of the project. Reproducible
> build/test is provided by the GitHub Actions matrix; distribution is via
> vcpkg/Conan (C++) and a pip wheel (Python).

---

## 3.1 CI/CD Pipeline Design

Stages run as a **GitHub Actions matrix** (`{linux, windows} × {cpu, cuda}`).

```mermaid
flowchart LR
    L[Lint & Format] --> B[Configure & Build] --> T[Test] --> K[Package] --> D[Deploy/Publish]
```

| Stage | Tooling | What it does |
| --- | --- | --- |
| **Lint** | `clang-format --dry-run --Werror`, `clang-tidy` | Style + static analysis; fails on drift. |
| **Build** | CMake presets + Ninja; vcpkg/Conan cache | CPU build always; CUDA build on GPU runners / with CUDA toolkit. |
| **Test** | `ctest` (GoogleTest), `pytest` for bindings | Unit + integration; CUDA tests gated by `IMGPROC_WITH_CUDA`. |
| **Sanitize** | ASan/UBSan job, `compute-sanitizer` (CUDA) | Memory-safety gate on a dedicated job. |
| **Package** | `cibuildwheel`, CPack | Python wheels (manylinux/Windows) + C++ archive. |
| **Deploy** | `release.yml` on tag `v*` | Upload wheels to PyPI/registry, attach artifacts to GitHub Release. |

**Environments:** `dev` (every PR, CPU-only fast loop) → `staging` (merge to
`main`, full CUDA matrix + benchmarks) → `prod` (tagged release → published
wheels + vcpkg/Conan packages). Promotion is gated on green tests + benchmark
non-regression.

**Caching tips:** cache the vcpkg/Conan binary cache and the CMake build dir
keyed on `vcpkg.json`/`conanfile.py` hash to keep CI under a few minutes.

---

## 3.2 Testing Strategy

| Layer | Framework | Target |
| --- | --- | --- |
| **Unit** | GoogleTest + GoogleMock | Per-class behavior (Image ops, blur correctness vs. reference, NMS, IoU). **Coverage ≥ 80%** on core/domain. |
| **GPU parity** | GoogleTest (typed/parameterized) | Assert CUDA output ≈ CPU output within tolerance (`EXPECT_NEAR` on pixel/det values). |
| **Integration** | GoogleTest | End-to-end `decode → preprocess → infer → track` on a fixture clip with a tiny model. |
| **Bindings** | `pytest` | Python API: NumPy round-trip, exception translation, dtype/layout handling. |
| **Benchmark** | google/benchmark | Hot-path latency/throughput; CI regression gate. |
| **Fuzz (opt.)** | libFuzzer | Image decoder / parser robustness against malformed inputs. |

**Patterns**
- Golden-image / golden-tensor fixtures stored in `tests/data/` (keep tiny; use
  Git LFS if they grow).
- Tolerance-based comparison for floating-point GPU/CPU divergence — never exact
  equality on float pixels.
- Mock the `ml::Backend` and registry DAO so detector/tracker logic is tested
  without a real model or database.
- Deterministic seeds for any stochastic step (tracking association tie-breaks).

---

## 3.3 Deployment Strategy

`imgproc` is an **embeddable library**, not a service, so it ships as packages
for its two consumer surfaces — **not** as a container image:

1. **C++ package** — installed via CMake (`find_package(imgproc)`), distributed
   through vcpkg port / Conan recipe with semantic versioning. The reference CLI
   (`imgproc-cli`) installs alongside it.
2. **Python wheel** — `pip install imgproc`. Built with `scikit-build-core` +
   `cibuildwheel` for manylinux + Windows; the CUDA wheel is a separate
   `imgproc-cuda` variant to keep the CPU wheel slim.

> **Why no Docker?** A library has no long-running process to host and no ports
> to expose — there is no "stack" to `docker compose up`. Reproducible builds
> are handled by the pinned CI matrix; if a downstream *service* embeds
> `imgproc`, that service owns its own container and simply `pip install`s or
> links the package. Model weights stay **out** of any such image (mounted or
> pulled from the registry) so images stay small and weights swappable.

---

## 3.4 Environment Management

Configuration is **12-factor**: code is identical across environments; behavior
is driven by environment variables / mounted config. See `.env.example`.

| Variable | Purpose |
| --- | --- |
| `IMGPROC_ENV` | `dev` \| `staging` \| `prod` |
| `IMGPROC_LOG_LEVEL` | `trace`…`critical` (default `warn`) |
| `IMGPROC_DEVICE` | `cpu` \| `cuda:0` \| `cuda:1` … |
| `IMGPROC_MODEL_DIR` | Allow-listed directory for model artifacts |
| `IMGPROC_REGISTRY_URL` | Model-registry DB DSN |
| `IMGPROC_NUM_STREAMS` | CUDA streams for pipelining |

- **dev** — CPU device, verbose logging, SQLite registry, tiny fixture models.
- **staging** — full CUDA, INFO logging, Postgres registry mirror of prod.
- **prod** — CUDA, WARN logging, read-only registry creds, secrets from a
  secrets manager (never `.env` files on disk).

---

## 3.5 Version Control Workflow

**Recommended: trunk-based development with short-lived feature branches.**

```
main (always releasable, protected)
  └── feat/cuda-blur-stream     (short-lived, < 2 days)
  └── fix/nms-tie-break
release tags: v1.4.0  (SemVer; triggers release.yml)
```

**Rationale** — A library with a published ABI and Python wheel benefits from a
single always-green trunk plus **SemVer release tags** that drive packaging.
Long-lived Gitflow branches add merge overhead and risk ABI drift; trunk-based +
feature flags keeps integration continuous. **Branch protection** on `main`
requires green CI + one review. ABI-breaking changes bump the **major** version
and are called out in `CHANGELOG.md`.

Commit style: **Conventional Commits** (`feat:`, `fix:`, `perf:`, `build:`) to
auto-generate the changelog and infer the next SemVer bump.

---

## 3.6 Common Pitfalls (this tech stack)

### CUDA / build
- **Architecture mismatch** — forgetting `CMAKE_CUDA_ARCHITECTURES`; the binary
  then silently won't run on the target GPU. Set explicit arch lists (e.g.
  `75;86;89`) or `native` for local dev.
- **Driver vs. toolkit skew** — a CUDA 12.x build needs a sufficiently new
  driver. Pin the runtime image's CUDA version to the build toolkit.
- **Sync vs. async bugs** — reading device results before the stream finishes.
  Always synchronize/event-wait before D2H reads; never benchmark without a
  `cudaStreamSynchronize`.
- **`cudaMalloc` in the hot loop** — allocations serialize and tank throughput;
  use buffer pools and pinned host memory.

### OpenCV
- **Channel order** — OpenCV is **BGR**, most ML models expect **RGB**. Convert
  explicitly; a silent BGR/RGB swap quietly wrecks accuracy.
- **`cv::Mat` data ownership** — shallow copies share buffers; an unintended
  in-place op corrupts shared data. Be explicit with `.clone()`.
- **Two CUDA worlds** — OpenCV's `cv::cuda` module vs. your own kernels both
  allocate GPU memory; coordinate to avoid double residency.

### pybind11 / Python
- **GIL & long native calls** — release the GIL (`py::gil_scoped_release`) around
  heavy compute so Python threads aren't blocked.
- **NumPy ↔ Image lifetime** — returning a view into a C++-owned buffer that
  outlives it = use-after-free. Use `py::keep_alive` or copy.
- **Layout/stride assumptions** — enforce contiguous, expected dtype arrays at
  the boundary; don't assume C-contiguous `uint8`.
- **ABI / manylinux** — wheels must be built against compatible libstdc++; build
  in the official `cibuildwheel` containers.

### General
- **Float determinism** — GPU vs. CPU and `-ffast-math` change results; test with
  tolerances, not equality.
- **Untrusted images** — decompression bombs and malformed files; enforce
  size/dim limits at decode (see `ARCHITECTURE.md` §2.5).
