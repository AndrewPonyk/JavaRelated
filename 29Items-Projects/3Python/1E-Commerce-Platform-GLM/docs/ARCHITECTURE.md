# E-Commerce Platform - Architecture Documentation

## 1. Chosen Architectural Pattern

### **Layered Monolith with Service-Oriented Components**

**Justification:**

For this e-commerce platform, we've chosen a **Layered Monolith** architecture with service-oriented internal components. This decision is based on:

1. **Team Size & Complexity**: A monolith is simpler to develop, test, and deploy for small-to-medium teams
2. **Cost Efficiency**: Single deployment reduces infrastructure complexity and operational overhead
3. **Performance**: In-process calls avoid network latency of microservices
4. **Transaction Management**: Easier to maintain ACID properties across related operations
5. **Future Evolution**: Clean service boundaries allow extraction to microservices if needed

The architecture uses clear separation of concerns through layered design, making it maintainable and testable while keeping deployment simple.

## 2. System Architecture Overview

```mermaid
graph TB
    subgraph "Client Layer"
        WEB[Web Browser]
        MOBILE[Mobile App]
    end

    subgraph "CDN & Edge"
        CDN[Amazon CloudFront]
        WAF[WAF / Rate Limiting]
    end

    subgraph "Load Balancer"
        ALB[Application Load Balancer]
    end

    subgraph "Web Tier - ECS"
        FE1[React Frontend Container 1]
        FE2[React Frontend Container 2]
        BE1[Django API Container 1]
        BE2[Django API Container 2]
    end

    subgraph "Application Layer"
        API[REST API Layer]
        AUTH[Authentication Service]
        CART[Cart Service]
        PRODUCT[Product Service]
        ORDER[Order Service]
        VENDOR[Vendor Service]
        SEARCH[Search Service]
    end

    subgraph "Async Processing"
        CELERY[Celery Workers]
        BEAT[Celery Beat Scheduler]
        QUEUE[Redis Queue]
    end

    subgraph "Data Layer"
        PG[(PostgreSQL)]
        REDIS[(Redis Cache)]
        ES[(Elasticsearch)]
        S3[S3 Object Storage]
    end

    subgraph "External Services"
        STRIPE[Stripe API]
        SENDGRID[SendGrid Email]
        ANALYTICS[Analytics Service]
    end

    WEB --> CDN
    MOBILE --> CDN
    CDN --> WAF
    WAF --> ALB
    ALB --> FE1 & FE2
    ALB --> BE1 & BE2

    BE1 & BE2 --> API
    API --> AUTH
    API --> CART
    API --> PRODUCT
    API --> ORDER
    API --> VENDOR
    API --> SEARCH

    AUTH --> PG
    CART --> PG
    CART --> REDIS
    PRODUCT --> PG
    PRODUCT --> REDIS
    PRODUCT --> ES
    ORDER --> PG
    VENDOR --> PG
    SEARCH --> ES

    ORDER --> QUEUE
    QUEUE --> CELERY
    CELERY --> STRIPE
    CELERY --> SENDGRID
    CELERY --> PG

    BEAT --> CELERY

    PRODUCT --> S3
    VENDOR --> S3

    classDef client fill:#e1f5fe
    classDef edge fill:#fff3e0
    classDef lb fill:#f3e5f5
    classDef compute fill:#e8f5e9
    classDef app fill:#fff9c4
    classDef async fill:#fce4ec
    classDef data fill:#e0f2f1
    classDef external fill:#efebe9

    class WEB,MOBILE client
    class CDN,WAF edge
    class ALB lb
    class FE1,FE2,BE1,BE2 compute
    class API,AUTH,CART,PRODUCT,ORDER,VENDOR,SEARCH app
    class CELERY,BEAT,QUEUE async
    class PG,REDIS,ES,S3 data
    class STRIPE,SENDGRID,ANALYTICS external
```

## 3. Key Component Interactions

### 3.1 Communication Patterns

| Type | Protocol | Use Case |
|------|----------|----------|
| **Client → Frontend** | HTTPS/HTTP2 | Static assets, SPA delivery |
| **Frontend → Backend** | REST/HTTPS | API calls, state updates |
| **Backend → Database** | Connection Pool | Direct SQL queries via SQLAlchemy |
| **Backend → Cache** | Redis Protocol | Cache get/set operations |
| **Backend → Search** | HTTP REST | Elasticsearch queries |
| **Backend → Async** | Redis (as broker) | Task queue messages |
| **Async Workers → External** | HTTPS | Third-party API calls |
| **Async → Backend** | Direct SQL | Task result updates |

### 3.2 API Gateway Pattern

The Django REST Framework serves as an internal API gateway:

```python
# API Gateway routes requests to appropriate services
/api/v1/auth/*    → Authentication Service
/api/v1/products/* → Product Service
/api/v1/cart/*     → Cart Service
/api/v1/orders/*   → Order Service
/api/v1/vendors/*  → Vendor Service
/api/v1/search/*   → Search Service
```

## 4. Data Flow

