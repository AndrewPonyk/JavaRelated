# E-Commerce Platform - Technical Notes

## 1. CI/CD Pipeline Design

### 1.1 Pipeline Overview

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Commit    │───▶│    Lint     │───▶│    Test     │───▶│    Build    │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                                                                │
     ┌──────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  Deploy Dev │───▶│ Deploy Stg  │───▶│ Deploy Prod │
└─────────────┘    └─────────────┘    └─────────────┘
                         │                   │
                    (Auto)              (Manual Approval)
```

### 1.2 Pipeline Stages

| Stage | Tools | Duration Target | Failure Action |
|-------|-------|-----------------|----------------|
| **Lint** | ruff, eslint, prettier | < 2 min | Block merge |
| **Test Unit** | pytest, vitest | < 5 min | Block merge |
| **Test Integration** | pytest + testcontainers | < 10 min | Block merge |
| **Security Scan** | bandit, npm audit, trivy | < 5 min | Warning/Block |
| **Build** | Docker multi-stage | < 5 min | Block deploy |
| **Deploy Dev** | Terraform + ECS | < 10 min | Auto-rollback |
| **Deploy Staging** | Terraform + ECS | < 10 min | Auto-rollback |
| **Deploy Prod** | Terraform + ECS | < 15 min | Auto-rollback |

### 1.3 Branch Triggers

```yaml
# Trigger Configuration
main:
  - lint
  - test
  - build
  - deploy (dev → staging → prod)

