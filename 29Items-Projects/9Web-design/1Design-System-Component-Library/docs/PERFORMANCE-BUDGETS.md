# Performance Budgets

## Package

- Library JavaScript gzip target: under 10 KB for the current component set.
- Library CSS gzip target: under 5 KB for the current component set.
- Keep Storybook-only dependencies out of runtime dependencies.

## Frontend

- Dashboard first meaningful render target: under 2 seconds on local Docker after API health is ready.
- API list endpoints use `limit` and `offset`; keep `limit` at or below 100.

## Review

Run `npm run build` and inspect Vite gzip output before release. Any budget increase must be called out in release notes.
