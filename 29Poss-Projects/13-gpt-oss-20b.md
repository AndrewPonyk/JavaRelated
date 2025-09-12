1. Java (Boot, JakartaEE, Hibernate, Maven, Gradle, JUnit, Micronaut, Quarkus, Vert.x, GraalVM, SLF4J, GraalVM (JIT + Native‑Image))

Project	Tech Stack	Short Tech Description	Business Logic
A – Micro‑service E‑commerce API	Spring Boot, Hibernate, Maven, JUnit, SLF4J, Docker	A stateless REST API that exposes product, order, and inventory endpoints. Uses Hibernate for ORM and JUnit for unit tests.	Handles CRUD for products, processes orders, and updates inventory in real time. Integrates with a payment gateway and sends order confirmation emails.
B – Real‑time Analytics Engine	Quarkus, Vert.x, GraalVM Native‑Image, Kafka, JUnit, Docker	A reactive micro‑service that consumes event streams from Kafka, aggregates metrics, and exposes a REST endpoint. Built with Quarkus for low‑latency and compiled to a native image.	Aggregates user activity logs, calculates real‑time KPIs, and stores results in PostgreSQL. Provides a dashboard API for front‑end consumption.
C – Serverless Function Platform	Micronaut, GraalVM Native‑Image, AWS Lambda, Maven, JUnit, SLF4J	A collection of lightweight functions that run on AWS Lambda, compiled to native binaries for fast cold starts. Uses Micronaut’s dependency injection and JUnit for testing.	Each function performs a specific business task (e.g., image resizing, email sending). Exposes an API Gateway endpoint for invocation.
D – High‑performance Cache Service	Vert.x, Redis, Maven, JUnit, SLF4J, Docker	A non‑blocking, event‑driven service that provides a key‑value cache with TTL support. Uses Vert.x for concurrency and Redis as the backing store.	Offers CRUD operations for cached data, supports pub/sub for cache invalidation, and exposes metrics via Prometheus. Designed for low‑latency read/write in a distributed environment.
2. Multithreading (Threads, Concurrency Primitives, Synchronization, Race Conditions, Deadlocks, Semaphores, Atomic Operations, Thread Pools, Starvation, Producer‑Consumer, Dining Philosophers, Sleeping Barber)

Project	Tech Stack	Short Tech Description	Business Logic
A – Parallel File Processor	Java Threads, ExecutorService, AtomicInteger, SLF4J	A desktop app that scans a directory, processes files in parallel, and writes results to a database. Uses a fixed thread pool and atomic counters for progress.	Reads large log files, extracts error patterns, and stores summaries. Provides a GUI progress bar and logs each thread’s activity.
B – Thread‑Safe Bank Transfer System	Java Concurrency, ReentrantLock, Condition, JUnit, Docker	A micro‑service that handles money transfers between accounts, ensuring consistency with fine‑grained locks. Uses ReentrantLock and Condition for wait/notify.	Validates balances, locks source and destination accounts, performs debit/credit, and logs the transaction. Prevents deadlocks by ordering lock acquisition.
C – Producer‑Consumer Message Queue	Java BlockingQueue, Semaphore, ThreadPoolExecutor, JUnit	A lightweight in‑memory queue that supports multiple producers and consumers with back‑pressure. Uses a bounded BlockingQueue and semaphores to control flow.	Producers generate tasks (e.g., image thumbnails), consumers process them, and results are persisted. Handles graceful shutdown and metrics.
D – Dining Philosophers Simulation	Java Threads, CyclicBarrier, JUnit, SLF4J	A console application that simulates the classic dining philosophers problem with configurable philosophers and forks. Uses CyclicBarrier to synchronize rounds.	Demonstrates deadlock avoidance by alternating fork acquisition order. Logs each philosopher’s state and measures starvation incidents.
3. Python (Django, pytest, Flask, FastAPI, NumPy, Pandas, PyTorch, TensorFlow, Jupyter, Asyncio)

