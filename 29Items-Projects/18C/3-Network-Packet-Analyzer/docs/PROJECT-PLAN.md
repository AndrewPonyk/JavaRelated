# Network Packet Analyzer — Project Plan

> **Codename:** `npa`
> **Tagline:** A Wireshark-lite TUI for live capture, decoding, and protocol-anomaly detection.
> **Stack:** C11 · libpcap · ncurses · POSIX threads · GNU Make

---

## 1. Overview

`npa` is a single-binary, terminal-native network analyzer aimed at operators and
engineers who need fast, dependency-light packet visibility on a host or jump box
where a GUI Wireshark install is impractical (servers, containers, embedded boxes,
SSH sessions).

It captures live traffic (or reads `.pcap` files), decodes the common protocol
stack (Ethernet → IPv4/IPv6 → TCP/UDP/ICMP), and runs a lightweight, rule-driven
**anomaly detector** that flags suspicious or malformed traffic using pattern
matching. Everything is rendered in an ncurses TUI with a live packet list, a
detail/hex pane, and a rolling statistics dashboard.

### Design pillars

| Pillar | What it means here |
|---|---|
| **Zero packet loss under load** | Capture thread never blocks on the UI; a lock-free-ish ring buffer absorbs bursts. |
| **Decode correctness** | Strict bounds checking — a malformed packet must never crash the analyzer. |
| **Low footprint** | No runtime deps beyond libpcap/ncurses; static-link friendly. Baseline RSS is a few MB; total memory is dominated by the capture ring (`ring_size` × per-slot snaplen) — ~64 MB at defaults, tunable via `--ring-size`/`--snaplen` for constrained hosts. |
| **Operator ergonomics** | Keyboard-first TUI, BPF filter input, live stats, color-coded anomalies. |

---

## 1.1 Project File Structure

The repository follows a **feature-module layout under `src/`**, with one directory
per concern. Headers live beside their implementation; only the public/stable
surface is mirrored into `include/`.

```text
3-Network-Packet-Analyzer/
├── docs/                          # Architecture & engineering docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── include/                       # Public/stable headers (versioned API surface)
│   └── npa/
│       └── npa.h                  # Version macros, top-level lifecycle API
│
├── src/                           # All implementation
│   ├── main.c                     # Entry point: CLI parse → wire threads → run
│   │
│   ├── common/                    # Cross-cutting type definitions (no logic)
│   │   ├── types.h                # Fixed-width aliases, result/error enums
│   │   └── packet.h               # decoded_packet_t, layer structs, captured_t
│   │
│   ├── capture/                   # libpcap acquisition layer
│   │   ├── capture.h
│   │   └── capture.c              # Capture thread, BPF compile, pcap loop
│   │
│   ├── buffer/                    # Lock-protected SPSC/MPSC ring buffer
│   │   ├── ring_buffer.h
│   │   └── ring_buffer.c
│   │
│   ├── decode/                    # Protocol dissectors (pure, side-effect free)
│   │   ├── decode.h               # Dispatch + per-layer decoder prototypes
│   │   ├── decode.c               # Top-level dissect orchestration
│   │   ├── ethernet.c
│   │   ├── ipv4.c
│   │   ├── tcp.c
│   │   ├── udp.c
│   │   └── icmp.c
│   │
│   ├── analysis/                  # Consumer-side processing
│   │   ├── analyzer.h
│   │   ├── analyzer.c             # Worker thread: drains ring → decode → detect
│   │   ├── anomaly.h
│   │   ├── anomaly.c              # Rule engine over decoded packets
│   │   ├── patterns.h
│   │   ├── patterns.c             # Built-in + loaded rule definitions
│   │   ├── statistics.h
│   │   └── statistics.c           # Rolling counters (proto mix, pps, bps, top talkers)
│   │
│   ├── ui/                        # ncurses presentation
│   │   ├── tui.h
│   │   ├── tui.c                  # Screen/loop lifecycle, input router
│   │   ├── views.h
│   │   └── views.c                # Packet-list / detail / stats / alerts panes
│   │
│   └── util/                      # Leaf utilities (depend on nothing internal)
│       ├── log.h
│       ├── log.c                  # Async-safe leveled logging to file
│       ├── config.h
│       └── config.c               # Config file + env + CLI merge
│
├── tests/                         # Unit tests (header-only micro-framework)
│   ├── test_common.h              # TEST()/ASSERT macros + runner glue
│   ├── test_ring_buffer.c
│   ├── test_decode.c
│   ├── test_anomaly.c
│   └── unity/
│       └── unity.h                # (placeholder) drop-in for Unity if adopted
│
├── config/
│   ├── npa.conf.example           # Annotated runtime config
│   └── patterns.rules             # Anomaly rule definitions (loadable)
│
├── scripts/
│   ├── install-deps.sh            # apt/brew dependency bootstrap
│   ├── lint.sh                    # clang-tidy + cppcheck wrapper
│   └── format.sh                  # clang-format apply/check
│
├── .github/
│   └── workflows/
│       ├── ci.yml                 # lint → build (gcc+clang, ASan) → test
│       └── release.yml            # Tagged static-binary + tarball release
│
├── .clang-format                  # Code style (LLVM-derived)
├── .clang-tidy                    # Static analysis rule set
├── .gitignore
├── .env.example                   # Runtime env template
├── Dockerfile                     # Reproducible build + slim runtime image
├── docker-compose.yml             # Dev container with NET_RAW/NET_ADMIN caps
├── Makefile                       # Build, test, lint, install, package
├── LICENSE
├── README.md
└── claude-opus-4-8.txt            # Architect attribution marker
```

