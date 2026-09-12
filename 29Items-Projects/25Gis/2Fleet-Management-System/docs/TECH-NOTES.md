# Fleet Management System Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended GitHub Actions stages:

1. Lint backend with Ruff and validate formatting.
2. Run backend unit tests with pytest.
3. Lint frontend with ESLint.
4. Run frontend type checks with `tsc`.
5. Build frontend with Vite.
6. Build backend and frontend Docker images.
7. Run integration tests against PostGIS, Redis, and Kafka service containers.
8. Push images to Amazon ECR.
9. Deploy to AWS ECS dev, then staging, then production with approval gates.

Production deployment should use immutable image tags such as the git SHA. Avoid mutable `latest` tags for ECS task definitions.

## 3.2 Testing Strategy

### Unit Testing

- Backend: `pytest`, `pytest-asyncio`, FastAPI `TestClient`, and `coverage`.
- Frontend: Vitest and React Testing Library.
- Coverage target: 80% for service logic and API validation paths.
- ML route prediction should include deterministic fixture-based tests for feature extraction and fallback logic.

### Integration Testing

- Run PostGIS, Redis, and Kafka through Docker Compose or GitHub Actions service containers.
- Validate PostGIS geofence queries with known geometry fixtures.
- Validate Kafka consumers using test topics and isolated consumer groups.
- Test API-to-database flows through FastAPI test clients.

### End-to-End Testing

- Use Playwright for browser workflows.
- Cover login, active fleet map, vehicle detail view, geofence creation, and trip playback.
- Prefer stable test IDs for interactive map controls because Leaflet DOM structure can change.

## 3.3 Deployment Strategy

Deploy as containers on AWS ECS:

- Backend FastAPI service runs behind an Application Load Balancer.
- Frontend can run as an Nginx-served static container or be deployed to S3/CloudFront later.
- PostGIS runs on Amazon RDS PostgreSQL with the PostGIS extension enabled.
- Redis runs on Amazon ElastiCache.
- Kafka runs on Amazon MSK or a compatible managed Kafka provider.
- Container images are built in CI and pushed to Amazon ECR.

For production, prefer separate ECS services for:

- API service.
- Telemetry consumer worker.
- Scheduled or batch route prediction jobs.

## 3.4 Environment Management

Use environment-specific configuration injected at runtime:

- Local: `.env` with Docker Compose.
- Dev/staging/prod: AWS Secrets Manager or SSM Parameter Store.
- Frontend public values: `VITE_` variables only.
- Backend secrets: never expose them through frontend builds.

The `.env.example` file in the repository root documents expected variables. Baseline template:

```dotenv
ENVIRONMENT=development
LOG_LEVEL=INFO
API_HOST=0.0.0.0
API_PORT=8000
CORS_ORIGINS=http://localhost:5173
DATABASE_URL=postgresql+psycopg://fleet:fleet@postgis:5432/fleet
REDIS_URL=redis://redis:6379/0
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
JWT_ISSUER=https://auth.example.com/
JWT_AUDIENCE=fleet-api
JWT_SECRET=replace-me-for-local-development
VITE_API_BASE_URL=http://localhost:8000
AWS_REGION=us-east-1
ENABLE_SECURITY_HEADERS=true
GZIP_MINIMUM_SIZE=1000
```

## 3.5 Version Control Workflow

Use trunk-based development with short-lived feature branches:

- Main branch is always releasable.
- Pull requests are small and reviewed before merge.
- Feature flags protect unfinished product behavior.
- Release tags are created from main using semantic versioning once the project stabilizes.

This is a good fit because the system has multiple deployable components and benefits from frequent integration.

## 3.6 Common Pitfalls

- PostGIS queries can become slow without correct GiST indexes and query plans.
- High-volume telemetry tables need partitioning before they become operationally painful.
- Redis should cache derived current state, not become the only source of truth.
- Kafka consumer ordering depends on partition keys; use stable keys such as `vehicle_id`.
- Leaflet maps can become sluggish with thousands of markers; clustering or canvas rendering may be required.
- ETA prediction should always have a deterministic fallback when ML services are unavailable.
- Local Docker Compose networking differs from AWS ECS service discovery; keep hostnames configurable.
- Avoid storing secrets in GitHub Actions logs, Docker images, or frontend environment variables.
