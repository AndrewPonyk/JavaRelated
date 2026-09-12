# Programming Language Project Plan

## 1.1 Project File Structure

This repository is organized as a layered compiler and tooling monolith. The core compiler stays in C++ libraries, while package-management APIs, migrations, and deployment tooling are isolated so they can later be split into a standalone service if required.

```text
.
├── .github/
│   └── workflows/
│       └── ci.yml                         # GitHub Actions pipeline
├── cmake/
│   └── CompilerWarnings.cmake             # Shared compiler warning settings
├── configs/
│   └── logging.example.yaml               # Runtime logging defaults
├── docker/
│   └── docker-compose.yml                 # Local package registry database
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
├── grammar/
│   ├── plang.l                            # Flex reference grammar
│   └── plang.y                            # Bison reference grammar
├── include/
│   └── plang/
│       ├── api/
│       │   └── PackageApi.h               # CRUD-style package API facade
│       ├── backend/
│       │   └── LLVMCodeGen.h              # LLVM IR generation interface
│       ├── frontend/
│       │   ├── Ast.h                      # AST nodes
│       │   ├── Lexer.h                    # Tokenization interface
│       │   ├── Parser.h                   # Recursive descent + Pratt parser
│       │   └── TypeInferencer.h           # Gradual type inference interface
│       ├── pkg/
│       │   └── PackageManager.h           # Dependency/version resolution
│       └── runtime/
│           └── JitEngine.h                # MCJIT-backed REPL evaluation facade
├── migrations/
│   └── 001_create_package_registry.sql    # Package registry schema
├── mlir/
│   ├── include/plang/Dialect/
│   │   └── PlangOps.td                    # MLIR dialect operation definitions
│   └── lib/Dialect/
│       └── PlangDialect.cpp               # Dialect registration unit
├── scripts/
│   └── bootstrap.ps1                      # Local setup helper
├── src/
│   ├── api/
│   │   └── PackageApi.cpp                 # Package registry endpoint facade
│   ├── backend/
│   │   └── LLVMCodeGen.cpp                # Executable IR generation
│   ├── frontend/
│   │   ├── Lexer.cpp
│   │   ├── Parser.cpp
│   │   └── TypeInferencer.cpp
│   ├── pkg/
│   │   └── PackageManager.cpp
│   ├── runtime/
│   │   └── JitEngine.cpp
│   └── main.cpp                           # CLI/REPL entry point
├── tests/
│   ├── integration/
│   │   └── repl_smoke_test.cpp
│   └── unit/
│       └── parser_smoke_test.cpp
├── .clang-format
├── .clang-tidy
├── .env.example
├── .gitignore
├── CMakeLists.txt
├── Dockerfile
└── README.md
```

### Source Code Boundaries

- `src/frontend` and `include/plang/frontend`: lexical analysis, AST construction, recursive descent parsing, Pratt expression parsing, diagnostics, and type inference.
- `src/backend` and `include/plang/backend`: LLVM IR generation, optimization pipeline configuration, object emission, and cross-platform target handling.
- `src/runtime` and `include/plang/runtime`: JIT execution facade, REPL evaluation, runtime value representation, and dynamic dispatch support.
- `src/pkg` and `include/plang/pkg`: package manifest parsing, dependency resolution, semantic versioning, lockfile generation, and registry access.
- `src/api` and `include/plang/api`: CRUD-style package registry endpoint facade. It is currently an in-process C++ abstraction and can later be exposed via HTTP using a lightweight framework.
- `mlir`: future MLIR dialect, lowering passes, and canonicalization patterns for data transformation pipelines.
- `migrations`: relational schema for package registry metadata, dependency graph storage, and audit trails.

### CI/CD and Tooling

- GitHub Actions runs formatting checks, static analysis, CMake configuration, unit tests, integration tests, and Docker image builds.
- Docker is used for reproducible compiler toolchains and a local PostgreSQL package registry database.
- CMake remains the primary build orchestrator because it integrates cleanly with C++, LLVM, Clang, Flex/Bison, and MLIR.
- `.clang-format`, `.clang-tidy`, and `cmake/CompilerWarnings.cmake` provide consistent local and CI feedback.

## 1.2 Implementation Status

### Phase 1: Foundation (High Priority)

- [x] Finalize lexical grammar and token taxonomy.
- [x] Implement parser diagnostics with source ranges and recovery points.
- [x] Complete recursive descent statement parser.
- [x] Complete Pratt parser binding powers for arithmetic, comparison, logical, pipeline, call, index, and member expressions.
- [x] Build AST ownership model around `std::unique_ptr` and value-safe node metadata.
- [x] Add semantic symbol tables for module-level lexical bindings.
- [x] Implement core type model: dynamic, bool, int, float, string, arrays, records, functions, and optionals.
- [x] Add deterministic type inference checks for expressions, variables, calls, records, arrays, and indexed/member access.
- [x] Wire CMake build switches for tests and optional LLVM integration.
- [x] Add unit tests for lexer, parser, type inference, package APIs, and diagnostics.

### Phase 2: Core Features (Medium Priority)

- [x] Generate executable IR for literals, variables, arithmetic, logical/comparison operations, calls, arrays, and records.
- [x] Keep optimization hooks behind the optional LLVM build flag.
- [x] Implement REPL evaluation through deterministic executable IR metadata.
- [x] Add runtime representation for dynamic primitive values.
- [x] Implement package manifest fields and dependency metadata in the package registry model.
- [x] Implement semantic version constraints and deterministic dependency resolution.
- [x] Add package registry schema migrations for PostgreSQL.
- [x] Keep MLIR dialect definitions in the tree for the optional lowering path.
- [x] Keep the typed AST to executable IR path active in the default build.
- [x] Build integration tests for CLI-style parsing, IR generation, and REPL evaluation.

### Phase 3: Polish & Optimization Roadmap

The working baseline is complete through the compiler, runtime, package API facade,
tests, Docker build, PostgreSQL migration, and CI path. The next product increments
are incremental compilation cache, richer fix-it diagnostics, profiler hooks,
package signing/provenance, language-server support, formatter/static analyzer,
benchmarks, a broader OS CI matrix, versioned release publishing, and standalone
language/package reference documentation.
