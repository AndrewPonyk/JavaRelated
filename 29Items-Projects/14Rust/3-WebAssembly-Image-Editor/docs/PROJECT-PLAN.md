# WebAssembly Image Editor - Project Plan

## Product boundary

This release is a privacy-first browser editor. JPEG, PNG, and WebP images are decoded, edited, cropped, and exported entirely in the browser. Rust compiled to WebAssembly performs pixel transforms in a Web Worker. The optional PostgreSQL control plane stores only anonymous-account metadata, projects, and edit recipes; it never accepts image bytes, filenames, previews, or EXIF data.

Auto-crop works without a remote service. The shipped manifest selects a deterministic local heuristic. The same adapter can load a reviewed ONNX artifact when a manifest with a checksum and tensor contract is deployed.

## Implemented repository structure

```text
.
|- api/[...path].ts                    # Vercel catch-all API entry point
|- apps/web/
|  |- api/presets.ts                   # Vercel adapter for the API handler
|  |- e2e/editor.spec.ts               # Browser import/edit/export journey
|  |- public/
|  |  |- models/model-manifest.json    # Auto-crop provider contract
|  |  `- wasm/                         # wasm-pack output at build time
|  |- server/
|  |  |- application.ts                # Configured API composition root
|  |  |- auth.ts                       # Signed anonymous session tokens
|  |  |- database.ts                   # PostgreSQL transaction boundary
|  |  |- http.ts                       # HTTP routes and error envelope
|  |  |- repositories.ts               # Parameterized PostgreSQL CRUD
|  |  |- services.ts                   # Ownership and domain rules
|  |  `- standalone.ts                 # Local/Docker Node HTTP server
|  |- src/
|  |  |- components/                   # Accessible editor and model status UI
|  |  |- contracts/editor.ts           # Shared strict Zod contracts
|  |  |- hooks/                        # Editor state/history and preset library
|  |  |- services/                     # Canvas, auto-crop, API, WASM adapters
|  |  |- workers/                      # Transferable-buffer image worker
|  |  `- test/                         # Vitest setup
|  |- package.json
|  `- vite.config.ts
|- crates/image-processor/
|  |- src/filters.rs                   # Deterministic RGBA filter kernels
|  |- src/geometry.rs                  # Validated crop operations
|  `- src/lib.rs                       # wasm-bindgen facade
|- database/
|  |- migrations/                     # Forward-only schema/index migrations
|  `- docker-init/                    # Least-privilege Compose runtime role
|- docs/                               # Architecture, operations, and API notes
|- .github/workflows/                  # CI and Vercel deployment
|- Dockerfile                          # WASM build, Node API, Nginx web targets
|- docker-compose.yml                  # PostgreSQL + API + web stack
|- nginx.conf                          # Static and /api reverse proxy configuration
`- package.json                        # Single-command quality workflow
```

## Delivery checklist

### Phase 1 - Foundation

- [x] Rust workspace, validated RGBA images, typed errors, native tests, and wasm-bindgen exports.
- [x] React/Vite shell with accessible upload, loading, error, clear, and preview states.
- [x] JPEG/PNG/WebP decoding, Canvas rendering, object-URL cleanup, file-type and pixel-limit validation.
- [x] Generated release WASM package and version-safe cache/revalidation configuration.
- [x] Formatting, linting, type checking, unit tests, browser tests, production build, and dependency audit commands.

### Phase 2 - Core editor and control plane

- [x] Grayscale, invert, sepia, brightness, contrast, saturation, blur, sharpen, crop, reset, undo, and redo.
- [x] Non-destructive edit recipe replay through a dedicated Web Worker with transferable RGBA buffers.
- [x] Export format, quality, and maximum-width controls.
- [x] Local auto-crop with manifest validation, optional checksum-verified ONNX execution, and a safe heuristic fallback.
- [x] Anonymous signed sessions plus complete project and preset CRUD with strict validation, ownership checks, PostgreSQL RLS, and rate limiting.
- [x] Playwright import/edit/auto-crop/export coverage and a no-unexpected-network-request assertion.

### Phase 3 - Release hardening

- [x] Multi-stage Docker image and Compose stack for PostgreSQL, API, and Nginx-hosted web application.
- [x] GitHub Actions quality pipeline and Vercel preview/production deployment workflow.
- [x] Correlation IDs, structured server errors, no-store API responses, security headers, and health endpoints.
- [x] Environment inventory, migration instructions, operational limits, and API documentation.
- [x] Bounded pagination, response compression, least-privilege containers/database access, and 80%+ automated coverage.

## Completion criteria

The release is complete when `npm test`, `npm run typecheck`, `npm run lint`, and `npm run build` succeed from a clean checkout; `docker compose up --build` exposes the web application on port 8080; and the API health endpoint reports ready after PostgreSQL migrations finish. The implementation uses no server-side image upload path.
