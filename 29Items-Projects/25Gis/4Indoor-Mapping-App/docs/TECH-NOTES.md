# Indoor Mapping App - Technical Notes

## 1. CI/CD Pipeline Design

Use two CI/CD tracks: one for repository validation and one for mobile release
builds.

### Pull Request Validation

1. Install dependencies for the API and Flutter app.
2. Run TypeScript linting and formatting checks.
3. Run Dart analysis and formatting checks.
4. Run backend unit tests.
5. Run Flutter unit/widget tests.
6. Validate SQL migrations in a disposable PostGIS container.
7. Build the Node.js API.
8. Build a non-signed Flutter debug or profile artifact where CI time allows.

### Main Branch Deployment

1. Repeat all pull request checks.
2. Build and tag API container image.
3. Apply database migrations to staging after backup/snapshot checks.
4. Deploy API to staging.
5. Run smoke tests against staging.
6. Build mobile artifacts through Codemagic.
7. Promote to production with manual approval for app store and API rollout.

## 2. Testing Strategy

### Unit Tests

- Backend: Jest with high coverage on services, validation schemas, and error
  handling. Target 80 percent coverage for business logic modules.
- Flutter: `flutter_test` for widgets, state management, repositories, and API
  client behavior.
- Shared contracts: TypeScript type checks and schema validation tests.

### Integration Tests

- Use Docker Compose to run API plus PostgreSQL/PostGIS.
- Test migrations from an empty database and from a previous version.
- Verify POI CRUD, venue/floor queries, spatial search, and route graph reads.
- Include Firebase token verification behind an adapter so local tests can use
  signed test tokens or a mock verifier.

### End-to-End Tests

- Use Flutter integration tests for critical mobile flows:
  - Open venue map.
  - Switch floors.
  - Search for a POI.
  - Request directions.
  - Show a simulated user position.
  - Render crowd heatmap updates.
- Use seeded venue fixtures to keep map and route expectations stable.
- Add Playwright or another browser E2E tool only if a web/admin client is added.

## 3. Deployment Strategy

- Deploy the API as a containerized Node.js service.
- Use PostgreSQL with PostGIS as a managed database where possible.
- Use Firebase Authentication and Firebase real-time services as managed
  platform capabilities.
- Use Codemagic for Flutter signing, platform-specific builds, and release
  distribution.
- Use separate dev, staging, and production environments.
- Apply migrations explicitly during deployment, with backups and rollback plans.
- Keep ML positioning workers separate from synchronous API serving when they
  become CPU-heavy or require independent scaling.

## 4. Environment Management

Configuration should be explicit, environment-specific, and never hard-coded in
source files.

### `.env.example` Template

```dotenv
NODE_ENV=development
PORT=8080

DATABASE_URL=postgres://indoor:indoor@localhost:5432/indoor_mapping
POSTGRES_DB=indoor_mapping
POSTGRES_USER=indoor
POSTGRES_PASSWORD=indoor

FIREBASE_PROJECT_ID=indoor-mapping-dev
FIREBASE_CLIENT_EMAIL=firebase-adminsdk@example.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nreplace-me\n-----END PRIVATE KEY-----\n"
REQUIRE_FIREBASE_AUTH=false
REQUIRE_HTTPS=false
TRUST_PROXY=false
CORS_ORIGINS=
PGSSLMODE=disable

MAPBOX_ACCESS_TOKEN=pk.replace-me
MAPBOX_STYLE_URL=mapbox://styles/example/indoor

POSITIONING_EVENT_TOPIC=positioning-events
HEATMAP_UPDATE_TOPIC=heatmap-updates

LOG_LEVEL=info
API_PUBLIC_BASE_URL=http://localhost:8080
MOBILE_API_BASE_URL=http://10.0.2.2:8080
```

### Environment Rules

- Dev may use local Docker Compose services and test Firebase projects.
- Staging should mirror production infrastructure size and security settings
  closely enough for release validation.
- Production secrets must be stored in managed secret stores or CI/CD secret
  variables.
- Flutter environment values should be passed through `--dart-define` or an
  environment-specific build configuration.

## 5. Version Control Workflow

Use **trunk-based development with short-lived feature branches**.

Rationale:

- Mobile, backend, and schema changes need frequent integration.
- Long-lived branches increase migration and contract drift.
- Feature flags are better than keeping large work isolated for weeks.
- Pull requests stay reviewable when they are small and merged often.

Recommended branch flow:

- `main`: Always releasable.
- `feature/<short-name>`: Short-lived feature branches.
- `fix/<short-name>`: Bug fix branches.
- `release/<version>`: Optional branch only when app store release hardening
  requires stabilization.

## 6. Common Pitfalls

- Indoor maps are not normal outdoor maps. Floor level, local coordinate systems,
  and venue-specific geometry must be modeled explicitly.
- PostGIS queries can become expensive without the right geometry types,
  simplification strategy, and indexes.
- WiFi/BLE positioning is noisy. The product needs confidence scores,
  calibration workflows, and graceful fallback states.
- Firebase real-time updates are useful for live state, but PostGIS should remain
  the source of truth for spatial data.
- Mapbox token restrictions need to be configured per platform to reduce abuse.
- Mobile permissions for Bluetooth, WiFi, and location differ by OS version and
  must be handled deliberately.
- Raw positioning data creates privacy risk. Retention, anonymization, consent,
  and access controls should be designed from day one.
- Route directions across floors need transition nodes such as stairs,
  elevators, escalators, and security checkpoints.
- Venue imports will fail in messy ways. Build validation reports before trying
  to render imported geometry in production.
