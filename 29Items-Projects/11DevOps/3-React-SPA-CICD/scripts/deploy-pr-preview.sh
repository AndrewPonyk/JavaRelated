#!/usr/bin/env bash
# Deploys dist/ to the PR-preview bucket under /pr-<n>/ and invalidates CloudFront.
# Called by .github/workflows/pr-preview.yml. Requires AWS credentials in the environment
# (OIDC-assumed role in CI). Docs: docs/TECH-NOTES.md §3.3.
set -euo pipefail

PR_NUMBER="${1:?usage: deploy-pr-preview.sh <pr-number>}"
: "${PREVIEW_BUCKET:?PREVIEW_BUCKET is required}"
: "${PREVIEW_CF_DISTRIBUTION_ID:?PREVIEW_CF_DISTRIBUTION_ID is required}"
: "${PREVIEW_DOMAIN:?PREVIEW_DOMAIN is required}"

PREFIX="pr-${PR_NUMBER}"

if [ ! -f dist/index.html ]; then
  echo "::error::dist/index.html not found — run the build first." >&2
  exit 1
fi

echo "Deploying preview for PR #${PR_NUMBER} to s3://${PREVIEW_BUCKET}/${PREFIX}/"

# The caching contract (docs/ARCHITECTURE.md §2.4):
#  1) content-hashed assets → cache forever
aws s3 sync dist/assets "s3://${PREVIEW_BUCKET}/${PREFIX}/assets" \
  --delete \
  --cache-control "public,max-age=31536000,immutable"

#  2) everything else (index.html, favicon, worker) → always revalidate
aws s3 sync dist "s3://${PREVIEW_BUCKET}/${PREFIX}" \
  --delete \
  --exclude "assets/*" \
  --cache-control "no-cache"

# Invalidate only this PR's prefix — keeps invalidation cost flat.
aws cloudfront create-invalidation \
  --distribution-id "${PREVIEW_CF_DISTRIBUTION_ID}" \
  --paths "/${PREFIX}/*" >/dev/null

URL="https://${PREVIEW_DOMAIN}/${PREFIX}/"
echo "Preview live at ${URL}"

# Expose the URL to later workflow steps (PR comment).
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "url=${URL}" >>"${GITHUB_OUTPUT}"
fi
