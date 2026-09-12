# Portable Process Monitor (`ppmon`)

A single-binary, dependency-light Windows console tool that polls the kernel and
performance subsystems on a fixed interval and reports per-process **CPU, memory,
and I/O** metrics. It exports a CSV time-series and raises **threshold-based
alerts** to the console. An optional read-only **WinSock2** listener streams the
same metrics to remote collectors.

> Status: **implemented & verified.** Builds clean under MSVC `/W4 /WX`; the
> full collection→domain→presentation pipeline runs against the live OS. 4 unit
> tests + 1 live-OS integration test pass; `cpack` produces the portable ZIP.
> The only deferred item is the optional ETW enrichment (see
> [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md)).

## Tech stack

| Concern | API |
|---------|-----|
| Polling clock | `QueryPerformanceCounter` (drift-free) |
| Process enumeration | `NtQuerySystemInformation` |
| Per-process metrics | PSAPI (`GetProcessTimes` / `GetProcessMemoryInfo` / `GetProcessIoCounters`) |
| System-wide counters | PDH (English counters, locale-safe) |
| Remote stream | WinSock2 (TCP, read-only) |
| Build / test / CI | CMake + CTest, GitHub Actions |

## Build

```powershell
# Configure + build + run unit tests
./scripts/build.ps1 -Config Release -Test

# Or directly:
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
ctest --test-dir build -C Release --output-on-failure
```

Requires CMake ≥ 3.20 and the MSVC toolchain (Visual Studio Build Tools).

## Run

```powershell
ppmon.exe --interval 1000 --top 15            # interactive top-N table
ppmon.exe --once --csv metrics.csv            # single poll to CSV (smoke mode)
ppmon.exe --listen 127.0.0.1:9555             # also stream metrics over TCP
ppmon.exe --privilege                         # request SeDebugPrivilege (full visibility)
ppmon.exe --help                              # all options
```

Configuration precedence: built-in defaults → `ppmon.ini` → CLI flags. See
[`config/ppmon.ini.example`](config/ppmon.ini.example).

### Reading the metric stream

With `--listen`, every poll cycle is pushed to connected TCP clients as a
line-delimited frame (`FRAME <n>` followed by `pid,image,cpu,ws,priv,read_bps,write_bps`):

```powershell
# quick subscriber
$c = New-Object System.Net.Sockets.TcpClient; $c.Connect("127.0.0.1", 9555)
$r = New-Object IO.StreamReader($c.GetStream()); while ($true) { $r.ReadLine() }
```

If `PPMON_STREAM_TOKEN` is set in the server's environment, a client must send
`<token>\n` before receiving data. The server is **read-only** (no commands).

## Testing

```powershell
ctest --test-dir build -C Release --output-on-failure          # unit tests
$env:PPMON_RUN_INTEGRATION = "1"                               # opt-in live-OS tests
cmake -S . -B build -G "Visual Studio 17 2022" -A x64          # re-configure to register them
cmake --build build --config Release; ctest --test-dir build -C Release
```

Unit tests cover the domain layer (alerting + per-process engine, CSV escaping
and round-trip, config INI/CLI parsing, sample-store delta math & eviction). The
integration test exercises the live collection layer (enumeration, PSAPI, PDH,
two-sample CPU%).

## Packaging & deployment

The deliverable is a **portable, install-free ZIP** (statically-linked CRT, no
VC++ redistributable needed):

```powershell
cmake --build build --config Release --target package   # -> build/ppmon-<ver>-<arch>.zip
```

> **No Docker / `docker-compose`.** This is a native tool that reads the host's
> kernel/PDH structures directly — containerizing it to monitor the host is not
> meaningful, and a Windows-only kernel tool has no Linux-container story. The
> equivalent "one command for the whole stack" is `./scripts/build.ps1 -Test
> -Package`. This is a deliberate, documented architecture decision — see
> [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) §3.3.

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — file structure & implementation TODOs
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, diagrams, data flow, security
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls

## Layout

```
include/ppmon/  public headers (one per module)
src/            implementations (status, config, log, timer, process_enum,
                metrics, pdh_counters, sample_store, alerting, csv_export,
                console_ui, net_server, main)
tests/          CTest unit tests (alerting, csv_export, config, sample_store)
cmake/          warning flags + packaging
.github/        CI workflow
```

## Troubleshooting

| Symptom | Cause & fix |
|---------|-------------|
| Many processes missing or show no metrics | They're owned by other users / higher integrity and can't be opened by a normal token. Run from an elevated prompt with `--privilege` to enable `SeDebugPrivilege`. Without it the tool *degrades gracefully* and simply skips inaccessible PIDs (by design). |
| Every process shows `0.0` CPU | CPU% is a **delta** between two samples. `--once` (one poll) always reports 0; let it run at least two intervals, or it's genuinely idle. |
| System CPU line reads `0.0%` at first | PDH rate counters need a second collection to produce a value; the first cycle after start is the warm-up. |
| `cmake` / `ctest` "not recognized" | They're not on PATH. Run from **"Developer PowerShell for VS"**, or add the VS-bundled CMake `bin` to PATH. |
| `--listen` fails to bind | Port in use, or a firewall prompt. Pick another port (`--listen 127.0.0.1:9556`). Binding a non-loopback address is trusted-network-only and requires an explicit address. |
| Stream client gets nothing | If `PPMON_STREAM_TOKEN` is set on the server, the client must send `<token>\n` first; otherwise the connection is closed. |
| Table looks garbled (raw `␛[` codes) | The console doesn't support virtual-terminal sequences. Use Windows Terminal / a modern console; output is still parseable. |
| Build fails on `NtQuerySystemInformation` types | Ensure you build **x64** (`-A x64`); the `SYSTEM_PROCESS_INFORMATION` layout assumes 64-bit. |

## License

MIT — see [LICENSE](LICENSE).
