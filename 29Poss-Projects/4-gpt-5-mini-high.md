gpt-5-mini-high

1) Java
- Big 1 — Enterprise Web Portal
  Tech Stack: [Spring Boot, Spring MVC, Hibernate, PostgreSQL, React, Maven/Gradle, Docker, Kubernetes, JUnit, SLF4J]
  Short Tech Description: Backend built with Spring Boot and Hibernate exposing REST/GraphQL endpoints. Frontend is a React SPA; CI/CD via Maven/Gradle and Docker containers on Kubernetes; tests with JUnit.
  Business Logic: Centralized portal for multi-tenant customer management, roles, and workflows. Supports audit trails, approval flows, reporting, and export to CSV/PDF.

- Big 2 — Event-driven Microservices (native)
  Tech Stack: [Quarkus, Kafka, PostgreSQL, Vert.x, GraalVM Native Image, React, OpenAPI, Docker, Prometheus]
  Short Tech Description: Quarkus microservices using reactive Vert.x and Kafka for events; compiled to native images with GraalVM for low startup latency. API gateway and React front-end; observability via Prometheus.
  Business Logic: High-throughput order processing with eventual consistency across services and event-driven inventory reconciliation. Includes SLA-based routing and retries for failed events.

- Medium 1 — Micronaut REST API + Admin UI
  Tech Stack: [Micronaut, Hibernate, MySQL, Vue.js, Gradle, Docker, JUnit]
  Short Tech Description: Lightweight Micronaut backend exposing REST endpoints with Hibernate ORM. Admin SPA in Vue.js; builds via Gradle and containerized.
  Business Logic: CRUD and batch operations for product catalog and pricing rules. Admins can run bulk updates, schedule maintenance tasks, and view change history.

- Medium 2 — Command-line Java Tool (GraalVM)
  Tech Stack: [Java 17, Picocli, Maven, GraalVM Native Image, SQLite]
  Short Tech Description: Single-binary CLI compiled to native with GraalVM for fast startup and small footprint. Uses Picocli for commands and embedded SQLite for lightweight storage.
  Business Logic: Data migration and transformation tool that ingests CSVs, applies mapping rules, and exports normalized datasets. Supports scheduling, dry-run, and audit logs.

2) Multithreading / Concurrency
- Big 1 — Distributed Job Scheduler
  Tech Stack: [Java (Executors, CompletableFuture), Kafka, Redis, Kubernetes, Spring Boot, React]
  Short Tech Description: Scheduler coordinates distributed worker pools using thread pools and message queues. Uses locks, semaphores, and optimistic concurrency for job claims.
  Business Logic: Schedule and horizontally execute recurring or ad-hoc jobs with failure handling and retries. Provides UI for inspecting running jobs, prioritization, and SLA enforcement.

- Big 2 — Low-latency Matching Engine
  Tech Stack: [C++ (std::thread, atomics), Linux, Redis, React, gRPC]
  Short Tech Description: Multithreaded core using lock-free queues and atomics for order matching. Exposes gRPC API and admin UI; focus on latency and throughput.
  Business Logic: Real-time order matching with priority matching rules, partial fills, and order book snapshots. Ensures consistency and fairness while avoiding deadlocks and starvation.

- Medium 1 — Producer-Consumer Streaming Service
  Tech Stack: [Python (asyncio), RabbitMQ, PostgreSQL, Vue.js]
  Short Tech Description: Asyncio-based producers and consumers with backpressure and semaphores for rate control. Dashboard to monitor queues and consumer lag.
  Business Logic: Ingests high-rate events, applies enrichment and persists results. Supports dynamic scaling of consumers and failure recovery.

- Medium 2 — Concurrency Teaching Sandbox
  Tech Stack: [JavaScript (WebWorkers), Node.js backend, React, Docker]
  Short Tech Description: Browser-based simulator demonstrating threads, mutexes, semaphores, and deadlocks using WebWorkers. Backend stores scenarios and leaderboards.
  Business Logic: Interactive lessons on synchronization patterns and classic problems (Dining Philosophers, Sleeping Barber). Users run scenarios, inspect traces, and compare algorithms.

3) Python
- Big 1 — Analytics SaaS (Django)
  Tech Stack: [Django, React, PostgreSQL, Celery+Redis, Docker, PyTorch/TensorFlow, pytest]
  Short Tech Description: Monolithic Django backend with REST/GraphQL APIs and asynchronous tasks via Celery. Frontend in React; models served using Torch/TensorFlow Docker services.
  Business Logic: Multi-tenant analytics platform that ingests customer data, runs ML models for segmentation, and produces dashboards. Includes scheduled model retraining, access control, and exportable reports.

- Big 2 — Real-time API + ML Inference
  Tech Stack: [FastAPI, Uvicorn, WebSockets, Redis, Kafka, React, TensorFlow Serving]
  Short Tech Description: FastAPI microservices for low-latency inference and WebSocket streaming. Uses Redis/Kafka for buffering and TensorFlow Serving for models.
  Business Logic: Real-time recommendation and personalization for user sessions, combining streaming signals with batch features. Supports A/B testing, feature flags, and fallback logic.

- Medium 1 — Flask Microservice + DB
  Tech Stack: [Flask, SQLAlchemy, PostgreSQL, Docker, pytest]
  Short Tech Description: REST API built in Flask with SQLAlchemy ORM and migrations. Containerized and unit-tested with pytest.
  Business Logic: Inventory and order microservice that manages product stock, reservations, and order validation rules. Provides webhooks for external systems.

- Medium 2 — Data Science Pipeline
  Tech Stack: [Pandas, NumPy, Jupyter, Airflow, S3]
  Short Tech Description: Notebook-driven ETL and feature engineering pipeline orchestrated by Airflow. Uses Pandas/NumPy for transforms and S3 for storage.
  Business Logic: Periodic data ingestion, cleaning, feature generation, and dataset publishing for ML teams. Supports lineage, versioning, and quality checks.

4) Machine Learning
- Big 1 — Recommendation Platform
  Tech Stack: [PyTorch, HuggingFace, Kafka, Redis, PostgreSQL, React, Docker, Kubernetes]
  Short Tech Description: Hybrid recommender using collaborative filtering and transformer-based content embeddings. Real-time inference served via microservices; models trained in PyTorch and deployed with Kubernetes.
  Business Logic: Personalized product/content recommendations with online A/B experiments and cold-start strategies. Includes ranking, offline evaluation, and feedback loops to retrain models.

