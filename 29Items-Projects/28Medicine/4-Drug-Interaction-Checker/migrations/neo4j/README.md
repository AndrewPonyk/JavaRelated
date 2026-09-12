# Neo4j Migrations

Versioned, **idempotent** Cypher scripts (all use `IF NOT EXISTS` / `MERGE`),
applied in filename order.

| File | Purpose |
| --- | --- |
| `001_constraints_indexes.cypher` | Uniqueness constraints, lookup + full-text indexes |
| `002_seed_reference_data.cypher` | Minimal dev/test seed (NOT for production) |

## Apply

```bash
# via cypher-shell
cypher-shell -a "$NEO4J_URI" -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
  -f 001_constraints_indexes.cypher

# or via the helper script (applies every file in order)
python ../../scripts/seed_neo4j.py            # schema + seed
python ../../scripts/seed_neo4j.py --schema-only
```

## Conventions

- **Idempotent only** — re-running any script must be safe.
- **Schema before data** — constraints/indexes (`001`) precede seeds (`002`).
- **No production data here** — real curated interactions load via a governed
  ETL job with provenance, not from these seed files.
- New changes get the next numeric prefix (`003_...`); never edit an applied file.
