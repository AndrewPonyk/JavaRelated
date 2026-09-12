# ElastiCache (Redis) module (stub) — cache + Celery broker/result store.
# TODO: aws_elasticache_replication_group with encryption in transit/at rest,
# automatic failover, and a subnet group.

variable "environment" { type = string }

variable "node_type" {
  type    = string
  default = "cache.t4g.small"
}

output "primary_endpoint" { value = "TODO" }
