# Concurrent Web Crawler - Architecture Documentation

## 1. Architectural Pattern

### Chosen Pattern: Layered Modular Monolith

This project adopts a **Layered Modular Monolith** architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                     │
│              (CrawlerApplication, CLI/API)              │
├─────────────────────────────────────────────────────────┤
│                   ORCHESTRATION LAYER                    │
│            (CrawlerEngine, Phaser Coordination)         │
├─────────────────────────────────────────────────────────┤
│                      CORE LAYER                          │
│    (PageFetcher, LinkExtractor, ContentProcessor)       │
├─────────────────────────────────────────────────────────┤
│                    ML/INDEXING LAYER                     │
│      (TfIdfCalculator, RelevanceScorer, Indexer)        │
├─────────────────────────────────────────────────────────┤
│                   INFRASTRUCTURE LAYER                   │
│     (DatabaseManager, RobotsTxtCache, RateLimiter)      │
└─────────────────────────────────────────────────────────┘
```

### Justification

1. **Simplicity**: A monolith is appropriate for this single-purpose tool
2. **Performance**: No network overhead between components
3. **Deployment**: Single JAR deployment to Railway
4. **Maintainability**: Clear module boundaries enable future extraction
5. **Testability**: Each layer can be tested independently

---

## 2. Key Component Interactions

### 2.1 Component Diagram

```mermaid
graph TB
    subgraph Application
        CA[CrawlerApplication]
        CLI[CLI Interface]
    end

    subgraph Orchestration
        CE[CrawlerEngine]
        PH[Phaser Coordinator]
        ES[ExecutorService]
        SEM[Semaphore]
    end

    subgraph Core
        UF[UrlFrontier]
        PF[PageFetcher]
        LE[LinkExtractor]
        CP[ContentProcessor]
    end

    subgraph ML
        TF[TfIdfCalculator]
        RS[RelevanceScorer]
        CI[ContentIndexer]
    end

    subgraph Infrastructure
        DM[DatabaseManager]
        RT[RobotsTxtCache]
        RL[RateLimiter]
    end

    subgraph Storage
        DB[(SQLite)]
    end

    CA --> CE
    CLI --> CA
    CE --> ES
    CE --> PH
    CE --> SEM
    CE --> UF
    ES --> PF
    PF --> LE
    PF --> CP
    CP --> TF
    CP --> RS
    RS --> CI
    PF --> RT
    PF --> RL
    CI --> DM
    UF --> DM
    DM --> DB
```

### 2.2 Communication Patterns

| From | To | Pattern | Description |
|------|-----|---------|-------------|
| CrawlerEngine | ExecutorService | Thread Pool | Submit CrawlTask instances |
| CrawlTask | PageFetcher | Direct Call | Fetch page synchronously |
| CrawlTask | UrlFrontier | ConcurrentHashMap | Thread-safe URL management |
| PageFetcher | RateLimiter | Semaphore | Acquire permits before fetch |
| CrawlerEngine | Phaser | Barrier Sync | Coordinate crawl phases |
| ContentProcessor | TfIdfCalculator | Direct Call | Calculate term weights |
| All Components | DatabaseManager | Connection Pool | SQLite access |

---

## 3. Data Flow

### 3.1 Main Crawl Flow

```mermaid
sequenceDiagram
    participant CLI as CLI/User
    participant CE as CrawlerEngine
    participant UF as UrlFrontier
    participant ES as ExecutorService
    participant CT as CrawlTask
    participant PF as PageFetcher
    participant RT as RobotsTxt
    participant RL as RateLimiter
    participant LE as LinkExtractor
    participant CP as ContentProcessor
    participant ML as ML/TfIdf
    participant DB as SQLite

    CLI->>CE: start(seedUrls)
    CE->>UF: addAll(seedUrls)
    CE->>ES: initialize(threadPool)

    loop Until frontier empty or stopped
        CE->>UF: getNextUrl()
        UF-->>CE: url
        CE->>ES: submit(CrawlTask)

        activate CT
        CT->>RT: isAllowed(url)
        RT-->>CT: allowed

        alt URL Allowed
            CT->>RL: acquire()
            RL-->>CT: permit
            CT->>PF: fetch(url)
            PF-->>CT: pageContent
            CT->>RL: release()

            CT->>LE: extractLinks(page)
            LE-->>CT: links[]
            CT->>UF: addAll(links)

            CT->>CP: process(page)
            CP->>ML: calculateTfIdf(content)
            ML-->>CP: scores
            CP->>DB: savePage(page, scores)
        end
        deactivate CT
    end

    CE-->>CLI: crawlComplete(stats)
