# E-Commerce Platform - Technical Notes

## 1. CI/CD Pipeline Design

### 1.1 Pipeline Stages

```mermaid
graph LR
    A[Push/PR] --> B[Lint & Format Check]
    B --> C[Security Scan]
    C --> D[Unit Tests]
    D --> E[Integration Tests]
    E --> F[Build Docker Images]
    F --> G[Push to ECR]
    G --> H{Branch}
    H -->|develop| I[Deploy to Dev]
    H -->|staging| J[Deploy to Staging]
    H -->|main| K[Deploy to Production]

    style A fill:#e1f5fe
    style I fill:#fff9c4
    style J fill:#fff3e0
    style K fill:#ffebee
```

### 1.2 GitHub Actions Workflow Structure

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on:
  push:
    branches: ['**']
  pull_request:
    branches: [main, develop, staging]

jobs:
  # Stage 1: Code Quality
  lint:
    runs-on: ubuntu-latest
    steps:
      - name: Lint Backend (Python)
        run: |
          ruff check backend/
          black --check backend/
          mypy backend/
      - name: Lint Frontend (TypeScript)
        run: |
          npm run lint
          npm run type-check

  # Stage 2: Security
  security:
    needs: lint
    steps:
      - name: Dependency Scan
        run: |
          pip-audit backend/
          npm audit
      - name: SAST Scan
        uses: bandit

  # Stage 3: Test
  test:
    needs: security
    steps:
      - name: Unit Tests
        run: |
          pytest backend/tests/unit/ --cov
          npm run test:unit
      - name: Integration Tests
        run: |
          pytest backend/tests/integration/

  # Stage 4: Build
  build:
    needs: test
    steps:
      - name: Build & Push Images
        run: |
          docker build -t backend:${{ github.sha }}
          docker push ecr.backend:${{ github.sha }}
```

### 1.3 Deployment Environments

| Environment | Trigger | Approval | Auto-rollback |
|-------------|---------|----------|---------------|
| **Dev** | Push to `develop` | None | Yes (on failure) |
| **Staging** | Push to `staging` | Optional | Yes (on failure) |
| **Production** | Merge to `main` | Required | Yes (manual + auto) |

## 2. Testing Strategy

### 2.1 Testing Pyramid

```
                    /\
                   /  \
                  / E2E \
                 /  10%  \
                /_________\
               /           \
              / Integration \
             /     30%      \
            /_______________\
           /                 \
          /     Unit Tests    \
         /        60%          \
        /_______________________/
```

### 2.2 Backend Testing (pytest)

**Framework Configuration:**

```python
# pytest.ini
[pytest]
testpaths = tests
python_files = test_*.py
python_classes = Test*
python_functions = test_*
addopts =
    --verbose
    --strict-markers
    --cov=backend
    --cov-report=html
    --cov-report=term-missing
    --cov-fail-under=80
markers =
    unit: Unit tests
    integration: Integration tests
    e2e: End-to-end tests
    slow: Slow running tests
```

**Example Test Structure:**

```python
# tests/conftest.py
import pytest
from django.test import Client
from backend.core.utils.db import SessionLocal

@pytest.fixture(scope="function")
def db():
    """Create a fresh database for each test."""
    session = SessionLocal()
    yield session
    session.rollback()
    session.close()

@pytest.fixture(scope="function")
def client(db):
    """Create a test client."""
    return Client()

@pytest.fixture
def auth_headers(client):
    """Return authenticated headers."""
    # Create test user
    response = client.post("/api/v1/auth/register", json={
        "email": "test@example.com",
        "password": "SecurePass123!"
    })
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}
```

**Example Tests:**

```python
# tests/unit/test_product_service.py
import pytest
from backend.api.services.product_service import ProductService

class TestProductService:
    """Unit tests for Product Service."""

    @pytest.mark.unit
    def test_get_product_by_id(self, db):
        """Test fetching a product by ID."""
        product = ProductService.get_by_id(1)
        assert product is not None
        assert product.id == 1

    @pytest.mark.unit
    def test_search_products(self, db):
        """Test product search functionality."""
        results = ProductService.search("laptop")
        assert len(results) > 0

    @pytest.mark.unit
    @pytest.mark.parametrize("price,expected", [
        (100, True),
        (0, False),
        (-10, False),
    ])
    def test_validate_price(self, price, expected):
        """Test price validation."""
        assert ProductService.validate_price(price) == expected


