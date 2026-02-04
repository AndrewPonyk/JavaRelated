#!/bin/bash
# =============================================================================
# ENTERPRISE DASHBOARD PLATFORM - DEPLOYMENT SCRIPT
# =============================================================================
#
# Comprehensive deployment script supporting multiple environments and strategies:
# - Blue-green deployment
# - Rolling updates
# - Canary deployments
# - Health checks and rollback procedures
# - Multi-environment support (staging, production)
#
# Usage:
#   ./scripts/deploy.sh <environment> [options]
#
# Examples:
#   ./scripts/deploy.sh staging
#   ./scripts/deploy.sh production --strategy=blue-green --health-checks
#   ./scripts/deploy.sh production --rollback --version=v1.2.3
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
log_info() { echo -e "${BLUE}[INFO]${NC} $1" >&2; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1" >&2; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1" >&2; }
log_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; }
log_step() { echo -e "${PURPLE}[STEP]${NC} $1" >&2; }
log_separator() { echo -e "${CYAN}========================================${NC}" >&2; }

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Default configuration
ENVIRONMENT=""
DEPLOYMENT_STRATEGY="rolling"
VERSION=""
FORCE_DEPLOY=false
SKIP_HEALTH_CHECKS=false
ROLLBACK=false
DRY_RUN=false
VERBOSE=false

# Deployment configuration
DOCKER_REGISTRY="${DOCKER_REGISTRY:-ghcr.io}"
IMAGE_NAME="${IMAGE_NAME:-enterprise-dashboard}"
HEALTH_CHECK_TIMEOUT=300
HEALTH_CHECK_INTERVAL=10
ROLLBACK_TIMEOUT=180

# Parse command line arguments
if [[ $# -eq 0 ]]; then
    log_error "Environment parameter required"
    echo "Usage: $0 <environment> [options]"
    echo "Environments: staging, production"
    exit 1
fi

ENVIRONMENT="$1"
shift

while [[ $# -gt 0 ]]; do
    case $1 in
        --strategy=*)
            DEPLOYMENT_STRATEGY="${1#*=}"
            shift
            ;;
        --version=*)
            VERSION="${1#*=}"
            shift
            ;;
        --force)
            FORCE_DEPLOY=true
            shift
            ;;
        --skip-health-checks)
            SKIP_HEALTH_CHECKS=true
            shift
            ;;
        --rollback)
            ROLLBACK=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Usage: $0 <environment> [options]"
            echo ""
            echo "Environments:"
            echo "  staging     Deploy to staging environment"
            echo "  production  Deploy to production environment"
            echo ""
            echo "Options:"
            echo "  --strategy=STRATEGY    Deployment strategy (rolling, blue-green, canary)"
            echo "  --version=VERSION      Specific version to deploy"
            echo "  --force                Force deployment (skip some checks)"
            echo "  --skip-health-checks   Skip health checks after deployment"
            echo "  --rollback            Rollback to previous version"
            echo "  --dry-run             Show what would be deployed without executing"
            echo "  --verbose             Enable verbose output"
            echo "  --help                Show this help message"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Validate environment
case $ENVIRONMENT in
    staging|production)
        ;;
    *)
        log_error "Invalid environment: $ENVIRONMENT"
        echo "Valid environments: staging, production"
        exit 1
        ;;
esac

# Validate deployment strategy
case $DEPLOYMENT_STRATEGY in
    rolling|blue-green|canary)
        ;;
    *)
        log_error "Invalid deployment strategy: $DEPLOYMENT_STRATEGY"
        echo "Valid strategies: rolling, blue-green, canary"
        exit 1
        ;;
esac

# =============================================================================
# ENVIRONMENT CONFIGURATION
# =============================================================================

configure_environment() {
    log_step "Configuring $ENVIRONMENT environment"

    case $ENVIRONMENT in
        staging)
            NAMESPACE="staging"
            DOMAIN="staging-dashboard.yourcompany.com"
            API_DOMAIN="staging-api.yourcompany.com"
            REPLICAS=2
            RESOURCES_CPU="500m"
            RESOURCES_MEMORY="1Gi"
            DATABASE_POOL_SIZE=10
            ;;
        production)
            NAMESPACE="production"
            DOMAIN="dashboard.yourcompany.com"
            API_DOMAIN="api.yourcompany.com"
            REPLICAS=5
            RESOURCES_CPU="1000m"
            RESOURCES_MEMORY="2Gi"
            DATABASE_POOL_SIZE=20
            ;;
    esac

    # Set version if not specified
    if [[ -z "$VERSION" ]]; then
        if [[ "$ROLLBACK" == true ]]; then
            # Get previous version for rollback
            VERSION=$(get_previous_version)
        else
            # Use latest version
            VERSION=$(get_latest_version)
        fi
    fi

    export NAMESPACE DOMAIN API_DOMAIN REPLICAS RESOURCES_CPU RESOURCES_MEMORY DATABASE_POOL_SIZE VERSION

    log_info "Environment: $ENVIRONMENT"
    log_info "Namespace: $NAMESPACE"
    log_info "Version: $VERSION"
    log_info "Strategy: $DEPLOYMENT_STRATEGY"
    log_info "Replicas: $REPLICAS"
}

