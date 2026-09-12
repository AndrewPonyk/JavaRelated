# Customer Portal SPA

React 18 + TypeScript + Vite single-page application for the Customer Portal — auth with
silent token refresh, dashboard (balance, trend sparkline, activity feed, announcements),
settings, consent-gated GA4 analytics, and A/B experimentation — delivered through a fully
gated CI/CD pipeline: ESLint/Prettier, Jest, Playwright, Lighthouse budgets, S3+CloudFront
PR previews, and DigitalOcean App Platform for staging/production.

| Documentation                                |                                            |
| -------------------------------------------- | ------------------------------------------ |
| [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) | structure, roadmap, what's done vs pending |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | patterns, diagrams, data flow, security    |
| [docs/TECH-NOTES.md](docs/TECH-NOTES.md)     | pipeline, testing, deployment, pitfalls    |
| [docs/adr/](docs/adr/)                       | architecture decision records              |

## App functionality

Everything below is implemented and covered by tests.

**Authentication**

1. **User can sign in with e-mail + password (demo account: `demo@example.com` / `Password123!`).**
2. **Login form validates input before submitting — invalid e-mail format or a password shorter than 8 characters shows a field-level error message.**
3. **Wrong credentials show "E-mail or password is incorrect" and the user stays on the login page.**
4. **Being offline shows a specific "check your connection" message instead of a generic error.**
5. **User's session survives token expiry — on any 401 the app silently refreshes the token and retries the request; the user never notices.**
6. **Anonymous user opening any protected page is redirected to login.**
7. **Deep link is preserved through login — opening `/settings` while signed out → login → user lands on `/settings`, not the dashboard.**
8. **Already-signed-in user visiting `/login` is redirected straight into the app.**
9. **User can sign out — returns to login, all routes protected again.**

**Dashboard**

10. **User sees their account balance, formatted in their locale/currency.**
11. **User sees their open support-ticket count with a status hint ("All resolved" / "Awaiting response").**
12. **User sees their last sign-in date ("First visit" if none).**
13. **User sees a 6-month balance-trend sparkline with a text summary ("Balance up €615.43 since 2026-02").**
14. **User sees their 10 most recent account transactions — credits green with `+`, debits with `−`, dated.**
15. **User sees active announcements (maintenance notices, new features) with publish dates.**
16. **User is greeted by name ("Welcome back, Demo Customer").**

**Settings**

17. **User can view their profile (display name, notification preferences).**
18. **User can change their display name (validated, max 80 chars).**
19. **User can toggle marketing e-mails and product-update e-mails independently.**
20. **User can save — only the changed fields are sent; a "settings saved" confirmation appears; clicking save with no changes is handled gracefully.**

**Privacy & experimentation**

21. **First-time user sees an analytics consent banner and can Allow or Decline — the choice persists, and Google Analytics never loads before "Allow".**
22. **User is deterministically assigned to A/B variants (same user → same variant every visit): a dashboard-layout experiment (grid vs condensed) and an onboarding experiment.**
23. **Users in the onboarding treatment see a "Get started" checklist on the dashboard and can dismiss it permanently.**

**Everywhere**

24. **Every data widget has explicit loading, error-with-Retry, and empty states — a failed widget never blanks the page, and Retry actually refetches.**
25. **Unknown URLs show a "Page not found" page; refreshing on any deep link works (no CDN 404s).**
26. **A crashed page shows a recoverable error panel (route-level), and a crashed app shows an apology + reload button — never a white screen.**
27. **Keyboard users get a skip-to-content link; all forms/widgets are screen-reader labelled.**
28. **Footer shows which environment and build version the user is on.**

**Explicitly _not_ implemented** (no backend endpoints exist for them): password reset, self-registration, and idle-session timeout UX — the first two need new API contract entries; the third is a tracked Phase 3 roadmap item in [PROJECT-PLAN §3](docs/PROJECT-PLAN.md).

## Quickstart

```bash
nvm use              # Node 22 (.nvmrc)
npm ci
npm run msw:init     # generates public/mockServiceWorker.js (gitignored)
npm run dev          # http://localhost:5173
```

Sign in with **demo@example.com / Password123!**

No configuration needed: committed `.env.development` defaults run the app against the
**MSW mock backend** (personal overrides go in gitignored `.env.local`). Mock data lives in
`mocks/db/store.ts`; the API contract is `contracts/openapi.yaml`.

### Docker

```bash
docker compose up    # self-contained stack (hermetic build) on http://localhost:8080
```

