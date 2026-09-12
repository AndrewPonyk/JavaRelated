# Technical Notes: Command-Line JSON Processor

## 3.1 CI/CD Pipeline Design

Our CI/CD pipeline will be built using GitHub Actions, focusing on the following key stages:
- **Linting & Formatting**: `cargo fmt -- --check` and `cargo clippy -- -D warnings`. Ensure code is idiomatic and clean.
- **Testing**: `cargo test` run across major target platforms (Linux, macOS, Windows) to ensure cross-platform compatibility.
- **Building**: `cargo build --release` for release artifacts.
- **Deploying (Release)**: On a new Git tag (e.g., `v1.0.0`), the pipeline will:
  1. Build optimized release binaries for all target architectures.
  2. Create a GitHub Release with attached binaries.
  3. Publish the crate to `crates.io` using `cargo publish`.

## 3.2 Testing Strategy

- **Unit Testing**: We will use the built-in Rust `#[test]` framework. Target at least 80% coverage for core processing logic (parsing, filtering). Use the `proptest` crate for property-based testing of the parser to ensure it handles edge cases correctly.
- **Integration Testing**: Tests in the `tests/` directory will compile the application as a black box and run the CLI against sample input JSON files, asserting against expected stdout/stderr output using the `assert_cmd` and `predicates` crates.
- **Benchmarking**: Use `criterion` to run performance benchmarks on core components and track regressions over time.

## 3.3 Deployment Strategy

As this is a CLI tool rather than a web service, "deployment" means distribution.
- **Primary Distribution**: `cargo install <crate-name>` from `crates.io`.
- **Secondary Distribution**: Pre-compiled binaries available on GitHub Releases.
- **Containerization**: Provide a minimalistic Docker image (`FROM scratch` or `alpine`) for users who want to run the tool within containerized data pipelines without installing Rust or the binary on the host.

## 3.4 Environment Management

CLI apps typically don't rely heavily on environment variables compared to web services, but we might use them for defaults or logging levels.

`.env.example`:
```env
# Logging level (error, warn, info, debug, trace)
RUST_LOG=info

# Default processing threads (optional, defaults to number of CPU cores)
JSON_PROC_THREADS=4
```

## 3.5 Version Control Workflow

**GitHub Flow** is recommended for this project:
- `main` is always stable and buildable.
- New features or bug fixes are developed in short-lived feature branches (`feature/add-jq-syntax`, `fix/oom-large-file`).
- Branches are merged into `main` via Pull Requests, which require passing CI checks and peer review.
- Releases are tagged directly on `main` when ready.

## 3.6 Common Pitfalls

- **String Allocations**: Parsing JSON can lead to massive heap allocations if not careful. We must use `&str` and `Cow<str>` to borrow from the source buffer where possible instead of cloning strings `String`.
- **Async Overhead**: For very small files, the overhead of the Tokio runtime might be slower than purely synchronous processing. We should provide a fast path for small inputs or ensure the chunk size is large enough to amortize async scheduling costs.
- **Complex JSON Structures**: Handling arbitrarily nested JSON (e.g., recursive structures) can cause stack overflows. We should limit nesting depth or use iterative processing where necessary.
