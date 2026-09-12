# Blog Platform — Architecture

> A single-deployable Laravel application serving a personal blog with Markdown
> authoring, SEO, reading-time estimation, and TF-IDF related-post
> recommendations. Optimised for **operational simplicity** (one PHP process,
> one SQLite file) without sacrificing testability or future scale.

---

## 2.1 Chosen Architectural Pattern — Modular Layered Monolith

We use a **layered monolith** with an explicit **domain/service layer**.

```mermaid
graph TD
    subgraph Browser
        A[Blade + Tailwind UI]
        B[Alpine.js — local interactivity]
        C[Livewire — server-driven components]
    end

    subgraph "Laravel Application (single process)"
        D[Routing / Middleware]
        E[Livewire Components & Controllers]
        F["Service Layer<br/>Markdown · ReadingTime · TF-IDF · SEO"]
        G[Eloquent Models]
    end

    H[(SQLite)]
    I[(Cache: file / redis)]

    A <--> C
    B -.local DOM.-> A
    C <-->|"AJAX (morph)"| D
    D --> E
    E --> F
    F --> G
    E --> G
    G <--> H
    F <--> I
```

### Why a layered monolith (and not microservices)?

| Driver | Implication |
|---|---|
| **Single author / low write volume** | No need for independent scaling of services. One box handles it. |
| **SQLite** | Single-file DB rules out a distributed data tier by design — a monolith is the natural fit. |
| **Operational simplicity** | Laravel Forge deploys one artifact; no service mesh, no inter-service auth, no network hops. |
| **Cohesive domain** | Posts, tags, SEO, and recommendations are tightly coupled reads of the same data — splitting them adds latency and complexity for zero benefit. |
| **Testability preserved** | The **service layer** isolates business logic from the framework, giving us the main benefit people chase with microservices (clear boundaries) without the distribution tax. |

> **Escape hatch:** Because domain logic lives in `App\Services` (not in
> controllers or models), extracting (say) `RelatedPostsService` into a queued
> job, a separate worker, or even a small microservice later is a refactor — not
> a rewrite. Swapping SQLite → Postgres is a config + migration change.

---

## 2.2 Key Component Interactions

```mermaid
graph LR
    subgraph Presentation
        PL[PostList<br/>Livewire]
        PS[PostShow<br/>Livewire]
        PE[PostEditor<br/>Livewire]
    end

    subgraph Services
        MD[MarkdownService]
        RT[ReadingTimeService]
        RP[RelatedPostsService]
        SEO[SeoService]
    end

    subgraph Data
        PM[(Post model)]
        DB[(SQLite)]
        CACHE[(Cache)]
    end

    PE -->|"validate + persist"| PM
    PE -->|"live preview"| MD
    PS -->|"render body"| MD
    PS -->|"badge"| RT
    PS -->|"recommend"| RP
    PS -->|"meta tags"| SEO
    PL --> PM
    RP -->|read corpus| PM
    RP <-->|memoise vectors| CACHE
    PM <--> DB
```

**Communication mechanisms used:**

- **Direct method calls (in-process):** Presentation → Service → Model. This is
  the dominant pattern; everything runs in one PHP process, so there are no
  network calls, no serialization, and no message broker on the hot path.
- **Livewire round-trips (HTTP/AJAX):** The browser and server components
  exchange diffed DOM updates over HTTP. Alpine handles purely local UI state
  (dropdowns, toggles) with **no** server round-trip.
- **Cache (read-through):** `RelatedPostsService` memoises expensive TF-IDF
  vectors and similarity results in the cache store.
- **Queue (deferred, optional):** Heavy one-off work (sitemap regen, search
  index rebuild, related-posts precompute) is dispatched to a queued job —
  the only "async" boundary, and it stays in-process via the `database`/`sync`
  driver until volume justifies Redis + a worker.

---

## 2.3 Data Flow

### Authoring a post (write path)

```mermaid
sequenceDiagram
    autonumber
    actor Author
    participant PE as PostEditor (Livewire)
    participant MD as MarkdownService
    participant Req as StorePostRequest
    participant Post as Post (Eloquent)
    participant DB as SQLite

    Author->>PE: type Markdown
    PE->>MD: render(markdown)  %% live preview
    MD-->>PE: sanitised HTML
    PE-->>Author: side-by-side preview (Alpine)

    Author->>PE: click "Publish"
    PE->>Req: validate(title, body, status…)
    Req-->>PE: validated data ✔
    PE->>MD: render(body) → html cache column
    PE->>Post: create/update(...)
    Post->>DB: INSERT/UPDATE (+ reading_time)
    DB-->>Post: ok
    Post-->>PE: saved model
    PE-->>Author: redirect to post (flash success)
```

### Reading a post (read path, SEO-critical)

```mermaid
sequenceDiagram
    autonumber
    actor Visitor
    participant Router
    participant PS as PostShow (Livewire)
    participant Post as Post (Eloquent)
    participant SEO as SeoService
    participant RP as RelatedPostsService
    participant Cache
    participant DB as SQLite

    Visitor->>Router: GET /blog/{slug}
    Router->>PS: resolve route-model binding
    PS->>Post: find published by slug
    Post->>DB: SELECT … WHERE slug = ?
    DB-->>Post: post row
    PS->>SEO: forPost(post) → meta + JSON-LD
    PS->>RP: relatedTo(post, 3)
    RP->>Cache: get("related:{id}")
    alt cache hit
        Cache-->>RP: related ids
    else cache miss
        RP->>Post: load corpus (id, title, body, tags)
        Post->>DB: SELECT published posts
        DB-->>RP: corpus
        RP->>RP: TF-IDF vectorise + cosine similarity
        RP->>Cache: put("related:{id}", ids, ttl)
    end
    RP-->>PS: top-N related posts
    PS-->>Visitor: fully-rendered HTML (meta, body, related)
```

