1) Java
   Big Project 1 — Global Lending & Risk Platform
   Tech Stack: Java 21, Spring Boot 3, Jakarta EE 10 (JAX‑RS), Hibernate ORM, Gradle, JUnit 5, Quarkus (edge), Vert.x (event bus), GraalVM Native Image (batch), SLF4J/Logback, Kafka, Postgres, Redis, Keycloak, React 18 + TypeScript, Next.js, Tailwind CSS
   Short Tech Description: Microservices platform for loan origination, servicing, and risk analytics. Designed for high throughput, regulatory audits, and regional compliance.
   Business Logic: Implements KYC/AML, pricing, underwriting, and credit policy rules. Manages schedules, collections, delinquency workflows, and audit trails.

   Big Project 2 — IoT Telematics & Fleet Operations
   Tech Stack: Java 21, Quarkus (native APIs), Vert.x, MQTT, Kafka, TimescaleDB, ClickHouse, Redis, OpenAPI, SLF4J, Micrometer/Prometheus, React + Next.js + Mapbox GL, Tailwind CSS
   Short Tech Description: Real‑time ingestion and analytics for vehicle telemetry, geofencing, and driver safety. Low‑latency APIs provide live dashboards and alerts.
   Business Logic: Rules compute ETA, route optimizations, and anomaly detection. Billing and SLA metrics computed per fleet, vehicle, and route.
   
    Medium Project 1 — HR & Payroll Self‑Service Portal
   Tech Stack: Java 21, Spring Boot 3, Spring Data JPA (Hibernate), Maven, JUnit 5, Liquibase, PostgreSQL, Redis, Thymeleaf + Alpine.js, SLF4J/Logback
   Short Tech Description: Modular monolith with clean boundaries for HR, payroll, and benefits. Offers secure employee self‑service and manager approvals.
   Business Logic: Handles payroll cycles, tax bands, and accrual policies. Implements approval chains, role‑based access, and audit logs.
   
    Medium Project 2 — Document Management & E‑Sign
   Tech Stack: Java 17, Jakarta EE 10, JAX‑RS, Hibernate, Gradle, JUnit 5, MinIO/S3, PostgreSQL, Keycloak, Vue 3 + Vite, Tailwind CSS
   Short Tech Description: Secure repository with versioning, OCR metadata, and e‑signature flows. REST APIs integrate with ERP and CRM systems.
   Business Logic: Manages templates, signers, routing, and expirations. Tracks legal evidence, timestamps, and compliance retention.
2) Multithreading
   Big Project 1 — Low‑Latency Trading Backtest & Live Engine
   Tech Stack: Java 21, LMAX Disruptor, Agrona, Chronicle Queue/Map, Aeron, Netty, JMH, JFR, SLF4J, React + TypeScript dashboards
   Short Tech Description: Lock‑free queues and off‑heap data structures drive microsecond latencies. Provides backtest, paper, and live trading modes.
   Business Logic: Strategy scheduling, portfolio risk, and order routing with throttling. Reconciliation, PnL, and compliance reporting included.
   
    Big Project 2 — Real‑Time Video Analytics Pipeline
   Tech Stack: Java 21, Project Loom/Virtual Threads, OpenCV, FFmpeg, gRPC, Kafka, Redis, ClickHouse, Micrometer/Prometheus, Next.js + WebRTC
   Short Tech Description: Concurrent frame pipelines for detection, tracking, and alerts. Scales across nodes with backpressure and adaptive batching.
   Business Logic: Zone‑based rules, incident tickets, and SLA metrics. Exports evidence clips and audit trails to stakeholders.
   Medium Project 1 — Distributed Web Crawler & Indexer
   Tech Stack: Java 17, ForkJoinPool, CompletableFuture, Vert.x HTTP client, Kafka, Elasticsearch, Redis, JUnit 5
   Short Tech Description: Highly concurrent crawler with politeness and deduplication. Supports content parsing, metadata, and language detection.
   Business Logic: Per‑domain rate limits, priority queues, and topic categorization. Exposes search APIs and freshness metrics.
   Medium Project 2 — Priority Job Scheduler
   Tech Stack: Java 17, ScheduledThreadPoolExecutor, Quartz, PostgreSQL, Redis, Spring Boot, SLF4J
   Short Tech Description: Multi‑tenant job orchestration with priorities, retries, and SLAs. Provides dashboards for job state and throughput.
   Business Logic: Enforces quotas, backoff policies, and dependencies. Bills tenants based on runtime, priority, and reserved capacity.
3) Python
   Big Project 1 — EMR & Appointments Platform
   Tech Stack: Python 3.11, Django + DRF, Celery + Redis, PostgreSQL, S3, OpenAPI, pytest, React + TypeScript + Next.js, Tailwind CSS
   Short Tech Description: HIPAA‑aware EMR with scheduling, messaging, and e‑prescriptions. Async tasks handle lab integrations and batch reports.
   Business Logic: Patient onboarding, eligibility checks, and prior auth. Role‑based workflows for providers, billing, and compliance.
   Big Project 2 — MLOps Experiment & Model Registry
   Tech Stack: Python 3.11, FastAPI, MLflow, Ray, DVC, Feast (feature store), S3, PostgreSQL, Grafana/Prometheus, React + TypeScript
   Short Tech Description: End‑to‑end experiment tracking, feature management, and model lifecycle. Serves models with A/B and canary rollout.
   Business Logic: Approval gates, lineage, and auditability. Cost tracking per project, environment, and dataset.
   Medium Project 1 — Data Catalog & Quality Monitor
   Tech Stack: Python 3.11, FastAPI, Great Expectations, Airflow, PostgreSQL, S3, pytest, Vue 3
   Short Tech Description: Catalog with schema lineage and validation rules. Alerts on drift, null spikes, and contract breaks.
   Business Logic: Enforces SLAs, ownership, and escalation policies. Provides scorecards and automated remediation suggestions.
   Medium Project 2 — Real‑Time Chat Translation Service
   Tech Stack: Python 3.11, FastAPI, WebSockets, Redis Pub/Sub, HuggingFace Transformers, SentencePiece, React + Next.js
   Short Tech Description: Concurrent chat with live translation across languages. Pluggable models and domain glossaries.
   Business Logic: Tenant isolation, usage quotas, and billing. Moderation filters and PII redaction pipelines.
4) Machine Learning
   Big Project 1 — Personalized Recommender Platform
   Tech Stack: Python 3.11, PyTorch, LightGBM, Faiss, Feast, Kafka, Spark, Airflow, FastAPI inference, Redis, PostgreSQL, React + Next.js
   Short Tech Description: Hybrid retrieval‑ranking with real‑time features and feedback loops. Supports streaming updates and batch retrains.
   Business Logic: User segmentation, constraints (diversity/novelty), and campaigns. Revenue attribution, uplift analysis, and A/B experiments.
   Big Project 2 — Document Intelligence & RAG
   Tech Stack: Python 3.11, Transformers (HF), sentence‑transformers, LangChain, Elasticsearch/OpenSearch, FastAPI, Celery, S3, React + TypeScript
   Short Tech Description: OCR, NER, and RAG for contracts and invoices. Domain adapters and guardrails for accuracy and safety.
   Business Logic: Workflow routing, confidence thresholds, and human‑in‑the‑loop. Compliance packs with redaction and retention rules.
   Medium Project 1 — Demand Forecasting Workbench
   Tech Stack: Python 3.11, Prophet, XGBoost, scikit‑learn, Darts, MLflow, Streamlit, PostgreSQL
   Short Tech Description: Multi‑hierarchy forecasts with holidays and promo effects. Scenario planning and error decomposition.
   Business Logic: Inventory policies, reorder points, and vendor lead times. Allocate budgets by SKU, channel, and region.
   Medium Project 2 — Transaction Fraud Detection
   Tech Stack: Python 3.11, PyTorch TabNet, CatBoost, Kafka, Feast, FastAPI, Redis, Grafana, React
   Short Tech Description: Online scoring with feature store and sliding windows. Explains decisions with SHAP and counterfactuals.
   Business Logic: Risk tiers, step‑up authentication, and case management. Auto‑disputes, recovery, and regulatory reporting.
