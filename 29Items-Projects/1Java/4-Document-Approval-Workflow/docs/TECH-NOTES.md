# Document Approval Workflow - Technical Notes

## 3.1 CI/CD Pipeline Design

The Continuous Integration & Continuous Deployment (CI/CD) pipelines are implemented using **GitHub Actions** (`.github/workflows/ci.yml` and `cd.yml`).

```mermaid
flowchart LR
    Commit([Git Push / PR]) --> Lint[1. Lint & Format Check<br/>Checkstyle / ESLint / Flake8]
    Lint --> Test[2. Automated Testing<br/>JUnit5 + Testcontainers + Vitest]
    Test --> Security[3. Security & Quality Scan<br/>SonarQube + Trivy Container Scan]
    Security --> Build[4. Container Build & Push<br/>Docker Multi-Stage -> GitHub Container Registry]
    Build --> DeployDev[5. Deploy to Staging<br/>Automated smoke tests]
    DeployDev --> DeployProd[6. Deploy to Production<br/>Manual approval gate & Canary rollout]
```

### Pipeline Stages
1. **Linting & Code Quality**:
   - Backend: Maven Checkstyle & SpotBugs.
   - Frontend: ESLint + Prettier + `tsc --noEmit`.
   - NLP Service: Flake8 and Black.
2. **Automated Testing & Coverage**:
   - Executes Unit & Integration test suites with code coverage reporting via JaCoCo (Target: > 80% branch coverage).
   - Uses **Testcontainers** for ephemeral MongoDB instances during CI execution.
3. **Security & Vulnerability Scanning**:
   - OWASP Dependency-Check on Java/Node dependencies.
   - Trivy scan on generated OCI container images.
4. **Multi-Stage Container Build**:
   - Builds optimized, non-root Docker images for Vert.x backend, Python NLP service, and Nginx-based React frontend.
5. **Deployment Automation**:
   - Automated CD deployment to Staging environment on merge to `main`.
   - Production deployment triggered via Semantic Version tags (`v*.*.*`) with manual reviewer approval gates.

---

## 3.2 Testing Strategy

```text
               / \
              / E2E \       Playwright / Cypress (End-to-End browser workflow)
             /-------\
            / Integr. \     JUnit 5 + Vertx-Junit5 + Testcontainers (Mongo & spaCy mock)
           /-----------\
          /    Unit     \   JUnit 5 / Mockito (Java) & Vitest / React Testing Library (TS)
         /---------------\
```

### 1. Unit Testing
- **Backend (Java 17)**: JUnit 5 (`@ExtendWith(VertxExtension.class)`) for reactive testing of handlers, services, state transition rules, and SLA calculation logic.
- **Frontend (TypeScript)**: Vitest + React Testing Library to test component rendering, user interactions, SLA countdown displays, and role permission gates.
- **Coverage Target**: Minimum 85% for core business logic (`DocumentService`, `SlaQuartzService`, State Machine transitions).

### 2. Integration Testing
- **Reactive EventBus & Verticle Tests**: Deploying full Vert.x test harnesses using `vertx-junit5` `VertxTestContext`.
- **Database Integration**: Using **Testcontainers MongoDB** to verify reactive BSON serialization, indexes, and aggregation queries without requiring an external database cluster.
- **NLP Client Mocking**: WireMock / MockWebServer to simulate high-latency and error states from the spaCy microservice.

### 3. End-to-End (E2E) Testing
- **Playwright Test Suite**: Automates end-to-end flows: Document creation -> Auto-routing verification -> Multi-level approvals across simulated user personas (Creator -> Legal -> Executive) -> PDF/Audit export.

---

## 3.3 Deployment Strategy

