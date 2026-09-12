# Hotel Booking Website - Technical Notes

## 3.1 CI/CD Pipeline Design

The GitHub Actions workflow performs:

1. Dependency installation with `npm install`.
2. ESLint checks.
3. Vitest unit and integration tests with coverage thresholds.
4. Static build validation.
5. Netlify deployment hook is documented in the workflow and can be enabled after secrets are configured.

## 3.2 Testing Strategy

- Unit tests cover date math, price calculation, and validation rules.
- Integration tests load the real SQL migration into an in-memory PostgreSQL-compatible database and exercise service/database behavior.
- Function tests invoke Netlify handlers with realistic event payloads.
- Playwright is configured for the public booking page and can run against the local Docker or Node server.

## 3.3 Deployment Strategy

- Production: deploy `src` and `netlify/functions` to Netlify using `netlify.toml`.
- Local full stack: run `docker-compose up --build` to start PostgreSQL and the Node adapter on port `8888`.
- Before using Docker, copy `.env.docker.example` to `.env` and replace placeholder values.
- Database: apply `migrations/001_initial_schema.sql` to PostgreSQL. Docker does this automatically on first database volume creation.

## 3.4 Environment Management

Required environment variables:

```dotenv
APP_ENV=development
SITE_URL=http://localhost:8888
POSTGRES_DB=hotel_booking
POSTGRES_USER=hotel_app
POSTGRES_PASSWORD=change-this-database-password
POSTGRES_PORT=5432
APP_PORT=8888
DATABASE_URL=postgres://hotel_app:change-this-database-password@localhost:5432/hotel_booking
DATABASE_SSL=false
STAFF_API_TOKEN=change-this-staff-token
SESSION_SECRET=change-this-session-secret
EMAIL_PROVIDER_API_KEY=change-this-email-provider-key
NETLIFY_AUTH_TOKEN=replace-only-in-ci
NETLIFY_SITE_ID=replace-only-in-ci
```

Browser code never receives database URLs or staff secrets. Staff requests pass the token as an HTTP header only from the admin UI.

## 3.5 Version Control Workflow

Use GitHub Flow:

- `main` remains deployable.
- Short-lived branches carry focused changes.
- Pull requests run linting, tests, and build validation.
- Netlify preview deployments can be enabled for review environments.

## 3.6 Common Pitfalls Addressed

- Client price trust: fixed by server-side price calculation.
- Date overlap bugs: fixed by date-only validation and active-booking overlap queries.
- Duplicate room reservations: fixed by transactional room locking during booking creation.
- Static-only data: replaced with PostgreSQL-backed endpoints.
- Staff data exposure: protected with staff token and role checks.
- Function coupling: business rules live in service modules, not handlers.
- Unbounded list responses: fixed with capped `limit`/`offset` pagination.
- XSS in dynamic browser rendering: fixed by escaping API-provided text before inserting HTML.
