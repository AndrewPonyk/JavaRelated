# Blog Platform

A personal blogging platform built with **Laravel 11**, **Livewire 3**, **SQLite**,
**Tailwind CSS**, and **Alpine.js**. Features Markdown authoring, SEO optimisation,
reading-time estimation, and TF-IDF-based related-post recommendations.

> Designed and implemented by **Opus 4.8**. See [`docs/`](docs/) for the full plan,
> architecture, and technical notes.

---

## Features

- ✍️ **Markdown authoring** with a live, server-rendered preview (Livewire)
- 🔒 **Safe rendering** — CommonMark with raw HTML disabled + HTML Purifier allow-list
- ⏱️ **Reading-time estimation** (configurable words-per-minute, image weighting)
- 🔗 **Related posts** via **TF-IDF** vectorisation + cosine similarity, cached
- 🔍 **SEO** — meta tags, Open Graph, Twitter cards, JSON-LD, `sitemap.xml`, `robots.txt`, RSS feed
- 🛠️ **Admin area** — authenticated dashboard, post CRUD (draft/publish/delete), category CRUD
- 🎨 **Tailwind Typography** article styling, **dark mode** (persisted)
- 🪶 **Single-file SQLite** for trivial deployment
- 🛡️ Security headers (CSP/HSTS/X-Frame-Options), rate-limited login, policies

## Tech Stack

| Concern        | Choice                          |
|----------------|---------------------------------|
| Language       | PHP 8.3                         |
| Framework      | Laravel 11                      |
| Interactivity  | Livewire 3 + Alpine.js          |
| Styling        | Tailwind CSS 3 (+ Typography)   |
| Database       | SQLite                          |
| Build          | Vite                            |
| Deploy / CI    | Laravel Forge + GitHub Actions  |

---

## Quick start — Docker (recommended)

The whole stack runs with one command. Requires Docker + Docker Compose.

```bash
docker compose up --build
```

This builds the image (PHP 8.3 + assets), runs migrations, **seeds demo content**,
and serves the blog at **http://localhost:8000**.

**Seeded admin login:** `author@example.com` / `password` → sign in at `/login`,
then visit `/admin`.

Run the test suite in the same image:

```bash
docker compose run --rm app php artisan test
```

## Quick start — local PHP

Requires PHP 8.3+ (with `pdo_sqlite`, `mbstring`, `dom`), Composer, and Node 20+.

```bash
# 1. Dependencies
composer install
npm install

# 2. Environment
cp .env.example .env
php artisan key:generate

# 3. Database (SQLite) + demo content
touch database/database.sqlite
php artisan migrate --seed

# 4. Assets + serve
npm run dev          # terminal 1 (Vite)
php artisan serve    # terminal 2 → http://localhost:8000
```

---

## Routes

| Path                     | What                                   | Auth |
|--------------------------|----------------------------------------|------|
| `/`                      | Article index (search, category filter)| —    |
| `/blog/{slug}`           | Single post + related posts            | —    |
| `/feed`                  | RSS 2.0 feed                           | —    |
| `/sitemap.xml`           | XML sitemap                            | —    |
| `/robots.txt`            | Robots directives                      | —    |
| `/login`                 | Admin sign-in                          | guest|
| `/admin`                 | Dashboard                              | auth |
| `/admin/posts`           | Manage posts (publish/delete)          | auth |
| `/admin/posts/create`    | New post (Markdown editor + preview)   | auth |
| `/admin/posts/{post}/edit` | Edit post                            | auth |
| `/admin/categories`      | Manage categories                      | auth |

## Quality gates

```bash
composer lint      # Laravel Pint (code style, --test)
composer analyse   # PHPStan / Larastan (level 6)
composer test      # PHPUnit (SQLite :memory:)
composer ci        # all of the above
```

## Configuration

App-specific tunables live in [`config/blog.php`](config/blog.php) (read from `.env`):

| Env var                    | Default | Purpose                                  |
|----------------------------|---------|------------------------------------------|
| `BLOG_WORDS_PER_MINUTE`    | 225     | Reading-speed for time estimates         |
| `BLOG_RELATED_POSTS_COUNT` | 3       | Number of related posts to show          |
| `BLOG_RELATED_CACHE_TTL`   | 86400   | Cache lifetime (s) for TF-IDF results    |
| `BLOG_EXCERPT_LENGTH`      | 160     | Excerpt / meta-description character budget|

Warm the related-posts cache (also scheduled nightly):

```bash
php artisan blog:warm-related
```

## Quality & reproducibility

- **Static analysis:** PHPStan/Larastan **level 6** — clean.
- **Style:** Laravel Pint (`declare(strict_types=1)` enforced) — clean.
- **Tests:** 55 tests, **~87% line coverage** (run `php artisan test --coverage`).
- **Reproducible installs:** `composer.lock` + `package-lock.json` are committed;
  `composer.json` pins `config.platform.php = 8.3.0` so resolution stays on the
  deploy target (prevents Symfony 8.x creep on newer local PHP).

## Security

- Markdown is rendered through CommonMark (raw HTML disabled) **and** HTML Purifier;
  JSON-LD is `JSON_HEX_TAG`-encoded so titles can't break out of the `<script>`.
- CSRF on all state-changing forms; Eloquent parameterises every query; passwords
  are Argon/bcrypt-hashed; security headers (CSP/HSTS/X-Frame-Options) on every response.
- **Known advisories:** `laravel/framework` 11.x carries 3 upstream advisories
  (signed-URL path confusion; CRLF-in-`email`-rule, CVE-2026-48019) that are
  **fixed only in Laravel 12**. Practical exposure here is low (no signed URLs;
  no user-supplied addresses flow into mail headers). `composer audit` surfaces
  them in CI (non-blocking). **Remediation:** upgrade to Laravel 12 when ready
  (`composer require laravel/framework:^12` + run the suite).

## Troubleshooting

| Symptom | Cause / Fix |
|---|---|
| `Vite manifest not found` | Assets not built — run `npm run build` (or `npm run dev`). In Docker the image builds them. |
| 500 on every page, `/up` works | Usually a DB path/permissions issue. Confirm `DB_DATABASE` is an **absolute** path and the file + its directory are writable. |
| `Database file … does not exist` | SQLite file missing — `touch database/database.sqlite` then `php artisan migrate`. |
| `composer install` blocks on Laravel 11 advisories | This environment's Composer blocks advisory-flagged packages. Install from the committed lock, or add `--no-security-blocking` (see `Dockerfile`). |
| Config changes not taking effect | Run `php artisan optimize:clear` (cached config/routes/views). |
| Livewire actions 419 / page expired | Stale CSRF token — hard refresh; ensure `SESSION_DRIVER` storage (table/redis) is reachable. |
| Related posts not updating | Cache is versioned; it busts on post save. Force with `php artisan blog:warm-related`. |

## Project layout

See [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) for the annotated file tree and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for diagrams and design rationale.

## License

MIT
