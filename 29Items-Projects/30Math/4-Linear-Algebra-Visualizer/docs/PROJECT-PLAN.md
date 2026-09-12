# Linear Algebra Visualizer — Project Plan

**Stack:** TypeScript · React 19 · Three.js (WebGL) · MathJax 3 · Zustand · Vite · Vercel Functions · Prisma/PostgreSQL
**Deployment:** Vercel (static SPA + serverless functions) · GitHub Actions CI
**Purpose:** Educational tool for 2D linear algebra — vector spaces, linear transformations, determinants,
eigenvalues/eigenvectors — with smooth WebGL animations, MathJax notation, and auto-generated exercises
that adapt to the learner's mastery level.

Companion documents: [ARCHITECTURE.md](./ARCHITECTURE.md) (system design) and
[TECH-NOTES.md](./TECH-NOTES.md) (CI/CD, testing, deployment, pitfalls).

---

## 1.1 Project File Structure

The project is a **client-centric SPA with a thin serverless backend**. There is no separate backend
service to deploy: Vercel builds the Vite SPA from `/src` and each file in `/api` becomes a serverless
function. Database migrations live under `/prisma/migrations` (committed); `docker-compose.yml`
provides the local development Postgres only — the app itself is not containerized.

```text
4-Linear-Algebra-Visualizer/
│
├── docs/                              # Architecture & engineering documentation
│   ├── PROJECT-PLAN.md                #   This file — structure + implementation roadmap
│   ├── ARCHITECTURE.md                #   Patterns, component interactions, data flow, security
│   └── TECH-NOTES.md                  #   CI/CD, testing, deployment, env management, pitfalls
│
├── .github/                           # ── CI/CD (GitHub Actions) ──
│   ├── workflows/
│   │   ├── ci.yml                     #   PR/push: lint → typecheck → unit tests → build → e2e
│   │   └── deploy.yml                 #   Production deploy to Vercel on push to master
│   └── PULL_REQUEST_TEMPLATE.md       #   PR checklist (tests, docs, screenshots)
│
├── api/                               # ── Backend: Vercel serverless functions (1 file = 1 route) ──
│   ├── _lib/                          #   Shared server code ("_" prefix ⇒ not routed)
│   │   ├── db.ts                      #     Prisma client singleton (serverless-safe)
│   │   ├── http.ts                    #     JSON/error envelope + method-guard helpers
│   │   ├── schemas.ts                 #     zod input schemas (validation at the trust boundary)
│   │   ├── mastery.ts                 #     Server-side mastery rule (mirrors core/difficulty)
│   │   ├── rateLimit.ts               #     Best-effort sliding-window limiter
│   │   └── progressRepo.ts            #     Service/repository layer (transactional writes)
│   ├── health.ts                      #   GET  /api/health    — liveness probe
│   ├── progress.ts                    #   GET  /api/progress  — per-topic mastery for a device
│   └── attempts.ts                    #   POST /api/attempts  — record attempt, recompute mastery
│
├── prisma/                            # ── Database ──
│   ├── schema.prisma                  #   Models: User, ExerciseAttempt, TopicProgress
│   ├── migrations/0001_init/          #   Committed initial migration (prisma migrate deploy)
│   └── seed.ts                        #   Local dev seed data
│
├── public/favicon.svg                 # Static assets served as-is
│
├── src/                               # ── Frontend source ──
│   ├── main.tsx                       #   Entry point (React root + ErrorBoundary)
│   ├── App.tsx                        #   Layout: canvas pane + controls/readouts/practice pane
│   ├── vite-env.d.ts                  #   Vite + typed import.meta.env
│   │
│   ├── core/                          #   PURE domain logic — zero React/Three.js imports
│   │   ├── math/
│   │   │   ├── vector2.ts             #     Immutable Vec2 + operations (incl. colinearity)
│   │   │   ├── matrix2.ts             #     Mat2 (2×2) + compose/invert/rotation/shear presets
│   │   │   ├── eigen.ts               #     2×2 eigen solver (distinct/repeated/complex)
│   │   │   ├── interpolation.ts       #     Easing + polar-decomposition matrix interpolation
│   │   │   ├── index.ts               #     Barrel export
│   │   │   └── *.test.ts              #     matrix2 / eigen / interpolation unit tests
│   │   ├── format/
│   │   │   ├── tex.ts                 #     Numbers/vectors/matrices/eigen/det → LaTeX
│   │   │   └── tex.test.ts
│   │   └── exercises/
│   │       ├── types.ts               #     Topic, Difficulty, Exercise, Answer, TopicProgress
│   │       ├── rng.ts                 #     Seeded PRNG (mulberry32) — reproducible exercises
│   │       ├── generator.ts           #     "Answer-first" generators, all 5 topics + eigenvector variant
│   │       ├── difficulty.ts          #     Mastery model + curriculum progression
│   │       ├── grading.ts             #     Tolerant numeric/vector/colinearity grading
│   │       ├── index.ts               #     Barrel export
│   │       └── *.test.ts              #     generator / grading / difficulty / rng tests
│   │
│   ├── rendering/                     #   Imperative Three.js layer (isolated from React)
│   │   ├── SceneManager.ts            #     Renderer/camera/loop, drag interaction, context loss
│   │   ├── TransformAnimator.ts       #     Eased polar-decomposition matrix tween
│   │   └── primitives/
│   │       ├── GridPlane.ts           #     Static + transformed grid, axes
│   │       └── VectorArrow.ts         #     Rigid thick-quad arrow, zero per-frame allocation
│   │
│   ├── state/                         #   Zustand stores — the React ⇄ WebGL bridge
│   │   ├── visualizerStore.ts         #     Target matrix, toggles, speed, user vectors
│   │   ├── progressStore.ts           #     Persisted mastery + streak, server reconciliation
│   │   └── *.test.ts
│   │
│   ├── services/                      #   HTTP boundary to /api
│   │   ├── apiClient.ts               #     fetch wrapper: base URL, timeout, ApiError
│   │   ├── progressService.ts         #     zod-validated calls + offline retry queue
│   │   └── *.test.ts
│   │
│   ├── hooks/useMathJax.ts            #   Typeset-after-commit hook (guards SSR/jsdom)
│   │
│   ├── components/
│   │   ├── VectorCanvas/VectorCanvas.tsx        # Mounts SceneManager; WebGL-support fallback
│   │   ├── MathNotation/MathNotation.tsx        # LaTeX span/block rendered by MathJax
│   │   ├── ExercisePanel/ExercisePanel.tsx      # Full practice loop + mastery bars + streak
│   │   ├── ExercisePanel/ExercisePanel.test.tsx # State machine + grading round-trip tests
│   │   ├── controls/MatrixInput.tsx             # 2×2 entry grid + presets (rotate/shear/scale/reflect)
│   │   ├── controls/VectorControls.tsx          # Add/remove draggable user vectors
│   │   ├── controls/AnimationControls.tsx       # Replay/reset, speed, layer toggles
│   │   └── ErrorBoundary.tsx                    # Top-level render-error containment
│   │
│   ├── styles/global.css              #   Design tokens + app layout (responsive)
│   ├── test/setup.ts                  #   Vitest setup (jest-dom, RTL cleanup, ResizeObserver)
│   └── types/mathjax.d.ts             #   window.MathJax global typing
│
├── tests/
│   ├── unit/api/                      #   API handler + repo tests (node env, mocked Prisma)
│   │   ├── mocks.ts                   #     req/res doubles
│   │   └── *.test.ts                  #     attempts / progress / progressRepo / rateLimit / health
│   └── e2e/smoke.spec.ts              #   Playwright: boot, WebGL, presets, offline exercise flow
│
├── docker-compose.yml                 # ── Local dev database (Postgres 16) — dev only ──
├── .env.example                       # ── Tools configuration ──
├── .gitattributes                     #   LF normalization (Windows-friendly repo)
├── .gitignore
├── .nvmrc                             #   Node 22
├── .prettierrc.json                   #   Formatting rules
├── eslint.config.js                   #   ESLint 9 flat config (TS + React hooks)
├── index.html                         #   SPA host page; MathJax CDN config + loader
├── package.json / package-lock.json   #   Scripts + pinned dependency tree
├── playwright.config.ts               #   E2E config (SwiftShader for headless WebGL)
├── tsconfig*.json                     #   Solution → app / node / api projects
├── vercel.json                        #   SPA rewrite (excluding /api), asset caching
├── vite.config.ts                     #   Vite + Vitest config, "@" alias, three.js chunking
├── README.md
└── claude-fable-5.txt                 #   Generator marker file
```