- Big 2 — AutoML & Model Ops Platform
  Tech Stack: [TensorFlow, XGBoost, LightGBM, Kubeflow, MLflow, React, Airflow]
  Short Tech Description: Pipeline for automated feature selection, hyperparameter tuning, and model deployment. Kubeflow orchestrates experiments; MLflow tracks artifacts and metrics.
  Business Logic: Automates model discovery for business problems, selects best model, and deploys with canary rollout. Provides drift detection and scheduled retraining.

- Medium 1 — Image Classification Web App
  Tech Stack: [TensorFlow/Keras, Flask/FastAPI, React, PostgreSQL, Docker]
  Short Tech Description: Lightweight inference API serving Keras models; React frontend for image upload and visualization. Containerized and scalable.
  Business Logic: Lets users upload images for classification and returns labels with confidence scores. Admin portal for dataset labeling and model retraining jobs.

- Medium 2 — Fraud Detection Microservice
  Tech Stack: [Scikit-learn, LightGBM, Python, Kafka, Redis, REST API]
  Short Tech Description: Streaming scoring microservice using LightGBM for fast predictions; features computed from streaming events. Caches results in Redis for hot customers.
  Business Logic: Detects anomalous transactions using ensemble models and rule-based heuristics. Raises alerts, attaches risk scores, and triggers manual reviews.

5) AWS (EKS/Kubernetes must be included)
- Big 1 — Cloud-native Microservices Platform (EKS)
  Tech Stack: [AWS EKS (Kubernetes), ECR, ALB, RDS (Postgres), S3, Terraform, GitHub Actions, Prometheus, Grafana]
  Short Tech Description: Full microservices stack deployed to EKS with Terraform for infra-as-code and GitHub Actions CI/CD. Uses managed RDS, S3 for storage, and Prometheus/Grafana for metrics.
  Business Logic: Multi-service SaaS product with tenant isolation, autoscaling, and blue/green deployments. Centralized logging and monitoring for SLAs and alerting.

- Big 2 — Serverless Data Lake & Analytics
  Tech Stack: [S3, Glue, Athena, Lambda, Kinesis, SageMaker, CloudFormation, QuickSight]
  Short Tech Description: Serverless ingestion pipelines using Kinesis and Lambda landing into S3; Glue crawlers prepare tables for Athena queries. SageMaker used for model training and inference.
  Business Logic: Aggregates event data across sources, enables ad-hoc analytics and scheduled reports. Provides data cataloging, RBAC, and model-driven insights for business users.

- Medium 1 — Scalable Web App with IaC
  Tech Stack: [EC2/ASG, RDS, ELB, Route53, CloudFormation/Terraform, S3, CloudWatch]
  Short Tech Description: Traditional web application running on autoscaling EC2 fleet with managed RDS and ALB. Infrastructure defined with CloudFormation or Terraform; observability via CloudWatch.
  Business Logic: Customer-facing website with product catalog, checkout, and admin back-office. Handles peak traffic via autoscaling and uses RDS read replicas for reporting.

- Medium 2 — Conversational Bot using AWS Services
  Tech Stack: [AWS Lambda, API Gateway, DynamoDB, Lex, S3, CloudWatch, Node.js/Python]
  Short Tech Description: Serverless chatbot with Lex for NLU and Lambda handlers for business logic; state persisted in DynamoDB. Exposed via API Gateway and integrated with front-end.
  Business Logic: Customer support assistant that answers product questions, creates tickets, and escalates to human agents. Tracks session context and transfers user data to CRM.

6) Databases
- Big 1 — Hybrid OLTP/OLAP Platform
  Tech Stack: [PostgreSQL, Citus, Kafka, Spark, Airflow, Redis, React, Docker, Kubernetes]
  Short Tech Description: Operational Postgres cluster sharded with Citus for OLTP and Spark for analytical processing on streaming data. Kafka for ingestion and Airflow for orchestration.
  Business Logic: Real-time analytics on transaction data while supporting low-latency transactional operations. Provides dashboards, cohorting, and rolling aggregations for business teams.

- Big 2 — Search & Analytics Engine
  Tech Stack: [Elasticsearch, Cassandra, Redis, Logstash, Kibana, Node.js, React]
  Short Tech Description: Event storage in Cassandra, search and analytics via Elasticsearch, logs ingested with Logstash. Frontend for query, visualization, and alerting.
  Business Logic: Indexes large document sets for fast full-text search and analytics. Supports faceted search, aggregations, and real-time dashboards for business metrics.

- Medium 1 — Inventory Management (RDBMS)
  Tech Stack: [MySQL, Node.js/Express, React, Sequelize/TypeORM, Docker]
  Short Tech Description: REST API over MySQL with ORM and React frontend for inventory operations. Containerized for deployment.
  Business Logic: Track inventory levels, suppliers, and re-order rules. Generates alerts when stock falls below thresholds and supports batch imports.

- Medium 2 — Graph-based Recommendation
  Tech Stack: [Neo4j, Python (py2neo), Flask, React]
  Short Tech Description: Graph database storing relationships and traversals for recommendations; API exposes path queries. Frontend visualizes social/relationship graphs.
  Business Logic: Recommend connections or items based on relationship traversals and similarity metrics. Supports influence scoring and rule-driven promotions.

7) Algorithms
- Big 1 — Route Optimization Engine
  Tech Stack: [Java/Go, Postgres/PostGIS, Redis, React, Docker, Kubernetes]
  Short Tech Description: Backend implements vehicle routing algorithms (CVRP, TSP variants) with heuristics and local search. Spatial DB with PostGIS and caching in Redis; front-end map UI.
  Business Logic: Optimize delivery routes minimizing distance/time while respecting capacity, time windows, and driver constraints. Provides re-optimization on dynamic events.

- Big 2 — Competition Judge Platform
  Tech Stack: [Node.js/Python, Docker Sandbox, Redis, React, Postgres]
  Short Tech Description: Secure judge system that compiles and runs submitted solutions in isolated containers; supports algorithm testcases and scoring. Provides leaderboards and problem management.
  Business Logic: Hosts programming contests with timed rounds, automated scoring, and ranking by correctness and runtime. Handles parallel submissions and plagiarism checks.

- Medium 1 — Algorithm Visualizer Web App
  Tech Stack: [TypeScript, React, D3.js, Node.js]
  Short Tech Description: Interactive visualizations for sorting, searching, graphs, and DP with step-by-step animation. Backend stores lesson scenarios and user progress.
  Business Logic: Teach algorithms through simulations and user-driven inputs. Allows custom data sets and comparison of algorithm complexity and performance.

