# Healthcare Claims Processing - Technical Notes

## 1. CI/CD Pipeline Design

### 1.1 Pipeline Overview

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Commit    │───▶│    Lint     │───▶│    Test     │───▶│    Build    │───▶│   Deploy    │
│             │    │  & Format   │    │             │    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                         │                  │                  │                  │
                         ▼                  ▼                  ▼                  ▼
                   - Checkstyle       - Unit Tests      - Native Build     - Dev (auto)
                   - SpotBugs         - Integration     - Container Image  - Staging (manual)
                   - ESLint           - Contract Tests  - Helm Chart       - Prod (approval)
                   - Prettier         - Coverage        - SBOM
```

### 1.2 GitHub Actions Stages

| Stage | Trigger | Actions | Duration Target |
|-------|---------|---------|-----------------|
| **Lint** | Every push | Checkstyle, SpotBugs, ESLint | < 2 min |
| **Unit Test** | Every push | JUnit 5 + Jest | < 5 min |
| **Integration Test** | PR to main | TestContainers | < 10 min |
| **Build** | Merge to main | Native build, Docker | < 15 min |
| **Deploy Dev** | Merge to main | Auto-deploy | < 5 min |
| **Deploy Staging** | Manual trigger | Blue-green | < 10 min |
| **Deploy Prod** | Approval required | Canary rollout | < 15 min |

### 1.3 Quality Gates

```yaml
quality_gates:
  code_coverage:
    minimum: 80%
    target: 90%

  static_analysis:
    blocker_bugs: 0
    critical_bugs: 0
    security_vulnerabilities: 0

  performance:
    cold_start_p99: 100ms
    api_response_p99: 200ms

  security:
    owasp_dependency_check: no_high_vulnerabilities
    container_scan: no_critical
```

---

## 2. Testing Strategy

### 2.1 Testing Pyramid

```
                    ┌───────────────┐
                    │     E2E       │  ← 10% - Critical paths only
                    │   (Cypress)   │
                    └───────────────┘
               ┌─────────────────────────┐
               │      Integration        │  ← 20% - API contracts, DB
               │    (TestContainers)     │
               └─────────────────────────┘
          ┌───────────────────────────────────┐
          │            Unit Tests             │  ← 70% - Business logic
          │        (JUnit 5, Mockito)         │
          └───────────────────────────────────┘
```

### 2.2 Backend Testing

#### Unit Testing (JUnit 5 + Mockito)

```java
// Target: 80%+ coverage on domain/service layer
@QuarkusTest
class ClaimServiceTest {

    @InjectMock
    ClaimRepository claimRepository;

    @Inject
    ClaimService claimService;

