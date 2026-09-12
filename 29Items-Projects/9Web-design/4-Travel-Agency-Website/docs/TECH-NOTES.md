# Travel Agency Website Technical Notes

## 3.1 CI/CD Pipeline Design

GitHub Actions runs:

1. `npm ci`
2. `npm run validate:env`
3. `npm run lint`
4. `npm test`
5. `npm run build`
6. `npm run test:e2e`
7. Optional Netlify production deploy on `main` when `NETLIFY_AUTH_TOKEN` and `NETLIFY_SITE_ID` secrets are configured.

## 3.2 Testing Strategy

- Unit tests cover filtering and validators.
- Integration tests cover CRUD flows for tours, destinations, and inquiries through the Express adapter and shared route handlers.
- Coverage thresholds are enforced at 70 percent globally.
- Playwright tests cover the home page, tour filters, Leaflet map rendering, and contact form validation/submission.
- The browser test server uses an in-memory PostgreSQL-compatible database seeded from the real migrations.
- Error-path tests cover malformed JSON, validation failures, unsupported methods, missing resources, and health checks.

## 3.3 Deployment Strategy

Netlify is the production target. Static files publish from the repository root, while `/api/*` routes redirect to Netlify Functions. The same API route modules run in Docker through Express for local development.

Docker uses:

- `app`: Node 20 application server
- `db`: PostgreSQL 16
- `postgres-data`: persistent local database volume

The Express host enables response compression, configurable CORS, optional HTTPS redirects behind a proxy, and `/health` for container/platform checks.

## 3.4 Environment Management

Required variables are documented in `.env.example`:

```dotenv
PUBLIC_SITE_URL=http://localhost:3000
PUBLIC_MAP_TILE_URL=https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png
DATABASE_URL=postgres://travel:travel@localhost:5432/travel_agency
DB_POOL_SIZE=5
CORS_ORIGIN=http://localhost:3000
ENFORCE_HTTPS=false
CRM_API_KEY=
EMAIL_FROM=leads@example.com
EMAIL_TO=sales@example.com
```

Development, preview, staging, and production should each define their own `DATABASE_URL` and allowed `CORS_ORIGIN`.

## 3.5 Version Control Workflow

GitHub Flow remains the recommended workflow:

- `main` is deployable.
- Feature branches open pull requests.
- Pull requests run CI and Netlify deploy previews.
- Production deploys happen from `main`.

## 3.6 Common Pitfalls Addressed

- Leaflet initialization is guarded by the presence of `#destinationMap`.
- AOS is initialized only when the library is loaded.
- Contact form submission uses `/api/inquiries`, which works through both Express and Netlify redirects.
- Database migrations seed the catalog, so API data and UI data do not diverge.
- Secrets are not committed; `.env.example` contains only names and safe defaults.
- API list endpoints use bounded `limit` and `offset` pagination.