5) AWS (EKS is MUST)
   Big Project 1 — Multi‑Tenant Analytics SaaS on EKS
   Tech Stack: AWS EKS (K8s), EC2, S3, EMR/Spark, Athena, Glue, Redshift Serverless, MSK, Lambda, API Gateway, RDS Postgres, DynamoDB, CloudWatch, IAM, VPC, Terraform, ArgoCD; Backend: Java Spring Boot; Frontend: Next.js + TypeScript
   Short Tech Description: Data lakehouse ingestion, transform, and interactive analytics. Multi‑tenant isolation with IaC and GitOps.
   Business Logic: Usage metering, tiered plans, and quotas. Row/column security, PII masking, and per‑tenant retention.
   Big Project 2 — Real‑Time Events & ML Serving on EKS
   Tech Stack: AWS EKS, MSK/Kinesis, Lambda, DynamoDB, ElastiCache, OpenSearch, S3, SageMaker (training/inference), CloudWatch, WAF, CDK/Terraform; Backend: Quarkus/Java; Frontend: React + TypeScript
   Short Tech Description: Stream processing with exactly‑once semantics and ML scoring endpoints. Autoscaling with HPA and spot integration.
   Business Logic: Rule engines for alerts, enrichment, and routing. AB tests and gradual rollouts with feature flags.
   Medium Project 1 — Serverless Data Lake Ingestion
   Tech Stack: S3, Lambda (Python), Step Functions, Glue, Athena, EventBridge, DynamoDB, CloudWatch, IAM; UI: Vue 3
   Short Tech Description: Event‑driven ingestion, schema evolution, and partitioning. Operational dashboards and DLQ handling.
   Business Logic: Data contracts, producer SLAs, and lineage. Cost controls via compaction and tiering policies.
   Medium Project 2 — Bedrock‑Powered Support Chatbot with RAG
   Tech Stack: Amazon Bedrock, OpenSearch Serverless, S3, Lambda (Python), API Gateway, Cognito, CloudFront; Frontend: Next.js
   Short Tech Description: Secure enterprise chatbot with retrieval‑augmented generation. Sources content from S3 and indexed KBs.
   Business Logic: Tenant‑aware access, redaction, and safety filters. Conversation analytics, feedback, and continuous learning.
6) DB (MySQL, PostgreSQL, Oracle, SQL Server, MongoDB, Redis, Cassandra, Elasticsearch, Snowflake, Neo4j, Pinecone, Weaviate, Milvus)
   Big Project 1 — Multi‑Store HTAP Data Platform
   Tech Stack: Java Spring Boot, Kafka, Debezium CDC, PostgreSQL, MySQL, Cassandra, Redis, Elasticsearch, Trino/Presto, Airflow, Docker/K8s, Grafana/Prometheus; Frontend: Next.js + TypeScript
   Short Tech Description: Unified ingestion and query layer across OLTP and OLAP stores with CDC. Supports low‑latency reads, analytical queries, and cross‑store federation.
   Business Logic: Multi‑tenant schemas, data contracts, and retention tiers. Row/column security, audit trails, and cost governance per workload.
   Big Project 2 — Enterprise Knowledge Graph + Vector Search
   Tech Stack: Python FastAPI, Neo4j/Neptune, OpenSearch/Elasticsearch, Weaviate/Pinecone/Milvus, Kafka, Airflow, HuggingFace embeddings, S3; Frontend: React + TypeScript + Cytoscape.js
   Short Tech Description: Builds a knowledge graph with semantic vectors for hybrid retrieval. Ingestion pipelines perform entity resolution and relationship extraction.
   Business Logic: Access control by entity type and lineage tracking. Relevance ranking, feedback loops, and compliance export (DSAR).
   Medium Project 1 — Heterogeneous DB Migration Factory
   Tech Stack: Python 3.11, Airflow, ora2pg, AWS DMS, Liquibase, PostgreSQL, SQL Server, S3; UI: Vue 3 + Vite
   Short Tech Description: Automated assessment, schema conversion, and phased cutover. Validates data parity with sampling and checksums.
   Business Logic: Change windows, rollback plans, and approvals. SLA dashboards for downtime, lag, and defect rates.
   Medium Project 2 — Real‑Time Leaderboards & Telemetry
   Tech Stack: Node.js (NestJS), Redis (Sorted Sets/Streams), PostgreSQL, WebSockets, NATS, Grafana; Frontend: Next.js + Tailwind
   Short Tech Description: Sub‑millisecond leaderboard updates and live metrics. Durable snapshots to Postgres and replay from streams.
   Business Logic: Seasonal resets, anti‑cheat heuristics, and moderation hooks. Tiered rewards and event‑based campaigns.
7) Algorithms (Sorting, Searching, Graph, DP, Greedy, Divide‑and‑Conquer, Backtracking, String, Computational Geometry)
   Big Project 1 — Competitive Programming & Judge Platform
   Tech Stack: Java Spring Boot, Python workers, gVisor/Docker sandboxes, RabbitMQ, PostgreSQL, MinIO, JUnit/pytest, K8s; Frontend: React + TypeScript
   Short Tech Description: Distributed judge executes submissions with time/memory limits. Rich problem sets with tags, hints, and editorials.
   Business Logic: Contest scheduling, rankings with penalties, and plagiarism checks. Author workflows for test generation and review.
   Big Project 2 — Logistics Routing & Optimization Suite
   Tech Stack: Python (OR‑Tools, NetworkX), Java microservices, Kafka, PostgreSQL, Redis, GeoJSON, Mapbox, K8s; Frontend: Next.js + Mapbox GL
   Short Tech Description: Solves VRP, TSP, and multi‑depot routing with constraints. Live re‑optimization on traffic and SLA breaches.
   Business Logic: Driver/vehicle constraints, time windows, and capacity rules. Cost models for fuel, tolls, overtime, and penalties.
   Medium Project 1 — String Algorithms Toolkit
   Tech Stack: Node.js + TypeScript (library), WebAssembly (hot paths), Jest, Docusaurus site; Demo UI: React
   Short Tech Description: Implements KMP, Z‑algo, suffix array/tree, Aho‑Corasick. Visualizations and benchmarks for comparisons.
   Business Logic: Commercial license with tiered features. Exportable reports on algorithm performance across datasets.
   Medium Project 2 — Portfolio Optimizer (DP/Greedy)
   Tech Stack: Python (NumPy, pandas, CVXPY), FastAPI, PostgreSQL; Frontend: Streamlit/React
   Short Tech Description: Asset selection using DP constraints and greedy heuristics. Scenario testing and risk decomposition.
   Business Logic: Compliance constraints (ESG/sector caps). Rebalancing policies and what‑if backtests.
