# Game Engine Core — Project Plan

> **Project:** Game Engine Core
> **Tech Stack:** C++20 · SDL2 · OpenGL · Vulkan · CMake · GoogleTest
> **Domain:** Data-oriented 3D game engine — ECS core, Vulkan rendering, physics, audio, scripting, ML-driven procedural terrain.
> **Targets:** Windows (MSVC), Linux (Clang/GCC) · Distribution via Steam · CI via GitHub Actions.

---

## 0. Reading Guide

| Document | Purpose |
|----------|---------|
| **PROJECT-PLAN.md** (this file) | File/directory layout, module boundaries, phased implementation checklist. |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Architectural pattern, component interactions, data flow, scalability, security, error/logging philosophy. |
| [TECH-NOTES.md](./TECH-NOTES.md) | CI/CD, testing, deployment, environment management, branching, pitfalls. |

> **Note on the brief's template.** The generic deliverable template uses web terms
> ("frontend component", "backend endpoint", "database schema"). This is a native
> C++ engine, so those slots are mapped to their engine-domain equivalents:
> - **Frontend component** → the *presentation/rendering layer* (RHI + Vulkan renderer, an editor-facing API).
> - **Backend endpoint** → a *core engine service* exposing CRUD-style operations (the ECS `Registry`: create/read/update/destroy entities & components).
> - **Database schema** → the *asset & procedural-content database* (SQLite schema in `migrations/`) used by the asset pipeline and the ML terrain cache.

---

## 1. Project File Structure

The layout follows a **modular monorepo**: each engine subsystem is a self-contained
CMake library with a public `include/` surface and a private `src/` implementation.
Consumers (the `runtime` executable, the `editor`, and `tests`) link only against
the public targets they need. This keeps compile-time coupling low and makes the
ECS/data-oriented boundaries explicit.