# tests/integration/test_order_flow.py
import pytest

class TestOrderFlow:
    """Integration tests for order processing."""

    @pytest.mark.integration
    def test_complete_checkout_flow(self, client, auth_headers):
        """Test the full checkout process."""
        # Add item to cart
        client.post("/api/v1/cart/items", json={
            "product_id": 1,
            "quantity": 2
        }, headers=auth_headers)

        # Checkout
        response = client.post("/api/v1/orders/checkout", json={
            "shipping_address": {
                "street": "123 Main St",
                "city": "New York",
                "zip": "10001"
            },
            "payment_method": "stripe"
        }, headers=auth_headers)

        assert response.status_code == 201
        assert response.json()["status"] == "pending"
```

### 2.3 Frontend Testing (Jest + React Testing Library)

**Configuration:**

```typescript
// jest.config.ts
import type { Config } from 'ts-jest';

export default {
  preset: 'ts-jest',
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/src/tests/setup.ts'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '\\.(css|less|scss)$': 'identity-obj-proxy',
  },
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/main.tsx',
    '!src/**/*.d.ts',
  ],
  coverageThreshold: {
    global: {
      branches: 70,
      functions: 75,
      lines: 75,
      statements: 75,
    },
  },
} satisfies Config;
```

**Example Tests:**

```typescript
// src/components/product/__tests__/ProductCard.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { ProductCard } from '../ProductCard';

describe('ProductCard', () => {
  const mockProduct = {
    id: 1,
    name: 'Test Product',
    price: 99.99,
    image: '/test.jpg',
  };

  it('renders product information correctly', () => {
    render(<ProductCard product={mockProduct} />);
    expect(screen.getByText('Test Product')).toBeInTheDocument();
    expect(screen.getByText('$99.99')).toBeInTheDocument();
  });

  it('calls onAddToCart when button is clicked', () => {
    const onAddToCart = jest.fn();
    render(<ProductCard product={mockProduct} onAddToCart={onAddToCart} />);

    fireEvent.click(screen.getByRole('button', { name: /add to cart/i }));
    expect(onAddToCart).toHaveBeenCalledWith(mockProduct);
  });

  it('shows loading state when isLoading is true', () => {
    render(<ProductCard product={mockProduct} isLoading />);
    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
  });
});
```

### 2.4 E2E Testing (Playwright)

```typescript
// tests/e2e/checkout.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Checkout Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to home page
    await page.goto('/');
    // Login
    await page.click('text=Login');
    await page.fill('[name="email"]', 'test@example.com');
    await page.fill('[name="password"]', 'SecurePass123!');
    await page.click('button[type="submit"]');
  });

  test('complete purchase flow', async ({ page }) => {
    // Search for product
    await page.fill('[placeholder="Search products..."]', 'laptop');
    await page.press('[placeholder="Search products..."]', 'Enter');

    // Select product
    await page.click('.product-card:first-child');

    // Add to cart
    await page.click('text=Add to Cart');
    await expect(page.locator('.cart-count')).toHaveText('1');

    // Go to checkout
    await page.click('text=Cart');
    await page.click('text=Checkout');

    // Fill shipping info
    await page.fill('[name="street"]', '123 Main St');
    await page.fill('[name="city"]', 'New York');
    await page.fill('[name="zip"]', '10001');

    // Complete payment (mock)
    await page.click('text=Place Order');

    // Verify success
    await expect(page).toHaveURL(/.*order-confirmation/);
    await expect(page.locator('text=Thank you for your order')).toBeVisible();
  });
});
```

### 2.5 Coverage Targets

| Component Type | Target Coverage |
|----------------|-----------------|
| **Critical Logic** (Payment, Auth) | 90%+ |
| **Business Logic** (Services) | 85%+ |
| **API Controllers** | 80%+ |
| **UI Components** | 75%+ |
| **Utilities** | 80%+ |

## 3. Deployment Strategy

### 3.1 Container Strategy

**Dockerfile Backend:**

```dockerfile
# docker/Dockerfile.backend
FROM python:3.12-slim as builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --user --no-cache-dir -r requirements.txt

