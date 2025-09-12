I will deliver 4 projects per topic (2 big, 2 medium) as requested. Each project lists a one-line tech stack, a short technical description (≥2 sentences), and business logic (≥2 sentences). I keep formatting simple: numbered topic, then four projects named Big-1, Big-2, Med-1, Med-2. Where a web front-end is relevant I include it. For AWS items EKS/Kubernetes is included as required. Due to the volume I keep each sentence concise and focused.

1) Java (Spring Boot, Jakarta EE, Hibernate, Maven/Gradle, JUnit, Micronaut, Quarkus, Vert.x, GraalVM (JIT+Native-Image), SLF4J)
- Big-1: Enterprise Order Management System
  Tech Stack: Spring Boot, Hibernate, PostgreSQL, Kafka, Redis, React, TypeScript, Maven, Docker, Kubernetes (EKS), Grafana, Jaeger, JUnit.
  Short Tech Description: Microservices-based Spring Boot backend with Hibernate for ORM and Kafka for eventing. Frontend SPA in React and TypeScript integrates via REST/gRPC; observability via Grafana and Jaeger; CI/CD with Maven pipelines and container images.
  Business Logic: Handles order lifecycle, inventory reservations, payments, and fulfillment with transactional integrity and eventual consistency across services. Supports high throughput with partitioned Kafka topics, optimistic locking and compensating transactions for failure recovery.
- Big-2: Real-time Risk Scoring Platform
  Tech Stack: Quarkus, Vert.x, PostgreSQL, Redis, ElasticSearch, gRPC, Angular, Gradle, Prometheus, Grafana, GraalVM native-image.
  Short Tech Description: Low-latency Quarkus services using Vert.x event loop for async processing and gRPC for internal comms. Native-image builds via GraalVM for fast cold starts and reduced memory footprint; search and analytics via Elasticsearch.
  Business Logic: Ingests streams of events, computes risk scores with feature pipelines, persists results, and serves low-latency scoring APIs for downstream systems. Implements rules, ML model inference caching, and circuit breakers for resilience.
- Med-1: Multi-tenant Billing API
  Tech Stack: Micronaut, Hibernate, MySQL, Redis, React, Maven, Docker, JUnit, SLF4J.
  Short Tech Description: Micronaut-based API with multitenancy support using schema-per-tenant or discriminator strategies. Lightweight and testable services with JUnit and structured logging via SLF4J.
  Business Logic: Calculates invoices, applies tiered pricing, manages subscriptions, and supports invoice generation and delivery. Includes tenant isolation, rate limiting, and usage aggregation jobs.
- Med-2: Internal Metrics Collector & Dashboard
  Tech Stack: Spring Boot actuator, Prometheus client, PostgreSQL, Vue.js, Gradle, Docker.
  Short Tech Description: Spring Boot application exposing metrics and application health; metrics collected by Prometheus and visualized through a Vue.js dashboard. Gradle build and Docker containers for deployments.
  Business Logic: Collects application and business metrics, stores aggregates for trend analysis, and exposes dashboards and alerts for SRE and product teams. Supports role-based access and scheduled export of KPIs.

2) Multithreading / Concurrency (threads, locks, semaphores, thread pools, classic problems)
- Big-1: High-performance Trading Engine
  Tech Stack: Java (low-latency, Disruptor), Netty, Aeron, Chronicle Queue, In-memory data grid, Docker, Kubernetes.
  Short Tech Description: Lock-free data structures and ring buffers (Disruptor/Aeron) to achieve sub-millisecond latencies. Careful thread-affinity, bounded queues, and custom thread pools minimize context switching.
  Business Logic: Receives market data, matches orders, updates order books, and publishes execution reports under strict concurrency and ordering guarantees. Uses fine-grained synchronization and atomic operations to avoid race conditions and prioritize throughput.
- Big-2: Distributed Job Scheduler with Fault Tolerance
  Tech Stack: Java, Akka (or actor model), Zookeeper/Consul, PostgreSQL, Kubernetes, Prometheus.
  Short Tech Description: Actor-based concurrency for scheduling and executing jobs across nodes with leader election. Uses semaphores and distributed locks via Zookeeper to prevent double execution and supports work-stealing pools.
  Business Logic: Schedules recurring and ad-hoc jobs, coordinates execution on worker nodes, handles retries and deadlock detection, and provides observability into running tasks. Ensures fairness and prevents starvation with priority queues and quotas.
- Med-1: Producer-Consumer Streaming Worker
  Tech Stack: Java Executors, Kafka, Spring Boot, Docker, JUnit.
  Short Tech Description: Simple worker service consuming Kafka topics with configurable thread pool and back-pressure handling. Uses bounded queues and semaphores to coordinate producers and consumers safely.
  Business Logic: Consumes messages, processes transformations, writes results to DB, and retries on transient failures. Implements graceful shutdown, pause/resume, and message ordering guarantees per partition.
- Med-2: Concurrency Patterns Learning Toolkit
  Tech Stack: Java, Swing or JavaFX UI, JUnit, Maven.
  Short Tech Description: Desktop app demonstrating threading primitives (locks, semaphores, atomics) with visualizations of race conditions, deadlocks, dining philosophers, and sleeping barber. Includes unit tests and reproducible scenarios.
  Business Logic: Educates engineers by simulating concurrent scenarios and demonstrating solutions such as lock ordering and timeout-based deadlock prevention. Allows experimentation with thread pool sizing and observes starvation effects.

3) Python (Django, pytest, Flask, FastAPI, NumPy, Pandas, PyTorch, TensorFlow, Jupyter, Asyncio)
- Big-1: ML-backed E-commerce Recommendation Platform
  Tech Stack: FastAPI, Python, PostgreSQL, Redis, Celery, PyTorch, Pandas, React, Docker, Kubernetes.
  Short Tech Description: FastAPI microservices exposing recommendation APIs with PyTorch models for collaborative and content-based recommendations. Data pipelines use Pandas and Celery for feature extraction; caching via Redis.
  Business Logic: Aggregates user behavior and product data to generate personalized recommendations, real-time scoring, and A/B testing. Handles model training, versioning, and incremental updates with retraining pipelines.
- Big-2: Clinical Data Analytics Platform
  Tech Stack: Django, PostgreSQL, Pandas, NumPy, TensorFlow, JupyterHub, React, Docker, Kubernetes.
  Short Tech Description: Django backend for storing clinical records and running analytics; JupyterHub for data scientists to prototype models; TensorFlow for predictive models. Role-based access controls and audit logging for compliance.
  Business Logic: Ingests clinical and imaging metadata, runs cohort selection, extracts features, and predicts outcomes using ML models. Supports explainability reports, data access controls, and periodic model retraining.
- Med-1: Async API Gateway
  Tech Stack: FastAPI, AsyncIO, NGINX, PostgreSQL, Docker, pytest.
  Short Tech Description: High-concurrency edge service using FastAPI and AsyncIO for request multiplexing and proxying to backend services. Built-in rate limiting and circuit breaker middleware.
  Business Logic: Routes and aggregates microservice responses, enforces quotas and authentication, and performs response caching. Provides observability for latency and error rates.
- Med-2: Data ETL and Reporting Service
  Tech Stack: Flask, Pandas, SQLAlchemy, MySQL, Celery, Redis.
  Short Tech Description: Flask-based API to trigger ETL jobs implemented with Pandas; Celery handles background tasks and retries. Reports are rendered as PDFs/CSV and delivered by email.
  Business Logic: Extracts data from sources, transforms into analytics-ready tables, and loads into reporting DB. Schedules periodic jobs, handles incremental loads, and enforces data quality checks.