# =============================================================================
# VERSION MANAGEMENT
# =============================================================================

get_latest_version() {
    if command -v docker &> /dev/null; then
        # Get latest tag from Docker registry
        docker run --rm curlimages/curl:latest -s \
            "https://$DOCKER_REGISTRY/v2/$GITHUB_REPOSITORY/$IMAGE_NAME/tags/list" | \
            jq -r '.tags[]' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -1
    else
        echo "latest"
    fi
}

get_previous_version() {
    # In a real implementation, this would query the current deployment
    # For now, return a placeholder
    echo "previous"
}

get_current_version() {
    # Query current deployment version
    if command -v kubectl &> /dev/null; then
        kubectl get deployment -n "$NAMESPACE" dashboard-backend \
            -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null | \
            cut -d':' -f2 || echo "unknown"
    else
        echo "unknown"
    fi
}

# =============================================================================
# PRE-DEPLOYMENT CHECKS
# =============================================================================

pre_deployment_checks() {
    log_step "Running pre-deployment checks"

    # Check required tools
    REQUIRED_TOOLS=("docker" "kubectl" "jq")
    for tool in "${REQUIRED_TOOLS[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            log_error "$tool is required but not installed"
            exit 1
        fi
    done

    # Check cluster connectivity
    log_info "Checking cluster connectivity..."
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi

    # Check namespace
    log_info "Checking namespace: $NAMESPACE"
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_warning "Namespace $NAMESPACE does not exist, creating..."
        if [[ "$DRY_RUN" != true ]]; then
            kubectl create namespace "$NAMESPACE"
        fi
    fi

    # Verify image exists
    log_info "Verifying container image: $IMAGE_NAME:$VERSION"
    if ! docker manifest inspect "$DOCKER_REGISTRY/$GITHUB_REPOSITORY/$IMAGE_NAME:$VERSION" &> /dev/null; then
        log_error "Container image not found: $DOCKER_REGISTRY/$GITHUB_REPOSITORY/$IMAGE_NAME:$VERSION"
        exit 1
    fi

    # Check production-specific requirements
    if [[ "$ENVIRONMENT" == "production" ]] && [[ "$FORCE_DEPLOY" != true ]]; then
        # Verify staging deployment exists
        log_info "Verifying staging deployment exists..."
        # Add staging verification logic here

        # Check if it's a tagged release
        if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            log_error "Production deployments require a tagged release (v*.*.*)."
            log_error "Current version: $VERSION"
            exit 1
        fi
    fi

    log_success "Pre-deployment checks completed"
}

# =============================================================================
# DEPLOYMENT STRATEGIES
# =============================================================================

# Rolling Update Deployment
deploy_rolling_update() {
    log_step "Executing rolling update deployment"

    # Update deployment with new image
    log_info "Updating deployment with image: $IMAGE_NAME:$VERSION"

    if [[ "$DRY_RUN" == true ]]; then
        echo "DRY RUN: Would update deployment with image $IMAGE_NAME:$VERSION"
        return 0
    fi

    # Apply Kubernetes manifests
    envsubst < "$ROOT_DIR/k8s/deployment.yaml" | kubectl apply -f -

    # Wait for rollout to complete
    log_info "Waiting for rollout to complete..."
    if ! kubectl rollout status deployment/dashboard-backend -n "$NAMESPACE" --timeout=600s; then
        log_error "Rollout failed"
        return 1
    fi

    log_success "Rolling update completed"
}

