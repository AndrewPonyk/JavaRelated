# Project List for 29 Technical Domains

## 1. Java

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** E-Commerce Platform | Java (Spring Boot, Hibernate, Maven), MySQL, React, Redux, Tailwind CSS, Docker, Kubernetes, JUnit | A Spring Boot backend with Hibernate for ORM manages product catalogs, user accounts, and order processing, deployed via Docker and Kubernetes for scalability. React with Redux and Tailwind CSS provides a responsive frontend for browsing and checkout. | Users can browse products, add to cart, and complete purchases with payment integration. Admins manage inventory, process orders, and generate sales reports. |
| **Big Project 2:** Real-Time Chat Application | Java (Quarkus, Vert.x, GraalVM Native Image), PostgreSQL, Vue.js, WebSocket, SLF4J, Gradle, JUnit, AWS EC2 | Quarkus with Vert.x handles real-time WebSocket communication for low-latency messaging, compiled to native image with GraalVM for performance. Vue.js frontend provides a dynamic chat interface, with PostgreSQL storing chat history and user data. | Users can join chat rooms, send/receive real-time messages, and view conversation history. Admins moderate chats and manage user permissions. |
| **Medium Project 1:** Task Management System | Java (Micronaut, Maven), MongoDB, Angular, Bootstrap, JUnit, Jenkins | Micronaut provides a lightweight, reactive backend for managing tasks and user roles, with MongoDB for flexible data storage. Angular with Bootstrap delivers a clean, responsive UI for task creation and tracking. | Users create, assign, and track tasks with deadlines and priorities. Managers can monitor progress and generate task completion reports. |
| **Medium Project 2:** API Gateway for Microservices | Java (Spring Boot, Gradle), Redis, Node.js, Express, JUnit, Docker | Spring Boot serves as an API gateway routing requests to microservices, with Redis for caching to improve performance. Node.js with Express provides a simple frontend dashboard for monitoring API usage. | The gateway authenticates and routes client requests to appropriate microservices. It provides metrics on API usage and response times for optimization. |

## 2. Multithreading

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Parallel Data Processing Pipeline | Java (Threads, Thread Pools, Locks), Python (Pandas, NumPy), AWS S3, Docker, JUnit | Java thread pools process large datasets in parallel, using locks to prevent race conditions, with data stored in AWS S3. Python with Pandas and NumPy handles post-processing analytics, containerized with Docker. | Processes massive datasets (e.g., logs) to generate insights like user behavior trends. Ensures data consistency and high throughput for real-time analytics. |
| **Big Project 2:** Dining Philosophers Simulation | Java (Semaphores, Synchronization), PostgreSQL, React, Tailwind CSS, JUnit, Kubernetes | Java implements the Dining Philosophers problem using semaphores to avoid deadlocks, with PostgreSQL logging simulation states. React with Tailwind CSS visualizes philosopher states and resource allocation. | Simulates resource contention among philosophers to demonstrate deadlock prevention. Provides educational insights into concurrency control for students. |
| **Medium Project 1:** Producer-Consumer Queue | Java (Threads, Atomic Operations), Redis, Node.js, Express, JUnit | Java uses threads and atomic operations to implement a producer-consumer queue, with Redis for persistent message storage. Node.js with Express offers a simple UI to monitor queue status. | Producers add tasks to a queue, while consumers process them concurrently. Ensures no data loss and efficient task distribution. |
| **Medium Project 2:** Sleeping Barber Simulation | Java (Thread Pools, Mutexes), MongoDB, Vue.js, JUnit, Docker | Java simulates the Sleeping Barber problem using thread pools and mutexes to manage customer queues, with MongoDB storing simulation logs. Vue.js provides a frontend to visualize barber and customer states. | Models a barber shop where customers wait for service, demonstrating thread synchronization. Tracks wait times and service efficiency for analysis. |

## 3. Python

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Social Media Analytics Dashboard | Python (Django, Pandas, NumPy), PostgreSQL, React, Tailwind CSS, pytest, AWS EC2 | Django serves as the backend for user authentication and data processing, with Pandas and NumPy analyzing social media metrics. React with Tailwind CSS provides an interactive dashboard for visualizing trends. | Users upload social media data to analyze engagement metrics like likes and shares. Generates reports to optimize content strategies. |
| **Big Project 2:** Real-Time Stock Trading Platform | Python (FastAPI, Asyncio, Pandas), Redis, Vue.js, pytest, AWS Lambda, CloudWatch | FastAPI with Asyncio handles real-time stock data APIs, with Redis caching frequent queries. Vue.js frontend displays live stock prices and trading options, monitored via CloudWatch. | Users can monitor stock prices and execute trades in real time. Provides historical trade analysis for portfolio management. |
| **Medium Project 1:** Blog Platform | Python (Flask, pytest), SQLite, Angular, Bootstrap, Jenkins | Flask powers a lightweight backend for blog post management, with SQLite for data storage. Angular with Bootstrap creates a responsive frontend for reading and editing posts. | Users can create, edit, and comment on blog posts. Admins moderate content and manage user accounts. |
| **Medium Project 2:** Data Analysis Notebook | Python (Jupyter, Pandas, NumPy), MongoDB, pytest, Docker | Jupyter notebooks enable interactive data analysis with Pandas and NumPy, storing results in MongoDB. Docker ensures consistent environments for analysis workflows. | Analysts explore datasets and generate visualizations for business insights. Saves analysis results for future reference. |

