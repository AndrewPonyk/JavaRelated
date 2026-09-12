# Technical Notes — Order Service CI/CD

Actionable guidance for building, testing, deploying and operating this repository.

## 3.1 CI/CD Pipeline Design

Two workflows, one promotion model: **build once, promote the same immutable image by SHA**.

### `ci.yml` — on every PR and every push to `main`

| Stage | Tooling | Gate |
|---|---|---|
| 1. Lint | Checkstyle (bound to Maven `validate`), Helm lint | Fails build on violation |
| 2. Tests + coverage | Surefire (JUnit 5/Mockito) **and** failsafe (Testcontainers ITs, `-Pintegration-tests`); JaCoCo `check` | **Line coverage ≥ 80%** or build fails |
| 3. Static analysis | `mvn sonar:sonar` with `-Dsonar.qualitygate.wait=true` | Sonar quality gate must be green |
| 4. Deployment risk score | `tools/risk-score/risk_score.py` over the PR diff | Advisory on PRs (posted to job summary); *blocking* only at prod promotion |
| 5. Image build & publish (`main` only) | Docker Buildx → ECR, OIDC auth, tag = `<git sha>` | — |
| 6. Image scan | Trivy | Fails on CRITICAL/HIGH (unfixed ignored) |
| 7. Staging deploy | `yq` bumps `values-staging.yaml` `image.tag`, commit `[skip ci]` | ArgoCD auto-syncs staging within ~3 min |

### `cd-production.yml` — manual `workflow_dispatch`

1. **Risk gate** — recomputes the risk score for the promoted range; fails if `score ≥ 0.7` unless
   `override_risk=true` (the override itself is visible in the audit trail).
2. **Human gate** — GitHub *environment* `production` with required reviewers.
3. **Promotion** — bumps `values-production.yaml` to the (already staging-proven) image tag, commits,
   tags `release-<tag>-<date>`. ArgoCD `order-service-production` is *manual-sync*: an operator (or a
   later automation step) presses Sync — deliberate two-key launch for prod.

Design rules that keep this maintainable:

- **CI never talks to the cluster.** All deploys are Git commits; rollback is `git revert` (or
  `argocd app rollback`). The entire release history is `git log deploy/helm`.
- **`[skip ci]` on bot commits** prevents build loops when CI pushes the values bump.
- **Quality gates run on the PR**, not after merge — trunk stays releasable.
- Concurrency groups cancel superseded PR runs; `main` runs are serialized so staging bumps don't race.

## 3.2 Testing Strategy

Pyramid, enforced by build phases:

| Layer | Tooling | Scope | Runs |
|---|---|---|---|
| Domain/unit | JUnit 5 + AssertJ | `Order`, `OrderStatus` state machine, mapper | every `mvn test`, milliseconds |
| Service/unit | Mockito (`@ExtendWith(MockitoExtension)`) | `OrderService` with mocked repository, fixed `Clock` | every `mvn test` |
| Web slice | `@WebMvcTest` + MockMvc + `@MockitoBean` | serialization, validation, ProblemDetail mapping, status codes | every `mvn test` |
| Security slice | `@WebMvcTest` + spring-security-test `jwt()` | 401/403/scope rules of the JWT chain, public paths | every `mvn test` |
| Integration | Testcontainers PostgreSQL 16 + `@ServiceConnection`, failsafe `*IT`; PATCH via httpclient5 | real SQL, Flyway migrations, full HTTP round-trips incl. idempotency replay | `mvn verify -Pintegration-tests` (needs Docker; **runs in CI**) |
| E2E/smoke | `scripts/smoke-test.sh` (curl + jq) | health + create/read against a *deployed* environment | post-deploy, staging & prod |
| Performance | k6 (Phase 3) | p95 budget regression | nightly |

Coverage policy — **≥ 80% line coverage, enforced by JaCoCo `check` in `mvn verify`**, mirrored in the
Sonar gate. Excluded from the metric (and documented in `pom.xml`): the bootstrap class, `config/`
`@Bean` wiring, and DTO records — measuring generated accessors inflates numbers without adding safety.
Entities and the state machine are *included*: that's where the business logic lives.

