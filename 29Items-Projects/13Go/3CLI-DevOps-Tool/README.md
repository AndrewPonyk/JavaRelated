# CLI DevOps Tool

Developer productivity CLI for DevOps automation, local task tracking, Docker/AWS operations, and simple pattern-based test suggestions.

## Quick Start

```powershell
go mod tidy
go run ./cmd/devopsctl --help
go run ./cmd/devopsctl tasks create --title "Run CI locally"
go run ./cmd/devopsctl tasks list
```

## Configuration

Copy `.env.example` or `configs/config.example.yaml` for local development. Runtime configuration is loaded by Viper from flags, environment variables, and config files.

## Current State

This repository is scaffolded with production-oriented boundaries and stub implementations. TODO comments mark the areas where real Docker, AWS, release, and suggestion workflows should be expanded.
