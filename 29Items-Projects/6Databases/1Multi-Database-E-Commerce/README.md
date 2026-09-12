# ShopFlow — Multi-Database E-Commerce Platform

A production-grade, **polyglot-persistence** e-commerce platform built on an
**event-driven microservices** architecture. Each bounded context is backed by
the datastore best suited to its access pattern.

| Concern                | Datastore        | Service |
|------------------------|------------------|---------|
| Orders / payments      | **Oracle**       | order-service |
| Product catalog + reviews | **MongoDB**   | catalog-service |
| Sessions / JWT / rate-limit | **Redis**   | auth-service, api-gateway |
| Full-text search       | **Elasticsearch** | search-service |
| Recommendations (graph)| **Neo4j**        | recommendation-service |
| Event backbone / realtime | **Kafka** + WebSocket | realtime-service |
| Review sentiment       | **Python ML**    | ml-service |

Frontend is **Next.js 14 / React / TypeScript**. Deployment targets **AWS EKS**
(Terraform + Jenkins). See [`docs/`](docs) for full architecture.

---

## Features — what the app can do

### Storefront (customer-facing)
- Browse the product catalog with pagination and category filtering
- View product detail: description, price, live stock, star rating, and review count
- Full-text product search with category and price-range filters
- Read and post product reviews (1–5 stars + text); the product's average rating updates automatically
- See "customers also bought…", personalized recommendations, and trending products
- Add items to a shopping cart (persisted in the browser), change quantities, and remove items
- Register an account, log in / log out, and stay signed in across sessions (JWT)
- Check out the cart to place an order — safe against double-submit (idempotency key)
- View order history and per-order detail; pay a pending order
- Loading / error / empty states and client-side form validation throughout

### Catalog & reviews (MongoDB)
- Create, update, and soft-delete products (admin) — flexible schema with variants & attributes
- List/browse active products by category (paginated); name search; distinct-category listing
- Product reviews with a rolling average-rating aggregate
- Publishes `product.events`; consumes ML sentiment results to annotate reviews

### Search & discovery (Elasticsearch)
- Full-text relevance search over product names
- Faceted filtering by category and price range, paginated
- Read-model kept in sync asynchronously from catalog events (CQRS projection)

### Recommendations (Neo4j)
- Graph-based collaborative filtering: "customers who bought X also bought Y"
- Personalized recommendations derived from a customer's purchase history
- Trending / most-purchased fallback for cold-start (new customers)
- Purchase graph built automatically from order events

### Orders & checkout (Oracle)
- Place orders with full ACID guarantees and price snapshotting at order time
- `Idempotency-Key` support — client retries never create duplicate orders
- Guarded lifecycle: `PENDING → PAID → FULFILLED / CANCELLED`
- Fetch a single order; list a customer's orders (paginated, newest first)
- Reliable event publishing via a transactional outbox (emitted only after commit)

### Accounts & security (Redis + gateway)
- Register, login, refresh, current-user (`/me`), and logout
- Short-lived JWT access tokens + rotating, Redis-backed refresh tokens
- BCrypt password hashing; seeded demo & admin accounts
- Single API gateway: edge JWT verification, CORS, and per-user / per-IP rate limiting
- Public browse endpoints vs. authenticated order endpoints

### Real-time updates (Kafka + WebSocket)
- Live order-status updates pushed to the customer over WebSocket (STOMP)
- Live product updates (price/stock) pushed to product-page subscribers

### Review sentiment ML (Python)
- Scores review sentiment (POSITIVE / NEGATIVE / NEUTRAL) via a transformer model (with heuristic fallback)
- Runs as a Kafka pipeline: `review.events` → score → `review.scored` (feeds the catalog)
- Also exposed as a synchronous REST scoring endpoint

### Platform capabilities (cross-cutting)
- Polyglot persistence — the right database for each bounded context
- Event-driven integration over Kafka with idempotent consumers
- Uniform API response envelope and centralized error handling (400/401/404/409/422/500)
- OpenAPI/Swagger UI per service, `/actuator/health` probes, and gzip response compression
- Correlation-id (`traceId`) propagation in logs across services

---

## Prerequisites

- **JDK 21** and Maven (or use the bundled `backend/mvnw` wrapper)
- **Node 20+**
- **Docker** + Docker Compose (for datastores and the containerized stack)

## Quickstart

### 1. Start the datastores

```bash
cp .env.example .env          # optional; compose has sane defaults
docker compose up -d          # Oracle, MongoDB, Redis, Elasticsearch, Neo4j, Kafka
```

### 2. Build & test the backend

```bash
cd backend
./mvnw verify                 # compiles all 8 modules + runs unit & Testcontainers tests
```