8) Javascript (ES6+, Next.js, TypeScript, Node.js, React, Vue, Angular, Express, Jest, Webpack, Babel, Vite, Svelte)
   Big Project 1 — Headless Commerce & CMS Platform
   Tech Stack: Node.js (NestJS), Next.js 14 (App Router), TypeScript, GraphQL (Apollo), PostgreSQL/Prisma, Redis, Elasticsearch, Stripe, Storybook, Turborepo, Jest/Playwright, K8s
   Short Tech Description: Modular headless backend with commerce, CMS, and search. Microfrontends enable brand sites with shared design system.
   Business Logic: Catalog, pricing, promotions, and inventory. Subscriptions, tax/VAT, multi‑currency, and content workflows.
   Big Project 2 — Real‑Time Collaboration Suite (Docs/Whiteboard)
   Tech Stack: Node.js, WebRTC, WebSockets, CRDTs (Automerge/Yjs), Redis, Kafka, S3, OpenAPI; Frontend: React + TypeScript + Canvas/WebGL
   Short Tech Description: Low‑latency collaborative editing with offline‑first CRDT sync. Media sharing, comments, and presence indicators.
   Business Logic: Team workspaces, access roles, and audit. Usage metering, billing, and enterprise SSO.
   Medium Project 1 — SSR E‑Commerce Starter
   Tech Stack: Next.js + TypeScript, Tailwind CSS, Prisma + PostgreSQL, Stripe, Cypress/Jest
   Short Tech Description: High‑performance SSR/ISR storefront with checkout and account. SEO‑ready with image optimization and analytics.
   Business Logic: Coupons, carts, and order management. Admin dashboard for products, stock, and refunds.
   Medium Project 2 — Analytics Dashboard with Microfrontends
   Tech Stack: Module Federation, React + Next.js, Webpack 5/Vite, Node.js BFF, D3/ECharts, Auth0
   Short Tech Description: Pluggable widgets loaded at runtime, independent deploys. BFF composes data from multiple services.
   Business Logic: Role‑based dashboards and tenant theming. Quotas, exports, and scheduled reports.
9) Web Design (HTML5, Bulma, Sass, Bootstrap, Tailwind, Figma, Adobe XD, Sketch, Webflow)
   Big Project 1 — Design System & Codegen Pipeline
   Tech Stack: Figma Tokens, Style Dictionary, Tailwind CSS, React/Vue component libraries, Storybook, Chromatic, Playwright, NX/Turborepo
   Short Tech Description: Synchronizes design tokens from Figma to code. Generates accessible, themed components with visual regression tests.
   Business Logic: Multi‑brand governance, versioning, and approvals. Adoption metrics, deprecation policies, and SLAs.
   Big Project 2 — No‑Code Landing Page Builder
   Tech Stack: SvelteKit/Next.js, GrapesJS, Tailwind, Node.js, PostgreSQL, Cloudflare Workers, Image CDN
   Short Tech Description: Drag‑and‑drop sections with responsive presets and A/B testing. Exports static or SSR sites with analytics.
   Business Logic: Templates marketplace and team collaboration. Plan tiers for traffic, domains, and integrations.
   Medium Project 1 — Accessibility Remediation Toolkit
   Tech Stack: Node.js CLI, Axe‑core/Pa11y, Lighthouse CI, GitHub Actions; Browser Extension: React + MV3
   Short Tech Description: Scans sites, creates fix tickets with code snippets. Tracks WCAG compliance trends over time.
   Business Logic: Service SLAs, prioritized backlogs, and training modules. Compliance reports for audits and attestations.
   Medium Project 2 — Multi‑Brand Theming Portal
   Tech Stack: React + Tailwind + CSS Variables, Next.js, Theme Editor (Monaco), Storybook
   Short Tech Description: Live theme editing with token propagation and previews. Exports consumable packages for apps.
   Business Logic: Role‑based approvals, release channels, and change logs. License tiers by number of brands and seats.
10) Security (Metasploit, Kali, Burp, nmap, Wireshark, OWASP ZAP, Cryptography, Malware Analysis, Web App Security)
    Big Project 1 — Managed Bug Bounty & VDP Platform
    Tech Stack: Python Django + Celery, PostgreSQL, Redis, S3, nmap/ZAP/Burp integrations, OIDC/Keycloak, K8s; Frontend: React + TypeScript
    Short Tech Description: Coordinates submissions, triage, and remediation workflows. Automated scans augment manual researcher findings.
    Business Logic: Bounty budgeting, SLAs, and severity matrices. Audit trails, exportable advisories, and vendor coordination.
    Big Project 2 — Zero‑Trust Access Proxy & Policy Engine
    Tech Stack: Go (data plane), Java Spring Boot (control plane), Envoy, OPA, mTLS, OAuth2/OIDC, Redis, Postgres, Prometheus; UI: Next.js
    Short Tech Description: Per‑request identity, device posture, and context policies. Sidecar/proxy model secures internal apps without VPN.
    Business Logic: Policy catalogs, exceptions with approvals, and just‑in‑time access. Session recording, anomaly alerts, and compliance packs.
    Medium Project 1 — Secrets & Key Management Gateway
    Tech Stack: Java Quarkus, HashiCorp Vault/KMS integrations, gRPC/REST, PostgreSQL, Prometheus; UI: Vue 3
    Short Tech Description: Uniform API for secrets, encryption, and signing. Rotation and lease management with audit logging.
    Business Logic: Tenant isolation, quotas, and usage billing. Break‑glass procedures and attestation reports.
    Medium Project 2 — Threat Modeling & Risk Register SaaS
    Tech Stack: Node.js (NestJS), GraphQL, Neo4j, PostgreSQL, S3, Auth0, Mermaid.js; Frontend: React + TypeScript
    Short Tech Description: Model systems, data flows, and STRIDE/LINDDUN risks. Links controls, tests, and evidence to risks.
    Business Logic: Review workflows, exception tracking, and dashboards. Exports for auditors and regulatory mappings.
11) DevOps (Jenkins, GitHub Actions, Docker, K8s, Ansible, Terraform, Prometheus, Grafana, ELK)
    Big Project 1 — Multi‑Cloud GitOps Platform
    Tech Stack: Terraform + Terragrunt, ArgoCD, Helm, Kubernetes (EKS/AKS/GKE), Docker, GitHub Actions/Jenkins, Ansible, Prometheus + Grafana, ELK/OpenSearch, Vault, SSO (OIDC); Frontend: Next.js + TypeScript Ops Portal
    Short Tech Description: End‑to‑end GitOps delivery across multi‑cloud with environment drift detection and policy enforcement. Self‑service templates bootstrap apps with observability, secrets, and CI/CD out of the box.
    Business Logic: Project onboarding, cost center tagging, and approval workflows. Policy as code gates (security, cost, compliance) and automated drift remediation with audit trails.
    Big Project 2 — Internal Developer Platform (IDP) with Backstage
    Tech Stack: Backstage, Kubernetes, Crossplane, Terraform, Jenkins/GitHub Actions, Argo Rollouts, Harbor/Artifactory, Prometheus, Grafana, Loki, Tempo, Sentry; Frontend: Backstage plugins (React + TypeScript)
    Short Tech Description: Developer portal standardizing golden paths, scorecards, and catalog. One‑click environments, ephemeral previews, and progressive delivery with canary/blue‑green.
    Business Logic: SLO/SLA definitions, error budgets, and escalation policies. Chargeback/showback reports per team, app, and environment.
    Medium Project 1 — Progressive Delivery Toolkit
    Tech Stack: Argo Rollouts/Flagger, Istio/Linkerd, Kubernetes, GitHub Actions, Terraform modules, Prometheus/Grafana; UI: Vue 3 status dashboard
    Short Tech Description: Canary strategies, feature flags, and automated rollback. Integrates metrics and error rates for rollout decisions.
    Business Logic: Risk scoring per release and approval gates. Release calendars, change freeze windows, and compliance exports.
    Medium Project 2 — FinOps & Capacity Planner
    Tech Stack: Kubernetes metrics + Prometheus, Grafana dashboards, Infracost, Terraform, Ansible, BigQuery/Athena; UI: React + TypeScript
    Short Tech Description: Centralized cost and capacity visibility with right‑sizing recommendations. Forecasting and anomaly detection on usage and spend.
    Business Logic: Budgets, alerts, and savings goals per team. Governance reports for reserved/spot utilization and waste elimination.
