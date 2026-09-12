# Project Plan: Command-Line JSON Processor

## 1.1 Project File Structure

The project follows a standard Rust crate structure optimized for a CLI tool with a focus on maintainability and separation of concerns.

```text
.
├── .env.example                # Example environment variables
├── .github/
│   └── workflows/
│       └── ci.yml              # GitHub Actions pipeline definitions
├── Cargo.toml                  # Project metadata and dependencies
├── Cargo.lock                  # Lockfile
├── docs/                       # Project documentation
│   ├── ARCHITECTURE.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
└── src/                        # Source code
    ├── main.rs                 # Entry point, initializes Tokio and runs CLI
    ├── cli.rs                  # Clap configuration and argument parsing
    ├── processor.rs            # Core JSON processing and Serde logic
    ├── schema.rs               # Schema inference and validation logic
    ├── error.rs                # Custom error types and handling (thiserror/anyhow)
    └── utils.rs                # Helper functions for I/O and streaming
```

### Components Breakdown:
- **`src/main.rs`**: The executable entry point. Handles async runtime setup.
- **`src/cli.rs`**: Handles user input, flags, and configuration.
- **`src/processor.rs`**: The core engine. Implements streaming and data transformation.
- **`src/schema.rs`**: Implements statistical schema inference logic.
- **CI/CD (`.github/workflows`)**: Defines testing, linting, and release processes.

## 1.2 Implementation TODO List

### [x] Phase 1: Foundation (High Priority)
- [x] Initialize Rust project (`cargo init`).
- [x] Add core dependencies to `Cargo.toml` (`tokio`, `serde`, `serde_json`, `clap`, `anyhow`, `thiserror`).
- [x] Implement `src/cli.rs` to handle basic arguments (input file, output file, filter expression).
- [x] Set up the async entry point in `src/main.rs`.
- [x] Create initial CI pipeline in `.github/workflows/ci.yml` for basic build and tests.

### [ ] Phase 2: Core Features (Medium Priority)
- [ ] Implement basic JSON stream reading in `src/processor.rs`.
- [ ] Implement Serde deserialization for generic JSON values.
- [ ] Add parallel processing capabilities using Tokio tasks or Rayon for large files.
- [ ] Implement basic filtering and transformation logic based on CLI arguments.
- [ ] Add basic schema inference mechanism in `src/schema.rs`.

### [ ] Phase 3: Polish & Optimization (Lower Priority)
- [ ] Optimize memory usage by reading in chunks instead of loading the whole file.
- [ ] Implement advanced statistical schema inference and validation rules.
- [ ] Refine error handling and user-facing error messages in `src/error.rs`.
- [ ] Add comprehensive unit and integration tests.
- [ ] Setup GitHub Action for automatic releases to `crates.io`.
