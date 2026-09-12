# Network Packet Analyzer — Technical Notes

> Actionable engineering guidance for building, testing, shipping, and operating `npa`.
> Pairs with `PROJECT-PLAN.md` (what) and `ARCHITECTURE.md` (how it's shaped).

---

## 3.1 CI/CD Pipeline Design

Target: **GitHub Actions** (mirrors trivially to GitLab CI / Jenkins). Two workflows:
`ci.yml` (every push/PR) and `release.yml` (on tag).

### Stages (in order, fail-fast)

```text
 ┌─ lint ──────────┐  clang-format --dry-run --Werror
 │                 │  clang-tidy  (warnings-as-errors on our headers)
 │                 │  cppcheck    --enable=warning,portability
 ├─ build matrix ──┤  {gcc, clang} × {release, asan+ubsan}
 │                 │  -Wall -Wextra -Wpedantic -Werror
 ├─ test ──────────┤  make test            (unit tests, all configs)
 │                 │  make test ASAN=1      (under sanitizers; leaks fail the build)
 ├─ smoke ─────────┤  npa -r tests/fixtures/sample.pcap --headless --count 100
 └─ package ───────┘  (release.yml only) static binary + tarball + checksums
```

### Principles
- **Warnings are errors in CI** (`-Werror`), but *not* in local dev builds (so WIP
  compiles). Gate it behind a `STRICT=1` make var that CI sets.
- **Sanitizers are a first-class build config**, not an afterthought. ASan+UBSan catch
  the bugs that matter most in C (OOB reads in decoders, use-after-free across threads).
  A leak or UB report **fails the build**.
- **Two compilers** (gcc + clang) — each catches diagnostics the other misses.
- **Headless smoke test**: the binary must run a real pcap end-to-end without a TTY
  (UI disabled via `--headless`) so CI can exercise capture→decode→detect.
- Cache `apt` deps; the toolchain install dominates wall-clock otherwise.

---

## 3.2 Testing Strategy

### Unit testing
- **Framework**: a tiny header-only harness (`tests/test_common.h`) — `TEST()` +
  `ASSERT*` macros, zero dependencies, perfect for C and easy in CI. If the suite
  grows, swap in **Unity** (`tests/unity/` is reserved for it) or **Criterion**.
- **What to unit test hardest** (highest bug-density, no I/O needed):
  - `buffer/ring_buffer` — fill/drain, wrap-around, full/empty edges, concurrent
    producer/consumer stress (spawn 1 producer + 1 consumer thread, assert no loss/dupes).
  - `decode/*` — feed **crafted byte arrays**, including deliberately **malformed/truncated**
    frames; assert correct fields *and* correct `NPA_ERR_TRUNCATED` on short input.
  - `analysis/anomaly` — table-driven: rule + sample packet → expected alert set.
- **Coverage target**: **≥ 80% lines** on `decode/`, `buffer/`, `analysis/`
  (the pure, logic-heavy core). UI and capture glue are exercised via smoke/integration,
  not chased for line coverage. Measure with `gcov`/`lcov` (`make coverage`).

### Integration testing
- Drive the real binary against **fixture `.pcap` files** (`tests/fixtures/`): known
  captures with known protocol mixes and known anomalies. Assert on `--headless --json`
  summary output (packet counts per protocol, alerts fired).
- Round-trip test: capture on a loopback veth, generate traffic (e.g. `tcpreplay` or a
  small socket script), assert the analyzer's counts match.

### End-to-end / system
- `tcpreplay` a curated pcap onto a `veth` pair inside the dev container; assert the
  TUI/stats reflect it. For TUI itself, drive with `tmux send-keys` + screen scrape, or
  keep UI logic thin and test the **view-model** (snapshot structs) directly rather than
  the rendered glyphs.
- **Fuzzing (Phase 3)**: libFuzzer/AFL++ harness feeding random bytes to `dissect()`.
  This is the single highest-value test for a packet parser — wire it into a nightly job.

---

## 3.3 Deployment Strategy

`npa` is a **native binary**, so "deployment" = distribution + the privilege to capture.

- **Primary: native packages**
  - **Debian/Ubuntu**: `apt install` from a built `.deb` (depends: `libpcap0.8`,
    `libncursesw6`). Postinst optionally runs `setcap cap_net_raw,cap_net_admin+eip`.
  - **macOS**: Homebrew formula (`brew install npa`), depends on `libpcap`, `ncurses`.
  - **Portable**: a **static binary** from `release.yml` for "scp and run" on boxes
    without package access (musl/static-libpcap build).
- **Containerization (for dev + ephemeral capture)**
  - Multi-stage `Dockerfile`: a `build` stage (full toolchain) → slim runtime stage
    (just the binary + shared libs, or fully static).
  - Containers need host networking and capabilities to see real traffic:
    `docker run --rm -it --net=host --cap-add=NET_RAW --cap-add=NET_ADMIN npa -i eth0`.
    Codified in `docker-compose.yml` for the dev loop.
- **Privilege model on deploy**: prefer **file capabilities** over running as root.
  Document `setcap` in README; the binary also supports open-then-drop-privileges.

---

## 3.4 Environment Management

`npa` resolves configuration with a clear **precedence chain** (later wins):

```
built-in defaults  <  /etc/npa/npa.conf  <  ./npa.conf  <  $NPA_* env vars  <  CLI flags
```

- **Dev**: run from the tree, verbose logging, small ring, offline pcap replay.
- **Staging**: production-like config, but pointed at a test/mirror interface.
- **Prod**: live interface, INFO logging to a rotating file, BPF pre-filter to reduce load.

Use `config/npa.conf.example` as the canonical annotated reference and `.env.example`
(below) for the env-var surface. Never commit a real `npa.conf` or `.env`.

```dotenv
# .env.example — copy to .env (gitignored). Env vars override config-file values.

# Capture
NPA_INTERFACE=eth0            # NIC to capture on (ignored if NPA_PCAP_FILE set)
NPA_PCAP_FILE=                # read offline instead of live capture
NPA_BPF_FILTER=               # e.g. "tcp port 443 or udp port 53"
NPA_SNAPLEN=65535             # bytes captured per packet (max = NPA_MAX_FRAME_LEN)
NPA_PROMISC=1                 # 1 = promiscuous mode

# Pipeline
NPA_RING_SIZE=1024            # ring buffer slots (power of two)
NPA_RING_FULL_POLICY=drop     # drop | block
NPA_WORKERS=1                 # analyzer threads (Phase 3: >1)

# Detection
NPA_RULES_FILE=config/patterns.rules

# UI / logging
NPA_HEADLESS=0                # 1 = no ncurses (CI/scripting)
NPA_REFRESH_HZ=30             # UI redraw cap
NPA_LOG_LEVEL=info            # trace|debug|info|warn|error
NPA_LOG_FILE=/var/log/npa.log # logs NEVER go to stdout (would corrupt the TUI)
```

---

## 3.5 Version Control Workflow

**Recommended: trunk-based development with short-lived feature branches.**

- **Why trunk-based** (over Gitflow): this is a small, fast-moving tool with a single
  release artifact. Long-lived `develop`/`release` branches add merge overhead with no
  payoff here. Keep `main` always green and releasable.
- **Branches**: `feat/ring-buffer`, `fix/ipv4-options-oob`, `docs/architecture` — short
  lived (< a few days), squash-merged via PR.
- **PR gate**: CI (lint + sanitized build + tests) must pass; at least one review.
- **Releases**: tag `vX.Y.Z` on `main` → `release.yml` builds and publishes the binary.
  **Semantic versioning**; pre-1.0 minor bumps may break CLI/rule-file format.
- **Commits**: Conventional Commits (`feat:`, `fix:`, `perf:`, `refactor:`, `test:`)
  to auto-generate the changelog.

---

## 3.6 Common Pitfalls (C / libpcap / ncurses / pthreads)

A field guide to the bugs this stack reliably produces:

### libpcap
- **The callback buffer is transient.** The `const u_char *` handed to your
  `pcap_handler` is reused on the next packet. **Copy it before enqueuing** — never
  store the pointer. (This is why `captured_frame_t` owns its bytes.)
- **`pcap_loop` blocks forever.** It won't return on a flag check. Stop it from another
  thread with `pcap_breakloop()`, and/or use `pcap_open_live` with a read timeout +
  `pcap_dispatch` loop so you can poll the stop flag.
- **Snaplen truncation is silent.** If `caplen < len`, you captured a partial packet.
  Decoders must use `caplen`, not the on-wire `len`.
- **Datalink isn't always Ethernet.** Check `pcap_datalink()` — loopback (`DLT_NULL`),
  Linux cooked (`DLT_LINUX_SLL`), and 802.11 have different L2 headers.

### ncurses
- **It is NOT thread-safe.** Exactly one thread may call ncurses functions. All our
  rendering lives in the UI thread; other threads communicate via snapshots only.
- **`stdout` is sacred.** Any stray `printf` corrupts the screen. Route all diagnostics
  to the log file. (See `util/log`.)
- **Always pair `initscr()` with `endwin()`** — including on signals/crashes, or the
  user's terminal is left in raw mode. Install handlers that call `endwin()`.
- **Wide chars / UTF-8**: link `ncursesw` (not `ncurses`) and `setlocale(LC_ALL, "")`
  early, or box-drawing/unicode renders as garbage.
- **Handle `SIGWINCH`**: on resize, `endwin()` → `refresh()` → recompute layout.

### POSIX threads
- **Lost wakeups / spurious wakeups**: always wait on a condition variable inside a
  `while (!predicate)` loop, never a bare `if`, and hold the mutex around the predicate.
- **Don't `printf`/`malloc` in signal handlers** — not async-signal-safe. Set an
  `atomic` flag and let the threads react; do teardown on the main thread.
- **Joinable vs detached**: join every thread on shutdown so ncurses teardown and the
  ring flush happen in a defined order. A detached capture thread can write into freed
  buffers during exit.
- **Data races on counters**: use `_Atomic` for cross-thread counters (drops, stats) or
  guard them — `volatile` is **not** a substitute for atomics in C11.

### C / build hygiene
- **Unaligned access & strict aliasing**: don't cast `u_char*` to `struct ip*` and
  dereference — that's UB and crashes on ARM. `memcpy` fields out and use `ntohs/ntohl`.
- **Endianness**: all multi-byte on-wire fields are big-endian; convert on read.
- **Always build with `-Wall -Wextra` and run ASan/UBSan in CI** — most of the above
  surface immediately under sanitizers.
- **Header guards / include hygiene**: every header has `#pragma once` (or guards);
  keep the `util`/`common` → leaf dependency direction to avoid include cycles.
