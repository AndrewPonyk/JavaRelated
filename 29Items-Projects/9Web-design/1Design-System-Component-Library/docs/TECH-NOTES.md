# Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended pipeline stages:

1. Install dependencies with lockfile enforcement using `npm ci`.
2. Run formatting and lint checks.
3. Run TypeScript type checks.
4. Run unit tests.
5. Run accessibility tests with jest-axe.
6. Build the component library.
7. Build Storybook.
8. Publish visual snapshots to Chromatic when credentials are available.
9. Upload package and Storybook artifacts from protected release tags.

For this local-first project, deployment means producing a tested package and Storybook artifact. A future internal deployment can publish Storybook to static hosting and the package to a private npm registry.

## 3.2 Testing Strategy

### Unit testing

- Use Jest with `ts-jest` and `jest-environment-jsdom`.
- Target at least 80% coverage for shared utilities and services.
- Target higher coverage for accessibility-critical components such as forms, modals, menus, and focus management.

### Integration testing

- Test token generation as an integration path from JSON tokens to Sass, CSS custom properties, and Tailwind theme output.
- Test API controllers through HTTP-level requests once the server is fully implemented.
- Validate generated artifacts are deterministic to avoid noisy reviews.

### End-to-end and accessibility testing

- Use Storybook interaction tests for component behavior.
- Use `jest-axe` for automated WCAG checks.
- Use Chromatic for visual regression and manual design review.
- Add Playwright only when cross-page or browser-level flows appear; Storybook tests are enough for the initial component library.

## 3.3 Deployment Strategy

The initial deployment strategy is package-oriented:

- Build a typed library bundle with Vite.
- Build static Storybook documentation.
- Publish visual baselines through Chromatic.
- Publish package artifacts to an internal registry after review.

Containerization is implemented for consistent local validation. `docker-compose up --build` starts Postgres, the Express API, migrations/seed data, and the Vite frontend.

## 3.4 Environment Management

Use `.env` for local development, CI secrets for automation, and never commit real secrets.

Required template:

```env
NODE_ENV=development
PORT=4100
POSTGRES_DB=design_system
POSTGRES_USER=design_system
POSTGRES_PASSWORD=change-me-for-shared-environments
FIGMA_TOKEN=
FIGMA_FILE_KEY=
CHROMATIC_PROJECT_TOKEN=
DATABASE_URL=postgres://design_system:change-me-for-shared-environments@localhost:5432/design_system
LOG_LEVEL=info
AUTO_MIGRATE=false
FIGMA_TOKENS_FILE=
VITE_API_BASE_URL=http://localhost:4100
CORS_ORIGIN=http://localhost:5173
RATE_LIMIT_WINDOW_MS=60000
RATE_LIMIT_MAX=300
REQUIRE_HTTPS=false
TRUST_PROXY=false
API_KEY=
```

Environment-specific values:

- Development: local `.env`, optional local Postgres through Docker Compose.
- Staging: CI-managed secrets, static Storybook preview, private package prerelease.
- Production: protected CI environment, immutable package versions, reviewed Chromatic baselines.

## 3.5 Version Control Workflow

Use **trunk-based development with short-lived feature branches**.

Rationale:

- Design systems benefit from frequent small changes and visible review.
- Token and component changes should be easy to diff and release.
- Long-lived branches increase visual regression conflicts and make adoption harder for consuming products.

Recommended rules:

- Protect `main`.
- Require lint, test, build, Storybook build, and Chromatic status checks.
- Use conventional commits to drive changelog generation.
- Release through version tags and reviewed package publishing jobs.

## 3.6 Common Pitfalls

- Token drift between Figma, Sass, Tailwind, and component code.
- Treating Storybook examples as documentation only instead of executable acceptance cases.
- Missing keyboard and focus states for interactive components.
- Relying only on automated accessibility tests; manual review remains necessary.
- Overfitting components to a single product instead of stable design-system primitives.
- Publishing breaking token changes without migration notes.
- Letting generated files create noisy diffs.
- Allowing visual review to bypass semantic HTML and WCAG requirements.
