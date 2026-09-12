# Linear Algebra Visualizer

Interactive 2D visualizer for vector spaces, linear transformations, determinants and
eigenvalues/eigenvectors — smooth WebGL animations (Three.js), proper math notation (MathJax),
and auto-generated practice exercises that adapt to your mastery level.

**Stack:** TypeScript · React 19 · Three.js · Zustand · Vite · Vercel Functions · Prisma/PostgreSQL

## Features

### Visualization canvas (WebGL)

- Draws a 2D coordinate plane: static reference grid + emphasized x/y axes
- Draws a second, bright grid that **animates whenever the matrix changes** — you watch the plane
  itself transform
- Shows basis vectors **î and ĵ as arrows** (blue/orange) moving to the matrix's columns
- Shows the **unit square** stretching with the transform — its area _is_ the determinant; it
  **turns red when orientation flips** (det < 0)
- Overlays **eigenvector arrows** (green), each scaled by its eigenvalue — the invariant lines of
  the transform; hidden automatically when eigenvalues are complex
- **Rotation-aware animation**: rotating 180° animates as an actual rotation (polar
  decomposition), not a collapse through the zero matrix
- Animation controls: **speed slider (0.25×–3×)**, **Replay**, **Reset to identity**
- **Layer toggles**: transformed grid on/off, unit square on/off, eigenvectors on/off
- Respects `prefers-reduced-motion` (snaps instead of animating)
- Detects missing WebGL and shows a friendly fallback instead of crashing; survives GPU context
  loss; resizes responsively

### Matrix editing

- **2×2 matrix editor** with inline validation (non-numbers rejected with a message, never applied)
- **One-click presets**: Identity, Rotate 90°, Shear, Scale ×2, Reflect X
- Enter key applies; the editor re-syncs when an exercise or preset changes the matrix

### Interactive vectors

- **Add up to 5 colored, labeled vectors** (v1, v2, …)
- **Drag a vector's tip directly on the canvas** — works even mid-transform (the drag is
  inverse-mapped through the current matrix)
- Vectors are drawn as A·v with **rigid, non-shearing arrowheads**; live coordinate readout in the
  panel; remove individually

### Live math readouts (MathJax LaTeX)

- Current matrix rendered as proper notation: `A = (pmatrix)`
- **det(A) with its geometric meaning**: "area × 4, orientation flipped"
- **Eigen analysis that adapts to the case**: distinct real λ₁, λ₂ _with their directions_;
  repeated (scalar "every direction is invariant" vs. defective "one invariant line"); complex
  pair explained as "rotation by 90°, scale 1.0"
- Degrades to readable raw TeX if the MathJax CDN is blocked — never crashes

### Practice mode (auto-generated exercises)

- **Five exercise topics**: vector addition · linear combinations (find α, β) · applying a
  transform (compute A·v) · determinants · eigenvalues
- **"Find an eigenvector" variant** appears from difficulty 4, graded by colinearity — (−2,−2)
  counts for (1,1)
- **Answer-first generation**: every exercise has clean integer answers by construction (e.g.
  eigen matrices built as P·D·P⁻¹)
- **Seeded & reproducible**: the same (topic, difficulty, seed) always regenerates the identical
  exercise
- **5 difficulty levels** — number magnitudes scale up
- **Adaptive progression**: per-topic mastery score (correct +15 % of remaining gap, wrong −30 %);
  topics unlock in curriculum order at the 80 % gate; the weakest topic gets reviewed once all are
  gated; difficulty is auto-picked from mastery
- The exercise's matrix is **mirrored into the canvas** so you see the question
- Answers accept **decimals and fractions ("1/3")**; inputs labeled per question (x/y, α/β, λ₁/λ₂)
- **Tolerant grading** (float-safe; eigenvalue pairs order-insensitive, coefficients
  order-sensitive), LaTeX feedback showing the correct answer on a miss
- **One graded submission per exercise** (no answer-farming after feedback), Enter submits, Next
  generates a fresh one
- **Streak counter** 🔥 and **per-topic mastery progress bars**

### Progress & backend

- **No signup**: anonymous device UUID in localStorage — zero PII
- Progress saved to **PostgreSQL** through 3 serverless endpoints: `GET /api/health`,
  `GET /api/progress`, `POST /api/attempts`
- **Server-authoritative mastery**: recomputed in a DB transaction per attempt — a tampered client
  can't inflate its score
- **Idempotent writes**: every attempt carries a UUID key; retries and races never double-count
- **Rate limiting** on the write endpoint (30/min); zod validation on every input; clean error
  envelopes (400/405/429/500)
- **Fully offline-capable**: progress cached locally, failed saves queue in localStorage and
  auto-flush on the next visit (permanently invalid ones dropped), explicit "Continue offline"
  mode, server value reconciles the optimistic one when it lands

### App shell

- Explicit **loading / error / ready** states with Retry, top-level ErrorBoundary (a crash becomes
  a recoverable panel)
- **Responsive layout**: canvas + side panel, stacks on screens < 900 px; dark theme;
  keyboard-operable controls

### For the maintainer

- `docker compose up -d` local Postgres + committed migrations + seed data
- **108 automated tests** (~99 % coverage) incl. mathematical-consistency property tests, plus 5
  Playwright E2E flows
- GitHub Actions CI (lint → typecheck → test → build → e2e) + Vercel deploy workflow; security
  headers (CSP, HSTS, …); `npm audit` at 0 vulnerabilities