### 4.1 User Registration Flow

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as API Gateway
    participant AS as Auth Service
    participant DB as PostgreSQL
    participant C as Cache (Redis)
    participant E as Email Service

    U->>F: Submit Registration Form
    F->>F: Validate Input
    F->>A: POST /api/v1/auth/register
    A->>AS: Forward Request
    AS->>AS: Validate Password, Email Format
    AS->>DB: Check Existing User
    DB-->>AS: User Not Found
    AS->>DB: Create User Record
    AS->>DB: Create Profile Record
    AS->>C: Cache User Session
    AS->>E: Queue Welcome Email
    AS-->>A: Return User Data + Token
    A-->>F: JSON Response
    F->>F: Store Token (Secure Storage)
    F->>F: Redirect to Dashboard
    E-->>U: Send Welcome Email
```

### 4.2 Product Search Flow

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as API Gateway
    participant PS as Product Service
    participant ES as Elasticsearch
    participant C as Cache (Redis)
    participant DB as PostgreSQL

    U->>F: Enter Search Query
    F->>F: Debounce Input (300ms)
    F->>A: GET /api/v1/search?q=query&filters={}
    A->>PS: Forward Search Request
    PS->>C: Check Cached Results
    alt Cache Hit
        C-->>PS: Return Cached Data
    else Cache Miss
        PS->>ES: Execute Search Query
        ES-->>PS: Return Product IDs + Scores
        PS->>DB: Fetch Full Product Details
        DB-->>PS: Product Data
        PS->>C: Cache Results (5min TTL)
    end
    PS->>PS: Apply Collaborative Filtering
    PS-->>A: Return Search Results
    A-->>F: JSON Response
    F->>F: Render Product Cards
```

### 4.3 Checkout & Order Flow

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as API Gateway
    participant CS as Cart Service
    participant OS as Order Service
    participant IS as Inventory Service
    participant Q as Celery Queue
    participant W as Celery Worker
    participant S as Stripe API
    participant DB as PostgreSQL

    U->>F: Click "Checkout"
    F->>A: GET /api/v1/cart
    A->>CS: Fetch Cart Items
    CS-->>F: Cart Data
    F->>U: Display Checkout Form

    U->>F: Submit Order
    F->>F: Validate Form
    F->>A: POST /api/v1/orders/checkout
    A->>OS: Create Order Request
    OS->>DB: Create Order (Pending)
    OS->>IS: Reserve Inventory
    IS->>DB: Lock Products
    IS-->>OS: Reservation Confirmed
    OS->>Q: Queue Payment Task
    OS-->>A: Return Order ID
    A-->>F: Order Created Response

    Q->>W: Pick up Payment Task
    W->>S: Process Payment
    S-->>W: Payment Success
    W->>DB: Update Order (Paid)
    W->>DB: Update Inventory
    W->>Q: Queue Email Task
    W->>Q: Queue Analytics Task
    W-->>U: Push Notification (Optional)

    U->>F: Navigate to Order Page
    F->>A: GET /api/v1/orders/{id}
    A-->>F: Order Details
```

## 5. Scalability & Performance Strategy

### 5.1 Horizontal Scaling

| Component | Scaling Strategy | Implementation |
|-----------|------------------|----------------|
| **Frontend** | Auto-scaling | ECS with ALB, target tracking |
| **API Servers** | Auto-scaling | ECS with CPU/Memory metrics |
| **Celery Workers** | Auto-scaling | ECS with queue depth metric |
| **Database** | Read Replicas | RDS Multi-AZ with read replicas |
| **Cache** | Cluster Mode | Redis Cluster with sharding |
| **Search** | Horizontal | Elasticsearch cluster |

### 5.2 Performance Optimization

**Database Level:**
- Proper indexing on frequently queried columns
- Connection pooling (pg_bouncer)
- Query optimization with EXPLAIN ANALYZE
- Denormalized views for reporting
- Partitioned tables for orders/history

**Cache Strategy:**
- Product details cache (5-15 min TTL)
- User session cache (24h TTL)
- Search results cache (5 min TTL)
- Cart data cache (7 day TTL)
- Full-page cache for home page

**Frontend Optimization:**
- Code splitting by route
- Lazy loading components
- Image optimization (WebP, lazy load)
- Service Worker for offline support
- CDN for static assets

### 5.3 Caching Hierarchy

```
Level 1: Browser Cache (CDN)
    ↓
Level 2: Application Cache (Redis)
    ↓
Level 3: Database Query Cache
    ↓
Level 4: PostgreSQL Database
```

## 6. Security Considerations

### 6.1 Authentication & Authorization

```mermaid
graph LR
    A[User Request] --> B{Authenticated?}
    B -->|No| C[Return 401]
    B -->|Yes| D{Authorized?}
    D -->|No| E[Return 403]
    D -->|Yes| F[Process Request]

    style A fill:#e1f5fe
    style C fill:#ffebee
    style E fill:#fff3e0
    style F fill:#e8f5e9
