# Multi-stage Dockerfile for Enterprise Dashboard Platform

# Stage 1: Base Node.js image
FROM node:18-alpine AS base
LABEL maintainer="Enterprise Dashboard Team"

# Install essential packages
RUN apk add --no-cache \
    curl \
    tini \
    && rm -rf /var/cache/apk/*

# Create app directory
WORKDIR /app

# Copy package files
COPY package*.json ./
COPY frontend/package*.json ./frontend/
COPY backend/package*.json ./backend/
COPY shared/package*.json ./shared/

# Stage 2: Dependencies installation
FROM base AS dependencies

# Install all dependencies (including devDependencies for build)
RUN npm ci --include=dev

# Copy source code
COPY . .

# Stage 3: Build stage
FROM dependencies AS build

# Set build environment
ENV NODE_ENV=production

# Generate Prisma client
RUN cd backend && npx prisma generate

# Build frontend
RUN npm run build:frontend

# Build backend
RUN npm run build:backend

# Stage 4: Production dependencies
FROM base AS prod-dependencies

# Install only production dependencies
ENV NODE_ENV=production
RUN npm ci --only=production && npm cache clean --force

# Stage 5: Production image
FROM node:18-alpine AS production

# Install runtime dependencies
RUN apk add --no-cache \
    curl \
    tini \
    dumb-init \
    && addgroup -g 1001 -S nodejs \
    && adduser -S nextjs -u 1001 \
    && rm -rf /var/cache/apk/*

# Set working directory
WORKDIR /app

# Copy production dependencies
COPY --from=prod-dependencies --chown=nextjs:nodejs /app/node_modules ./node_modules
COPY --from=prod-dependencies --chown=nextjs:nodejs /app/package*.json ./

# Copy built applications
COPY --from=build --chown=nextjs:nodejs /app/dist ./dist
COPY --from=build --chown=nextjs:nodejs /app/backend/dist ./backend/dist
COPY --from=build --chown=nextjs:nodejs /app/backend/prisma ./backend/prisma

# Copy configuration files
COPY --from=build --chown=nextjs:nodejs /app/backend/package*.json ./backend/

# Create necessary directories
RUN mkdir -p /app/logs /app/uploads \
    && chown -R nextjs:nodejs /app/logs /app/uploads

# Set environment variables
ENV NODE_ENV=production
ENV PORT=3001
ENV HOST=0.0.0.0

# Expose port
EXPOSE 3001

# Switch to non-root user
USER nextjs

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:3001/health || exit 1

# Use tini as PID 1
ENTRYPOINT ["/sbin/tini", "--"]

# Start the application
CMD ["node", "backend/dist/server.js"]

# Development stage
FROM dependencies AS development

# Install additional development tools
RUN apk add --no-cache git

# Set development environment
ENV NODE_ENV=development

# Copy source code
COPY . .

# Expose ports for development
EXPOSE 3000 3001 3002

# Start development server
CMD ["npm", "run", "dev"]

# Testing stage
FROM dependencies AS testing

# Set test environment
ENV NODE_ENV=test

# Copy source code and test files
COPY . .

# Install Playwright dependencies
RUN npx playwright install --with-deps

# Run tests
CMD ["npm", "test"]

# Builder stage for CI/CD
FROM build AS builder

# This stage is used for exporting built files
# Usage: docker build --target=builder --output=./dist .

# Copy built files to output
RUN mkdir -p /output
RUN cp -r dist/* /output/
RUN cp -r backend/dist /output/backend

# Nginx stage for serving frontend (optional)
FROM nginx:alpine AS frontend

# Copy built frontend files
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY docker/nginx.conf /etc/nginx/nginx.conf

# Expose port
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]