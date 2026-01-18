# Healthcare Claims Processing - Architecture Documentation

## 1. Executive Summary

This document describes the architecture for the Healthcare Claims Processing system, a cloud-native application designed to process insurance claims with automated adjudication, ML-powered fraud detection, and real-time integration with external scoring APIs.

---

## 2. Architectural Pattern

### 2.1 Chosen Pattern: Event-Driven Microservices with CQRS

```mermaid
graph TB
    subgraph "Client Layer"
        WEB[Angular Web App]
        MOBILE[Mobile Apps]
        API_CLIENTS[API Clients]
    end

    subgraph "API Gateway"
        GW[Azure API Management]
    end

    subgraph "Application Services"
        CLAIMS[Claims Service<br/>Quarkus]
        FRAUD[Fraud Detection<br/>Service]
        ADJUD[Adjudication<br/>Service]
        NOTIFY[Notification<br/>Service]
    end

    subgraph "Event Bus"
        KAFKA[Apache Kafka]
    end

    subgraph "Data Layer"
        PG[(PostgreSQL)]
        ES[(Elasticsearch)]
        REDIS[(Redis Cache)]
    end

    subgraph "External"
        ML_API[ML Scoring API]
        PROVIDER_API[Provider API]
    end

    WEB --> GW
    MOBILE --> GW
    API_CLIENTS --> GW

    GW --> CLAIMS
    GW --> FRAUD

    CLAIMS --> KAFKA
    CLAIMS --> PG

    FRAUD --> KAFKA
    FRAUD --> ML_API

    ADJUD --> KAFKA
    ADJUD --> PG

    KAFKA --> ADJUD
    KAFKA --> NOTIFY
    KAFKA --> ES

    CLAIMS --> REDIS
    CLAIMS --> ES
```

### 2.2 Pattern Justification

| Requirement | How Architecture Addresses It |
|-------------|------------------------------|
| **High Throughput** | Kafka enables async processing, prevents blocking |
| **Low Latency** | Quarkus native compilation achieves <100ms cold starts |
| **Fraud Detection** | Event-driven allows real-time ML scoring integration |
| **Auditability** | Event sourcing provides complete claim history |
| **Scalability** | Stateless services scale horizontally on Azure Functions |
| **Resilience** | Decoupled services fail independently |

---

## 3. Key Component Interactions

### 3.1 Service Communication Matrix

```mermaid
graph LR
    subgraph "Synchronous (GraphQL/REST)"
        A[Client] -->|GraphQL| B[Claims Service]
        B -->|REST| C[Provider API]
        B -->|gRPC| D[Fraud Service]
    end

    subgraph "Asynchronous (Kafka)"
        E[Claims Service] -->|claim.submitted| F[Kafka]
        F -->|claim.submitted| G[Adjudication]
        F -->|claim.submitted| H[Fraud Detection]
        G -->|claim.adjudicated| F
        H -->|fraud.scored| F
    end
```

### 3.2 Communication Patterns

| Pattern | Use Case | Technology |
|---------|----------|------------|
| Request/Response | User queries, CRUD operations | GraphQL, REST |
| Publish/Subscribe | Claim events, fraud alerts | Kafka Topics |
| Event Sourcing | Claim state tracking | Kafka + PostgreSQL |
| CQRS | Separate read/write models | Elasticsearch (read), PostgreSQL (write) |

---

## 4. Data Flow

### 4.1 Claim Submission Flow

```mermaid
sequenceDiagram
    participant U as User
    participant GW as API Gateway
    participant CS as Claims Service
    participant K as Kafka
    participant AS as Adjudication
    participant FD as Fraud Detection
    participant ML as ML API
    participant DB as PostgreSQL
    participant ES as Elasticsearch
    participant N as Notification

    U->>GW: Submit Claim (GraphQL)
    GW->>CS: Forward Request
    CS->>CS: Validate Input
    CS->>DB: Save Claim (PENDING)
    CS->>K: Publish claim.submitted
    CS-->>U: Return Claim ID

    par Parallel Processing
        K->>AS: Consume claim.submitted
        AS->>AS: Apply Rules Engine
        AS->>DB: Update Claim Status
        AS->>K: Publish claim.adjudicated
    and
        K->>FD: Consume claim.submitted
        FD->>ML: Request Fraud Score
        ML-->>FD: Return Score
        FD->>K: Publish fraud.scored
    end

    K->>CS: Consume fraud.scored
    CS->>DB: Update Fraud Score

    alt High Fraud Score
        CS->>K: Publish claim.flagged
        K->>N: Send Alert
        N->>U: Notify Reviewer
    end

    K->>ES: Index Claim
```