4) Machine Learning (Scikit-learn, TensorFlow, PyTorch, Keras, XGBoost, LightGBM, CatBoost, HuggingFace Transformers, LangChain, LangGraph & Langsmith)
- Big-1: End-to-end MLOps Platform
  Tech Stack: Python, Kubeflow, TensorFlow, PyTorch, MLflow, S3, PostgreSQL, Kubernetes, Airflow, Grafana.
  Short Tech Description: Pipeline orchestration with Kubeflow and Airflow, model tracking with MLflow, storage on S3, and deployment as KFServing/TFServing on Kubernetes. Supports experiments, lineage, and can host both TensorFlow and PyTorch models.
  Business Logic: Manages data ingestion, feature stores, model training, evaluation, deployment, monitoring, and rollback. Automates retraining triggers and drift detection, enabling production-grade ML lifecycle management.
- Big-2: Large Language Model Application Platform
  Tech Stack: HuggingFace Transformers, LangChain, LangGraph, Langsmith integrations, PyTorch, Redis, FastAPI, React, Kubernetes, GPU nodes.
  Short Tech Description: Platform to host, orchestrate, and chain LLM components using LangChain and LangGraph; Langsmith for evaluation and tracking. GPU-backed PyTorch inference with sharding and caching through Redis.
  Business Logic: Allows building conversational agents, retrieval-augmented generation (RAG), and multi-step pipelines combining LLMs and tools. Manages prompt templates, memory, and safety filters while tracking conversations and metrics.
- Med-1: Fraud Detection Service
  Tech Stack: Scikit-learn, XGBoost, Python, Kafka, PostgreSQL, Docker.
  Short Tech Description: Batch and streaming feature extractor feeding XGBoost models for fraud scoring; pipelines use scikit-learn for preprocessing. Kafka streams transactions to the scoring service for near real-time decisions.
  Business Logic: Scores transactions, flags suspicious activity, triggers workflows for review, and adapts thresholds per merchant. Supports feedback loop for labelled fraud cases to retrain models.
- Med-2: Image Classification API
  Tech Stack: PyTorch, FastAPI, Docker, S3, PostgreSQL.
  Short Tech Description: REST API that performs inference with PyTorch CNN models and returns predictions with confidence and bounding boxes. Includes image preprocessing and batching for throughput.
  Business Logic: Accepts uploads, processes images for classification and metadata extraction, stores predictions, and allows human-in-the-loop label correction to improve models. Provides model versioning and A/B testing endpoints.

5) AWS (EC2, S3, EKS (Kubernetes MUST), Lambda, RDS, DynamoDB, VPC, IAM, CloudFormation, CloudWatch, SNS/SQS, Bedrock, SageMaker)
- Big-1: Scalable SaaS on AWS with EKS
  Tech Stack: Java Spring Boot & Node.js microservices, React frontend, PostgreSQL (RDS), DynamoDB, S3, EKS (Kubernetes), CloudFormation/Terraform, ALB, CloudWatch, SNS/SQS, IAM, ECR.
  Short Tech Description: Multi-tenant SaaS deployed on EKS with Kubernetes autoscaling, RDS for relational data, and S3 for object storage. Infrastructure managed via CloudFormation/Terraform; observability via CloudWatch and Grafana.
  Business Logic: Manages onboarding, tenant isolation, subscription billing, role-based access, data backup, and disaster recovery. Handles asynchronous tasks via SQS and SNS, and secures resources using fine-grained IAM roles and VPC networking.
- Big-2: ML Platform with SageMaker and Bedrock
  Tech Stack: SageMaker for training, Bedrock for LLMs, S3, EKS for serving, Lambda for lightweight orchestration, RDS, CloudFormation, IAM.
  Short Tech Description: Uses SageMaker for large-scale training and model registry; Bedrock for managed LLM inference where available. Serving stack runs on EKS with autoscaling and model caching; Lambda triggers pipelines and lightweight tasks.
  Business Logic: Automates model training, validation, deployment, and monitoring; exposes model endpoints to applications with access control. Tracks costs and resource usage and scales GPU instances for heavy workloads.
- Med-1: Serverless Event-driven Workflow
  Tech Stack: Lambda, API Gateway, DynamoDB, S3, Step Functions, CloudWatch, SNS/SQS.
  Short Tech Description: Serverless architecture for event-driven workflows using Step Functions and Lambda with DynamoDB as state store. S3 holds payloads and CloudWatch monitors execution.
  Business Logic: Handles document processing pipeline where uploads trigger stateful workflows that perform validation, OCR, and notification. Retries and error handling implemented via Step Functions and DLQs.
- Med-2: Containerized Web App on EKS
  Tech Stack: Node.js backend, React frontend, EKS, RDS (Postgres), S3, ALB, CloudFormation, Prometheus on EKS.
  Short Tech Description: Standard containerized web app deployed to EKS with CI/CD pipelines using CodePipeline or GitHub Actions. Uses RDS for transactional data and S3 for static assets.
  Business Logic: Typical CRUD app with user management, roles, file uploads to S3, and audit logs. Implements multi-AZ RDS for high availability and automatic backups.

6) Databases (MySQL, PostgreSQL, Oracle, SQL Server, MongoDB, Redis, Cassandra, Elasticsearch, Snowflake, Neo4j, Pinecone, Weaviate, Milvus)
- Big-1: Multi-model Analytics Platform
  Tech Stack: PostgreSQL (OLTP), Snowflake (analytics), Kafka, Elasticsearch, Redis, Python ETL, React, Kubernetes.
  Short Tech Description: Hybrid architecture separating transactional and analytical workloads with PostgreSQL for OLTP and Snowflake for analytics. ETL pipelines move processed data into search indexes (Elasticsearch) and vector stores if needed.
  Business Logic: Supports real-time dashboards, complex analytics, full-text search, and ML-ready datasets. Ensures eventual consistency and provides role-based access to analytical data.
- Big-2: Knowledge Graph & Semantic Search
  Tech Stack: Neo4j, Milvus/Pinecone vector DB, Elasticsearch, Python, FastAPI, React.
  Short Tech Description: Combines property graph capabilities (Neo4j) with vector similarity search (Milvus/Pinecone) and textual search (Elasticsearch) for rich semantic queries. API layer unifies graph traversals and vector retrieval.
  Business Logic: Stores entities and relationships, enriches content with embeddings, and supports semantic queries like “find experts connected to topic X.” Enables recommendations, discovery, and relationship analytics.
- Med-1: High-throughput Time Series Store
  Tech Stack: Cassandra (or TimescaleDB on PostgreSQL), Redis for caching, Grafana, Java services.
  Short Tech Description: Distributed Cassandra cluster for ingesting high write rates of time-series data with retention policies. Redis for hot reads and Grafana for visualization.
  Business Logic: Collects telemetry, aggregates metrics, supports rollups and downsampling, and serves dashboards and alerts. Ensures efficient compaction and TTL-based data lifecycle.
- Med-2: Document Management with Search
  Tech Stack: MongoDB, Elasticsearch, Node.js, React, S3.
  Short Tech Description: MongoDB stores document metadata and JSON content while Elasticsearch provides fast full-text search and aggregation. Node.js API coordinates ingestion, indexing, and retrieval.
  Business Logic: Indexes documents for search, manages versions and access controls, supports faceted search and preview generation. Implements sync between source DB and search index with resiliency to failures.

7) Algorithms (Sorting, Searching, Graphs, DP, Greedy, Divide-and-Conquer, Backtracking, Strings, Computational Geometry)
- Big-1: Route Optimization & Logistics System
  Tech Stack: Java/C++ for core algorithms, PostgreSQL with PostGIS, React, Docker, Kubernetes.
  Short Tech Description: Core routing engine implemented in C++ with Dijkstra/A*, heuristics, and DP for vehicle routing problem (VRP). Spatial data handled via PostGIS and optimized geometry algorithms.
  Business Logic: Optimizes multi-vehicle deliveries under time windows, capacities, and costs while providing route adjustments in real-time. Supports re-optimization when new orders arrive and constraints change.
