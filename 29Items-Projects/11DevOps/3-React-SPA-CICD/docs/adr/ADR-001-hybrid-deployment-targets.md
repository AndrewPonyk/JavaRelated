# ADR-001: Hybrid deployment targets (AWS for PR previews, DigitalOcean for staging/production)

- **Status:** Accepted
- **Date:** 2026-07-12

## Context

The delivery requirements name two platforms: **S3 + CloudFront deployments for pull-request
previews** and **DigitalOcean App Platform** as the deployment platform for long-lived
environments. Running two clouds for one static frontend is unusual enough to deserve a
recorded rationale and an exit strategy.

## Decision

Keep both, with a sharp boundary:

- **AWS S3 + CloudFront** hosts _ephemeral_ PR previews under path prefixes
  (`/pr-<n>/`), created and destroyed by CI (`pr-preview.yml`, `pr-preview-cleanup.yml`).
  One bucket + one distribution; cost scales with open PRs, approximately zero at rest.
  CI authenticates via GitHub OIDC role assumption — no long-lived AWS keys.
- **DigitalOcean App Platform** hosts _stable_ environments (staging, production) from
  declarative specs in `infra/digitalocean/`, deployed exclusively by GitHub Actions.

## Alternatives considered

- **Everything on DO:** App Platform has no first-class per-PR preview primitive with automatic
  teardown and path/wildcard previews; simulating it (one app per PR via API) is slower to
  provision and easier to leak than S3 prefix previews.
- **Everything on AWS:** viable (S3+CF for all envs), but the platform requirement names DO for
  the product environments, and App Platform's managed build/rollback UX is a genuine
  operational win for a small team.
- **Vercel/Netlify for previews:** best-in-class previews, but adds a third vendor and moves
  build execution outside our CI gates.

## Consequences

- Two credential surfaces — mitigated by OIDC (AWS) and a scoped token in a protected
  GitHub Environment (DO).
- Preview parity is _good but not perfect_ (CloudFront function fallback vs DO
  `catchall_document`; previews default to the hermetic MSW build). Acceptable for review
  purposes; staging remains the fidelity environment.
- The build contract (`dist/` + caching rules) is platform-neutral, so consolidating onto
  either cloud later is a workflow edit, not an application change.
