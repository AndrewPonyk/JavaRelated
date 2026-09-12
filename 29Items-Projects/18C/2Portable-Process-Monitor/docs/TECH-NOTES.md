# Portable Process Monitor — Technical Notes

> Actionable guidance for building, testing, shipping, and maintaining `ppmon`.
> See also [PROJECT-PLAN.md](./PROJECT-PLAN.md) and [ARCHITECTURE.md](./ARCHITECTURE.md).

---

## 3.1 CI/CD Pipeline Design

The pipeline is a linear gate on `windows-latest` GitHub Actions runners (MSVC
toolchain), defined in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

```
lint ──▶ configure ──▶ build ──▶ test ──▶ package ──▶ (release on tag)
```

| Stage     | Tooling | Gate |
|-----------|---------|------|
| **lint**  | `clang-format --dry-run --Werror`, `clang-tidy` | Fails on style/static-analysis violations. |
| **configure** | `cmake -S . -B build -DCMAKE_BUILD_TYPE=Release` | Catches CMake errors early. |
| **build** | `cmake --build build --config Release` with `/W4 /WX` | Warnings are errors. |
| **test**  | `ctest --test-dir build --output-on-failure` | All unit tests must pass. |
| **package** | `cpack` → portable ZIP (binary + config sample + README) | Artifact uploaded. |
| **release** | On `v*` tag: attach ZIP to a GitHub Release | Manual approval optional. |

- **Matrix:** Debug + Release, and (later) `x64` + `arm64` to keep the binary
  genuinely portable across Windows architectures.
- **Caching:** Cache the CMake build directory and any vendored deps keyed on the
  `CMakeLists.txt` hash to keep CI under ~2 minutes.
- **Environments:** "dev/staging/prod" map to **CI build → tagged pre-release →
  tagged stable release**, since this is a downloadable binary, not a hosted
  service.

---

## 3.2 Testing Strategy

- **Unit testing framework:** Plain C test executables registered via CTest — no
  third-party dependency required for a portable C project. Each `tests/test_*.c`
  is its own executable using simple `assert`-style macros (`EXPECT_EQ`, etc.) in
  a shared test header. (If richer fixtures are needed later, **Unity** or
  **Criterion** drop in cleanly.)
  - **Coverage target:** ≥ 80% line coverage of the **domain layer**
    (`alerting`, `csv_export`, `sample_store`, `config`). The collection layer is
    excluded from the line target because it is thin Win32 glue best validated by
    integration tests. Measure with **OpenCppCoverage** in CI.
- **What to unit-test (no live OS):** CPU% delta math, ring-buffer wraparound and
  PID-reuse detection, threshold/hysteresis logic, CSV escaping (commas, quotes,
  newlines), config precedence (defaults → INI → CLI).
- **Integration testing:** A guarded suite (`PPMON_RUN_INTEGRATION=1`) runs the
  real loop for N polls against the live OS and asserts invariants: enumeration
  returns the current PID, sums are non-negative, CSV file is well-formed. Kept
  out of the default CTest gate so the unit gate stays deterministic and fast.
- **End-to-end:** A PowerShell smoke script (`scripts/`) launches the built binary
  with `--once --csv out.csv`, waits, then validates the CSV header/row count and
  exit code. This is the artifact-level check run after `package`.

---

## 3.3 Deployment Strategy

This is a **portable, zero-install binary** — there is no server to deploy.

- **Artifact:** a self-contained `ppmon.exe` plus `ppmon.ini.example` and README,
  zipped by CPack. No installer, no registry writes, no admin requirement for the
  default (current-user) mode.
- **Linking:** Statically link the C runtime (`/MT`) so the binary runs on a clean
  Windows install with no VC++ redistributable. PSAPI/PDH/WinSock2 import libs are
  linked normally (they ship with Windows). `ntdll` is linked for
  `NtQuerySystemInformation`.
- **Containerisation:** Not applicable in the Linux/Docker sense — the tool reads
  host kernel structures directly. A Windows container image (`servercore`) is
  *possible* for fleet telemetry scenarios but is explicitly out of scope for v1;
  the portable ZIP is the deliverable.
- **Distribution:** GitHub Releases (ZIP per architecture). Optionally a `winget`
  / Scoop manifest later for one-line install.

---

