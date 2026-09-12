# Linear Algebra Visualizer — Technical Notes

Actionable engineering guidance. Companion to [ARCHITECTURE.md](./ARCHITECTURE.md).

---

## 3.1 CI/CD Pipeline Design

```mermaid
flowchart LR
    PR["PR / push"] --> L["Lint<br/>eslint + prettier"]
    L --> T["Typecheck<br/>tsc -b (app+node+api)"]
    T --> U["Unit tests<br/>vitest + coverage"]
    U --> B["Build<br/>vite build"]
    B --> E["E2E<br/>Playwright vs vite preview"]
    E --> PV["Vercel preview deploy<br/>(per PR)"]
    PV -->|merge to master| PROD["Vercel production deploy"]
```

Implemented in `.github/workflows/`:

- **`ci.yml`** — runs on every PR and push to `master`. Three jobs: `verify` (lint → typecheck →
  unit tests with coverage artifact), `build` (Vite production build, `dist/` artifact), `e2e`
  (Playwright against `vite preview`, report uploaded on failure). Fail fast and sequentially inside
  each job so the first broken stage is obvious. `concurrency` cancels superseded runs.
- **`deploy.yml`** — production deploy on push to `master` via Vercel CLI
  (`vercel pull → build → deploy --prebuilt`). **Simpler alternative:** enable Vercel's GitHub
  integration (automatic preview per PR + production on merge) and delete this workflow — keep it
  only if you need deploys gated by CI or build-once-deploy-same-artifact semantics.

Notes:

- `npm ci` and `actions/setup-node` caching **require a committed `package-lock.json`** — commit it
  after the first `npm install` (tracked in PROJECT-PLAN Phase 1).
- `prisma generate` runs via `postinstall`, so typechecking `api/` works in CI without a database.
- Stage-relevant env: none needed for CI (no DB in unit/E2E path — the SPA degrades to offline mode).

## 3.2 Testing Strategy

**Pyramid: many pure unit tests, few component tests, a handful of E2E.**

| Level         | Tooling                                                 | Target                                                                 | What to test                                                                                                                                                                                                                         |
| ------------- | ------------------------------------------------------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Unit (pure)   | Vitest                                                  | `src/core/**` **≥ 90 %** lines                                         | eigen solver vs hand-computed cases; generator determinism (same seed ⇒ same exercise); _mathematical consistency properties_ (e.g. `eigen(generated.matrix)` equals the generator's embedded answer); grading tolerance/colinearity |
| Unit (UI)     | Vitest + React Testing Library + jsdom                  | key components                                                         | `ExercisePanel` state machine: loading → ready, error → retry/offline; grading round trip. Mock `services/*`, never mock `core/*`                                                                                                    |
| API handlers  | Vitest, node env, mocked Prisma                         | `tests/unit/api/**`                                                    | validation → 400, method guard → 405, rate limit → 429, error envelopes, server-side mastery math, enum mapping. **Tests live outside `api/`** — Vercel would route `api/*.test.ts` as functions                                     |
| Live DB       | `docker compose up -d` + `prisma migrate deploy` + seed | migration & repo                                                       | committed migration applies cleanly; transactional `recordAttempt` verified against real Postgres                                                                                                                                    |
| E2E           | Playwright (Chromium)                                   | critical paths                                                         | app boots, canvas mounts, presets update eigen/det readouts, full offline exercise flow, vector add/remove                                                                                                                           |
| Coverage gate | `@vitest/coverage-v8`                                   | ≥ 80 % statements on covered dirs (`core`, `services`, `state`, `api`) | enforced via `vite.config.ts` thresholds (currently ~99 %)                                                                                                                                                                           |

Principles:

- **Don't test Three.js internals or pixels.** Test _our_ scene state (matrix applied to the group,
  arrow visibility) and leave rasterization to Three.js. Visual-regression screenshots are a
  Phase-3 optional layer, not the foundation.
- **Property-style tests over examples** for generators: loop 50 seeds and assert invariants
  (integer entries, eigenvalues distinct, expected answer consistent). Consider `fast-check` later.