- Medium 2 — String Pattern Library
  Tech Stack: [C++/Rust, Unit Tests, npm wrapper (optional)]
  Short Tech Description: High-performance library implementing KMP, Aho-Corasick, suffix arrays, and regex optimizations. Exposes bindings for higher-level languages.
  Business Logic: Provides fast substring, pattern matching, and multi-pattern search for log analysis and text mining. Offers streaming interfaces for large texts.

8) JavaScript / Frontend & Node
- Big 1 — Next.js SaaS Platform
  Tech Stack: [Next.js (TypeScript), Node.js, GraphQL, PostgreSQL, Redis, Docker, Kubernetes, Jest]
  Short Tech Description: SSR/SSG hybrid frontend in Next.js with a GraphQL backend and PostgreSQL. Caching via Redis and deployments on Kubernetes.
  Business Logic: Multi-tenant SaaS with subscription billing, team workspaces, and rich content editing. Includes admin dashboards, usage quotas, and role-based access.

- Big 2 — Real-time Collaborative Editor
  Tech Stack: [React, TypeScript, WebRTC, CRDT (Automerge), Node.js, Redis, WebSockets]
  Short Tech Description: CRDT-based collaboration engine with low-latency peer sync via WebRTC and server relay. Frontend React editor synced in real time; server manages persistence.
  Business Logic: Multi-user document editing with conflict-free merging and collaborative cursors. Provides version history, access controls, and export.

- Medium 1 — E-commerce SPA
  Tech Stack: [Vue.js, Node.js/Express, MongoDB, Stripe, Docker]
  Short Tech Description: Vue SPA for storefront and admin with a Node.js API and MongoDB for product/catalog storage. Payment integration via Stripe.
  Business Logic: Product browsing, cart, checkout, and order fulfillment flow. Admins manage inventory, discounts, and promotions.

- Medium 2 — Dashboard with Svelte & TypeScript
  Tech Stack: [Svelte, TypeScript, REST API, PostgreSQL]
  Short Tech Description: Lightweight Svelte frontend consuming REST endpoints with charts and widgets. Focus on fast load and small bundle sizes.
  Business Logic: Business analytics dashboard with configurable widgets and scheduled reports. Users can create saved views and share dashboards.

9) Web Design / UX
- Big 1 — Design System & Component Library
  Tech Stack: [Figma, React, Storybook, Tailwind CSS, TypeScript, Node.js]
  Short Tech Description: Centralized design tokens and components in Figma synced to a Storybook-based React component library. Tailwind for utility theming and automated token export.
  Business Logic: Provides consistent UI components, accessibility guidelines, and theming support across multiple products. Enables designers and developers to iterate faster.

- Big 2 — Headless Site Builder
  Tech Stack: [Webflow/React, Headless CMS (Sanity/Strapi), Next.js, Tailwind]
  Short Tech Description: Visual site builder with a headless CMS storing structured content; Next.js frontend for fast delivery. Exports stable templates and SEO metadata.
  Business Logic: Non-technical users build marketing sites via drag-and-drop and templates. Handles multi-language pages, SEO optimizations, and A/B testing.

- Medium 1 — Portfolio Generator
  Tech Stack: [HTML5, Tailwind CSS, Node.js, EJS, Netlify]
  Short Tech Description: Static site generator with templating and Tailwind-based themes. Deploys as static site to Netlify/Vercel.
  Business Logic: Users input profile, projects, and images to generate a polished portfolio site. Supports export and custom domain management.

- Medium 2 — Accessibility Audit Tool
  Tech Stack: [Node.js, Puppeteer, Lighthouse, React]
  Short Tech Description: Automated crawler runs Lighthouse and custom rules to detect accessibility issues. Frontend displays issues with remediation tips.
  Business Logic: Scans sites for WCAG violations and produces prioritized tickets for dev teams. Tracks improvement over time and enforces accessibility SLAs.

10) Security
- Big 1 — Web App Vulnerability Scanner
  Tech Stack: [Python, OWASP ZAP, Burp integrations, React, Elasticsearch, Docker, CI]
  Short Tech Description: Automated scanning pipeline using ZAP and integrated Burp checks; findings stored in Elasticsearch. Interactive UI for triage and remediation.
  Business Logic: Regularly scans web apps and APIs for OWASP top 10 issues and custom policies. Generates prioritized remediation tickets and risk scores for assets.

- Big 2 — SIEM & Incident Response Platform
  Tech Stack: [ELK Stack, Suricata, Kafka, Kibana, Python, React]
  Short Tech Description: Logs and network events ingested into Elasticsearch; Suricata provides IDS alerts. UI for incident investigation and playbook automation.
  Business Logic: Correlates alerts across sources to detect breaches and orchestrates response playbooks. Stores evidence, timelines, and supports compliance reporting.

- Medium 1 — Pentest Lab Automation
  Tech Stack: [Kali Linux, Metasploit, Vagrant/VirtualBox, Terraform (cloud), Node.js]
  Short Tech Description: Automated provisioning of isolated pentest environments using Vagrant or cloud Terraform modules. Preconfigured vulnerable VMs and tooling.
  Business Logic: Enables repeated pentesting exercises, scenario templates, and scoring. Facilitates training and regression tests after fixes.

- Medium 2 — Secure Auth Service
  Tech Stack: [OAuth2/OpenID Connect, Java/Spring Security or Node.js, JWT, HSM/PKCS#11 optional]
  Short Tech Description: Central authentication and authorization microservice implementing OAuth2 and OIDC flows; tokens signed with HSM-backed keys if available. Exposes introspection and userinfo endpoints.
  Business Logic: Handles login, MFA, consent, token issuance, and session management for multiple applications. Provides audit logs and role-based access control.

11) DevOps / SRE
- Big 1 — Enterprise CI/CD Platform
  Tech Stack: [Jenkins/GitLab, Kubernetes, Helm, Terraform, Docker, Nexus, Prometheus, Grafana]
  Short Tech Description: End-to-end CI/CD pipelines with reusable Helm charts and Terraform-managed infra. Artifact repository and metrics for pipeline health.
  Business Logic: Automates build, test, and deployment for many services with environment promotion, approval gates, and canary rollouts. Enforces compliance and rollback policies.

- Big 2 — Observability & Alerting Stack
  Tech Stack: [Prometheus, Grafana, Loki, Jaeger, Alertmanager, Kubernetes, Terraform]
  Short Tech Description: Metrics, logs, and tracing integrated with alerting and dashboards. Deployable via Helm charts and managed with Terraform.
  Business Logic: Provides SLO/SLA monitoring, incident detection, and root-cause tracing across distributed systems. Supports runbooks and automated paging.