## Documentation

| Doc                                          | Contents                                                               |
| -------------------------------------------- | ---------------------------------------------------------------------- |
| [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) | Full file structure + roadmap with implementation status               |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Pattern, component interactions, data flow, security, error philosophy |
| [docs/TECH-NOTES.md](docs/TECH-NOTES.md)     | CI/CD, testing strategy, deployment, env management, pitfalls          |

## Quickstart

```bash
nvm use                 # Node 22 (.nvmrc)
npm install             # also runs `prisma generate` (postinstall)
npm run dev             # frontend only — practice loop runs in offline mode
```

Full stack locally (functions + database):

```bash
cp .env.example .env         # defaults match docker-compose credentials
docker compose up -d         # local Postgres 16
npx prisma migrate deploy    # apply committed migrations
npm run db:seed              # optional demo data
npm run dev:full             # `vercel dev` — SPA + /api on one port
```

## Scripts

| Command                                      | What it does                                                 |
| -------------------------------------------- | ------------------------------------------------------------ |
| `npm run dev` / `dev:full`                   | Vite dev server / full stack via `vercel dev`                |
| `npm run build`                              | Typecheck all projects + production build                    |
| `npm run test` / `test:coverage`             | Vitest unit + API handler tests (100 tests)                  |
| `npm run e2e`                                | Playwright smoke + flow tests (build first: `npm run build`) |
| `npm run lint` / `format`                    | ESLint / Prettier                                            |
| `npm run db:migrate` / `db:push` / `db:seed` | Prisma workflows                                             |

## API

All responses are JSON; errors use the envelope `{ "error": { "code": "...", "message": "..." } }`
with statuses 400 (validation), 405 (method), 429 (rate limited), 500 (internal).

### `GET /api/health` — liveness probe

```bash
curl http://localhost:3000/api/health
# → 200 {"ok":true,"service":"linear-algebra-visualizer-api","timestamp":"2026-07-11T20:00:00.000Z"}
```

### `GET /api/progress?deviceId=…` — per-topic mastery for an (anonymous) device

```bash
curl "http://localhost:3000/api/progress?deviceId=demo-device-00000000"
# → 200 {"progress":[{"topic":"vectors","mastery":0.85,"attempts":12}]}
# Unknown devices get {"progress":[]} — the client merges local defaults.
```

### `POST /api/attempts` — record a graded attempt (rate limited)

The server recomputes mastery in a transaction and returns the authoritative row. `attemptId` is a
client-generated UUID idempotency key: replaying the same attempt (offline-queue retry) is a no-op.

```bash
curl -X POST http://localhost:3000/api/attempts \
  -H "Content-Type: application/json" \
  -d '{
    "attemptId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "deviceId": "demo-device-00000000",
    "topic": "eigenvalues",
    "difficulty": 3,
    "seed": 42,
    "correct": true,
    "durationMs": 8500
  }'
# → 201 {"ok":true,"progress":{"topic":"eigenvalues","mastery":0.15,"attempts":1}}
```

## Troubleshooting

- **"WebGL is not available" panel** — enable hardware acceleration in the browser, or update GPU
  drivers. Headless/CI environments need SwiftShader (`--use-angle=swiftshader`, already configured
  for Playwright).
- **Math shows as raw `\(\TeX\)` text** — the MathJax CDN (cdn.jsdelivr.net) is blocked or offline.
  The app stays fully functional; notation degrades to source text by design.
- **Practice panel says "Could not load progress"** — you're running `npm run dev` (no `/api`).
  Use **Continue offline** (progress persists locally) or run the full stack: `docker compose up -d`
  → `npx prisma migrate deploy` → `npm run dev:full`.
- **`docker compose up` fails / port 5432 busy** — another Postgres is running; stop it or change
  the port mapping in `docker-compose.yml` _and_ the URLs in `.env`.
- **`prisma migrate deploy` says "Environment variable not found: DATABASE_URL"** — copy
  `.env.example` to `.env` first (the Prisma CLI reads `.env`, but only from the project root).
- **`npm run dev:full` asks for a Vercel login** — `vercel dev` needs a (free) Vercel account and a
  linked project the first time. Frontend-only `npm run dev` needs neither.
- **`npm run e2e` fails with "vite preview" errors** — build first (`npm run build`); preview serves
  the last production build.
- **Line-ending churn on Windows** — the repo normalizes to LF via `.gitattributes`; run
  `git add --renormalize .` once if your clone predates it.

## Verification status

Everything below ran green in this workspace:

- `npm run format:check` / `npm run lint` — clean (one benign fast-refresh warning)
- `npm run typecheck` — clean across app / node / api projects
- `npm run test:coverage` — **108/108 tests**, ~99 % statement coverage on core/services/state/api
- `npm run build` — ~202 KB gz initial JS (three.js chunk-split), under the 300 KB budget
- `npm run e2e` — 5/5 Playwright tests incl. WebGL rendering and the full offline exercise flow
- `npm audit` — **0 vulnerabilities** (transitive dev-tool pins via scoped `overrides`)
- Live DB: `docker compose up -d` → `prisma migrate deploy` (2 migrations) → seed → transactional
  write with server-computed mastery → **idempotent replay verified** (same `attemptId` twice ⇒
  counted once; concurrent distinct attempts counted correctly)

Remaining setup that needs account access (see PROJECT-PLAN Phase 1): create the Vercel project
(secrets for `deploy.yml` or Git integration) and provision the production Postgres.