```

### 3.2 Multi-Phase Crawl Coordination

```mermaid
flowchart TD
    subgraph Phase1[Phase 1: Discovery]
        P1A[Fetch Seed URLs]
        P1B[Extract Links]
        P1C[Populate Frontier]
    end

    subgraph Phase2[Phase 2: Expansion]
        P2A[Prioritize URLs]
        P2B[Parallel Fetching]
        P2C[Content Extraction]
    end

    subgraph Phase3[Phase 3: Indexing]
        P3A[Calculate TF-IDF]
        P3B[Score Relevance]
        P3C[Build Index]
    end

    subgraph Phase4[Phase 4: Completion]
        P4A[Persist State]
        P4B[Generate Report]
        P4C[Cleanup]
    end

    P1A --> P1B --> P1C
    P1C -->|Phaser.arriveAndAwaitAdvance| P2A
    P2A --> P2B --> P2C
    P2C -->|Phaser.arriveAndAwaitAdvance| P3A
    P3A --> P3B --> P3C
    P3C -->|Phaser.arriveAndAwaitAdvance| P4A
    P4A --> P4B --> P4C
```

---

## 4. Scalability & Performance Strategy

### 4.1 Concurrency Model

```mermaid
graph LR
    subgraph ThreadPool[ExecutorService Thread Pool]
        T1[Worker 1]
        T2[Worker 2]
        T3[Worker 3]
        TN[Worker N]
    end

    subgraph Semaphore[Connection Semaphore]
        P1[Permit 1]
        P2[Permit 2]
        PM[Permit M]
    end

    subgraph RateLimit[Per-Domain Rate Limiters]
        D1[domain-a.com: 1 req/s]
        D2[domain-b.com: 2 req/s]
        D3[domain-c.com: 0.5 req/s]
    end

    T1 --> P1
    T2 --> P2
    T3 --> PM

    P1 --> D1
    P2 --> D2
    PM --> D3
```

### 4.2 Performance Strategies

| Strategy | Implementation | Benefit |
|----------|----------------|---------|
| Bounded Thread Pool | `Executors.newFixedThreadPool(N)` | Prevents resource exhaustion |
| Connection Limiting | `Semaphore(maxConnections)` | Respects server limits |
| URL Deduplication | `ConcurrentHashMap.putIfAbsent()` | Avoids redundant fetches |
| Batch Writes | SQLite transactions | Reduces I/O overhead |
| Lazy Loading | Demand-paged robots.txt | Reduces startup time |
| Caching | Guava Cache for robots.txt | Reduces network calls |

### 4.3 Memory Management

```java
// Bounded frontier prevents OOM
private final BlockingQueue<String> frontier =
    new LinkedBlockingQueue<>(MAX_FRONTIER_SIZE);

// LRU cache for visited URLs (evicts oldest)
private final Map<String, Boolean> visited =
    Collections.synchronizedMap(new LinkedHashMap<>(MAX_VISITED, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_VISITED;
        }
    });
```

---

## 5. Security Considerations

### 5.1 Input Validation

```mermaid
flowchart LR
    URL[User URL] --> V1{Valid URL?}
    V1 -->|No| R1[Reject]
    V1 -->|Yes| V2{Safe Protocol?}
    V2 -->|No| R2[Reject]
    V2 -->|Yes| V3{Not Blacklisted?}
    V3 -->|No| R3[Reject]
    V3 -->|Yes| N[Normalize URL]
    N --> Process[Process URL]
```

### 5.2 Security Measures

| Area | Measure | Implementation |
|------|---------|----------------|
| URL Safety | Protocol whitelist | Only HTTP/HTTPS allowed |
| SSRF Prevention | IP range blacklist | Block private/localhost |
| Resource Limits | Timeout & size caps | Max 30s, 10MB per page |
| SQL Injection | Prepared statements | All queries parameterized |
| Credential Storage | Environment variables | Never in code/config files |
| Rate Limiting | Per-domain throttling | Configurable delays |
| robots.txt | Strict compliance | Honor Crawl-Delay |

### 5.3 SSL/TLS Handling

```java
// Default: Strict SSL validation
// Configurable for testing environments only
public class PageFetcher {
    private boolean validateSSL = true;

