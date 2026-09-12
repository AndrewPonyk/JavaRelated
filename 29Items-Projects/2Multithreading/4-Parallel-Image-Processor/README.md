# Parallel Image Processor

A desktop batch image processor built around a Fork/Join work-stealing compute kernel. Point it
at a directory of images, describe a pipeline (resize, grayscale, blur, sharpen, OpenCV-backed
enhancement), and it fans the batch out across all available cores — via the CLI, the JavaFX UI,
or a local HTTP control API.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design writeup. In short: a
**Modular Layered Monolith** with a **Hexagonal (Ports & Adapters) core**, decomposed at two
levels — `BatchProcessingTask` (`RecursiveTask`, per batch) splits into `TileProcessingAction`
(`RecursiveAction`, per image tile) — so both many-small-images and few-huge-images batches keep
every core busy.

## Modules

| Module | Purpose |
|---|---|
| `pip-core` | Domain model + the Fork/Join engine. Zero external runtime dependencies — this is the hexagonal boundary. |
| `pip-persistence` | SQLite adapter for job/batch history. |
| `pip-native` | OpenCV-backed image enhancement via JNI. |
| `pip-ui` | JavaFX desktop UI. |
| `pip-app` | Assembly point: config, DI wiring, CLI, local control API, `main()`. |

Dependencies only point inward, toward `pip-core`.

## Features

- **Batch processing**: point it at an input directory, get every image processed into an output
  directory, optionally recursing into subdirectories (`-r/--recursive`).
- **Fork/Join parallelism**: work is split across images in a batch *and* across tiles within a
  single large image, keeping every available core busy (`--parallelism N` to override).
