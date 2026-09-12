# Parallel Image Processor — Project Plan

> **Status:** Architecture baseline (scaffolded)
> **Owner:** Platform / Imaging
> **Tech Stack:** Java 21 · ForkJoinPool · `RecursiveAction` / `RecursiveTask` · Shenandoah GC · JavaFX 21 · ImageIO · `StampedLock` · OpenCV via JNI
> **Deployment:** Local desktop execution (no containers, no cloud runtime)

---

## 0. Executive Summary

Parallel Image Processor (**PIP**) is a **desktop batch image processing application**. It ingests a
directory of images, applies an ordered pipeline of operations (resize → filter → watermark →
ML enhance), and writes results to an output directory — saturating all available CPU cores using a
**two-level divide-and-conquer** strategy on a `ForkJoinPool`:

| Level | Abstraction | Splits on | Rationale |
|-------|-------------|-----------|-----------|
| **L1 — Batch** | `RecursiveTask<BatchResult>` | List of `ImageJob` | Coarse-grained; each image is independent → near-linear speedup |
| **L2 — Tile** | `RecursiveAction` | Horizontal bands of one `BufferedImage` | Fine-grained; keeps a single 8000×6000 image from becoming a serial tail |

Job/EXIF **metadata is read far more often than written** (UI table refresh, progress polling,
pipeline lookups) so the metadata cache is guarded by a **`StampedLock` with optimistic reads** —
no cache-line write traffic on the hot read path.

Because the workload allocates large short-lived `int[]`/`BufferedImage` buffers, the JVM runs with
**Shenandoah** (concurrent evacuation) to keep GC pauses sub-10 ms and the JavaFX UI thread smooth.

**Non-goals (explicit):** no Docker, no Kubernetes, no server deployment, no multi-tenant auth, no
cloud storage. PIP is a single-user local tool; every architectural decision below is calibrated to
that scale.

---

## 1. Project File Structure

### 1.1 Top-level layout

```text
4-Parallel-Image-Processor/
│
├── pom.xml                          # Parent POM — aggregator + dependencyManagement + toolchain
├── README.md
├── LICENSE
├── .gitignore  .gitattributes  .editorconfig
├── .env.example                     # Local runtime config template (see TECH-NOTES §3.4)
├── Opus 5.txt                       # Model marker file
│
├── .mvn/
│   ├── jvm.config                   # Maven JVM flags (heap for large-image tests)
│   └── maven.config                 # Default CLI flags (-T1C, --no-transfer-progress)
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                   # PR gate: lint → test → build → verify → jlink smoke
│   │   ├── release.yml              # Tag-driven: jpackage native installers (win/linux/mac)
│   │   ├── native-build.yml         # CMake build of the OpenCV JNI shim (3 OS matrix)
│   │   └── codeql.yml               # Static security analysis (java + cpp)
│   ├── ISSUE_TEMPLATE/{bug_report.md,feature_request.md}
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── dependabot.yml
│
├── config/                          # Tool configuration — checked in, referenced by POMs
│   ├── checkstyle/{checkstyle.xml,suppressions.xml}
│   ├── spotbugs/exclude.xml
│   ├── pmd/ruleset.xml
│   ├── jvm/
│   │   ├── shenandoah.vmoptions     # Production GC/JIT flags
│   │   └── debug.vmoptions          # +GC logging, +JFR, assertions on
│   └── application.properties       # Default runtime config (overridden by env / --D flags)
│
├── docs/
│   ├── PROJECT-PLAN.md              # ← this file
│   ├── ARCHITECTURE.md
│   ├── TECH-NOTES.md
│   ├── adr/                         # Architecture Decision Records — currently empty; planned,
│   │                                 not yet authored. 0001 (Vector API) and 0002 (FFM API) are
│   │                                 the first real entries, added alongside the JMH benchmarks below.
│   └── diagrams/                    # Exported Mermaid/PlantUML renders (optional)
│
├── migrations/                      # Canonical SQL — SQLite, forward-only, numbered
│   ├── V001__init_schema.sql
│   ├── V002__watermark_presets.sql
│   └── V003__indexes_and_views.sql
│
├── native/                          # OpenCV JNI shim (C++), built out-of-band by CMake
│   ├── CMakeLists.txt
│   ├── include/com_parallelimage_nativebridge_NativeImageEnhancer.h
│   ├── src/pip_enhance.cpp
│   ├── cmake/FindOpenCVLocal.cmake
│   └── README.md                    # How to build on win/linux/mac
│
├── scripts/                         # Developer entry points (no Docker anywhere)
│   ├── build.cmd / build.sh
│   ├── run-ui.cmd  / run-ui.sh      # JavaFX desktop app + Shenandoah flags
│   ├── run-cli.cmd / run-cli.sh     # Headless batch mode
│   ├── build-native.cmd / .sh       # CMake → libpip_enhance
│   └── bench.sh                     # Scaling benchmark harness (1..N parallelism)
│
├── samples/                         # Test fixtures / demo assets
│   ├── input/  output/  watermarks/
│
├── benchmarks/                      # JMH harness — currently empty; module registration + the
│                                     first benchmarks (tile-threshold, Vector API, JNI/FFM) land
│                                     together, see §2 Phase 3.1
│
└── <modules — see §1.2>
```

