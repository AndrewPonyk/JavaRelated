# Project Plan — Customer Portal SPA (React SPA CI/CD)

|                  |                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **Status**       | Implemented & locally verified — Phases 1–2 code complete; cloud onboarding items pending (see §3)                              |
| **Last updated** | 2026-07-12                                                                                                                      |
| **Tech stack**   | React 18, TypeScript, Vite, Jest, Playwright, ESLint, Prettier                                                                  |
| **Delivery**     | GitHub Actions → S3 + CloudFront (PR previews) → DigitalOcean App Platform (staging / production)                               |
| **Related docs** | [ARCHITECTURE.md](./ARCHITECTURE.md) · [TECH-NOTES.md](./TECH-NOTES.md) · [ADR-001](./adr/ADR-001-hybrid-deployment-targets.md) |

---

## 1. Overview & Goals

The Customer Portal is a client-side rendered Single Page Application through which customers
sign in, review their account dashboard, and manage their settings. The application is delivered
as static, CDN-cached assets and talks to a separately owned REST API.

This repository's mission is twofold:

1. **Product**: a fast, accessible, well-tested portal (auth, dashboard, settings).
2. **Delivery machine**: a CI/CD pipeline where every pull request is linted, unit-tested,
   E2E-tested, built, performance-budgeted (Lighthouse CI), and deployed to a disposable
   S3 + CloudFront preview URL — and where `main` flows automatically to staging and, via
   approved releases, to production on DigitalOcean App Platform.

### Success criteria

- PR feedback loop (lint + unit + build) completes in **< 10 minutes**.
- Every PR gets a **live preview URL** posted as a PR comment; previews are torn down on close.
- **Lighthouse budgets** (performance ≥ 0.90, accessibility ≥ 0.95, LCP ≤ 2.5 s, TBT ≤ 300 ms,
  CLS ≤ 0.10) are enforced as merge gates, not aspirations.
- Unit coverage on `src/` ≥ **80 % lines / 70 % branches**; E2E covers the three critical
  journeys (login, dashboard load, settings save).
- Deploys are boring: staging on every `main` merge, production on approved releases,
  with post-deploy smoke tests and a documented rollback path.
- A/B experiment decisions are made from GA4 → BigQuery data via an offline **ML regression**
  pipeline, not gut feel (see ARCHITECTURE §2.3).

---

## 2. Project File Structure (Code + CI + Tools)

### 2.1 Full repository layout

