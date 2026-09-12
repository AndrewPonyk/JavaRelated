# Cross-Platform (KMP) News App 🇺🇦

An enterprise-grade, offline-first **Kotlin Multiplatform (KMP)** news application built with **Compose Multiplatform** targeting **Android**, **iOS**, and **Desktop (Windows, macOS, Linux)** from a single, unified Kotlin codebase.

> **💡 Single-Codebase Architecture Highlight:**  
> Over **90%+ of the entire application code is 100% shared** across all platforms in `/shared/src/commonMain`. All UI screens, MVI architecture, animations, news ranking algorithms, Ktor networking, SQLite queries, and test suites are written only once. Each platform (Android, Windows Desktop, iOS) only requires a few lines of boilerplate entry-point code (~10–25 lines) to host the shared application.

---

## 🌟 Concrete Capabilities (What the App Can Do)

1. **Multi-Source News Aggregation**: Concurrently fetches and combines news articles from NewsAPI, The Guardian, and Open RSS feeds into a unified feed.
2. **Ukraine News Prioritization**: Analyzes keywords in English and Ukrainian to automatically calculate relevance scores and place Ukraine-related stories at the top of the feed.
3. **Spotlight Highlights Banner**: Displays a top summary banner showing the total count of prioritized Ukraine stories currently active in the application.
4. **Feed Category Filtering**: Enables instant filtering between All Sources, Ukraine Focus, World News, and Saved Bookmarks using interactive chips.
5. **Real-Time Keyword Search**: Provides a live search bar that filters articles instantly across titles, descriptions, authors, and source names.
6. **Offline SQLite Caching**: Stores articles in an embedded client-side database, allowing the app to open with zero-latency and function without an internet connection.
7. **One-Tap Article Bookmarking**: Allows users to save favorite stories locally on their device with persistent storage across app restarts.
8. **Article Detail Reader**: Opens a full-screen modal reader showing complete story content, source links, priority scores, and author attribution.
9. **Local Article Deletion**: Gives users the ability to remove unwanted articles from their local feed cache directly from the detail view.
10. **Manual Refresh & Sync**: Includes a top-bar refresh button to immediately re-synchronize fresh news across all remote sources on demand.
11. **Network Fault Isolation**: Continues displaying available news if one provider fails, and presents an interactive retry screen when disconnected.
12. **Adaptive Dark & Light Themes**: Automatically adjusts the Material 3 design and color palette to match the user's system appearance settings.
13. **Cross-Platform Native Deployment**: Delivers a native experience across Android phones, Windows/macOS/Linux desktops, and iOS devices from a single codebase.
14. **Unified Single Codebase Architecture**: Over 90% of the entire application code is located in the shared multiplatform module (`/shared/src/commonMain`), compiling identically into both the Android APK and the Windows standalone `.exe`.
15. **Shared Presentation & UI Packages**: The entire user interface is 100% shared across Android and Windows in [`org.example.project.presentation.feed`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/presentation/feed) (`NewsFeedScreen.kt`, `NewsFeedViewModel.kt`, `NewsFeedContract.kt`), [`org.example.project.presentation.components`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/presentation/components) (`ArticleCard.kt`, `UkraineBanner.kt`, `ArticleDetailDialog.kt`), and [`org.example.project.presentation.theme`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/presentation/theme).
16. **Shared Pure Kotlin Domain Logic**: All business rules and Ukraine scoring algorithms are shared in [`org.example.project.domain.usecase`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/domain/usecase) (`GetUkraineNewsUseCase.kt`), [`org.example.project.domain.repository`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/domain/repository) (`NewsRepository.kt`), and [`org.example.project.domain.model`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/domain/model) (`Article.kt`, `NewsSource.kt`).
17. **Shared Multi-Source Network & Caching Engine**: All network fetching, DTO parsers, and SQLite caching logic reside in [`org.example.project.data.remote`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/data/remote) (`KtorNewsApi.kt`), [`org.example.project.data.local`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/data/local) (`ArticleDao.kt`), [`org.example.project.data.repository`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/kotlin/org/example/project/data/repository) (`NewsRepositoryImpl.kt`), and [`NewsDatabase.sq`](./Cross-Platform-App-KMP-News-App/shared/src/commonMain/sqldelight/org/example/project/database/NewsDatabase.sq).
18. **Lightweight Platform Launchers**: The Android APK and Windows `.exe` only require tiny single-file entry points (`MainActivity.kt` in `:androidApp` and `main.kt` in `:desktopApp`) that simply invoke the shared `App()` composable.

---

## 📊 Code Sharing Breakdown: 90%+ Shared vs. Platform-Specific