```text
1-Game-Engine-Core/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                 # lint → configure → build → test (matrix: win/linux, vulkan/gl)
│   │   ├── release.yml            # tagged builds, packaging, Steam upload (steamcmd)
│   │   └── codeql.yml             # static security analysis (C++)
│   └── ISSUE_TEMPLATE/
│       └── bug_report.md
├── cmake/
│   ├── CompilerWarnings.cmake     # warnings-as-errors, per-compiler flags
│   ├── Dependencies.cmake         # find_package / FetchContent wiring
│   ├── StaticAnalysis.cmake       # clang-tidy / cppcheck integration
│   └── modules/
│       └── FindShaderc.cmake      # locate glslc/shaderc for shader compilation
├── docs/
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   ├── TECH-NOTES.md
│   ├── adr/                       # Architecture Decision Records
│   │   └── 0001-ecs-sparse-set.md
│   └── diagrams/                  # exported diagram sources/images
│
├── engine/                        # ── the engine, split into linkable modules ──
│   ├── core/                      # foundation: ECS, memory, math, jobs, events, logging
│   │   ├── include/engine/core/
│   │   │   ├── Types.hpp
│   │   │   ├── Log.hpp
│   │   │   ├── Assert.hpp
│   │   │   ├── Result.hpp         # error-handling vocabulary type (no exceptions in hot paths)
│   │   │   ├── ecs/
│   │   │   │   ├── Entity.hpp
│   │   │   │   ├── SparseSet.hpp  # cache-friendly component storage
│   │   │   │   ├── ComponentPool.hpp
│   │   │   │   ├── Registry.hpp   # the "backend endpoint": entity/component CRUD
│   │   │   │   ├── View.hpp       # multi-component iteration
│   │   │   │   └── System.hpp
│   │   │   ├── memory/
│   │   │   │   ├── Allocator.hpp
│   │   │   │   ├── LinearAllocator.hpp
│   │   │   │   └── PoolAllocator.hpp
│   │   │   ├── math/
│   │   │   │   ├── Vec.hpp
│   │   │   │   └── Mat4.hpp
│   │   │   ├── jobs/
│   │   │   │   └── JobSystem.hpp  # work-stealing task scheduler
│   │   │   └── event/
│   │   │       └── EventBus.hpp
│   │   ├── src/
│   │   │   ├── Log.cpp
│   │   │   ├── ecs/Registry.cpp
│   │   │   ├── memory/PoolAllocator.cpp
│   │   │   └── jobs/JobSystem.cpp
│   │   └── CMakeLists.txt
│   │
│   ├── platform/                  # windowing (SDL2), input, filesystem, timing
│   │   ├── include/engine/platform/{Window,Input,Filesystem,Clock}.hpp
│   │   ├── src/{Window,Input}.cpp
│   │   └── CMakeLists.txt
│   │
│   ├── rendering/                 # the "frontend": RHI abstraction + Vulkan/GL backends
│   │   ├── include/engine/rendering/
│   │   │   ├── rhi/{RHIDevice,RHITypes,Swapchain}.hpp
│   │   │   ├── Renderer.hpp
│   │   │   ├── RenderGraph.hpp
│   │   │   └── vulkan/{VulkanDevice,VulkanRenderer}.hpp
│   │   ├── src/{Renderer,RenderGraph}.cpp
│   │   ├── src/vulkan/{VulkanDevice,VulkanRenderer}.cpp
│   │   └── CMakeLists.txt
│   │
│   ├── physics/                   # rigid-body / collision world
│   │   ├── include/engine/physics/PhysicsWorld.hpp
│   │   ├── src/PhysicsWorld.cpp
│   │   └── CMakeLists.txt
│   │
│   ├── audio/                     # mixer + spatial audio
│   │   ├── include/engine/audio/AudioEngine.hpp
│   │   ├── src/AudioEngine.cpp
│   │   └── CMakeLists.txt
│   │
│   ├── scripting/                 # Lua (sol2) gameplay scripting + hot-reload
│   │   ├── include/engine/scripting/ScriptEngine.hpp
│   │   ├── src/ScriptEngine.cpp
│   │   └── CMakeLists.txt
│   │
│   ├── resources/                 # asset manager, (de)serialization, asset DB access
│   │   ├── include/engine/resources/{AssetManager,Serialization,AssetDatabase}.hpp
│   │   ├── src/{AssetManager,AssetDatabase}.cpp
│   │   └── CMakeLists.txt
│   │
│   ├── procgen/                   # ML-based procedural terrain generation
│   │   ├── include/engine/procgen/{TerrainGenerator,TerrainModel}.hpp
│   │   ├── src/TerrainGenerator.cpp
│   │   └── CMakeLists.txt
│   │
│   └── scene/                     # scene graph, serialization, world assembly
│       ├── include/engine/scene/Scene.hpp
│       ├── src/Scene.cpp
│       └── CMakeLists.txt
│
├── runtime/                       # the shippable engine executable
│   ├── src/main.cpp               # window + engine loop bootstrap
│   └── CMakeLists.txt
│
├── editor/                        # in-engine tooling (ImGui) — optional target
│   ├── src/EditorApp.cpp
│   └── CMakeLists.txt
│
├── tests/                         # GoogleTest unit/integration suites
│   ├── core/{test_registry,test_sparse_set,test_pool_allocator}.cpp
│   ├── rendering/test_render_graph.cpp
│   ├── procgen/test_terrain_generator.cpp
│   └── CMakeLists.txt
│
├── benchmarks/                    # micro-benchmarks (google/benchmark)
│   └── bench_ecs.cpp
│
├── assets/                        # runtime data, version-controlled
│   ├── shaders/{triangle.vert,triangle.frag}
│   ├── scenes/example.scene.json
│   └── config/engine.default.json
│
├── tools/
│   ├── ml/                        # Python: train & export terrain models (ONNX)
│   │   ├── train_terrain.py
│   │   └── requirements.txt
│   └── scripts/
│       ├── compile_shaders.sh     # glslc *.vert/.frag → SPIR-V
│       └── setup_dev.ps1
│
├── migrations/                    # asset/content database schema (SQLite)
│   ├── 001_initial_schema.sql
│   └── 002_procgen_cache.sql
│
├── external/                      # third-party (vcpkg manifest preferred; submodules fallback)
│   └── README.md
│
├── .clang-format
├── .clang-tidy
├── .editorconfig
├── .gitignore
├── .gitattributes
├── .dockerignore
├── .env.example
├── CMakeLists.txt                 # top-level: options, subdirs, install/export
├── CMakePresets.json              # named configure/build/test presets
├── vcpkg.json                     # dependency manifest (SDL2, vulkan-headers, glm, ...)
├── Dockerfile                     # reproducible Linux build/CI image
├── LICENSE
└── README.md
```

### 1.1 Module dependency rules

```text
core  ◄── platform ◄── rendering ◄── scene ◄── runtime/editor
  ▲          ▲             ▲           ▲
  └── physics, audio, scripting, resources, procgen ─┘
```

- **`core` depends on nothing** in the engine (only the std lib + a math lib). Everything depends on `core`.
- No module may include another module's `src/` headers — only its public `include/` surface.
- `runtime` and `editor` are the only targets allowed to wire concrete subsystems together (composition root).
- Cyclic dependencies are forbidden and enforced at the CMake target level.

---

