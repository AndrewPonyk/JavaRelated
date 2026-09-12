# Travel Agency Website

Multi-page travel agency website built with HTML5, Bootstrap 5, Vanilla JS, Leaflet.js, AOS, Swiper.js, Netlify Functions, Express, and PostgreSQL.

## Features

- Static pages for Home, Destinations, Tours, About, and Contact.
- Database-backed tour catalog with filtering by region, price, duration, and difficulty.
- Database-backed destination map markers rendered with Leaflet.js.
- Featured tour carousel rendered with Swiper.js.
- Contact inquiry form with browser validation, API validation, and PostgreSQL persistence.
- CRUD API for tours, destinations, and inquiries.
- Docker Compose full stack with Node app and PostgreSQL.
- Jest unit/integration coverage and Playwright browser smoke tests.

## Requirements

- Node.js 20+
- npm 10+
- Docker Desktop for the full local stack

## Local Development Without Docker

This mode uses the in-memory PostgreSQL-compatible test database and is useful for UI/test work:

```bash
npm ci
npm run start:test
```

Open `http://localhost:3000`.

## Full Stack With Docker

```bash
docker-compose up --build
```

The app runs at `http://localhost:3000`. PostgreSQL is available inside the Docker network with:

```text
database: travel_agency
user: travel
password: travel
```

Migrations run automatically when the app container starts.

## Manual Database Migration

With PostgreSQL running:

```bash
copy .env.example .env
npm run db:migrate
```

On non-Windows shells, use `cp .env.example .env`.

## Quality Gates

```bash
npm run lint
npm test
npm run build
npm run test:e2e
```

`npm test` enforces 70 percent global coverage.

## API

API documentation is available in [docs/API.md](docs/API.md).

Primary routes:

- `GET|POST /api/tours`
- `GET|PATCH|DELETE /api/tours/:id`
- `GET|POST /api/destinations`
- `GET|PATCH|DELETE /api/destinations/:id`
- `GET|POST /api/inquiries`
- `GET|PATCH|DELETE /api/inquiries/:id`

## Deployment

Netlify is the production target. Configure these environment variables in Netlify:

- `DATABASE_URL`
- `DB_POOL_SIZE`
- `CORS_ORIGIN`
- `ENFORCE_HTTPS`
- `CRM_API_KEY`
- `EMAIL_FROM`
- `EMAIL_TO`

GitHub Actions runs linting, coverage tests, build validation, Playwright tests, and optionally deploys to Netlify when `NETLIFY_AUTH_TOKEN` and `NETLIFY_SITE_ID` repository secrets are configured.

## Health Check

```bash
curl http://localhost:3000/health
```

Expected response:

```json
{
  "status": "ok",
  "database": "ok",
  "uptime": 12.34
}
```

## Troubleshooting

- If `docker-compose up --build` fails because port `3000` is in use, stop the existing process or change the app port mapping in `docker-compose.yml`.
- If local migrations fail, verify `DATABASE_URL` points to a reachable PostgreSQL database and run `npm run db:migrate` again.
- If Playwright reuses a stale local server, stop the process on port `3000` and rerun `npm run test:e2e`.
- If Netlify API routes return database errors, confirm the production `DATABASE_URL` is configured and reachable from Netlify Functions.

## Rollback

Use Netlify deploy history to restore a previous static/function deployment. Database migrations are additive for this implementation; create an explicit down migration before removing or renaming persisted columns.
