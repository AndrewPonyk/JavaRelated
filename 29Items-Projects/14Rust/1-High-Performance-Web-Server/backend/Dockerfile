FROM rust:latest as builder
WORKDIR /usr/src/app
COPY . .
# We need to install libpq-dev for diesel postgres
RUN apt-get update && apt-get install -y libpq-dev
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y libpq-dev ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=builder /usr/src/app/target/release/high-performance-web-server /usr/local/bin/high-performance-web-server
EXPOSE 8080
CMD ["high-performance-web-server"]