### 1.2 Maven module graph

Five modules, dependencies strictly one-directional (no cycles). `pip-core` has **zero external
runtime dependencies** — it is pure JDK, which keeps it fast to test and trivially portable.

```text
pip-core ◄──── pip-persistence ◄──┐
   ▲                              │
   ├──────── pip-native ──────────┼──── pip-app  (launcher: CLI + local HTTP control API)
   │                              │        ▲
   └──────── pip-ui ──────────────┘        │
                                     (assembles everything)
```

| Module | Responsibility | External deps |
|--------|----------------|---------------|
| `pip-core` | Domain model, fork/join engine, filters, `StampedLock` metadata store, ports (interfaces), SPI | **none** (JDK only) |
| `pip-persistence` | SQLite adapter for the `JobRepository` port, migration runner | `sqlite-jdbc` |
| `pip-native` | JNI bridge to OpenCV `ImageEnhancer` SPI impl + graceful pure-Java fallback | none (loads `.dll`/`.so`) |
| `pip-ui` | JavaFX views, view-models, background `Task` plumbing | `javafx-{base,graphics,controls,swing}` |
| `pip-app` | Wiring/DI, config, CLI, local HTTP control endpoint, `main()` | none (JDK `httpserver`) |

#### `pip-core` source tree (the heart of the project)

```text
pip-core/src/main/java/com/parallelimage/core/
├── model/
│   ├── ImageJob.java             # record: id, source, target, options, status
│   ├── JobStatus.java            # enum: PENDING → RUNNING → {COMPLETED, FAILED, CANCELLED}
│   ├── ImageMetadata.java        # record: dimensions, format, byte size, EXIF subset
│   ├── ProcessingOptions.java    # record + builder: ops list, quality, tile threshold
│   ├── Tile.java                 # record: x, y, width, height  (+ split helpers)
│   ├── BatchResult.java          # record: succeeded, failed, durations (mergeable monoid)
│   └── JobOutcome.java           # sealed: Success | Failure(cause)
├── pipeline/
│   ├── ImageOperation.java       # sealed interface: Resize|Grayscale|Blur|Sharpen|Watermark|Enhance
│   ├── OperationPipeline.java    # ordered application, immutable
│   └── TileKernel.java           # functional: apply(src, dst, Tile)  ← the parallel unit
├── filter/
│   ├── ResizeFilter.java         # bilinear, whole-image (not tileable — see note in code)
│   ├── GrayscaleFilter.java      # tileable, ITU-R BT.709 luma
│   ├── BoxBlurFilter.java        # tileable with halo/apron overlap
│   ├── SharpenFilter.java        # tileable 3×3 convolution
│   └── WatermarkFilter.java      # Graphics2D composite, whole-image
├── fork/
│   ├── ForkJoinConfig.java       # pool construction, parallelism, thread factory, ASYNC mode
│   ├── TileProcessingAction.java # RecursiveAction — L2 intra-image split
│   ├── BatchProcessingTask.java  # RecursiveTask<BatchResult> — L1 inter-image split
│   └── CancellationToken.java    # cooperative cancellation across the task tree
├── metadata/
│   └── MetadataStore.java        # StampedLock: tryOptimisticRead → validate → readLock fallback
├── io/
│   ├── ImageLoader.java          # ImageIO read + normalization to TYPE_INT_RGB/ARGB
│   ├── ImageSink.java            # ImageIO write + atomic temp-file rename
│   └── ImageDiscovery.java       # directory walk, extension filter, symlink guard
├── port/
│   └── JobRepository.java        # outbound port implemented by pip-persistence
├── spi/
│   ├── ImageEnhancer.java        # ServiceLoader contract (OpenCV impl lives in pip-native)
│   └── PassthroughEnhancer.java  # always-available fallback, priority 0
├── progress/
│   ├── ProgressListener.java     # observer; UI adapts to JavaFX Platform.runLater
│   └── ProgressEvent.java        # record: jobId, phase, completed, total
├── engine/
│   ├── ImageProcessingEngine.java# façade: submit(batch) → CompletableFuture<BatchResult>
│   └── EngineStats.java          # steal count, queued task count, throughput
├── error/
│   ├── PipException.java         # root unchecked
│   ├── ImageIoException.java
│   └── PipelineException.java
└── util/
    ├── Preconditions.java
    └── StopWatch.java
```

