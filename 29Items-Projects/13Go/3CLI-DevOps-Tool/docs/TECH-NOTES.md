# CLI DevOps Tool Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended GitHub Actions stages:

1. **Lint:** `gofmt`, `go vet`, and `golangci-lint`.
2. **Test:** Unit tests on every push and PR. Integration tests can run with SQLite and mocked Docker/AWS clients by default.
3. **Build:** Cross-compile binaries for Linux, macOS, and Windows.
4. **Package:** Generate checksums, archives, Homebrew formula updates, and Debian package metadata.
5. **Deploy:** Publish GitHub Releases first, then update Homebrew/APT channels after release verification.

Use separate release workflows triggered by tags such as `v1.2.3`. Keep deployment credentials in GitHub Actions secrets and scope tokens to the specific package repositories.

## 3.2 Testing Strategy

- **Unit tests:** Use Go's standard `testing` package plus table-driven tests. Target 70-80% coverage on service, config, and command validation packages.
- **Integration tests:** Use temporary SQLite databases and local filesystem fixtures. Mock AWS/Docker by default; reserve live tests for explicitly enabled CI jobs.
- **End-to-end tests:** Execute the compiled CLI binary with temporary config and database paths. Verify exit codes, stdout/stderr contracts, and JSON output shape.
- **Golden files:** Useful for table output and suggestion text, but keep them small to avoid brittle snapshots.
- **Contract tests:** Add tests for JSON output fields before external automation depends on them.

## 3.3 Deployment Strategy

The primary artifact is a static or near-static CLI binary distributed through:

- GitHub Releases for direct downloads.
- Homebrew formula for macOS and Linux users.
- APT package for Debian/Ubuntu environments.
- Container image for CI runners that prefer immutable tool images.

The Docker image should contain the CLI binary and minimal runtime dependencies. Mount the SQLite database path as a volume when command history must persist between runs.

## 3.4 Environment Management

Configuration precedence should be:

1. Explicit CLI flags.
2. Environment variables with the `DEVOPSCTL_` prefix.
3. YAML config file.
4. Built-in defaults.

Use `.env.example` as the documented variable contract. Real `.env` files should remain local and ignored by Git.

Important configuration values:

- `DEVOPSCTL_CONFIG`: Optional config file path.
- `DEVOPSCTL_DB_PATH`: SQLite database path.
- `DEVOPSCTL_LOG_LEVEL`: `debug`, `info`, `warn`, or `error`.
- `DEVOPSCTL_AWS_REGION`: Default AWS region.
- `DEVOPSCTL_DOCKER_HOST`: Optional Docker daemon endpoint override.
- `DEVOPSCTL_API_TOKEN`: Token for optional local API mode.

## 3.5 Version Control Workflow

Use **trunk-based development with short-lived branches**.

This project is a CLI tool with automated tests and release tags, so long-lived release branches add unnecessary drift. Keep `main` releasable, protect it with CI, and use pull requests for review. Create release tags from `main` and patch forward instead of maintaining multiple active release branches unless enterprise support requirements appear.

## 3.6 Common Pitfalls

- Cobra command packages can become dumping grounds. Keep command handlers thin and push business rules into services.
- Viper can hide configuration precedence bugs. Test flag, environment, and file precedence explicitly.
- SQLite is reliable for local state, but concurrent writes from multiple CLI processes need short transactions and sensible busy timeouts.
- GORM migrations should be controlled. Avoid accidental destructive auto-migration in production user data.
- Docker SDK behavior differs by platform, especially Docker Desktop on Windows/macOS versus native Linux sockets.
- AWS SDK clients must use contexts with timeouts so CLI commands do not hang indefinitely.
- Do not store cloud credentials in the local database. Use AWS profiles, environment variables, or platform credential stores.
- Pattern-matching "LLM suggestions" can overfit to narrow logs. Return confidence levels and let users inspect the source evidence.