# Production image
FROM python:3.12-slim

WORKDIR /app

# Copy installed packages
COPY --from=builder /root/.local /root/.local
ENV PATH=/root/.local/bin:$PATH

# Copy application
COPY backend/ .

# Create non-root user
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:8000/health')"

EXPOSE 8000

CMD ["gunicorn", "backend.main:app", "--bind", "0.0.0.0:8000", "--workers", "4"]
```

**Dockerfile Frontend:**

```dockerfile
# docker/Dockerfile.frontend
FROM node:20-alpine as builder

WORKDIR /app

# Install dependencies
COPY frontend/package*.json ./
RUN npm ci

# Build application
COPY frontend/ .
RUN npm run build

# Production image with nginx
FROM nginx:alpine

# Copy built files
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config
COPY docker/nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

### 3.2 AWS ECS Deployment

**Task Definition:**

```json
{
  "family": "ecommerce-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "2048",
  "memory": "4096",
  "containerDefinitions": [
    {
      "name": "backend",
      "image": "123456789.dkr.ecr.us-east-1.amazonaws.com/ecommerce-backend:latest",
      "portMappings": [{"containerPort": 8000, "protocol": "tcp"}],
      "environment": [
        {"name": "DJANGO_SETTINGS_MODULE", "value": "backend.core.config.settings"}
      ],
      "secrets": [
        {"name": "DATABASE_URL", "valueFrom": "arn:aws:secretsmanager:...:db-url"},
        {"name": "SECRET_KEY", "valueFrom": "arn:aws:secretsmanager:...:secret-key"}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ecommerce-backend",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8000/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

### 3.3 Blue-Green Deployment

```mermaid
graph LR
    A[ALB] --> B[Blue Environment]
    A --> C[Green Environment]

    D[Traffic]
    D -->|100%| B
    D -->|0%| C

    E[Deploy New Version]
    E --> C

    F[Smoke Tests]
    F --> C

    G[Shift Traffic]
    G -->|0%| B
    G -->|100%| C

    H[Old Version]
    H --> B
```

**Deployment Steps:**

1. Deploy new version to Green environment
2. Run smoke tests against Green
3. Shift 10% traffic to Green
4. Monitor metrics for 5 minutes
5. Shift remaining traffic gradually (25% → 50% → 100%)
6. Terminate Blue environment after success

## 4. Environment Management

### 4.1 Environment Variables Template

```bash
# .env.example

# =============================================================================
# Application
# =============================================================================
APP_NAME=ecommerce-platform
APP_ENV=development
DEBUG=true
LOG_LEVEL=INFO

# =============================================================================
# Django Settings
# =============================================================================
DJANGO_SECRET_KEY=change-me-in-production
DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1
DJANGO_CORS_ORIGINS=http://localhost:3000,http://localhost:8000

# =============================================================================
# Database (PostgreSQL)
# =============================================================================
DATABASE_URL=postgresql://user:password@localhost:5432/ecommerce
DATABASE_POOL_SIZE=20
DATABASE_MAX_OVERFLOW=10

# =============================================================================
# Redis (Cache & Sessions)
# =============================================================================
REDIS_URL=redis://localhost:6379/0
REDIS_CACHE_TTL=300
REDIS_SESSION_TTL=86400

# =============================================================================
# Elasticsearch
# =============================================================================
ELASTICSEARCH_URL=http://localhost:9200
ELASTICSEARCH_INDEX_PREFIX=ecommerce

# =============================================================================
# Celery (Async Tasks)
# =============================================================================
CELERY_BROKER_URL=redis://localhost:6379/1
CELERY_RESULT_BACKEND=redis://localhost:6379/2
CELERY_TASK_TIMEOUT=300

# =============================================================================
# External Services
# =============================================================================
# Stripe (Payments)
STRIPE_PUBLIC_KEY=pk_test_...
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# SendGrid (Email)
SENDGRID_API_KEY=SG.xxx
SENDGRID_FROM_EMAIL=noreply@example.com

# AWS (S3, SES, etc.)
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
AWS_REGION=us-east-1
AWS_S3_BUCKET=ecommerce-assets

# =============================================================================
# Security
# =============================================================================
JWT_ACCESS_TOKEN_EXPIRE_MINUTES=30
JWT_REFRESH_TOKEN_EXPIRE_DAYS=7
PASSWORD_MIN_LENGTH=8

