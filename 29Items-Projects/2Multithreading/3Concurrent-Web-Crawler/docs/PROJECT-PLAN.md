# Concurrent Web Crawler - Project Plan

## Project Overview

A high-performance, bounded concurrent web crawler built with Java 11+ featuring:
- **ExecutorService** for managed thread pool execution
- **Semaphore** for connection limit control
- **Phaser** for coordinated multi-phase crawling
- **ConcurrentHashMap** for thread-safe URL tracking
- **Jsoup** for HTML parsing and web scraping
- **SQLite** for persistent storage
- **TF-IDF** based content indexing with ML relevance scoring

---

## 1. Project File Structure

```
concurrent-web-crawler/
├── docs/
│   ├── PROJECT-PLAN.md          # This file
│   ├── ARCHITECTURE.md          # System architecture documentation
│   └── TECH-NOTES.md            # Technical implementation notes
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── crawler/
│   │   │           ├── CrawlerApplication.java      # Main entry point
│   │   │           │
│   │   │           ├── api/
│   │   │           │   └── HealthServer.java        # HTTP health/API server
│   │   │           │
│   │   │           ├── config/
│   │   │           │   ├── CrawlerConfig.java       # Configuration POJO
│   │   │           │   └── ConfigLoader.java        # Config file loader
│   │   │           │
│   │   │           ├── core/
│   │   │           │   ├── CrawlerEngine.java       # Main crawler orchestrator
│   │   │           │   ├── CrawlTask.java           # Individual crawl task
│   │   │           │   ├── UrlFrontier.java         # URL queue management
│   │   │           │   ├── PageFetcher.java         # HTTP fetching with Jsoup
│   │   │           │   ├── LinkExtractor.java       # Extract links from pages
│   │   │           │   └── ContentProcessor.java    # Process page content
│   │   │           │
│   │   │           ├── ml/
│   │   │           │   ├── TfIdfCalculator.java     # TF-IDF implementation
│   │   │           │   ├── RelevanceScorer.java     # ML relevance scoring
│   │   │           │   ├── ContentIndexer.java      # Content indexing
│   │   │           │   └── TextPreprocessor.java    # Text normalization
│   │   │           │
│   │   │           ├── db/
│   │   │           │   ├── DatabaseManager.java     # SQLite connection pool
│   │   │           │   ├── PageRepository.java      # Page CRUD operations
│   │   │           │   ├── IndexRepository.java     # Index storage
│   │   │           │   └── CrawlStateRepository.java # Crawl state persistence
│   │   │           │
│   │   │           ├── robots/
│   │   │           │   ├── RobotsTxtParser.java     # robots.txt parser
│   │   │           │   ├── RobotsTxtCache.java      # Cache parsed rules
│   │   │           │   └── RateLimiter.java         # Per-domain rate limiting
│   │   │           │
│   │   │           └── util/
│   │   │               ├── UrlNormalizer.java       # URL canonicalization
│   │   │               ├── DomainExtractor.java     # Extract domain from URL
│   │   │               └── CrawlMetrics.java        # Statistics tracking
│   │   │
│   │   └── resources/
│   │       ├── application.properties               # Main configuration
│   │       ├── logback.xml                          # Logging configuration
│   │       └── schema.sql                           # SQLite schema
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── crawler/
│                   ├── api/
│                   │   └── HealthServerTest.java
│                   ├── core/
│                   │   ├── CrawlerEngineTest.java
│                   │   ├── UrlFrontierTest.java
│                   │   ├── PageFetcherTest.java
│                   │   ├── PageFetcherIntegrationTest.java
│                   │   └── LinkExtractorTest.java
│                   ├── ml/
│                   │   ├── TfIdfCalculatorTest.java
│                   │   ├── RelevanceScorerTest.java
│                   │   └── TextPreprocessorTest.java
│                   ├── db/
│                   │   ├── DatabaseManagerTest.java
│                   │   └── PageRepositoryTest.java
│                   ├── robots/
│                   │   └── RobotsTxtParserTest.java
│                   └── util/
│                       ├── UrlNormalizerTest.java
│                       └── DomainExtractorTest.java
│
├── .github/
│   └── workflows/
│       ├── ci.yml                                   # CI pipeline
│       └── deploy.yml                               # Railway deployment
│
├── docker/
│   └── Dockerfile.dev                               # Development container
│
├── .env.example                                     # Environment template
├── .gitignore                                       # Git ignore rules
├── Dockerfile                                       # Production container
├── docker-compose.yml                               # Docker orchestration
├── railway.json                                     # Railway configuration
├── pom.xml                                          # Maven build config
└── README.md                                        # Project documentation
```

---

## 2. Implementation TODO List

