#!/usr/bin/env bash
# Build backend + frontend images and push to ECR, tagged by git SHA.
# Usage: build_and_push.sh <git_sha>
set -euo pipefail

SHA="${1:?git sha required}"
ECR_REGISTRY="${ECR_REGISTRY:?set ECR_REGISTRY}"

# TODO: implement
#   docker build -t "$ECR_REGISTRY/ecommerce-backend:$SHA" ./backend
#   docker build -t "$ECR_REGISTRY/ecommerce-frontend:$SHA" ./frontend
#   docker push "$ECR_REGISTRY/ecommerce-backend:$SHA"
#   docker push "$ECR_REGISTRY/ecommerce-frontend:$SHA"
echo "TODO: build & push images for $SHA to $ECR_REGISTRY"