| Layer / Component | Location | Shared vs. Platform-Specific | Details |
|---|---|---|---|
| **UI & Screens (Compose)** | `shared/src/commonMain/kotlin/.../presentation` | **100% Shared** | All UI components, screens, theme, animations |
| **Domain & Business Logic** | `shared/src/commonMain/kotlin/.../domain` | **100% Shared** | Ukraine scoring engine, sorting, models, use cases |
| **Networking & Local DB** | `shared/src/commonMain/kotlin/.../data` | **100% Shared** | Ktor client, DTOs, SQLDelight database schema |
| **Automated Test Suite** | `shared/src/commonTest/kotlin/...` | **100% Shared** | Unit and integration test suites |
| **Android Wrapper** | `androidApp` + `androidMain` | **~2% (Platform)** | Thin `MainActivity.kt` (~25 lines) + SQLite context |
| **Windows Desktop Wrapper** | `desktopApp` + `jvmMain` | **~2% (Platform)** | Thin `main.kt` (~13 lines) creating OS window frame |
| **iOS Wrapper** | `iosApp` + `iosMain` | **~2% (Platform)** | Thin `MainViewController.kt` (~6 lines) bridge to SwiftUI |

---

## 🏗️ Architecture Overview

```
Cross-Platform-App-KMP-News-App/
├── shared/                                 # 90%+ Shared Multiplatform Core
│   └── src/
│       ├── commonMain/                     # 100% Shared UI, Business Logic & Data
│       │   ├── kotlin/org/example/project/
│       │   │   ├── domain/                 # Pure Kotlin Business Logic
│       │   │   │   ├── model/              # Article, NewsSource
│       │   │   │   ├── repository/         # NewsRepository Contract
│       │   │   │   └── usecase/            # GetUkraineNewsUseCase (Prioritization & Scoring)
│       │   │   ├── data/                   # Data Layer & Remote Sync
│       │   │   │   ├── remote/             # KtorNewsApi (NewsAPI, Guardian, RSS)
│       │   │   │   ├── local/              # ArticleDao & DatabaseDriverFactory
│       │   │   │   └── repository/         # NewsRepositoryImpl (Offline-First Sync)
│       │   │   └── presentation/           # Compose Multiplatform UI (MVI)
│       │   │       ├── feed/               # NewsFeedScreen, NewsFeedViewModel, NewsFeedContract
│       │   │       ├── components/         # ArticleCard, UkraineBanner, ArticleDetailDialog
│       │   │       └── theme/              # Material 3 Theme & Colors
│       │   └── sqldelight/                 # NewsDatabase.sq SQLite Schema
│       ├── androidMain/                    # Tiny Android SQLite Driver Adapter (~12 lines)
│       ├── jvmMain/                        # Tiny Desktop JDBC Driver Adapter (~11 lines)
│       └── iosMain/                        # Tiny iOS Native Driver Adapter (~11 lines)
├── androidApp/                             # Android Entry Launcher: MainActivity.kt (~25 lines)
├── desktopApp/                             # Desktop Entry Launcher: main.kt (~13 lines)
├── iosApp/                                 # iOS Entry Launcher: iOSApp.swift (~10 lines)
└── docs/                                   # Architectural & Technical Documentation
    ├── PROJECT-PLAN.md
    ├── ARCHITECTURE.md
    └── TECH-NOTES.md
```

---

## 🚀 Getting Started & Running the Apps

### Prerequisites
- **JDK 17 or JDK 21**
- **Android Studio** (for Android simulator/device runs)
- **Xcode** (for iOS simulator/device runs, macOS host only)

### 1. Run Desktop App (Windows / macOS / Linux)
```bash
# Hot reload mode:
./gradlew :desktopApp:hotRun --auto

# Standard run:
./gradlew :desktopApp:run

# Assemble desktop executable distribution (.exe / .msi):
./gradlew :desktopApp:packageDistributionForCurrentOS
```

### 2. Run Android App
```bash
./gradlew :androidApp:assembleDebug
```

### 3. Run iOS App
Open the `/iosApp` directory in Xcode and run on any iOS Simulator or connected Apple device.

---

## 🧪 Running Automated Tests

Run the complete multiplatform unit and integration test suite:

```bash
# Run shared common & JVM tests:
./gradlew :shared:jvmTest

# Run Android host tests:
./gradlew :shared:testAndroidHostTest

# Run iOS simulator tests (macOS only):
./gradlew :shared:iosSimulatorArm64Test
```

---

## 📄 Documentation Links
* [PROJECT-PLAN.md](./docs/PROJECT-PLAN.md): Implementation roadmap and phase deliverables.
* [ARCHITECTURE.md](./docs/ARCHITECTURE.md): Detailed MVI and Clean Architecture specification with Mermaid sequence diagrams.
* [TECH-NOTES.md](./docs/TECH-NOTES.md): CI/CD pipelines, testing strategies, and deployment guidelines.
