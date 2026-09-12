# Technical Notes: Shellcode Development

## 3.1 CI/CD Pipeline Design

Stages run on every push/PR to `main`/`master` (see `.github/workflows/ci.yml`):

1. **Install** — `nasm`, `build-essential`, Python deps from `requirements.txt`.
2. **Lint** — N/A for raw asm; Python scripts checked with `ruff`/`black` (future).
3. **Build** — `make all` (assemble + link).
4. **Extract** — `make extract` (raw `.text` → `bin/*.bin`).
5. **Test** — `make test` (pytest: null-byte check, length budget, exec smoke).
6. **Deploy** — N/A as a service. "Deployment" = publishing the payload artifact
   or pushing the exploit script to a CTF platform.

## 3.2 Testing Strategy

- **Unit** — `encoder.py` functions: XOR round-trip, null-byte rejection,
  key auto-selection. Pure-Python, fast.
- **Integration** — assemble `execve.asm`, extract, assert `0x00` not in bytes,
  assert length ≤ budget (e.g. 64 bytes).
- **End-to-end** — inject into a deliberately-vulnerable C wrapper
  (`int main(){ ((void(*)())shellcode)(); }`) inside the Docker lab and assert
  a shell is spawned. Skipped on non-Linux hosts via `pytest.mark.skipif`.

Coverage target: the Python tooling aims for ≥90%; asm is covered by the E2E
exec test rather than line coverage.

## 3.3 Deployment Strategy

Single `Dockerfile` builds an isolated Ubuntu lab image with `nasm`,
`build-essential`, and Python deps. Two profiles:

- **Dev** — ASLR disabled (`docker run --security-opt seccomp=unconfined ...`),
  predictable addresses, fast iteration.
- **Hardened** — same image, ASLR/NX/stack-canary on, to test payloads against
  realistic protections.

No orchestration needed; this is a single-node research image.

## 3.4 Environment Management

`TARGET_*` and debug flags live in `.env`, loaded by `dotenv`. Copy
`.env.example` → `.env` and override per-environment (local lab vs. CTF box).
CI injects the same keys via repository secrets so code is environment-agnostic.

## 3.5 Version Control Workflow

**GitHub Flow** — short-lived feature branches off `master`, PR + green CI to
merge. Rationale: solo/small-team research repo, no long-lived release branches,
CI is the quality gate. Tags mark published payloads (`payload/execve-v1`).

## 3.6 Common Pitfalls (this tech stack)

- **Null bytes** — `mov eax, 0` → `B8 00 00 00 00`. Use `xor eax, eax`
  (`31 C0`). The encoder exists for bytes you can't avoid.
- **Position independence** — hardcoded addresses break under ASLR/relocation.
  Use RIP-relative (`lea`) and stack-based string building; never absolute.
- **Stack alignment** — glibc/SSE paths need a 16-byte-aligned `rsp` at call
  boundaries; misalignment → segfault in `movaps`. Align before `call`.
- **String termination** — `"/bin/sh"` is 7 bytes; pad to `/bin//sh` (8) so it
  fits a push without a mid-stream null, then null-terminate via a pushed zero.
- **Extracting the wrong bytes** — `objcopy --only-section=.text` must target the
  *linked* ELF, not the `.o`, or you may include relocations/padding.
- **Running on the host** — extracted shellcode executes real syscalls. Always
  run inside the Docker lab, never on your workstation.
