# Cross-Platform KMP News App — Technical Notes & Guide

## 3.1 CI/CD Pipeline Design

The CI/CD pipeline is implemented using **GitHub Actions** across two dedicated workflows:
1. **Pull Request & Push Validation (`ci.yml`)**: Fast feedback loop executing static analysis, multiplatform unit tests, and multi-target compilation on Linux, macOS, and Windows runners.
2. **Release & Store Deployment (`release.yml`)**: Automated signing, artifact packaging (`.aab` for Android, `.ipa` / TestFlight for iOS, `.dmg` / `.msi` / `.deb` for Desktop), and release tagging triggered on version tags (`v*.*.*`).

```
PR / Push Trigger
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. Static Analysis & Linting (Detekt + Spotless + Ktlint)   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Automated Multiplatform Tests                            │
│    ├── Common / JVM Tests (:shared:jvmTest)                 │
│    ├── Android Host Tests (:shared:testAndroidHostTest)     │
│    └── iOS Simulator Tests (macOS: :shared:iosSimulatorArm) │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Build & Package Target Artifacts                         │
│    ├── Android: assembleRelease / bundleRelease (AAB)       │
│    ├── Desktop: packageDistributionForCurrentOS (MSI/DMG)   │
│    └── iOS: Xcodebuild Archive / Swift Framework (IPA)      │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Release tag only)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Deployment Distribution                                  │
│    ├── Google Play Store (Internal / Production Track)       │
│    ├── Apple App Store / TestFlight (via Fastlane)          │
│    └── GitHub Releases (Desktop Binaries & Release Notes)   │
└─────────────────────────────────────────────────────────────┘
```

---

## 3.2 Testing Strategy

| Test Layer | Scope & Framework | Target Coverage | Execution Frequency |
|---|---|---|---|
| **Unit Tests (Domain & UseCases)** | `kotlin.test`, `kotlinx-coroutines-test`, `Turbine` | **> 90%** | Every PR commit |
| **Data Layer Tests (Mocks/Local DB)** | `MockEngine` (Ktor), In-Memory SQLite Driver | **> 80%** | Every PR commit |
| **Presentation / ViewModel Tests** | MVI StateFlow assertion using `Turbine` | **> 85%** | Every PR commit |
| **Compose UI Component Tests** | `androidx.compose.ui.test` / Desktop UI Test Runner | Key Screens | Nightly / Pre-release |
| **End-to-End Smoke Tests** | Maestro / Appium cross-platform UI driver | Critical user journeys | Prior to production deploy |

### Recommended Test Code Pattern (Turbine + Coroutines)
```kotlin
@Test
fun `loadNews emits cached articles first and then fresh ukraine-prioritized articles`() = runTest {
    val fakeRepo = FakeNewsRepository()
    val useCase = GetUkraineNewsUseCase(fakeRepo)
    val viewModel = NewsFeedViewModel(useCase)

    viewModel.state.test {
        assertEquals(NewsFeedState.Initial, awaitItem())
        viewModel.handleIntent(NewsFeedIntent.LoadFeed)
        
        // Assert Loading state
        val loadingState = awaitItem()
        assertTrue(loadingState.isLoading)
        
        // Assert Loaded state with Ukraine prioritized
        val loadedState = awaitItem()
        assertFalse(loadedState.isLoading)
        assertTrue(loadedState.articles.first().isUkraineRelated)
    }
}
```

---

## 3.3 Deployment Strategy

