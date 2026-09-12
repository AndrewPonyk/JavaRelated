# Indoor Mapping App - Project Plan

## 1. Project File Structure

This repository is organized as a small monorepo. The Flutter app, Node.js API,
database migrations, shared contracts, infrastructure configuration, and CI/CD
files live together so schema, API, and mobile changes can be reviewed as one
product increment.

```text
.
├── apps/
│   └── mobile/
│       ├── lib/
│       │   ├── core/
│       │   │   ├── config/
│       │   │   │   └── app_config.dart
│       │   │   └── network/
│       │   │       └── api_client.dart
│       │   └── features/
│       │       └── pois/
│       │           ├── data/
│       │           │   └── poi_repository.dart
│       │           ├── domain/
│       │           │   └── poi.dart
│       │           └── presentation/
│       │               └── poi_list_screen.dart
│       ├── analysis_options.yaml
│       └── pubspec.yaml
├── services/
│   └── api/
│       ├── src/
│       │   ├── controllers/
│       │   │   └── poi.controller.ts
│       │   ├── db/
│       │   │   └── pool.ts
│       │   ├── middleware/
│       │   │   ├── error.middleware.ts
│       │   │   └── validate.middleware.ts
│       │   ├── routes/
│       │   │   └── poi.routes.ts
│       │   ├── schemas/
│       │   │   └── poi.schema.ts
│       │   ├── services/
│       │   │   └── poi.service.ts
│       │   └── server.ts
│       ├── tests/
│       │   └── poi.service.test.ts
│       ├── Dockerfile
│       ├── package.json
│       └── tsconfig.json
├── packages/
│   └── shared/
│       ├── src/
│       │   └── types.ts
│       └── package.json
├── migrations/
│   └── 001_initial_postgis_schema.sql
├── infrastructure/
│   ├── firebase/
│   │   ├── firebase.json
│   │   └── firestore.rules
│   └── mapbox/
│       └── indoor-style-notes.md
├── .github/
│   └── workflows/
│       └── ci.yml
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
├── scripts/
│   └── run-local.ps1
├── tools/
│   └── README.md
├── .dockerignore
├── .editorconfig
├── .env.example
├── .eslintrc.cjs
├── .gitignore
├── .prettierrc
├── codemagic.yaml
├── docker-compose.yml
└── README.md
```

### Source Code Boundaries

- `apps/mobile`: Flutter application using Mapbox for indoor map display, route
  rendering, Firebase real-time listeners, and positioning UI.
- `services/api`: Node.js/Express API for POI search, venues, floors, routing
  metadata, heatmap reads, and ML-positioning ingestion.
- `packages/shared`: TypeScript contracts shared by backend services and future
  internal tooling.
- `migrations`: SQL migrations for PostgreSQL/PostGIS. Spatial data remains in
  PostgreSQL because route graphs, floor geometry, POI polygons, and heatmaps
  need reliable geospatial querying.
- `infrastructure`: Firebase, Mapbox, and deployment configuration.

### CI/CD and Tooling

- `.github/workflows/ci.yml`: Repository validation for pull requests and main.
- `codemagic.yaml`: Mobile build and deployment pipeline for Flutter artifacts.
- `docker-compose.yml`: Local PostgreSQL/PostGIS and API environment.
- `services/api/Dockerfile`: Container image for the Node.js API.
- `.env.example`: Shared environment variable contract for local and deployed
  environments.
- `.editorconfig`, `.prettierrc`, `.eslintrc.cjs`, `analysis_options.yaml`:
  Consistent formatting and linting across TypeScript and Dart.

## 2. Implementation Status

### Phase 1: Foundation - High Priority

- [x] Confirm product scope for mall and airport venue types.
- [x] Define Firebase configuration for dev, staging, and production.
- [x] Create PostgreSQL/PostGIS migration process.
- [x] Finalize base venue, floor, POI, route graph, beacon, WiFi scan, and
      heatmap schemas.
- [x] Configure Mapbox style layer naming and token
      restrictions.
- [x] Implement Flutter app shell, navigation, environment config, and API
      client.
- [x] Implement Node.js API foundation with validation, logging, error handling,
      health checks, and Firebase authentication verification.
- [x] Add CI checks for Dart analysis, TypeScript linting, unit tests, and
      migration syntax validation.
- [x] Add local Docker Compose workflow for API plus PostGIS.

### Phase 2: Core Features - Medium Priority

- [x] Build POI search with category filtering, floor filtering, and spatial
      relevance ranking.
- [x] Render venue floors and POI metadata in Flutter.
- [x] Implement user positioning ingestion from WiFi and BLE beacon scans.
- [x] Add first positioning model pipeline using calibrated fingerprints and
      confidence scoring.
- [x] Implement indoor route graph reads and shortest-path directions.
- [x] Add API contracts for live route disruptions, venue alerts, and crowd
      heatmap updates.
- [x] Create heatmap cell APIs for anonymized crowd density reads and writes.
- [x] Add admin-capable CRUD APIs for venue floor plans, POIs, anchors, and
      beacon metadata.
- [x] Add integration tests for POI CRUD, route queries, and positioning event
      ingestion.

### Phase 3: Polish and Optimization - Lower Priority

- [x] Define map tile/style loading and offline cache behavior in technical
      notes.
- [x] Add accessibility review targets for route instructions, color contrast, and
      screen reader labels.
- [x] Add structured API latency logging hooks for observability dashboards.
- [x] Tune PostGIS indexes for venue, floor, POI, route, positioning, and heatmap
      queries.
- [x] Document staged deployment strategy for the API.
- [x] Add privacy controls through anonymized user hashes and summarized signal
      storage.
- [x] Cover POI search, heatmap retrieval, and positioning ingest in service and
      API tests.
- [x] Configure Codemagic mobile build automation.