---

## 2. Implementation TODO List

Prioritized, dependency-ordered. Each item is small enough to be one PR.
`[x]` = delivered in this scaffold; `[ ]` = remaining work.

### Phase 1 — Foundation (HIGH priority) — *"a batch runs end-to-end, headless"*

**1.1 Build & tooling**
- [x] Parent POM with `maven.compiler.release=21`, module aggregation, `dependencyManagement`
- [x] Per-module POMs; `pip-core` dependency-free
- [x] `.editorconfig`, `.gitignore`, `.gitattributes` (binary fixture handling)
- [x] Checkstyle / SpotBugs / PMD configs wired into the `verify` phase
- [x] `scripts/run-*.{cmd,sh}` with Shenandoah + module-path flags
- [ ] **Pin the JDK via `~/.m2/toolchains.xml`** so Maven cannot silently build with JDK 11
- [ ] `mvnw` Maven wrapper committed for reproducible local builds

**1.2 Domain model**
- [x] `ImageJob`, `ImageMetadata`, `ProcessingOptions`, `Tile`, `BatchResult`, `JobOutcome`
- [x] `ImageOperation` sealed hierarchy + exhaustive `switch` dispatch
- [x] Validate `ProcessingOptions` invariants at construction — the canonical constructor
      validates via `Preconditions` (non-null/non-blank, quality ∈ [0,1], positive thresholds);
      covered by `ProcessingOptionsTest`
- [x] `Tile.split()` property tests — `TileTest.recursiveSplitPartitionsExactly` asserts no-gap,
      no-overlap, full coverage of the source rect across many CSV cases

**1.3 Fork/join engine**
- [x] `ForkJoinConfig` — parallelism, named worker threads, uncaught-exception handler
- [x] `TileProcessingAction` (L2) with `SEQUENTIAL_THRESHOLD` and `invokeAll` fork/compute split
- [x] `BatchProcessingTask` (L1) returning a mergeable `BatchResult`
- [x] `CancellationToken` checked at every `compute()` entry
- [ ] **Calibrate `SEQUENTIAL_THRESHOLD`** empirically (target: 10k–100k pixels/leaf) via JMH
- [x] Halo/apron handling for convolution kernels so tile seams are artefact-free — the
      blur/sharpen filters read neighbours across tile boundaries from the **immutable source**
      image, which is correct; verified byte-for-byte by `TileKernelDeterminismTest`'s
      `tiledOutputMatchesSequential`/`parallelismDoesNotChangeOutput`
