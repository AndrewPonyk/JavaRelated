glm-4.5

### 1. Java
**Big Project 1**  
Tech Stack: Spring Boot, Hibernate, PostgreSQL, React, Docker, Kubernetes, Maven, JUnit, Prometheus  
Short Tech Description: A microservices-based e-commerce platform using Spring Boot for backend services and React for the frontend. PostgreSQL handles transactional data with Hibernate ORM.  
Business Logic: Manages product catalogs, user accounts, and order processing with real-time inventory updates. Implements payment gateway integration and recommendation algorithms.

**Big Project 2**  
Tech Stack: Quarkus, GraalVM (Native Image), MongoDB, Vert.x, Kubernetes, Gradle, JUnit, Grafana  
Short Tech Description: High-performance IoT data ingestion system using Quarkus and GraalVM for native compilation. Vert.x handles asynchronous event processing with MongoDB storage.  
Business Logic: Processes sensor data from devices, triggers alerts for anomalies, and provides analytics dashboards. Scales dynamically based on data load.

**Medium Project 1**  
Tech Stack: Micronaut, MySQL, Angular, Docker, Maven, SLF4J, Jaeger  
Short Tech Description: RESTful task management API with Micronaut and Angular frontend. MySQL stores tasks with Hibernate validation.  
Business Logic: Allows users to create, assign, and track tasks with deadlines. Supports role-based access control and audit logging.

**Medium Project 2**  
Tech Stack: Jakarta EE, Payara, Redis, Vue.js, Jenkins, Maven, Arquillian  
Short Tech Description: Real-time chat application using Jakarta EE WebSocket and Redis for message caching. Vue.js powers the UI.  
Business Logic: Enables group/private messaging with file sharing. Integrates user presence indicators and message encryption.

---

### 2. Multithreading
**Big Project 1**  
Tech Stack: Java (Concurrent API), C++, Python, Docker, JUnit, pytest  
Short Tech Description: Distributed task scheduler using Java thread pools and C++ for low-latency workers. Python manages orchestration.  
Business Logic: Distributes computational tasks across nodes, prioritizes jobs dynamically, and handles worker failures. Optimizes resource allocation.

**Big Project 2**  
Tech Stack: Go (Goroutines/Channels), Rust (Tokio), React, PostgreSQL, Docker  
Short Tech Description: Real-time stock trading platform with Go for order matching and Rust for risk analysis. React dashboard.  
Business Logic: Matches buy/sell orders with microsecond latency. Applies risk checks (e.g., circuit breakers) and logs audit trails.

**Medium Project 1**  
Tech Stack: Java (ExecutorService), SQLite, Swing, Maven, JUnit  
Short Tech Description: Airport baggage simulation using Java thread pools and SQLite for state tracking. Swing GUI.  
Business Logic: Models conveyor belts and sorting logic to prevent deadlocks. Visualizes baggage flow and collision points.

**Medium Project 2**  
Tech Stack: Python (Asyncio), Redis, FastAPI, Docker, pytest  
Short Tech Description: Asynchronous web scraper with Asyncio and Redis for rate limiting. FastAPI exposes endpoints.  
Business Logic: Crawls e-commerce sites, extracts pricing data, and enforces politeness policies. Stores results in Redis for caching.

---

### 3. Python
**Big Project 1**  
Tech Stack: Django, PostgreSQL, React, Celery, Redis, Docker, pytest  
Short Tech Description: SaaS analytics platform with Django backend and React dashboard. Celery handles async reporting.  
Business Logic: Tracks user behavior, generates custom reports, and integrates with third-party APIs (e.g., Google Analytics).

**Big Project 2**  
Tech Stack: FastAPI, PyTorch, MongoDB, Vue.js, Kubernetes, Docker, pytest  
Short Tech Description: AI-powered content moderation service using FastAPI and PyTorch NLP models. Vue.js admin panel.  
Business Logic: Scans user-generated text/images for policy violations. Flags content and provides moderation workflows.