- Big-2: Graph Analytics Platform
  Tech Stack: Scala/Spark GraphX, Neo4j, Python, React, Kubernetes.
  Short Tech Description: Large-scale graph processing using GraphX and batch algorithms for centrality, community detection, and shortest paths. Interactive graph queries served by Neo4j for ad-hoc analysis.
  Business Logic: Analyzes network structures for fraud rings, social graphs, and supply chain dependencies. Supports scheduled batch jobs and interactive exploration for analysts.
- Med-1: Advanced Search Engine Component
  Tech Stack: C++/Java, Trie and suffix array implementations, Elasticsearch integration.
  Short Tech Description: Implements efficient string algorithms for autocomplete, fuzzy matching, and substring search using tries, suffix arrays, and BK-trees. Integrates with Elasticsearch for indexing pipelines.
  Business Logic: Provides highly responsive search suggestions and fuzzy matching, ranks and filters results by relevance and business rules. Reduces latency for large corpora with memory-optimized structures.
- Med-2: Puzzle Solver Toolkit
  Tech Stack: Python, backtracking and DP implementations, simple Flask UI.
  Short Tech Description: Toolkit implementing backtracking (e.g., N-Queens), DP (knapsack), and greedy algorithms with visual step-throughs. Flask UI allows users to input problem parameters and observe solutions.
  Business Logic: Educational tool to demonstrate algorithm performance, complexity, and solution verification. Includes benchmarking and comparative charting for algorithm choices.

8) JavaScript (ES6+, Next.js, TypeScript, Node.js, React, Vue, Angular, Express, Jest, Webpack, Babel, Vite, Svelte)
- Big-1: Global eCommerce Platform
  Tech Stack: Next.js (React), Node.js microservices, TypeScript, PostgreSQL, Redis, Elasticsearch, Kubernetes, Stripe integration.
  Short Tech Description: Server-side rendered Next.js storefront for SEO and performance with TypeScript and React. Backend microservices in Node.js/Express handle cart, checkout, and inventory; search via Elasticsearch.
  Business Logic: Manages catalogs, localized pricing, promotions, order lifecycle, and payment processing. Provides scalability via stateless services, caching strategies, and CDN for assets.
- Big-2: Real-time Collaboration Suite
  Tech Stack: React, WebSockets/Socket.io, Node.js, CRDTs/OT algorithms, TypeScript, MongoDB.
  Short Tech Description: Real-time collaborative editors using CRDTs or Operational Transforms to merge concurrent edits. Backend Node.js manages sessions, presence, and persistence.
  Business Logic: Supports simultaneous editing with conflict-free merging, user presence, undo/redo semantics, and access control. Ensures eventual consistency and offline sync.
- Med-1: Admin Dashboard Boilerplate
  Tech Stack: Vue.js (or React), TypeScript, Vite, Node.js API, Jest, Tailwind CSS.
  Short Tech Description: Reusable admin UI components, auth flows, and data tables built with Vue/React and TypeScript. Vite for fast dev builds and Jest for unit testing.
  Business Logic: Provides CRUD operations, role-based UI visibility, and export/import of data. Simplifies integration with backend APIs and common dashboard patterns.
- Med-2: Serverless API with Next.js
  Tech Stack: Next.js API routes, TypeScript, Vercel or AWS Lambda, Prisma, SQLite/Postgres.
  Short Tech Description: Lightweight API endpoints deployed serverless with Next.js functions and TypeScript, using Prisma ORM to access DB. Fast deployment and automatic scaling.
  Business Logic: Exposes business endpoints for a single-page app, handles auth, validation, and rate limiting. Keeps cold-start impact low via caching and optimized functions.

9) Web Design (HTML5, Bulma, Sass, Bootstrap, Tailwind CSS, Figma, Adobe XD, Sketch, Webflow)
- Big-1: Design System & Component Library
  Tech Stack: Figma for design, Storybook, React, TypeScript, Tailwind CSS, Sass, Jest.
  Short Tech Description: Centralized design tokens and component library built from Figma specs and exposed via Storybook; themeable with CSS variables and Tailwind utilities. Includes accessibility checks and visual regression tests.
  Business Logic: Standardizes UI across products to reduce design debt, accelerate front-end development, and ensure consistency. Supports theming per brand and exportable design assets for product teams.
- Big-2: Marketing Website Builder (No-code)
  Tech Stack: React, Next.js, Webflow-like editor, Tailwind CSS, Node.js backend, S3.
  Short Tech Description: Drag-and-drop web page builder with WYSIWYG editor, components, and template management mirroring Webflow functionality. Persisted pages are exported as static assets to S3/CDN.
  Business Logic: Allows marketing teams to create pages without dev help, manage versions, and publish to production instantly. Handles SEO metadata, image optimization, and A/B testing variants.
- Med-1: Responsive UI Kit
  Tech Stack: HTML5, Tailwind CSS, Bootstrap, Sass, Figma source files.
  Short Tech Description: Pre-built responsive components and templates with variant examples and Figma design files. Includes accessible form controls and grid utilities.
  Business Logic: Accelerates prototyping and implementation for product teams, enforces brand styles, and reduces front-end dev time. Supports export to static HTML for quick landing pages.
- Med-2: Landing Page Funnel
  Tech Stack: Webflow, custom JS, Google Analytics, Mailchimp integration.
  Short Tech Description: High-conversion landing pages built in Webflow with integrated analytics and lead capture forms. Light-weight JavaScript for dynamic behaviors and A/B testing.
  Business Logic: Captures leads, measures conversion funnels, integrates with marketing automation, and supports rapid iteration based on analytics. Optimized for SEO and performance.

10) Security (Metasploit, Kali Linux, Burp Suite, nmap, Wireshark, OWASP ZAP, Cryptography, Malware Analysis, Web App Security)
- Big-1: Enterprise App Security Platform
  Tech Stack: Python, Docker, Kubernetes, OWASP ZAP automation, Burp Suite integration, Elastic Stack, Vault for secrets.
  Short Tech Description: Centralized platform automating static (SAST) and dynamic (DAST) scanning, vulnerability aggregation, and prioritization. Integrates results into CI pipelines and dashboards via Elastic.
  Business Logic: Scans code and running apps, correlates findings with asset inventory, triages vulnerabilities by risk and business impact, and triggers workflows for remediation. Stores secrets securely and tracks compliance status.
- Big-2: Threat Hunting & Network Monitoring Suite
  Tech Stack: ELK/Elastic Stack, Suricata, Zeek, Kafka, Python enrichment, Grafana.
  Short Tech Description: Collects network telemetry and IDS alerts, enriches events, and indexes into Elastic for searches and detection rules. Enables SOC analysts to hunt and pivot across indicators.
  Business Logic: Detects anomalous network behavior, flags suspicious flows, and automates alerting and case management. Supports threat intelligence feeds and playbook-driven response via integrations.
- Med-1: Web App Penetration Toolkit
  Tech Stack: Kali tooling, Burp Suite, OWASP ZAP, nmap, Python exploit scripts.
  Short Tech Description: Toolkit and automation scripts to perform authenticated scanning, fuzzing, and payload testing. Includes reporting templates and remediation guidance mapped to OWASP Top 10.
  Business Logic: Performs periodic pen-tests and continuous security checks on staging environments, produces prioritized findings, and verifies fixes. Runs safe scans to avoid disruption and uses authenticated sessions to increase coverage.
- Med-2: Cryptography-as-a-Service Module
  Tech Stack: Go or Java, HashiCorp Vault, HSM integration, TLS, REST API.
  Short Tech Description: Centralized crypto module offering key management, signing, encryption, and rotation backed by Vault or HSM. Exposes uniform APIs for developers to avoid ad-hoc cryptography.
  Business Logic: Provides secure key storage, enforces usage policies and audit logging, and enables transparent encryption for sensitive data. Simplifies compliance and key lifecycle management for applications.

