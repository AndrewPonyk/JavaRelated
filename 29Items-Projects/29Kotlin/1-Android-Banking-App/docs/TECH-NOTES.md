# Tech Notes — Android Banking App

Practical guidance for building, testing, and shipping this project. Paths reference the repo root.

## 3.1 CI/CD Pipeline Design

Two workflows live in `.github/workflows/`:

**`ci.yml`** — runs on every push/PR, two parallel jobs:

```
android job                          backend job
─────────────────────                ─────────────────
checkout + JDK 21 (temurin)          checkout + JDK 21
setup-gradle (caching)               setup-gradle
:app:detekt        (static analysis)  :backend:check   (compile + tests)
:app:testDebugUnitTest (unit tests)
:app:assembleDebug  (build APK)
upload artifact: app-debug.apk
```

**`deploy-play.yml`** — manual (`workflow_dispatch`) or on `v*` tags:

```
decode KEYSTORE_BASE64 → app/bank-release.jks
:app:bundleRelease (signing creds from env/secrets)
r0adkll/upload-google-play → internal track
```

Design choices:

- **Lint → test → build ordering** inside each job fails fast on cheap checks.
- Gradle caching via `gradle/actions/setup-gradle@v4` (build cache + wrapper checksum).
- Release signing is env-gated (see `app/build.gradle.kts`): the release build is unsigned locally
  unless `bank-release.jks` + env vars exist — CI never needs secrets for a debug build.
- **Docker is dev/demo hosting only** — the delivery target stays Google Play (AAB) for the app
  and a fat jar (`:backend:buildFatJar`) for the backend. A `docker-compose.yml` (Ktor api +
  PostgreSQL with Flyway migrations, persistent `pgdata` volume) exists for running the full
  stack on a LAN host; see README "Run the stack with Docker". Fat-jar gotcha baked in:
  shadow must `mergeServiceFiles()` or Flyway's ServiceLoader plugins (migration prefixes!)
  get clobbered and migrations silently don't run.
- Promotion model: internal → closed → production tracks in Play Console; `deploy-play.yml` only
  uploads to `internal`.

## 3.2 Testing Strategy

| Layer | Tooling | What to test | Notes |
|---|---|---|---|
| Domain / fraud rules | JUnit 4 (+ `RuleBasedFraudDetectionEngineTest` as the pattern) | pure functions, boundary amounts, risk thresholds | fastest, aim for the bulk of coverage here |
| ViewModels | `kotlinx-coroutines-test`, MockK | UiState transitions, error folding, blocked-transfer path | swap dispatchers with `StandardTestDispatcher` |
| Repository | Room in-memory DB (`Room.inMemoryDatabaseBuilder`) | cache refresh, upsert, transfer round-trip with `MockWebServer` | MockWebServer from OkHttp |
| Compose UI | `createAndroidComposeRule` + `ui-test-junit4` | loading/error/data renders, retry button, transfer form validation | run on emulator/CI macOS-less via `runs-on: ubuntu-latest` + AVD action (Phase 3) |
| Backend | Ktor `testApplication` | route contracts, validation (422), idempotency replay, fraud block (403) | `PaymentsRouteTest` is the seed |
| E2E | Compose androidTest driving app against a locally running `:backend:run` | full transfer round-trip | the emulator is the E2E environment — backend on host, app hits `10.0.2.2:8080` |

**Coverage targets:** ≥ 80% on `domain/` + `fraud/` (pure logic), ≥ 70% on `data/` mappers and
repository; no numeric target on UI. Coverage gate added to CI in Phase 3 (Kover).

## 3.3 Deployment Strategy

**App → Google Play**

1. Build signed AAB (`:app:bundleRelease`) — versioning: bump `versionCode` per release, keep
   `versionName` semver (`1.2.0`). Play App Signing holds the upload key.
2. CI uploads to **internal** track → QA → closed beta → staged production rollout (10% → 50% → 100%).
3. Signing keys generated once (`keytool -genkey -v -keystore bank-release.jks -keyalg RSA -keysize 4096`),
   stored as GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

**Backend** — fat jar (`./gradlew :backend:buildFatJar`) → any JVM host (Cloud Run source deploy,
VM, or bare metal). Scaling story in ARCHITECTURE §2.4. Containerization deliberately out of scope.

## 3.4 Environment Management

One mechanism per side, both overridable by env vars:

**Android** — `BuildConfig.BACKEND_BASE_URL`, resolved in order:
gradle property `-PbackendBaseUrl=…` → env `BACKEND_BASE_URL` → default `http://192.168.0.104:8080/`
(the dev remote PC running the docker-compose stack — see README).

