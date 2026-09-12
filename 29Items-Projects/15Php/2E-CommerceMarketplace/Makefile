# -----------------------------------------------------------------------------
#  Developer task runner. Run `make help` for the list of targets.
# -----------------------------------------------------------------------------
.DEFAULT_GOAL := help
SHELL := /bin/bash

BACKEND  := backend
FRONTEND := frontend

## ---- bootstrap -------------------------------------------------------------
.PHONY: up
up: ## Start infra (postgres, rabbitmq, elasticsearch, ml-service)
	docker compose up -d

.PHONY: down
down: ## Stop infra
	docker compose down

.PHONY: backend-install
backend-install: ## Install PHP deps, generate JWT keys, run migrations, create the search index
	cd $(BACKEND) && composer install
	cd $(BACKEND) && php bin/console lexik:jwt:generate-keypair --skip-if-exists
	cd $(BACKEND) && php bin/console doctrine:migrations:migrate --no-interaction
	cd $(BACKEND) && php bin/console app:search:create-index || true

.PHONY: frontend-install
frontend-install: ## Install JS deps
	cd $(FRONTEND) && npm ci

## ---- dev -------------------------------------------------------------------
.PHONY: dev
dev: ## Run API + SPA + async worker concurrently
	$(MAKE) -j3 backend-serve frontend-serve worker

.PHONY: backend-serve
backend-serve:
	cd $(BACKEND) && symfony server:start --port=8000 || php -S localhost:8000 -t public

.PHONY: frontend-serve
frontend-serve:
	cd $(FRONTEND) && npm run dev

.PHONY: worker
worker: ## Consume async Messenger transport (projections, fraud checks, emails)
	cd $(BACKEND) && php bin/console messenger:consume async --time-limit=3600 -vv

## ---- quality ---------------------------------------------------------------
.PHONY: lint
lint: ## Static analysis + code style (backend) and eslint (frontend)
	cd $(BACKEND) && vendor/bin/php-cs-fixer fix --dry-run --diff
	cd $(BACKEND) && vendor/bin/phpstan analyse
	cd $(FRONTEND) && npm run lint

.PHONY: test
test: ## Run all test suites
	cd $(BACKEND) && vendor/bin/phpunit
	cd $(FRONTEND) && npm run test -- --run
	cd ml-service && pytest -q

.PHONY: fix
fix: ## Auto-fix code style
	cd $(BACKEND) && vendor/bin/php-cs-fixer fix
	cd $(FRONTEND) && npm run lint -- --fix

## ---- help ------------------------------------------------------------------
.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
