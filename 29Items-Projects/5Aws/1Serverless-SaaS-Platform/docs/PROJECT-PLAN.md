# Serverless SaaS Platform — Project Structure & Plan

## Executive Summary
The **Serverless SaaS Platform** is a multi-tenant B2B Software-as-a-Service system built on a modern serverless paradigm using AWS Lambda, Amazon API Gateway, Amazon DynamoDB, Amazon Cognito, Amazon EventBridge, Amazon SQS, and Amazon SageMaker, deployed via AWS CDK. It incorporates usage-based tiered billing, fine-grained multi-tenant data isolation, geolocation-based access restrictions, and ML-driven capacity and usage prediction.

---

## 1. Project File Structure (Code + CI + Tools)

```
serverless-saas-platform/
├── .github/                                  # Repository workflows & PR checks
│   └── workflows/
│       └── ci.yml                            # GitHub Actions workflow for PR verification
├── docs/                                     # Architecture & Planning Documentation
│   ├── ARCHITECTURE.md                       # Deep architectural documentation & diagrams
│   ├── PROJECT-PLAN.md                       # Project plan, structure & prioritized TODOs
│   └── TECH-NOTES.md                         # Technical guidelines, CI/CD, and best practices
├── infrastructure/                           # AWS CDK Infrastructure as Code (TypeScript)
│   ├── bin/
│   │   └── app.ts                            # CDK App entrypoint instantiating stacks
│   ├── lib/
│   │   ├── api-stack.ts                      # API Gateway, Custom Domains, Authorizers
│   │   ├── auth-stack.ts                     # Cognito User Pools, App Clients, User Groups
│   │   ├── compute-stack.ts                  # Lambda functions, Layers, Concurrency settings
│   │   ├── database-stack.ts                 # DynamoDB single-table definition & GSIs
│   │   ├── eventbridge-stack.ts              # Custom EventBus, Event rules, DLQs
│   │   ├── pipeline-stack.ts                 # AWS CodePipeline & CodeBuild construct
│   │   └── sagemaker-stack.ts                # SageMaker Serverless Inference endpoint
│   ├── cdk.json                              # CDK configuration and feature flags
│   ├── package.json                          # CDK dependencies and scripts
│   └── tsconfig.json                         # TypeScript configuration for CDK
├── backend/                                  # Backend Microservices (Python 3.12)
│   ├── src/
│   │   ├── api/                              # HTTP API Gateways / Lambda Handlers
│   │   │   ├── authorizer.py                 # Multi-tenant Cognito & Geolocation Lambda Authorizer
│   │   │   └── v1/
│   │   │       ├── billing.py                # Billing & invoice preview endpoints
│   │   │       ├── predictions.py            # Capacity prediction query endpoints
│   │   │       ├── tenants.py                # Tenant onboarding & management endpoints
│   │   │       └── usage.py                  # Metered event ingestion endpoints
│   │   ├── core/                             # Cross-cutting foundational modules
│   │   │   ├── config.py                     # Environment and app configuration
│   │   │   ├── exceptions.py                 # Domain and HTTP custom exceptions
│   │   │   ├── logger.py                     # AWS Lambda Powertools structured logging
│   │   │   └── telemetry.py                  # AWS X-Ray tracing & CloudWatch metrics
│   │   ├── db/                               # Data layer & persistence
│   │   │   ├── dynamodb_client.py            # Boto3 client wrapper with retry & exponential backoff
│   │   │   └── single_table.py               # Single-Table Design entity schemas and queries
│   │   ├── events/                           # Event-Driven architecture (EventBridge & SQS)
│   │   │   ├── handlers/
│   │   │   │   ├── threshold_exceeded.py     # Consumer for usage threshold limit alerts
│   │   │   │   ├── tenant_provisioned.py     # Post-provisioning setup & seeding handler
│   │   │   │   └── usage_ingested.py         # Batch usage processor (SQS to DynamoDB)
│   │   │   └── publisher.py                  # EventBridge event dispatch helper
│   │   ├── models/                           # Pydantic schemas for validation & DTOs
│   │   │   ├── billing.py                    # Billing tiers, usage quotas, invoice models
│   │   │   ├── tenant.py                     # Tenant profile, geolocation rules, statuses
│   │   │   └── usage.py                      # Raw usage events, aggregated counters
│   │   ├── sagemaker/                        # Machine Learning integration
│   │   │   ├── inference_client.py           # SageMaker Runtime client for predictions
│   │   │   └── model_trigger.py              # Batch feature extractor for training/inference
│   │   └── services/                         # Business logic services
│   │       ├── billing_service.py            # Tiered calculation and overage billing engine
│   │       ├── prediction_service.py         # Capacity forecasting and anomaly detection
│   │       ├── tenant_service.py             # Tenant lifecycle and isolation enforcement
│   │       └── usage_service.py              # Ingestion, validation, and real-time metering
│   ├── tests/                                # Test Suite
│   │   ├── conftest.py                       # Pytest fixtures and mock AWS clients (moto)
│   │   ├── integration/                      # Integration tests against local/mock services
│   │   │   └── test_api_endpoints.py
│   │   └── unit/                             # Unit tests
│   │       ├── test_authorizer.py
│   │       ├── test_billing_service.py
│   │       ├── test_single_table.py
│   │       └── test_usage_service.py
│   ├── pyproject.toml                        # Ruff, Black, Mypy, and Pytest configuration
│   ├── requirements.txt                      # Production Lambda dependencies
│   └── requirements-dev.txt                  # Dev, linting, and testing dependencies
├── frontend/                                 # Single-Page Web Application (React + TypeScript)
│   ├── src/
│   │   ├── api/                              # REST API client and interceptors
│   │   │   ├── client.ts                     # Axios/Fetch client with Cognito Bearer tokens
│   │   │   ├── tenants.ts                    # Tenant API calls
│   │   │   └── usage.ts                      # Usage & Prediction API calls
│   │   ├── components/                       # Modular UI components
│   │   │   ├── common/
│   │   │   │   ├── ErrorBoundary.tsx         # Global error fallback UI
│   │   │   │   └── LoadingSpinner.tsx        # Accessible loading indicators
│   │   │   ├── dashboard/
│   │   │   │   ├── CapacityPredictionChart.tsx# Chart for predicted vs actual usage
│   │   │   │   └── UsageMetricsCard.tsx      # Real-time usage & quota display card
│   │   │   └── tenants/
│   │   │       ├── TenantDetails.tsx         # Tenant configuration and geo-policy view
│   │   │       └── TenantList.tsx            # B2B admin tenant selector
│   │   ├── hooks/                            # Custom React hooks
│   │   │   ├── useAuth.ts                    # Cognito auth state and tenant claims
│   │   │   └── useUsageMetrics.ts            # Hook for polling/streaming usage metrics
│   │   ├── types/                            # TypeScript interfaces & types
│   │   │   ├── tenant.ts                     # Tenant entity types
│   │   │   └── usage.ts                      # Metrics and prediction data models
│   │   ├── App.tsx                           # Main route switch and tenant provider
│   │   ├── index.css                         # Tailwind/Vanilla CSS styling & design system
│   │   └── main.tsx                          # React DOM initialization
│   ├── index.html                            # HTML5 entrypoint
│   ├── package.json                          # React dependencies and scripts
│   ├── tsconfig.json                         # TypeScript configuration
│   └── vite.config.ts                        # Vite bundler configuration
├── migrations/                               # Database Schemas & Initial State
│   ├── single_table_design.json              # Visual entity chart and access pattern spec
│   └── seed_tenants.py                       # Local seed script for testing tenants
├── pipelines/                                # CI/CD Specs (AWS CodePipeline & CodeBuild)
│   ├── buildspec-backend.yml                 # CodeBuild spec: Python tests & package
│   ├── buildspec-cdk.yml                     # CodeBuild spec: CDK Synth & Deploy
│   └── buildspec-frontend.yml                # CodeBuild spec: React build & S3 sync
├── .env.example                              # Local environment variables blueprint
├── .gitignore                                # Git ignore patterns
└── README.md                                 # High-level developer onboarding guide
```

