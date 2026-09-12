# Project Plan: Gym Membership API

## 1. Executive Summary & Context
* **Project Name:** Gym Membership API
* **Target Architecture:** Modular Monolith (Layered Hexagonal / Clean Architecture)
* **Tech Stack:** Kotlin 2.1+, Ktor 3.1+, Exposed ORM, PostgreSQL, Koin DI, Ktor JWT Auth, Coroutine Jobs
* **Deployment Target:** AWS ECS (Fargate), AWS RDS PostgreSQL, GitHub Actions CI/CD
* **Core Business Capabilities:**
  * Member lifecycle & registration management.
  * Tiered subscription plans (Basic, Premium, VIP) with granular feature gating.
  * Membership freeze mechanism (max 30 calendar days per rolling calendar year).
  * Tier-based guest pass limits and redemption tracking.
  * Check-in access gate validation (active membership check, facility capacity enforcement, badge-sharing anti-passback rate limiter).
  * Proactive expiry warning webhooks dispatched 7 days prior to subscription renewal.
  * Background cron / coroutine worker for auto-unfreezing and renewal alerts.

---

## 1.1 Project File Structure

```
3-Gym-Membership-API/
├── .github/
│   └── workflows/
│       ├── ci-build-test.yml              # Linting, compile, unit & integration tests
│       └── cd-deploy-ecs.yml              # Docker build, ECR push, ECS task definition update
├── docs/
│   ├── ARCHITECTURE.md                    # System architecture, C4 diagrams, interactions
│   ├── PROJECT-PLAN.md                    # Project roadmap, directory structure, task list
│   └── TECH-NOTES.md                      # Dev guidelines, security, testing & deployment notes
├── frontend/                              # Administrative & Check-in Desk Web UI
│   ├── src/
│   │   ├── components/
│   │   │   ├── MemberCheckIn.tsx          # Real-time badge scan & turnstile check-in UI
│   │   │   └── SubscriptionManager.tsx    # Member freeze & plan upgrade component
│   │   ├── App.tsx
│   │   └── index.tsx
│   └── package.json
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml                 # Version catalog (Ktor, Kotlin, Exposed, Koin, etc.)
├── src/
│   ├── main/
│   │   ├── kotlin/com/gym/
│   │   │   ├── Application.kt             # EngineMain bootstrapper & module declaration
│   │   │   ├── common/                    # Cross-cutting primitives & responses
│   │   │   │   ├── ApiResponse.kt         # Standard JSON response wrapper & paging
│   │   │   │   └── Result.kt              # Functional domain result type
│   │   │   ├── config/                    # Environment & App configuration models
│   │   │   │   └── AppConfig.kt           # Strong-typed Hocon/Yaml configuration
│   │   │   ├── di/                        # Koin Dependency Injection modules
│   │   │   │   ├── DatabaseModule.kt      # HikariCP DataSource & Exposed database setup
│   │   │   │   ├── RepositoryModule.kt    # Repository singleton bindings
│   │   │   │   ├── ServiceModule.kt       # Domain services & rate-limiters
│   │   │   │   └── AppModule.kt           # Composite root module
│   │   │   ├── domain/                    # Core business domain entities & enums
│   │   │   │   ├── Member.kt              # Member entity, contact info, status
│   │   │   │   ├── Plan.kt                # Plan tiers (Basic, Premium, VIP), prices, perks
│   │   │   │   ├── Subscription.kt        # Active subscription, validity period, freeze state
│   │   │   │   ├── CheckIn.kt             # Check-in log, location, gate access decision
│   │   │   │   ├── FreezeRecord.kt        # Freeze start/end dates, cumulative quota
│   │   │   │   └── GuestPass.kt           # Guest pass tracking & usage
│   │   │   ├── jobs/                      # Asynchronous coroutine workers
│   │   │   │   ├── ExpiryWarningJob.kt    # 7-day subscription expiration reminder webhook job
│   │   │   │   └── AutoUnfreezeJob.kt     # Automatic reactivation of expired freezes
│   │   │   ├── plugins/                   # Ktor server plugins & middleware
│   │   │   │   ├── Database.kt            # Exposed DB connection lifecycle
│   │   │   │   ├── HTTP.kt                # CORS, DefaultHeaders
│   │   │   │   ├── Koin.kt                # Koin DI initialization plugin
│   │   │   │   ├── Monitoring.kt          # CallLogging & Prometheus metrics
│   │   │   │   ├── Routing.kt             # Central route registry & OpenAPI/Swagger
│   │   │   │   ├── Security.kt            # JWT Bearer authentication & role claims
│   │   │   │   ├── Serialization.kt       # Kotlinx.serialization JSON config
│   │   │   │   └── StatusPages.kt         # Global domain exception to HTTP mapping
│   │   │   ├── repository/                # Exposed SQL database tables & DAOs
│   │   │   │   ├── Tables.kt              # Exposed Table DSL definitions
│   │   │   │   ├── MemberRepository.kt    # Member persistence interface & Exposed impl
│   │   │   │   ├── SubscriptionRepository.kt # Subscription & Freeze persistence
│   │   │   │   └── CheckInRepository.kt   # Check-in & turnstile logging persistence
│   │   │   ├── routes/                    # HTTP REST controller endpoints
│   │   │   │   ├── AuthRoutes.kt          # /api/v1/auth (Login, Token Refresh)
│   │   │   │   ├── MemberRoutes.kt        # /api/v1/members (Registration, profile)
│   │   │   │   ├── SubscriptionRoutes.kt  # /api/v1/subscriptions (Upgrade, Freeze, Renew)
│   │   │   │   ├── CheckInRoutes.kt       # /api/v1/check-in (Badge scan, Turnstile validate)
│   │   │   │   └── WebhookRoutes.kt       # /api/v1/webhooks (External notification dispatch)
│   │   │   ├── security/                  # Security, Crypto, and Anti-Passback Rate Limiting
│   │   │   │   ├── JwtConfig.kt           # JWT token generation & verification
│   │   │   │   ├── PasswordHasher.kt      # BCrypt password hashing
│   │   │   │   └── AntiPassbackLimiter.kt # Sliding window badge sharing detector
│   │   │   └── service/                   # Pure business logic services
│   │   │       ├── MemberService.kt       # Member registration, profile lifecycle
│   │   │       ├── SubscriptionService.kt # Freeze validation (30-day cap), tier gating
│   │   │       ├── CheckInService.kt      # Turnstile validation (active status, capacity)
│   │   │       ├── WebhookService.kt      # Expiry notice webhook dispatcher
│   │   │       └── CapacityService.kt     # Real-time gym occupancy tracker
│   │   └── resources/
│   │       ├── application.yaml           # App configuration (Ktor, DB, JWT, Webhooks)
│   │       ├── logback.xml                # Structured logging configuration
│   │       └── db/migration/              # Flyway / Exposed schema migrations
│   │           ├── V1__init_schema.sql    # Tables: members, plans, subscriptions, checkins
│   │           └── V2__seed_plans.sql     # Baseline seed data for Basic, Premium, VIP
│   └── test/
│       └── kotlin/com/gym/
│           ├── ApplicationTest.kt         # Health & baseline integration tests
│           ├── routes/
│           │   ├── MemberRoutesTest.kt    # Member CRUD endpoint tests
│           │   └── CheckInRoutesTest.kt   # Badge check-in and capacity tests
│           └── service/
│               ├── SubscriptionServiceTest.kt # Freeze quota & tier feature gating unit tests
│               └── AntiPassbackTest.kt    # Rapid badge reuse rate-limiting tests
├── .editorconfig                          # Code style definition
├── .env.example                           # Sample environment configuration
├── .gitignore                             # Git ignore rules
├── build.gradle.kts                       # Gradle build script (Kotlin DSL)
├── Dockerfile                             # Multi-stage production container build
├── docker-compose.yml                     # Local development environment (API + Postgres)
├── gradlew / gradlew.bat                  # Gradle Wrapper
├── settings.gradle.kts                    # Gradle root configuration
└── gemini-3.7-flash.txt                   # LLM identity artifact
```