# =============================================================================
# Rate Limiting
# =============================================================================
RATE_LIMIT_PER_MINUTE=60
RATE_LIMIT_BURST=100

# =============================================================================
# Feature Flags
# =============================================================================
FEATURE_ENABLE_REGISTRATION=true
FEATURE_ENABLE_VENDOR_DASHBOARD=true
FEATURE_ENABLE_RECOMMENDATIONS=true
```

### 4.2 Environment-Specific Configs

| Environment | Debug Mode | Log Level | Cache TTL | Workers |
|-------------|------------|-----------|-----------|---------|
| **Development** | true | DEBUG | 60s | 2 |
| **Staging** | false | INFO | 300s | 4 |
| **Production** | false | WARNING | 600s | 8+ |

## 5. Version Control Workflow

### 5.1 Chosen Strategy: Modified GitHub Flow

```
                    (staging)
                       ↑
                       │
    ┌──────────────────┴──────────────────┐
    │                                     │
(main) ────────→ (develop) ────────→ (feature/*)
    │                                     │
    └───────── production-ready ───────────┘
              └── release tags
```

**Branch Rules:**

| Branch | Purpose | Protection | Merge Requirements |
|--------|---------|------------|-------------------|
| `main` | Production | ✅ Protected | PR + 2 approvals + tests pass |
| `staging` | Pre-production | ✅ Protected | PR + 1 approval + tests pass |
| `develop` | Integration | ❌ Open | PR + tests pass |
| `feature/*` | Feature work | ❌ Open | PR + tests pass |

### 5.2 Commit Convention

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style changes (formatting)
- `refactor`: Code refactoring
- `perf`: Performance improvement
- `test`: Adding or updating tests
- `chore`: Maintenance tasks
- `ci`: CI/CD changes

**Examples:**
```
feat(cart): add item quantity validation
fix(payment): handle stripe webhook retries
docs(api): update authentication endpoints
test(order): add checkout flow integration tests
```

### 5.3 Release Process

```bash
# 1. Create release branch from main
git checkout -b release/v1.2.0 main

# 2. Update version numbers
# Update package.json, __init__.py, etc.

# 3. Tag and push
git tag -a v1.2.0 -m "Release v1.2.0"
git push origin main --tags

# 4. Create GitHub Release with changelog
gh release create v1.2.0 --notes "## What's New..."
```

## 6. Common Pitfalls & Solutions

### 6.1 Django Specific

| Pitfall | Solution |
|---------|----------|
| **N+1 Queries** | Use `select_related()` and `prefetch_related()` |
| **Race Conditions** | Use `select_for_update()` for critical sections |
| **Memory Leaks** | Close DB connections in long-running tasks |
| **Middleware Order** | Ensure auth middleware runs before custom middleware |

**Example:**

```python
# BAD: N+1 query problem
orders = Order.objects.all()
for order in orders:
    print(order.customer.name)  # N+1 queries!

# GOOD: Prefetch related data
orders = Order.objects.select_related('customer').all()
for order in orders:
    print(order.customer.name)  # Single query!

# BAD: Race condition in inventory
def decrease_inventory(product_id, quantity):
    product = Product.objects.get(id=product_id)
    if product.stock >= quantity:
        product.stock -= quantity  # Race condition!
        product.save()

# GOOD: Use select_for_update
def decrease_inventory(product_id, quantity):
    with transaction.atomic():
        product = Product.objects.select_for_update().get(id=product_id)
        if product.stock >= quantity:
            product.stock -= quantity
            product.save()
```

### 6.2 Celery Specific

| Pitfall | Solution |
|---------|----------|
| **Task Results Too Large** | Don't return large objects; use S3 |
| **Long-Running Tasks** | Break into smaller chunks with `chain` |
| **Task Timeouts** | Set appropriate `task_time_limit` |
| **Deadlocks** | Avoid task chains that wait on each other |

```python
# GOOD: Break long tasks into chunks
@celery.task
def process_large_report(report_id):
    chunks = get_report_chunks(report_id)
    for chunk in chunks:
        process_chunk.delay(chunk.id)  # Process in parallel

# GOOD: Handle idempotency
@celery.task(bind=True)
def send_notification(self, user_id, message_id):
    # Check if already sent
    if Notification.objects.filter(message_id=message_id).exists():
        return
    # Send notification...
```

### 6.3 Redis Specific

| Pitfall | Solution |
|---------|----------|
| **Cache Stampede** | Use cache locks or `SET NX EX` |
| **Memory Overflow** | Set `maxmemory` and `eviction policy` |
| **Connection Pool Exhaustion** | Properly configure connection pool |
| **Stale Data** | Use appropriate TTL and cache invalidation |

```python
# GOOD: Cache stampede prevention
def get_product_with_cache(product_id):
    cache_key = f"product:{product_id}"
    product = cache.get(cache_key)

    if product is None:
        # Acquire lock
        lock_key = f"lock:{cache_key}"
        lock = cache.lock(lock_key, timeout=10)
        if lock.acquire(blocking=False):
            try:
                # Double-check cache
                product = cache.get(cache_key)
                if product is None:
                    product = fetch_from_db(product_id)
                    cache.set(cache_key, product, timeout=300)
            finally:
                lock.release()
        else:
            # Wait and retry
            time.sleep(0.1)
            return get_product_with_cache(product_id)

    return product
```

### 6.4 React Specific

| Pitfall | Solution |
|---------|----------|
| **Unnecessary Re-renders** | Use `React.memo`, `useMemo`, `useCallback` |
| **Memory Leaks** | Cleanup effects, cancel subscriptions |
| **Prop Drilling** | Use Context API or state management |
| **Large Bundle Size** | Code splitting, lazy loading |

```typescript
// GOOD: Prevent unnecessary re-renders
const ProductCard = React.memo(({ product, onAddToCart }) => {
  return (
    <div className="product-card">
      <h3>{product.name}</h3>
      <button onClick={() => onAddToCart(product)}>Add to Cart</button>
    </div>
  );
});

// GOOD: Memoize expensive calculations
function useProductFilters(products: Product[], filters: Filters) {
  const filtered = useMemo(() => {
    return products.filter(p =>
      p.price >= filters.minPrice &&
      p.category === filters.category
    );
  }, [products, filters]);

  return filtered;
}

// GOOD: Cleanup effects
useEffect(() => {
  const subscription = WebSocket.subscribe(channel);
  return () => subscription.unsubscribe(); // Cleanup!
}, [channel]);
```

### 6.5 PostgreSQL Specific

| Pitfall | Solution |
|---------|----------|
| **Bloat** | Regular `VACUUM ANALYZE` |
| **Slow Queries** | Use `EXPLAIN ANALYZE`, add indexes |
| **Connection Exhaustion** | Use connection pooling (pg_bouncer) |
| **Lock Contention** | Keep transactions short |

```sql
-- GOOD: Useful indexes
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_products_category_price ON products(category_id, price)
  WHERE status = 'active';

-- GOOD: Partial index (smaller, faster)
CREATE INDEX idx_active_products ON products(id)
  WHERE status = 'active' AND stock > 0;

-- GOOD: Covering index
CREATE INDEX idx_order_search ON orders(user_id, status, created_at)
  INCLUDE (total_amount);
```

## 7. Monitoring & Observability

### 7.1 Metrics to Track

| Category | Metrics | Tool |
|----------|---------|------|
| **Application** | Request rate, error rate, latency | CloudWatch |
| **Business** | Orders/min, revenue, cart abandonment | Custom |
| **Infrastructure** | CPU, memory, disk, network | CloudWatch |
| **Database** | Connections, slow queries, lock time | RDS Insights |
| **Cache** | Hit rate, memory usage, evictions | Redis Cloud |

### 7.2 Alerting Thresholds

```yaml
alerts:
  # Critical (Page on-call)
  - name: HighErrorRate
    condition: error_rate > 5%
    duration: 2m
    action: pagerduty

  - name: DatabaseDown
    condition: db_connection == 0
    duration: 30s
    action: pagerduty

  # Warning (Email/Slack)
  - name: HighLatency
    condition: p95_latency > 500ms
    duration: 5m
    action: slack

  - name: LowCacheHitRate
    condition: cache_hit_rate < 70%
    duration: 10m
    action: email
```

---

*Document Version: 1.0*
*Last Updated: 2024*
