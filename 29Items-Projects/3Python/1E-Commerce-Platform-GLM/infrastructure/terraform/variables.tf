# Terraform Variables

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "ecommerce"
}

variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "production"], var.environment)
    error_message = "Environment must be dev, staging, or production."
  }
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

# VPC
variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones"
  type        = number
  default     = 2
}

# ECS
variable "ecs_cluster_size" {
  description = "ECS cluster capacity provider"
  type        = string
  default     = "FARGATE"
}

variable "backend_cpu" {
  description = "CPU units for backend task"
  type        = number
  default     = 2048
}

variable "backend_memory" {
  description = "Memory for backend task (MB)"
  type        = number
  default     = 4096
}

variable "backend_desired_count" {
  description = "Desired count for backend service"
  type        = number
  default     = 2
}

variable "backend_repository_url" {
  description = "ECR repository URL for backend"
  type        = string
}

variable "image_tag" {
  description = "Docker image tag"
  type        = string
  default     = "latest"
}

# Database
variable "database_name" {
  description = "Database name"
  type        = string
  default     = "ecommerce"
}

variable "database_user" {
  description = "Database username"
  type        = string
  default     = "postgres"
}

variable "database_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

# Redis
variable "redis_node_count" {
  description = "Number of Redis nodes"
  type        = number
  default     = 1
}

variable "redis_instance_type" {
  description = "ElastiCache instance type"
  type        = string
  default     = "cache.t3.micro"
}

# Elasticsearch
variable "es_instance_type" {
  description = "Elasticsearch instance type"
  type        = string
  default     = "t3.small.search"
}

variable "es_instance_count" {
  description = "Number of Elasticsearch instances"
  type        = number
  default     = 1
}

# SSL
variable "ssl_certificate_arn" {
  description = "ACM certificate ARN for SSL"
  type        = string
  default     = ""
}
