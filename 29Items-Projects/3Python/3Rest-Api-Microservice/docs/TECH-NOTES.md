# Technical Notes

## 1. CI/CD Pipeline Design

### GitHub Actions Workflow

```mermaid
flowchart LR
    A[Push/PR] --> B[Lint]
    B --> C[Unit Tests]
    C --> D[Integration Tests]
    D --> E{Branch?}
    E -->|main| F[Deploy Production]
    E -->|develop| G[Deploy Staging]
    E -->|feature/*| H[Skip Deploy]
```

### Pipeline Stages

| Stage | Tools | Failure Action |
|-------|-------|----------------|
| Lint | black, flake8, isort | Block merge |
| Type Check | mypy | Warning only |
| Unit Tests | pytest --cov | Block if <80% coverage |
| Integration Tests | pytest + test DB | Block merge |
| Security Scan | bandit | Warning + report |
| Build | Docker | Block deploy |
| Deploy | Railway CLI | Rollback on failure |

### Recommended Workflow File Structure

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]
jobs:
  lint:
    # Fast feedback - runs first
  test:
    needs: lint
    # Unit + integration tests
  build:
    needs: test
    # Docker image build
```

---

## 2. Testing Strategy

### 2.1 Test Pyramid

```
        /\
       /  \     E2E Tests (10%)
      /----\    - Full API workflows
     /      \
    /--------\  Integration Tests (30%)
   /          \ - API endpoints + DB
  /------------\
 /              \ Unit Tests (60%)
/________________\ - Services, utils, validators
```

### 2.2 Unit Testing

**Framework:** pytest

**Coverage Target:** 80% minimum

**Key Patterns:**
```python
# Parameterized tests for edge cases
@pytest.mark.parametrize("input,expected", [
    ("valid@email.com", True),
    ("invalid-email", False),
    ("", False),
    (None, False),
])
def test_email_validation(input, expected):
    assert validate_email(input) == expected
```

**Mocking Strategy:**
- Mock external services (NLP, database for unit tests)
- Use `pytest-mock` for clean mocking
- Fixtures for common test data

### 2.3 Integration Testing

**Database Strategy:**
- Separate test database
- Transaction rollback per test
- Factory fixtures for data creation

```python
# conftest.py
@pytest.fixture
def app():
    app = create_app('testing')
    with app.app_context():
        db.create_all()
        yield app
        db.drop_all()

@pytest.fixture
def client(app):
    return app.test_client()
```

### 2.4 API Testing with requests

```python
def test_create_customer(client):
    response = client.post('/api/customers', json={
        'name': 'John Doe',
        'email': 'john@example.com'
    })
    assert response.status_code == 201
    assert response.json['name'] == 'John Doe'
```

---

## 3. Deployment Strategy

### 3.1 Railway Deployment

```mermaid
flowchart TB
    GH[GitHub Push] --> RA[Railway Auto-Deploy]
    RA --> BUILD[Build Docker Image]
    BUILD --> HEALTH[Health Check]
    HEALTH -->|Pass| SWAP[Swap Traffic]
    HEALTH -->|Fail| ROLLBACK[Rollback]
```

### 3.2 Containerization

**Dockerfile Strategy:**
- Multi-stage build for smaller images
- Non-root user for security
- Health check endpoint

```dockerfile
# Stage 1: Dependencies
FROM python:3.12-slim as builder
# Install dependencies

# Stage 2: Runtime
FROM python:3.12-slim
# Copy only necessary files
```

### 3.3 Environment Variables

| Variable | Dev | Staging | Production |
|----------|-----|---------|------------|
| FLASK_ENV | development | staging | production |
| DEBUG | True | False | False |
| DATABASE_URL | localhost | railway-staging | railway-prod |
| LOG_LEVEL | DEBUG | INFO | WARNING |

---

## 4. Environment Management

### 4.1 Configuration Classes

```python
class Config:
    """Base configuration"""
    SECRET_KEY = os.environ.get('SECRET_KEY')
    SQLALCHEMY_TRACK_MODIFICATIONS = False

class DevelopmentConfig(Config):
    DEBUG = True
    SQLALCHEMY_DATABASE_URI = 'mysql://...'

