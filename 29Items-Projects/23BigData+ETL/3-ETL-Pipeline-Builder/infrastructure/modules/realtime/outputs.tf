output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecr_repository_urls" {
  value = { for key, repo in aws_ecr_repository.services : key => repo.repository_url }
}

output "redis_primary_endpoint" {
  description = "REDIS_URL host for cloud environments"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}
