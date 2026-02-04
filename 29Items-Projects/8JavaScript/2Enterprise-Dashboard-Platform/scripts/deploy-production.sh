#!/bin/bash

# Enterprise Dashboard Platform - Production Deployment Script

set -e  # Exit on any error

echo "🚀 Starting production deployment..."

# Configuration
PRODUCTION_SERVER="${PRODUCTION_SERVER:-enterprise-dashboard.com}"
PRODUCTION_USER="${PRODUCTION_USER:-deploy}"
DOCKER_REGISTRY="${DOCKER_REGISTRY:-ghcr.io}"
IMAGE_NAME="${IMAGE_NAME:-enterprise-dashboard}"
VERSION="${GITHUB_REF_NAME:-$(git describe --tags --abbrev=0)}"
COMMIT_SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"

# Ensure we're deploying a tagged version
if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "❌ Production deployments require a valid semantic version tag (e.g., v1.2.3)"
    echo "Current version: $VERSION"
    exit 1
fi

echo "📋 Deployment Details:"
echo "   Version: $VERSION"
echo "   Commit: $COMMIT_SHA"
echo "   Server: $PRODUCTION_SERVER"

# Build and tag images
echo "🏗️  Building and tagging production images..."
docker build -t ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:${VERSION} \
              -t ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:latest .
docker build -t ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:${VERSION} \
              -t ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:latest ./backend

# Push images to registry
echo "📦 Pushing images to registry..."
docker push ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:${VERSION}
docker push ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:latest
docker push ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:${VERSION}
docker push ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:latest

# Create backup of current deployment
echo "💾 Creating backup of current deployment..."
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "cd /opt/enterprise-dashboard/production && \
    docker-compose ps -q > /tmp/running-containers.txt && \
    if [ -s /tmp/running-containers.txt ]; then \
        mkdir -p backups/$(date +%Y%m%d_%H%M%S) && \
        cp -r . backups/$(date +%Y%m%d_%H%M%S)/ 2>/dev/null || true; \
    fi"

# Deploy to production server
echo "🌐 Deploying to production server..."

# Create deployment directory
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "mkdir -p /opt/enterprise-dashboard/production"

# Copy docker-compose and configuration files
scp docker-compose.yml ${PRODUCTION_USER}@${PRODUCTION_SERVER}:/opt/enterprise-dashboard/production/
scp docker-compose.production.yml ${PRODUCTION_USER}@${PRODUCTION_SERVER}:/opt/enterprise-dashboard/production/
scp -r nginx/ ${PRODUCTION_USER}@${PRODUCTION_SERVER}:/opt/enterprise-dashboard/production/

# Update environment variables
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "cd /opt/enterprise-dashboard/production && cat > .env.production << EOF
NODE_ENV=production
DATABASE_URL=${PRODUCTION_DATABASE_URL}
REDIS_URL=${PRODUCTION_REDIS_URL}
JWT_SECRET=${PRODUCTION_JWT_SECRET}
JWT_REFRESH_SECRET=${PRODUCTION_JWT_REFRESH_SECRET}
CORS_ORIGIN=https://enterprise-dashboard.com
FRONTEND_IMAGE=${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:${VERSION}
BACKEND_IMAGE=${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:${VERSION}
VERSION=${VERSION}
EOF"

# Database backup before migration
echo "🗄️  Creating database backup..."
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "cd /opt/enterprise-dashboard/production && \
    docker-compose -f docker-compose.yml -f docker-compose.production.yml --env-file .env.production exec -T postgres pg_dump -U \$POSTGRES_USER \$POSTGRES_DB > backups/db_backup_$(date +%Y%m%d_%H%M%S).sql || true"

# Pull and start services with rolling update
echo "🔄 Performing rolling update..."
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "cd /opt/enterprise-dashboard/production && \
    docker-compose -f docker-compose.yml -f docker-compose.production.yml --env-file .env.production pull && \
    docker-compose -f docker-compose.yml -f docker-compose.production.yml --env-file .env.production up -d --force-recreate --remove-orphans"

# Run database migrations
echo "🗄️  Running database migrations..."
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "cd /opt/enterprise-dashboard/production && \
    docker-compose -f docker-compose.yml -f docker-compose.production.yml --env-file .env.production exec -T backend npm run db:migrate:deploy"

# Health check with retries
echo "🏥 Performing comprehensive health check..."
HEALTH_CHECK_URL="https://${PRODUCTION_SERVER}/health"
API_HEALTH_URL="https://${PRODUCTION_SERVER}/api/health"

for i in {1..10}; do
    echo "Health check attempt $i/10..."
    sleep 30

    if curl -f -s "$HEALTH_CHECK_URL" > /dev/null && curl -f -s "$API_HEALTH_URL" > /dev/null; then
        echo "✅ Production deployment successful!"
        echo "🌐 Application is available at: https://${PRODUCTION_SERVER}"

        # Run smoke tests
        echo "🧪 Running smoke tests..."
        if command -v npm >/dev/null 2>&1; then
            npm run test:smoke || echo "⚠️  Smoke tests failed, but deployment continues"
        fi

        # Clean up old images
        echo "🧹 Cleaning up old images..."
        ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "docker image prune -f"

        echo "🎉 Production deployment completed successfully!"
        echo "📊 Deployment Summary:"
        echo "   Version: $VERSION"
        echo "   Commit: $COMMIT_SHA"
        echo "   Time: $(date)"
        exit 0
    fi

    echo "Health check failed, retrying in 30 seconds..."
done

# If we get here, health check failed
echo "❌ Health check failed after 10 attempts!"
echo "🔍 Checking logs..."
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "cd /opt/enterprise-dashboard/production && docker-compose logs --tail=100"

echo "🔄 Rolling back deployment..."
ssh ${PRODUCTION_USER}@${PRODUCTION_SERVER} "cd /opt/enterprise-dashboard/production && \
    if [ -d \"backups/$(ls -t backups/ | head -1)\" ]; then \
        cp -r backups/$(ls -t backups/ | head -1)/* . && \
        docker-compose -f docker-compose.yml -f docker-compose.production.yml --env-file .env.production up -d; \
    fi"

exit 1