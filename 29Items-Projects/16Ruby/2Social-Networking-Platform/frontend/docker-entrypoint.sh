#!/bin/sh
set -eu

GRAPHQL_ENDPOINT="${GRAPHQL_ENDPOINT:-${VITE_GRAPHQL_ENDPOINT:-http://localhost:3000/graphql}}"
escaped_endpoint="$(printf '%s' "$GRAPHQL_ENDPOINT" | sed 's/\\/\\\\/g; s/"/\\"/g')"

cat > /usr/share/nginx/html/env.js <<EOF
window.__APP_CONFIG__ = {"GRAPHQL_ENDPOINT":"${escaped_endpoint}"};
EOF
