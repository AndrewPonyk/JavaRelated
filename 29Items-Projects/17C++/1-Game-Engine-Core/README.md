# Game Engine Core

A data-oriented **C++20** game engine built around an **Entity-Component-System (ECS)**
with cache-friendly memory layouts, a backend-agnostic **render hardware interface (RHI)**,
**rigid-body physics**, a **software audio mixer**, **command/Lua scripting**, and
**procedural terrain** generation.

> **Tech stack:** C++20 · SDL2 · OpenGL · Vulkan · CMake · GoogleTest
> **Targets:** Windows (MSVC) & Linux (Clang/GCC) · ships via **Steam** · CI on **GitHub Actions**

[![CI](https://github.com/example/game-engine-core/actions/workflows/ci.yml/badge.svg)](.github/workflows/ci.yml)

---

## Highlights

- 🧩 **Sparse-set ECS** — `index + generation` entity handles, dense contiguous component
  storage, multi-component `View` iteration, and a deferred `CommandBuffer` for safe
  structural changes during iteration. (ADR: [docs/adr/0001-ecs-sparse-set.md](docs/adr/0001-ecs-sparse-set.md))
- ⚙️ **Data-oriented core** — linear (arena) + pool allocators, a work-stealing-style
  job system (`dispatch` / `parallelFor` / `waitForIdle`), a type-safe event bus, math, logging.
- 🧱 **Physics** — fixed-timestep integration, sort-and-sweep AABB broadphase, MTV
  collision resolution with restitution, and ray casts.
- 🔊 **Audio** — real PCM software mixer: per-voice volume, distance attenuation, and
  constant-power 3D panning, mixed into an interleaved stereo buffer.
- 🎨 **Rendering** — RHI abstraction + a real **render graph** (dependency DAG,
  topological scheduling, dead-pass culling, barrier derivation). Vulkan is an optional
  backend; a headless **Null** backend keeps everything testable.
- 🏔️ **Procedural terrain** — deterministic multi-octave generation (ONNX-ready), meshed
  and cached in the asset database.
- 💾 **Persistence** — built-in JSON parser/serializer, scene save/load round-trip, and a
  file-backed asset/procgen **database**.
- 📜 **Scripting** — a sandboxed command registry (zero-dep) with an optional Lua backend.
- 🧪 **Tested** — 78 unit/integration tests, runnable with zero third-party dependencies.

## Dependency posture

The **engine core and every CPU-side subsystem build and run with no third-party
packages.** External SDKs are *optional backends* that enhance the default:

| Optional dependency | Enables | Default without it |
|---------------------|---------|--------------------|
| Vulkan SDK          | GPU rendering | Headless **Null** RHI |
| SDL2                | OS window + audio device | Headless window / mixer-only audio |
| Lua + sol2          | Lua scripting | Built-in command registry |
| ONNX Runtime        | ML terrain models | Deterministic procedural fallback |
| SQLite3             | SQL asset DB | File-backed JSON database |
| GoogleTest          | test runner | Built-in gtest-compatible shim |

## Quick start (zero dependencies)

Prereqs: a C++20 compiler (MSVC 19.3x / Clang 16+ / GCC 12+), CMake ≥ 3.24, Ninja.

```bash
cmake -S . -B build -G Ninja -DENGINE_BUILD_TESTS=ON
cmake --build build
ctest --test-dir build --output-on-failure     # 78 tests
./build/bin/GameEngineCore --smoke-test         # boots all subsystems headless, exits 0
```

On Windows with the bundled MSVC toolchain, run the same commands from a
*Developer PowerShell* (or after `vcvars64.bat`); binaries land in `build\bin\`.

### Docker (build + test + run, one command)

```bash
docker compose up --build      # builds the engine, runs the test suite, runs the smoke test
```

### Full build with GPU backends (optional)

Install [vcpkg](https://github.com/microsoft/vcpkg) (`VCPKG_ROOT` set) and the Vulkan SDK,
then use the presets:

```bash
cmake --preset dev && cmake --build --preset dev
```

### Vulkan offscreen rendering (verify the GPU path)

The Vulkan backend (`engine/rendering/src/vulkan/`) renders **offscreen** — into an
image, no window required. It is fully written but **needs the Vulkan SDK to compile**
(headers + `glslc`); it has not yet been compiled in the authoring environment, so treat
the first build as the initial verification.

```bash
# 1) Install the Vulkan SDK (sets VULKAN_SDK, provides glslc).  e.g. on Windows:
#    winget install KhronosGroup.VulkanSDK   (then restart the shell)
# 2) Configure with Vulkan enabled:
cmake -S . -B build -G Ninja -DENGINE_ENABLE_VULKAN=ON
cmake --build build
# 3) Run the offscreen smoke test — renders the triangle and writes triangle.ppm:
./build/bin/vulkan_offscreen_triangle      # exit 0 = a triangle was drawn
```

`triangle.ppm` will contain a red/green/blue triangle on a dark background. The same
`VulkanDevice` plugs into the engine's RHI + render graph, so the runtime uses it
automatically once Vulkan is enabled. The on-screen (windowed) swapchain path needs SDL2
and is the next step.

## Repository layout

```
engine/      core · platform · rendering · physics · audio · scripting · resources · procgen · scene
runtime/     the engine executable (composition root)   editor/  ImGui tooling (headless without ImGui)
tests/       78 GoogleTest(-compatible) tests + shim     benchmarks/  google/benchmark micro-benchmarks
assets/      shaders · scenes · config                   migrations/  SQLite schema (optional backend)
tools/       ml (train/export ONNX) · scripts            docs/  PROJECT-PLAN · ARCHITECTURE · TECH-NOTES · adr
```

## Documentation

| Doc | Contents |
|-----|----------|
| [PROJECT-PLAN.md](docs/PROJECT-PLAN.md) | File structure, module boundaries, phased TODO (now complete). |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Pattern, interactions, data flow, scalability, security, errors (Mermaid). |
| [TECH-NOTES.md](docs/TECH-NOTES.md) | CI/CD, testing, deployment, environments, branching, pitfalls. |

## Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| `cmake`/`cl` *not recognized* (Windows) | The MSVC tools aren't on `PATH`. Run from a **Developer PowerShell for VS 2022**, or `call vcvars64.bat` first. |
| `std::format` / `<format>` not found | Compiler isn't C++20-complete. Use MSVC ≥ 19.30 (VS 2022), Clang ≥ 16, or GCC ≥ 13. |
| Build fails on a warning | Warnings are errors by default. Diagnose the warning, or temporarily configure with `-DENGINE_WARNINGS_AS_ERRORS=OFF`. |
| `No tests were found` / `ctest` empty | Configure with `-DENGINE_BUILD_TESTS=ON`. Without system GoogleTest the suite runs as a single aggregate test via the built-in shim. |
| `[Window] built without SDL2 — running headless` | Expected for the zero-dependency build. Install SDL2 + the Vulkan SDK and use `cmake --preset dev` for a real window/GPU. |
| `[RHI] Vulkan ... using Null device` | Same — Vulkan SDK absent. The Null backend is the intended headless/CI default. |
| `stat failed: assets/...` at runtime | Run from a directory containing `assets/` (CMake stages it next to the binary), or set `ENGINE_ASSET_ROOT` (see [`.env.example`](.env.example)). |
| Linker errors about missing engine symbols | Link the modules you use (`engine::core`, `engine::scene`, …); `core` is required by all. |
| `vcpkg` install fails | The manifest `builtin-baseline` is a placeholder — set it to a real vcpkg commit SHA, or just use the dependency-free build (no vcpkg needed). |

## License

[MIT](LICENSE).
