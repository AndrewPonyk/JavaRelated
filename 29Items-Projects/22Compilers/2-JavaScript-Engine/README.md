# JavaScript Engine

An embeddable C++ JavaScript-like runtime with a compiler frontend, bytecode VM, runtime values/objects, inline-cache tracking, debug-session CRUD, and SQLite metadata storage.

## Requirements

- CMake 3.22+
- C++20 compiler: GCC, Clang, or MSVC
- SQLite development headers
- Ninja recommended
- Docker optional

## Build And Test

```powershell
cmake --preset debug
cmake --build --preset debug
ctest --test-dir build/debug --output-on-failure
```

Coverage gate:

```powershell
.\tools\coverage.ps1 -MinimumLineCoverage 80
```

On systems without CMake installed locally, use Docker:

```powershell
docker compose -f docker/docker-compose.yml up --build
```

Apply SQLite migrations manually when using an external metadata database:

```powershell
.\tools\apply-migrations.ps1 -DatabasePath build/jsengine.sqlite
```

## CLI

```powershell
build/debug/jsengine_cli "let x = 10; x = x + 5; x * 2;"
```

Expected output:

```text
30
```

## Supported Source Syntax

- `let`, `const`, and `var`
- Global assignment
- Object property assignment
- `return`
- Number, string, boolean, null, undefined
- Object literals
- Dot property access
- `+`, `-`, `*`, `/`, unary `-`
- Line and block comments

## Public API

```cpp
#include "jsengine/engine.h"

jsengine::Engine engine;
auto result = engine.evaluate("let user = { score: 40 + 2 }; user.score;");
```

See [API.md](docs/API.md) for debug and storage API details.

## Troubleshooting

- `cmake` not found: install CMake 3.22+ or use Docker Compose.
- `SQLite3 not found`: install SQLite development headers such as `libsqlite3-dev` on Ubuntu.
- `Ninja` generator unavailable: install Ninja or change the generator in `CMakePresets.json`.
- Docker cannot access the engine: start Docker Desktop and enable Linux containers.
- Coverage command missing `gcovr`: install `gcovr` locally or run the project in the Docker image.