12) C# (.NET 8, ASP.NET Core, Blazor, EF, LINQ, MAUI, WPF, NUnit)
    Big Project 1 — Modular ERP & Finance Suite
    Tech Stack: .NET 8 ASP.NET Core microservices, EF Core, Dapper, MassTransit + RabbitMQ, SQL Server, Redis, Elasticsearch, OIDC, Kubernetes, Serilog; Frontend: Blazor WebAssembly + Tailwind; Desktop: WPF for back‑office tools
    Short Tech Description: Domain‑driven ERP covering GL/AP/AR, inventory, and procurement. Event‑driven integrations with analytics and search capabilities.
    Business Logic: Multi‑entity accounting, tax/VAT, and approvals. Budget controls, audit logs, and financial consolidations with period closures.
    Big Project 2 — Real‑Time OMS & Market Data
    Tech Stack: .NET 8 minimal APIs, gRPC, SignalR, EF Core, SQL Server, Kafka, Redis, Prometheus; Frontend: React + TypeScript + Highcharts
    Short Tech Description: Low‑latency order routing and streaming market data. Resilient matching with circuit breakers and throttling.
    Business Logic: Risk checks, position limits, and margin rules. Order lifecycle, trade capture, and regulatory reporting.
    Medium Project 1 — Helpdesk & ITSM Portal
    Tech Stack: ASP.NET Core MVC, EF Core, SQL Server, Hangfire, IdentityServer, Serilog; Frontend: Blazor Server + Tailwind
    Short Tech Description: Ticketing, knowledge base, and CMDB with automation. SLA timers and agent productivity dashboards.
    Business Logic: Prioritization rules, escalations, and approvals. Change management workflows with CAB gates and audit history.
    Medium Project 2 — IoT Telemetry Hub
    Tech Stack: .NET 8 Web APIs, MQTT, Azure IoT Hub (or EMQX), TimescaleDB/PostgreSQL, Redis, InfluxDB, Grafana; Frontend: Angular + ngx‑charts
    Short Tech Description: Ingests high‑frequency device telemetry and commands. Rule engine performs aggregations and anomaly alerts.
    Business Logic: Device provisioning, policies, and digital twins. Billing per message, device, and feature tier.
13) GO (Goroutines, Channels, net/http, Gin, Go Modules, gRPC, Docker, Testify)
    Big Project 1 — Cloud‑Native Event Streaming Platform
    Tech Stack: Go (Gin/gRPC), NATS/Kafka, Protobuf, ClickHouse, Redis, OpenTelemetry, Prometheus, Grafana, Jaeger, Kubernetes; Frontend: Next.js + TypeScript Ops UI
    Short Tech Description: High‑throughput event ingestion and processing with backpressure. Pluggable processors and exactly‑once sinks.
    Business Logic: Tenant isolation, quotas, and retention policies. Billing by throughput, storage, and premium features.
    Big Project 2 — Edge API Gateway & Rate Limiter
    Tech Stack: Go (net/http, gRPC), Envoy, OPA, Redis, Postgres, OpenAPI, OTEL, Docker/K8s; Frontend: React + TypeScript admin console
    Short Tech Description: Extensible gateway with authN/Z, rate limiting, and request transformation. Hot reload for routes and policies.
    Business Logic: API keys, usage plans, and monetization. SLAs, analytics, and usage‑based billing with overage protection.
    Medium Project 1 — Distributed Scheduler
    Tech Stack: Go, Raft (etcd client), gRPC, PostgreSQL, Redis, Cron expressions, Prometheus; UI: SvelteKit + Tailwind
    Short Tech Description: Leader‑elected scheduler with durable jobs and retries. Time‑zone aware and backoff strategies built‑in.
    Business Logic: Multi‑tenant quotas and priorities. Job ownership, auditability, and chargeback.
    Medium Project 2 — Feature Flags & A/B Testing
    Tech Stack: Go (Gin), PostgreSQL, Redis, WebSockets/SSE SDKs, OTEL; Frontend: Vue 3 admin
    Short Tech Description: Low‑latency flag evaluations with SDKs for web/mobile. Experiments and targeting rules with real‑time updates.
    Business Logic: Segments, rollouts, and success metrics. Plan tiers based on MAUs, environments, and SLA.
14) Rust (Cargo, Rustup, Tokio, WebAssembly, Actix, Rocket, Diesel)
    Big Project 1 — Time‑Series DB & Analytics
    Tech Stack: Rust (Tokio), Arrow/Parquet, gRPC, Raft (consensus), RocksDB, SIMD optimizations, Prometheus exporter; Frontend: React + TypeScript + ECharts
    Short Tech Description: Columnar, compressed time‑series storage with SQL‑like queries. Horizontal scale with replication and retention policies.
    Business Logic: Tiered storage, quotas, and archival. Enterprise features: RBAC, encryption, and audit logging.
    Big Project 2 — Secure RAG & Inference Gateway
    Tech Stack: Rust (Actix‑web), WebAssembly plugins, OpenAI/HF adapters, Redis, Postgres, OpenSearch, OTEL; Frontend: Next.js
    Short Tech Description: High‑performance gateway hosting adapters for LLMs and vector search. Policy‑enforced pipelines with sandboxed WASM transforms.
    Business Logic: Tenant‑aware limits, content filters, and masking. Metering, billing, and SLA reporting.
    Medium Project 1 — CLI Data Wrangler
    Tech Stack: Rust (structopt/clap, polars, rayon), Parquet/CSV/JSON, Arrow; Packaging: cross‑platform builds
    Short Tech Description: Parallel data transforms, joins, and aggregations on large files. Schema inference and push‑down filters.
    Business Logic: Pro features (incremental processing, profiles). Team licenses with telemetry and update channels.
    Medium Project 2 — Image Processing Microservice
    Tech Stack: Rust (Rocket), SIMD, WASM optional, S3/MinIO, Redis cache, OTEL; Frontend: Minimal React admin
    Short Tech Description: Fast thumbnailing, format conversion, and filters. Deterministic pipelines and content hashing for cache keys.
    Business Logic: Tiered pricing by throughput and storage. SLAs for latency, availability, and error budgets.
15) PHP (Laravel, Symfony, Composer, PHPUnit, Doctrine, Twig)
    Big Project 1 — Multi‑Vendor Marketplace
    Tech Stack: Laravel 11, Sanctum/Passport, MySQL, Redis, Elasticsearch, Horizon/Queues, S3, PHPUnit; Frontend: Next.js + TypeScript + Tailwind; Payments: Stripe/Adyen
    Short Tech Description: Headless Laravel backend for catalog, orders, and vendors. Scalable search, caching, and background jobs.
    Business Logic: Commission models, payouts, and disputes. Promotions, inventory sync, and SLA dashboards for vendors.
    Big Project 2 — Subscription SaaS & Billing
    Tech Stack: Symfony 7, API Platform, Doctrine ORM, PostgreSQL, Redis, RabbitMQ, Twig for admin, PHPUnit; Frontend: React + TypeScript
    Short Tech Description: Subscription plans, metered billing, entitlements, and trials. Webhooks integrate with accounting and CRM.
    Business Logic: Proration, dunning, and collections workflows. Role‑based access, audit logs, and exportable invoices.
    Medium Project 1 — Headless CMS + Content APIs
    Tech Stack: Laravel, MySQL, Redis, Meilisearch/Elasticsearch, PHP‑DI, PHPUnit; Frontend: Nuxt 3/Vue 3
    Short Tech Description: Structured content types, versioning, and localization. CDN‑friendly APIs with image optimization.
    Business Logic: Editorial workflows, review/approval stages, and scheduling. Multisite theming and access policies.
    Medium Project 2 — Customer Support Portal
    Tech Stack: Symfony, Messenger (RabbitMQ), PostgreSQL, Redis, Twig, PHPUnit; Frontend: SvelteKit
    Short Tech Description: Ticketing, canned responses, and SLA timers. Knowledge base and customer widgets.
    Business Logic: Priority matrices, escalation paths, and CSAT/QA reviews. Reporting on backlog, response times, and agent performance.
