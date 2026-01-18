# Enterprise Banking Platform - Technical Notes

## Table of Contents
1. [CI/CD Pipeline Design](#1-cicd-pipeline-design)
2. [Testing Strategy](#2-testing-strategy)
3. [Deployment Strategy](#3-deployment-strategy)
4. [Environment Management](#4-environment-management)
5. [Version Control Workflow](#5-version-control-workflow)
6. [Common Pitfalls](#6-common-pitfalls)

---

## 1. CI/CD Pipeline Design

### 1.1 Pipeline Architecture

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Commit    │───▶│    Build    │───▶│    Test     │───▶│   Quality   │───▶│   Deploy    │
│   Trigger   │    │   & Lint    │    │   Suite     │    │    Gate     │    │   Stage     │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                         │                  │                  │                  │
                    ┌────▼────┐        ┌────▼────┐        ┌────▼────┐        ┌────▼────┐
                    │ Compile │        │  Unit   │        │ SonarQube│       │   Dev   │
                    │  Java   │        │  Tests  │        │  Scan    │        │   Env   │
                    ├─────────┤        ├─────────┤        ├──────────┤        ├─────────┤
                    │  Build  │        │ Integr. │        │ Security │        │ Staging │
                    │ Frontend│        │  Tests  │        │  Scan    │        │   Env   │
                    ├─────────┤        ├─────────┤        ├──────────┤        ├─────────┤
                    │ Docker  │        │Contract │        │ Coverage │        │  Prod   │
                    │  Build  │        │  Tests  │        │  Check   │        │   Env   │
                    └─────────┘        └─────────┘        └──────────┘        └─────────┘
```

### 1.2 Jenkins Pipeline Stages

```groovy
// Jenkinsfile stages overview
stages {
    stage('Checkout') {
        // Clone repository
    }
    stage('Build') {
        parallel {
            stage('Backend') {
                // Maven build for all services
            }
            stage('Frontend') {
                // npm install && npm run build
            }
        }
    }
    stage('Test') {
        parallel {
            stage('Unit Tests') {
                // JUnit 5 + Jest tests
            }
            stage('Integration Tests') {
                // Testcontainers + WebTestClient
            }
        }
    }
    stage('Quality Gate') {
        parallel {
            stage('SonarQube') {
                // Code quality analysis
            }
            stage('Security Scan') {
                // OWASP dependency check
            }
        }
    }
    stage('Docker Build') {
        // Build and tag images
    }
    stage('Push to Registry') {
        // Push to ECR
    }
    stage('Deploy to Dev') {
        // Kubernetes deployment
    }
    stage('E2E Tests') {
        // Playwright tests
    }
    stage('Deploy to Staging') {
        when { branch 'release/*' }
    }
    stage('Deploy to Production') {
        when { branch 'main' }
        input { message "Deploy to production?" }
    }
}
```

### 1.3 Quality Gates

| Gate | Threshold | Action on Failure |
|------|-----------|-------------------|
| Unit Test Coverage | >= 80% | Block merge |
| SonarQube Quality | A rating | Block merge |
| Security Vulnerabilities | 0 Critical/High | Block merge |
| Integration Tests | 100% pass | Block deploy |
| Performance Baseline | < 10% regression | Warning |

### 1.4 Artifact Management

```yaml
# Artifacts produced per build
artifacts:
  - name: account-service
    type: docker-image
    registry: ECR
    tag: ${GIT_SHA}

  - name: frontend
    type: docker-image
    registry: ECR
    tag: ${GIT_SHA}

  - name: helm-charts
    type: helm
    repository: S3
    version: ${SEMVER}
```

---

## 2. Testing Strategy

### 2.1 Testing Pyramid

```
                    ┌─────────┐
                    │   E2E   │  < 5% (Playwright)
                   ┌┴─────────┴┐
                   │Integration│  15% (Testcontainers)
                  ┌┴───────────┴┐
                  │ Component   │  20% (WebTestClient)
                 ┌┴─────────────┴┐
                 │     Unit      │  60%+ (JUnit 5 / Jest)
                └─────────────────┘
```

### 2.2 Backend Testing

#### Unit Tests (JUnit 5 + Mockito)
```java
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldCreateAccountSuccessfully() {
        // Given
        CreateAccountCommand cmd = new CreateAccountCommand("John Doe", "USD");
        when(accountRepository.save(any())).thenReturn(Mono.just(mockAccount()));

        // When
        Mono<Account> result = accountService.createAccount(cmd);

        // Then
        StepVerifier.create(result)
            .assertNext(account -> {
                assertThat(account.getOwnerName()).isEqualTo("John Doe");
                assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            })
            .verifyComplete();
    }
}
```

#### Integration Tests (Testcontainers)
```java
@SpringBootTest
@Testcontainers
class AccountRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/testdb");
    }

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldPersistAndRetrieveAccount() {
        // Test actual database operations
    }
}
```

#### Reactive Testing (StepVerifier)
```java
@Test
void shouldHandleTransferWithFraudCheck() {
    Mono<TransferResult> result = transactionService.transfer(transferRequest);

    StepVerifier.create(result)
        .expectNextMatches(r -> r.getStatus() == TransferStatus.COMPLETED)
        .expectComplete()
        .verify(Duration.ofSeconds(5));
}
```

### 2.3 Frontend Testing

#### Component Tests (React Testing Library)
```typescript
import { render, screen, waitFor } from '@testing-library/react';
import { AccountDashboard } from './AccountDashboard';

describe('AccountDashboard', () => {
  it('should display account balance', async () => {
    render(<AccountDashboard accountId="ACC-123" />);

    await waitFor(() => {
      expect(screen.getByText('$1,000.00')).toBeInTheDocument();
    });
  });

  it('should show loading state', () => {
    render(<AccountDashboard accountId="ACC-123" />);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });
});
```

### 2.4 Contract Testing (Spring Cloud Contract)
```yaml
# contracts/transfer-api.yml
description: Fund transfer endpoint
request:
  method: POST
  url: /api/v1/transfers
  headers:
    Content-Type: application/json
  body:
    sourceAccountId: "ACC-001"
    targetAccountId: "ACC-002"
    amount: 100.00
    currency: "USD"
response:
  status: 200
  body:
    transferId: $(regex('[A-Z]{3}-[0-9]{6}'))
    status: "COMPLETED"
```

### 2.5 Performance Testing (Gatling)
```scala
class TransferSimulation extends Simulation {
  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .header("Authorization", "Bearer ${token}")

  val transferScenario = scenario("Fund Transfer")
    .exec(
      http("Create Transfer")
        .post("/api/v1/transfers")
        .body(StringBody("""{"sourceAccountId":"ACC-001","targetAccountId":"ACC-002","amount":100}"""))
        .check(status.is(200))
    )

  setUp(
    transferScenario.inject(
      rampUsers(100).during(60.seconds),
      constantUsersPerSec(50).during(5.minutes)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(500),
     global.successfulRequests.percent.gt(99)
   )
}
```

---

## 3. Deployment Strategy

### 3.1 Containerization

#### Multi-stage Dockerfile (Backend)
```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

# GraalVM Native Build (optional)
FROM ghcr.io/graalvm/native-image:21 AS native-builder
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN native-image -jar app.jar -o app-native

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Frontend Dockerfile
```dockerfile
# Build stage
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 3.2 Kubernetes Deployment Strategy

#### Blue-Green Deployment
```yaml
# Blue deployment (current)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: account-service-blue
  labels:
    app: account-service
    version: blue
spec:
  replicas: 3
  selector:
    matchLabels:
      app: account-service
      version: blue
---
# Green deployment (new)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: account-service-green
  labels:
    app: account-service
    version: green
spec:
  replicas: 3
  selector:
    matchLabels:
      app: account-service
      version: green
---
# Service switches between blue/green
apiVersion: v1
kind: Service
metadata:
  name: account-service
spec:
  selector:
    app: account-service
    version: blue  # Switch to green after validation
```

### 3.3 Helm Chart Structure
```
helm-charts/
├── account-service/
│   ├── Chart.yaml
│   ├── values.yaml
│   ├── values-dev.yaml
│   ├── values-staging.yaml
│   ├── values-prod.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── ingress.yaml
│       ├── configmap.yaml
│       ├── secret.yaml
│       └── hpa.yaml
```

### 3.4 AWS EKS Infrastructure
```hcl
# Terraform EKS Module
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"

  cluster_name    = "banking-platform-${var.environment}"
  cluster_version = "1.28"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  eks_managed_node_groups = {
    main = {
      min_size     = 3
      max_size     = 10
      desired_size = 5

      instance_types = ["m5.xlarge"]
      capacity_type  = "ON_DEMAND"
    }
  }
}
```

---

## 4. Environment Management

### 4.1 Environment Configuration

#### `.env.example`
```bash
# Application
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=banking
DB_USERNAME=banking_user
DB_PASSWORD=change_me_in_production

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_CONSUMER_GROUP=banking-consumer

# Security
JWT_SECRET=your-256-bit-secret-key-change-in-production
JWT_EXPIRATION=3600
OAUTH2_CLIENT_ID=banking-client
OAUTH2_CLIENT_SECRET=change_me

# AWS (for production)
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# Monitoring
GRAFANA_URL=http://localhost:3000
PROMETHEUS_URL=http://localhost:9090

# Feature Flags
FEATURE_ML_FRAUD_DETECTION=true
FEATURE_INSTANT_TRANSFERS=false
```

### 4.2 Spring Profiles

```yaml
# application.yml (common)
spring:
  application:
    name: account-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/banking_dev
    username: dev_user
    password: dev_password
  kafka:
    bootstrap-servers: localhost:9092

logging:
  level:
    com.bank: DEBUG
    org.springframework.r2dbc: DEBUG

---
# application-staging.yml
spring:
  config:
    activate:
      on-profile: staging
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:5432/banking_staging
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:5432/banking_prod
    pool:
      initial-size: 10
      max-size: 50
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

logging:
  level:
    root: WARN
    com.bank: INFO
```

### 4.3 Secrets Management

```yaml
# Kubernetes Secret (encrypted at rest)
apiVersion: v1
kind: Secret
metadata:
  name: banking-secrets
type: Opaque
stringData:
  db-password: ${DB_PASSWORD}
  jwt-secret: ${JWT_SECRET}
  redis-password: ${REDIS_PASSWORD}

---
# ExternalSecret (AWS Secrets Manager)
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: banking-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: banking-secrets
  data:
    - secretKey: db-password
      remoteRef:
        key: banking/prod/database
        property: password
```

---

## 5. Version Control Workflow

### 5.1 Branching Strategy: GitHub Flow with Release Branches

```
main (protected)
│
├── feature/ACC-123-add-transfer-limits
│   └── (PR → main)
│
├── feature/LOAN-456-credit-scoring
│   └── (PR → main)
│
├── bugfix/TXN-789-fix-race-condition
│   └── (PR → main)
│
└── release/v1.2.0
    └── (hotfix → cherry-pick to main)
```

### 5.2 Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/{ticket}-{description}` | `feature/ACC-123-add-transfer-limits` |
| Bugfix | `bugfix/{ticket}-{description}` | `bugfix/TXN-456-fix-timeout` |
| Hotfix | `hotfix/{ticket}-{description}` | `hotfix/SEC-001-patch-vulnerability` |
| Release | `release/v{major}.{minor}.{patch}` | `release/v1.2.0` |

### 5.3 Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code restructuring
- `docs`: Documentation
- `test`: Tests
- `chore`: Build/tooling

**Example:**
```
feat(transaction): add real-time fraud scoring

Integrate CatBoost ML model for transaction risk scoring.
Transactions above 0.7 risk score are flagged for review.

Closes #123
```

### 5.4 PR Requirements

- [ ] All CI checks passing
- [ ] At least 1 approval from code owner
- [ ] No merge conflicts
- [ ] Linked to issue/ticket
- [ ] Documentation updated (if applicable)
- [ ] Test coverage maintained

---

## 6. Common Pitfalls

### 6.1 Reactive Programming Pitfalls

#### Problem: Blocking calls in reactive chain
```java
// BAD - Blocks the event loop
Mono<Account> getAccount(String id) {
    Account account = jdbcTemplate.queryForObject(...); // BLOCKING!
    return Mono.just(account);
}

// GOOD - Fully reactive
Mono<Account> getAccount(String id) {
    return accountRepository.findById(id); // R2DBC non-blocking
}
```

#### Problem: Not subscribing to Mono/Flux
```java
// BAD - Nothing happens!
accountService.createAccount(cmd);

// GOOD - Subscribe or return to caller
accountService.createAccount(cmd).subscribe();
// or
return accountService.createAccount(cmd);
```

#### Problem: Losing context in async boundaries
```java
// BAD - MDC context lost
return accountService.getAccount(id)
    .map(account -> {
        log.info("Processing"); // No trace ID!
        return account;
    });

// GOOD - Use context propagation
return accountService.getAccount(id)
    .contextWrite(Context.of("traceId", traceId))
    .doOnNext(account -> log.info("Processing")); // Has context
```

### 6.2 Event Sourcing Pitfalls

#### Problem: Unbounded event streams
```java
// BAD - Loading all events for replay
List<Event> allEvents = eventStore.getAllEvents(accountId); // OOM for old accounts!

// GOOD - Use snapshots
Snapshot snapshot = snapshotStore.getLatest(accountId);
List<Event> recentEvents = eventStore.getEventsSince(accountId, snapshot.getVersion());
Account account = Account.reconstitute(snapshot, recentEvents);
```

#### Problem: Schema evolution issues
```java
// BAD - Breaking change
record AccountCreated(String accountId, String name) {} // Missing currency!

// GOOD - Versioned events with defaults
record AccountCreatedV2(
    String accountId,
    String name,
    String currency // Added in V2
) {
    // Handle V1 events
    static AccountCreatedV2 fromV1(AccountCreated v1) {
        return new AccountCreatedV2(v1.accountId(), v1.name(), "USD");
    }
}
```

### 6.3 CQRS Pitfalls

#### Problem: Inconsistent read after write
```java
// BAD - Immediate read after write fails
transferService.initiateTransfer(cmd).block();
Account account = queryService.getBalance(accountId).block(); // Stale!

// GOOD - Accept eventual consistency or use command result
TransferResult result = transferService.initiateTransfer(cmd).block();
// Use result.getNewBalance() instead of querying
```

### 6.4 R2DBC / Database Pitfalls

#### Problem: N+1 queries in reactive
```java
// BAD - N+1 problem
return accountRepository.findAll()
    .flatMap(account ->
        transactionRepository.findByAccountId(account.getId()) // N queries!
    );

// GOOD - Batch fetch
return accountRepository.findAllWithTransactions(); // Single JOIN query
```

#### Problem: Connection pool exhaustion
```yaml
# BAD - Default pool too small
spring:
  r2dbc:
    pool:
      max-size: 10  # Too small for high traffic

# GOOD - Sized for load
spring:
  r2dbc:
    pool:
      initial-size: 20
      max-size: 100
      max-idle-time: 30m
```

### 6.5 Kubernetes / Deployment Pitfalls

#### Problem: Missing health checks
```yaml
# BAD - No probes
spec:
  containers:
    - name: account-service
      image: account-service:latest

# GOOD - Proper probes
spec:
  containers:
    - name: account-service
      image: account-service:latest
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 30
        periodSeconds: 10
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 5
        periodSeconds: 5
```

#### Problem: Resource limits not set
```yaml
# BAD - No limits (can consume all node resources)
containers:
  - name: account-service

# GOOD - Proper limits
containers:
  - name: account-service
    resources:
      requests:
        memory: "512Mi"
        cpu: "250m"
      limits:
        memory: "1Gi"
        cpu: "1000m"
```

### 6.6 GraalVM Native Pitfalls

#### Problem: Reflection not configured
```java
// Fails at runtime in native image
ObjectMapper mapper = new ObjectMapper();
Account account = mapper.readValue(json, Account.class); // ClassNotFoundException!

// Fix: Add reflect-config.json
[
  {
    "name": "com.bank.account.model.Account",
    "allDeclaredConstructors": true,
    "allDeclaredFields": true,
    "allDeclaredMethods": true
  }
]
```

#### Problem: Dynamic proxies
```java
// Spring Data repositories need proxy hints
// Add to native-image.properties
Args = --initialize-at-build-time=org.springframework.data.repository
```

### 6.7 Security Pitfalls

#### Problem: Logging sensitive data
```java
// BAD - PII in logs
log.info("Processing transfer for account: {}", account.toString());

// GOOD - Masked logging
log.info("Processing transfer for account: {}", account.getMaskedId());
```

#### Problem: Hardcoded secrets
```java
// BAD - Secret in code
String jwtSecret = "my-secret-key-12345";

// GOOD - External configuration
@Value("${jwt.secret}")
private String jwtSecret;
```

---

## Quick Reference Commands

```bash
# Build all services
./mvnw clean package -DskipTests

# Run with Docker Compose
docker-compose -f docker-compose.dev.yml up -d

# Run specific service tests
./mvnw test -pl services/account-service

# Generate native image
./mvnw -Pnative native:compile

# Kubernetes deployment
kubectl apply -k infrastructure/kubernetes/overlays/dev

# View logs
kubectl logs -f deployment/account-service -n banking

# Port forward for debugging
kubectl port-forward svc/account-service 8080:8080 -n banking
```

---

*Document Version: 1.0*
*Last Updated: 2026-01-17*
