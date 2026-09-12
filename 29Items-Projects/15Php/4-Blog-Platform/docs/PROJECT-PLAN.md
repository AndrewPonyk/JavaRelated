# Blog Platform — Project Plan

> **Stack:** PHP 8.3 · Laravel 11 · Livewire 3 · SQLite · Tailwind CSS 3 · Alpine.js
> **Deployment:** Laravel Forge · GitHub Actions
> **Domain:** Personal blogging platform with Markdown authoring, SEO, reading-time
> estimation, and TF-IDF related-post recommendations.

---

## 1.1 Project File Structure

The project is a **modular Laravel monolith**. Code is organised by responsibility:
thin HTTP/Livewire layers on top, a dedicated `Services` layer holding the
interesting domain logic (Markdown, SEO, reading time, TF-IDF), and Eloquent
models for persistence. SQLite keeps deployment trivial (a single file), while the
service boundaries keep the door open for swapping to MySQL/Postgres later.

```text
4-Blog-Platform/
│
├── app/                              # Application source (PSR-4: App\)
│   ├── Http/
│   │   ├── Controllers/
│   │   │   ├── PostController.php     # Public, SEO-friendly post pages
│   │   │   └── SitemapController.php  # XML sitemap (stub)
│   │   ├── Middleware/                # Custom middleware (stubs)
│   │   └── Requests/
│   │       └── StorePostRequest.php   # FormRequest validation
│   │
│   ├── Console/Commands/             # Artisan commands
│   │   └── WarmRelatedPostsCache.php  # Pre-compute TF-IDF cache (scheduled)
│   │
│   ├── Exceptions/
│   │   └── PostNotPublishedException.php
│   │
│   ├── Livewire/                      # Livewire 3 full-page + nested components
│   │   ├── Auth/Login.php             # Rate-limited admin sign-in
│   │   ├── Admin/                     # Authenticated admin UI
│   │   │   ├── Dashboard.php          # Stats + quick links
│   │   │   ├── PostManager.php        # List/publish/delete posts
│   │   │   └── CategoryManager.php    # Category CRUD
│   │   └── Posts/
│   │       ├── PostList.php           # Paginated, searchable index
│   │       ├── PostShow.php           # Single post + related posts
│   │       └── PostEditor.php         # Create/update with live Markdown preview
│   │
│   ├── Models/                        # Eloquent models
│   │   ├── Post.php  Category.php  Tag.php  User.php
│   │
│   ├── Observers/
│   │   └── PostObserver.php           # Busts related-posts cache on write
│   │
│   ├── Policies/                      # Authorisation (auto-discovered)
│   │   ├── PostPolicy.php
│   │   └── CategoryPolicy.php
│   │
│   ├── Services/                      # ── Domain logic (framework-agnostic) ──
│   │   ├── MarkdownService.php        # CommonMark → sanitised HTML
│   │   ├── ReadingTimeService.php     # Word-count → minutes
│   │   ├── RelatedPostsService.php    # TF-IDF + cosine similarity
│   │   └── Seo/SeoService.php         # Meta, Open Graph, JSON-LD
│   │
│   ├── Http/
│   │   ├── Controllers/               # Auth/Logout, Post feed, Sitemap/robots
│   │   ├── Middleware/SecurityHeaders.php
│   │   └── Requests/StorePostRequest.php
│   │
│   ├── Providers/AppServiceProvider.php
│   └── View/Components/
│
├── bootstrap/
│   ├── app.php                        # Laravel 11 bootstrap (middleware + exceptions)
│   └── providers.php                  # App service providers
│
├── config/                           # Framework + app config
│   ├── app.php auth.php cache.php database.php session.php …
│   ├── livewire.php                   # Full-page layout → layouts.app
│   └── blog.php                       # App-specific tunables (WPM, related count)
│
├── lang/en/                          # Validation/auth translation strings
├── docker/entrypoint.sh             # Container bootstrap (migrate → serve)
│
├── database/
│   ├── migrations/                   # Schema as code
│   │   ├── 0001_..._create_users_table.php
│   │   ├── 0002_..._create_categories_table.php
│   │   ├── 0003_..._create_posts_table.php
│   │   ├── 0004_..._create_tags_table.php
│   │   └── 0005_..._create_post_tag_table.php
│   ├── factories/
│   │   └── PostFactory.php
│   └── seeders/
│       └── DatabaseSeeder.php
│
├── public/                           # Web root (index.php, compiled assets)
│
├── resources/
│   ├── css/app.css                   # Tailwind entrypoint
│   ├── js/app.js                     # Alpine + Livewire bootstrap
│   └── views/
│       ├── layouts/app.blade.php      # Master layout w/ SEO slot
│       ├── livewire/posts/*.blade.php # Component templates
│       └── components/                # Shared Blade components
│
├── routes/
│   ├── web.php                       # Web + Livewire routes
│   └── console.php                   # Scheduled/Artisan closures (stub)
│
├── storage/                          # Compiled views, cache, logs, SQLite db
│   └── app/database.sqlite           # ← SQLite database file (gitignored)
│
├── tests/
│   ├── Unit/
│   │   ├── ReadingTimeServiceTest.php
│   │   └── RelatedPostsServiceTest.php
│   └── Feature/
│       └── PostManagementTest.php
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                    # Lint → static analysis → test → build
│   │   └── deploy.yml                # Deploy to Forge on green main
│   └── ISSUE_TEMPLATE/bug_report.md
│
├── scripts/
│   └── deploy.sh                     # Forge deployment script (zero-downtime)
│
├── .env.example                      # Environment template
├── .gitignore
├── composer.json                     # PHP dependencies + scripts
├── package.json                      # Node dependencies (Vite/Tailwind)
├── vite.config.js
├── tailwind.config.js
├── postcss.config.js
├── phpunit.xml                       # Test suite config
├── pint.json                         # Laravel Pint (code style)
├── phpstan.neon                      # Larastan static analysis config
└── README.md
```

