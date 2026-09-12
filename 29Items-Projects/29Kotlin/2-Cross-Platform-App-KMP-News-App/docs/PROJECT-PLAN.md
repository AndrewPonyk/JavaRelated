# Cross-Platform KMP News App — Project Structure & Plan

## 1. Executive Summary
The **Cross-Platform KMP News App** is an enterprise-grade Kotlin Multiplatform (KMP) application powered by Compose Multiplatform. It delivers a unified, high-performance user experience across **Android**, **iOS**, and **Desktop (macOS, Windows, Linux)** from a single shared Kotlin codebase. 

The application aggregates real-time news feeds from at least 3 distinct news sources (e.g., NewsAPI, The Guardian API, and Open RSS Feeds) and applies business logic to prioritize and highlight news related to **Ukraine and Ukrainians**.

---

## 1.1 Project File Structure (Code + CI + Tools)

```
Cross-Platform-App-KMP-News-App/
├── .github/                                # Continuous Integration & Deployment
│   ├── workflows/
│   │   ├── ci.yml                          # Build, lint, and test validation
│   │   └── release.yml                     # Multiplatform distribution & store deployment
│   └── pull_request_template.md            # PR quality checklist
│
├── .editorconfig                           # Universal code styling rules
├── .gitignore                              # Git exclusion patterns
├── detekt.yml                              # Kotlin static analysis configuration
├── Dockerfile                              # Headless Linux CI & Gradle build container
├── gradle.properties                       # JVM memory & Gradle build daemon configs
├── gradlew / gradlew.bat                   # Gradle wrapper binaries
├── settings.gradle.kts                     # Gradle module tree & plugin management
├── build.gradle.kts                        # Root build configuration
│
├── gradle/
│   ├── libs.versions.toml                  # Version Catalog for dependencies & plugins
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── docs/                                   # Architectural & Technical Documentation
│   ├── PROJECT-PLAN.md                     # File structure, roadmap, and sprint tasks
│   ├── ARCHITECTURE.md                     # MVI, Clean Architecture, and Mermaid diagrams
│   └── TECH-NOTES.md                       # CI/CD, testing, environment, and deployment
│
├── androidApp/                             # Android Platform Target
│   ├── build.gradle.kts                    # Android application plugin & dependencies
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml         # Android app manifest, permissions, and theme
│           ├── kotlin/org/example/project/
│           │   └── MainActivity.kt         # Android Activity hosting shared Compose UI
│           └── res/                        # Android-specific launch icons & resources
│
├── desktopApp/                             # Desktop Platform Target (JVM)
│   ├── build.gradle.kts                    # Compose Desktop packaging configuration
│   └── src/
│       └── main/
│           └── kotlin/org/example/project/
│               └── main.kt                 # Desktop window initialization & lifecycle
│
├── iosApp/                                 # iOS Platform Target (Xcode Project)
│   ├── iosApp.xcodeproj/                  # Xcode project configuration
│   └── iosApp/
│       ├── iOSApp.swift                    # SwiftUI App entry point
│       └── ContentView.swift               # UIViewControllerRepresentable for KMP
│
└── shared/                                 # Shared Multiplatform Logic & Compose UI
    ├── build.gradle.kts                    # Multiplatform target & dependency definitions
    └── src/
        ├── commonMain/                     # Shared across Android, iOS & Desktop
        │   ├── composeResources/           # Compose Multiplatform vectorized drawables & fonts
        │   ├── sqldelight/                 # SQLDelight database schemas & queries
        │   │   └── org/example/project/database/
        │   │       └── NewsDatabase.sq     # Cached article entities & SQL queries
        │   └── kotlin/org/example/project/
        │       ├── App.kt                  # Root shared Compose Application Composable
        │       ├── di/                     # Dependency Injection (Koin / Service Locator)
        │       │   └── AppModule.kt        # Network, database, and repository bindings
        │       ├── domain/                 # Domain Layer (Pure Kotlin, Zero UI/Platform deps)
        │       │   ├── model/
        │       │   │   ├── Article.kt      # Core business entity
        │       │   │   └── NewsSource.kt   # News provider metadata
        │       ├── repository/
        │       │   │   └── NewsRepository.kt # Repository abstraction
        │       │   └── usecase/
        │       │       └── GetUkraineNewsUseCase.kt # Ukraine news prioritization logic
        │       ├── data/                   # Data Layer (Remote API, Local DB, Caching)
        │       │   ├── remote/
        │       │   │   ├── NewsApi.kt      # Remote API contract
        │       │   │   ├── KtorNewsApi.kt  # Ktor HTTP client multi-source implementation
        │       │   │   └── dto/
        │       │   │       ├── NewsApiResponse.kt # NewsAPI JSON DTO
        │       │   │       └── GuardianResponse.kt# The Guardian API JSON DTO
        │       │   ├── local/
        │       │   │   ├── DatabaseDriverFactory.kt # Expect class for platform DB drivers
        │       │   │   └── ArticleDao.kt   # Local SQLite persistence abstraction
        │       │   └── repository/
        │       │       └── NewsRepositoryImpl.kt # Offline-first Repository implementation
        │       ├── presentation/           # Presentation Layer (MVI Architecture)
        │       │   ├── theme/
        │       │   │   ├── Color.kt        # Material 3 color schemes (Light / Dark)
        │       │   │   ├── Theme.kt        # Compose dynamic theme wrapper
        │       │   │   └── Type.kt         # Typography definitions
        │       │   ├── components/
        │       │   │   ├── ArticleCard.kt  # Responsive news feed card
        │       │   │   ├── UkraineBanner.kt# Ukraine highlighting banner
        │       │   │   ├── ErrorView.kt    # Generic retry and error indicator
        │       │   │   └── LoadingView.kt  # Shimmer / Progress indicator
        │       │   └── feed/
        │       │       ├── NewsFeedContract.kt # MVI State, Intent, and Single Event (Effect)
        │       │       ├── NewsFeedViewModel.kt# ViewModel managing reactive state
        │       │       └── NewsFeedScreen.kt   # Main Compose feed screen
        │       └── util/
        │           ├── DateTimeFormatter.kt# Multiplatform date-time parser
        │           └── NetworkResult.kt    # Sealed class for Success/Error/Loading
        │
        ├── androidMain/                    # Android-specific implementations
        │   └── kotlin/org/example/project/
        │       ├── Platform.android.kt     # Android Platform info
        │       └── data/local/
        │           └── DatabaseDriverFactory.android.kt # Android SQLite Driver (AndroidSqliteDriver)
        │
        ├── jvmMain/                        # Desktop JVM implementations
        │   └── kotlin/org/example/project/
        │       ├── Platform.jvm.kt         # Desktop Platform info
        │       └── data/local/
        │           └── DatabaseDriverFactory.jvm.kt # Desktop SQLite Driver (JdbcSqliteDriver)
        │
        ├── iosMain/                        # iOS-specific implementations
        │   └── kotlin/org/example/project/
        │       ├── Platform.ios.kt         # iOS Platform info
        │       ├── MainViewController.kt   # UIViewController bridge for Swift
        │       └── data/local/
        │           └── DatabaseDriverFactory.ios.kt # iOS Native SQLite Driver (NativeSqliteDriver)
        │
        └── commonTest/                     # Multiplatform Unit & Usecase Tests
            └── kotlin/org/example/project/
                ├── domain/usecase/GetUkraineNewsUseCaseTest.kt
                └── presentation/feed/NewsFeedViewModelTest.kt
```