```text
3-React-SPA-CICD/
├── .github/                            # ── CI/CD: GitHub Actions ──────────────────────────
│   ├── workflows/
│   │   ├── ci.yml                      # PR + main: lint → format → typecheck → unit → build → E2E → Lighthouse
│   │   ├── pr-preview.yml              # Deploys each PR to S3 + CloudFront under /pr-<n>/, comments the URL
│   │   ├── pr-preview-cleanup.yml      # Tears the preview down when the PR closes
│   │   ├── deploy-staging.yml          # main → DigitalOcean App Platform (staging) + smoke tests
│   │   ├── deploy-production.yml       # Release published → production (env-protected) + smoke tests
│   │   └── nightly.yml                 # Cron: full cross-browser E2E matrix, npm audit, Lighthouse trend
│   ├── actions/
│   │   └── setup-node-cache/action.yml # Composite action: Node from .nvmrc + npm cache + npm ci
│   ├── CODEOWNERS
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── dependabot.yml                  # Weekly npm + Actions updates, minor/patch grouped
│
├── docs/                               # ── Documentation ──────────────────────────────────
│   ├── PROJECT-PLAN.md                 # This file
│   ├── ARCHITECTURE.md                 # Patterns, diagrams, data flow, security
│   ├── TECH-NOTES.md                   # Pipeline design, testing, deployment, pitfalls
│   └── adr/
│       └── ADR-001-hybrid-deployment-targets.md
│
├── contracts/
│   └── openapi.yaml                    # API contract owned jointly with the backend team.
│                                       # The SPA's "schema source of truth" (see §2.2 below).
│
├── src/                                # ── Application source ─────────────────────────────
│   ├── main.tsx                        # Bootstrap: optional MSW, analytics init, ReactDOM root
│   ├── App.tsx                         # Providers + RouterProvider composition
│   ├── App.test.tsx                    # jsdom integration: login → dashboard → sign-out via real router
│   ├── vite-env.d.ts                   # Typed import.meta.env (VITE_* variables)
│   ├── app/                            # Application shell: cross-cutting wiring
│   │   ├── env.ts                      # Single place that reads import.meta.env (Jest-mockable)
│   │   ├── env.types.ts                # AppEnv interface shared by env.ts and the test mock
│   │   ├── router.tsx                  # Route table, lazy-loaded pages, BASE_URL-aware basename
│   │   └── providers.tsx               # ErrorBoundary + AuthProvider (+ future QueryClient/Theme)
│   ├── api/                            # Transport layer
│   │   ├── httpClient.ts               # fetch wrapper: auth header, timeouts, ApiError, zod parsing,
│   │   │                               #   single-flight silent refresh + one replay on 401
│   │   ├── httpClient.test.ts
│   │   └── types.ts                    # Shared API schemas/types (zod)
│   ├── features/                       # Feature slices (screaming architecture)
│   │   ├── auth/
│   │   │   ├── AuthProvider.tsx        # Session state machine: unknown → authenticated/anonymous
│   │   │   ├── AuthProvider.test.tsx
│   │   │   ├── useAuth.ts
│   │   │   ├── auth.api.ts             # login/logout/me + zod schemas
│   │   │   ├── LoginPage.tsx           # Validated form, error states, analytics events
│   │   │   ├── LoginPage.test.tsx
│   │   │   ├── ProtectedRoute.tsx
│   │   │   └── ProtectedRoute.test.tsx
│   │   ├── dashboard/
│   │   │   ├── DashboardPage.tsx       # Cards + trend + activity + announcements + A/B variants
│   │   │   ├── DashboardPage.test.tsx
│   │   │   ├── dashboard.api.ts        # Summary/activity zod schemas (tracks openapi.yaml)
│   │   │   ├── useDashboardData.ts
│   │   │   └── components/
│   │   │       ├── AccountSummaryCard.tsx
│   │   │       ├── ActivityFeed.tsx          # GET /v1/activity, signed amounts
│   │   │       ├── BalanceTrendCard.tsx      # Dependency-free SVG sparkline
│   │   │       ├── BalanceTrendCard.test.tsx
│   │   │       └── OnboardingChecklist.tsx   # 'onboarding-checklist' treatment UI
│   │   └── settings/
│   │       ├── SettingsPage.tsx        # Diff-based PATCH, saved/error states
│   │       ├── SettingsPage.test.tsx
│   │       └── settings.api.ts
│   ├── components/                     # Shared presentational/utility components
│   │   ├── ExampleComponent.tsx        # Reference pattern: fetch + loading/error/empty states
│   │   ├── ExampleComponent.test.tsx
│   │   ├── ErrorBoundary.tsx           # Class boundary + router errorElement fallback
│   │   ├── ErrorBoundary.test.tsx
│   │   ├── ConsentBanner.tsx           # Analytics consent (gates gtag.js injection)
│   │   ├── ConsentBanner.test.tsx
│   │   ├── LoadingSpinner.tsx
│   │   └── layout/
│   │       └── AppLayout.tsx           # Header/nav/footer shell, Suspense, page-view tracking
│   ├── hooks/
│   │   └── useFetch.ts                 # Abortable data-fetching hook with status machine
│   ├── lib/
│   │   ├── logger.ts                   # Leveled logger + pluggable error transport (registerErrorTransport)
│   │   ├── logger.test.ts
│   │   └── analytics/
│   │       ├── analytics.ts            # GA4 (gtag) wrapper: Consent Mode, opt-in script injection, no-op offline
│   │       ├── analytics.test.ts / analytics.disabled.test.ts
│   │       ├── events.ts               # Typed analytics event map (compile-time event contract)
│   │       ├── abTesting.ts            # Deterministic bucketing + exposure logging (feeds ML regression)
│   │       ├── abTesting.test.ts
│   │       ├── webVitals.ts            # CLS/LCP/INP/FCP/TTFB → GA4 (real-user monitoring)
│   │       └── webVitals.test.ts
│   ├── styles/
│   │   └── global.css
│   └── test/                           # Jest infrastructure (not shipped)
│       ├── polyfills.ts                # jsdom polyfills required by MSW v2
│       ├── setupTests.ts               # jest-dom + MSW server lifecycle
│       ├── env.mock.ts                 # Replaces src/app/env.ts under Jest; mutable via setTestEnv()
│       ├── fileMock.ts
│       └── testUtils.tsx               # render() with providers/router wired
│
├── mocks/                              # ── Mock backend (MSW) — the API stand-in ──────────
│   ├── handlers/
│   │   ├── index.ts
│   │   ├── auth.handlers.ts            # POST /v1/auth/login etc. with server-side zod validation
│   │   ├── dashboard.handlers.ts
│   │   └── settings.handlers.ts
│   ├── db/
│   │   └── store.ts                    # Seeded in-memory "database" + resetDb()
│   ├── browser.ts                      # setupWorker (dev + hermetic E2E builds)
│   └── server.ts                       # setupServer (Jest)
│
├── e2e/                                # ── Playwright E2E suite ───────────────────────────
│   ├── fixtures/auth.fixture.ts        # loggedInPage fixture (UI login against MSW build)
│   ├── smoke.spec.ts                   # @smoke — safe to run against live envs post-deploy
│   ├── auth.spec.ts
│   ├── dashboard.spec.ts
│   └── tsconfig.json
│
├── scripts/                            # ── Operational scripts ────────────────────────────
│   ├── deploy-pr-preview.sh            # S3 sync (split cache-control) + CloudFront invalidation
│   ├── teardown-pr-preview.sh
│   ├── check-env.mjs                   # Fails the build when required VITE_* vars are missing
│   └── prepare-husky.mjs               # Hook install guarded to standalone-git-root checkouts only
│
├── infra/                              # ── Infrastructure definitions ─────────────────────
│   ├── digitalocean/
│   │   ├── app.yaml                    # DO App Platform spec — production static site
│   │   └── app.staging.yaml            # DO App Platform spec — staging
│   └── aws/
│       └── pr-preview-stack.yml        # CloudFormation: private S3 + CloudFront OAC + SPA rewrite fn
│
├── public/                             # Static assets copied verbatim into dist/
│   ├── favicon.svg
│   └── robots.txt                      # (mockServiceWorker.js is generated here via `npm run msw:init`)
│
├── index.html                          # Vite entry HTML
├── package.json
├── vite.config.ts
├── tsconfig.json                       # App + mocks (strict, bundler resolution, @/* alias)
├── tsconfig.node.json                  # Tooling configs (vite/jest/playwright configs)
├── tsconfig.jest.json                  # CJS overrides for ts-jest
├── jest.config.ts
├── playwright.config.ts
├── eslint.config.js                    # ESLint 9 flat config
├── .prettierrc.json / .prettierignore
├── .lighthouserc.json                  # Lighthouse CI budgets & assertions
├── .env.example                        # Documented environment template (no secrets!)
├── .env.e2e                            # Checked-in config for hermetic E2E builds (MSW on)
├── .env.development                    # Committed dev defaults — `npm run dev` works out of the box
├── .editorconfig / .gitattributes / .gitignore / .nvmrc
├── .husky/pre-commit                   # lint-staged hook
├── Dockerfile / nginx.conf / nginx-security-headers.conf / .dockerignore
│                                       # Optional container path — CSP/HSTS/XFO verified live (TECH-NOTES §3.6 #12)
├── docker-compose.yml                  # `docker compose up` → self-contained stack on :8080 (verified)
├── Claude-Fable-5.txt                  # Authoring-model marker (intentionally empty)
└── README.md
```

