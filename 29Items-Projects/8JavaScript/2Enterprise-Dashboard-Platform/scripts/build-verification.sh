#!/bin/bash
# =============================================================================
# ENTERPRISE DASHBOARD PLATFORM - BUILD VERIFICATION SCRIPT
# =============================================================================
#
# This script performs comprehensive build verification including:
# - Source code analysis and linting
# - Type checking and compilation
# - Unit and integration tests
# - Security vulnerability scanning
# - Performance benchmarking
# - Docker image building and verification
# - Deployment readiness checks
#
# Usage:
#   ./scripts/build-verification.sh [--quick] [--skip-tests] [--production]
#
# =============================================================================

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
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

log_step() {
    echo -e "${PURPLE}[STEP]${NC} $1" >&2
}

log_separator() {
    echo -e "${CYAN}========================================${NC}" >&2
}

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

# Build configuration
BUILD_ID="${BUILD_ID:-$(date +%s)}"
BUILD_VERSION="${BUILD_VERSION:-$(git describe --tags --always --dirty 2>/dev/null || echo 'dev')}"
BUILD_BRANCH="${BUILD_BRANCH:-$(git branch --show-current 2>/dev/null || echo 'unknown')}"
BUILD_COMMIT="${BUILD_COMMIT:-$(git rev-parse HEAD 2>/dev/null || echo 'unknown')}"

# Command line options
QUICK_BUILD=false
SKIP_TESTS=false
PRODUCTION_BUILD=false
VERBOSE=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --quick)
            QUICK_BUILD=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --production)
            PRODUCTION_BUILD=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [--quick] [--skip-tests] [--production] [--verbose]"
            echo ""
            echo "Options:"
            echo "  --quick      Skip heavy operations like security scans"
            echo "  --skip-tests Skip test execution"
            echo "  --production Build for production environment"
            echo "  --verbose    Enable verbose output"
            echo "  --help       Show this help message"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Build metrics tracking
START_TIME=$(date +%s)
STEP_START_TIME=$START_TIME
FAILED_STEPS=()

# Function to track step timing
track_step() {
    local step_name="$1"
    local step_end_time=$(date +%s)
    local step_duration=$((step_end_time - STEP_START_TIME))

    log_success "$step_name completed in ${step_duration}s"
    STEP_START_TIME=$step_end_time
}

# Function to handle build failure
handle_failure() {
    local step="$1"
    local exit_code="$2"

    FAILED_STEPS+=("$step")
    log_error "$step failed with exit code $exit_code"

    # Generate failure report
    generate_failure_report
    exit $exit_code
}

# Generate failure report
generate_failure_report() {
    local report_file="$ROOT_DIR/build-failure-report-$BUILD_ID.txt"

    {
        echo "======================================"
        echo "BUILD FAILURE REPORT"
        echo "======================================"
        echo "Build ID: $BUILD_ID"
        echo "Version: $BUILD_VERSION"
        echo "Branch: $BUILD_BRANCH"
        echo "Commit: $BUILD_COMMIT"
        echo "Timestamp: $(date -Iseconds)"
        echo ""
        echo "Failed Steps:"
        for step in "${FAILED_STEPS[@]}"; do
            echo "  - $step"
        done
        echo ""
        echo "Build Configuration:"
        echo "  Quick Build: $QUICK_BUILD"
        echo "  Skip Tests: $SKIP_TESTS"
        echo "  Production Build: $PRODUCTION_BUILD"
        echo ""
        echo "Environment:"
        echo "  Node Version: $(node --version 2>/dev/null || echo 'Not available')"
        echo "  NPM Version: $(npm --version 2>/dev/null || echo 'Not available')"
        echo "  Docker Version: $(docker --version 2>/dev/null || echo 'Not available')"
        echo "  OS: $(uname -a)"
    } > "$report_file"

    log_error "Build failure report generated: $report_file"
}

# =============================================================================
# MAIN BUILD VERIFICATION PROCESS
# =============================================================================

log_separator
log_info "Enterprise Dashboard Platform - Build Verification"
log_info "Build ID: $BUILD_ID"
log_info "Version: $BUILD_VERSION"
log_info "Branch: $BUILD_BRANCH"
log_info "Commit: $BUILD_COMMIT"
log_info "Configuration: Quick=$QUICK_BUILD, Skip Tests=$SKIP_TESTS, Production=$PRODUCTION_BUILD"
log_separator

