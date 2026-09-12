# Cross-Platform KMP News App — Architecture Documentation

## 2.1 Chosen Architectural Pattern: Clean Architecture + MVI (Model-View-Intent)

The **Cross-Platform KMP News App** is built following **Clean Architecture** principles combined with the **MVI (Model-View-Intent)** presentation pattern.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Presentation Layer                            │
│   Compose Multiplatform UI  ◄───  NewsFeedState  ◄───  NewsFeedViewModel│
│             │                                                    ▲      │
│             └─────────────── NewsFeedIntent ─────────────────────┘      │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ Invokes
┌────────────────────────────────────▼────────────────────────────────────┐
│                              Domain Layer                               │
│           GetUkraineNewsUseCase  ◄───►  NewsRepository (Interface)      │
│                         (Pure Kotlin / Zero Deps)                       │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ Implements
┌────────────────────────────────────▼────────────────────────────────────┐
│                               Data Layer                                │
│       NewsRepositoryImpl (Offline-First Cache & Multi-Source Sync)       │
│                  ┌──────────────────┴──────────────────┐                │
│                  ▼                                     ▼                │
│       Ktor Remote Multi-API                   SQLDelight Local Cache    │
│  (NewsAPI, Guardian, RSS Feeds)               (SQLite Multiplatform)    │
└─────────────────────────────────────────────────────────────────────────┘
```

### Architectural Rationale
1. **Unidirectional Data Flow (UDF)**: MVI guarantees that UI state is predictable and immutable. User interactions trigger `Intents`, the `ViewModel` processes them through Use Cases, and produces a single, immutable `State` stream via Kotlin `StateFlow`.
2. **Strict Separation of Concerns**: The `Domain` layer has zero dependencies on Android, iOS, JVM UI frameworks, Ktor, or SQLDelight. It contains pure business rules (such as ranking and prioritizing Ukraine-related news).
3. **Multiplatform Code Sharing (>85%)**: Presentation (Compose Multiplatform), Domain (Pure Kotlin), and Data (Ktor + SQLDelight) are 100% shared across Android, iOS, and Desktop. Only platform bridges (Activity, ViewController, Desktop Window) and SQLite drivers are platform-specific.

---

## 2.2 Key Component Interactions

```mermaid
graph TD
    UI[Compose UI: NewsFeedScreen] -->|1. Dispatches Intent: Refresh / Filter| VM[NewsFeedViewModel]
    VM -->|2. Executes UseCase| UC[GetUkraineNewsUseCase]
    UC -->|3. Requests Articles| Repo[NewsRepositoryImpl]
    
    subgraph Data Synchronization
        Repo -->|4a. Query Cached Data| DB[(SQLDelight SQLite DB)]
        Repo -->|4b. Concurrent Network Requests| Ktor[Ktor Multi-Source API]
        Ktor -->|Fetch| API1[NewsAPI.org]
        Ktor -->|Fetch| API2[The Guardian API]
        Ktor -->|Fetch| API3[Open RSS Feeds]
        API1 -->|DTO Response| Ktor
        API2 -->|DTO Response| Ktor
        API3 -->|DTO Response| Ktor
        Ktor -->|Mapped Domain Entities| Repo
        Repo -->|5. Insert / Update Cache| DB
    end

    DB -->|6. Emit Flow of Entities| Repo
    Repo -->|7. Domain Article List| UC
    UC -->|8. Apply Ukraine Prioritization Logic| VM
    VM -->|9. Emit Immutable StateFlow| UI
