# Enterprise Banking Platform - Architecture Documentation

## Table of Contents
1. [Architectural Pattern](#1-architectural-pattern)
2. [Key Component Interactions](#2-key-component-interactions)
3. [Data Flow](#3-data-flow)
4. [Scalability & Performance Strategy](#4-scalability--performance-strategy)
5. [Security Considerations](#5-security-considerations)
6. [Error Handling & Logging Philosophy](#6-error-handling--logging-philosophy)

---

## 1. Architectural Pattern

### 1.1 Chosen Pattern: Event-Driven Microservices with CQRS and Event Sourcing

This platform implements a **reactive event-driven microservices architecture** combining:
- **CQRS (Command Query Responsibility Segregation)** for transaction processing
- **Event Sourcing** for account state management
- **Saga Pattern** for distributed transaction coordination

### 1.2 Justification

| Requirement | Architectural Response |
|------------|----------------------|
| High transaction throughput | Reactive stack (WebFlux) with non-blocking I/O |
| Audit compliance | Event Sourcing provides complete audit trail |
| Complex queries vs writes | CQRS separates read/write models |
| Fault tolerance | Event-driven decoupling, Saga compensation |
| ML integration | Async event streaming to fraud service |
| Scalability | Stateless services, horizontal scaling |

### 1.3 High-Level Architecture Diagram

```mermaid
graph TB
    subgraph "Client Layer"
        WEB[React Web App]
        MOBILE[Mobile Apps]
        EXT[External Partners]
    end

    subgraph "Edge Layer"
        CDN[CloudFront CDN]
        WAF[AWS WAF]
        ALB[Application Load Balancer]
    end

    subgraph "API Gateway Layer"
        GW[Spring Cloud Gateway]
        AUTH[OAuth2 / JWT Auth]
        RATE[Rate Limiter]
    end

    subgraph "Service Mesh"
        subgraph "Core Services"
            ACC[Account Service]
            TXN[Transaction Service]
            LOAN[Loan Service]
        end

        subgraph "Supporting Services"
            FRAUD[Fraud Detection]
            NOTIF[Notification Service]
        end

        subgraph "Infrastructure Services"
            CONFIG[Config Server]
            DISCOVERY[Eureka Discovery]
        end
    end

    subgraph "Event Backbone"
        KAFKA[Apache Kafka]
        SCHEMA[Schema Registry]
    end

    subgraph "Data Layer"
        PG_ACC[(PostgreSQL<br/>Accounts)]
        PG_TXN[(PostgreSQL<br/>Transactions)]
        PG_LOAN[(PostgreSQL<br/>Loans)]
        REDIS[(Redis Cache)]
        ES[(Elasticsearch<br/>Search/Analytics)]
    end

    subgraph "ML Platform"
        CATBOOST[CatBoost Model]
        FEATURE[Feature Store]
    end

    WEB --> CDN
    MOBILE --> ALB
    EXT --> WAF
    CDN --> ALB
    WAF --> ALB
    ALB --> GW

    GW --> AUTH
    GW --> RATE
    GW --> ACC
    GW --> TXN
    GW --> LOAN

    ACC --> PG_ACC
    ACC --> KAFKA
    ACC --> REDIS

    TXN --> PG_TXN
    TXN --> KAFKA
    TXN --> REDIS

    LOAN --> PG_LOAN
    LOAN --> KAFKA

    KAFKA --> FRAUD
    KAFKA --> NOTIF
    KAFKA --> ES

    FRAUD --> CATBOOST
    FRAUD --> FEATURE

    CONFIG --> ACC
    CONFIG --> TXN
    CONFIG --> LOAN

    DISCOVERY --> GW
```

---

## 2. Key Component Interactions

### 2.1 Service Communication Patterns

```mermaid
graph LR
    subgraph "Synchronous"
        A[API Gateway] -->|REST/WebFlux| B[Account Service]
        A -->|REST/WebFlux| C[Transaction Service]
        B -->|gRPC| D[Fraud Service]
    end

    subgraph "Asynchronous"
        C -->|Kafka Event| E[Event Store]
        E -->|Kafka Event| F[Notification Service]
        E -->|Kafka Event| G[Analytics Pipeline]
    end

    subgraph "Query"
        H[Read API] -->|R2DBC| I[(Read Replica)]
    end
```

### 2.2 Communication Matrix

| From | To | Protocol | Pattern | Purpose |
|------|-----|----------|---------|---------|
| Gateway | All Services | HTTP/2 | Request-Response | API routing |
| Transaction | Fraud Detection | gRPC | Request-Response | Real-time scoring |
| Account | Event Store | Kafka | Publish | Event sourcing |
| Transaction | Kafka | Kafka | Publish | CQRS events |
| Kafka | Query Service | Kafka | Subscribe | Read model update |
| Services | Config Server | HTTP | Pull | Configuration |
| Services | Discovery | HTTP | Register/Heartbeat | Service mesh |

### 2.3 CQRS Implementation Detail

```mermaid
graph TB
    subgraph "Command Side"
        CMD[Command API]
        CMDH[Command Handler]
        AGG[Aggregate]
        EVTS[Event Store]

        CMD --> CMDH
        CMDH --> AGG
        AGG --> EVTS
    end

    subgraph "Event Bus"
        KAFKA[Kafka Topics]
        EVTS --> KAFKA
    end

    subgraph "Query Side"
        PROJ[Projector]
        READ[(Read DB)]
        QUERY[Query API]

        KAFKA --> PROJ
        PROJ --> READ
        QUERY --> READ
    end

    CLIENT[Client] -->|Write| CMD
    CLIENT -->|Read| QUERY
```

---

## 3. Data Flow

### 3.1 Fund Transfer Flow (Main Use Case)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant GW as API Gateway
    participant TS as Transaction Service
    participant FS as Fraud Service
    participant AS as Account Service
    participant K as Kafka
    participant DB as PostgreSQL

    U->>GW: POST /transfers
    GW->>GW: Validate JWT
    GW->>TS: Forward request

    TS->>TS: Validate transfer command
    TS->>FS: Score transaction (gRPC)
    FS->>FS: CatBoost inference
    FS-->>TS: Risk score: 0.15

    alt Risk Score > Threshold
        TS-->>U: 403 Transfer blocked
    else Risk Score OK
        TS->>K: Publish TransferInitiated

        par Debit Source Account
            K->>AS: DebitCommand
            AS->>DB: Update balance (R2DBC)
            AS->>K: AccountDebited
        and Credit Target Account
            K->>AS: CreditCommand
            AS->>DB: Update balance (R2DBC)
            AS->>K: AccountCredited
        end

        TS->>K: TransferCompleted
        K->>TS: Update read model
        TS-->>U: 200 Transfer successful
    end
```

### 3.2 Event Sourcing - Account Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Created: AccountCreated
    Created --> Active: AccountActivated
    Active --> Active: FundsDeposited
    Active --> Active: FundsWithdrawn
    Active --> Frozen: AccountFrozen
    Frozen --> Active: AccountUnfrozen
    Active --> Closed: AccountClosed
    Closed --> [*]
```

### 3.3 Data Flow Diagram

```mermaid
flowchart LR
    subgraph Input
        UI[Web UI]
        API[External API]
        BATCH[Batch Jobs]
    end

    subgraph Processing
        VAL[Validation Layer]
        BIZ[Business Logic]
        EVT[Event Generation]
    end

    subgraph Storage
        CMD_DB[(Command DB)]
        EVT_STORE[(Event Store)]
        READ_DB[(Read Model)]
        CACHE[(Redis Cache)]
    end

    subgraph Output
        RESP[API Response]
        NOTIF[Notifications]
        REPORT[Reports]
    end

    UI --> VAL
    API --> VAL
    BATCH --> VAL

    VAL --> BIZ
    BIZ --> EVT

    EVT --> CMD_DB
    EVT --> EVT_STORE
    EVT_STORE --> READ_DB
    BIZ --> CACHE

    READ_DB --> RESP
    CACHE --> RESP
    EVT_STORE --> NOTIF
    READ_DB --> REPORT
```

---

## 4. Scalability & Performance Strategy

### 4.1 Horizontal Scaling Architecture

```mermaid
graph TB
    subgraph "Auto-Scaling Groups"
        subgraph "Account Service Pool"
            ACC1[Account Pod 1]
            ACC2[Account Pod 2]
            ACC3[Account Pod N]
        end

        subgraph "Transaction Service Pool"
            TXN1[Transaction Pod 1]
            TXN2[Transaction Pod 2]
            TXN3[Transaction Pod N]
        end
    end

    subgraph "Data Tier"
        subgraph "PostgreSQL"
            MASTER[(Primary)]
            REPLICA1[(Read Replica 1)]
            REPLICA2[(Read Replica 2)]
        end

        subgraph "Redis Cluster"
            R1[Shard 1]
            R2[Shard 2]
            R3[Shard 3]
        end
    end

    LB[Load Balancer] --> ACC1
    LB --> ACC2
    LB --> ACC3
    LB --> TXN1
    LB --> TXN2
    LB --> TXN3

    ACC1 & ACC2 & ACC3 --> MASTER
    TXN1 & TXN2 & TXN3 --> REPLICA1
    TXN1 & TXN2 & TXN3 --> REPLICA2

    ACC1 & TXN1 --> R1
    ACC2 & TXN2 --> R2
    ACC3 & TXN3 --> R3
```

### 4.2 Performance Strategies

| Strategy | Implementation | Expected Gain |
|----------|---------------|---------------|
| **Reactive I/O** | WebFlux + R2DBC | 5-10x throughput |
| **Connection Pooling** | R2DBC Pool | Reduced latency |
| **Caching** | Redis with TTL | 90% cache hit rate |
| **Read Replicas** | PostgreSQL streaming | Scale reads |
| **Event Batching** | Kafka batch producer | Higher throughput |
| **GraalVM Native** | AOT compilation | 50ms startup |
| **Query Optimization** | Materialized views | Fast aggregations |

### 4.3 Capacity Planning

```
Target Metrics:
- 10,000 TPS for account queries
- 1,000 TPS for fund transfers
- < 100ms P99 latency for reads
- < 500ms P99 latency for transfers
- 99.99% availability

Scaling Triggers:
- CPU > 70%: Scale out +2 pods
- Memory > 80%: Scale out +2 pods
- Request queue > 100: Scale out +4 pods
- Error rate > 1%: Alert + investigate
```

---

## 5. Security Considerations

### 5.1 Security Architecture

```mermaid
graph TB
    subgraph "Perimeter"
        WAF[AWS WAF]
        SHIELD[AWS Shield]
        CDN[CloudFront]
    end

    subgraph "Network"
        VPC[Private VPC]
        SG[Security Groups]
        NACL[Network ACLs]
    end

    subgraph "Application"
        AUTH[OAuth2/OIDC]
        RBAC[Role-Based Access]
        ENCRYPT[TLS 1.3]
    end

    subgraph "Data"
        KMS[AWS KMS]
        SECRETS[Secrets Manager]
        MASK[Data Masking]
    end

    subgraph "Audit"
        LOGS[CloudWatch Logs]
        TRAIL[CloudTrail]
        SIEM[SIEM Integration]
    end

    WAF --> VPC
    VPC --> AUTH
    AUTH --> RBAC
    RBAC --> KMS
    KMS --> LOGS
```

### 5.2 Authentication & Authorization

```mermaid
sequenceDiagram
    participant U as User
    participant GW as Gateway
    participant IDP as Identity Provider
    participant SVC as Service
    participant DB as Database

    U->>GW: Request + Credentials
    GW->>IDP: Validate credentials
    IDP-->>GW: JWT Token
    GW->>GW: Attach JWT to request
    GW->>SVC: Request + JWT
    SVC->>SVC: Validate JWT signature
    SVC->>SVC: Extract roles/permissions
    SVC->>SVC: Check RBAC policy
    alt Authorized
        SVC->>DB: Execute query
        DB-->>SVC: Data
        SVC-->>U: 200 Response
    else Unauthorized
        SVC-->>U: 403 Forbidden
    end
```

### 5.3 Security Controls Matrix

| Control | Implementation | Layer |
|---------|---------------|-------|
| **Authentication** | OAuth2 + JWT | Application |
| **Authorization** | Spring Security RBAC | Application |
| **API Security** | Rate limiting, WAF | Edge |
| **Data Encryption** | TLS 1.3 in transit, AES-256 at rest | Network/Data |
| **Secret Management** | AWS Secrets Manager | Infrastructure |
| **Input Validation** | Bean Validation, sanitization | Application |
| **SQL Injection** | Parameterized queries (R2DBC) | Data |
| **XSS Prevention** | React auto-escaping, CSP | Frontend |
| **CSRF Protection** | SameSite cookies, tokens | Application |
| **Audit Logging** | All mutations logged | Application |

### 5.4 Data Classification

| Classification | Examples | Protection |
|---------------|----------|------------|
| **PII** | Name, SSN, Address | Encryption, masking |
| **Financial** | Account numbers, balances | Encryption, audit |
| **Authentication** | Passwords, tokens | Hashing, rotation |
| **Public** | Product info | Standard protection |

---

## 6. Error Handling & Logging Philosophy

### 6.1 Error Handling Strategy

```mermaid
flowchart TD
    REQ[Incoming Request] --> VAL{Validation}

    VAL -->|Invalid| ERR400[400 Bad Request]
    VAL -->|Valid| AUTH{Authorization}

    AUTH -->|Unauthorized| ERR401[401 Unauthorized]
    AUTH -->|Forbidden| ERR403[403 Forbidden]
    AUTH -->|OK| BIZ{Business Logic}

    BIZ -->|Not Found| ERR404[404 Not Found]
    BIZ -->|Conflict| ERR409[409 Conflict]
    BIZ -->|Business Error| ERR422[422 Unprocessable]
    BIZ -->|Success| RESP200[200 OK]

    subgraph "Error Response"
        ERR400 --> FORMAT
        ERR401 --> FORMAT
        ERR403 --> FORMAT
        ERR404 --> FORMAT
        ERR409 --> FORMAT
        ERR422 --> FORMAT
        FORMAT[Standardized Error Format]
    end
```

### 6.2 Standardized Error Response Format

```json
{
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Account has insufficient balance for this transfer",
    "details": {
      "accountId": "ACC-12345",
      "requestedAmount": 1000.00,
      "availableBalance": 500.00
    },
    "traceId": "abc-123-def-456",
    "timestamp": "2026-01-17T10:30:00Z",
    "path": "/api/v1/transfers"
  }
}
```

### 6.3 Logging Architecture

```mermaid
graph LR
    subgraph "Services"
        S1[Account Service]
        S2[Transaction Service]
        S3[Loan Service]
    end

    subgraph "Collection"
        FLUENTD[Fluentd/Fluent Bit]
    end

    subgraph "Processing"
        KAFKA_LOG[Kafka - Logs Topic]
    end

    subgraph "Storage & Analysis"
        ES[Elasticsearch]
        KIBANA[Kibana]
        GRAFANA[Grafana]
    end

    subgraph "Alerting"
        ALERT[Alert Manager]
        PAGER[PagerDuty]
    end

    S1 --> FLUENTD
    S2 --> FLUENTD
    S3 --> FLUENTD

    FLUENTD --> KAFKA_LOG
    KAFKA_LOG --> ES
    ES --> KIBANA
    ES --> GRAFANA
    GRAFANA --> ALERT
    ALERT --> PAGER
```

### 6.4 Logging Standards

| Level | Usage | Example |
|-------|-------|---------|
| **ERROR** | Unrecoverable failures | Database connection lost |
| **WARN** | Recoverable issues | Retry succeeded after failure |
| **INFO** | Business events | Transfer completed |
| **DEBUG** | Diagnostic info | Request/response details |
| **TRACE** | Fine-grained debug | Method entry/exit |

### 6.5 Structured Log Format

```json
{
  "timestamp": "2026-01-17T10:30:00.123Z",
  "level": "INFO",
  "service": "transaction-service",
  "traceId": "abc-123",
  "spanId": "def-456",
  "userId": "USR-789",
  "action": "TRANSFER_COMPLETED",
  "message": "Fund transfer completed successfully",
  "metadata": {
    "sourceAccount": "ACC-111",
    "targetAccount": "ACC-222",
    "amount": 500.00,
    "currency": "USD",
    "durationMs": 145
  }
}
```

### 6.6 Distributed Tracing

```mermaid
gantt
    title Request Trace Timeline
    dateFormat X
    axisFormat %L ms

    section Gateway
    JWT Validation    :0, 5
    Route Resolution  :5, 8

    section Transaction
    Command Handling  :10, 25

    section Fraud Check
    ML Inference      :25, 45

    section Account
    Debit Operation   :50, 70
    Credit Operation  :50, 75

    section Event Store
    Persist Events    :80, 95

    section Response
    Build Response    :95, 100
```

### 6.7 Resilience Patterns (Resilience4j)

```java
// Circuit Breaker Configuration
@CircuitBreaker(name = "fraudService", fallbackMethod = "fallbackFraudCheck")
@Retry(name = "fraudService")
@TimeLimiter(name = "fraudService")
public Mono<RiskScore> checkFraud(Transaction tx) {
    return fraudClient.score(tx);
}

// Fallback - allow transaction with elevated logging
public Mono<RiskScore> fallbackFraudCheck(Transaction tx, Exception e) {
    log.warn("Fraud service unavailable, applying manual review flag");
    return Mono.just(RiskScore.MANUAL_REVIEW);
}
```

---

## Appendix A: Technology Decision Records

### ADR-001: Reactive Stack Selection
**Decision**: Use Spring WebFlux with R2DBC
**Rationale**: High concurrency requirement for banking transactions
**Trade-offs**: Steeper learning curve, debugging complexity

### ADR-002: Event Sourcing for Accounts
**Decision**: Implement event sourcing for account aggregate
**Rationale**: Complete audit trail required by compliance
**Trade-offs**: Increased storage, replay complexity

### ADR-003: CQRS for Transactions
**Decision**: Separate read and write models
**Rationale**: Different scaling needs for queries vs commands
**Trade-offs**: Eventual consistency, increased complexity

---

*Document Version: 1.0*
*Last Updated: 2026-01-17*
