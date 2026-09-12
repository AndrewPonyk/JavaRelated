# Technical Notes — Drug Interaction Checker

Actionable guidance for building, testing, and operating this stack.

---

## 3.1 CI/CD Pipeline Design

Pipelines (GitHub Actions) are path-filtered so backend/frontend/infra changes
build independently.

```
 ┌─────────┐   ┌──────────┐   ┌──────────┐   ┌─────────┐   ┌──────────────┐
 │  Lint   │ → │  Type    │ → │  Test    │ → │  Build  │ → │   Deploy     │
 │ ruff /  │   │  mypy /  │   │ pytest / │   │ docker  │   │ kustomize →  │
 │ eslint  │   │  tsc     │   │ vitest   │   │ → ECR   │   │ EKS (dev→…)  │
 └─────────┘   └──────────┘   └──────────┘   └─────────┘   └──────────────┘
```

- **`backend-ci.yml`** — ruff + black `--check`, mypy, `pytest -m "not
  integration"` with coverage; uploads coverage artifact.
- **`frontend-ci.yml`** — `npm ci`, eslint, `tsc -b`, `vite build`.
- **`deploy.yml`** — on `main` (or manual `workflow_dispatch` with environment
  choice): authenticate to AWS via **OIDC** (no static keys), build & push
  images to **ECR** tagged with the commit SHA, then
  `kustomize build overlays/<env> | kubectl apply -f -` and wait on rollout.
- **Gates:** PRs must pass lint+type+unit; `staging`/`prod` deploys require a
  protected **GitHub Environment** with required reviewers.
- **Promotion:** the *same image digest* validated in `dev` is promoted to
  `staging`→`prod` (immutable artifact promotion, not rebuilds).
- **Integration stage:** spins up Neo4j as a service container, seeds it, and
  runs `pytest -m integration` before allowing deploy.

---

## 3.2 Testing Strategy

| Layer | Tooling | Target |
| --- | --- | --- |
| Backend unit | `pytest`, `pytest-asyncio` | Services & pure logic; fakes for repos/clients. **≥ 80%** on `services/` & `ml/`. |
| Backend integration | `pytest -m integration`, Neo4j service container, `respx` for RxNorm | Repositories (real Cypher), endpoints via `TestClient`. |
| Contract | Schemathesis against OpenAPI (optional) | API never drifts from its schema. |
| Frontend unit | Vitest + React Testing Library | Components, hooks, severity rendering. |
| E2E | Playwright | "enter drugs → check → see severity-coded results". |
| Load | k6 | Latency/throughput SLOs on `/interactions/check`. |

Principles: mock **only at boundaries** (RxNorm HTTP, Neo4j driver, ML HTTP) —
never internal modules; deterministic tests (seeded graph fixtures); test the
**fail-soft** paths explicitly (RxNorm down, ML down).

---

## 3.3 Deployment Strategy

- **Containerization:** multi-stage Dockerfiles; non-root user; slim base
  images; healthcheck. One backend image runs **two workloads** via different
  commands (`app.main:app` and `app.ml_main:app`).
- **Kubernetes (EKS):** Kustomize `base` + `overlays/{dev,staging,prod}`.
  Each workload: Deployment + Service + HPA; liveness/readiness probes on
  `/api/v1/health/live` and `/ready`.
- **Ingress:** AWS Load Balancer Controller (ALB) routes `/api`→backend, `/`→
  frontend; TLS via ACM.
- **Stateful deps:** Neo4j via the official Helm chart / AuraDB; Redis via
  ElastiCache; **secrets** via External Secrets Operator from AWS Secrets
  Manager; pod AWS access via **IRSA**.
- **Releases:** rolling updates by default; adopt **canary/blue-green** (Argo
  Rollouts) in Phase 3. Always keep `kubectl rollout undo` as the fast rollback.
- **IaC:** `infra/terraform` provisions VPC/EKS/ECR/IAM (skeleton provided).

---

## 3.4 Environment Management

- Config via environment variables, parsed by `pydantic-settings` (`Settings`).
  Local dev reads `.env`; in-cluster reads ConfigMap + Secret.
- **Never** commit real secrets. `secrets.example.yaml` and `.env.example` are
  templates only.
- Three environments — `dev`, `staging`, `prod` — differ only by overlay values
  (replicas, image tag, resource limits, external endpoints).

### `.env.example`

```dotenv
# ---- Application ----
ENVIRONMENT=development
DEBUG=true
API_V1_PREFIX=/api/v1

# ---- Neo4j ----
NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=password
NEO4J_DATABASE=neo4j

# ---- RxNorm ----
RXNORM_BASE_URL=https://rxnav.nlm.nih.gov/REST
RXNORM_TIMEOUT_SECONDS=10

# ---- ML severity service ----
ML_SERVICE_URL=http://localhost:8001
ML_ENABLED=true
ML_MODEL_ARTIFACT_URI=s3://dic-models/severity/latest

# ---- Security ----
JWT_SECRET_KEY=change-me-in-prod
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
CORS_ORIGINS=["http://localhost:5173"]

# ---- Frontend (Vite) ----
VITE_API_BASE_URL=/api/v1
```

---

## 3.5 Version Control Workflow

**Trunk-based development with short-lived feature branches.**

- `main` is always deployable; protected (required checks + review).
- Branch `feat/…`, `fix/…`, `chore/…`; open PR early; squash-merge.
- **Conventional Commits** drive changelog/versioning.
- CI on every PR; merge to `main` auto-deploys to `dev`; `staging`/`prod` are
  gated promotions of the validated image.
- *Rationale:* a single deployable trunk minimizes merge debt and matches
  continuous delivery to EKS far better than long-lived Gitflow branches for a
  team of this size.

---

## 3.6 Common Pitfalls (this stack)

- **RxNorm name ambiguity:** a name can map to multiple RxCUIs/TTYs. Normalize
  to **ingredient (IN)** level for interaction logic; prefer RxCUI/NDC input
  from pharmacy systems over free-text names.
- **RxNorm availability/rate limits:** the public RxNav API can be slow or
  throttle. Always set timeouts, **cache** aggressively, and degrade soft.
- **Neo4j undirected dedupe:** `MATCH (a)-[r]-(b)` returns both bindings; filter
  with `a.rxcui < b.rxcui` to emit each pair once (see repositories).
- **Async driver lifecycle:** create the Neo4j driver once at startup, close on
  shutdown, reuse the pool; never open a driver per request.
- **O(N²) pairwise blow-up:** large medication lists explode pair counts —
  cap N, dedupe ingredients, and only invoke ML for pairs lacking curated data.
- **Combination drugs:** branded/combination products must be expanded to all
  active ingredients or interactions are missed.
- **Pydantic v2 + settings:** list/JSON env vars (e.g. `CORS_ORIGINS`) must be
  valid JSON; `lru_cache` the settings accessor.
- **Clinical safety:** never present ML output as established fact — flag
  `ml_predicted` and surface confidence; keep curated sources authoritative.
- **PHI leakage:** scrub patient identifiers from logs/traces; treat the
  pharmacy batch payload as sensitive.
- **EKS auth:** prefer IRSA + OIDC over static credentials everywhere
  (CI → AWS, pods → AWS).
