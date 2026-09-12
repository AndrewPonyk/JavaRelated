# Design System & Component Library

Local-first design system package with TypeScript Web Components, Sass/Tailwind token artifacts, Storybook documentation, a Postgres-backed token governance API, accessibility tests, and Chromatic-ready CI.

## What Runs

- Frontend app: `http://localhost:5173`
- API: `http://localhost:4100`
- Storybook: `http://localhost:6006`
- Postgres: `localhost:5432`

## Quick Start

```bash
npm install
npm run tokens:build
npm run format
npm run lint
npm test
npm run build
```

## Run With Docker

```bash
docker-compose up --build
```

Docker starts:

- `postgres`: token metadata database
- `api`: Express API, migrations, seed data
- `web`: Vite frontend for the governance dashboard

Open `http://localhost:5173` after the services are healthy.

For shared environments, create a local `.env` file and override at least `POSTGRES_PASSWORD`, `API_KEY`, `CORS_ORIGIN`, `REQUIRE_HTTPS`, and `TRUST_PROXY`. The defaults are local-development values only.

## Run Locally Without Docker

Start Postgres and set `DATABASE_URL`, then run:

```bash
npm run db:setup
npm run api:dev
npm run dev
```

## Storybook

```bash
npm run storybook
npm run build:storybook
```

## API

The API uses JSON request and response bodies. Successful list/get responses use `{ "data": ... }`. Errors use:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Token payload is invalid",
    "details": {}
  }
}
```

Main endpoints:

- `GET /health`
- `GET /api/status`
- `GET /api/token-sets`
- `POST /api/token-sets`
- `GET /api/token-sets/:id`
- `PATCH /api/token-sets/:id`
- `DELETE /api/token-sets/:id`
- `GET /api/token-sets/:id/diff/:compareId`
- `GET /api/token-sets/:id/audit`
- `GET /api/tokens`
- `POST /api/tokens`
- `GET /api/tokens/:id`
- `PATCH /api/tokens/:id`
- `DELETE /api/tokens/:id`
- `POST /api/tokens/:id/approve`
- `POST /api/tokens/:id/reject`
- `POST /api/tokens/:id/deprecate`
- `GET /api/tokens/:id/versions`
- `GET /api/tokens/:id/audit`
- `GET /api/tokens/changelog`

List endpoints accept `limit` and `offset` query parameters and return pagination metadata:

```json
{
  "data": [],
  "meta": {
    "limit": 25,
    "offset": 0,
    "total": 0
  }
}
```

More detail is in [API.md](docs/API.md).

## Token Workflow

The canonical token source is [design-tokens.json](src/tokens/design-tokens.json). Generated artifacts are:

- [tokens.scss](src/styles/tokens.scss)
- [tailwind-theme.json](src/tokens/tailwind-theme.json)

Regenerate artifacts:

```bash
npm run tokens:build
```

Sync from a local Tokens Studio/Figma export:

```bash
FIGMA_TOKENS_FILE=path/to/export.json npm run tokens:sync
```

## Verification

```bash
npm run format
npm run lint
npm test
npm run build
npm run build:storybook
```

Current automated coverage target is at least 80% statements, functions, and lines, with branch coverage enforced at 70% or higher.

## Troubleshooting

- Docker cannot connect to `dockerDesktopLinuxEngine`: start Docker Desktop and wait for the Linux engine to be ready.
- API returns `UNAUTHORIZED`: set `x-api-key` to the configured `API_KEY`, or leave `API_KEY` empty for local-only development.
- API returns `HTTPS_REQUIRED`: either call through HTTPS or set `REQUIRE_HTTPS=false` for local development.
- Postgres authentication fails: make sure `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` agree.
- Storybook build fails with `spawn EPERM`: allow Storybook/esbuild to spawn its worker process, or rerun from a shell with permission to execute binaries in `node_modules`.
