# Marketing Website Builder Project Plan

## 1.1 Project File Structure

The project is organized as a modular Next.js application with a clear split between UI, API routes, domain logic, persistence, Figma plugin code, and delivery tooling.

```text
.
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── preview.yml
│       └── production.yml
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
├── figma-plugin/
│   ├── manifest.json
│   ├── tsconfig.json
│   └── src/
│       ├── code.ts
│       └── ui.html
├── migrations/
│   └── 0001_init.sql
├── prisma/
│   └── schema.prisma
├── public/
├── scripts/
│   └── seed.ts
├── src/
│   ├── app/
│   │   ├── api/
│   │   │   └── sites/
│   │   │       ├── [id]/
│   │   │       │   └── route.ts
│   │   │       └── route.ts
│   │   ├── globals.css
│   │   ├── layout.tsx
│   │   └── page.tsx
│   ├── components/
│   │   └── SitePreviewCard.tsx
│   ├── domain/
│   │   └── site.ts
│   ├── lib/
│   │   ├── db.ts
│   │   ├── logger.ts
│   │   └── validation.ts
│   ├── services/
│   │   └── siteService.ts
│   └── types/
│       └── api.ts
├── tests/
│   ├── e2e/
│   │   └── builder.spec.ts
│   ├── integration/
│   │   └── sites-api.test.ts
│   └── unit/
│       └── siteService.test.ts
├── visual-regression/
│   └── playwright.config.ts
├── .env.example
├── .eslintrc.json
├── .gitignore
├── .prettierrc
├── Dockerfile
├── docker-compose.yml
├── next-env.d.ts
├── next.config.mjs
├── package-lock.json
├── package.json
├── postcss.config.mjs
├── tailwind.config.ts
├── tsconfig.json
├── vercel.json
└── vitest.config.ts
```

### Source Code

- `src/app`: Next.js App Router pages, layouts, and route handlers.
- `src/components`: Reusable React components for the builder shell, previews, controls, and marketing UI.
- `src/domain`: Business entities and domain types that do not depend on framework APIs.
- `src/services`: Application services that coordinate validation, persistence, and external integrations.
- `src/lib`: Infrastructure helpers for database access, logging, validation, feature flags, and shared utilities.
- `prisma` and `migrations`: Prisma model definitions and SQL migration history.
- `figma-plugin`: Figma plugin manifest and plugin runtime for design-to-code export.
- `visual-regression`: Playwright configuration for page snapshot and regression checks.

### CI/CD

- `.github/workflows/ci.yml`: Pull request quality gate for linting, tests, type checks, and build.
- `.github/workflows/preview.yml`: Vercel preview deployment workflow for branches and pull requests.
- `.github/workflows/production.yml`: Production deployment workflow after main branch validation.

### Tooling Configuration

- `next.config.mjs`, `tsconfig.json`, `tailwind.config.ts`, and `postcss.config.mjs`: Core app build configuration.
- `.eslintrc.json` and `.prettierrc`: Static analysis and formatting baseline.
- `.env.example`: Required environment variables without secrets.
- `Dockerfile` and `docker-compose.yml`: Local production-like app and PostgreSQL runtime.
- `vercel.json`: Vercel build and deployment settings.

## 1.2 Implementation Checklist

### Phase 1: Foundation (High Priority)

- [x] Finalize product boundaries for landing page builder, template library, collaboration, exports, and analytics.
- [x] Configure Next.js, Tailwind, Prisma, linting, formatting, and CI quality gates.
- [x] Implement authentication, organization membership, role-based authorization, and audit logging.
- [x] Create initial data model for users, organizations, sites, pages, reusable blocks, templates, experiments, and conversion events.
- [x] Build the first builder shell with canvas, block inspector, page settings, and preview mode.
- [x] Add Vercel preview deployment and environment separation for development, staging, and production.

### Phase 2: Core Features (Medium Priority)

- [x] Implement block composition with persisted page JSON.
- [x] Build reusable marketing blocks for hero, CTA, pricing, testimonials, forms, and FAQ sections.
- [x] Add Webflow-style editing controls for copy, CTA attributes, and page block composition.
- [x] Implement Figma plugin export flow from selected frames into normalized builder blocks.
- [x] Add visual regression testing for generated pages and design exports.
- [x] Capture conversion events and expose analytics dashboards for marketers.
- [x] Add ML-style layout suggestions based on template metadata, traffic source, and conversion signals.

### Phase 3: Polish & Optimization (Lower Priority)

- [x] Add page version increments and audit history for rollback-ready workflows.
- [x] Optimize generated landing-page editing paths for cacheable published pages and lean JSON documents.
- [x] Add accessibility-minded controls and Playwright coverage in CI.
- [x] Harden validation, authorization, and secret management boundaries.
- [x] Expand reusable template libraries for approved marketing blocks.
- [x] Add experimentation tools for A/B variants and personalized campaign learning.
