# Document Approval Workflow - Project Plan

## Executive Summary
**Document Approval Workflow** is an enterprise-grade, event-driven reactive application designed to streamline, automate, and monitor multi-level document approval lifecycles. Built upon **Eclipse Vert.x (Java 17)**, **MongoDB**, **Quartz Scheduler**, **Python spaCy NLP Microservice**, and a modern **React (TypeScript)** frontend, the system guarantees high-throughput, non-blocking asynchronous event processing, automated NLP-based sentiment and classification routing, and precision SLA tracking with automated escalation.

---

## 1.1 Project File Structure (Code + CI + Tools)

```text
4-Document-Approval-Workflow/
├── .github/
│   └── workflows/
│       ├── ci.yml                          # Continuous Integration (Lint, Test, Build, Scan)
│       └── cd.yml                          # Continuous Deployment (Docker build & deploy)
├── docs/
│   ├── PROJECT-PLAN.md                     # Comprehensive project structure & implementation roadmap
│   ├── ARCHITECTURE.md                    # Deep-dive architecture, reactive topologies & data flows
│   └── TECH-NOTES.md                       # Engineering guidelines, CI/CD, testing, SLA & pitfalls
├── backend/                                # Reactive Vert.x Java 17 Microservice
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/approval/workflow/
│   │   │   │   ├── MainVerticle.java       # System orchestrator & verticle deployer
│   │   │   │   ├── config/
│   │   │   │   │   └── AppConfig.java      # Type-safe configuration loader (Vert.x Config)
│   │   │   │   ├── handlers/
│   │   │   │   │   ├── AuthHandler.java    # JWT & Role-Based Access Control (RBAC) handler
│   │   │   │   │   ├── DocumentHandler.java# REST endpoint request/response handlers
│   │   │   │   │   └── ErrorHandler.java   # Centralized reactive error & failure router
│   │   │   │   ├── models/
│   │   │   │   │   ├── ApprovalStep.java   # Approval node, assignees, actions, comments
│   │   │   │   │   ├── Document.java       # Core aggregate root entity (JSON/BSON mapped)
│   │   │   │   │   ├── DocumentStatus.java # State Machine Enum (DRAFT, IN_REVIEW, APPROVED, etc.)
│   │   │   │   │   ├── NlpAnalysisResult.java # Sentiment score, polarity, entity tags, category
│   │   │   │   │   ├── Role.java           # Security & Routing Roles (CREATOR, MANAGER, LEGAL, etc.)
│   │   │   │   │   └── SlaRecord.java      # SLA deadline, escalation rule, breach flags
│   │   │   │   ├── services/
│   │   │   │   │   ├── DocumentService.java # Business rules & state transition validation
│   │   │   │   │   ├── NlpClientService.java# Reactive WebClient calling Python spaCy service
│   │   │   │   │   └── SlaQuartzService.java# Quartz scheduler integration for timer jobs
│   │   │   │   └── verticles/
│   │   │   │       ├── DatabaseVerticle.java# Reactive MongoDB Client & persistence worker
│   │   │   │       ├── DocumentWorkflowVerticle.java # EventBus consumer for workflow transitions
│   │   │   │       ├── HttpApiVerticle.java# Vert.x Web Router, HTTP REST & WebSocket / SSE server
│   │   │   │       ├── NlpRoutingVerticle.java # EventBus worker delegating text analysis to spaCy
│   │   │   │       └── SlaSchedulerVerticle.java # Quartz job triggers & SLA breach event emitter
│   │   │   └── resources/
│   │   │       ├── application.json        # Base configuration schema & defaults
│   │   │       └── logback.xml             # SLF4J structured logback configuration
│   │   └── test/
│   │       ├── java/com/approval/workflow/
│   │       │   ├── DocumentWorkflowTest.java # JUnit 5 + Vertx-Junit5 reactive test suite
│   │       │   └── SlaBreachIntegrationTest.java # SLA timeout & escalation event test
│   │       └── resources/
│   │           └── test-config.json        # Test-specific environment settings
│   ├── Dockerfile                          # Multi-stage Eclipse Temurin JDK 17 build
│   └── pom.xml                             # Maven build with Vert.x 4.5, MongoDB, Quartz, SLF4J
├── nlp-service/                            # spaCy NLP & Sentiment Microservice (Python)
│   ├── main.py                             # FastAPI service: sentiment analysis & document categorization
│   ├── requirements.txt                    # FastAPI, uvicorn, spacy, spacytextblob
│   └── Dockerfile                          # Python 3.11 slim container with en_core_web_md
├── frontend/                               # React + TypeScript + Vite Web Application
│   ├── public/
│   │   └── favicon.svg
│   ├── src/
│   │   ├── assets/
│   │   ├── components/
│   │   │   ├── ApprovalActionCard.tsx      # Interactive Approve/Reject/Delegate panel
│   │   │   ├── DocumentDashboard.tsx       # Main workflow monitoring & filtering table
│   │   │   ├── DocumentSubmitModal.tsx     # Rich document ingestion modal with real-time feedback
│   │   │   ├── Header.tsx                  # Global navigation, role switcher & notification counter
│   │   │   ├── SentimentBadge.tsx          # Visual indicator for spaCy sentiment & urgency
│   │   │   └── SlaTimerBadge.tsx           # Dynamic countdown badge with breach warning triggers
│   │   ├── hooks/
│   │   │   └── useEventStream.ts           # WebSocket / SSE hook for reactive live document updates
│   │   ├── services/
│   │   │   └── api.ts                      # REST API client with Axios / Fetch abstractions
│   │   ├── styles/
│   │   │   └── index.css                   # Modern CSS system (glassmorphism, dark palette, animations)
│   │   ├── types/
│   │   │   └── document.ts                 # TypeScript interfaces mirroring backend contracts
│   │   ├── App.tsx                         # Core application shell & state orchestration
│   │   └── main.tsx                        # React 18 DOM mount point
│   ├── index.html                          # Entry HTML5 document
│   ├── package.json                        # Node dependencies & build scripts
│   ├── tsconfig.json                       # Strict TypeScript compiler options
│   ├── vite.config.ts                      # Vite build configuration & reverse-proxy rules
│   ├── nginx.conf                          # Production Nginx reverse proxy configuration
│   └── Dockerfile                          # Multi-stage Node.js build to Nginx Alpine
├── mongo-init/
│   └── 01-init-collections.js              # MongoDB validation schemas, compound indexes, seed data
├── docker-compose.yml                      # Local full-stack orchestration (Vert.x, Mongo, spaCy, React)
├── .env.example                            # Comprehensive environment variables template
├── .gitignore                              # Git exclusion rules for Java, Node, Python, IDEs
└── gemini-3.7-flash.txt                    # LLM model stamp
```