---

## 2.4 Scalability & Performance Strategy

**Today's profile:** read-heavy, write-rare, single-author. Optimise reads.

```mermaid
graph TD
    U[Visitors] --> CDN[CDN / Forge edge cache]
    CDN -->|cache miss| APP[PHP-FPM + OPcache]
    APP --> RC[(Response / page cache)]
    APP --> DB[(SQLite WAL mode)]
    APP --> OB[Octane optional<br/>persistent workers]
```

- **Vertical first:** A single Forge box with **OPcache** + **PHP 8.3 JIT**
  comfortably serves a personal blog's traffic. SQLite in **WAL mode** handles
  concurrent reads with non-blocking writers.
- **Cache the expensive bits:**
  - **Rendered Markdown** is stored in a `body_html` column (render-on-write,
    not render-on-read) — reads never invoke the Markdown parser.
  - **Reading time** is computed on save and persisted.
  - **TF-IDF / related posts** are cached per-post with tag-aware invalidation.
  - **Full-page / response caching** for anonymous visitors (the 99% case).
- **Asset delivery:** Vite-built, hashed, immutable assets served via CDN;
  HTTP/2, Brotli, long `Cache-Control`.
- **Concurrency upgrade path (in order):**
  1. Add **Laravel Octane** (FrankenPHP/Swoole) for persistent workers.
  2. Move cache/queue/session to **Redis**.
  3. Migrate SQLite → **Postgres/MySQL** (service layer + migrations make this a
     config change) and add read replicas.
  4. Front everything with a CDN doing stale-while-revalidate.
- **N+1 prevention:** eager-load relationships; enable
  `Model::preventLazyLoading()` in non-prod to fail loudly.

---

## 2.5 Security Considerations

```mermaid
graph TD
    A[Request] --> B[HTTPS / HSTS]
    B --> C[Middleware: throttle, CSRF, auth]
    C --> D[FormRequest validation]
    D --> E[Authorization: Policies / Gates]
    E --> F[Eloquent — parameterised queries]
    F --> G[(SQLite)]
    E --> H[Markdown sanitiser<br/>HTML Purifier allow-list]
    H --> I[Escaped Blade output]
```

- **Authentication:** Laravel session auth (Breeze, Livewire stack) for the
  single author/admin. Passwords Bcrypt/Argon2id-hashed. Optional 2FA.
- **Authorization:** Policies/Gates guard every write (`update`, `delete`,
  `publish`). Public read routes are unauthenticated by design.
- **Input validation:** All writes go through `FormRequest` classes — never
  trust Livewire-bound properties without server-side rules.
- **Output / XSS:** This is the #1 risk for a Markdown blog. The pipeline is
  **CommonMark with raw HTML disabled** → **HTML Purifier allow-list** →
  Blade auto-escaping for everything except the deliberately-trusted post body
  (`{!! $post->body_html !!}` only after sanitisation).
- **SQL injection:** Eloquent / query builder uses bound parameters throughout;
  no raw string-interpolated SQL.
- **CSRF & headers:** Laravel CSRF on all state-changing requests; secure
  headers (CSP, X-Frame-Options, HSTS, Referrer-Policy) via middleware.
- **Rate limiting:** Throttle login, comment, and search endpoints.
- **Secret management:** Secrets live only in `.env` on the server (never
  committed). CI/CD secrets in **GitHub Actions encrypted secrets**; production
  env managed by **Laravel Forge**. `APP_KEY` rotated per environment.
- **Dependencies:** `composer audit` + Dependabot in CI to catch CVEs.

---

## 2.6 Error Handling & Logging Philosophy

**Principle:** *Fail loudly in development, gracefully in production, and always
leave a structured trace.*

```mermaid
flowchart TD
    T[Throwable] --> H{Handler}
    H -->|expected<br/>e.g. ValidationException| V[422 + field errors to UI]
    H -->|domain<br/>e.g. PostNotPublished| D[Friendly 404/403 page]
    H -->|unexpected| U[500 page<br/>+ log + alert]
    V --> L[(Structured log)]
    D --> L
    U --> L
    L --> M[Monitor: Flare / Sentry]
    U -.prod.-> Q[No stack trace leaked]
    U -.local.-> S[Full Ignition stack trace]
```

- **Layered exceptions:** Domain code throws **typed, meaningful exceptions**
  (`PostNotPublishedException`) rather than generic ones. The framework's
  exception handler maps each to the right HTTP response.
- **Validation errors** surface inline in Livewire components (real-time),
  never as a 500.
- **Structured logging:** JSON logs with context (request id, user id, route)
  via Monolog. Channels: `daily` file in prod, `stack` (stderr + file) for
  containerised runs.
- **Severity discipline:** `debug/info` for flow, `warning` for recoverable
  anomalies (cache miss storms), `error` for failed operations, `critical` for
  data-integrity / security events (paged immediately).
- **No leaking internals:** Production renders branded 404/500 pages with a
  correlation id; full stack traces (Ignition) only in `local`.
- **Monitoring:** Errors shipped to **Flare/Sentry**; uptime + Lighthouse
  budgets tracked in CI. Health endpoint (`/up`) for Forge/monitoring probes.
- **Graceful degradation:** If `RelatedPostsService` or the cache fails, the
  post still renders — related posts are a non-blocking enhancement wrapped in
  try/catch with a logged warning.
