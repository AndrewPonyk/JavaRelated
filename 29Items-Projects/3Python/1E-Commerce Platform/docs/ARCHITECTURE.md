# E-Commerce Platform - Architecture Documentation

## 1. Architectural Pattern

### Chosen Pattern: Modular Monolith with Event-Driven Components

This architecture combines the simplicity of a monolith with clear module boundaries and asynchronous processing capabilities.

```mermaid
graph TB
    subgraph "Client Layer"
        WEB[React SPA]
        MOBILE[Mobile Apps]
    end

    subgraph "API Gateway"
        NGINX[Nginx / ALB]
    end

    subgraph "Application Layer"
        DJANGO[Django Application]
        subgraph "Domain Modules"
            USERS[Users]
            PRODUCTS[Products]
            CART[Cart]
            CHECKOUT[Checkout]
            INVENTORY[Inventory]
            VENDORS[Vendors]
            SEARCH[Search]
            RECO[Recommendations]
        end
    end

    subgraph "Async Processing"
        CELERY[Celery Workers]
        BEAT[Celery Beat]
    end

    subgraph "Data Layer"
        PG[(PostgreSQL)]
        REDIS[(Redis)]
        ES[(Elasticsearch)]
        S3[(S3 Storage)]
    end

    WEB --> NGINX
    MOBILE --> NGINX
    NGINX --> DJANGO

    DJANGO --> USERS
    DJANGO --> PRODUCTS
    DJANGO --> CART
    DJANGO --> CHECKOUT
    DJANGO --> INVENTORY
    DJANGO --> VENDORS
    DJANGO --> SEARCH
    DJANGO --> RECO

    DJANGO --> PG
    DJANGO --> REDIS
    DJANGO --> ES
    DJANGO --> S3

    DJANGO --> CELERY
    BEAT --> CELERY
    CELERY --> PG
    CELERY --> REDIS
    CELERY --> ES
```

### Justification

1. **Simplicity**: Single deployable unit reduces operational complexity
2. **Clear Boundaries**: Django apps provide logical separation while sharing infrastructure
3. **Async Capability**: Celery enables event-driven patterns for long-running tasks
4. **Scalability Path**: Can evolve to microservices if needed (each Django app can become a service)
5. **Team Size**: Suitable for small-to-medium teams (3-8 developers)

---

## 2. Key Component Interactions

### 2.1 API Communication

```mermaid
graph LR
    subgraph "Frontend"
        REACT[React App]
    end

    subgraph "Backend"
        DRF[Django REST Framework]
        AUTH[JWT Auth Middleware]
        VIEWS[ViewSets]
        SERVICES[Service Layer]
        MODELS[Models / ORM]
    end

    REACT -->|REST API| DRF
    DRF --> AUTH
    AUTH --> VIEWS
    VIEWS --> SERVICES
    SERVICES --> MODELS
```

### 2.2 Message Queue Communication

```mermaid
graph TB
    subgraph "Django Application"
        VIEW[API View]
        TASK_DISPATCH[Task Dispatcher]
    end

    subgraph "Message Broker"
        REDIS_QUEUE[(Redis Queue)]
    end

    subgraph "Workers"
        WORKER1[Celery Worker 1]
        WORKER2[Celery Worker 2]
        WORKER3[Celery Worker 3]
    end

    subgraph "Task Types"
        EMAIL[Email Tasks]
        INDEX[Search Indexing]
        RECO[ML Training]
        INVENTORY[Stock Updates]
    end

    VIEW --> TASK_DISPATCH
    TASK_DISPATCH --> REDIS_QUEUE
    REDIS_QUEUE --> WORKER1
    REDIS_QUEUE --> WORKER2
    REDIS_QUEUE --> WORKER3

    WORKER1 --> EMAIL
    WORKER2 --> INDEX
    WORKER3 --> RECO
    WORKER3 --> INVENTORY
```

### 2.3 Component Communication Matrix

