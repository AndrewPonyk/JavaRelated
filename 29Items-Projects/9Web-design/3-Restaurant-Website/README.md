# Restaurant Website

Production-ready static restaurant website with Tailwind CSS, vanilla JavaScript, GSAP, Swiper.js, Lucide icons, Postgres-backed reservation CRUD, Netlify Function deployment, and Docker Compose support.

## Requirements

- Node.js 22+
- npm 10+
- Docker Desktop for full-stack local runs

## Local Frontend

```bash
npm install
npm run dev
```

Open `http://127.0.0.1:5173`.

## Full Stack With Docker

```bash
docker compose up --build
```

The full application runs at `http://localhost:8080`, with Postgres on `localhost:5432`.

## Local API Without Docker

Start a Postgres database, copy `.env.example` to `.env`, then run:

```bash
npm run db:migrate
npm start
```

The API is available at `http://localhost:8080/api/reservations`.

## Verification

```bash
npm run lint
npm test
npm run build
npm run test:e2e
```

`npm test` runs unit and integration tests with 80% minimum statement, line, and function coverage for backend and shared modules.

## Troubleshooting

- If Playwright reports a Vite overlay on Windows, run `npm run test:e2e`; the script builds first and tests the production preview on port `4173`.
- If Docker cannot bind `5432` or `8080`, stop the local service using that port or change the published port in `docker-compose.yml`.
- If reservations fail in production, confirm `DATABASE_URL` is set and migrations have run with `npm run db:migrate`.
- If Formspree notifications are not received, confirm `FORM_ENDPOINT` is set to a real Formspree form URL. Empty or example endpoints are skipped.

## Reservation API

### List Reservations

`GET /api/reservations?status=requested&date=2026-06-01&page=1&pageSize=20`

Optional query parameters: `status`, `date`, `page`, `pageSize`.

### Create Reservation

`POST /api/reservations`

```json
{
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "date": "2026-06-01",
  "partySize": 4,
  "notes": "Window table if available"
}
```

### Read Reservation

`GET /api/reservations/:id`

### Update Reservation

`PUT /api/reservations/:id`

```json
{
  "status": "confirmed",
  "notes": "Confirmed by phone"
}
```

### Delete Reservation

`DELETE /api/reservations/:id`

All API responses use a JSON envelope with `ok: true` on success or `ok: false`, `error`, `code`, and `details` on failure.

## Deployment

Netlify serves the static build from `dist` and routes `/api/reservations` to `netlify/functions/reservations.js`. Configure these environment variables in Netlify:

- `DATABASE_URL`
- `FORM_ENDPOINT` if Formspree notification forwarding is needed
- `CORS_ORIGIN`

GitHub Actions runs install, lint, unit/integration tests, build, e2e smoke tests, and deploys `main` to Netlify when secrets are configured.