```

**Implementation:**
- JWT tokens for stateless authentication
- Refresh token rotation
- Role-based access control (RBAC)
- API key authentication for vendor integrations
- OAuth 2.0 for social login

### 6.2 Data Protection

| Concern | Solution |
|---------|----------|
| **Data at Rest** | AES-256 encryption (RDS default) |
| **Data in Transit** | TLS 1.3 for all connections |
| **PII Storage** | Encrypted fields, access logs |
| **Password** | bcrypt with salt, minimum 12 rounds |
| **PCI Compliance** | No card data storage, tokenize via Stripe |
| **GDPR** | Right to export/delete implementation |

### 6.3 API Security

```
Security Layers:
┌─────────────────────────────────────┐
│ 1. WAF (AWS WAF)                    │
│    - Rate limiting                   │
│    - SQL injection protection        │
│    - XSS protection                  │
├─────────────────────────────────────┤
│ 2. API Gateway (Django Middleware)  │
│    - Authentication check            │
│    - Request validation              │
│    - CSRF protection                 │
├─────────────────────────────────────┤
│ 3. Application Level                 │
│    - Input sanitization              │
│    - Output encoding                 │
│    - Parameterized queries           │
├─────────────────────────────────────┤
│ 4. Service Level                     │
│    - Principle of least privilege    │
│    - Database user restrictions      │
└─────────────────────────────────────┘
```

### 6.4 Secret Management

- **AWS Secrets Manager**: Store database credentials, API keys
- **Environment Variables**: Container-specific config
- **Parameter Store**: CI/CD pipeline secrets
- **Rotation Policy**: Automatic secret rotation (90 days)

## 7. Error Handling & Logging Philosophy

### 7.1 Error Response Structure

```typescript
// Standard API Error Response
interface ApiError {
  error: {
    code: string;           // e.g., "VALIDATION_ERROR"
    message: string;        // Human-readable message
    details?: any;          // Validation errors, stack trace (dev)
    requestId: string;      // For tracing
    timestamp: string;      // ISO 8601
  };
}
```

### 7.2 Error Categories

| Category | HTTP Code | Example |
|----------|-----------|---------|
| **Validation** | 400 | Invalid email format |
| **Authentication** | 401 | Missing/invalid token |
| **Authorization** | 403 | Insufficient permissions |
| **Not Found** | 404 | Product doesn't exist |
| **Conflict** | 409 | Email already registered |
| **Rate Limit** | 429 | Too many requests |
| **Server Error** | 500 | Unexpected error |

### 7.3 Logging Strategy

```
Log Levels:
┌────────────────────────────────────┐
│ CRITICAL    System-wide failure    │ → PagerDuty
│ ERROR       Failed operations      │ → Sentry + CloudWatch
│ WARNING     Degraded performance   │ → CloudWatch
│ INFO        Business events        │ → CloudWatch + Analytics
│ DEBUG       Detailed diagnostics   │ → Development only
└────────────────────────────────────┘
```

**Log Format (JSON):**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "service": "order-service",
  "environment": "production",
  "request_id": "req-abc123",
  "user_id": "user-456",
  "event": "order_created",
  "order_id": "ORD-789",
  "amount": 99.99
}
```

### 7.4 Distributed Tracing

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API Gateway
    participant S1 as Service 1
    participant S2 as Service 2
    participant DB as Database

    C->>GW: Request (X-Request-ID: abc123)
    GW->>S1: Forward (trace: abc123, span: 001)
    S1->>DB: Query (trace: abc123, span: 002)
    DB-->>S1: Results
    S1->>S2: Call Service (trace: abc123, span: 003)
    S2-->>S1: Response
    S1-->>GW: Response (trace: abc123)
    GW-->>C: Response (X-Request-ID: abc123)
```

## 8. Deployment Architecture

### 8.1 Environment Strategy

```
┌─────────────────────────────────────────────────────────┐
│                    Production                            │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Multi-AZ Deployment                            │   │
│  │  - 2+ AZs for high availability                 │   │
│  │  - Blue-Green deployments                       │   │
│  │  - Auto-scaling enabled                         │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    Staging                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Single-AZ Deployment                           │   │
│  │  - Pre-production testing                       │   │
│  │  - Load testing environment                     │   │
│  │  - Production-like configuration                │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    Development                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Local / Docker Compose                         │   │
│  │  - Feature development                          │   │
│  │  - Unit / Integration testing                   │   │
│  │  - Hot reload enabled                           │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 8.2 Deployment Pipeline

```mermaid
graph LR
    A[Push Code] --> B[CI Pipeline]
    B --> C{Tests Pass?}
    C -->|No| D[Notify Developer]
    C -->|Yes| E[Build Docker Image]
    E --> F[Push to ECR]
    F --> G[Deploy to Dev]
    G --> H{Manual Approve}
    H -->|Yes| I[Deploy to Staging]
    I --> J{E2E Tests Pass?}
    J -->|No| D
    J -->|Yes| K[Deploy to Production]
    K --> L[Smoke Tests]
    L --> M{Healthy?}
    M -->|No| N[Rollback]
    M -->|Yes| O[Complete]
```

---

*Document Version: 1.0*
*Last Updated: 2024*
*Architecture Status: Designed*