| Source | Target | Method | Use Case |
|--------|--------|--------|----------|
| Frontend | Backend | REST API | All user interactions |
| Backend | Database | SQLAlchemy/Django ORM | Data persistence |
| Backend | Redis | Direct connection | Caching, session storage |
| Backend | Elasticsearch | elasticsearch-dsl | Search queries |
| Backend | Celery | Task queue | Async operations |
| Celery | Backend DB | Django ORM | Background updates |
| Celery | External APIs | HTTP | Email, payments |

---

## 3. Data Flow

### 3.1 Product Search Flow

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as Django API
    participant R as Redis Cache
    participant E as Elasticsearch
    participant M as ML Service

    U->>F: Enter search query
    F->>A: GET /api/search?q=...
    A->>R: Check cache

    alt Cache Hit
        R-->>A: Return cached results
    else Cache Miss
        A->>E: Search products
        E-->>A: Return matches
        A->>M: Get recommendations
        M-->>A: Return similar products
        A->>R: Cache results (5 min TTL)
    end

    A-->>F: JSON response
    F-->>U: Display results
```

### 3.2 Checkout Flow

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as Django API
    participant DB as PostgreSQL
    participant C as Celery
    participant P as Stripe
    participant E as Email Service

    U->>F: Click Checkout
    F->>A: POST /api/checkout
    A->>DB: Validate cart & stock

    alt Stock Available
        A->>DB: Reserve inventory
        A->>P: Create payment intent
        P-->>A: Return client secret
        A-->>F: Return payment form

        U->>F: Submit payment
        F->>P: Confirm payment
        P-->>F: Payment success
        F->>A: POST /api/orders/confirm

        A->>DB: Create order
        A->>DB: Deduct inventory
        A->>C: Queue email task
        C->>E: Send confirmation

        A-->>F: Order confirmed
    else Stock Unavailable
        A-->>F: Error: Out of stock
    end
```

### 3.3 Recommendation Engine Flow

```mermaid
flowchart TB
    subgraph "Data Collection"
        E1[View Events]
        E2[Purchase Events]
        E3[Cart Events]
        E4[Rating Events]
    end

    subgraph "ETL Pipeline"
        COLLECT[Event Collector]
        TRANSFORM[Data Transformer]
        LOAD[Feature Store]
    end

    subgraph "ML Pipeline"
        TRAIN[Model Training]
        EVAL[Model Evaluation]
        DEPLOY[Model Deployment]
    end

    subgraph "Serving"
        API[Recommendation API]
        CACHE[Redis Cache]
    end

    E1 --> COLLECT
    E2 --> COLLECT
    E3 --> COLLECT
    E4 --> COLLECT

    COLLECT --> TRANSFORM
    TRANSFORM --> LOAD
    LOAD --> TRAIN
    TRAIN --> EVAL
    EVAL -->|Approved| DEPLOY
    DEPLOY --> API
    API --> CACHE
```

---

## 4. Scalability & Performance Strategy

### 4.1 Horizontal Scaling Architecture

```mermaid
graph TB
    subgraph "Load Balancing"
        ALB[AWS ALB]
    end

    subgraph "Application Tier"
        ECS1[ECS Task 1]
        ECS2[ECS Task 2]
        ECS3[ECS Task N]
    end

    subgraph "Worker Tier"
        W1[Worker 1]
        W2[Worker 2]
        W3[Worker N]
    end

    subgraph "Data Tier"
        RDS[(RDS Primary)]
        RDS_R[(RDS Replica)]
        REDIS_C[(Redis Cluster)]
        ES_C[(ES Cluster)]
    end

    ALB --> ECS1
    ALB --> ECS2
    ALB --> ECS3

    ECS1 --> RDS
    ECS2 --> RDS
    ECS3 --> RDS

    ECS1 -.->|Reads| RDS_R
    ECS2 -.->|Reads| RDS_R

    ECS1 --> REDIS_C
    ECS1 --> ES_C
```

