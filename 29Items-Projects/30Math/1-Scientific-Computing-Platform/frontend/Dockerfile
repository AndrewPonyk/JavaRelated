# Frontend build image. Production serves the static bundle from
# S3 + CloudFront (docs/TECH-NOTES.md §3.3); this container exists for local
# docker-compose parity and PR preview environments.
#
#   docker build -f frontend/Dockerfile -t scp-frontend:dev frontend

FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
