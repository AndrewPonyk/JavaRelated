# Database

The ordered migrations create and optimize the metadata control-plane schema:

- `editor_users` for an anonymous signed-session subject.
- `editor_projects` owned by a user.
- `edit_presets` containing a validated JSON edit recipe and optional owner-visible project.

Migration 001 installs `pgcrypto`, UUID defaults, update timestamps, foreign keys, row-level security policies, and `FORCE ROW LEVEL SECURITY`. Migration 002 adds indexes matching owner-scoped pagination order. The application opens each project or preset transaction with `SET LOCAL app.user_id`, so PostgreSQL enforces the same ownership rule as the service layer.

## Applying migrations manually

Use a privileged migration identity, not the web application's least-privileged runtime identity:

```bash
psql "$MIGRATION_DATABASE_URL" -v ON_ERROR_STOP=1 -f database/migrations/001_create_presets.sql
psql "$MIGRATION_DATABASE_URL" -v ON_ERROR_STOP=1 -f database/migrations/002_add_pagination_indexes.sql
```

Production uses separate identities:

- A migration identity owns the schema and applies ordered SQL files.
- A `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOINHERIT` runtime identity receives only connect/schema usage, table CRUD, sequence usage, and function execution.

`database/docker-init/002_create_runtime_role.sh` creates and grants that runtime identity for a fresh Compose volume using environment-supplied credentials. Browsers and Vite never receive either database credential. PostgreSQL initialization files run only when the named volume is first created.

Migrations are append-only. Apply a new migration once, record it with the deployment, and keep it compatible with the preceding API version for the duration of a rollback window.
