#!/usr/bin/env bash
# Update ECS services to the new image tag. With --blue-green, drive a
# CodeDeploy deployment that shifts traffic and auto-rolls-back on alarm.
# Usage: deploy_ecs.sh <environment> <image_tag> [--blue-green]
set -euo pipefail

ENVIRONMENT="${1:?environment required}"
TAG="${2:?image tag required}"
MODE="${3:-rolling}"

echo "TODO: deploy $ENVIRONMENT with tag $TAG (mode: $MODE)"
# TODO: register new task definition revision; update services; wait for
# services-stable; on failure, roll back to previous revision.