class ProductionConfig(Config):
    DEBUG = False
    SQLALCHEMY_DATABASE_URI = os.environ.get('DATABASE_URL')
```

### 4.2 .env.example Template

```bash
# Application
FLASK_APP=run.py
FLASK_ENV=development
SECRET_KEY=your-secret-key-here

# Database
DATABASE_URL=mysql://user:password@localhost:3306/dbname

# NLP
HF_MODEL_NAME=distilbert-base-uncased
HF_CACHE_DIR=./models

# API
API_KEY=your-api-key-here
RATE_LIMIT=100/hour
```

---

## 5. Version Control Workflow

### Recommended: GitHub Flow

```mermaid
gitGraph
    commit id: "initial"
    branch feature/customer-api
    commit id: "add models"
    commit id: "add endpoints"
    checkout main
    merge feature/customer-api
    branch feature/nlp-service
    commit id: "add nlp"
    checkout main
    merge feature/nlp-service
    commit id: "release v1.0"
```

### Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Feature | feature/description | feature/customer-search |
| Bugfix | fix/description | fix/validation-error |
| Hotfix | hotfix/description | hotfix/security-patch |
| Release | release/version | release/v1.0.0 |

### Commit Message Format

```
type(scope): description

[optional body]

[optional footer]
```

**Types:** feat, fix, docs, style, refactor, test, chore

**Example:**
```
feat(customers): add search endpoint

- Implement full-text search
- Add pagination support
- Include filter by date range

Closes #42
```

---

## 6. Common Pitfalls

### 6.1 Flask/SQLAlchemy Issues

| Pitfall | Problem | Solution |
|---------|---------|----------|
| Circular imports | App factory imports models that import db | Use `current_app` and late binding |
| Session leaks | Uncommitted sessions in tests | Use `db.session.rollback()` in fixtures |
| N+1 queries | Loading relationships lazily | Use `joinedload()` or `selectinload()` |
| Connection exhaustion | Too many open connections | Configure pool_size, pool_recycle |

### 6.2 Testing Pitfalls

| Pitfall | Problem | Solution |
|---------|---------|----------|
| Test pollution | Tests affect each other | Transaction rollback, fresh fixtures |
| Slow NLP tests | Loading models repeatedly | Module-scoped model fixtures |
| Flaky tests | Race conditions, timing | Avoid sleep(), use proper waits |

### 6.3 NLP/Hugging Face Issues

| Pitfall | Problem | Solution |
|---------|---------|----------|
| Memory bloat | Model loaded multiple times | Singleton pattern, module-level cache |
| Cold start | First request slow | Warm-up endpoint, preload on startup |
| Model size | Large download on deploy | Cache models, use smaller variants |
| GPU/CPU mismatch | CUDA errors | Force CPU: `device='cpu'` |

### 6.4 MySQL Specific

| Pitfall | Problem | Solution |
|---------|---------|----------|
| UTF-8 issues | Emoji/special chars fail | Use `utf8mb4` charset |
| Timezone confusion | Datetime inconsistencies | Store UTC, convert on display |
| Connection timeout | Railway idle connections | Set `pool_recycle=3600` |

### 6.5 Railway Deployment

| Pitfall | Problem | Solution |
|---------|---------|----------|
| Build timeout | NLP dependencies slow | Use pre-built wheels, slim dependencies |
| Memory limits | Transformers OOM | Use smaller models, lazy loading |
| Cold starts | Slow first request | Health check warmup, persistent workers |

---

## 7. Performance Tips

### Database Optimization

```python
# Bad: N+1 query
customers = Customer.query.all()
for c in customers:
    print(c.orders)  # Separate query each time

# Good: Eager loading
customers = Customer.query.options(
    selectinload(Customer.orders)
).all()
```

### NLP Optimization

```python
# Lazy load model only when needed
_model = None

def get_model():
    global _model
    if _model is None:
        _model = pipeline("sentiment-analysis",
                         model="distilbert-base-uncased",
                         device=-1)  # CPU
    return _model
```

### Caching Pattern

```python
from functools import lru_cache

@lru_cache(maxsize=1000)
def get_customer_stats(customer_id: int) -> dict:
    # Expensive aggregation
    return calculate_stats(customer_id)
```