Testing advice specific to this stack:

- Inject `java.time.Clock` (done) — timestamp assertions become exact, no `Thread.sleep`, no flakes.
- Prefer `@WebMvcTest` slices over `@SpringBootTest` for controllers — 10× faster, no DB needed.
- Testcontainers + `@ServiceConnection` (Boot 3.1+) removes all datasource property plumbing from ITs.
- Compare money with `isEqualByComparingTo` — `BigDecimal.equals` is scale-sensitive (`24.9700 ≠ 24.97`).

## 3.3 Deployment Strategy

**Containerization.** Multi-stage Dockerfile: `maven:3.9-eclipse-temurin-21` builds (with a BuildKit
`.m2` cache mount), `eclipse-temurin:21-jre` runs as UID 10001. Image contains a JRE and the fat jar,
nothing else. Checkstyle is skipped inside the image build (`-Dcheckstyle.skip`) — linting is CI's job.

**Runtime platform.** AWS EKS; one Helm chart per service; environment differences live *only* in
`values-staging.yaml` / `values-production.yaml` (replicas, hosts, HPA bounds, IRSA role, log format).

**Progressive delivery.**
- *Staging:* rolling update on every merge to `main` (auto-sync).
- *Production default:* rolling update with `maxUnavailable: 0` — for a stateless API with graceful
  shutdown this is zero-downtime and operationally boring (matches the project brief).
- *Production, high-risk releases:* set `blueGreen.enabled: true` → chart renders an **Argo Rollout**
  (blue/green: `activeService`/`previewService`, `autoPromotionEnabled: false`). Verify the preview
  stack via the preview Service, then promote (`kubectl argo rollouts promote order-service`) or abort
  with instant traffic-back. The risk score recommends this path automatically at `score ≥ 0.6`.
  ⚠ Switching Deployment ⇄ Rollout replaces the workload object — flip the flag at a release boundary,
  not mid-flight, and install the Argo Rollouts controller first.
- *Database compatibility rule (makes both patterns safe):* every migration must be **expand/contract**
  — new code runs against the old schema and vice versa during the overlap window. Never rename/drop in
  the same release that stops using a column.

**Rollback.** Three layers, fastest first: `argocd app rollback` (previous rendered manifests) →
`git revert` of the values bump (GitOps-clean) → blue-green abort (traffic flip, seconds). Images are
immutable SHAs, so "roll back" never means "rebuild". Step-by-step commands:
[docs/runbooks/rollback.md](runbooks/rollback.md).

## 3.4 Environment Management

Configuration precedence: **baked defaults (`application.yml`) < Spring profile < environment variables
(ConfigMap/Secret) < ExternalSecrets**. Code never branches on environment names; only config differs.

| Environment | Profile | Config source | Secrets |
|---|---|---|---|
| Local | *(default)* | `application.yml` + `.env`/IDE, `docker-compose up postgres` | dev-only literals in compose |
| Staging | `staging` | Helm `values-staging.yaml` → ConfigMap | ExternalSecret → `orders/staging/db` |
| Production | `production` | Helm `values-production.yaml` → ConfigMap | ExternalSecret → `orders/production/db` |

`.env.example` (kept at repo root; copy to `.env`, never commit `.env`):

```dotenv
# ── Order Service (runtime) ─────────────────────────────────────────
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080
MANAGEMENT_SERVER_PORT=8081
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/orders
SPRING_DATASOURCE_USERNAME=orders
SPRING_DATASOURCE_PASSWORD=orders-local-pw
DB_POOL_SIZE=10

# ── docker-compose (local infra) ────────────────────────────────────
POSTGRES_DB=orders
POSTGRES_USER=orders
POSTGRES_PASSWORD=orders-local-pw

# ── CI/CD (GitHub secrets/variables — listed for reference only) ───
# vars.AWS_REGION=eu-central-1
# vars.AWS_ROLE_ARN=arn:aws:iam::<account>:role/github-oidc-order-service
# vars.SONAR_HOST_URL=https://sonar.example.com
# secrets.SONAR_TOKEN=<token>
```