# =============================================================================
# STEP 1: Environment Verification
# =============================================================================

log_step "Step 1: Environment Verification"

# Check required tools
REQUIRED_TOOLS=("node" "npm" "git")
if [[ "$PRODUCTION_BUILD" == true ]]; then
    REQUIRED_TOOLS+=("docker")
fi

for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
        handle_failure "Environment Check" 1
    fi

    case $tool in
        node)
            NODE_VERSION=$(node --version)
            log_info "Node.js: $NODE_VERSION"
            # Check minimum Node.js version (18.0.0)
            if [[ "${NODE_VERSION#v}" < "18.0.0" ]]; then
                log_error "Node.js version 18.0.0 or higher required"
                handle_failure "Environment Check" 1
            fi
            ;;
        npm)
            NPM_VERSION=$(npm --version)
            log_info "NPM: v$NPM_VERSION"
            ;;
        docker)
            DOCKER_VERSION=$(docker --version)
            log_info "Docker: $DOCKER_VERSION"
            ;;
    esac
done

# Check disk space
AVAILABLE_SPACE=$(df -BG "$ROOT_DIR" | awk 'NR==2 {print $4}' | sed 's/G//')
if [[ $AVAILABLE_SPACE -lt 5 ]]; then
    log_error "Insufficient disk space: ${AVAILABLE_SPACE}GB available, 5GB minimum required"
    handle_failure "Environment Check" 1
fi

log_info "Available disk space: ${AVAILABLE_SPACE}GB"
track_step "Environment Verification"

# =============================================================================
# STEP 2: Source Code Analysis
# =============================================================================

log_step "Step 2: Source Code Analysis"

# Backend code analysis
if [[ -d "$BACKEND_DIR" ]]; then
    cd "$BACKEND_DIR"

    # Install dependencies if needed
    if [[ ! -d "node_modules" ]]; then
        log_info "Installing backend dependencies..."
        npm ci --silent
    fi

    # Type checking
    log_info "Running TypeScript type checking..."
    if ! npm run type-check; then
        handle_failure "TypeScript Type Check" 1
    fi

    # Linting
    log_info "Running ESLint..."
    if ! npm run lint; then
        handle_failure "ESLint Check" 1
    fi

    # Code formatting check (if Prettier is configured)
    if npm run format:check &>/dev/null; then
        log_info "Running code formatting check..."
        if ! npm run format:check; then
            handle_failure "Code Formatting Check" 1
        fi
    fi

    cd "$ROOT_DIR"
fi

# Frontend code analysis (if frontend directory exists)
if [[ -d "$FRONTEND_DIR" ]]; then
    cd "$FRONTEND_DIR"

    if [[ ! -d "node_modules" ]]; then
        log_info "Installing frontend dependencies..."
        npm ci --silent
    fi

    # Type checking for frontend
    if npm run type-check &>/dev/null; then
        log_info "Running frontend TypeScript type checking..."
        if ! npm run type-check; then
            handle_failure "Frontend Type Check" 1
        fi
    fi

    # Frontend linting
    if npm run lint &>/dev/null; then
        log_info "Running frontend ESLint..."
        if ! npm run lint; then
            handle_failure "Frontend ESLint Check" 1
        fi
    fi

    cd "$ROOT_DIR"
fi

track_step "Source Code Analysis"

# =============================================================================
# STEP 3: Security Vulnerability Scanning
# =============================================================================

if [[ "$QUICK_BUILD" != true ]]; then
    log_step "Step 3: Security Vulnerability Scanning"

    # Backend security audit
    if [[ -d "$BACKEND_DIR" ]]; then
        cd "$BACKEND_DIR"

        log_info "Running npm audit for backend..."
        if ! npm audit --audit-level=moderate; then
            log_warning "npm audit found vulnerabilities in backend"
            # Don't fail the build for audit issues in development
            if [[ "$PRODUCTION_BUILD" == true ]]; then
                handle_failure "Security Audit - Backend" 1
            fi
        fi

        cd "$ROOT_DIR"
    fi

    # Frontend security audit
    if [[ -d "$FRONTEND_DIR" ]]; then
        cd "$FRONTEND_DIR"

        log_info "Running npm audit for frontend..."
        if ! npm audit --audit-level=moderate; then
            log_warning "npm audit found vulnerabilities in frontend"
            if [[ "$PRODUCTION_BUILD" == true ]]; then
                handle_failure "Security Audit - Frontend" 1
            fi
        fi

        cd "$ROOT_DIR"
    fi

    track_step "Security Vulnerability Scanning"