16) Ruby (Rails, Sinatra, Hanami, Bundler, RSpec, RuboCop, Sidekiq, Capistrano)
    Big Project 1 — Multi‑Tenant CRM & Billing SaaS
    Tech Stack: Ruby on Rails 7, Hotwire/Turbo/Stimulus, PostgreSQL, Redis, Sidekiq, Stripe, Elasticsearch, ActiveStorage + S3, RSpec, RuboCop, Capistrano/Docker/K8s; Frontend: Rails views + Tailwind or React
    Short Tech Description: SaaS for accounts, contacts, deals, subscriptions, and invoices. Real‑time UI via Hotwire and background jobs for heavy tasks.
    Business Logic: Tiered plans, proration, dunning, and tax/VAT. Advanced permissions, audit logs, approval workflows, and revenue reporting.
    Big Project 2 — Marketplace & Logistics Platform
    Tech Stack: Rails API, JWT, PostgreSQL, Redis, Sidekiq, Elasticsearch/OpenSearch, ActiveStorage + S3, PayPal/Adyen, RSpec; Frontend: React + TypeScript + Next.js
    Short Tech Description: Headless marketplace with search, product catalogs, and distributed fulfillment. Integrates carriers for rates, labels, and tracking.
    Business Logic: Order lifecycle, returns/disputes, and vendor payouts. Pricing rules, promotions, and compliance for regional tax/ship constraints.
    Medium Project 1 — Incident Management & On‑Call
    Tech Stack: Rails 7, Hotwire, PostgreSQL, Redis, Sidekiq, Webhooks, Twilio/SendGrid, RSpec; Frontend: Tailwind
    Short Tech Description: Alert routing, escalations, and on‑call rotations. Integrates with monitoring tools via webhooks and APIs.
    Business Logic: SLA timers, runbooks, and post‑mortem reports. Policy‑based routing and business‑hour exceptions.
    Medium Project 2 — Feature Flags & Experimentation
    Tech Stack: Sinatra/Hanami service, PostgreSQL, Redis, Sidekiq, SDKs (JS/Ruby/Java), OpenAPI, RSpec; Frontend: Vue 3 admin
    Short Tech Description: Low‑latency flag evaluation and A/B tests with targeting. Real‑time updates and exposure tracking.
    Business Logic: Rollout rules, segments, and success metrics. Plan tiers by MAUs/environments and governance with approvals.
17) C++ (STL, Boost, RAII, Qt, CUDA, Concurrency, Clang/GCC, CMake)
    Big Project 1 — High‑Performance Exchange Matching Engine
    Tech Stack: C++20, Boost.Asio, lock‑free structures, Folly, gRPC for control, FlatBuffers, Redis, PostgreSQL, Prometheus exporter, CMake, Clang/GCC; UI: Qt admin + React dashboards
    Short Tech Description: Microsecond latency order book and matching with risk checks. Deterministic replay and persistence with journaling.
    Business Logic: Order lifecycle (IOC/FOK/GTC), throttling, and circuit breakers. Margin/risk validation and regulatory audit exports.
    Big Project 2 — 3D SLAM & Robotics Mapping Suite
    Tech Stack: C++20, CUDA, OpenCV, PCL, Eigen, ROS2, g2o, ZeroMQ, CMake; UI: Qt visualizer
    Short Tech Description: Real‑time visual‑inertial SLAM, loop closure, and map fusion. GPU‑accelerated feature extraction and alignment.
    Business Logic: Path planning with obstacle costs and kinematics. Mission scheduling, map persistence, and telemetry analytics.
    Medium Project 1 — Cross‑Platform Image Editor
    Tech Stack: C++/Qt, OpenCV, Skia, Plugin SDK, CMake, Catch2; Updater/Installer: cross‑platform
    Short Tech Description: Layers, masks, filters, and color management. Plugin system with sandboxing and versioned APIs.
    Business Logic: Licensing (trial/pro), asset packs, and team seats. Template marketplace and telemetry‑driven UX improvements.
    Medium Project 2 — Distributed In‑Memory Cache with Raft
    Tech Stack: C++20, Raft consensus, gRPC, Protobuf, RocksDB (persistence), CMake, Prometheus exporter
    Short Tech Description: Strongly consistent key‑value store with sharding and failover. Snapshotting, compaction, and rolling upgrades.
    Business Logic: Quotas, per‑tenant namespaces, and metering. Premium features for geo‑replication and backup SLAs.
18) C (GCC, Clang/LLVM, Make/CMake, Valgrind, GDB, Embedded C, OpenMP, GTK)
    Big Project 1 — Embedded IoT RTOS Firmware Suite
    Tech Stack: C (C99/C11), FreeRTOS/Zephyr, lwIP/MQTT, mbedTLS, OTA updater, CMake, GDB/OpenOCD, Unit tests (Unity/CMock)
    Short Tech Description: Secure firmware for sensors/actuators with OTA updates and telemetry. Low‑power modes, watchdogs, and robust storage.
    Business Logic: Device provisioning, certificate rotation, and policy enforcement. Fleet rules, command scheduling, and compliance reports.
    Big Project 2 — High‑Performance Network IDS/IPS
    Tech Stack: C, DPDK/eBPF/libpcap, Hyperscan, mTLS, CMake, Prometheus exporter, Lua rules, Valgrind/GDB
    Short Tech Description: Zero‑copy packet processing with signature and anomaly detection. Scales line‑rate on commodity NICs.
    Business Logic: Rule packs, threat scores, and auto‑containment hooks. SOC integrations, case management, and evidence exports.
    Medium Project 1 — GTK System Monitor & Tuner
    Tech Stack: C, GTK, libvirt, libproc, CMake/Autotools, Polkit integration
    Short Tech Description: Real‑time CPU/mem/IO graphs and process controls. Kernel parameter tweaks with safety guards.
    Business Logic: Role‑based profiles and policy locks. Audit logs, exportable reports, and advisory suggestions.
    Medium Project 2 — Parallel Scientific Compute Library
    Tech Stack: C, OpenMP/MPI optional, BLAS/LAPACK bindings, CMake, Unit tests
    Short Tech Description: Parallel transforms, solvers, and statistics with cache‑friendly layouts. Pluggable backends and vectorization.
    Business Logic: Dual license (OSS core, commercial addons). Support contracts and benchmark‑based sizing recommendations.
