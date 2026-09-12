## What & why

<!-- Short description of the change and its motivation. Link the issue. -->

## Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor / tech debt
- [ ] CI/CD or infrastructure
- [ ] Documentation

## Checklist

- [ ] Unit tests added/updated; `mvn verify` passes locally (JaCoCo line coverage ≥ 80%)
- [ ] Database migrations are **expand/contract** (backward compatible with the running version)
- [ ] API changes are reflected in the OpenAPI annotations
- [ ] Helm/values changes reviewed for both staging **and** production
- [ ] Risk-score summary reviewed (CI job output); high-risk items called out below

## Deployment notes

<!-- Anything the deployer must know: new env vars/secrets, migration order,
     feature flags, whether this release should use blue-green (risk >= 0.6). -->