---

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)
- [x] **Project Scaffolding**: Gradle 8.14.3 + Ktor 3.1.1 + Version Catalog (`libs.versions.toml`).
- [x] **Base Configuration**: `application.yaml`, `logback.xml`, JSON serialization.
- [ ] **Database & Migrations**: Configure HikariCP connection pool, Exposed SQL tables, and Flyway migration scripts (`V1__init_schema.sql`, `V2__seed_plans.sql`).
- [ ] **Dependency Injection**: Configure Koin modules (`DatabaseModule`, `RepositoryModule`, `ServiceModule`, `AppModule`).
- [ ] **Authentication & Security**: Implement JWT generation/verification, BCrypt password hashing, and role-based route interceptors (`MEMBER`, `TRAINER`, `ADMIN`).
- [ ] **Global Error Handling**: Implement `StatusPages` mapping domain exceptions (`MembershipFrozenException`, `CapacityExceededException`, `BadgeSharingDetectedException`) to standardized JSON responses.

### Phase 2: Core Business Features (Medium Priority)
- [ ] **Member Lifecycle**: Registration, profile lookup, and member status transitions (`ACTIVE`, `FROZEN`, `EXPIRED`, `CANCELLED`).
- [ ] **Tiered Plans & Feature Gating**:
  - `BASIC`: Gym floor access only, 0 guest passes.
  - `PREMIUM`: Gym floor + Pool/Sauna access, 2 guest passes/month.
  - `VIP`: All-access (Classes, PT consultation, Pool), 5 guest passes/month, priority locker.
- [ ] **Membership Freeze Engine**:
  - Validate freeze request duration $\le$ 30 cumulative days in the current calendar year.
  - Record freeze periods and compute adjusted renewal expiration dates.
- [ ] **Check-In Validation Engine**:
  - Verification: subscription is `ACTIVE` and not expired/frozen.
  - Capacity check: ensure current gym occupancy < `MAX_GYM_CAPACITY`.
  - Feature gate check: ensure member's tier permits requested zone (e.g. Pool/Spa).
  - Anti-passback rate limiter: prevent duplicate check-ins on the same badge within 15 minutes.
- [ ] **Guest Pass Management**: Validate and debit remaining monthly guest passes.
- [ ] **Coroutine Background Workers**:
  - `ExpiryWarningJob`: Nightly scan for subscriptions expiring in exactly 7 days; dispatches webhook alerts.
  - `AutoUnfreezeJob`: Automatically reactivates memberships whose scheduled freeze period has concluded.

### Phase 3: Polish, Operations & UI (Lower Priority)
- [ ] **Admin Check-in Frontend Component**: React/TypeScript component for front desk personnel to monitor turnstile badge scans, occupancy meters, and error overrides.
- [ ] **Containerization & CI/CD**:
  - Multi-stage `Dockerfile` with JRE 17/21 runtime.
  - `docker-compose.yml` for instant local developer bootstrap.
  - GitHub Actions CI pipeline (lint, unit tests, integration tests).
  - GitHub Actions CD pipeline (build Docker container, push to AWS ECR, deploy to AWS ECS Fargate).
- [ ] **Monitoring & Observability**: Prometheus metrics endpoint (`/metrics`) and health check (`/health`).
- [ ] **Integration Test Suite**: Comprehensive tests covering badge sharing prevention, freeze limits, and subscription expiration.