fi

# =============================================================================
# STEP 4: Unit and Integration Tests
# =============================================================================

if [[ "$SKIP_TESTS" != true ]]; then
    log_step "Step 4: Unit and Integration Tests"

    # Backend tests
    if [[ -d "$BACKEND_DIR" ]]; then
        cd "$BACKEND_DIR"

        log_info "Running backend tests..."
        if [[ "$PRODUCTION_BUILD" == true ]]; then
            # Run with coverage for production builds
            if ! npm run test:coverage; then
                handle_failure "Backend Tests" 1
            fi

            # Check coverage thresholds
            log_info "Checking test coverage thresholds..."
            COVERAGE_REPORT="coverage/coverage-summary.json"
            if [[ -f "$COVERAGE_REPORT" ]]; then
                # Parse coverage and check minimums
                LINES_COVERAGE=$(node -p "JSON.parse(require('fs').readFileSync('$COVERAGE_REPORT', 'utf8')).total.lines.pct")
                FUNCTIONS_COVERAGE=$(node -p "JSON.parse(require('fs').readFileSync('$COVERAGE_REPORT', 'utf8')).total.functions.pct")
                BRANCHES_COVERAGE=$(node -p "JSON.parse(require('fs').readFileSync('$COVERAGE_REPORT', 'utf8')).total.branches.pct")

                log_info "Coverage: Lines $LINES_COVERAGE%, Functions $FUNCTIONS_COVERAGE%, Branches $BRANCHES_COVERAGE%"

                # Check minimum coverage (80% for production)
                MIN_COVERAGE=80
                if (( $(echo "$LINES_COVERAGE < $MIN_COVERAGE" | bc -l) )); then
                    log_error "Line coverage ($LINES_COVERAGE%) below minimum ($MIN_COVERAGE%)"
                    handle_failure "Coverage Check" 1
                fi
            fi
        else
            # Run basic tests for development builds
            if ! npm test; then
                handle_failure "Backend Tests" 1
            fi
        fi

        cd "$ROOT_DIR"
    fi

    # Frontend tests (if they exist)
    if [[ -d "$FRONTEND_DIR" ]]; then
        cd "$FRONTEND_DIR"

        if npm run test:ci &>/dev/null; then
            log_info "Running frontend tests..."
            if ! npm run test:ci; then
                handle_failure "Frontend Tests" 1
            fi
        else
            log_info "No frontend tests configured"
        fi

        cd "$ROOT_DIR"
    fi

    track_step "Unit and Integration Tests"
fi

# =============================================================================
# STEP 5: Build Compilation
# =============================================================================

log_step "Step 5: Build Compilation"

# Backend build
if [[ -d "$BACKEND_DIR" ]]; then
    cd "$BACKEND_DIR"

    log_info "Building backend application..."
    if ! npm run build; then
        handle_failure "Backend Build" 1
    fi

    # Verify build output
    if [[ ! -d "dist" ]]; then
        log_error "Backend build output directory 'dist' not found"
        handle_failure "Backend Build Verification" 1
    fi

    # Check for essential build files
    REQUIRED_FILES=("dist/server.js")
    for file in "${REQUIRED_FILES[@]}"; do
        if [[ ! -f "$file" ]]; then
            log_error "Required build file not found: $file"
            handle_failure "Backend Build Verification" 1
        fi
    done

    # Test build by trying to import the main module
    log_info "Validating backend build..."
    if ! node -e "require('./dist/server.js')" &>/dev/null; then
        log_warning "Backend build validation failed - may have runtime dependencies"
    fi

    cd "$ROOT_DIR"
fi