## 3.5 Version Control Workflow

**Trunk-based development.** Short-lived branches (`feat/…`, `fix/…`) → PR → squash-merge to `main`.
`main` is always releasable; every merge deploys to staging automatically; production is an explicit
promotion of a staging-proven SHA.

Why not the alternatives: **Gitflow**'s `develop`/`release` branches solve batch-release problems this
GitOps setup doesn't have and would delay integration; plain **GitHub Flow** is close, but we add the
explicit *promotion* step (environment approval + risk gate) because prod deploys here are
business-visible events. Branch protection on `main`: required checks = build-test + Sonar gate,
1 review, linear history. Hotfix = same flow, just faster review — no separate mechanism to rehearse.

Releases are annotated tags (`release-<sha>-<date>`) created by the promotion workflow, giving an
auditable ledger of what ran in prod and when.

## 3.6 Common Pitfalls (this exact stack)

1. **JPA `open-in-view`** — Boot's default `true` holds connections through view rendering and hides
   lazy-loading bugs. Disabled here; map entities → DTOs inside the transactional service.
2. **N+1 on collections** — listing orders then touching `items` per row. Solved with a summary
   projection for lists and `@EntityGraph` for detail fetch; watch Hibernate's
   `HHH90003004` (in-memory pagination) if you ever join-fetch a collection *with* paging.
3. **`BigDecimal` money** — never `double`; `NUMERIC(19,4)` columns; compare with `compareTo`.
4. **Flyway vs `ddl-auto`** — keep `validate` forever. Checksum drift after editing an applied
   migration will stop startup: never edit applied migrations, always add a new version.
5. **CI commit loops** — the staging bump commit must carry `[skip ci]` *and* the workflow needs
   `paths-ignore`/guard, or Actions and ArgoCD will ping-pong forever.
6. **ArgoCD fighting HPA** — Argo sees `replicas` drift when the HPA scales. The chart omits
   `replicas` when autoscaling is on, and the Application `ignoreDifferences` covers the rest.
7. **`latest` tags** — mutable tags break rollback and Argo diffing. SHA tags only; `latest` never leaves CI.
8. **Graceful shutdown half-configured** — you need *all three*: `server.shutdown: graceful`,
   `preStop` sleep ≥ ALB deregistration delay, `terminationGracePeriodSeconds` > drain time.
   Miss one and rolling deploys throw 502s under load.
9. **JVM vs cgroup memory** — set `MaxRAMPercentage` and container limits together; default heap
   sizing plus a tight limit = OOMKilled pods that "worked locally".
10. **Hikari pool math** — pods × pool size must stay under RDS `max_connections` (HPA max included:
    6 pods × 10 = 60 connections; db.t4g.medium ≈ 400 — fine, but re-check when scaling either side).
11. **Sonar on shallow clones** — blame data needs `fetch-depth: 0` or new-code detection misfires.
12. **Testcontainers on constrained CI** — Ryuk reaper needs Docker socket access; on locked-down
    runners set `TESTCONTAINERS_RYUK_DISABLED=true` and accept manual cleanup.
13. **Helm/Argo drift via hooks** — avoid Helm hooks for migrations under ArgoCD (different lifecycle);
    Flyway-on-startup (current) or an Argo `PreSync` Job are the two sane options.
14. **Blue-green + destructive migrations** — blue and green share one database; expand/contract
    discipline (§3.3) is what makes the instant abort actually safe.
15. **Actuator on a separate management port** — five consumers must agree on 8081: container probes,
    the Service's `management` port, the ServiceMonitor endpoint, the ALB `healthcheck-port`
    annotation, and the NetworkPolicy. External smoke tests can't reach actuator anymore — they poll
    the public `/v3/api-docs` instead (see `scripts/smoke-test.sh`).
16. **DB in readiness, never in liveness** — readiness includes the `db` indicator (stop traffic when
    PostgreSQL is gone, recover automatically); putting it in liveness would restart healthy pods in
    a loop during a database outage.