## 4. Machine Learning

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Sentiment Analysis for Customer Reviews | Python (HuggingFace Transformers, Scikit-learn, Pandas), AWS Sagemaker, PostgreSQL, React, pytest | HuggingFace Transformers train a sentiment analysis model on review data, deployed via AWS Sagemaker. React frontend displays sentiment trends, with PostgreSQL storing results. | Analyzes customer reviews to classify sentiment (positive/negative). Helps businesses understand customer satisfaction and improve products. |
| **Big Project 2:** Image Classification Service | Python (TensorFlow, Keras, NumPy), AWS EC2, MongoDB, Vue.js, pytest, Docker | TensorFlow and Keras build a convolutional neural network for image classification, hosted on AWS EC2. Vue.js provides a frontend for uploading images and viewing results, with MongoDB storing metadata. | Users upload images to classify objects (e.g., animals, products). Supports quality control in manufacturing or content moderation. |
| **Medium Project 1:** Fraud Detection System | Python (XGBoost, Pandas), MySQL, Node.js, Express, pytest | XGBoost trains a model to detect fraudulent transactions, with Pandas for data preprocessing and MySQL for storage. Node.js with Express offers a dashboard for monitoring fraud alerts. | Identifies suspicious transactions in real time. Alerts financial institutions to prevent fraudulent activities. |
| **Medium Project 2:** Predictive Maintenance | Python (LightGBM, NumPy), Redis, Angular, pytest, Jenkins | LightGBM predicts equipment failures using sensor data, with Redis caching predictions. Angular frontend visualizes maintenance schedules. | Predicts when machines need maintenance to prevent downtime. Optimizes maintenance schedules for cost efficiency. |

## 5. AWS

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Serverless E-Learning Platform | AWS (Lambda, S3, DynamoDB, CloudFormation), Python (FastAPI), React, Tailwind CSS, Jenkins | AWS Lambda handles serverless backend logic, with S3 for course content storage and DynamoDB for user data. React with Tailwind CSS provides an interactive frontend for course access. | Students access video courses, take quizzes, and track progress. Instructors upload content and monitor student performance. |
| **Big Project 2:** Log Analytics Platform | AWS (EKS, CloudWatch, SQS), Java (Spring Boot), Elasticsearch, Vue.js, Docker | EKS orchestrates containerized Spring Boot microservices for log processing, with CloudWatch for monitoring and SQS for queuing. Vue.js frontend visualizes log analytics stored in Elasticsearch. | Processes and analyzes application logs to detect anomalies. Provides insights for system optimization and debugging. |
| **Medium Project 1:** File Sharing Service | AWS (S3, IAM, Lambda), Python (Flask), Angular, Bootstrap | S3 stores user-uploaded files, with Lambda handling access control via IAM policies. Flask backend and Angular frontend provide a simple file-sharing interface. | Users upload and share files securely with access controls. Tracks file access and download metrics. |
| **Medium Project 2:** Notification System | AWS (SNS, SQS, RDS), Node.js (Express), React, Tailwind CSS | SNS and SQS manage notification queues, with RDS storing user preferences. Node.js with Express and React frontend delivers a notification dashboard. | Sends real-time notifications (email/SMS) to users based on events. Allows users to customize notification preferences. |

## 6. Databases

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Inventory Management System | PostgreSQL, Java (Spring Boot, Hibernate), React, Tailwind CSS, Docker, JUnit | PostgreSQL stores inventory data, with Spring Boot and Hibernate managing CRUD operations. React with Tailwind CSS provides a responsive UI for inventory tracking. | Tracks product stock levels, updates inventory on sales, and generates restocking alerts. Supports multi-warehouse management. |
| **Big Project 2:** Search Engine for Documents | Elasticsearch, Python (FastAPI, Pandas), Vue.js, AWS EC2, pytest | Elasticsearch indexes and searches documents, with FastAPI handling API requests and Pandas for metadata processing. Vue.js frontend provides a search interface. | Users search for documents using keywords with relevance ranking. Supports filtering and categorization for efficient retrieval. |
| **Medium Project 1:** Social Network Graph | Neo4j, Java (Spring Boot), Angular, Bootstrap, JUnit | Neo4j stores user relationships in a graph database, with Spring Boot handling queries. Angular with Bootstrap displays a network visualization. | Users connect and follow others, with recommendations based on graph traversal. Analyzes network connections for influence metrics. |
| **Medium Project 2:** Vector Search for Recommendations | Pinecone, Python (Flask), React, Tailwind CSS, pytest | Pinecone handles vector-based similarity searches for recommendations, with Flask as the backend API. React with Tailwind CSS provides a recommendation UI. | Recommends products or content based on user preferences. Improves recommendation accuracy using vector embeddings. |