Project	Tech Stack	Short Tech Description	Business Logic
A – Data‑Driven Sales Dashboard	Django, Pandas, NumPy, Plotly, pytest	A web app that ingests CSV sales data, performs ETL with Pandas, and visualizes KPIs. Uses Django ORM for persistence and Plotly for charts.	Allows users to upload sales files, view monthly revenue, and export filtered reports. Implements role‑based access for managers.
B – Async Chat Server	FastAPI, WebSockets, Asyncio, pytest	A real‑time chat application built on FastAPI with WebSocket support. Uses Asyncio for non‑blocking I/O and pytest for integration tests.	Supports multiple chat rooms, user authentication, and message history stored in PostgreSQL. Provides a REST API for room management.
C – Image Classification API	Flask, PyTorch, TorchVision, NumPy, pytest	A REST API that accepts image uploads, runs a pre‑trained ResNet model, and returns predicted labels. Uses Flask for routing and PyTorch for inference.	Accepts JPEG/PNG, preprocesses with TorchVision transforms, and returns top‑5 predictions with confidence scores. Logs inference latency.
D – Jupyter Notebook for Time‑Series Forecasting	Jupyter, Pandas, Statsmodels, Prophet, NumPy	An interactive notebook that loads historical sales data, performs feature engineering, and trains a Prophet model. Uses Pandas for data wrangling.	Generates 30‑day forecasts, visualizes residuals, and exports predictions to CSV. Includes parameter tuning and cross‑validation.
4. Machine Learning (Scikit‑learn, TensorFlow, PyTorch, Keras, XGBoost, LightGBM, CatBoost, HuggingFace Transformers, Langchain, LangGraph & Langsmith)

Project	Tech Stack	Short Tech Description	Business Logic
A – Customer Churn Predictor	Scikit‑learn, Pandas, XGBoost, Flask, Docker	A web service that predicts churn probability for telecom customers. Uses XGBoost trained on historical churn data.	Exposes a REST endpoint that accepts customer features and returns churn risk. Stores predictions in a PostgreSQL database for audit.
B – Sentiment Analysis Chatbot	HuggingFace Transformers, FastAPI, Langchain, Docker	A chatbot that classifies user messages as positive/negative/neutral and responds accordingly. Uses a pre‑trained BERT model via HuggingFace.	Integrates with a messaging platform, logs conversations, and updates a sentiment dashboard. Supports fine‑tuning on domain data.
C – Image Segmentation Pipeline	TensorFlow, Keras, OpenCV, PyTorch, Jupyter	A notebook that trains a U‑Net model for medical image segmentation. Uses TensorFlow/Keras for training and PyTorch for inference.	Loads annotated MRI scans, performs data augmentation, trains the model, and evaluates Dice coefficient. Exports a TensorFlow Lite model for mobile deployment.
D – Conversational Retrieval System	LangGraph, Langsmith, OpenAI API, FastAPI	A retrieval‑augmented generation system that answers user queries using a knowledge base. Uses LangGraph for dialogue flow and Langsmith for monitoring.	Indexes PDF documents, retrieves relevant passages, and generates responses via GPT‑4. Provides a web UI and logs conversation quality metrics.
5. AWS (EC2, S3, EKS, Lambda, RDS, DynamoDB, VPC, IAM, CloudFormation, CloudWatch, SNS/SQS, Bedrock, SageMaker)

Project	Tech Stack	Short Tech Description	Business Logic
A – Scalable E‑commerce Platform	EKS, EC2, RDS, S3, CloudFormation, CloudWatch, IAM	A Kubernetes‑based micro‑service architecture running on EKS, with EC2 nodes and RDS for relational data. Uses CloudFormation for IaC and CloudWatch for observability.	Handles product catalog, cart, checkout, and payment services. Auto‑scales based on traffic and stores static assets in S3.
B – Serverless Image Processing Pipeline	Lambda, S3, SNS, SQS, DynamoDB, CloudFormation	Images uploaded to S3 trigger a Lambda chain that resizes, compresses, and stores metadata in DynamoDB. SNS notifies downstream services.	Supports a photo‑sharing app where users upload images; the pipeline generates thumbnails and updates user galleries.
C – Machine‑Learning Model Hosting	SageMaker, ECR, IAM, CloudWatch, CloudFormation	Trains a recommendation model in SageMaker, pushes the container to ECR, and deploys it as a real‑time endpoint. Uses CloudWatch for monitoring.	Provides personalized product recommendations to the e‑commerce front‑end via a REST API.
D – Multi‑Region Disaster Recovery	EC2, RDS, S3, VPC Peering, CloudFormation, CloudWatch	Replicates primary database and storage to a secondary region using cross‑region replication. Uses VPC peering for secure traffic.	Ensures high availability; automatic failover is triggered by CloudWatch alarms. Provides a backup strategy and point‑in‑time recovery.
6. Databases (MySQL, PostgreSQL, Oracle, SQL Server, MongoDB, Redis, Cassandra, Elasticsearch, Snowflake, Neo4j, Pinecone, Weaviate, Milvus)

