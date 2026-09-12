#!/usr/bin/env bash
# Removes a PR preview (S3 prefix + CloudFront invalidation).
# Called by .github/workflows/pr-preview-cleanup.yml on PR close.
set -euo pipefail

PR_NUMBER="${1:?usage: teardown-pr-preview.sh <pr-number>}"
: "${PREVIEW_BUCKET:?PREVIEW_BUCKET is required}"
: "${PREVIEW_CF_DISTRIBUTION_ID:?PREVIEW_CF_DISTRIBUTION_ID is required}"

PREFIX="pr-${PR_NUMBER}"

echo "Tearing down preview for PR #${PR_NUMBER} (s3://${PREVIEW_BUCKET}/${PREFIX}/)"

aws s3 rm "s3://${PREVIEW_BUCKET}/${PREFIX}" --recursive

aws cloudfront create-invalidation \
  --distribution-id "${PREVIEW_CF_DISTRIBUTION_ID}" \
  --paths "/${PREFIX}/*" >/dev/null

echo "Preview ${PREFIX} removed."