11) DevOps (Jenkins, GitHub Actions, Docker, K8s, Ansible, Terraform, Prometheus, Grafana, ELK)
- Big-1: Enterprise CI/CD & GitOps Platform
  Tech Stack: GitHub Actions/ArgoCD, Jenkins for legacy jobs, Terraform, Kubernetes (EKS/AKS/GKE), Docker, Prometheus, Grafana, Loki, ELK.
  Short Tech Description: GitOps-driven deployments using ArgoCD and declarative Terraform for infra provisioning. CI pipelines in GitHub Actions with Jenkins bridging legacy processes; observability via Prometheus and Grafana.
  Business Logic: Automates build, test, and deploy with compliance gates and rollback strategies, supports multi-cluster deployments, and enforces policy as code. Provides audit trails and performance monitoring for SRE teams.
- Big-2: Infrastructure Automation & Secrets Management
  Tech Stack: Terraform, Ansible, Vault, Kubernetes, AWS, Docker.
  Short Tech Description: IaC-driven infrastructure provisioning with Terraform; configuration management with Ansible; secrets handled by Vault; containerized workloads on Kubernetes. Reusable modules and testing frameworks.
  Business Logic: Enables reproducible infra builds, configuration drift detection, and secure secret distribution to workloads. Supports blue-green deployments and infrastructure drift remediation.
- Med-1: Observability & Alerting Stack
  Tech Stack: Prometheus, Grafana, Alertmanager, Jaeger, Loki, Kubernetes.
  Short Tech Description: Metrics collection with Prometheus, distributed tracing with Jaeger, log aggregation with Loki, dashboards in Grafana, and alerting rules in Alertmanager. Kubernetes-native deployment for scalability.
  Business Logic: Tracks SLIs/SLOs, triggers alerts for threshold breaches, and provides root-cause analysis using traces and logs. Integrates paging and runbook links to improve incident resolution.
- Med-2: Dockerized App CI Pipeline
  Tech Stack: GitHub Actions, Docker, Docker Compose, Snyk scanning, Jest/Mocha tests.
  Short Tech Description: Simple container build and test pipeline that builds images, runs unit/integration tests, scans for vulnerabilities, and pushes images to a registry. Uses Compose for local integration testing.
  Business Logic: Ensures images are tested and security-scanned before promotion to staging; automates tagging and semantic versioning. Reduces human error and accelerates delivery.

12) C# (.NET 8, ASP.NET Core, Blazor, Entity Framework, LINQ, MAUI, WPF, NUnit)
- Big-1: Enterprise CRM on .NET
  Tech Stack: .NET 8, ASP.NET Core, Blazor frontend, Entity Framework Core, SQL Server, Redis, Kubernetes on AKS, Azure DevOps.
  Short Tech Description: Monolithic or microservices architecture using ASP.NET Core services and Blazor for a rich client UI. EF Core for data access, Redis for caching, and Kubernetes for scalable hosting.
  Business Logic: Manages accounts, contacts, pipelines, activity logging, and reporting; integrates with email and telephony providers. Implements role-based access and audit trails for compliance.
- Big-2: Cross-platform Mobile App with Backend
  Tech Stack: .NET MAUI, ASP.NET Core Web API, SQLite/mobile sync, Azure Functions, SignalR.
  Short Tech Description: MAUI for native mobile apps across iOS/Android with offline-first sync to backend via Web API and SignalR for realtime updates. Backend uses ASP.NET Core and optional serverless functions.
  Business Logic: Provides synced tasks, messaging, and notifications with conflict resolution on sync. Supports push notifications and background sync policies.
- Med-1: Admin Portal with Blazor
  Tech Stack: Blazor Server, Entity Framework Core, SQL Server, NUnit for tests.
  Short Tech Description: Blazor Server-based admin UI with reusable components, EF Core for data interaction, and server-side rendering for rapid development. Unit and integration tests via NUnit.
  Business Logic: CRUD operations, role management, audit logs, and batch operations for administrative tasks. Supports export of data and scheduled reports.
- Med-2: Desktop Tool for Data Analysis
  Tech Stack: WPF, .NET 8, SQLite, LINQ, MVVM pattern, NUnit.
  Short Tech Description: WPF desktop application with MVVM for interactive data analysis and visualization; SQLite for local stores and LINQ for queries. Modular UI components and plugin support.
  Business Logic: Imports datasets, applies filters and aggregations, and visualizes time series and charts. Enables users to save workspaces and export analysis.

13) Go (Goroutines, Channels, net/http, Gin, GORM or sqlx, Go Modules, gRPC, Docker, Testify)
- Big-1: High-throughput API Gateway in Go
  Tech Stack: Go, Gin, gRPC, Envoy sidecar, Redis, PostgreSQL, Kubernetes, Prometheus.
  Short Tech Description: Go-based gateway with lightweight goroutine concurrency, gRPC support for backend services, and pluggable middleware. Uses channels and worker pools to manage asynchronous tasks.
  Business Logic: Routes requests, enforces auth and quotas, does protocol translations, and provides rate limiting. Designed for high throughput and low latency with graceful degradation.
- Big-2: Distributed Tracing Collector
  Tech Stack: Go, Kafka, Thrift/Protobuf, PostgreSQL, Elasticsearch, Docker.
  Short Tech Description: Collector for distributed tracing data faster than interpreted languages; buffering using channels and batched writes to persistence layers. Efficient memory usage and back-pressure handling.
  Business Logic: Receives traces, aggregates spans, builds service dependency graphs, and provides search capabilities for traces and logs. Implements TTL and archival for old traces.
- Med-1: Lightweight Microservice
  Tech Stack: Go, net/http or Gin, sqlx with PostgreSQL, Docker, Testify.
  Short Tech Description: Small stateless microservice written in Go using idiomatic goroutines and channels where appropriate. Clean architecture with tests via Testify and easy Dockerization.
  Business Logic: Exposes REST endpoints for domain operations, persists data into Postgres, and supports health checks and metrics. Designed for fast startup and low CPU/memory footprint.
- Med-2: CLI Tool for DB Migrations
  Tech Stack: Go, Cobra CLI, sqlx, CI integration.
  Short Tech Description: Native CLI allowing database migrations, rollbacks, and seeding with concurrency-safe locking. Single binary distribution and idempotent operations.
  Business Logic: Applies schema changes in order, records applied migrations, and prevents concurrent migration runs with advisory locks. Useful in deployment automation.

14) Rust (Cargo, Rustup, crates.io, Tokio, WebAssembly, Actix, Rocket, Diesel, Serde)
- Big-1: Low-latency Data Pipeline
  Tech Stack: Rust, Tokio async runtime, Kafka (rdkafka), Diesel ORM, PostgreSQL, Kubernetes, Prometheus.
  Short Tech Description: High-performance async services in Rust for ingestion and transformation with Tokio and zero-cost abstractions. Strong typing and borrow checker ensure memory safety and concurrency correctness.
  Business Logic: Ingests high-volume events, performs streaming transformations, writes to analytical stores, and provides back-pressure control. Focuses on throughput and deterministic latency with small memory footprint.
- Big-2: Secure Backend with WebAssembly
  Tech Stack: Rust, Actix or Rocket, Wasm modules for sandboxed plugins, Diesel, Redis.
  Short Tech Description: Rust backend exposing secure plugin execution via WebAssembly sandboxing to run untrusted code safely. Uses Serde for serialization and Diesel for DB access.
  Business Logic: Allows customer-defined rules executed in sandboxes for data processing and custom logic, with strict resource limits. Manages plugin lifecycle, results validation, and audit logging.
- Med-1: CLI Backup & Restore Utility
  Tech Stack: Rust, Clap (CLI), Tokio, Serde, SQLite.
  Short Tech Description: Fast, memory-safe command-line utility for efficient backups with concurrency and streaming, and reliable restores. Single static binary distribution via Cargo.
  Business Logic: Performs incremental backups, verifies integrity with checksums, and supports scheduled runs via system cron. Minimizes I/O footprint and ensures atomic restore semantics.
