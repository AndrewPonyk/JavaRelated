# EHR Integration Platform — Project Plan

> **Mission:** A HIPAA-compliant healthcare data-exchange backbone that connects
> hospitals and clinics. It ingests legacy **HL7v2** feeds, normalizes them to
> **FHIR R4** resources served by a **HAPI FHIR** server, streams EHR change
> events over **Kafka**, and applies **ML entity linking** to power cohort/risk
> stratification across similar clinical notes.

- **Status:** 🟡 Scaffolding / architecture baseline
- **Owners:** Platform Engineering (backend), Clinical Data (mappings), Data Science (ML), SRE (EKS)
- **Compliance scope:** HIPAA, HITECH, SOC 2 Type II (target)

---

## 1.1 Project File Structure

The repository is a **polyrepo-friendly monorepo**: one Git repo, multiple
independently deployable services under `backend/`, a single SPA under
`frontend/`, and all platform/infra-as-code under `infrastructure/`. Each backend
service is its own Maven module so it builds, tests, and containerizes in
isolation while sharing a `common` library.

```text
1-EHR-Integration-Platform/
├── docs/                              # Living documentation
│   ├── PROJECT-PLAN.md               # ← this file
│   ├── ARCHITECTURE.md               # System design + Mermaid diagrams
│   └── TECH-NOTES.md                 # CI/CD, testing, deploy, pitfalls
│
├── backend/                          # Java 21 / Spring Boot 3 (Maven multi-module)
│   ├── pom.xml                       # Parent POM: dependency & plugin management
│   ├── checkstyle.xml                # Shared static-analysis ruleset
│   │
│   ├── common/                       # Shared library (DTOs, errors, security, audit)
│   │   └── src/main/java/com/ehrplatform/common/
│   │       ├── dto/                  # Cross-service transfer objects
│   │       ├── exception/            # Base exceptions + ApiError model
│   │       └── security/             # JWT/OAuth2 + PHI audit helpers
│   │
│   ├── fhir-gateway-service/         # HAPI FHIR R4 server (system of record API)
│   │   ├── src/main/java/com/ehrplatform/fhir/
│   │   │   ├── FhirGatewayApplication.java
│   │   │   ├── config/               # HAPI RestfulServer, interceptors, security
│   │   │   ├── provider/             # IResourceProvider (Patient, Observation…)
│   │   │   ├── controller/           # Spring MVC ops endpoints (non-FHIR admin)
│   │   │   ├── service/              # Business logic
│   │   │   ├── repository/           # Oracle persistence (Spring Data JPA)
│   │   │   ├── domain/               # JPA entities (resource index)
│   │   │   └── exception/            # FHIR OperationOutcome mapping
│   │   ├── src/main/resources/
│   │   │   ├── application.yml
│   │   │   └── db/migration/         # Flyway (Oracle) — service-local schema
│   │   ├── src/test/                 # Unit + slice tests
│   │   └── Dockerfile
│   │
│   ├── hl7v2-ingestion-service/      # MLLP listener + HL7v2→FHIR mapping (HAPI HL7)
│   │   ├── src/main/java/com/ehrplatform/hl7/
│   │   │   ├── Hl7IngestionApplication.java
│   │   │   ├── listener/             # MLLP/TCP inbound channel
│   │   │   ├── parser/               # HAPI HL7v2 PipeParser wrapper
│   │   │   └── mapper/               # ADT/ORU/ORM → FHIR R4 transforms
│   │   └── Dockerfile
│   │
│   ├── event-streaming-service/      # Kafka backbone (produce/consume EHR events)
│   │   ├── src/main/java/com/ehrplatform/events/
│   │   │   ├── EventStreamingApplication.java
│   │   │   ├── config/               # Kafka producer/consumer + Avro/JSON schema
│   │   │   ├── producer/             # Publishes resource lifecycle events
│   │   │   ├── consumer/             # Projections, outbox relay, DLQ handling
│   │   │   └── model/                # Event envelope contracts
│   │   └── Dockerfile
│   │
│   └── entity-linking-service/       # ML entity linking + risk stratification
│       ├── src/main/java/com/ehrplatform/ml/
│       │   ├── EntityLinkingApplication.java
│       │   ├── controller/           # /api/v1/stratification, /api/v1/link
│       │   ├── service/              # Candidate generation, scoring orchestration
│       │   └── client/               # gRPC/REST client to Python model server
│       └── Dockerfile
│
├── frontend/                         # React 18 + TypeScript + Vite SPA (clinician console)
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── .eslintrc.cjs
│   ├── .prettierrc
│   ├── Dockerfile                    # Multi-stage → static assets behind nginx
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── api/                      # Typed FHIR client (fetch wrapper)
│       ├── components/               # PatientList, PatientDetail, …
│       ├── hooks/                    # usePatients (TanStack Query)
│       └── types/                    # FHIR R4 TypeScript models
│
├── db/
│   └── migrations/                   # Canonical Oracle DDL (Flyway, repo-wide reference)
│
├── infrastructure/                   # Infrastructure as Code
│   ├── terraform/                    # AWS: VPC, EKS, MSK, RDS/Oracle, KMS, IAM
│   ├── k8s/                          # Kustomize base + overlays (dev/staging/prod)
│   │   ├── base/
│   │   └── overlays/prod/
│   └── helm/ehr-platform/            # Optional Helm chart for the full stack
│
├── .github/workflows/                # GitHub Actions pipelines
│   ├── ci.yml                        # Lint → test → build → scan → image
│   └── cd.yml                        # Deploy dev → staging → prod (EKS)
│
├── docker-compose.yml                # Local dev: Kafka, Oracle XE, services
├── .env.example                      # Documented environment contract
├── .editorconfig
├── .gitignore
└── README.md
```

