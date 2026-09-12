# Restaurant Website Project Plan

## 1.1 Project File Structure

The implemented application is a static-first restaurant website with a Postgres-backed reservation API. The same reservation controller is used by the Netlify Function adapter and the Docker/local Express server.

```text
.
|-- index.html
|-- package.json
|-- package-lock.json
|-- netlify.toml
|-- Dockerfile
|-- docker-compose.yml
|-- .env.example
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- docs/
|   |-- API.md
|   |-- PROJECT-PLAN.md
|   |-- ARCHITECTURE.md
|   `-- TECH-NOTES.md
|-- migrations/
|   `-- 001_create_reservations.sql
|-- netlify/
|   `-- functions/
|       `-- reservations.js
|-- public/
|   |-- assets/
|   |   `-- images/
|   `-- data/
|       `-- menu.json
|-- src/
|   |-- backend/
|   |   |-- app.js
|   |   |-- server.js
|   |   |-- controllers/
|   |   |-- db/
|   |   |-- repositories/
|   |   `-- services/
|   |-- frontend/
|   |   |-- main.js
|   |   |-- components/
|   |   |-- data/
|   |   `-- styles/
|   `-- shared/
|       `-- validation/
`-- tests/
    |-- fixtures/
    |-- integration/
    |-- unit/
    `-- e2e/
```

### Source Code Boundaries

- `src/frontend`: vanilla JavaScript components for menu filtering, featured carousel, gallery lightbox, animations, and reservation submission.
- `src/shared`: validation reused by frontend and backend.
- `src/backend`: Express app, reservation controller, service logic, repository, Postgres pool, and migration runner.
- `netlify/functions`: serverless adapter for Netlify deployment.
- `migrations`: executable Postgres schema.
- `tests`: unit, integration, and Playwright smoke tests.

### CI/CD Structure

- `.github/workflows/ci.yml`: install, lint, tests, build, Playwright, deploy.
- `netlify.toml`: build settings, function redirects, cache headers, and security headers.
- `Dockerfile` and `docker-compose.yml`: full-stack local and container deployment path.

## 1.2 Completed Implementation Checklist

### Phase 1: Foundation

- [x] Finalized Aster Table branding, copy, palette, and visual direction.
- [x] Added production-safe SVG visual assets and a remote video source with local poster fallback.
- [x] Configured Formspree forwarding through environment variables.
- [x] Implemented sticky navigation and mobile menu behavior.
- [x] Implemented reservation validation with accessible inline errors.
- [x] Documented Netlify environment variables.
- [x] Configured GitHub Actions for CI and Netlify deployment.

### Phase 2: Core Features

- [x] Built menu filtering for starters, mains, desserts, and drinks.
- [x] Added Swiper carousel support for featured menu items and gallery.
- [x] Added GSAP scroll reveals with reduced-motion fallback.
- [x] Implemented lightbox gallery with click-away and Escape handling.
- [x] Added chef story and conversion-focused reservation section.
- [x] Added unit tests for validation.
- [x] Added integration tests for CRUD API flows.
- [x] Added Playwright smoke tests for homepage, menu, gallery, and reservation behavior.

### Phase 3: Polish & Optimization

- [x] Added responsive SVG assets and cache headers.
- [x] Added restaurant structured data and Open Graph metadata.
- [x] Added analytics-ready event boundaries in the frontend interaction flow.
- [x] Added production logging for API errors.
- [x] Reviewed security headers in `netlify.toml`.
