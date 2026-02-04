#!/bin/bash
# =============================================================================
# ENTERPRISE DASHBOARD PLATFORM - PRODUCTION STARTUP SCRIPT
# =============================================================================
#
# This script handles the production startup sequence with proper error handling,
# health checks, and graceful failure recovery.
#
# =============================================================================

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_separator() {
    echo "========================================" >&2
}

# Application information
APP_NAME="${APP_NAME:-Enterprise Dashboard Platform}"
APP_VERSION="${VERSION:-1.0.0}"
NODE_ENV="${NODE_ENV:-production}"
APP_USER="${APP_USER:-dashboard}"

# Startup configuration
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-300}"  # 5 minutes
HEALTH_CHECK_RETRIES="${HEALTH_CHECK_RETRIES:-30}"
HEALTH_CHECK_INTERVAL="${HEALTH_CHECK_INTERVAL:-10}"

log_separator
log_info "Starting ${APP_NAME} v${APP_VERSION} in ${NODE_ENV} mode"
log_info "User: ${APP_USER}, PID: $$"
log_separator

# =============================================================================
# Pre-flight Checks
# =============================================================================

log_info "Performing pre-flight checks..."

# Check if running as correct user
if [ "$(whoami)" != "$APP_USER" ]; then
    log_error "Must run as user: $APP_USER, currently running as: $(whoami)"
    exit 1
fi

# Check Node.js version
NODE_VERSION=$(node --version)
log_info "Node.js version: $NODE_VERSION"

# Check required environment variables
REQUIRED_VARS=(
    "DATABASE_URL"
    "REDIS_URL"
    "JWT_SECRET"
    "SESSION_SECRET"
)

MISSING_VARS=()
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var:-}" ]; then
        MISSING_VARS+=("$var")
    fi
done