- Medium 1 — GitOps Deployment Pipeline
  Tech Stack: [GitHub Actions, ArgoCD, Kubernetes, Docker, Helm]
  Short Tech Description: Declarative GitOps flows where Git is the source of truth; ArgoCD applies manifests to clusters. Actions handle image builds and RBAC.
  Business Logic: Developers change Git to trigger environment updates with review processes. Ensures reproducible deployments and audit trails.

- Medium 2 — Infrastructure Automation Toolkit
  Tech Stack: [Terraform, Ansible, Packer, Vault for secrets]
  Short Tech Description: Terraform for cloud provisioning and Ansible for configuration orchestration; Packer builds golden images and Vault manages secrets. Tests via terratest or kitchen.
  Business Logic: Provision and configure consistent environments for dev, staging, and prod with versioned infrastructure. Supports secrets rotation and compliance checks.

12) C# / .NET
- Big 1 — Enterprise ERP (ASP.NET Core + Blazor)
  Tech Stack: [ASP.NET Core, Blazor, SQL Server, Entity Framework, Identity, Docker, Azure]
  Short Tech Description: Server-side Blazor UI with ASP.NET Core APIs and EF Core for data access. Authentication via Identity and Azure AD integration.
  Business Logic: Full ERP covering HR, finance, inventory, and procurement with workflows and approvals. Role-based modules and multi-company support.

- Big 2 — Cross-platform Mobile + Desktop (MAUI)
  Tech Stack: [.NET 8, MAUI, Azure Functions, Cosmos DB, Xamarin migration, CI/CD]
  Short Tech Description: Single codebase for mobile and desktop using MAUI; serverless backends in Azure Functions. Data stored in Cosmos DB with sync support.
  Business Logic: Field service application with offline capabilities, sync, and scheduling for technicians. Includes photo attachments, checklists, and invoicing.

- Medium 1 — Microservice Suite (.NET + gRPC)
  Tech Stack: [ASP.NET Core, gRPC, PostgreSQL, Docker, Kubernetes, NUnit]
  Short Tech Description: Lightweight microservices communicating via gRPC with protobuf schemas; each service has its own DB. Unit and integration tests with NUnit.
  Business Logic: Modular business capabilities such as billing, user management, and notifications. Services support versioning and side-by-side deployment.

- Medium 2 — Desktop Finance Tool (WPF)
  Tech Stack: [WPF, MVVM, Entity Framework, SQLite, NUnit]
  Short Tech Description: Rich desktop UI using WPF and MVVM pattern; local persistence via SQLite and EF. Unit tested with NUnit.
  Business Logic: Personal finance manager supporting budgeting, transaction import, reconciliation, and reporting. Scheduled backups and data export.

13) Go
- Big 1 — High-performance Proxy / Load Balancer
  Tech Stack: [Go (goroutines, net/http), gRPC, Envoy integration, Kubernetes, Docker]
  Short Tech Description: Concurrent connection handling using goroutines and efficient I/O; supports gRPC and HTTP routing, health checks, and rate limiting. Deployed in Kubernetes.
  Business Logic: Acts as a programmable edge proxy with traffic shaping and canary routing. Provides metrics, circuit breaking, and dynamic config.

- Big 2 — Telemetry Collector & Aggregator
  Tech Stack: [Go, Prometheus client, Kafka, PostgreSQL, Grafana]
  Short Tech Description: High-throughput metrics collector written in Go with batching and backpressure. Stores aggregates for long-term analysis and exposes ingestion APIs.
  Business Logic: Collects telemetry from clients, performs rollup and retention policies, and serves dashboards and alerts. Supports downstream export and alert rules.

- Medium 1 — REST API with Gin
  Tech Stack: [Go, Gin, GORM, PostgreSQL, Docker]
  Short Tech Description: RESTful API built with Gin for routing and GORM for ORM; Dockerized for deployment. Includes unit and integration tests.
  Business Logic: Standard CRUD and business workflows for resource management, with pagination, filtering, and RBAC. Exposes webhooks for external integrations.

- Medium 2 — DevOps CLI Tool
  Tech Stack: [Go, Cobra, Docker, GitHub Actions]
  Short Tech Description: Single-binary CLI for environment orchestration and helper tasks built with Cobra. Easy distribution and installation.
  Business Logic: Helps engineers bootstrap projects, run environment checks, and automate repetitive deploy tasks. Supports plugin commands and config templating.

14) Rust
- Big 1 — High-performance Web Server & Microservices
  Tech Stack: [Actix/Tokio, Diesel, PostgreSQL, WebAssembly for client, Docker, Kubernetes]
  Short Tech Description: Async Rust services using Tokio/Actix for low-latency endpoints and Diesel for DB. Parts compiled to WASM for in-browser heavy computation.
  Business Logic: Processes streaming data and provides low-latency APIs for analytics. Ensures memory safety and performance for throughput-sensitive workloads.

- Big 2 — Secure Embedded System
  Tech Stack: [Rust (no_std), Embedded HAL, Cross-compilation, CI]
  Short Tech Description: Firmware using Rust safety guarantees for embedded hardware with secure update mechanism. Cross-compilation and hardware-in-loop tests.
  Business Logic: Controls critical device functions with fault tolerance and rollback updates. Implements secure boot and encrypted telemetry reports.

- Medium 1 — Concurrent CLI for Data Processing
  Tech Stack: [Rust, Rayon/Tokio, Serde, Clap]
  Short Tech Description: Multithreaded CLI that processes large datasets using Rayon for parallelism or Tokio for async I/O. Serialization via Serde.
  Business Logic: Bulk transforms, aggregation, and export of logs and metrics. Supports streaming mode and checkpointing.

- Medium 2 — WebAssembly Compute Module
  Tech Stack: [Rust, wasm-pack, JavaScript integration, Web Workers]
  Short Tech Description: CPU-bound routines compiled to WASM for browser execution with JS bindings. Runs in Web Workers for parallelism.
  Business Logic: Offloads heavy transforms (e.g., image/video filters or crypto) to client to reduce server load. Provides consistent results across platforms.

15) PHP
- Big 1 — Enterprise CMS (Laravel)
  Tech Stack: [Laravel, Vue.js, MySQL, Elasticsearch, Docker, Redis, PHPUnit]
  Short Tech Description: Laravel backend with Vue single-page admin and MySQL; Elasticsearch for full-text search and Redis for caching. Containerized for deployment.
  Business Logic: Manage content, multi-site publishing, and editorial workflows with roles and permissions. Supports scheduled publishing and content versioning.