**Why this shape**

- `src/core` is a **dependency-free domain kernel**: every eigen computation, exercise generator and
  grading rule is a pure function. This is where correctness matters most and testing is cheapest
  (it sits at ~100 % line coverage).
- `src/rendering` keeps Three.js **out of React's render cycle** — React never re-renders at 60 fps;
  the scene subscribes to the store instead (see ARCHITECTURE.md §2.2).
- `api/` mirrors Vercel's file-system routing, so there is no router framework to maintain. Handler
  tests live under `tests/unit/api` (NOT inside `api/` — Vercel would route `*.test.ts` files).
- Frontend (`src`) and functions (`api`) are deliberately decoupled (the topic enum and the mastery
  rule are mirrored in `api/_lib`, a dozen lines total) — extract a `shared/` package only if drift
  actually hurts (rule of three).

---

## 1.2 Implementation TODO List

Status legend: `[x]` implemented and verified in this repo · `[ ]` open (reason noted).

### Phase 1 — Foundation (high priority) — **complete**

- [x] Repository scaffold: directory layout, TypeScript project references, Vite, ESLint/Prettier
- [x] Pure math core: `Vec2`, `Mat2`, eigen solver (all three regimes), interpolation — unit-tested
- [x] Seeded RNG + exercise/answer type model
- [x] Three.js scene: orthographic camera, static/transformed grids, rigid basis arrows,
      unit square with det-sign coloring, eased matrix tween, resize handling, exhaustive disposal