---

## 2. Implementation TODO List

### Phase 1: Foundation (High Priority)
- [ ] **Infrastructure & Tooling Setup**
  - [ ] Initialize AWS CDK project with TypeScript and baseline stack structure (`infrastructure/`).
  - [ ] Configure `package.json`, `cdk.json`, and AWS accounts/regions across environments.
  - [ ] Define shared parameters, tags, and KMS Customer Managed Keys (CMKs) for encryption.
- [ ] **Security & Identity**
  - [ ] Deploy Amazon Cognito User Pool with custom attributes (`custom:tenant_id`, `custom:role`, `custom:allowed_regions`).
  - [ ] Build and deploy API Gateway HTTP/REST API with Lambda Custom Authorizer (`backend/src/api/authorizer.py`).
  - [ ] Implement Geolocation restriction filter in Authorizer using `CloudFront-Viewer-Country` headers and CIDR blocklists.
- [ ] **Data Layer (Single-Table Design)**
  - [ ] Implement Amazon DynamoDB table with partition key (`PK`) and sort key (`SK`) using on-demand billing.
  - [ ] Create Global Secondary Indexes (GSI1 for Entity Type / Inverted Index, GSI2 for Tenant Date-Range queries).
  - [ ] Write Python database repository wrapper (`single_table.py`) with tenant isolation checks.
