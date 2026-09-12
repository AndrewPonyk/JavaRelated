# EHR Integration Platform

A HIPAA-oriented healthcare data-exchange platform connecting hospitals and
clinics. It ingests legacy **HL7v2** feeds, normalizes them to **FHIR R4**
resources served by a **HAPI FHIR** server, streams EHR change events over
**Kafka** (transactional outbox → CQRS projection), and applies **ML entity
linking / risk stratification** over similar clinical notes.

> **Status: Phases 1–2 implemented** — real CRUD, persistence, eventing, HL7
> mapping, ML scoring, frontend, tests, and CI. See
> [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) for exact scope and remaining
> Phase-3 items.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, HAPI FHIR 7.4 (R4), HAPI HL7v2 2.5, Spring Kafka |
| Data | Oracle (runtime) / H2 (tests), Kafka, transactional outbox |
| Frontend | React 18, TypeScript, Vite, TanStack Query |
| ML | Cosine-kNN over clinical-note TF vectors (dependency-free, in-process) |
| Platform | AWS EKS, Terraform, Kustomize/Helm, GitHub Actions |

## Services

| Service | Port(s) | Responsibility |
|---|---|---|
| `fhir-gateway-service` | 8081 | FHIR R4 system-of-record (Patient/Observation/Encounter), outbox, PHI audit |
| `hl7v2-ingestion-service` | 8082, 2575 (MLLP) | Parse HL7v2 → FHIR → submit to gateway |
| `event-streaming-service` | 8083 | Kafka consumer → CQRS projection + DLQ |
| `entity-linking-service` | 8084 | Risk stratification + entity linking over similar notes |
| `frontend` | 5173 (dev) / 8080 (nginx) | Clinician console |

## Prerequisites

- **JDK 21** (Spring Boot 3.3 requires 17+). Point Maven at it via `JAVA_HOME`.
- Maven 3.9+, Node 20+, Docker (for `docker compose` and Oracle/Kafka integration tests).

## Build & test

### Backend
```bash
cd backend
# Ensure Maven uses JDK 21:
export JAVA_HOME=/path/to/jdk-21          # Windows: set to C:\Programs\jdk-21.0.2
mvn verify                                 # compile + run all tests (H2; no Docker needed)
```
- Unit + integration tests run against in-memory **H2** — no external services required.
- Tests tagged `oracle` (real Oracle via Testcontainers) are **excluded by default**;
  run them with `mvn test -Doracle` style profiles + Docker when needed.
- Coverage reports: `backend/<module>/target/site/jacoco/index.html`.

### Frontend
```bash
cd frontend
npm install
npm test            # Vitest (jsdom)
npm run build       # type-check + production build
npm run dev         # dev server at http://localhost:5173 (proxies to backend)
```

## Run the full stack (Docker)

```bash
cp .env.example .env          # dummy values only — never real PHI
docker compose up --build     # Oracle + Kafka + 4 services + frontend
```
> First build compiles each Java service in-image (slow on a cold Maven cache).
> Services run with the **dev** profile (open security) so no identity provider
> is needed locally. The console is at <http://localhost:8080>.

Infra only (then run services from your IDE):
```bash
docker compose up oracle kafka
```

## API quick reference

FHIR (gateway, port 8081):
```bash
# Create
curl -X POST http://localhost:8081/fhir/Patient -H 'Content-Type: application/fhir+json' \
  -d '{"resourceType":"Patient","name":[{"family":"Hopper","given":["Grace"]}],
       "identifier":[{"system":"urn:mrn","value":"MRN-1"}]}'
# Search / read
curl 'http://localhost:8081/fhir/Patient?identifier=urn:mrn|MRN-1'
curl 'http://localhost:8081/fhir/Patient?family=Hopper'
```

REST admin (gateway) + OpenAPI UI at `/swagger-ui.html`:
```bash
curl -X POST http://localhost:8081/api/v1/patients -H 'Content-Type: application/json' \
  -d '{"identifierSystem":"urn:mrn","identifierValue":"REST-1","familyName":"Turing","givenName":"Alan"}'
```

Risk stratification / entity linking (entity-linking, port 8084):
```bash
curl -X POST http://localhost:8084/api/v1/stratification -H 'Content-Type: application/json' \
  -d '{"patientId":"p1","noteText":"chest pain elevated troponin","topK":3}'
```

CQRS projection (event-streaming, port 8083):
```bash
curl http://localhost:8083/api/v1/projections/stats
```

## Documentation

- 📋 [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — structure, phased status, milestones
- 🏛️ [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, components, data flow, security (Mermaid)
- 🔧 [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls

## Security & compliance

PHI is treated as first-class: OAuth2/SMART-on-FHIR authz (prod), TLS + KMS
encryption (infra), append-only PHI audit on every access, secrets via AWS
Secrets Manager, PHI-safe structured logging with a correlation id. The **dev**
profile disables auth for local convenience and must never be used with real PHI.

## License

Proprietary — internal project.