### 2.2 Where the backend and database live

This repository is **frontend-only by design** — the Portal API is a separately owned service
(different repo, team, and release cadence). To keep the SPA honest without owning a backend:

- **`contracts/openapi.yaml`** is the API contract the client is built against. Frontend zod
  schemas (`src/api`, `src/features/*/**.api.ts`) and mock handlers must track it.
- **`mocks/`** is a full MSW mock of that contract with an in-memory seeded store
  (`mocks/db/store.ts`) — the closest thing this repo has to a "database schema". It powers
  local dev, Jest integration tests, and hermetic E2E builds.
- **Database migrations** belong to the API repository and are intentionally absent here.

If the product later consolidates into a monorepo, the target layout is
`apps/web` (this code), `apps/api`, `packages/shared-contracts`, `db/migrations` — the current
feature-sliced `src/` moves wholesale into `apps/web/src` with no internal restructuring.

### 2.3 CI/CD pipeline files

| Workflow                 | Trigger                    | Purpose / gates                                                                                                           |
| ------------------------ | -------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `ci.yml`                 | every PR, push to `main`   | lint, format check, typecheck, Jest (+coverage), production build, Playwright (chromium, hermetic), Lighthouse CI budgets |
| `pr-preview.yml`         | PR opened/updated          | build with `--base /pr-<n>/` → S3 sync → CloudFront invalidation → sticky PR comment with URL (AWS auth via OIDC)         |
| `pr-preview-cleanup.yml` | PR closed                  | delete `s3://…/pr-<n>/`, invalidate, update comment                                                                       |
| `deploy-staging.yml`     | push to `main`             | deploy DO App Platform staging spec → `@smoke` E2E against staging URL                                                    |
| `deploy-production.yml`  | release published / manual | environment-protected deploy of prod spec → `@smoke` E2E → notify                                                         |
| `nightly.yml`            | cron 03:00 UTC             | chromium+firefox+webkit E2E matrix, `npm audit`, Lighthouse trend run                                                     |