### Containerization Strategy
- All services are containerized using **Docker Multi-Stage Builds**:
  - **Backend**: Eclipse Temurin 17 JRE Alpine base image, running as a non-root `appuser`. JVM options optimized for container memory constraints (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`).
  - **NLP Service**: Python 3.11-slim with pre-downloaded spaCy model (`en_core_web_md`), executed via high-performance `uvicorn` with multiple workers.
  - **Frontend**: Vite static build served via Alpine Nginx with Gzip/Brotli compression, HTTP/2, and security headers (CSP, X-Frame-Options, HSTS).

### Infrastructure Topology
- **Local / On-Premise**: Orchestrated via `docker-compose.yml`.
- **Cloud / Kubernetes**: Deployable as Kubernetes Pods with:
  - Horizontal Pod Autoscaler (HPA) targeting 70% CPU / EventBus queue depth.
  - Liveness (`/health/liveness`) and Readiness (`/health/readiness`) probes connected to Vert.x Health Checks.

---

## 3.4 Environment Management

Configuration is loaded hierarchically through the **Vert.x Config module**:
1. Default embedded JSON (`application.json`).
2. System environment variables (e.g., `MONGODB_URI`, `JWT_SECRET`).
3. Kubernetes ConfigMaps and Secrets mounted as volume files.

### Configuration Reference Template (`.env.example`)
```bash
# ==============================================================================
# Document Approval Workflow - Environment Configuration
# ==============================================================================

# Backend Server Configuration
HTTP_PORT=8080
HTTP_HOST=0.0.0.0
APP_ENV=development
LOG_LEVEL=INFO

# JWT & Security
JWT_SECRET=super-secret-key-change-in-production-min-32-chars-length
JWT_EXPIRATION_SECONDS=86400

# MongoDB Configuration
MONGODB_URI=mongodb://root:example@localhost:27017/document_workflow?authSource=admin
MONGODB_DB_NAME=document_workflow
MONGODB_MAX_POOL_SIZE=50

# spaCy NLP Microservice Configuration
NLP_SERVICE_HOST=localhost
NLP_SERVICE_PORT=8000
NLP_SERVICE_TIMEOUT_MS=3000
NLP_CIRCUIT_BREAKER_MAX_FAILURES=5

# SLA & Quartz Scheduler Configuration
SLA_CHECK_INTERVAL_SECONDS=30
SLA_DEFAULT_TIER1_HOURS=4
SLA_DEFAULT_TIER2_HOURS=24
SLA_AUTO_ESCALATE_ENABLED=true

# Frontend Configuration
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_BASE_URL=ws://localhost:8080/ws
```

---

## 3.5 Version Control & Branching Strategy

We adopt **GitHub Flow / Trunk-Based Development** with short-lived feature branches:
- **`main` Branch**: Production-ready code; protected from direct commits. Requires passing CI checks and at least 1 peer approval.
- **Feature Branches**: `feat/<issue-id>-<short-description>` (e.g., `feat/DOC-102-sla-escalation`).
- **Fix Branches**: `fix/<issue-id>-<short-description>`.
- **Commit Conventions**: **Conventional Commits** (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **Release Tagging**: Automated semantic version tags (`v1.0.0`, `v1.1.0`) triggered upon merging release PRs.

---

## 3.6 Common Pitfalls & How to Avoid Them

### 1. Blocking the Vert.x Event Loop (The "Golden Rule")
- **Pitfall**: Running blocking synchronous operations (e.g., `Thread.sleep()`, synchronous file I/O, heavy Regex, or synchronous JDBC) directly on a Vert.x Event Loop thread freezes the entire application.
- **Remedy**: Always use asynchronous non-blocking APIs (e.g., `vertx.fileSystem()`, Vert.x WebClient, Reactive Mongo Driver). When synchronous legacy code is unavoidable, wrap it in `vertx.executeBlocking()` or deploy it to a dedicated **Worker Verticle**.

### 2. Quartz Clustering & Race Conditions
- **Pitfall**: When scaling the backend horizontally to multiple instances, multiple Quartz schedulers might fire duplicate SLA breach events for the same document.
- **Remedy**: Configure Quartz with a centralized persistent JobStore (or utilize MongoDB distributed lease locks / Vert.x clustered singletons) so only one node executes SLA timeout escalations.

### 3. Asynchronous Unhandled Future Failures
- **Pitfall**: Ignoring `.onFailure()` on a Vert.x `Future` chain results in silent swallowing of exceptions, leaving pending HTTP requests hung indefinitely without a response.
- **Remedy**: Always attach terminating error handlers or propagate failures through Vert.x Router's `ctx.fail(throwable)` so the centralized `ErrorHandler` can render a proper 500/400 JSON response.

### 4. spaCy Model Cold-Start & Memory Consumption
- **Pitfall**: Loading large NLP models (e.g., `en_core_web_trf` or `en_core_web_md`) during runtime request handling causes high latency spikes and memory bloat.
- **Remedy**: Pre-load the spaCy pipeline at FastAPI application startup (`@app.on_event("startup")`) and keep the Python container memory allocation at >= 1.5GB.
