variable "aws_region" {
  type        = string
  description = "AWS region for the distributed task queue infrastructure."
  default     = "us-east-1"
}

variable "environment" {
  type        = string
  description = "Deployment environment name."
  default     = "dev"
}

variable "postgres_instance_class" {
  type        = string
  description = "RDS PostgreSQL instance class."
  default     = "db.t4g.micro"
}

variable "redis_node_type" {
  type        = string
  description = "ElastiCache Redis node type."
  default     = "cache.t4g.micro"
}

variable "api_ingress_cidr_blocks" {
  type        = list(string)
  description = "CIDR blocks allowed to reach the API service security group."
  default     = ["10.0.0.0/8"]
}

variable "deletion_protection" {
  type        = bool
  description = "Enable deletion protection for stateful resources."
  default     = true
}