### 4.2 Performance Strategies

| Layer | Strategy | Implementation |
|-------|----------|----------------|
| **API** | Response caching | Redis with 5-min TTL for read-heavy endpoints |
| **Database** | Read replicas | Route read queries to replica |
| **Database** | Connection pooling | PgBouncer with 100 connections |
| **Search** | Result caching | Cache popular searches |
| **Assets** | CDN | CloudFront for static files |
| **Images** | Lazy loading | Progressive image loading |
| **API** | Pagination | Cursor-based pagination |
| **Async** | Task queues | Offload heavy operations to Celery |

### 4.3 Caching Strategy

```mermaid
graph LR
    subgraph "Cache Layers"
        L1[Browser Cache]
        L2[CDN Cache]
        L3[Application Cache]
        L4[Database Cache]
    end

    L1 -->|Miss| L2
    L2 -->|Miss| L3
    L3 -->|Miss| L4
    L4 -->|Miss| DB[(Database)]
```

| Cache Type | TTL | Invalidation |
|------------|-----|--------------|
| Product listings | 5 min | On product update |
| Product details | 15 min | On product update |
| User sessions | 24 hours | On logout |
| Search results | 5 min | Time-based |
| Cart data | 7 days | On checkout |
| Recommendations | 1 hour | On model update |

---

## 5. Security Considerations

### 5.1 Security Architecture

```mermaid
graph TB
    subgraph "Edge Security"
        WAF[AWS WAF]
        DDOS[DDoS Protection]
    end

    subgraph "Application Security"
        JWT[JWT Authentication]
        RBAC[Role-Based Access]
        RATE[Rate Limiting]
        CORS[CORS Policy]
    end

    subgraph "Data Security"
        ENC_T[Encryption in Transit]
        ENC_R[Encryption at Rest]
        MASK[Data Masking]
    end

    subgraph "Secret Management"
        SM[AWS Secrets Manager]
        ENV[Environment Variables]
    end

    WAF --> JWT
    DDOS --> WAF
    JWT --> RBAC
    RBAC --> RATE

    ENC_T --> ENC_R
    SM --> ENV
```

### 5.2 Authentication & Authorization

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as Auth Service
    participant DB as Database
    participant R as Redis

    U->>F: Login (email, password)
    F->>A: POST /api/auth/login
    A->>DB: Validate credentials
    DB-->>A: User record
    A->>A: Generate JWT (access + refresh)
    A->>R: Store refresh token
    A-->>F: Return tokens
    F->>F: Store access token (memory)
    F->>F: Store refresh token (httpOnly cookie)

    Note over F,A: Subsequent requests

    F->>A: Request + Bearer token
    A->>A: Validate JWT signature
    A->>A: Check permissions
    A-->>F: Response
```

### 5.3 Security Checklist

| Category | Measure | Implementation |
|----------|---------|----------------|
| **Auth** | Password hashing | Argon2id |
| **Auth** | Token expiry | Access: 15min, Refresh: 7 days |
| **Auth** | MFA support | TOTP (optional) |
| **API** | Rate limiting | 100 req/min per user |
| **API** | Input validation | Pydantic/DRF serializers |
| **API** | SQL injection | Parameterized queries |
| **API** | XSS prevention | Content-Security-Policy |
| **Data** | PII encryption | AES-256 for sensitive fields |
| **Data** | Audit logging | All data modifications |
| **Infra** | Secrets | AWS Secrets Manager |
| **Infra** | Network | VPC with private subnets |

---

## 6. Error Handling & Logging Philosophy

### 6.1 Error Handling Strategy

```mermaid
flowchart TB
    subgraph "Error Types"
        VAL[Validation Errors]
        AUTH[Authentication Errors]
        BIZ[Business Logic Errors]
        SYS[System Errors]
    end

    subgraph "Handling"
        H400[400 Bad Request]
        H401[401 Unauthorized]
        H422[422 Unprocessable]
        H500[500 Internal Error]
    end

    subgraph "Response"
        CLIENT[Client-Friendly Message]
        LOG[Detailed Log Entry]
        ALERT[Alert if Critical]
    end

    VAL --> H400
    AUTH --> H401
    BIZ --> H422
    SYS --> H500

    H400 --> CLIENT
    H401 --> CLIENT
    H422 --> CLIENT
    H500 --> CLIENT

    H400 --> LOG
    H500 --> LOG
    H500 --> ALERT