- **Pipeline DSL**: chain `grayscale`, `resize:WxH[:fit]`, `blur:RADIUS`, `sharpen:AMOUNT`, and
  OpenCV-backed `enhance:MODE[:STRENGTH]` (`clahe`/`denoise`/`super_resolution`) stages with `>` —
  see [Pipeline DSL](#pipeline-dsl) below.
- **Format conversion**: write as PNG, JPG/JPEG, WebP, BMP, or GIF, with a quality setting for
  lossy formats (`-f/--format`, `--quality`).
- **Three run modes**: CLI batch mode (default), JavaFX desktop UI (`--ui`), or a headless local
  HTTP control API only (`--serve`).
- **Local HTTP control API**: submit/list/inspect batches, stream live progress via SSE, read live
  pool stats, cancel the in-flight batch — see [Local control API](#local-control-api---serve-or---ui)
  below. Loopback-only, optional bearer-token auth, one batch at a time.
- **Job/batch history**: every batch's outcome is persisted to SQLite (unless `--no-history` or
  `db.enabled=false`), queryable via the API or UI, with optional retention-day pruning.
- **Crash-safe resume**: a job left `RUNNING` when the app crashes is detected and marked `FAILED`
  on the next startup instead of leaving history stuck mid-batch.
- **Dry-run planning** (`--dry-run`): prints the planned job count and resolved output paths
  without writing any files.
- **Metadata stripping** (`--strip-metadata`): strip EXIF data from output images.
- **Overwrite control**: refuses to clobber existing output files unless `--overwrite` is passed.
- **Adaptive throttling**: samples JVM heap occupancy and backs off splitting into smaller tiles
  under memory pressure, trading parallelism for GC headroom instead of risking an OOM.
- **Structured logging**: plain text or JSON log output (`log.format`/`PIP_LOG_FORMAT`) for
  machine-readable log ingestion.
- **JFR events**: emits custom Java Flight Recorder events at the batch and tile level, for
  profiling with standard JFR tooling.
- **Live engine stats**: parallelism, active threads, work-steal count, heap utilisation, GC pause
  p99, and throughput (megapixels/sec) — exposed via the API and as individually labeled fields in
  the UI.
- **Internationalization**: UI and CLI text are localizable via `ui.locale`/`PIP_LOCALE`; English
  and Ukrainian (`uk`) are provided out of the box.
- **Layered configuration**: settings resolve through JVM system properties → environment
  variables → `config/application.properties` → compiled-in defaults — see
  [Configuration](#configuration) below.

## Requirements

- JDK 21
- Maven (wrapper not included — use your own `mvn`)
- OpenCV native libraries are only needed for the `enhance:` pipeline stage; without them it falls
  back to a no-op automatically (see [`native/README.md`](native/README.md))

This repo builds with JDK 21 specifically. If your default `java`/`mvn` resolve to an older JDK,
point `JAVA_HOME` at a JDK 21 install for the build:

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

## Build

```bash
# Fast build: compile + unit tests, no static analysis or coverage gates
mvn verify

# Full build: also runs checkstyle, SpotBugs, PMD, and enforces JaCoCo coverage floors
# (line >= 80%, branch >= 70%, per module — see the quality profile in the root pom.xml)
mvn -Pquality verify
```

CI runs `mvn -Pquality verify`; the plain `mvn verify` is for fast local iteration.

## Run

```bash
# CLI batch mode
java -jar pip-app/target/pip-app.jar --in ./photos --out ./out --pipeline "grayscale > resize:800x600"

# JavaFX desktop UI
java -jar pip-app/target/pip-app.jar --ui

# Local HTTP control API only (no window)
java -jar pip-app/target/pip-app.jar --serve
```

`pip-app/target/pip-app.jar` has its runtime classpath in its manifest (`target/lib/`, populated
by `mvn package`/`verify`), so it runs standalone — no shaded/uber jar (JavaFX's native library
loading doesn't survive shading).

`--ui` and `--serve` are mutually exclusive; omit both to run in CLI batch mode.

## CLI usage

```
usage: pip [options]

required:
  -i, --in DIR              input directory
  -o, --out DIR              output directory (must differ from --in)

options:
  -p, --pipeline SPEC         pipeline stages, e.g. "grayscale > resize:800x600 > sharpen:1.5"
  -f, --format FMT             png | jpg | jpeg | webp | bmp | gif
      --quality Q               0.0-1.0, lossy formats only
      --parallelism N          fork/join pool size (default: available processors)
  -r, --recursive              descend into subdirectories
      --overwrite               overwrite existing output files
      --strip-metadata          strip EXIF on output
      --no-history              skip recording this batch in the history database
      --dry-run                 plan the batch and print it, but write nothing
  -q, --quiet                   summary only, nothing on stderr
      --ui                      launch the JavaFX UI instead of batch mode
      --serve                   start the local control API instead of batch mode
  -h, --help
  -V, --version
```

### Pipeline DSL

Stages are separated by `>`, stage arguments by `:`:

| Stage | Syntax |
|---|---|
| Grayscale | `grayscale` (or `greyscale`) |
| Resize | `resize:WxH` or `resize:WxH:fit` |
| Blur | `blur:RADIUS` (1–64) |
| Sharpen | `sharpen:AMOUNT` (0.0–5.0) |
| Enhance (OpenCV) | `enhance:MODE[:STRENGTH]` — `MODE` is `clahe`, `denoise`, or `super_resolution`; `STRENGTH` is 0.0–1.0, default 0.5 |

`enhance` silently falls back to a no-op if the OpenCV native library isn't available for your
platform.

### Exit codes

| Code | Meaning |
|---|---|
| `0` | all jobs succeeded |
| `1` | at least one job failed |
| `2` | bad usage (missing/invalid flags) |
| `3` | startup failure (e.g. `--in` isn't a directory, port already in use) |

## Local control API (`--serve` or `--ui`)

The control API binds to `127.0.0.1` only — never `0.0.0.0` — hardcoded, not configurable. It's
meant for local tools (the JavaFX UI itself, scripts, `curl`), not network access.

| Endpoint | Description |
|---|---|
| `POST /batches` | Submit a batch. Returns `202` + batch id immediately; runs in the background. |
| `GET /batches` | Recent batches from history. |
| `GET /batches/{id}` | One batch's summary. |
| `GET /batches/{id}/events` | SSE stream of that batch's progress, while it's the one running. |
| `GET /stats` | Live pool counters. |
| `POST /cancel` | Cancel the currently running batch. |
| `GET /health` | Liveness check — the only endpoint that needs no token. |

Only one batch runs at a time; submitting a second batch while one is in flight returns `409
Conflict`.

If `api.token` / `PIP_API_TOKEN` is set, every endpoint except `/health` requires
`Authorization: Bearer <token>` (checked with a constant-time comparison). This guards against
other local processes on the same machine (e.g. browser JS hitting the loopback port) — the
loopback bind is what keeps the network out. There are no CORS headers, by design: there's no
legitimate browser client for this API.

```bash
curl -X POST http://127.0.0.1:8137/batches \
  -H "Authorization: Bearer $PIP_API_TOKEN" \
  -d '{"input":"./photos","output":"./out","pipeline":"grayscale"}'

curl -N http://127.0.0.1:8137/batches/<batch-id>/events
```

## Configuration

Settings resolve through four layers, highest priority first:

1. JVM system properties: `-Dpip.<key>=...`
2. Environment variables (see table below)
3. `config/application.properties` next to the working directory you run from (falls back to a
   packaged default on the classpath if that file is absent)
4. Compiled-in defaults

Copy [`.env.example`](.env.example) to `.env` (or export the variables directly) and
[`config/application.properties`](config/application.properties) to customize either way — they
cover the same settings, so pick whichever fits your workflow.

| Property | Environment variable | Default |
|---|---|---|
| `db.path` | `PIP_DB_PATH` | `~/.pip/history.db` |
| `db.enabled` | `PIP_DB_ENABLED` | `true` |
| `batches.parallelism` | `PIP_PARALLELISM` | available processors |
| `batches.tileThresholdPixels` | `PIP_TILE_THRESHOLD` | — |
| `batches.batchThresholdJobs` | `PIP_BATCH_THRESHOLD` | — |
| `batches.maxPixelsPerImage` | `PIP_MAX_PIXELS` | — |
| `output.format` | `PIP_OUTPUT_FORMAT` | source format |
| `output.quality` | `PIP_OUTPUT_QUALITY` | — |
| `output.stripMetadata` | `PIP_STRIP_METADATA` | `false` |
| `output.overwriteExisting` | `PIP_OVERWRITE` | `false` |
| `pipeline.default` | `PIP_PIPELINE` | — |
| `api.enabled` | `PIP_API_ENABLED` | `false` (off unless explicitly enabled) |
| `api.port` | `PIP_API_PORT` | `8137` |
| `api.token` | `PIP_API_TOKEN` | none |
| `retention.days` | `PIP_RETENTION_DAYS` | `0` (disabled) |
| `log.format` | `PIP_LOG_FORMAT` | `text` (or `json`) |
| `ui.locale` | `PIP_LOCALE` | English (BCP 47 tag, e.g. `uk`) |

## Testing & coverage

```bash
mvn test                # unit tests only
mvn -Pquality verify     # unit tests + checkstyle + SpotBugs + PMD + coverage gate
mvn -Pintegration verify # also runs **/*IT.java integration tests via failsafe
```

Coverage floors (JaCoCo, enforced under `-Pquality`) default to 80% line / 70% branch per module,
overridable per module for classes with a legitimate reason to sit lower (e.g. thin JNI-wiring
classes). A JaCoCo HTML report is generated on every `mvn test`/`verify` under
`<module>/target/site/jacoco/index.html`, regardless of whether the gate is enforced.

## Troubleshooting

- **`mvn` picks up the wrong JDK / build fails on language features** — set `JAVA_HOME` to a JDK
  21 install as shown above; this project targets `--release 21`.
- **`enhance:` pipeline stage does nothing** — the OpenCV native library isn't available for your
  platform; this is a documented fallback, not a bug. See [`native/README.md`](native/README.md)
  for build/install instructions.
- **`--serve` exits with startup failure** — the configured port is already in use; pick a
  different `api.port` / `PIP_API_PORT`, or find and stop whatever is bound to the current one.
- **History/API calls silently do nothing with history data** — check `db.enabled`; when it's
  `false` (or the configured `db.path` can't be opened as SQLite), the app degrades to a no-op
  repository rather than failing the batch.
- **A batch submission returns `409 Conflict`** — only one batch runs at a time; wait for the
  current one to finish or `POST /cancel` it first.
