## What & why

<!-- Link the issue. One paragraph: what changes and why now. -->

## Checklist

- [ ] Tests added/updated (unit; integration if behavior crosses a boundary)
- [ ] Docs touched if contracts changed (`docs/`, `kafka/topics.yaml`, index templates)
- [ ] No secrets / no PII in code, config, or test fixtures

### Streaming changes only (`streaming/**`)

- [ ] **Savepoint compatibility stated:** compatible / requires savepoint migration / new job
- [ ] Operator `uid()`s unchanged (or migration plan described)
- [ ] Exactly-once impact reviewed (sink guarantees, `read_committed` consumers, idempotence keys)

### Schema / topic changes

- [ ] `schemaVersion` bumped & consumers tolerate old + new during rollout
- [ ] Migration is expand → migrate → contract (no breaking step)