### Phase 1: Foundation (High Priority) ✅ COMPLETE

- [x] Create project structure and documentation
- [x] Set up Maven build configuration
- [x] Implement `ConfigLoader` and `CrawlerConfig`
- [x] Create SQLite schema and `DatabaseManager`
- [x] Implement basic `PageFetcher` with Jsoup
- [x] Build `RobotsTxtParser` for compliance
- [x] Create `UrlNormalizer` utility
- [x] Set up logging with Logback
- [x] Write unit tests for foundation components
- [x] Configure GitHub Actions CI pipeline

### Phase 2: Core Features (Medium Priority) ✅ COMPLETE

- [x] Implement `CrawlerEngine` with ExecutorService
- [x] Build `UrlFrontier` with ConcurrentHashMap
- [x] Add Semaphore-based connection limiting
- [x] Implement Phaser for multi-phase crawling
- [x] Create `RateLimiter` for per-domain throttling
- [x] Build `LinkExtractor` for URL discovery
- [x] Implement `ContentProcessor` for page analysis
- [x] Create `PageRepository` for CRUD operations
- [x] Add `CrawlStateRepository` for persistence
- [x] Implement `CrawlMetrics` for statistics
- [x] Write integration tests for core crawler

### Phase 3: ML & Indexing (Medium Priority) ✅ COMPLETE

- [x] Implement `TextPreprocessor` (tokenization, stemming)
- [x] Build `TfIdfCalculator` for term weighting
- [x] Create `ContentIndexer` for document indexing
- [x] Implement `RelevanceScorer` with ML model
- [x] Add `IndexRepository` for search support
- [x] Create simple search API endpoint
- [x] Write tests for ML components

### Phase 4: Polish & Optimization ✅ COMPLETE

- [x] Add retry logic with exponential backoff
- [x] Implement graceful shutdown handling
- [x] Add crawl resumption capability
- [x] Add HTTP health server for Railway
- [x] Create Docker configuration
- [x] Documentation completion
- [ ] Performance profiling and tuning (ongoing)
- [ ] Deploy to Railway (ready - needs account setup)

---

## 3. Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Jsoup | 1.17.2 | HTML parsing and web scraping |
| SQLite JDBC | 3.45.1.0 | SQLite database driver |
| SLF4J + Logback | 1.4.14 | Logging framework |
| JUnit 5 | 5.10.2 | Unit testing |
| Mockito | 5.10.0 | Mocking for tests |
| WireMock | 3.4.2 | HTTP mocking for tests |
| Apache Commons Lang | 3.14.0 | String utilities |
| Guava | 33.0.0-jre | Collections & caching |

---

## 4. Milestone Timeline

### Milestone 1: MVP Crawler ✅
- Basic URL fetching
- robots.txt compliance
- SQLite storage
- Single-threaded operation

### Milestone 2: Concurrent Crawler ✅
- Thread pool execution
- Semaphore rate limiting
- Phaser coordination
- Multi-domain support

### Milestone 3: Intelligent Crawler ✅
- TF-IDF indexing
- Relevance scoring
- Content-aware crawling
- Search capability

### Milestone 4: Production Ready ✅
- Docker containerization
- CI/CD automation (GitHub Actions)
- Health check endpoint
- Documentation complete

---

## 5. Risk Assessment

| Risk | Probability | Impact | Mitigation | Status |
|------|-------------|--------|------------|--------|
| Rate limiting by sites | High | Medium | Respect robots.txt, add delays | ✅ Mitigated |
| Memory exhaustion | Medium | High | Bounded queues, disk spillover | ✅ Mitigated |
| Database contention | Medium | Medium | Connection pooling, batching | ✅ Mitigated |
| Infinite crawl loops | Low | High | URL normalization, visited set | ✅ Mitigated |
| SSL/TLS issues | Low | Medium | Configurable SSL handling | ✅ Mitigated |

---

## 6. Success Criteria

1. **Correctness**: Respects robots.txt and rate limits ✅
2. **Performance**: 100+ pages/minute sustained crawl rate (to be verified)
3. **Reliability**: Graceful handling of network errors ✅
4. **Scalability**: Handles 1M+ URLs in frontier ✅ (bounded queue)
5. **Quality**: 80%+ test coverage on core components ✅

---

## 7. Quick Start

### Build
```bash
mvn clean package
```

### Run
```bash
# Basic crawl
java -jar target/concurrent-web-crawler-1.0.0-SNAPSHOT.jar https://example.com

# With HTTP server for monitoring
java -jar target/concurrent-web-crawler-1.0.0-SNAPSHOT.jar --server https://example.com
```

### Docker
```bash
docker-compose up
```

### Test
```bash
mvn test
```