### Why this layout

- **Concern-per-directory** keeps the dependency graph acyclic: `util` → nothing,
  `common` → nothing, `decode` → `common`, `analysis` → `decode`/`buffer`/`common`,
  `ui` → `analysis`/`common`, `main` → everything. This makes unit testing of
  `decode`/`buffer`/`anomaly` trivial (no UI or pcap needed).
- **`include/npa/` vs `src/*.h`** separates the *stable* surface (what a future
  `libnpa` or test harness links against) from internal headers that can churn.
- **Tests mirror modules**, so coverage gaps are visible at a glance.

---

## 1.2 Implementation TODO List

Tasks are ordered by dependency and priority. `[ ]` = not started.

### Phase 1 — Foundation (HIGH)
- [x] Lock down `common/types.h` and `common/packet.h` (the data contract everyone shares).
- [x] Implement `util/log` (leveled, file-backed, signal-safe-ish) — needed for debugging everything else.
- [x] Implement `buffer/ring_buffer` (mutex + condvar bounded queue) **+ unit tests** (this is the concurrency heart; get it right first).
- [x] Implement `capture/capture`: open device, compile/apply BPF, run the dispatch loop on its own thread, enqueue raw frames.
- [x] `main.c`: full CLI (`-i`/`-r`/`-f`/…), pipeline orchestration, signal handling.
- [x] `Makefile` with dep tracking, `make` / `make test` / `make clean`.
- [x] CI: build matrix (gcc, clang) + ASan/UBSan build.

### Phase 2 — Core Features (MEDIUM)
- [x] `decode/`: Ethernet, IPv4, TCP, UDP, ICMP dissectors with **strict bounds checks** + unit tests over crafted byte buffers.
- [x] IPv6 dissector (`ipv6.c`, extension-header walk) and VLAN/Q-in-Q (802.1Q/802.1ad) unwrap.
- [x] `analysis/statistics`: per-protocol counters, pps/bps, top-talkers (hash table).
- [x] `analysis/analyzer` worker thread: drain ring → decode → update stats → run detector.
- [x] `analysis/anomaly` + `patterns`: rule engine (port-scan heuristic, malformed-header, signature/byte-pattern match, TTL/flag oddities, bad checksums).
- [x] `config/patterns.rules` loader (`patterns.c`).
- [x] `ui/tui` + `ui/views`: packet-list pane, detail+hex pane, stats footer, alert ticker; key routing (scroll, freeze, quit, filter prompt, export).
- [x] Graceful shutdown: signal handler → stop capture → flush ring → join threads → endwin.

### Phase 3 — Polish & Optimization (LOWER)
- [x] Live BPF re-filter without restart (queued recompile + `pcap_setfilter` on the capture thread).
- [x] Color pairs by protocol/severity; resize (`KEY_RESIZE`/`SIGWINCH`) relayout.
- [x] Export retained packets to `.pcap` (UI `w` key) + full-fidelity capture-to-disk (`-w`).
- [x] Hash-table top-talkers with bounded-probe eviction.
- [x] `config/npa.conf` + `/etc/npa/npa.conf` parser with the full precedence chain.
- [x] IPv4/TCP/UDP/ICMP checksum verification; headless JSON output mode.
- [x] Man page (`npa.1`), Homebrew formula, Debian packaging.
- [ ] Replace the ring-buffer mutex with a true lock-free SPSC ring on the hot path; benchmark. *(deferred — mutex ring is correct and tested)*
- [ ] Fuzz the decoders (libFuzzer/AFL++) and wire into a nightly CI job. *(deferred)*

---

## 2. Milestones & Definition of Done

| Milestone | DoD |
|---|---|
| **M1 — "It captures"** | `npa -i eth0` prints live one-line summaries; clean Ctrl-C shutdown; ring buffer unit tests green. |
| **M2 — "It decodes"** | Full L2–L4 decode visible in TUI detail pane; decoder tests cover malformed inputs; no leaks under ASan. |
| **M3 — "It detects"** | Anomaly rules fire and surface in the alert pane; rules loadable from file; stats dashboard live. |
| **M4 — "It ships"** | Static binary released via CI; README + man page; Docker image with proper capabilities. |

---

## 3. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Packet loss under burst | Misleading analysis | Bounded ring + drop-counter surfaced in UI; size buffer from CLI. |
| Privilege requirements (raw sockets) | Won't run unprivileged | Document `setcap cap_net_raw,cap_net_admin+eip`; drop privileges after `pcap_open`. |
| ncurses + threads races | UI corruption/crash | **Only the UI thread touches ncurses**; all cross-thread data flows via the ring/snapshots. |
| Malformed-packet crashes | DoS / instability | Every dissector validates length before dereference; fuzz in CI. |
| Endianness / alignment | Wrong fields on some arches | Use `ntohs/ntohl` + `memcpy` into structs, never cast-and-deref on unaligned data. |
