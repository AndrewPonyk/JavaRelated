# Database Migrations

**EF Core migrations are the source of truth.** The `V00x__*.sql` files here document the
schema and support SQL-based tooling/reviews; they are kept in step with the EF model in
`src/backend/src/InvoiceFactoring.Infrastructure/Persistence`.

## Generate / apply with EF Core

```bash
cd src/backend

# Create a migration after changing the model
dotnet ef migrations add <Name> \
  -p src/InvoiceFactoring.Infrastructure \
  -s src/InvoiceFactoring.Api \
  -o Persistence/Migrations

# Apply to the configured database
dotnet ef database update \
  -p src/InvoiceFactoring.Infrastructure \
  -s src/InvoiceFactoring.Api

# Produce an idempotent SQL bundle for CI/CD (run as a pre-deploy Job in AKS)
dotnet ef migrations bundle \
  -p src/InvoiceFactoring.Infrastructure \
  -s src/InvoiceFactoring.Api \
  --self-contained -o efbundle
```

## Zero-downtime rule

Use **expand → migrate → contract**: additive changes first (new nullable columns/tables),
deploy code that writes both old+new, backfill, then drop the old shape in a later release.
Never make a destructive change in the same release that deploys the code depending on it
(TECH-NOTES §3.3).
