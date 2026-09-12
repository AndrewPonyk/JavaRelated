# WebAssembly Image Editor - Technical Notes

## 1. CI/CD pipeline

GitHub Actions in `.github/workflows/ci.yml` runs the delivery gate in this order:

```mermaid
flowchart LR
  A[Checkout and caches] --> B[Install Node/Rust/wasm-pack]
  B --> C[Generate release WASM]
  C --> D[Format, lint, typecheck]
  D --> E[80% coverage gate + PostgreSQL-memory integration + Rust tests]
  E --> F[Playwright Chromium]
  F --> G[Production build]
  G --> H[Upload artifact]
```

`deploy.yml` builds the same application and uses scoped Vercel credentials for preview deployments on pull requests and production deployment from the protected default branch. Deployment environments provide the production database URL and signing secret; they are never compiled into the web bundle.

## 2. Testing strategy

- **Rust:** native unit, integration, and property tests cover dimensions, filters, alpha preservation, crop bounds, and invalid input.
- **TypeScript:** Vitest covers contracts, Canvas/model adapters, authentication, rate limiting, services, HTTP routes, validation/error mapping, and PostgreSQL repository behavior using `pg-mem`; browser tests cover the integrated UI workflow.
- **Browser:** Playwright generates a small PNG, imports it, applies an effect, auto-crops, exports, runs Axe checks, and fails on unexpected non-local network traffic.
- **Coverage:** `npm run test:coverage` enforces 80% minimums for statements, lines, and functions plus 70% for branches. Authentication, ownership, pagination, duplicate names, invalid UUIDs, failed readiness, rate limits, Canvas boundaries, and ONNX fallback/success paths have direct assertions.

Run all release checks locally:

```bash
npm run format:check
npm run typecheck
npm run lint
npm test
npm run test:coverage
npm run build
```

## 3. Deployment and container strategy

### Vercel

Vercel serves `apps/web/dist` and routes `/api/*` to `api/[...path].ts`, which delegates to the same handler used by Docker. Configure `DATABASE_URL`, `JWT_SECRET`, `CORS_ORIGIN`, and `PRESET_API_RATE_LIMIT_PER_MINUTE` as server runtime values. The build runs `wasm-pack` before Vite so generated assets are present in `public/wasm`.

### Docker Compose

The included Compose topology is a full local deployment:

```mermaid
flowchart LR
  Browser -->|:8080| Web[Nginx web container]
  Web -->|/api| API[Node API container]
  API --> DB[(PostgreSQL 16)]
```

`Dockerfile` has separate build/runtime stages. The web image contains only static output and unprivileged Nginx. The API source is bundled into one Node ESM artifact; its final image contains no TypeScript launcher or development dependencies and runs as the unprivileged `node` user with a read-only filesystem. PostgreSQL applies ordered migrations and then creates a separate non-superuser runtime role.

```bash
cp .env.example .env
# Replace all secret markers, then:
docker compose up --build
# Web: http://localhost:8080
# API health through proxy: http://localhost:8080/api/health
```

Use `docker compose down` to stop the stack. The named PostgreSQL volume is retained; use an explicitly approved `docker compose down -v` only when local data may be discarded.

## 4. Environment management

Copy `.env.example` to `.env.local` for direct local execution or to `.env` for Compose. Compose fails closed when database/JWT secrets are missing. Separate database instances and independent random signing/database secrets are required for development, preview, and production.

```dotenv
# Public build-time values. VITE_ values are visible in the browser.
VITE_MODEL_MANIFEST_URL=/models/model-manifest.json
VITE_ENABLE_PRESET_API=true
VITE_API_BASE_URL=
VITE_MAX_IMAGE_PIXELS=40000000
VITE_MAX_IMAGE_BYTES=25000000
API_PROXY_TARGET=http://localhost:3000

# Server-only values; examples are not deployable secrets.
DATABASE_URL=postgresql://image_editor_app:url-encoded-password@localhost:5432/image_editor
JWT_SECRET=replace-with-at-least-32-random-characters
CORS_ORIGIN=http://localhost:5173
PRESET_API_RATE_LIMIT_PER_MINUTE=60
PORT=3000
```

Never put a secret in a `VITE_` variable. Rotate the production signing key through a controlled session-invalidating deployment. A model manifest is public configuration and must reference only reviewed public assets.

## 5. Version control workflow

Use trunk-based GitHub Flow: create short-lived branches, open a pull request, require CI and review, squash-merge to protected `main`, and tag production releases. Keep `Cargo.lock` and `package-lock.json` committed. `apps/web/public/wasm` is generated during builds and is ignored. Schema migrations are append-only, applied once, and compatible with the immediately preceding deployed API during a rollout.

## 6. Stack-specific operational notes

- Build with release WASM; generated bindings and the Rust source must stay compatible with the installed `wasm-pack` version.
- A browser canvas can exceed practical memory limits before the input file looks large. `VITE_MAX_IMAGE_PIXELS` is enforced after decode.
- `VITE_MAX_IMAGE_BYTES` rejects oversized encoded input before browser decoding.
- Transfer buffers to the worker; do not store large buffers in browser persistence or global app state.
- `canvas.toBlob` can reject a requested codec or return no blob; the export adapter reports a user-safe failure.
- ONNX tensor dimensions, channel ordering, normalization, checksum, and output format must match the reviewed model exactly. The shipped heuristic remains available on every model failure.
- Vercel functions require a reachable PostgreSQL instance and a server-side connection configuration. For high concurrency use a managed pooler compatible with the `pg` driver.
- The built-in fixed-window limiter is appropriate for one container. A horizontally scaled Vercel deployment should additionally enforce a distributed/platform rate limit at the edge.
- Nginx compresses text/JSON/WASM responses, overwrites client-supplied forwarding headers, and revalidates the fixed-name WASM bundle to prevent version skew.
