# ── Stage 1: build static assets ─────────────────────────────
FROM node:20-slim AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
RUN npm run build

# ── Stage 2: serve via nginx ─────────────────────────────────
FROM nginx:1.27-alpine AS runtime
# Security headers + COOP/COEP for Cornerstone codecs live in this config.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