Project	Tech Stack	Short Tech Description	Business Logic
A – Real‑time Analytics with Snowflake	Snowflake, Python, Airflow, dbt, Tableau	ETL pipeline that loads clickstream data into Snowflake, transforms with dbt, and visualizes in Tableau.	Provides daily dashboards for marketing spend, user acquisition, and conversion rates.
B – Graph‑Based Recommendation Engine	Neo4j, Java, Spring Boot, JUnit	Stores user‑item interactions as a graph and runs a recommendation algorithm using Cypher queries.	Generates personalized item suggestions based on social connections and purchase history.
C – Vector Search Service	Pinecone, Python, FastAPI, Docker	Indexes product embeddings and exposes a REST API for semantic search. Uses Pinecone’s managed vector store.	Allows users to search products by natural language queries, returning the most semantically relevant items.
D – Distributed Key‑Value Store	Cassandra, Java, Spring Data, Docker	A highly available, fault‑tolerant key‑value store for session data. Uses Cassandra’s tunable consistency.	Stores user session tokens, supports read/write scaling, and integrates with a load balancer.
7. Algorithms (Sorting, Searching, Graph, Dynamic Programming, Greedy, Divide‑and‑Conquer, Backtracking, String, Computational Geometry)

Project	Tech Stack	Short Tech Description	Business Logic
A – Route Optimizer	Java, Dijkstra, A*, JUnit	Calculates optimal delivery routes for a fleet of vehicles. Uses graph algorithms to find shortest paths.	Considers traffic constraints, vehicle capacity, and time windows. Generates daily route plans for dispatch.
B – DNA Sequence Alignment Tool	Python, Biopython, Needleman‑Wunsch, Flask	Aligns two DNA sequences using dynamic programming. Provides a web interface for input and results.	Supports pairwise alignment, visualizes matches/mismatches, and outputs alignment score.
C – Sudoku Solver	C++, Backtracking, STL, Google Test	Solves Sudoku puzzles using recursive backtracking. Optimized with constraint propagation.	Accepts puzzle input via CLI, returns solved grid, and logs solving time.
D – Convex Hull Visualizer	JavaScript, Canvas, Graham Scan, Jest	Interactive web app that lets users place points and visualizes the convex hull. Uses Graham Scan algorithm.	Demonstrates computational geometry concepts; allows export of hull coordinates.
8. JavaScript (ES6+, Next.js, TypeScript, Node.js, React, Vue.js, Angular, Express, Jest, Webpack, Babel, Vite, Svelte)

Project	Tech Stack	Short Tech Description	Business Logic
A – Full‑stack Task Manager	Next.js, TypeScript, React, Node.js, Express, PostgreSQL, Jest	Server‑side rendered React app with API routes in Express. Uses TypeScript for type safety.	Users can create, update, delete tasks; supports real‑time collaboration via WebSockets.
B – E‑commerce SPA	Vue.js, Vite, TypeScript, Node.js, Express, MongoDB, Jest	Single‑page application with product catalog, cart, and checkout. Uses Vite for fast dev builds.	Implements product search, pagination, and payment integration with Stripe.
C – Real‑time Dashboard	Angular, RxJS, Node.js, Express, Redis, Jest	Dashboard that streams live metrics from a backend via WebSockets. Uses RxJS for reactive streams.	Displays CPU, memory, and custom application metrics; allows threshold alerts.
D – Static Site Generator	Svelte, SvelteKit, TypeScript, Vite, Markdown, Jest	Generates a static blog site from Markdown files. Uses SvelteKit for routing.	Supports tag filtering, RSS feed, and incremental builds. Deploys to Netlify.
9. Web Design (HTML5, Bulma, Sass, Bootstrap, Tailwind CSS, Figma, Adobe XD, Sketch, Webflow)