- Floating point: assert with tolerances (`1e-6` internal, `1e-3` for user-facing grading), never
  `toBe` on computed floats.

## 3.3 Deployment Strategy

**Platform: Vercel — static SPA + serverless functions. No containers.**

Docker is deliberately **not used for the application**: Vercel builds from source and provides the
runtime for both the static bundle and `api/` functions; a Dockerfile would add a parallel artifact
nobody deploys. (If self-hosting ever becomes a requirement, the shape is: `nginx` for `dist/` + a
small Node server wrapping `api/` handlers — revisit then.) The repo's `docker-compose.yml` exists
for one thing only: the **local development Postgres** (`docker compose up -d`, then
`npx prisma migrate deploy` + `npm run db:seed`).

- **Environments:** production (`master`) · preview (every PR gets an isolated URL) · local dev.
- **Database:** Neon or Vercel Postgres. Use the **pooled** connection string in `DATABASE_URL`
  (serverless!) and the direct one in `DIRECT_DATABASE_URL` for migrations.
- **Migrations:** `prisma migrate dev` locally (committed under `prisma/migrations/`);
  `prisma migrate deploy` against production as a release step _before_ promoting the deploy that
  depends on the new schema. Keep migrations backwards-compatible one release back (expand →
  migrate → contract) so previews and prod can share a DB branch safely; Neon DB branching per
  preview is the cleaner upgrade.
- **Rollback:** Vercel keeps every deployment immutable — "Promote previous deployment" is the
  rollback button. DB rollbacks are forward-fixes (new migration), never `migrate down` in prod.

## 3.4 Environment Management

| Concern       | development                                                       | preview (PR)                     | production                    |
| ------------- | ----------------------------------------------------------------- | -------------------------------- | ----------------------------- |
| Frontend env  | `.env` copied from `.env.example`                                 | Vercel env (Preview scope)       | Vercel env (Production scope) |
| Functions env | `vercel dev` (or `vercel env pull`)                               | Vercel env (Preview)             | Vercel env (Production)       |
| Database      | `docker compose up -d` Postgres (matches `.env.example` defaults) | shared preview DB or Neon branch | production DB                 |

Rules:

- `.env*` git-ignored; **`.env.example` is the contract** — every new variable lands there first,
  documented, valueless or with safe local defaults.
- Only `VITE_`-prefixed vars are bundled client-side ⇒ **secrets must never be `VITE_`-prefixed.**
- Local full-stack dev: `vercel env pull .env.local` then `npm run dev:full` (`vercel dev`).
  Frontend-only dev: plain `npm run dev` — the app degrades to offline mode without `/api`.

`.env.example` (kept in repo root; shown here for reference):

```bash
# ── Server-side (Vercel Functions only — NEVER exposed to the browser) ─────
# Pooled connection string (Neon pooler / pgbouncer) — required in serverless.
DATABASE_URL="postgresql://postgres:postgres@localhost:5432/linalg_viz?schema=public"
# Direct (non-pooled) connection used only by `prisma migrate`.
DIRECT_DATABASE_URL="postgresql://postgres:postgres@localhost:5432/linalg_viz?schema=public"

# ── Client-side (bundled into JS — MUST start with VITE_, never secrets) ───
VITE_API_BASE_URL="/api"
VITE_ENABLE_ANALYTICS="false"
```

## 3.5 Version Control Workflow

**Trunk-based / GitHub Flow:** short-lived branches off `master`, PR + green CI + preview URL,
squash-merge, deploy on merge.

Rationale: a small team shipping a web app with per-PR preview deployments gets nothing from
Gitflow's `develop`/release/hotfix ceremony — the preview _is_ the staging environment and `master`
is always releasable. Conventions:

- Branch names: `feat/eigen-overlay`, `fix/grading-tolerance`, `docs/tech-notes`.
- Squash-merge keeps `master` linear; PR title becomes the commit subject (Conventional Commits
  style `feat: …`, `fix: …` recommended — enables changelog automation later).
- Never force-push `master`; protect it (require CI + 1 review where team size allows).
- Feature flags (a constant in code is fine at this scale) over long-lived branches.

## 3.6 Common Pitfalls (this specific stack)

