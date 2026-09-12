# Programming Language

A C++ DSL compiler and package-registry playground for data transformation and analysis. The implemented core includes recursive descent parsing, Pratt expression parsing, gradual type inference, executable IR metadata, REPL evaluation, and in-process package dependency resolution.

## Build

```powershell
cmake -S . -B build -DPLANG_ENABLE_TESTS=ON
cmake --build build
ctest --test-dir build --output-on-failure
```

`PLANG_ENABLE_LLVM` is optional. The default build emits deterministic executable IR text that keeps local builds independent from a pinned LLVM installation.

On Windows, the executable is usually written to `build\Debug\plang.exe` with Visual Studio generators. Ninja and single-config generators usually write to `build\plang.exe`.

## Try

```powershell
build\Debug\plang.exe
```

Then enter statements such as:

```text
let answer = 40 + 2;
answer |> print;
```

## Docker

```powershell
docker compose --env-file .env.example -f docker/docker-compose.yml up --build
```

The compose stack starts PostgreSQL with the registry migration mounted and builds the `plang` CLI image.

## API Facade

The package registry facade is an in-process C++ API used by tests and future service adapters. It supports:

- `POST /packages` with body `name=core`
- `GET /packages?limit=50&offset=0`
- `GET /packages/{name}`
- `PUT /packages/{name}` with body `name=new-name`
- `DELETE /packages/{name}`
- `POST /packages/{name}/versions` with body `version=1.2.0&sha256=<64 hex chars>&manifest={}&deps=util@>=1.0.0`
- `GET /packages/{name}/versions/{version}`
- `DELETE /packages/{name}/versions/{version}`
- `POST /packages/{name}/versions/{version}/yank`
- `POST /resolve` with body `deps=core@^1.0.0`

Validation rules:

- Package names are 1-128 characters and may contain letters, digits, `_`, `-`, and `.`.
- Versions use `MAJOR.MINOR.PATCH` semantic versions.
- Archives require a 64-character SHA-256 hex digest.
- Manifests must be JSON object or array text no larger than 64 KiB.
- List responses are paginated with `limit` capped at 100.

## Configuration

Copy `.env.example` to `.env` for local use and set `PLANG_REGISTRY_DB_PASSWORD` to a local value. Keep real `.env` files untracked.

## Troubleshooting

- `cmake` not found: install CMake and a C++20 compiler, or use the Docker command above.
- Docker Compose reports missing `PLANG_REGISTRY_DB_PASSWORD`: pass `--env-file .env.example` for local testing or define the variable in your shell.
- Tests fail after changing parser behavior: run `ctest --test-dir build --output-on-failure` to see the exact failing unit or integration case.