Project	Tech Stack	Short Tech Description	Business Logic
A – Portfolio Website	HTML5, Tailwind CSS, Sass, Figma	Responsive personal portfolio built with Tailwind and Sass. Designed in Figma and exported to code.	Showcases projects, blog posts, and contact form. Uses Netlify Forms for submissions.
B – Corporate Landing Page	Bootstrap, HTML5, CSS3, Adobe XD	High‑fidelity landing page for a B2B SaaS product. Uses Bootstrap grid and components.	Features hero section, feature list, pricing table, and CTA. Integrates with Google Analytics.
C – E‑commerce Product Page	Bulma, HTML5, Sass, Sketch	Product detail page with image carousel, reviews, and add‑to‑cart button. Styled with Bulma.	Connects to a backend API for inventory and pricing. Implements lazy loading of images.
D – Interactive Data Viz	D3.js, HTML5, CSS3, Webflow	Data‑driven interactive chart embedded in a Webflow site. Uses D3 for SVG manipulation.	Visualizes quarterly sales; allows drill‑down by region. Exports data to CSV.
10. Security (Metasploit, Kali Linux, Burp Suite, nmap, Wireshark, OWASP ZAP, Cryptography, Malware Analysis, Web App Security)

Project	Tech Stack	Short Tech Description	Business Logic
A – Red‑Team Automation	Python, Metasploit, nmap, Wireshark, Docker	Automates reconnaissance, vulnerability scanning, and exploitation. Uses Metasploit modules via API.	Generates a report of discovered hosts, open ports, and exploited services.
B – Web App Pen‑Test Toolkit	Burp Suite, OWASP ZAP, Selenium, Python	Combines automated scanners with manual testing scripts. Uses Selenium for dynamic content.	Detects XSS, CSRF, SQLi, and authentication bypasses. Outputs a detailed vulnerability report.
C – Malware Sandbox	C++, Wireshark, Docker, Python	Executes suspicious binaries in an isolated container, captures network traffic, and analyzes behavior.	Detects persistence mechanisms, file system changes, and outbound connections.
D – Secure API Gateway	Node.js, Express, JWT, OAuth2, OpenSSL	Provides authentication, rate limiting, and request validation for micro‑services.	Validates JWT tokens, enforces scopes, and logs all requests. Protects against injection and replay attacks.
11. DevOps (Jenkins, GitHub Actions, Docker, Kubernetes, Ansible, Terraform, Prometheus, Grafana, ELK)

Project	Tech Stack	Short Tech Description	Business Logic
A – CI/CD Pipeline for Micro‑services	Jenkins, Docker, Kubernetes, Helm, Prometheus, Grafana	Builds, tests, and deploys micro‑services to a Kubernetes cluster. Uses Helm charts for deployment.	Automates rollbacks on failure, monitors health, and visualizes metrics.
B – Infrastructure as Code Demo	Terraform, Ansible, AWS, Docker	Provisions a VPC, EC2 instances, RDS, and S3 buckets. Uses Ansible for configuration.	Enables reproducible environments for development and staging.
C – Observability Stack	ELK (Elasticsearch, Logstash, Kibana), Prometheus, Grafana	Collects logs, metrics, and traces from applications. Provides dashboards and alerting.	Monitors application performance, detects anomalies, and correlates logs with metrics.
D – Blue/Green Deployment	GitHub Actions, Docker, Kubernetes, Helm, Terraform	Deploys new application version to a separate environment, switches traffic after validation.	Minimizes downtime, allows quick rollback, and ensures zero‑downtime releases.
12. C# (.NET 8, ASP.NET Core, Blazor, Entity Framework, LINQ, MAUI, WPF, NUnit)

Project	Tech Stack	Short Tech Description	Business Logic
A – Inventory Management System	ASP.NET Core, EF Core, SQL Server, Blazor Server, NUnit	Web app that tracks stock levels, orders, and suppliers. Uses Blazor for interactive UI.	Provides real‑time inventory dashboards, reorder alerts, and supplier management.
B – Desktop POS Application	WPF, .NET 8, SQLite, MVVM, NUnit	Point‑of‑sale desktop app for small retailers. Stores data locally in SQLite.	Handles sales, returns, inventory updates, and generates receipts.
C – Cross‑Platform Mobile App	MAUI, .NET 8, Azure Mobile Apps, EF Core	Mobile app for field agents to log customer visits. Syncs data with Azure backend.	Supports offline mode, geolocation tagging, and push notifications.
D – API for Financial Analytics	ASP.NET Core, EF Core, PostgreSQL, LINQ, Swagger	REST API that aggregates financial metrics from multiple sources.	Exposes endpoints for portfolio performance, risk metrics, and transaction history.
13. Go (Goroutines, Channels, net/http, Gin, Go Modules, gRPC, Docker, Testify)