    public Document fetch(String url) throws IOException {
        Connection conn = Jsoup.connect(url)
            .timeout(TIMEOUT_MS)
            .maxBodySize(MAX_BODY_SIZE)
            .validateTLSCertificates(validateSSL);
        return conn.get();
    }
}
```

---

## 6. Error Handling & Logging Philosophy

### 6.1 Error Handling Strategy

```mermaid
flowchart TD
    E[Error Occurs] --> C{Error Type}

    C -->|Network Timeout| R1[Retry with Backoff]
    C -->|HTTP 429| R2[Respect Retry-After]
    C -->|HTTP 4xx| L1[Log & Skip]
    C -->|HTTP 5xx| R3[Retry Limited]
    C -->|Parse Error| L2[Log & Continue]
    C -->|robots.txt Denied| L3[Log & Skip Domain]
    C -->|DB Error| F[Fail Fast]

    R1 --> RC{Retry Count}
    R2 --> RC
    R3 --> RC

    RC -->|< Max| RE[Requeue URL]
    RC -->|>= Max| DLQ[Dead Letter Queue]
```

### 6.2 Retry Policy

```java
public class RetryPolicy {
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    public long getDelay(int attempt) {
        // Exponential backoff: 1s, 2s, 4s
        return BASE_DELAY_MS * (long) Math.pow(2, attempt);
    }

    public boolean shouldRetry(int attempt, Exception e) {
        if (attempt >= MAX_RETRIES) return false;
        return isRetryable(e);
    }

    private boolean isRetryable(Exception e) {
        return e instanceof SocketTimeoutException
            || e instanceof ConnectException
            || (e instanceof HttpStatusException
                && ((HttpStatusException) e).getStatusCode() >= 500);
    }
}
```

### 6.3 Logging Structure

```
[TIMESTAMP] [LEVEL] [THREAD] [COMPONENT] - Message {context}

Examples:
2024-01-15 10:23:45.123 INFO  [pool-1-thread-3] PageFetcher - Fetching URL {url=https://example.com/page, attempt=1}
2024-01-15 10:23:46.456 WARN  [pool-1-thread-3] PageFetcher - Retry scheduled {url=https://example.com/page, error=timeout, nextAttempt=2}
2024-01-15 10:23:47.789 ERROR [pool-1-thread-3] PageFetcher - Max retries exceeded {url=https://example.com/page, totalAttempts=3}
```

### 6.4 Metrics Collection

```java
public class CrawlMetrics {
    private final AtomicLong pagesProcessed = new AtomicLong();
    private final AtomicLong bytesDownloaded = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final ConcurrentHashMap<Integer, AtomicLong> statusCodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> domainCounts = new ConcurrentHashMap<>();

    // Thread-safe increment methods
    public void recordPage(String domain, int statusCode, long bytes) {
        pagesProcessed.incrementAndGet();
        bytesDownloaded.addAndGet(bytes);
        statusCodes.computeIfAbsent(statusCode, k -> new AtomicLong()).incrementAndGet();
        domainCounts.computeIfAbsent(domain, k -> new AtomicLong()).incrementAndGet();
    }
}
```

---

## 7. Database Schema

### 7.1 Entity Relationship Diagram

```mermaid
erDiagram
    PAGES {
        INTEGER id PK
        TEXT url UK
        TEXT domain
        TEXT title
        TEXT content_hash
        INTEGER status_code
        REAL relevance_score
        TIMESTAMP crawled_at
        TIMESTAMP last_modified
    }

    INDEX_TERMS {
        INTEGER id PK
        TEXT term UK
        INTEGER document_freq
    }

    PAGE_TERMS {
        INTEGER page_id FK
        INTEGER term_id FK
        REAL tf_idf_score
        INTEGER term_freq
    }

    CRAWL_STATE {
        INTEGER id PK
        TEXT frontier_snapshot
        TEXT visited_snapshot
        TIMESTAMP saved_at
        TEXT status
    }

    ROBOTS_CACHE {
        TEXT domain PK
        TEXT rules
        REAL crawl_delay
        TIMESTAMP fetched_at
        TIMESTAMP expires_at
    }

    PAGES ||--o{ PAGE_TERMS : "has"
    INDEX_TERMS ||--o{ PAGE_TERMS : "in"
```

---

## 8. Deployment Architecture

### 8.1 Railway Deployment

```mermaid
flowchart LR
    subgraph GitHub
        GH[Repository]
        GA[GitHub Actions]
    end

    subgraph Railway
        RS[Railway Service]
        RV[Volume Mount]
    end

    subgraph Container
        JVM[Java 17 JVM]
        APP[Crawler JAR]
        DB[(SQLite)]
    end

    GH -->|Push| GA
    GA -->|Deploy| RS
    RS --> Container
    RV -->|Persist| DB
    JVM --> APP
    APP --> DB
```

### 8.2 Container Strategy

- **Base Image**: `eclipse-temurin:17-jre-alpine`
- **Size Target**: < 200MB
- **Health Check**: HTTP endpoint on `/health`
- **Graceful Shutdown**: SIGTERM handler for clean stop
