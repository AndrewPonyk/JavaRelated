# Multi-stage Dockerfile for Enterprise Dashboard Frontend
# Supports both development and production builds

# Build stage - using Debian slim for faster native module installs
FROM node:18-slim AS builder

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    make \
    g++ \
    libcairo2-dev \
    libjpeg-dev \
    libpango1.0-dev \
    libgif-dev \
    libpixman-1-dev \
    libjpeg62-turbo-dev \
    libfreetype6-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy package files
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci --silent && npm cache clean --force

# Copy source code
COPY . .

# Build arguments
ARG VITE_API_BASE_URL=/api
ARG VITE_APP_ENV=development
ARG VITE_ML_ENABLED=true
ARG VITE_ANALYTICS_ENABLED=true

# Set environment variables
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_APP_ENV=$VITE_APP_ENV
ENV VITE_ML_ENABLED=$VITE_ML_ENABLED
ENV VITE_ANALYTICS_ENABLED=$VITE_ANALYTICS_ENABLED

# Build the frontend only
RUN npm run build:frontend

# Production stage
FROM nginx:alpine AS production

# Install curl for health checks
RUN apk add --no-cache curl

# Copy built application
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

# Copy health check script
COPY docker/health-check.sh /usr/local/bin/health-check.sh
RUN chmod +x /usr/local/bin/health-check.sh

# Set permissions for nginx user (already exists in nginx:alpine)
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    chown -R nginx:nginx /etc/nginx/conf.d && \
    touch /run/nginx.pid && \
    chown nginx:nginx /run/nginx.pid

# Switch to non-root user
USER nginx

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD /usr/local/bin/health-check.sh

# Start nginx
CMD ["nginx", "-g", "daemon off;"]

# Development stage - using Debian slim for faster native module installs
FROM node:18-slim AS development

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    make \
    g++ \
    libcairo2-dev \
    libjpeg-dev \
    libpango1.0-dev \
    libgif-dev \
    libpixman-1-dev \
    libjpeg62-turbo-dev \
    libfreetype6-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy package files
COPY package.json package-lock.json ./

# Install all dependencies (including dev dependencies)
RUN npm ci --silent

# Copy source code
COPY . .

# Expose port for development server
EXPOSE 3000

# Health check for development
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3000/ || exit 1

# Start development server (frontend only)
CMD ["npm", "run", "dev:frontend", "--", "--host", "0.0.0.0"]