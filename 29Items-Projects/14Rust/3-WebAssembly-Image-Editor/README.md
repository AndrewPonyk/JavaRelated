# WebAssembly Image Editor

A privacy-first image editor in which Canvas handles decoding/export, a Rust WebAssembly module performs filters and crops in a Web Worker, and image pixels remain in the browser. The optional PostgreSQL control plane persists only anonymous identities, projects, and validated edit recipes.

## What the app can do

1. Import JPEG images from the user's device.
2. Import PNG images.
3. Import WebP images.
4. Reject empty, unsupported, oversized, or excessively large decoded images.
5. Keep image pixels entirely in the browser; images are never uploaded to the API.
6. Preview the currently edited image.
7. Apply a grayscale filter.
8. Invert image colors.
9. Apply a sepia filter.
10. Adjust image brightness.
11. Adjust image contrast.
12. Adjust image saturation.
13. Blur an image.
14. Sharpen an image.
15. Crop an image using numeric crop coordinates.
16. Crop to a selected aspect ratio.
17. Suggest a crop automatically using local image analysis.
18. Run an optional ONNX auto-cropping model when a valid model is configured.
19. Verify the ONNX model checksum before executing it.
20. Fall back to heuristic auto-cropping if the ONNX model cannot load or execute.
21. Undo editing operations.
22. Redo previously undone operations.
23. Reset the image to its original imported state.
24. Preserve edits as a non-destructive recipe that can be replayed.
25. Process filters and crops through Rust compiled to WebAssembly.
26. Run WebAssembly image processing in a Web Worker to keep the interface responsive.
27. Export the edited image as JPEG.
28. Export the edited image as PNG.
29. Export the edited image as WebP.
30. Configure JPEG and WebP export quality.
31. Limit the maximum width of the exported image.
32. Generate a sanitized export filename based on the original filename.
33. Create an anonymous editor session.
34. Create saved projects.
35. List saved projects with bounded pagination.
36. Rename saved projects.
37. Delete saved projects after confirmation.
38. Create reusable editing presets.
39. Associate presets with a project.
40. List saved presets with bounded pagination and optional project filtering.
41. Load a preset into the editor.
42. Rename a preset or replace its saved recipe with the current edits.
43. Delete saved presets after confirmation.
44. Keep projects and presets isolated to their owning anonymous session.
45. Validate project, preset, crop, filter, pagination, model, and API inputs.
46. Detect duplicate project or preset names and return a conflict response.
47. Rate-limit API requests.
48. Report API and database readiness through `/api/health`.
49. Work as a pixel-only editor without PostgreSQL when saved presets are disabled.
50. Run PostgreSQL, the API, and the web application as a complete Docker Compose stack.
51. Deploy the frontend and serverless API to Vercel.
52. Adapt the editor layout for desktop and smaller-screen displays.

## Docker quick start

Prerequisite: Docker Desktop or Docker Engine with Compose.

1. Copy `.env.example` to `.env`.
2. Replace every `replace-with-...` value. Use independently generated random values; `DATABASE_PASSWORD` must be URL-safe and `JWT_SECRET` must contain at least 32 characters. `openssl rand -hex 32` is one suitable generator.
3. Start the full stack:

```bash
docker compose up --build
```

Open http://localhost:8080. Readiness is available at http://localhost:8080/api/health. Set `WEB_PORT` when that host port is occupied. The database, bundled/unprivileged Node API, and unprivileged Nginx web service each have a health check. Stop containers with `docker compose down`; the named PostgreSQL volume is retained.

## Local development

Prerequisites:

- Node.js 22 and npm 10+
- Rust 1.85+ with the `wasm32-unknown-unknown` target
- `wasm-pack` 0.13.1
- PostgreSQL 16+ when saved presets are enabled

Install and start the pixel-only editor:

```bash
npm install
npm run build:wasm
npm run dev
```

To enable persisted presets, copy `.env.example` to `.env.local`, replace its secret/database values, apply both migrations with a privileged migration identity, grant a non-superuser runtime identity as described in [database/README.md](database/README.md), and run in separate terminals:

```bash
npm run api
npm run dev
```

Vite loads the root `.env.local` and proxies `/api` to `API_PROXY_TARGET` (`http://localhost:3000` by default). The API command also loads that file. Neither `.env` nor `.env.local` is committed.

## Release checks

```bash
npm run format:check
npm run typecheck
npm run lint
npm run test:coverage
npm test
npm run build
npm audit --omit=dev
cargo audit
docker compose config --quiet
```

`npm test` runs Vitest, native Rust tests, and the Playwright Chromium flow. Install its local browser once with `npx playwright install chromium`. Coverage gates are 80% for statements, lines, and functions and 70% for branches.

## Deployment

Vercel is the primary public deployment. Configure `DATABASE_URL`, `JWT_SECRET`, `CORS_ORIGIN`, and `PRESET_API_RATE_LIMIT_PER_MINUTE` in scoped Vercel environments. Vercel terminates HTTPS. A self-hosted container deployment must be placed behind a trusted HTTPS load balancer or reverse proxy; port 8080 is plain HTTP intended for the internal network.

See [API documentation](docs/API.md), [architecture](docs/ARCHITECTURE.md), and [technical notes](docs/TECH-NOTES.md).

## Troubleshooting

- **Compose reports a required variable is missing:** create `.env` from `.env.example` and replace all secret markers.
- **Database is healthy but API is not:** run `docker compose logs api db`; verify the runtime password is URL-safe and unchanged from the password used when the volume was initialized.
- **Credentials changed after first startup:** PostgreSQL initialization files run only for a new volume. Apply the role/password change manually. Use `docker compose down -v` only when all local database data may be permanently discarded.
- **WASM module is missing:** run `npm run build:wasm` before `npm run dev`.
- **Local API cannot find configuration:** ensure the root `.env.local` exists; `npm run api` deliberately fails closed without it.
- **Saved presets return 401:** clear this site's session storage and reload so the browser can create a new anonymous session.
- **Saved presets return 409:** project and preset names are unique per anonymous owner; choose another name.
- **Browser tests cannot launch:** run `npx playwright install chromium`.
- **Auto-crop reports the heuristic provider:** this is the working default. ONNX is used only when a valid manifest, checksum, and model artifact are deployed.

## Privacy and authentication scope

The application has no image-upload endpoint. Image bytes, filenames, EXIF data, previews, and exports are not sent to the API. Authentication is intentionally passwordless and anonymous, so password hashing is not applicable. The API uses bearer tokens rather than cookies, so cookie-based CSRF is not part of its threat surface; XSS is mitigated with React escaping, strict schemas, and a restrictive CSP.
