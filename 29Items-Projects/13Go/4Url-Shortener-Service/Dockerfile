FROM golang:1.26-alpine AS backend-builder

WORKDIR /app
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /bin/url-shortener ./cmd/api

FROM alpine:3.20

RUN adduser -D -H appuser
WORKDIR /app
COPY --from=backend-builder /bin/url-shortener /app/url-shortener
COPY migrations /app/migrations

USER appuser
EXPOSE 8080
CMD ["/app/url-shortener"]
