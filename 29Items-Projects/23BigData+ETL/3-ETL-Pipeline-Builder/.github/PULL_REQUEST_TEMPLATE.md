## What & why

<!-- Link the issue. One paragraph: what changes and why now. -->

## Type of change

- [ ] Pipeline / DAG
- [ ] dbt model (attach `dbt build --select state:modified+` output if warehouse-touching)
- [ ] Streaming processor / anomaly model
- [ ] API / frontend
- [ ] Snowflake migration (**forward-only — never edit an applied version**)
- [ ] Infrastructure (attach `terraform plan` summary)

## Checklist

- [ ] Unit tests added/updated; `make lint test` green locally
- [ ] Data contracts: new/changed columns covered by dbt tests or a GE expectation
- [ ] Backwards compatible with events already in Kafka / rows already in RAW
- [ ] Config changes reflected in `.env.example` and docs/TECH-NOTES §3.4
- [ ] No secrets, account ids, or endpoints hardcoded
