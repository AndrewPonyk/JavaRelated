# Indoor Mapping App

Indoor venue mapping and navigation starter with a Flutter mobile app, Node.js
REST API, and PostgreSQL/PostGIS spatial storage.

## What Is Implemented

- CRUD APIs for venues, floors, POIs, route nodes, route edges, beacon anchors,
  WiFi fingerprints, positioning events, and heatmap cells.
- POI search with venue, floor, category, text, limit, and optional spatial
  ranking.
- Route directions over the stored route graph using shortest-path routing.
- Positioning ingestion that estimates location from stored WiFi fingerprints.
- Stable JSON error envelopes, Zod validation, database constraint handling,
  request logging, and optional Firebase ID token enforcement.
- Flutter UI for venue/floor selection, POI search/filtering, create/edit/delete,
  refresh, empty states, loading states, and API error display.
- Docker Compose for local PostGIS plus API.
- Jest tests with coverage thresholds and Flutter widget/repository tests.

## Prerequisites

- Node.js 22+
- npm 10+
- Docker Desktop or compatible Docker Compose
- Flutter 3.35+ and Dart 3.9+

## Backend Setup

Install dependencies:

```powershell
npm install
```

Run API tests and coverage:

```powershell
npm test
```

Backend test suites:

- [services/api/tests/http.test.ts](services/api/tests/http.test.ts): HTTP
  foundation, validation, error mapping, and HTTPS enforcement.
- [services/api/tests/services.test.ts](services/api/tests/services.test.ts):
  POI search, entity creation/update, route directions, and positioning ingest.
- [services/api/tests/crud-services.test.ts](services/api/tests/crud-services.test.ts):
  CRUD service branches for venues, floors, POIs, route graph, beacons, WiFi,
  positioning, and heatmaps.
- [services/api/tests/setup-env.ts](services/api/tests/setup-env.ts): Jest
  environment setup; this is not a test suite.

Build the API:

```powershell
npm run build
```

Run the local stack:

```powershell
docker compose up --build
```

The API listens on `http://localhost:8080`. Health check:

```powershell
curl http://localhost:8080/health
```

## Mobile Setup

Install Flutter dependencies:

```powershell
cd apps/mobile
flutter pub get
```

Run mobile tests:

```powershell
flutter test
```

Flutter test suites:

- [apps/mobile/test/poi_repository_test.dart](apps/mobile/test/poi_repository_test.dart):
  API envelope parsing and backend error propagation.
- [apps/mobile/test/poi_list_screen_test.dart](apps/mobile/test/poi_list_screen_test.dart):
  loaded POI UI state and empty venue UI state.

Run the app against the local API:

```powershell
flutter run `
  --dart-define=MOBILE_API_BASE_URL=http://10.0.2.2:8080 `
  --dart-define=MAPBOX_ACCESS_TOKEN=pk.replace-me
```

Use `http://localhost:8080` for desktop/web targets and `http://10.0.2.2:8080`
for the Android emulator.

## Environment

Copy `.env.example` when running the API outside Docker:

```powershell
Copy-Item .env.example .env
```

For local development, `REQUIRE_FIREBASE_AUTH=false` keeps write endpoints usable
without Firebase credentials. Set `REQUIRE_FIREBASE_AUTH=true` in staging or
production and provide `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, and
`FIREBASE_PRIVATE_KEY`.

Important production settings:

- `CORS_ORIGINS`: comma-separated browser origins allowed to call the API.
- `REQUIRE_HTTPS=true`: rejects requests that did not arrive over HTTPS.
- `TRUST_PROXY=true`: enables proxy-aware HTTPS checks behind a load balancer.
- `PGSSLMODE=require`: enables TLS for PostgreSQL connections.
- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`: local Docker database
  values; override them in your shell or `.env`.

## API Documentation

See [docs/API.md](docs/API.md) for endpoint details and payload examples.

## Database

The initial migration is [migrations/001_initial_postgis_schema.sql](migrations/001_initial_postgis_schema.sql).
Docker Compose applies it automatically on a fresh database volume.

## CI/CD

GitHub Actions validates migrations, lints, tests, and builds the API, then runs
Flutter analysis and tests. Codemagic is configured for Flutter debug artifact
builds using environment variables from the `indoor_mapping_dev` group.

## Troubleshooting

- Docker build fails with `dockerDesktopLinuxEngine`: start Docker Desktop and
  wait for the Linux engine before running `docker compose up --build`.
- API startup fails with `DATABASE_URL is required`: copy `.env.example` to
  `.env` or set `DATABASE_URL` in the shell running `npm --workspace services/api
run dev`.
- Mobile emulator cannot reach the API: use `http://10.0.2.2:8080` for Android
  emulators and `http://localhost:8080` for desktop/web targets.
- Browser clients receive CORS failures: set `CORS_ORIGINS` to the exact web
  origin, for example `https://admin.example.com`.
- Firebase auth returns `firebase_not_configured`: either set
  `REQUIRE_FIREBASE_AUTH=false` locally or provide Firebase service account
  variables.
