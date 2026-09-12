# CLI DevOps Tool Project Plan

## 1.1 Project File Structure

This project is a layered Go CLI application. Cobra owns the command surface, Viper owns configuration, GORM/SQLite provide local state, and adapter packages isolate cloud/container SDKs from business logic.

```text
.
├── .github/
│   └── workflows/
│       ├── ci.yml                  # Lint, test, build on every PR/push
│       └── release.yml             # Tagged release binaries and package artifacts
├── cmd/
│   └── devopsctl/
│       └── main.go                 # CLI entry point
├── configs/
│   └── config.example.yaml         # Viper YAML config example
├── deploy/
│   ├── apt/                        # APT packaging metadata placeholder
│   └── homebrew/                   # Homebrew formula placeholder
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
├── internal/
│   ├── api/                        # Optional local HTTP API endpoints
│   ├── app/                        # Application composition/root wiring
│   ├── aws/                        # AWS SDK adapter
│   ├── commands/                   # Cobra command modules
│   ├── config/                     # Viper-backed configuration loading
│   ├── database/                   # GORM connection and migration helpers
│   ├── docker/                     # Docker SDK adapter
│   ├── logging/                    # Shared logging setup
│   ├── models/                     # GORM entities and domain records
│   ├── service/                    # Business logic and orchestration
│   └── ui/                         # CLI presentation components
├── migrations/
│   └── 001_create_tasks.sql        # Initial SQLite schema
├── scripts/
│   └── build.ps1                   # Local build helper
├── test/
│   └── integration/                # Integration test fixtures and suites
├── .editorconfig
├── .env.example
├── .gitignore
├── .golangci.yml
├── Dockerfile
├── Makefile
├── docker-compose.yml
├── go.mod
└── README.md
```

### Source Code Organization

- `cmd/devopsctl`: Small executable entry point only. It should not contain business logic.
- `internal/commands`: Cobra command definitions, command validation, CLI routing, and output mode selection.
- `internal/service`: Core DevOps automation workflows, task lifecycle, test suggestion orchestration, and policy decisions.
- `internal/models`: Database-backed domain models. Keep GORM tags here, not in service logic.
- `internal/database`: Database connection, migration execution, and transaction helpers.
- `internal/config`: Viper config loading from flags, environment variables, and config files.
- `internal/api`: Optional local HTTP API for embedding the tool in automation runners.
- `internal/ui`: CLI "frontend" rendering components for loading, error, and table/list display.
- `internal/aws` and `internal/docker`: SDK adapters. These should expose narrow interfaces to services.

### CI/CD Structure

- `.github/workflows/ci.yml`: Runs format checks, linting, unit tests, integration tests where possible, and a cross-platform build matrix.
- `.github/workflows/release.yml`: Publishes release artifacts on version tags and leaves TODO hooks for Homebrew/APT publishing.
- `deploy/homebrew`: Homebrew formula templates and release tap instructions.
- `deploy/apt`: Debian package metadata, signing instructions, and repository publishing scripts.

### Tool Configuration

- `.golangci.yml`: Linter profile for Go quality gates.
- `.editorconfig`: Cross-editor formatting consistency.
- `.env.example`: Environment variable contract for local development and CI.
- `configs/config.example.yaml`: Viper config file template.
- `Dockerfile` and `docker-compose.yml`: Containerized CLI runner and local SQLite volume example.
- `Makefile` and `scripts/build.ps1`: Developer shortcuts for build/test/release preparation.

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)

- [ ] Finalize module path, binary name, and package naming conventions.
- [ ] Implement Cobra root command with global flags for config path, output format, database path, and log level.
- [ ] Implement Viper configuration precedence: flags, environment, config file, defaults.
- [ ] Implement SQLite connection lifecycle with GORM and migration runner.
- [ ] Add task CRUD service with validation and repository boundaries.
- [ ] Add structured logging and consistent error wrapping.
- [ ] Add CI workflow for `gofmt`, `go test`, `go vet`, `golangci-lint`, and binary build.
- [ ] Add basic unit tests for config loading, task service validation, and command parsing.

### Phase 2: Core Features (Medium Priority)

- [ ] Implement DevOps task generator with deterministic templates.
- [ ] Add simple pattern-matching LLM suggestion engine for failed tests and common CI errors.
- [ ] Add Docker SDK workflows: list containers, inspect images, run one-shot jobs.
- [ ] Add AWS SDK workflows for identity validation and selected service operations.
- [ ] Add local HTTP API for automation runners that need CRUD access without shelling out.
- [ ] Add integration tests for SQLite migrations and command execution.
- [ ] Add JSON, YAML, and table output renderers for all major commands.
- [ ] Add shell completions and man page generation.

### Phase 3: Polish & Optimization (Lower Priority)

- [ ] Add release automation for Homebrew tap and APT packages.
- [ ] Add telemetry-free diagnostics bundle command for support cases.
- [ ] Add command performance benchmarks for high-volume local task operations.
- [ ] Add migration rollback policy and backup guidance for SQLite files.
- [ ] Add plugin or template extension points for team-specific DevOps workflows.
- [ ] Harden AWS/Docker adapters with retry, timeout, and circuit-breaker policies.
- [ ] Improve documentation with command examples and troubleshooting guides.
