# Project Management Platform — Project Plan

## Overview
A Jira-like project management platform built on Rails 7 with Hotwire for SPA-like UX. Supports sprints, issues, time tracking, and ML-based workload prediction for sprint planning optimization.

**Stack:** Ruby 3.3, Rails 7, PostgreSQL, Redis, Sidekiq, ActionCable, Hotwire (Turbo + Stimulus)
**Deployment:** Heroku + GitHub Actions CI/CD

---

## 1.1 Project File Structure

```
1Project-Management-Platform/
├── .github/
│   └── workflows/
│       ├── ci.yml                     # Lint, test, security scan on PR
│       ├── deploy-staging.yml         # Auto-deploy to Heroku staging on main
│       └── deploy-production.yml      # Manual deploy to production
├── app/
│   ├── controllers/
│   │   ├── application_controller.rb
│   │   ├── issues_controller.rb
│   │   ├── sprints_controller.rb
│   │   ├── projects_controller.rb
│   │   ├── time_entries_controller.rb
│   │   └── api/
│   │       └── v1/
│   │           ├── base_controller.rb
│   │           └── issues_controller.rb
│   ├── models/
│   │   ├── user.rb
│   │   ├── project.rb
│   │   ├── sprint.rb
│   │   ├── issue.rb
│   │   ├── time_entry.rb
│   │   └── workload_prediction.rb
│   ├── services/
│   │   ├── sprint_planner.rb
│   │   ├── workload_predictor.rb      # ML inference wrapper
│   │   └── issue_state_machine.rb
│   ├── jobs/
│   │   ├── application_job.rb
│   │   ├── workload_prediction_job.rb
│   │   └── sprint_metrics_job.rb
│   ├── channels/
│   │   ├── application_cable/
│   │   └── issue_board_channel.rb     # Real-time board updates
│   ├── javascript/
│   │   ├── application.js
│   │   └── controllers/               # Stimulus controllers
│   │       ├── board_controller.js
│   │       ├── issue_card_controller.js
│   │       └── time_tracker_controller.js
│   ├── views/
│   │   ├── layouts/application.html.erb
│   │   ├── issues/
│   │   └── sprints/
│   └── policies/                      # Pundit authorization
├── config/
│   ├── application.rb
│   ├── database.yml
│   ├── cable.yml
│   ├── sidekiq.yml
│   ├── routes.rb
│   └── initializers/
│       ├── sidekiq.rb
│       ├── redis.rb
│       └── sentry.rb
├── db/
│   ├── migrate/
│   ├── schema.rb
│   └── seeds.rb
├── lib/
│   ├── ml/
│   │   └── workload_model_client.rb   # External ML service HTTP client
│   └── tasks/
├── spec/                              # RSpec tests
│   ├── models/
│   ├── controllers/
│   ├── services/
│   ├── system/                        # Capybara system specs
│   └── rails_helper.rb
├── docs/
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
├── bin/
├── .env.example
├── .rubocop.yml
├── Gemfile
├── Procfile                           # Heroku process types
├── Dockerfile                         # Optional: container builds
└── README.md
```

---

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)
- [ ] Bootstrap Rails 7 app with PostgreSQL and Redis
- [ ] Set up Devise for authentication
- [ ] Configure Sidekiq + Redis for background jobs
- [ ] Configure ActionCable with Redis adapter for production
- [ ] Implement core models: User, Project, Sprint, Issue, TimeEntry
- [ ] Set up Pundit for authorization (project-scoped access)
- [ ] Configure RSpec, FactoryBot, Capybara
- [ ] Set up GitHub Actions CI (rubocop, brakeman, rspec)
- [ ] Configure Heroku staging pipeline
- [ ] Add Sentry for error monitoring

### Phase 2: Core Features (Medium Priority)
- [ ] Issue board with Turbo Streams for live updates
- [ ] Sprint planning UI with drag-and-drop (Stimulus + Sortable.js)
- [ ] Time tracking with start/stop timer
- [ ] ActionCable channel for real-time board synchronization
- [ ] REST API (v1) for mobile/integrations with token auth
- [ ] Sprint burndown charts (Chartkick)
- [ ] Email notifications (ActionMailer + Sidekiq)
- [ ] Full-text search (pg_search gem)
- [ ] ML workload predictor service integration
- [ ] Sprint planning optimization algorithm

### Phase 3: Polish & Optimization (Lower Priority)
- [ ] N+1 query audit with Bullet
- [ ] Fragment caching for issue cards
- [ ] Russian Doll caching for sprint views
- [ ] Database query optimization and indexing review
- [ ] Rate limiting (rack-attack)
- [ ] Audit logging (paper_trail)
- [ ] Slack/webhook integrations
- [ ] PDF report exports
- [ ] Accessibility audit (WCAG 2.1 AA)
- [ ] Performance monitoring (Skylight or New Relic)
- [ ] Production load testing
