# Hotel Booking Website

Responsive hotel booking application built with HTML5, Bootstrap 5, Vanilla JS, Leaflet.js, AOS, Swiper.js, Netlify Functions, PostgreSQL, Docker, and GitHub Actions.

## Features

- Search hotel rooms by city, dates, guests, and room type.
- Server-side availability checks with overlapping booking prevention.
- Server-side price calculation with taxes.
- Guest booking creation with confirmation codes.
- Staff admin UI for room inventory and booking status management.
- CRUD APIs for hotels, rooms, guests, bookings, and staff users.
- PostgreSQL migration with seed hotels, rooms, indexes, and constraints.

## Run With Docker

```bash
copy .env.docker.example .env
# Edit .env and replace every change-this-* value first.
docker-compose up --build
```

Open:

```text
http://localhost:8888
```

Use the `STAFF_API_TOKEN` value from your local `.env` file in the admin section.

The PostgreSQL migration runs automatically the first time the Docker database volume is created.

## Run Locally With Node

```bash
npm install
copy .env.example .env
# Edit .env and point DATABASE_URL at PostgreSQL.
npm run dev
```

Set `DATABASE_URL` in `.env` to a reachable PostgreSQL database before using API features.

## Tests

```bash
npm test
```

The test suite includes unit tests, database-backed integration tests, and Netlify Function handler tests. Coverage thresholds are configured in `vitest.config.js`.

End-to-end tests:

```bash
npm run test:e2e
```

## API

All endpoints return `{ "data": ... }` on success or `{ "error": { "code": "...", "message": "..." } }` on failure.

Public endpoints:

- `GET /api/hotels`
- `GET /api/rooms`
- `GET /api/availability?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD&guests=2`
- `POST /api/bookings`
- `GET /api/bookings?confirmationCode=HB-...`

Staff endpoints require headers:

```text
x-staff-token: <STAFF_API_TOKEN>
x-staff-role: manager
```

Staff CRUD endpoints:

- `POST|PATCH|DELETE /api/hotels`
- `POST|PATCH|DELETE /api/rooms`
- `GET|POST|PATCH|DELETE /api/guests`
- `GET|PATCH|DELETE /api/bookings`
- `GET|POST|PATCH|DELETE /api/staff-users`

## Deployment

Netlify uses:

- Static publish directory: `src`
- Functions directory: `netlify/functions`
- Redirects and headers: `netlify.toml`

Set these Netlify environment variables:

- `DATABASE_URL`
- `DATABASE_SSL`
- `STAFF_API_TOKEN`
- `SESSION_SECRET`
- `EMAIL_PROVIDER_API_KEY`

## Documentation

- `docs/PROJECT-PLAN.md`
- `docs/ARCHITECTURE.md`
- `docs/TECH-NOTES.md`
- `docs/API.md`

## Troubleshooting

- Docker reports missing variables: copy `.env.docker.example` to `.env` and replace the placeholder values.
- Database migration did not rerun: remove the Docker volume with `docker compose down -v`, then start again.
- Admin UI returns 401: confirm the token entered in the UI matches `STAFF_API_TOKEN`.
- Port 8888 is busy: stop the conflicting process or change the `PORT`/compose port mapping.
- PostgreSQL port 5432 is busy: change `POSTGRES_PORT` in `.env`.
