#!/usr/bin/env bash
# Run Django migrations as a one-off ECS task BEFORE the new service revision
# goes live. Migrations must be backward-compatible (expand/contract).
# Usage: run_migrations.sh <environment> <image_tag>
set -euo pipefail

ENVIRONMENT="${1:?environment required}"
TAG="${2:?image tag required}"

# TODO: aws ecs run-task with the migrate command override, then wait for it
# to reach STOPPED and assert exitCode == 0.
echo "TODO: run 'python manage.py migrate' on $ENVIRONMENT using tag $TAG"
