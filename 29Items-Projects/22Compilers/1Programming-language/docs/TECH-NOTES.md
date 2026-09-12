# Programming Language Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended stages:

1. **Linting:** run `clang-format --dry-run`, `clang-tidy`, CMake format checks, and Markdown linting.
2. **Testing:** run C++ unit tests, parser golden tests, semantic diagnostics tests, and package resolver tests.
3. **Building:** configure with CMake, build Debug and Release variants, and produce CLI artifacts.
4. **Packaging:** build Docker image and archive native binaries per target platform.
5. **Deploying:** publish prerelease artifacts from `main`, promote tagged releases to production package channels.

The starter GitHub Actions workflow in this repository is intentionally conservative and can be expanded after the real LLVM toolchain matrix is finalized.

## 3.2 Testing Strategy

- Use Catch2 or GoogleTest for C++ unit tests.
- Target high coverage on deterministic compiler logic: lexer, parser, type inference, dependency resolver, and manifest validation.
- Use golden files for diagnostics and IR snapshots, but avoid brittle tests that assert every LLVM formatting detail unless intentionally testing codegen.
- Use integration tests for CLI execution, REPL evaluation, package install/update flows, and registry migration compatibility.
- Add fuzzing for lexer/parser once grammar stabilizes.
- Add benchmark tests for large source files, deep expression trees, dependency graph resolution, and JIT warmup.

Suggested coverage targets:

- Frontend parser and diagnostics: 85% or higher.
- Type inference and semantic analysis: 80% or higher.
- Package resolver: 90% or higher.
- LLVM/MLIR integration: meaningful path coverage plus golden assertions, not raw percentage chasing.

## 3.3 Deployment Strategy

The compiler should ship as:

- Native CLI binaries for Windows, Linux, and macOS.
- A Docker image for reproducible CI and build environments.
- Optional package registry service components if the registry becomes a deployed service.

Containerization strategy:

- Use a build image with CMake, Ninja, Clang, LLVM, MLIR, Flex, and Bison.
- Keep runtime images smaller if serving registry APIs separately.
- Pin major LLVM versions to avoid accidental ABI and CMake integration drift.

## 3.4 Environment Management

Use layered configuration:

- Development: `.env` loaded locally and ignored by git.
- Staging: deployment platform variables plus non-production package registry.
- Production: secret manager and locked-down registry credentials.

The repository includes `.env.example` with safe sample values. Any real `.env` file must stay untracked.

Key configuration groups:

- Compiler defaults: optimization level, target triple, diagnostics verbosity.
- Registry connectivity: database URL, registry base URL, token audience.
- Runtime safety: JIT enablement, sandbox flags, resource limits.
- Logging: log level, format, and output sink.

## 3.5 Version Control Workflow

Use **trunk-based development with short-lived branches**.

Rationale:

- Compiler work benefits from frequent integration because parser, type inference, codegen, and tests interact tightly.
- Short-lived branches reduce long-running merge conflicts in grammar, AST, and diagnostics code.
- Release tags can drive binary and Docker publishing.

Recommended rules:

- Protect `main` with required CI.
- Use feature branches for focused changes.
- Prefer small pull requests with parser/codegen tests included.
- Tag releases with semantic versions after changelog generation.

## 3.6 Common Pitfalls

- LLVM and MLIR APIs shift between releases; pin the supported major version.
- CMake discovery for LLVM can differ across package managers and operating systems.
- MCJIT is useful for a first REPL, but ORC JIT may be a better long-term runtime path.
- Pratt parser binding powers become hard to maintain without a central operator table.
- Gradual typing can hide runtime failures unless dynamic boundaries are explicit and tested.
- Dependency resolution needs deterministic tie-breaking or lockfiles will churn.
- Golden LLVM IR tests can become noisy after optimization changes; keep them scoped.
- Flex/Bison generated files should have a clear policy: either generated in build directories or checked in with pinned tool versions.
- Package registry security cannot be bolted on late; include signing, checksums, and audit trails in the schema early.