### 4.2 Claim State Machine

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED: Submit
    SUBMITTED --> VALIDATING: Auto
    VALIDATING --> INVALID: Validation Failed
    VALIDATING --> PENDING_ADJUDICATION: Valid

    PENDING_ADJUDICATION --> AUTO_ADJUDICATED: Rules Match
    PENDING_ADJUDICATION --> PENDING_REVIEW: Manual Required
    PENDING_ADJUDICATION --> FLAGGED_FRAUD: High Fraud Score

    AUTO_ADJUDICATED --> APPROVED: Pass
    AUTO_ADJUDICATED --> DENIED: Fail

    PENDING_REVIEW --> APPROVED: Reviewer Approves
    PENDING_REVIEW --> DENIED: Reviewer Denies

    FLAGGED_FRAUD --> PENDING_REVIEW: Fraud Team Review
    FLAGGED_FRAUD --> DENIED: Confirmed Fraud

    INVALID --> [*]
    APPROVED --> PAID: Payment Processed
    PAID --> [*]
    DENIED --> [*]
```

---

## 5. Component Architecture

### 5.1 Claims Service (Core)

```mermaid
graph TB
    subgraph "Claims Service"
        subgraph "API Layer"
            REST[REST Controller]
            GQL[GraphQL Resolver]
        end

        subgraph "Application Layer"
            SVC[ClaimService]
            ADJ[AdjudicationService]
            FRD[FraudDetectionService]
        end

        subgraph "Domain Layer"
            ENT[Entities]
            REPO[Repositories]
            EVT[Domain Events]
        end

        subgraph "Infrastructure"
            DB_CONN[DB Connection Pool]
            KAFKA_PROD[Kafka Producer]
            ES_CLIENT[ES Client]
            CACHE[Redis Client]
        end
    end

    REST --> SVC
    GQL --> SVC
    SVC --> ADJ
    SVC --> FRD
    SVC --> REPO
    SVC --> KAFKA_PROD
    REPO --> DB_CONN
    SVC --> ES_CLIENT
    SVC --> CACHE