Project	Tech Stack	Short Tech Description	Business Logic
A – High‑throughput Log Aggregator	Go, Gin, Channels, ElasticSearch, Docker	Collects logs from micro‑services, processes them concurrently, and indexes into ElasticSearch.	Provides real‑time log search, alerting, and retention policies.
B – gRPC‑Based Chat Service	Go, gRPC, Protocol Buffers, Docker	Implements a chat server with bi‑directional streaming. Uses goroutines for each client.	Supports group chats, message history, and presence indicators.
C – Distributed Key‑Value Store	Go, Raft, BoltDB, Docker	Implements a consensus‑based key‑value store with replication.	Provides ACID guarantees, sharding, and client API.
D – RESTful Image Processing API	Go, Gin, Go Modules, Docker, Testify	Accepts image uploads, resizes, and returns processed image.	Uses goroutines for concurrent processing, caches results, and logs usage.
14. Rust (Cargo, Rustup, Crates.io, Tokio, WebAssembly, Actix, Rocket, Diesel, Serde)

Project	Tech Stack	Short Tech Description	Business Logic
A – WebAssembly Game Engine	Rust, wasm-bindgen, WebGL, Cargo	Compiles a 2D game engine to WebAssembly for browser execution.	Provides physics, rendering, and input handling; exposes API to JavaScript.
B – High‑performance API Server	Actix‑Web, Diesel, PostgreSQL, Tokio	REST API that handles thousands of concurrent requests with async I/O.	Supports user authentication, data CRUD, and rate limiting.
C – CLI Data Processor	Rust, Clap, Serde, Rayon	Command‑line tool that processes large CSV files in parallel.	Performs filtering, aggregation, and writes results to JSON.
D – Secure Messaging Service	Rocket, Diesel, OpenSSL, Tokio	Provides end‑to‑end encrypted messaging over HTTPS.	Stores encrypted payloads, manages keys, and logs message metadata.
15. PHP (Laravel, Symfony, Composer, PHPUnit, Doctrine, Twig)

Project	Tech Stack	Short Tech Description	Business Logic
A – Content Management System	Laravel, Blade, MySQL, PHPUnit	Web CMS that allows authors to create, edit, and publish articles.	Supports user roles, media uploads, SEO metadata, and comment moderation.
B – E‑commerce Platform	Symfony, Doctrine, Twig, PHPUnit	Full‑stack e‑commerce site with product catalog, cart, and checkout.	Integrates with payment gateway, handles inventory, and sends order emails.
C – API for IoT Devices	Laravel, API Resources, MySQL, PHPUnit	REST API that receives telemetry from IoT sensors and stores data.	Provides authentication, data validation, and real‑time dashboards.
D – SaaS Subscription Service	Symfony, Doctrine, Stripe, PHPUnit	Manages user subscriptions, billing, and usage limits.	Implements webhooks for payment events, auto‑cancellation, and invoicing.
16. Ruby (Rails, Sinatra, Hanami, Bundler, RSpec, RuboCop, Sidekiq, Capistrano)

Project	Tech Stack	Short Tech Description	Business Logic
A – Social Networking Site	Rails, PostgreSQL, Sidekiq, RSpec	Users can create profiles, post updates, and follow others.	Handles real‑time notifications, feed generation, and privacy settings.
B – API for Mobile App	Sinatra, MongoDB, RSpec	Lightweight API that serves data to a native mobile app.	Provides endpoints for user auth, content retrieval, and push notifications.
C – Task Automation Platform	Hanami, Redis, Sidekiq, RSpec	Allows users to schedule and run background jobs.	Supports cron‑style scheduling, job monitoring, and retry logic.
D – E‑learning Portal	Rails, MySQL, RSpec	Course management system with quizzes, progress tracking, and certificates.	Handles enrollment, content delivery, and analytics dashboards.
17. C++ (STL, Boost, RAII, Qt, CUDA, Concurrency, Clang/GCC, CMake)