feature/*:
  - lint
  - test

pull_request:
  - lint
  - test
  - security scan
```

---

## 2. Testing Strategy

### 2.1 Test Pyramid

```
              ┌───────┐
              │  E2E  │  (< 10 tests)
              │ Tests │  Critical user journeys
             ┌┴───────┴┐
             │Integr.  │  (50-100 tests)
             │ Tests   │  API contracts, DB operations
            ┌┴─────────┴┐
            │   Unit    │  (500+ tests)
            │   Tests   │  Business logic, utilities
            └───────────┘
```

### 2.2 Backend Testing (pytest)

**Coverage Target:** 80%+ for core business logic

```python
# Test organization
backend/tests/
├── conftest.py          # Shared fixtures
├── unit/
│   ├── test_models.py
│   ├── test_services.py
│   └── test_utils.py
├── integration/
│   ├── test_api_products.py
│   ├── test_api_cart.py
│   └── test_api_checkout.py
└── factories/
    ├── user_factory.py
    └── product_factory.py
```

**Key Fixtures:**

```python
# conftest.py
@pytest.fixture
def db_session():
    """Transactional database session that rolls back after each test."""
    pass

@pytest.fixture
def authenticated_client(db_session, user_factory):
    """API client with JWT authentication."""
    pass

@pytest.fixture
def redis_mock():
    """Mocked Redis for cache testing."""
    pass
```

### 2.3 Frontend Testing (Vitest + Testing Library)

**Coverage Target:** 70%+ for components

```typescript
// Test organization
frontend/tests/
├── setup.ts             // Test setup
├── components/
│   ├── ProductCard.test.tsx
│   └── CartDrawer.test.tsx
├── hooks/
│   └── useCart.test.ts
└── pages/
    └── Checkout.test.tsx
```

### 2.4 E2E Testing (Playwright)

**Critical Flows:**
1. User registration → login → browse → add to cart → checkout
2. Vendor product upload → approval → listing
3. Search → filter → purchase

---

## 3. Deployment Strategy

### 3.1 Container Strategy

```dockerfile
# Multi-stage build for minimal image size
FROM python:3.12-slim as builder
# Install dependencies, compile assets

FROM python:3.12-slim as runtime
# Copy only runtime artifacts
# ~150MB final image
```

### 3.2 ECS Deployment Model

```
┌─────────────────────────────────────────────────┐
│                  ECS Cluster                     │
├─────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐              │
│  │  Service:   │  │  Service:   │              │
│  │    Web      │  │   Worker    │              │
│  │  (3 tasks)  │  │  (2 tasks)  │              │
│  └─────────────┘  └─────────────┘              │
│                                                  │
│  ┌─────────────┐                               │
│  │  Service:   │                               │
│  │   Beat      │                               │
│  │  (1 task)   │                               │
│  └─────────────┘                               │
└─────────────────────────────────────────────────┘
```

### 3.3 Zero-Downtime Deployment

1. **Rolling Update:** New tasks start before old tasks stop
2. **Health Checks:** ALB verifies `/health` endpoint responds
3. **Connection Draining:** 30-second drain period
4. **Rollback:** Automatic on failed health checks

### 3.4 Database Migrations

```bash
# Safe migration process
1. Run backward-compatible migrations first
2. Deploy new application code
3. Run cleanup migrations (if any)
4. Never modify existing columns directly - add new, migrate, remove old
```

---

## 4. Environment Management

### 4.1 Configuration Hierarchy

```
Environment Variables (highest priority)
        ↓
.env.{environment} file
        ↓
Default values in settings (lowest priority)
```

### 4.2 Environment Template

See `.env.example` in project root for full template.

**Key Environment Variables:**

| Variable | Dev | Staging | Production |
|----------|-----|---------|------------|
| `DEBUG` | true | false | false |
| `LOG_LEVEL` | DEBUG | INFO | WARNING |
| `CACHE_TTL` | 60 | 300 | 300 |
| `CELERY_WORKERS` | 1 | 2 | 4 |
| `DB_POOL_SIZE` | 5 | 10 | 20 |

### 4.3 Secret Management

```
Production secrets → AWS Secrets Manager
                          ↓
                   ECS Task Definition
                          ↓
                   Environment Variables
```

**Never commit:**
- API keys
- Database passwords
- JWT secrets
- Encryption keys

---

## 5. Version Control Workflow

### 5.1 Recommended: GitHub Flow

```
main (protected)
  │
  ├── feature/add-cart-persistence
  │     └── (PR → Review → Merge)
  │
  ├── feature/elasticsearch-integration
  │     └── (PR → Review → Merge)
  │
  └── hotfix/fix-payment-bug
        └── (PR → Review → Merge → Deploy)
```

### 5.2 Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<ticket>-<description>` | `feature/SHOP-123-add-wishlist` |
| Bugfix | `bugfix/<ticket>-<description>` | `bugfix/SHOP-456-cart-total` |
| Hotfix | `hotfix/<description>` | `hotfix/fix-payment-timeout` |
| Release | `release/v<version>` | `release/v1.2.0` |

### 5.3 Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:** feat, fix, docs, style, refactor, test, chore

**Example:**
```
feat(cart): add persistent cart for guest users

- Store cart in Redis with 7-day TTL
- Merge guest cart on login
- Add cart restoration on session resume

Closes SHOP-123
```

### 5.4 Pull Request Checklist

- [ ] Code follows style guidelines (lint passes)
- [ ] Tests added/updated for changes
- [ ] Documentation updated if needed
- [ ] No secrets or sensitive data committed
- [ ] Database migrations are backward-compatible
- [ ] PR description explains the "why"

---

## 6. Common Pitfalls

### 6.1 Django + Celery Issues

| Pitfall | Solution |
|---------|----------|
| **Circular imports with tasks** | Import tasks inside functions, not at module level |
| **Long-running tasks blocking workers** | Use `soft_time_limit` and `time_limit` |
| **Lost tasks on deploy** | Use `acks_late=True` for critical tasks |
| **Database connections in workers** | Close connections after task: `django.db.close_old_connections()` |

```python
# Good: Task with proper timeouts
@celery_app.task(
    bind=True,
    max_retries=3,
    soft_time_limit=300,
    time_limit=600,
    acks_late=True
)
def process_order(self, order_id):
    try:
        # ... processing
    except SoftTimeLimitExceeded:
        # Cleanup and retry
        self.retry(countdown=60)
```

### 6.2 PostgreSQL Performance

| Pitfall | Solution |
|---------|----------|
| **N+1 queries** | Use `select_related()` and `prefetch_related()` |
| **Missing indexes** | Add indexes for frequently filtered columns |
| **Large table scans** | Implement pagination, avoid `count()` on large tables |
| **Lock contention** | Use `select_for_update(skip_locked=True)` |

### 6.3 Redis Cache Issues

| Pitfall | Solution |
|---------|----------|
| **Cache stampede** | Use probabilistic early expiration |
| **Memory exhaustion** | Set `maxmemory-policy` to `allkeys-lru` |
| **Stale data** | Implement cache invalidation on writes |
| **Serialization overhead** | Use `msgpack` instead of JSON for large objects |

### 6.4 Elasticsearch Challenges

| Pitfall | Solution |
|---------|----------|
| **Index mapping conflicts** | Define explicit mappings, don't rely on dynamic |
| **Slow indexing** | Use bulk operations, async indexing via Celery |
| **Relevance tuning** | Start simple, iterate based on user feedback |
| **Cluster memory** | Allocate 50% of RAM to ES heap (max 32GB) |

### 6.5 React + TypeScript

| Pitfall | Solution |
|---------|----------|
| **Prop drilling** | Use React Context or state management |
| **Unnecessary re-renders** | Use `useMemo`, `useCallback`, `React.memo` |
| **Type any abuse** | Enable `strict` mode in tsconfig |
| **Stale closures in hooks** | Include all deps in dependency arrays |

### 6.6 AWS ECS Deployment

| Pitfall | Solution |
|---------|----------|
| **Task OOM kills** | Set appropriate memory limits, monitor usage |
| **Slow deployments** | Reduce health check intervals, optimize image size |
| **Service discovery issues** | Use AWS Cloud Map or ALB-based routing |
| **Log loss** | Use `awslogs` driver with retention policy |

---

## 7. Performance Checklist

### 7.1 Backend

- [ ] Database queries optimized (no N+1)
- [ ] Indexes on filtered/sorted columns
- [ ] Connection pooling configured
- [ ] Expensive operations async (Celery)
- [ ] Redis caching for hot data
- [ ] Pagination on list endpoints
- [ ] Compression enabled (gzip/brotli)

### 7.2 Frontend

- [ ] Code splitting (lazy loading routes)
- [ ] Image optimization (WebP, lazy loading)
- [ ] Bundle size < 250KB (gzipped)
- [ ] Critical CSS inlined
- [ ] Service worker for caching
- [ ] Debounced search inputs

### 7.3 Infrastructure

- [ ] CDN for static assets
- [ ] Auto-scaling configured
- [ ] Database read replicas
- [ ] Redis cluster mode
- [ ] Health checks < 30s intervals

---

## 8. Monitoring & Alerting

### 8.1 Key Metrics

| Metric | Warning | Critical |
|--------|---------|----------|
| API Response Time (p95) | > 500ms | > 2s |
| Error Rate | > 1% | > 5% |
| CPU Usage | > 70% | > 90% |
| Memory Usage | > 75% | > 90% |
| Database Connections | > 80% pool | > 95% pool |
| Queue Depth | > 1000 | > 5000 |

### 8.2 Recommended Alerts

1. **Error spike:** Error rate > 5% for 5 minutes
2. **Latency degradation:** p95 > 2s for 5 minutes
3. **Resource exhaustion:** CPU/Memory > 90% for 10 minutes
4. **Task backlog:** Queue depth > 5000 for 15 minutes
5. **Database issues:** Connection failures, slow queries > 5s

---

## 9. Quick Reference

### 9.1 Local Development Commands

```bash
# Start all services
docker-compose -f docker-compose.dev.yml up

# Run backend tests
docker-compose exec backend pytest

# Run frontend tests
docker-compose exec frontend npm test

# Database migrations
docker-compose exec backend python manage.py migrate

# Celery worker (development)
docker-compose exec backend celery -A core worker -l DEBUG

# Access Django shell
docker-compose exec backend python manage.py shell_plus
```

### 9.2 Production Commands

```bash
# Deploy to staging
# Deployment is handled via GitHub Actions CI/CD
# See .github/workflows/cd-staging.yml

# View logs
aws logs tail /ecs/ecommerce-staging --follow

# Scale workers
aws ecs update-service --cluster ecommerce-prod \
  --service worker --desired-count 4

# Run management command
aws ecs run-task --cluster ecommerce-prod \
  --task-definition ecommerce-management \
  --overrides '{"containerOverrides":[{"name":"app","command":["python","manage.py","clearsessions"]}]}'
```
