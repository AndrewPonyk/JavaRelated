#!/usr/bin/env sh
set -eu

direction="${1:-up}"

if [ -z "${DATABASE_URL:-}" ]; then
  echo "DATABASE_URL is required"
  exit 1
fi

migrate -path migrations -database "$DATABASE_URL" "$direction"