- Med-2: WebAssembly Frontend Module
  Tech Stack: Rust compiled to Wasm, Yew or wasm-bindgen, integrated into React frontend.
  Short Tech Description: CPU-heavy computations moved to WebAssembly modules compiled from Rust to accelerate client-side processing. Interop via wasm-bindgen for DOM and JS calls.
  Business Logic: Offloads intensive image/video processing or crypto operations to client-side Wasm for reduced server costs and improved responsiveness. Ensures safe memory and predictable performance.

15) PHP (Laravel, Symfony, Composer, PHPUnit, Doctrine, Twig)
- Big-1: Multi-tenant CMS & E-commerce
  Tech Stack: Laravel, MySQL, Redis, Vue.js, Docker, PHP-FPM, Composer, PHPUnit.
  Short Tech Description: Laravel-based extensible CMS with e-commerce modules, multi-tenant support, and queue-backed jobs. API-first design with Vue.js storefront and admin.
  Business Logic: Manages content, products, orders, promotions, and tenant-specific themes and data. Supports search, payment gateways, and extensibility via plugins.
- Big-2: Financial Reporting System
  Tech Stack: Symfony, Doctrine ORM, PostgreSQL, Twig, RabbitMQ.
  Short Tech Description: Symfony for domain-driven architecture and Doctrine for complex relational models; Twig for server-rendered reporting UI. Queue-driven batch jobs for long-running aggregation.
  Business Logic: Generates financial statements, consolidations, and audit trails with role-based approvals. Handles data imports from external accounting systems and maintains history for compliance.
- Med-1: REST API for Mobile Apps
  Tech Stack: Laravel Lumen or Slim, MySQL, JWT auth, PHPUnit.
  Short Tech Description: Lightweight REST API built with Lumen for mobile backend needs, optimized for performance and low latency. Unit tests and API contracts ensured with PHPUnit.
  Business Logic: Manages user accounts, content, push notifications, and media uploads with secure auth and rate limits. Provides versioned endpoints for backward compatibility.
- Med-2: Content Publishing Platform
  Tech Stack: WordPress custom plugins (PHP), MySQL, Composer-managed libraries.
  Short Tech Description: Extensible WordPress installation with custom plugin architecture built in PHP and composer-managed dependencies. Themes use Twig-like templating for consistency.
  Business Logic: Enables authors to create and schedule content, manage editorial workflows, and monetize via subscriptions or ads. Supports caching and CDN for high traffic pages.

16) Ruby (Rails, Sinatra, Hanami, Bundler, RSpec, RuboCop, Sidekiq, Capistrano)
- Big-1: SaaS Application in Rails
  Tech Stack: Ruby on Rails, PostgreSQL, Redis, Sidekiq, React frontend, Capistrano, Docker.
  Short Tech Description: Rails monolith or modular services with Sidekiq for background jobs; React used for dynamic parts. RSpec for testing and RuboCop for linting.
  Business Logic: Subscription management, multi-tenant data separation, billing, and analytics. Background tasks handle billing cycles, notifications, and data sync.
- Big-2: Marketplace Platform
  Tech Stack: Hanami or Rails, PostgreSQL, Elasticsearch, Stripe integration.
  Short Tech Description: Marketplace with listings, search, bookings, and reviews; Elasticsearch for fast search; background jobs for notifications and reconciliation. Clean architecture with bundler-managed gems.
  Business Logic: Facilitates transactions between buyers and sellers, disburses funds, handles disputes, and collects platform fees. Implements reputation scoring and fraud detection heuristics.
- Med-1: Lightweight API with Sinatra
  Tech Stack: Sinatra, PostgreSQL, JWT, RSpec.
  Short Tech Description: Minimal, fast API endpoints built in Sinatra for microservices or internal tools. Simple test suite with RSpec ensures endpoint behavior.
  Business Logic: Serves specific domain functionality like analytics ingestion or user profile APIs with strict SLA. Designed for rapid iteration and easy deployment.
- Med-2: Job Processing System
  Tech Stack: Sidekiq, Redis, Rails or Sinatra, Capistrano for deployment.
  Short Tech Description: Background processing queue managing scheduled and ad-hoc jobs with Sidekiq and Redis. Monitored via basic dashboards and health checks.
  Business Logic: Processes emails, report generation, and external API synchronizations asynchronously to keep web request latency low. Supports retries, DLQs, and rate limiting.

17) C++ (STL, Boost, RAII, Qt, CUDA, Concurrency, Clang/GCC, CMake)
- Big-1: High-performance Simulation Engine
  Tech Stack: C++, Boost, Eigen, OpenMP/CUDA for GPU acceleration, CMake, Qt for visualization.
  Short Tech Description: Native C++ simulation core optimized for numerical stability and performance with RAII resource management. GPU kernels via CUDA and multithreading via OpenMP/TBB for throughput.
  Business Logic: Runs large-scale physics or financial simulations, handles checkpointing and distributed task execution, and provides visualization. Ensures reproducibility and precision across runs.
- Big-2: Real-time Video Processing Pipeline
  Tech Stack: C++, FFmpeg, OpenCV, CUDA, Qt for UI.
  Short Tech Description: Low-level video ingestion and codec handling with FFmpeg integrated into a C++ pipeline; OpenCV and CUDA for real-time effects and analytics. Cross-platform GUI via Qt.
  Business Logic: Processes live video streams, applies transformations, detects objects, and outputs encoded streams for delivery. Supports pipelined stages and adaptive resource management.
- Med-1: Native Desktop App with Qt
  Tech Stack: C++, Qt, SQLite, CMake.
  Short Tech Description: Cross-platform desktop application with Qt widgets, local persistence via SQLite, and modular plugin architecture. Uses RAII and modern C++17 idioms.
  Business Logic: Provides domain-specific tools (e.g., CAD, editing) with undo/redo, import/export, and customizable toolchains. Ensures responsive UI by offloading heavy tasks to worker threads.
- Med-2: Library for Numerical Algorithms
  Tech Stack: C++, templated STL, Boost, GoogleTest.
  Short Tech Description: Header-only or compiled library offering optimized sorting, searching, and DP algorithm implementations with benchmarks. Well-documented API and unit tests.
  Business Logic: Provides robust building blocks for other systems needing high-performance algorithmic primitives. Focuses on correctness, numerical stability, and API ergonomics.

18) C (GCC/Clang, Make/CMake, Valgrind, GDB, Embedded C, OpenMP, GTK)
- Big-1: Embedded Real-time Controller
  Tech Stack: C, FreeRTOS or bare-metal, GCC toolchain, hardware-specific SDK, JTAG debug, Valgrind/asan (host testing).
  Short Tech Description: Deterministic real-time firmware using C with careful memory management and interrupt-driven concurrency. Hardware interfaces via SPI/I2C/UART and rigorous testing using host-side simulation.
  Business Logic: Controls sensors, actuators, and communication with strict timing and safety constraints. Implements watchdogs, fail-safe states, and OTA update mechanisms.
- Big-2: High-performance Scientific Codebase
  Tech Stack: C, OpenMP/MPI, Make/CMake, optimized BLAS/LAPACK.
  Short Tech Description: Parallelized numeric computations using OpenMP for shared memory and MPI for distributed runs. Focused on SIMD-friendly data structures and cache locality.
  Business Logic: Solves large linear systems, PDEs, or simulation workloads with checkpointing and reproducible outputs. Tuned for HPC clusters and batch scheduling systems.
- Med-1: Native GTK Desktop Utility
  Tech Stack: C, GTK, Glib, SQLite, Make.
  Short Tech Description: Lightweight GUI application using GTK for Linux desktops, minimal dependencies, and responsive event-driven design. Uses GObject where appropriate and simple persistence.
  Business Logic: Provides utilities like file management, image conversion, or system monitoring with low memory footprint. Integrates with system services and supports plugin scripts.