- [ ] **CI/CD Foundation**
  - [ ] Create AWS CodePipeline with Source, Build, and CDK Synth stages (`pipelines/buildspec-cdk.yml`).
  - [ ] Set up PR validation checks via GitHub Actions / CodeBuild.

### Phase 2: Core Features (Medium Priority)
- [ ] **Tenant Management Service**
  - [ ] Implement tenant onboarding flow: create tenant record in DynamoDB, provision initial admin, publish `TenantCreated` event.
  - [ ] Implement tenant suspension/activation and quota update endpoints.
- [ ] **High-Throughput Usage Ingestion**
  - [ ] Implement high-performance usage ingestion endpoint (`POST /v1/usage/events`) with API Gateway direct integration to SQS.
  - [ ] Implement consumer Lambda with SQS batch processing and DynamoDB atomic counters (`ADD` operations).
  - [ ] Dispatch threshold alert events to Amazon EventBridge when usage reaches 80% and 100% of tenant limits.
- [ ] **Event-Driven Workflows**
  - [ ] Set up custom EventBridge bus `saas-platform-eventbus`.
  - [ ] Configure rules for `UsageThresholdExceeded` to trigger notifications and automated rate-limiting.
  - [ ] Implement Dead Letter Queues (DLQ) for failed event processing and alerts.
- [ ] **ML-Powered Usage Prediction**
  - [ ] Deploy Amazon SageMaker Serverless Inference Endpoint running time-series forecasting (DeepAR / XGBoost).
  - [ ] Create batch feature extractor Lambda to aggregate 30-day historical usage into S3.
  - [ ] Implement `GET /v1/predictions/capacity` endpoint that queries SageMaker for 7-day tenant capacity forecast.
- [ ] **Frontend B2B Dashboard**
  - [ ] Build React SPA with Vite, TypeScript, and modern component architecture.
  - [ ] Implement Cognito authentication flow (JWT refresh, token storage).
  - [ ] Create Real-Time Usage Metrics card with current billing cycle progress and tier limits.
  - [ ] Implement Capacity Prediction visual chart comparing historical usage vs. SageMaker forecasts.

### Phase 3: Polish & Optimization (Lower Priority)
- [ ] **Advanced Geolocation & WAF Hardening**
  - [ ] Attach AWS WAF to API Gateway and CloudFront with Managed Rule Groups and Geo-Match statements.
  - [ ] Implement dynamic geo-fencing configuration per tenant stored in DynamoDB cache.
- [ ] **Resilience & Cost Optimization**
  - [ ] Configure DynamoDB auto-scaling and evaluate DynamoDB Accelerator (DAX) for read-heavy tenant configs.
  - [ ] Implement Provisioned Concurrency for latency-critical Lambda functions (Authorizer, Ingestion).
  - [ ] Configure S3 lifecycle policies for raw usage event archives (Glacier Instant Retrieval after 90 days).
- [ ] **Observability & Operational Excellence**
  - [ ] Integrate AWS Lambda Powertools for Python (Structured Logger, Tracer with X-Ray, Metrics).
  - [ ] Create CloudWatch Dashboard for Tenant Usage, API latencies (p95, p99), 4xx/5xx rates, and DLQ depths.
  - [ ] Configure PagerDuty/SNS alerts for DLQ message drops and SageMaker endpoint invocation errors.
