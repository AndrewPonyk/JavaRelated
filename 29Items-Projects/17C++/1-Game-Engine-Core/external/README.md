# external/

Third-party code that is **not** managed by the vcpkg manifest lives here — vendored
sources or git submodules pinned to a specific commit.

## Policy

- **Prefer `vcpkg.json`** (the manifest in the repo root) for dependencies. It pins
  versions via the builtin baseline and integrates with CMake `find_package`.
- Use this directory only when a dependency is unavailable in vcpkg, must be patched,
  or is fetched as a submodule (e.g., a locally bootstrapped `vcpkg` itself via
  `tools/scripts/setup_dev.ps1`).
- Anything here must be license-compatible (see root `LICENSE`) and recorded with its
  upstream URL + pinned commit.

## Typical contents (when used)

| Path | Purpose |
|------|---------|
| `external/vcpkg/` | Bootstrapped vcpkg (git-ignored; created by setup scripts). |
| `external/<lib>/` | Vendored/patched third-party source, pinned to a commit. |

> This directory is intentionally near-empty in source control — the build resolves
> dependencies through vcpkg by default.