```

### Component Roles
- **Presentation (MVI)**:
  - `NewsFeedScreen`: Declarative Compose Multiplatform UI components reacting to state changes.
  - `NewsFeedViewModel`: Manages coroutine lifecycles, collects use case outputs, handles side effects (e.g. snackbars, opening browser links).
  - `NewsFeedContract`: Strictly defines `NewsFeedState`, `NewsFeedIntent`, and `NewsFeedEffect`.
- **Domain**:
  - `Article`: Core business model with properties (`id`, `title`, `description`, `source`, `url`, `imageUrl`, `publishedAt`, `isUkraineRelated`, `relevanceScore`).
  - `GetUkraineNewsUseCase`: Executes regex-based multi-lingual keyword analysis (`Ukraine`, `Ukrainian`, `Kyiv`, `Україна`, `ЗСУ`, etc.), boosting relevant articles to the top of the feed while maintaining secondary chronological sorting.
  - `NewsRepository`: Gateway contract providing reactive `Flow<List<Article>>`.
- **Data**:
  - `NewsRepositoryImpl`: Implements the Network-Bound Resource pattern. Emits cached articles immediately, executes background API sync across 3 sources concurrently using Kotlin Coroutines `async/await`, updates local DB, and re-emits updated state.
  - `KtorNewsApi`: Configured with JSON serialization, timeout controls, logging, and multi-source aggregation.
  - `ArticleDao` & `NewsDatabase`: SQLDelight SQLite storage ensuring full offline usability.

---

## 2.3 Data Flow & Synchronization Lifecycle

The diagram below details the complete request-to-render lifecycle during a user pull-to-refresh or initial launch.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as NewsFeedScreen (Compose)
    participant VM as NewsFeedViewModel
    participant UC as GetUkraineNewsUseCase
    participant Repo as NewsRepositoryImpl
    participant LocalDB as SQLDelight DB
    participant RemoteAPI as Ktor HTTP Client (Multi-Source)

    User->>UI: Launch App / Pull to Refresh
    UI->>VM: sendIntent(NewsFeedIntent.LoadFeed)
    VM->>UI: emit State(isLoading = true, isOffline = false)
    
    VM->>UC: invoke()
    UC->>Repo: getNewsStream(forceRefresh = true)
    
    Note over Repo,LocalDB: 1. Instant Cache Emission
    Repo->>LocalDB: queryAllArticles()
    LocalDB-->>Repo: List<ArticleEntity>
    Repo-->>UC: emit List<Article> (Cached)
    UC->>UC: evaluateUkraineRelevance()
    UC-->>VM: emit Prioritized List<Article>
    VM->>UI: emit State(articles = List, isLoading = true)

    Note over Repo,RemoteAPI: 2. Background Concurrent Network Sync
    par Fetch NewsAPI
        Repo->>RemoteAPI: fetchNewsApiTopHeadlines()
        RemoteAPI-->>Repo: NewsApiResponseDTO
    and Fetch The Guardian
        Repo->>RemoteAPI: fetchGuardianWorldNews()
        RemoteAPI-->>Repo: GuardianResponseDTO
    and Fetch RSS Feed
        Repo->>RemoteAPI: fetchOpenRssNews()
        RemoteAPI-->>Repo: RssFeedDTO
    end

    Note over Repo: Merge, Deduplicate & Map to Domain
    Repo->>LocalDB: clearOldAndInsertNew(articles)
    LocalDB-->>Repo: Cache Updated Trigger
    
    Repo-->>UC: emit List<Article> (Fresh)
    UC->>UC: score & sort (Ukraine First -> Timestamp Desc)
    UC-->>VM: emit Final Prioritized Articles
    VM->>UI: emit State(articles = FreshList, isLoading = false)
    UI-->>User: Render Interactive News Feed
```

---

## 2.4 Scalability & Performance Strategy

1. **Lazy Compose Lists**: `LazyColumn` with stable, deterministic keys (`key = { it.id }`) prevents unnecessary recompositions when new articles are added.
2. **Asynchronous Image Loading**: Coil 3 (Multiplatform) handles disk/memory caching and background decoding for article thumbnails with shimmer placeholders.
3. **Coroutines & Dispatchers**:
   - Network requests run on `Dispatchers.IO`.
   - SQLite queries execute on background worker threads.
   - UI state updates dispatch smoothly on `Dispatchers.Main`.
4. **Structured Concurrency**: Network fetches from different news sources run concurrently via `coroutineScope { awaitAll(...) }` with individual try-catch isolation—if one news source times out, the other two continue without failing the entire feed.
5. **Memory Footprint**: Database entries older than 7 days are automatically pruned during database maintenance routines.

---

## 2.5 Security Considerations

1. **Secret Management**:
   - API keys are injected at build time via Gradle `local.properties` or CI environment variables (e.g. `NEWS_API_KEY`, `GUARDIAN_API_KEY`).
   - Keys are compiled into `BuildConfig` / internal constants and never committed to version control.
2. **Network Security**:
   - Strict HTTPS enforcement with TLS 1.3.
   - Ktor `HttpTimeout` configuration (Connect: 10s, Socket: 15s) prevents denial-of-service hanging.
   - Cleartext HTTP traffic is disabled in Android `AndroidManifest.xml` (`android:usesCleartextTraffic="false"`) and iOS `Info.plist` (App Transport Security).
3. **Data Protection**:
   - Local SQLite database stored in application-sandboxed storage.
   - SQLCipher integration ready for encrypted storage if user bookmarks or personal feeds are added.

---

## 2.6 Error Handling & Logging Philosophy

1. **Result Wrappers**: Operations return a sealed `NetworkResult<T>` (`Success`, `Error`, `Loading`) rather than throwing raw exceptions across architecture layers.
2. **Graceful Degradation**:
   - When network connectivity is unavailable, the app falls back to cached SQLite articles and displays an unobtrusive `OfflineBanner`.
   - Individual news source failures are caught independently so partial feeds are still presented.
3. **User-Facing Error Communication**:
   - Transient errors trigger a `Snackbar` or inline `ErrorView` with a direct **"Retry"** action button.
4. **Structured Logging**: Multiplatform logger (e.g., Napier / Kermit) with debug-only logging levels to ensure no sensitive URL query parameters or tokens are leaked in production releases.
