# Marketing Website Builder

Production-oriented no-code landing page builder for marketing teams. The app provides organization-scoped workspaces, role-aware APIs, block-based landing page editing, Figma selection import, conversion analytics, layout suggestions, experiments, and Vercel-ready deployment automation.

## Tech Stack

- Next.js App Router, React, Tailwind CSS
- Prisma and PostgreSQL
- Alpine.js dependency for embeddable published-page runtime behavior
- Figma plugin source for design-to-code import
- Vitest, Playwright, ESLint, Prettier
- Docker Compose, GitHub Actions, Vercel

## Local Setup

```bash
npm install
copy .env.example .env.local
npm run db:generate
npm run dev
```

For a database-backed local run, start PostgreSQL first and run migrations:

```bash
docker compose up db -d
npm run db:deploy
npm run db:seed
npm run dev
```

Open `http://localhost:3000`. The app uses `demo@builder.local` as the local actor unless `DEMO_USER_EMAIL` or `x-user-email` is provided.

## Docker

```bash
docker-compose up --build
```

The app container runs `prisma migrate deploy` before starting Next.js. PostgreSQL is exposed on port `5432`; the app is exposed on port `3000`.

## Quality Checks

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

`npm test` runs Vitest with coverage thresholds over the core business validation and page document logic. Browser checks run with:

```bash
npm run test:e2e
```

## Main API Areas

- `GET/POST /api/organizations`
- `GET/PATCH/DELETE /api/organizations/:id`
- `GET/POST /api/sites`
- `GET/PATCH/DELETE /api/sites/:id`
- `GET/POST /api/sites/:id/pages`
- `PATCH/DELETE /api/sites/:id/pages/:pageId`
- `GET/POST /api/templates`
- `PATCH/DELETE /api/templates/:id`
- `GET/POST /api/experiments`
- `PATCH/DELETE /api/experiments/:id`
- `POST /api/conversion-events`
- `GET /api/analytics`
- `GET/POST /api/sites/:id/suggestions`
- `POST /api/figma/import`

See [docs/API.md](C:/mygit/JavaRelated/29Items-Projects/9Web-design/2-Marketing-WebSite-Builder/docs/API.md) for request details.
