#!/bin/bash

# Enterprise Dashboard Platform - Staging Deployment Script

set -e  # Exit on any error

echo "🚀 Starting staging deployment..."

# Configuration
STAGING_SERVER="${STAGING_SERVER:-staging.enterprise-dashboard.com}"
STAGING_USER="${STAGING_USER:-deploy}"
DOCKER_REGISTRY="${DOCKER_REGISTRY:-ghcr.io}"
IMAGE_NAME="${IMAGE_NAME:-enterprise-dashboard}"
BRANCH="${GITHUB_REF_NAME:-develop}"
COMMIT_SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"

# Build and tag images
echo "🏗️  Building and tagging images..."
docker build -t ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:${BRANCH}-${COMMIT_SHA} .
docker build -t ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:${BRANCH}-${COMMIT_SHA} ./backend

# Push images to registry
echo "📦 Pushing images to registry..."
docker push ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:${BRANCH}-${COMMIT_SHA}
docker push ${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:${BRANCH}-${COMMIT_SHA}

# Deploy to staging server
echo "🌐 Deploying to staging server..."

# Create deployment directory
ssh ${STAGING_USER}@${STAGING_SERVER} "mkdir -p /opt/enterprise-dashboard/staging"

# Copy docker-compose and configuration files
scp docker-compose.yml ${STAGING_USER}@${STAGING_SERVER}:/opt/enterprise-dashboard/staging/
scp docker-compose.staging.yml ${STAGING_USER}@${STAGING_SERVER}:/opt/enterprise-dashboard/staging/
scp -r nginx/ ${STAGING_USER}@${STAGING_SERVER}:/opt/enterprise-dashboard/staging/

# Update environment variables
ssh ${STAGING_USER}@${STAGING_SERVER} "cd /opt/enterprise-dashboard/staging && cat > .env.staging << EOF
NODE_ENV=staging
DATABASE_URL=${STAGING_DATABASE_URL}
REDIS_URL=${STAGING_REDIS_URL}
JWT_SECRET=${STAGING_JWT_SECRET}
JWT_REFRESH_SECRET=${STAGING_JWT_REFRESH_SECRET}
CORS_ORIGIN=https://staging.enterprise-dashboard.com
FRONTEND_IMAGE=${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/frontend:${BRANCH}-${COMMIT_SHA}
BACKEND_IMAGE=${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-company/enterprise-dashboard}/backend:${BRANCH}-${COMMIT_SHA}
EOF"

# Pull and start services
ssh ${STAGING_USER}@${STAGING_SERVER} "cd /opt/enterprise-dashboard/staging && \
    docker-compose -f docker-compose.yml -f docker-compose.staging.yml --env-file .env.staging pull && \
    docker-compose -f docker-compose.yml -f docker-compose.staging.yml --env-file .env.staging up -d"

# Run database migrations
echo "🗄️  Running database migrations..."
ssh ${STAGING_USER}@${STAGING_SERVER} "cd /opt/enterprise-dashboard/staging && \
    docker-compose -f docker-compose.yml -f docker-compose.staging.yml --env-file .env.staging exec -T backend npm run db:migrate:deploy"

# Health check
echo "🏥 Performing health check..."
sleep 30

HEALTH_CHECK_URL="https://${STAGING_SERVER}/health"
if curl -f -s "$HEALTH_CHECK_URL" > /dev/null; then
    echo "✅ Staging deployment successful!"
    echo "🌐 Application is available at: https://${STAGING_SERVER}"
else
    echo "❌ Health check failed!"
    echo "🔍 Checking logs..."
    ssh ${STAGING_USER}@${STAGING_SERVER} "cd /opt/enterprise-dashboard/staging && docker-compose logs --tail=50"
    exit 1
fi

# Clean up old images
echo "🧹 Cleaning up old images..."
ssh ${STAGING_USER}@${STAGING_SERVER} "docker image prune -f"

echo "🎉 Staging deployment completed successfully!"