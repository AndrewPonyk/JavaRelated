# Concurrent Web Crawler

A high-performance, bounded concurrent web crawler built with Java 17+ featuring:

- **ExecutorService** for managed thread pool execution
- **Semaphore** for connection limit control
- **Phaser** for coordinated multi-phase crawling
- **ConcurrentHashMap** for thread-safe URL tracking
- **Jsoup** for HTML parsing and web scraping
- **SQLite** for persistent storage
- **TF-IDF** based content indexing with ML relevance scoring

## Features

- Respects `robots.txt` and configurable rate limits
- Multi-phase crawling with Phaser coordination
- URL normalization and deduplication
- Content-based relevance scoring
- Persistent storage with SQLite
- Graceful shutdown handling
- Comprehensive logging and metrics

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- (Optional) Docker for containerized deployment

### Build

```bash
# Clone the repository
git clone https://github.com/example/concurrent-web-crawler.git
cd concurrent-web-crawler

# Build with Maven
mvn clean package

# Run tests
mvn test
```

### Run

```bash
# Basic usage
java -jar target/concurrent-web-crawler-1.0.0-SNAPSHOT.jar https://example.com

# With options
java -jar target/concurrent-web-crawler-1.0.0-SNAPSHOT.jar \
    --max-pages=100 \
    --threads=5 \
    https://example.com https://another-site.com

# Show help
java -jar target/concurrent-web-crawler-1.0.0-SNAPSHOT.jar --help
```

### Docker

```bash
# Build image
docker build -t concurrent-crawler .

# Run container
docker run -v $(pwd)/data:/app/data concurrent-crawler https://example.com
```

## Configuration

Configuration can be provided via:
1. `application.properties` (classpath)
2. External config file (`--config=path`)
3. Environment variables (highest priority)

### Key Settings

| Variable | Default | Description |
|----------|---------|-------------|
| `CRAWLER_THREADS` | 10 | Worker thread pool size |
| `CRAWLER_MAX_CONNECTIONS` | 20 | Max concurrent connections |
| `CRAWLER_DEFAULT_DELAY_MS` | 1000 | Delay between requests |
| `CRAWLER_MAX_PAGES` | 10000 | Max pages to crawl |
| `DB_PATH` | ./data/crawler.db | SQLite database path |

See `.env.example` for all available options.

## Architecture

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

## Project Structure

```
concurrent-web-crawler/
├── docs/                    # Documentation
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
├── src/
│   ├── main/java/com/crawler/
│   │   ├── CrawlerApplication.java
│   │   ├── config/          # Configuration handling
│   │   ├── core/            # Core crawling logic
│   │   ├── ml/              # TF-IDF and relevance scoring
│   │   ├── db/              # Database repositories
│   │   ├── robots/          # robots.txt handling
│   │   └── util/            # Utility classes
│   └── test/                # Unit tests
├── .github/workflows/       # CI/CD pipelines
├── Dockerfile               # Production container
├── pom.xml                  # Maven configuration
└── README.md
```

## Documentation

- [Project Plan](docs/PROJECT-PLAN.md) - Implementation roadmap and TODOs
- [Architecture](docs/ARCHITECTURE.md) - System design and diagrams
- [Tech Notes](docs/TECH-NOTES.md) - Technical guidance and pitfalls

## Development

### Running Tests

```bash
# All tests
mvn test

# With coverage
mvn test jacoco:report
# Report at target/site/jacoco/index.html

# Specific test class
mvn test -Dtest=TfIdfCalculatorTest
```

### Code Quality

```bash
# Checkstyle
mvn checkstyle:check

# SpotBugs (if configured)
mvn spotbugs:check
```

### Development Container

```bash
# Build dev image
docker build -f docker/Dockerfile.dev -t crawler-dev .

# Run with volume mount for hot-reload
docker run -it -v $(pwd):/app -p 5005:5005 crawler-dev
```

## Deployment

### Railway

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login and deploy
railway login
railway link
railway up
```

### GitHub Actions

The project includes CI/CD workflows for:
- Building and testing on every PR
- Docker image building on main branch
- Automated deployment to Railway

## License

MIT License - see [LICENSE](LICENSE) for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Acknowledgments

- [Jsoup](https://jsoup.org/) for HTML parsing
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) for database access
- [Guava](https://github.com/google/guava) for utilities
