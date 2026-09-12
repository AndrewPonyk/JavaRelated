# syntax=docker/dockerfile:1
# Build the SPA, then serve the static bundle from a minimal Nginx image.
# In production the bundle is typically pushed to S3 + CloudFront instead
# (TECH-NOTES §3.3); this image is for parity / on-prem serving.

FROM node:20-slim AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1