## 2. Implementation TODO List

Priority legend: 🔴 high · 🟡 medium · 🟢 lower. Status: ✅ done · 🔶 optional backend
(the default dependency-free path is implemented; the listed SDK adds the GPU/native variant).

### Phase 1 — Foundation 🔴

- [x] **Build system**: top-level `CMakeLists.txt`, `CMakePresets.json`, `vcpkg.json` manifest, warnings-as-errors. ✅
- [x] **CI skeleton**: GitHub Actions matrix (Windows-MSVC, Linux-Clang) — configure + build + test on every PR. ✅
- [x] **`core/Types`, `Log`, `Assert`, `Result`**: logging, assertions, and the `Result<T,E>` error vocabulary. ✅
- [x] **Memory allocators**: `LinearAllocator` (frame arena) and `PoolAllocator` (fixed-size blocks). ✅
- [x] **ECS core (data-oriented)**: `SparseSet`, `ComponentPool`, `Registry` (CRUD), `View`, `System`, `CommandBuffer`. ✅
- [x] **Job system**: thread pool with `dispatch`/`parallelFor`/`waitForIdle` (single shared queue; work-stealing is a future upgrade). ✅
- [x] **Platform**: `Window` (SDL2 🔶 / headless), `Input`, high-resolution `Clock`, `Filesystem`. ✅
- [x] **Unit tests** for ECS, allocators, job system, math, events (the correctness bedrock). ✅

### Phase 2 — Core Features 🟡

- [x] **RHI abstraction** (`RHIDevice`, `Swapchain`, command/buffer/texture handles) — backend-agnostic, with a Null device. ✅
- [x] **Render graph**: dependency DAG, topological scheduling, dead-pass culling, barrier derivation. ✅
- [~] **Vulkan backend (offscreen)**: instance → device → render pass → triangle pipeline → command recording → image readback, all implemented; the `vulkan_offscreen_triangle` sample renders the triangle to a PPM. 🔶 **Build-ready behind `ENGINE_HAS_VULKAN`, but written-not-yet-compiled** (the authoring machine has the Vulkan runtime but no SDK headers/`glslc`). The on-screen swapchain/window path still needs SDL. See README → "Vulkan offscreen rendering".
- [ ] **OpenGL backend** (fallback path) behind the same RHI. 🔶
- [x] **Resources**: `AssetManager` (async load via job system), `Serialization` (JSON + content hashing), `AssetDatabase` (file-backed; SQLite 🔶). ✅
- [x] **Scene**: ECS-backed scene, JSON (de)serialization round-trip, spawn helpers. ✅
- [x] **Physics**: sort-and-sweep AABB broadphase + integration + MTV resolution + raycast, as ECS systems. ✅
- [x] **Audio**: software mixer + 3D spatialization, listener-relative panning/attenuation. ✅
- [x] **Scripting**: command-registry scripting + lifecycle hook (`on_update`); Lua/sol2 🔶. ✅
- [x] **ProcGen**: `TerrainGenerator` (deterministic multi-octave; ONNX 🔶) + tile mesh + DB cache. ✅
- [x] **Integration tests**: headless engine tick (smoke test), scene round-trip, asset DB persistence, terrain delivery. ✅

### Phase 3 — Polish & Optimization 🟢

- [x] **Editor**: scene-hierarchy walk (ImGui 🔶 for the full dockspace/inspector). ✅
- [x] **Hardening**: ASan/UBSan job in CI; bounds-checked parsers; validation in RHI/DB/physics. ✅
- [x] **Benchmarks**: `bench_ecs` (google/benchmark 🔶) for the ECS hot path. ✅
- [ ] **Profiling/telemetry**: frame timings present; GPU timestamps + Tracy. 🔶
- [ ] **Performance**: deeper SoA splits + multithreaded system scheduling. 🟢
- [ ] **Hot-reload**: shaders/scripts/assets at runtime. 🟢
- [ ] **Packaging**: Steam depot + `steamcmd` upload (release.yml) + crash reporting. 🟢
- [ ] **Docs**: Doxygen API generation. 🟢

> **Status:** the engine builds dependency-free and **78 tests pass** (`ctest`), the
> headless runtime smoke test exits 0, and the editor runs. Remaining unchecked items
> are GPU/native backends (need an SDK + device) or longer-horizon polish.

---

## 3. Definition of Done (per module)

A module is "done" when it: (1) compiles warnings-clean on all CI compilers,
(2) has unit tests with meaningful assertions (not just construction),
(3) exposes a documented public header surface, (4) has no leaks under ASan,
and (5) is referenced by at least one integration test or the `runtime` loop.