Project	Tech Stack	Short Tech Description	Business Logic
A – High‑performance Image Processor	C++, OpenCV, CUDA, CMake	Accelerates image filtering using GPU kernels.	Processes large image datasets, supports batch operations, and outputs results to disk.
B – GUI File Explorer	Qt, C++, STL, CMake	Desktop application that navigates file system with drag‑and‑drop.	Provides search, preview, and context menu actions.
C – Multi‑threaded Web Server	C++, Boost.Asio, STL, CMake	Handles HTTP requests concurrently using thread pool.	Supports static file serving, CGI, and basic authentication.
D – Scientific Simulation Engine	C++, Eigen, Boost, CMake	Simulates particle physics with parallel computation.	Uses RAII for resource management, outputs simulation data to CSV.
18. C (GCC, Clang, Make/CMake, Valgrind, GDB, Embedded C, OpenMP, GTK)

Project	Tech Stack	Short Tech Description	Business Logic
A – Embedded Sensor Firmware	C, GCC, Make, OpenMP	Firmware for a microcontroller that reads sensor data and transmits via UART.	Implements watchdog, power‑saving modes, and data buffering.
B – GTK Desktop Utility	C, GTK, Make, Valgrind	GUI tool that monitors system resources and displays charts.	Uses OpenMP for parallel data collection, updates UI in real time.
C – High‑speed Network Packet Sniffer	C, libpcap, GCC, GDB	Captures and analyzes network packets, filters by protocol.	Provides live statistics, packet dump, and export to pcap file.
D – Memory‑leak Detector	C, Valgrind, GCC, Make	Tool that runs user programs and reports memory usage.	Generates detailed reports, highlights leaks, and suggests fixes.
19. Flutter (Dart, Hot Reload, Widgets, Material Design, Cupertino, BLoC, Provider, Riverpod, FlutterFlow)

Project	Tech Stack	Short Tech Description	Business Logic
A – Fitness Tracker App	Flutter, Firebase, Riverpod	Mobile app that logs workouts, tracks progress, and syncs with cloud.	Stores workout sessions, calculates calories burned, and displays charts.
B – E‑commerce Mobile Store	Flutter, BLoC, Stripe, Firebase	Shopping app with product catalog, cart, and checkout.	Handles user authentication, payment processing, and order tracking.
C – Real‑time Chat App	Flutter, Provider, WebSocket, Firebase	Cross‑platform chat with group rooms and media sharing.	Supports push notifications, read receipts, and offline message queue.
D – Restaurant Reservation System	Flutter, Riverpod, Google Maps API, Firebase	Allows users to view restaurants, check availability, and book tables.	Integrates with Google Maps for location, sends confirmation emails.
20. Game Development (Unity, Unreal Engine, Godot, CryEngine, GameMaker Studio, Cocos2d‑x, Blender, Substance Painter, Photon Networking)

Project	Tech Stack	Short Tech Description	Business Logic
A – 2D Platformer	Godot, GDScript, Blender	Side‑scroller with physics, enemies, and level editor.	Implements player controls, AI, and level progression.
B – Multiplayer FPS	Unity, C#, Photon Networking	First‑person shooter with real‑time multiplayer.	Handles matchmaking, player stats, and in‑game economy.
C – Puzzle Game	Unreal Engine, C++, Blueprints	3D puzzle with physics interactions.	Provides level design tools, scoring, and achievements.
D – VR Exploration Game	Unity, C#, Oculus SDK	Virtual reality adventure with interactive environment.	Supports hand tracking, object manipulation, and immersive audio.
21. Video (Processing, Optimizing, Video Algorithms)

Project	Tech Stack	Short Tech Description	Business Logic
A – Video Compression Tool	Python, FFmpeg, PyAV	CLI that compresses videos to target bitrate while preserving quality.	Uses two‑pass encoding, supports multiple codecs, and outputs stats.
B – Real‑time Object Detection in Video	Python, OpenCV, TensorFlow, Docker	Detects objects frame‑by‑frame and draws bounding boxes.	Streams processed video to a web UI, logs detection counts.
C – Video Stitching Application	C++, OpenCV, Qt	Merges multiple video clips into a single panorama.	Handles camera motion, color correction, and seam blending.
D – Adaptive Bitrate Streaming Server	Node.js, HLS.js, FFmpeg, Docker	Transcodes uploaded videos into multiple resolutions and serves via HLS.	Detects client bandwidth, switches streams, and logs usage.
22. Compilers (Parsing Theory, LLVM, JIT/AOT, Static Analysis, Type Systems)