- Big 2 — Multi-tenant SaaS (Symfony)
  Tech Stack: [Symfony, PostgreSQL, Docker, RabbitMQ, API Platform, React]
  Short Tech Description: Symfony-based multi-tenant architecture with PostgreSQL schemas per tenant or shared tables. Async tasks via RabbitMQ.
  Business Logic: Offers tenant isolation, configurable features per customer, billing integration, and usage quotas. Self-serve onboarding and tenant administration.

- Medium 1 — Lightweight API (Lumen)
  Tech Stack: [Laravel Lumen, Redis, MySQL, Docker]
  Short Tech Description: Minimal microservice API using Lumen for performance with Redis caching. Simple test suite and CI pipeline.
  Business Logic: Authenticated resource service providing product info, pricing, and simple business rules. Exposes rate-limited endpoints for partners.

- Medium 2 — E-commerce Site (WooCommerce)
  Tech Stack: [WordPress, WooCommerce, PHP, MySQL, React (headless storefront)]
  Short Tech Description: WooCommerce store with optional headless React storefront for custom UX. Payment integrations and plugins for shipping/taxes.
  Business Logic: Online storefront with product catalog, cart, promotions, and order processing. Admin tools for stock and fulfillment.

16) Ruby
- Big 1 — Marketplace Platform (Rails)
  Tech Stack: [Ruby on Rails, React, PostgreSQL, Sidekiq, Redis, Docker, RSpec]
  Short Tech Description: Rails monolith exposing JSON APIs consumed by React frontend; background jobs via Sidekiq. PostgreSQL for relational data and Redis for caching.
  Business Logic: Hosts buyers and sellers with listings, payments, disputes, and reviews. Handles search, recommendations, and commission calculations.

- Big 2 — Analytics SaaS (Hanami/Microservices)
  Tech Stack: [Hanami, Kafka, PostgreSQL, React, Docker, Ruby workers]
  Short Tech Description: Lightweight Ruby services for ingestion and processing using Kafka and background workers. Frontend React dashboards.
  Business Logic: Collects event streams, computes analytics, and surfaces insights to customers. Offers scheduled reports and retention policies.

- Medium 1 — Blogging Platform (Rails)
  Tech Stack: [Rails, PostgreSQL, ActionText, Tailwind CSS]
  Short Tech Description: Opinionated blogging engine built on Rails with rich text and SEO features. Simple admin interface and themes.
  Business Logic: Publish posts, manage authors, schedule posts, and moderate comments. Supports RSS, social sharing, and analytics.

- Medium 2 — Background Job Orchestration
  Tech Stack: [Sidekiq, Redis, Ruby, Sinatra admin]
  Short Tech Description: Job scheduling and retry management built on Sidekiq with lightweight admin UI in Sinatra. Monitoring via Redis metrics.
  Business Logic: Define complex job chains and dependency trees; handle retries, backoff and dead-lettering. Provides dashboards and SLA alerts.

17) C++
- Big 1 — Low-latency Trading Engine
  Tech Stack: [C++17/20, DPDK, Boost, custom allocator, Linux, Redis, gRPC]
  Short Tech Description: Ultra-low-latency matching engine with kernel-bypass networking and custom memory management. Uses lock-free structures and careful CPU pinning.
  Business Logic: Market data ingestion, order matching, risk checks, and clearing with microsecond latencies. Handles market making, order throttling, and audit trails.

- Big 2 — Rendering Subsystem for Game Engine
  Tech Stack: [C++, Vulkan/DirectX, GLSL/HLSL, SDL, CMake, Blender pipeline]
  Short Tech Description: Renderer module providing scene graph, GPU resource management, and shader pipeline. Integration with asset pipeline from Blender.
  Business Logic: Real-time rendering with culling, lighting, and post-processing for immersive scenes. Supports editor mode and runtime optimizations.

- Medium 1 — Cross-platform Desktop App (Qt)
  Tech Stack: [C++, Qt, SQLite, CMake]
  Short Tech Description: GUI application using Qt for native look-and-feel and SQLite for local storage. CMake build for multi-platform packaging.
  Business Logic: Productivity tool for data entry, reporting, and export. Offline-first UX with sync options.

- Medium 2 — Scientific Numeric Library
  Tech Stack: [C++, Eigen, OpenMP/CUDA optional, Boost.Test]
  Short Tech Description: High-performance numeric routines leveraging Eigen and optional GPU acceleration. Parallelized via OpenMP/CUDA where available.
  Business Logic: Provides linear algebra, solvers, and specialized kernels for simulations and ML prototyping. Offers accuracy modes and profiling.

18) C
- Big 1 — Embedded RTOS Firmware
  Tech Stack: [C (GCC), FreeRTOS, STM32 HAL, OpenOCD, CI]
  Short Tech Description: Real-time firmware using FreeRTOS tasks, semaphores, and interrupts for device control. Toolchain uses GCC and on-target debugging.
  Business Logic: Controls sensors/actuators with deterministic scheduling, safety checks, and firmware-over-the-air updates. Ensures fail-safe modes and logging.

- Big 2 — High-performance Network Stack
  Tech Stack: [C, Linux kernel modules / userspace networking, valgrind, CI]
  Short Tech Description: Custom TCP/UDP optimizations implemented in C with tun/tap or kernel module integration. Focus on throughput and minimal copy.
  Business Logic: Accelerates packet processing for proxies, VNFs, or high-throughput gateways. Implements QoS, flow steering, and per-flow stats.

- Medium 1 — System Monitoring CLI
  Tech Stack: [C, ncurses, procfs, Make/CMake]
  Short Tech Description: Terminal-based system monitor displaying CPU, memory, and process stats with ncurses UI. Uses low-level procfs for performance.
  Business Logic: Provides operators with near-real-time system state and alerting thresholds. Supports remote monitoring via SSH.

- Medium 2 — Parallel Compute App (OpenMP)
  Tech Stack: [C, OpenMP, GCC/Clang, Make]
  Short Tech Description: Multi-threaded numeric application using OpenMP pragmas to parallelize loops. Built with standard toolchains.
  Business Logic: Accelerated simulations or batch processing jobs that scale across CPU cores. Provides configuration for thread affinity and chunk sizes.

