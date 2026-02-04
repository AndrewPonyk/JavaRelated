# Multi-stage Dockerfile for Enterprise Dashboard Backend
# Supports both development and production builds

# Build stage - using Debian for Prisma compatibility
FROM node:18-slim AS builder

# Set working directory
WORKDIR /app

# Install system dependencies for native modules
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
    openssl \
    && rm -rf /var/lib/apt/lists/*

# Copy package files
COPY package.json ./
COPY package-lock.json* ./

# Install dependencies (fallback to npm install if no lock file)
RUN if [ -f package-lock.json ]; then npm ci --silent; else npm install --silent; fi && npm cache clean --force

# Copy source code
COPY . .

# Generate Prisma client
RUN npx prisma generate

# Build the application
RUN npm run build

# Production stage - using Debian slim for OpenSSL 1.1 compatibility with Prisma
FROM node:18-slim AS production

# Install curl for health checks and dumb-init for signal handling
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    dumb-init \
    openssl \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Create non-root user
RUN groupadd -g 1001 nodejs && \
    useradd -u 1001 -g nodejs -s /bin/false -d /app nodejs

# Copy built application and dependencies
COPY --from=builder --chown=nodejs:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=nodejs:nodejs /app/src ./src
COPY --from=builder --chown=nodejs:nodejs /app/scripts ./scripts
COPY --from=builder --chown=nodejs:nodejs /app/package.json ./package.json
COPY --from=builder --chown=nodejs:nodejs /app/tsconfig.json ./tsconfig.json
COPY --from=builder --chown=nodejs:nodejs /app/prisma ./prisma

# Copy health check script
COPY --chown=nodejs:nodejs docker/health-check.sh /usr/local/bin/health-check.sh
RUN chmod +x /usr/local/bin/health-check.sh

# Create logs and backups directories
RUN mkdir -p logs backups && chown -R nodejs:nodejs logs backups

# Switch to non-root user
USER nodejs

# Expose port
EXPOSE 3001

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD /usr/local/bin/health-check.sh

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start the application
CMD ["npm", "start"]

# Development stage - using Debian slim for faster native module installs
FROM node:18-slim AS development

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
    openssl \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy package files
COPY package.json ./
COPY package-lock.json* ./

# Install all dependencies (including dev dependencies)
RUN if [ -f package-lock.json ]; then npm ci --silent; else npm install --silent; fi

# Copy source code
COPY . .

# Generate Prisma client
RUN npx prisma generate

# Create logs directory
RUN mkdir -p logs

# Expose port
EXPOSE 3001

# Health check for development
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3001/health || exit 1

# Start development server with hot reload
CMD ["npm", "run", "dev"]