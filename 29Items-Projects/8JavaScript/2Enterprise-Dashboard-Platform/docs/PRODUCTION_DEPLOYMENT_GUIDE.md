# Enterprise Dashboard Platform - Production Deployment Guide

## 📋 Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Infrastructure Setup](#infrastructure-setup)
4. [Environment Configuration](#environment-configuration)
5. [Database Setup](#database-setup)
6. [Application Deployment](#application-deployment)
7. [SSL/TLS Configuration](#ssltls-configuration)
8. [Monitoring Setup](#monitoring-setup)
9. [Backup Configuration](#backup-configuration)
10. [Post-Deployment Verification](#post-deployment-verification)
11. [Rollback Procedures](#rollback-procedures)
12. [Troubleshooting](#troubleshooting)

## 🎯 Overview

This guide provides comprehensive instructions for deploying the Enterprise Dashboard Platform to production environments. The platform supports multiple deployment strategies and infrastructure configurations to ensure high availability, security, and performance.

### Architecture Overview

```mermaid
graph TB
    LB[Load Balancer/Ingress] --> FE[Frontend Pods]
    LB --> API[Backend API Pods]
    API --> DB[(PostgreSQL)]
    API --> REDIS[(Redis Cache)]
    API --> S3[File Storage]

    API --> PROM[Prometheus]
    PROM --> GRAF[Grafana]
    API --> LOKI[Loki Logs]
```

### Deployment Strategies Supported

- **Rolling Update** - Zero-downtime updates with gradual instance replacement
- **Blue-Green** - Complete environment switch with instant rollback capability
- **Canary** - Gradual traffic shifting with real-time monitoring

## 🔧 Prerequisites

### Infrastructure Requirements

| Component | Minimum | Recommended | Production |
|-----------|---------|-------------|------------|
| **Kubernetes Cluster** | 1.25+ | 1.27+ | 1.28+ |
| **CPU** | 4 cores | 8 cores | 16+ cores |
| **Memory** | 8GB | 16GB | 32GB+ |
| **Storage** | 100GB SSD | 500GB SSD | 1TB+ NVMe |
| **Network** | 100 Mbps | 1 Gbps | 10 Gbps |

### Required Tools

```bash
# Install required command-line tools
kubectl version --client # v1.25+
docker --version         # v20.0+
helm version            # v3.10+
git --version          # v2.30+
jq --version           # v1.6+
curl --version         # v7.0+
```

### Access Requirements

- **Kubernetes Cluster** - Admin access to target cluster
- **Container Registry** - Pull access to image registry (GHCR, Docker Hub, etc.)
- **DNS Management** - Ability to configure DNS records
- **SSL Certificates** - Valid certificates for your domains
- **Secrets Management** - Access to secrets management system

## 🏗️ Infrastructure Setup

### 1. Kubernetes Cluster Setup

#### Using AWS EKS

```bash
# Create EKS cluster
eksctl create cluster \
  --name enterprise-dashboard \
  --region us-west-2 \
  --version 1.28 \
  --nodes 3 \
  --nodes-min 3 \
  --nodes-max 10 \
  --node-type t3.large \
  --with-oidc \
  --managed

# Verify cluster
kubectl get nodes
```

#### Using Azure AKS

```bash
# Create resource group
az group create --name enterprise-dashboard-rg --location westus2

# Create AKS cluster
az aks create \
  --resource-group enterprise-dashboard-rg \
  --name enterprise-dashboard \
  --node-count 3 \
  --node-vm-size Standard_D2s_v3 \
  --generate-ssh-keys \
  --kubernetes-version 1.28.0

# Get credentials
az aks get-credentials --resource-group enterprise-dashboard-rg --name enterprise-dashboard
```

#### Using Google GKE

```bash
# Create GKE cluster
gcloud container clusters create enterprise-dashboard \
  --zone us-central1-a \
  --cluster-version 1.28 \
  --machine-type n1-standard-4 \
  --num-nodes 3 \
  --enable-autoscaling \
  --min-nodes 3 \
  --max-nodes 10

# Get credentials
gcloud container clusters get-credentials enterprise-dashboard --zone us-central1-a
```

### 2. Namespace and RBAC Setup

```bash
# Create production namespace
kubectl create namespace production

# Create service accounts and RBAC
kubectl apply -f k8s/rbac.yaml

# Verify permissions
kubectl auth can-i create pods --namespace production --as system:serviceaccount:production:dashboard-backend
```

### 3. Storage Configuration

#### PostgreSQL Setup

**Option A: Managed Database (Recommended for Production)**

```bash
# AWS RDS PostgreSQL
aws rds create-db-instance \
  --db-instance-identifier enterprise-dashboard-prod \
  --db-instance-class db.r5.large \
  --engine postgres \
  --engine-version 16.1 \
  --master-username dbadmin \
  --master-user-password "$(openssl rand -base64 32)" \
  --allocated-storage 100 \
  --storage-type gp2 \
  --vpc-security-group-ids sg-xxxxxxxxx \
  --backup-retention-period 30 \
  --multi-az \
  --storage-encrypted
```

**Option B: In-Cluster PostgreSQL (Development/Staging)**

```bash
# Add Bitnami Helm repository
helm repo add bitnami https://charts.bitnami.com/bitnami

# Install PostgreSQL
helm install postgresql bitnami/postgresql \
  --namespace production \
  --set auth.postgresPassword="$(openssl rand -base64 32)" \
  --set primary.persistence.size=100Gi \
  --set primary.resources.requests.memory=2Gi \
  --set primary.resources.requests.cpu=1000m
```

#### Redis Setup

```bash
# Install Redis
helm install redis bitnami/redis \
  --namespace production \
  --set auth.password="$(openssl rand -base64 32)" \
  --set master.persistence.size=10Gi \
  --set replica.replicaCount=2
```

## ⚙️ Environment Configuration

### 1. Create Production Secrets

```bash
# Create secrets from environment file
kubectl create secret generic dashboard-secrets \
  --namespace production \
  --from-env-file=backend/.env.production

# Or create secrets individually
kubectl create secret generic dashboard-secrets \
  --namespace production \
  --from-literal=database-url="postgresql://user:pass@host:5432/db" \
  --from-literal=redis-url="redis://user:pass@host:6379" \
  --from-literal=jwt-secret="$(openssl rand -base64 64)" \
  --from-literal=session-secret="$(openssl rand -base64 64)"
```

### 2. Configure TLS Certificates

**Option A: Let's Encrypt with cert-manager**

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Create ClusterIssuer
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@yourcompany.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
```

**Option B: Manual Certificate Upload**

```bash
# Create TLS secret from existing certificates
kubectl create secret tls dashboard-tls \
  --namespace production \
  --cert=path/to/certificate.crt \
  --key=path/to/private.key
```

### 3. Configure Environment Variables

Create `k8s/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: dashboard-config
  namespace: production
data:
  NODE_ENV: "production"
  LOG_LEVEL: "info"
  CORS_ORIGIN: "https://dashboard.yourcompany.com"
  HELMET_ENABLED: "true"
  RATE_LIMIT_ENABLED: "true"
  CSRF_ENABLED: "true"
  FORCE_HTTPS: "true"
  HEALTH_CHECK_ENABLED: "true"
  PERFORMANCE_MONITORING_ENABLED: "true"
```

## 🗄️ Database Setup

### 1. Database Migration

```bash
# Run database migrations
export DATABASE_URL="postgresql://user:pass@host:5432/database"

# Using the application container
kubectl run migration-job \
  --namespace production \
  --image=ghcr.io/your-org/enterprise-dashboard:latest \
  --rm -it --restart=Never \
  --env="DATABASE_URL=$DATABASE_URL" \
  -- npx prisma migrate deploy

# Or using a Kubernetes Job
kubectl apply -f k8s/migration-job.yaml
```

### 2. Database Optimization

```sql
-- Connect to your PostgreSQL database and run these optimizations

-- Enable monitoring
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
ALTER SYSTEM SET pg_stat_statements.track = 'all';

-- Optimize for production workload
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET work_mem = '4MB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';

-- Apply changes
SELECT pg_reload_conf();
```

### 3. Database Backup Setup

```bash
# Create backup storage
kubectl apply -f - <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: backup-storage
  namespace: production
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 100Gi
EOF

# Deploy backup CronJob
kubectl apply -f k8s/backup-cronjob.yaml
```

## 🚀 Application Deployment

### 1. Build and Push Images

```bash
# Build production images
docker build -f backend/Dockerfile.production -t enterprise-dashboard-backend:v1.0.0 backend/
docker build -f frontend/Dockerfile.production -t enterprise-dashboard-frontend:v1.0.0 frontend/

# Tag for registry
docker tag enterprise-dashboard-backend:v1.0.0 ghcr.io/your-org/enterprise-dashboard-backend:v1.0.0
docker tag enterprise-dashboard-frontend:v1.0.0 ghcr.io/your-org/enterprise-dashboard-frontend:v1.0.0

# Push to registry
docker push ghcr.io/your-org/enterprise-dashboard-backend:v1.0.0
docker push ghcr.io/your-org/enterprise-dashboard-frontend:v1.0.0
```

### 2. Deploy Application

```bash
# Set deployment variables
export ENVIRONMENT=production
export NAMESPACE=production
export VERSION=v1.0.0
export DOMAIN=dashboard.yourcompany.com
export REPLICAS=3
export RESOURCES_CPU=1000m
export RESOURCES_MEMORY=2Gi
export DATABASE_POOL_SIZE=20

# Deploy application
envsubst < k8s/deployment.yaml | kubectl apply -f -

# Verify deployment
kubectl rollout status deployment/dashboard-backend -n production --timeout=600s
```

### 3. Configure Ingress

```bash
# Install NGINX Ingress Controller
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --set controller.service.type=LoadBalancer

# Apply ingress configuration
kubectl apply -f k8s/ingress.yaml
```

## 🔒 SSL/TLS Configuration

### 1. Automatic Certificate Management

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: dashboard-ingress
  namespace: production
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - dashboard.yourcompany.com
    - api.yourcompany.com
    secretName: dashboard-tls-auto
  rules:
  - host: dashboard.yourcompany.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: dashboard-frontend-service
            port:
              number: 80
  - host: api.yourcompany.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: dashboard-service
            port:
              number: 80
```

### 2. Security Headers Configuration

```yaml
# Additional security annotations for ingress
nginx.ingress.kubernetes.io/configuration-snippet: |
  add_header X-Frame-Options "SAMEORIGIN" always;
  add_header X-Content-Type-Options "nosniff" always;
  add_header X-XSS-Protection "1; mode=block" always;
  add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
  add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' wss:; font-src 'self';" always;
```

## 📊 Monitoring Setup

### 1. Prometheus and Grafana

```bash
# Add Helm repositories
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts

# Install Prometheus
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.retention=30d \
  --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=50Gi

# Install Grafana (if not included in prometheus stack)
helm install grafana grafana/grafana \
  --namespace monitoring \
  --set persistence.enabled=true \
  --set persistence.size=10Gi \
  --set adminPassword="$(openssl rand -base64 32)"
```

### 2. Application Metrics

```bash
# Create ServiceMonitor for application metrics
kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: dashboard-metrics
  namespace: production
spec:
  selector:
    matchLabels:
      app: enterprise-dashboard
      component: backend
  endpoints:
  - port: http
    path: /metrics
    interval: 30s
EOF
```

### 3. Log Aggregation

```bash
# Install Loki
helm install loki grafana/loki \
  --namespace monitoring \
  --set persistence.enabled=true \
  --set persistence.size=50Gi

# Install Promtail for log collection
helm install promtail grafana/promtail \
  --namespace monitoring \
  --set config.lokiAddress=http://loki:3100/loki/api/v1/push
```

## 💾 Backup Configuration

### 1. Database Backup Strategy

```bash
# Create backup schedule
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: database-backup
  namespace: production
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:16-alpine
            env:
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: dashboard-secrets
                  key: postgres-password
            command:
            - /bin/bash
            - -c
            - |
              pg_dump -h postgres-host -U postgres -d enterprise_dashboard > /backup/backup-$(date +%Y%m%d-%H%M%S).sql
              # Upload to S3 or other storage
              aws s3 cp /backup/backup-$(date +%Y%m%d-%H%M%S).sql s3://your-backup-bucket/
            volumeMounts:
            - name: backup-storage
              mountPath: /backup
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: backup-storage
          restartPolicy: OnFailure
EOF
```

### 2. Application Data Backup

```bash
# Backup uploaded files and configuration
kubectl apply -f k8s/backup-cronjob.yaml
```

## ✅ Post-Deployment Verification

### 1. Automated Verification

```bash
# Run deployment verification script
./scripts/deployment-verification.sh production --full-suite

# Check all pods are running
kubectl get pods -n production -l app=enterprise-dashboard

# Verify services
kubectl get services -n production

# Check ingress
kubectl get ingress -n production
```

### 2. Manual Health Checks

```bash
# Test API endpoints
curl -k https://api.yourcompany.com/health
curl -k https://api.yourcompany.com/api/performance/metrics

# Test frontend
curl -k https://dashboard.yourcompany.com/

# Test SSL certificate
echo | openssl s_client -servername dashboard.yourcompany.com -connect dashboard.yourcompany.com:443 2>/dev/null | openssl x509 -noout -dates
```

### 3. Performance Validation

```bash
# Load testing with Apache Bench
ab -n 1000 -c 10 https://api.yourcompany.com/health

# Monitor resource usage
kubectl top pods -n production
kubectl top nodes
```

## 🔄 Rollback Procedures

### 1. Quick Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/dashboard-backend -n production

# Rollback to specific revision
kubectl rollout undo deployment/dashboard-backend -n production --to-revision=2

# Check rollout status
kubectl rollout status deployment/dashboard-backend -n production
```

### 2. Blue-Green Rollback

```bash
# Switch traffic back to blue environment
kubectl patch service dashboard-service -n production \
  -p '{"spec":{"selector":{"version":"blue"}}}'

# Verify rollback
./scripts/deployment-verification.sh production
```

### 3. Database Rollback

```bash
# Restore from backup
kubectl run restore-job \
  --namespace production \
  --image=postgres:16-alpine \
  --rm -it --restart=Never \
  --env="PGPASSWORD=$DB_PASSWORD" \
  -- psql -h $DB_HOST -U $DB_USER -d $DB_NAME -f /backup/backup-YYYYMMDD-HHMMSS.sql
```

## 🔧 Troubleshooting

### Common Issues and Solutions

#### Pods Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n production

# Check logs
kubectl logs <pod-name> -n production --previous

# Common causes:
# - Image pull errors: Check registry credentials
# - Resource limits: Check node resources
# - Configuration errors: Verify secrets and configmaps
```

#### Database Connection Issues

```bash
# Test database connectivity
kubectl run db-test \
  --namespace production \
  --image=postgres:16-alpine \
  --rm -it --restart=Never \
  --env="PGPASSWORD=$DB_PASSWORD" \
  -- psql -h $DB_HOST -U $DB_USER -c "SELECT version();"

# Check database logs
kubectl logs postgresql-0 -n production
```

#### SSL Certificate Issues

```bash
# Check certificate status
kubectl describe certificate dashboard-tls-auto -n production

# Check cert-manager logs
kubectl logs -l app=cert-manager -n cert-manager

# Manual certificate renewal
kubectl delete certificate dashboard-tls-auto -n production
kubectl apply -f k8s/ingress.yaml
```

#### High Resource Usage

```bash
# Check resource usage
kubectl top pods -n production --sort-by=memory
kubectl top nodes --sort-by=memory

# Scale up if needed
kubectl scale deployment dashboard-backend -n production --replicas=5

# Check for memory leaks
kubectl exec -it <pod-name> -n production -- ps aux
```

## 📚 Additional Resources

### Documentation Links

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [Prometheus Monitoring](https://prometheus.io/docs/)
- [Let's Encrypt](https://letsencrypt.org/docs/)

### Support Contacts

- **DevOps Team**: devops@yourcompany.com
- **Platform Team**: platform@yourcompany.com
- **Security Team**: security@yourcompany.com

### Emergency Procedures

1. **Service Outage**: Contact on-call engineer via PagerDuty
2. **Security Incident**: Follow incident response playbook
3. **Data Loss**: Initiate disaster recovery procedures

---

**Last Updated**: December 2024
**Version**: 1.0.0
**Maintainer**: Platform Engineering Team