Project	Tech Stack	Short Tech Description	Business Logic
A – Mini‑Language Compiler	LLVM, C++, Flex/Bison	Compiles a toy language into LLVM IR, then JITs to native code.	Supports arithmetic, control flow, and function calls.
B – Static Analyzer for Python	Python, AST, mypy, Docker	Analyzes Python code for type errors and potential bugs.	Generates reports, highlights unsafe patterns, and suggests fixes.
C – JIT‑Enabled Scripting Engine	Java, GraalVM, Truffle	Executes dynamic scripts with JIT compilation for performance.	Provides sandboxed execution, memory limits, and profiling.
D – Type‑Inference Library	Haskell, GHC, QuickCheck	Implements Hindley–Milner type inference for a functional language.	Parses code, infers types, and reports type errors.
23. Big Data + ETL (Spark, Kafka, Hive/Trino, Flink, Airflow, dbt, NiFi, Snowflake, Databricks, AWS Glue, BigQuery, Beam)

Project	Tech Stack	Short Tech Description	Business Logic
A – Real‑time Fraud Detection Pipeline	Kafka, Flink, Spark, PostgreSQL	Streams transaction data, applies ML model, flags fraud.	Uses Flink for low‑latency processing, stores results in PostgreSQL.
B – Batch ETL for Retail Analytics	Airflow, dbt, Snowflake, Spark	Extracts sales data, transforms, loads into Snowflake.	Generates daily sales reports, inventory forecasts, and dashboards.
C – Data Lake Ingestion	NiFi, Hadoop, Hive, Trino	Ingests raw logs into HDFS, catalogs with Hive, queries via Trino.	Supports schema evolution, data lineage, and access control.
D – Serverless Data Pipeline	AWS Glue, Lambda, S3, Athena	Processes CSV uploads, cleans data, stores in S3, queries via Athena.	Automates data quality checks, generates metrics, and triggers alerts.
24. Blockchain (Solidity, Python, Rust, Web3.js, SHA‑256, Truffle, IPFS, Hardhat, OpenZeppelin, Ganache, Ethers.js, Ethereum, Hyperledger Fabric, PoW, Merkle Trees, Trezor, MythX, PoS, PBFT)

Project	Tech Stack	Short Tech Description	Business Logic
A – Decentralized Marketplace	Solidity, Hardhat, OpenZeppelin, Web3.js, IPFS	Smart contract for buying/selling digital goods, stores metadata on IPFS.	Handles escrow, dispute resolution, and royalty distribution.
B – Supply Chain Traceability	Hyperledger Fabric, Go, Node.js, CouchDB	Tracks product provenance across multiple stakeholders.	Records each transfer, verifies authenticity, and provides audit trail.
C – PoW Mining Simulator	Rust, SHA‑256, OpenCL	Simulates proof‑of‑work mining, visualizes hash rate and difficulty.	Demonstrates mining economics, block rewards, and network security.
D – Smart‑Contract Auditing Tool	Python, MythX API, Solidity	Static analysis of contracts for vulnerabilities.	Generates risk reports, suggests mitigations, and integrates with CI.
25. GIS (ArcGIS, QGIS, PostGIS, Mapbox, Leaflet, GeoServer, GDAL, OpenLayers, Cesium, GeoJSON, R‑tree, Shapefile)

Project	Tech Stack	Short Tech Description	Business Logic
A – Urban Planning Dashboard	Leaflet, GeoServer, PostGIS, Node.js	Visualizes zoning, traffic, and demographic layers.	Allows planners to overlay proposals, run spatial queries, and export maps.
B – Asset Tracking System	Mapbox GL JS, GeoJSON, PostgreSQL, Docker	Tracks fleet vehicles in real time on a map.	Provides route optimization, ETA calculations, and geofencing alerts.
C – Environmental Impact Analyzer	QGIS, GDAL, Python, R‑tree	Analyzes land use changes over time.	Generates heatmaps, calculates affected areas, and exports reports.
D – 3D Terrain Viewer	Cesium, GeoJSON, Node.js	Renders high‑resolution terrain and building models.	Supports navigation, measurement tools, and layer toggling.
26. Finance (Loan Origination, Credit‑Scoring, VaR, Portfolio Optimization, Market Prediction, Algorithmic Trading, Derivatives Pricing, Asset Allocation, Treasury Management)