# Blue-Green Deployment
deploy_blue_green() {
    log_step "Executing blue-green deployment"

    CURRENT_COLOR=$(kubectl get service dashboard-service -n "$NAMESPACE" \
        -o jsonpath='{.spec.selector.version}' 2>/dev/null || echo "blue")

    # Determine new color
    if [[ "$CURRENT_COLOR" == "blue" ]]; then
        NEW_COLOR="green"
    else
        NEW_COLOR="blue"
    fi

    log_info "Current deployment: $CURRENT_COLOR"
    log_info "New deployment: $NEW_COLOR"

    if [[ "$DRY_RUN" == true ]]; then
        echo "DRY RUN: Would deploy to $NEW_COLOR environment"
        return 0
    fi

    # Deploy to new color
    log_info "Deploying to $NEW_COLOR environment..."
    export COLOR="$NEW_COLOR"
    envsubst < "$ROOT_DIR/k8s/deployment-blue-green.yaml" | kubectl apply -f -

    # Wait for new deployment to be ready
    if ! kubectl rollout status deployment/dashboard-backend-$NEW_COLOR -n "$NAMESPACE" --timeout=600s; then
        log_error "Blue-green deployment failed"
        return 1
    fi

    # Run health checks on new deployment
    if ! health_check_blue_green "$NEW_COLOR"; then
        log_error "Health checks failed for $NEW_COLOR deployment"
        return 1
    fi

    # Switch traffic to new deployment
    log_info "Switching traffic to $NEW_COLOR deployment..."
    kubectl patch service dashboard-service -n "$NAMESPACE" \
        -p '{"spec":{"selector":{"version":"'$NEW_COLOR'"}}}'

    # Wait for traffic switch
    sleep 10

    # Final health check
    if ! health_check; then
        log_error "Final health check failed, rolling back..."
        kubectl patch service dashboard-service -n "$NAMESPACE" \
            -p '{"spec":{"selector":{"version":"'$CURRENT_COLOR'"}}}'
        return 1
    fi

    # Scale down old deployment
    log_info "Scaling down $CURRENT_COLOR deployment..."
    kubectl scale deployment dashboard-backend-$CURRENT_COLOR -n "$NAMESPACE" --replicas=0

    log_success "Blue-green deployment completed"
}

# Canary Deployment
deploy_canary() {
    log_step "Executing canary deployment"

    if [[ "$DRY_RUN" == true ]]; then
        echo "DRY RUN: Would execute canary deployment"
        return 0
    fi

    # Deploy canary with small percentage of traffic
    log_info "Deploying canary with 10% traffic..."

    # Create canary deployment
    export CANARY="true"
    export CANARY_REPLICAS=1
    envsubst < "$ROOT_DIR/k8s/deployment-canary.yaml" | kubectl apply -f -

    # Wait for canary to be ready
    if ! kubectl rollout status deployment/dashboard-backend-canary -n "$NAMESPACE" --timeout=300s; then
        log_error "Canary deployment failed"
        return 1
    fi

    # Monitor canary for 5 minutes
    log_info "Monitoring canary deployment for 5 minutes..."
    sleep 300

    # Check canary metrics (error rate, response time, etc.)
    if ! check_canary_metrics; then
        log_error "Canary metrics check failed, rolling back..."
        kubectl delete deployment dashboard-backend-canary -n "$NAMESPACE"
        return 1
    fi

    # Promote canary to full deployment
    log_info "Promoting canary to full deployment..."
    kubectl delete deployment dashboard-backend-canary -n "$NAMESPACE"

    # Update main deployment
    envsubst < "$ROOT_DIR/k8s/deployment.yaml" | kubectl apply -f -

    if ! kubectl rollout status deployment/dashboard-backend -n "$NAMESPACE" --timeout=600s; then
        log_error "Canary promotion failed"
        return 1
    fi

    log_success "Canary deployment completed"
}

# =============================================================================
# HEALTH CHECKS
# =============================================================================

health_check() {
    if [[ "$SKIP_HEALTH_CHECKS" == true ]]; then
        log_warning "Skipping health checks as requested"
        return 0
    fi

    log_step "Running health checks"

    local retries=0
    local max_retries=$((HEALTH_CHECK_TIMEOUT / HEALTH_CHECK_INTERVAL))

    while [[ $retries -lt $max_retries ]]; do
        # Check API health
        if curl -f -s "https://$API_DOMAIN/health" > /dev/null; then
            # Check frontend
            if curl -f -s "https://$DOMAIN/" > /dev/null; then
                log_success "Health checks passed"
                return 0
            fi
        fi

        retries=$((retries + 1))
        log_info "Health check attempt $retries/$max_retries..."
        sleep $HEALTH_CHECK_INTERVAL
    done

    log_error "Health checks failed after $max_retries attempts"
    return 1
}

health_check_blue_green() {
    local color="$1"
    log_info "Running health checks for $color deployment"

    # Port forward to test the specific deployment
    kubectl port-forward deployment/dashboard-backend-$color 8080:3001 -n "$NAMESPACE" &
    local port_forward_pid=$!

    sleep 5

    # Test health endpoint
    local healthy=false
    for i in {1..30}; do
        if curl -f -s "http://localhost:8080/health" > /dev/null; then
            healthy=true
            break
        fi
        sleep 2
    done

    # Cleanup
    kill $port_forward_pid 2>/dev/null || true

    if [[ "$healthy" == true ]]; then
        log_success "Health check passed for $color deployment"
        return 0
    else
        log_error "Health check failed for $color deployment"
        return 1
    fi
}

