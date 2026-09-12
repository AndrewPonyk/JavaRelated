.PHONY: dev test lint migrate-up migrate-down docker-build

dev:
	docker compose up --build

test:
	go test ./cmd/... ./internal/... ./tests/integration

lint:
	gofmt -w $$(find cmd internal tests -name '*.go')
	go vet ./cmd/... ./internal/... ./tests/integration

migrate-up:
	./scripts/migrate.sh up

migrate-down:
	./scripts/migrate.sh down

docker-build:
	docker build -t url-shortener-service:local .