19) Flutter (Dart, Widgets, Material, Cupertino, BLoC, Provider, Riverpod, FlutterFlow)
    Big Project 1 — Super App (Commerce, Wallet, Chat)
    Tech Stack: Flutter (iOS/Android/Web), Dart, BLoC/Riverpod, Firebase/Auth/Firestore or Hasura GraphQL, Stripe, WebRTC, Push (FCM/APNs); Backend: Node.js NestJS or Go; Infra: Docker/K8s
    Short Tech Description: Unified shopping, payments, and real‑time chat with offline‑first sync. Deep links, biometrics, and accessibility‑first design.
    Business Logic: Carts, checkout, loyalty, and refunds. Role‑based admin, promotions, and notification campaigns.
    Big Project 2 — Telemedicine & Remote Care
    Tech Stack: Flutter, Dart, Riverpod, WebRTC, FastAPI/Node backend, PostgreSQL, Redis, OpenAPI, Keycloak/Cognito
    Short Tech Description: HD video consults, e‑prescriptions, and secure messaging. Integrates device readings and appointment workflows.
    Business Logic: Insurance eligibility, copay, and claims. Triage forms, provider scheduling, and clinical notes.
    Medium Project 1 — Field Service & Offline Ops
    Tech Stack: Flutter, Provider, SQLite/hive for offline, Hasura GraphQL/Supabase backend, Mapbox
    Short Tech Description: Work orders, routes, and photo capture with robust offline queues. Sync conflict resolution and delta updates.
    Business Logic: SLAs, technician skills matching, and parts usage. Costing per job and customer sign‑off flows.
    Medium Project 2 — Personal Finance & Budgeting
    Tech Stack: Flutter, Riverpod, Plaid integration, Local encryption, FastAPI backend + PostgreSQL
    Short Tech Description: Account aggregation, budgets, and category insights. Secure local vault with optional cloud sync.
    Business Logic: Goals, alerts, and cashflow projections. Premium analytics and household multi‑user sharing.
20) Gamedev (Unity, Unreal, Godot, CryEngine, GameMaker, Cocos2d‑x, Blender, Substance, Photon)
    Big Project 1 — MMO Survival (Server‑Authoritative)
    Tech Stack: Unreal Engine 5 (C++/Blueprints), Dedicated servers, SpatialOS or custom sharding, EOS/PlayFab, Redis, PostgreSQL, Prometheus; Tools: Blender/Substance; Networking: Unreal networking + EOS
    Short Tech Description: Large open world with base‑building, crafting, and factions. Server‑authoritative simulation with anti‑cheat and rollback.
    Business Logic: Player economy, guilds, and territory wars. Seasonal content, battle pass, and cosmetics store.
    Big Project 2 — Co‑op Puzzle Builder with UGC
    Tech Stack: Unity 2022+, C#, DOTS (selective), Addressables, Photon Fusion/NGO, PlayFab backend, Cloud Storage, CI for mod pipelines
    Short Tech Description: Players create/share puzzle levels with real‑time co‑op solving. Cloud UGC with moderation and versioning.
    Business Logic: Creator payouts, marketplace fees, and seasonal challenges. Progression tracks, achievements, and events.
    Medium Project 1 — 2D Platformer Creator (Godot)
    Tech Stack: Godot 4 (GDScript/C#), TileMaps, Lightweight physics, Export to Web/Mobile; Tools: Aseprite pipeline
    Short Tech Description: Drag‑and‑drop level editor with asset packs and scripting. One‑click export and community sharing.
    Business Logic: Asset pack marketplace and featured slots. Monetization via premium exports and cloud saves.
    Medium Project 2 — VR Training Simulator
    Tech Stack: Unity + XR Interaction Toolkit, OpenXR, URP, C#, LMS (xAPI/SCORM) integration, CI for content builds
    Short Tech Description: Realistic scenarios with scoring and guidance. Portable content bundles for different roles/sites.
    Business Logic: Licensing per seat/module and analytics for compliance. Certification paths and manager dashboards.
21) Video (processing, optimizing, algorithms)
    Big Project 1 — Cloud Video Transcoding & Streaming Platform
    Tech Stack: Python FastAPI, FFmpeg/GStreamer, Nvidia NVENC, HLS/DASH, Redis, PostgreSQL, S3, Kafka, Kubernetes; Frontend: Next.js + TypeScript player (hls.js/shaka)
    Short Tech Description: Ingests uploads/live streams and transcodes into adaptive bitrates with GPU acceleration. Optimizes packaging, thumbnails, and captions with parallel pipelines.
    Business Logic: Tiered storage, bandwidth metering, and per‑minute transcoding billing. DRM/license management, geo‑blocking, and SLA reports for publishers.
    Big Project 2 — Real‑Time Video Analytics & Alerting
    Tech Stack: Python + OpenCV/DeepStream, PyTorch, Triton Inference Server, Kafka, ClickHouse, Redis, FastAPI, Grafana; Frontend: React + Mapbox + WebRTC preview
    Short Tech Description: Detects objects, zones, and events on live feeds at scale. Supports GPU scheduling, model hot‑swaps, and edge aggregation.
    Business Logic: Rule‑based alerts, incident tickets, and evidence clips. Customer contracts define retention, privacy masks, and access controls.
    Medium Project 1 — VOD Optimization Toolkit
    Tech Stack: Node.js CLI, FFmpeg, SSIM/PSNR/VMAF metrics, S3/CloudFront; UI: Minimal React dashboard
    Short Tech Description: Automated ABR ladder generation with quality metrics. Precomputes spritesheets, chapters, and captions.
    Business Logic: Cost calculator compares storage/CDN savings vs quality. Batch jobs scheduled per library with audit logs.
    Medium Project 2 — Collaborative Video Review Portal
    Tech Stack: Java Spring Boot, PostgreSQL, Redis, WebSockets, S3, OpenAPI; Frontend: Next.js + TypeScript + MSE player
    Short Tech Description: Frame‑accurate commenting, versioning, and approvals. Integrates watermarking and secure sharing.
    Business Logic: Reviewer roles, deadlines, and sign‑off workflows. Project billing by seats, storage, and review cycles.
22) Compilers (Parsing theory, LLVM, JIT/AOT, static analysis, type systems)
    Big Project 1 — DSL Compiler for Data Pipelines
    Tech Stack: LLVM, MLIR, ANTLR4, C++/Rust backend, Python tooling, Parquet/Arrow, gRPC; Frontend: VS Code extension (TypeScript)
    Short Tech Description: Compiles a high‑level DSL into vectorized execution plans. Targets CPUs/GPUs with optimizations for joins, filters, and window ops.
    Business Logic: Enterprise license with governance and audit of pipeline lineage. Monetizes features like cost models, optimizers, and compliance packs.
    Big Project 2 — Secure JIT Runtime for Plugins
    Tech Stack: WebAssembly (WASI), Cranelift/Wasmtime, Rust runtime, Capabilities sandbox, gRPC/HTTP; UI: React admin for policies
    Short Tech Description: Executes untrusted plugins safely with fine‑grained capabilities. Hot‑reloads, versioning, and deterministic resource limits.
    Business Logic: Marketplace for plugins with revenue share. Policy templates, approval flows, and audit logs for regulated tenants.
    Medium Project 1 — Static Analyzer & Linter Suite
    Tech Stack: Java/Kotlin, CFG/SSA, dataflow/taint analysis, SARIF, GitHub Actions integration; UI: Next.js reports
    Short Tech Description: Detects security/code smells with precise interprocedural analysis. Baseline mode reduces noise, with autofixes for common patterns.
    Business Logic: Pricing by repos and seats, SLAs for rule updates. Compliance mappings (OWASP/CWE/PCI) and exportable reports.
    Medium Project 2 — Educational Language + IDE
    Tech Stack: ANTLR grammar, TypeScript interpreter, Monaco editor, WebAssembly JIT prototype
    Short Tech Description: Toy language with types, closures, and pattern matching. Interactive debugger and visualization of parsing/typing.
    Business Logic: Course licensing for classrooms and cohorts. Certification tracks and instructor analytics.