**Medium Project 1**  
Tech Stack: Flask, SQLite, Bootstrap, Gunicorn, pytest  
Short Tech Description: Blog platform with Flask and SQLite. Bootstrap for responsive UI.  
Business Logic: Supports user posts, comments, and markdown editing. Implements SEO optimization and RSS feeds.

**Medium Project 2**  
Tech Stack: NumPy, Pandas, Jupyter, Matplotlib, Docker  
Short Tech Description: Data analysis toolkit for CSV/Excel files. Jupyter notebooks for interactive exploration.  
Business Logic: Cleans datasets, performs statistical analysis, and exports visualizations. Supports scheduled reports via email.

---

### 4. Machine Learning
**Big Project 1**  
Tech Stack: TensorFlow, Kubernetes, FastAPI, React, MLflow, Docker, pytest  
Short Tech Description: MLOps pipeline for image classification with TensorFlow model training. FastAPI serves predictions.  
Business Logic: Trains models on user-uploaded images, deploys versions via Kubernetes, and monitors drift.

**Big Project 2**  
Tech Stack: PyTorch, HuggingFace, Kafka, Spark, Grafana, Docker  
Short Tech Description: Real-time sentiment analysis engine using PyTorch and HuggingFace transformers. Kafka ingests data.  
Business Logic: Analyzes social media feeds for brand sentiment. Triggers alerts for negative trends and generates reports.

**Medium Project 1**  
Tech Stack: Scikit-learn, Flask, SQLite, React, Docker, pytest  
Short Tech Description: Loan approval predictor with Scikit-learn. Flask API and React UI.  
Business Logic: Uses applicant data to predict loan risk. Explains decisions via SHAP values.

**Medium Project 2**  
Tech Stack: XGBoost, Airflow, PostgreSQL, Docker, pytest  
Short Tech Description: Sales forecasting tool with XGBoost. Airflow orchestrates daily training.  
Business Logic: Predicts product demand using historical data. Optimizes inventory and supply chain decisions.

---

### 5. AWS
**Big Project 1**  
Tech Stack: AWS EKS, Lambda, S3, RDS, DynamoDB, React, Terraform, CloudWatch  
Short Tech Description: Serverless video streaming platform on EKS. Lambda processes uploads; S3 stores videos.  
Business Logic: Transcodes videos, manages user subscriptions, and recommends content via ML (SageMaker).

**Big Project 2**  
Tech Stack: AWS EC2, VPC, IAM, CloudFormation, SNS/SQS, Python, Jenkins  
Short Tech Description: Multi-tenant SaaS infrastructure with EC2 auto-scaling. CloudFormation provisions resources.  
Business Logic: Isolates tenant data via VPC, uses SQS for async tasks, and monitors costs with CloudWatch.

**Medium Project 1**  
Tech Stack: AWS Lambda, S3, API Gateway, React, SAM, DynamoDB  
Short Tech Description: Serverless URL shortener with Lambda and DynamoDB. React frontend.  
Business Logic: Generates short URLs, tracks click analytics, and enforces rate limits.

**Medium Project 2**  
Tech Stack: AWS RDS, EC2, S3, Python, Boto3, Docker  
Short Tech Description: Backup automation tool using RDS snapshots and S3. Python CLI.  
Business Logic: Schedules backups, encrypts data, and notifies admins via SNS on failures.

---

### 6. DB
**Big Project 1**  
Tech Stack: PostgreSQL, Redis, Elasticsearch, Kafka, Java, React, Docker  
Short Tech Description: E-commerce search platform with PostgreSQL for transactions and Elasticsearch for product search.  
Business Logic: Handles fuzzy search, faceting, and real-time inventory updates. Caches results with Redis.

**Big Project 2**  
Tech Stack: MongoDB, Neo4j, Cassandra, Python, Spark, Docker  
Short Tech Description: Fraud detection system using Neo4j for relationship mapping and Cassandra for time-series data.  
Business Logic: Identifies fraudulent patterns via graph queries and stores transaction history.

**Medium Project 1**  
Tech Stack: MySQL, Redis, Node.js, Vue.js, Docker  
Short Tech Description: Real-time leaderboard for gaming apps. MySQL stores scores; Redis caches rankings.  
Business Logic: Updates rankings instantly, supports global filters, and syncs data across regions.

