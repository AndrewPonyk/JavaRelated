#!/usr/bin/env bash
# Promote the exact image already validated in staging to the production ECR
# (build once, deploy many — TECH-NOTES §3.1). Usage: promote_image.sh <tag>
set -euo pipefail

TAG="${1:?tag required}"
echo "TODO: re-tag staging image '$TAG' into the production ECR repository"