## 7. Algorithms

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Route Optimization System | Python (NumPy, Pandas), PostgreSQL, React, Tailwind CSS, pytest, Docker | Python implements graph algorithms (Dijkstra's, A*) for route optimization, with PostgreSQL storing location data. React with Tailwind CSS visualizes optimal routes. | Optimizes delivery routes for logistics companies to minimize time and cost. Supports real-time traffic updates and rerouting. |
| **Big Project 2:** Chess AI | Python (NumPy), Java (Spring Boot), MongoDB, Vue.js, pytest, AWS EC2 | Python uses minimax with alpha-beta pruning for chess AI, with Spring Boot handling game state APIs and MongoDB storing moves. Vue.js frontend renders the chessboard. | Players compete against an AI opponent with adjustable difficulty. Tracks game history and provides move suggestions. |
| **Medium Project 1:** String Search Engine | Java (Spring Boot), Redis, Angular, Bootstrap, JUnit | Java implements KMP string matching for fast text search, with Redis caching results. Angular with Bootstrap provides a search UI. | Users search for keywords in large text corpora with instant results. Supports autocomplete and search history. |
| **Medium Project 2:** Knapsack Problem Solver | Python (NumPy), SQLite, Node.js, Express, pytest | Python implements dynamic programming for the knapsack problem, with SQLite storing item data. Node.js with Express provides a frontend for input and results. | Optimizes item selection for limited capacity (e.g., cargo loading). Displays optimal item combinations and total value. |

## 8. Javascript

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Online Learning Management System | JavaScript (Node.js, Express, React, TypeScript), MongoDB, Jest, Tailwind CSS, AWS EC2 | Node.js with Express handles backend APIs, while React with TypeScript builds a dynamic frontend for course management. MongoDB stores course and user data, with Jest for testing. | Students enroll in courses, complete assignments, and track progress. Instructors create content and monitor student performance. |
| **Big Project 2:** Real-Time Collaborative Editor | JavaScript (Next.js, WebSocket, TypeScript), Redis, Vue.js, Jest, Docker | Next.js with WebSocket enables real-time collaborative editing, with Redis for session management. Vue.js provides a responsive editor interface. | Multiple users edit documents simultaneously with live updates. Supports version history and conflict resolution. |
| **Medium Project 1:** Task Tracker | JavaScript (Node.js, Express, Angular), SQLite, Jest, Bootstrap | Node.js with Express manages task APIs, with SQLite for data storage. Angular with Bootstrap creates a simple task management UI. | Users create and track tasks with priorities and deadlines. Provides task completion reports for productivity analysis. |
| **Medium Project 2:** Weather Dashboard | JavaScript (React, TypeScript), Node.js, Redis, Tailwind CSS, Jest | React with TypeScript builds a weather dashboard, with Node.js fetching data from external APIs and Redis caching results. Tailwind CSS ensures a modern UI. | Displays real-time weather updates for multiple cities. Allows users to save favorite locations for quick access. |

## 9. Web Design

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Portfolio Website Builder | HTML5, Tailwind CSS, JavaScript (React, TypeScript), Node.js, MongoDB, Figma | React with TypeScript and Tailwind CSS creates customizable portfolio templates, with Node.js and MongoDB handling user data. Figma is used for designing UI prototypes. | Users create and customize portfolio websites with drag-and-drop features. Supports publishing and sharing portfolios online. |
| **Big Project 2:** E-Commerce UI Kit | HTML5, Bootstrap, Sass, JavaScript (Vue.js), Figma, AWS S3 | Vue.js with Bootstrap and Sass builds a reusable UI kit for e-commerce, hosted on AWS S3. Figma designs ensure consistent styling across components. | Provides pre-built UI components for e-commerce websites (e.g., product cards, carts). Accelerates development for online stores. |
| **Medium Project 1:** Personal Blog Template | HTML5, Bulma, JavaScript (React), SQLite, Adobe XD | React with Bulma creates a lightweight blog template, with SQLite storing post data. Adobe XD is used for wireframing the UI. | Users publish blog posts with rich text formatting. Supports commenting and social sharing features. |
| **Medium Project 2:** Landing Page Generator | HTML5, Tailwind CSS, JavaScript (Svelte), Node.js, Webflow | Svelte with Tailwind CSS builds dynamic landing pages, with Node.js managing templates. Webflow is used for rapid prototyping and design. | Businesses create landing pages for campaigns with customizable layouts. Tracks visitor metrics for performance analysis. |

## 10. Security

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Web Vulnerability Scanner | Python (Flask), OWASP ZAP, Burp Suite, PostgreSQL, React, Tailwind CSS, pytest | Flask backend integrates OWASP ZAP and Burp Suite for automated vulnerability scanning, with PostgreSQL storing results. React with Tailwind CSS provides a dashboard for scan reports. | Scans websites for vulnerabilities like XSS and SQL injection. Generates reports for developers to fix security issues. |
| **Big Project 2:** Network Intrusion Detection System | Python (Scapy, Pandas), Wireshark, Elasticsearch, Vue.js, pytest, AWS EC2 | Scapy analyzes network packets for intrusion detection, with Wireshark for manual inspection and Elasticsearch for log storage. Vue.js frontend displays real-time alerts. | Monitors network traffic for suspicious activities. Alerts admins to potential threats like DDoS attacks. |
| **Medium Project 1:** Password Manager | Python (Flask), Cryptography, SQLite, Angular, Bootstrap, pytest | Flask backend with Cryptography secures password storage in SQLite. Angular with Bootstrap provides a secure UI for managing passwords. | Users store and retrieve encrypted passwords. Supports secure password generation and sharing. |
| **Medium Project 2:** Malware Analysis Tool | Python (Pandas), Metasploit, MongoDB, React, Tailwind CSS, pytest | Pandas processes malware data, with Metasploit for simulation and MongoDB for storage. React with Tailwind CSS visualizes analysis results. | Analyzes malware behavior to identify threats. Provides actionable insights for cybersecurity teams. |

## 11. DevOps

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** CI/CD Pipeline for Microservices | Jenkins, Docker, Kubernetes, Terraform, Prometheus, Grafana, Java (Spring Boot), React | Jenkins automates CI/CD for Spring Boot microservices, with Docker and Kubernetes for deployment and Terraform for infrastructure. Prometheus and Grafana monitor performance. | Automates deployment of microservices for a scalable application. Monitors system health and ensures zero-downtime updates. |
| **Big Project 2:** Log Aggregation Platform | Docker, Kubernetes, ELK Stack, Ansible, Python (Flask), Vue.js, AWS EKS | ELK Stack aggregates and visualizes logs, with Docker and Kubernetes on AWS EKS for deployment. Ansible automates configuration, and Vue.js provides a frontend dashboard. | Collects and analyzes logs from distributed systems. Helps teams identify and resolve issues quickly. |
| **Medium Project 1:** Infrastructure Monitoring Tool | Prometheus, Grafana, Docker, Python (FastAPI), React, Tailwind CSS | Prometheus collects system metrics, with Grafana for visualization and Docker for deployment. FastAPI and React provide a custom monitoring dashboard. | Monitors server performance metrics like CPU and memory usage. Alerts admins to potential system failures. |
| **Medium Project 2:** Automated Backup System | Ansible, Docker, AWS S3, Python (Flask), Angular, Bootstrap | Ansible automates backups to AWS S3, with Docker containerizing the backup service. Flask and Angular provide a UI for scheduling and monitoring backups. | Schedules and manages data backups for applications. Ensures data recovery in case of failures. |

## 12. C#

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Healthcare Management System | C# (.NET 8, ASP.NET Core, Entity Framework), SQL Server, Blazor, Tailwind CSS, NUnit, AWS RDS | ASP.NET Core with Entity Framework manages patient and appointment data, stored in SQL Server. Blazor with Tailwind CSS provides a responsive frontend for healthcare providers. | Manages patient records, appointments, and billing. Supports secure access for doctors and admins. |
| **Big Project 2:** Inventory Tracking App | C# (.NET 8, MAUI, LINQ), SQLite, NUnit, Docker, Azure | .NET MAUI builds a cross-platform app for inventory tracking, using LINQ for data queries and SQLite for storage. Docker deploys the backend on Azure. | Tracks inventory across multiple locations with real-time updates. Generates reports for stock levels and reorder needs. |
| **Medium Project 1:** Task Scheduler | C# (.NET 8, ASP.NET Core), PostgreSQL, Blazor, Bootstrap, NUnit | ASP.NET Core handles task scheduling APIs, with PostgreSQL storing tasks. Blazor with Bootstrap provides a simple task management UI. | Users schedule and track tasks with reminders. Supports recurring tasks and priority settings. |
| **Medium Project 2:** Expense Tracker | C# (.NET 8, MAUI), SQLite, NUnit, Azure | .NET MAUI builds a mobile app for expense tracking, with SQLite for local storage. Azure hosts the backend for syncing data. | Users log and categorize expenses with budget tracking. Generates spending reports for financial planning. |

## 13. Go

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Real-Time Monitoring System | Go (Gin, Goroutines, Channels), Prometheus, Grafana, PostgreSQL, React, Docker | Gin with Goroutines handles real-time data processing, with PostgreSQL storing metrics. Prometheus and Grafana visualize system performance, with React for the frontend. | Monitors server health and application metrics in real time. Alerts admins to performance issues. |
| **Big Project 2:** File Sync Service | Go (net/http, Go Modules), AWS S3, Vue.js, Testify, Kubernetes | Go with net/http builds a file sync API, with AWS S3 for storage. Vue.js frontend allows users to manage files, deployed via Kubernetes. | Syncs files across devices with version control. Ensures secure and reliable file access. |
| **Medium Project 1:** URL Shortener | Go (Gin), Redis, Angular, Testify, Docker | Gin creates a lightweight URL shortening service, with Redis for fast lookups. Angular provides a simple frontend for creating and tracking URLs. | Users create short URLs for long links. Tracks click analytics for each shortened URL. |
| **Medium Project 2:** Chatbot Backend | Go (gRPC), MongoDB, React, Testify, AWS EC2 | Go with gRPC handles chatbot API requests, with MongoDB storing conversation data. React frontend provides a chatbot interface. | Users interact with a chatbot for customer support. Logs conversations for analysis and improvement. |

## 14. Rust

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** High-Performance Web Server | Rust (Actix, Tokio), PostgreSQL, React, Tailwind CSS, Cargo, Docker | Actix with Tokio builds a high-performance web server, with PostgreSQL for data storage. React with Tailwind CSS provides a dynamic frontend. | Serves dynamic web content with low latency. Supports user authentication and content management. |
| **Big Project 2:** Blockchain Node | Rust (Tokio, Serde), MongoDB, Vue.js, Cargo, AWS EC2 | Rust with Tokio implements a blockchain node for transaction processing, with MongoDB for storage. Vue.js frontend monitors node status. | Processes and validates blockchain transactions. Provides real-time node metrics for network health. |
| **Medium Project 1:** File Compression Tool | Rust (Cargo), SQLite, Angular, Tailwind CSS | Rust implements a file compression algorithm, with SQLite storing metadata. Angular with Tailwind CSS provides a UI for compression tasks. | Compresses files to save storage space. Tracks compression history and performance. |
| **Medium Project 2:** Task Queue System | Rust (Rocket, Tokio), Redis, React, Cargo | Rocket with Tokio manages a task queue, with Redis for task storage. React frontend displays queue status and task progress. | Distributes tasks across workers for parallel processing. Monitors task completion and errors. |

## 15. PHP

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Online Marketplace | PHP (Laravel, Doctrine), MySQL, Vue.js, Tailwind CSS, PHPUnit, AWS EC2 | Laravel with Doctrine handles backend logic for product listings and transactions, with MySQL for data storage. Vue.js with Tailwind CSS creates a responsive marketplace UI. | Users buy and sell products with secure payments. Admins manage listings and resolve disputes. |
| **Big Project 2:** Content Management System | PHP (Symfony, Twig), PostgreSQL, React, Bootstrap, PHPUnit, Docker | Symfony with Twig manages content creation and publishing, with PostgreSQL for storage. React with Bootstrap provides a frontend for content editing. | Users create and manage website content with templates. Supports multi-user roles and content approval workflows. |
| **Medium Project 1:** Event Booking System | PHP (Laravel), SQLite, Angular, Bulma, PHPUnit | Laravel handles event booking APIs, with SQLite for lightweight storage. Angular with Bulma provides a booking interface. | Users book tickets for events with seat selection. Tracks bookings and sends confirmation emails. |
| **Medium Project 2:** Forum Platform | PHP (Symfony), MySQL, Vue.js, Tailwind CSS, PHPUnit | Symfony manages forum threads and user accounts, with MySQL for data storage. Vue.js with Tailwind CSS creates a modern forum UI. | Users post and comment on forum threads. Moderators manage content and user bans. |

## 16. Ruby

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** E-Learning Platform | Ruby (Rails, Sidekiq), PostgreSQL, React, Tailwind CSS, RSpec, Capistrano, AWS EC2 | Rails with Sidekiq handles course management and background jobs, with PostgreSQL for data storage. React with Tailwind CSS provides an interactive learning interface. | Students access courses and track progress. Instructors upload content and manage quizzes. |
| **Big Project 2:** Social Media Platform | Ruby (Sinatra, RuboCop), MongoDB, Vue.js, Bootstrap, RSpec, Docker | Sinatra manages lightweight APIs for social media features, with MongoDB for flexible storage. Vue.js with Bootstrap creates a responsive social feed UI. | Users post updates, follow others, and like content. Supports real-time notifications and analytics. |
| **Medium Project 1:** Task Management App | Ruby (Rails), SQLite, Angular, Bulma, RSpec | Rails handles task management APIs, with SQLite for storage. Angular with Bulma provides a simple task tracking UI. | Users create and assign tasks with deadlines. Tracks task completion and team productivity. |
| **Medium Project 2:** Blog Engine | Ruby (Hanami), PostgreSQL, React, Tailwind CSS, RSpec | Hanami provides a lightweight backend for blog management, with PostgreSQL for storage. React with Tailwind CSS creates a modern blog frontend. | Users publish and comment on blog posts. Supports tagging and search functionality. |

## 17. C++

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** 3D Graphics Engine | C++ (STL, OpenGL, Boost), SQLite, Qt Framework, CMake, AWS EC2 | C++ with OpenGL and Boost builds a 3D graphics engine, with SQLite for asset storage. Qt Framework provides a UI for scene editing, deployed on AWS EC2. | Developers create and render 3D scenes for games or simulations. Supports real-time rendering and asset management. |
| **Big Project 2:** Real-Time Trading System | C++ (STL, Concurrency), PostgreSQL, React, Tailwind CSS, CMake, Docker | C++ with concurrency primitives handles high-frequency trading, with PostgreSQL for transaction storage. React with Tailwind CSS visualizes trade data. | Processes stock trades with low latency. Provides real-time market analytics for traders. |
| **Medium Project 1:** File Encryption Tool | C++ (STL, Boost), SQLite, Angular, Bootstrap, CMake | C++ with Boost implements file encryption, with SQLite storing metadata. Angular with Bootstrap provides a UI for encryption tasks. | Users encrypt and decrypt files securely. Tracks encryption history and key management. |
| **Medium Project 2:** Network Packet Analyzer | C++ (STL), MongoDB, Vue.js, CMake, Docker | C++ processes network packets for analysis, with MongoDB storing results. Vue.js frontend displays packet statistics. | Analyzes network traffic for performance and security. Generates reports for network optimization. |

## 18. C

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Embedded IoT Device Controller | C (Embedded C, GCC), SQLite, React, Tailwind CSS, GDB, AWS IoT | Embedded C controls IoT devices, compiled with GCC and debugged with GDB, with SQLite for local storage. React with Tailwind CSS provides a remote control dashboard. | Manages IoT devices like smart lights or sensors. Provides real-time status and control for users. |
| **Big Project 2:** Real-Time Operating System | C (OpenMP, Make), PostgreSQL, Vue.js, Valgrind, Docker | C with OpenMP implements a lightweight RTOS, with PostgreSQL for task logging. Vue.js frontend monitors system performance, with Valgrind for memory debugging. | Schedules tasks for embedded systems with real-time constraints. Monitors task execution and resource usage. |
| **Medium Project 1:** Command-Line File Manager | C (GCC), SQLite, Angular, Bootstrap, GDB | C with GCC builds a command-line file manager, with SQLite for metadata. Angular with Bootstrap provides a web-based UI for file operations. | Users manage files and directories via CLI or web interface. Supports file search and organization. |
| **Medium Project 2:** System Monitoring Tool | C (Clang), Redis, React, Tailwind CSS, Make | C with Clang monitors system resources, with Redis for caching metrics. React with Tailwind CSS visualizes CPU and memory usage. | Tracks system performance in real time. Alerts admins to resource bottlenecks. |

## 19. Flutter

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Mobile Fitness Tracker | Flutter (Dart, BLoC, Firebase), PostgreSQL, Tailwind CSS, Docker | Flutter with BLoC manages fitness tracking logic, with Firebase for authentication and PostgreSQL for data storage. Tailwind CSS styles web-based dashboards. | Users track workouts, calories, and goals. Syncs data across devices and generates fitness reports. |
| **Big Project 2:** Food Delivery App | Flutter (Dart, Provider, Firebase), MongoDB, AWS EC2, Docker | Flutter with Provider builds a cross-platform food delivery app, with Firebase for real-time updates and MongoDB for order storage. AWS EC2 hosts the backend. | Users order food from restaurants with real-time tracking. Restaurants manage menus and orders. |
| **Medium Project 1:** Todo List App | Flutter (Dart, Riverpod), SQLite, Firebase | Flutter with Riverpod manages a todo list app, with SQLite for local storage and Firebase for cloud sync. | Users create and manage tasks with reminders. Syncs tasks across devices for accessibility. |
| **Medium Project 2:** Event Planner App | Flutter (Dart, BLoC), Firebase, PostgreSQL | Flutter with BLoC builds an event planner app, with Firebase for notifications and PostgreSQL for event data. | Users plan and share events with RSVPs. Tracks event details and attendee responses. |

## 20. Game Development

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** 3D Adventure Game | Unity, C#, Blender, Photon Networking, AWS EC2, PostgreSQL | Unity with C# builds a 3D adventure game, with Blender for asset creation and Photon Networking for multiplayer. PostgreSQL stores player progress on AWS EC2. | Players explore a 3D world, complete quests, and compete online. Tracks player progress and leaderboards. |
| **Big Project 2:** Multiplayer Card Game | Unreal Engine, C++, MongoDB, Substance Painter, AWS EC2 | Unreal Engine with C++ implements a multiplayer card game, with Substance Painter for card visuals. MongoDB stores game states, hosted on AWS EC2. | Players compete in real-time card matches. Supports deck customization and ranking systems. |
| **Medium Project 1:** 2D Platformer | Godot, GDScript, SQLite, Blender | Godot with GDScript builds a 2D platformer, with Blender for sprite creation. SQLite stores player progress locally. | Players navigate levels, collect items, and avoid obstacles. Tracks high scores and level completion. |
| **Medium Project 2:** Puzzle Game | GameMaker Studio, GML, MongoDB, AWS EC2 | GameMaker Studio with GML creates a puzzle game, with MongoDB for storing player data. AWS EC2 hosts leaderboards. | Players solve puzzles to progress through levels. Tracks scores and shares leaderboards online. |

## 21. Video Processing

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Video Streaming Platform | Python (Flask, FFmpeg), AWS S3, PostgreSQL, React, Tailwind CSS, Docker | Flask with FFmpeg processes and optimizes video streams, stored in AWS S3. React with Tailwind CSS provides a streaming frontend, with PostgreSQL for metadata. | Users stream videos with adaptive bitrate. Supports user playlists and watch history. |
| **Big Project 2:** Video Editing Suite | Python (OpenCV, NumPy), MongoDB, Vue.js, AWS EC2, pytest | OpenCV with NumPy handles video editing tasks like trimming and effects, with MongoDB for project storage. Vue.js frontend provides an editing interface. | Users edit videos with filters and transitions. Saves projects for later editing and sharing. |
| **Medium Project 1:** Video Compression Tool | Python (FFmpeg), SQLite, Angular, Bootstrap, pytest | FFmpeg compresses videos, with SQLite storing compression settings. Angular with Bootstrap provides a simple UI for compression tasks. | Users compress videos to reduce file size. Tracks compression history and quality metrics. |
| **Medium Project 2:** Motion Detection System | Python (OpenCV), Redis, React, Tailwind CSS, pytest | OpenCV detects motion in video feeds, with Redis caching alerts. React with Tailwind CSS displays motion events. | Monitors security cameras for motion detection. Sends real-time alerts to users. |

## 22. Compilers

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Custom Programming Language | C++ (LLVM, Parsing Theory), PostgreSQL, React, Tailwind CSS, CMake | C++ with LLVM implements a custom language compiler, with PostgreSQL storing ASTs. React with Tailwind CSS provides a code editor and debugger UI. | Developers write and compile code in a custom language. Supports debugging and error reporting. |
| **Big Project 2:** JIT Compiler for Scripting | C (Clang/LLVM), MongoDB, Vue.js, Make, AWS EC2 | C with LLVM builds a JIT compiler for a scripting language, with MongoDB for script storage. Vue.js frontend provides a scripting playground. | Executes scripts with dynamic compilation for performance. Supports real-time script testing and debugging. |
| **Medium Project 1:** Code Linter | C++ (Clang), SQLite, Angular, Bootstrap, CMake | C++ with Clang implements a static analysis linter, with SQLite for rule storage. Angular with Bootstrap provides a UI for linting results. | Analyzes code for style and error violations. Generates reports for code quality improvement. |
| **Medium Project 2:** Type Checker | C (LLVM), Redis, React, Tailwind CSS, Make | C with LLVM implements a type checker for a custom language, with Redis caching type data. React with Tailwind CSS displays type errors. | Ensures type safety in codebases. Provides detailed type mismatch reports. |

## 23. Big Data + ETL

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Real-Time Data Pipeline | Spark, Kafka, Airflow, Snowflake, Python (Pandas), React, AWS EC2 | Spark processes streaming data with Kafka for ingestion, orchestrated by Airflow. Snowflake stores processed data, with React for visualization. | Processes real-time data (e.g., IoT sensor data) for analytics. Generates dashboards for operational insights. |
| **Big Project 2:** Data Warehouse ETL | Python (dbt, Pandas), Hive, Databricks, PostgreSQL, Vue.js, AWS Glue | dbt with Pandas transforms data for Hive, managed by Databricks. Vue.js frontend visualizes ETL results, with AWS Glue for automation. | Extracts and transforms business data for reporting. Supports data warehousing for analytics teams. |
| **Medium Project 1:** Log Processing Pipeline | Python (NiFi, Pandas), Kafka, MongoDB, Angular, pytest | NiFi orchestrates log data ingestion, with Pandas for processing and Kafka for streaming. Angular frontend displays log analytics. | Processes application logs for error detection. Provides insights for system optimization. |
| **Medium Project 2:** Data Integration Tool | Python (Beam), BigQuery, React, Tailwind CSS, pytest | Beam handles data integration workflows, with BigQuery for storage. React with Tailwind CSS provides a UI for data mapping. | Integrates data from multiple sources for unified analytics. Supports custom data transformation rules. |

## 24. Blockchain

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Decentralized Voting System | Solidity, Ethereum, Web3.js, Truffle, Python (FastAPI), React, AWS EC2 | Solidity smart contracts on Ethereum manage voting logic, with Web3.js for interaction. FastAPI and React provide a voting frontend, deployed on AWS EC2. | Users cast secure, transparent votes on the blockchain. Ensures tamper-proof election results. |
| **Big Project 2:** NFT Marketplace | Solidity, IPFS, OpenZeppelin, Hardhat, Vue.js, MongoDB, AWS S3 | Solidity with OpenZeppelin builds NFT smart contracts, with IPFS for asset storage. Vue.js frontend manages NFT trading, with MongoDB for metadata. | Users buy, sell, and mint NFTs. Tracks ownership and transaction history on the blockchain. |
| **Medium Project 1:** Crypto Wallet | Solidity, Ethers.js, Python (Flask), SQLite, React, Tailwind CSS | Solidity manages wallet smart contracts, with Ethers.js for blockchain interaction. Flask and React provide a wallet management UI. | Users manage cryptocurrency balances and transactions. Supports secure key storage and transfers. |
| **Medium Project 2:** Supply Chain Tracker | Solidity, Hyperledger Fabric, Web3.js, PostgreSQL, Angular, Bootstrap | Hyperledger Fabric tracks supply chain data, with Solidity for public blockchain integration. Angular frontend displays product provenance. | Tracks goods from origin to delivery on the blockchain. Ensures transparency for consumers and suppliers. |

## 25. GIS

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Urban Planning Dashboard | Python (GeoPandas, PostGIS), QGIS, Leaflet, PostgreSQL, React, AWS EC2 | GeoPandas with PostGIS processes spatial data, with QGIS for map design. Leaflet and React visualize urban planning data, hosted on AWS EC2. | Planners analyze urban growth and infrastructure needs. Visualizes zoning and population density maps. |
| **Big Project 2:** Real-Time Traffic Mapping | Python (GDAL, GeoServer), Mapbox, MongoDB, Vue.js, Docker | GDAL with GeoServer processes real-time traffic data, with Mapbox for mapping. Vue.js frontend displays traffic conditions, with MongoDB for storage. | Displays live traffic updates for commuters. Supports route optimization and congestion alerts. |
| **Medium Project 1:** Location-Based Search | Python (GeoJSON), PostGIS, Angular, OpenLayers, AWS EC2 | PostGIS handles spatial queries for location search, with GeoJSON for data exchange. Angular with OpenLayers provides a search UI. | Users search for nearby points of interest (e.g., restaurants). Returns results based on proximity and filters. |
| **Medium Project 2:** Environmental Monitoring | Python (GeoPandas), QGIS, React, SQLite, Tailwind CSS | GeoPandas processes environmental data, with QGIS for map visualization. React with Tailwind CSS displays monitoring results. | Tracks environmental metrics like air quality. Generates reports for regulatory compliance. |

## 26. Finance

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Algorithmic Trading Platform | Python (Pandas, NumPy), PostgreSQL, React, Tailwind CSS, AWS EC2, pytest | Pandas and NumPy implement trading algorithms, with PostgreSQL for trade data. React with Tailwind CSS provides a trading dashboard. | Executes automated trades based on market signals. Tracks portfolio performance and risk metrics. |
| **Big Project 2:** Loan Origination System | Java (Spring Boot), MySQL, Vue.js, Tailwind CSS, JUnit, AWS RDS | Spring Boot handles loan application workflows, with MySQL for data storage. Vue.js with Tailwind CSS provides a loan application frontend. | Processes loan applications with credit scoring. Manages approval workflows and disbursements. |
| **Medium Project 1:** Budget Planner | Python (Flask), SQLite, Angular, Bootstrap, pytest | Flask manages budget planning APIs, with SQLite for user data. Angular with Bootstrap creates a budgeting UI. | Users track income and expenses with budget goals. Generates financial health reports. |
| **Medium Project 2:** Portfolio Optimizer | Python (Pandas, NumPy), MongoDB, React, Tailwind CSS, pytest | Pandas and NumPy optimize investment portfolios, with MongoDB for storage. React with Tailwind CSS visualizes portfolio performance. | Optimizes asset allocation for maximum returns. Provides risk analysis and rebalancing suggestions. |

## 27. Assembly

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Custom OS Kernel | Assembly (NASM), C, SQLite, React, Tailwind CSS, GDB | NASM with C builds a lightweight OS kernel, with SQLite for system logs. React with Tailwind CSS provides a system monitoring UI. | Manages low-level system resources like memory and interrupts. Provides a foundation for custom OS development. |
| **Big Project 2:** Embedded Device Firmware | Assembly (MASM), C, PostgreSQL, Vue.js, GDB, AWS EC2 | MASM with C implements firmware for embedded devices, with PostgreSQL for data logging. Vue.js frontend monitors device status. | Controls hardware peripherals in real time. Logs device activity for diagnostics. |
| **Medium Project 1:** CPU Emulator | Assembly (NASM), SQLite, Angular, Bootstrap, GDB | NASM builds a CPU emulator for x86 instructions, with SQLite for instruction logs. Angular with Bootstrap provides a simulation UI. | Simulates CPU instruction execution for educational purposes. Visualizes register and memory states. |
| **Medium Project 2:** System Call Wrapper | Assembly (MASM), Redis, React, Tailwind CSS, GDB | MASM implements system call wrappers, with Redis for caching. React with Tailwind CSS displays system call metrics. | Simplifies system call usage for developers. Tracks call performance and errors. |

## 28. Medicine

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Telemedicine Platform | Python (Django, FHIR), PostgreSQL, React, Tailwind CSS, AWS EC2, pytest | Django with FHIR handles patient data interoperability, with PostgreSQL for storage. React with Tailwind CSS provides a telemedicine UI. | Connects patients and doctors for virtual consultations. Manages medical records and prescriptions securely. |
| **Big Project 2:** Medical Imaging Analysis | Python (TensorFlow, DICOM), MongoDB, Vue.js, AWS Sagemaker, pytest | TensorFlow processes DICOM images for diagnostics, with MongoDB for storage. Vue.js frontend displays analysis results, deployed on AWS Sagemaker. | Analyzes medical images for disease detection (e.g., tumors). Provides diagnostic reports for doctors. |
| **Medium Project 1:** Health Monitoring App | Python (Flask), SQLite, Angular, Bootstrap, pytest | Flask manages health data APIs, with SQLite for local storage. Angular with Bootstrap provides a health tracking UI. | Users track vitals like heart rate and sleep. Generates health trend reports. |
| **Medium Project 2:** Drug Interaction Checker | Python (FastAPI), PostgreSQL, React, Tailwind CSS, pytest | FastAPI checks drug interactions, with PostgreSQL for drug data. React with Tailwind CSS provides an interaction checker UI. | Identifies potential drug interactions for prescriptions. Supports safe medication planning. |

## 29. Kotlin

| Project | Tech Stack | Tech Description | Business Logic |
|---------|------------|------------------|----------------|
| **Big Project 1:** Mobile Banking App | Kotlin (Android SDK, Jetpack Compose, Retrofit), Firebase, PostgreSQL, AWS EC2 | Kotlin with Jetpack Compose builds a modern banking app, with Retrofit for API calls and Firebase for authentication. PostgreSQL stores transaction data. | Users manage accounts, transfer funds, and pay bills. Provides transaction history and budgeting tools. |
| **Big Project 2:** E-Commerce Mobile App | Kotlin (Android SDK, Coroutines, Room), MongoDB, Firebase, AWS EC2 | Kotlin with Coroutines and Room builds a cross-platform e-commerce app, with MongoDB for product data. Firebase handles push notifications. | Users browse and purchase products with secure checkout. Tracks orders and delivery status. |
| **Medium Project 1:** Note-Taking App | Kotlin (Android SDK, Jetpack Compose), SQLite, Firebase | Kotlin with Jetpack Compose creates a note-taking app, with SQLite for local storage. Firebase syncs notes across devices. | Users create and organize notes with tags. Supports cloud backup and sharing. |
| **Medium Project 2:** Fitness Tracker | Kotlin (Android SDK, Room), Firebase, PostgreSQL | Kotlin with Room manages fitness data, with Firebase for real-time updates. PostgreSQL stores user profiles. | Tracks workouts and calories with goal setting. Syncs data for cross-device access. |