# Technical Notes

## 3.1 CI/CD Pipeline Design

The main CI pipeline should run on every pull request and push to `main`.

Recommended stages:

1. Install dependencies with `npm ci`.
2. Validate formatting and linting with ESLint and Prettier.
3. Run TypeScript checks with `tsc --noEmit`.
4. Run unit and integration tests with Vitest.
5. Run Playwright smoke and visual regression tests where browser dependencies are available.
6. Build the Next.js app with `npm run build`.
7. Deploy previews from pull requests and production from `main`.

GitHub Actions should remain the quality gate, while Vercel handles optimized hosting, preview URLs, edge caching, and production promotion.

## 3.2 Testing Strategy

### Unit Testing

Use Vitest for domain and service tests. The configured suite enforces 70 percent or better coverage over the core page document and validation logic, with higher coverage on the conversion-sensitive document transformation paths.

### Integration Testing

Use route handler integration tests for stable public contracts and database-backed service tests when a test PostgreSQL instance is available. Cover CRUD flows, validation failures, authorization failures, Figma import payloads, and publish workflows.

### End-to-End Testing

Use Playwright for builder workflows and visual regression. Keep stable smoke tests for login, creating a site, editing a block, previewing, and publishing. Store baseline screenshots for generated pages and Figma imports in a controlled visual regression workflow.

## 3.3 Deployment Strategy

Deploy the app to Vercel using preview deployments for pull requests, a staging environment for release validation, and production deployments from `main`.

Containerization is useful for local development and CI parity. The included `Dockerfile` builds the Next.js application, and `docker-compose.yml` provides PostgreSQL plus an app container. Production hosting can remain Vercel-native unless the product later needs long-running workers that are better deployed on a container platform.

## 3.4 Environment Management

Use environment-specific variables for development, staging, and production. Store real values in Vercel environment settings, GitHub Actions secrets, or a managed secret store.

Required template:

```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/marketing_builder
NEXT_PUBLIC_APP_URL=http://localhost:3000
AUTH_SECRET=replace-me
FIGMA_PLUGIN_CLIENT_ID=replace-me
WEBFLOW_CLIENT_ID=replace-me
WEBFLOW_CLIENT_SECRET=replace-me
ML_SUGGESTIONS_API_URL=http://localhost:4000
VISUAL_REGRESSION_BASE_URL=http://localhost:3000
```

Do not share `.env.local` between developers. Each environment should define explicit values and avoid hidden defaults for production-sensitive settings.

## 3.5 Version Control Workflow

Use **trunk-based development with short-lived branches**. This fits a Vercel preview workflow and keeps product increments small. Feature branches should be merged behind feature flags when incomplete or risky.

Recommended branch flow:

- `main`: Always deployable.
- `feature/*`: Short-lived branches for product increments.
- `fix/*`: Small fixes and production hardening.
- `release/*`: Optional only when coordinated manual QA is required.

## 3.6 Common Pitfalls

- Treating page-builder JSON as an unversioned blob. Add schema versions and migrations early.
- Letting Figma export produce arbitrary code. Normalize Figma nodes into supported builder blocks instead.
- Mixing public landing-page runtime code with private builder APIs. Keep published-page rendering paths minimal and cacheable.
- Running visual regression on unstable dynamic content. Freeze dates, images, animations, and test data.
- Overloading serverless functions with long-running jobs. Move visual snapshots, ML recommendations, and heavy exports into async workers.
- Forgetting authorization in nested resources. Every site, page, block, analytics report, and export must be scoped to an organization.
