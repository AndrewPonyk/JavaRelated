# JavaScript Engine Technical Notes

## 3.1 CI/CD Pipeline Design

The GitHub Actions workflow performs:

1. Dependency installation: CMake, Ninja, Clang tooling, gcovr, SQLite CLI, and SQLite development headers.
2. Formatting check with `clang-format`.
3. CMake configuration.
4. Build of library, CLI, and tests.
5. CTest execution.
6. GCC coverage build with an 80% line coverage gate.

Docker uses the same dependency set, which keeps local container runs close to CI.

## 3.2 Testing Strategy

- Unit tests cover lexing, VM bytecode behavior, and source-level engine evaluation.
- Integration tests cover debug-session CRUD and SQLite metadata persistence.
- Tests are standalone C++ executables registered with CTest.
- Current tests cover the critical supported flows: arithmetic, declarations, assignments, objects, property access, parse/runtime error behavior, API validation, and database CRUD.

## 3.3 Deployment Strategy

The deployable artifacts are:

- `jsengine` static library.
- `jsengine_cli` executable.
- Public headers under `include/jsengine`.
- Docker image capable of building and running the test suite.

`docker compose -f docker/docker-compose.yml up --build` builds the image and runs CTest.

## 3.4 Environment Management

- CMake presets select Debug, Release, and sanitizer builds.
- `config/engine.dev.json`, `config/engine.prod.json`, and `config/logging.json` contain runtime defaults.
- `.env.example` documents optional local settings for build type, logging, heap limits, metadata path, and debug binding.

## 3.5 Version Control Workflow

Use trunk-based development:

- Keep `main` buildable.
- Use small pull requests.
- Require CI before merge.
- Tag releases once the public embedding API changes intentionally.

## 3.6 Common Pitfalls

- Parser and VM behavior must evolve together; every new AST node needs bytecode coverage.
- Object shape changes must remain consistent with inline-cache state transitions.
- SQLite schema changes must be reflected in both migrations and `MetadataStore::initializeSchema()`.
- Runtime errors should return structured failures rather than process crashes.
- Docker and CI dependency lists should stay synchronized.
