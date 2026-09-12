# Static Site Generator - Project Plan

## 1.1 Project File Structure

The project is organized as a layered Ruby/Sinatra application that can run as a local build tool, expose a small API for preview/search, and deploy static output to AWS S3 and CloudFront.

```text
.
+-- .env.example
+-- .github/
|   +-- workflows/
|       +-- ci.yml
+-- .gitignore
+-- .rubocop.yml
+-- .ruby-version
+-- Dockerfile
+-- docker-compose.yml
+-- Gemfile
+-- README.md
+-- Rakefile
+-- app.rb
+-- config.ru
+-- app/
|   +-- configuration.rb
|   +-- components/
|   |   +-- search_component.erb
|   +-- errors.rb
|   +-- helpers/
|   |   +-- view_helpers.rb
|   +-- models/
|   |   +-- page.rb
|   +-- repositories/
|   |   +-- page_repository.rb
|   +-- routes/
|   |   +-- api_routes.rb
|   |   +-- site_routes.rb
|   +-- services/
|       +-- feed_generator.rb
|       +-- keyword_extractor.rb
|       +-- link_validator.rb
|       +-- markdown_parser.rb
|       +-- redirect_manager.rb
|       +-- s3_deployer.rb
|       +-- search_indexer.rb
|       +-- site_builder.rb
|       +-- sitemap_generator.rb
|       +-- template_renderer.rb
|       +-- version_index.rb
+-- build/
|   +-- .gitkeep
+-- config/
|   +-- environments.yml
|   +-- settings.yml
+-- content/
|   +-- docs/
|       +-- v1/
|       |   +-- getting-started.md
|       +-- v2/
|           +-- getting-started.md
+-- data/
|   +-- redirects.yml
+-- deploy/
|   +-- cloudfront-invalidation.json
|   +-- s3-policy.json
+-- docs/
|   +-- ARCHITECTURE.md
|   +-- API.md
|   +-- PROJECT-PLAN.md
|   +-- TECH-NOTES.md
+-- migrations/
|   +-- 001_create_content_metadata.sql
+-- public/
|   +-- assets/
|   |   +-- css/
|   |   |   +-- site.css
|   |   +-- js/
|   |       +-- search_component.js
|   +-- search/
|       +-- index.json
+-- scripts/
|   +-- build.rb
|   +-- deploy.rb
|   +-- keyword_extract.rb
|   +-- migrate.rb
|   +-- validate.rb
+-- spec/
|   +-- routes/
|   |   +-- api_routes_spec.rb
|   +-- services/
|   |   +-- keyword_extractor_spec.rb
|   |   +-- markdown_parser_spec.rb
|   |   +-- page_repository_spec.rb
|   |   +-- s3_deployer_spec.rb
|   |   +-- search_indexer_spec.rb
|   |   +-- site_builder_spec.rb
|   |   +-- template_renderer_spec.rb
|   |   +-- version_index_spec.rb
|   +-- spec_helper.rb
+-- templates/
    +-- layouts/
    |   +-- default.erb
    +-- partials/
    |   +-- nav.erb
    +-- page.erb
    +-- search.erb
```

### Source Code Organization

- `app.rb` is the Sinatra application entry point.
- `app/routes` owns HTTP route definitions and API boundaries.
- `app/services` owns build, parse, render, search, keyword extraction, and deployment workflows.
- `app/models` contains lightweight domain models.
- `app/repositories` owns Markdown-backed CRUD and SQLite metadata persistence.
- `app/helpers` exposes presentation helpers for ERB templates.
- `app/components` contains reusable ERB view fragments for server-rendered UI.
- `content` stores Markdown source files with YAML front matter.
- `templates` stores ERB layouts, pages, and partials used during static generation.
- `public` stores browser assets and local preview fallback search artifacts.
- `migrations` contains SQLite metadata/search cache schema files for persistent content indexes and build reports.

### CI/CD Organization

- `.github/workflows/ci.yml` runs linting, tests, build validation, and deployment gates.
- `scripts/build.rb` is the build command used locally and in CI.
- `scripts/deploy.rb` deploys generated output to S3 and optionally invalidates CloudFront.
- `deploy/s3-policy.json` documents the expected bucket policy shape.
- `deploy/cloudfront-invalidation.json` documents invalidation payloads.

### Tools Configuration

- `.ruby-version` pins Ruby 3.3 for local and CI parity.
- `Gemfile` defines Sinatra, Markdown, YAML/front matter, AWS SDK, test, and lint dependencies.
- `.rubocop.yml` captures formatting and linting defaults.
- `.env.example` documents required runtime and deployment settings.
- `Dockerfile` provides a reproducible build and preview container.
- `config/settings.yml` and `config/environments.yml` separate application defaults from environment-specific overrides.

## 1.2 Implementation Checklist

### Phase 1: Foundation - High Priority

- [x] Finalize Ruby 3.3 dependency versions.
- [x] Implement Markdown parsing with YAML front matter validation.
- [x] Implement ERB template rendering with layout and partial support.
- [x] Build static output into `build/` with deterministic file paths.
- [x] Add Sinatra preview routes for generated pages and search API.
- [x] Add RSpec coverage for parser, builder, and API route behavior.
- [x] Configure GitHub Actions for lint, test, and build validation.
- [x] Define `.env.example` and required AWS deployment settings.

### Phase 2: Core Features - Medium Priority

- [x] Generate version indexes from `content/docs/<version>/`.
- [x] Generate client-side search index JSON during build.
- [x] Implement keyword extraction for content tagging and search boosting.
- [x] Add redirects and canonical URL support.
- [x] Add S3 upload with cache-control policies by asset type.
- [x] Add CloudFront invalidation after production deployment.
- [x] Add integration tests for full build output.
- [x] Add preview UI for switching documentation versions.

### Phase 3: Polish & Optimization - Lower Priority

- [x] Add incremental build support based on content checksums.
- [x] Add broken-link and missing-asset validation.
- [x] Add sitemap and RSS generation.
- [x] Add search result highlighting and keyboard navigation.
- [x] Add build telemetry and structured logs.
- [x] Tune CloudFront cache policies for HTML, assets, and search indexes.
- [x] Add accessible labels and live regions for generated templates.
- [x] Document authoring conventions for front matter and Markdown.