- Med-2: Networking Daemon
  Tech Stack: C, POSIX sockets, systemd unit, Valgrind-tested.
  Short Tech Description: Efficient TCP/UDP server handling large numbers of connections with epoll/kqueue and non-blocking I/O. Comprehensive logging and runtime diagnostics with Valgrind and GDB support.
  Business Logic: Handles protocol parsing, connection lifecycle, and resource limits; serves as lightweight message broker or protocol gateway. Emphasizes robustness and graceful degradation.

19) Flutter (Dart, Hot Reload, Widgets, Material, Cupertino, BLoC, Provider, Riverpod, FlutterFlow)
- Big-1: Cross-platform Mobile & Desktop App
  Tech Stack: Flutter, Dart, Riverpod for state, Firebase backend, CI/CD for iOS/Android, Docker for backend.
  Short Tech Description: Single codebase for mobile and desktop with responsive layouts, platform-adaptive widgets, and state management via Riverpod/BLoC. Integrates with Firebase for auth and real-time data.
  Business Logic: Provides user workflows (e.g., social app, productivity) and synchronizes offline changes when online. Supports push notifications, onboarding, and analytics.
- Big-2: E-commerce Mobile App with PWA
  Tech Stack: Flutter (mobile), Flutter Web for PWA, Provider, Stripe integration, Node.js backend.
  Short Tech Description: Flutter app optimized for native mobile and web PWA with shared UI and business logic. Provider for simple state management and integrations for payments and analytics.
  Business Logic: Browsing, cart, checkout, order history, and push notifications. Offline caching for catalogs and smooth UI transitions.
- Med-1: Internal Field Service App
  Tech Stack: Flutter, SQLite, background sync, REST API backend.
  Short Tech Description: Mobile app used by field agents for task lists, forms, photo capture, and offline sync with conflict resolution. Local DB via SQLite and background sync for reliability.
  Business Logic: Assigns jobs, captures completion evidence, syncs with central system, and supports signature capture and attachments. Enforces role-based access and audit trail for compliance.
- Med-2: Prototype App via FlutterFlow
  Tech Stack: FlutterFlow generated app, Firebase backend.
  Short Tech Description: Rapid prototyping using FlutterFlow with exportable Flutter source for further customization. Firebase for quick auth and data storage.
  Business Logic: Demonstrates product features and user flows for validation before full engineering investment. Allows stakeholders to test interactive prototypes and collect feedback.

20) Game Development (Unity, Unreal, Godot, CryEngine, GameMaker, Cocos2d-x, Blender, Substance Painter, Photon Networking)
- Big-1: Multiplayer Action Game
  Tech Stack: Unity (C#), Photon or custom server in Go, Blender assets, Substance Painter, Kubernetes for dedicated servers.
  Short Tech Description: Unity client with authoritative server architecture to manage game state and fairness; networking via Photon or custom UDP protocol. Asset pipeline includes Blender modeling and Substance for texturing.
  Business Logic: Manages matchmaking, authoritative state, latency compensation, and anti-cheat. Implements economy, progression, and live events with server-side validation.
- Big-2: Open-world RPG in Unreal
  Tech Stack: Unreal Engine (C++/Blueprints), Houdini/Blender, Perforce for assets, multiplayer frameworks.
  Short Tech Description: Large-scale Unreal project leveraging C++ and Blueprints for gameplay systems, with streaming worlds and LOD systems. Robust asset pipeline and performance profiling.
  Business Logic: Quest systems, NPC behavior, inventory/economy, and save/load systems; supports expansions and modding. Balances content and progression via telemetry.
- Med-1: 2D Mobile Puzzle Game
  Tech Stack: Godot or Unity 2D, C#, Blender for art.
  Short Tech Description: Simple 2D mechanics with levels, leaderboards, and in-app purchases; optimized for mobile performance. Uses engine’s scene system for rapid iteration.
  Business Logic: Level progression, monetization via ads/IAP, social sharing and leaderboards. Keeps sessions short and retention features (daily rewards).
- Med-2: Multiplayer Turn-based Game Backend
  Tech Stack: Node.js or Go for server, WebSockets, Redis for state, Unity/HTML5 clients.
  Short Tech Description: Turn-based authoritative server managing game rounds, persistence in Redis and DB, and match lifecycle. Simple API for client synchronization.
  Business Logic: Manages turns, enforces rules, resolves conflicts deterministically, and persists match history. Supports reconnection and matchmaking.

21) Video (processing, optimizing and other video algorithms)
- Big-1: Cloud Video Transcoding & Delivery Platform
  Tech Stack: FFmpeg, Kubernetes, S3, HLS/DASH packaging, CDN integration, Python orchestration, Elastic Transcoder alternatives.
  Short Tech Description: Scalable transcoding pipeline with worker pods performing codec conversions, adaptive bitrate packaging, and thumbnails. Job orchestration and retries managed via message queues.
  Business Logic: Ingests raw uploads, transcodes to multiple resolutions/codecs, packages for streaming, and delivers via CDN. Manages DRM, captions, and analytics for playback.
- Big-2: Video Analytics & Indexing
  Tech Stack: OpenCV, TensorFlow/PyTorch for detection, FFmpeg, Elasticsearch, Kubernetes.
  Short Tech Description: Extracts metadata, scene detection, object recognition, and face detection from video frames; indexes results for search. Batch and streaming modes supported.
  Business Logic: Enables search by scene, object, or transcript, supports automated moderation, and generates highlights. Handles large-scale batch processing and near-real-time pipelines.
- Med-1: Client-side Video Editor
  Tech Stack: WebAssembly (FFmpeg compiled to Wasm), React frontend, Web Workers.
  Short Tech Description: Browser-based editing using Wasm-compiled FFmpeg for trimming, filters, and codecs with Web Worker threading. Local processing reduces server costs.
  Business Logic: Allows users to perform edits and export compressed outputs, preserving metadata and offering presets for social platforms. Provides progress indicators and error handling.
- Med-2: Video Compression Optimizer
  Tech Stack: FFmpeg scripts, Python tuning frameworks, CI for experiments.
  Short Tech Description: Automated tool to hunt optimal codec settings per content type using bitrate ladder and perceptual metrics. Runs comparative encodes and reports PSNR/SSIM and VMAF.
  Business Logic: Produces storage and delivery cost savings by selecting encoding ladders, balancing visual quality and bandwidth. Integrates with encoding pipelines to apply learned settings.

22) Compilers (compiler design, parsing, LLVM, JIT/AOT, static analysis, type systems)
- Big-1: Production-ready Language & Toolchain
  Tech Stack: Frontend in ANTLR or hand-written parser, intermediate representation, LLVM for codegen, toolchain in C++/Rust, CI for testing.
  Short Tech Description: Design and implement lexer, parser, AST, type checker, IR lowering, optimizer passes, and LLVM backend for code emission. Includes test suites, debuggers, and standard library.
  Business Logic: Compiles high-level language into optimized native code with options for AOT and possible JIT integration. Supports cross-platform builds and linking with system libraries.
- Big-2: JIT VM for Dynamic Language
  Tech Stack: Runtime in C++/Rust, tracing JIT, bytecode interpreter, GC subsystem, integration with LLVM or custom codegen.
  Short Tech Description: Implements interpreter + JIT compiling hot paths to machine code to boost performance. Includes profiling, deoptimization, and safe garbage collection.
  Business Logic: Executes dynamic language workloads with runtime types, optimizes hot functions, and manages memory safely. Useful for high-performance scripting or analytics.
- Med-1: Static Analyzer for Security
  Tech Stack: Clang static analyzer plugin, LLVM, C++/Python for reporting.
  Short Tech Description: Static code analysis that finds common vulnerability patterns, taint flows, and undefined behaviors via AST and dataflow analyses. Provides actionable reports and integrates into CI.
  Business Logic: Prevents vulnerabilities before deployment by enforcing coding rules and detecting risky patterns. Outputs prioritized findings for engineering remediation.