### 2.4 Tool configuration files

| Tool                | File(s)                                              | Notes                                                        |
| ------------------- | ---------------------------------------------------- | ------------------------------------------------------------ |
| Vite                | `vite.config.ts`, `.env.*`                           | `@/* → src/*` alias, vendor chunking, sourcemaps             |
| TypeScript          | `tsconfig*.json`                                     | strict; separate configs for app / tooling / Jest            |
| ESLint 9            | `eslint.config.js`                                   | flat config; TS + React + hooks + jsx-a11y + prettier compat |
| Prettier            | `.prettierrc.json`, `.prettierignore`                | formatting is a CI gate (`format:check`)                     |
| Jest                | `jest.config.ts`, `tsconfig.jest.json`, `src/test/*` | jsdom, MSW, coverage thresholds                              |
| Playwright          | `playwright.config.ts`, `e2e/`                       | hermetic webServer or `PLAYWRIGHT_BASE_URL`                  |
| Lighthouse CI       | `.lighthouserc.json`                                 | budgets as merge gates                                       |
| Husky + lint-staged | `.husky/pre-commit`, `package.json#lint-staged`      | fast local gate before CI                                    |
| MSW                 | `mocks/`, `public/mockServiceWorker.js` (generated)  | `npm run msw:init` after install                             |
| Docker (optional)   | `Dockerfile`, `nginx.conf`                           | only if header/CSP control is required (TECH-NOTES §3.3)     |

---

## 3. Implementation TODO List

### Phase 0 — Scaffold (this delivery) ✅

- [x] Repository layout, tooling configs, CI workflows, infra specs, docs, code stubs.

### Phase 1 — Foundation (HIGH priority)

Local items completed and verified 2026-07-12; the remaining items need GitHub/AWS/DO org
access and are the deployment-onboarding runbook.