**Medium Project 2**  
Tech Stack: Elasticsearch, Logstash, Kibana (ELK), Docker  
Short Tech Description: Log aggregation pipeline for DevOps. Ingests app logs via Logstash.  
Business Logic: Visualizes errors, tracks performance metrics, and sets up alerting rules.

---

*(Note: Due to length constraints, I'll provide projects for the remaining 23 items in a condensed format. Each follows the same structure.)*

### 7. Algorithms
**Big 1** Tech Stack: C++, Python, React, Docker, Git  
*Short Tech Description*: Route optimization engine using Dijkstra/A* algorithms. React dashboard for logistics planning.  
*Business Logic*: Calculates shortest paths for delivery fleets, integrates live traffic data, and minimizes fuel costs.

**Big 2** Tech Stack: Java, Python, Kubernetes, Spark  
*Short Tech Description*: Genomic sequence analyzer with dynamic programming (Smith-Waterman). Spark handles large datasets.  
*Business Logic*: Identifies gene mutations, aligns sequences, and predicts disease risks.

**Medium 1** Tech Stack: Python, Flask, NumPy, Docker  
*Short Tech Description*: Sudoku solver with backtracking. Flask API for puzzle input/output.  
*Business Logic*: Validates puzzles, provides step-by-step solutions, and rates difficulty.

**Medium 2** Tech Stack: C++, OpenGL, Docker  
*Short Tech Description*: 3D convex hull visualizer using Quickhull algorithm.  
*Business Logic*: Generates hulls from point clouds, supports rotation/zoom, and exports models.

---

### 8. JavaScript
**Big 1** Tech Stack: Next.js, TypeScript, Node.js, MongoDB, Docker, Jest  
*Short Tech Description*: Social media platform with Next.js SSR. MongoDB stores posts/user data.  
*Business Logic*: Real-time feeds, user interactions (likes/shares), and content moderation.

**Big 2** Tech Stack: React Native, Node.js, Firebase, Redux, Docker  
*Short Tech Description*: Cross-platform fitness app with Firebase auth/storage.  
*Business Logic*: Tracks workouts, syncs data across devices, and suggests routines via ML.

**Medium 1** Tech Stack: Vue.js, Express, MySQL, Docker, Mocha  
*Short Tech Description*: Task tracker with drag-and-drop UI. Express API.  
*Business Logic*: Manages sprints, assigns tasks, and sends deadline reminders.

**Medium 2** Tech Stack: Svelte, Vite, WebSockets, Docker  
*Short Tech Description*: Collaborative whiteboard using WebSockets.  
*Business Logic*: Real-time drawing, shape tools, and session recording.

---

### 9. Web Design
**Big 1** Tech Stack: Figma, Tailwind CSS, React, Next.js, Docker  
*Short Tech Description*: Portfolio website builder with Figma designs. React components for drag-drop editing.  
*Business Logic*: Users create portfolios from templates, publish to custom domains, and track analytics.

**Big 2** Tech Stack: Adobe XD, Bootstrap, WordPress, Docker  
*Short Tech Description*: E-commerce theme for WordPress. Bootstrap-based responsive design.  
*Business Logic*: Supports product variants, cart/checkout flows, and SEO optimization.

**Medium 1** Tech Stack: Sketch, Sass, Gatsby, Netlify  
*Short Tech Description*: Blog template with Sass theming. Gatsby for static generation.  
*Business Logic*: Markdown-based posts, newsletter signup, and comment system.

**Medium 2** Tech Stack: Webflow, Zapier, Airtable  
*Short Tech Description*: No-code CRM dashboard. Webflow UI; Airtable backend.  
*Business Logic*: Manages leads, automates follow-ups via Zapier, and generates reports.

---

### 10. Security
**Big 1** Tech Stack: Python, Flask, OWASP ZAP, Docker, Jenkins  
*Short Tech Description*: Vulnerability scanner with OWASP ZAP integration. Flask dashboard.  
*Business Logic*: Scans web apps for XSS/SQLi, generates reports, and prioritizes fixes.

**Big 2** Tech Stack: Kali Linux, Metasploit, Wireshark, React, Docker  
*Short Tech Description*: Network security monitoring tool. Wireshark packet analysis; React UI.  
*Business Logic*: Detects intrusions, logs anomalies, and visualizes attack paths.

**Medium 1** Tech Stack: Burp Suite, Java, Spring Boot, Docker  
*Short Tech Description*: API security tester using Burp Suite. Spring Boot proxy.  
*Business Logic*: Tests endpoints for auth flaws, rate limits, and data exposure.

**Medium 2** Tech Stack: nmap, Python, Bash, Docker  
*Short Tech Description*: Port scanner with Python CLI. nmap backend.  
*Business Logic*: Discovers open ports, identifies services, and flags vulnerabilities.

---

### 11. DevOps
**Big 1** Tech Stack: Kubernetes, Helm, Prometheus, Grafana, Jenkins, Terraform  
*Short Tech Description*: Multi-cloud monitoring stack. Prometheus/Grafana dashboards; Terraform IaC.  
*Business Logic*: Tracks resource usage, sets up auto-scaling, and alerts on failures.

**Big 2** Tech Stack: Docker, Ansible, ELK, GitLab CI, AWS  
*Short Tech Description*: CI/CD pipeline for microservices. Ansible configures instances.  
*Business Logic*: Builds/tests containers, deploys to AWS, and rolls back on errors.

**Medium 1** Tech Stack: GitHub Actions, Docker, AWS ECR, Slack  
*Short Tech Description*: Automated deployment workflow. GitHub Actions triggers builds.  
*Business Logic*: Runs tests on PRs, pushes images to ECR, and notifies Slack.

**Medium 2** Tech Stack: Jenkins, Docker, Kubernetes, Slack  
*Short Tech Description*: Scheduled backup system. Jenkins orchestration.  
*Business Logic*: Backups DBs to S3, verifies integrity, and alerts on failures.

---

### 12. C#
**Big 1** Tech Stack: .NET 8, Blazor, Entity Framework, SQL Server, Docker, NUnit  
*Short Tech Description*: ERP system with Blazor UI. SQL Server for finance modules.  
*Business Logic*: Manages payroll, inventory, and reporting with role-based access.

**Big 2** Tech Stack: ASP.NET Core, MAUI, Azure, SignalR, Docker  
*Short Tech Description*: Real-time field service app. MAUI mobile clients; SignalR for updates.  
*Business Logic*: Dispatches technicians, tracks job status, and captures signatures.

**Medium 1** Tech Stack: WPF, Entity Framework, SQLite, NUnit  
*Short Tech Description*: Desktop library manager. WPF UI; SQLite backend.  
*Business Logic*: Catalogs books, tracks loans, and sends overdue reminders.

**Medium 2** Tech Stack: LINQ, ASP.NET Core, React, Docker  
*Short Tech Description*: Data visualization API. LINQ queries; React charts.  
*Business Logic*: Aggregates sales data, filters by region, and exports PDFs.

---

### 13. GO
**Big 1** Tech Stack: Go, Gin, gRPC, Kubernetes, Docker, Testify  
*Short Tech Description*: Microservices payment gateway. gRPC for internal comms.  
*Business Logic*: Processes transactions, handles fraud checks, and reconciles settlements.

**Big 2** Tech Stack: Go, Channels, Docker, Prometheus, React  
*Short Tech Description*: Real-time auction platform. Channels manage bid streams.  
*Business Logic*: Handles concurrent bids, enforces auction rules, and notifies winners.

**Medium 1** Tech Stack: Go, net/http, SQLite, Docker  
*Short Tech Description*: URL shortener with Go HTTP server. SQLite for mappings.  
*Business Logic*: Generates short codes, redirects URLs, and tracks clicks.

**Medium 2** Tech Stack: Go, gRPC, Docker, Testify  
*Short Tech Description*: Chat service with gRPC. Go backend; React client.  
*Business Logic*: Supports group chats, message history, and user presence.

---

### 14. Rust
**Big 1** Tech Stack: Rust, Actix, Diesel, PostgreSQL, Docker, Rocket  
*Short Tech Description*: High-frequency trading engine. Actix for low-latency APIs.  
*Business Logic*: Executes trades, manages order books, and enforces risk limits.

**Big 2** Tech Stack: Rust, Tokio, WebAssembly, React, Docker  
*Short Tech Description*: Browser-based game with WebAssembly. React UI.  
*Business Logic*: Real-time multiplayer gameplay, physics simulation, and leaderboards.

**Medium 1** Tech Stack: Rust, Serde, CLI, Docker  
*Short Tech Description*: JSON data validator. Serde for parsing.  
*Business Logic*: Validates schemas, converts formats, and generates reports.

**Medium 2** Tech Stack: Rust, Diesel, SQLite, Docker  
*Short Tech Description*: Task queue with Diesel ORM. SQLite backend.  
*Business Logic*: Schedules jobs, retries failures, and prioritizes tasks.

---

### 15. PHP
**Big 1** Tech Stack: Laravel, MySQL, Vue.js, Docker, PHPUnit  
*Short Tech Description*: Learning management system. Laravel backend; Vue.js UI.  
*Business Logic*: Manages courses, assignments, and grades with video streaming.

**Big 2** Tech Stack: Symfony, Doctrine, Redis, React, Docker  
*Short Tech Description*: E-commerce platform. Symfony API; React storefront.  
*Business Logic*: Handles cart/checkout, inventory sync, and payment processing.

**Medium 1** Tech Stack: Laravel, Blade, SQLite, PHPUnit  
*Short Tech Description*: Blog platform with Blade templating. SQLite storage.  
*Business Logic*: Supports posts, categories, and user comments with SEO.

**Medium 2** Tech Stack: Symfony, API Platform, Docker, PHPUnit  
*Short Tech Description*: RESTful API for IoT devices. API Platform.  
*Business Logic*: Authenticates devices, ingests sensor data, and triggers alerts.

---

### 16. Ruby
**Big 1** Tech Stack: Rails, PostgreSQL, Sidekiq, React, Docker, RSpec  
*Short Tech Description*: Social networking platform. Rails backend; React feed.  
*Business Logic*: Manages profiles, posts, and notifications with real-time updates.

**Big 2** Tech Stack: Rails, Redis, Hanami, Docker, RSpec  
*Short Tech Description*: Hotel booking system. Hanami for admin; Rails for customers.  
*Business Logic*: Handles reservations, payments, and room availability.

**Medium 1** Tech Stack: Sinatra, SQLite, Bootstrap, Docker  
*Short Tech Description*: URL shortener. Sinatra API; Bootstrap UI.  
*Business Logic*: Generates short URLs, tracks analytics, and enforces limits.

**Medium 2** Tech Stack: Rails, Action Cable, Docker, RSpec  
*Short Tech Description*: Real-time chat app. Action Cable for WebSockets.  
*Business Logic*: Supports private/group chats with file sharing.

---

### 17. C++
**Big 1** Tech Stack: C++, Qt, Boost, CUDA, Docker, CMake  
*Short Tech Description*: CAD modeling tool. Qt GUI; CUDA for rendering.  
*Business Logic*: Designs 3D models, simulates physics, and exports to standard formats.

**Big 2** Tech Stack: C++, OpenMP, MPI, Docker, Clang  
*Short Tech Description*: Climate simulation with MPI parallelization.  
*Business Logic*: Models weather patterns, predicts storms, and visualizes data.

**Medium 1** Tech Stack: C++, STL, GTK, Docker  
*Short Tech Description*: File manager with GTK UI. STL for file operations.  
*Business Logic*: Browses directories, previews files, and manages permissions.

**Medium 2** Tech Stack: C++, Boost, Docker, CMake  
*Short Tech Description*: Network packet analyzer. Boost.Asio for sockets.  
*Business Logic*: Captures packets, filters protocols, and logs traffic.

---

### 18. C
**Big 1** Tech Stack: C, GTK, GCC, Valgrind, Docker  
*Short Tech Description*: Text editor with syntax highlighting. GTK UI.  
*Business Logic*: Supports tabs, plugins, and macros for multiple languages.

**Big 2** Tech Stack: C, OpenMP, Clang, Docker  
*Short Tech Description*: Image processing tool. OpenMP for parallel filters.  
*Business Logic*: Applies filters (blur/sharpen), resizes images, and batches processes.

**Medium 1** Tech Stack: C, SQLite, GCC, Docker  
*Short Tech Description*: CLI task manager. SQLite backend.  
*Business Logic*: Adds/deletes tasks, sets priorities, and lists due items.

**Medium 2** Tech Stack: C, POSIX, Docker, GDB  
*Short Tech Description*: Multi-threaded web server. POSIX sockets.  
*Business Logic*: Handles HTTP requests, serves static files, and logs access.

---

### 19. Flutter
**Big 1** Tech Stack: Flutter, Dart, Firebase, BLoC, Docker  
*Short Tech Description*: Food delivery app. Firebase auth/storage; BLoC state.  
*Business Logic*: Manages orders, tracks delivery, and integrates payment gateways.

**Big 2** Tech Stack: Flutter, Provider, PostgreSQL, Node.js, Docker  
*Short Tech Description*: Fitness tracker with Node.js backend. Provider for state.  
*Business Logic*: Logs workouts, syncs with wearables, and suggests goals.

**Medium 1** Tech Stack: Flutter, Riverpod, SQLite, Docker  
*Short Tech Description*: Quiz app. Riverpod state management.  
*Business Logic*: Categories questions, tracks scores, and shows leaderboards.

**Medium 2** Tech Stack: Flutter, Hive, Docker  
*Short Tech Description*: Offline note-taking app. Hive for local storage.  
*Business Logic*: Creates/edits notes, adds images, and syncs when online.

---

### 20. Gamedev
**Big 1** Tech Stack: Unity, C#, Photon, Blender, Docker  
*Short Tech Description*: Multiplayer battle royale. Unity engine; Photon networking.  
*Business Logic*: Manages player lobbies, in-game purchases, and matchmaking.

**Big 2** Tech Stack: Unreal Engine, C++, AWS, Docker  
*Short Tech Description*: Open-world RPG. Unreal for graphics; AWS for servers.  
*Business Logic*: Features quests, NPC AI, and persistent world saves.

**Medium 1** Tech Stack: Godot, GDScript, Docker  
*Short Tech Description*: 2D puzzle platformer. Godot engine.  
*Business Logic*: Designs levels, implements physics, and tracks progress.

**Medium 2** Tech Stack: Unity, C#, Docker  
*Short Tech Description*: VR meditation app. Unity XR integration.  
*Business Logic*: Guides breathing exercises, tracks sessions, and customizes environments.

---

### 21. Video
**Big 1** Tech Stack: FFmpeg, Python, OpenCV, React, Docker  
*Short Tech Description*: Video editing SaaS. FFmpeg for processing; React UI.  
*Business Logic*: Trims/crops videos, adds filters, and exports to social media formats.

**Big 2** Tech Stack: CUDA, C++, Python, Docker  
*Short Tech Description*: Real-time video analytics. CUDA for GPU acceleration.  
*Business Logic*: Detects objects, tracks motion, and alerts on security events.

**Medium 1** Tech Stack: FFmpeg, CLI, Docker  
*Short Tech Description*: Batch video compressor. FFmpeg backend.  
*Business Logic*: Reduces file sizes, maintains quality, and supports multiple codecs.

**Medium 2** Tech Stack: Python, OpenCV, Streamlit, Docker  
*Short Tech Description*: Live stream filter app. OpenCV for effects.  
*Business Logic*: Applies AR filters during streams and records clips.

---

### 22. Compilers
**Big 1** Tech Stack: LLVM, C++, Python, Docker  
*Short Tech Description*: Custom language compiler. LLVM backend; Python frontend.  
*Business Logic*: Compiles domain-specific language to machine code with optimizations.

**Big 2** Tech Stack: Java, ANTLR, Docker, JUnit  
*Short Tech Description*: SQL parser with ANTLR. Java-based interpreter.  
*Business Logic*: Validates queries, generates execution plans, and optimizes joins.

**Medium 1** Tech Stack: Rust, LALRPOP, Docker  
*Short Tech Description*: JSON parser generator. LALRPOP for grammar.  
*Business Logic*: Defines schemas, validates inputs, and serializes data.

**Medium 2** Tech Stack: Python, PLY, Docker  
*Short Tech Description*: Calculator compiler. PLY for lexing/parsing.  
*Business Logic*: Evaluates expressions, handles variables, and shows ASTs.

---

### 23. Bigdata+ETL
**Big 1** Tech Stack: Spark, Kafka, Airflow, Snowflake, Docker, dbt  
*Short Tech Description*: Customer data pipeline. Kafka ingests events; Snowflake DW.  
*Business Logic*: Cleans data, builds customer 360 profiles, and powers BI dashboards.

**Big 2** Tech Stack: Flink, Beam, BigQuery, Python, Docker  
*Short Tech Description*: Real-time fraud detection. Flink processes streams; BigQuery stores results.  
*Business Logic*: Flags suspicious transactions using ML models and blocks payments.

**Medium 1** Tech Stack: Airflow, dbt, PostgreSQL, Docker  
*Short Tech Description*: Sales reporting pipeline. Airflow schedules jobs; dbt transforms.  
*Business Logic*: Aggregates sales data, calculates KPIs, and emails reports.

**Medium 2** Tech Stack: NiFi, Kafka, Elasticsearch, Docker  
*Short Tech Description*: Log ingestion flow. NiFi routes data; Elasticsearch indexes.  
*Business Logic*: Collects logs from apps, parses fields, and enables search.

---

### 24. Blockchain
**Big 1** Tech Stack: Solidity, Web3.js, React, IPFS, Truffle, Docker  
*Short Tech Description*: NFT marketplace. Ethereum blockchain; IPFS for metadata.  
*Business Logic*: Mints NFTs, handles auctions, and manages royalties.

**Big 2** Tech Stack: Hyperledger Fabric, Go, REST, Docker  
*Short Tech Description*: Supply chain tracker. Hyperledger for permissioned ledger.  
*Business Logic*: Records product journeys, verifies authenticity, and tracks recalls.

**Medium 1** Tech Stack: Ethereum, Python, Web3.py, Docker  
*Short Tech Description*: Voting dApp. Python smart contracts.  
*Business Logic*: Ensures vote anonymity, tallies results, and prevents double-voting.

**Medium 2** Tech Stack: Rust, Solana, Anchor, Docker  
*Short Tech Description*: DeFi lending protocol. Solana blockchain.  
*Business Logic*: Manages collateral, calculates interest, and liquidates undercollateralized loans.

---

### 25. GIS
**Big 1** Tech Stack: PostGIS, GeoServer, Leaflet, React, Docker  
*Short Tech Description*: Urban planning tool. PostGIS stores spatial data; Leaflet maps.  
*Business Logic*: Visualizes zoning, overlays infrastructure, and simulates development.

**Big 2** Tech Stack: QGIS, Python, GDAL, Docker  
*Short Tech Description*: Disaster response system. QGIS for analysis; Python scripts.  
*Business Logic*: Maps affected areas, plans evacuation routes, and allocates resources.

**Medium 1** Tech Stack: Mapbox, React, GeoJSON, Docker  
*Short Tech Description*: Location-based social app. Mapbox maps; React UI.  
*Business Logic*: Shares location-based posts, finds nearby events, and checks in.

**Medium 2** Tech Stack: ArcGIS, Python, Docker  
*Short Tech Description*: Field data collector. ArcGIS SDK; Python backend.  
*Business Logic*: Captures GPS points, attaches photos, and syncs to cloud.

---

### 26. Finance
**Big 1** Tech Stack: Python, Pandas, FastAPI, React, Docker, pytest  
*Short Tech Description*: Algorithmic trading platform. FastAPI for order execution.  
*Business Logic*: Backtests strategies, places trades via APIs, and tracks P&L.

**Big 2** Tech Stack: Java, Spring Boot, Kafka, Angular, Docker  
*Short Tech Description*: Loan origination system. Spring Boot workflows; Kafka events.  
*Business Logic*: Processes applications, runs credit checks, and disburses funds.

**Medium 1** Tech Stack: Python, Scikit-learn, Streamlit, Docker  
*Short Tech Description*: Credit risk scorer. Scikit-learn models; Streamlit UI.  
*Business Logic*: Predicts default risk, explains factors, and recommends actions.

**Medium 2** Tech Stack: R, Shiny, PostgreSQL, Docker  
*Short Tech Description*: Portfolio optimizer. Shiny dashboard; PostgreSQL storage.  
*Business Logic*: Balances asset allocation, minimizes risk, and projects returns.

---

### 27. Assembly
**Big 1** Tech Stack: x86 Assembly, NASM, C, Docker  
*Short Tech Description*: Bootloader for custom OS. NASM assembly; C for runtime.  
*Business Logic*: Initializes hardware, loads kernel, and switches to protected mode.

**Big 2** Tech Stack: ARM Assembly, QEMU, Docker  
*Short Tech Description*: Malware analysis tool. QEMU emulator; ARM assembly.  
*Business Logic*: Disassembles code, traces execution, and detects obfuscation.

**Medium 1** Tech Stack: x86 Assembly, NASM, Docker  
*Short Tech Description*: String encryption utility. NASM backend.  
*Business Logic*: Encrypts/decrypts text using XOR and supports key rotation.

**Medium 2** Tech Stack: MIPS Assembly, MARS, Docker  
*Short Tech Description*: Calculator simulator. MARS emulator; MIPS assembly.  
*Business Logic*: Performs arithmetic, handles errors, and displays results.

---

### 28. Medicine
**Big 1** Tech Stack: Python, FHIR, Django, React, Docker, pytest  
*Short Tech Description*: EHR system with FHIR interoperability. Django backend; React UI.  
*Business Logic*: Manages patient records, schedules appointments, and shares data with labs.

**Big 2** Tech Stack: TensorFlow, DICOM, Python, Docker  
*Short Tech Description*: Medical image analyzer. TensorFlow for tumor detection.  
*Business Logic*: Processes DICOM scans, highlights anomalies, and generates reports.

**Medium 1** Tech Stack: Python, CRISPR-Cas9, Streamlit, Docker  
*Short Tech Description*: Gene editing simulator. Streamlit UI; CRISPR models.  
*Business Logic*: Designs guide RNAs, predicts off-target effects, and visualizes edits.

**Medium 2** Tech Stack: AlphaFold, Python, Docker  
*Short Tech Description*: Protein structure predictor. AlphaFold integration.  
*Business Logic*: Predicts 3D structures from sequences, validates models, and exports PDB files.

---

### 29. Kotlin
**Big 1** Tech Stack: Kotlin, Jetpack Compose, Room, Firebase, Docker, JUnit  
*Short Tech Description*: Fitness tracking app. Jetpack Compose UI; Firebase sync.  
*Business Logic*: Logs workouts, tracks health metrics, and integrates with wearables.

**Big 2** Tech Stack: Kotlin, Ktor, PostgreSQL, React, Docker  
*Short Tech Description*: Real-time chat service. Ktor backend; React client.  
*Business Logic*: Supports group messaging, file sharing, and end-to-end encryption.

**Medium 1** Tech Stack: Kotlin, Coroutines, SQLite, Docker  
*Short Tech Description*: Weather app. Coroutines for async calls; SQLite cache.  
*Business Logic*: Fetches forecasts, displays maps, and sends severe weather alerts.

**Medium 2** Tech Stack: Kotlin, Retrofit, GraphQL, Docker  
*Short Tech Description*: Movie explorer. Retrofit for API; GraphQL queries.  
*Business Logic*: Searches movies, shows details, and manages watchlists.