Project	Tech Stack	Short Tech Description	Business Logic
A – Credit‑Scoring Engine	Python, scikit‑learn, Flask, PostgreSQL	Trains logistic regression model on borrower data.	Scores applicants, flags high‑risk, and integrates with loan origination API.
B – Portfolio Optimization Tool	Python, CVXPY, Pandas, Flask	Solves mean‑variance optimization with constraints.	Generates asset allocation, rebalancing schedules, and risk metrics.
C – Algorithmic Trading Bot	Python, Zipline, Alpaca API, Docker	Executes market‑making strategy on equities.	Monitors order book, places limit orders, and logs P&L.
D – Derivatives Pricing Service	C++, QuantLib, gRPC, Docker	Prices options using Black‑Scholes and Monte Carlo.	Exposes API for pricing, Greeks calculation, and sensitivity analysis.
27. Assembly (x86, ARM, NASM, MASM, Machine Code, System Calls, Linker)

Project	Tech Stack	Short Tech Description	Business Logic
A – Bootloader	NASM, x86, BIOS, Linker	Implements a simple 16‑bit bootloader that loads a kernel.	Parses MBR, loads kernel into memory, and transfers control.
B – System Call Wrapper	C, Assembly, Linux, GCC	Provides C wrappers for Linux syscalls using inline assembly.	Enables file I/O, process creation, and memory mapping.
C – Performance‑Critical Math Library	ARM, Assembly, GCC, Make	Implements fast matrix multiplication in ARM assembly.	Optimized for NEON SIMD, used in embedded DSP.
D – Reverse‑Engineering Demo	x86, IDA, Ghidra, NASM	Disassembles a small binary, reconstructs source.	Demonstrates control flow, stack usage, and function prologues.
28. Medicine (FHIR, AlphaFold, CRISPR‑Cas9, DICOM)

Project	Tech Stack	Short Tech Description	Business Logic
A – FHIR Patient Portal	Java, Spring Boot, HAPI‑FHIR, PostgreSQL	Web portal for patients to view records, schedule appointments.	Implements FHIR resources (Patient, Observation), secure auth, and audit logging.
B – Protein Structure Predictor	Python, AlphaFold, PyTorch, Docker	Runs AlphaFold to predict protein 3D structures from sequences.	Accepts FASTA input, returns PDB file, and visualizes with PyMOL.
C – CRISPR Guide Design Tool	Python, Biopython, Flask, PostgreSQL	Designs guide RNAs for target genes, checks off‑target sites.	Provides scoring, visual heatmaps, and export to CSV.
D – DICOM Viewer	C++, Qt, DCMTK, Docker	Desktop app to view, annotate, and convert DICOM images.	Supports multi‑modal imaging, DICOMweb, and PACS integration.
29. Kotlin (Android SDK, Jetpack Compose, Gradle, Retrofit, Coroutines, Room, Firebase, Ktor)

Project	Tech Stack	Short Tech Description	Business Logic
A – Social Media App	Android, Jetpack Compose, Retrofit, Coroutines, Firebase	Native Android app for posting, liking, and following.	Handles real‑time updates via Firebase Realtime Database, image uploads, and push notifications.
B – Expense Tracker	Android, Room, Kotlin Coroutines, Jetpack Compose	Tracks personal expenses, categorizes, and visualizes trends.	Syncs with Google Drive, supports offline mode, and generates monthly reports.
C – REST API Server	Ktor, Exposed, PostgreSQL, Gradle	Kotlin backend exposing CRUD endpoints for a task manager.	Uses coroutines for async I/O, JWT auth, and rate limiting.
D – IoT Device Controller	Kotlin, Ktor, MQTT, Coroutines	Server that receives sensor data via MQTT, stores in InfluxDB.	Provides WebSocket API for real‑time dashboards, alerts on thresholds.