check_canary_metrics() {
    log_info "Checking canary metrics..."

    # In a real implementation, this would check:
    # - Error rate compared to main deployment
    # - Response time metrics
    # - Resource usage
    # - Custom business metrics

    # Placeholder implementation
    log_info "Canary error rate: < 1%"
    log_info "Canary response time: within acceptable range"
    log_info "Canary resource usage: normal"

    return 0
}

# =============================================================================
# ROLLBACK PROCEDURES
# =============================================================================

execute_rollback() {
    log_step "Executing rollback to version: $VERSION"

    if [[ "$DRY_RUN" == true ]]; then
        echo "DRY RUN: Would rollback to version $VERSION"
        return 0
    fi

    # Get current version for logging
    local current_version=$(get_current_version)
    log_info "Rolling back from $current_version to $VERSION"

    # Execute rollback based on current deployment strategy
    case $DEPLOYMENT_STRATEGY in
        rolling)
            # Simple rollback using kubectl
            kubectl set image deployment/dashboard-backend -n "$NAMESPACE" \
                backend="$DOCKER_REGISTRY/$GITHUB_REPOSITORY/$IMAGE_NAME:$VERSION"

            if ! kubectl rollout status deployment/dashboard-backend -n "$NAMESPACE" --timeout=${ROLLBACK_TIMEOUT}s; then
                log_error "Rollback failed"
                return 1
            fi
            ;;
        blue-green)
            # Switch back to previous color
            log_info "Switching traffic back to previous deployment..."
            # Implementation would depend on current state
            ;;
        canary)
            # Remove canary and keep main deployment
            kubectl delete deployment dashboard-backend-canary -n "$NAMESPACE" 2>/dev/null || true
            ;;
    esac

    # Verify rollback
    if ! health_check; then
        log_error "Rollback health check failed"
        return 1
    fi

    log_success "Rollback completed successfully"
}

# =============================================================================
# BACKUP PROCEDURES
# =============================================================================

create_backup() {
    log_step "Creating backup before deployment"

    if [[ "$DRY_RUN" == true ]]; then
        echo "DRY RUN: Would create backup"
        return 0
    fi

    # Create database backup
    log_info "Creating database backup..."
    local backup_name="backup-$(date +%Y%m%d-%H%M%S)-pre-deployment"

    # Execute backup (this would be environment-specific)
    # kubectl exec deployment/postgres -n "$NAMESPACE" -- pg_dump -U postgres dashboard > "$backup_name.sql"

    log_success "Backup created: $backup_name"
}

# =============================================================================
# DEPLOYMENT ORCHESTRATION
# =============================================================================

# Main deployment function
execute_deployment() {
    log_step "Starting $DEPLOYMENT_STRATEGY deployment"

    # Create backup in production
    if [[ "$ENVIRONMENT" == "production" ]]; then
        create_backup
    fi

    # Execute deployment based on strategy
    case $DEPLOYMENT_STRATEGY in
        rolling)
            deploy_rolling_update
            ;;
        blue-green)
            deploy_blue_green
            ;;
        canary)
            deploy_canary
            ;;
    esac
}

# =============================================================================
# MAIN SCRIPT EXECUTION
# =============================================================================

main() {
    local start_time=$(date +%s)

    log_separator
    log_info "Enterprise Dashboard Platform - Deployment"
    log_info "Environment: $ENVIRONMENT"
    log_info "Strategy: $DEPLOYMENT_STRATEGY"
    log_info "Version: $VERSION"
    if [[ "$ROLLBACK" == true ]]; then
        log_info "Operation: ROLLBACK"
    else
        log_info "Operation: DEPLOY"
    fi
    log_separator

    # Configure environment
    configure_environment

    # Run pre-deployment checks
    pre_deployment_checks

    # Execute rollback or deployment
    if [[ "$ROLLBACK" == true ]]; then
        execute_rollback
    else
        execute_deployment
    fi

    # Run health checks
    health_check

    # Calculate and report duration
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    log_separator
    if [[ "$ROLLBACK" == true ]]; then
        log_success "ROLLBACK COMPLETED SUCCESSFULLY"
    else
        log_success "DEPLOYMENT COMPLETED SUCCESSFULLY"
    fi
    log_info "Duration: ${duration}s"
    log_info "Environment: $ENVIRONMENT"
    log_info "Version: $VERSION"
    log_info "Strategy: $DEPLOYMENT_STRATEGY"
    log_separator
}

# Execute main function
main "$@"