19) Flutter (Dart)
- Big 1 — Mobile Banking App
  Tech Stack: [Flutter, Dart, Firebase/Auth, GraphQL backend (Node/Go), CI/CD, SQLite for offline]
  Short Tech Description: Cross-platform mobile app using Flutter with secure auth and local encrypted storage. Sync with backend via GraphQL and push notifications.
  Business Logic: Account management, transfers, statements, and biometric login. Supports offline operations, scheduled payments, and fraud alerts.

- Big 2 — Field Workforce Platform
  Tech Stack: [Flutter, Riverpod/BLoC, GraphQL/Ktor backend, SQLite, AWS S3]
  Short Tech Description: Flutter app with offline-first sync and structured forms; backend in Kotlin or Node. Handles media uploads and conflict resolution.
  Business Logic: Dispatch tasks to field workers with checklists, signatures, and photo evidence. Includes scheduling, routing, and supervisor approvals.

- Medium 1 — Social App MVP
  Tech Stack: [Flutter, Firebase Firestore, Firebase Auth, Cloud Functions]
  Short Tech Description: Rapid MVP with Firebase backend for user auth, realtime feeds, and media storage. Flutter UI for iOS/Android.
  Business Logic: Create posts, follow users, like/comment, and push notifications. Moderation tools for community safety.

- Medium 2 — Widget Library & Themes
  Tech Stack: [Flutter, Dart, Provider/Riverpod, Unit Tests]
  Short Tech Description: Reusable widget collection with theming, accessibility, and demo app. Test coverage for components.
  Business Logic: Offers consistent UI components and design tokens for multiple mobile products. Supports theming and RTL.

20) Game Development
- Big 1 — Multiplayer Action Game (Unity)
  Tech Stack: [Unity (C#), Photon / Mirror, Blender assets, AWS GameLift, Docker, GitHub Actions]
  Short Tech Description: Unity client with authoritative server or managed multiplayer via Photon; asset pipeline from Blender. Deployable servers with GameLift.
  Business Logic: Fast-paced multiplayer matches with matchmaking, leaderboards, and in-game economy. Includes anti-cheat and reconnection mechanics.

- Big 2 — Realistic Demo (Unreal Engine)
  Tech Stack: [Unreal Engine (C++/Blueprints), Substance Painter, Perforce, NVIDIA RTX features]
  Short Tech Description: High-fidelity demo using Unreal's rendering, physics, and cinematic tools. Asset pipeline and source control.
  Business Logic: Showcases advanced lighting, AI behavior, and interactive sequences for an immersive demo experience. Supports cutscenes and player-driven choices.

- Medium 1 — 2D Indie Game (Godot)
  Tech Stack: [Godot, GDScript/C#, Tiled, Git]
  Short Tech Description: Lightweight 2D game built in Godot with tilemap levels and scripting. Easy cross-platform exports.
  Business Logic: Playable demo with levels, scoring, and simple progression. Includes level editor for designers.

- Medium 2 — Level Editor Tool
  Tech Stack: [Unity Editor Tools/C#, Electron for web export, SQLite]
  Short Tech Description: Desktop/editor plugin to design levels, place assets, and export runtime packs. Uses SQLite to store maps.
  Business Logic: Designers create and test maps, save presets, and publish level packs. Supports validation and asset usage reports.

21) Video (processing, optimization, algorithms)
- Big 1 — Video Streaming & Transcoding Platform
  Tech Stack: [FFmpeg, Kubernetes, Kafka, S3/Cloud Storage, CDN (CloudFront), React, Node.js]
  Short Tech Description: Ingests uploads and performs transcoding pipelines with FFmpeg in scalable workers on Kubernetes. Store variants in S3 and deliver via CDN.
  Business Logic: Converts incoming videos into multi-bitrate HLS/DASH formats, generates thumbnails and metadata. Supports live and VOD workflows with DRM options.

- Big 2 — Sports Video Analytics
  Tech Stack: [PyTorch, OpenCV, FFmpeg, Kafka, PostgreSQL, React]
  Short Tech Description: Computer-vision models detect players, actions, and events; FFmpeg for pre/post-processing. Results streamed via Kafka to dashboards.
  Business Logic: Automatically tag and index plays, generate highlights, and provide advanced metrics for coaches. Enables searchable event timelines and automated highlight reels.

- Medium 1 — Desktop Video Compressor
  Tech Stack: [Electron, FFmpeg, React, Native bindings]
  Short Tech Description: Cross-platform desktop app wrapping FFmpeg for user-friendly compression presets and batch processing. GUI built with Electron.
  Business Logic: Users compress, transcode, and trim videos for web or mobile with preset profiles. Supports watch folders and automated export.

- Medium 2 — Web-based Clip Editor
  Tech Stack: [WebAssembly (FFmpeg/WASM), React, WebRTC, IndexedDB]
  Short Tech Description: Browser-based trimming, concatenation, and basic effects using FFmpeg compiled to WASM. Client-side editing with offline storage.
  Business Logic: Lightweight editor for creating short clips and memes without server uploads. Exports optimized files for social platforms.

22) Compilers / Language Tech
- Big 1 — Full Language Compiler (LLVM backend)
  Tech Stack: [OCaml/Rust/C++, LLVM, Lex/Yacc or ANTLR, CI]
  Short Tech Description: Frontend with lexer/parser, semantic analysis, IR, and LLVM codegen supporting JIT/AOT. Tooling for optimizations and debugging.
  Business Logic: Compiles custom domain-specific language into efficient machine code or JITs for REPL use. Targets performance-critical domains and supports cross-platform builds.

- Big 2 — Static Analysis & Linter Platform
  Tech Stack: [Rust/Go, Clang tooling, AST frameworks, React]
  Short Tech Description: Static analysis engine that parses code, runs dataflow and taint analyses, and surfaces security/quality findings. Web UI for rule management.
  Business Logic: Detects defects, security vulnerabilities, and style regressions across codebases. Integrates into CI to block risky PRs and provide remediation hints.

- Medium 1 — Bytecode VM
  Tech Stack: [C++/Rust, Custom bytecode, JIT optional]
  Short Tech Description: Lightweight stack machine or register VM executing compiled bytecode with GC and debugger hooks. Optionally supports JIT via LLVM.
  Business Logic: Executes programs from DSLs or educational languages with introspection and step-by-step debugging. Useful for sandboxes and teaching.

- Medium 2 — DSL for ETL
  Tech Stack: [Python/Scala, ANTLR, Spark connector, Web UI]
  Short Tech Description: Small declarative DSL parsed into Spark jobs for ETL pipelines. Editor and preview UI for transformations.
  Business Logic: Let analysts write high-level ETL specs that compile to scalable jobs. Handles joins, aggregations, and windowing with execution plans.

23) Big Data & ETL
- Big 1 — Real-time Analytics Platform
  Tech Stack: [Kafka, Flink/Spark Streaming, Hive/Trino, Snowflake/Databricks, Airflow, Kubernetes]
  Short Tech Description: Streaming ingestion via Kafka processed by Flink for low-latency aggregation; batch jobs on Spark and Trino for ad-hoc queries. Orchestration via Airflow.
  Business Logic: Provides real-time dashboards, alerting, and historical analytics for business metrics. Handles schema evolution, backfills, and data quality.

- Big 2 — Lakehouse on Cloud
  Tech Stack: [S3, Delta Lake/Iceberg, Databricks, Glue, Athena, Terraform]
  Short Tech Description: Central data lake with transactional table formats and compute clusters for analytics and ML. Cataloging and governance via Glue and Terraform-managed infra.
  Business Logic: Unified storage for raw and curated datasets enabling analytics, ML, and reporting. Implements data lineage, access controls, and cost management.

- Medium 1 — ETL Orchestrator
  Tech Stack: [Airflow, dbt, Python, Postgres, Docker]
  Short Tech Description: Airflow DAGs trigger dbt models for transformations; Python tasks handle custom ingestion. Containerized for reproducibility.
  Business Logic: Periodic ingestion from sources, transformations, and publishing to a reporting schema. Provides testing, documentation, and dependency graphs.

- Medium 2 — Streaming Ingestion Service
  Tech Stack: [Kafka Connect, Debezium, PostgreSQL, S3 sink]
  Short Tech Description: Incremental change capture using Debezium feeding Kafka and connectors to downstream sinks. Configurable transforms and schema registry.
  Business Logic: Mirror transactional DB changes into analytic stores and data lake. Enables near-real-time ETL with exactly-once semantics where possible.

24) Blockchain / Web3
- Big 1 — DeFi Protocol & Frontend
  Tech Stack: [Solidity (OpenZeppelin), Hardhat, Ethers.js, React, The Graph, IPFS, Node.js]
  Short Tech Description: Smart contracts for lending/pools with audited patterns, front-end dApp interacting via Ethers.js, indexing via The Graph. Unit and security tests run in Hardhat.
  Business Logic: Users supply/borrow assets, earn interest, and manage collateral with liquidation rules. Governance and tokenomics built-in with on-chain proposals.