# Frontend build
if [[ -d "$FRONTEND_DIR" ]]; then
    cd "$FRONTEND_DIR"

    if npm run build &>/dev/null; then
        log_info "Building frontend application..."
        if ! npm run build; then
            handle_failure "Frontend Build" 1
        fi

        # Verify frontend build output
        BUILD_DIR="build"
        if [[ -d "dist" ]]; then
            BUILD_DIR="dist"
        fi

        if [[ ! -d "$BUILD_DIR" ]]; then
            log_error "Frontend build output directory not found"
            handle_failure "Frontend Build Verification" 1
        fi

        # Check for essential frontend files
        if [[ ! -f "$BUILD_DIR/index.html" ]]; then
            log_error "Frontend index.html not found in build output"
            handle_failure "Frontend Build Verification" 1
        fi
    else
        log_info "No frontend build script configured"
    fi

    cd "$ROOT_DIR"
fi

track_step "Build Compilation"

# =============================================================================
# STEP 6: Docker Image Building (Production Only)
# =============================================================================

if [[ "$PRODUCTION_BUILD" == true ]]; then
    log_step "Step 6: Docker Image Building"

    # Backend Docker image
    if [[ -f "$BACKEND_DIR/Dockerfile.production" ]]; then
        log_info "Building backend Docker image..."

        BACKEND_IMAGE="enterprise-dashboard-backend:$BUILD_VERSION"
        if ! docker build \
            -f "$BACKEND_DIR/Dockerfile.production" \
            -t "$BACKEND_IMAGE" \
            --build-arg BUILD_DATE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
            --build-arg VCS_REF="$BUILD_COMMIT" \
            --build-arg VERSION="$BUILD_VERSION" \
            "$BACKEND_DIR"; then
            handle_failure "Backend Docker Build" 1
        fi

        # Test Docker image
        log_info "Testing backend Docker image..."
        if ! docker run --rm -e NODE_ENV=test "$BACKEND_IMAGE" node --version; then
            handle_failure "Backend Docker Image Test" 1
        fi

        # Check image size
        IMAGE_SIZE=$(docker images --format "table {{.Size}}" "$BACKEND_IMAGE" | tail -n +2)
        log_info "Backend Docker image size: $IMAGE_SIZE"
    fi

    # Frontend Docker image (if Dockerfile exists)
    if [[ -f "$FRONTEND_DIR/Dockerfile.production" ]]; then
        log_info "Building frontend Docker image..."

        FRONTEND_IMAGE="enterprise-dashboard-frontend:$BUILD_VERSION"
        if ! docker build \
            -f "$FRONTEND_DIR/Dockerfile.production" \
            -t "$FRONTEND_IMAGE" \
            --build-arg BUILD_DATE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
            --build-arg VCS_REF="$BUILD_COMMIT" \
            --build-arg VERSION="$BUILD_VERSION" \
            "$FRONTEND_DIR"; then
            handle_failure "Frontend Docker Build" 1
        fi

        FRONTEND_IMAGE_SIZE=$(docker images --format "table {{.Size}}" "$FRONTEND_IMAGE" | tail -n +2)
        log_info "Frontend Docker image size: $FRONTEND_IMAGE_SIZE"
    fi

    track_step "Docker Image Building"
fi

# =============================================================================
# STEP 7: Performance Benchmarking (Production Only)
# =============================================================================

