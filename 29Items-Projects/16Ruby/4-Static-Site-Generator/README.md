# Static Site Generator

A Ruby 3.3 and Sinatra documentation site generator. It reads versioned Markdown files with YAML front matter, renders ERB templates, builds static HTML, creates search/version metadata, validates links, and deploys the final `build/` directory to AWS S3 and CloudFront.

## What It Generates

Source content such as:

```text
content/docs/v2/getting-started.md
```

generates:

```text
build/docs/v2/getting-started.html
build/search/index.json
build/versions.json
build/sitemap.xml
build/feed.xml
build/manifest.json
```

## Requirements

- Ruby 3.3
- Bundler
- SQLite
- Docker and Docker Compose for containerized usage
- AWS credentials for deployment

## Local Setup

```bash
bundle install
cp .env.example .env
bundle exec rake migrate
bundle exec rake build
bundle exec rackup --host 0.0.0.0 --port 4567
```

Open `http://localhost:4567`.

## Docker Setup

```bash
docker compose up --build
```

The container runs migrations, builds the static output, then starts the preview app at `http://localhost:4567`.

After the app is already running, rebuild the generated HTML with:

```bash
docker compose exec app bundle exec ruby scripts/build.rb
```

This writes output back to your local `build/` folder because the project directory is mounted into the container.

Run the full validation suite in Docker:

```bash
docker compose run --rm test
```

## Test And Build

```bash
bundle exec rake
```

This runs linting, tests, static build generation, and build validation.

The test suite enforces at least 80% coverage. CI runs the same lint, test, build, and validation stages.

## Authoring Content

Create Markdown files under `content/docs/<version>/`.

```md
---
title: Getting Started
slug: getting-started
description: Current documentation entry point.
tags:
  - guide
---

# Getting Started

Markdown body content.
```

Required front matter:

- `title`
- `slug`

The version is inferred from the folder name, for example `content/docs/v2`.

The URL uses the `slug`, not necessarily the filename. For example:

```text
content/docs/v3/ekopfo_v18.md
```

with:

```yaml
slug: markdown-ecopfo
```

is available at:

```text
http://localhost:4567/docs/v3/markdown-ecopfo
```

The generated static file is:

```text
build/docs/v3/markdown-ecopfo.html
```

Browse all documentation pages at:

```text
http://localhost:4567/docs
```

Browse one version at:

```text
http://localhost:4567/docs/v3
```

Typical Docker workflow:

```bash
docker compose up --build
# edit or add Markdown files in content/docs/<version>/
docker compose exec app bundle exec ruby scripts/build.rb
```

## API

See [docs/API.md](docs/API.md).

The API is intended for local preview and controlled authoring workflows. In `APP_ENV=production`, mutating API
requests require `Authorization: Bearer <API_AUTH_TOKEN>`.

## Deployment

Configure `.env` or GitHub Actions secrets:

```bash
AWS_REGION=us-east-1
AWS_S3_BUCKET=example-docs-bucket
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
CLOUDFRONT_DISTRIBUTION_ID=...
API_AUTH_TOKEN=...
```

Then run:

```bash
bundle exec rake deploy
```

Production hosting is static: S3 stores files and CloudFront serves them.

## Troubleshooting

- `bundle: command not found`: install Ruby 3.3 and Bundler, or use the Docker commands.
- Docker cannot connect to `dockerDesktopLinuxEngine`: start Docker Desktop and ensure the Linux engine is running.
- `Content-Type must be application/json`: send JSON write requests with `Content-Type: application/json`.
- `Unauthorized` in production: set `API_AUTH_TOKEN` and send it as a Bearer token for write/build API calls.
- Broken links during `rake validate`: inspect the error details and add the missing generated page or fix the link.