```

### 5.2 Domain Model

```mermaid
classDiagram
    class Claim {
        +UUID id
        +String claimNumber
        +ClaimType type
        +ClaimStatus status
        +BigDecimal amount
        +LocalDate serviceDate
        +UUID patientId
        +UUID providerId
        +Double fraudScore
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class Patient {
        +UUID id
        +String memberId
        +String firstName
        +String lastName
        +LocalDate dateOfBirth
        +String policyNumber
    }

    class Provider {
        +UUID id
        +String npi
        +String name
        +String specialty
        +String taxId
        +Boolean inNetwork
    }

    class AdjudicationResult {
        +UUID id
        +UUID claimId
        +String ruleApplied
        +String decision
        +BigDecimal allowedAmount
        +String reason
        +LocalDateTime processedAt
    }

    class ClaimEvent {
        +UUID id
        +UUID claimId
        +String eventType
        +String payload
        +LocalDateTime timestamp
    }

    Claim "1" --> "*" AdjudicationResult
    Claim "1" --> "*" ClaimEvent
    Claim "*" --> "1" Patient
    Claim "*" --> "1" Provider
```

---

## 6. Scalability & Performance Strategy

### 6.1 Scaling Architecture

```mermaid
graph TB
    subgraph "Auto-Scaling Groups"
        subgraph "Web Tier"
            WEB1[Angular CDN]
        end

        subgraph "API Tier - Azure Functions"
            API1[Claims Function 1]
            API2[Claims Function 2]
            API3[Claims Function N]
        end

        subgraph "Processing Tier"
            PROC1[Kafka Consumer 1]
            PROC2[Kafka Consumer 2]
            PROC3[Kafka Consumer N]
        end

        subgraph "Data Tier"
            DB_PRIMARY[(PostgreSQL Primary)]
            DB_REPLICA[(PostgreSQL Replica)]
            ES_CLUSTER[(ES Cluster)]
            REDIS_CLUSTER[(Redis Cluster)]
        end
    end

    LB[Azure Load Balancer] --> API1
    LB --> API2
    LB --> API3

    API1 --> DB_PRIMARY
    API2 --> DB_PRIMARY
    API3 --> DB_PRIMARY

    API1 -.-> DB_REPLICA
    API2 -.-> DB_REPLICA

    API1 --> REDIS_CLUSTER
    API2 --> REDIS_CLUSTER
```

### 6.2 Performance Targets

| Metric | Target | Strategy |
|--------|--------|----------|
| API Response Time (p99) | < 200ms | Reactive I/O, caching |
| Cold Start | < 100ms | Quarkus native, minimal deps |
| Throughput | 10,000 claims/min | Kafka partitioning, horizontal scaling |
| Search Latency | < 50ms | Elasticsearch optimization |
| Database Connections | Pool: 20-100 | PgBouncer connection pooling |

### 6.3 Caching Strategy

```mermaid
graph LR
    subgraph "Cache Layers"
        L1[L1: In-Memory<br/>Caffeine]
        L2[L2: Distributed<br/>Redis]
        L3[L3: CDN<br/>Azure CDN]
    end

    REQ[Request] --> L1
    L1 -->|Miss| L2
    L2 -->|Miss| L3
    L3 -->|Miss| DB[(Database)]
```

**Cache TTLs:**
- Provider lookups: 1 hour
- Patient data: 15 minutes
- Claim status: 30 seconds
- Rules configuration: 5 minutes

---

## 7. Security Architecture

### 7.1 Security Layers

```mermaid
graph TB
    subgraph "Perimeter Security"
        WAF[Azure WAF]
        DDOS[DDoS Protection]
    end

    subgraph "Identity & Access"
        AZURE_AD[Azure AD B2C]
        JWT[JWT Tokens]
        RBAC[Role-Based Access]
    end

    subgraph "Application Security"
        VALID[Input Validation]
        AUTHZ[Authorization]
        AUDIT[Audit Logging]
    end

    subgraph "Data Security"
        TLS[TLS 1.3]
        ENCRYPT[Encryption at Rest]
        MASK[Data Masking]
    end

    subgraph "Secrets"
        KV[Azure Key Vault]
    end

    USER[User] --> WAF
    WAF --> DDOS
    DDOS --> AZURE_AD
    AZURE_AD --> JWT
    JWT --> RBAC
    RBAC --> VALID
    VALID --> AUTHZ
    AUTHZ --> TLS
    TLS --> ENCRYPT
    KV --> APPLICATION
```

### 7.2 Authentication Flow

```mermaid
sequenceDiagram
    participant U as User
    participant FE as Angular App
    participant AD as Azure AD B2C
    participant API as Claims API
    participant KV as Key Vault

    U->>FE: Login Request
    FE->>AD: OAuth 2.0 PKCE
    AD->>AD: Authenticate
    AD-->>FE: ID Token + Access Token
    FE->>API: GraphQL + Bearer Token
    API->>API: Validate JWT
    API->>KV: Get Signing Keys
    KV-->>API: Keys
    API->>API: Verify Claims
    API-->>FE: Response
```

### 7.3 Security Controls

| Layer | Control | Implementation |
|-------|---------|----------------|
| Network | Firewall | Azure NSG, WAF rules |
| Transport | Encryption | TLS 1.3 everywhere |
| Identity | Authentication | Azure AD B2C, OAuth 2.0 |
| Authorization | RBAC | Claims-based, fine-grained |
| Data | Encryption | AES-256 at rest |
| Data | Masking | PHI fields masked in logs |
| Audit | Logging | All access logged, immutable |
| Secrets | Management | Azure Key Vault, no hardcoding |

### 7.4 HIPAA Compliance Considerations

- PHI data encrypted at rest and in transit
- Audit logging for all data access
- Role-based access with minimum privilege
- Data masking in non-production environments
- Business Associate Agreements (BAA) with cloud providers

---

## 8. Error Handling & Logging

### 8.1 Error Handling Strategy

```mermaid
graph TB
    subgraph "Error Categories"
        VAL[Validation Errors<br/>400 Bad Request]
        AUTH[Auth Errors<br/>401/403]
        BIZ[Business Errors<br/>422 Unprocessable]
        SYS[System Errors<br/>500 Internal]
        EXT[External Errors<br/>502/503/504]
    end

    subgraph "Handling"
        VAL --> LOG_WARN[Log Warning]
        AUTH --> LOG_SEC[Security Log]
        BIZ --> LOG_INFO[Log Info]
        SYS --> LOG_ERROR[Log Error + Alert]
        EXT --> RETRY[Retry + Circuit Breaker]
    end

    subgraph "Response"
        LOG_WARN --> RESP[Structured Error Response]
        LOG_SEC --> RESP
        LOG_INFO --> RESP
        LOG_ERROR --> RESP
        RETRY --> RESP
    end
```

### 8.2 Structured Logging Format

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "level": "ERROR",
  "service": "claims-service",
  "traceId": "abc123",
  "spanId": "def456",
  "userId": "user-789",
  "claimId": "claim-012",
  "message": "Failed to process claim",
  "error": {
    "type": "AdjudicationException",
    "message": "Rule engine timeout",
    "stackTrace": "..."
  },
  "context": {
    "claimType": "MEDICAL",
    "amount": 1500.00
  }
}
```

### 8.3 Observability Stack

```mermaid
graph LR
    subgraph "Collection"
        APP[Application] --> OTEL[OpenTelemetry]
        APP --> PROM[Prometheus Metrics]
        APP --> LOGS[Structured Logs]
    end

    subgraph "Processing"
        OTEL --> JAEGER[Jaeger]
        PROM --> GRAF_PROM[Prometheus Server]
        LOGS --> ELK[ELK Stack]
    end

    subgraph "Visualization"
        JAEGER --> GRAF[Grafana]
        GRAF_PROM --> GRAF
        ELK --> GRAF
    end

    subgraph "Alerting"
        GRAF --> ALERT[AlertManager]
        ALERT --> PAGER[PagerDuty]
        ALERT --> SLACK[Slack]
    end
```

---

## 9. Infrastructure Architecture

### 9.1 Azure Deployment

```mermaid
graph TB
    subgraph "Azure Region - Primary"
        subgraph "Networking"
            VNET[Virtual Network]
            SUBNET_APP[App Subnet]
            SUBNET_DATA[Data Subnet]
        end

        subgraph "Compute"
            FUNC[Azure Functions<br/>Consumption Plan]
            AKS[AKS Cluster<br/>Kafka Consumers]
        end

        subgraph "Data"
            PG_FLEX[PostgreSQL<br/>Flexible Server]
            ES_AZURE[Elasticsearch<br/>on AKS]
            REDIS_AZURE[Azure Cache<br/>for Redis]
        end

        subgraph "Messaging"
            EVENTHUB[Azure Event Hubs<br/>Kafka Protocol]
        end

        subgraph "Security"
            KV_AZURE[Azure Key Vault]
            AD_B2C[Azure AD B2C]
        end
    end

    FUNC --> SUBNET_APP
    AKS --> SUBNET_APP
    PG_FLEX --> SUBNET_DATA
    ES_AZURE --> SUBNET_DATA
    REDIS_AZURE --> SUBNET_DATA
```

### 9.2 Disaster Recovery

| Component | RPO | RTO | Strategy |
|-----------|-----|-----|----------|
| PostgreSQL | 5 min | 30 min | Geo-replication, PITR |
| Kafka | 0 | 15 min | Multi-AZ, replication |
| Elasticsearch | 1 hour | 1 hour | Snapshots to blob storage |
| Functions | 0 | 5 min | Multi-region deployment |

---

## 10. API Design

### 10.1 GraphQL Schema Overview

```graphql
type Query {
  claim(id: ID!): Claim
  claims(filter: ClaimFilter, pagination: Pagination): ClaimConnection
  searchClaims(query: String!): [Claim]
}

type Mutation {
  submitClaim(input: ClaimInput!): Claim
  updateClaimStatus(id: ID!, status: ClaimStatus!): Claim
  approveClaim(id: ID!, notes: String): Claim
  denyClaim(id: ID!, reason: String!): Claim
}

type Subscription {
  claimStatusChanged(claimId: ID!): ClaimStatusEvent
  fraudAlerts: FraudAlert
}
```

### 10.2 REST Endpoints (Fallback)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/claims | Submit new claim |
| GET | /api/v1/claims/{id} | Get claim by ID |
| GET | /api/v1/claims | List claims with filters |
| PATCH | /api/v1/claims/{id}/status | Update claim status |
| GET | /api/v1/claims/{id}/history | Get claim history |
| GET | /api/v1/health | Health check |

---

## 11. Fraud Detection Service Architecture

### 11.1 Service Overview

The Fraud Detection Service is a standalone Python FastAPI microservice that provides ML-powered fraud scoring for healthcare claims.

```mermaid
graph TB
    subgraph "Fraud Detection Service"
        API[FastAPI REST API]
        ENGINE[Scoring Engine]
        RULES[Heuristic Rules]
        ML[ML Model Loader]
    end

    CLAIMS[Claims Service] -->|POST /api/v1/score| API
    API --> ENGINE
    ENGINE --> RULES
    ENGINE --> ML

    subgraph "Scoring Factors"
        F1[Amount Analysis]
        F2[Date Patterns]
        F3[Procedure Codes]
        F4[Diagnosis Codes]
        F5[Claim Type Risk]
        F6[Round Amounts]
    end

    RULES --> F1
    RULES --> F2
    RULES --> F3
    RULES --> F4
    RULES --> F5
    RULES --> F6
```

### 11.2 Fraud Scoring API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/score` | POST | Score a claim for fraud |
| `/health` | GET | Health check |
| `/api/v1/metrics` | GET | Service metrics |
| `/api/v1/model/info` | GET | Model information |
| `/docs` | GET | Swagger UI |

### 11.3 Risk Levels

| Score Range | Risk Level | Recommended Action |
|-------------|------------|-------------------|
| 0.0 - 0.3 | LOW | Auto-approve |
| 0.3 - 0.5 | MEDIUM | Flag for review |
| 0.5 - 0.7 | HIGH | Manual review required |
| 0.7 - 1.0 | CRITICAL | Deny / SIU referral |

### 11.4 Scoring Factors

| Factor | Weight | Description |
|--------|--------|-------------|
| Extreme Amount ($50k+) | 0.30 | Very high claim amounts |
| Very High Amount ($15k+) | 0.20 | High claim amounts |
| High Amount ($5k+) | 0.10 | Above-average amounts |
| Future Service Date | 0.40 | Invalid future dates |
| Weekend Service | 0.08 | Services on weekends |
| High-Risk Procedures | 0.05-0.10 | Flagged CPT codes |
| Vague Diagnoses | 0.04-0.08 | Non-specific ICD-10 |
| Round Amounts | 0.04-0.08 | Suspicious round figures |

### 11.5 Integration with Claims Service

```java
// FraudScoringClient.java - Backend integration
@ApplicationScoped
public class FraudScoringClient {
    @ConfigProperty(name = "fraud.api.url")
    String fraudApiUrl;  // http://fraud-detection:8090

    public Uni<Double> getScore(Claim claim) {
        // POST to /api/v1/score
        // Returns fraud probability 0.0 - 1.0
    }
}
```

### 11.6 Extending with Real ML

The service is designed to be extended with real ML models:

```python
# Example: Adding scikit-learn model
import joblib

class FraudScoringEngine:
    def __init__(self):
        self.model = joblib.load("models/fraud_model.pkl")

    def score(self, request):
        features = self._extract_features(request)
        return self.model.predict_proba(features)[0][1]
```

---

## 12. Technology Stack Summary

| Layer | Technology | Version |
|-------|------------|---------|
| Runtime | Java | 21 (LTS) |
| Framework | Quarkus | 3.x |
| ORM | Hibernate Reactive | 2.x |
| Database | PostgreSQL | 15 |
| Message Broker | Kafka (Event Hubs) | 3.x |
| Search | Elasticsearch | 8.x |
| Cache | Redis | 7.x |
| Frontend | Angular | 17 |
| API | GraphQL + REST | - |
| **Fraud Detection** | **Python FastAPI** | **3.11** |
| Cloud | Azure | - |
| IaC | Terraform | 1.5+ |
| CI/CD | GitHub Actions | - |
