# Multi-stage: build the Angular bundle, serve via nginx.
# Prod (recommended): sync dist/ to S3 + CloudFront instead — see TECH-NOTES §3.3.
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm ci || npm install     # lockfile is generated on first local install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/etl-pipeline-builder-ui/browser /usr/share/nginx/html
EXPOSE 80
