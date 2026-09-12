# URL Shortener API

A production-oriented URL shortener built with Actix Web, Diesel/SQLite, and React. It provides public link creation and redirects plus token-protected management, custom aliases, expiration, visit counts, pagination, rate limiting, response compression, security headers, and health checks.

## What the application can do

1. Create a short URL from any valid HTTP or HTTPS destination.

2. Automatically generate a unique seven-character short code.

3. Let administrators create custom aliases, such as `/docs`.

4. Redirect visitors from a short link to its destination using HTTP 302.

5. Count every successful visit to a short URL.

6. Store short links and visit counts persistently in SQLite.

7. Provide a web form where users can shorten and copy links.

8. List all short URLs through an administrator-only API with pagination.

9. Retrieve a link's destination, status, timestamps, expiration, and visit count.

10. Update a short URL's destination.

11. Disable and reactivate short URLs.

12. Set an optional expiration date for a short URL.

13. Delete short URLs through the administrator API.

14. Return HTTP 410 when a link is disabled or expired.

15. Protect management operations with an administrator API token.

16. Restrict custom-alias creation to authenticated administrators.

17. Validate URL schemes, lengths, credentials, whitespace, control characters, aliases, statuses, and expiration dates.

18. Reject duplicate custom aliases with HTTP 409.

19. Rate-limit link creation per client IP and return `Retry-After` information.

20. Safely identify clients behind a configured trusted reverse proxy.

21. Return consistent JSON errors for invalid requests, authentication failures, missing resources, unsupported methods, conflicts, inactive links, rate limits, and server failures.

22. Compress API responses when supported by the client.

23. Restrict browser access to explicitly configured CORS origins.

24. Apply CSP, optional HSTS, frame protection, content-type protection, referrer restrictions, and other security headers.

25. Expose a `/health` endpoint that verifies database connectivity.

26. Run database migrations automatically during startup.

27. Run locally with Rust and Node.js or as an API-plus-frontend Docker Compose stack.

28. Persist Docker-hosted data in a named volume.

29. Support HTTPS enforcement at a deployment platform or reverse proxy.

30. Deploy using the included Railway and Fly.io configuration examples.

## Quick start with Docker

Prerequisites: Docker Engine with Docker Compose v2.

1. Copy the example configuration:

   ```bash
   cp .env.example .env
   ```

   On PowerShell, use `Copy-Item .env.example .env`.

2. Replace `ADMIN_API_TOKEN` in `.env` with a random value of at least 32 characters. For example:

   ```bash
   openssl rand -hex 32
   ```

3. Build and start both services:

   ```bash
   docker compose up --build -d
   docker compose ps
   ```

Open the frontend at `http://localhost:3000`. The API is available locally at `http://localhost:8080`; migrations run automatically and the SQLite database is stored in the `url-data` volume. The API port is bound to loopback by default, while Nginx proxies browser requests and replaces spoofable forwarding headers.

## Local development

Prerequisites:

- Rust 1.88 or newer
- Node.js 22 and npm
- No external database; SQLite is bundled

Start the API:

```bash
cp .env.example .env
# Set a new ADMIN_API_TOKEN in .env
cargo run
```

In another terminal, start the frontend:

```bash
cd frontend
npm ci
npm run dev
```

The API listens on `127.0.0.1:8080` and Vite on `0.0.0.0:3000` by default. The Vite development server proxies `/api` and `/health` to the API.

## Configuration

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `ADMIN_API_TOKEN` | Yes | None | Management token; 32–4096 printable ASCII characters without spaces. |
| `BIND_ADDRESS` | No | `127.0.0.1:8080` | API IP address and port. |
| `DATABASE_URL` | No | `url_shortener.db` | SQLite file path. Its parent directory must exist and be writable. |
| `PUBLIC_BASE_URL` | No | `http://localhost:8080` | Origin used in generated short URLs. |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000` | Comma-separated exact browser origins; wildcards are rejected. |
| `CREATION_RATE_WINDOW_SECONDS` | No | `60` | Creation-rate window, from 1 to 86,400 seconds. |
| `CREATION_RATE_LIMIT` | No | `30` | Creations allowed per client/window, from 1 to 100,000. |
| `CREATION_RATE_MAX_CLIENTS` | No | `10000` | Maximum client addresses retained by the in-process limiter, from 100 to 1,000,000. |
| `TRUST_PROXY_HEADERS` | No | `false` | Use the first `X-Forwarded-For` address for rate limiting. Enable only behind an edge that replaces client-supplied forwarding headers. |
| `ENABLE_HSTS` | No | `false` | Adds HSTS. Requires an HTTPS `PUBLIC_BASE_URL`; enable only after TLS is working. |
| `RUST_LOG` | No | `info` | Tracing filter. |

Configuration is validated at startup. The committed [environment template](.env.example) contains every supported variable; `.env`, SQLite files, build output, and frontend dependencies are ignored by Git.

## API

The complete machine-readable contract, schemas, examples, error responses, and constraints are in [docs/openapi.yaml](docs/openapi.yaml).

| Method and path | Authentication | Behavior |
| --- | --- | --- |
| `GET /health` | Public | Verifies that the service can query the database. |
| `POST /api/v1/urls` | Public | Creates a short URL. A supplied `custom_code` requires the admin token. |
| `GET /api/v1/urls?page=1&per_page=20` | Admin | Lists URLs; `per_page` is limited to 1–100. |
| `GET /api/v1/urls/{code}` | Admin | Returns URL metadata. |
| `PUT /api/v1/urls/{code}` | Admin | Replaces destination, status, and expiration. |
| `DELETE /api/v1/urls/{code}` | Admin | Deletes a URL. |
| `GET /{code}` | Public | Atomically records a visit and responds with HTTP 302. |

Create and follow a URL:

```bash
curl -sS -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"long_url":"https://example.com/docs"}'