---

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)
- [x] **Project Scaffolding & Build Definitions**: Setup Maven `pom.xml` with Java 17, Vert.x 4.5.x, MongoDB Client, Quartz, JUnit 5, and SLF4J/Logback.
- [x] **Core Architecture Documentation**: Formulate `/docs/PROJECT-PLAN.md`, `/docs/ARCHITECTURE.md`, and `/docs/TECH-NOTES.md`.
- [x] **Reactive Vert.x Infrastructure**: Implement `MainVerticle` to deploy HTTP, EventBus, Database, and Scheduler Verticles safely with backpressure and graceful shutdown.
- [x] **Domain Models & DTOs**: Create `Document`, `ApprovalStep`, `DocumentStatus`, `SlaRecord`, `NlpAnalysisResult`, and `Role` models.
- [x] **MongoDB Persistence Layer**: Establish `DatabaseVerticle` with reactive BSON serialization, query handlers, and MongoDB schema validation scripts.
- [x] **Basic REST API Gateway**: Build `HttpApiVerticle` with Vert.x Web Router, request body handlers, and CORS/Security middleware.
- [x] **Python spaCy NLP Microservice**: Implement `nlp-service/main.py` with text classification and sentiment polarity analysis.

### Phase 2: Core Features (Medium Priority)
- [x] **Asynchronous EventBus Interconnect**: Bind document creation, routing, and audit events to Vert.x distributed EventBus addresses (`workflow.document.create`, `workflow.document.route`, `workflow.document.action`).
- [x] **NLP-Driven Intelligent Auto-Routing**: Implement `NlpRoutingVerticle` to query spaCy for document sentiment & category, auto-assigning VIP routes for high-urgency or negative sentiment complaints.
- [x] **Role-Based Multi-Tier Workflow Engine**: Implement `DocumentWorkflowVerticle` with configurable approval matrices (Team Lead -> Dept Head -> Legal/Finance -> Executive).
- [x] **Quartz-Powered SLA Engine**: Build `SlaSchedulerVerticle` and `SlaQuartzService` to schedule cron/duration triggers, calculate SLA deadlines, and emit breach events.
- [x] **Real-Time Live Event Streaming**: Implement SSE / WebSocket bridge in Vert.x Web to push document status changes and SLA countdowns instantly to connected clients.
- [x] **React + TypeScript UI Dashboard**: Build comprehensive dashboard with interactive approval actions, SLA countdown badges, and sentiment visualization.

### Phase 3: Polish, Reliability & Optimization (Lower Priority / Production Readiness)
- [x] **Containerization & Compose Orchestration**: Configure `docker-compose.yml` for unified local execution of MongoDB, Vert.x backend, spaCy service, and React frontend.
- [x] **CI/CD Automation**: Create GitHub Actions pipelines for automated linting, unit/integration testing, Docker multi-arch packaging, and deployment checks.
- [x] **Audit Trail & Event Sourcing History**: Store immutable timeline of all approvals, rejections, comments, and SLA updates in MongoDB.
- [x] **Automated Reactive Testing**: Write JUnit 5 + `VertxExtension` test suites validating asynchronous EventBus routing, DB persistence, and SLA breach handling.
- [x] **Security Hardening**: Configure JWT token verification, RBAC guard rails, input sanitization, and environment secret handling.