23) Big Data + ETL (Spark, Kafka, Hive/Trino, Flink, Airflow, dbt, NiFi, Snowflake, Databricks, Glue, BigQuery, Beam)
    Big Project 1 — Unified Lakehouse & Governance Hub
    Tech Stack: Databricks/Spark, Delta Lake, Kafka, Airflow, Trino, dbt, Unity Catalog, S3, Glue/Athena, Grafana; Frontend: React + TypeScript governance UI
    Short Tech Description: Curates bronze/silver/gold layers with lineage and quality checks. Query federation and semantic layer for BI tools.
    Business Logic: Data contracts, SLAs, and stewardship workflows. Cost and performance dashboards with optimization recommendations.
    Big Project 2 — Real‑Time Stream Processing Platform
    Tech Stack: Apache Flink, Kafka, Debezium CDC, Elasticsearch/OpenSearch, ClickHouse, Redis, Kubernetes; API: FastAPI/Quarkus; UI: Next.js
    Short Tech Description: Stateful stream jobs with exactly‑once processing and backpressure. Real‑time aggregation, enrichment, and alerting.
    Business Logic: Multi‑tenant namespaces with quotas and priorities. Versioned jobs, approvals, and incident runbooks.
    Medium Project 1 — ELT with dbt & Warehouse
    Tech Stack: dbt Core, Snowflake/BigQuery/Redshift, Airflow, Great Expectations, Metabase; UI: Minimal React status portal
    Short Tech Description: SQL‑first transformations with tests and docs. Automated deployments with environment promotion.
    Business Logic: Data product ownership, SLAs, and chargeback. Access control by role and dataset, with usage analytics.
    Medium Project 2 — Data Integration Factory
    Tech Stack: Apache NiFi, Kafka Connect, JDBC/SaaS connectors, S3, Parquet, Schema Registry; UI: Vue 3
    Short Tech Description: Drag‑drop flows with templates for common sources/targets. Monitors throughput, errors, and schema drift.
    Business Logic: Connector marketplace and support SLAs. Quotas, alerts, and capacity planning for pipelines.
24) Blockchain (Solidity, Python, Rust, Web3.js, Truffle, IPFS, Hardhat, OpenZeppelin, Ganache, Ethers.js, Ethereum, Hyperledger Fabric, PoW/PoS, Merkle Trees, Trezor, MythX)
    Big Project 1 — DeFi Lending & Derivatives Protocol
    Tech Stack: Solidity + OpenZeppelin, Hardhat/Foundry, Ethers.js, subgraphs (The Graph), IPFS/Arweave, Python risk sims, React + Next.js dApp, Ledger/Trezor support
    Short Tech Description: Collateralized lending, interest rate models, and AMM integrations. On‑chain governance, oracle feeds, and liquidation bots.
    Business Logic: Tokenomics for treasury and incentives. Audits, bug bounties, and risk frameworks for collateral onboarding.
    Big Project 2 — Consortium Supply Chain on Fabric
    Tech Stack: Hyperledger Fabric, Chaincode (Go), CA/Peers/Orderers, CouchDB, REST gateway, Kafka, IPFS for docs; Frontend: Angular or React
    Short Tech Description: Shared ledger for provenance, certificates, and transfers. Private data collections for sensitive fields.
    Business Logic: Role‑based endorsements, SLAs, and penalties. Compliance exports (COO, ESG) and dispute resolution workflows.
    Medium Project 1 — NFT Ticketing with Anti‑Fraud
    Tech Stack: Solidity, Hardhat, Ethers.js, OpenZeppelin ERC‑721/1155, QR/signature verification, Next.js dApp
    Short Tech Description: Token‑gated events with transfer rules and royalties. Off‑chain allowlists and dynamic metadata.
    Business Logic: Revenue splits for organizers/artists. Secondary market fees, refunds, and access revocation policies.
    Medium Project 2 — Custody & Key Management Service
    Tech Stack: HSM/KMS, Threshold signatures (TSS), MPC, Rust/Go service, gRPC/REST, Postgres; UI: React admin
    Short Tech Description: Secure wallet orchestration with policies and approvals. Supports hardware wallets and programmatic flows.
    Business Logic: Transaction policies, limits, and multi‑sig approvals. Audit trails, compliance checks, and fee schedules.
25) GIS (ArcGIS, QGIS, PostGIS, Mapbox, Leaflet, GeoServer, GDAL, OpenLayers, Cesium, Coordinate systems, R‑tree, Shapefile)
    Big Project 1 — Urban Mobility & Routing Analytics
    Tech Stack: PostGIS, pgRouting, GeoServer, Kafka, Spark/GeoMesa, Mapbox/Deck.gl, Cesium for 3D, Python FastAPI; Frontend: Next.js
    Short Tech Description: Aggregates GPS/GTFS to compute travel times, coverage, and congestion. Provides multi‑modal routing and 3D city analytics.
    Business Logic: KPIs for operators, SLAs, and planning scenarios. Tiered access for agencies, operators, and public dashboards.
    Big Project 2 — Land Management & Cadastral System
    Tech Stack: PostgreSQL + PostGIS, GeoServer, QGIS integrations, GDAL/OGR, OpenLayers/Leaflet UI, Java Spring Boot APIs; CAD import pipeline
    Short Tech Description: Parcel registry with geodetic accuracy, topologies, and legal overlays. Versioned edits, conflict resolution, and audit trails.
    Business Logic: Workflow for survey approvals, permits, and fees. Role‑based access with public/secure layers and export services.
    Medium Project 1 — Indoor Mapping & Wayfinding
    Tech Stack: PostGIS, IndoorGML, MQTT beacons, WebSockets, React + Mapbox GL, Python FastAPI
    Short Tech Description: Floorplans, POIs, and real‑time location for venues. Pathfinding with accessibility constraints and occupancy.
    Business Logic: Venue contracts, SLAs for accuracy, and privacy controls. Advertising spots, heatmaps, and operational reports.
    Medium Project 2 — Geospatial ETL & Tile Server
    Tech Stack: Tippecanoe, MBTiles/Vector tiles, GDAL, MinIO/S3, Tileserver‑GL, Node.js orchestrator; UI: Vue 3
    Short Tech Description: Converts shapefiles/GeoJSON to optimized vector tiles. Caches tiles with CDN integration and style packs.
    Business Logic: Project‑based pricing by area and zoom levels. License terms for data usage, updates, and re‑distribution.