### Why this layout

| Decision | Rationale |
|---|---|
| **Monorepo, multi-module Maven** | Atomic cross-service changes (e.g., a shared event contract) land in one PR; CI builds only affected modules. |
| **Service-per-bounded-context** | FHIR API, HL7 ingestion, eventing, and ML scale and fail independently — different load profiles and release cadences. |
| **`common` library** | One source of truth for the event envelope, error model, and PHI-audit hooks; prevents contract drift. |
| **IaC co-located** | EKS/MSK/Oracle topology versioned with the code that runs on it; reviewable in the same PR. |
| **`db/migrations` + service-local Flyway** | Canonical reference DDL lives centrally; each service owns and applies its own schema slice at boot. |

---

## 1.2 Implementation TODO List

### ✅ Phase 0 — Repository baseline (this scaffold)
- [x] Directory structure + module layout
- [x] Architecture & technical documentation
- [x] Stub code for each service, frontend, DB, and CI/CD
- [x] `.env.example`, Dockerfiles, linter configs

### 🟢 Phase 1 — Foundation (high priority) — **DONE**
- [x] **P1-1** Parent POM dependency mgmt: Spring Boot 3.3, HAPI FHIR 7.4, Spring Kafka, ojdbc8 (Oracle 11g), Flyway (HAPI BOM scoped to HAPI modules so Boot's curated versions win)
- [x] **P1-2** HAPI FHIR `RestfulServer` with `PatientResourceProvider` (read/search/create/update/delete)
- [x] **P1-3** Oracle connectivity + Flyway baseline migration (resource indexes, outbox, audit)
- [x] **P1-4** AuthN/Z: OAuth2 resource server (prod profile) + open dev profile; PHI audit on every access
- [x] **P1-5** Local dev environment via `docker-compose` (Oracle XE, Kafka, all services + frontend)
- [x] **P1-6** CI pipeline: lint → unit/integration tests (JaCoCo) → build → scan → image push
- [x] **P1-7** Terraform skeleton: VPC, EKS, KMS, IRSA (modules wired; apply is a deploy-time step)

### 🟢 Phase 2 — Core features (medium priority) — **DONE**
- [x] **P2-1** HL7v2 MLLP listener (`SmartLifecycle` HAPI `HL7Service`) + ACK/NACK; lenient PipeParser
- [x] **P2-2** HL7v2 → FHIR R4 mappers (ADT^A01/A04/A08 → Patient/Encounter; ORU^R01 → Patient/Observation)
- [x] **P2-3** Transactional **outbox** + scheduled relay → Kafka publish of resource lifecycle events
- [x] **P2-4** Event consumer: CQRS projection (idempotent dedupe), `@RetryableTopic` + DLQ
- [x] **P2-5** FHIR providers: Patient, Observation, Encounter (full CRUD + search)
- [x] **P2-6** Entity-linking service: candidate generation + cosine-kNN scorer (local model)
- [x] **P2-7** Risk-stratification endpoint backed by similar notes (cosine kNN regressor)
- [x] **P2-8** Frontend: patient search, detail, registration (validated), and risk view wired to the APIs
- [x] **P2-9** CD pipeline defined (dev → staging → prod on EKS); requires a live cluster to execute

> **Implemented scope notes (honest status):**
> - The ML scorer is a real, dependency-free cosine-kNN over a clinical-note corpus
>   (TF vectors). A remote embedding server is the documented production scale-out.
> - `DocumentReference`/`Condition` providers and Redis read-cache are deferred to Phase 3.
> - Test DBs use in-memory H2 (Oracle-compatible mappings); Oracle runs at runtime/CI
>   via Testcontainers (tests tagged `oracle`, excluded from the default `mvn test`).

### 🟢 Phase 3 — Polish & optimization (lower priority)
- [ ] **P3-1** FHIR Bulk Data ($export) + `_history` versioning
- [ ] **P3-2** Read-model caching (Redis) and FHIR search parameter tuning on Oracle
- [ ] **P3-3** Observability: OpenTelemetry traces, RED/USE dashboards, SLO alerts
- [ ] **P3-4** Chaos/load testing (k6 + Gatling) against FHIR + ingestion paths
- [ ] **P3-5** Schema Registry + Avro for Kafka contracts; backward-compat CI gate
- [ ] **P3-6** ML model registry, drift monitoring, human-in-the-loop link review UI
- [ ] **P3-7** DR runbook, multi-AZ failover game-days, backup/restore verification
- [ ] **P3-8** Full HIPAA control mapping + third-party penetration test

---

## Milestones

| Milestone | Exit criteria |
|---|---|
| **M1 — Walking skeleton** | FHIR `Patient` read/write on Oracle, deployed to dev EKS via CI/CD |
| **M2 — Live ingestion** | HL7v2 ADT feed produces FHIR `Patient`/`Encounter`, events on Kafka |
| **M3 — Intelligence** | Risk-stratification endpoint returns cohorts from similar notes |
| **M4 — Production-ready** | HIPAA controls met, SLOs defined, pen-test passed, DR validated |
