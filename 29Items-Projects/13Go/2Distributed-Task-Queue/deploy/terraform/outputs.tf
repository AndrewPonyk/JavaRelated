output "ecr_repository_url" {
  description = "ECR repository URL used by the CI pipeline."
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name for API, worker, scheduler, and NATS services."
  value       = aws_ecs_cluster.main.name
}

output "postgres_endpoint" {
  description = "PostgreSQL endpoint for DATABASE_URL construction."
  value       = aws_db_instance.postgres.address
}

output "redis_endpoint" {
  description = "Redis endpoint for REDIS_ADDR."
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}
