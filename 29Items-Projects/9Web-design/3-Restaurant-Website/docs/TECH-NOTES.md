# Restaurant Website Technical Notes

## 3.1 CI/CD Pipeline Design

GitHub Actions runs:

1. `npm ci`
2. `npm run lint`
3. `npm test`
4. `npm run build`
5. `npx playwright install --with-deps chromium`
6. `npm run test:e2e`
7. Netlify production deploy from `main`

The deploy job depends on verification passing.

## 3.2 Testing Strategy

- **Unit tests:** Vitest covers shared reservation validation.
- **Integration tests:** Supertest exercises Express reservation CRUD against a pg-mem Postgres-compatible database loaded from the real migration.
- **Coverage target:** 80% minimum statement, line, and function coverage for backend and shared modules.
- **End-to-end tests:** Playwright covers homepage rendering, menu filtering, gallery lightbox behavior, client validation, and successful reservation submission with an intercepted API response.

## 3.3 Deployment Strategy

Primary deployment is Netlify:

- Build command: `npm run build`
- Publish directory: `dist`
- Functions directory: `netlify/functions`
- API redirects: `/api/reservations` to the reservation function

Container deployment is also supported:

- `docker compose up --build` starts Postgres, runs migrations, serves static files, and exposes the API on port `8080`.

## 3.4 Environment Management

Use `.env.example` as the template:

```env
FORM_ENDPOINT=https://formspree.io/f/your-form-id
PUBLIC_SITE_URL=http://localhost:5173
PORT=8080
NODE_ENV=development
CORS_ORIGIN=http://localhost:5173
API_RATE_LIMIT_PER_MINUTE=60
NETLIFY_SITE_ID=replace-me
NETLIFY_AUTH_TOKEN=replace-me
ANALYTICS_ID=
DATABASE_URL=postgres://restaurant:restaurant@localhost:5432/restaurant
```

Local `.env` files stay untracked. Netlify and GitHub Actions receive secrets through their managed environment settings.

## 3.5 Version Control Workflow

Use GitHub Flow:

- `main` is deployable.
- Work happens in short-lived branches.
- Pull requests run CI and generate Netlify previews.
- Merge only after checks and preview verification.

## 3.6 Common Pitfalls Addressed

- CDN libraries are version-pinned.
- Reservation validation runs on client and server.
- SQL uses parameterized queries.
- The API has consistent public errors.
- The frontend respects reduced-motion preferences.
- Docker Compose proves the database-backed path locally.
