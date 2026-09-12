# High-Performance Web Server (CDN Edge)

## Features & Functionality

### 🌐 Core Edge Router (Data Plane)
1. **Dynamic Upstream Routing:** Intercepts incoming HTTP requests on specific paths (e.g., `/api/users`) and acts as a reverse proxy, fetching data from a configured external origin URL.
2. **High-Speed Redis Caching:** Saves the origin's response in a Redis in-memory cache on the first request. Subsequent requests to that exact path are served blazingly fast directly from memory, skipping external network calls.
3. **Configurable Time-To-Live (TTL):** Respects custom expiration timers for cached assets, automatically evicting stale data from memory once the TTL expires.
4. **Response Compression:** Automatically compresses outgoing HTTP payloads (using gzip/brotli) to reduce network bandwidth and speed up load times for the end user.
5. **Metrics Telemetry:** Actively tracks every single request, tallying up "Cache Hits" and "Cache Misses" in Redis to monitor CDN performance.

### 🎛️ Control Plane Dashboard (Frontend)
6. **Rule Management UI:** Provides a React-based graphical interface where administrators can view all active routing rules in a clean table format.
7. **Dynamic Rule Creation:** Add new routing rules on the fly without restarting the server. It validates inputs (ensuring paths start with `/` and URLs use `http(s)://`) and saves the rule to PostgreSQL.
8. **Rule Deletion:** Instantly delete existing routing rules from the database with a single click.
9. **Performance Monitoring:** Features an "Edge Node Status" dashboard tab that pulls telemetry data from the backend, displaying total requests, latency, and the calculated **Cache Hit Rate %** of the node. 

### ⚙️ Infrastructure & Reliability
10. **Containerized Portability:** The entire app (Database, Cache, Backend, Frontend) spins up using a single `docker-compose` command, meaning it can be deployed anywhere in seconds.
11. **Self-Healing Dependencies:** Automatically monitors its own PostgreSQL and Redis containers, ensuring the backend server waits for them to be 100% healthy before booting to prevent crashes. 
12. **Health Endpoints:** Exposes a dedicated `/health` API endpoint that can be used by load balancers (like AWS ALB or Nginx) to verify the edge node is alive and ready to receive traffic.

## Setup Instructions

### Prerequisites
- Docker & Docker Compose

### Running the Stack locally (Production Mode)

1. **Start the Entire Infrastructure**
   ```bash
   docker-compose up --build -d
   ```
   *This single command builds and starts the Postgres DB, Redis Cache, Rust Backend (port 8080), and React Frontend (port 80).*

2. **Run Database Migrations**
   (If running locally without Docker, use `diesel_cli`. When using Docker, the database schema can be mounted or migrations run manually within the container for now.)
   ```bash
   docker-compose exec backend cargo install diesel_cli --no-default-features --features postgres
   docker-compose exec backend diesel setup
   docker-compose exec backend diesel migration run
   ```

3. **Verify Health**
   ```bash
   curl http://localhost:8080/health
   ```
   *Expected response: `{"status":"ok"}`*

### Usage
1. Open the dashboard `http://localhost`.
2. Go to "Routing Rules" and add a new rule.
   - Intercept Path: `/test-json`
   - Origin URL: `https://jsonplaceholder.typicode.com/todos/1`
3. Hit `http://localhost:8080/test-json` in your browser.
4. Check the "Metrics Dashboard" to see the cache hit rate increase on subsequent requests.
