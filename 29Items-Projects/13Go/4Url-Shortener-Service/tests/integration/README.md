# Integration Tests

Place integration tests here when repository and cache behavior is implemented.

Recommended approach:

- Start PostgreSQL and Redis with Docker Compose or testcontainers-go.
- Run migrations before each test suite.
- Use isolated schemas or truncate tables between tests.
- Keep slow end-to-end browser tests separate from repository integration tests.