if [ ${#MISSING_VARS[@]} -ne 0 ]; then
    log_error "Missing required environment variables:"
    for var in "${MISSING_VARS[@]}"; do
        log_error "  - $var"
    done
    exit 1
fi

log_success "Pre-flight checks completed"

# =============================================================================
# Directory and Permissions Setup
# =============================================================================

log_info "Setting up directories and permissions..."

# Create required directories
REQUIRED_DIRS=(
    "/app/logs"
    "/app/backups"
    "/app/uploads"
    "/app/tmp"
)

for dir in "${REQUIRED_DIRS[@]}"; do
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir"
        log_info "Created directory: $dir"
    fi

    # Ensure proper ownership
    if [ "$(stat -c '%U' "$dir")" != "$APP_USER" ]; then
        log_warning "Directory ownership will be fixed by container orchestrator: $dir"
    fi
done

# Check write permissions
for dir in "${REQUIRED_DIRS[@]}"; do
    if [ ! -w "$dir" ]; then
        log_error "No write permission for directory: $dir"
        exit 1
    fi
done

log_success "Directory setup completed"

# =============================================================================
# Database Migration and Setup
# =============================================================================

log_info "Checking database connectivity and migrations..."

# Function to check database connectivity
check_database() {
    local retries=0
    local max_retries=30

    while [ $retries -lt $max_retries ]; do
        if npx prisma db push --accept-data-loss >/dev/null 2>&1; then
            log_success "Database connectivity verified"
            return 0
        fi

        retries=$((retries + 1))
        log_info "Database connection attempt $retries/$max_retries failed, retrying in 5s..."
        sleep 5
    done

    log_error "Database connectivity check failed after $max_retries attempts"
    return 1
}

# Check database connection
if ! check_database; then
    log_error "Cannot establish database connection"
    exit 1
fi

# Run database migrations
log_info "Running database migrations..."
if npx prisma migrate deploy; then
    log_success "Database migrations completed successfully"
else
    log_error "Database migration failed"
    exit 1
fi

# Generate Prisma client (in case it's not generated)
log_info "Ensuring Prisma client is generated..."
if npx prisma generate >/dev/null 2>&1; then
    log_success "Prisma client generation completed"
else
    log_warning "Prisma client generation had issues (may already be generated)"
fi

# =============================================================================
# Redis Connectivity Check
# =============================================================================

log_info "Checking Redis connectivity..."

# Function to check Redis connectivity
check_redis() {
    local retries=0
    local max_retries=30

    while [ $retries -lt $max_retries ]; do
        # Simple Redis ping test using Node.js
        if node -e "
            const Redis = require('ioredis');
            const redis = new Redis(process.env.REDIS_URL);
            redis.ping()
                .then(() => { console.log('Redis OK'); process.exit(0); })
                .catch(() => { process.exit(1); });
        " >/dev/null 2>&1; then
            log_success "Redis connectivity verified"
            return 0
        fi

        retries=$((retries + 1))
        log_info "Redis connection attempt $retries/$max_retries failed, retrying in 5s..."
        sleep 5
    done

    log_error "Redis connectivity check failed after $max_retries attempts"
    return 1
}

if ! check_redis; then
    log_error "Cannot establish Redis connection"
    exit 1
fi

# =============================================================================
# Production Security Checks
# =============================================================================

log_info "Running production security validation..."

# Check JWT secret strength
JWT_SECRET_LENGTH=${#JWT_SECRET}
if [ $JWT_SECRET_LENGTH -lt 64 ]; then
    log_error "JWT_SECRET is too short ($JWT_SECRET_LENGTH chars), should be at least 64 characters"
    exit 1
fi

# Check session secret strength
SESSION_SECRET_LENGTH=${#SESSION_SECRET}
if [ $SESSION_SECRET_LENGTH -lt 64 ]; then
    log_error "SESSION_SECRET is too short ($SESSION_SECRET_LENGTH chars), should be at least 64 characters"
    exit 1
fi

# Check for development settings in production
if [ "${DEBUG_ENABLED:-false}" = "true" ]; then
    log_error "DEBUG_ENABLED should be false in production"
    exit 1
fi

if [ "${ENABLE_API_DOCS:-false}" = "true" ]; then
    log_warning "API documentation is enabled in production"
fi

log_success "Production security validation completed"

# =============================================================================
# Cache Warmup (Optional)
# =============================================================================

if [ "${CACHE_WARMUP_ENABLED:-true}" = "true" ]; then
    log_info "Performing cache warmup..."

    # Start the application in background for warmup
    node dist/server.js &
    APP_PID=$!

    # Wait for application to start
    sleep 10

    # Perform cache warmup via health check endpoint
    if curl -f http://localhost:${PORT:-3001}/health >/dev/null 2>&1; then
        log_success "Cache warmup completed"
    else
        log_warning "Cache warmup failed, continuing anyway"
    fi

    # Stop the warmup instance
    kill $APP_PID 2>/dev/null || true
    wait $APP_PID 2>/dev/null || true

    # Wait a moment before final startup
    sleep 2
fi

# =============================================================================
# Final Application Startup
# =============================================================================

log_info "Starting application server..."

# Set up signal handling for graceful shutdown
cleanup() {
    local exit_code=$?
    log_info "Received shutdown signal, performing cleanup..."

    if [ -n "${APP_PID:-}" ]; then
        log_info "Terminating application process (PID: $APP_PID)..."
        kill -TERM $APP_PID 2>/dev/null || true

        # Wait for graceful shutdown
        local timeout=30
        while [ $timeout -gt 0 ] && kill -0 $APP_PID 2>/dev/null; do
            sleep 1
            timeout=$((timeout - 1))
        done

        # Force kill if still running
        if kill -0 $APP_PID 2>/dev/null; then
            log_warning "Force killing application process..."
            kill -KILL $APP_PID 2>/dev/null || true
        fi

        wait $APP_PID 2>/dev/null || true
    fi

    log_info "Cleanup completed"
    exit $exit_code
}

# Register signal handlers
trap cleanup SIGTERM SIGINT SIGQUIT

# Start the application
log_success "All checks passed, starting ${APP_NAME}..."
log_info "Process will be monitored for health and auto-restart on failure"
log_separator

# Health check function
health_check() {
    curl -f "http://localhost:${PORT:-3001}/health" >/dev/null 2>&1
}

# Start application with monitoring
while true; do
    log_info "Starting application instance..."

    # Start the Node.js application
    node dist/server.js &
    APP_PID=$!

    log_info "Application started with PID: $APP_PID"

    # Wait for app to become healthy
    local health_retries=0
    local app_healthy=false

    while [ $health_retries -lt $HEALTH_CHECK_RETRIES ]; do
        sleep $HEALTH_CHECK_INTERVAL

        # Check if process is still running
        if ! kill -0 $APP_PID 2>/dev/null; then
            log_error "Application process died unexpectedly"
            break
        fi

        # Check health endpoint
        if health_check; then
            log_success "Application is healthy and ready"
            app_healthy=true
            break
        fi

        health_retries=$((health_retries + 1))
        log_info "Health check attempt $health_retries/$HEALTH_CHECK_RETRIES..."
    done

    if [ "$app_healthy" = true ]; then
        # Monitor the application
        log_info "Monitoring application health..."

        while kill -0 $APP_PID 2>/dev/null; do
            sleep 30

            # Periodic health check
            if ! health_check; then
                log_warning "Health check failed, application may be unhealthy"
            fi
        done

        log_warning "Application process terminated unexpectedly"
    else
        log_error "Application failed to become healthy"

        # Kill the unhealthy process
        kill -TERM $APP_PID 2>/dev/null || true
        wait $APP_PID 2>/dev/null || true
    fi

    # Restart delay
    log_info "Waiting 10 seconds before restart attempt..."
    sleep 10
done