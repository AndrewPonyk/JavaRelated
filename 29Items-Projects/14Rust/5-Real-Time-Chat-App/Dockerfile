# syntax=docker/dockerfile:1.7
FROM rust:1.88-bookworm AS builder
WORKDIR /build

COPY Cargo.toml Cargo.lock ./
COPY backend/Cargo.toml backend/Cargo.toml
COPY shared/Cargo.toml shared/Cargo.toml
COPY backend/src backend/src
COPY shared/src shared/src
RUN --mount=type=cache,target=/usr/local/cargo/registry,sharing=locked \
    --mount=type=cache,target=/build/target,sharing=locked \
    cargo build --locked --release --bin chat-server \
    && cp /build/target/release/chat-server /build/chat-server

FROM debian:bookworm-slim AS runtime
RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system chat \
    && useradd --system --gid chat --home-dir /app chat

WORKDIR /app
COPY --from=builder --chown=chat:chat /build/chat-server /app/chat-server
COPY --chown=chat:chat frontend /app/frontend
COPY --chown=chat:chat migrations /app/migrations

USER chat
ENV APP_HOST=0.0.0.0 \
    APP_PORT=8080 \
    STATIC_DIR=/app/frontend \
    MIGRATIONS_DIR=/app/migrations
EXPOSE 8080
ENTRYPOINT ["/app/chat-server"]
