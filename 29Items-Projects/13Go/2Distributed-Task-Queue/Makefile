.PHONY: fmt vet test coverage build run-api run-worker run-scheduler compose-up compose-down

fmt:
	gofmt -w .

vet:
	go vet ./...

test:
	go test ./...

coverage:
	go test ./... -race
	go test ./internal/service ./internal/worker ./tests/integration -coverpkg=./internal/api,./internal/service,./internal/worker -coverprofile=coverage.out -covermode=atomic
	go tool cover -func=coverage.out
	@total=$$(go tool cover -func=coverage.out | awk '/total:/ {print substr($$3, 1, length($$3)-1)}'); awk -v total="$$total" 'BEGIN { if (total < 80) exit 1 }'

build:
	go build -o bin/api ./cmd/api
	go build -o bin/worker ./cmd/worker
	go build -o bin/scheduler ./cmd/scheduler

run-api:
	go run ./cmd/api

run-worker:
	go run ./cmd/worker

run-scheduler:
	go run ./cmd/scheduler

compose-up:
	docker compose up --build

compose-down:
	docker compose down
