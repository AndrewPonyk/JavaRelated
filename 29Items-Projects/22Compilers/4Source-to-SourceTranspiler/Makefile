.PHONY: check test build dev

check:
	cargo fmt --all -- --check
	cargo clippy --workspace --all-targets -- -D warnings

test:
	cargo test --workspace

build:
	cargo build --workspace

dev:
	docker compose up --build