    @Test
    void shouldSubmitValidClaim() {
        // Given
        ClaimRequest request = createValidClaimRequest();
        when(claimRepository.persist(any())).thenReturn(Uni.createFrom().item(savedClaim));

        // When
        Claim result = claimService.submit(request).await().indefinitely();

        // Then
        assertThat(result.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    }
}
```

#### Integration Testing (TestContainers)

```java
@QuarkusIntegrationTest
@Testcontainers
class ClaimIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("claims_test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Test
    void shouldProcessClaimEndToEnd() {
        // Full integration test with real DB and Kafka
    }
}
```

### 2.3 Frontend Testing

#### Component Testing (Jest + Angular Testing Library)

```typescript
describe('ClaimListComponent', () => {
  it('should display claims from API', async () => {
    const mockClaims = [{ id: '1', claimNumber: 'CLM-001', status: 'PENDING' }];
    const claimService = TestBed.inject(ClaimService);
    jest.spyOn(claimService, 'getClaims').mockReturnValue(of(mockClaims));

    const { getByText } = await render(ClaimListComponent);

    expect(getByText('CLM-001')).toBeInTheDocument();
  });
});
```

#### E2E Testing (Cypress)

```typescript
describe('Claims Workflow', () => {
  it('should submit and process a claim', () => {
    cy.login('claims-processor@example.com');
    cy.visit('/claims/new');
    cy.fillClaimForm({ amount: 1500, type: 'MEDICAL' });
    cy.get('[data-testid="submit-claim"]').click();
    cy.url().should('include', '/claims/');
    cy.get('[data-testid="claim-status"]').should('contain', 'SUBMITTED');
  });
});
```

### 2.4 Coverage Targets

| Layer | Target | Tool |
|-------|--------|------|
| Domain Services | 90% | JaCoCo |
| REST Controllers | 80% | JaCoCo |
| GraphQL Resolvers | 80% | JaCoCo |
| Angular Components | 80% | Jest |
| Angular Services | 90% | Jest |
| E2E Critical Paths | 100% | Cypress |

---

## 3. Deployment Strategy

### 3.1 Container Strategy

```dockerfile
# Multi-stage build for Quarkus native
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:23.1-java21 AS build
COPY --chown=quarkus:quarkus . /code
WORKDIR /code
RUN ./mvnw package -Pnative -DskipTests

FROM quay.io/quarkus/quarkus-micro-image:2.0
COPY --from=build /code/target/*-runner /application
EXPOSE 8080
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

### 3.2 Azure Functions Deployment

```yaml
deployment:
  type: azure-functions
  runtime: custom
  plan: consumption  # For serverless

  scaling:
    min_instances: 0
    max_instances: 100

  cold_start_optimization:
    - Native compilation (GraalVM)
    - Minimal dependencies
    - Pre-warming enabled
```

### 3.3 Deployment Environments

```
┌─────────────────────────────────────────────────────────────────┐
│                     Development                                  │
│  - Auto-deploy on merge to main                                 │
│  - Single instance                                              │
│  - Shared Kafka (dev namespace)                                 │
│  - PostgreSQL (dev tier)                                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Staging                                     │
│  - Manual trigger with approval                                 │
│  - Production-like config                                       │
│  - Dedicated Kafka cluster                                      │
│  - PostgreSQL (production tier, smaller)                        │
│  - Full integration testing                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Production                                   │
│  - Requires 2 approvals                                         │
│  - Blue-green deployment                                        │
│  - Canary rollout (10% → 50% → 100%)                           │
│  - Multi-AZ deployment                                          │
│  - Geo-redundant database                                       │
└─────────────────────────────────────────────────────────────────┘
```

### 3.4 Rollback Strategy

```yaml
rollback:
  automatic:
    triggers:
      - error_rate > 5%
      - latency_p99 > 500ms
      - health_check_failures > 3
    action: immediate_rollback

  manual:
    retention: 5 previous versions
    command: "az functionapp deployment slot swap --rollback"
```

---

## 4. Environment Management

### 4.1 Configuration Hierarchy

```
application.properties          ← Defaults
  └── application-{profile}.properties   ← Profile overrides
        └── Environment Variables        ← Runtime overrides (12-factor)
              └── Azure Key Vault        ← Secrets
```

### 4.2 Environment Variables Template

```bash
# .env.example

# Application
QUARKUS_PROFILE=dev
QUARKUS_HTTP_PORT=8080
QUARKUS_LOG_LEVEL=INFO

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=claims
DB_USERNAME=claims_user
# DB_PASSWORD - Use Azure Key Vault in production

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_SECURITY_PROTOCOL=PLAINTEXT
# KAFKA_SASL_JAAS_CONFIG - Use Azure Key Vault in production

# Elasticsearch
ES_HOSTS=http://localhost:9200
ES_INDEX_PREFIX=claims

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# External APIs
FRAUD_API_URL=https://fraud-api.example.com
# FRAUD_API_KEY - Use Azure Key Vault in production

# Azure
AZURE_TENANT_ID=your-tenant-id
AZURE_CLIENT_ID=your-client-id
# AZURE_CLIENT_SECRET - Use Azure Key Vault in production
AZURE_KEYVAULT_URL=https://your-vault.vault.azure.net/

# Feature Flags
FEATURE_ML_FRAUD_DETECTION=true
FEATURE_ASYNC_ADJUDICATION=true

# Observability
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
OTEL_SERVICE_NAME=claims-service
```

### 4.3 Secret Management

| Secret Type | Storage | Access Method |
|-------------|---------|---------------|
| Database credentials | Azure Key Vault | Managed Identity |
| API keys | Azure Key Vault | Managed Identity |
| Kafka credentials | Azure Key Vault | Managed Identity |
| JWT signing keys | Azure Key Vault | Managed Identity |
| TLS certificates | Azure Key Vault | Auto-rotation |

---

## 5. Version Control Workflow

### 5.1 Branching Strategy: GitHub Flow (Simplified)

```
main ─────────────────────────────────────────────────────────────▶
       │           │           │           │
       │  feature/ │  feature/ │  bugfix/  │
       │  claim-   │  fraud-   │  fix-     │
       │  submit   │  score    │  timeout  │
       │     │     │     │     │     │     │
       │     ▼     │     ▼     │     ▼     │
       └─────┴─────┴─────┴─────┴─────┴─────┘
              PR         PR         PR
```

### 5.2 Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/short-description` | `feature/claim-submission` |
| Bug fix | `bugfix/issue-number-desc` | `bugfix/123-timeout-error` |
| Hotfix | `hotfix/critical-desc` | `hotfix/security-patch` |
| Release | `release/version` | `release/1.2.0` |

### 5.3 Commit Message Convention

```
<type>(<scope>): <subject>

<body>

<footer>

Types: feat, fix, docs, style, refactor, test, chore
Scope: claims, fraud, api, frontend, infra

Example:
feat(claims): add auto-adjudication for simple claims

Implement rules engine for automatic processing of claims
under $500 with standard procedure codes.

Closes #123
```

### 5.4 Pull Request Requirements

- [ ] All CI checks pass
- [ ] Code coverage maintained/improved
- [ ] At least 1 approval for features
- [ ] At least 2 approvals for production deployments
- [ ] No unresolved conversations
- [ ] Linked to issue/ticket

---

## 6. Common Pitfalls

### 6.1 Hibernate Reactive Gotchas

| Pitfall | Description | Solution |
|---------|-------------|----------|
| **Blocking calls** | Mixing blocking and reactive code | Use `Uni.runOnWorkerPool()` for blocking operations |
| **N+1 queries** | Lazy loading in reactive context | Use `@Fetch(FetchType.EAGER)` or explicit joins |
| **Transaction scope** | Transactions don't span async boundaries | Use `@WithTransaction` annotation properly |
| **Connection pool exhaustion** | Too many concurrent operations | Configure pool size, use bulkhead pattern |

```java
// BAD - Blocking in reactive chain
public Uni<Claim> processClaim(UUID id) {
    return claimRepository.findById(id)
        .map(claim -> {
            externalApi.blockingCall(); // DON'T DO THIS
            return claim;
        });
}

// GOOD - Run blocking on worker pool
public Uni<Claim> processClaim(UUID id) {
    return claimRepository.findById(id)
        .flatMap(claim ->
            Uni.createFrom().item(() -> externalApi.blockingCall())
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .map(result -> claim)
        );
}
```

### 6.2 Kafka Common Issues

| Pitfall | Description | Solution |
|---------|-------------|----------|
| **Message ordering** | Messages processed out of order | Use partition keys based on claim ID |
| **Duplicate processing** | At-least-once delivery causes duplicates | Implement idempotent consumers |
| **Consumer lag** | Processing falls behind | Add consumer instances, tune batch size |
| **Poison messages** | Bad messages block consumer | Dead letter queue, error handling |

```java
// Idempotent consumer pattern
@Incoming("claims")
@Transactional
public Uni<Void> processClaimEvent(ClaimEvent event) {
    return processedEventRepository.existsById(event.getId())
        .flatMap(exists -> {
            if (exists) {
                log.info("Skipping duplicate event: {}", event.getId());
                return Uni.createFrom().voidItem();
            }
            return processEvent(event)
                .flatMap(v -> processedEventRepository.persist(new ProcessedEvent(event.getId())));
        });
}
```

### 6.3 GraphQL N+1 Problem

```java
// BAD - N+1 queries
@QueryMapping
public Uni<List<Claim>> claims() {
    return claimRepository.findAll()
        .map(claims -> claims.stream()
            .map(c -> {
                c.setPatient(patientRepository.findById(c.getPatientId()).await()); // N queries!
                return c;
            })
            .toList());
}

// GOOD - Batch loading with DataLoader
@QueryMapping
public Uni<List<Claim>> claims(@DataLoaderRegistry DataLoaderRegistry registry) {
    return claimRepository.findAll()
        .flatMap(claims -> {
            DataLoader<UUID, Patient> patientLoader = registry.getDataLoader("patientLoader");
            return Multi.createFrom().iterable(claims)
                .flatMap(claim ->
                    Uni.createFrom().completionStage(patientLoader.load(claim.getPatientId()))
                        .map(patient -> { claim.setPatient(patient); return claim; })
                )
                .collect().asList();
        });
}
```

### 6.4 Azure Functions Cold Start

| Issue | Impact | Mitigation |
|-------|--------|------------|
| JVM startup | 2-5 seconds | Use GraalVM native image |
| Class loading | 500ms-1s | Minimize dependencies |
| Connection init | 200-500ms | Connection pooling, keep-alive |
| First request | Variable | Pre-warming, premium plan |

```properties
# application.properties optimizations for cold start
quarkus.package.type=native
quarkus.native.container-build=true
quarkus.native.additional-build-args=-H:+StaticExecutableWithDynamicLibC

# Minimize startup
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.sql-load-script=no-file
```

### 6.5 Security Pitfalls

| Pitfall | Risk | Prevention |
|---------|------|------------|
| **PHI in logs** | HIPAA violation | Configure log masking |
| **SQL injection** | Data breach | Use parameterized queries (Hibernate handles) |
| **JWT validation** | Unauthorized access | Validate issuer, audience, expiry |
| **Secrets in code** | Credential exposure | Use Azure Key Vault, never commit secrets |

```java
// Log masking for PHI
@Slf4j
public class ClaimService {
    public Uni<Claim> processClaim(Claim claim) {
        // NEVER log PHI directly
        log.info("Processing claim: {}", claim.getClaimNumber()); // OK
        // log.info("Patient: {}", claim.getPatient()); // BAD - contains PHI

        return processInternal(claim);
    }
}
```

---

## 7. Performance Optimization Tips

### 7.1 Database Query Optimization

```java
// Use projections for read-only queries
public interface ClaimSummaryProjection {
    UUID getId();
    String getClaimNumber();
    ClaimStatus getStatus();
    BigDecimal getAmount();
}

// Index recommendations
// CREATE INDEX idx_claims_status ON claims(status);
// CREATE INDEX idx_claims_patient_id ON claims(patient_id);
// CREATE INDEX idx_claims_created_at ON claims(created_at DESC);
```

### 7.2 Caching Strategy

```java
@ApplicationScoped
public class ProviderService {

    @CacheResult(cacheName = "providers")
    public Uni<Provider> getProvider(UUID id) {
        return providerRepository.findById(id);
    }

    @CacheInvalidate(cacheName = "providers")
    public Uni<Provider> updateProvider(UUID id, Provider provider) {
        return providerRepository.update(provider);
    }
}

// application.properties
quarkus.cache.caffeine."providers".expire-after-write=1H
quarkus.cache.caffeine."providers".maximum-size=1000
```

### 7.3 Async Processing Patterns

```java
// Fan-out processing for independent operations
public Uni<ClaimProcessingResult> processClaimFully(Claim claim) {
    return Uni.combine().all().unis(
        adjudicationService.evaluate(claim),
        fraudService.score(claim),
        notificationService.notifySubmission(claim)
    ).asTuple()
    .map(tuple -> new ClaimProcessingResult(
        tuple.getItem1(), // adjudication result
        tuple.getItem2(), // fraud score
        tuple.getItem3()  // notification status
    ));
}
```

---

## 8. Monitoring & Alerting

### 8.1 Key Metrics

| Metric | Alert Threshold | Dashboard |
|--------|-----------------|-----------|
| API latency p99 | > 500ms | Response Times |
| Error rate | > 1% | Error Tracking |
| Claims processed/min | < 100 | Throughput |
| Kafka consumer lag | > 10000 | Event Processing |
| Database connections | > 80% pool | Infrastructure |
| Fraud detection latency | > 2s | ML Integration |

### 8.2 Health Check Endpoints

```java
@Liveness
@ApplicationScoped
public class LivenessCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("alive");
    }
}

@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {
    @Inject
    PgPool pgPool;

    @Override
    public HealthCheckResponse call() {
        return pgPool.query("SELECT 1").execute()
            .map(r -> HealthCheckResponse.up("database"))
            .onFailure().recoverWithItem(
                HealthCheckResponse.down("database")
            )
            .await().indefinitely();
    }
}
```

---

## 9. Development Setup

### 9.1 Prerequisites

```bash
# Required tools
java --version          # Java 21+
mvn --version          # Maven 3.9+
node --version         # Node.js 20+
docker --version       # Docker 24+
az --version           # Azure CLI 2.50+
terraform --version    # Terraform 1.5+
```

### 9.2 Local Development

```bash
# Start infrastructure
docker-compose -f infrastructure/docker/docker-compose.dev.yml up -d

# Backend (Quarkus dev mode with live reload)
cd backend
./mvnw quarkus:dev

# Frontend (Angular dev server)
cd frontend
npm install
npm start

# Run tests
./mvnw test                    # Unit tests
./mvnw verify                  # Integration tests
npm test                       # Frontend tests
```

### 9.3 IDE Setup

**IntelliJ IDEA:**
- Install Quarkus Tools plugin
- Enable annotation processing
- Configure code style from `.editorconfig`

**VS Code:**
- Install Angular Language Service
- Install Quarkus extension
- Install ESLint, Prettier extensions

---

## 10. Fraud Detection Service

### 10.1 Overview

The Fraud Detection Service is a standalone Python FastAPI microservice that provides ML-powered fraud scoring. It's designed to be easily extended with real ML models.

### 10.2 Running Locally

```bash
cd fraud-detection-service

# Install dependencies
pip install -r requirements.txt

# Run the server
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload

# Or with Docker
docker build -t fraud-detection-service .
docker run -p 8090:8090 fraud-detection-service
```

### 10.3 API Usage

```bash
# Score a claim
curl -X POST http://localhost:8090/api/v1/score \
  -H "Content-Type: application/json" \
  -d '{
    "claimId": "CLM-001",
    "claimType": "MEDICAL",
    "amount": 15000.00,
    "serviceDate": "2024-01-15",
    "patientId": "patient-123",
    "providerId": "provider-456",
    "diagnosisCodes": "M79.3",
    "procedureCodes": "99215"
  }'

# Response
{
  "claimId": "CLM-001",
  "score": 0.45,
  "riskLevel": "MEDIUM",
  "riskFactors": [
    {
      "factor": "very_high_amount",
      "weight": 0.20,
      "description": "Very high claim amount ($15,000.00)"
    }
  ],
  "recommendation": "REVIEW - Flag for secondary review",
  "modelVersion": "1.0.0"
}
```

### 10.4 Scoring Logic

The current implementation uses heuristic rules:

| Factor | Detection | Weight |
|--------|-----------|--------|
| **Amount** | $5k+, $15k+, $50k+ | 0.10 - 0.30 |
| **Service Date** | Weekends, future dates, >90 days old | 0.05 - 0.40 |
| **Procedures** | High-risk CPT codes (99215, 99223, etc.) | 0.05 - 0.10 |
| **Diagnosis** | Vague ICD-10 codes (M79, R51, R10) | 0.04 - 0.08 |
| **Claim Type** | DME, Rehabilitation, Inpatient | 0.05 - 0.08 |
| **Round Amounts** | Exact hundreds/thousands | 0.04 - 0.08 |

### 10.5 Extending with Real ML

To add a trained ML model:

```python
# fraud-detection-service/app/scoring.py
import joblib
import numpy as np

class FraudScoringEngine:
    def __init__(self):
        # Load pre-trained model
        self.model = joblib.load("models/fraud_classifier.pkl")
        self.scaler = joblib.load("models/feature_scaler.pkl")

    def score(self, request):
        # Extract features
        features = self._extract_features(request)

        # Scale features
        scaled = self.scaler.transform([features])

        # Get prediction probability
        score = self.model.predict_proba(scaled)[0][1]

        return score, self._explain(features, score)

    def _extract_features(self, request):
        return [
            request.amount,
            self._day_of_week(request.serviceDate),
            self._is_weekend(request.serviceDate),
            self._claim_type_risk(request.claimType),
            self._procedure_risk(request.procedureCodes),
            self._diagnosis_risk(request.diagnosisCodes),
        ]
```

### 10.6 Integration with Backend

The Quarkus backend integrates via `FraudScoringClient`:

```java
// backend/src/main/java/com/healthcare/claims/infrastructure/fraud/FraudScoringClient.java
@ApplicationScoped
public class FraudScoringClient {

    @ConfigProperty(name = "fraud.api.url", defaultValue = "http://localhost:8090")
    String fraudApiUrl;

    public Uni<Double> getScore(Claim claim) {
        return Uni.createFrom().item(() -> {
            // POST to /api/v1/score
            Response response = client.target(fraudApiUrl)
                .path("/api/v1/score")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(buildRequest(claim)));

            Map<String, Object> result = response.readEntity(Map.class);
            return ((Number) result.get("score")).doubleValue();
        });
    }
}
```

### 10.7 Docker Compose Integration

The fraud detection service is included in both docker-compose files:

```yaml
# infrastructure/docker/docker-compose.yml
fraud-detection:
  build:
    context: ../../fraud-detection-service
    dockerfile: Dockerfile
  ports:
    - "8090:8090"
  healthcheck:
    test: ["CMD", "python", "-c", "import httpx; httpx.get('http://localhost:8090/health').raise_for_status()"]
```

### 10.8 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | 8090 | Server port |
| `LOG_LEVEL` | INFO | Logging level |
| `MODEL_PATH` | - | Path to ML model file (optional) |
