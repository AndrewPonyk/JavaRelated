FROM golang:1.22-alpine AS build

WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN go build -o /out/devopsctl ./cmd/devopsctl

FROM alpine:3.20
RUN adduser -D -H devopsctl
USER devopsctl
WORKDIR /workspace
COPY --from=build /out/devopsctl /usr/local/bin/devopsctl
ENTRYPOINT ["devopsctl"]