- Big 2 — Permissioned Supply Chain DLT
  Tech Stack: [Hyperledger Fabric, Go/Node chaincode, React, CouchDB, Docker Swarm/K8s]
  Short Tech Description: Private blockchain network managing provenance with chaincode for asset lifecycle. Frontend for stakeholders and ledger explorers.
  Business Logic: Track goods from origin to delivery with immutable records and access controls. Supports audits, dispute resolution, and KYC integrations.

- Medium 1 — NFT Marketplace
  Tech Stack: [Solidity, OpenZeppelin, IPFS/Pinata, Next.js, Ethers.js, Node.js]
  Short Tech Description: Minting and trading smart contracts with metadata stored on IPFS; Next.js storefront for browsing and auctions. Wallet integration via MetaMask.
  Business Logic: Create, list, bid, and transfer NFTs with royalties, auctions, and royalty splits. Admin tools for moderation and collection curation.

- Medium 2 — Crypto Wallet (Web + Mobile)
  Tech Stack: [React, React Native, Ethers.js/web3.js, Secure enclave integration]
  Short Tech Description: Multi-chain wallet UI for key management with encrypted local storage and optional hardware wallet support. Transaction signing flows and gas estimation.
  Business Logic: Manage addresses, send/receive tokens, view balances and transaction history. Supports token swaps via integrated DEX aggregators.

25) GIS / Geospatial
- Big 1 — Spatial Data Platform
  Tech Stack: [PostGIS, GeoServer, Mapbox, React, Kafka, Docker, Elasticsearch]
  Short Tech Description: Store spatial data in PostGIS, serve tiles and WMS via GeoServer, and render via Mapbox on the frontend. Index spatial search in Elasticsearch.
  Business Logic: Manage large geospatial datasets, perform spatial queries, and serve interactive maps with overlays. Supports user-uploaded layers and spatial analytics.

- Big 2 — Real-time Fleet Tracking
  Tech Stack: [Node.js, PostGIS, Redis, Mapbox/Leaflet, WebSockets, Docker]
  Short Tech Description: Real-time position ingestion via WebSockets, store tracks in PostGIS, and display live positions on map front-end. Redis for session and geofencing caches.
  Business Logic: Track vehicles, compute ETA, geofence alerts, and historical route playback. Includes dispatching and anomaly detection for routes.

- Medium 1 — Map Visualization App
  Tech Stack: [Leaflet, Node.js/Express, PostGIS, React]
  Short Tech Description: Web app to visualize spatial layers, query features and draw geometries. Backend serves GeoJSON and tile endpoints.
  Business Logic: Display thematic maps, filter data by attributes, and export selected areas. Supports user annotations and sharing.

- Medium 2 — Geoprocessing Pipeline
  Tech Stack: [GDAL, Python, QGIS, PostGIS]
  Short Tech Description: Batch geoprocessing scripts using GDAL for reprojection, raster/vector transforms and loading into PostGIS. QGIS for visual QA.
  Business Logic: Convert source data into standardized coordinate systems, compute spatial indices and produce tiling for maps. Automates ETL for GIS datasets.

26) Finance (Loan origination, trading, risk)
- Big 1 — Algo Trading Platform with Backtest
  Tech Stack: [Python (Pandas), C++ execution engine, Kafka, PostgreSQL, React, Docker, Kubernetes]
  Short Tech Description: Strategy research in Python with fast execution via C++ low-latency bridges; event bus with Kafka and backtesting harness. Dashboard for strategy metrics.
  Business Logic: Support strategy lifecycle from research to live: backtest, paper trade, and live execution with slippage and risk controls. Portfolio and position management with order routing.

- Big 2 — Loan Origination & Servicing System
  Tech Stack: [Java/Spring Boot, Angular/React, PostgreSQL, Kafka, Redis, Docker, KYC integrations]
  Short Tech Description: End-to-end loan lifecycle management with application, underwriting rules engine, and servicing modules. Integrates with credit bureaus and payment gateways.
  Business Logic: Accept loan applications, run credit scoring and decision logic, and manage repayment schedules. Handles defaults, restructuring, collections, and audit trails.

