# Technical Notes & Engineering Guidelines: Fitness Tracker App

## 3.1 CI/CD Pipeline Design

The CI/CD pipeline is designed using **GitHub Actions** with multi-stage verification for both Android and Backend modules.

```
+------------------------------------------------------------------------------------+
|                                GITHUB ACTIONS PIPELINE                             |
+------------------------------------------------------------------------------------+
| 1. LINT & STATIC ANALYSIS : Detekt + ktlint + Android Lint                        |
| 2. UNIT & INTEGRATION TEST: ./gradlew testDebugUnitTest (Android) & test (Backend) |
| 3. BUILD ARTIFACTS        : Assemble Debug APK & Bundle Release AAB                |
| 4. SECURITY & SCAN        : Dependency vulnerability audit & Secret scanning       |
| 5. DEPLOYMENT             : Google Play Internal Track & Backend Container Registry|
+------------------------------------------------------------------------------------+
```

### Pipelines Overview:
- **`android-ci.yml`**: Triggered on every pull request to `main` and `develop`. Executes `ktlintCheck`, `detekt`, `testDebugUnitTest`, and builds unsigned APKs.
- **`backend-ci.yml`**: Builds Ktor/Spring service, spins up a test MySQL container with Flyway migrations, executes integration tests, and builds Docker image.
- **`release-playstore.yml`**: Triggered on semver git tags (e.g. `v1.0.0`). Signs the Android App Bundle (AAB) using GitHub Secrets keystore and uploads to Google Play Console via the Google Play Developer API.

---

## 3.2 Testing Strategy

| Test Level | Scope | Frameworks & Tools | Target Coverage |
| :--- | :--- | :--- | :--- |
| **Unit Tests** | ViewModels, UseCases, Mappers, Repositories | JUnit 5, MockK, Kotlinx Coroutines Test, Turbine | > 85% domain logic |
| **Local DB Tests** | Room DAOs, Migrations, Query correctness | AndroidX Room Testing, Robolectric / In-Memory SQLite | 100% DAO queries |
| **UI Component Tests** | Compose Screens, Design System Tokens, State Renderings | `createComposeRule`, Robolectric / Espresso | Key interactive flows |
| **Backend Integration** | Endpoints, Service layer, SQL migrations | Ktor Test Engine / JUnit 5, Testcontainers (MySQL) | > 80% controller & service |
| **Background Workers** | WorkManager execution constraints and payload sync | `WorkManagerTestInitHelper`, TestListenableWorker | Critical sync jobs |

---

## 3.3 Deployment Strategy

### Android Client:
1. **Build Types & Flavors:**
   - `debug`: Logging enabled, points to local/staging backend (`http://10.0.2.2:8080` for emulator).
   - `staging`: Proguard disabled, points to Staging cloud API with staging Health Connect credentials.
   - `release`: Minified with R8, Proguard rules active, points to Production Cloud API with SSL pinning.
2. **Google Play Publishing:** Automated via GitHub Actions using Google Play Android Publisher API and keystore signing secrets.

### Backend Infrastructure:
1. **Containerization:** Multi-stage `Dockerfile` using lightweight Eclipse Temurin Alpine JRE base images.
2. **Cloud Orchestration:** Deployable to Google Cloud Run, AWS ECS Fargate, or Kubernetes with horizontal pod autoscaling based on CPU/HTTP concurrency.
3. **Database Migrations:** Automated Flyway migrations running on application bootstrap.

---

## 3.4 Environment Management

### Environment Profiles
- `local`: SQLite in-memory / Local MySQL on Docker (`localhost:3306`), mock Health Connect data.
- `staging`: Staging MySQL RDS, sandbox backend endpoints.
- `production`: High-availability MySQL replica set, production Health Connect client IDs.

### `.env.example` Template
```bash
# ==============================================================================
# Fitness Tracker App - Environment Configuration Template
# ==============================================================================

# Backend Service Configuration
SERVER_PORT=8080
SERVER_HOST=0.0.0.0
APP_ENV=development # development | staging | production
JWT_SECRET=super_secret_jwt_signing_key_fitness_tracker_32chars!
JWT_EXPIRATION_HOURS=24

# MySQL Database Configuration
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=fitness_tracker_db
DB_USER=fitness_user
DB_PASSWORD=fitness_password_secure
DB_POOL_MAX_SIZE=10

# ML Recommendation Model Service
ML_MODEL_PATH=/opt/models/workout_recommendation_v1.tflite
ML_RECOMMENDATION_THRESHOLD=0.75

# Mobile App Client Config (Android BuildConfig overrides)
API_BASE_URL_DEBUG=http://10.0.2.2:8080/
API_BASE_URL_RELEASE=https://api.fitnesstracker.example.com/
HEALTH_CONNECT_PACKAGE_NAME=com.google.android.apps.healthdata
SYNC_INTERVAL_MINUTES=15
```

---

## 3.5 Version Control Workflow

We adhere to **GitHub Flow with Release Tags**:
1. **`main` Branch:** Always deployable, protected branch requiring CI check passes and code reviews before merge.
2. **Feature Branches (`feature/feature-name`):** Branch off `main` for individual components, use cases, or UI screens.
3. **Pull Requests:** Must pass Detekt, unit test suites, and compile checks.
4. **Semver Tags (`vX.Y.Z`):** Trigger automated release build pipelines for Play Store deployment.

---

## 3.6 Common Pitfalls & Mitigations

1. **Health Connect Permission Revocation:**
   - *Pitfall:* Users can revoke permissions in system settings at any time, leading to `SecurityException` crashes.
   - *Mitigation:* Always verify permissions via `HealthConnectClient.permissionController.getGrantedPermissions()` before every query.
2. **WorkManager Battery Optimizations / Doze Mode:**
   - *Pitfall:* Exact sync timestamps cannot be guaranteed due to Android battery saver and OEM aggressive task killers.
   - *Mitigation:* Use flexible intervals (`ExistingPeriodicWorkPolicy.KEEP`), mark urgent syncs with `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`.
3. **Room Database Migrations:**
   - *Pitfall:* Modifying database entities without schema version bump causes app crash on upgrade.
   - *Mitigation:* Enable `exportSchema = true` and write automated Room Migration unit tests comparing schema JSON hashes.
4. **Jetpack Compose Performance & Recomposition Loops:**
   - *Pitfall:* Passing unstable collections (`List<T>`) or reading `State<T>` directly inside non-inline composables triggers excessive recomposition.
   - *Mitigation:* Wrap collections in `ImmutableList` or annotate models with `@Immutable`, defer state reads using lambdas (`Modifier.offset { ... }`).
5. **Timezone & Daylight Savings Offsets:**
   - *Pitfall:* Storing workouts as local strings causes inaccuracies during cross-timezone travel or weekly aggregation charts.
   - *Mitigation:* Always persist timestamps in UTC `Instant` (epoch milliseconds) and convert to user's `ZoneId` only at presentation layer.