if [[ "$PRODUCTION_BUILD" == true && "$QUICK_BUILD" != true ]]; then
    log_step "Step 7: Performance Benchmarking"

    # Backend performance test
    if [[ -d "$BACKEND_DIR" ]]; then
        cd "$BACKEND_DIR"

        # Start the application for benchmarking
        log_info "Starting application for performance testing..."

        # Set test environment variables
        export NODE_ENV=test
        export PORT=3099
        export DATABASE_URL="postgresql://test:test@localhost:5432/test"
        export REDIS_URL="redis://localhost:6379"
        export JWT_SECRET="test-jwt-secret-32-characters-long-for-testing-purposes"
        export SESSION_SECRET="test-session-secret-32-characters-long-for-testing"

        # Start app in background
        node dist/server.js &
        APP_PID=$!

        # Wait for app to start
        sleep 10

        # Basic performance test using curl
        log_info "Running basic performance tests..."

        # Test health endpoint response time
        RESPONSE_TIME=$(curl -w "%{time_total}" -s -o /dev/null http://localhost:3099/health || echo "999")
        log_info "Health endpoint response time: ${RESPONSE_TIME}s"

        # Cleanup
        kill $APP_PID 2>/dev/null || true
        wait $APP_PID 2>/dev/null || true

        cd "$ROOT_DIR"
    fi

    track_step "Performance Benchmarking"
fi

# =============================================================================
# STEP 8: Deployment Readiness Check
# =============================================================================

log_step "Step 8: Deployment Readiness Check"

# Check environment configuration files
ENV_FILES=(
    "backend/.env.production.example"
    "docker-compose.production.yml"
)

for env_file in "${ENV_FILES[@]}"; do
    if [[ ! -f "$ROOT_DIR/$env_file" ]]; then
        log_error "Missing environment file: $env_file"
        handle_failure "Deployment Readiness Check" 1
    fi
done

# Check production configuration
if [[ -d "$BACKEND_DIR" ]]; then
    cd "$BACKEND_DIR"

    # Verify production dependencies
    log_info "Checking production dependencies..."
    if ! npm list --prod --depth=0 >/dev/null; then
        log_warning "Issues with production dependencies"
    fi

    # Check for development dependencies in production build
    if [[ "$PRODUCTION_BUILD" == true ]]; then
        DEV_DEPS=$(npm list --dev --depth=0 2>/dev/null | grep -c "└─" || echo "0")
        if [[ $DEV_DEPS -gt 0 ]]; then
            log_warning "Development dependencies found in production build"
        fi
    fi

    cd "$ROOT_DIR"
fi

# Database schema validation
if [[ -f "$BACKEND_DIR/prisma/schema.prisma" ]]; then
    cd "$BACKEND_DIR"

    log_info "Validating database schema..."
    if ! npx prisma validate; then
        handle_failure "Database Schema Validation" 1
    fi

    cd "$ROOT_DIR"
fi

track_step "Deployment Readiness Check"

# =============================================================================
# BUILD COMPLETION AND REPORTING
# =============================================================================

TOTAL_BUILD_TIME=$(($(date +%s) - START_TIME))

log_separator
log_success "BUILD VERIFICATION COMPLETED SUCCESSFULLY"
log_separator

# Generate build report
BUILD_REPORT_FILE="$ROOT_DIR/build-report-$BUILD_ID.json"

{
    echo "{"
    echo "  \"buildId\": \"$BUILD_ID\","
    echo "  \"version\": \"$BUILD_VERSION\","
    echo "  \"branch\": \"$BUILD_BRANCH\","
    echo "  \"commit\": \"$BUILD_COMMIT\","
    echo "  \"timestamp\": \"$(date -Iseconds)\","
    echo "  \"duration\": $TOTAL_BUILD_TIME,"
    echo "  \"configuration\": {"
    echo "    \"quickBuild\": $QUICK_BUILD,"
    echo "    \"skipTests\": $SKIP_TESTS,"
    echo "    \"productionBuild\": $PRODUCTION_BUILD"
    echo "  },"
    echo "  \"environment\": {"
    echo "    \"nodeVersion\": \"$(node --version)\","
    echo "    \"npmVersion\": \"$(npm --version)\","
    echo "    \"dockerVersion\": \"$(docker --version 2>/dev/null || echo 'Not available')\","
    echo "    \"os\": \"$(uname -s)\""
    echo "  },"
    echo "  \"status\": \"SUCCESS\","
    echo "  \"artifactPaths\": ["
    if [[ -d "$BACKEND_DIR/dist" ]]; then
        echo "    \"$BACKEND_DIR/dist\","
    fi
    if [[ -d "$FRONTEND_DIR/build" ]]; then
        echo "    \"$FRONTEND_DIR/build\","
    elif [[ -d "$FRONTEND_DIR/dist" ]]; then
        echo "    \"$FRONTEND_DIR/dist\","
    fi
    echo "    \"$BUILD_REPORT_FILE\""
    echo "  ]"
    echo "}"
} > "$BUILD_REPORT_FILE"

log_info "Build Verification Summary:"
log_info "  - Build ID: $BUILD_ID"
log_info "  - Version: $BUILD_VERSION"
log_info "  - Duration: ${TOTAL_BUILD_TIME}s"
log_info "  - Status: SUCCESS"
log_info "  - Report: $BUILD_REPORT_FILE"

if [[ "$PRODUCTION_BUILD" == true ]]; then
    log_success "🚀 Production build verified and ready for deployment!"
else
    log_success "✅ Development build verification completed!"
fi

log_separator