- Med-2: DSL Compiler to Bytecode
  Tech Stack: Parser generator (ANTLR), custom bytecode VM, test harness.
  Short Tech Description: Domain-specific language compiled into compact bytecode executed by a small VM; supports basic types, control flow, and FFI hooks. Simple tooling for REPL and debugging.
  Business Logic: Enables domain experts to write concise scripts that are executed efficiently and sandboxed. Useful for configuration, data transformations, or templating engines.

23) Big Data + ETL (Spark, Kafka, Hive/Trino, Flink, Airflow, dbt, NiFi, Snowflake, Databricks, AWS Glue, BigQuery, Beam)
- Big-1: Real-time Analytics Platform
  Tech Stack: Kafka, Flink (stream processing), Spark for batch, HDFS or S3, Trino for ad-hoc queries, Airflow orchestration, Snowflake for warehousing.
  Short Tech Description: Hybrid streaming and batch platform ingesting events via Kafka, processing in Flink for real-time materialized views and Spark for heavy batch jobs. Trino and Snowflake provide interactive and nearline analytics.
  Business Logic: Powers dashboards, alerting, and ML feature stores with low-latency updates. Ensures schema evolution, guarantee semantics, and data governance.
- Big-2: Enterprise ETL with dbt & Airflow
  Tech Stack: Airflow for orchestration, dbt for transformations, BigQuery or Snowflake as warehouse, Kafka/NiFi for ingestion.
  Short Tech Description: Orchestrated ETL/ELT with Airflow triggering ingestion and dbt managing SQL transformations for lineage and tests. Centralized metadata and reusable models.
  Business Logic: Produces curated analytics tables for BI, enforces data quality tests, and schedules refreshes per SLAs. Supports incremental loads and backfills.
- Med-1: Log Aggregation Pipeline
  Tech Stack: Filebeat/Fluentd, Kafka, Elasticsearch or BigQuery, Airflow for retention jobs.
  Short Tech Description: Centralizes logs from apps and infra into a searchable store, enriches events, and maintains retention and cost policies. Uses indexing and partitioning strategies.
  Business Logic: Enables centralized troubleshooting, security audit, and business metrics extraction. Supports alerting on anomalies and retention-based archiving.
- Med-2: Data Ingestion Service
  Tech Stack: NiFi or AWS Glue, Python processors, S3, Postgres for metadata.
  Short Tech Description: Flexible ingestion framework that connects sources (DBs, APIs, files) and moves data to landing zones with schema detection and transformations. Metadata store for lineage.
  Business Logic: Normalizes and validates incoming data, triggers downstream ETL, and maintains audit records of ingestions and errors. Provides retry and dead-letter handling.

24) Blockchain (Solidity, Python/Rust clients, Web3.js, SHA-256, Truffle, IPFS, Hardhat, OpenZeppelin, Ganache, Ethers.js, Ethereum, Hyperledger)
- Big-1: Enterprise Blockchain Supply Chain
  Tech Stack: Hyperledger Fabric for permissioned ledger, Node.js chaincode, PostgreSQL for off-chain data, React frontend, IPFS for docs.
  Short Tech Description: Permissioned DLT to record provenance of goods, smart contracts for transfer logic, and off-chain DB for heavy queries. IPFS stores large artifacts referenced by ledger entries.
  Business Logic: Records immutable transactions for supply transfers, ownership changes, and certifications. Enables auditability, dispute resolution, and selective privacy via channels.
- Big-2: Decentralized Marketplace (Ethereum)
  Tech Stack: Solidity contracts, Hardhat, OpenZeppelin, Ethers.js, React, IPFS for assets, Infura/Alchemy.
  Short Tech Description: Smart contracts manage listings, escrows, and dispute resolution; frontend uses web3 libraries for wallet integration. Test environment uses Ganache/Hardhat and audited OpenZeppelin modules.
  Business Logic: Facilitates peer-to-peer trade with on-chain escrow, dispute mechanisms, and fee collection. Handles metadata storage on IPFS and off-chain order matching.
- Med-1: Tokenized Loyalty Program
  Tech Stack: ERC-20/ERC-721 contracts in Solidity, Node.js backend, React, PostgreSQL.
  Short Tech Description: Blockchain tokens representing loyalty points or NFTs for rewards; backend manages KYC and redemption flows and caches on PostgreSQL. Wallet integration via MetaMask.
  Business Logic: Issues tokens for customer actions, allows redemption via secure off-chain validation, and supports burn/mint policies for promotions. Tracks user balances and rewards history.
- Med-2: Audit & Merkle Proof Tool
  Tech Stack: Python/Rust, SHA-256 utilities, Merkle tree libraries, simple web UI.
  Short Tech Description: Generates Merkle roots and proofs to prove inclusion of records in a ledger; helps anchor off-chain data to public chains via transactions. Provides verification utilities.
  Business Logic: Used for document notarization, supply chain anchors, and integrity proofs. Enables third-party auditors to verify that a dataset was committed at a particular time.

25) GIS (ArcGIS, QGIS, PostGIS, Mapbox, Leaflet, GeoServer, GDAL, OpenLayers, Cesium, coordinate systems, GeoJSON, R-tree)
- Big-1: Geospatial Analytics & Mapping Platform
  Tech Stack: PostGIS/PostgreSQL, GeoServer, Mapbox GL, Cesium for 3D, GDAL for transforms, Kubernetes, Python processing.
  Short Tech Description: Stores spatial data in PostGIS, serves tiles and WMS via GeoServer, and provides vector/3D visualization with Mapbox and Cesium. GDAL pipelines perform reprojection and raster processing.
  Business Logic: Supports spatial queries, routing, spatial analytics like heatmaps and catchments, and 3D visualization for urban planning. Handles large raster datasets, tiling, and dynamic rendering.
- Big-2: Real-time Fleet Tracking System
  Tech Stack: MQTT or WebSockets, PostGIS, Leaflet, React, Redis for geospatial cache.
  Short Tech Description: Ingests telematics data, stores positions in PostGIS, and serves real-time maps in Leaflet. Redis used for fast nearest-neighbor queries and geofencing alerts.
  Business Logic: Tracks assets, calculates ETA, triggers geofence events, and provides replay/history. Supports routing optimizations and heatmaps for operational insights.
- Med-1: Indoor Mapping & Navigation
  Tech Stack: GeoJSON floor plans, Mapbox or OpenLayers, React Native for mobile, PostGIS.
  Short Tech Description: Maps indoor spaces using custom coordinate systems and provides turn-by-turn indoor guidance. Uses beacons or WiFi for positioning where GPS is weak.
  Business Logic: Helps users navigate buildings, locate assets, and report maintenance issues. Supports multi-floor awareness and accessibility routing.
- Med-2: Spatial Data ETL Service
  Tech Stack: GDAL/OGR, Python, PostGIS, Airflow.
  Short Tech Description: ETL pipelines to ingest shapefiles, GeoJSON, and rasters, transform CRS, and load into PostGIS for analysis. Airflow schedules jobs with retries and monitoring.
  Business Logic: Normalizes heterogeneous sources into canonical schemas, applies spatial indices (R-tree), and enriches data with spatial joins. Ensures data integrity and provenance for downstream consumers.

26) Finance (loan origination & servicing, credit scoring, risk management, portfolio optimization, market prediction, algo trading)
- Big-1: Retail Lending Platform
  Tech Stack: Java Spring Boot, Kafka, Postgres, Redis, Python for scoring models (scikit-learn/XGBoost), React frontend, Kubernetes.
  Short Tech Description: End-to-end loan origination, underwriting workflows, credit decisioning using ML models, and loan servicing with amortization schedules. Event-driven architecture for lifecycle events and notifications.
  Business Logic: Accepts applications, evaluates credit via models and rules, makes offers, disburses funds, and manages repayments and collections. Ensures regulatory reporting and audit trails.