1. **React re-render vs 60 fps loop.** If the animation drives React state per frame, the app dies
   at scale. Keep the loop inside `SceneManager`; React communicates via the store only
   (`transformVersion` signal). Never `setState` inside `requestAnimationFrame` here.
2. **Three.js resource leaks.** Geometries/materials/renderer are GPU resources — `dispose()` all of
   them on unmount (React 18/19 StrictMode double-mounts effects in dev and will surface leaks
   fast). `SceneManager.dispose()` is the single choke point; keep it exhaustive.
3. **Entrywise matrix lerp passes through singularity.** Interpolating `I → rotation(π)` entrywise
   collapses the plane mid-animation (det → 0 visual "implosion"). Solved here with
   polar-decomposition interpolation (`interpolateTransform`): rotate the rotation part along the
   shortest arc, lerp the stretch part. Entrywise lerp remains only as the fallback for
   orientation-reversing endpoints, where the momentary flatten is the honest picture of a
   reflection. Unit tests pin both behaviors.
4. **MathJax typeset races.** Typesetting the whole document on every React commit causes flicker
   and stale nodes. Configure `startup.typeset: false`, typeset only changed subtrees
   (`useMathJaxTypeset`), call `typesetClear` on cleanup, and no-op when `window.MathJax` is absent
   (jsdom, CDN blocked). CDN outage must degrade to raw TeX text, not a crash.
5. **Headless CI has no GPU.** WebGL in Playwright needs SwiftShader
   (`--use-angle=swiftshader` — already in `playwright.config.ts`); assert on DOM/app state, not on
   rendered pixels, or E2E becomes flaky.
6. **Prisma on serverless.** New client per invocation exhausts Postgres connections. Use the
   `globalThis` singleton (`api/_lib/db.ts`) **and** a pooled connection string. Cold starts are
   ~100–300 ms extra with Prisma — fine for fire-and-forget writes; revisit with Accelerate/driver
   adapters if p95 matters later.
7. **`VITE_` prefix leaks.** Anything `VITE_*` ships to every browser. Audit new env vars in PR
   review; secrets live only in function scope.
8. **Grading floats.** `0.1 + 0.2 !== 0.3`; users type `0.33` for `1/3`. Grade with tolerance and
   compare eigenvectors by **colinearity** (|cos| ≈ 1), never componentwise equality — `(−1,−1)` is
   the same eigenvector as `(1,1)`.
9. **Windows line endings & tooling.** `.gitattributes` normalizes to LF (Prettier `endOfLine`
   consistent) — prevents noisy diffs and shellcheck-style CI failures on mixed teams.
10. **`@types/three` drift.** Three.js releases monthly (`0.17x`); keep `three` and `@types/three`
    minor-locked together, upgrade deliberately (renderer/color-management behavior changes between
    minors are release-noted, not semver-signaled).
11. **jsdom has no WebGL/ResizeObserver.** Never mount `VectorCanvas` in unit tests; the setup file
    polyfills `ResizeObserver` and the rendering layer stays out of jsdom entirely (test it E2E).
12. **Vercel SPA rewrites swallowing `/api`.** The catch-all rewrite to `index.html` must exclude
    `/api/*` (see `vercel.json`), or every API call returns HTML with status 200 — a classically
    confusing bug.
13. **Retry queues are at-least-once.** A response lost after the server committed means the client
    retries a write that already happened. Two rules keep this sane: every attempt carries a
    client-generated `attemptId` the server dedupes on (unique column + replay check + P2002 race
    handling), and only _retryable_ failures re-queue — a non-429 4xx is permanently invalid and
    must be dropped or it jams the queue forever. Also make the flusher non-reentrant: React
    StrictMode double-mounts effects and would otherwise double-POST the whole queue.
14. **Transitive dev-dependency audits.** `@vercel/node` pins old `undici`/`ajv`/`minimatch`
    versions; `npm audit fix --force` "fixes" them by downgrading `@vercel/node` itself — worse.
    Use `package.json#overrides` with same-major patched versions instead (they only affect
    `vercel dev` tooling, never the shipped bundle or the deployed runtime).
