# Project Management Platform

Jira-like project management built on Rails 7 + Hotwire, with ML-driven sprint planning.

## Features
- Projects with role-scoped memberships (admin / member / viewer) via Pundit
- Issues with statuses, priorities, assignees, estimates, full-text search (pg_search)
- Sprints with workload predictions served by an async Sidekiq job
- Live board with Turbo Streams + Stimulus drag-and-drop (Sortable.js)
- Time tracking per issue
- REST API (v1) with Bearer-token auth
- Rate limiting (rack-attack), Sentry error monitoring, lograge JSON logs
- ActionCable over Redis for real-time board sync

## Quick Start (Docker)

```bash
docker-compose up --build
```

Then open http://localhost:3000. Seed login:
- **admin@example.com / password123** (admin)
- **alice@example.com / password123** (member)

## Quick Start (Local)

```bash
bundle install
cp .env.example .env
bin/rails db:prepare db:seed
bin/dev    # Rails + Sidekiq + Tailwind watcher via Foreman
```

## Testing

```bash
bundle exec rspec               # full suite
bundle exec rspec spec/models   # models only
bundle exec rubocop             # lint
bundle exec brakeman -z         # security scan
```

Coverage report is generated in `coverage/` (SimpleCov, 70% floor).

## API

```bash
# List issues for a project
curl -H "Authorization: Bearer $API_TOKEN" \
     http://localhost:3000/api/v1/projects/1/issues

# Create an issue
curl -X POST -H "Authorization: Bearer $API_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"issue":{"title":"Fix it","status":"todo","priority":"high"}}' \
     http://localhost:3000/api/v1/projects/1/issues
```

Tokens are auto-generated on `User` create and accessible via `user.api_token`.

## Deployment

Production is deployed to Heroku via GitHub Actions:
- Push to `main` → auto-deploys to staging via `.github/workflows/deploy-staging.yml`
- Manual run of `deploy-production.yml` (with "DEPLOY" confirmation) promotes to production
- Release phase runs `rails db:migrate` atomically before new dynos boot
- `strong_migrations` gem enforces online-safe migrations

## Docs
- [Project Plan & Roadmap](docs/PROJECT-PLAN.md)
- [Architecture](docs/ARCHITECTURE.md) — diagrams, data flow, scaling, security
- [Technical Notes](docs/TECH-NOTES.md) — CI/CD, testing, pitfalls

## Stack
Ruby 3.3 · Rails 7.1 · PostgreSQL 15 · Redis 7 · Sidekiq · ActionCable · Hotwire (Turbo + Stimulus) · Tailwind CSS · Heroku · GitHub Actions · RSpec + Capybara · Sentry
