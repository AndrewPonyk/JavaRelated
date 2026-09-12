BINARY_NAME := devopsctl
PKG := ./...

.PHONY: fmt lint test build clean

fmt:
	gofmt -w ./cmd ./internal

lint:
	go vet $(PKG)
	golangci-lint run

test:
	go test $(PKG)

build:
	go build -o bin/$(BINARY_NAME) ./cmd/devopsctl

clean:
	go clean
	rm -rf bin dist coverage.out
