# JavaScript Engine Project Plan

## 1.1 Project File Structure

This repository is a layered modular monolith for an embeddable C++ JavaScript-like runtime. The implemented scope is intentionally compact and production-oriented: source text is tokenized, parsed into an AST, compiled into bytecode, executed by an in-process VM, and supported by runtime objects, inline caches, trace recording, debug-session CRUD, and SQLite-backed metadata.

```text
.
├── CMakeLists.txt
├── CMakePresets.json
├── Dockerfile
├── docker/docker-compose.yml
├── .github/workflows/ci.yml
├── cmake/ToolchainOptions.cmake
├── config/
├── docs/
├── include/jsengine/
│   ├── compiler/
│   ├── debug/
│   ├── runtime/
│   ├── storage/
│   └── vm/
├── src/
│   ├── compiler/
│   ├── debug/
│   ├── runtime/
│   ├── storage/
│   ├── tools/
│   └── vm/
├── migrations/
├── tests/
├── benchmarks/
└── examples/
```

### Source Organization

- `compiler`: lexer, recursive-descent parser, AST, and bytecode compiler.
- `vm`: compact bytecode module, stack VM, arithmetic, globals, object creation, property get/set, inline cache updates, and trace heat tracking.
- `runtime`: value representation, object model, property descriptors, shape IDs, and GC accounting primitives.
- `debug`: embeddable debug-session CRUD API with validation and JSON responses.
- `storage`: SQLite-backed metadata store for key/value config, module-cache entries, and runtime events.
- `tests`: executable unit and integration tests registered with CTest.
- `benchmarks`: standalone microbenchmark for object property access.

## 1.2 Completed Implementation Checklist

### Phase 1: Foundation

- [x] Tagged runtime value representation for undefined, null, boolean, number, string, and object.
- [x] Bytecode instruction set for constants, globals, arithmetic, objects, properties, stack control, and return.
- [x] Lexer for identifiers, numbers, strings, comments, keywords, and symbols.
- [x] Parser for declarations, assignments, returns, expressions, object literals, and property access.
- [x] Bytecode compiler from AST to VM instructions.
- [x] Interpreter loop with deterministic runtime errors.
- [x] `const` declaration enforcement and duplicate binding checks.
- [x] Object model with property descriptors, stable insertion order, shape IDs, and inspection.
- [x] Structured public result model for success and recoverable errors.
- [x] CMake, Docker, GitHub Actions, formatter, linter, and environment configuration.
- [x] Unit and integration test harness.

### Phase 2: Core Features

- [x] Monomorphic-to-polymorphic inline cache state tracking for property sites.
- [x] GC accounting for nursery allocation, promotion, write barriers, and incremental marking state.
- [x] Debug-session create, read, update, delete, list operations.
- [x] Paginated debug-session and storage list operations.
- [x] SQLite metadata persistence for runtime config, module-cache records, runtime events, and indexed lookups.
- [x] Host-facing `jsengine::Engine` API and CLI.
- [x] Integration tests for source evaluation, debug API, VM execution, and metadata persistence.

### Phase 3: Polish & Optimization

- [x] Trace recorder heat tracking and trace records for hot bytecode offsets.
- [x] Property-access benchmark.
- [x] Docker workflow that builds and runs tests.
- [x] README and API documentation.
- [x] CI pipeline for configure, build, format check, tests, and coverage gate.

## Supported Language Surface

The runtime supports a practical embedded subset:

- `let`, `const`, and `var` declarations. Reassigning `const` returns a runtime error.
- Assignment to globals and object properties.
- `return` statements.
- Number, string, boolean, null, and undefined literals.
- Object literals with string or identifier property names.
- Dot property access.
- Arithmetic operators `+`, `-`, `*`, `/` and unary `-`.
- Line and block comments.

Unsupported ECMAScript features are rejected by the parser or runtime instead of returning silent fallback values.
