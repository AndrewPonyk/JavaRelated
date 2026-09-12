# npa — Network Packet Analyzer

A **Wireshark-lite** for the terminal: live packet capture, protocol decoding, and
rule-driven **anomaly detection**, rendered in an ncurses TUI. Single static-ish
binary, minimal dependencies — built for servers, jump boxes, and SSH sessions
where a GUI isn't an option.

> **Stack:** C11 · libpcap / Npcap · ncurses · threads (pthreads / Win32) · Make / CMake
> **Status:** implemented — capture pipeline, full L2–L4 decoders (incl. IPv6 &
> checksums), anomaly engine with loadable rules, ncurses TUI, headless/JSON mode,
> pcap export, and a unit + smoke test suite. Runs on **Linux/macOS** (full TUI +
> live capture) and **Windows/MSVC** (headless; offline with zero deps, live via
> Npcap). Roadmap & stretch items in [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md).

---

## Features

- **Live & offline capture** — `-i eth0` or `-r capture.pcap` (same pipeline).
- **Protocol decoding** — Ethernet/802.1Q → IPv4 → TCP/UDP/ICMP, with strict,
  fuzz-friendly bounds checking (a malformed packet never crashes the analyzer).
- **Anomaly detection** — built-in rules (NULL/XMAS/SYN-FIN scans, low TTL,
  malformed frames) + a stateful port-scan heuristic; loadable rule file.
- **Live stats** — packet/byte rates, protocol mix, top talkers.
- **Lossless under bursts** — capture and analysis are decoupled by a bounded
  ring buffer; drops (if any) are counted and surfaced, never silent.
- **Headless mode** — `--headless` for scripting/CI, no TTY required.

## Architecture at a glance

```
[capture thread] --ring buffer--> [analyzer thread] --snapshots--> [ui thread]
   libpcap            (bounded)     decode+detect+stats               ncurses
```

Three threads, one clear rule: **only the UI thread touches ncurses**; everything
crosses thread boundaries through the ring buffer or locked snapshots. Full
write-up with diagrams in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Build

```bash
# 1. Dependencies (Debian/Ubuntu/Fedora/macOS auto-detected)
./scripts/install-deps.sh

# 2. Build the binary  ->  build/bin/npa
make

# 3. Run the unit tests (no libpcap/ncurses needed for these)
make test
```

Useful switches: `make STRICT=1` (warnings as errors), `make ASAN=1` (sanitizers),
`make OPT=-O3`, `CC=clang make`.

### Build on Windows (MSVC + CMake)

Windows builds run **headless** (JSON/text; no ncurses TUI) and use a small
platform shim under `src/platform/win/` (Win32 threads, `getopt`, `clock_gettime`).
**Offline analysis needs no external libraries** — a built-in pcap reader is used.

From a *Developer PowerShell/Command Prompt for VS* (so `cl`/`nmake` are on PATH):

```bat
cmake -S . -B build -G "NMake Makefiles"
cmake --build build
ctest --test-dir build --output-on-failure
build\npa.exe -r tests\fixtures\sample.pcap --headless --json
```

**Live capture on Windows** uses [Npcap](https://npcap.com). Install Npcap + the
Npcap SDK, point `NPCAP_SDK` at the SDK root (with `Include\` and `Lib\x64\`), then
reconfigure — CMake links `wpcap`/`Packet` and enables `-i`:

```bat
set NPCAP_SDK=C:\npcap-sdk
cmake -S . -B build -G "NMake Makefiles" && cmake --build build
build\npa.exe -i "\Device\NPF_{...}" -f "tcp port 443" --headless --json
```

> CMake also works on Linux/macOS (auto-detects libpcap + ncursesw); the `Makefile`
> remains the canonical POSIX build.

## Run

Capturing live traffic needs raw-socket privileges. Prefer **file capabilities**
over running as root:

```bash
sudo setcap cap_net_raw,cap_net_admin+eip ./build/bin/npa

./build/bin/npa -i eth0 -f "tcp port 443 or udp port 53"   # live, filtered
./build/bin/npa -i eth0 -w out.pcap                        # analyze + save to disk
./build/bin/npa -r capture.pcap                            # offline replay
./build/bin/npa -r capture.pcap --headless --json          # machine-readable
```

Run `./build/bin/npa --help` for the full flag list (`man docs/npa.1` for the
manual). Configuration also resolves from `/etc/npa/npa.conf`, `./npa.conf`, and
`$NPA_*` env vars (later wins) — see [`config/npa.conf.example`](config/npa.conf.example)
and [`.env.example`](.env.example).

### Headless / JSON (scripting & CI)

`--headless` runs without the TUI; add `--json` for JSON Lines on stdout (logs
stay on stderr/file). Each alert is one object, followed by a final summary:

```bash
$ npa -r capture.pcap --headless --json --count 1000
{"type":"alert","ts_ms":123,"severity":"MEDIUM","rule":"tcp-xmas-scan","src":"10.0.0.200",...}
{"type":"alert","ts_ms":140,"severity":"HIGH","rule":"port-scan","src":"10.0.0.200",...}
{"type":"summary","packets":34,"ipv4":33,"tcp":31,"udp":1,"icmp":1,"alerts":4,"dropped":0}
```

`--count N` stops after N packets; `--stats-interval S` prints a periodic stats line.

### Try it without any setup

```bash
make smoke            # builds, generates a sample pcap, runs headless JSON
```

### Keybindings (TUI)

| Key | Action |
|-----|--------|
| `↑` / `↓` | Move selection in the packet list |
| `PgUp` / `PgDn` / `Home` / `End` | Scroll the list |
| `p` | Freeze / resume the live list (stats keep updating) |
| `/` | Enter a new BPF filter and apply it live |
| `w` | Export retained packets to a timestamped `.pcap` |
| `q` | Quit |

## Docker

```bash
# End-to-end demo: replays a bundled sample pcap and prints JSON (no privileges)
docker compose up demo

# Live capture (needs host net + raw caps)
docker compose run --rm npa -i eth0 -f "tcp port 443"

# Offline analysis of your own file
docker run --rm -v "$PWD:/data" npa -r /data/capture.pcap --headless --json
```

## Anomaly rules

Detection rules live in [`config/patterns.rules`](config/patterns.rules) (loaded with
`--rules`). Each rule matches on TCP flags, ports, TTL, byte signatures, or the
malformed flag. The compiled-in built-ins always run even without a rule file.

## Project layout

```
src/           capture · buffer · decode · analysis · ui · util  (one dir per concern)
src/capture/   libpcap/Npcap backend + portable pcap_file reader/writer
src/platform/  win/ — Windows shims (pthreads, getopt, clock_gettime)
include/       public/stable headers (npa/npa.h)
tests/         unit tests + header-only micro-framework + fixture generator
config/        npa.conf.example, patterns.rules
docs/          PROJECT-PLAN · ARCHITECTURE · TECH-NOTES · npa.1
Makefile       canonical POSIX build   ·   CMakeLists.txt  cross-platform (incl. Windows)
```

## Security notes

`npa` reads raw traffic, so payloads may contain secrets. They stay in memory and
are never persisted unless you explicitly export. Logs record metadata, never raw
payloads. Decoders treat every packet as adversarial input. Details in
[`docs/ARCHITECTURE.md` §2.5](docs/ARCHITECTURE.md).

## Contributing

Trunk-based: short-lived `feat/…`/`fix/…` branches, PRs gated on CI
(format → lint → sanitized build → tests). Conventional Commits. See
[`docs/TECH-NOTES.md`](docs/TECH-NOTES.md).

## License

MIT — see [`LICENSE`](LICENSE).