### 3. Run the platform

**Option A — everything in Docker** (builds all service images):

```bash
docker compose --profile apps up -d --build
```

**Option B — services on the host** (faster inner loop). Each Spring Boot
service reads its config from env with `localhost` defaults that match the
compose datastores, so:

```bash
cd backend
./mvnw -pl api-gateway spring-boot:run          # :8080  (repeat per service)
./mvnw -pl auth-service spring-boot:run         # :8081
./mvnw -pl catalog-service spring-boot:run      # :8082
./mvnw -pl order-service spring-boot:run        # :8083
./mvnw -pl search-service spring-boot:run       # :8084
./mvnw -pl recommendation-service spring-boot:run  # :8085
./mvnw -pl realtime-service spring-boot:run     # :8086
# ml-service:
cd ml-service && pip install -r requirements.txt && uvicorn app.main:app --port 8000
```

### 4. Start the storefront

```bash
cd frontend
npm install
npm run dev                   # http://localhost:3000
```

Storefront → http://localhost:3000 · API gateway → http://localhost:8080

**Demo account:** `demo` / `password123` (seeded by auth-service on startup).

---

## Running the tests

```bash
cd backend && ./mvnw test        # 28 tests: unit + Oracle/Mongo/Neo4j Testcontainers
cd ml-service && pytest -q       # FastAPI + sentiment tests
cd frontend && npm run build     # type-check + lint + production build
```

Integration tests spin real datastores via **Testcontainers**, so Docker must be
running. Coverage reports (JaCoCo) are produced under each module's `target/`.

## Key API endpoints (via the gateway, `/api/v1`)

| Method | Path | Service |
|--------|------|---------|
| POST | `/auth/register`, `/auth/login`, `/auth/refresh` | auth |
| GET  | `/products`, `/products/{id}`, `/products/categories` | catalog |
| GET/POST | `/products/{id}/reviews` | catalog → ml sentiment |
| GET  | `/search?q=&category=&minPrice=&maxPrice=` | search (Elasticsearch) |
| GET  | `/recommendations/products/{id}/also-bought`, `/recommendations/trending` | reco (Neo4j) |
| POST | `/orders` (+ `Idempotency-Key`), `/orders/{id}/payment` | order (Oracle) |
| WS   | `/ws` → `/topic/orders/{customerId}` | realtime |

Each REST service serves interactive **OpenAPI docs** (springdoc) at
`/swagger-ui.html` — e.g. catalog http://localhost:8082/swagger-ui.html,
order http://localhost:8083/swagger-ui.html. Example request:

```bash
# login, then place an order with the returned token
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"password123"}' | jq -r .data.accessToken)
curl -s localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"demo","currency":"EUR","items":[{"productId":"p-1001","quantity":1,"unitPrice":1299.00}]}'
```

## Event flow (Kafka)

```
catalog ──product.events──▶ search (index) + recommendation (product nodes) + realtime
order   ──order.events────▶ recommendation (BOUGHT graph) + realtime
catalog ──review.events───▶ ml-service ──review.scored──▶ catalog (sentiment)
```

## Troubleshooting

| Symptom | Cause / Fix |
|---|---|
| `mvnw`/tests fail with "Could not find a valid Docker environment" | Start Docker Desktop — Testcontainers needs a running Docker daemon. |
| Integration tests slow / Oracle "starting" | Oracle initializes its DB on first boot (~1 min). Check `docker compose ps` for `healthy`. |
| Maven builds fail on Java version | Spring Boot 3 needs JDK 17+. `export JAVA_HOME=/path/to/jdk-21` (Windows: set it to `C:\...\jdk-21`). |
| `Port already in use` | Another process holds the port. Stop it or change the service `server.port`. |
| Service can't reach Kafka | Host processes use `localhost:9092`; **containers** use `kafka:29092` (INTERNAL listener). |
| `401 Unauthorized` calling `/api/v1/orders` | Log in first — order endpoints require a Bearer token (gateway-enforced). |
| Frontend shows "Unable to reach the server" | Ensure the gateway (`:8080`) is up and `NEXT_PUBLIC_API_BASE_URL` points to it; CORS allows `http://localhost:3000`. |
| Search returns nothing right after creating a product | Indexing is asynchronous (Kafka → Elasticsearch, ~1–2 s). Retry the query. |
| `npm audit` still lists highs | Pinned to the latest **14.2.x** (all criticals fixed); remaining advisories require the breaking Next 15 upgrade. |

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — structure & roadmap
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — patterns, data flow, scaling, security
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls

## License

Proprietary — internal reference scaffold.