### Layering at a glance

| Layer            | Responsibility                                   | Examples                              |
|------------------|--------------------------------------------------|---------------------------------------|
| Presentation     | Render UI, capture input, no business rules      | Livewire components, Blade views      |
| HTTP             | Routing, validation, SEO-friendly controllers    | `PostController`, `StorePostRequest`  |
| **Domain/Service** | **Pure business logic, unit-testable**         | `RelatedPostsService`, `MarkdownService` |
| Persistence      | Data mapping & relationships                     | Eloquent models, migrations           |

---

## 1.2 Implementation TODO List

### ✅ Phase 1 — Foundation (High Priority) — **COMPLETE**

- [x] Scaffold Laravel 11 app, configure PSR-4 autoload (`config/*`, `bootstrap/*`)
- [x] Configure SQLite connection + DB file (`database/database.sqlite`)
- [x] Install Livewire 3, Tailwind 3, Alpine.js via Vite
- [x] Build migrations: `users`, `categories`, `posts`, `tags`, `post_tag` (+ cache/jobs)
- [x] Implement core models with relationships + `slug` route binding
- [x] Authentication for admin (Livewire `Login`, rate-limited, single author)
- [x] Base layout (`layouts/app.blade.php`) + Tailwind design tokens + dark mode
- [x] CI pipeline: Pint + PHPStan + PHPUnit (`.github/workflows/ci.yml`)
- [x] `.env.example` complete; Forge deploy script wired (`scripts/deploy.sh`)

### 🟡 Phase 2 — Core Features (Medium Priority) — **COMPLETE**

- [x] `MarkdownService`: CommonMark + GFM extension, HTML sanitisation
- [x] `ReadingTimeService`: configurable WPM, image weighting
- [x] `PostEditor` Livewire component with **live** Markdown preview
- [x] `PostList` with search, category filter, pagination
- [x] `PostShow` rendering sanitised HTML + reading time badge
- [x] `RelatedPostsService`: TF-IDF vectorisation + cosine similarity
- [x] `SeoService`: title/meta/canonical, Open Graph, Twitter cards, JSON-LD
- [x] XML sitemap + `robots.txt` generation
- [x] Draft/published workflow with scheduled publishing (future `published_at`)
- [x] Feature + unit test coverage for services and post lifecycle (10 test classes)

### 🟢 Phase 3 — Polish & Optimisation (Lower Priority)

- [x] Cache TF-IDF related-post results (versioned invalidation via `PostObserver`)
- [x] RSS feed (`/feed`)
- [x] Dark mode (Tailwind `class` strategy + `localStorage`, no FOUC)
- [x] Scheduled cache warming (`blog:warm-related`, nightly)
- [x] Security headers middleware (CSP/HSTS/X-Frame-Options)
- [ ] Syntax highlighting for code blocks (Shiki/Torchlight at build or runtime)
- [ ] Image optimisation + responsive `srcset`, lazy loading
- [ ] Reading progress bar, "back to top", copy-code buttons (Alpine)
- [ ] Accessibility audit (WCAG AA), Lighthouse ≥ 95 across the board
- [ ] Comment system (or Webmentions) — optional
- [ ] Full-text search upgrade (Laravel Scout + SQLite FTS5)
- [ ] Observability: structured logs, error tracking (Sentry/Flare)

---

## Definition of Done (per feature)

1. Code passes **Pint** (style) and **PHPStan level 6+** (static analysis).
2. Unit tests for any service logic; feature test for any user-facing flow.
3. No N+1 queries (verified via Laravel Debugbar / `preventLazyLoading`).
4. SEO metadata present and validated for any new public page.
5. Documented in code (PHPDoc) and, where relevant, in `/docs`.
