# AWS EC2 Deployment Notes

Recommended production shape:

- EC2 hosts run versioned Docker images behind an Application Load Balancer.
- PostGIS runs on Amazon RDS for PostgreSQL with PostGIS enabled.
- Raster inputs, trained models, and generated artifacts live in S3.
- Secrets live in AWS Secrets Manager or Systems Manager Parameter Store.
- CloudWatch collects container logs and host metrics.

Implemented repository support:

- Docker Compose bundle validation in CI.
- Environment profile files for development, staging, and production.
- PostGIS migration and backup scripts.
- Container images for backend and frontend.

Production requirements:

- Set `ENFORCE_HTTPS=true`.
- Source database, GeoServer, and JWT values from AWS Secrets Manager or Systems Manager Parameter Store.
- Use a non-superuser PostGIS account for the API and a read-scoped account for GeoServer.