Serves through the production nginx config — SPA fallback, security headers, immutable-asset
caching, `/healthz` — the same contract the CDN targets implement.

## Scripts

| Command                           | What it does                                             |
| --------------------------------- | -------------------------------------------------------- |
| `npm run dev`                     | Vite dev server with mock API                            |
| `npm run build`                   | typecheck + production build to `dist/`                  |
| `npm run lint` / `lint:fix`       | ESLint (flat config, zero warnings allowed)              |
| `npm run format` / `format:check` | Prettier                                                 |
| `npm run typecheck`               | `tsc --noEmit` (app code; tests are checked by ts-jest)  |
| `npm test` / `test:watch`         | Jest + Testing Library + MSW                             |
| `npm run test:coverage`           | Jest with the 80/70 coverage gates                       |
| `npm run test:e2e`                | Playwright against a hermetic local build (builds first) |
| `npm run test:e2e:smoke`          | `@smoke` specs only — used against live envs by CI       |
| `npm run lighthouse`              | Lighthouse CI with the budgets in `.lighthouserc.json`   |

## Verified locally (2026-07-12, post production-polish audit)

- `lint`, `format:check`, `typecheck` — clean
- `test:coverage` — **81 tests, 17 suites**, 95.8 % lines / 82.4 % branches (gates: 80/70)
- `test:e2e` — **10 Playwright tests** (chromium, hermetic MSW build, vite 6)
- `lighthouse` — every budget assertion green against the production `dist/`
- `npm audit` — **0 vulnerabilities in production dependencies** (dev-tooling triage: TECH-NOTES §3.6 #11)
- `docker compose up` — healthz, SPA deep-link fallback, caching contract, **and security
  headers (HSTS, CSP w/ frame-ancestors, X-Frame-Options, nosniff) probed on the live container**

## Delivery pipeline (summary)

- **Every PR:** lint → unit → build → E2E + Lighthouse budgets, plus a live preview at
  `https://<preview-domain>/pr-<n>/` (commented on the PR, torn down on close).
- **Merge to `main`:** auto-deploy to staging (DO App Platform) + `@smoke` E2E.
- **Publish a Release:** approval-gated production deploy + `@smoke` E2E.

Cloud onboarding (GitHub environments, AWS OIDC stack, DO apps) is the checklist in
[PROJECT-PLAN §3 Phase 1](docs/PROJECT-PLAN.md); mechanics, secrets inventory, and rollback
procedure are in [TECH-NOTES §3.3–3.4](docs/TECH-NOTES.md).

## Troubleshooting

| Symptom                                                                      | Cause → fix                                                                                                       |
| ---------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Blank page / "portal failed to start" in dev                                 | `public/mockServiceWorker.js` missing (it's gitignored) → `npm run msw:init`                                      |
| `Port 5173/4173 is already in use`                                           | both servers use `strictPort` by design → stop the other process (or run `npm run preview -- --port <n>` locally) |
| Jest: `Cannot find module 'msw/node'` or ESM `SyntaxError` in `node_modules` | test environment / msw version drift → keep `jest-fixed-jsdom` and the `msw@~2.10` pin (TECH-NOTES §3.6 #2)       |
| Playwright: `browserType.launch: Executable doesn't exist`                   | browsers not installed → `npx playwright install chromium`                                                        |
| `test:e2e` seems to test stale code                                          | it rebuilds via `e2e:serve` — if you bypassed it, delete `dist/` and rerun                                        |
| bash scripts fail in CI with `bad interpreter`                               | CRLF line endings → `.gitattributes` enforces LF; re-checkout the files (TECH-NOTES §3.6 #8)                      |
| `docker compose up` fails during `npm ci`                                    | corporate proxy/cert inside the build → pass `--build-arg` proxy vars or build on an unrestricted network         |
| Lighthouse scores fluctuate locally                                          | expected ±5 noise → assertions use the median of 3 runs; compare `lhci` output, not single runs                   |
| GA events don't appear                                                       | by design until: a `VITE_GA_MEASUREMENT_ID` is set for the build **and** the user accepts the consent banner      |

## House rules

- Every `VITE_*` value ships to the browser — never a secret.
- All env access goes through `src/app/env.ts`; all logging through `src/lib/logger.ts`;
  all analytics events through the typed map in `src/lib/analytics/events.ts`.
- Feature slices under `src/features/` don't import each other; shared code moves down
  into `components/`, `hooks/`, or `lib/`.
- The mock API (`mocks/`) must track `contracts/openapi.yaml` — zod response parsing will
  fail loudly on drift, by design.