## 3.4 Environment Management

Configuration precedence (lowest → highest): **built-in defaults → INI file →
CLI flags → environment variables for secrets only**.

- Runtime config lives in `ppmon.ini` (sample: `config/ppmon.ini.example`).
- No build-time secrets. The only environment variable is the optional remote
  stream token. See the `.env.example` template below — note that for a native
  tool this primarily documents CI/release variables, not app config.

```dotenv
# .env.example  — used by CI and the optional network stream, not by core polling

# --- Build / CI ---
CMAKE_BUILD_TYPE=Release          # Debug | Release | RelWithDebInfo
PPMON_TARGET_ARCH=x64             # x64 | arm64

# --- Runtime (optional network streaming) ---
PPMON_LISTEN_ADDR=127.0.0.1       # bind address for the WinSock2 server
PPMON_LISTEN_PORT=9555            # TCP port for the metric stream
PPMON_STREAM_TOKEN=               # shared token required by remote subscribers (leave empty to disable auth)

# --- Test toggles ---
PPMON_RUN_INTEGRATION=0           # 1 to enable live-OS integration tests in CTest

# --- Release (GitHub Actions secrets, do NOT commit real values) ---
GH_RELEASE_TOKEN=                 # provided by CI as a secret, never hard-coded
```

> The repo ships `.env.example` only. Real `.env` files are git-ignored.

---

## 3.5 Version Control Workflow

**Trunk-based development with short-lived feature branches.**

- `master` is always green and releasable; CI gates every PR.
- Feature work happens on `feat/<topic>` branches, squash-merged via PR after the
  full pipeline passes and one review.
- **Releases are tags** (`vMAJOR.MINOR.PATCH`, SemVer) cut from `master`; the
  release stage of CI fires on the tag.
- Rationale: a single-maintainer-friendly native tool with fast CI does not need
  the ceremony of Gitflow's long-lived `develop`/`release` branches. Trunk-based
  keeps integration continuous and history linear, which suits a small C codebase.

---

## 3.6 Common Pitfalls (this stack specifically)

- **`NtQuerySystemInformation` is semi-documented.** The `SYSTEM_PROCESS_INFORMATION`
  layout differs subtly across Windows builds and bitness. Always size with the
  two-call pattern and handle `STATUS_INFO_LENGTH_MISMATCH` in a retry loop with a
  generous slack factor; never assume a fixed struct size.
- **CPU% requires two samples.** A single `GetProcessTimes` call is meaningless —
  CPU usage is `Δ(kernel+user time) / Δ(wallclock) / numCores`. Anchor wallclock
  to `QueryPerformanceCounter`, **not** `GetTickCount`, and divide by core count or
  you will report > 100%.
- **PID reuse.** A PID can be recycled between polls. Key cached handles on
  `(PID, process start time)` and invalidate on mismatch, or you will attribute one
  process's metrics to another.
- **Handle leaks.** Every `OpenProcess` needs a matching `CloseHandle`. In a 1 Hz
  loop over hundreds of processes a single leaked handle per poll exhausts the
  table within hours. Use the single `cleanup:` unwind idiom and run under a handle
  watchdog in integration tests.
- **PDH counter paths are locale-sensitive.** English counter names (`\Processor(_Total)\% Processor Time`)
  break on localized Windows. Use `PdhAddEnglishCounter` (not `PdhAddCounter`) to
  stay locale-independent.
- **`PdhCollectQueryData` needs a warm-up.** Rate counters return 0 (or `PDH_INVALID_DATA`)
  on the very first collection — collect once and discard before the first real read.
- **WinSock2 init ordering.** `WSAStartup` must succeed before any socket call and
  `WSACleanup` must run exactly once at shutdown; forgetting it yields cryptic
  `WSANOTINITIALISED` errors. Guard with a refcount if the server can restart.
- **`/W4 /WX` + Windows headers.** Some SDK headers emit warnings under `/W4`;
  isolate Win32 includes behind a `WIN32_LEAN_AND_MEAN` + push/pop warning pragma
  wrapper so the project's own `/WX` does not choke on SDK noise.
- **Console redirection corrupting CSV.** Keep metrics/CSV on `stdout` and *all*
  logs/UI chrome on `stderr`; mixing them makes piped CSV unparseable.
```
