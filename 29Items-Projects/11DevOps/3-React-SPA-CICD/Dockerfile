# ══════════════════════════════════════════════════════════════════════════════
# OPTIONAL container path. Default hosting is DO App Platform static sites
# (infra/digitalocean/). Use this image when response-header control (strict CSP,
# HSTS) outgrows static hosting, or to run on any container platform.
# Docs: docs/TECH-NOTES.md §3.3, ADR-001.
#
#   docker build -t customer-portal --build-arg VITE_API_BASE_URL=https://api.example.com .
#   docker run -p 8080:8080 customer-portal
# ══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: build ──────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

# scripts/ must exist before npm ci: the `prepare` lifecycle hook runs
# scripts/prepare-husky.mjs (which HUSKY=0 turns into an immediate no-op).
COPY package.json package-lock.json ./
COPY scripts ./scripts
ENV HUSKY=0
RUN npm ci --no-audit --no-fund

COPY . .

# VITE_* is baked at build time (docs/TECH-NOTES.md §3.4) — one image per environment.
ARG VITE_ENV_NAME=production
ARG VITE_API_BASE_URL=https://api.example.com
ARG VITE_ENABLE_MSW=false
ARG VITE_GA_MEASUREMENT_ID=
ARG VITE_APP_VERSION=docker

# The MSW worker script is generated (gitignored), so fresh checkouts need this;
# it only activates in builds where VITE_ENABLE_MSW=true.
RUN npm run msw:init && npm run build

# ── Stage 2: serve ──────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html

# Non-root friendly port; matches nginx.conf listen directive.
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