---

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)
- [x] **Project Initialization**: Setup Kotlin Multiplatform + Compose Multiplatform targets (Android, JVM, iOS).
- [x] **Dependency Catalog**: Configure `libs.versions.toml` with Ktor, SQLDelight/Room, Kotlinx Serialization, Coroutines, and Compose Material 3.
- [x] **Architecture Scaffolding**: Create package boundaries for `domain`, `data`, `presentation`, and `di`.
- [x] **Platform Driver Setup**: Create `DatabaseDriverFactory` `expect/actual` declarations for Android, Desktop JVM, and iOS.
- [x] **Core Domain Models**: Define domain entities (`Article`, `NewsSource`) and repository interfaces.
- [x] **Local Database Definition**: Author `NewsDatabase.sq` schema for offline article persistence.

### Phase 2: Core Features (Medium Priority)
- [x] **Multi-Source Network Client**: Implement `KtorNewsApi` fetching from NewsAPI, Guardian API, and Open RSS feeds concurrently.
- [x] **Data Serialization**: Create Kotlinx Serialization DTOs for each news provider with robust null-safety parsing.
- [x] **Business Logic**: Implement `GetUkraineNewsUseCase` with keyword scoring algorithm prioritizing Ukraine-related articles at the top of the feed.
- [x] **Repository Layer**: Implement `NewsRepositoryImpl` with offline-first synchronization strategy (Network-Bound Resource).
- [x] **MVI State Management**: Implement `NewsFeedContract` (State, Intent, Effect) and `NewsFeedViewModel`.
- [x] **Compose UI Implementation**: Build `NewsFeedScreen`, responsive `ArticleCard`, `UkraineBanner`, and shimmer loading state.
- [x] **Unit Testing**: Write tests for `GetUkraineNewsUseCase` and `NewsFeedViewModel` validating sorting logic.

### Phase 3: Polish & Optimization (Lower Priority)
- [x] **Dark / Light Theme**: Build dynamic Material 3 design system supporting platform color schemes.
- [x] **Error & Connectivity Handling**: Add network retry mechanism, offline indicator banner, and pull-to-refresh.
- [x] **CI/CD Automation**: Implement GitHub Actions workflows for multiplatform testing, linting, and build validation.
- [x] **Code Quality & Linting**: Add `.editorconfig` and Detekt rules for strict code consistency.
- [x] **Desktop Packaging**: Configure native distribution packages (`.dmg`, `.msi`, `.deb`) via Compose Desktop Gradle plugin.
- [x] **Store Deployment Setup**: Document Fastlane and App Store / Google Play publishing pipelines.