### 1. Android (Google Play)
- Generated Artifact: Android App Bundle (`.aab`) with ProGuard / R8 minification.
- Signing: Android Keystore credentials stored in GitHub Encrypted Secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`).
- Distribution: Fastlane `supply` or Google Play Developer Publishing API action.

### 2. iOS (Apple App Store / TestFlight)
- Generated Artifact: `.ipa` package generated via Xcode Command Line Tools on `macos-14` GitHub runner.
- Signing: Apple Distribution Certificate + Provisioning Profile managed securely via Fastlane Match.
- Distribution: Automatic upload to TestFlight for internal QA upon merge to `main`.

### 3. Desktop (macOS, Windows, Linux)
- Powered by `org.jetbrains.compose` desktop packaging task:
  - macOS: `.dmg` & `.pkg` (signed and notarized via Apple notarytool).
  - Windows: `.msi` & `.exe` (signed with Authenticode certificate).
  - Linux: `.deb` & `.rpm` (packaged with bundled minimal JVM runtime via `jlink`).
- Distribution: Automated GitHub Releases containing checksums and signed binaries.

---

## 3.4 Environment Management

Configuration is decoupled by environment (`development`, `staging`, `production`) using Gradle properties and compile-time `BuildConfig`.

### Template: `.env.example`
```ini
# ==============================================================================
# Cross-Platform KMP News App Environment Variables
# Copy to .env or local.properties (DO NOT COMMIT SENSITIVE VALUES)
# ==============================================================================

# News API Configuration (https://newsapi.org)
NEWS_API_BASE_URL=https://newsapi.org/v2
NEWS_API_KEY=your_news_api_key_here

# The Guardian API Configuration (https://open-platform.theguardian.com)
GUARDIAN_API_BASE_URL=https://content.guardianapis.com
GUARDIAN_API_KEY=your_guardian_api_key_here

# Open RSS / Feed Source Configuration
RSS_FEED_UKRAINE_URL=https://feeds.bbci.co.uk/news/world/rss.xml
RSS_FEED_SECONDARY_URL=https://rss.nytimes.com/services/xml/rss/nyt/World.xml

# App Configuration
APP_ENVIRONMENT=development
ENABLE_NETWORK_LOGGING=true
CACHE_TTL_MINUTES=30
```

---

## 3.5 Version Control Workflow

We adopt **Trunk-Based Development** with short-lived feature branches:

```
main ────●─────────●─────────●─────────● (Always deployable)
          \       /           \       /
           ●─────● (feat/api)  ●─────● (fix/cache-ttl)
```

### Workflow Rules:
1. **Branch Naming**: `feat/<name>`, `fix/<name>`, `refactor/<name>`, `chore/<name>`.
2. **Pull Requests**: Must pass all automated CI checks (linting, tests, multiplatform compilation) + require at least 1 peer approval.
3. **Linear History**: PRs are merged via **Squash and Merge** to maintain a clean git history.
4. **SemVer Automated Releases**: Semantic release tags (`v1.0.0`) trigger production build workflows automatically.

---

## 3.6 Common Pitfalls & Solutions in KMP

1. **Compose Recomposition Loops**:
   - *Issue*: Passing unstable parameters (e.g. standard `List<T>`) causing excessive recompositions.
   - *Solution*: Use `@Immutable` / `@Stable` data classes or `kotlinx.collections.immutable.ImmutableList`.
2. **Ktor Client Engine Multiplatform Compatibility**:
   - *Issue*: Using JVM-only engines like `CIO` or `Apache` on iOS leads to runtime linkage crashes.
   - *Solution*: Use `Darwin` engine for iOS, `OkHttp` or `Android` engine for Android, and `Java` / `CIO` engine for Desktop JVM.
3. **SQLDelight Native Driver Concurrency**:
   - *Issue*: Accessing SQLite instances across background threads in Kotlin/Native without proper connection pooling.
   - *Solution*: Initialize `NativeSqliteDriver` with appropriate schema creation flags and manage access through single repository singletons.
4. **Platform Image Decoding**:
   - *Issue*: Android and iOS render network image URLs differently.
   - *Solution*: Use `Coil 3` for Compose Multiplatform with multiplatform networking integration (`coil-network-ktor3`).
5. **Main Thread Dispatching on Kotlin/Native (iOS)**:
   - *Issue*: StateFlow updates dispatched on background thread cause UI crashes in iOS Compose views.
   - *Solution*: Always collect and post MVI state on `Dispatchers.Main.immediate`.