26) Finance (Loan Origination, Credit Scoring, Risk, Portfolio, Trading, Derivatives, Treasury)
    Big Project 1 — Core Banking: Loan Origination & Servicing
    Tech Stack: Java 21, Spring Boot 3, Kafka, PostgreSQL/Oracle, Redis, Camunda BPMN, Keycloak, OpenAPI; Frontend: React + TypeScript + Next.js; Reports: Jasper
    Short Tech Description: End‑to‑end origination with underwriting, pricing, and e‑sign. Servicing engine manages schedules, escrow, and collections at scale.
    Business Logic: KYC/AML, credit risk, and policy rules with explainability. Delinquency workflows, promises‑to‑pay, and regulatory reporting with audit trails.
    Big Project 2 — Quant Research, Backtesting & Execution
    Tech Stack: Python (NumPy/pandas, scikit‑learn, PyTorch), FastAPI, Celery, Kafka, ClickHouse, PostgreSQL, Redis; Frontend: Next.js + TypeScript + Highcharts; Connectors: FIX/REST
    Short Tech Description: Multi‑asset backtests with walk‑forward validation and factor libraries. Live execution with smart order routing and throttling.
    Business Logic: Portfolio construction, risk (VaR, stress), and transaction cost modeling. Compliance checks, pre/post‑trade analytics, and kill‑switch controls.
    Medium Project 1 — Robo‑Advisor & Portfolio Rebalancer
    Tech Stack: Node.js (NestJS), Python (CVXPY), PostgreSQL, Redis, Airflow, OpenAPI; Frontend: React + TypeScript
    Short Tech Description: Goals‑based investing with ETF model portfolios and tax‑aware rebalancing. Simulates scenarios and glide paths.
    Business Logic: Suitability checks, IPS capture, and constraints (ESG/sector caps). Fee tiering, rebalancing bands, and client reporting.
    Medium Project 2 — Treasury & Liquidity Risk Dashboard
    Tech Stack: Go (gRPC/REST) or Java Quarkus, Kafka, TimescaleDB/PostgreSQL, Redis, Grafana; Frontend: Next.js + Tailwind
    Short Tech Description: Real‑time cash positions, LCR/NSFR metrics, and stress scenarios. Integrates with payment rails/ledgers for intraday views.
    Business Logic: Funding ladder optimization and counterparty limits. Alerts on liquidity gaps with escalation and approval workflows.
27) Assembly (x86/ARM, NASM/MASM, ISA, Interrupts, Linking)
    Big Project 1 — Hypervisor‑Backed Sandbox & Malware Lab
    Tech Stack: C/C++ + x86_64 ASM (NASM), VT‑x/AMD‑V, EPT/NPT, Windows driver hooks, Capstone/Keystone, Frida; UI: React + TypeScript analysis portal
    Short Tech Description: Lightweight hypervisor with instrumentation to observe syscalls, memory, and network. Deterministic snapshots for safe malware detonation.
    Business Logic: Case management, IOC extraction, and TTP catalogs. Subscription plans for analysis hours, storage, and sharing controls.
    Big Project 2 — JIT Emulator & Binary Translation Framework
    Tech Stack: C++20, ARM64/x86 ASM stubs, LLVM MC/ORC JIT, Dynarec, QEMU‑style decoders; UI: Qt tools + web dashboard (Next.js)
    Short Tech Description: Dynamic binary translation between x86↔ARM with hot block caching. Hooks for tracing, coverage, and fuzzing integrations.
    Business Logic: Licensing for embedded/diagnostics vendors. Add‑ons for coverage reports, profiling, and compliance traceability.
    Medium Project 1 — OS Kernel Teaching Kit
    Tech Stack: C + x86 ASM (bootloader, IDT/GDT), GRUB/UEFI, ELF linker scripts, Make/CMake, QEMU/GDB
    Short Tech Description: Minimal kernel with task switching, paging, and drivers. Labs demonstrate interrupts, syscalls, and scheduler basics.
    Business Logic: Course bundles for universities and bootcamps. Instructor dashboards, grading scripts, and certification exams.
    Medium Project 2 — Firmware Reverse Engineering Toolkit
    Tech Stack: Python (capstone/angr), C helpers + ARM Thumb ASM snippets, Binwalk, Radare2/Ghidra integrations; UI: Electron/React
    Short Tech Description: Extracts and analyzes firmware with signatures and diffing. Pattern libraries for crypto, compression, and packers.
    Business Logic: Team seats, private signature repos, and support SLAs. Export reports for audits and vulnerability disclosures.
28) Medicine (FHIR, AlphaFold, CRISPR, DICOM)
    Big Project 1 — FHIR‑Native EHR & Interop Hub
    Tech Stack: Java Spring Boot, HAPI FHIR R4/R5, PostgreSQL, Redis, Keycloak, S3, Kafka, OpenAPI; Frontend: React + TypeScript; Mobile: Kotlin (Android) optional
    Short Tech Description: Unified patient, encounter, orders, and observations via FHIR APIs. Integrates payers, labs, and devices with consent policies.
    Business Logic: Prior auth, eligibility, and claim attachments. Role‑based access, break‑glass, and HIPAA audit & retention.
    Big Project 2 — Imaging PACS/VNA with AI Triage
    Tech Stack: Python FastAPI, DICOM/DCMTK, Orthanc, CUDA/Torch for triage models, PostgreSQL, S3; Viewer: OHIF (React)
    Short Tech Description: DICOM store with routing, normalization, and tagging. AI models flag urgent findings and prioritize worklists.
    Business Logic: Study routing rules, turnaround SLAs, and QA double‑reads. Billing per study/storage and compliance exports (DICOM SR).
    Medium Project 1 — Genomics Pipeline & Variant Explorer
    Tech Stack: Nextflow/Snakemake, Python (pysam), VCF/BCF, S3/MinIO, PostgreSQL; UI: Next.js + Vega
    Short Tech Description: Automates alignment, variant calling, and annotation. Interactive explorer with filters and pedigrees.
    Business Logic: Lab workflows, sign‑off, and report templates. Data retention and de‑identification per regulation.
    Medium Project 2 — CRISPR Guide Design & Off‑Target Finder
    Tech Stack: Python (Biopython, scikit‑learn), BLAST/BWA, Redis cache, FastAPI; UI: React + TypeScript
    Short Tech Description: Designs gRNAs with scoring for on‑target efficiency and off‑target risk. Genome‑wide search with batch jobs and caches.
    Business Logic: Project quotas, collaboration, and audit. Premium genomes, annotations, and priority compute queues.
29) Kotlin (Android SDK, Jetpack Compose, Retrofit, Coroutines, Room, Firebase, Ktor)
    Big Project 1 — Digital Banking Super App
    Tech Stack: Kotlin (Android), Jetpack Compose, Coroutines/Flow, Hilt, Room, Retrofit, biometric auth, Firebase Crashlytics/Push; Backend: Ktor (Kotlin) + PostgreSQL/Redis; Web Admin: React + TypeScript
    Short Tech Description: Accounts, cards, transfers, and budgeting with offline‑first. Secure session handling, device binding, and encrypted storage.
    Business Logic: KYC onboarding, risk scoring, and limits. Offers, rewards, and disputes with ticketing and SLA tracking.
    Big Project 2 — On‑Demand Logistics & Courier
    Tech Stack: Kotlin (Android), Compose, Maps/Location, WorkManager, Retrofit; Backend: Spring Boot/Quarkus, Kafka, Postgres, Redis; Dispatch: OR‑Tools microservice; Web: Next.js
    Short Tech Description: Real‑time driver tracking, batching, and route optimization. Customer, courier, and dispatcher apps with live updates.
    Business Logic: Pricing, surge, and SLA windows. Proof‑of‑delivery, disputes, and payouts with fee schedules.
    Medium Project 1 — Telehealth Visits & e‑Rx
    Tech Stack: Kotlin (Android), Compose, WebRTC, Retrofit, Room; Backend: FastAPI + PostgreSQL, FHIR adapters; Web Admin: React
    Short Tech Description: Secure video consults with messaging, labs, and prescriptions. Works across variable networks with adaptive bitrate.
    Business Logic: Eligibility checks, copay, and claims submission. Scheduling, notes, and follow‑up reminders with consents.
    Medium Project 2 — Personal Knowledge & Tasks
    Tech Stack: Kotlin (Android), Compose, Room, WorkManager, AppSearch, Retrofit; Backend: Ktor + Postgres; Optional Sync: Firebase
    Short Tech Description: Notes, tasks, and calendar with offline‑first and powerful search. Smart suggestions and cross‑device sync.
    Business Logic: Premium features (OCR, voice, AI summaries). Team spaces, roles, and export/compliance options.