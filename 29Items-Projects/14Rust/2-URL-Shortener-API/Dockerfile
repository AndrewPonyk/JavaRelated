# syntax=docker/dockerfile:1

FROM rust:1.88-bookworm AS builder
WORKDIR /app
ENV CARGO_HTTP_TIMEOUT=120 CARGO_NET_RETRY=5
COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY migrations ./migrations
RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/app/target \
    cargo build --release --locked && \
    cp target/release/url-shortener-api /tmp/url-shortener-api

FROM busybox:1.37.0-musl AS utilities

FROM debian:bookworm-slim
RUN mkdir /data && chown 65534:65534 /data
COPY --from=utilities /bin/busybox /usr/local/bin/busybox
COPY --from=builder /tmp/url-shortener-api /usr/local/bin/url-shortener-api
ENV BIND_ADDRESS=0.0.0.0:8080 DATABASE_URL=/data/url_shortener.db
EXPOSE 8080
USER 65534:65534
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 CMD ["busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/health"]
CMD ["url-shortener-api"]