- [x] Zustand store wiring (matrix, transform version, toggles, speed, user vectors)
- [x] MathJax integration (CDN config + typeset hook + `MathNotation`, degrades to raw TeX)
- [x] CI workflow (lint → typecheck → unit → build → e2e) and Vercel deploy workflow
- [x] `npm install` run; `package-lock.json` generated (include it in the commit)
- [x] Local database: `docker-compose.yml` + committed initial migration + seed — verified live
- [ ] Create the Vercel project; set `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` repo
      secrets (or use Vercel Git integration and delete `deploy.yml`) — _needs account access_
- [ ] Provision production PostgreSQL (Neon/Vercel Postgres) and set env vars — _needs account access_
- [ ] Eyeball 60 fps on mid-range hardware — _manual check; design follows the perf budget and
      e2e verifies rendering, but a human should watch it once_

### Phase 2 — Core features (medium priority) — **complete except external services**

- [x] All five topic generators with difficulty-scaled ranges; eigen topic mixes in
      "find an eigenvector" questions from difficulty 4
- [x] Exercise loop UX: session streak counter + per-topic mastery bars; fraction input ("1/3")
- [x] Eigen overlays: invariant-direction arrows (scaled by λ); complex case explained in the
      readout as rotation angle + scale
- [x] Determinant story: live `det(A)` readout with area factor + orientation; unit square turns
      red when orientation flips
- [x] Draggable user vectors on the canvas (pointer picking + inverse-mapped drag), synced to store
- [x] Persistence hardening: offline retry queue in localStorage (retryable failures only,
      non-reentrant flush), flushed on boot; server/local merge; **idempotent writes** via
      client-generated `attemptId` (unique column, replay check, race handling)
- [x] Server-side mastery recomputation in a Prisma transaction (client mastery never trusted)
- [x] Security headers on all responses (CSP, HSTS, nosniff, frame denial, referrer/permissions policy)
- [x] Dependency audit clean (`npm audit`: 0 vulnerabilities; transitive dev-tool pins via overrides)
- [x] Rate limiting on the write endpoint (best-effort in-memory sliding window; platform
      WAF/Redis is the distributed upgrade, see ARCHITECTURE §2.5)
- [x] E2E suite: boot, WebGL canvas, preset → readout assertions, full offline exercise flow,
      vector add/remove
- [ ] "Show solution" step-by-step walkthrough in TeX — _content work, next feature up_
- [ ] Error telemetry (Sentry) on SPA + functions — _needs account_

### Phase 3 — Polish & optimization (lower priority) — **partially complete**

- [x] Rotation-aware matrix interpolation via polar decomposition (entrywise lerp kept only as
      the honest fallback for orientation-reversing endpoints)
- [x] Thick anti-aliased arrows with rigid (non-shearing) heads — quad shafts, world-space tips
- [x] Accessibility baseline: `prefers-reduced-motion` (snap instead of tween), keyboard-driven
      matrix/answer entry with Enter-to-submit, aria roles/labels on canvas, readouts, progress bars
- [ ] MathJax a11y extensions (assistive MathML) — _evaluate bundle/CDN options first_
- [ ] Lighthouse CI perf budget check (initial JS is ~201 KB gz, under the 300 KB budget;
      three.js is chunk-split — enforcement in CI still to add)
- [ ] Visual regression tests (Playwright screenshots of canonical transforms)
- [ ] Optional accounts (OAuth) migrating anonymous device history — _product decision_
- [ ] Content expansion: composition of transforms, change of basis, 3D upgrade path
- [ ] i18n scaffold (UI strings only; math stays universal)

### Milestone acceptance

| Milestone    | Definition of done                                                                                                           | Status                                              |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| M1 (Phase 1) | Matrix input animates grid smoothly; eigen readout matches hand computation; CI pipeline defined; local DB migrated          | ✅ verified locally (Vercel project pending)        |
| M2 (Phase 2) | Adaptive practice loop works online & offline; progress survives reload; attempts visible in DB with server-computed mastery | ✅ verified against live Postgres                   |
| M3 (Phase 3) | Lighthouse ≥ 90 perf/a11y; visual-regression suite; error telemetry                                                          | ◐ perf budget met; telemetry/visual-regression open |