- [x] Dependencies installed on Node 22 (`.nvmrc`); `package-lock.json` committed. _Note: lockfile currently generated on Windows — regenerate from the first Linux CI run (TECH-NOTES §3.6 #8)._
- [x] `npm run msw:init` generates `public/mockServiceWorker.js` (non-interactive `--save=false`); app verified against mocks.
- [x] Local quality loop green and verified: `lint`, `format:check`, `typecheck`, `test` (76 tests, 95 % lines / 82 % branches), `build`, `test:e2e` (10 Playwright tests, hermetic chromium), and Lighthouse assertions against the production `dist/`.
- [x] Husky guarded for safety: `scripts/prepare-husky.mjs` installs hooks only when the project is its own git root (prevents hijacking a parent repo's hooksPath; no-ops under `HUSKY=0`/Docker/CI).
- [x] `docker compose up` verified end-to-end: healthz, SPA fallback on deep links, immutable-asset + no-cache header contract.
- [ ] Push to GitHub; confirm `ci.yml` passes end-to-end on a hello-world PR (walking skeleton). _(requires org access)_
- [ ] Branch protection on `main`: required checks (quality, unit, build, e2e, lighthouse), linear history, no force push. _(requires org access)_
- [ ] Create GitHub Environments `pr-preview`, `staging`, `production` (production: required reviewers). _(requires org access)_
- [ ] AWS: deploy `infra/aws/pr-preview-stack.yml`; create the GitHub OIDC role; set `AWS_ROLE_ARN`, `PREVIEW_BUCKET`, `PREVIEW_CF_DISTRIBUTION_ID`, `PREVIEW_DOMAIN` repo variables/secrets. _(requires org access)_
- [ ] DigitalOcean: create staging + production apps from `infra/digitalocean/*.yaml` (update `repo:`), store `DIGITALOCEAN_ACCESS_TOKEN` secret. _(requires org access)_
- [ ] First automatic staging deploy from `main`; smoke suite green against staging. _(requires org access)_

### Phase 2 — Core features (MEDIUM priority)

- [x] Auth: silent refresh (`/v1/auth/refresh`) with single-flight + one-replay-on-401 implemented in `httpClient.ts`; covered by transport tests (concurrency, no-loop, failure path).
- [x] Dashboard: summary cards, balance-trend sparkline (`BalanceTrendCard`), recent-activity feed (`ActivityFeed`), announcements — all contract-typed and tested.
- [x] Settings: profile + notification preferences with diff-based PATCH (only changed fields), saved/error states; controlled form kept deliberately (react-hook-form deferred until field count grows).
- [x] E2E suite fleshed out: auth journeys (wrong creds, deep-link return, sign-out), dashboard widgets, settings save, SPA-fallback smoke — role/label selectors only.
- [x] Consent banner + GA Consent Mode: `gtag.js` only injected after opt-in; decision persisted; typed event dictionary in `src/lib/analytics/events.ts`.
- [x] Error reporting seam: `registerErrorTransport()` in `logger.ts` (throw-safe, warn/error only). _Wiring an actual Sentry DSN + sourcemap upload in `deploy-*.yml` is an ops task._
- [ ] GA4 property creation + events verified in DebugView from staging. _(requires GA org access)_
- [ ] PR previews live: sticky comment with URL; cleanup verified on close. _(requires AWS onboarding above)_
- [ ] Contract sync: CI job that diffs `contracts/openapi.yaml` against the backend's published spec (fail on drift). _(requires backend team's published spec)_

### Phase 3 — Polish & optimization (LOWER priority)

- [x] A/B frontend half: deterministic bucketing, render-time exposure logging, and both registry experiments have real UI (`dashboard-layout-v2` layout switch, `onboarding-checklist` treatment component).
- [ ] A/B warehouse half: GA4 → BigQuery export → nightly **ML regression job** (logistic/linear regression with covariate adjustment, e.g. CUPED) → experiment decision doc → winning variant promoted in the `EXPERIMENTS` registry. _(runs in the analytics warehouse, not this repo)_
- [ ] Remote experiment/config service layered over the `EXPERIMENTS` registry to change allocations without redeploying.
- [ ] Web-vitals RUM dashboards (GA4 exploration or BigQuery) with alert thresholds matching Lighthouse budgets.
- [ ] Visual regression (Playwright `toHaveScreenshot`) for dashboard + settings on chromium.
- [ ] Bundle analysis in CI (rollup-plugin-visualizer artifact) + per-route size budgets.
- [ ] Dependabot auto-merge for green minor/patch dev-dependencies.
- [ ] CSP hardening: move staging/prod to the container path (`Dockerfile` + `nginx.conf`) if DO static hosting header limits block a strict CSP.
- [ ] Accessibility: axe checks in Playwright, manual audit, focus management on route change.
- [ ] i18n scaffolding (react-i18next) if/when a second locale is confirmed.
- [ ] Runtime config endpoint (`/config.json`) if per-environment rebuilds become painful.
- [ ] Pin GitHub Actions by commit SHA; enable OpenSSF Scorecard / step-security hardening.

### Milestones

| Milestone                     | Contents                        | Exit criteria                                                         |
| ----------------------------- | ------------------------------- | --------------------------------------------------------------------- |
| **M1 — Walking skeleton**     | Phase 1                         | Local half ✅ (all gates verified); cloud half pending org onboarding |
| **M2 — Feature complete**     | Phase 2 auth/dashboard/settings | Code complete ✅; product sign-off on staging pending M1 cloud half   |
| **M3 — Production launch**    | prod workflow + runbook         | First approved release deployed; rollback rehearsed                   |
| **M4 — Experimentation live** | Phase 3 A/B + ML loop           | First experiment decided from the regression pipeline                 |

---

## 4. Risks & Mitigations

| Risk                                                                  | Impact | Mitigation                                                                                            |
| --------------------------------------------------------------------- | ------ | ----------------------------------------------------------------------------------------------------- |
| Two cloud targets (AWS previews + DO envs) doubles credential surface | Medium | OIDC (no long-lived AWS keys), scoped DO token, environments-gated secrets — see ADR-001              |
| Mock API drifts from the real backend                                 | High   | `contracts/openapi.yaml` as contract, zod response parsing fails fast, contract-diff CI job (Phase 2) |
| Lighthouse score flakiness blocks merges                              | Medium | median of 3 runs, numeric metric assertions with headroom, warn-first rollout                         |
| GA data loss (adblock/consent) skews A/B regression                   | Medium | treat GA as lossy, model non-exposure, consider first-party collection later                          |
| `VITE_*` values are public                                            | High   | never put secrets in `VITE_*`; `.env.example` documents this; secret scanning in CI (Phase 3)         |
| Windows dev vs Linux CI drift (line endings, native optional deps)    | Low    | `.gitattributes` forces LF, lockfile committed from CI platform                                       |