curl -i http://localhost:8080/SHORT_CODE
```

Create a custom alias and list records:

```bash
curl -sS -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Token: YOUR_ADMIN_TOKEN' \
  -d '{"long_url":"https://example.com/docs","custom_code":"docs","expires_at":null}'

curl -sS 'http://localhost:8080/api/v1/urls?page=1&per_page=20' \
  -H 'X-Admin-Token: YOUR_ADMIN_TOKEN'
```

Errors use a stable JSON envelope:

```json
{
  "code": "validation_error",
  "message": "invalid request: long_url must be an absolute URL"
}
```

Expected statuses include 400 for validation, 401 for missing/invalid management credentials, 404 for unknown records/routes, 405 for unsupported methods, 409 for alias collisions, 410 for disabled or expired links, 429 for rate limits, and 500 for unexpected failures. Rate-limit responses include `Retry-After`.

## Verification

Run the same core checks as CI:

```bash
cargo fmt --all -- --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test --all-targets --all-features
cargo llvm-cov --all-targets --workspace --fail-under-lines 80
cargo audit
cd frontend
npm ci
npm test
npm run build
npm audit --omit=dev --audit-level=high
```

CI also validates Compose and builds both container images. Integration tests create isolated temporary SQLite databases and cover lifecycle, authorization, malformed inputs, pagination boundaries, collisions, expiration/status behavior, bounded proxy-aware rate limits, concurrent visit increments, and standard error responses.

## Production deployment

- Terminate TLS at the platform/edge and redirect HTTP to HTTPS. Set `PUBLIC_BASE_URL` to the public HTTPS origin, then enable HSTS. The Fly example already sets `force_https = true`.
- Keep `ADMIN_API_TOKEN` in a platform secret store, rotate it periodically, and never bake it into an image or frontend bundle.
- Set `TRUST_PROXY_HEADERS=true` only when the API cannot be reached around a proxy that replaces `X-Forwarded-For`. The included Nginx/Compose path does this.
- Run one API replica with SQLite. Mount `/data` on durable storage, schedule backups of `url_shortener.db`, and test restores. Move to managed PostgreSQL before horizontal scaling.
- Keep the API and frontend images immutable. Both run without Linux capabilities and with read-only root filesystems under Compose.
- Railway discovers the root `railway.toml`; configure a persistent `/data` volume and service environment variables in Railway. The optional GitHub deployment job additionally requires protected `RAILWAY_TOKEN` and `RAILWAY_SERVICE_ID` secrets.

## Troubleshooting

- **Startup says `ADMIN_API_TOKEN is required`:** create `.env` from the example and set a 32+ character token.
- **Startup rejects HSTS:** use an HTTPS `PUBLIC_BASE_URL`, or leave `ENABLE_HSTS=false` during local HTTP development.
- **Database cannot be opened:** ensure the `DATABASE_URL` parent exists and the runtime user can write to it. In Docker, confirm the `url-data` volume is mounted.
- **Browser request is blocked by CORS:** add the exact frontend origin to `CORS_ALLOWED_ORIGINS`; paths and wildcards are intentionally invalid.
- **All proxied users share one rate limit:** enable trusted proxy headers only after configuring the edge to replace incoming `X-Forwarded-For`.
- **New clients receive 429 while existing clients do not:** the limiter reached `CREATION_RATE_MAX_CLIENTS`; raise it within available memory or shorten the rate window.
- **Port already in use:** change the host-side port mapping in `docker-compose.yml` or stop the conflicting process.
- **Container is unhealthy:** inspect `docker compose logs api`; `GET /health` returns 500 when the database is unavailable.

See [architecture](docs/ARCHITECTURE.md), [technical notes](docs/TECH-NOTES.md), and the [delivery plan](docs/PROJECT-PLAN.md) for design boundaries and scaling guidance.
