# Database Migrations (Oracle)

This directory holds the **canonical, repo-wide reference DDL** for the platform's
Oracle schema — useful for DBAs, ER review, and onboarding.

At runtime, each service **owns and applies its own slice** via service-local
Flyway migrations (e.g. `backend/fhir-gateway-service/src/main/resources/db/migration`).
The files here are the consolidated source of truth that those service migrations
are derived from. Keep them in sync; never let a service write to another
service's tables (database-per-service ownership).

Conventions:
- Versioned, immutable, forward-only (`V<n>__description.sql`).
- **Backward-compatible** (expand-then-contract) so rolling deploys + rollback are safe.
- Oracle uppercases unquoted identifiers — keep names consistent with JPA mappings.