- Medium 1 — Risk Dashboard (VaR)
  Tech Stack: [Python, NumPy, SciPy, Dash/React, PostgreSQL]
  Short Tech Description: Compute daily VaR and stress scenarios with Python numeric stacks; present dashboards with time-series and scenario drill-down. Exportable reports.
  Business Logic: Calculate portfolio risk exposures and capital requirements with configurable models. Supports scenario analysis and risk attribution.

- Medium 2 — Portfolio Rebalancer Service
  Tech Stack: [C#/Java/Python microservice, Postgres, React admin]
  Short Tech Description: Service computes target allocations and issues rebalancing trades, includes transaction cost optimizations. UI for manual overrides and scheduling.
  Business Logic: Periodically rebalances portfolios according to policy, considering tax, trading costs, and liquidity constraints. Generates trade instructions and compliance logs.

27) Assembly / Low-level
- Big 1 — Educational CPU Emulator & Assembler
  Tech Stack: [C/C++, Qt or web frontend, NASM-compatible assembler, Debugger UI]
  Short Tech Description: Emulator simulating registers, instruction decoding, and memory; assembler for a simple instruction set. GUI for step debugging and visualization.
  Business Logic: Teach CPU architecture and assembly by letting users write, assemble, and run programs with breakpoints and register views. Includes lab exercises and performance metrics.

- Big 2 — Optimizing Compiler Backend
  Tech Stack: [LLVM IR, C++/Rust, custom register allocator, unit tests]
  Short Tech Description: Backend that lowers IR to optimized assembly with register allocation and peephole optimizations. Supports multiple target ISAs.
  Business Logic: Produce efficient assembly for hotspots and minimize instruction count and memory access. Useful for domain-specific languages and embedded targets.

- Medium 1 — Bootloader / Tiny OS Kernel
  Tech Stack: [Assembly (x86/ARM), C, QEMU, Make]
  Short Tech Description: Small bootloader that initializes hardware and loads a minimal kernel; basic scheduler and system calls in C with assembly startup.
  Business Logic: Boot sequence, hardware initialization, and syscall handling for educational OS features. Demonstrates context switching and interrupt handling.

- Medium 2 — Hand-optimized Crypto Routines
  Tech Stack: [Assembly (x86_64/ARM Neon), C wrappers, unit tests]
  Short Tech Description: Assembly-optimized implementations for AES, SHA, or modular arithmetic with C APIs and test harness. Uses SIMD and pipeline-friendly code.
  Business Logic: Speed up cryptographic primitives for TLS or blockchain workloads. Fallback to portable C when not supported.

28) Medicine / Health Tech
- Big 1 — FHIR-based EHR System
  Tech Stack: [Java Spring Boot / Node.js, FHIR server (HAPI/FHIR), React, DICOM viewer (OHIF), PostgreSQL, Docker, OAuth2]
  Short Tech Description: Implements FHIR resources and APIs for clinical data, with integrated DICOM viewer for imaging. Frontend React app for clinicians and role-based auth.
  Business Logic: Manage patient records, appointments, orders, and imaging with consent and audit trails. Supports interoperability and data exchange via FHIR endpoints.

- Big 2 — Drug Discovery Platform with AlphaFold Integration
  Tech Stack: [Python, AlphaFold model integration, PyTorch/TensorFlow, PostgreSQL, React, Kubernetes]
  Short Tech Description: Pipeline to run structure predictions, score compounds, and track experiments; orchestrated in Kubernetes. Visualization of protein structures in frontend.
  Business Logic: Prioritize candidate molecules by predicted binding and stability, provide experiment metadata, and track in-silico testing cycles. Supports collaboration and reproducible results.

- Medium 1 — DICOM PACS Viewer (Web)
  Tech Stack: [DICOM toolkit, OHIF viewer, Node.js, PostgreSQL, React]
  Short Tech Description: Web-based PACS for storing and viewing DICOM series with zoom, windowing, and measurements. Backend handles DICOM C-STORE ingestion.
  Business Logic: Radiologists view, annotate, and share images; attachments saved in patient record. Supports access control and audit logging for compliance.

- Medium 2 — CRISPR Simulation Sandbox
  Tech Stack: [Python, Jupyter, BioPython, React (dashboard), Docker]
  Short Tech Description: Simulation environment modeling CRISPR edits, off-target probabilities, and sequence constraints. Notebook workflows for scientists and a simple dashboard.
  Business Logic: Evaluate guide RNA choices, simulate edit outcomes, and estimate off-target risks. Helps design experiments and track results.

29) Kotlin
- Big 1 — Android + Multiplatform App
  Tech Stack: [Kotlin Multiplatform, Android (Jetpack Compose), iOS (Kotlin/Native), Ktor backend, GraphQL, PostgreSQL]
  Short Tech Description: Shared Kotlin code for business logic across Android and iOS; Jetpack Compose UI on Android and native UI on iOS. Backend in Ktor serving GraphQL APIs.
  Business Logic: Consumer-facing app with synchronized user data, offline caching, and push notifications. Includes subscription billing and analytics.

- Big 2 — High-performance Backend (Ktor)
  Tech Stack: [Ktor, Coroutines, Exposed (ORM), PostgreSQL, Docker, gRPC]
  Short Tech Description: Coroutine-based Kotlin microservices using Ktor for async endpoints and Exposed for DB access. gRPC for inter-service comms and Docker deployment.
  Business Logic: Serves as backend for transactional services with high concurrency and backpressure handling. Implements domain workflows and audit logging.

- Medium 1 — Desktop App (Compose for Desktop)
  Tech Stack: [Kotlin, Compose for Desktop, SQLite, Gradle]
  Short Tech Description: Cross-platform desktop UI using Jetbrains Compose; local persistence in SQLite. Packaged with Gradle.
  Business Logic: Productivity or analytics tool with offline capabilities and sync to server. Supports templating and export.

- Medium 2 — REST API with Spring Boot & Kotlin
  Tech Stack: [Spring Boot (Kotlin), Spring Data, PostgreSQL, Docker, JUnit]
  Short Tech Description: Kotlin-first Spring Boot REST service leveraging Spring Data and Kotlin idioms. Tests with JUnit and MockK.
  Business Logic: CRUD APIs with business validations, batch endpoints, and role-based access. Integrates with external services and processes scheduled jobs.

If you want, I can:
- Expand any given project into detailed architecture, endpoints, DB schema, and tasks.
- Generate README, Dockerfiles, CI pipelines, and sample code scaffolding for specific projects. Which topic/project should I expand first?