- Big-2: Algorithmic Trading System
  Tech Stack: C++/Java for low-latency matching, Python for strategy backtesting, Kafka, KDB/Time-series DB, FIX connectivity.
  Short Tech Description: Low-latency execution engines with co-located services for market data ingest and order submission, plus Pythonic backtesting frameworks. Risk controls at multiple layers to prevent rogue trading.
  Business Logic: Runs strategies, monitors P&L and exposures, enforces risk limits, and routes orders to exchanges. Backtesting against historical data validates strategies before live deployment.
- Med-1: Credit-scoring Model Pipeline
  Tech Stack: Python, Pandas, scikit-learn/XGBoost, MLFlow, PostgreSQL.
  Short Tech Description: Feature engineering and model training pipelines producing explainable credit scores with SHAP and performance monitoring. MLflow logs experiments and model artifacts.
  Business Logic: Scores applicants using features, thresholds, and manual overrides; integrates with origination system for decisions and with monitoring for drift and fairness. Supports credit limit assignments and periodic re-scoring.
- Med-2: Portfolio Optimization Tool
  Tech Stack: Python, NumPy, SciPy, CVXOPT or PyPortfolioOpt, React UI.
  Short Tech Description: Implements Markowitz mean-variance optimization, risk parity, and Black-Litterman models in Python with web UI for scenario testing. Solvers for convex optimization and constraints.
  Business Logic: Optimizes allocations given return expectations and risk budgets, supports rebalancing schedules, and simulates historical performance. Integrates transaction cost models and liquidity constraints.

27) Assembly (CPU architecture x86/ARM, assembler NASM/MASM, machine code, memory addressing, system calls, linker)
- Big-1: Operating System Kernel Subsystem
  Tech Stack: C with assembly (x86_64/ARM), GCC/Clang, custom bootloader, linker scripts, QEMU for testing.
  Short Tech Description: Kernel modules in C with critical routines in assembly for context switching, system calls, and interrupt handling. Boot sequences and memory management implemented with careful linker scripts.
  Business Logic: Provides process scheduling, basic filesystem, and IPC primitives to support system services. Emphasizes correctness and isolation through memory protection.
- Big-2: Embedded Firmware with Bare-metal Assembly
  Tech Stack: ARM Cortex-M assembly and C, GCC toolchain, OpenOCD/JTAG, Make.
  Short Tech Description: Performance- and size-critical embedded firmware using assembly for startup, interrupt vectors, and tight loops. C used for higher-level logic and peripherals.
  Business Logic: Controls hardware, handles real-time constraints, and interfaces with sensors and actuators with deterministic behavior. Provides bootloader and robust update mechanism.
- Med-1: Bootloader Project
  Tech Stack: NASM or GAS, C, linker scripts, QEMU for emulation.
  Short Tech Description: Minimal bootloader that loads kernel images, handles basic hardware initialization, and passes kernel parameters. Demonstrates stage-1/2 loader architecture.
  Business Logic: Boots multiple kernels, supports small filesystem for kernel images, and provides simple recovery options. Verifies integrity and provides verbose diagnostics.
- Med-2: Assembler & Disassembler Tooling
  Tech Stack: C/C++ or Rust, Capstone/Keystone, tests and CLI.
  Short Tech Description: Implements assembler and disassembler support for target architectures, useful for reverse engineering and embedded development. Integrates with toolchain and supports instruction encoding/decoding.
  Business Logic: Translates human-readable assembly to machine code and vice versa, aiding debugging, patching, and teaching. Validates encodings and outputs for integration with build systems.

28) Medicine (FHIR, AlphaFold, CRISPR, DICOM)
- Big-1: Clinical Data Interoperability Platform (FHIR)
  Tech Stack: Java or .NET FHIR servers (HAPI/FHIR or Microsoft), PostgreSQL, HL7 integrations, React portal, Docker/Kubernetes.
  Short Tech Description: Implements FHIR-compliant API server that stores and serves patient resources, supports HL7 v2 bridges, and integrates with EHRs. Security via OAuth2 and audit logging for compliance.
  Business Logic: Normalizes clinical data across systems, supports patient consent, query/analytics, and clinical decision support triggers. Ensures provenance and supports consent-driven data sharing.
- Big-2: Medical Imaging Pipeline (DICOM)
  Tech Stack: DICOM stores (Orthanc), Python for pipelines, TensorFlow/PyTorch for image analysis, PACS integration, Web UI.
  Short Tech Description: Receives and indexes DICOM imaging studies, runs AI inference for detection, and presents results with overlays for radiologists. Orthanc or similar used for PACS operations.
  Business Logic: Performs automated triage, segmentation, and annotation to accelerate diagnosis and routing. Keeps audit logs, handles HL7 notifications, and supports human validation loops.
- Med-1: Genomics Data Platform (AlphaFold/CRISPR tooling)
  Tech Stack: Python, Docker, GPU clusters, AlphaFold models, Nextflow for pipelines, PostgreSQL for metadata.
  Short Tech Description: Runs protein structure predictions (AlphaFold) and CRISPR guide design pipelines with reproducible Nextflow workflows. Manages compute and stores results with provenance.
  Business Logic: Supports researchers by running large-scale predictions, annotating sequences, and tracking experimental metadata. Enables search by structure, similarity, and predicted functional annotations.
- Med-2: Telehealth Appointment System
  Tech Stack: Django/Flask, WebRTC for video, FHIR integration for records, PostgreSQL.
  Short Tech Description: Telemedicine platform that schedules visits, manages secure video calls, and stores encounter metadata in FHIR resources. Encrypts communication and logs events for compliance.
  Business Logic: Manages appointment workflows, captures patient histories, writes encounter notes, and integrates with billing. Enforces privacy, consent, and data retention policies.

29) Kotlin (Android SDK, Jetpack Compose, Gradle, Retrofit, Coroutines, Room, Firebase, Ktor)
- Big-1: Consumer Banking Mobile App
  Tech Stack: Kotlin Android, Jetpack Compose, Coroutines, Room, Retrofit, Spring Boot backend, Kubernetes, Firebase for notifications.
  Short Tech Description: Modern Android app with Compose UI, offline caching via Room, and Coroutine-driven async flows. Backend services expose secure APIs via Retrofit with OAuth2.
  Business Logic: Allows account viewing, transfers, bill pay, transaction search, and secure authentication (MFA). Ensures transaction atomicity, user session security, and regulatory logging.
- Big-2: Backend in Kotlin (Ktor) for Microservices
  Tech Stack: Ktor, Kotlin coroutines, PostgreSQL, Docker, Kubernetes, OpenAPI.
  Short Tech Description: Asynchronous Kotlin microservices with Ktor and coroutines for efficient IO; typed APIs and structured logging. Uses Ktor clients for internal comms and graceful shutdown.
  Business Logic: Hosts domain services like user management, catalogs, and payments with transactional guarantees. Supports health checks, metrics, and rolling upgrades.
- Med-1: Android Field Data Collection App
  Tech Stack: Kotlin, Jetpack Compose, Offline sync with WorkManager and Room, Firebase auth.
  Short Tech Description: Mobile app for offline-first data capture, background sync, and attachments upload; employs coroutines for background tasks. Simple UX for field operators.
  Business Logic: Captures survey/form data with photos and geotags, queues uploads when online, and ensures conflict resolution. Supports user roles and audit trails.
- Med-2: Kotlin Multiplatform Library
  Tech Stack: Kotlin Multiplatform Mobile (KMM), common business logic, iOS and Android integration.
  Short Tech Description: Shared Kotlin modules for domain logic, networking, and serialization to reduce duplicated code across mobile platforms. Uses Gradle for managing targets and testing.
  Business Logic: Provides shared validation, caching and network abstractions usable across apps, reducing bugs and speeding feature parity. Enables coherent release management across platforms.