```

### 6.2 Standard Error Response Format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input provided",
    "details": [
      {
        "field": "email",
        "message": "Must be a valid email address"
      }
    ],
    "request_id": "req_abc123",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### 6.3 Logging Architecture

```mermaid
graph LR
    subgraph "Log Sources"
        APP[Application Logs]
        ACCESS[Access Logs]
        ERROR[Error Logs]
        AUDIT[Audit Logs]
    end

    subgraph "Collection"
        FLUENT[Fluentd]
    end

    subgraph "Storage"
        CW[CloudWatch Logs]
        S3[S3 Archive]
    end

    subgraph "Analysis"
        DASH[CloudWatch Dashboards]
        ALARM[CloudWatch Alarms]
        SENTRY[Sentry]
    end

    APP --> FLUENT
    ACCESS --> FLUENT
    ERROR --> FLUENT
    AUDIT --> FLUENT

    FLUENT --> CW
    FLUENT --> S3

    CW --> DASH
    CW --> ALARM
    ERROR --> SENTRY
```

### 6.4 Logging Standards

| Log Level | Use Case | Example |
|-----------|----------|---------|
| **DEBUG** | Development only | Variable values, query details |
| **INFO** | Normal operations | Request received, task completed |
| **WARNING** | Recoverable issues | Deprecated API usage, retry attempt |
| **ERROR** | Failures | Payment failed, external API error |
| **CRITICAL** | System failures | Database connection lost |

### 6.5 Structured Log Format

```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "ERROR",
  "service": "checkout",
  "request_id": "req_abc123",
  "user_id": "user_456",
  "message": "Payment processing failed",
  "context": {
    "order_id": "order_789",
    "amount": 99.99,
    "error_code": "card_declined"
  },
  "stack_trace": "..."
}
```

---

## 7. Infrastructure Overview

```mermaid
graph TB
    subgraph "AWS Region"
        subgraph "VPC"
            subgraph "Public Subnet"
                ALB[Application Load Balancer]
                NAT[NAT Gateway]
            end

            subgraph "Private Subnet - App"
                ECS[ECS Cluster]
                WORKER[Celery Workers]
            end

            subgraph "Private Subnet - Data"
                RDS[(RDS PostgreSQL)]
                REDIS[(ElastiCache Redis)]
                ES[(OpenSearch)]
            end
        end

        S3[(S3 Buckets)]
        CF[CloudFront]
        SM[Secrets Manager]
        ECR[ECR Registry]
    end

    INTERNET((Internet)) --> CF
    CF --> ALB
    ALB --> ECS
    ECS --> RDS
    ECS --> REDIS
    ECS --> ES
    ECS --> S3
    WORKER --> RDS
    WORKER --> REDIS

    ECS --> SM
    ECR --> ECS
```

---

## 8. Deployment Architecture

```mermaid
graph LR
    subgraph "CI/CD Pipeline"
        GH[GitHub]
        GA[GitHub Actions]
        ECR[ECR]
    end

    subgraph "Environments"
        DEV[Dev]
        STG[Staging]
        PROD[Production]
    end

    GH -->|Push| GA
    GA -->|Build| ECR
    ECR -->|Deploy| DEV
    DEV -->|Promote| STG
    STG -->|Approve| PROD
```

| Environment | Purpose | Data |
|-------------|---------|------|
| **Development** | Feature development | Synthetic data |
| **Staging** | Pre-production testing | Anonymized production clone |
| **Production** | Live system | Real customer data |
