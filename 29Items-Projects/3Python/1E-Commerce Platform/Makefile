# Makefile for E-Commerce Platform
.PHONY: help dev prod stop logs clean test lint format

# Default target
help:
	@echo "E-Commerce Platform - Development Commands"
	@echo ""
	@echo "Development:"
	@echo "  make dev          - Start development environment"
	@echo "  make dev-build    - Rebuild and start development environment"
	@echo "  make stop         - Stop all containers"
	@echo "  make logs         - View container logs"
	@echo "  make shell        - Open Django shell"
	@echo ""
	@echo "Production:"
	@echo "  make prod         - Start production environment"
	@echo "  make prod-build   - Rebuild and start production"
	@echo ""
	@echo "Testing:"
	@echo "  make test         - Run backend tests"
	@echo "  make test-cov     - Run tests with coverage"
	@echo "  make lint         - Run linters"
	@echo "  make format       - Format code"
	@echo ""
	@echo "Database:"
	@echo "  make migrate      - Run database migrations"
	@echo "  make makemigrations - Create new migrations"
	@echo ""
	@echo "Cleanup:"
	@echo "  make clean        - Remove containers and volumes"
	@echo "  make clean-all    - Remove everything including images"

# Development
dev:
	@echo "Starting development environment..."
	@cp -n .env.example .env 2>/dev/null || true
	docker-compose -f docker-compose.dev.yml up

dev-build:
	@echo "Building and starting development environment..."
	@cp -n .env.example .env 2>/dev/null || true
	docker-compose -f docker-compose.dev.yml up --build

dev-detached:
	@echo "Starting development environment in background..."
	@cp -n .env.example .env 2>/dev/null || true
	docker-compose -f docker-compose.dev.yml up -d

# Production
prod:
	docker-compose up

prod-build:
	docker-compose up --build

# Stop containers
stop:
	docker-compose -f docker-compose.dev.yml down
	docker-compose down

# Logs
logs:
	docker-compose -f docker-compose.dev.yml logs -f

logs-backend:
	docker-compose -f docker-compose.dev.yml logs -f backend

logs-frontend:
	docker-compose -f docker-compose.dev.yml logs -f frontend

logs-celery:
	docker-compose -f docker-compose.dev.yml logs -f celery-worker

# Shell access
shell:
	docker-compose -f docker-compose.dev.yml exec backend python manage.py shell

bash:
	docker-compose -f docker-compose.dev.yml exec backend bash

# Database
migrate:
	docker-compose -f docker-compose.dev.yml exec backend python manage.py migrate

makemigrations:
	docker-compose -f docker-compose.dev.yml exec backend python manage.py makemigrations

# Testing
test:
	docker-compose -f docker-compose.dev.yml exec backend pytest

test-cov:
	docker-compose -f docker-compose.dev.yml exec backend pytest --cov=apps --cov-report=html

# Linting
lint:
	docker-compose -f docker-compose.dev.yml exec backend flake8 apps/
	docker-compose -f docker-compose.dev.yml exec frontend npm run lint

format:
	docker-compose -f docker-compose.dev.yml exec backend black apps/
	docker-compose -f docker-compose.dev.yml exec frontend npm run format

# Cleanup
clean:
	docker-compose -f docker-compose.dev.yml down -v
	docker-compose down -v

clean-all:
	docker-compose -f docker-compose.dev.yml down -v --rmi all
	docker-compose down -v --rmi all