- [x] `ManagedBlocker` for JNI work — `NativeImageEnhancer.ensureModelLoaded()` already wraps the
      one-time model-load I/O in a `ForkJoinPool.ManagedBlocker` (`ModelLoad`); the per-image
      `enhance()` call deliberately does **not** get one — it is CPU-bound, not idle, so wrapping
      it would only start compensating threads that contend for already-busy cores (see the
      class's own javadoc)

**1.4 I/O**
- [x] `ImageLoader` / `ImageSink` with ImageIO, atomic write via temp-file + `ATOMIC_MOVE`
- [x] `ImageDiscovery` directory walk with extension allow-list and symlink loop guard
- [x] Explicitly disable ImageIO disk cache — `ImageLoader`'s static initializer already calls
      `ImageIO.setUseCache(false)`
- [ ] Progressive/large-TIFF streaming path via `ImageReader.readTile` for >100 MP inputs

**1.5 Metadata concurrency**
- [x] `MetadataStore` with `StampedLock` optimistic read → validate → pessimistic fallback
- [x] `tryConvertToWriteLock` upgrade path for read-modify-write
- [x] Concurrency stress test: N readers + M writers, assert no torn reads —
      `MetadataStoreTest.concurrentReadsAreConsistent` runs 4 writers + 4 readers and asserts the
      aggregate invariant (`totalPixels == count * perRecordPixels`) never observes a torn read

**1.6 Headless CLI**
- [x] `Main` with `--headless` / `--ui` dispatch
- [x] `CliOptions` parser + `CliRunner`
- [x] Exit-code contract: `CliRunner.EXIT_OK/EXIT_FAILURES/EXIT_USAGE/EXIT_STARTUP` (`0`/`1`/`2`/
      `3`), tested in `MainTest`/`CliRunnerTest`
- [x] `--dry-run` mode that resolves the job list and prints the plan without writing

### Phase 2 — Core features (MEDIUM priority) — *"the product is usable"*

**2.1 Persistence**
- [x] `migrations/V001..V003` SQLite DDL (jobs, batches, metadata, presets, indexes, views)
- [x] `MigrationRunner` — forward-only, checksum-verified, single-transaction per migration
- [x] `SqliteJobRepository` implementing the `JobRepository` port
- [x] `PRAGMA journal_mode=WAL` + `busy_timeout=5000` (+ `foreign_keys=ON`, `synchronous=NORMAL`) applied on every connection in `Database.applyPragmas`
- [x] Batched transactional inserts — the `pip-db-writer` daemon thread drains its write queue in groups of up to 256 and commits each group in one transaction
- [x] Retention job: one-shot purge of completed batches older than `retention.days` at startup (`ServiceRegistry.open`), not a periodic scheduler

**2.2 JavaFX UI**
- [x] `PipApplication` + `MainView` shell, `app.css` theme
- [x] `BatchProcessorView` — loading / error / empty / loaded states, `TableView` of jobs
- [x] `RepositoryQueryTask` — off-FX-thread data fetch via `javafx.concurrent.Task`
- [x] `ProgressBridge` — coalesced `Platform.runLater` so the FX thread is never flooded
- [x] Drag-and-drop folder onto the window to create a batch
- [x] Before/after preview pane with a split-slider (`SwingFXUtils.toFXImage`)
- [x] Operation-pipeline editor (reorderable list, per-op parameter form)
- [x] Cancel button wired to `CancellationToken` via `viewModel.cancel()` → `engine.cancel()` — deliberately *not* `ForkJoinTask.cancel(true)`, which does not interrupt a task already inside `compute()` (see `CancellationToken` javadoc, `docs/TECH-NOTES.md` §3.6 A6)
- [x] Persist window geometry & last-used directories to `~/.pip/ui.properties`

**2.3 Native / OpenCV**
- [x] `ImageEnhancer` SPI + `PassthroughEnhancer` fallback + `ServiceLoader` registration
- [x] `NativeImageEnhancer` JNI declarations + `NativeLibraryLoader` (extract-from-jar + `System.load`)
- [x] `native/` CMake project skeleton with the JNI signature contract
- [x] `pip_enhance.cpp`: CLAHE + `fastNlMeansDenoisingColored` + optional DNN super-resolution (EDSR x2, guarded by `PIP_WITH_DNN_SUPERRES`)
- [x] Pixels passed as a **direct `ByteBuffer`** (zero-copy) rather than `int[]` critical arrays
- [x] Crash isolation: `-Xcheck:jni` in CI (`native-build.yml`); documented that a native segfault kills the JVM (`native/README.md` §7, Risk Register R1)
- [x] Per-OS artifacts published by `native-build.yml` into `pip-native/src/main/resources/native/`

**2.4 Local control API** (headless automation without a server deployment)
- [x] `BatchJobController` on the JDK `com.sun.net.httpserver`, bound to `127.0.0.1` only
- [x] `JobRequestValidator` — path traversal, extension, size, and range validation
- [x] Hand-rolled `Json` writer (no Jackson dependency for ~200 lines of output)
- [x] Loopback-only enforcement test + shared-secret bearer token for scripted clients (`BatchJobControllerTest`)
- [x] `GET /batches/{id}/events` — SSE progress stream; `POST /batches` is async (`202` + batch id)

### Phase 3 — Polish & optimization (LOWER priority)

**3.1 Performance**
- [ ] JMH suite: tile-threshold sweep, parallelism 1..2N, GC comparison (G1 vs Shenandoah vs ZGC)
- [ ] Publish a scaling curve + Amdahl analysis in `docs/PERFORMANCE.md`
- [ ] Evaluate the Vector API (`jdk.incubator.vector`) for the grayscale/convolution inner loops
- [ ] Evaluate `Arena`/`MemorySegment` (FFM API) as a replacement for hand-written JNI
- [ ] Off-heap pixel buffer pool to cut allocation churn on large batches
- [ ] Adaptive parallelism: back off when heap occupancy > 80 % (avoid OOM on huge images)

**3.2 Observability**
- [ ] JFR event types for `TileProcessed` / `JobCompleted`; ship a `.jfc` profile
- [ ] `EngineStats` panel in the UI: steal count, queue depth, MP/s, GC pause p99
- [ ] Structured JSON logging via `System.Logger` + a custom handler

**3.3 Robustness & distribution**
- [ ] `jlink` custom runtime image, then `jpackage` → `.msi` / `.deb` / `.dmg` — **Deferred:**
      `jpackage` needs per-OS build agents and a WiX toolset beyond this round's local capacity;
      revisit with a proper multi-OS release pipeline
- [ ] Crash-safe resume: on startup, re-queue jobs left in `RUNNING`
- [ ] Mutation testing (PIT) on `pip-core`; target ≥ 70 % mutation score
- [ ] Accessibility pass on the UI (focus order, contrast, keyboard-only operation)
- [ ] i18n via `ResourceBundle`

---

## 3. Definition of Done (per PR)

1. `mvn -T1C clean verify` green with **JDK 21** (Checkstyle + SpotBugs + PMD + tests).
2. New concurrent code has a test that actually exercises >1 thread.
3. Public API has Javadoc stating **thread-safety** and **blocking behaviour**.
4. No new `synchronized` on a hot read path without a note on why `StampedLock` was unsuitable.
5. `docs/adr/` updated if a structural decision changed.

## 4. Risk Register

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R1 | JNI segfault takes down the whole JVM | Med | High | Validate every buffer on the Java side; `PassthroughEnhancer` fallback; `-Xcheck:jni` in CI; consider an out-of-process native worker if instability persists |
| R2 | `OutOfMemoryError` on very large images × high parallelism | High | High | Cap concurrent decodes; heap-aware admission control; stream tiles for >100 MP |
| R3 | Blocking I/O starves the `ForkJoinPool` | High | Med | `ForkJoinPool.ManagedBlocker` around decode/encode/JNI; keep a separate I/O executor |
| R4 | SQLite `SQLITE_BUSY` under concurrent writers | Med | Med | WAL mode, single writer thread, `busy_timeout`, batched transactions |
| R5 | Shenandoah unavailable in the user's JVM build | Low | Low | Detect at startup, log, fall back to G1 |
| R6 | Tile seams / off-by-one artefacts in convolution filters | Med | Med | Read halo from the immutable source; golden-image regression tests |
| R7 | JavaFX FX-thread flooding by progress events | Med | Med | Coalesce events; ≤ 30 UI updates/sec |