| Environment | URL | How |
|---|---|---|
| Docker stack on dev remote PC | `http://192.168.0.104:8080/` | default |
| Local emulator (`:backend:run`) | `http://10.0.2.2:8080/` | `-PbackendBaseUrl=…` |
| Physical device | `http://<lan-ip>:8080/` | `-PbackendBaseUrl=…` |
| Staging | `https://staging-api.example.com/` | CI job env |
| Production | `https://api.example.com/` | CI job env (secret) |

**Backend** — HOCON with env overrides (`application.conf`): `PORT`, `DATABASE_URL`, `DB_USER`,
`DB_PASSWORD`.

**`.env.example`** (committed; copy values into CI secrets, never commit the filled file):

```dotenv
# ---- Android app (gradle -P property or CI env) ----
BACKEND_BASE_URL=http://10.0.2.2:8080/

# ---- Release signing (CI secrets; local: place bank-release.jks in app/) ----
KEYSTORE_PASSWORD=changeit
KEY_ALIAS=bank-upload
KEY_PASSWORD=changeit
# GitHub secret holding base64 of the jks, used by deploy-play.yml:
KEYSTORE_BASE64=

# ---- Google Play upload (CI secret, JSON service account) ----
PLAY_SERVICE_ACCOUNT_JSON=

# ---- Ktor backend ----
PORT=8080
DATABASE_URL=jdbc:postgresql://localhost:5432/bank
DB_USER=bank
DB_PASSWORD=bank
```

**Firebase**: `app/google-services.json.example` documents the shape; copy a real file to
`app/google-services.json` **and only then apply the `com.google.gms.google-services` plugin** —
applying it without the JSON breaks every build (see pitfalls).

## 3.5 Version Control Workflow

**Trunk-based development** (lightweight GitHub Flow):

- `master` is always buildable — CI is the gate; protected branch, PR required.
- Short-lived feature branches (`feature/transfer-flow`, `fix/fraud-velocity-rule`), squash-merged.
- **Conventional Commits** (`feat:`, `fix:`, `chore:`, `docs:`) — enables changelog automation and
  future automated version bumps for release tags `v1.2.0`.
- Rationale over Gitflow: single team, continuous deployment to an internal track — release branches
  would add ceremony without value. Release = tag `v*` → deploy workflow.

## 3.6 Common Pitfalls (this stack, specifically)

1. **Compose ↔ Kotlin version lockstep.** With Kotlin 2.x the Compose compiler ships as the
   `org.jetbrains.kotlin.plugin.compose` plugin — its version **must equal** the Kotlin version
   (2.0.21 here). Upgrading Kotlin without it produces cryptic IR errors.
2. **KSP version = `kotlin-1.0.n`.** KSP releases track Kotlin exactly (`2.0.21-1.0.27`). A mismatch
   fails at plugin apply time.
3. **`google-services` plugin + missing JSON = broken build for everyone.** That's why it is NOT
   applied yet; apply it in the same commit that adds a real `google-services.json`.
4. **Money is `Long` minor units.** Never `Double`/`Float` — `0.1 + 0.2` bugs are unacceptable in
   banking. Parsing user input: `BigDecimal → movePointRight(2) → toLong()`.
5. **Retrofit base URL must end with `/`** or every relative path 404s at runtime while looking
   like a network error.
6. **Idempotency is not optional.** Mobile networks retry. Every money mutation carries an
   `Idempotency-Key`; the server stores the rendered response (see V1 schema) and replays it.
7. **Room schema export** — `ksp { arg("room.schemaLocation", …) }` is set; commit `app/schemas/`.
   Without it, silent schema drift makes migrations impossible later.
8. **`EncryptedSharedPreferences` + backup rules**: exclude `bank_secure_prefs` from auto-backup
   (Keystore keys don't restore to a new device) — `backup_rules.xml` needs updating in Phase 2.
9. **Biometric fallback policy**: `BIOMETRIC_STRONG` or refuse the operation — a silent fallback to
   "just press OK" defeats the gate.
10. **Fraud rules must be injectable/deterministic to test** — time comes in as a parameter or
    `Clock`, never `System.currentTimeMillis()` deep inside logic (the stub does this; fix in Phase 2).
11. **Slow-network dependency downloads**: Gradle/AGP artifacts are large; on flaky links prefer
    resumable downloads (`curl -C -`) and retries — a truncated cached artifact breaks builds
    confusingly until the Gradle cache is purged.
12. **Backend in-memory repo loses data on restart** — fine for demo; the Flyway schema exists so
    the Exposed swap (Phase 2) is additive, not a rewrite.
