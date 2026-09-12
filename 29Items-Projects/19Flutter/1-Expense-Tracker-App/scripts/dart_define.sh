#!/usr/bin/env bash
# .env -> --dart-define-from-file bridge (docs/TECH-NOTES.md §3.4).
#
# Flutter's --dart-define-from-file accepts a .env-style file directly, so this
# script's real job is just validating one exists before handing it to `flutter`
# and forwarding whatever subcommand/flags the caller wants — it is a guard, not
# a parser.
#
# Usage:
#   scripts/dart_define.sh run --flavor dev
#   scripts/dart_define.sh build appbundle --flavor prod
#   ENV_FILE=.env.staging scripts/dart_define.sh build ipa --flavor staging
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "error: $ENV_FILE not found." >&2
  echo "Copy .env.example to $ENV_FILE and fill in real values first." >&2
  exit 1
fi

if [[ "$#" -eq 0 ]]; then
  echo "usage: $0 <flutter-subcommand> [flutter-args...]" >&2
  echo "example: $0 run --flavor dev" >&2
  exit 1
fi

echo "Using $ENV_FILE" >&2
exec flutter "$1" --dart-define-from-file="$ENV_FILE" "${@:2}"
